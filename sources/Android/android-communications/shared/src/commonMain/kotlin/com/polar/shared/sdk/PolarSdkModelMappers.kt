package com.polar.shared.sdk

data class PolarDiskSpaceModel(
    val totalSpace: Long,
    val freeSpace: Long
)

data class PolarSkinTemperatureModel(
    val sourceDeviceId: String?,
    val measurementType: PolarSkinTemperatureMeasurementType?,
    val sensorLocation: PolarSkinTemperatureSensorLocation?,
    val samples: List<PolarSkinTemperatureSampleModel>
)

data class PolarSkinTemperatureSampleModel(
    val recordingTimeDeltaMs: Long,
    val temperature: Float
)

enum class PolarSkinTemperatureMeasurementType {
    TM_SKIN_TEMPERATURE,
    TM_CORE_TEMPERATURE;

    companion object {
        fun fromValue(value: Int): PolarSkinTemperatureMeasurementType? {
            return when (value) {
                1 -> TM_SKIN_TEMPERATURE
                2 -> TM_CORE_TEMPERATURE
                else -> null
            }
        }
    }
}

enum class PolarSkinTemperatureSensorLocation {
    SL_DISTAL,
    SL_PROXIMAL;

    companion object {
        fun fromValue(value: Int): PolarSkinTemperatureSensorLocation? {
            return when (value) {
                1 -> SL_DISTAL
                2 -> SL_PROXIMAL
                else -> null
            }
        }
    }
}

object PolarSdkModelMappers {
    private const val UINT32_MASK = 0xFFFF_FFFFL
    private val AVAILABLE_PMD_DATA_TYPES = listOf(
        "ECG" to "ECG",
        "ACC" to "ACC",
        "PPG" to "PPG",
        "PPI" to "PPI",
        "GYRO" to "GYRO",
        "MAG" to "MAGNETOMETER",
        "PRESSURE" to "PRESSURE",
        "LOCATION" to "LOCATION",
        "TEMPERATURE" to "TEMPERATURE",
        "SKIN_TEMP" to "SKIN_TEMPERATURE"
    )

    fun skinTemperaturePath(day: String): String {
        return "/U/0/$day/SKINTEMP/TEMPCONT.BPB"
    }

    fun diskSpace(fragmentSize: Long, totalFragments: Long, freeFragments: Long): PolarDiskSpaceModel {
        val unsignedFragmentSize = fragmentSize and UINT32_MASK
        return PolarDiskSpaceModel(
            totalSpace = unsignedFragmentSize * totalFragments,
            freeSpace = unsignedFragmentSize * freeFragments
        )
    }

    fun availableOfflineRecordingDataTypeNames(
        pmdMeasurementTypeNames: Set<String>,
        includeLocation: Boolean = true,
        includePressure: Boolean = true
    ): Set<String> {
        return availablePmdDataTypeNames(
            pmdMeasurementTypeNames = pmdMeasurementTypeNames + if ("OFFLINE_HR" in pmdMeasurementTypeNames) setOf("HR") else emptySet(),
            includeHr = false,
            includeLocation = includeLocation,
            includePressure = includePressure
        )
    }

    fun availableOnlineStreamDataTypeNames(
        pmdMeasurementTypeNames: Set<String>,
        hasHrService: Boolean,
        includeLocation: Boolean = true,
        includePressure: Boolean = true
    ): Set<String> {
        return availablePmdDataTypeNames(
            pmdMeasurementTypeNames = pmdMeasurementTypeNames,
            includeHr = hasHrService,
            includeLocation = includeLocation,
            includePressure = includePressure
        )
    }

    fun skinTemperature(
        sourceDeviceId: String?,
        measurementType: Int,
        sensorLocation: Int,
        samples: List<PolarSkinTemperatureSampleModel>
    ): PolarSkinTemperatureModel {
        return PolarSkinTemperatureModel(
            sourceDeviceId = sourceDeviceId,
            measurementType = PolarSkinTemperatureMeasurementType.fromValue(measurementType),
            sensorLocation = PolarSkinTemperatureSensorLocation.fromValue(sensorLocation),
            samples = samples
        )
    }

    private fun availablePmdDataTypeNames(
        pmdMeasurementTypeNames: Set<String>,
        includeHr: Boolean,
        includeLocation: Boolean,
        includePressure: Boolean
    ): Set<String> {
        val result = linkedSetOf<String>()
        if (includeHr) {
            result += "HR"
        }
        AVAILABLE_PMD_DATA_TYPES.forEach { (measurementTypeName, publicDataTypeName) ->
            if (!includeLocation && publicDataTypeName == "LOCATION") return@forEach
            if (!includePressure && publicDataTypeName == "PRESSURE") return@forEach
            if (measurementTypeName in pmdMeasurementTypeNames) {
                result += publicDataTypeName
            }
        }
        if ("HR" in pmdMeasurementTypeNames) {
            result += "HR"
        }
        return result
    }
}
