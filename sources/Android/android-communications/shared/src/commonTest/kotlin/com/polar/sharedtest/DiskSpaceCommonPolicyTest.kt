package com.polar.sharedtest

import com.polar.shared.sdk.PolarSdkModelMappers
import kotlin.test.Test
import kotlin.test.assertEquals

class DiskSpaceCommonPolicyTest {
    @Test
    fun diskSpaceGoldenVectorsDefineExecutableCommonUnsignedFragmentPolicy() {
        DISK_SPACE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")

            expected.optionalStringValue("error")?.let { error ->
                assertEquals(error, parseDiskSpaceProto(input.stringValue("hex")).error, caseId)
                assertEquals("typed-parse-error", vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("policy"), caseId)
                return@forEach
            }

            val proto = input.objectValue("proto")
            val calculated = PolarSdkModelMappers.diskSpace(
                fragmentSize = proto.longValue("fragmentSize"),
                totalFragments = proto.longValue("totalFragments"),
                freeFragments = proto.longValue("freeFragments")
            )
            assertEquals(expected.longValue("totalSpace"), calculated.totalSpace, "$caseId totalSpace")
            assertEquals(expected.longValue("freeSpace"), calculated.freeSpace, "$caseId freeSpace")

            vector.optionalObjectValue("platformExpectations")?.optionalObjectValue("commonDecision")?.let { commonDecision ->
                assertEquals("treat-as-unsigned-uint32", commonDecision.stringValue("fragmentSizePolicy"), caseId)
                assertEquals(expected.longValue("totalSpace"), commonDecision.longValue("totalSpace"), "$caseId common totalSpace")
                assertEquals(expected.longValue("freeSpace"), commonDecision.longValue("freeSpace"), "$caseId common freeSpace")
            }
        }
    }

    @Test
    fun diskSpaceReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/disk-space/disk-space-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")
        assertEquals("disk-space-readiness", manifest.stringValue("id"))
        assertEquals("diskSpaceReadiness", input.stringValue("kind"))
        assertEquals(DISK_SPACE_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        assertEquals(requiredDiskSpaceFamilies, requiredFamilies)
        assertEquals(requiredDiskSpaceFamilies, coveredFamilies)
        assertEquals(DISK_SPACE_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.PolarDiskSpaceTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDiskSpaceDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.DiskSpaceCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun parseDiskSpaceProto(hex: String): DiskSpaceParseResult {
        val bytes = hexToBytes(hex)
        var index = 0
        while (index < bytes.size) {
            val tag = readVarint(bytes, index) ?: return DiskSpaceParseResult(error = "parse-error")
            index = tag.nextIndex
            val value = readVarint(bytes, index) ?: return DiskSpaceParseResult(error = "parse-error")
            index = value.nextIndex
        }
        return DiskSpaceParseResult()
    }

    private fun readVarint(bytes: ByteArray, start: Int): VarintRead? {
        var value = 0L
        var shift = 0
        var index = start
        while (index < bytes.size && shift < 64) {
            val byte = bytes[index].toInt() and 0xFF
            value = value or ((byte and 0x7F).toLong() shl shift)
            index += 1
            if ((byte and 0x80) == 0) {
                return VarintRead(value = value, nextIndex = index)
            }
            shift += 7
        }
        return null
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field in $this")
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

    private data class DiskSpaceParseResult(
        val error: String? = null
    )

    private data class VarintRead(
        val value: Long,
        val nextIndex: Int
    )

    private companion object {
        const val UNSIGNED_32_BIT_MASK = "implemented by PolarSdkModelMappers"
        val DISK_SPACE_VECTORS = listOf(
            "sdk/disk-space/malformed-truncated-varint.json",
            "sdk/disk-space/typical-fragments.json",
            "sdk/disk-space/uint32-max-fragment-platform-difference.json",
            "sdk/disk-space/zero-fragments.json"
        )
        val DISK_SPACE_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/disk-space/typical-fragments.json",
            "sdk/disk-space/zero-fragments.json",
            "sdk/disk-space/uint32-max-fragment-platform-difference.json",
            "sdk/disk-space/malformed-truncated-varint.json"
        )
        val requiredDiskSpaceFamilies = listOf(
            "byte-total-calculation",
            "free-byte-calculation",
            "zero-fragment-counts",
            "unsigned-uint32-fragment-size-policy",
            "android-signed-fragment-platform-reference",
            "ios-unsigned-fragment-platform-reference",
            "typed-malformed-varint-parse-error",
            "platform-disk-space-vector-reference-gate",
            "compile-verification-gate"
        )
        val DISK_SPACE_READINESS_COMMON_DECISION = "Disk-space model shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS disk-space tests continue to reference the same vectors, byte-total and free-byte calculations remain covered, zero-fragment counts remain explicit, fragment size uses the unsigned 32-bit policy instead of inheriting Android signed-int exposure or Swift UInt32 behavior accidentally, malformed truncated varints map to typed parse errors, and the shared tests are compile-verified."
    }
}
