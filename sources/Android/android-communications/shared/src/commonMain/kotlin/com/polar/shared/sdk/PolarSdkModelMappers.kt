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
    TM_CORE_TEMPERATURE
}

enum class PolarSkinTemperatureSensorLocation {
    SL_DISTAL,
    SL_PROXIMAL
}

object PolarSdkModelMappers {
    private const val UINT32_MASK = 0xFFFF_FFFFL

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
            measurementType = when (measurementType) {
                1 -> PolarSkinTemperatureMeasurementType.TM_SKIN_TEMPERATURE
                2 -> PolarSkinTemperatureMeasurementType.TM_CORE_TEMPERATURE
                else -> null
            },
            sensorLocation = when (sensorLocation) {
                1 -> PolarSkinTemperatureSensorLocation.SL_DISTAL
                2 -> PolarSkinTemperatureSensorLocation.SL_PROXIMAL
                else -> null
            },
            samples = samples
        )
    }
}
