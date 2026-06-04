package com.polar.shared.pmd

data class PolarPmdActiveMeasurement(
    val activeBits: Int,
    val measurementBits: Int,
    val measurementTypeName: String,
    val androidStateName: String,
    val iosStateName: String
)

data class PolarPmdControlPointResponse(
    val responseCode: Int,
    val opCodeValue: Int,
    val opCodeName: String,
    val measurementType: Int,
    val measurementTypeName: String,
    val statusValue: Int,
    val statusName: String,
    val more: Boolean,
    val parameters: ByteArray
) {
    val parametersHex: String = parameters.toHex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PolarPmdControlPointResponse) return false
        return responseCode == other.responseCode &&
            opCodeValue == other.opCodeValue &&
            opCodeName == other.opCodeName &&
            measurementType == other.measurementType &&
            measurementTypeName == other.measurementTypeName &&
            statusValue == other.statusValue &&
            statusName == other.statusName &&
            more == other.more &&
            parameters.contentEquals(other.parameters)
    }

    override fun hashCode(): Int {
        var result = responseCode
        result = 31 * result + opCodeValue
        result = 31 * result + opCodeName.hashCode()
        result = 31 * result + measurementType
        result = 31 * result + measurementTypeName.hashCode()
        result = 31 * result + statusValue
        result = 31 * result + statusName.hashCode()
        result = 31 * result + more.hashCode()
        result = 31 * result + parameters.contentHashCode()
        return result
    }
}

data class PolarPmdControlPointParseResult(
    val response: PolarPmdControlPointResponse? = null,
    val error: PolarPmdParseError? = null
) {
    fun requireResponse(): PolarPmdControlPointResponse {
        return response ?: throw IllegalArgumentException(error?.vectorName ?: "invalidPMDData")
    }
}

object PolarPmdControlPoint {
    private const val SUCCESS_STATUS = 0

    fun parseActiveMeasurement(responseByte: Int): PolarPmdActiveMeasurement {
        val activeBits = (responseByte shr 6) and 0x03
        val measurementBits = responseByte and 0x3F
        return PolarPmdActiveMeasurement(
            activeBits = activeBits,
            measurementBits = measurementBits,
            measurementTypeName = measurementBits.measurementName(),
            androidStateName = activeBits.androidActiveStateName(),
            iosStateName = activeBits.iosActiveStateName()
        )
    }

    fun parseControlPointResponse(data: ByteArray): PolarPmdControlPointParseResult {
        if (data.size < 4) {
            return PolarPmdControlPointParseResult(error = PolarPmdParseError.InvalidPmdData)
        }
        val statusValue = data[3].unsigned()
        val parameters = if (statusValue == SUCCESS_STATUS && data.size > 5) {
            data.copyOfRange(5, data.size)
        } else {
            byteArrayOf()
        }
        return PolarPmdControlPointParseResult(
            response = PolarPmdControlPointResponse(
                responseCode = data[0].unsigned(),
                opCodeValue = data[1].unsigned(),
                opCodeName = data[1].unsigned().opCodeName(),
                measurementType = data[2].unsigned(),
                measurementTypeName = data[2].unsigned().measurementName(),
                statusValue = statusValue,
                statusName = statusValue.statusName(),
                more = statusValue == SUCCESS_STATUS && data.size > 4 && data[4].toInt() != 0,
                parameters = parameters
            )
        )
    }
}

private fun Byte.unsigned(): Int {
    return toInt() and 0xFF
}

private fun Int.opCodeName(): String {
    return when (this) {
        1 -> "GET_MEASUREMENT_SETTINGS"
        2 -> "REQUEST_MEASUREMENT_START"
        3 -> "STOP_MEASUREMENT"
        4 -> "GET_SDK_MODE_MEASUREMENT_SETTINGS"
        5 -> "GET_MEASUREMENT_STATUS"
        6 -> "GET_SDK_MODE_STATUS"
        7 -> "GET_OFFLINE_RECORDING_TRIGGER_STATUS"
        8 -> "SET_OFFLINE_RECORDING_TRIGGER_MODE"
        9 -> "SET_OFFLINE_RECORDING_TRIGGER_SETTINGS"
        else -> "UNKNOWN"
    }
}

private fun Int.measurementName(): String {
    return when (this) {
        0 -> "ECG"
        1 -> "PPG"
        2 -> "ACC"
        3 -> "PPI"
        5 -> "GYRO"
        6 -> "MAG"
        7 -> "SKIN_TEMP"
        9 -> "SDK_MODE"
        10 -> "LOCATION"
        11 -> "PRESSURE"
        12 -> "TEMPERATURE"
        13 -> "OFFLINE_RECORDING"
        14 -> "OFFLINE_HR"
        else -> "UNKNOWN"
    }
}

private fun Int.statusName(): String {
    return when (this) {
        0 -> "SUCCESS"
        1 -> "ERROR_INVALID_OP_CODE"
        2 -> "ERROR_INVALID_MEASUREMENT_TYPE"
        3 -> "ERROR_NOT_SUPPORTED"
        4 -> "ERROR_INVALID_LENGTH"
        5 -> "ERROR_INVALID_PARAMETER"
        6 -> "ERROR_ALREADY_IN_STATE"
        7 -> "ERROR_INVALID_RESOLUTION"
        8 -> "ERROR_INVALID_SAMPLE_RATE"
        9 -> "ERROR_INVALID_RANGE"
        10 -> "ERROR_INVALID_MTU"
        11 -> "ERROR_INVALID_NUMBER_OF_CHANNELS"
        12 -> "ERROR_INVALID_STATE"
        13 -> "ERROR_DEVICE_IN_CHARGER"
        14 -> "ERROR_DISK_FULL"
        else -> "UNKNOWN"
    }
}

private fun Int.androidActiveStateName(): String {
    return when (this) {
        0 -> "NO_ACTIVE_MEASUREMENT"
        1 -> "ONLINE_MEASUREMENT_ACTIVE"
        2 -> "OFFLINE_MEASUREMENT_ACTIVE"
        3 -> "ONLINE_AND_OFFLINE_ACTIVE"
        else -> "UNKNOWN_MEASUREMENT_STATUS"
    }
}

private fun Int.iosActiveStateName(): String {
    return when (this) {
        0 -> "no_measurement_active"
        1 -> "online_measurement_active"
        2 -> "offline_measurement_active"
        3 -> "online_offline_measurement_active"
        else -> "unknown"
    }
}

private fun ByteArray.toHex(): String {
    return joinToString(separator = "") { byte ->
        val value = byte.toInt() and 0xFF
        "${(value / 16).toHexDigit()}${(value % 16).toHexDigit()}"
    }
}

private fun Int.toHexDigit(): Char {
    return if (this < 10) '0' + this else 'a' + (this - 10)
}
