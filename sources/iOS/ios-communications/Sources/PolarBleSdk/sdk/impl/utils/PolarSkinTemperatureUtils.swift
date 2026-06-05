//  Copyright © 2025 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let SKIN_TEMPERATURE_DIRECTORY = "SKINTEMP/"
private let SKIN_TEMPERATURE_PROTO = "TEMPCONT.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarSkinTemperatureUtils"

internal class PolarSkinTemperatureUtils {
    static func skinTemperatureReadOperation(date: Date) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        let path = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(SKIN_TEMPERATURE_DIRECTORY)\(SKIN_TEMPERATURE_PROTO)"
        if let plannedOperation = PolarFileFacadeRuntimePlanner.fileFacadeOperation(id: "skin-temperature-read", command: "GET", path: path) {
            return plannedOperation
        }
        return (.get, path)
    }

    static func readSkinTemperatureData(client: BlePsFtpClient, date: Date) async -> PolarSkinTemperatureData.PolarSkinTemperatureResult? {
        BleLogger.trace(TAG, "readSkinTemperatureData: \(date)")
        let plannedOperation = skinTemperatureReadOperation(date: date)
        let filePath = plannedOperation.path
        let operation = Protocol_PbPFtpOperation.with { $0.command = plannedOperation.command; $0.path = plannedOperation.path }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let skinTemp = try Data_TemperatureMeasurementPeriod(serializedBytes: Data(response))
            return PolarSkinTemperatureData.PolarSkinTemperatureResult(
                date: date,
                sensorLocation: sensorLocation(from: skinTemp.sensorLocation),
                measurementType: measurementType(from: skinTemp.measurementType),
                skinTemperatureList: PolarSkinTemperatureData.fromPbTemperatureMeasurementSamples(pbTemperatureMeasurementData: skinTemp.temperatureMeasurementSamples)
            )
        } catch {
            BleLogger.error("readSkinTemperatureData() failed for path: \(filePath), error: \(error)")
            return nil
        }
    }

    private static func measurementType(from value: TemperatureMeasurementType) -> PolarSkinTemperatureData.SkinTemperatureMeasurementType {
        #if canImport(PolarBleSdkShared)
        switch PolarIosSharedBridge.shared.skinTemperatureMeasurementType(value: Int32(value.rawValue)) {
        case "TM_SKIN_TEMPERATURE": return .TM_SKIN_TEMPERATURE
        case "TM_CORE_TEMPERATURE": return .TM_CORE_TEMPERATURE
        default: return .TM_UNKNOWN
        }
        #else
        return PolarSkinTemperatureData.SkinTemperatureMeasurementType.getByValue(value: value)
        #endif
    }

    private static func sensorLocation(from value: SensorLocation) -> PolarSkinTemperatureData.SkinTemperatureSensorLocation {
        #if canImport(PolarBleSdkShared)
        switch PolarIosSharedBridge.shared.skinTemperatureSensorLocation(value: Int32(value.rawValue)) {
        case "SL_DISTAL": return .SL_DISTAL
        case "SL_PROXIMAL": return .SL_PROXIMAL
        default: return .SL_UNKNOWN
        }
        #else
        return PolarSkinTemperatureData.SkinTemperatureSensorLocation.getByValue(value: value)
        #endif
    }
}
