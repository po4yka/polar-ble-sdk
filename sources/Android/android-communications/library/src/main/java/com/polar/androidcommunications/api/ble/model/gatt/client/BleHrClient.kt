package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import com.polar.shared.ble.PolarGattHrCodec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.util.*

/**
 * The `BleHrClient` class implements BLE Heart Rate client for receiving heart rate data from BLE Heart Rate service.
 *
 * This class implements the BLE Heart Rate client, which conforms to version 1.0 of the Heart Rate Service specification
 * defined by the Bluetooth Special Interest Group (SIG) at https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/
 *
 * The client receives RR-Interval data in 1/1024 milliseconds units. The RR-Interval data unit is defined in chapter 3.113.2 of
 * the GATT Specification Supplement v8
 *
 * @property txInterface The BLE GATT transmitter interface used to send and receive GATT requests and responses.
 */
class BleHrClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, HR_SERVICE) {
    companion object {
        private const val TAG = "BleHrClient"
        val BODY_SENSOR_LOCATION: UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val HR_SERVICE: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        const val HR_SERVICE_16BIT_UUID = "180D"
    }

    /**
     * Heart rate data received from BLE Heart Rate Service.
     *
     * @property hrValue heart rate in BPM (beats per minute)
     * @property sensorContact true if the sensor has contact (with a measurable surface e.g. skin)
     * @property energy the accumulated energy expended in kilo Joules since the last time it was reset.
     * @property rrs  list of RR-intervals represented by 1/1024 second as unit. The interval with index 0 is older then the interval with index 1.
     * @property rrsMs list of RRs in milliseconds. The interval with index 0 is older then the interval with index 1.
     * @property sensorContactSupported true if the sensor supports [sensorContact]
     * @property rrPresent: true if RR data is available in this sample
     */
    data class HrNotificationData(
        val hrValue: Int,
        val sensorContact: Boolean,
        val energy: Int,
        val rrs: List<Int>,
        val rrsMs: List<Int>,
        val sensorContactSupported: Boolean,
        val rrPresent: Boolean
    )

    private val hrObserverAtomicList = AtomicSet<Channel<HrNotificationData>>()

    init {
        addCharacteristicRead(BODY_SENSOR_LOCATION)
    }

    override fun reset() {
        super.reset()
        try {
            ChannelUtils.postDisconnectedAndClearList(hrObserverAtomicList)
        } catch (e: Exception) {
            BleLogger.d(TAG, "Failed to post disconnected to hr observers. Reason $e")
        }
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (data.isNotEmpty()) {
            BleLogger.d(TAG, "Processing service data. Status: " + status + ".  Data length: " + data.size)
            if (status == ATT_SUCCESS && characteristic == HR_MEASUREMENT) {
                val measurement = PolarGattHrCodec.parseHrMeasurement(data)
                ChannelUtils.emitNext(hrObserverAtomicList) { observer ->
                    observer.trySend(
                        HrNotificationData(
                            measurement.hr,
                            measurement.sensorContact,
                            measurement.energy,
                            measurement.rrs,
                            measurement.rrsMs,
                            measurement.sensorContactSupported,
                            measurement.rrPresent
                        )
                    )
                }
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        BleLogger.d(TAG, "Service data written not processed in BleHrClient")
    }

    override fun toString(): String {
        // and so on
        return "HR gatt client"
    }

    /**
     * @return Flow stream
     * Produces: onNext, for every hr notification event
     * onError, if client is not initially connected or ble disconnects
     */
    fun observeHrNotifications(checkConnection: Boolean): Flow<HrNotificationData> {
        return ChannelUtils.monitorNotifications(hrObserverAtomicList, txInterface, checkConnection)
            .onStart {
                BleLogger.d(TAG, "Start observing HR")
                addCharacteristicNotification(HR_MEASUREMENT)
                txInterface.setCharacteristicNotify(HR_SERVICE, HR_MEASUREMENT, true)
            }
            .onCompletion {
                BleLogger.d(TAG, "Stop observing HR")
                removeCharacteristicNotification(HR_MEASUREMENT)
                try {
                    txInterface.setCharacteristicNotify(HR_SERVICE, HR_MEASUREMENT, false)
                } catch (e: Exception) {
                    // this may happen if connection is already closed, no need sent the exception to downstream
                    BleLogger.d(TAG, "HR client is not able to set characteristic notify to false. Reason $e")
                }
            }
    }

    /**
     * @return Result<Unit>
     * failure if removing HR_MEASUREMENT notification fails
     */
    fun stopObserveHrNotifications(checkConnection: Boolean): Result<Unit> {
        BleLogger.d(TAG, "Stop observing HR")
        removeCharacteristicNotification(HR_MEASUREMENT)
        return try {
            txInterface.setCharacteristicNotify(HR_SERVICE, HR_MEASUREMENT, false)
            Result.success(Unit)
        } catch (e: Exception) {
            // this may happen if connection is already closed, no need sent the exception to downstream
            BleLogger.d(TAG, "HR client is not able to set characteristic notify to false. Reason $e")
            Result.failure(e)
        }
    }
}
