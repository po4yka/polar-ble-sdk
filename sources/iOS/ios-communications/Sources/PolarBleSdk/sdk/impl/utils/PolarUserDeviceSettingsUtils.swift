//  Copyright © 2024 Polar. All rights reserved.

import Foundation

public let DEVICE_SETTINGS_FILE_PATH = "/U/0/S/UDEVSET.BPB"
public let SENSOR_SETTINGS_FILE_PATH = "/UDEVSET.BPB"
private let TAG = "PolarUserDeviceSettingsUtils"

internal class PolarUserDeviceSettingsUtils {
    static func getUserDeviceSettings(client: BlePsFtpClient, deviceSettingsPath: String) async throws -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult {
        BleLogger.trace(TAG, "getUserDeviceSettings")
        let plannedOperation = PolarRuntimePlanner.userDeviceSettingsOperations(id: "get-user-device-settings", kind: "read", path: deviceSettingsPath)?.first
        let operation = Protocol_PbPFtpOperation.with {
            $0.command = plannedOperation?.command ?? .get
            $0.path = plannedOperation?.path ?? deviceSettingsPath
        }
        PolarRuntimePlanner.userDeviceSettings(id: "get-user-device-settings", kind: "read", path: deviceSettingsPath)
        let response = try await client.request(try operation.serializedBytes())
        let proto = try Data_PbUserDeviceSettings(serializedBytes: Data(response))
        return PolarUserDeviceSettings.fromProto(pbUserDeviceSettings: proto)
    }
}
