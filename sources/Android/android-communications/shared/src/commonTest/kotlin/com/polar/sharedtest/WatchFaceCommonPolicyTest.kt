package com.polar.sharedtest

import com.polar.shared.sdk.PolarWatchFaceComplicationName
import com.polar.shared.sdk.PolarWatchFaceFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchFaceCommonPolicyTest {
    @Test
    fun watchFaceGoldenVectorsDefineExecutableCommonFieldComplicationAndMalformedPolicy() {
        WATCH_FACE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val expected = vector.objectValue("expected")
            val expectedFields = expected.objectValue("fields")
            val actual = if (vector.objectValue("input").optionalStringValue("flatBufferHex") != null) {
                parseMalformedOrDefault(vector.objectValue("input").stringValue("flatBufferHex"))
            } else {
                normalizeFields(vector.objectValue("input").objectValue("fields"))
            }

            assertEquals(expectedFields.intValue("timeStyleId"), actual.timeStyleId, "$caseId timeStyleId")
            assertEquals(expectedFields.intValue("complicationLayoutId"), actual.complicationLayoutId, "$caseId complicationLayoutId")
            assertEquals(expectedFields.intValue("backgroundStyleId"), actual.backgroundStyleId, "$caseId backgroundStyleId")
            assertEquals(expectedFields.intValue("accentColor").toLong(), actual.accentColor, "$caseId accentColor")
            assertEquals(expectedFields.intValue("fontfaceId"), actual.fontfaceId, "$caseId fontfaceId")
            assertEquals(expectedFields.intArrayValue("complicationIds"), actual.complicationIds, "$caseId complicationIds")

            expected.optionalStringArrayValue("knownComplications")?.let { expectedNames ->
                assertEquals(expectedNames, actual.complicationIds.map { id -> id.complicationName() }, "$caseId knownComplications")
            }
            expected.optionalObjectValue("kvtx")?.let { kvtx ->
                assertEquals(0, kvtx.intValue("firstOpcode"), "$caseId firstOpcode")
                assertEquals(5, kvtx.intValue("commitOpcode"), "$caseId commitOpcode")
                assertEquals(1064434511, kvtx.intValue("key"), "$caseId kvtx key")
            }
            vector.optionalObjectValue("commonDecision")?.let { decision ->
                assertEquals("preserve-raw-id-in-config-fields-and-return-null-for-enum-lookup", decision.stringValue("unknownComplicationPolicy"), caseId)
                assertEquals(null, actual.complicationIds.first().complicationNameOrNull(), "$caseId unknown complication lookup")
            }
        }
    }

    @Test
    fun watchFaceReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/watch-face/watch-face-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")
        assertEquals("watch-face-readiness", manifest.stringValue("id"))
        assertEquals("watchFaceReadiness", input.stringValue("kind"))
        assertEquals(WATCH_FACE_VECTORS, policyVectorPaths)
        assertEquals(requiredWatchFaceFamilies, requiredFamilies)
        assertEquals(requiredWatchFaceFamilies, coveredFamilies)
        assertEquals(WATCH_FACE_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarWatchFaceUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarWatchFaceUtilsTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.WatchFaceCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun normalizeFields(fields: String): PolarWatchFaceFields {
        return PolarWatchFaceFields.fromNullableFields(
            timeStyleId = fields.optionalIntValue("timeStyleId") ?: 0,
            complicationLayoutId = fields.optionalIntValue("complicationLayoutId") ?: 0,
            backgroundStyleId = fields.optionalIntValue("backgroundStyleId") ?: 0,
            accentColor = fields.optionalLongValue("accentColor"),
            complicationIds = fields.optionalIntArrayValue("complicationIds"),
            fontfaceId = fields.optionalIntValue("fontfaceId")
        )
    }

    private fun parseMalformedOrDefault(hex: String): PolarWatchFaceFields {
        return if (hexToBytes(hex).size < MINIMUM_FLATBUFFER_HEADER_SIZE) {
            PolarWatchFaceFields()
        } else {
            error("Only malformed default policy is covered in current shared common vectors")
        }
    }

    private fun Int.complicationName(): String {
        return complicationNameOrNull() ?: error("Unknown complication id $this")
    }

    private fun Int.complicationNameOrNull(): String? {
        return PolarWatchFaceComplicationName.fromId(this)?.name
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private fun String.optionalLongValue(field: String): Long? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong()
    }

    private fun String.optionalIntArrayValue(field: String): List<Int>? {
        return Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this)?.groupValues?.get(1)?.let { content ->
            Regex("-?\\d+").findAll(content).map { match -> match.value.toInt() }.toList()
        }
    }

    private fun String.intArrayValue(field: String): List<Int> {
        return optionalIntArrayValue(field) ?: error("Missing int array field $field")
    }

    private fun String.balancedEnd(start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until length) {
            val char = this[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = inString
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString && char == open) depth += 1
            if (!inString && char == close) {
                depth -= 1
                if (depth == 0) return index
            }
        }
        error("Unbalanced $open$close block")
    }

    private companion object {
        const val MINIMUM_FLATBUFFER_HEADER_SIZE = 4
        val WATCH_FACE_VECTORS = listOf(
            "sdk/watch-face/all-fields-with-complications.json",
            "sdk/watch-face/default-fields.json",
            "sdk/watch-face/malformed-too-short.json",
            "sdk/watch-face/ordered-complications-with-empty.json",
            "sdk/watch-face/unknown-complication-preserved.json"
        )
        val requiredWatchFaceFamilies = listOf(
            "default-field-zeroing",
            "scalar-field-round-trip",
            "complication-id-order-preservation",
            "empty-complication-id-preservation",
            "known-complication-lookup",
            "unknown-complication-raw-id-preservation",
            "unknown-complication-null-lookup-policy",
            "malformed-too-short-defaulting",
            "kvtx-wrapper-metadata",
            "platform-watch-face-vector-reference-gate",
            "compile-verification-gate"
        )
        const val WATCH_FACE_READINESS_COMMON_DECISION = "Watch-face model migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS watch-face tests continue to reference the same vectors, default fields, scalar fields, complication ordering, empty complication IDs, known complication lookup, unknown raw complication ID preservation with null enum lookup, malformed too-short defaulting, KVTX wrapper metadata, and the shared tests are compile-verified."
    }
}
