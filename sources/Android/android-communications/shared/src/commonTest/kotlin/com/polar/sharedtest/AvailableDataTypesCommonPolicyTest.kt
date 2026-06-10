package com.polar.sharedtest

import com.polar.shared.sdk.PolarSdkModelMappers
import kotlin.test.Test
import kotlin.test.assertEquals

class AvailableDataTypesCommonPolicyTest {
    @Test
    fun offlineRecordingAvailabilityMapsPmdNamesToPublicDataTypesWithPlatformFilters() {
        val pmdTypes = setOf("ECG", "ACC", "PPG", "PPI", "GYRO", "MAG", "PRESSURE", "LOCATION", "TEMPERATURE", "SKIN_TEMP", "OFFLINE_HR")

        assertEquals(
            setOf("ECG", "ACC", "PPG", "PPI", "GYRO", "MAGNETOMETER", "PRESSURE", "LOCATION", "TEMPERATURE", "SKIN_TEMPERATURE", "HR"),
            PolarSdkModelMappers.availableOfflineRecordingDataTypeNames(pmdTypes)
        )
        assertEquals(
            setOf("ECG", "ACC", "PPG", "PPI", "GYRO", "MAGNETOMETER", "TEMPERATURE", "SKIN_TEMPERATURE", "HR"),
            PolarSdkModelMappers.availableOfflineRecordingDataTypeNames(pmdTypes, includeLocation = false, includePressure = false)
        )
    }

    @Test
    fun onlineStreamAvailabilityMergesPlatformHrServiceWithPmdFeatures() {
        val pmdTypes = setOf("ECG", "ACC", "PPG", "PPI", "GYRO", "MAG", "PRESSURE", "LOCATION", "TEMPERATURE", "SKIN_TEMP", "OFFLINE_HR")

        assertEquals(
            setOf("HR", "ECG", "ACC", "PPG", "PPI", "GYRO", "MAGNETOMETER", "PRESSURE", "LOCATION", "TEMPERATURE", "SKIN_TEMPERATURE"),
            PolarSdkModelMappers.availableOnlineStreamDataTypeNames(pmdTypes, hasHrService = true)
        )
        assertEquals(
            setOf("ECG", "ACC", "PPG", "PPI", "GYRO", "MAGNETOMETER", "PRESSURE", "TEMPERATURE", "SKIN_TEMPERATURE"),
            PolarSdkModelMappers.availableOnlineStreamDataTypeNames(pmdTypes, hasHrService = false, includeLocation = false, includePressure = true)
        )
    }

    @Test
    fun hrServiceAvailabilityProjectsOnlyDiscoveredHrService() {
        assertEquals(setOf("HR"), PolarSdkModelMappers.availableHrServiceDataTypeNames(hasHrService = true))
        assertEquals(emptySet(), PolarSdkModelMappers.availableHrServiceDataTypeNames(hasHrService = false))
    }

    @Test
    fun publicDataTypeNamesMapToSharedPmdMeasurementTypeNames() {
        val cases = mapOf(
            "ECG" to "ECG",
            "ACC" to "ACC",
            "PPG" to "PPG",
            "PPI" to "PPI",
            "GYRO" to "GYRO",
            "MAGNETOMETER" to "MAG",
            "PRESSURE" to "PRESSURE",
            "LOCATION" to "LOCATION",
            "TEMPERATURE" to "TEMPERATURE",
            "SKIN_TEMPERATURE" to "SKIN_TEMP",
            "HR" to "OFFLINE_HR"
        )

        cases.forEach { (publicDataType, measurementType) ->
            assertEquals(measurementType, PolarSdkModelMappers.pmdMeasurementTypeNameForPublicDataTypeName(publicDataType), publicDataType)
        }
        assertEquals(null, PolarSdkModelMappers.pmdMeasurementTypeNameForPublicDataTypeName("UNKNOWN"))
    }
}
