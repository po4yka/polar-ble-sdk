// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import protocol.PftpRequest
import java.io.ByteArrayInputStream

private const val TAG = "PolarWatchFaceUtils"

data class WatchfaceConfigFields(
    val timeStyleId: Int = 0,
    val complicationLayoutId: Int = 0,
    val backgroundStyleId: Int = 0,
    val accentColor: Long = 0L,
    val complicationIds: List<Int> = emptyList(),
    val fontfaceId: Int = 0
)

internal object PolarWatchFaceUtils {

    /** KVS key for "ui.watchface_config" */
    const val WATCH_FACE_CONFIG_KVS_KEY: Int = 1064434511

    private const val KVTX_FILE_PATH = "/SYS/KVTX"

    internal fun watchFaceReadOperation(): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val plan = PolarRuntimePlannerAdapter.planFileFacade("watch-face-read-kvtx", "GET", KVTX_FILE_PATH)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    internal fun watchFaceWriteOperation(): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val plan = PolarRuntimePlannerAdapter.planFileFacade("watch-face-write-kvtx", "PUT", KVTX_FILE_PATH)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    /**
     * Build a KVTXScript (WRITE_BYTES + COMMIT) carrying a full WatchfaceConfig FlatBuffer.
     * Delegates generic framing to [KvtxScriptUtils.buildWriteAndCommit].
     */
    fun buildKvtxScript(fields: WatchfaceConfigFields): ByteArray {
        val configData = buildWatchFaceConfigFlatBuffer(fields)
        return KvtxScriptUtils.buildWriteAndCommit(WATCH_FACE_CONFIG_KVS_KEY, configData)
    }

    /**
     * Build a complete WatchfaceConfig FlatBuffer preserving ALL fields from [fields].
     *
     * FlatBuffers encodes scalar fields only when they differ from their default (0),
     * so unchanged zero fields are omitted automatically.
     */
    internal fun buildWatchFaceConfigFlatBuffer(fields: WatchfaceConfigFields): ByteArray {
        return PolarRuntimePlannerAdapter.buildWatchFaceConfigFlatBuffer(
            PolarRuntimePlannerAdapter.PlannedWatchFaceFields(
                timeStyleId = fields.timeStyleId,
                complicationLayoutId = fields.complicationLayoutId,
                backgroundStyleId = fields.backgroundStyleId,
                accentColor = fields.accentColor,
                complicationIds = fields.complicationIds,
                fontfaceId = fields.fontfaceId
            )
        )
    }

    fun extractWatchFaceConfigFromKvtxScript(script: ByteArray): ByteArray? =
        KvtxScriptUtils.extractValueForKey(script, WATCH_FACE_CONFIG_KVS_KEY)

    fun parseWatchFaceConfigFlatBuffer(raw: ByteArray): WatchfaceConfigFields {
        val empty = sharedWatchfaceConfigFields()

        if (raw.size < 4) {
            BleLogger.w(TAG, "parseWatchFaceConfigFlatBuffer: too short (${raw.size} bytes), returning defaults")
            return empty
        }

        val sharedFields = PolarRuntimePlannerAdapter.parseWatchFaceConfigFlatBuffer(raw)
        return WatchfaceConfigFields(
            timeStyleId = sharedFields.timeStyleId,
            complicationLayoutId = sharedFields.complicationLayoutId,
            backgroundStyleId = sharedFields.backgroundStyleId,
            accentColor = sharedFields.accentColor,
            complicationIds = sharedFields.complicationIds,
            fontfaceId = sharedFields.fontfaceId
        )
    }

    private fun sharedWatchfaceConfigFields(
        timeStyleId: Int? = null,
        complicationLayoutId: Int? = null,
        backgroundStyleId: Int? = null,
        accentColor: Long? = null,
        complicationIds: List<Int>? = null,
        fontfaceId: Int? = null
    ): WatchfaceConfigFields {
        val fields = PolarRuntimePlannerAdapter.watchFaceConfigFields(
            timeStyleId = timeStyleId,
            complicationLayoutId = complicationLayoutId,
            backgroundStyleId = backgroundStyleId,
            accentColor = accentColor,
            complicationIds = complicationIds,
            fontfaceId = fontfaceId
        )
        return WatchfaceConfigFields(
            timeStyleId = fields.timeStyleId,
            complicationLayoutId = fields.complicationLayoutId,
            backgroundStyleId = fields.backgroundStyleId,
            accentColor = fields.accentColor,
            complicationIds = fields.complicationIds,
            fontfaceId = fields.fontfaceId
        )
    }

    internal suspend fun readWatchFaceConfigFields(
        identifier: String,
        listener: BleDeviceListener?,
        handleError: (Throwable) -> Exception
    ): WatchfaceConfigFields {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "readWatchFaceConfigFields: GET /SYS/KVTX")
        val readOperation = watchFaceReadOperation()
        val kvtxScript = try {
            client.request(PolarRuntimePlannerAdapter.fileOperationBytes(readOperation)).toByteArray()
        } catch (throwable: Throwable) {
            BleLogger.e(TAG, "readWatchFaceConfigFields: GET failed: ${throwable.message}")
            throw handleError(throwable)
        }
        BleLogger.d(TAG, "readWatchFaceConfigFields: received ${kvtxScript.size} bytes")
        val flatBufferBytes = extractWatchFaceConfigFromKvtxScript(kvtxScript)
        if (flatBufferBytes == null) {
            BleLogger.w(TAG, "readWatchFaceConfigFields: key not found, returning all-defaults")
            return WatchfaceConfigFields()
        }
        return parseWatchFaceConfigFlatBuffer(flatBufferBytes)
    }

    internal suspend fun writeWatchFaceComplicationInts(
        identifier: String,
        int32Ids: List<Int>,
        listener: BleDeviceListener?,
        handleError: (Throwable) -> Exception
    ) {
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: reading current config before write")
        val existingFields = readWatchFaceConfigFields(identifier, listener, handleError)
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: existing=$existingFields")

        val mergedFields = existingFields.copy(complicationIds = int32Ids)
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: merged=$mergedFields")

        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val kvtxScript = buildKvtxScript(mergedFields)
        BleLogger.d(TAG, "writeWatchFaceComplicationInts: PUT ${kvtxScript.size} bytes to $KVTX_FILE_PATH")
        val writeOperation = watchFaceWriteOperation()
        val requestBytes = PolarRuntimePlannerAdapter.fileOperationBytes(writeOperation)
        PolarRuntimePlannerAdapter.ensurePsFtpWriteRuntimePlan(kvtxScript.size)
        client.write(requestBytes, ByteArrayInputStream(kvtxScript)).collect {}
    }
}
