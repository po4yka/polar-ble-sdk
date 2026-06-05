package com.polar.sharedtest

import com.polar.shared.runtime.PolarBackupRestoreFile
import com.polar.shared.runtime.PolarPsFtpNotificationPacket
import com.polar.shared.runtime.PolarPsFtpWriteAckTimeout
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemainingRuntimeWorkflowPlanningCommonTest {
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
