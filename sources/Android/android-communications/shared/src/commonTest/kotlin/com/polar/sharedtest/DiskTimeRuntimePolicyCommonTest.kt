package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class DiskTimeRuntimePolicyCommonTest {
    @Test
    fun diskTimeQueryPolicyVectorDefinesExecutableCommonQueryPlanning() {
        val vector = loadGoldenVectorText("sdk/disk-time-runtime/disk-time-query-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val operations = input.objectArray("operations").map { operation ->
            DiskTimeOperation(
                id = operation.stringValue("id"),
                kind = operation.stringValue("kind"),
                query = operation.optionalStringValue("query"),
                queries = operation.optionalStringArrayValue("queries") ?: emptyList(),
                parameters = operation.optionalStringArrayValue("parameters") ?: emptyList(),
                expectedFields = operation.optionalStringArrayValue("expectedFields") ?: emptyList()
            )
        }
        val expectedCaseList = expected.objectValue("commonRuntimePrototype").objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }
        val runtime = FakeDiskTimeQueryRuntime()

        assertEquals("disk-time-query-policy", vector.stringValue("id"))
        assertEquals("disk_time_query_policy", vector.stringValue("case"))
        assertEquals(requiredDiskTimeOperationIds, operations.map { it.id })
        assertEquals(requiredDiskTimeOperationIds, expectedCaseList.map { it.stringValue("id") })
        assertEquals("fake-disk-time-query-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("public-facade-query-capture", vector.objectValue("execution").stringValue("transport"))
        assertEquals(diskTimePolicyCommonDecision, vector.stringValue("commonDecision"))
        assertDiskTimePolicyFields(input.objectArray("operations").associateBy { it.stringValue("id") })

        operations.forEach { operation ->
            val outcome = runtime.run(operation)
            val expected = expectedCases.getValue(operation.id)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, operation.id)
        }
    }

    @Test
    fun diskTimeQueryReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/disk-time-runtime/disk-time-query-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        assertEquals("disk-time-query-readiness", manifest.stringValue("id"))
        assertEquals("diskTimeQueryReadiness", input.stringValue("kind"))
        assertEquals("sdk/disk-time-runtime/disk-time-query-policy.json", policyVectorPath)
        assertEquals(requiredDiskTimeFamilies, requiredFamilies)
        assertEquals(requiredDiskTimeFamilies, coveredFamilies)
        assertEquals("disk-time-query-policy", policy.stringValue("id"))
        assertEquals("disk_time_query_policy", policy.stringValue("case"))
        assertEquals(requiredDiskTimeOperationIds, policy.objectValue("input").objectArray("operations").map { it.stringValue("id") })
        assertEquals(requiredDiskTimeOperationIds, policy.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").map { it.stringValue("id") })
        assertDiskTimePolicyFields(policy.objectValue("input").objectArray("operations").associateBy { it.stringValue("id") })
        assertEquals(diskTimeReadinessCommonDecision, expected.stringValue("commonDecision"))
        val commonRuntimePrototype = expected.objectValue("commonRuntimePrototype")
        assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.stringValue("reason"))
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.DiskTimeRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private val requiredDiskTimeOperationIds = listOf(
        "get-disk-space",
        "get-local-time",
        "get-local-time-with-zone",
        "set-local-time-v2",
        "set-local-time-h10",
        "set-local-time-failure",
        "get-local-time-failure",
        "get-local-time-with-zone-failure",
        "get-disk-space-failure"
    )

    private val requiredDiskTimeFamilies = listOf(
        "disk-space-query",
        "local-time-query",
        "local-time-with-zone-query",
        "v2-system-and-local-time-sequence",
        "h10-single-local-time-query",
        "set-local-time-transport-error",
        "local-time-transport-error",
        "local-time-with-zone-transport-error",
        "disk-space-transport-error",
        "filesystem-capability-gate",
        "facade-error-mapping-gate",
        "platform-facade-vector-reference-gate",
        "compile-verification-gate"
    )

    private val diskTimeReadinessCommonDecision = "Disk/time facade runtime migration may proceed only after disk-time-query-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, filesystem capability gates remain platform-owned, public facade error mapping is pinned for disk-space and local-time query failures, V2 two-query time setting and H10 single-query behavior are preserved or explicitly reconciled, and the shared tests are compile-verified."

    private val diskTimePolicyCommonDecision = "Promote disk/time query planning only after facade tests keep current H10 capability behavior and V2 two-query time-setting semantics pinned."

    private fun assertDiskTimePolicyFields(operationsById: Map<String, String>) {
        assertEquals("GET_DISK_SPACE", operationsById.getValue("get-disk-space").stringValue("query"))
        assertEquals("GET_LOCAL_TIME", operationsById.getValue("get-local-time").stringValue("query"))
        assertEquals("GET_LOCAL_TIME", operationsById.getValue("get-local-time-with-zone").stringValue("query"))
        assertEquals(listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"), operationsById.getValue("set-local-time-v2").stringArrayValue("queries"))
        assertEquals(listOf("systemTimeHour=10", "localTimeHour=12", "systemTimeTrusted=true"), operationsById.getValue("set-local-time-v2").stringArrayValue("expectedFields"))
        assertEquals(listOf("SET_LOCAL_TIME"), operationsById.getValue("set-local-time-h10").stringArrayValue("queries"))
        assertEquals(listOf("localTimeHour=12"), operationsById.getValue("set-local-time-h10").stringArrayValue("expectedFields"))
    }

    private class FakeDiskTimeQueryRuntime {
        fun run(operation: DiskTimeOperation): DiskTimeOutcome {
            return when (operation.kind) {
                "query" -> DiskTimeOutcome(operation.singleQueryCommands(), "success")
                "queryFailure" -> DiskTimeOutcome(operation.singleQueryCommands(), "transport-error")
                "setLocalTimeV2" -> DiskTimeOutcome(operation.setLocalTimeV2Commands(), "success")
                "setLocalTimeH10" -> DiskTimeOutcome(operation.setLocalTimeH10Commands(), "success")
                else -> error("Unsupported disk/time operation ${operation.kind}")
            }
        }

        private fun DiskTimeOperation.singleQueryCommands(): List<String> {
            val commands = mutableListOf("query:${requireNotNull(query)}")
            if (parameters.isEmpty()) {
                commands += "parameters:none"
            } else {
                commands += parameters.map { parameter -> "field:$parameter" }
            }
            return commands
        }

        private fun DiskTimeOperation.setLocalTimeV2Commands(): List<String> {
            require(queries == listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"))
            val systemTimeHour = expectedFields.first { it.startsWith("systemTimeHour=") }
            val systemTimeTrusted = expectedFields.first { it == "systemTimeTrusted=true" }
            val localTimeHour = expectedFields.first { it.startsWith("localTimeHour=") }
            return listOf(
                "query:SET_SYSTEM_TIME",
                "field:$systemTimeHour",
                "field:$systemTimeTrusted",
                "query:SET_LOCAL_TIME",
                "field:$localTimeHour"
            )
        }

        private fun DiskTimeOperation.setLocalTimeH10Commands(): List<String> {
            require(queries == listOf("SET_LOCAL_TIME"))
            val localTimeHour = expectedFields.first { it.startsWith("localTimeHour=") }
            return listOf(
                "query:SET_LOCAL_TIME",
                "field:$localTimeHour"
            )
        }
    }

    private data class DiskTimeOperation(
        val id: String,
        val kind: String,
        val query: String?,
        val queries: List<String>,
        val parameters: List<String>,
        val expectedFields: List<String>
    )

    private data class DiskTimeOutcome(
        val commands: List<String>,
        val terminal: String
    )
}
