package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class GnssLocationOwnershipCommonPolicyTest {
    @Test
    fun gnssLocationGoldenVectorsPinAndroidOwnedParserPolicyBeforeCommonMigration() {
        GNSS_LOCATION_ANDROID_OWNED_VECTORS.forEach { vectorCase ->
            val vector = loadGoldenVectorText(vectorCase.relativePath)
            val expected = vector.objectValue("expected")
            val platforms = vector.objectValue("platforms")
            val sample = expected.objectArray("samples").single()
            val header = parseSensorHeader(vector.objectValue("input").stringValue("dataFrameHex"))

            assertEquals(vectorCase.caseId, vector.stringValue("id"), vectorCase.relativePath)
            assertEquals("LOCATION", expected.stringValue("measurementType"), vectorCase.caseId)
            assertEquals(10, header.measurementType, vectorCase.caseId)
            assertEquals(vectorCase.frameType, expected.intValue("frameType"), vectorCase.caseId)
            assertEquals(vectorCase.frameType, header.frameType, vectorCase.caseId)
            assertEquals(false, expected.booleanValue("compressed"), vectorCase.caseId)
            assertEquals(false, header.compressed, vectorCase.caseId)
            assertEquals(vectorCase.sampleKind, sample.stringValue("sampleKind"), vectorCase.caseId)
            assertEquals(PROTOCOL_ONLY_MIGRATION_OWNERSHIP, expected.stringValue("migrationOwnership"), vectorCase.caseId)
            assertEquals(true, platforms.booleanValue("android"), "${vectorCase.caseId} android ownership")
            assertEquals(false, platforms.booleanValue("ios"), "${vectorCase.caseId} ios ownership")
            assertEquals(false, platforms.booleanValue("common"), "${vectorCase.caseId} common ownership")
        }
    }

    @Test
    fun gnssLocationReadinessManifestNamesEveryPreMigrationOwnershipFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/gnss-location-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredBehaviorFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredBehaviorFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("gnss-location-readiness", manifest.stringValue("id"))
        assertEquals("gnssLocationReadiness", input.stringValue("kind"))
        assertEquals("androidOwnedPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(REQUIRED_GNSS_LOCATION_OWNERSHIP_FAMILIES, requiredBehaviorFamilies)
        assertEquals(REQUIRED_GNSS_LOCATION_OWNERSHIP_FAMILIES, coveredBehaviorFamilies)
        assertEquals(GNSS_LOCATION_ANDROID_OWNED_VECTORS.map { it.relativePath }, policyVectorPaths)
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GnssLocationDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(false, consumerTests.hasStringArray("ios"))
        assertEquals(listOf("com.polar.sharedtest.GnssLocationOwnershipCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseSensorHeader(hex: String): GnssHeader {
        val bytes = hexToBytes(hex)
        require(bytes.size >= HEADER_SIZE) { "GNSS vector must include a PMD header" }
        val rawFrameType = bytes[9].toInt() and 0xFF
        return GnssHeader(
            measurementType = bytes[0].toInt() and 0xFF,
            frameType = rawFrameType and 0x7F,
            compressed = (rawFrameType and 0x80) != 0
        )
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val values = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: error("Missing array field $field in $this")
        return Regex("\"([^\"]+)\"").findAll(values).map { match -> match.groupValues[1] }.toList()
    }

    private fun String.hasStringArray(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*\\[").containsMatchIn(this)
    }

    private data class GnssVectorCase(
        val relativePath: String,
        val caseId: String,
        val frameType: Int,
        val sampleKind: String
    )

    private data class GnssHeader(
        val measurementType: Int,
        val frameType: Int,
        val compressed: Boolean
    )

    private companion object {
        const val HEADER_SIZE = 10
        val REQUIRED_GNSS_LOCATION_OWNERSHIP_FAMILIES = listOf(
            "android-owned-raw-type0-coordinate",
            "android-owned-raw-type1-satellite-dilution",
            "android-owned-raw-type2-satellite-summary",
            "android-owned-raw-type3-nmea",
            "non-ios-parser-ownership",
            "non-common-parser-ownership",
            "future-shared-parser-parity-gate",
            "compile-verification-gate"
        )
        val GNSS_LOCATION_ANDROID_OWNED_VECTORS = listOf(
            GnssVectorCase("protocol/sensors/gnss-location-raw-type0-coordinate.json", "gnss-location-raw-type0-coordinate", 0, "coordinate"),
            GnssVectorCase("protocol/sensors/gnss-location-raw-type1-satellite-dilution.json", "gnss-location-raw-type1-satellite-dilution", 1, "satelliteDilution"),
            GnssVectorCase("protocol/sensors/gnss-location-raw-type2-satellite-summary.json", "gnss-location-raw-type2-satellite-summary", 2, "satelliteSummary"),
            GnssVectorCase("protocol/sensors/gnss-location-raw-type3-nmea.json", "gnss-location-raw-type3-nmea", 3, "nmea")
        )
    }
}
