package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.api.model.PolarFirmwareVersionInfo
import fi.polar.remote.representation.protobuf.Device
import fi.polar.remote.representation.protobuf.Structures
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

internal object PolarFirmwareUpdateUtils {

    /**
     * Comparator for sorting FW files so that the order doesn't matter as long as
     * the SYSUPDAT.IMG file is the last one (since it makes the device boot itself).
     */
    class FwFileComparator : Comparator<File> {
        override fun compare(f1: File, f2: File): Int {
            return PolarRuntimePlannerAdapter.firmwareFilePriority(f1.name).compareTo(PolarRuntimePlannerAdapter.firmwareFilePriority(f2.name))
        }
    }

    const val BUFFER_SIZE = 8192

    private val DEVICE_FIRMWARE_INFO_PATH: String
        get() = PolarRuntimePlannerAdapter.firmwareDeviceInfoPath()
    private const val TAG = "PolarFirmwareUpdateUtils"

    suspend fun readDeviceFirmwareInfo(client: BlePsFtpClient, deviceId: String): PolarFirmwareVersionInfo {
        BleLogger.d(TAG, "readDeviceFirmwareInfo: $deviceId")
        val plan = PolarRuntimePlannerAdapter.planFileFacade("firmware-read-device-info", "GET", DEVICE_FIRMWARE_INFO_PATH)
        val response = client.request(
            PolarRuntimePlannerAdapter.fileOperationBytes(plan)
        )
        val proto = Device.PbDeviceInfo.parseFrom(response.toByteArray())
        return PolarFirmwareVersionInfo(
            deviceFwVersion = devicePbVersionToString(proto.deviceVersion),
            deviceModelName = proto.modelName,
            deviceHardwareCode = proto.hardwareCode
        )
    }

    fun isAvailableFirmwareVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        return PolarRuntimePlannerAdapter.isAvailableFirmwareVersionHigher(currentVersion, availableVersion)
    }

    fun unzipFirmwarePackage(zipBytes: ByteArray): ByteArray {
        extractFirmwarePackagePayloads(zipBytes).firstOrNull()?.let { return it.second }
        try {
            val byteArrayInputStream = ByteArrayInputStream(zipBytes)
            val zipInputStream = ZipInputStream(byteArrayInputStream)
            val byteArrayOutputStream = ByteArrayOutputStream()

            zipInputStream.nextEntry
            val buffer = ByteArray(BUFFER_SIZE)
            var length: Int

            while (zipInputStream.read(buffer).also { length = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, length)
            }

            zipInputStream.closeEntry()
            zipInputStream.close()

            return byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to unzip firmware package: $e")
            throw e
        }
    }

    fun extractFirmwarePackagePayloads(zipBytes: ByteArray): List<Pair<String, ByteArray>> {
        val firmwareFiles = mutableListOf<Pair<String, ByteArray>>()
        val buffer = ByteArray(BUFFER_SIZE)
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                val entryFileName = entry.name
                if (!PolarRuntimePlannerAdapter.firmwarePackageEntryIsPayload(entryFileName)) {
                    BleLogger.d(TAG, "Skipping firmware package entry $entryFileName")
                    zipInputStream.closeEntry()
                    continue
                }

                val byteArrayOutputStream = ByteArrayOutputStream()
                var length: Int
                while (zipInputStream.read(buffer).also { length = it } != -1) {
                    byteArrayOutputStream.write(buffer, 0, length)
                }
                BleLogger.d(TAG, "Extracted firmware package payload: $entryFileName")
                firmwareFiles.add(entryFileName to byteArrayOutputStream.toByteArray())
                zipInputStream.closeEntry()
            }
        }

        val orderedFirmwareNames = PolarRuntimePlannerAdapter.firmwarePayloadFileNames(firmwareFiles.map { it.first })
        val remainingFirmwareFiles = firmwareFiles.toMutableList()
        return orderedFirmwareNames.mapNotNull { fileName ->
            val index = remainingFirmwareFiles.indexOfFirst { it.first == fileName }
            if (index >= 0) remainingFirmwareFiles.removeAt(index) else null
        }
    }

    fun firmwareWriteFailure(error: Throwable, fileName: String): Throwable? {
        val terminal = (error as? PftpResponseError)?.let { pftpError ->
            PolarRuntimePlannerAdapter.firmwareWriteTerminal(pftpError.error, fileName)
        } ?: return error
        return when (terminal) {
            "success-rebooting" -> {
                val plan = PolarRuntimePlannerAdapter.planFirmwareSystemUpdateRebootSuccessWorkflow(listOf(fileName))
                require(plan.statuses.last() == "fwUpdateCompletedSuccessfully")
                require(plan.terminalError == null)
                null
            }
            "battery-too-low" -> {
                val plan = PolarRuntimePlannerAdapter.planFirmwareBatteryTooLowTerminalWorkflow(listOf(fileName))
                require(plan.statuses.last() == "fwUpdateFailed")
                require(plan.terminalError == "battery-too-low")
                PolarBleSdkInternalException("Battery too low to perform firmware update")
            }
            else -> error
        }
    }

    private fun devicePbVersionToString(pbVersion: Structures.PbVersion): String =
        PolarRuntimePlannerAdapter.firmwareDeviceVersion(pbVersion.major, pbVersion.minor, pbVersion.patch)
}
