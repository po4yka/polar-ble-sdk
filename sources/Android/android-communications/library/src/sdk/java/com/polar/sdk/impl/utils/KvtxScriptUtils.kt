// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

/**
 * Generic KVTXScript builder and scanner.
 */
internal object KvtxScriptUtils {

    const val CMD_WRITE_BYTES: Byte = 0x00
    const val CMD_APPEND_BYTES: Byte = 0x01
    const val CMD_REMOVE: Byte = 0x02
    const val CMD_COPY: Byte = 0x03
    const val CMD_MOVE: Byte = 0x04
    const val CMD_COMMIT: Byte = 0x05
    const val CMD_WRITE_BYTES_EX: Byte = 0x06
    const val CMD_APPEND_BYTES_EX: Byte = 0x07
    const val CMD_REMOVE_EX: Byte = 0x08

    /**
     * Build a KVTXScript that writes [data] to [kvKey] and commits.
     *
     * Structure: WRITE_BYTES(key, data) + COMMIT
     */
    fun buildWriteAndCommit(kvKey: Int, data: ByteArray): ByteArray =
        PolarRuntimePlannerAdapter.kvtxBuildWriteAndCommit(kvKey, data)

    /**
     * Scan a full KVTXScript binary and extract the raw value bytes stored under [kvKey].
     *
     * Returns `null` if the key is not present (or was removed) in the script.
     */
    fun extractValueForKey(script: ByteArray, kvKey: Int): ByteArray? =
        PolarRuntimePlannerAdapter.kvtxExtractValueForKey(script, kvKey)

    fun u32Le(value: Int): ByteArray =
        PolarRuntimePlannerAdapter.kvtxU32Le(value)
}
