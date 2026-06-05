package com.polar.sharedtest

import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarRuntimeOrchestration
import kotlin.test.Test
import kotlin.test.assertEquals

class FileFacadeRuntimePolicyCommonTest {
    @Test
    fun fileFacadeRuntimePolicyVectorDefinesExecutableCommonCommandPlanning() {
        val vector = loadGoldenVectorText("sdk/file-utils/file-facade-runtime-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
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
        assertEquals(fileFacadeRuntimeMigrationDecision, vector.stringValue("commonDecision"))
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
    fun fileFacadeRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
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
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
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

    private val fileFacadeRuntimeMigrationDecision = "Promote low-level file facade planning only after read/write/delete public APIs reference this vector, directory traversal remains covered by list-files vectors, and runtime-error-policy.json keeps malformed directory, response-error, transport-error, empty read payload, delete request failure, write progress success, and write-stream failure behavior pinned."

    private val fileFacadeRuntimeReadinessDecision = "File facade runtime migration may proceed only after file-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, directory-list traversal vectors remain linked, runtime-error-policy.json keeps malformed-directory, response-error, transport-error, empty read payload, delete request failure, write progress before completion, read/write/delete response-error, and write-stream failure behavior covered, public facade error mapping is pinned, and the shared tests are compile-verified."

    private val fileFacadeRuntimeNotes = "This vector complements file-read-write-delete-operations.json and runtime-error-policy.json. It owns the public facade command paths, payload capture, empty read payload success, delete request failure, write progress success, and read/write/delete response-error propagation for low-level read/write/delete calls; directory-list traversal remains covered by list-files-shallow-all.json and list-files-recursive-filtered.json."

}
