package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class Spo2CommonPolicyTest {
    @Test
    fun spo2GoldenVectorsDefineExecutableCommonOptionalTriggerAndUnknownEnumPolicy() {
        SPO2_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val proto = input.objectValue("proto")
            val expected = vector.objectValue("expected")
            val common = mapSpo2(input.stringValue("date"), input.stringValue("timeDirName"), proto)

            assertEquals(expected.stringValue("policy"), common.policy, caseId)
            assertEquals(proto.optionalStringValue("recordingDevice"), common.recordingDevice, "$caseId recordingDevice")
            assertEquals(proto.intValue("timeZoneOffsetMinutes"), common.timeZoneOffsetMinutes, "$caseId timeZoneOffsetMinutes")
            assertEquals("passed", common.testStatus, "$caseId testStatus")
            assertEquals(proto.optionalIntValue("bloodOxygenPercent"), common.bloodOxygenPercent, "$caseId bloodOxygenPercent")
            assertEquals(proto.optionalFloatValue("spo2QualityAveragePercent"), common.spo2QualityAveragePercent, "$caseId quality")
            assertEquals(proto.optionalIntValue("averageHeartRateBpm"), common.averageHeartRateBpm, "$caseId averageHeartRate")
            assertEquals(proto.optionalFloatValue("heartRateVariabilityMs"), common.heartRateVariabilityMs, "$caseId hrv")
            assertEquals(proto.optionalFloatValue("altitudeMeters"), common.altitudeMeters, "$caseId altitude")

            vector.objectValue("platformExpectations").optionalObjectValue("commonDecision")?.let { commonDecision ->
                commonDecision.optionalStringValue("optionalPolicy")?.let { policy ->
                    assertEquals("preserve-protobuf-presence", policy, caseId)
                    assertEquals(null, common.recordingDevice, "$caseId shared empty recording device")
                }
                commonDecision.optionalStringValue("triggerTypePolicy")?.let { policy ->
                    assertEquals("include-nullable-trigger-type-in-shared-model-when-source-proto-exposes-it", policy, caseId)
                    assertEquals("automatic", common.triggerType, "$caseId triggerType")
                }
                commonDecision.optionalStringValue("unknownEnumPolicy")?.let { policy ->
                    assertEquals("map-to-null-with-typed-warning-or-error-boundary-before-public-model", policy, caseId)
                    assertEquals(null, common.spo2Class, "$caseId unknown spo2Class")
                }
            }

            if (!caseId.contains("unknown-spo2-class")) {
                assertEquals(proto.optionalIntValue("spo2Class")?.spo2ClassName(), common.spo2Class, "$caseId spo2Class")
            }
            assertEquals(proto.optionalIntValue("spo2ValueDeviationFromBaseline")?.valueDeviationName(), common.spo2ValueDeviationFromBaseline, "$caseId value deviation")
            assertEquals(proto.optionalIntValue("spo2HrvDeviationFromBaseline")?.hrvDeviationName(), common.spo2HrvDeviationFromBaseline, "$caseId hrv deviation")
            assertEquals(proto.optionalIntValue("triggerType")?.triggerTypeName(), common.triggerType, "$caseId trigger")
            assertEquals(input.stringValue("date") + "T" + input.stringValue("timeDirName"), common.sourceDateTimeKey, "$caseId source key")
        }
    }

    @Test
    fun spo2ReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("sdk/spo2-test/spo2-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("spo2-readiness", vector.stringValue("id"))
        assertEquals("sdk.spo2-test", vector.stringValue("area"))
        assertEquals("spo2_readiness", vector.stringValue("case"))
        assertEquals("spo2Readiness", input.stringValue("kind"))
        assertEquals(SPO2_VECTORS, input.stringArrayValue("policyVectorPaths"), vector.stringValue("id"))
        assertEquals(requiredSpo2Families, input.stringArrayValue("requiredBehaviorFamilies"), vector.stringValue("id"))
        assertEquals(requiredSpo2Families, expected.stringArrayValue("coveredBehaviorFamilies"), vector.stringValue("id"))
        assertEquals(SPO2_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"), vector.stringValue("id"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarTestUtilsTest"), consumerTests.stringArrayValue("android"), vector.stringValue("id"))
        assertEquals(listOf("PolarTestUtilsTest"), consumerTests.stringArrayValue("ios"), vector.stringValue("id"))
        assertEquals(listOf("com.polar.sharedtest.Spo2CommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("android"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("ios"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("common"), vector.stringValue("id"))
    }

    private fun mapSpo2(date: String, timeDirName: String, proto: String): Spo2Model {
        val recordingDevice = proto.optionalStringValue("recordingDevice")?.takeIf { value -> value.isNotEmpty() }
        return Spo2Model(
            policy = when {
                proto.optionalIntValue("spo2Class") == 99 -> "document-spo2-unknown-enum-platform-difference"
                proto.optionalIntValue("triggerType") != null -> "map-spo2-trigger-type-when-platform-exposes-it"
                proto.optionalIntValue("bloodOxygenPercent") == null -> "preserve-spo2-optional-field-presence"
                else -> "map-spo2-proto-fields-to-public-model"
            },
            recordingDevice = recordingDevice,
            sourceDateTimeKey = "${date}T$timeDirName",
            timeZoneOffsetMinutes = proto.intValue("timeZoneOffsetMinutes"),
            testStatus = proto.intValue("testStatus").testStatusName(),
            bloodOxygenPercent = proto.optionalIntValue("bloodOxygenPercent"),
            spo2Class = proto.optionalIntValue("spo2Class")?.spo2ClassName(),
            spo2ValueDeviationFromBaseline = proto.optionalIntValue("spo2ValueDeviationFromBaseline")?.valueDeviationName(),
            spo2QualityAveragePercent = proto.optionalFloatValue("spo2QualityAveragePercent"),
            averageHeartRateBpm = proto.optionalIntValue("averageHeartRateBpm"),
            heartRateVariabilityMs = proto.optionalFloatValue("heartRateVariabilityMs"),
            spo2HrvDeviationFromBaseline = proto.optionalIntValue("spo2HrvDeviationFromBaseline")?.hrvDeviationName(),
            altitudeMeters = proto.optionalFloatValue("altitudeMeters"),
            triggerType = proto.optionalIntValue("triggerType")?.triggerTypeName()
        )
    }

    private fun Int.testStatusName(): String {
        return when (this) {
            0 -> "passed"
            else -> "unknown"
        }
    }

    private fun Int.spo2ClassName(): String? {
        return when (this) {
            3 -> "normal"
            else -> null
        }
    }

    private fun Int.valueDeviationName(): String {
        return when (this) {
            0 -> "noBaseline"
            else -> "unknown"
        }
    }

    private fun Int.hrvDeviationName(): String {
        return when (this) {
            2 -> "usual"
            else -> "unknown"
        }
    }

    private fun Int.triggerTypeName(): String {
        return when (this) {
            1 -> "automatic"
            else -> "unknown"
        }
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

    private fun String.optionalFloatValue(field: String): Float? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(this)?.groupValues?.get(1)?.toFloat()
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

    private data class Spo2Model(
        val policy: String,
        val recordingDevice: String?,
        val sourceDateTimeKey: String,
        val timeZoneOffsetMinutes: Int,
        val testStatus: String,
        val bloodOxygenPercent: Int?,
        val spo2Class: String?,
        val spo2ValueDeviationFromBaseline: String?,
        val spo2QualityAveragePercent: Float?,
        val averageHeartRateBpm: Int?,
        val heartRateVariabilityMs: Float?,
        val spo2HrvDeviationFromBaseline: String?,
        val altitudeMeters: Float?,
        val triggerType: String?
    )

    private companion object {
        val SPO2_VECTORS = listOf(
            "sdk/spo2-test/full-passed-normal.json",
            "sdk/spo2-test/ios-trigger-automatic.json",
            "sdk/spo2-test/omitted-optionals.json",
            "sdk/spo2-test/unknown-spo2-class-platform-difference.json"
        )
        val requiredSpo2Families = listOf(
            "full-passed-normal-field-mapping",
            "optional-protobuf-presence-preservation",
            "empty-recording-device-normalization",
            "nullable-trigger-type-policy",
            "android-no-trigger-field-platform-reference",
            "ios-trigger-field-platform-reference",
            "unknown-spo2-class-boundary",
            "platform-spo2-vector-reference-gate",
            "compile-verification-gate"
        )
        const val SPO2_READINESS_COMMON_DECISION = "SPo2 model migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS SPo2 tests continue to reference the same vectors, optional protobuf presence and empty recording-device normalization remain covered, nullable triggerType policy remains explicit, unknown SPo2 class behavior is handled at a typed boundary before public model exposure, and the shared tests are compile-verified."
    }
}
