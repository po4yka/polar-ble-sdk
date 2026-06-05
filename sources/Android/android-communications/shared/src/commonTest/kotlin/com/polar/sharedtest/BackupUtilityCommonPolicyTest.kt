package com.polar.sharedtest

import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupUtilityCommonPolicyTest {
    @Test
    fun backupExpansionAndRestoreWritesGoldenVectorDefinesExecutableCommonPolicy() {
        val vector = loadGoldenVectorText("sdk/backup-utils/backup-expansion-and-restore-writes.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val backup = FakeBackupUtility(input.objectValue("files"))

        val rootPaths = PolarWorkflowRuntimePlanning.backupRootPaths(
            backup.backupTextEntries(input.objectValue("files").stringValue("/SYS/BACKUP.TXT"))
        )
        val expandedPaths = backup.expandBackupEntries(rootPaths)
        val actualBackupFiles = backup.readBackupFiles(expandedPaths)

        assertEquals(expected.objectArray("backupFiles").map { it.stringValue("path") }, actualBackupFiles.map { it.path })
        assertEquals(expected.objectArray("backupFiles").map { it.stringValue("dataHex") }, actualBackupFiles.map { it.dataHex })

        val restoreTransport = ScriptedCommonFakeTransport(input.objectArray("restoreFiles").map { CommonFakeTransportOutcome.Complete })
        backup.restore(input.objectArray("restoreFiles"), restoreTransport)
        assertEquals(
            expected.objectArray("restoreWrites").map { expectedWrite ->
                CommonFakeTransportCommand(CommonFakeTransportOperation.WRITE, expectedWrite.stringValue("path"), expectedWrite.stringValue("dataHex"))
            },
            restoreTransport.commands
        )
    }

    @Test
    fun restoreFailureGoldenVectorPinsPlatformSplitBeforeCommonWorkflowMigration() {
        val vector = loadGoldenVectorText("sdk/backup-utils/restore-failure-platform-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        val transport = ScriptedCommonFakeTransport(input.objectArray("restoreFiles").map { restoreFile ->
            when (restoreFile.stringValue("writeResult")) {
                "success" -> CommonFakeTransportOutcome.Complete
                "failure" -> CommonFakeTransportOutcome.TransportError("Restore failed")
                else -> error("Unsupported restore write result ${restoreFile.stringValue("writeResult")}")
            }
        })
        val failures = FakeBackupUtility("{}").restore(input.objectArray("restoreFiles"), transport)

        assertEquals(
            expected.objectArray("writes").map { expectedWrite ->
                CommonFakeTransportCommand(CommonFakeTransportOperation.WRITE, expectedWrite.stringValue("path"), expectedWrite.stringValue("dataHex"))
            },
            transport.commands
        )
        assertEquals("restore-failure-platform-policy", vector.stringValue("id"))
        assertEquals("sdk.backup-utils", vector.stringValue("area"))
        assertEquals("restore_failure_platform_policy", vector.stringValue("case"))
        assertEquals(listOf("PUT", "PUT", "PUT"), expected.objectArray("writes").map { write -> write.stringValue("command") }, vector.stringValue("id"))
        assertEquals(listOf("/SYS/BT/BTDEV.BPB", "/SYS/BT/SVSTATUS.BPB", "/RANDOM/FILE.TXT"), expected.objectArray("writes").map { write -> write.stringValue("path") }, vector.stringValue("id"))
        assertEquals(listOf("0102", "0304", "0506"), expected.objectArray("writes").map { write -> write.stringValue("dataHex") }, vector.stringValue("id"))
        assertEquals(false, expected.objectValue("android").booleanValue("throws"))
        assertEquals(true, expected.objectValue("ios").booleanValue("throws"))
        assertEquals(listOf("/SYS/BT/SVSTATUS.BPB"), failures)
        assertEquals("/SYS/BT/SVSTATUS.BPB", expected.objectValue("ios").stringValue("errorContains"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarBackupManagerTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBackupManagerTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.BackupUtilityCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("android"), vector.stringValue("id"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("ios"), vector.stringValue("id"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("common"), vector.stringValue("id"))
        assertEquals("KMP should choose whether restore failure aggregation belongs in shared code or remains platform-specific compatibility behavior.", vector.stringValue("notes"), vector.stringValue("id"))
    }

    @Test
    fun backupWorkflowReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/backup-utils/backup-workflow-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals("backup-workflow-readiness", manifest.stringValue("id"))
        assertEquals("backupWorkflowReadiness", input.stringValue("kind"))
        assertEquals(requiredBackupWorkflowPolicyVectorPaths, input.stringArrayValue("policyVectorPaths"))
        assertEquals(requiredBackupWorkflowFamilies, requiredFamilies)
        assertEquals(requiredBackupWorkflowFamilies, coveredFamilies)
        assertEquals(backupWorkflowReadinessDecision, expected.stringValue("commonDecision"))
        assertEquals(requiredBackupWorkflowPolicyIds, input.stringArrayValue("policyVectorPaths").map { path -> loadGoldenVectorText(path).stringValue("id") })
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarBackupManagerTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBackupManagerTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.BackupUtilityCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    @Test
    fun backupRootPathPlanningMergesDefaultsAndNormalizesUserWildcardDuplicates() {
        assertEquals(
            listOf("/SYS/BT/", "/U/*/USERID.BPB", "/U/0/S/PHYSDATA.BPB", "/U/0/S/UDEVSET.BPB", "/U/0/S/PREFS.BPB"),
            PolarWorkflowRuntimePlanning.backupRootPaths(listOf("/SYS/BT/", "/U/*/USERID.BPB"))
        )
        assertEquals(
            PolarWorkflowRuntimePlanning.defaultBackupPaths(),
            PolarWorkflowRuntimePlanning.backupRootPaths(emptyList())
        )
    }

    @Test
    fun backupFilePathPlanningSplitsDirectoryAndFileName() {
        val filePath = PolarWorkflowRuntimePlanning.backupFilePath("/SYS/BT/BTDEV.BPB")

        assertEquals("/SYS/BT/", filePath.directory)
        assertEquals("BTDEV.BPB", filePath.fileName)
    }

    private val requiredBackupWorkflowPolicyVectorPaths = listOf(
        "sdk/backup-utils/backup-expansion-and-restore-writes.json",
        "sdk/backup-utils/restore-failure-platform-policy.json"
    )

    private val requiredBackupWorkflowPolicyIds = listOf(
        "backup-expansion-and-restore-writes",
        "restore-failure-platform-policy"
    )

    private val requiredBackupWorkflowFamilies = listOf(
        "backup-txt-expansion",
        "backup-directory-expansion",
        "default-user-file-inclusion",
        "backup-file-read-order",
        "restore-put-command-planning",
        "restore-payload-preservation",
        "restore-write-order",
        "restore-failure-platform-split",
        "restore-failure-aggregation-decision-gate",
        "platform-backup-vector-reference-gate",
        "compile-verification-gate"
    )

    private val backupWorkflowReadinessDecision = "Backup workflow migration may proceed only after backup-expansion-and-restore-writes.json, restore-failure-platform-policy.json, and this readiness manifest are executable from shared commonTest, Android and iOS backup tests continue to reference the same vectors, BACKUP.TXT expansion and default user-file inclusion stay covered, restore PUT command order and payload bytes remain pinned, restore failure aggregation is deliberately standardized or deliberately preserved as a platform split, and the shared tests are compile-verified."

    private class FakeBackupUtility(
        private val filesJson: String
    ) {
        fun backupTextEntries(backupTextHex: String): List<String> {
            return hexToBytes(backupTextHex).decodeAscii().split("\n").filter { path -> path.isNotEmpty() }
        }

        fun expandBackupEntries(rootPaths: List<String>): List<String> {
            return rootPaths.flatMap { path ->
                if (path.endsWith("/")) {
                    listDirectory(path)
                } else {
                    listOf(path)
                }
            }
        }

        fun readBackupFiles(paths: List<String>): List<BackupArtifact> {
            return paths.map { path -> BackupArtifact(path, filesJson.stringValue(path)) }
        }

        fun restore(restoreFiles: List<String>, transport: ScriptedCommonFakeTransport): List<String> {
            val failures = mutableListOf<String>()
            restoreFiles.forEach { restoreFile ->
                val path = restoreFile.stringValue("directory") + restoreFile.stringValue("fileName")
                val outcome = transport.write(path, hexToBytes(restoreFile.stringValue("dataHex")))
                if (outcome is CommonFakeTransportOutcome.TransportError || outcome is CommonFakeTransportOutcome.ResponseError || outcome is CommonFakeTransportOutcome.Timeout) {
                    failures += path
                }
            }
            return failures
        }

        private fun listDirectory(directory: String): List<String> {
            return Regex("\"([^\"]+)\"\\s*:\\s*\"[^\"]*\"").findAll(filesJson).map { match -> match.groupValues[1] }.filter { path ->
                path.startsWith(directory) && path != directory
            }.toList()
        }
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private fun ByteArray.decodeAscii(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toChar().toString() }
    }

    private data class BackupArtifact(
        val path: String,
        val dataHex: String
    )
}
