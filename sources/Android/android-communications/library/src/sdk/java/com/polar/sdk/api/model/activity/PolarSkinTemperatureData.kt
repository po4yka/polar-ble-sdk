package com.polar.sdk.api.model

import com.polar.services.datamodels.protobuf.TemperatureMeasurement.TemperatureMeasurementSample
import java.time.LocalDate

data class PolarSkinTemperatureData(val date: LocalDate? = null, val result: PolarSkinTemperatureResult? = null)

/**
 * TM_SKIN_TEMPERATURE, body temperature measured from skin surface
 * TM_CORE_TEMPERATURE, body temperature measured from inside a body
 */
enum class SkinTemperatureMeasurementType(val value: Int) {
    TM_UNKNOWN(0),
    TM_SKIN_TEMPERATURE(1),
    TM_CORE_TEMPERATURE(2);

    companion object {
        infix fun from(value: Int): SkinTemperatureMeasurementType? =
            PolarSdkModelAdapter.skinTemperatureMeasurementTypeName(value)?.toAndroidMeasurementType()
    }
}

/**
 * SL_DISTAL, sensor is located away from torso, for example on wrist
 * SL_PROXIMAL, sensor is located on torso, for example on chest
 */
enum class SkinTemperatureSensorLocation(val value: Int) {
    SL_UNKNOWN(0),
    SL_DISTAL(1),
    SL_PROXIMAL(2);

    companion object {
        infix fun from(value: Int): SkinTemperatureSensorLocation? =
            PolarSdkModelAdapter.skinTemperatureSensorLocationName(value)?.toAndroidSensorLocation()
    }
}

data class PolarSkinTemperatureResult(
    val deviceId: String?,
    val sensorLocation: SkinTemperatureSensorLocation?,
    val measurementType: SkinTemperatureMeasurementType?,
    val skinTemperatureList: List<PolarSkinTemperatureDataSample>?
)

data class PolarSkinTemperatureDataSample(
    val recordingTimeDeltaMs: Long,
    val temperature: Float
)

fun fromPbTemperatureMeasurementSamples(pbTemperatureMeasurementData: List<TemperatureMeasurementSample>):
        List<PolarSkinTemperatureDataSample> {
    return pbTemperatureMeasurementData.toPlannedSkinTemperatureSamples()
        .let(PolarSdkModelAdapter::skinTemperatureSamples)
        .toAndroidSkinTemperatureSamples()
}

private fun List<TemperatureMeasurementSample>.toPlannedSkinTemperatureSamples(): List<PolarSdkModelAdapter.PlannedSkinTemperatureSample> {
    return map { sample ->
        PolarSdkModelAdapter.PlannedSkinTemperatureSample(
            recordingTimeDeltaMs = sample.recordingTimeDeltaMilliseconds,
            temperature = sample.temperatureCelsius
        )
    }
}

internal fun sharedSkinTemperatureResult(
    sourceDeviceId: String?,
    measurementType: Int,
    sensorLocation: Int,
    samples: List<TemperatureMeasurementSample>
): PolarSkinTemperatureResult {
    val shared = PolarSdkModelAdapter.skinTemperature(
        sourceDeviceId = sourceDeviceId,
        measurementType = measurementType,
        sensorLocation = sensorLocation,
        samples = samples.toPlannedSkinTemperatureSamples()
    )
    return PolarSkinTemperatureResult(
        deviceId = shared.sourceDeviceId,
        sensorLocation = shared.sensorLocation?.toAndroidSensorLocation(),
        measurementType = shared.measurementType?.toAndroidMeasurementType(),
        skinTemperatureList = shared.samples.toAndroidSkinTemperatureSamples()
    )
}

private fun List<PolarSdkModelAdapter.PlannedSkinTemperatureSample>.toAndroidSkinTemperatureSamples(): List<PolarSkinTemperatureDataSample> {
    return map { sample ->
        PolarSkinTemperatureDataSample(
            recordingTimeDeltaMs = sample.recordingTimeDeltaMs,
            temperature = sample.temperature
        )
    }
}

private fun String.toAndroidMeasurementType(): SkinTemperatureMeasurementType {
    return when (this) {
        "TM_SKIN_TEMPERATURE" -> SkinTemperatureMeasurementType.TM_SKIN_TEMPERATURE
        "TM_CORE_TEMPERATURE" -> SkinTemperatureMeasurementType.TM_CORE_TEMPERATURE
        else -> error("Unknown skin temperature measurement type $this")
    }
}

private fun String.toAndroidSensorLocation(): SkinTemperatureSensorLocation {
    return when (this) {
        "SL_DISTAL" -> SkinTemperatureSensorLocation.SL_DISTAL
        "SL_PROXIMAL" -> SkinTemperatureSensorLocation.SL_PROXIMAL
        else -> error("Unknown skin temperature sensor location $this")
    }
}
