package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.sdk.impl.utils.PolarBackupManager
import com.polar.sdk.impl.utils.PolarBackupManager.BackupFileData
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader

class PolarBackupManagerTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `backupDevice() should read and backup files`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)

        val mockBackupFileContent = ByteArrayOutputStream().apply {
            write(("/SYS/BT/\n" +
                    "/U/*/USERID.BPB\n" +
                    "/RANDOM/FILE.TXT").toByteArray())
        }

        val builder = PbPFtpDirectory.newBuilder()
            .addAllEntries(
                listOf(
                    PbPFtpEntry.newBuilder().setName("BACKUP.TXT").setSize(1234).build(),
                    PbPFtpEntry.newBuilder().setName("BT/").setSize(1234).build(),
                )
            )

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            builder.build().writeTo(this)
        }

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/SYS/")
                .build().toByteArray()
        )} returns mockDirectoryContent

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/SYS/BACKUP.TXT")
                .build().toByteArray()
        )} returns mockBackupFileContent

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/SYS/BT/")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/USERID.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/RANDOM/FILE.TXT")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        // Default backup files
        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/S/PHYSDATA.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/S/UDEVSET.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/S/PREFS.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        // Act
        backupManager.backupDevice()

        // Assert
        coVerify {
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/SYS/")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/SYS/BACKUP.TXT")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/SYS/BT/")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/USERID.BPB")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/RANDOM/FILE.TXT")
                    .build().toByteArray()
            )
            // Default files
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/S/PHYSDATA.BPB")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/S/UDEVSET.BPB")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/S/PREFS.BPB")
                    .build().toByteArray()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `restoreBackup() should restore files`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)

        val mockFileData = listOf(
            BackupFileData(byteArrayOf(), "/SYS/BT/", "BTDEV.BPB"),
            BackupFileData(byteArrayOf(), "/SYS/BT/", "SVSTATUS.BPB"),
            BackupFileData(byteArrayOf(), "/RANDOM/", "FILE.TXT")
        )

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/SYS/BT/BTDEV.BPB").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/SYS/BT/SVSTATUS.BPB").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/RANDOM/FILE.TXT").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        // Act
        backupManager.restoreBackup(mockFileData)

        // Assert
        coVerify {
            mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                    .setPath("/SYS/BT/BTDEV.BPB")
                    .build().toByteArray(), any()
            )
            mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                    .setPath("/SYS/BT/SVSTATUS.BPB")
                    .build().toByteArray(), any()
            )
            mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                    .setPath("/RANDOM/FILE.TXT")
                    .build().toByteArray(), any()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `backup golden vectors expand files and preserve restore writes`() = runTest {
        val vector = loadBackupVector("backup-expansion-and-restore-writes")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val backupManager = PolarBackupManager(mockClient)

        coEvery { mockClient.request(any<ByteArray>()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            when {
                input.getAsJsonObject("directories").has(operation.path) -> directoryResponse(input.getAsJsonObject("directories").getAsJsonArray(operation.path))
                input.getAsJsonObject("files").has(operation.path) -> ByteArrayOutputStream().apply {
                    write(hexToBytes(input.getAsJsonObject("files").get(operation.path).asString))
                }
                else -> error("Unexpected backup request path ${operation.path}")
            }
        }

        val backupFiles = backupManager.backupDevice()
        val actualBackupFiles = backupFiles.associate { it.directory + it.fileName to it.data.toHex() }

        expected.getAsJsonArray("backupFiles").forEach { element ->
            val file = element.asJsonObject
            Assert.assertEquals(file.get("path").asString, file.get("dataHex").asString, actualBackupFiles[file.get("path").asString])
        }
        Assert.assertEquals(expected.getAsJsonArray("backupFiles").size(), actualBackupFiles.size)

        val capturedWrites = mutableListOf<Pair<ByteArray, ByteArray>>()
        every { mockClient.write(any(), any()) } answers {
            val stream = secondArg<ByteArrayInputStream>()
            capturedWrites.add(firstArg<ByteArray>() to stream.readBytes())
            flowOf(0L)
        }

        backupManager.restoreBackup(
            input.getAsJsonArray("restoreFiles").map { element ->
                val file = element.asJsonObject
                BackupFileData(
                    hexToBytes(file.get("dataHex").asString),
                    file.get("directory").asString,
                    file.get("fileName").asString
                )
            }
        )

        val expectedWrites = expected.getAsJsonArray("restoreWrites")
        Assert.assertEquals(expectedWrites.size(), capturedWrites.size)
        expectedWrites.forEachIndexed { index, element ->
            val expectedWrite = element.asJsonObject
            val operation = PftpRequest.PbPFtpOperation.parseFrom(capturedWrites[index].first)
            Assert.assertEquals(expectedWrite.get("command").asString, operation.command.name)
            Assert.assertEquals(expectedWrite.get("path").asString, operation.path)
            Assert.assertEquals(expectedWrite.get("dataHex").asString, capturedWrites[index].second.toHex())
        }
    }

    @Test
    fun `backup golden vectors preserve Android restore failure policy`() = runBlocking {
        val vector = loadBackupVector("restore-failure-platform-policy")
        val inputFiles = vector.getAsJsonObject("input").getAsJsonArray("restoreFiles").map { it.asJsonObject }
        val expected = vector.getAsJsonObject("expected")
        val backupManager = PolarBackupManager(mockClient)
        val capturedWrites = mutableListOf<Pair<ByteArray, ByteArray>>()

        every { mockClient.write(any(), any()) } answers {
            val stream = secondArg<ByteArrayInputStream>()
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            capturedWrites.add(firstArg<ByteArray>() to stream.readBytes())
            if (inputFiles.first { it.get("directory").asString + it.get("fileName").asString == operation.path }.get("writeResult").asString == "failure") {
                flow { throw RuntimeException("restore write failed") }
            } else {
                flowOf(0L)
            }
        }

        withTimeout(5_000) {
            backupManager.restoreBackup(
                inputFiles.map { file ->
                    BackupFileData(
                        hexToBytes(file.get("dataHex").asString),
                        file.get("directory").asString,
                        file.get("fileName").asString
                    )
                }
            )
        }

        Assert.assertFalse(expected.getAsJsonObject("android").get("throws").asBoolean)
        val expectedWrites = expected.getAsJsonArray("writes")
        Assert.assertEquals(expectedWrites.size(), capturedWrites.size)
        expectedWrites.forEachIndexed { index, element ->
            val expectedWrite = element.asJsonObject
            val operation = PftpRequest.PbPFtpOperation.parseFrom(capturedWrites[index].first)
            Assert.assertEquals(expectedWrite.get("command").asString, operation.command.name)
            Assert.assertEquals(expectedWrite.get("path").asString, operation.path)
            Assert.assertEquals(expectedWrite.get("dataHex").asString, capturedWrites[index].second.toHex())
        }
    }

    @Test
    fun `backup root path planning delegates default merge to shared planner`() {
        Assert.assertEquals(
            listOf("/SYS/BT/", "/U/*/USERID.BPB", "/U/0/S/PHYSDATA.BPB", "/U/0/S/UDEVSET.BPB", "/U/0/S/PREFS.BPB"),
            PolarRuntimePlannerAdapter.backupRootPaths(listOf("/SYS/BT/", "/U/*/USERID.BPB"))
        )
        Assert.assertEquals(
            PolarRuntimePlannerAdapter.defaultBackupPaths(),
            PolarRuntimePlannerAdapter.backupRootPaths(emptyList())
        )
    }

    @Test
    fun `backup text parsing delegates Android compatibility policy to shared planner`() {
        Assert.assertEquals(
            listOf("/SYS/BT/", " /TRIMMED/PATH.BPB ", "/FINAL/NO_NEWLINE.BPB"),
            PolarRuntimePlannerAdapter.parseBackupTextForAndroid("/SYS/BT/\n /TRIMMED/PATH.BPB \n/SYS/BT/\n/FINAL/NO_NEWLINE.BPB")
        )
    }

    @Test
    fun `backup file path splitting delegates to shared planner`() {
        Assert.assertEquals(
            "/SYS/BT/" to "BTDEV.BPB",
            PolarRuntimePlannerAdapter.backupFilePath("/SYS/BT/BTDEV.BPB")
        )
    }

    @Test
    fun `backup golden vectors follow neutral KMP vector shape`() {
        listOf("backup-expansion-and-restore-writes", "restore-failure-platform-policy", "backup-workflow-readiness").forEach { id ->
            val vector = loadBackupVector(id)
            Assert.assertEquals(id, vector.get("id").asString)
            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.get("android").asBoolean)
            Assert.assertTrue(id, platforms.get("ios").asBoolean)
            Assert.assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `backup workflow readiness manifest is pinned before workflow migration`() {
        val vector = loadBackupVector("backup-workflow-readiness")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        Assert.assertEquals("backupWorkflowReadiness", input.get("kind").asString)
        Assert.assertEquals(BACKUP_WORKFLOW_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(BACKUP_WORKFLOW_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(BACKUP_WORKFLOW_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(BACKUP_WORKFLOW_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(listOf("com.polar.sdk.api.model.utils.PolarBackupManagerTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarBackupManagerTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.BackupUtilityCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun directoryResponse(entries: JsonArray): ByteArrayOutputStream {
        return ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    entries.map { element ->
                        val entry = element.asJsonObject
                        PbPFtpEntry.newBuilder()
                            .setName(entry.get("name").asString)
                            .setSize(entry.get("size").asLong)
                            .build()
                    }
                )
                .build()
                .writeTo(this)
        }
    }

    private fun loadBackupVector(id: String): JsonObject {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/backup-utils")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .first { it.get("id").asString == id }
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

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        val BACKUP_WORKFLOW_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/backup-utils/backup-expansion-and-restore-writes.json",
            "sdk/backup-utils/restore-failure-platform-policy.json"
        )

        val BACKUP_WORKFLOW_READINESS_FAMILIES = listOf(
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

        const val BACKUP_WORKFLOW_READINESS_COMMON_DECISION = "Backup workflow migration may proceed only after backup-expansion-and-restore-writes.json, restore-failure-platform-policy.json, and this readiness manifest are executable from shared commonTest, Android and iOS backup tests continue to reference the same vectors, BACKUP.TXT expansion and default user-file inclusion stay covered, restore PUT command order and payload bytes remain pinned, restore failure aggregation is deliberately standardized or deliberately preserved as a platform split, and the shared tests are compile-verified."
    }
}
