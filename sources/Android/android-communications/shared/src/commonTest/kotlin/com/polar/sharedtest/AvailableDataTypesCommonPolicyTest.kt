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

    @Test
    fun availableDataTypesReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/available-data-types/available-data-types-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")

        assertEquals("available-data-types-readiness", manifest.stringValue("id"))
        assertEquals("availableDataTypesReadiness", input.stringValue("kind"))
        assertEquals(AVAILABLE_DATA_TYPES_READINESS_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals("coveredBySharedContractCharacterization", expected.stringValue("sharedOwnershipStatus"))
        assertEquals(AVAILABLE_DATA_TYPES_READINESS_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(AVAILABLE_DATA_TYPES_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        val prototype = expected.objectValue("commonRuntimePrototype")
        assertEquals("executable shared commonTest available-data-types planning guard", prototype.stringValue("status"))
        assertEquals("Declared because this vector is consumed by shared commonTest and platform adapter tests before available-data-types runtime delegation moves further into shared.", prototype.stringValue("reason"))
        assertEquals(listOf("com.polar.sdk.impl.utils.PolarRuntimePlannerAdapterTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDataUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.AvailableDataTypesCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private companion object {
        val AVAILABLE_DATA_TYPES_READINESS_FAMILIES = listOf(
            "offline-pmd-to-public-mapping",
            "online-pmd-to-public-mapping",
            "hr-service-availability-projection",
            "ios-location-pressure-filter-boundary",
            "android-full-surface-boundary",
            "public-to-pmd-measurement-lookup",
            "unknown-public-type-null-boundary",
            "pmd-feature-read-platform-boundary",
            "hr-service-discovery-platform-boundary",
            "public-error-mapping-boundary",
            "platform-available-data-type-vector-reference-gate",
            "compile-verification-gate"
        )
        const val AVAILABLE_DATA_TYPES_READINESS_COMMON_DECISION = "Available-data-types shared ownership remains valid while this readiness manifest is executable from shared commonTest, Android and iOS data utility tests continue to pin offline and online PMD-to-public mapping, HR-service availability projection, iOS location/pressure filters, Android full public surface, public-to-PMD measurement lookup, unknown public type boundaries, PMD feature-read boundaries, HR-service discovery boundaries, public error mapping boundaries, platform vector references, and compile verification before broader availability facade behavior moves."
    }
}
