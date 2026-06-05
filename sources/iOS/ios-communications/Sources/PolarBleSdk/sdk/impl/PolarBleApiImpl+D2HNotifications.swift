import Foundation

extension PolarBleApiImpl: PolarDeviceToHostNotificationsApi {
    func observeDeviceToHostNotifications(identifier: String) -> AsyncThrowingStream<PolarD2HNotificationData, Error> {
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    for try await notification in client.waitNotification() {
                        let mappedNotification: PolarDeviceToHostNotification
                        let planningNotificationType: String
                        if let sharedNotificationType = PolarRuntimePlanner.d2hNotificationTypeName(notificationId: Int(notification.id)) {
                            guard let sharedMappedNotification = PolarDeviceToHostNotification(sharedNotificationType: sharedNotificationType) else {
                                continuation.finish(throwing: PolarErrors.invalidArgument(description: "Shared D2H notification type \(sharedNotificationType) is not represented by the iOS public enum"))
                                return
                            }
                            mappedNotification = sharedMappedNotification
                            planningNotificationType = sharedNotificationType
                        } else {
                            guard let rawMappedNotification = PolarDeviceToHostNotification(rawValue: Int(notification.id)) else {
                                BleLogger.trace("Unknown notification type: \(notification.id)")
                                continue
                            }
                            mappedNotification = rawMappedNotification
                            planningNotificationType = rawMappedNotification.sharedNotificationType
                        }
                        let parameters = Data(notification.parameters)
                        let sharedParsedProtoName = PolarRuntimePlanner.d2hParsedProtoName(notificationType: planningNotificationType, parametersHex: parameters.map { String(format: "%02x", $0) }.joined())
                        let parsedParameters = BlePsFtpClient.parseD2HNotificationParameters(mappedNotification, data: parameters, sharedParsedProtoName: sharedParsedProtoName)
                        let data = PolarD2HNotificationData(
                            notificationType: mappedNotification,
                            parameters: parameters,
                            parsedParameters: parsedParameters
                        )
                        BleLogger.trace("Received D2H notification for \(identifier): \(data.notificationType)")
                        continuation.yield(data)
                    }
                    continuation.finish()
                } catch {
                    BleLogger.error("D2H notification error for \(identifier): \(error.localizedDescription)")
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }
}

private extension PolarDeviceToHostNotification {
    init?(sharedNotificationType: String) {
        switch sharedNotificationType {
        case "FILESYSTEM_MODIFIED": self = .filesystemModified
        case "INTERNAL_TEST_EVENT": self = .internalTestEvent
        case "IDLING": self = .idling
        case "BATTERY_STATUS": self = .batteryStatus
        case "INACTIVITY_ALERT": self = .inactivityAlert
        case "TRAINING_SESSION_STATUS": self = .trainingSessionStatus
        case "SYNC_REQUIRED": self = .syncRequired
        case "AUTOSYNC_STATUS": self = .autosyncStatus
        case "PNS_DH_NOTIFICATION_RESPONSE": self = .pnsDhNotificationResponse
        case "PNS_SETTINGS": self = .pnsSettings
        case "START_GPS_MEASUREMENT": self = .startGpsMeasurement
        case "STOP_GPS_MEASUREMENT": self = .stopGpsMeasurement
        case "KEEP_BACKGROUND_ALIVE": self = .keepBackgroundAlive
        case "POLAR_SHELL_DH_DATA": self = .polarShellDhData
        case "MEDIA_CONTROL_REQUEST_DH": self = .mediaControlRequestDh
        case "MEDIA_CONTROL_COMMAND_DH": self = .mediaControlCommandDh
        case "MEDIA_CONTROL_ENABLED": self = .mediaControlEnabled
        case "REST_API_EVENT": self = .restApiEvent
        case "EXERCISE_STATUS": self = .exerciseStatus
        default: return nil
        }
    }

    var sharedNotificationType: String {
        switch self {
        case .filesystemModified: return "FILESYSTEM_MODIFIED"
        case .internalTestEvent: return "INTERNAL_TEST_EVENT"
        case .idling: return "IDLING"
        case .batteryStatus: return "BATTERY_STATUS"
        case .inactivityAlert: return "INACTIVITY_ALERT"
        case .trainingSessionStatus: return "TRAINING_SESSION_STATUS"
        case .syncRequired: return "SYNC_REQUIRED"
        case .autosyncStatus: return "AUTOSYNC_STATUS"
        case .pnsDhNotificationResponse: return "PNS_DH_NOTIFICATION_RESPONSE"
        case .pnsSettings: return "PNS_SETTINGS"
        case .startGpsMeasurement: return "START_GPS_MEASUREMENT"
        case .stopGpsMeasurement: return "STOP_GPS_MEASUREMENT"
        case .keepBackgroundAlive: return "KEEP_BACKGROUND_ALIVE"
        case .polarShellDhData: return "POLAR_SHELL_DH_DATA"
        case .mediaControlRequestDh: return "MEDIA_CONTROL_REQUEST_DH"
        case .mediaControlCommandDh: return "MEDIA_CONTROL_COMMAND_DH"
        case .mediaControlEnabled: return "MEDIA_CONTROL_ENABLED"
        case .restApiEvent: return "REST_API_EVENT"
        case .exerciseStatus: return "EXERCISE_STATUS"
        }
    }
}

extension BlePsFtpClient {
    static func parseD2HNotificationParameters(_ notification: PolarDeviceToHostNotification, data: Data, sharedParsedProtoName: String? = nil) -> Any? {
        if data.isEmpty { return nil }
        do {
            if let sharedParsedProtoName {
                return try parseD2HNotificationParameters(sharedParsedProtoName: sharedParsedProtoName, data: data)
            }
            switch notification {
            case .syncRequired:       return try Protocol_PbPFtpSyncRequiredParams(serializedBytes: data, extensions: nil)
            case .filesystemModified: return try Protocol_PbPFtpFilesystemModifiedParams(serializedBytes: data, extensions: nil)
            case .inactivityAlert:    return try Protocol_PbPFtpInactivityAlert(serializedBytes: data, extensions: nil)
            case .trainingSessionStatus: return try Protocol_PbPFtpTrainingSessionStatus(serializedBytes: data, extensions: nil)
            case .autosyncStatus:     return try Protocol_PbPFtpAutoSyncStatusParams(serializedBytes: data, extensions: nil)
            case .pnsDhNotificationResponse: return try Protocol_PbPftpPnsDHNotificationResponse(serializedBytes: data, extensions: nil)
            case .pnsSettings:        return try Protocol_PbPftpPnsState(serializedBytes: data, extensions: nil)
            case .startGpsMeasurement: return try Protocol_PbPftpStartGPSMeasurement(serializedBytes: data, extensions: nil)
            case .polarShellDhData:   return try Protocol_PbPFtpPolarShellMessageParams(serializedBytes: data, extensions: nil)
            case .mediaControlRequestDh: return try Protocol_PbPftpDHMediaControlRequest(serializedBytes: data, extensions: nil)
            case .mediaControlCommandDh: return try Protocol_PbPftpDHMediaControlCommand(serializedBytes: data, extensions: nil)
            case .mediaControlEnabled: return try Protocol_PbPftpDHMediaControlEnabled(serializedBytes: data, extensions: nil)
            case .restApiEvent:       return try Protocol_PbPftpDHRestApiEvent(serializedBytes: data, extensions: nil)
            case .exerciseStatus:     return try Protocol_PbPftpDHExerciseStatus(serializedBytes: data, extensions: nil)
            default:
                BleLogger.trace("No parameter parsing implemented for: \(notification)")
                return nil
            }
        } catch {
            BleLogger.error("Failed to parse D2H notification parameters for \(notification): \(error.localizedDescription)")
            return nil
        }
    }

    private static func parseD2HNotificationParameters(sharedParsedProtoName: String, data: Data) throws -> Any? {
        switch sharedParsedProtoName {
        case "PbPFtpSyncRequiredParams": return try Protocol_PbPFtpSyncRequiredParams(serializedBytes: data, extensions: nil)
        case "PbPFtpFilesystemModifiedParams": return try Protocol_PbPFtpFilesystemModifiedParams(serializedBytes: data, extensions: nil)
        case "PbPFtpInactivityAlert": return try Protocol_PbPFtpInactivityAlert(serializedBytes: data, extensions: nil)
        case "PbPFtpTrainingSessionStatus": return try Protocol_PbPFtpTrainingSessionStatus(serializedBytes: data, extensions: nil)
        case "PbPFtpAutoSyncStatusParams": return try Protocol_PbPFtpAutoSyncStatusParams(serializedBytes: data, extensions: nil)
        case "PbPftpPnsDHNotificationResponse": return try Protocol_PbPftpPnsDHNotificationResponse(serializedBytes: data, extensions: nil)
        case "PbPftpPnsState": return try Protocol_PbPftpPnsState(serializedBytes: data, extensions: nil)
        case "PbPftpStartGPSMeasurement": return try Protocol_PbPftpStartGPSMeasurement(serializedBytes: data, extensions: nil)
        case "PbPFtpPolarShellMessageParams": return try Protocol_PbPFtpPolarShellMessageParams(serializedBytes: data, extensions: nil)
        case "PbPftpDHMediaControlRequest": return try Protocol_PbPftpDHMediaControlRequest(serializedBytes: data, extensions: nil)
        case "PbPftpDHMediaControlCommand": return try Protocol_PbPftpDHMediaControlCommand(serializedBytes: data, extensions: nil)
        case "PbPftpDHMediaControlEnabled": return try Protocol_PbPftpDHMediaControlEnabled(serializedBytes: data, extensions: nil)
        case "PbPftpDHRestApiEvent": return try Protocol_PbPftpDHRestApiEvent(serializedBytes: data, extensions: nil)
        case "PbPftpDHExerciseStatus": return try Protocol_PbPftpDHExerciseStatus(serializedBytes: data, extensions: nil)
        default:
            BleLogger.trace("No shared D2H parameter parser implemented for: \(sharedParsedProtoName)")
            return nil
        }
    }
}
