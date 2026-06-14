package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import com.polar.sdk.impl.utils.PolarServiceClientUtils.sessionPsFtpClientReady
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

internal object PolarFileUtils {

    internal suspend fun removeSingleFile(
        identifier: String,
        filePath: String,
        listener: BleDeviceListener?,
        tag: String
    ): ByteArrayOutputStream {
        val session = sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        val plan = PolarRuntimePlannerAdapter.planFileFacade(PolarFileFacadePlanIds.DELETE_FILE_SUCCESS, "REMOVE", filePath)
        return try {
            client.request(PolarRuntimePlannerAdapter.fileOperationBytes(plan))
        } catch (error: Throwable) {
            PolarRuntimePlannerAdapter.planFileRuntimeError("removeSingleFile", filePath, error)
            BleLogger.d(tag, "An error occurred while trying to remove $filePath, error: $error")
            throw handleError(error)
        }
    }

    fun listFiles(
        identifier: String,
        folderPath: String = "/",
        condition: FetchRecursiveCondition,
        listener: BleDeviceListener?,
        tag: String
    ): Flow<String> {
        val session = try {
            sessionPsFtpClientReady(identifier, listener = listener)
        } catch (error: Throwable) {
            return flow { throw error }
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return flow { throw PolarServiceNotAvailable() }
        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> {
                val path = PolarRuntimePlannerAdapter.normalizeFileListFolderPath(folderPath)
                fetchRecursively(
                    client = client,
                    path = path,
                    recurseDeep = true,
                    condition = condition,
                    tag = tag
                ).map { it.first }
            }
            else -> flow { throw PolarOperationNotSupported() }
        }
    }

    internal fun interface FetchRecursiveCondition {
        fun include(entry: String): Boolean
    }

    internal fun fetchRecursively(
        client: BlePsFtpClient,
        path: String,
        condition: FetchRecursiveCondition?,
        recurseDeep: Boolean?,
        tag: String
    ): Flow<Pair<String, Long>> = flow {
        BleLogger.d(tag, "fetchRecursively: Starting fetch for path: $path")

        val plan = PolarRuntimePlannerAdapter.planFileFacade(PolarFileFacadePlanIds.LIST_DIRECTORY_SUCCESS, "GET", path)

        try {
            val byteArrayOutputStream = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(plan))
            val dir = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
            val entries = mutableMapOf<String, Long>()

            if (condition != null) {
                for (entry in dir.entriesList) {
                    BleLogger.d(tag, "fetchRecursively: Found entry, name: ${entry.name}, size: ${entry.size}")
                    if (condition.include(entry.name)) {
                        entries[path + entry.name] = entry.size
                    }
                }
            } else {
                for (entry in dir.entriesList) {
                    entries[path + entry.name] = entry.size
                }
            }

            if (entries.isNotEmpty()) {
                for (entry in entries.toList()) {
                    if (entry.first.endsWith("/") && recurseDeep == true) {
                        fetchRecursively(client, entry.first, condition, recurseDeep, tag)
                            .collect { emit(it) }
                    } else {
                        emit(entry)
                    }
                }
            } else {
                BleLogger.d(tag, "fetchRecursively: No entries found for path: $path")
            }
        } catch (throwable: Throwable) {
            if (throwable is PftpResponseError) {
                val errorId = throwable.error
                // The file or directory was not found. Return empty results.
                if (errorId == PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number) {
                    BleLogger.w(tag, "Directory not found for path: $path, returning empty")
                    // Empty flow - just return
                } else {
                    BleLogger.e(tag, "fetchRecursively: Error occurred for path: $path, error: $throwable")
                    throw throwable
                }
            } else {
                BleLogger.e(tag, "fetchRecursively: Error occurred for path: $path, error: $throwable")
                throw throwable
            }
        }
    }

    internal suspend fun pFtpWriteOperation(
        identifier: String,
        listener: BleDeviceListener?,
        data: ByteArray,
        path: String,
        tag: String
    ) {
        val session = sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val plan = PolarRuntimePlannerAdapter.planFileFacade(PolarFileFacadePlanIds.WRITE_FILE_SUCCESS, "PUT", path, data.toHexString())
        val requestBytes = PolarRuntimePlannerAdapter.fileOperationBytes(plan)
        val dataInputStream = ByteArrayInputStream(data)

        try {
            PolarRuntimePlannerAdapter.ensurePsFtpWriteRuntimePlan(data.size)
            client.write(requestBytes, dataInputStream)
                .collect { progress ->
                    BleLogger.d(tag, "pFtpWriteOperation client write progress $progress: $path")
                }
            BleLogger.d(tag, "pFtpWriteOperation client write completed for $path")
        } catch (error: Throwable) {
            PolarRuntimePlannerAdapter.planFileRuntimeError("writeFile", path, error)
            BleLogger.e(tag, "pFtpWriteOperation() client write $path error: $error")
            throw error
        }
    }

    private fun handleError(throwable: Throwable): Exception {
        // Pass through SDK-level exceptions directly — do not wrap them
        if (throwable is Exception &&
            throwable.javaClass.name.startsWith("com.polar.sdk.api.errors")) {
            return throwable
        }
        if (throwable is BleDisconnected) {
            return PolarDeviceDisconnected()
        } else if (throwable is PftpResponseError) {
            val errorId = throwable.error
            val pftpError = PbPFtpError.forNumber(errorId)
            if (pftpError != null) return Exception(pftpError.toString())
        }
        return Exception(throwable)
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    /*
    * BLE Low Level methods. These are experimental methods. Usage is heavily discouraged,
    * use SDK APIs from PolarBleApi instead.
    */

    // Low level API method
    suspend fun readFile(identifier: String, filePath: String, listener: BleDeviceListener?, tag: String): ByteArray? {
        val session = try {
            sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            throw handleError(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        val plan = PolarRuntimePlannerAdapter.planFileFacade(PolarFileFacadePlanIds.READ_FILE_SUCCESS, "GET", filePath)
        return try {
            val data = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(plan))
            BleLogger.d(tag, "readFile at path filePath $filePath")
            data.toByteArray()
        } catch (throwable: Throwable) {
            PolarRuntimePlannerAdapter.planFileRuntimeError("readFile", filePath, throwable)
            throw handleError(throwable)
        }
    }

    // Low level API method
    suspend fun writeFile(identifier: String, filePath: String, listener: BleDeviceListener?, fileData: ByteArray, tag: String) {
        pFtpWriteOperation(identifier, listener, fileData, filePath, tag)
    }

    // Low level API method
    suspend fun getFileList(identifier: String, filePath: String, recurseDeep: Boolean, listener: BleDeviceListener?, tag: String): List<String> {
        val session = try {
            sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            throw handleError(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> {
                val path = PolarRuntimePlannerAdapter.normalizeFileListFolderPath(filePath)
                val results = mutableListOf<String>()
                fetchRecursively(
                    client = client,
                    path = path,
                    condition = null,
                    recurseDeep = recurseDeep,
                    tag = tag
                ).collect { results.add(it.first) }
                results
            }
            else -> throw PolarOperationNotSupported()
        }
    }

    // Low level API method
    suspend fun removeFileOrDirectory(
        identifier: String,
        filePath: String,
        listener: BleDeviceListener?,
        tag: String
    ) {
        val session = try {
            sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            throw handleError(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        val plan = PolarRuntimePlannerAdapter.planFileFacade(PolarFileFacadePlanIds.DELETE_FILE_SUCCESS, "REMOVE", filePath)
        try {
            client.request(PolarRuntimePlannerAdapter.fileOperationBytes(plan))
            BleLogger.d(tag, "All items successfully removed from filePath $filePath from device $identifier.")
        } catch (error: Throwable) {
            PolarRuntimePlannerAdapter.planFileRuntimeError("removeSingleFile", filePath, error)
            BleLogger.d(tag, "Error while trying to remove item from filePath $filePath from device $identifier, error: $error.")
            throw handleError(error)
        }
    }
}
