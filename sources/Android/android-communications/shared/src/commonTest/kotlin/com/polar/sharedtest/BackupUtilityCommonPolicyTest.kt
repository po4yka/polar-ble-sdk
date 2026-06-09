package com.polar.sharedtest

import com.polar.shared.runtime.PolarBackupRestoreFile
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupUtilityCommonPolicyTest {
    @Test
    fun backupExpansionAndRestoreWritesGoldenVectorDefinesExecutableCommonPolicy() {
        val vector = loadGoldenVectorText("sdk/backup-utils/backup-expansion-and-restore-writes.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val files = input.objectValue("files").objectEntries()
        val backupText = hexToBytes(files.getValue("/SYS/BACKUP.TXT")).decodeAscii()

        val rootPaths = PolarWorkflowRuntimePlanning.backupRootPaths(backupText.split("\n").filter { path -> path.isNotEmpty() })
        val expandedPaths = PolarWorkflowRuntimePlanning.expandBackupEntries(rootPaths.joinToString(separator = "\n"), files.keys.toList())
        val actualBackupFiles = PolarWorkflowRuntimePlanning.readBackupFiles(expandedPaths, files)

        assertEquals(expected.objectArray("backupFiles").map { it.stringValue("path") }, actualBackupFiles.map { it.path })
        assertEquals(expected.objectArray("backupFiles").map { it.stringValue("dataHex") }, actualBackupFiles.map { it.dataHex })

        val restorePlan = PolarWorkflowRuntimePlanning.planBackupRestore(input.objectArray("restoreFiles").map { restoreFile -> restoreFile.toBackupRestoreFile() })
        assertEquals(expected.objectArray("restoreWrites").map { expectedWrite -> "PUT:${expectedWrite.stringValue("path")}:${expectedWrite.stringValue("dataHex")}" }, restorePlan.commands)
    }

    @Test
    fun backupWorkflowRunsThroughCommonFakeTraversalAndRestoreHarness() {
        val vector = loadGoldenVectorText("sdk/backup-utils/backup-expansion-and-restore-writes.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val fakeRuntime = CommonBackupFakeRuntime(
            directories = input.objectValue("directories").directoryEntries(),
            files = input.objectValue("files").objectEntries()
        )

        val backupFiles = fakeRuntime.backupDevice()
        val restoreCommands = fakeRuntime.restoreBackup(input.objectArray("restoreFiles").map { restoreFile -> restoreFile.toBackupRestoreFile() })

        assertEquals(expected.objectArray("backupFiles").map { file -> file.stringValue("path") }, backupFiles.map { file -> file.path })
        assertEquals(expected.objectArray("backupFiles").map { file -> file.stringValue("dataHex") }, backupFiles.map { file -> file.dataHex })
        assertEquals(
            listOf(
                "GET:/SYS/",
                "GET:/SYS/BACKUP.TXT",
                "GET:/SYS/BT/",
                "GET:/SYS/BT/BTDEV.BPB",
                "GET:/SYS/BT/SVSTATUS.BPB",
                "GET:/RANDOM/FILE.TXT",
                "GET:/U/0/S/PHYSDATA.BPB",
                "GET:/U/0/S/UDEVSET.BPB",
                "GET:/U/0/S/PREFS.BPB",
                "GET:/U/0/USERID.BPB"
            ),
            fakeRuntime.readCommands
        )
        assertEquals(expected.objectArray("restoreWrites").map { expectedWrite -> "PUT:${expectedWrite.stringValue("path")}:${expectedWrite.stringValue("dataHex")}" }, restoreCommands)
        assertEquals(expected.objectArray("restoreWrites").map { expectedWrite -> expectedWrite.stringValue("dataHex") }, fakeRuntime.restorePayloads)
    }

    @Test
    fun restoreFailureGoldenVectorPinsPlatformSplitBeforeCommonWorkflowMigration() {
        val vector = loadGoldenVectorText("sdk/backup-utils/restore-failure-platform-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        val restorePlan = PolarWorkflowRuntimePlanning.planBackupRestore(input.objectArray("restoreFiles").map { restoreFile -> restoreFile.toBackupRestoreFile() })

        assertEquals(
            expected.objectArray("writes").map { expectedWrite -> "PUT:${expectedWrite.stringValue("path")}:${expectedWrite.stringValue("dataHex")}" },
            restorePlan.commands
        )
        assertEquals("restore-failure-platform-policy", vector.stringValue("id"))
        assertEquals("sdk.backup-utils", vector.stringValue("area"))
        assertEquals("restore_failure_platform_policy", vector.stringValue("case"))
        assertEquals(listOf("PUT", "PUT", "PUT"), expected.objectArray("writes").map { write -> write.stringValue("command") }, vector.stringValue("id"))
        assertEquals(listOf("/SYS/BT/BTDEV.BPB", "/SYS/BT/SVSTATUS.BPB", "/RANDOM/FILE.TXT"), expected.objectArray("writes").map { write -> write.stringValue("path") }, vector.stringValue("id"))
        assertEquals(listOf("0102", "0304", "0506"), expected.objectArray("writes").map { write -> write.stringValue("dataHex") }, vector.stringValue("id"))
        assertEquals(false, expected.objectValue("android").booleanValue("throws"))
        assertEquals(true, expected.objectValue("ios").booleanValue("throws"))
        assertEquals(listOf("/SYS/BT/SVSTATUS.BPB"), restorePlan.terminalError?.split(",") ?: emptyList())
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
        assertEquals("/U/0/S/UDEVSET.BPB", PolarWorkflowRuntimePlanning.backupTraversalRootPath("/U/*/S/UDEVSET.BPB"))
        assertEquals("/SYS/BT/", PolarWorkflowRuntimePlanning.backupTraversalRootPath("/SYS/BT/"))
        val wildcardPlan = PolarWorkflowRuntimePlanning.backupTraversalPlan("/SYS/*/BT/BTDEV.BPB")
        assertEquals("/SYS/*/BT/BTDEV.BPB", wildcardPlan.path)
        assertEquals("/SYS/", wildcardPlan.wildcardRootPath)
        assertEquals("BT", wildcardPlan.wildcardSubFolder)
    }

    @Test
    fun backupTextParsingPinsCurrentAndroidAndIosCompatibilitySplit() {
        val backupText = "/SYS/BT/\n /TRIMMED/PATH.BPB \n/SYS/BT/\n/FINAL/NO_NEWLINE.BPB"

        assertEquals(
            listOf("/SYS/BT/", " /TRIMMED/PATH.BPB ", "/FINAL/NO_NEWLINE.BPB"),
            PolarWorkflowRuntimePlanning.parseBackupTextForAndroid(backupText)
        )
        assertEquals(
            listOf("/SYS/BT/", "/TRIMMED/PATH.BPB", "/SYS/BT/"),
            PolarWorkflowRuntimePlanning.parseBackupTextForIos(backupText)
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

    private fun String.toBackupRestoreFile(): PolarBackupRestoreFile {
        return PolarBackupRestoreFile(
            directory = stringValue("directory"),
            fileName = stringValue("fileName"),
            dataHex = stringValue("dataHex"),
            writeResult = optionalStringValue("writeResult") ?: "success"
        )
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private fun ByteArray.decodeAscii(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toChar().toString() }
    }

    private fun String.objectEntries(): Map<String, String> {
        return Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(this).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun String.directoryEntries(): Map<String, List<String>> {
        return Regex("\"([^\"]+)\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).findAll(this).associate { match ->
            val path = match.groupValues[1]
            val entries = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(match.groupValues[2]).map { entry -> entry.groupValues[1] }.toList()
            path to entries
        }
    }

    private class CommonBackupFakeRuntime(
        private val directories: Map<String, List<String>>,
        private val files: Map<String, String>
    ) {
        private val capturedReadCommands = mutableListOf<String>()
        private val capturedRestorePayloads = mutableListOf<String>()

        val readCommands: List<String>
            get() = capturedReadCommands.toList()
        val restorePayloads: List<String>
            get() = capturedRestorePayloads.toList()

        fun backupDevice(): List<PolarBackupFileCapture> {
            val sysEntries = listDirectory("/SYS/")
            val backupText = if (sysEntries.any { entry -> entry == "BACKUP.TXT" }) {
                decodeAsciiHex(readFile("/SYS/BACKUP.TXT"))
            } else {
                ""
            }
            return PolarWorkflowRuntimePlanning.backupRootPaths(backupText.split("\n").filter { path -> path.isNotEmpty() })
                .flatMap { root -> backupPath(root.replace("/U/*/", "/U/0/")) }
        }

        fun restoreBackup(restoreFiles: List<PolarBackupRestoreFile>): List<String> {
            val plan = PolarWorkflowRuntimePlanning.planBackupRestore(restoreFiles)
            capturedRestorePayloads += restoreFiles.map { file -> file.dataHex }
            return plan.commands
        }

        private fun backupPath(path: String): List<PolarBackupFileCapture> {
            return if (path.endsWith("/")) {
                listDirectory(path).flatMap { entry -> backupPath(path + entry) }
            } else {
                listOf(PolarBackupFileCapture(path, readFile(path)))
            }
        }

        private fun listDirectory(path: String): List<String> {
            capturedReadCommands += "GET:$path"
            return directories[path] ?: emptyList()
        }

        private fun readFile(path: String): String {
            capturedReadCommands += "GET:$path"
            return files.getValue(path)
        }

        private fun decodeAsciiHex(value: String): String {
            return value.chunked(2).joinToString(separator = "") { byte -> byte.toInt(16).toChar().toString() }
        }
    }

    private data class PolarBackupFileCapture(
        val path: String,
        val dataHex: String
    )

}
