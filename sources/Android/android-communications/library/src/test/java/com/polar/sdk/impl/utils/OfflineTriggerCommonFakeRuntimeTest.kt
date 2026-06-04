package com.polar.sdk.impl.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader

class OfflineTriggerCommonFakeRuntimeTest {

    @Test
    fun `offline trigger runtime policy vector is executable by common fake runtime prototype`() {
        val vector = loadVector("sdk/offline-recording/trigger-runtime-policy.json")
        val scenarios = vector.getAsJsonObject("input").getAsJsonArray("scenarios").map { it.asJsonObject }
        val expectedCases = vector.getAsJsonObject("expected")
            .getAsJsonObject("commonRuntimePrototype")
            .getAsJsonArray("cases")
            .map { it.asJsonObject }
            .associateBy { it.get("id").asString }
        val input = vector.getAsJsonObject("input")

        assertEquals(TRIGGER_RUNTIME_SCENARIO_IDS, scenarios.map { it.get("id").asString })
        assertEquals(TRIGGER_RUNTIME_SCENARIO_IDS, expectedCases.keys.toList())

        scenarios.forEach { scenario ->
            val caseId = scenario.get("id").asString
            val outcome = FakeOfflineTriggerRuntime(input).run(scenario)
            val expected = expectedCases.getValue(caseId)

            assertEquals(caseId, expected.getAsJsonArray("operations").map { it.asString }, outcome.operations)
            assertEquals(caseId, expected.get("terminal").asString, outcome.terminal)
            if (expected.has("enabledFeatures")) {
                assertEquals(caseId, expected.getAsJsonArray("enabledFeatures").map { it.asString }, outcome.enabledFeatures)
            }
            if (expected.has("excludedFeatures")) {
                assertEquals(caseId, expected.getAsJsonArray("excludedFeatures").map { it.asString }, outcome.excludedFeatures)
            }
        }
    }

    @Test
    fun `offline trigger runtime policy keeps packet split explicit before migration`() {
        val vector = loadVector("sdk/offline-recording/trigger-runtime-policy.json")

        assertTrue(vector.get("notes").asString.contains("explicit length byte"))
        assertTrue(vector.get("notes").asString.contains("iOS appends setting and secret bytes directly"))
        assertTrue(vector.getAsJsonObject("expected").get("commonDecision").asString.contains("typed steps"))
    }

    private class FakeOfflineTriggerRuntime(private val input: JsonObject) {
        fun run(scenario: JsonObject): RuntimeOutcome {
            return when (scenario.get("operation").asString) {
                "setOfflineRecordingTrigger" -> setTrigger(scenario.getAsJsonObject("transport"))
                "getOfflineRecordingTriggerSetup" -> getTrigger(scenario.getAsJsonObject("transport"))
                else -> error("Unsupported offline trigger operation ${scenario.get("operation").asString}")
            }
        }

        private fun setTrigger(transport: JsonObject): RuntimeOutcome {
            val operations = mutableListOf("setMode:${input.getAsJsonObject("desiredTrigger").get("mode").asString}")
            if (transport.get("setMode").asString != "success") {
                return RuntimeOutcome(operations, "control-point-error")
            }

            operations += "getStatus"
            if (transport.get("getStatus").asString != "success") {
                return RuntimeOutcome(operations, "transport-error")
            }

            val settingResult = transport.get("setSettings").asString
            val desiredFeatures = desiredFeatures()
            val secretPresent = input.getAsJsonObject("desiredTrigger").getAsJsonObject("secret").get("present").asBoolean
            for (current in input.getAsJsonArray("currentDeviceTriggers").map { it.asJsonObject }) {
                val currentType = current.get("type").asString
                val desired = desiredFeatures[currentType] ?: desiredFeatures[if (currentType == "OFFLINE_HR") "HR" else currentType]
                operations += if (desired != null) {
                    val hasSettings = desired.get("selectedSettings") != null && desired.get("selectedSettings").isJsonObject
                    "setSetting:$currentType:enabled:${if (hasSettings) "settings" else "no-settings"}:${if (secretPresent) "secret" else "no-secret"}"
                } else {
                    "setSetting:$currentType:disabled"
                }
                if (settingResult != "success") {
                    return RuntimeOutcome(operations, "control-point-error")
                }
            }

            return RuntimeOutcome(operations, "success")
        }

        private fun getTrigger(transport: JsonObject): RuntimeOutcome {
            val operations = listOf("getStatus")
            if (transport.get("getStatus").asString != "success") {
                return RuntimeOutcome(operations, "transport-error")
            }
            val enabled = input.getAsJsonArray("currentDeviceTriggers")
                .map { it.asJsonObject }
                .filter { it.get("status").asString == "enabled" && it.get("type").asString != "GYRO" }
                .map { if (it.get("type").asString == "OFFLINE_HR") "HR" else it.get("type").asString }
            return RuntimeOutcome(
                operations = operations,
                terminal = "success",
                enabledFeatures = enabled,
                excludedFeatures = listOf("GYRO")
            )
        }

        private fun desiredFeatures(): Map<String, JsonObject> {
            return input.getAsJsonObject("desiredTrigger")
                .getAsJsonArray("features")
                .map { it.asJsonObject }
                .associateBy { it.get("type").asString }
        }
    }

    private data class RuntimeOutcome(
        val operations: List<String>,
        val terminal: String,
        val enabledFeatures: List<String> = emptyList(),
        val excludedFeatures: List<String> = emptyList()
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

    private companion object {
        val TRIGGER_RUNTIME_SCENARIO_IDS = listOf(
            "set-trigger-success-with-secret",
            "set-trigger-mode-error",
            "set-trigger-status-read-error",
            "set-trigger-setting-error",
            "get-trigger-success",
            "get-trigger-transport-error"
        )
    }
}
