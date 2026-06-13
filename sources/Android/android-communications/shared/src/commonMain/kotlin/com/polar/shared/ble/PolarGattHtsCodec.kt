package com.polar.shared.ble

import kotlin.math.pow
import kotlin.math.roundToInt

object PolarGattHtsCodec {
    data class TemperatureMeasurement(
        val temperatureCelsius: Float,
        val temperatureFahrenheit: Float,
        val isFahrenheit: Boolean,
        val exponent: Int,
        val mantissa: Int
    )

    private const val TEMP_ACCURACY: Int = 100

    fun parseTemperatureMeasurement(data: ByteArray): TemperatureMeasurement {
        val flags = data[0].toInt() and 0xFF
        val isFahrenheit = (flags and 0x01) != 0
        val mantissa = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8) or ((data[3].toInt() and 0xFF) shl 16)
        val exponent = data[4].toInt()
        val temperature = ((mantissa * 10.0.pow(exponent.toDouble()).toFloat() * TEMP_ACCURACY).roundToInt() / TEMP_ACCURACY.toFloat())
        val celsius = if (!isFahrenheit) temperature else (temperature - 32.0f) * 5.0f / 9.0f
        val fahrenheit = if (isFahrenheit) temperature else temperature * 9.0f / 5.0f + 32.0f
        return TemperatureMeasurement(celsius, fahrenheit, isFahrenheit, exponent, mantissa)
    }
}
