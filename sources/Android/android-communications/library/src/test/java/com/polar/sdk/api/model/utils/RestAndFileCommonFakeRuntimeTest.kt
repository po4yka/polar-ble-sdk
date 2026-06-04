package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import protocol.PftpResponse.PbPFtpDirectory
import java.io.File
import java.io.FileReader

class RestAndFileCommonFakeRuntimeTest {

    @Test
    fun `REST request transport policy vector is executable by common fake runtime prototype`() {
        val vector = loadVector("sdk/rest-service/rest-request-transport-policy.json")
        val requests = vector.getAsJsonObject("input").getAsJsonArray("requests").map { it.asJsonObject }
        val expectedCases = expectedCases(vector)
        val runtime = FakePftpRuntime()

        assertEquals(REST_REQUEST_TRANSPORT_SCENARIO_IDS, requests.map { it.get("id").asString })
        assertEquals(REST_REQUEST_TRANSPORT_SCENARIO_IDS, expectedCases.keys.toList())

        requests.forEach { request ->
            val id = request.get("id").asString
            val outcome = runtime.restGet(request.get("path").asString, request.getAsJsonObject("transport"))
            val expected = expectedCases.getValue(id)

            assertEquals(id, expected.get("command").asString, runtime.commands.last().command)
            assertEquals(id, expected.get("path").asString, runtime.commands.last().path)
            assertOutcome(id, expected, outcome)
        }
    }

    @Test
    fun `file utility runtime error policy vector is executable by common fake runtime prototype`() {
        val vector = loadVector("sdk/file-utils/runtime-error-policy.json")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }
        val expectedCases = expectedCases(vector)
        val runtime = FakePftpRuntime()

        assertEquals(FILE_RUNTIME_ERROR_CASE_IDS, cases.map { it.get("id").asString })
        assertEquals(FILE_RUNTIME_ERROR_CASE_IDS, expectedCases.keys.toList())

        cases.forEach { testCase ->
            val id = testCase.get("id").asString
            val outcome = when (testCase.get("operation").asString) {
                "listFiles" -> runtime.listFiles(testCase.get("path").asString, testCase.getAsJsonObject("transport"))
                "readFile" -> runtime.readFile(testCase.get("path").asString, testCase.getAsJsonObject("transport"))
                "writeFile" -> runtime.writeFile(
                    testCase.get("path").asString,
                    hexToBytes(testCase.get("payloadHex").asString),
                    testCase.getAsJsonObject("transport")
                )
                "removeSingleFile" -> runtime.removeSingleFile(testCase.get("path").asString, testCase.getAsJsonObject("transport"))
                else -> error("Unsupported file utility operation ${testCase.get("operation").asString}")
            }
            val expected = expectedCases.getValue(id)

            assertEquals(id, expected.get("command").asString, runtime.commands.last().command)
            assertEquals(id, expected.get("path").asString, runtime.commands.last().path)
            if (expected.has("capturedPayloadHex")) {
                assertEquals(id, expected.get("capturedPayloadHex").asString, runtime.commands.last().payloadHex)
            }
            assertOutcome(id, expected, outcome)
        }
    }

    private fun assertOutcome(caseId: String, expected: JsonObject, outcome: RuntimeOutcome) {
        assertEquals(caseId, expected.get("outcome").asString, outcome.name)
        when (outcome) {
            is RuntimeOutcome.ResponseError -> {
                assertEquals(caseId, expected.get("status").asInt, outcome.status)
                assertEquals(caseId, expected.get("message").asString, outcome.message)
            }
            is RuntimeOutcome.TransportError -> assertEquals(caseId, expected.get("error").asString, outcome.error)
            is RuntimeOutcome.WriteStreamError -> assertEquals(caseId, expected.get("error").asString, outcome.error)
            is RuntimeOutcome.DirectoryMissing -> assertEquals(caseId, expected.get("status").asInt, outcome.status)
            is RuntimeOutcome.SuccessBytes -> error("Unexpected raw success bytes for $caseId")
            RuntimeOutcome.DirectoryParseFailure,
            RuntimeOutcome.EmptySuccessPolicyRequired -> Unit
        }
    }

    private fun expectedCases(vector: JsonObject): Map<String, JsonObject> {
        return vector.getAsJsonObject("expected")
            .getAsJsonObject("commonRuntimePrototype")
            .getAsJsonArray("cases")
            .map { it.asJsonObject }
            .associateBy { it.get("id").asString }
    }

    private class FakePftpRuntime {
        val commands = mutableListOf<CapturedCommand>()

        fun restGet(path: String, transport: JsonObject): RuntimeOutcome {
            commands += CapturedCommand("GET", path)
            return requestOutcome(transport, emptySuccess = RuntimeOutcome.EmptySuccessPolicyRequired)
        }

        fun listFiles(path: String, transport: JsonObject): RuntimeOutcome {
            commands += CapturedCommand("GET", path)
            val response = requestOutcome(transport, emptySuccess = RuntimeOutcome.DirectoryParseFailure)
            if (response is RuntimeOutcome.ResponseError && response.status == 103) return RuntimeOutcome.DirectoryMissing(response.status)
            if (response !is RuntimeOutcome.SuccessBytes) return response
            return try {
                PbPFtpDirectory.parseFrom(response.bytes)
                RuntimeOutcome.DirectoryParseFailure
            } catch (_: Throwable) {
                RuntimeOutcome.DirectoryParseFailure
            }
        }

        fun readFile(path: String, transport: JsonObject): RuntimeOutcome {
            commands += CapturedCommand("GET", path)
            return requestOutcome(transport, emptySuccess = RuntimeOutcome.EmptySuccessPolicyRequired).withoutSuccessBytes()
        }

        fun writeFile(path: String, payload: ByteArray, transport: JsonObject): RuntimeOutcome {
            commands += CapturedCommand("PUT", path, payload.toHex())
            return when (transport.get("mode").asString) {
                "writeStreamError" -> RuntimeOutcome.WriteStreamError(transport.get("error").asString)
                else -> requestOutcome(transport, emptySuccess = RuntimeOutcome.EmptySuccessPolicyRequired).withoutSuccessBytes()
            }
        }

        fun removeSingleFile(path: String, transport: JsonObject): RuntimeOutcome {
            commands += CapturedCommand("REMOVE", path)
            return requestOutcome(transport, emptySuccess = RuntimeOutcome.EmptySuccessPolicyRequired).withoutSuccessBytes()
        }

        private fun requestOutcome(transport: JsonObject, emptySuccess: RuntimeOutcome): RuntimeOutcome {
            return when (transport.get("mode").asString) {
                "pftpResponseError" -> RuntimeOutcome.ResponseError(transport.get("status").asInt, transport.get("message").asString)
                "transportError" -> RuntimeOutcome.TransportError(transport.get("error").asString)
                "success" -> {
                    val bytes = hexToBytes(transport.get("payloadHex").asString)
                    if (bytes.isEmpty()) emptySuccess else RuntimeOutcome.SuccessBytes(bytes)
                }
                else -> error("Unsupported transport mode ${transport.get("mode").asString}")
            }
        }

        private fun RuntimeOutcome.withoutSuccessBytes(): RuntimeOutcome {
            return when (this) {
                is RuntimeOutcome.SuccessBytes -> RuntimeOutcome.EmptySuccessPolicyRequired
                else -> this
            }
        }
    }

    private data class CapturedCommand(
        val command: String,
        val path: String,
        val payloadHex: String? = null
    )

    private sealed class RuntimeOutcome(val name: String) {
        data class ResponseError(val status: Int, val message: String) : RuntimeOutcome("response-error")
        data class TransportError(val error: String) : RuntimeOutcome("transport-error")
        data class WriteStreamError(val error: String) : RuntimeOutcome("write-stream-error")
        data class DirectoryMissing(val status: Int) : RuntimeOutcome("directory-missing")
        data class SuccessBytes(val bytes: ByteArray) : RuntimeOutcome("success-bytes")
        object DirectoryParseFailure : RuntimeOutcome("directory-parse-failure")
        object EmptySuccessPolicyRequired : RuntimeOutcome("requires-empty-response-policy")
    }

    private fun loadVector(relativePath: String): JsonObject {
        val file = findRepositoryRoot()
            .resolve("testdata/golden-vectors")
            .resolve(relativePath)
        return FileReader(file).use { reader ->
            JsonParser().parse(reader).asJsonObject
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

    private companion object {
        val REST_REQUEST_TRANSPORT_SCENARIO_IDS = listOf(
            "service-list-request-error-payload",
            "service-description-request-error-payload",
            "service-list-empty-transport-response",
            "service-description-empty-transport-response"
        )

        val FILE_RUNTIME_ERROR_CASE_IDS = listOf(
            "directory-list-response-error-103",
            "directory-list-malformed-payload",
            "read-file-transport-error",
            "write-file-stream-error-after-header",
            "delete-file-response-error"
        )
    }
}

private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

private fun ByteArray.toHex(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte) }
}
