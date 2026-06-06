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
}
