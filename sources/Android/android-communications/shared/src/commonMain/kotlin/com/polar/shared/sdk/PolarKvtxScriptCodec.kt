package com.polar.shared.sdk

object PolarKvtxScriptCodec {
    const val CMD_WRITE_BYTES: Byte = 0x00
    const val CMD_APPEND_BYTES: Byte = 0x01
    const val CMD_REMOVE: Byte = 0x02
    const val CMD_COPY: Byte = 0x03
    const val CMD_MOVE: Byte = 0x04
    const val CMD_COMMIT: Byte = 0x05
    const val CMD_WRITE_BYTES_EX: Byte = 0x06
    const val CMD_APPEND_BYTES_EX: Byte = 0x07
    const val CMD_REMOVE_EX: Byte = 0x08

    fun buildWriteAndCommit(kvKey: Long, data: ByteArray): ByteArray {
        return byteArrayOf(CMD_WRITE_BYTES) + u32Le(kvKey) + u32Le(data.size.toLong()) + data + byteArrayOf(CMD_COMMIT)
    }

    fun extractValueForKey(script: ByteArray, kvKey: Long): ByteArray? {
        var index = 0
        var current: ByteArray? = null
        val key = kvKey and UINT32_MASK
        while (index < script.size) {
            when (val opcode = script[index].toInt() and 0xFF) {
                CMD_WRITE_BYTES.toInt() -> {
                    val command = script.readKeyAndData(index + 1)
                    if (command.key == key) current = command.data
                    index = command.nextIndex
                }
                CMD_APPEND_BYTES.toInt() -> {
                    val command = script.readKeyAndData(index + 1)
                    if (command.key == key) current = (current ?: ByteArray(0)) + command.data
                    index = command.nextIndex
                }
                CMD_REMOVE.toInt() -> {
                    val commandKey = script.readLeU32(index + 1)
                    if (commandKey == key) current = null
                    index += 5
                }
                CMD_COPY.toInt(), CMD_MOVE.toInt() -> {
                    script.readLeU32(index + 1)
                    script.readLeU32(index + 5)
                    index += 9
                }
                CMD_COMMIT.toInt() -> index += 1
                CMD_WRITE_BYTES_EX.toInt() -> {
                    val command = script.readExtendedKeyAndData(index + 1)
                    if (command.key == key && command.indexBytes.isEmpty()) current = command.data
                    index = command.nextIndex
                }
                CMD_APPEND_BYTES_EX.toInt() -> {
                    val command = script.readExtendedKeyAndData(index + 1)
                    if (command.key == key && command.indexBytes.isEmpty()) current = (current ?: ByteArray(0)) + command.data
                    index = command.nextIndex
                }
                CMD_REMOVE_EX.toInt() -> {
                    val command = script.readExtendedKey(index + 1)
                    if (command.key == key && command.indexBytes.isEmpty()) current = null
                    index = command.nextIndex
                }
                else -> return current
            }
        }
        return current
    }

    fun u32Le(value: Long): ByteArray {
        val unsigned = value and UINT32_MASK
        return ByteArray(4) { index -> ((unsigned shr (index * 8)) and 0xFF).toByte() }
    }

    private fun ByteArray.readKeyAndData(start: Int): CommandData {
        val key = readLeU32(start)
        val length = readLeI32(start + 4)
        val dataStart = start + 8
        val dataEnd = dataStart + length
        if (length < 0 || dataEnd < dataStart || dataEnd > size) throw PolarKvtxMalformedScriptException("Malformed KVTX command payload")
        return CommandData(key = key, data = copyOfRange(dataStart, dataEnd), nextIndex = dataEnd)
    }

    private fun ByteArray.readExtendedKeyAndData(start: Int): ExtendedCommandData {
        val command = readExtendedKey(start)
        val length = readLeI32(command.nextIndex)
        val dataStart = command.nextIndex + 4
        val dataEnd = dataStart + length
        if (length < 0 || dataEnd < dataStart || dataEnd > size) throw PolarKvtxMalformedScriptException("Malformed KVTX extended command payload")
        return ExtendedCommandData(command.key, command.indexBytes, copyOfRange(dataStart, dataEnd), dataEnd)
    }

    private fun ByteArray.readExtendedKey(start: Int): ExtendedCommandKey {
        val key = readLeU32(start)
        if (start + 5 > size) throw PolarKvtxMalformedScriptException("Malformed KVTX extended command key")
        val indexLength = this[start + 4].toInt() and 0xFF
        val indexStart = start + 5
        val indexEnd = indexStart + indexLength
        if (indexEnd > size) throw PolarKvtxMalformedScriptException("Malformed KVTX extended command index")
        return ExtendedCommandKey(key = key, indexBytes = copyOfRange(indexStart, indexEnd), nextIndex = indexEnd)
    }

    private fun ByteArray.readLeU32(offset: Int): Long {
        if (offset + 4 > size) throw PolarKvtxMalformedScriptException("Malformed KVTX uint32")
        var value = 0L
        for (index in 0 until 4) {
            value = value or ((this[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    private fun ByteArray.readLeI32(offset: Int): Int {
        if (offset + 4 > size) throw PolarKvtxMalformedScriptException("Malformed KVTX int32")
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8) or ((this[offset + 2].toInt() and 0xFF) shl 16) or ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private data class CommandData(val key: Long, val data: ByteArray, val nextIndex: Int)
    private data class ExtendedCommandKey(val key: Long, val indexBytes: ByteArray, val nextIndex: Int)
    private data class ExtendedCommandData(val key: Long, val indexBytes: ByteArray, val data: ByteArray, val nextIndex: Int)
    private const val UINT32_MASK = 0xFFFF_FFFFL
}

class PolarKvtxMalformedScriptException(message: String) : IllegalArgumentException(message)
