package com.polar.androidcommunications.api.ble.model.polar

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.DeviceCapabilities
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.DeviceCapabilitiesConfig
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.DefaultsSection
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Path

class BlePolarDeviceCapabilitiesUtilityTest {

    private lateinit var context: Context
    private lateinit var assets: AssetManager

    @Before
    fun resetCapabilitiesState() {
        context = mockk(relaxed = true)
        assets = mockk(relaxed = true)
        every { context.assets } returns assets

        mockkStatic(Environment::class)
        val docsDir = File(System.getProperty("java.io.tmpdir"), "BlePolarDeviceCapabilitiesUtilityTestDocs")
        docsDir.mkdirs()
        // Delete PolarConfig so the file is always recreated from the mock asset stream
        File(docsDir, "PolarConfig").deleteRecursively()
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) } returns docsDir

        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "test-reset",
                devices = emptyMap(),
                defaults = DefaultsSection(
                    fileSystemType = "UNKNOWN_FILE_SYSTEM",
                    recordingSupported = false,
                    firmwareUpdateSupported = false,
                    isDeviceSensor = false,
                    activityDataSupported = false
                )
            )
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getFileSystemType_whenResetDefaultsLoaded_returnsUnknown() {
        val result = BlePolarDeviceCapabilitiesUtility.getFileSystemType("h10")
        assertEquals(FileSystemType.UNKNOWN_FILE_SYSTEM, result)
    }

    @Test
    fun isRecordingSupported_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("h10"))
    }

    @Test
    fun isFirmwareUpdateSupported_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported("h10"))
    }

    @Test
    fun isDeviceSensor_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isDeviceSensor("h10"))
    }

    @Test
    fun isActivityDataSupported_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isActivityDataSupported("h10"))
    }

    @Test
    fun getFileSystemType_whenInitializedWithKnownTypes_mapsCorrectly() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = mapOf(
                    "h10" to DeviceCapabilities(fileSystemType = "H10_FILE_SYSTEM"),
                    "ignite3" to DeviceCapabilities(fileSystemType = "POLAR_FILE_SYSTEM_V2"),
                    "mystery" to DeviceCapabilities(fileSystemType = "SOMETHING_ELSE")
                ),
                defaults = DefaultsSection(fileSystemType = "POLAR_FILE_SYSTEM_V2")
            )
        )

        assertEquals(FileSystemType.H10_FILE_SYSTEM, BlePolarDeviceCapabilitiesUtility.getFileSystemType("h10"))
        assertEquals(FileSystemType.POLAR_FILE_SYSTEM_V2, BlePolarDeviceCapabilitiesUtility.getFileSystemType("ignite3"))
        assertEquals(FileSystemType.UNKNOWN_FILE_SYSTEM, BlePolarDeviceCapabilitiesUtility.getFileSystemType("mystery"))
    }

    @Test
    fun getFileSystemType_whenDeviceMissing_usesDefault() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = emptyMap(),
                defaults = DefaultsSection(fileSystemType = "H10_FILE_SYSTEM")
            )
        )

        val result = BlePolarDeviceCapabilitiesUtility.getFileSystemType("unknown-device")
        assertEquals(FileSystemType.H10_FILE_SYSTEM, result)
    }

    @Test
    fun booleanFlags_whenDevicePresent_useDeviceValues() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = mapOf(
                    "h10" to DeviceCapabilities(
                        recordingSupported = true,
                        firmwareUpdateSupported = false,
                        isDeviceSensor = true,
                        activityDataSupported = false
                    )
                ),
                defaults = DefaultsSection(
                    recordingSupported = false,
                    firmwareUpdateSupported = true,
                    isDeviceSensor = false,
                    activityDataSupported = true
                )
            )
        )

        assertTrue(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("h10"))
        assertFalse(BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported("h10"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isDeviceSensor("h10"))
        assertFalse(BlePolarDeviceCapabilitiesUtility.isActivityDataSupported("h10"))
    }

    @Test
    fun booleanFlags_whenDeviceMissing_useDefaults() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = emptyMap(),
                defaults = DefaultsSection(
                    recordingSupported = true,
                    firmwareUpdateSupported = false,
                    isDeviceSensor = true,
                    activityDataSupported = true
                )
            )
        )

        assertTrue(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("missing"))
        assertFalse(BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported("missing"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isDeviceSensor("missing"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isActivityDataSupported("missing"))
    }

    @Test
    fun deviceTypeLookup_isCaseInsensitive() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = mapOf(
                    "h10" to DeviceCapabilities(
                        fileSystemType = "H10_FILE_SYSTEM",
                        recordingSupported = true
                    )
                ),
                defaults = DefaultsSection(
                    fileSystemType = "POLAR_FILE_SYSTEM_V2",
                    recordingSupported = false
                )
            )
        )

        assertEquals(FileSystemType.H10_FILE_SYSTEM, BlePolarDeviceCapabilitiesUtility.getFileSystemType("H10"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("H10"))
    }

    @Test
    fun capabilityGoldenVectorsMatchAndroidBehavior() {
        loadCapabilityLookupVectors().forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val config = input.getAsJsonObject("config")
            val expectedResults = vector.getAsJsonObject("expected").getAsJsonArray("results")

            assertEquals("deviceCapabilityLookup", input.get("kind").asString)
            resetStateAndInitialize(capabilityConfigFrom(config))

            expectedResults.forEach { resultElement ->
                val expected = resultElement.asJsonObject
                val deviceType = expected.get("deviceType").asString
                assertEquals("$caseId filesystem $deviceType", expected.get("fileSystemType").asString, BlePolarDeviceCapabilitiesUtility.getFileSystemType(deviceType).name)
                assertEquals("$caseId recording $deviceType", expected.get("recordingSupported").asBoolean, BlePolarDeviceCapabilitiesUtility.isRecordingSupported(deviceType))
                expected.optionalBoolean("firmwareUpdateSupported")?.let { expectedFirmware ->
                    assertEquals("$caseId firmware $deviceType", expectedFirmware, BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported(deviceType))
                }
                expected.optionalBoolean("activityDataSupported")?.let { expectedActivity ->
                    assertEquals("$caseId activity $deviceType", expectedActivity, BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType))
                }
                expected.optionalBoolean("isDeviceSensor")?.let { expectedSensor ->
                    assertEquals("$caseId sensor $deviceType", expectedSensor, BlePolarDeviceCapabilitiesUtility.isDeviceSensor(deviceType))
                }
            }
        }
    }

    @Test
    fun capabilityMergeGoldenVectorMatchesAndroidBehavior() {
        val vector = loadCapabilityMergeVector()
        val caseId = vector.get("id").asString
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val expectedResults = expected.getAsJsonArray("results")

        assertEquals("deviceCapabilityConfigMerge", input.get("kind").asString)
        assertEquals("user-device-fields-win-missing-user-fields-fall-back-to-bundled-user-only-devices-survive-bundled-defaults-win", expected.get("mergePolicy").asString)
        resetStateAndInitialize(
            assetConfig = capabilityConfigFrom(input.getAsJsonObject("bundledConfig")),
            userConfig = capabilityConfigFrom(input.getAsJsonObject("userConfig"))
        )
        val mergedConfig = loadWrittenCapabilityConfig()
        assertEquals("$caseId merged version", expected.get("mergedVersion").asString, mergedConfig.version)
        resetStateAndInitialize(mergedConfig)

        expectedResults.forEach { resultElement ->
            val expectedResult = resultElement.asJsonObject
            val deviceType = expectedResult.get("deviceType").asString
            assertEquals("$caseId filesystem $deviceType", expectedResult.get("fileSystemType").asString, BlePolarDeviceCapabilitiesUtility.getFileSystemType(deviceType).name)
            assertEquals("$caseId recording $deviceType", expectedResult.get("recordingSupported").asBoolean, BlePolarDeviceCapabilitiesUtility.isRecordingSupported(deviceType))
            assertEquals("$caseId firmware $deviceType", expectedResult.get("firmwareUpdateSupported").asBoolean, BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported(deviceType))
            assertEquals("$caseId activity $deviceType", expectedResult.get("activityDataSupported").asBoolean, BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType))
            assertEquals("$caseId sensor $deviceType", expectedResult.get("isDeviceSensor").asBoolean, BlePolarDeviceCapabilitiesUtility.isDeviceSensor(deviceType))
        }
    }

    @Test
    fun capabilityLookupReadinessManifest_isPinnedBeforeCapabilityMigration() {
        val manifest = loadCapabilityLookupReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val expectedFamilies = listOf(
            "filesystem-type-mapping",
            "unknown-filesystem-default",
            "missing-device-defaults",
            "case-insensitive-device-type",
            "recording-support-defaults",
            "firmware-update-defaults",
            "activity-data-defaults",
            "sensor-device-defaults",
            "version-mismatch-user-config-merge",
            "resource-override-platform-ownership",
            "platform-capability-vector-references",
            "compile-verification-gate"
        )

        assertEquals("capability-lookup-readiness", manifest.get("id").asString)
        assertEquals("deviceCapabilityLookupReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/device-capabilities/capability-boolean-flags.json",
                "sdk/device-capabilities/capability-config-merge.json",
                "sdk/device-capabilities/capability-lookup-basic.json",
                "sdk/device-capabilities/capability-lookup-default-h10.json",
                "sdk/device-capabilities/capability-resource-override-ownership.json"
            ),
            input.getAsJsonArray("policyVectorPaths").map { it.asString }
        )
        assertEquals("coveredByPreMigrationCharacterization", expected.get("migrationReadiness").asString)
        assertEquals(expectedFamilies, input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString })
        assertEquals(expectedFamilies, expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString })
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtilityTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.DeviceCapabilitiesCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
        val resourceOwnership = loadCapabilityResourceOverrideVector()
        val resourceInput = resourceOwnership.getAsJsonObject("input")
        val resourceExpected = resourceOwnership.getAsJsonObject("expected")
        assertEquals("deviceCapabilityResourceOverrideOwnership", resourceInput.get("kind").asString)
        assertEquals("platformOwnedResourceBoundary", resourceExpected.get("migrationReadiness").asString)
        assertEquals(
            "Resource selection and app-level override precedence remain platform-owned until common resource loading is deliberately introduced with Android asset/external-file tests, iOS Bundle.main/SDK-bundle/sandbox tests, and shared common parser tests.",
            resourceExpected.get("platformDecision").asString
        )
    }

    private fun resetStateAndInitialize(config: DeviceCapabilitiesConfig) {
        val json = Gson().toJson(config)
        val utilityClass = BlePolarDeviceCapabilitiesUtility::class.java
        val initializedField = utilityClass.getDeclaredField("initialized")
        initializedField.isAccessible = true
        initializedField.setBoolean(null, false)

        // Delete PolarConfig so initialize() always writes + reads the fresh mock asset JSON
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        File(docsDir, "PolarConfig").deleteRecursively()

        // Return a fresh stream on every call — ByteArrayInputStream is exhausted after one read
        every { assets.open(any()) } answers { ByteArrayInputStream(json.toByteArray()) }
        BlePolarDeviceCapabilitiesUtility.initialize(context)
    }

    private fun resetStateAndInitialize(assetConfig: DeviceCapabilitiesConfig, userConfig: DeviceCapabilitiesConfig) {
        val utilityClass = BlePolarDeviceCapabilitiesUtility::class.java
        val initializedField = utilityClass.getDeclaredField("initialized")
        initializedField.isAccessible = true
        initializedField.setBoolean(null, false)

        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val configDir = File(docsDir, "PolarConfig")
        configDir.deleteRecursively()
        configDir.mkdirs()
        File(configDir, "polar_device_capabilities.json").writeText(Gson().toJson(userConfig))

        every { assets.open(any()) } answers { ByteArrayInputStream(Gson().toJson(assetConfig).toByteArray()) }
        BlePolarDeviceCapabilitiesUtility.initialize(context)
    }

    private fun loadWrittenCapabilityConfig(): DeviceCapabilitiesConfig {
        val configFile = File(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "PolarConfig"
            ),
            "polar_device_capabilities.json"
        )
        return Gson().fromJson(configFile.readText(), DeviceCapabilitiesConfig::class.java)
    }

    private fun capabilityConfigFrom(config: JsonObject): DeviceCapabilitiesConfig {
        val defaults = config.getAsJsonObject("defaults")
        val devices = config.getAsJsonObject("devices").entrySet().associate { (deviceType, value) ->
            val device = value.asJsonObject
            deviceType to DeviceCapabilities(
                fileSystemType = device.optionalString("fileSystemType"),
                recordingSupported = device.optionalBoolean("recordingSupported"),
                firmwareUpdateSupported = device.optionalBoolean("firmwareUpdateSupported"),
                activityDataSupported = device.optionalBoolean("activityDataSupported"),
                isDeviceSensor = device.optionalBoolean("isDeviceSensor")
            )
        }
        return DeviceCapabilitiesConfig(
            version = config.get("version").asString,
            devices = devices,
            defaults = DefaultsSection(
                fileSystemType = defaults.optionalString("fileSystemType") ?: "UNKNOWN_FILE_SYSTEM",
                recordingSupported = defaults.optionalBoolean("recordingSupported") ?: false,
                firmwareUpdateSupported = defaults.optionalBoolean("firmwareUpdateSupported") ?: false,
                activityDataSupported = defaults.optionalBoolean("activityDataSupported") ?: false,
                isDeviceSensor = defaults.optionalBoolean("isDeviceSensor") ?: false
            )
        )
    }

    private fun loadCapabilityLookupVectors(): List<JsonObject> {
        return listOf(
            "capability-boolean-flags.json",
            "capability-lookup-basic.json",
            "capability-lookup-default-h10.json"
        ).map { fileName ->
            Gson().fromJson(
                findRepositoryRoot()
                    .resolve("testdata/golden-vectors/sdk/device-capabilities")
                    .resolve(fileName)
                    .toFile()
                    .readText(),
                JsonObject::class.java
            )
        }
    }

    private fun loadCapabilityMergeVector(): JsonObject {
        return Gson().fromJson(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/device-capabilities/capability-config-merge.json")
                .toFile()
                .readText(),
            JsonObject::class.java
        )
    }

    private fun loadCapabilityResourceOverrideVector(): JsonObject {
        return Gson().fromJson(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/device-capabilities/capability-resource-override-ownership.json")
                .toFile()
                .readText(),
            JsonObject::class.java
        )
    }

    private fun loadCapabilityLookupReadinessManifest(): JsonObject {
        return Gson().fromJson(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/device-capabilities/capability-lookup-readiness.json")
                .toFile()
                .readText(),
            JsonObject::class.java
        )
    }

    private fun findRepositoryRoot(): Path {
        val startDirectory = File(System.getProperty("user.dir") ?: ".")
        return generateSequence(startDirectory) { file -> file.parentFile }
            .map { file -> file.toPath() }
            .first { path -> path.resolve("testdata/golden-vectors").toFile().isDirectory }
    }

    private fun JsonObject.optionalString(field: String): String? {
        return get(field)?.takeUnless { it.isJsonNull }?.asString
    }

    private fun JsonObject.optionalBoolean(field: String): Boolean? {
        return get(field)?.takeUnless { it.isJsonNull }?.asBoolean
    }
}
