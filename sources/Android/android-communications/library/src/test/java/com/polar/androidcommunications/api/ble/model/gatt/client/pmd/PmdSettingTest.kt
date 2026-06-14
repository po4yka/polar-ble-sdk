package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting.PmdSettingType
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader

class PmdSettingTest {
    @Test
    fun testPmdSettingsWithRange() {
        //Arrange
        val bytes = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x34.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x02.toByte(), 0x04.toByte(),
            0xF5.toByte(), 0x00.toByte(), 0xF4.toByte(), 0x01.toByte(), 0xE8.toByte(), 0x03.toByte(), 0xD0.toByte(), 0x07.toByte(), 0x04.toByte(), 0x01.toByte(),
            0x03.toByte()
        )

        // Parameters
        // Setting Type : 00 (Sample Rate)
        // array_length : 01
        // array of settings values: 34 00 (52Hz)
        val sampleRate = 52
        //Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 10 00 (16)
        val resolution = 16
        // Setting Type : 02 (Range)
        // array_length : 04
        // array of settings values: F5 00 (245)
        val range1 = 245
        // array of settings values: F4 01 (500)
        val range2 = 500
        // array of settings values: E8 03 (1000)
        val range3 = 1000
        // array of settings values: D0 07 (2000)
        val range4 = 2000
        // Setting Type : 04 (Channels)
        // array_length : 01
        // array of settings values: 03 (3 Channels)
        val channels = 3
        val numberOfSettings = 4

        //Act
        val pmdSetting = PmdSetting(bytes)

        // Assert
        Assert.assertEquals(numberOfSettings, pmdSetting.settings.size)
        Assert.assertEquals(sampleRate, pmdSetting.settings[PmdSettingType.SAMPLE_RATE]!!.iterator().next())
        Assert.assertEquals(1, pmdSetting.settings[PmdSettingType.SAMPLE_RATE]!!.size)
        Assert.assertEquals(resolution, pmdSetting.settings[PmdSettingType.RESOLUTION]!!.iterator().next())
        Assert.assertEquals(1, pmdSetting.settings[PmdSettingType.RESOLUTION]!!.size)
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range1))
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range2))
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range3))
        Assert.assertTrue(pmdSetting.settings[PmdSettingType.RANGE]!!.contains(range4))
        Assert.assertEquals(4, pmdSetting.settings[PmdSettingType.RANGE]!!.size)
        Assert.assertEquals(channels, pmdSetting.settings[PmdSettingType.CHANNELS]!!.iterator().next())
        Assert.assertEquals(1, pmdSetting.settings[PmdSettingType.CHANNELS]!!.size)
        Assert.assertNull(pmdSetting.settings[PmdSettingType.RANGE_MILLIUNIT])
        Assert.assertNull(pmdSetting.settings[PmdSettingType.FACTOR])
    }

    @Test
    fun testPmdSettingWithRangeMilliUnit() {
        //Arrange
        val bytes = byteArrayOf(
            PmdSettingType.RANGE_MILLIUNIT.numVal.toByte(), 0x02.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            PmdSettingType.RESOLUTION.numVal.toByte(), 0x01.toByte(), 0x0E.toByte(), 0x00
        )
        // Parameters
        // Setting Type : 03 (Range milli unit)
        // array_length : 02
        // array of settings values: FF FF FF FF(52Hz)
        // array of settings values: FF 00 00 00(52Hz)
        // Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 0E 00 (16)
        val resolution = 14
        val numberOfSettings = 2

        // Act
        val settings = PmdSetting(bytes)

        // Assert
        Assert.assertEquals(numberOfSettings, settings.settings.size)
        Assert.assertTrue(settings.settings.containsKey(PmdSettingType.RANGE_MILLIUNIT))
        Assert.assertEquals(2, settings.settings[PmdSettingType.RANGE_MILLIUNIT]?.size)
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE_MILLIUNIT]!!.contains(-1))
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE_MILLIUNIT]!!.contains(0xff))
        Assert.assertTrue(settings.settings.containsKey(PmdSettingType.RESOLUTION))
        Assert.assertEquals(1, settings.settings[PmdSettingType.RESOLUTION]?.size)
        Assert.assertTrue(settings.settings[PmdSettingType.RESOLUTION]!!.contains(resolution))
    }

    @Test
    fun testPmdSelectedSerialization() {
        //Arrange
        val selected: MutableMap<PmdSettingType, Int> = mutableMapOf()
        val sampleRate = 0x7FFF
        selected[PmdSettingType.SAMPLE_RATE] = sampleRate
        val resolution = 0
        selected[PmdSettingType.RESOLUTION] = resolution
        val range = 15
        selected[PmdSettingType.RANGE] = range
        val rangeMilliUnit = Int.MAX_VALUE
        selected[PmdSettingType.RANGE_MILLIUNIT] = rangeMilliUnit
        val channels = 4
        selected[PmdSettingType.CHANNELS] = channels
        val factor = 15
        selected[PmdSettingType.FACTOR] = factor
        val numberOfSettings = 5

        //Act
        val settingsFromSelected = PmdSetting(selected)
        val serializedSelected = settingsFromSelected.serializeSelected()
        val settings = PmdSetting(serializedSelected)

        //Assert
        Assert.assertEquals(numberOfSettings, settings.settings.size)
        Assert.assertTrue(settings.settings[PmdSettingType.SAMPLE_RATE]!!.contains(sampleRate))
        Assert.assertEquals(1, settings.settings[PmdSettingType.SAMPLE_RATE]?.size)
        Assert.assertTrue(settings.settings[PmdSettingType.RESOLUTION]!!.contains(resolution))
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE]!!.contains(range))
        Assert.assertTrue(settings.settings[PmdSettingType.RANGE_MILLIUNIT]!!.contains(rangeMilliUnit))
        Assert.assertTrue(settings.settings[PmdSettingType.CHANNELS]!!.contains(channels))
        Assert.assertNull(settings.settings[PmdSettingType.FACTOR])
    }

    @Test
    fun pmdSettingGoldenVectors_matchAndroidBehavior() {
        val vectors = loadPmdSettingVectors()
        Assert.assertTrue("Expected PMD setting golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val caseId = vector.get("id").asString
            val androidExpectations = vector
                .getAsJsonObject("platformExpectations")
                ?.getAsJsonObject("android")
            if (input.has("selected")) {
                val selected = input.getAsJsonObject("selected").entrySet().associate { entry ->
                    PmdSettingType.valueOf(entry.key) to entry.value.asInt
                }
                val serialized = PmdSetting(selected).serializeSelected()
                val expectedSerializedHex = expected.get("serializedHex").asString
                Assert.assertEquals(caseId, expectedSerializedHex, serialized.toHexString())
                val settings = PmdSetting(serialized).settings
                expected.getAsJsonObject("settings")?.let { expectedSettings ->
                    assertSettings(caseId, expectedSettings, settings)
                }
                expected.getAsJsonArray("missingSettings")?.forEach { missing ->
                    Assert.assertNull(caseId, settings[PmdSettingType.valueOf(missing.asString)])
                }
                return@forEach
            }
            if (androidExpectations?.has("parseError") == true) {
                assertParseError(caseId, androidExpectations.get("parseError").asString, input.get("hex").asString.hexToByteArray())
                return@forEach
            }

            val settings = PmdSetting(input.get("hex").asString.hexToByteArray()).settings

            expected.getAsJsonObject("settings")?.let { expectedSettings ->
                assertSettings(caseId, expectedSettings, settings)
            }

            val androidSettings = androidExpectations?.getAsJsonObject("settings")
            androidSettings?.let {
                assertSettings(caseId, it, settings)
            }

            expected.getAsJsonArray("missingSettings")?.forEach { missing ->
                Assert.assertNull(caseId, settings[PmdSettingType.valueOf(missing.asString)])
            }
        }
    }

    @Test
    fun `pmd setting golden vectors follow neutral KMP vector shape`() {
        loadPmdSettingVectors().forEach { vector ->
            val id = vector.get("id")?.asString ?: "unknown-vector"

            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.has("android"))
            Assert.assertTrue(id, platforms.has("ios"))
            Assert.assertTrue(id, platforms.has("common"))
        }
    }

    @Test
    fun `PMD settings readiness manifest is pinned before parser migration`() {
        val manifest = loadPmdSettingsReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        Assert.assertEquals("pmd-settings-readiness", manifest.get("id").asString)
        Assert.assertEquals("pmdSettingsReadiness", input.get("kind").asString)
        Assert.assertEquals(PMD_SETTINGS_READINESS_POLICY_PATHS, policyPaths)
        Assert.assertEquals(PMD_SETTINGS_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(PMD_SETTINGS_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(PMD_SETTINGS_READINESS_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSettingTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PmdSettingTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.PmdSettingsCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private val PMD_SETTINGS_READINESS_POLICY_PATHS = listOf(
        "protocol/pmd/settings-basic-range.json",
        "protocol/pmd/settings-duplicate-sample-rate-factor.json",
        "protocol/pmd/settings-range-milliunit-platform-difference.json",
        "protocol/pmd/settings-security-value-platform-error.json",
        "protocol/pmd/settings-selected-serialization-max-values.json",
        "protocol/pmd/settings-truncated-resolution-platform-difference.json",
        "protocol/pmd/settings-unknown-type-platform-difference.json"
    )

    private val PMD_SETTINGS_READINESS_FAMILIES = listOf(
        "basic-settings-parsing",
        "duplicate-setting-overwrite",
        "factor-setting-parsing",
        "selected-setting-serialization",
        "selected-factor-skip-policy",
        "range-milliunit-signedness-platform-decision",
        "security-setting-platform-error-policy",
        "truncated-value-platform-decision",
        "unknown-setting-type-platform-decision",
        "platform-pmd-settings-vector-reference-gate",
        "compile-verification-gate"
    )

    private val PMD_SETTINGS_READINESS_DECISION = "PMD settings migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS PMD settings tests continue to reference the same vectors, baseline parsing, duplicate overwrite behavior, FACTOR parsing, selected-setting serialization, skipped FACTOR serialization, RANGE_MILLIUNIT signedness platform decisions, SECURITY setting parse policy, truncated-value policy, unknown-setting-type policy, and compile verification remain explicit before production PMD settings logic moves."

    private fun assertParseError(caseId: String, expectedError: String, data: ByteArray) {
        when (expectedError) {
            "indexOutOfBounds" -> Assert.assertThrows(caseId, IndexOutOfBoundsException::class.java) {
                PmdSetting(data)
            }
            "assertionError" -> Assert.assertThrows(caseId, AssertionError::class.java) {
                PmdSetting(data)
            }
            else -> Assert.fail("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    private fun assertSettings(
        caseId: String,
        expectedSettings: JsonObject,
        actualSettings: Map<PmdSettingType, Set<Int>>
    ) {
        expectedSettings.entrySet().forEach { entry ->
            val type = PmdSettingType.valueOf(entry.key)
            val actualValues = actualSettings[type] ?: emptySet()
            Assert.assertEquals(caseId, entry.value.asJsonArray.toIntSet(), actualValues)
        }
    }

    private fun loadPmdSettingVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/pmd")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("settings-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString != "pmdSettingsReadiness" }
    }

    private fun loadPmdSettingsReadinessManifest(): JsonObject {
        val manifestFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/pmd/settings-readiness.json")
        FileReader(manifestFile).use { reader ->
            return JsonParser.parseReader(reader).asJsonObject
        }
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    private fun JsonArray.toIntSet(): Set<Int> = map { it.asInt }.toSet()

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
}
