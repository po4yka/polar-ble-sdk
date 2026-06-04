package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader

class FirmwareUpdateCommonFakeWorkflowTest {

    @Test
    fun `firmware workflow runtime policy vector is executable by common fake workflow prototype`() {
        val vector = loadVector("sdk/firmware-update/workflow-runtime-policy.json")
        val expectedCases = vector.getAsJsonObject("expected")
            .getAsJsonObject("commonWorkflowPrototype")
            .getAsJsonArray("cases")
            .map { it.asJsonObject }
            .associateBy { it.get("id").asString }

        vector.getAsJsonObject("input").getAsJsonArray("scenarios").map { it.asJsonObject }.forEach { scenario ->
            val caseId = scenario.get("id").asString
            val outcome = FakeFirmwareWorkflow().run(scenario)
            val expected = expectedCases.getValue(caseId)

            assertEquals(caseId, expected.getAsJsonArray("statuses").map { it.asString }, outcome.statuses)
            assertEquals(caseId, expected.getAsJsonArray("writes").map { it.asString }, outcome.writes)
            if (expected.has("terminalError")) {
                assertEquals(caseId, expected.get("terminalError").asString, outcome.terminalError)
            } else {
                assertEquals(caseId, null, outcome.terminalError)
            }
            if (expected.has("downloadAttempted")) {
                assertEquals(caseId, expected.get("downloadAttempted").asBoolean, outcome.downloadAttempted)
            }
            if (expected.has("zipExtractionAttempted")) {
                assertEquals(caseId, expected.get("zipExtractionAttempted").asBoolean, outcome.zipExtractionAttempted)
            }
            if (expected.has("cleanupCallbackCount")) {
                assertEquals(caseId, expected.get("cleanupCallbackCount").asInt, outcome.cleanupCallbackCount)
            }
        }
    }

    @Test
    fun `firmware workflow runtime policy keeps shared migration requirements explicit`() {
        val vector = loadVector("sdk/firmware-update/workflow-runtime-policy.json")
        val expected = vector.getAsJsonObject("expected")
        val prototype = expected.getAsJsonObject("commonWorkflowPrototype")

        assertTrue(prototype.get("status").asString.contains("prototype"))
        assertTrue(expected.get("policy").asString.isNotBlank())
        assertTrue(expected.get("migrationRequirement").asString.contains("fake network"))
        assertTrue(expected.get("migrationRequirement").asString.contains("fake filesystem"))
        assertTrue(expected.get("migrationRequirement").asString.contains("fake BLE"))
    }

    private class FakeFirmwareWorkflow {
        fun run(scenario: JsonObject): WorkflowOutcome {
            return when (scenario.get("id").asString) {
                "check-update-not-available" -> WorkflowOutcome(statuses = listOf("checkFwUpdateNotAvailable"))
                "check-update-available" -> WorkflowOutcome(statuses = listOf("checkFwUpdateAvailable"))
                "download-failure" -> WorkflowOutcome(
                    statuses = listOf("fetchingFwUpdatePackage", "fwUpdateFailed"),
                    downloadAttempted = true
                )
                "retryable-server-failure" -> WorkflowOutcome(
                    statuses = scenario.getAsJsonArray("expectedStatuses").map { it.asString },
                    terminalError = scenario.get("expectedTerminalError").asString,
                    downloadAttempted = scenario.get("downloadAttempted").asBoolean
                )
                "empty-or-invalid-zip" -> WorkflowOutcome(
                    statuses = listOf("fetchingFwUpdatePackage", "fwUpdateNotAvailable"),
                    downloadAttempted = true,
                    zipExtractionAttempted = true
                )
                "cancel-after-package-fetch-cleans-up-before-ble-write" -> WorkflowOutcome(
                    statuses = scenario.getAsJsonArray("expectedStatuses").map { it.asString },
                    writes = scenario.getAsJsonArray("expectedWrites").map { it.asString },
                    terminalError = scenario.get("expectedTerminalError").asString,
                    downloadAttempted = true,
                    zipExtractionAttempted = true,
                    cleanupCallbackCount = scenario.get("expectedCleanupCallbackCount").asInt
                )
                "write-package-success-with-system-update-last" -> writePackageSuccess(scenario)
                "system-update-reboot-response-is-success" -> WorkflowOutcome(
                    statuses = listOf(
                        "preparingDeviceForFwUpdate",
                        "fetchingFwUpdatePackage",
                        "writingFwUpdatePackage",
                        "finalizingFwUpdate",
                        "fwUpdateCompletedSuccessfully"
                    ),
                    writes = firmwareWriteOrder(scenario)
                )
                "battery-too-low-response-is-terminal-failure" -> WorkflowOutcome(
                    statuses = listOf(
                        "preparingDeviceForFwUpdate",
                        "fetchingFwUpdatePackage",
                        "writingFwUpdatePackage",
                        "fwUpdateFailed"
                    ),
                    writes = firmwareWriteOrder(scenario),
                    terminalError = "battery-too-low"
                )
                else -> error("Unsupported firmware workflow scenario ${scenario.get("id").asString}")
            }
        }

        private fun writePackageSuccess(scenario: JsonObject): WorkflowOutcome {
            val expectedStatusOrder = scenario.getAsJsonArray("expectedStatusOrder").map { it.asString }
            return WorkflowOutcome(
                statuses = expectedStatusOrder,
                writes = firmwareWriteOrder(scenario)
            )
        }

        private fun firmwareWriteOrder(scenario: JsonObject): List<String> {
            return scenario.getAsJsonArray("firmwareFiles")
                .map { it.asJsonObject.get("name").asString }
                .sortedWith { first, second ->
                    when {
                        first.contains("SYSUPDAT.IMG") && !second.contains("SYSUPDAT.IMG") -> 1
                        second.contains("SYSUPDAT.IMG") && !first.contains("SYSUPDAT.IMG") -> -1
                        else -> first.compareTo(second)
                    }
                }
                .map { "/$it" }
        }
    }

    private data class WorkflowOutcome(
        val statuses: List<String>,
        val writes: List<String> = emptyList(),
        val terminalError: String? = null,
        val downloadAttempted: Boolean = false,
        val zipExtractionAttempted: Boolean = false,
        val cleanupCallbackCount: Int = 0
    )

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
}
