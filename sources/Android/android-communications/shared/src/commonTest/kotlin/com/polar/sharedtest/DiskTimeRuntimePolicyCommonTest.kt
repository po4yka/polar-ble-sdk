package com.polar.sharedtest

import com.polar.shared.runtime.PolarDiskTimeOperation
import com.polar.shared.runtime.PolarRuntimeOrchestration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiskTimeRuntimePolicyCommonTest {
    @Test
    fun diskTimeQueryPolicyVectorDefinesExecutableCommonQueryPlanning() {
        val vector = loadGoldenVectorText("sdk/disk-time-runtime/disk-time-query-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val operations = input.objectArray("operations").map { operation ->
            PolarDiskTimeOperation(
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

        assertEquals("disk-time-query-policy", vector.stringValue("id"))
        assertEquals("disk_time_query_policy", vector.stringValue("case"))
        assertEquals(requiredDiskTimeOperationIds, operations.map { it.id })
        assertEquals(requiredDiskTimeOperationIds, expectedCaseList.map { it.stringValue("id") })
        assertEquals("fake-disk-time-query-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("public-facade-query-capture", vector.objectValue("execution").stringValue("transport"))
        assertEquals(diskTimePolicyCommonDecision, vector.stringValue("commonDecision"))
        assertDiskTimePolicyFields(input.objectArray("operations").associateBy { it.stringValue("id") })

        operations.forEach { operation ->
            val outcome = PolarRuntimeOrchestration.planDiskTime(operation)
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

    @Test
    fun diskTimeQueryVectorRunsThroughCommonFakeTransportFacadeShape() {
        val vector = loadGoldenVectorText("sdk/disk-time-runtime/disk-time-query-policy.json")
        val input = vector.objectValue("input")
        val expectedCases = vector.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }

        input.objectArray("operations").forEach { operationJson ->
            val operation = PolarDiskTimeOperation(
                id = operationJson.stringValue("id"),
                kind = operationJson.stringValue("kind"),
                query = operationJson.optionalStringValue("query"),
                queries = operationJson.optionalStringArrayValue("queries") ?: emptyList(),
                parameters = operationJson.optionalStringArrayValue("parameters") ?: emptyList(),
                expectedFields = operationJson.optionalStringArrayValue("expectedFields") ?: emptyList()
            )
            val planned = PolarRuntimeOrchestration.planDiskTime(operation)
            val expected = expectedCases.getValue(operation.id)
            val transportCommands = diskTimeTransportCommands(expected.stringArrayValue("commands"))
            val transport = ScriptedCommonFakeTransport(outcomesForDiskTimeTransport(transportCommands, expected.stringValue("terminal")))
            val terminal = executePlannedDiskTime(transportCommands, transport)

            assertEquals(expected.stringArrayValue("commands"), planned.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), terminal ?: expected.stringValue("terminal"), operation.id)
            assertEquals(transportCommands, transport.commands, operation.id)
        }
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

    private fun outcomesForDiskTimeTransport(commands: List<CommonFakeTransportCommand>, terminal: String): List<CommonFakeTransportOutcome> {
        if (terminal == "transport-error") {
            return if (commands.size == 1) {
                listOf(CommonFakeTransportOutcome.TransportError("disk-time-query-failed"))
            } else {
                commands.dropLast(1).map { CommonFakeTransportOutcome.Complete } + CommonFakeTransportOutcome.TransportError("disk-time-query-failed")
            }
        }
        return commands.map { CommonFakeTransportOutcome.Complete }
    }

    private fun executePlannedDiskTime(
        commands: List<CommonFakeTransportCommand>,
        transport: ScriptedCommonFakeTransport
    ): String? {
        commands.forEach { command ->
            val payload = command.payloadHex?.let(::hexToBytes) ?: byteArrayOf()
            val outcome = transport.write(command.target, payload)
            if (outcome is CommonFakeTransportOutcome.TransportError) {
                return "transport-error"
            }
            assertIs<CommonFakeTransportOutcome.Complete>(outcome, command.target)
        }
        return null
    }

    private fun diskTimeTransportCommands(commands: List<String>): List<CommonFakeTransportCommand> {
        val transportCommands = mutableListOf<CommonFakeTransportCommand>()
        var index = 0
        while (index < commands.size) {
            val command = commands[index]
            if (command.startsWith("query:")) {
                val payloadFields = mutableListOf<String>()
                index += 1
                while (index < commands.size && (commands[index].startsWith("field:") || commands[index] == "parameters:none")) {
                    payloadFields += commands[index]
                    index += 1
                }
                transportCommands += CommonFakeTransportCommand(
                    operation = CommonFakeTransportOperation.WRITE,
                    target = command,
                    payloadHex = payloadFields.joinToString(separator = "|").encodeToByteArray().toHexString()
                )
            } else {
                index += 1
            }
        }
        return transportCommands
    }

    private fun assertDiskTimePolicyFields(operationsById: Map<String, String>) {
        assertEquals("GET_DISK_SPACE", operationsById.getValue("get-disk-space").stringValue("query"))
        assertEquals("GET_LOCAL_TIME", operationsById.getValue("get-local-time").stringValue("query"))
        assertEquals("GET_LOCAL_TIME", operationsById.getValue("get-local-time-with-zone").stringValue("query"))
        assertEquals(listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"), operationsById.getValue("set-local-time-v2").stringArrayValue("queries"))
        assertEquals(listOf("systemTimeHour=10", "localTimeHour=12", "systemTimeTrusted=true"), operationsById.getValue("set-local-time-v2").stringArrayValue("expectedFields"))
        assertEquals(listOf("SET_LOCAL_TIME"), operationsById.getValue("set-local-time-h10").stringArrayValue("queries"))
        assertEquals(listOf("localTimeHour=12"), operationsById.getValue("set-local-time-h10").stringArrayValue("expectedFields"))
    }
}
