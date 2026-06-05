// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import com.google.protobuf.InvalidProtocolBufferException
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.shared.runtime.PolarD2hRuntimePlanning
import com.polar.sdk.api.PolarD2HNotificationData
import com.polar.sdk.api.PolarDeviceToHostNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import protocol.PftpNotification.*

private const val TAG = "PolarD2HNotificationsUtils"

/**
 * Extension function for BlePsFtpClient to observe device-to-host notifications.
 *
 * @param identifier Polar device ID or BT address (used for logging)
 * @return Flow of parsed device-to-host notifications
 */
fun BlePsFtpClient.observeDeviceToHostNotifications(identifier: String): Flow<PolarD2HNotificationData> {
    return waitForNotification()
        .transform { notification ->
            val sharedNotificationType = PolarD2hRuntimePlanning.notificationTypeOrNull(notification.id)
            if (sharedNotificationType == null) {
                BleLogger.w(TAG, "Unknown notification type: ${notification.id}")
            } else {
                val notificationType = PolarDeviceToHostNotification.fromValue(notification.id)
                    ?: error("Shared D2H notification type $sharedNotificationType is not represented by the Android public enum")
                val parameters = notification.byteArrayOutputStream.toByteArray()
                val parsedParameters = parseD2HNotificationParameters(notificationType, parameters)
                emit(PolarD2HNotificationData(notificationType, parameters, parsedParameters))
            }
        }
        .onEach { data ->
            BleLogger.d(TAG, "Received D2H notification for $identifier: ${data.notificationType}")
        }
        .catch { error ->
            BleLogger.e(TAG, "D2H notification error for $identifier: ${error.message}")
            throw error
        }
}

/**
 * Parses the raw parameter data based on the notification type.
 *
 * @param notificationType The type of notification
 * @param data Raw parameter data
 * @return Parsed parameter object or null if parsing fails or no parameters expected
 */
private fun parseD2HNotificationParameters(
    notificationType: PolarDeviceToHostNotification,
    data: ByteArray
): Any? {
    if (data.isEmpty()) {
        return null
    }

    val parsedProtoName = PolarD2hRuntimePlanning.parsedProtoName(notificationType.name, data.toHexString())
        ?: localParsedProtoName(notificationType)

    return try {
        when (parsedProtoName) {
            "PbPFtpSyncRequiredParams" -> {
                PbPFtpSyncRequiredParams.parseFrom(data)
            }
            "PbPFtpFilesystemModifiedParams" -> {
                PbPFtpFilesystemModifiedParams.parseFrom(data)
            }
            "PbPFtpInactivityAlert" -> {
                PbPFtpInactivityAlert.parseFrom(data)
            }
            "PbPFtpTrainingSessionStatus" -> {
                PbPFtpTrainingSessionStatus.parseFrom(data)
            }
            "PbPFtpAutoSyncStatusParams" -> {
                PbPFtpAutoSyncStatusParams.parseFrom(data)
            }
            "PbPftpPnsDHNotificationResponse" -> {
                PbPftpPnsDHNotificationResponse.parseFrom(data)
            }
            "PbPftpPnsState" -> {
                PbPftpPnsState.parseFrom(data)
            }
            "PbPftpStartGPSMeasurement" -> {
                PbPftpStartGPSMeasurement.parseFrom(data)
            }
            "PbPFtpPolarShellMessageParams" -> {
                PbPFtpPolarShellMessageParams.parseFrom(data)
            }
            "PbPftpDHMediaControlRequest" -> {
                PbPftpDHMediaControlRequest.parseFrom(data)
            }
            "PbPftpDHMediaControlCommand" -> {
                PbPftpDHMediaControlCommand.parseFrom(data)
            }
            "PbPftpDHMediaControlEnabled" -> {
                PbPftpDHMediaControlEnabled.parseFrom(data)
            }
            "PbPftpDHRestApiEvent" -> {
                PbPftpDHRestApiEvent.parseFrom(data)
            }
            "PbPftpDHExerciseStatus" -> {
                PbPftpDHExerciseStatus.parseFrom(data)
            }
            else -> {
                BleLogger.d(TAG, "No parameter parsing implemented for: $notificationType")
                null
            }
        }
    } catch (e: InvalidProtocolBufferException) {
        BleLogger.e(TAG, "Failed to parse parameters for $notificationType: ${e.message}")
        null
    }
}

private fun localParsedProtoName(notificationType: PolarDeviceToHostNotification): String? {
    return when (notificationType) {
        PolarDeviceToHostNotification.SYNC_REQUIRED -> "PbPFtpSyncRequiredParams"
        PolarDeviceToHostNotification.FILESYSTEM_MODIFIED -> "PbPFtpFilesystemModifiedParams"
        PolarDeviceToHostNotification.INACTIVITY_ALERT -> "PbPFtpInactivityAlert"
        PolarDeviceToHostNotification.TRAINING_SESSION_STATUS -> "PbPFtpTrainingSessionStatus"
        PolarDeviceToHostNotification.AUTOSYNC_STATUS -> "PbPFtpAutoSyncStatusParams"
        PolarDeviceToHostNotification.PNS_DH_NOTIFICATION_RESPONSE -> "PbPftpPnsDHNotificationResponse"
        PolarDeviceToHostNotification.PNS_SETTINGS -> "PbPftpPnsState"
        PolarDeviceToHostNotification.START_GPS_MEASUREMENT -> "PbPftpStartGPSMeasurement"
        PolarDeviceToHostNotification.POLAR_SHELL_DH_DATA -> "PbPFtpPolarShellMessageParams"
        PolarDeviceToHostNotification.MEDIA_CONTROL_REQUEST_DH -> "PbPftpDHMediaControlRequest"
        PolarDeviceToHostNotification.MEDIA_CONTROL_COMMAND_DH -> "PbPftpDHMediaControlCommand"
        PolarDeviceToHostNotification.MEDIA_CONTROL_ENABLED -> "PbPftpDHMediaControlEnabled"
        PolarDeviceToHostNotification.REST_API_EVENT -> "PbPftpDHRestApiEvent"
        PolarDeviceToHostNotification.EXERCISE_STATUS -> "PbPftpDHExerciseStatus"
        else -> null
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}
