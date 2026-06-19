package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.BleLogger.Companion.w
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BleRscClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, RSC_SERVICE) {
    class RscNotificationData(
        var strideLengthPresent: Boolean,
        var totalDistancePresent: Boolean,
        var running: Boolean,
        var speed: Long,
        var cadence: Long,
        var strideLength: Long,
        var totalDistance: Long
    )

    private val observers = AtomicSet<Channel<RscNotificationData>>()

    init {
        addCharacteristicNotification(RSC_MEASUREMENT)
        addCharacteristicRead(RSC_FEATURE)
    }

    override fun reset() {
        super.reset()
        ChannelUtils.postDisconnectedAndClearList(observers)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (status == ATT_SUCCESS && data.isNotEmpty()) {
            if (characteristic == RSC_MEASUREMENT) {
                // Bluetooth RSC flags are packed into the first byte.
                // Mandatory fields: flags (1) + speed (2) + cadence (1) = 4 bytes minimum.
                if (data.size < 4) {
                    w(TAG, "RSC measurement packet too short (${data.size} bytes), skipped")
                    return
                }
                var index = 0
                val flags = data[index++].toLong()
                val strideLenPresent = (flags and 0x01L) == 0x01L
                val totalDistancePresent = (flags and 0x02L) == 0x02L
                val running = (flags and 0x04L) == 0x04L

                val speed = ((data[index++].toInt() and 0xFF) or ((data[index++].toInt() and 0xFF) shl 8)).toLong()
                val cadence = (data[index++].toInt() and 0xFF).toLong()

                var strideLength: Long = 0
                var totalDistance: Long = 0

                if (strideLenPresent) {
                    if (data.size < index + 2) {
                        w(TAG, "RSC measurement packet too short for stride length (${data.size} bytes), skipped")
                        return
                    }
                    strideLength = ((data[index++].toInt() and 0xFF) or ((data[index++].toInt() and 0xFF) shl 8)).toLong()
                }

                if (totalDistancePresent) {
                    if (data.size < index + 4) {
                        w(TAG, "RSC measurement packet too short for total distance (${data.size} bytes), skipped")
                        return
                    }
                    totalDistance = ((data[index++].toInt() and 0xFF) or ((data[index++].toInt() and 0xFF) shl 8) or ((data[index++].toInt() and 0xFF) shl 16) or ((data[index].toInt() and 0xFF) shl 24)).toLong()
                }

                val finalStrideLength = strideLength
                val finalTotalDistance = totalDistance
                ChannelUtils.emitNext(observers) { observer ->
                    observer.trySend(
                        RscNotificationData(
                            strideLenPresent,
                            totalDistancePresent,
                            running,
                            speed,
                            cadence,
                            finalStrideLength,
                            finalTotalDistance
                        )
                    )
                }
            } else if (characteristic == RSC_FEATURE) {
                if (data.size < 2) {
                    w(TAG, "RSC feature packet too short (${data.size} bytes), skipped")
                    return
                }
                val feature = (data[0].toInt() or (data[1].toInt() shl 8)).toLong()
                d(TAG, "RSC Feature Characteristic read: $feature")
            }
        } else {
            w(
                TAG,
                "Process service data with status $status, skipped"
            )
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // do  nothing
    }

    override fun toString(): String {
        return "RSC service "
    }

    override suspend fun clientReady(checkConnection: Boolean) {
        waitNotificationEnabled(RSC_MEASUREMENT, checkConnection)
    }

    // API
    /**
     * @return Flow stream of RscNotificationData
     * Produces: onNext for every Rsc notification event
     * onError for Interrupted mutex wait
     * onCompleted none except further configuration applied. If bound to fragment or activity life cycle this might be produced
     */
    fun monitorRscNotifications(): Flow<RscNotificationData> {
        return ChannelUtils.monitorNotifications(observers, txInterface, true)
    }

    companion object {
        private val TAG: String = BleRscClient::class.java.simpleName

        val RSC_FEATURE: UUID = UUID.fromString("00002a54-0000-1000-8000-00805f9b34fb")
        val RSC_MEASUREMENT: UUID = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb")
        val RSC_SERVICE: UUID = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
    }
}
