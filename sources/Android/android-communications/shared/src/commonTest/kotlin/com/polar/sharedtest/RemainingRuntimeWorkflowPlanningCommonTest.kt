package com.polar.sharedtest

import com.polar.shared.runtime.PolarBackupRestoreFile
import com.polar.shared.runtime.PolarFirmwareWorkflowScenario
import com.polar.shared.runtime.PolarOfflineTriggerDesiredFeature
import com.polar.shared.runtime.PolarOfflineTriggerDeviceTrigger
import com.polar.shared.runtime.PolarOfflineTriggerTransport
import com.polar.shared.runtime.PolarPsFtpNotificationPacket
import com.polar.shared.runtime.PolarPsFtpWriteAckTimeout
import com.polar.shared.runtime.PolarStoredDataCleanupDateFolder
import com.polar.shared.runtime.PolarStoredDataCleanupSampleFile
import com.polar.shared.runtime.PolarStoredDataCleanupScenario
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemainingRuntimeWorkflowPlanningCommonTest {
    @Test
    fun storedDataCleanupVectorsRunThroughProductionCommonPlanner() {
        val vector = loadGoldenVectorText("sdk/stored-data-cleanup/cleanup-workflow-policy.json")
        val expectedCases = vector.objectValue("expected").objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }

        vector.objectValue("input").objectArray("scenarios").forEach { scenarioJson ->
            val scenario = PolarStoredDataCleanupScenario(
                id = scenarioJson.stringValue("id"),
                kind = scenarioJson.stringValue("kind"),
                rootPath = scenarioJson.optionalStringValue("rootPath"),
                includePrefixes = scenarioJson.optionalStringArrayValue("includePrefixes") ?: emptyList(),
                includeSuffixes = scenarioJson.optionalStringArrayValue("includeSuffixes") ?: emptyList(),
                entries = scenarioJson.optionalStringArrayValue("entries") ?: emptyList(),
                cutoffDate = scenarioJson.optionalStringValue("cutoffDate"),
                dateFolders = scenarioJson.optionalObjectArray("dateFolders").map { folder ->
                    PolarStoredDataCleanupDateFolder(
                        path = folder.stringValue("path"),
                        beforeCutoff = folder.booleanValue("beforeCutoff"),
                        removeFiles = folder.stringArrayValue("removeFiles"),
                        pruneEmptyParents = folder.booleanValue("pruneEmptyParents")
                    )
                },
                sampleFiles = scenarioJson.optionalObjectArray("sampleFiles").map { sample ->
                    PolarStoredDataCleanupSampleFile(
                        path = sample.stringValue("path"),
                        embeddedDay = sample.stringValue("embeddedDay")
                    )
                }
            )
            val expected = expectedCases.getValue(scenario.id)
            val outcome = PolarWorkflowRuntimePlanning.planStoredDataCleanup(scenario)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, scenario.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, scenario.id)
        }
    }

    @Test
    fun offlineTriggerVectorsRunThroughProductionCommonPlanner() {
        val vector = loadGoldenVectorText("sdk/offline-recording/trigger-runtime-policy.json")
        val input = vector.objectValue("input")
        val expectedCases = vector.objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }
        val current = input.objectArray("currentDeviceTriggers").map { trigger ->
            PolarOfflineTriggerDeviceTrigger(
                type = trigger.stringValue("type"),
                status = trigger.stringValue("status")
            )
        }
        val desired = input.objectValue("desiredTrigger")
        val desiredFeatures = desired.objectArray("features").map { feature ->
            PolarOfflineTriggerDesiredFeature(
                type = feature.stringValue("type"),
                hasSelectedSettings = feature.contains("\"selectedSettings\"\\s*:\\s*\\{".toRegex())
            )
        }
        val secretPresent = desired.objectValue("secret").booleanValue("present")

        input.objectArray("scenarios").forEach { scenario ->
            val transport = scenario.objectValue("transport")
            val outcome = PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
                operation = scenario.stringValue("operation"),
                currentDeviceTriggers = current,
                desiredMode = desired.stringValue("mode"),
                desiredFeatures = desiredFeatures,
                secretPresent = secretPresent,
                transport = PolarOfflineTriggerTransport(
                    setMode = transport.optionalStringValue("setMode") ?: "success",
                    getStatus = transport.optionalStringValue("getStatus") ?: "success",
                    setSettings = transport.optionalStringValue("setSettings") ?: "success"
                )
            )
            val expected = expectedCases.getValue(scenario.stringValue("id"))

            assertEquals(expected.stringArrayValue("operations"), outcome.commands, scenario.stringValue("id"))
            assertEquals(expected.stringValue("terminal"), outcome.terminal, scenario.stringValue("id"))
            expected.optionalStringArrayValue("enabledFeatures")?.let { assertEquals(it, outcome.enabledFeatures, scenario.stringValue("id")) }
            expected.optionalStringArrayValue("excludedFeatures")?.let { assertEquals(it, outcome.excludedFeatures, scenario.stringValue("id")) }
        }
    }

    @Test
    fun firmwareWorkflowVectorsRunThroughProductionCommonPlanner() {
        val vector = loadGoldenVectorText("sdk/firmware-update/workflow-runtime-policy.json")
        val expectedCases = vector.objectValue("expected").objectValue("commonWorkflowPrototype").objectArray("cases").associateBy { it.stringValue("id") }

        vector.objectValue("input").objectArray("scenarios").forEach { scenarioJson ->
            val scenario = PolarFirmwareWorkflowScenario(
                id = scenarioJson.stringValue("id"),
                expectedStatuses = scenarioJson.optionalStringArrayValue("expectedStatuses") ?: emptyList(),
                expectedTerminalStatus = scenarioJson.optionalStringValue("expectedTerminalStatus"),
                expectedTerminalError = scenarioJson.optionalStringValue("expectedTerminalError"),
                downloadAttempted = scenarioJson.optionalBooleanValue("downloadAttempted") ?: false,
                zipExtractionAttempted = scenarioJson.optionalStringValue("zipExtraction") != null,
                expectedCleanupCallbackCount = scenarioJson.optionalIntValue("expectedCleanupCallbackCount") ?: 0,
                expectedWrites = scenarioJson.optionalStringArrayValue("expectedWrites") ?: emptyList(),
                expectedStatusOrder = scenarioJson.optionalStringArrayValue("expectedStatusOrder") ?: emptyList(),
                firmwareFiles = scenarioJson.optionalObjectArray("firmwareFiles").map { it.stringValue("name") },
                writeTerminalError = scenarioJson.optionalObjectValue("writeTerminalError")?.optionalStringValue("pftpError")
            )
            val expected = expectedCases.getValue(scenario.id)
            val outcome = PolarWorkflowRuntimePlanning.planFirmwareWorkflow(scenario)

            assertEquals(expected.stringArrayValue("statuses"), outcome.statuses, scenario.id)
            assertEquals(expected.stringArrayValue("writes"), outcome.writes, scenario.id)
            assertEquals(expected.optionalStringValue("terminalError"), outcome.terminalError, scenario.id)
            expected.optionalBooleanValue("downloadAttempted")?.let { assertEquals(it, outcome.downloadAttempted, scenario.id) }
            expected.optionalBooleanValue("zipExtractionAttempted")?.let { assertEquals(it, outcome.zipExtractionAttempted, scenario.id) }
            expected.optionalIntValue("cleanupCallbackCount")?.let { assertEquals(it, outcome.cleanupCallbackCount, scenario.id) }
        }
    }

    @Test
    fun backupWorkflowVectorsRunThroughProductionCommonPlanner() {
        val vector = loadGoldenVectorText("sdk/backup-utils/backup-expansion-and-restore-writes.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val files = input.objectValue("files").objectEntries()
        val backupText = hexToBytes(files.getValue("/SYS/BACKUP.TXT")).decodeAscii()
        val expanded = PolarWorkflowRuntimePlanning.expandBackupEntries(backupText, files.keys.toList())
        val backupFiles = PolarWorkflowRuntimePlanning.readBackupFiles(expanded + PolarWorkflowRuntimePlanning.defaultBackupPaths(), files)

        assertEquals(expected.objectArray("backupFiles").map { it.stringValue("path") }, backupFiles.map { it.path })
        assertEquals(expected.objectArray("backupFiles").map { it.stringValue("dataHex") }, backupFiles.map { it.dataHex })

        val restorePlan = PolarWorkflowRuntimePlanning.planBackupRestore(input.objectArray("restoreFiles").map { restore ->
            PolarBackupRestoreFile(
                directory = restore.stringValue("directory"),
                fileName = restore.stringValue("fileName"),
                dataHex = restore.stringValue("dataHex")
            )
        })
        assertEquals(expected.objectArray("restoreWrites").map { it.stringValue("path") }, restorePlan.writes)
    }

    @Test
    fun psFtpByteCodecAndRuntimeVectorsRunThroughProductionCommonPlanner() {
        val frameVector = loadGoldenVectorText("sdk/psftp-rfc76/final-last-frame.json")
        val frame = PolarWorkflowRuntimePlanning.decodeRfc76Frame(hexToBytes(frameVector.objectValue("input").stringValue("frameHex")))
        val frameExpected = frameVector.objectValue("expected")
        assertEquals(frameExpected.intValue("next"), frame.next)
        assertEquals(frameExpected.intValue("status"), frame.status)
        assertEquals(frameExpected.intValue("sequenceNumber"), frame.sequenceNumber)
        assertEquals(frameExpected.stringValue("payloadHex"), frame.payload!!.toHex())

        val streamVector = loadGoldenVectorText("sdk/psftp-message-stream/complete-message-streams.json")
        val streamCase = streamVector.objectValue("input").objectArray("cases").first { it.stringValue("id") == "query-with-header" }
        assertEquals(
            streamCase.stringValue("expectedHex"),
            PolarWorkflowRuntimePlanning.encodeCompleteMessageStream(
                type = streamCase.stringValue("type"),
                header = hexToBytes(streamCase.stringValue("headerHex")),
                idValue = streamCase.intValue("idValue")
            ).toHex()
        )

        val timeoutVector = loadGoldenVectorText("sdk/psftp-response/write-ack-timeout-policy.json")
        val failure = timeoutVector.objectValue("input").objectValue("failure")
        val timeout = assertFailsWith<PolarPsFtpWriteAckTimeout> {
            PolarWorkflowRuntimePlanning.planPsFtpWrite(
                payload = hexToBytes(timeoutVector.objectValue("input").stringValue("payloadHex")),
                transportTransmit = failure.stringValue("transportTransmit"),
                writeAck = failure.stringValue("writeAck")
            )
        }
        assertEquals(failure.stringValue("point"), timeout.point)

        val notificationVector = loadGoldenVectorText("sdk/psftp-notifications/notification-reassembly.json")
        val notificationCase = notificationVector.objectValue("input").objectArray("cases").first()
        val notifications = PolarWorkflowRuntimePlanning.reassembleNotifications(notificationCase.stringArrayValue("framesHex").map { hex ->
            PolarPsFtpNotificationPacket(hexToBytes(hex), 0)
        })
        assertEquals(notificationCase.objectValue("expected").intValue("id"), notifications.single().id)
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

    private fun String.objectEntries(): Map<String, String> {
        return Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(this).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun ByteArray.decodeAscii(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toChar().toString() }
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
