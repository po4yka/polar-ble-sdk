//  Copyright © 2024 Polar. All rights reserved.

import Foundation

internal let DEVICE_SETTINGS_FILE_PATH = "/U/0/S/UDEVSET.BPB"
internal let SENSOR_SETTINGS_FILE_PATH = "/UDEVSET.BPB"
private let TAG = "PolarUserDeviceSettingsUtils"

internal class PolarUserDeviceSettingsUtils {
    static func getUserDeviceSettings(client: BlePsFtpClient, deviceSettingsPath: String) async throws -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult {
        BleLogger.trace(TAG, "getUserDeviceSettings")
        let plannedOperation = PolarRuntimePlanner.userDeviceSettingsOperations(id: "get-user-device-settings", kind: "read", path: deviceSettingsPath)?.first
        let operation = plannedOperation ?? (.get, deviceSettingsPath)
        let terminal = PolarRuntimePlanner.userDeviceSettings(id: "get-user-device-settings", kind: "read", path: deviceSettingsPath)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw PolarErrors.polarBleSdkInternalException(description: "User-device-settings planning failed: \(terminal)")
        }
        let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(operation))
        let proto = try Data_PbUserDeviceSettings(serializedBytes: Data(response))
        return PolarUserDeviceSettings.fromProto(pbUserDeviceSettings: proto)
    }
}
