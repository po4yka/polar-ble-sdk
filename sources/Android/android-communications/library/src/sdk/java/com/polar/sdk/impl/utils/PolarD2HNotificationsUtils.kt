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
            val parameters = notification.byteArrayOutputStream.toByteArray()
            val emissionPlan = PolarD2hRuntimePlanning.planNotificationEmission(notification.id, parameters.toHexString())
            if (emissionPlan == null) {
                BleLogger.w(TAG, "Unknown notification type: ${notification.id}")
            } else {
                val notificationType = PolarDeviceToHostNotification.fromValue(notification.id)
                    ?: error("Shared D2H notification type ${emissionPlan.notificationType} is not represented by the Android public enum")
                val parsedParameters = parseD2HNotificationParameters(notificationType, parameters, emissionPlan.parsedProto)
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
    data: ByteArray,
    sharedParsedProtoName: String? = PolarD2hRuntimePlanning.parsedProtoName(notificationType.name, data.toHexString())
): Any? {
    if (data.isEmpty()) {
        return null
    }

    return try {
        when (sharedParsedProtoName) {
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

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}
