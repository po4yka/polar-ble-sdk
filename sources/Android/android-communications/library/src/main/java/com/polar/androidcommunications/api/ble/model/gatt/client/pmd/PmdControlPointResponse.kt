package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.shared.pmd.PolarPmdControlPoint

class PmdControlPointResponse(data: ByteArray) {
    companion object {
        const val CONTROL_POINT_RESPONSE_CODE = 0xF0.toByte()
    }

    private val parsed = PolarPmdControlPoint.parseControlPointResponse(data).response ?: throw IndexOutOfBoundsException("invalidPMDData")

    val responseCode: Byte = parsed.responseCode.toByte()
    val opCode: PmdControlPointCommandClientToService = PmdControlPointCommandClientToService.values()[parsed.opCodeValue]
    val measurementType: Byte = parsed.measurementType.toByte()
    val status: PmdControlPointResponseCode = PmdControlPointResponseCode.values()[parsed.statusValue]
    val more: Boolean
    var parameters: ByteArray

    enum class PmdControlPointResponseCode(val numVal: Int) {
        SUCCESS(0),
        ERROR_INVALID_OP_CODE(1),
        ERROR_INVALID_MEASUREMENT_TYPE(2),
        ERROR_NOT_SUPPORTED(3),
        ERROR_INVALID_LENGTH(4),
        ERROR_INVALID_PARAMETER(5),
        ERROR_ALREADY_IN_STATE(6),
        ERROR_INVALID_RESOLUTION(7),
        ERROR_INVALID_SAMPLE_RATE(8),
        ERROR_INVALID_RANGE(9),
        ERROR_INVALID_MTU(10),
        ERROR_INVALID_NUMBER_OF_CHANNELS(11),
        ERROR_INVALID_STATE(12),
        ERROR_DEVICE_IN_CHARGER(13),
        ERROR_DISK_FULL(14);
    }

    init {
        more = parsed.more
        parameters = parsed.parameters
    }

    override fun toString(): String {
        return "\n" +
                "responseCode: ${"%02x".format(responseCode)}\n" +
                "opCode: $opCode\n" +
                "measurementType: $measurementType\n" +
                "status: $status\n" +
                "more: $more\n" +
                "parameters: ${parameters.joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }}"
    }
}
