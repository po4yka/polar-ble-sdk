package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import com.polar.sdk.impl.utils.PolarFirmwareUpdateUtils
import com.polar.sdk.impl.utils.PolarFirmwareUpdateUtils.FwFileComparator
import fi.polar.remote.representation.protobuf.Device
import fi.polar.remote.representation.protobuf.Structures.PbVersion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class PolarFirmwareUpdateUtilsTest {

    private val mockClient = mockk<BlePsFtpClient>()
    private val firmwareFilePath = "/DEVICE.BPB"

    @Test
    fun `readDeviceFirmwareInfo() should return firmware info`() = runTest {
        // Arrange
        Assert.assertEquals(firmwareFilePath, PolarRuntimePlannerAdapter.firmwareDeviceInfoPath())
        val deviceId = "123456"
        val firmwareVersion = "1.2.0"
        val modelName = "Model"
        val hardwareCode = "00112233.01"

        val proto = Device.PbDeviceInfo.newBuilder()
                .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
                .setModelName(modelName)
                .setHardwareCode(hardwareCode)
                .build()

        val mockResponseContent = ByteArrayOutputStream().apply {
            proto.writeTo(this)
        }

        coEvery { mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath(firmwareFilePath)
                        .build().toByteArray()
        ) } returns mockResponseContent

        // Act
        val firmwareInfo = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(mockClient, deviceId)

        // Assert
        coVerify {
            mockClient.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(firmwareFilePath)
                            .build().toByteArray()
            )
        }
        confirmVerified(mockClient)

        assert(firmwareInfo.deviceFwVersion == firmwareVersion)
        assert(firmwareInfo.deviceModelName == modelName)
        assert(firmwareInfo.deviceHardwareCode == hardwareCode)
    }

    @Test
    fun `isAvailableFirmwareVersionHigher() should return true when current version is smaller than available version`() {
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "1.0.0",
                "2.0.0"
            )
        )
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "2.0.1"
            )
        )
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "2.1.0"
            )
        )
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "3.0.0"
            )
        )
    }

    @Test
    fun `isAvailableFirmwareVersionHigher() should return false when current version is same or higher than available version`() {
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "1.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.1",
                "2.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.1.0",
                "2.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "3.0.0",
                "2.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "2.0.0"
            )
        )
    }

    @Test
    fun `FwFileComparator sorts files correctly`() {
        // Arrange
        val btFile = mockFile("BTUPDAT.BIN")
        val sysFile = mockFile("SYSUPDAT.IMG")
        val touchFile = mockFile("TCHUPDAT.BIN")
        val files = mutableListOf(btFile, sysFile, touchFile)

        // Act
        files.sortWith(FwFileComparator())

        // Assert
        Assert.assertEquals(btFile, files[0])
        Assert.assertEquals(touchFile, files[1])
        Assert.assertEquals(sysFile, files[2])
    }

    @Test
    fun `FwFileComparator keeps already sorted files`() {
        // Arrange
        val f1 = mockFile("BTUPDAT.BIN")
        val f2 = mockFile("TCHUPDAT.BIN")
        val f3 = mockFile("SYSUPDAT.IMG")
        val files = mutableListOf(f1, f2, f3)

        // Act
        files.sortWith(FwFileComparator())

        // Assert
        Assert.assertEquals(f1, files[0])
        Assert.assertEquals(f2, files[1])
        Assert.assertEquals(f3, files[2])
    }

    @Test
    fun `firmware device info golden vectors map proto to model`() = runTest {
        loadFirmwareUpdateVectors()
            .filter { it.inputKind() == "deviceInfo" }
            .forEach { vector ->
                val caseId = vector.get("id").asString
                val protoFields = vector.getAsJsonObject("input").getAsJsonObject("proto")
                val version = protoFields.getAsJsonObject("version")
                val proto = Device.PbDeviceInfo.newBuilder()
                    .setDeviceVersion(
                        PbVersion.newBuilder()
                            .setMajor(version.get("major").asInt)
                            .setMinor(version.get("minor").asInt)
                            .setPatch(version.get("patch").asInt)
                    )
                    .setModelName(protoFields.get("modelName").asString)
                    .setHardwareCode(protoFields.get("hardwareCode").asString)
                    .build()
                val mockResponseContent = ByteArrayOutputStream().apply {
                    proto.writeTo(this)
                }
                val client = mockk<BlePsFtpClient>()
                coEvery { client.request(any()) } returns mockResponseContent

                val firmwareInfo = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client, "123456")
                val expected = vector.getAsJsonObject("expected")

                Assert.assertEquals(caseId, expected.get("deviceFwVersion").asString, firmwareInfo.deviceFwVersion)
                Assert.assertEquals(caseId, expected.get("deviceModelName").asString, firmwareInfo.deviceModelName)
                Assert.assertEquals(caseId, expected.get("deviceHardwareCode").asString, firmwareInfo.deviceHardwareCode)
            }
    }

    @Test
    fun `firmware version comparison golden vectors match current policy`() {
        loadFirmwareUpdateVectors()
            .filter { it.inputKind() == "versionComparison" }
            .flatMap { it.getAsJsonObject("input").getAsJsonArray("cases").toJsonObjects() }
            .forEach { testCase ->
                Assert.assertEquals(
                    "${testCase.get("currentVersion").asString} -> ${testCase.get("availableVersion").asString}",
                    testCase.get("expectedHigher").asBoolean,
                    PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                        testCase.get("currentVersion").asString,
                        testCase.get("availableVersion").asString
                    )
                )
            }
    }

    @Test
    fun `firmware version comparison golden vectors preserve invalid version policy`() {
        loadFirmwareUpdateVectors()
            .filter { it.inputKind() == "versionComparisonError" }
            .flatMap { it.getAsJsonObject("input").getAsJsonArray("cases").toJsonObjects() }
            .forEach { testCase ->
                Assert.assertThrows(
                    "${testCase.get("currentVersion").asString} -> ${testCase.get("availableVersion").asString}",
                    NumberFormatException::class.java
                ) {
                    PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                        testCase.get("currentVersion").asString,
                        testCase.get("availableVersion").asString
                    )
                }
            }
    }

    @Test
    fun `firmware file ordering golden vectors keep system update last`() {
        loadFirmwareUpdateVectors()
            .filter { it.inputKind() == "fileOrdering" }
            .flatMap { it.getAsJsonObject("input").getAsJsonArray("cases").toJsonObjects() }
            .forEach { testCase ->
                val files = testCase.getAsJsonArray("input")
                    .map { File(it.asString) }
                    .toMutableList()

                files.sortWith(FwFileComparator())

                Assert.assertEquals(
                    testCase.getAsJsonArray("expected").map { it.asString },
                    files.map { it.name }
                )
            }
    }

    @Test
    fun `firmware payload file names use shared skip and ordering policy`() {
        Assert.assertEquals(
            listOf("APPUPDAT.BIN", "BTUPDAT.BIN", "README.TXT", "SYSUPDAT.IMG"),
            PolarRuntimePlannerAdapter.firmwarePayloadFileNames(listOf("readme.txt", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN", "README.TXT"))
        )
    }

    @Test
    fun `firmware package extraction uses shared payload filter and ordering policy`() {
        val zipBytes = zipPackage(
            "readme.txt" to "skip".toByteArray(),
            "SYSUPDAT.IMG" to "sys".toByteArray(),
            "APPUPDAT.BIN" to "app".toByteArray(),
            "BTUPDAT.BIN" to "bt".toByteArray()
        )

        val payloads = PolarFirmwareUpdateUtils.extractFirmwarePackagePayloads(zipBytes)

        Assert.assertEquals(listOf("APPUPDAT.BIN", "BTUPDAT.BIN", "SYSUPDAT.IMG"), payloads.map { it.first })
        Assert.assertEquals(listOf("app", "bt", "sys"), payloads.map { String(it.second) })
        Assert.assertArrayEquals("app".toByteArray(), PolarFirmwareUpdateUtils.unzipFirmwarePackage(zipBytes))
    }

    @Test
    fun `firmware write failure maps shared battery terminal to Android public error`() {
        val batteryError = PftpResponseError("Battery too low", PbPFtpError.BATTERY_TOO_LOW_VALUE)
        val rebootOnSystemUpdate = PftpResponseError("Rebooting", PbPFtpError.REBOOTING_VALUE)
        val rebootOnNonSystemUpdate = PftpResponseError("Rebooting", PbPFtpError.REBOOTING_VALUE)
        val transportError = IllegalStateException("transport failed")

        val mappedBatteryError = PolarFirmwareUpdateUtils.firmwareWriteFailure(batteryError, "SYSUPDAT.IMG")

        Assert.assertTrue(mappedBatteryError is PolarBleSdkInternalException)
        Assert.assertEquals("Battery too low to perform firmware update", mappedBatteryError?.message)
        Assert.assertNull(PolarFirmwareUpdateUtils.firmwareWriteFailure(rebootOnSystemUpdate, "SYSUPDAT.IMG"))
        Assert.assertSame(rebootOnNonSystemUpdate, PolarFirmwareUpdateUtils.firmwareWriteFailure(rebootOnNonSystemUpdate, "BTUPDAT.BIN"))
        Assert.assertSame(transportError, PolarFirmwareUpdateUtils.firmwareWriteFailure(transportError, "SYSUPDAT.IMG"))
    }

    @Test
    fun `firmware golden vectors follow neutral KMP vector shape`() {
        loadFirmwareUpdateVectors().forEach { vector ->
            val id = vector.get("id").asString
            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            Assert.assertTrue(id, vector.getAsJsonObject("input").has("kind"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.get("android").asBoolean)
            Assert.assertTrue(id, platforms.get("ios").asBoolean)
            Assert.assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `workflow runtime policy vector is pinned before workflow migration`() {
        val vector = loadFirmwareUpdateVectors().first { it.get("id").asString == "workflow-runtime-policy" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val expectedCommonPrototype = expected.getAsJsonObject("commonWorkflowPrototype")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val scenarioIds = input.getAsJsonArray("scenarios").map { it.asJsonObject.get("id").asString }
        val commonPrototypeCaseIds = expectedCommonPrototype.getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }

        Assert.assertEquals("firmwareWorkflowRuntimePolicy", input.get("kind").asString)
        Assert.assertEquals(FIRMWARE_WORKFLOW_SCENARIOS, scenarioIds)
        Assert.assertEquals("firmware-update-workflow-runtime-matrix", expected.get("policy").asString)
        Assert.assertEquals(FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT, expected.get("migrationRequirement").asString)
        Assert.assertEquals("executable shared commonTest", expectedCommonPrototype.get("status").asString)
        Assert.assertEquals(FIRMWARE_WORKFLOW_SCENARIOS, commonPrototypeCaseIds)
        Assert.assertEquals("shared-common-test", vector.getAsJsonObject("execution").get("common").asString)
        Assert.assertEquals("partial-production-shared-policy-consumption", vector.getAsJsonObject("execution").get("android").asString)
        Assert.assertEquals("partial-production-shared-policy-consumption", vector.getAsJsonObject("execution").get("ios").asString)
        Assert.assertEquals(FIRMWARE_WORKFLOW_ANDROID_PRODUCTION_EVIDENCE, vector.getAsJsonObject("platformExpectations").get("android").asString)
        Assert.assertEquals(FIRMWARE_WORKFLOW_IOS_PRODUCTION_EVIDENCE, vector.getAsJsonObject("platformExpectations").get("ios").asString)
        Assert.assertEquals(FIRMWARE_WORKFLOW_COMMON_DECISION, vector.getAsJsonObject("platformExpectations").getAsJsonObject("commonDecision").get("workflowPolicy").asString)
        Assert.assertTrue("workflow-runtime-policy", vector.has("execution"))
        Assert.assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarFirmwareUpdateUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `workflow runtime readiness manifest is pinned before workflow migration`() {
        val vector = loadFirmwareUpdateVectors().first { it.get("id").asString == "workflow-runtime-readiness" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        Assert.assertEquals("firmwareWorkflowRuntimeReadiness", input.get("kind").asString)
        Assert.assertEquals("sdk/firmware-update/workflow-runtime-policy.json", input.get("policyVectorPath").asString)
        Assert.assertEquals(FIRMWARE_WORKFLOW_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(FIRMWARE_WORKFLOW_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals("executable shared commonTest runtime planning guard", expected.getAsJsonObject("commonRuntimePrototype").get("status").asString)
        Assert.assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.getAsJsonObject("commonRuntimePrototype").get("reason").asString)
        Assert.assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarFirmwareUpdateUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `firmware utility readiness manifest is pinned before utility migration`() {
        val vector = loadFirmwareUpdateVectors().first { it.get("id").asString == "firmware-utility-readiness" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        Assert.assertEquals("firmwareUtilityReadiness", input.get("kind").asString)
        Assert.assertEquals("compileVerifiedPreMigrationCharacterization", expected.get("migrationReadiness").asString)
        Assert.assertEquals(FIRMWARE_UTILITY_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(FIRMWARE_UTILITY_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(FIRMWARE_UTILITY_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarFirmwareUpdateUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.FirmwareUpdateUtilityCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun mockFile(name: String): File = File(name)

    private fun JsonArray.toJsonObjects(): List<JsonObject> = map { it.asJsonObject }

    private fun JsonObject.inputKind(): String = getAsJsonObject("input").get("kind").asString

    private fun loadFirmwareUpdateVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/firmware-update")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
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

    private fun zipPackage(vararg entries: Pair<String, ByteArray>): ByteArray {
        return ByteArrayOutputStream().use { byteOutput ->
            ZipOutputStream(byteOutput).use { zipOutput ->
                entries.forEach { (name, payload) ->
                    zipOutput.putNextEntry(ZipEntry(name))
                    zipOutput.write(payload)
                    zipOutput.closeEntry()
                }
            }
            byteOutput.toByteArray()
        }
    }

    private companion object {
        val FIRMWARE_UTILITY_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/firmware-update/device-info-basic.json",
            "sdk/firmware-update/device-info-zero-version.json",
            "sdk/firmware-update/version-comparison.json",
            "sdk/firmware-update/version-comparison-invalid.json",
            "sdk/firmware-update/file-ordering.json"
        )

        val FIRMWARE_UTILITY_READINESS_FAMILIES = listOf(
            "device-info-protobuf-mapping",
            "zero-version-preservation",
            "dotted-integer-version-comparison",
            "invalid-version-typed-parse-failure",
            "system-update-file-ordering-last",
            "platform-firmware-utility-vector-references",
            "compile-verification-gate"
        )

        val FIRMWARE_WORKFLOW_READINESS_FAMILIES = listOf(
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

        val FIRMWARE_WORKFLOW_SCENARIOS = listOf(
            "check-update-not-available",
            "check-update-available",
            "download-failure",
            "retryable-server-failure",
            "client-request-failure",
            "empty-or-invalid-zip",
            "cancel-after-package-fetch-cleans-up-before-ble-write",
            "write-package-success-with-system-update-last",
            "system-update-reboot-response-is-success",
            "non-system-reboot-response-is-terminal-failure",
            "battery-too-low-response-is-terminal-failure"
        )

        const val FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT = "Before moving firmware update orchestration into common KMP code, implement injectable fake network, fake filesystem or zip extraction, and fake BLE write dependencies that can reproduce update availability, download failures, invalid packages, sorted package writes, reboot success, and terminal device errors."

        const val FIRMWARE_WORKFLOW_COMMON_DECISION = "separate device-info parsing, server availability, retryable server failures, package download, zip extraction, file ordering, BLE write progress, finalization step planning, reboot success, non-system reboot failure, and terminal device errors into typed common workflow states before KMP migration"

        const val FIRMWARE_WORKFLOW_ANDROID_PRODUCTION_EVIDENCE = "BDBleApiImpl and PolarFirmwareUpdateUtils consume shared planning for device-info path, payload entry filtering, firmware file ordering/write paths, PSFTP write progress throttling, retry delay execution, finalization step planning, reboot response success, non-system reboot failure, and battery-too-low terminal write policy while keeping network, zip parsing, backup, reconnect, filesystem, and BLE writes platform-owned."

        const val FIRMWARE_WORKFLOW_IOS_PRODUCTION_EVIDENCE = "PolarBleApiImpl and PolarFirmwareUpdateUtils consume shared planning for device-info path, payload entry filtering, firmware file ordering/write paths, PSFTP write progress throttling, retry delay execution, finalization step planning, reboot response success, non-system reboot failure, and battery-too-low terminal write policy while keeping network, zip parsing, backup, reconnect, filesystem, and BLE writes platform-owned."

        const val FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION = "Firmware workflow migration may proceed only after workflow-runtime-policy.json and this readiness manifest are executable from shared commonTest, fake network/filesystem/BLE writer dependencies are injectable, shared production file-order/progress/finalization-step/terminal write policy consumption remains pinned on Android and iOS, retryable fake-network server failure classification, terminal device errors, cancellation cleanup before BLE writes, and shared-planned retry delay execution are pinned, public facade error mapping is pinned, and the shared tests are compile-verified."
    }
}
