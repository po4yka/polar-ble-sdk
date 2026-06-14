package com.polar.sharedtest

import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals

class FileFacadeRuntimePolicyCommonTest {
    @Test
    fun ledConfigPayloadPlanningPreservesSdkAndPpiByteOrder() {
        assertEquals(listOf(1, 0), PolarWorkflowRuntimePlanning.ledConfigPayloadBytes(sdkModeLedEnabled = true, ppiModeLedEnabled = false))
        assertEquals(listOf(0, 1), PolarWorkflowRuntimePlanning.ledConfigPayloadBytes(sdkModeLedEnabled = false, ppiModeLedEnabled = true))
    }

    @Test
    fun fileFacadeRuntimePolicyVectorDefinesExecutableCommonCommandPlanning() {
        val vector = loadGoldenVectorText("sdk/file-utils/file-facade-runtime-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        assertEquals("/U/0/", PolarRuntimeOrchestration.normalizeFileListFolderPath("U/0"))
        assertEquals("/", PolarRuntimeOrchestration.normalizeFileListFolderPath(""))
        val operations = vector.objectValue("input").objectArray("operations").map { operation ->
            PolarFileFacadeOperation(
                id = operation.stringValue("id"),
                command = operation.stringValue("command"),
                path = operation.stringValue("path"),
                payloadHex = operation.optionalStringValue("payloadHex"),
                responseHex = operation.optionalStringValue("responseHex"),
                progress = operation.optionalSignedIntArrayValue("progress") ?: emptyList(),
                transportMode = operation.optionalObjectValue("transport")?.stringValue("mode")
            )
        }
        val expectedCaseList = expected.objectValue("commonRuntimePrototype").objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }

        assertEquals("file-facade-runtime-policy", vector.stringValue("id"))
        assertEquals("fileFacadeRuntimePolicy", input.stringValue("kind"))
        assertEquals(requiredFileFacadeRuntimeOperationIds, operations.map { it.id })
        assertEquals(requiredFileFacadeRuntimeOperationIds, expectedCaseList.map { it.stringValue("id") })
        assertEquals(requiredFileFacadeRuntimePaths, operations.map { it.path })
        assertEquals(requiredFileFacadeRuntimeResponseHexById, operations.mapNotNull { operation -> operation.responseHex?.let { operation.id to it } }.toMap())
        assertEquals(requiredFileFacadeRuntimePayloadHexById, operations.mapNotNull { operation -> operation.payloadHex?.let { operation.id to it } }.toMap())
        assertEquals(requiredFileFacadeRuntimeProgressById, operations.filter { it.progress.isNotEmpty() }.associate { it.id to it.progress.map { progress -> "progress:$progress" } })
        assertEquals("response-error:103:missing", expectedCases.getValue("read-low-level-file-response-error").stringValue("terminal"))
        assertEquals("write-stream-error-after-payload", expectedCases.getValue("write-low-level-file-stream-failure").stringValue("terminal"))
        assertEquals(requiredFileFacadeRuntimePlatformTerminals, input.objectArray("operations").filter { it.optionalObjectValue("expectedPlatformTerminal") != null }.associate { operation ->
            operation.stringValue("id") to operation.objectValue("expectedPlatformTerminal").let { platformTerminal ->
                platformTerminal.stringValue("android") to platformTerminal.stringValue("ios")
            }
        })
        assertEquals(fileFacadeRuntimePolicyDecision, expected.stringValue("commonDecision"))
        assertEquals(fileFacadeRuntimeOwnershipDecision, vector.stringValue("commonDecision"))
        assertEquals("fake-file-facade-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("public-facade-psftp-command-capture", vector.objectValue("execution").stringValue("transport"))
        assertEquals(fileFacadeRuntimeNotes, vector.stringValue("notes"))
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests", "PolarFileUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))

        operations.forEach { operation ->
            val outcome = PolarRuntimeOrchestration.planFileFacade(operation)
            val expected = expectedCases.getValue(operation.id)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, operation.id)
            expected.optionalStringValue("resultHex")?.let { resultHex ->
                assertEquals(resultHex, outcome.resultHex, operation.id)
            }
        }
    }

    @Test
    fun fileFacadeRuntimeReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/file-utils/file-facade-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")
        assertEquals("file-facade-runtime-readiness", manifest.stringValue("id"))
        assertEquals("fileFacadeRuntimeReadiness", input.stringValue("kind"))
        assertEquals("sdk/file-utils/file-facade-runtime-policy.json", policyVectorPath)
        assertEquals(requiredFileFacadeRuntimeFamilies, requiredFamilies)
        assertEquals(requiredFileFacadeRuntimeFamilies, coveredFamilies)
        assertEquals(fileFacadeRuntimeReadinessDecision, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production shared ownership.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals("file-facade-runtime-policy", policy.stringValue("id"))
        assertEquals(requiredFileFacadeRuntimeOperationIds, policy.objectValue("input").objectArray("operations").map { it.stringValue("id") })
        assertEquals(fileFacadeLinkedPolicyVectorPaths, listOf("list-files-shallow-all.json", "list-files-recursive-filtered.json"))
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests", "PolarFileUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    @Test
    fun fileFacadeRuntimeVectorRunsThroughCommonFakeTransportFacadeShape() {
        val vector = loadGoldenVectorText("sdk/file-utils/file-facade-runtime-policy.json")
        val input = vector.objectValue("input")
        val expectedCases = vector.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }

        input.objectArray("operations").forEach { operationJson ->
            val operation = PolarFileFacadeOperation(
                id = operationJson.stringValue("id"),
                command = operationJson.stringValue("command"),
                path = operationJson.stringValue("path"),
                payloadHex = operationJson.optionalStringValue("payloadHex"),
                responseHex = operationJson.optionalStringValue("responseHex"),
                progress = operationJson.optionalSignedIntArrayValue("progress") ?: emptyList(),
                transportMode = operationJson.optionalObjectValue("transport")?.stringValue("mode")
            )
            val planned = PolarRuntimeOrchestration.planFileFacade(operation)
            val expected = expectedCases.getValue(operation.id)
            val transport = ScriptedCommonFakeTransport(listOf(outcomeForFileFacade(operation, expected.stringValue("terminal"))))
            val terminal = executePlannedFileFacade(expected.stringArrayValue("commands"), transport)

            assertEquals(expected.stringArrayValue("commands"), planned.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), terminal, operation.id)
            assertEquals(expectedFileFacadeTransportCommands(expected.stringArrayValue("commands")), transport.commands, operation.id)
            expected.optionalStringValue("resultHex")?.let { resultHex ->
                assertEquals(resultHex, planned.resultHex, operation.id)
            }
        }
    }

    private val requiredFileFacadeRuntimeOperationIds = listOf(
        "read-low-level-file-success",
        "read-low-level-file-empty-success",
        "read-low-level-file-request-failure",
        "read-low-level-file-response-error",
        "write-low-level-file-success",
        "write-low-level-file-progress-success",
        "write-low-level-file-stream-failure",
        "write-low-level-file-response-error",
        "delete-low-level-file-success",
        "delete-low-level-file-request-failure",
        "delete-low-level-file-response-error"
    )

    private val requiredFileFacadeRuntimePaths = listOf(
        "/U/0/CUSTOM.BIN",
        "/U/0/EMPTY.BIN",
        "/U/0/CUSTOM.BIN",
        "/U/0/CUSTOM.BIN",
        "/U/0/CUSTOM.BIN",
        "/U/0/PROGRESS.BIN",
        "/U/0/CUSTOM.BIN",
        "/U/0/CUSTOM.BIN",
        "/U/0/CUSTOM.BIN",
        "/U/0/CUSTOM.BIN",
        "/U/0/CUSTOM.BIN"
    )

    private val requiredFileFacadeRuntimeResponseHexById = mapOf(
        "read-low-level-file-success" to "010203",
        "read-low-level-file-empty-success" to ""
    )

    private val requiredFileFacadeRuntimePayloadHexById = mapOf(
        "write-low-level-file-success" to "0a0b",
        "write-low-level-file-progress-success" to "1011",
        "write-low-level-file-stream-failure" to "0c0d",
        "write-low-level-file-response-error" to "0e0f"
    )

    private val requiredFileFacadeRuntimeProgressById = mapOf(
        "write-low-level-file-progress-success" to listOf("progress:0", "progress:2")
    )

    private val requiredFileFacadeRuntimePlatformTerminals = mapOf(
        "read-low-level-file-response-error" to ("pftp-response-error-name" to "device-error-wrapper"),
        "write-low-level-file-response-error" to ("pftp-response-error-object" to "pftp-response-error-code")
    )

    private val fileFacadeLinkedPolicyVectorPaths = listOf(
        "list-files-shallow-all.json",
        "list-files-recursive-filtered.json"
    )

    private val requiredFileFacadeRuntimeFamilies = listOf(
        "low-level-file-path-gate",
        "read-file-get-success",
        "read-file-empty-success",
        "read-file-request-failure",
        "read-file-response-error",
        "write-file-put-success",
        "write-file-payload-capture",
        "write-file-progress-before-completion",
        "write-file-stream-failure-after-payload",
        "write-file-response-error-after-payload",
        "delete-file-remove-success",
        "delete-file-request-failure",
        "delete-file-response-error",
        "directory-list-shallow-vector-reference-gate",
        "directory-list-recursive-vector-reference-gate",
        "read-write-delete-model-vector-reference-gate",
        "runtime-error-policy-reference-gate",
        "malformed-directory-policy-gate",
        "response-error-policy-gate",
        "facade-error-mapping-gate",
        "platform-facade-vector-reference-gate",
        "compile-verification-gate"
    )

    private val fileFacadeRuntimePolicyDecision = "A shared file facade runtime may own deterministic GET/PUT/REMOVE planning, empty read payloads, write progress consumption, and payload capture only after platform facades keep public error mapping, read/write/delete request and response-error propagation, and directory-list traversal policies pinned."

    private val fileFacadeRuntimeOwnershipDecision = "Promote low-level file facade planning only while read/write/delete public APIs reference this vector, directory traversal remains covered by list-files vectors, and runtime-error-policy.json keeps malformed directory, response-error, transport-error, empty read payload, delete request failure, write progress success, and write-stream failure behavior pinned."

    private val fileFacadeRuntimeReadinessDecision = "File facade runtime shared ownership remains valid while file-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, directory-list traversal vectors remain linked, runtime-error-policy.json keeps malformed-directory, response-error, transport-error, empty read payload, delete request failure, write progress before completion, read/write/delete response-error, and write-stream failure behavior covered, public facade error mapping is pinned, and the shared tests are compile-verified."

    private val fileFacadeRuntimeNotes = "This vector complements file-read-write-delete-operations.json and runtime-error-policy.json. It owns the public facade command paths, payload capture, empty read payload success, delete request failure, write progress success, and read/write/delete response-error propagation for low-level read/write/delete calls; directory-list traversal remains covered by list-files-shallow-all.json and list-files-recursive-filtered.json."

    private fun outcomeForFileFacade(operation: PolarFileFacadeOperation, terminal: String): CommonFakeTransportOutcome {
        return when {
            terminal == "success" && operation.command == "GET" -> CommonFakeTransportOutcome.Bytes(hexToBytes(operation.responseHex ?: ""))
            terminal == "success" -> CommonFakeTransportOutcome.Complete
            terminal == "transport-error" -> CommonFakeTransportOutcome.TransportError("file-facade-request-failed")
            terminal == "write-stream-error-after-payload" -> CommonFakeTransportOutcome.TransportError("file-write-stream-failed")
            terminal.startsWith("response-error:") -> CommonFakeTransportOutcome.ResponseError(status = 103, message = "missing")
            else -> error("Unsupported file facade terminal $terminal")
        }
    }

    private fun executePlannedFileFacade(
        commands: List<String>,
        transport: ScriptedCommonFakeTransport
    ): String {
        val operationCommand = commands.first { command -> command.startsWith("GET:") || command.startsWith("PUT:") || command.startsWith("REMOVE:") }
        return when {
            operationCommand.startsWith("GET:") -> describeFileFacadeRead(transport.read(operationCommand.removePrefix("GET:")))
            operationCommand.startsWith("PUT:") -> {
                val payloadHex = commands.firstOrNull { command -> command.startsWith("payload:") }?.removePrefix("payload:") ?: ""
                describeFileFacadeWrite(transport.write(operationCommand.removePrefix("PUT:"), hexToBytes(payloadHex)))
            }
            operationCommand.startsWith("REMOVE:") -> describeFileFacadeRemove(transport.remove(operationCommand.removePrefix("REMOVE:")))
            else -> error("Unsupported file facade command $operationCommand")
        }
    }

    private fun describeFileFacadeRead(outcome: CommonFakeTransportOutcome): String {
        return when (outcome) {
            is CommonFakeTransportOutcome.Bytes -> "success"
            is CommonFakeTransportOutcome.TransportError -> "transport-error"
            is CommonFakeTransportOutcome.ResponseError -> "response-error:${outcome.status}:${outcome.message}"
            is CommonFakeTransportOutcome.Timeout -> error("File facade vector does not use timeout outcome ${outcome.label}")
            CommonFakeTransportOutcome.Complete -> error("File GET cannot complete without bytes")
        }
    }

    private fun describeFileFacadeWrite(outcome: CommonFakeTransportOutcome): String {
        return when (outcome) {
            CommonFakeTransportOutcome.Complete -> "success"
            is CommonFakeTransportOutcome.TransportError -> "write-stream-error-after-payload"
            is CommonFakeTransportOutcome.ResponseError -> "response-error:${outcome.status}:${outcome.message}"
            is CommonFakeTransportOutcome.Bytes -> error("File PUT cannot return bytes ${outcome.value.toHexString()}")
            is CommonFakeTransportOutcome.Timeout -> error("File facade vector does not use timeout outcome ${outcome.label}")
        }
    }

    private fun describeFileFacadeRemove(outcome: CommonFakeTransportOutcome): String {
        return when (outcome) {
            CommonFakeTransportOutcome.Complete -> "success"
            is CommonFakeTransportOutcome.TransportError -> "transport-error"
            is CommonFakeTransportOutcome.ResponseError -> "response-error:${outcome.status}:${outcome.message}"
            is CommonFakeTransportOutcome.Bytes -> error("File REMOVE cannot return bytes ${outcome.value.toHexString()}")
            is CommonFakeTransportOutcome.Timeout -> error("File facade vector does not use timeout outcome ${outcome.label}")
        }
    }

    private fun expectedFileFacadeTransportCommands(commands: List<String>): List<CommonFakeTransportCommand> {
        val operationCommand = commands.first { command -> command.startsWith("GET:") || command.startsWith("PUT:") || command.startsWith("REMOVE:") }
        return when {
            operationCommand.startsWith("GET:") -> listOf(CommonFakeTransportCommand(CommonFakeTransportOperation.READ, operationCommand.removePrefix("GET:")))
            operationCommand.startsWith("PUT:") -> {
                val payloadHex = commands.first { command -> command.startsWith("payload:") }.removePrefix("payload:")
                listOf(CommonFakeTransportCommand(CommonFakeTransportOperation.WRITE, operationCommand.removePrefix("PUT:"), payloadHex))
            }
            operationCommand.startsWith("REMOVE:") -> listOf(CommonFakeTransportCommand(CommonFakeTransportOperation.REMOVE, operationCommand.removePrefix("REMOVE:")))
            else -> error("Unsupported file facade command $operationCommand")
        }
    }

}
