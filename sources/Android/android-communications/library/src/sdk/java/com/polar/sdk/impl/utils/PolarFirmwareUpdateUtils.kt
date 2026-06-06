package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.PolarFirmwareVersionInfo
import com.polar.shared.sdk.PolarFirmwareUpdateModels
import fi.polar.remote.representation.protobuf.Device
import fi.polar.remote.representation.protobuf.Structures
import protocol.PftpRequest
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
            return PolarFirmwareUpdateModels.firmwareFilePriority(f1.name).compareTo(PolarFirmwareUpdateModels.firmwareFilePriority(f2.name))
        }
    }

    const val FIRMWARE_UPDATE_FILE_PATH = "/SYSUPDAT.IMG"
    const val BUFFER_SIZE = 8192

    private val DEVICE_FIRMWARE_INFO_PATH: String
        get() = PolarRuntimePlannerAdapter.firmwareDeviceInfoPath()
    private const val TAG = "PolarFirmwareUpdateUtils"

    suspend fun readDeviceFirmwareInfo(client: BlePsFtpClient, deviceId: String): PolarFirmwareVersionInfo {
        BleLogger.d(TAG, "readDeviceFirmwareInfo: $deviceId")
        val plan = PolarRuntimePlannerAdapter.planFileFacade("firmware-read-device-info", "GET", DEVICE_FIRMWARE_INFO_PATH)
        val response = client.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PolarRuntimePlannerAdapter.fileOperationCommand(plan))
                .setPath(PolarRuntimePlannerAdapter.fileOperationPath(plan))
                .build()
                .toByteArray()
        )
        val proto = Device.PbDeviceInfo.parseFrom(response.toByteArray())
        return PolarFirmwareVersionInfo(
            deviceFwVersion = devicePbVersionToString(proto.deviceVersion),
            deviceModelName = proto.modelName,
            deviceHardwareCode = proto.hardwareCode
        )
    }

    fun isAvailableFirmwareVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        return PolarFirmwareUpdateModels.isAvailableFirmwareVersionHigher(currentVersion, availableVersion)
    }

    fun unzipFirmwarePackage(zipBytes: ByteArray): ByteArray {
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

    private fun devicePbVersionToString(pbVersion: Structures.PbVersion): String =
        PolarFirmwareUpdateModels.deviceVersionToString(pbVersion.major, pbVersion.minor, pbVersion.patch)
}
