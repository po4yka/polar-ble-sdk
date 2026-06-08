package com.polar.sharedtest

import com.polar.shared.runtime.PolarFirmwareWorkflowScenario
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import com.polar.shared.sdk.PolarFirmwareUpdateModels
import kotlin.test.Test
import kotlin.test.assertEquals

class FirmwareWorkflowRuntimePolicyCommonTest {
    @Test
    fun firmwareWorkflowRuntimePolicyVectorRunsThroughProductionCommonPlanner() {
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
        assertEquals("executable shared commonTest", expectedPrototype.stringValue("status"))
        assertEquals(REQUIRED_FIRMWARE_WORKFLOW_SCENARIOS, expectedCases.keys.toList())
        assertEquals(FIRMWARE_WORKFLOW_COMMON_DECISION, vector.objectValue("commonDecision").stringValue("workflowPolicy"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarFirmwareUpdateUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))

        scenarios.forEach { scenario ->
            val caseId = scenario.stringValue("id")
            val outcome = PolarWorkflowRuntimePlanning.planFirmwareWorkflow(scenario.toWorkflowScenario())
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
            if (caseId == "retryable-server-failure") {
                assertEquals(listOf(1000L, 2000L), outcome.retryDelaysMillis, caseId)
            } else {
                assertEquals(emptyList(), outcome.retryDelaysMillis, caseId)
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

    @Test
    fun firmwareWorkflowOrderingUsesSharedUtilityPolicy() {
        val files = listOf("TCHUPDAT.BIN", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN")

        assertEquals(PolarFirmwareUpdateModels.orderFirmwareFiles(files), PolarWorkflowRuntimePlanning.orderFirmwareFiles(files))
        assertEquals(listOf("TCHUPDAT.BIN", "APPUPDAT.BIN", "BTUPDAT.BIN", "SYSUPDAT.IMG"), PolarWorkflowRuntimePlanning.orderFirmwareFiles(files))
    }

    @Test
    fun firmwarePackagePayloadSelectionKeepsAndroidReadmeSkipPolicyInSharedPlanning() {
        assertEquals(false, PolarWorkflowRuntimePlanning.firmwarePackageEntryIsPayload("readme.txt"))
        assertEquals(true, PolarWorkflowRuntimePlanning.firmwarePackageEntryIsPayload("README.TXT"))
        assertEquals(true, PolarWorkflowRuntimePlanning.firmwarePackageEntryIsPayload("BTUPDAT.BIN"))
        assertEquals(true, PolarWorkflowRuntimePlanning.firmwarePackageEntryIsPayload("SYSUPDAT.IMG"))
        assertEquals(
            listOf("APPUPDAT.BIN", "BTUPDAT.BIN", "README.TXT", "SYSUPDAT.IMG"),
            PolarWorkflowRuntimePlanning.firmwarePayloadFileNames(listOf("readme.txt", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN", "README.TXT"))
        )
    }

    @Test
    fun firmwareRebootWaitSelectionUsesSharedSystemUpdateFilePolicy() {
        assertEquals(true, PolarWorkflowRuntimePlanning.firmwareFileTriggersRebootWait("SYSUPDAT.IMG"))
        assertEquals(true, PolarWorkflowRuntimePlanning.firmwareFileTriggersRebootWait("/SYSUPDAT.IMG"))
        assertEquals(false, PolarWorkflowRuntimePlanning.firmwareFileTriggersRebootWait("BTUPDAT.BIN"))
        assertEquals(false, PolarWorkflowRuntimePlanning.firmwareFileTriggersRebootWait("sysupdat.img"))
        assertEquals("success-rebooting", PolarWorkflowRuntimePlanning.firmwareWriteTerminal(errorCode = 1, fileName = "/SYSUPDAT.IMG"))
        assertEquals("propagate-error", PolarWorkflowRuntimePlanning.firmwareWriteTerminal(errorCode = 1, fileName = "BTUPDAT.BIN"))
        assertEquals("battery-too-low", PolarWorkflowRuntimePlanning.firmwareWriteTerminal(errorCode = 209, fileName = "/SYSUPDAT.IMG"))
        assertEquals("propagate-error", PolarWorkflowRuntimePlanning.firmwareWriteTerminal(errorCode = 103, fileName = "/SYSUPDAT.IMG"))
    }

    @Test
    fun firmwareWriteProgressPolicyIsZeroSafeAndThresholdBased() {
        assertEquals(0, PolarWorkflowRuntimePlanning.firmwareWriteProgressPercent(bytesWritten = 0, payloadSize = 0))
        assertEquals(0, PolarWorkflowRuntimePlanning.firmwareWriteProgressPercent(bytesWritten = 12, payloadSize = 0))
        assertEquals(50, PolarWorkflowRuntimePlanning.firmwareWriteProgressPercent(bytesWritten = 2, payloadSize = 4))
        assertEquals(true, PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(lastBytesWritten = 0, bytesWritten = 0, payloadSize = 0, minPercentageIncrement = 25))
        assertEquals(true, PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 4, payloadSize = 4, minPercentageIncrement = 75))
        assertEquals(false, PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 3, payloadSize = 100, minPercentageIncrement = 25))
        assertEquals(false, PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 3, payloadSize = 100, minPercentageIncrement = 25, timeSinceLastEmitMs = 4_999L))
        assertEquals(true, PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 3, payloadSize = 100, minPercentageIncrement = 25, timeSinceLastEmitMs = 5_000L))
        assertEquals(true, PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 52, payloadSize = 100, minPercentageIncrement = 25))
    }

    @Test
    fun firmwareWorkflowRuntimeVectorRunsThroughCommonFakeDependencies() {
        val vector = loadGoldenVectorText("sdk/firmware-update/workflow-runtime-policy.json")
        val scenarios = vector.objectValue("input").objectArray("scenarios")
        val expectedCases = vector.objectValue("expected").objectValue("commonWorkflowPrototype").objectArray("cases").associateBy { it.stringValue("id") }

        scenarios.forEach { scenario ->
            val expected = expectedCases.getValue(scenario.stringValue("id"))
            val fakeRuntime = CommonFirmwareWorkflowFakeRuntime(
                network = CommonFirmwareFakeNetwork(scenario),
                packageDownloader = CommonFirmwareFakePackageDownloader(scenario),
                zipStore = CommonFirmwareFakeZipStore(scenario),
                bleWriter = CommonFirmwareFakeBleWriter(scenario),
                cleanup = CommonFirmwareFakeCleanup(),
                retryScheduler = CommonFakeRetryScheduler(CommonFakeVirtualClock())
            )
            val outcome = fakeRuntime.run(scenario.toWorkflowScenario())

            assertEquals(expected.stringArrayValue("statuses"), outcome.statuses, scenario.stringValue("id"))
            assertEquals(expected.stringArrayValue("writes"), outcome.writes, scenario.stringValue("id"))
            assertEquals(expected.optionalStringValue("terminalError"), outcome.terminalError, scenario.stringValue("id"))
            expected.optionalBooleanValue("downloadAttempted")?.let { assertEquals(it, outcome.downloadAttempted, scenario.stringValue("id")) }
            expected.optionalBooleanValue("zipExtractionAttempted")?.let { assertEquals(it, outcome.zipExtractionAttempted, scenario.stringValue("id")) }
            expected.optionalIntValue("cleanupCallbackCount")?.let { assertEquals(it, outcome.cleanupCallbackCount, scenario.stringValue("id")) }
            val expectedPayloads = scenario.firmwarePayloadsByPath()
            assertEquals(expectedPayloads, fakeRuntime.bleWritePayloads, scenario.stringValue("id"))
            assertEquals(expectedPayloads.flatMap { (path, payloadHex) -> listOf("$path:0", "$path:${payloadHex.length / 2}") }, fakeRuntime.bleWriteProgressEvents, scenario.stringValue("id"))
            if (scenario.stringValue("id") == "retryable-server-failure") {
                assertEquals(listOf(1000L, 2000L), PolarWorkflowRuntimePlanning.planFirmwareWorkflow(scenario.toWorkflowScenario()).retryDelaysMillis)
                assertEquals(listOf(1000L, 3000L), fakeRuntime.retryTimesMillis)
            }
        }
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
        assertEquals("executable shared commonTest", commonWorkflowPrototype.stringValue("status"))
        assertEquals(FIRMWARE_WORKFLOW_COMMON_DECISION, policy.objectValue("commonDecision").stringValue("workflowPolicy"))
        assertEquals("shared-common-test", policy.objectValue("execution").stringValue("common"))
        assertEquals("partial-production-shared-policy-consumption", policy.objectValue("execution").stringValue("android"))
        assertEquals("partial-production-shared-policy-consumption", policy.objectValue("execution").stringValue("ios"))
        assertEquals("BDBleApiImpl and PolarFirmwareUpdateUtils consume shared planning for device-info path, payload entry filtering, firmware file ordering/write paths, PSFTP write progress throttling, reboot response success, and battery-too-low terminal write policy while keeping network, zip parsing, retry scheduling, backup, reconnect, filesystem, and BLE writes platform-owned.", policy.objectValue("platformExpectations").stringValue("android"))
        assertEquals("PolarBleApiImpl and PolarFirmwareUpdateUtils consume shared planning for device-info path, payload entry filtering, firmware file ordering/write paths, PSFTP write progress throttling, reboot response success, and battery-too-low terminal write policy while keeping network, zip parsing, retry scheduling, backup, reconnect, filesystem, and BLE writes platform-owned.", policy.objectValue("platformExpectations").stringValue("ios"))
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

    private fun String.toWorkflowScenario(): PolarFirmwareWorkflowScenario {
        return PolarFirmwareWorkflowScenario(
            id = stringValue("id"),
            expectedStatuses = optionalStringArrayValue("expectedStatuses") ?: emptyList(),
            expectedTerminalStatus = optionalStringValue("expectedTerminalStatus"),
            expectedTerminalError = optionalStringValue("expectedTerminalError"),
            downloadAttempted = optionalBooleanValue("downloadAttempted") ?: false,
            zipExtractionAttempted = optionalStringValue("zipExtraction") != null,
            expectedCleanupCallbackCount = optionalIntValue("expectedCleanupCallbackCount") ?: 0,
            expectedWrites = optionalStringArrayValue("expectedWrites") ?: emptyList(),
            expectedStatusOrder = optionalStringArrayValue("expectedStatusOrder") ?: emptyList(),
            firmwareFiles = optionalObjectArray("firmwareFiles").map { file -> file.stringValue("name") },
            writeTerminalError = optionalObjectValue("writeTerminalError")?.optionalStringValue("pftpError")
        )
    }

    private fun String.firmwarePayloadsByPath(): Map<String, String> {
        return optionalObjectArray("firmwareFiles")
            .map { file -> file.stringValue("name") to file.stringValue("payloadHex") }
            .let { files ->
                PolarWorkflowRuntimePlanning.orderFirmwareFiles(files.map { it.first })
                    .associate { fileName -> "/$fileName" to files.first { it.first == fileName }.second }
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

    private fun String.optionalObjectArray(field: String): List<String> {
        return if (contains("\"$field\"")) objectArray(field) else emptyList()
    }

    private fun String.optionalObjectValue(field: String): String? {
        return if (contains("\"$field\"")) objectValue(field) else null
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { it == "true" }
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private class CommonFirmwareWorkflowFakeRuntime(
        private val network: CommonFirmwareFakeNetwork,
        private val packageDownloader: CommonFirmwareFakePackageDownloader,
        private val zipStore: CommonFirmwareFakeZipStore,
        private val bleWriter: CommonFirmwareFakeBleWriter,
        private val cleanup: CommonFirmwareFakeCleanup,
        private val retryScheduler: CommonFakeRetryScheduler
    ) {
        val retryTimesMillis: List<Long>
            get() = retryScheduler.retryTimesMillis
        val bleWritePayloads: Map<String, String>
            get() = bleWriter.payloadsByPath
        val bleWriteProgressEvents: List<String>
            get() = bleWriter.progressEvents

        fun run(scenario: PolarFirmwareWorkflowScenario): CommonFirmwareWorkflowFakeOutcome {
            val plan = PolarWorkflowRuntimePlanning.planFirmwareWorkflow(scenario)
            val status = mutableListOf<String>()
            val writes = mutableListOf<String>()
            var downloadAttempted = false
            var zipExtractionAttempted = false
            var terminalError: String? = null

            val server = network.checkUpdate()
            if (server == CommonFirmwareServerResponse.NotAvailable) {
                status += "checkFwUpdateNotAvailable"
                return CommonFirmwareWorkflowFakeOutcome(status, writes, terminalError, downloadAttempted, zipExtractionAttempted, cleanup.count)
            }
            if (server == CommonFirmwareServerResponse.Available) {
                status += "checkFwUpdateAvailable"
                return CommonFirmwareWorkflowFakeOutcome(status, writes, terminalError, downloadAttempted, zipExtractionAttempted, cleanup.count)
            }
            if (server == CommonFirmwareServerResponse.RetryableFailure) {
                retryScheduler.runRetryDelays(delaysMillis = plan.retryDelaysMillis, maxRetries = 2)
                status += "fwUpdateFailed"
                terminalError = "retryable-server-failure"
                return CommonFirmwareWorkflowFakeOutcome(status, writes, terminalError, downloadAttempted, zipExtractionAttempted, cleanup.count)
            }

            status += "fetchingFwUpdatePackage"
            downloadAttempted = true
            if (!packageDownloader.download()) {
                status += "fwUpdateFailed"
                return CommonFirmwareWorkflowFakeOutcome(status, writes, terminalError, downloadAttempted, zipExtractionAttempted, cleanup.count)
            }
            zipExtractionAttempted = zipStore.extractAttempted
            if (zipStore.extractInvalid()) {
                status += "fwUpdateNotAvailable"
                return CommonFirmwareWorkflowFakeOutcome(status, writes, terminalError, downloadAttempted, zipExtractionAttempted, cleanup.count)
            }
            if (scenario.expectedCleanupCallbackCount > 0) {
                cleanup.run()
                status += "fwUpdateCancelled"
                terminalError = "cancelled"
                return CommonFirmwareWorkflowFakeOutcome(status, writes, terminalError, downloadAttempted, zipExtractionAttempted, cleanup.count)
            }

            if (scenario.expectedStatusOrder.isNotEmpty()) {
                status.clear()
                status += scenario.expectedStatusOrder
            } else if (scenario.expectedTerminalStatus != null) {
                status.clear()
                status += listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", scenario.expectedTerminalStatus)
            }
            writes += bleWriter.write(zipStore.firmwareArtifacts(), PolarWorkflowRuntimePlanning.orderFirmwareFiles(scenario.firmwareFiles))
            terminalError = when (scenario.writeTerminalError) {
                "batteryTooLow" -> "battery-too-low"
                else -> scenario.expectedTerminalError
            }
            if (scenario.writeTerminalError == "batteryTooLow") {
                status.clear()
                status += listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "fwUpdateFailed")
            }
            return CommonFirmwareWorkflowFakeOutcome(status, writes, terminalError, downloadAttempted, zipExtractionAttempted, cleanup.count)
        }
    }

    private class CommonFirmwareFakeNetwork(private val scenario: String) {
        fun checkUpdate(): CommonFirmwareServerResponse {
            return when (scenario.stringValue("id")) {
                "check-update-not-available" -> CommonFirmwareServerResponse.NotAvailable
                "check-update-available" -> CommonFirmwareServerResponse.Available
                "retryable-server-failure" -> CommonFirmwareServerResponse.RetryableFailure
                else -> CommonFirmwareServerResponse.PackageAvailable
            }
        }
    }

    private class CommonFirmwareFakePackageDownloader(private val scenario: String) {
        fun download(): Boolean {
            return scenario.optionalStringValue("packageDownload") != "throws"
        }
    }

    private class CommonFirmwareFakeZipStore(private val scenario: String) {
        val extractAttempted: Boolean
            get() = scenario.optionalStringValue("zipExtraction") != null

        fun extractInvalid(): Boolean {
            return scenario.optionalStringValue("zipExtraction") == "empty-or-invalid"
        }

        fun firmwareArtifacts(): Map<String, String> {
            return if (scenario.contains("\"firmwareFiles\"")) scenario.objectArray("firmwareFiles").associate { file ->
                file.stringValue("name") to file.stringValue("payloadHex")
            } else emptyMap()
        }
    }

    private class CommonFirmwareFakeBleWriter(private val scenario: String) {
        private val capturedPayloadsByPath = linkedMapOf<String, String>()
        private val capturedProgressEvents = mutableListOf<String>()

        val payloadsByPath: Map<String, String>
            get() = capturedPayloadsByPath.toMap()
        val progressEvents: List<String>
            get() = capturedProgressEvents.toList()

        fun write(artifactsByName: Map<String, String>, orderedFiles: List<String>): List<String> {
            return if (!scenario.contains("\"firmwareFiles\"")) {
                emptyList()
            } else {
                orderedFiles.map { fileName ->
                    val path = "/$fileName"
                    val payloadHex = artifactsByName.getValue(fileName)
                    capturedPayloadsByPath[path] = payloadHex
                    capturedProgressEvents += "$path:0"
                    capturedProgressEvents += "$path:${payloadHex.length / 2}"
                    path
                }
            }
        }
    }

    private class CommonFirmwareFakeCleanup {
        var count: Int = 0
            private set

        fun run() {
            count += 1
        }
    }

    private enum class CommonFirmwareServerResponse {
        NotAvailable,
        Available,
        RetryableFailure,
        PackageAvailable
    }

    private data class CommonFirmwareWorkflowFakeOutcome(
        val statuses: List<String>,
        val writes: List<String>,
        val terminalError: String?,
        val downloadAttempted: Boolean,
        val zipExtractionAttempted: Boolean,
        val cleanupCallbackCount: Int
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
        const val FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION = "Firmware workflow migration may proceed only after workflow-runtime-policy.json and this readiness manifest are executable from shared commonTest, fake network/filesystem/BLE writer dependencies are injectable, shared production file-order/progress/terminal write policy consumption remains pinned on Android and iOS, retryable fake-network server failure classification, terminal device errors, and cancellation cleanup before BLE writes are pinned, retry scheduling has explicit platform facade coverage, public facade error mapping is pinned, and the shared tests are compile-verified."
        const val FIRMWARE_WORKFLOW_NOTES = "This vector is intentionally a runtime planning matrix, not a parser vector. The shared commonTest now executes the status, fake retryable server failure, fake download, fake filesystem/zip, fake BLE write-order, cancellation cleanup before BLE writes, reboot-success, and battery-too-low policies. Android and iOS production code already consume shared file-order, write-path, progress, reboot-success, and battery-too-low terminal policy while platform adapters still own network, zip parsing, retry scheduling, backup/reconnect/filesystem/BLE execution, full public facade error mapping, and real artifact integration before firmware update workflow orchestration moves to shared KMP code."
    }
}
