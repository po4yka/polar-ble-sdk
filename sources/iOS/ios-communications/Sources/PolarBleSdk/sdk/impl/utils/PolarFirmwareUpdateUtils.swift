//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import ZIPFoundation

protocol FirmwarePackageExtracting {
    func unzipFirmwarePackage(zippedData: Data) -> [String: Data]?
}

final class ZipFirmwarePackageExtractor: FirmwarePackageExtracting {
    func unzipFirmwarePackage(zippedData: Data) -> [String: Data]? {
        let temporaryDirectory = FileManager.default.temporaryDirectory

        let zipFilePath = temporaryDirectory.appendingPathComponent(UUID().uuidString + ".zip")
        do {
            try zippedData.write(to: zipFilePath)

            let destinationURL = temporaryDirectory.appendingPathComponent(UUID().uuidString)

            try FileManager.default.unzipItem(at: zipFilePath, to: destinationURL)

            let contents = try FileManager.default.contentsOfDirectory(at: destinationURL, includingPropertiesForKeys: nil)
            guard !contents.isEmpty else {
                BleLogger.error("unzipFirmwarePackage() error: No files found in the extracted directory")
                return nil
            }
            var fileDataDictionary: [String: Data] = [:]
            for fileURL in contents {
                let fileName = fileURL.lastPathComponent
                guard PolarFirmwareUpdateUtils.firmwarePackageEntryIsPayload(fileName) else {
                    BleLogger.trace("Skipping file: \(fileName)")
                    continue
                }
                let decompressedData = try Data(contentsOf: fileURL)
                fileDataDictionary[fileName] = decompressedData
                BleLogger.trace("Extracted file: \(fileName) - Size: \(decompressedData.count) bytes")
            }

            try FileManager.default.removeItem(at: zipFilePath)
            try FileManager.default.removeItem(at: destinationURL)

            return fileDataDictionary
        } catch {
            BleLogger.error("Error during unzipFirmwarePackage(): \(error)")
            return nil
        }
    }
}

class PolarFirmwareUpdateUtils {
    static let FIRMWARE_UPDATE_FILE_PATH = PolarRuntimePlanner.firmwareSystemUpdateFilePath()
    static let DEVICE_FIRMWARE_INFO_PATH = PolarRuntimePlanner.firmwareDeviceInfoPath()
    static var packageExtractor: FirmwarePackageExtracting = ZipFirmwarePackageExtractor()

    internal class FwFileComparator {
        static func compare(_ file1: String, _ file2: String) -> ComparisonResult {
            let firstPriority = PolarRuntimePlanner.firmwareFilePriority(file1)
            let secondPriority = PolarRuntimePlanner.firmwareFilePriority(file2)
            if firstPriority < secondPriority { return .orderedAscending }
            if firstPriority > secondPriority { return .orderedDescending }
            return .orderedSame
        }
    }
    
    static func readDeviceFirmwareInfo(client: BlePsFtpClient, deviceId: String) async -> PolarFirmwareVersionInfo? {
        let plannedOperation = deviceFirmwareInfoOperation()
        let request = plannedOperation ?? (.get, DEVICE_FIRMWARE_INFO_PATH)
        do {
            try ensureDeviceFirmwareInfoReadPlan()
            let serializedBytes = try PolarRuntimePlanner.fileOperationBytes(request)
            let response = try await client.request(serializedBytes)
            let proto = try Data_PbDeviceInfo(serializedBytes: response as Data)
            return PolarFirmwareVersionInfo(
                deviceFwVersion: devicePbVersionToString(pbVersion: proto.deviceVersion),
                deviceModelName: proto.modelName,
                deviceHardwareCode: proto.hardwareCode
            )
        } catch {
            BleLogger.error("Failed to request device info: \(deviceId), error: \(error)")
            return nil
        }
    }

    private static func deviceFirmwareInfoOperation() -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        return PolarRuntimePlanner.fileFacadeOperation(id: "firmware-read-device-info", command: "GET", path: DEVICE_FIRMWARE_INFO_PATH)
    }

    private static func ensureDeviceFirmwareInfoReadPlan() throws {
        let terminal = PolarRuntimePlanner.fileFacade(id: "firmware-read-device-info", command: "GET", path: DEVICE_FIRMWARE_INFO_PATH)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw NSError(domain: "PolarFirmwareUpdateUtils", code: -1, userInfo: [NSLocalizedDescriptionKey: "Firmware device-info planning failed: \(terminal)"])
        }
    }

    static func isAvailableFirmwareVersionHigher(currentVersion: String, availableVersion: String) -> Bool {
        return PolarRuntimePlanner.isFirmwareVersionHigher(currentVersion: currentVersion, availableVersion: availableVersion)
    }

    static func firmwarePackageEntryIsPayload(_ fileName: String) -> Bool {
        return PolarRuntimePlanner.firmwarePackageEntryIsPayload(fileName)
    }

    static func firmwareFileTriggersRebootWait(_ fileName: String) -> Bool {
        return PolarRuntimePlanner.firmwareFileTriggersRebootWait(fileName)
    }

    static func unzipFirmwarePackage(zippedData: Data) -> [String: Data]? {
        return packageExtractor.unzipFirmwarePackage(zippedData: zippedData)
    }

    static func firmwareWriteFailure(error: Error, fileName: String, mapBatteryTooLow: () -> Error, mapError: (Error) -> Error) -> Error? {
        let errorCode = (error as NSError).code
        guard isPftpErrorCode(errorCode) else {
            return mapError(error)
        }
        let terminal = PolarRuntimePlanner.firmwareWriteTerminal(errorCode: errorCode, fileName: fileName)
        if terminal == "success-rebooting" {
            let workflowTerminal = PolarRuntimePlanner.firmwareSystemUpdateRebootSuccessWorkflow(fileNames: [fileName])
            guard workflowTerminal == "success" || workflowTerminal == "platform-owned" else {
                return mapError(NSError(domain: "PolarFirmwareUpdateUtils", code: -1, userInfo: [NSLocalizedDescriptionKey: "Firmware system reboot planning failed: \(workflowTerminal)"]))
            }
            return nil
        }
        if terminal == "battery-too-low" {
            let workflowTerminal = PolarRuntimePlanner.firmwareBatteryTooLowTerminalWorkflow(fileNames: [fileName])
            guard workflowTerminal == "success" || workflowTerminal == "platform-owned" else {
                return mapError(NSError(domain: "PolarFirmwareUpdateUtils", code: -1, userInfo: [NSLocalizedDescriptionKey: "Firmware battery terminal planning failed: \(workflowTerminal)"]))
            }
            if let terminalError = PolarRuntimePlanner.firmwareBatteryTooLowTerminalError(fileNames: [fileName]), terminalError != "battery-too-low" {
                return mapError(NSError(domain: "PolarFirmwareUpdateUtils", code: -1, userInfo: [NSLocalizedDescriptionKey: "Firmware battery terminal-error planning failed: \(terminalError)"]))
            }
            return mapBatteryTooLow()
        }
        return mapError(error)
    }

    private static func isPftpErrorCode(_ code: Int) -> Bool {
        return code == 0 || code == 1 || code == 2 || (100...108).contains(code) || (200...209).contains(code)
    }
    
    private static func devicePbVersionToString(pbVersion: PbVersion) -> String {
        return PolarRuntimePlanner.firmwareDeviceVersion(major: Int(pbVersion.major), minor: Int(pbVersion.minor), patch: Int(pbVersion.patch))
    }
}
