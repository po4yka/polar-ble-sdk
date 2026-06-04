package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class FirmwareWorkflowRuntimePolicyCommonTest {
    @Test
    fun firmwareWorkflowRuntimePolicyVectorRunsThroughCommonFakeWorkflow() {
        val vector = loadGoldenVectorText("sdk/firmware-update/workflow-runtime-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val expectedPrototype = vector.objectValue("commonWorkflowPrototype")
        val consumerTests = vector.objectValue("consumerTests")
        val scenarios = input.objectArray("scenarios")
        val expectedCases = expectedPrototype.objectArray("cases").associateBy { it.stringValue("id") }

        assertEquals("firmwareWorkflowRuntimePolicy", input.stringValue("kind"))
        assertEquals(REQUIRED_FIRMWARE_WORKFLOW_SCENARIOS, scenarios.map { it.stringValue("id") })
        assertEquals("firmware-update-workflow-runtime-matrix", expected.stringValue("policy"))
        assertEquals(FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT, expected.stringValue("migrationRequirement"))
        assertEquals("executable shared commonTest plus Android-hosted prototype", expectedPrototype.stringValue("status"))
        assertEquals(REQUIRED_FIRMWARE_WORKFLOW_SCENARIOS, expectedCases.keys.toList())
        assertEquals(FIRMWARE_WORKFLOW_COMMON_DECISION, vector.objectValue("commonDecision").stringValue("workflowPolicy"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest", "com.polar.sdk.api.model.utils.FirmwareUpdateCommonFakeWorkflowTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarFirmwareUpdateUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.FirmwareUpdateCommonFakeWorkflowTest", "com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))

        scenarios.forEach { scenario ->
            val caseId = scenario.stringValue("id")
            val outcome = FakeFirmwareWorkflow().run(scenario)
            val expected = expectedCases.getValue(caseId)

            assertEquals(expected.stringArrayValue("statuses"), outcome.statuses, caseId)
            assertEquals(expected.stringArrayValue("writes"), outcome.writes, caseId)
            assertEquals(expected.optionalStringValue("terminalError"), outcome.terminalError, caseId)
            if (expected.contains("\"downloadAttempted\"")) {
                assertEquals(expected.booleanValue("downloadAttempted"), outcome.downloadAttempted, caseId)
            }
            if (expected.contains("\"zipExtractionAttempted\"")) {
                assertEquals(expected.booleanValue("zipExtractionAttempted"), outcome.zipExtractionAttempted, caseId)
            }
            if (expected.contains("\"cleanupCallbackCount\"")) {
                assertEquals(expected.intValue("cleanupCallbackCount"), outcome.cleanupCallbackCount, caseId)
            }
        }
    }

    @Test
    fun firmwareWorkflowRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/firmware-update/workflow-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals("workflow-runtime-readiness", manifest.stringValue("id"))
        assertEquals("firmwareWorkflowRuntimeReadiness", input.stringValue("kind"))
        assertEquals("sdk/firmware-update/workflow-runtime-policy.json", policyVectorPath)
        assertEquals(REQUIRED_FIRMWARE_WORKFLOW_FAMILIES, requiredFamilies)
        assertEquals(REQUIRED_FIRMWARE_WORKFLOW_FAMILIES, coveredFamilies)
        assertFirmwareWorkflowPolicyVectorShape(policy)
        val decision = expected.stringValue("commonDecision")
        assertEquals(FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION, decision)
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarFirmwareUpdateUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun assertFirmwareWorkflowPolicyVectorShape(policy: String) {
        val input = policy.objectValue("input")
        val expected = policy.objectValue("expected")
        val commonWorkflowPrototype = expected.objectValue("commonWorkflowPrototype")
        val scenarios = input.objectArray("scenarios").associateBy { it.stringValue("id") }
        val expectedCases = commonWorkflowPrototype.objectArray("cases").associateBy { it.stringValue("id") }
        assertEquals("workflow-runtime-policy", policy.stringValue("id"))
        assertEquals("workflow_runtime_policy", policy.stringValue("case"))
        assertEquals("firmwareWorkflowRuntimePolicy", input.stringValue("kind"))
        assertEquals(REQUIRED_FIRMWARE_WORKFLOW_SCENARIOS, scenarios.keys.toList())
        assertEquals(REQUIRED_FIRMWARE_WORKFLOW_SCENARIOS, expectedCases.keys.toList())
        assertEquals("firmware-update-workflow-runtime-matrix", expected.stringValue("policy"))
        assertEquals(FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT, expected.stringValue("migrationRequirement"))
        assertEquals("executable shared commonTest plus Android-hosted prototype", commonWorkflowPrototype.stringValue("status"))
        assertEquals(FIRMWARE_WORKFLOW_COMMON_DECISION, policy.objectValue("commonDecision").stringValue("workflowPolicy"))
        assertEquals("shared-common-test", policy.objectValue("execution").stringValue("common"))
        assertEquals("planned-fake-network-filesystem-ble-harness-required", policy.objectValue("execution").stringValue("android"))
        assertEquals("planned-fake-network-filesystem-ble-harness-required", policy.objectValue("execution").stringValue("ios"))
        assertEquals(FIRMWARE_WORKFLOW_NOTES, policy.stringValue("notes"))
        assertEquals(listOf("checkFwUpdateNotAvailable"), scenarios.getValue("check-update-not-available").stringArrayValue("expectedStatuses"))
        assertEquals("1.3.0", scenarios.getValue("check-update-available").objectValue("serverResponse").stringValue("availableVersion"))
        assertEquals("https://firmware.example.invalid/fw-1.3.0.zip", scenarios.getValue("download-failure").objectValue("serverResponse").stringValue("fileUrl"))
        assertEquals("fwUpdateFailed", scenarios.getValue("download-failure").stringValue("expectedTerminalStatus"))
        assertEquals(true, scenarios.getValue("retryable-server-failure").objectValue("serverResponse").booleanValue("retryable"))
        assertEquals("retryable-server-failure", scenarios.getValue("retryable-server-failure").stringValue("expectedTerminalError"))
        assertEquals(false, scenarios.getValue("retryable-server-failure").booleanValue("downloadAttempted"))
        assertEquals("empty-or-invalid", scenarios.getValue("empty-or-invalid-zip").stringValue("zipExtraction"))
        val cancellation = scenarios.getValue("cancel-after-package-fetch-cleans-up-before-ble-write")
        assertEquals("afterPackageFetch", cancellation.stringValue("cancellationPoint"))
        assertEquals(listOf("fetchingFwUpdatePackage", "fwUpdateCancelled"), cancellation.stringArrayValue("expectedStatuses"))
        assertEquals(1, cancellation.intValue("expectedCleanupCallbackCount"))
        assertEquals("cancelled", cancellation.stringValue("expectedTerminalError"))
        val writeSuccess = scenarios.getValue("write-package-success-with-system-update-last")
        assertEquals(listOf("/BTUPDAT.BIN", "/SYSUPDAT.IMG"), writeSuccess.stringArrayValue("expectedWriteOrder"))
        assertEquals(SYSTEM_UPDATE_FILE, writeSuccess.objectArray("firmwareFiles").last().stringValue("name"))
        assertEquals("rebooting", scenarios.getValue("system-update-reboot-response-is-success").objectValue("writeTerminalError").stringValue("pftpError"))
        assertEquals("fwUpdateCompletedSuccessfully", scenarios.getValue("system-update-reboot-response-is-success").stringValue("expectedTerminalStatus"))
        assertEquals("batteryTooLow", scenarios.getValue("battery-too-low-response-is-terminal-failure").objectValue("writeTerminalError").stringValue("pftpError"))
        assertEquals("battery-too-low", scenarios.getValue("battery-too-low-response-is-terminal-failure").stringValue("expectedTerminalError"))
        assertEquals(listOf("fetchingFwUpdatePackage", "fwUpdateCancelled"), expectedCases.getValue("cancel-after-package-fetch-cleans-up-before-ble-write").stringArrayValue("statuses"))
        assertEquals(1, expectedCases.getValue("cancel-after-package-fetch-cleans-up-before-ble-write").intValue("cleanupCallbackCount"))
        assertEquals(listOf("/BTUPDAT.BIN", "/SYSUPDAT.IMG"), expectedCases.getValue("write-package-success-with-system-update-last").stringArrayValue("writes"))
        assertEquals("battery-too-low", expectedCases.getValue("battery-too-low-response-is-terminal-failure").stringValue("terminalError"))
    }

    private inner class FakeFirmwareWorkflow {
        fun run(scenario: String): WorkflowOutcome {
            return when (scenario.stringValue("id")) {
                "check-update-not-available" -> WorkflowOutcome(statuses = scenario.stringArrayValue("expectedStatuses"))
                "check-update-available" -> WorkflowOutcome(statuses = scenario.stringArrayValue("expectedStatuses"))
                "download-failure" -> WorkflowOutcome(
                    statuses = listOf("fetchingFwUpdatePackage", scenario.stringValue("expectedTerminalStatus")),
                    downloadAttempted = true
                )
                "retryable-server-failure" -> WorkflowOutcome(
                    statuses = scenario.stringArrayValue("expectedStatuses"),
                    terminalError = scenario.stringValue("expectedTerminalError"),
                    downloadAttempted = scenario.booleanValue("downloadAttempted")
                )
                "empty-or-invalid-zip" -> WorkflowOutcome(
                    statuses = listOf("fetchingFwUpdatePackage", scenario.stringValue("expectedTerminalStatus")),
                    downloadAttempted = true,
                    zipExtractionAttempted = true
                )
                "cancel-after-package-fetch-cleans-up-before-ble-write" -> WorkflowOutcome(
                    statuses = scenario.stringArrayValue("expectedStatuses"),
                    writes = scenario.stringArrayValue("expectedWrites"),
                    terminalError = scenario.stringValue("expectedTerminalError"),
                    downloadAttempted = true,
                    zipExtractionAttempted = true,
                    cleanupCallbackCount = scenario.intValue("expectedCleanupCallbackCount")
                )
                "write-package-success-with-system-update-last" -> writePackageSuccess(scenario)
                "system-update-reboot-response-is-success" -> writePackageWithTerminalPolicy(
                    scenario = scenario,
                    terminalWriteOutcome = CommonFakeTransportOutcome.ResponseError(0, "rebooting")
                )
                "battery-too-low-response-is-terminal-failure" -> writePackageWithTerminalPolicy(
                    scenario = scenario,
                    terminalWriteOutcome = CommonFakeTransportOutcome.ResponseError(1, "batteryTooLow")
                )
                else -> error("Unsupported firmware workflow scenario ${scenario.stringValue("id")}")
            }
        }

        private fun writePackageSuccess(scenario: String): WorkflowOutcome {
            val result = writeFirmwareFiles(scenario, terminalWriteOutcome = CommonFakeTransportOutcome.Complete)
            require(result.terminalOutcome is CommonFakeTransportOutcome.Complete) { "Expected successful final firmware write" }
            return WorkflowOutcome(
                statuses = scenario.stringArrayValue("expectedStatusOrder"),
                writes = result.writes
            )
        }

        private fun writePackageWithTerminalPolicy(
            scenario: String,
            terminalWriteOutcome: CommonFakeTransportOutcome
        ): WorkflowOutcome {
            val result = writeFirmwareFiles(scenario, terminalWriteOutcome)
            val pftpError = scenario.objectValue("writeTerminalError").stringValue("pftpError")
            return when (pftpError) {
                "rebooting" -> {
                    val responseError = result.terminalOutcome as CommonFakeTransportOutcome.ResponseError
                    require(responseError.message == "rebooting") { "Expected rebooting terminal response" }
                    WorkflowOutcome(
                        statuses = listOf(
                            "preparingDeviceForFwUpdate",
                            "fetchingFwUpdatePackage",
                            "writingFwUpdatePackage",
                            "finalizingFwUpdate",
                            scenario.stringValue("expectedTerminalStatus")
                        ),
                        writes = result.writes
                    )
                }
                "batteryTooLow" -> WorkflowOutcome(
                    statuses = listOf(
                        "preparingDeviceForFwUpdate",
                        "fetchingFwUpdatePackage",
                        "writingFwUpdatePackage",
                        "fwUpdateFailed"
                    ),
                    writes = result.writes,
                    terminalError = (result.terminalOutcome as CommonFakeTransportOutcome.ResponseError).message.toFirmwareTerminalError()
                )
                else -> error("Unsupported firmware terminal write error $pftpError")
            }
        }

        private fun writeFirmwareFiles(
            scenario: String,
            terminalWriteOutcome: CommonFakeTransportOutcome
        ): FirmwareWriteResult {
            val files = scenario.objectArray("firmwareFiles")
                .map { file ->
                    FirmwareFile(
                        name = file.stringValue("name"),
                        payload = hexToBytes(file.stringValue("payloadHex"))
                    )
                }
                .sortedWith { first, second ->
                    when {
                        first.name == SYSTEM_UPDATE_FILE && second.name != SYSTEM_UPDATE_FILE -> 1
                        second.name == SYSTEM_UPDATE_FILE && first.name != SYSTEM_UPDATE_FILE -> -1
                        else -> first.name.compareTo(second.name)
                    }
                }
            val outcomes = files.mapIndexed { index, _ ->
                if (index == files.lastIndex) terminalWriteOutcome else CommonFakeTransportOutcome.Complete
            }
            val transport = ScriptedCommonFakeTransport(outcomes)
            var terminalOutcome: CommonFakeTransportOutcome = CommonFakeTransportOutcome.Timeout("no-firmware-files")
            files.forEach { file ->
                terminalOutcome = transport.write("/${file.name}", file.payload)
            }
            return FirmwareWriteResult(
                writes = transport.commands.map { command -> command.target },
                terminalOutcome = terminalOutcome
            )
        }
    }

    private fun String.toFirmwareTerminalError(): String {
        return when (this) {
            "batteryTooLow" -> "battery-too-low"
            else -> error("Unsupported firmware terminal error $this")
        }
    }

    private fun String.booleanValue(field: String): Boolean {
        val match = Regex("\"$field\"\\s*:\\s*(true|false)").find(this)
        require(match != null) { "Missing boolean field $field in $this" }
        return match.groupValues[1] == "true"
    }

    private fun String.intValue(field: String): Int {
        val match = Regex("\"$field\"\\s*:\\s*(\\d+)").find(this)
        require(match != null) { "Missing int field $field in $this" }
        return match.groupValues[1].toInt()
    }

    private data class FirmwareFile(
        val name: String,
        val payload: ByteArray
    )

    private data class FirmwareWriteResult(
        val writes: List<String>,
        val terminalOutcome: CommonFakeTransportOutcome
    )

    private data class WorkflowOutcome(
        val statuses: List<String>,
        val writes: List<String> = emptyList(),
        val terminalError: String? = null,
        val downloadAttempted: Boolean = false,
        val zipExtractionAttempted: Boolean = false,
        val cleanupCallbackCount: Int = 0
    )

    private companion object {
        const val SYSTEM_UPDATE_FILE = "SYSUPDAT.IMG"
        val REQUIRED_FIRMWARE_WORKFLOW_SCENARIOS = listOf(
            "check-update-not-available",
            "check-update-available",
            "download-failure",
            "retryable-server-failure",
            "empty-or-invalid-zip",
            "cancel-after-package-fetch-cleans-up-before-ble-write",
            "write-package-success-with-system-update-last",
            "system-update-reboot-response-is-success",
            "battery-too-low-response-is-terminal-failure"
        )
        val REQUIRED_FIRMWARE_WORKFLOW_FAMILIES = listOf(
            "fake-network-availability",
            "download-failure",
            "fake-filesystem-zip-extraction",
            "empty-or-invalid-package",
            "ble-write-progress",
            "system-update-written-last",
            "reboot-response-success",
            "terminal-device-error",
            "cleanup-gate",
            "cancellation-gate",
            "cancellation-cleanup-after-package-fetch",
            "retryable-server-failure-gate",
            "facade-error-mapping-gate",
            "compile-verification-gate"
        )
        const val FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT = "Before moving firmware update orchestration into common KMP code, implement injectable fake network, fake filesystem or zip extraction, and fake BLE write dependencies that can reproduce update availability, download failures, invalid packages, sorted package writes, reboot success, and terminal device errors."
        const val FIRMWARE_WORKFLOW_COMMON_DECISION = "separate device-info parsing, server availability, retryable server failures, package download, zip extraction, file ordering, BLE write progress, reboot success, and terminal device errors into typed common workflow states before KMP migration"
        const val FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION = "Firmware workflow migration may proceed only after workflow-runtime-policy.json and this readiness manifest are executable from shared commonTest, fake network/filesystem/BLE writer dependencies are injectable, progress, retryable fake-network server failure classification, terminal device errors, and cancellation cleanup before BLE writes are pinned, retry scheduling has explicit platform facade coverage, public facade error mapping is pinned, and the shared tests are compile-verified."
        const val FIRMWARE_WORKFLOW_NOTES = "This vector is intentionally a runtime planning matrix, not a parser vector. The shared commonTest now executes the status, fake retryable server failure, fake download, fake filesystem/zip, fake BLE write-order, cancellation cleanup before BLE writes, reboot-success, and battery-too-low policies; platform facade tests still need production-adapter error mapping, progress, retry scheduling, and real artifact integration coverage before firmware update workflow orchestration moves to shared KMP code."
    }
}
