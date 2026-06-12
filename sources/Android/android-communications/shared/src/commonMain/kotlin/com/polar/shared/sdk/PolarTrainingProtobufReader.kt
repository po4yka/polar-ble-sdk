package com.polar.shared.sdk

private const val PROTOBUF_WIRE_VARINT = 0
private const val PROTOBUF_WIRE_FIXED64 = 1
private const val PROTOBUF_WIRE_LENGTH_DELIMITED = 2
private const val PROTOBUF_WIRE_FIXED32 = 5

internal class PolarTrainingProtobufReader(private val bytes: ByteArray) {
    private var offset = 0

    fun forEachField(block: (fieldNumber: Int, wireType: Int, reader: PolarTrainingProtobufReader) -> Unit) {
        while (offset < bytes.size) {
            val tag = readRawVarint()
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            block(fieldNumber, wireType, this)
        }
    }

    fun readVarintField(wireType: Int): Long {
        require(wireType == PROTOBUF_WIRE_VARINT) { "Expected protobuf varint wire type but got $wireType" }
        return readRawVarint()
    }

    fun readStringField(wireType: Int): String {
        return readLengthDelimitedBytes(wireType).decodeToString()
    }

    fun readFloatField(wireType: Int): Float {
        require(wireType == PROTOBUF_WIRE_FIXED32) { "Expected protobuf fixed32 wire type but got $wireType" }
        val value = readLittleEndian32()
        return Float.fromBits(value)
    }

    fun readDoubleField(wireType: Int): Double {
        require(wireType == PROTOBUF_WIRE_FIXED64) { "Expected protobuf fixed64 wire type but got $wireType" }
        val value = readLittleEndian64()
        return Double.fromBits(value)
    }

    fun readLengthDelimitedField(wireType: Int, block: (PolarTrainingProtobufReader) -> Unit) {
        block(PolarTrainingProtobufReader(readLengthDelimitedBytes(wireType)))
    }

    fun readUInt32RepeatedField(wireType: Int): List<Int> {
        return when (wireType) {
            PROTOBUF_WIRE_VARINT -> listOf(readRawVarint().toInt())
            PROTOBUF_WIRE_LENGTH_DELIMITED -> {
                val packed = PolarTrainingProtobufReader(readLengthDelimitedBytes(wireType))
                buildList {
                    while (packed.hasRemaining()) add(packed.readRawVarint().toInt())
                }
            }
            else -> error("Expected protobuf uint32 repeated wire type but got $wireType")
        }
    }

    fun readSInt32RepeatedField(wireType: Int): List<Int> {
        return readUInt32RepeatedField(wireType).map(::decodeZigZag32)
    }

    fun readDoubleRepeatedField(wireType: Int): List<Double> {
        return when (wireType) {
            PROTOBUF_WIRE_FIXED64 -> listOf(Double.fromBits(readLittleEndian64()))
            PROTOBUF_WIRE_LENGTH_DELIMITED -> {
                val packed = PolarTrainingProtobufReader(readLengthDelimitedBytes(wireType))
                buildList {
                    while (packed.hasRemaining()) add(Double.fromBits(packed.readLittleEndian64()))
                }
            }
            else -> error("Expected protobuf double repeated wire type but got $wireType")
        }
    }

    fun readSInt64RepeatedField(wireType: Int): List<Long> {
        return when (wireType) {
            PROTOBUF_WIRE_VARINT -> listOf(decodeZigZag64(readRawVarint()))
            PROTOBUF_WIRE_LENGTH_DELIMITED -> {
                val packed = PolarTrainingProtobufReader(readLengthDelimitedBytes(wireType))
                buildList {
                    while (packed.hasRemaining()) add(decodeZigZag64(packed.readRawVarint()))
                }
            }
            else -> error("Expected protobuf sint64 repeated wire type but got $wireType")
        }
    }

    fun skip(wireType: Int) {
        when (wireType) {
            PROTOBUF_WIRE_VARINT -> readRawVarint()
            PROTOBUF_WIRE_FIXED64 -> offset += 8
            PROTOBUF_WIRE_LENGTH_DELIMITED -> {
                val length = readRawVarint().toInt()
                require(length >= 0 && offset + length <= bytes.size) { "Invalid protobuf length $length at offset $offset" }
                offset += length
            }
            PROTOBUF_WIRE_FIXED32 -> offset += 4
            else -> error("Unsupported protobuf wire type $wireType")
        }
        require(offset <= bytes.size) { "Protobuf field extends past payload end" }
    }

    private fun hasRemaining(): Boolean {
        return offset < bytes.size
    }

    private fun readLengthDelimitedBytes(wireType: Int): ByteArray {
        require(wireType == PROTOBUF_WIRE_LENGTH_DELIMITED) { "Expected protobuf length-delimited wire type but got $wireType" }
        val length = readRawVarint().toInt()
        require(length >= 0 && offset + length <= bytes.size) { "Invalid protobuf length $length at offset $offset" }
        val result = bytes.copyOfRange(offset, offset + length)
        offset += length
        return result
    }

    private fun readLittleEndian32(): Int {
        require(offset + 4 <= bytes.size) { "Truncated protobuf fixed32" }
        val value = (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
        offset += 4
        return value
    }

    private fun readLittleEndian64(): Long {
        require(offset + 8 <= bytes.size) { "Truncated protobuf fixed64" }
        var value = 0L
        for (index in 0 until 8) {
            value = value or ((bytes[offset + index].toLong() and 0xffL) shl (8 * index))
        }
        offset += 8
        return value
    }

    fun readRawVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            require(offset < bytes.size) { "Truncated protobuf varint" }
            val value = bytes[offset++].toInt() and 0xff
            result = result or ((value and 0x7f).toLong() shl shift)
            if ((value and 0x80) == 0) {
                return result
            }
            shift += 7
        }
        error("Malformed protobuf varint")
    }

    private fun decodeZigZag32(value: Int): Int {
        return (value ushr 1) xor -(value and 1)
    }

    private fun decodeZigZag64(value: Long): Long {
        return (value ushr 1) xor -(value and 1L)
    }
}
