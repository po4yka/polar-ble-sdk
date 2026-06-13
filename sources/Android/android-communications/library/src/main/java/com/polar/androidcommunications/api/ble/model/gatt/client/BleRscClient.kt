package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.BleLogger.Companion.w
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import com.polar.shared.ble.PolarGattRscCodec
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
                val measurement = PolarGattRscCodec.parseRscMeasurement(data)
                ChannelUtils.emitNext(observers) { observer ->
                    observer.trySend(
                        RscNotificationData(
                            measurement.strideLengthPresent,
                            measurement.totalDistancePresent,
                            measurement.running,
                            measurement.speedRaw,
                            measurement.cadence.toLong(),
                            measurement.strideLength,
                            measurement.totalDistanceAndroidRaw
                        )
                    )
                }
            } else if (characteristic == RSC_FEATURE) {
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
