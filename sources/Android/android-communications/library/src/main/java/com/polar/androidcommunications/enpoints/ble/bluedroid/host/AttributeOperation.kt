package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothGattCharacteristic

class AttributeOperation : Comparable<AttributeOperation> {
    override fun compareTo(another: AttributeOperation): Int {
        return this.priority().compareTo(another.priority())
    }

    private fun priority(): Int {
        return when {
            attributeOperation == AttributeOperationCommand.DESCRIPTOR_WRITE && isPartOfPrimaryService -> 0
            attributeOperation == AttributeOperationCommand.DESCRIPTOR_WRITE -> 1
            else -> 2
        }
    }

    enum class AttributeOperationCommand {
        CHARACTERISTIC_READ,
        CHARACTERISTIC_WRITE,
        DESCRIPTOR_WRITE,
        CHARACTERISTIC_NOTIFY // add more later
    }

    @JvmField
    val attributeOperation: AttributeOperationCommand
    private lateinit var data: ByteArray
    @JvmField
    val characteristic: BluetoothGattCharacteristic
    @JvmField
    var isPartOfPrimaryService = false

    // notification or indication
    var isEnable: Boolean = false
        private set
    var isWithResponse: Boolean = false
        private set

    internal constructor(
        attributeOperation: AttributeOperationCommand,
        characteristic: BluetoothGattCharacteristic
    ) {
        this.attributeOperation = attributeOperation
        this.characteristic = characteristic
    }

    internal constructor(
        attributeOperation: AttributeOperationCommand,
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ) {
        this.attributeOperation = attributeOperation
        this.characteristic = characteristic
        this.isEnable = enable
    }

    internal constructor(
        attributeOperation: AttributeOperationCommand,
        data: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        withResponse: Boolean
    ) {
        this.attributeOperation = attributeOperation
        this.data = data
        this.characteristic = characteristic
        this.isWithResponse = withResponse
    }

    fun getData(): ByteArray? {
        return data
    }
}