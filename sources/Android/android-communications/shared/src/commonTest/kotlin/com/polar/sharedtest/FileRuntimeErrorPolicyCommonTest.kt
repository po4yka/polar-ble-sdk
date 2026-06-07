package com.polar.sharedtest

import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarFileRuntimeErrorOperation
import com.polar.shared.runtime.PolarFileRuntimeErrorPlan
import com.polar.shared.runtime.PolarRuntimeOrchestration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileRuntimeErrorPolicyCommonTest {
    @Test
    fun fileListingGoldenVectorsDefineExecutableCommonTraversalPolicy() {
        listOf(
            "sdk/file-utils/list-files-shallow-all.json",
            "sdk/file-utils/list-files-recursive-filtered.json"
        ).forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val input = vector.objectValue("input")
            val result = FakeFileUtility(input.objectValue("directories")).listFiles(
                rootPath = input.stringValue("rootPath"),
                recurseDeep = input.booleanValue("recurseDeep"),
                condition = input.stringValue("condition")
            )

            assertEquals(vector.objectValue("expected").stringArrayValue("paths"), result, vector.stringValue("id"))
        }
    }

    @Test
    fun fileReadWriteDeleteGoldenVectorRunsThroughProductionFileFacadePlanner() {
        val vector = loadGoldenVectorText("sdk/file-utils/file-read-write-delete-operations.json")
        val operations = vector.objectValue("input").objectArray("operations")
        val expectedOperations = vector.objectValue("expected").objectArray("operations")

        operations.forEachIndexed { index, operation ->
            val expected = expectedOperations[index]
            val outcome = PolarRuntimeOrchestration.planFileFacade(
                PolarFileFacadeOperation(
                    id = operation.stringValue("action"),
                    command = expected.stringValue("command"),
                    path = operation.stringValue("path"),
                    payloadHex = operation.optionalStringValue("payloadHex"),
                    responseHex = operation.optionalStringValue("responseHex"),
                    progress = emptyList(),
                    transportMode = null
                )
            )

            assertEquals("${expected.stringValue("command")}:${expected.stringValue("path")}", outcome.commands.first(), operation.stringValue("action"))
            expected.optionalStringValue("writtenHex")?.let { expectedPayload ->
                assertEquals("payload:$expectedPayload", outcome.commands.last(), operation.stringValue("action"))
            }
            expected.optionalStringValue("resultHex")?.let { expectedPayload ->
                assertEquals(expectedPayload, outcome.resultHex, operation.stringValue("action"))
            }
        }
    }

    @Test
    fun fileRuntimeErrorPolicyVectorRunsThroughProductionCommonPlanner() {
        val vector = loadGoldenVectorText("sdk/file-utils/runtime-error-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val expectedPrototype = vector.objectValue("commonRuntimePrototype")
        val consumerTests = vector.objectValue("consumerTests")
        val cases = input.objectArray("cases").map { testCase ->
            val transport = testCase.objectValue("transport")
            PolarFileRuntimeErrorOperation(
                id = testCase.stringValue("id"),
                operation = testCase.stringValue("operation"),
                path = testCase.stringValue("path"),
                payloadHex = testCase.optionalStringValue("payloadHex"),
                transportMode = transport.stringValue("mode"),
                status = transport.optionalIntValue("status"),
                message = transport.optionalStringValue("message"),
                error = transport.optionalStringValue("error"),
                responsePayloadHex = transport.optionalStringValue("payloadHex")
            )
        }
        val expectedCases = expectedPrototype.objectArray("cases").associateBy { it.stringValue("id") }

        assertEquals("fileRuntimeErrorPolicy", input.stringValue("kind"))
        assertEquals(requiredFileRuntimeErrorCaseIds, cases.map { it.id })
        assertEquals(fileRuntimeErrorMigrationRequirement, expected.stringValue("migrationRequirement"))
        assertEquals("executable shared commonTest", expectedPrototype.stringValue("status"))
        assertEquals(requiredFileRuntimeErrorCaseIds, expectedCases.keys.toList())
        assertEquals(fileRuntimeErrorCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("shared-common-test", vector.objectValue("execution").stringValue("status"))
        assertEquals("executable shared commonTest covers file command capture and typed runtime errors before facade mapping moves", vector.objectValue("platformExpectations").stringValue("common"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarFileUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FileRuntimeErrorPolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))

        cases.forEach { testCase ->
            val outcome = PolarRuntimeOrchestration.planFileRuntimeError(testCase)
            val expected = expectedCases.getValue(testCase.id)

            assertEquals(expected.stringValue("command"), outcome.command, testCase.id)
            assertEquals(expected.stringValue("path"), outcome.path, testCase.id)
            expected.optionalStringValue("capturedPayloadHex")?.let { expectedPayload ->
                assertEquals(expectedPayload, outcome.capturedPayloadHex, testCase.id)
            }
            assertOutcome(testCase.id, expected, outcome)
        }
    }

    @Test
    fun fileRuntimeErrorReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/file-utils/runtime-error-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals("runtime-error-readiness", manifest.stringValue("id"))
        assertEquals("fileRuntimeErrorReadiness", input.stringValue("kind"))
        assertEquals("sdk/file-utils/runtime-error-policy.json", policyVectorPath)
        assertEquals(requiredFileRuntimeErrorFamilies, requiredFamilies)
        assertEquals(requiredFileRuntimeErrorFamilies, coveredFamilies)
        assertFileRuntimeErrorPolicyVectorShape(policy)
        assertEquals(fileRuntimeErrorReadinessCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarFileUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FileRuntimeErrorPolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun assertFileRuntimeErrorPolicyVectorShape(policy: String) {
        val input = policy.objectValue("input")
        val expected = policy.objectValue("expected")
        val commonRuntimePrototype = policy.objectValue("commonRuntimePrototype")
        val cases = input.objectArray("cases").associateBy { it.stringValue("id") }
        val expectedCases = commonRuntimePrototype.objectArray("cases").associateBy { it.stringValue("id") }
        assertEquals("runtime-error-policy", policy.stringValue("id"))
        assertEquals("runtime_error_policy", policy.stringValue("case"))
        assertEquals("fileRuntimeErrorPolicy", input.stringValue("kind"))
        assertEquals(requiredFileRuntimeErrorCaseIds, cases.keys.toList())
        assertEquals(requiredFileRuntimeErrorCaseIds, expectedCases.keys.toList())
        assertEquals(fileRuntimeErrorMigrationRequirement, expected.stringValue("migrationRequirement"))
        assertEquals(fileRuntimeErrorCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("shared-common-test", policy.objectValue("execution").stringValue("status"))
        assertEquals("executable shared commonTest covers file command capture and typed runtime errors before facade mapping moves", policy.objectValue("platformExpectations").stringValue("common"))
        assertEquals("BDBleApiImplTest and file-facade-runtime-policy.json pin Android public low-level file response-error object/name mapping, empty-read success, write-stream failure, and delete request failure behavior", policy.objectValue("platformExpectations").stringValue("android"))
        assertEquals("PolarBleApiImplTests and file-facade-runtime-policy.json pin iOS public low-level file response-error code mapping, empty-read success, write-stream failure, and delete request failure behavior", policy.objectValue("platformExpectations").stringValue("ios"))
        assertEquals("This vector complements executable file read/write/delete and directory-listing vectors with shared commonTest runtime checks plus Android and iOS public low-level file facade compatibility through file-facade-runtime-policy.json; directory traversal and additional file workflows still need their own facade compatibility before production file orchestration delegates to shared code.", policy.stringValue("notes"))
        assertFileRuntimeErrorCase(cases.getValue("directory-list-response-error-103"), expectedCases.getValue("directory-list-response-error-103"), "listFiles", "/U/0/", "pftpResponseError", 103, "No such file or directory", null, "GET", "directory-missing")
        assertFileRuntimeErrorCase(cases.getValue("directory-list-malformed-payload"), expectedCases.getValue("directory-list-malformed-payload"), "listFiles", "/U/0/", "success", null, null, "ffff", "GET", "directory-parse-failure")
        assertFileRuntimeErrorCase(cases.getValue("read-file-transport-error"), expectedCases.getValue("read-file-transport-error"), "readFile", "/U/0/S/PHYSDATA.BPB", "transportError", null, "deviceNotConnected", null, "GET", "transport-error")
        assertFileRuntimeErrorCase(cases.getValue("write-file-stream-error-after-header"), expectedCases.getValue("write-file-stream-error-after-header"), "writeFile", "/U/0/S/PREFS.BPB", "writeStreamError", null, "deviceNotConnected", "01020304", "PUT", "write-stream-error")
        assertFileRuntimeErrorCase(cases.getValue("delete-file-response-error"), expectedCases.getValue("delete-file-response-error"), "removeSingleFile", "/U/0/S/PREFS.BPB", "pftpResponseError", 500, "Delete failed", null, "REMOVE", "response-error")
    }

    private fun assertFileRuntimeErrorCase(case: String, expectedCase: String, operation: String, path: String, transportMode: String, status: Int?, messageOrError: String?, payloadHex: String?, command: String, outcome: String) {
        assertEquals(operation, case.stringValue("operation"))
        assertEquals(path, case.stringValue("path"))
        assertEquals(transportMode, case.objectValue("transport").stringValue("mode"))
        status?.let { assertEquals(it, case.objectValue("transport").intValue("status")) }
        messageOrError?.let {
            val transport = case.objectValue("transport")
            if (transportMode == "pftpResponseError") assertEquals(it, transport.stringValue("message")) else assertEquals(it, transport.stringValue("error"))
        }
        payloadHex?.let {
            if (operation == "writeFile") assertEquals(it, case.stringValue("payloadHex")) else assertEquals(it, case.objectValue("transport").stringValue("payloadHex"))
        }
        assertEquals(command, expectedCase.stringValue("command"))
        assertEquals(path, expectedCase.stringValue("path"))
        assertEquals(outcome, expectedCase.stringValue("outcome"))
        status?.let { assertEquals(it, expectedCase.intValue("status")) }
        messageOrError?.let {
            if (outcome == "response-error") assertEquals(it, expectedCase.stringValue("message")) else if (outcome == "transport-error" || outcome == "write-stream-error") assertEquals(it, expectedCase.stringValue("error"))
        }
        if (operation == "writeFile") assertEquals("01020304", expectedCase.stringValue("capturedPayloadHex"))
    }

    private val requiredFileRuntimeErrorCaseIds = listOf(
        "directory-list-response-error-103",
        "directory-list-malformed-payload",
        "read-file-transport-error",
        "write-file-stream-error-after-header",
        "delete-file-response-error"
    )

    private val requiredFileRuntimeErrorFamilies = listOf(
        "directory-missing-status-103",
        "directory-malformed-payload-parse-failure",
        "read-file-transport-error",
        "write-file-put-header-before-stream-error",
        "write-file-payload-capture-before-stream-error",
        "delete-file-response-error-status-message",
        "command-path-capture-for-every-operation",
        "facade-error-mapping-pinned",
        "platform-runtime-vector-reference-gate",
        "compile-verification-gate"
    )

    private val fileRuntimeErrorMigrationRequirement = "Before moving file utility orchestration into common KMP code, implement fake PFTP request and write-stream tests that cover malformed directory payloads, request-level transport errors, response-error status mapping, and write-stream failures after the PUT header is prepared."

    private val fileRuntimeErrorCommonDecision = "Preserve documented platform-specific policies only where required for public compatibility; otherwise normalize shared runtime errors to typed request, parse, response, and write-stream failures."

    private val fileRuntimeErrorReadinessCommonDecision = "File runtime error migration may proceed only after runtime-error-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS file tests continue to reference the same vectors, directory missing status 103, malformed directory payload parse failure, read transport errors, write PUT header and payload capture before stream failure, delete response-error status/message mapping, command/path capture, public facade error mapping stays pinned through file-facade-runtime-policy.json, and the shared tests are compile-verified."

    private fun assertOutcome(caseId: String, expected: String, outcome: PolarFileRuntimeErrorPlan) {
        assertEquals(expected.stringValue("outcome"), outcome.outcome, caseId)
        outcome.status?.let { status -> assertEquals(expected.intValue("status"), status, caseId) }
        outcome.message?.let { message -> assertEquals(expected.stringValue("message"), message, caseId) }
        outcome.error?.let { error -> assertEquals(expected.stringValue("error"), error, caseId) }
    }

    private class FakeFileUtility(
        private val directoriesJson: String
    ) {
        fun listFiles(rootPath: String, recurseDeep: Boolean, condition: String): List<String> {
            val normalizedRoot = rootPath.normalizedDirectoryPath()
            val emitted = mutableListOf<String>()
            visit(normalizedRoot, recurseDeep, condition, emitted)
            return emitted
        }

        private fun visit(directory: String, recurseDeep: Boolean, condition: String, emitted: MutableList<String>) {
            directoryEntries(directory).forEach { entry ->
                val fullPath = directory + entry.name
                if (entry.isDirectory) {
                    if (!recurseDeep && entry.matches(condition)) {
                        emitted += fullPath
                    }
                    if (recurseDeep) {
                        visit(fullPath, recurseDeep, condition, emitted)
                    }
                } else if (entry.matches(condition)) {
                    emitted += fullPath
                }
            }
        }

        private fun directoryEntries(directory: String): List<FileEntry> {
            val key = "\"$directory\""
            val keyIndex = directoriesJson.indexOf(key)
            if (keyIndex < 0) return emptyList()
            val arrayStart = directoriesJson.indexOf('[', keyIndex)
            val arrayEnd = directoriesJson.balancedEnd(arrayStart, '[', ']')
            val arrayJson = "{\"entries\":${directoriesJson.substring(arrayStart, arrayEnd + 1)}}"
            return arrayJson.objectArray("entries").map { entry ->
                FileEntry(entry.stringValue("name"), entry.intValue("size"))
            }
        }

        private fun FileEntry.matches(condition: String): Boolean {
            return when (condition) {
                "include-all" -> true
                "entry-name-contains-dot" -> name.contains(".")
                else -> error("Unsupported file-list condition $condition")
            }
        }
    }

    private fun String.normalizedDirectoryPath(): String {
        val prefixed = if (startsWith("/")) this else "/$this"
        return if (prefixed.endsWith("/")) prefixed else "$prefixed/"
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
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

    private data class FileEntry(
        val name: String,
        val size: Int
    ) {
        val isDirectory: Boolean
            get() = name.endsWith("/")
    }

    private fun String.optionalIntValue(field: String): Int? {
        val valueStart = "\"$field\"".toRegex().find(this)?.range?.last?.plus(1) ?: return null
        val match = Regex("-?\\d+").find(this, valueStart) ?: return null
        return match.value.toInt()
    }
}
