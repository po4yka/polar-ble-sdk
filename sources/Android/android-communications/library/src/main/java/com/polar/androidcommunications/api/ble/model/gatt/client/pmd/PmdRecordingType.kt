package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.shared.pmd.PolarPmdRecordingType

enum class PmdRecordingType(val numVal: UByte) {
    ONLINE(0u),
    OFFLINE(1u);

    fun asBitField(): UByte {
        return toSharedType().asBitField().toUByte()
    }

    companion object {
        private const val RECORDING_TYPE_BIT_MASK: UByte = 0x80u

        fun fromId(id: Byte): PmdRecordingType {
            for (type in values()) {
                if (type.numVal == (id.toUByte() and RECORDING_TYPE_BIT_MASK)) {
                    return type
                }
            }
            return ONLINE
        }
    }
}

private fun PmdRecordingType.toSharedType(): PolarPmdRecordingType {
    return PolarPmdRecordingType.valueOf(name)
}
