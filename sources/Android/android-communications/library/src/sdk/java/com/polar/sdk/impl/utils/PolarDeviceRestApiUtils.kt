package com.polar.sdk.impl.utils

import com.google.gson.Gson
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.RestApiEventPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import protocol.PftpNotification.PbPftpDHRestApiEvent

private const val TAG = "PolarDeviceRestApiUtils"

fun BlePsFtpClient.receiveRestApiEventData(identifier: String): Flow<Array<ByteArray>> {
    return waitForNotification()
        .filter { PolarRuntimePlannerAdapter.d2hNotificationTypeName(it.id) == "REST_API_EVENT" }
        .map { PbPftpDHRestApiEvent.parseFrom(it.byteArrayOutputStream.toByteArray()) }
        .map { proto ->
            if (proto.hasUncompressed() && proto.uncompressed) {
                PolarRuntimePlannerAdapter.restEventPayloads(uncompressed = true, proto.eventList.map { it.toByteArray() })
            } else {
                PolarRuntimePlannerAdapter.restEventPayloads(uncompressed = false, proto.eventList.map { it.toByteArray() })
            }.toTypedArray()
        }
}

fun BlePsFtpClient.receiveRestApiEvents(identifier: String): Flow<List<String>> {
    return receiveRestApiEventData(identifier)
        .map { array -> array.map { it.toString(Charsets.UTF_8) } }
        .onEach { item ->
            BleLogger.d(TAG, "Receive REST API events for $identifier emitted item: $item")
        }
        .catch { error ->
            BleLogger.d(TAG, "Receive REST API events for $identifier error occurred: ${error.message}")
            throw error
        }
}

/**
 * Parses string to REST API parameter JSON string to data class objects that subclass RestApiEventPayload
 */
inline fun <reified T : RestApiEventPayload> String.toObject(): T = Gson().fromJson(this, T::class.java)
