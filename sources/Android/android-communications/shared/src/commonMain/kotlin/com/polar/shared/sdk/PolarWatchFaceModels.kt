package com.polar.shared.sdk

data class PolarWatchFaceFields(
    val timeStyleId: Int = 0,
    val complicationLayoutId: Int = 0,
    val backgroundStyleId: Int = 0,
    val accentColor: Long = 0L,
    val complicationIds: List<Int> = emptyList(),
    val fontfaceId: Int = 0
) {
    companion object {
        fun fromNullableFields(
            timeStyleId: Int?,
            complicationLayoutId: Int?,
            backgroundStyleId: Int?,
            accentColor: Long?,
            complicationIds: List<Int>?,
            fontfaceId: Int?
        ): PolarWatchFaceFields {
            return PolarWatchFaceFields(
                timeStyleId = timeStyleId ?: 0,
                complicationLayoutId = complicationLayoutId ?: 0,
                backgroundStyleId = backgroundStyleId ?: 0,
                accentColor = accentColor ?: 0L,
                complicationIds = complicationIds ?: emptyList(),
                fontfaceId = fontfaceId ?: 0
            )
        }
    }
}

object PolarWatchFaceConfigFlatBuffer {
    fun build(fields: PolarWatchFaceFields): ByteArray {
        val builder = FlatBufferBuilder(initialSize = 256)
        builder.startVector(elementSize = 4, count = fields.complicationIds.size, alignment = 4)
        for (index in fields.complicationIds.indices.reversed()) {
            builder.addRawInt32(fields.complicationIds[index])
        }
        val vectorOffset = builder.endVector(count = fields.complicationIds.size)

        builder.startTable(fieldCount = 6)
        builder.addInt16(field = 0, value = fields.timeStyleId, defaultValue = 0)
        builder.addInt16(field = 1, value = fields.complicationLayoutId, defaultValue = 0)
        builder.addInt16(field = 2, value = fields.backgroundStyleId, defaultValue = 0)
        builder.addInt32(field = 3, value = fields.accentColor.toInt(), defaultValue = 0)
        builder.addOffset(field = 4, value = vectorOffset, defaultValue = 0)
        builder.addByte(field = 5, value = fields.fontfaceId, defaultValue = 0)
        val tableOffset = builder.endTable()
        builder.finish(rootTable = tableOffset)
        return builder.sizedByteArray()
    }

    fun parse(raw: ByteArray): PolarWatchFaceFields {
        val empty = PolarWatchFaceFields()
        if (raw.size < 4) return empty

        val rootOffset = raw.readLeU32(0).toInt()
        if (rootOffset < 0 || rootOffset + 4 > raw.size) return empty

        val vtableOffsetFromTable = raw.readLeI32(rootOffset)
        val vtablePos = rootOffset - vtableOffsetFromTable
        if (vtablePos < 0 || vtablePos + 4 > raw.size) return empty

        val vtableSize = raw.readLeU16(vtablePos)
        val fieldCount = (vtableSize - 4) / 2
        fun fieldOffset(fieldIndex: Int): Int {
            if (fieldIndex >= fieldCount) return 0
            val offsetPosition = vtablePos + 4 + fieldIndex * 2
            return if (offsetPosition + 2 <= raw.size) raw.readLeU16(offsetPosition) else 0
        }

        fun readU16Field(fieldIndex: Int): Int {
            val offset = fieldOffset(fieldIndex)
            return if (offset == 0) 0 else raw.readLeU16OrZero(rootOffset + offset)
        }

        fun readU32Field(fieldIndex: Int): Long {
            val offset = fieldOffset(fieldIndex)
            return if (offset == 0) 0L else raw.readLeU32OrZero(rootOffset + offset)
        }

        val complicationIds = run {
            val offset = fieldOffset(4)
            if (offset == 0) return@run emptyList()
            val vectorRefPos = rootOffset + offset
            if (vectorRefPos + 4 > raw.size) return@run emptyList()
            val vectorPos = vectorRefPos + raw.readLeI32(vectorRefPos)
            if (vectorPos + 4 > raw.size) return@run emptyList()
            val vectorLength = raw.readLeI32(vectorPos)
            if (vectorLength !in 0..1000) return@run emptyList()
            val dataStart = vectorPos + 4
            if (dataStart + vectorLength * 4 > raw.size) return@run emptyList()
            List(vectorLength) { index -> raw.readLeI32(dataStart + index * 4) }
        }

        val fontfaceOffset = fieldOffset(5)
        return PolarWatchFaceFields.fromNullableFields(
            timeStyleId = readU16Field(0),
            complicationLayoutId = readU16Field(1),
            backgroundStyleId = readU16Field(2),
            accentColor = readU32Field(3),
            complicationIds = complicationIds,
            fontfaceId = if (fontfaceOffset == 0 || rootOffset + fontfaceOffset >= raw.size) 0 else raw[rootOffset + fontfaceOffset].toInt() and 0xFF
        )
    }

    private fun ByteArray.readLeU16(position: Int): Int {
        return (this[position].toInt() and 0xFF) or ((this[position + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.readLeU16OrZero(position: Int): Int {
        return if (position + 2 <= size) readLeU16(position) else 0
    }

    private fun ByteArray.readLeU32(position: Int): Long {
        return (this[position].toLong() and 0xFFL) or
            ((this[position + 1].toLong() and 0xFFL) shl 8) or
            ((this[position + 2].toLong() and 0xFFL) shl 16) or
            ((this[position + 3].toLong() and 0xFFL) shl 24)
    }

    private fun ByteArray.readLeU32OrZero(position: Int): Long {
        return if (position + 4 <= size) readLeU32(position) else 0L
    }

    private fun ByteArray.readLeI32(position: Int): Int {
        return readLeU32(position).toInt()
    }

    private class FlatBufferBuilder(initialSize: Int) {
        private var buffer = ByteArray(initialSize)
        private var space = initialSize
        private var minAlignment = 1
        private var tableVtable: IntArray = IntArray(0)
        private var tableObjectStart = 0

        private val offset: Int get() = buffer.size - space

        fun addRawInt32(value: Int) {
            prep(size = 4, additional = 0)
            putInt32(value)
        }

        fun startVector(elementSize: Int, count: Int, alignment: Int) {
            prep(size = 4, additional = elementSize * count)
            prep(size = alignment, additional = elementSize * count)
        }

        fun endVector(count: Int): Int {
            putInt32(count)
            return offset
        }

        fun startTable(fieldCount: Int) {
            tableVtable = IntArray(fieldCount)
            tableObjectStart = offset
        }

        fun addByte(field: Int, value: Int, defaultValue: Int) {
            if (value != defaultValue) {
                prep(size = 1, additional = 0)
                putByte(value)
                slot(field)
            }
        }

        fun addInt16(field: Int, value: Int, defaultValue: Int) {
            if (value != defaultValue) {
                prep(size = 2, additional = 0)
                putInt16(value)
                slot(field)
            }
        }

        fun addInt32(field: Int, value: Int, defaultValue: Int) {
            if (value != defaultValue) {
                addRawInt32(value)
                slot(field)
            }
        }

        fun addOffset(field: Int, value: Int, defaultValue: Int) {
            if (value != defaultValue) {
                prep(size = 4, additional = 0)
                val storedOffset = offset - value + 4
                putInt32(storedOffset)
                slot(field)
            }
        }

        fun endTable(): Int {
            prep(size = 4, additional = 0)
            putInt32(0)
            val objectOffset = offset
            var usedFieldCount = tableVtable.size
            while (usedFieldCount > 0 && tableVtable[usedFieldCount - 1] == 0) {
                usedFieldCount -= 1
            }
            for (index in usedFieldCount - 1 downTo 0) {
                val fieldOffset = if (tableVtable[index] != 0) objectOffset - tableVtable[index] else 0
                prep(size = 2, additional = 0)
                putInt16(fieldOffset)
            }
            prep(size = 2, additional = 0)
            putInt16(objectOffset - tableObjectStart)
            val vtableByteSize = usedFieldCount * 2 + 4
            prep(size = 2, additional = 0)
            putInt16(vtableByteSize)
            val vtableOffset = offset
            writeLeI32AtOffset(objectOffset, vtableOffset - objectOffset)
            return objectOffset
        }

        fun finish(rootTable: Int) {
            prep(size = minAlignment, additional = 4)
            addOffsetValue(rootTable)
        }

        fun sizedByteArray(): ByteArray {
            return buffer.copyOfRange(space, buffer.size)
        }

        private fun addOffsetValue(value: Int) {
            prep(size = 4, additional = 0)
            val storedOffset = offset - value + 4
            putInt32(storedOffset)
        }

        private fun slot(field: Int) {
            tableVtable[field] = offset
        }

        private fun prep(size: Int, additional: Int) {
            if (size > minAlignment) minAlignment = size
            val currentOffset = offset + additional
            val alignmentSize = (-currentOffset) and (size - 1)
            growIfNeeded(alignmentSize + size + additional)
            space -= alignmentSize
        }

        private fun growIfNeeded(needed: Int) {
            if (space >= needed) return
            val used = buffer.size - space
            var newSize = buffer.size
            while (newSize - used < needed) {
                newSize = maxOf(newSize * 2, needed + used)
            }
            val newBuffer = ByteArray(newSize)
            val newSpace = newSize - used
            buffer.copyInto(newBuffer, destinationOffset = newSpace, startIndex = space, endIndex = buffer.size)
            buffer = newBuffer
            space = newSpace
        }

        private fun putByte(value: Int) {
            space -= 1
            buffer[space] = value.toByte()
        }

        private fun putInt16(value: Int) {
            space -= 2
            buffer[space] = value.toByte()
            buffer[space + 1] = (value shr 8).toByte()
        }

        private fun putInt32(value: Int) {
            space -= 4
            buffer[space] = value.toByte()
            buffer[space + 1] = (value shr 8).toByte()
            buffer[space + 2] = (value shr 16).toByte()
            buffer[space + 3] = (value shr 24).toByte()
        }

        private fun writeLeI32AtOffset(offset: Int, value: Int) {
            val position = buffer.size - offset
            buffer[position] = value.toByte()
            buffer[position + 1] = (value shr 8).toByte()
            buffer[position + 2] = (value shr 16).toByte()
            buffer[position + 3] = (value shr 24).toByte()
        }
    }
}

enum class PolarWatchFaceComplicationName(val complicationId: String) {
    ALARM("alarm-complication"),
    ALTITUDE("altitude-complication"),
    ACTIVITY("activity-percentage-complication"),
    BATTERY("battery-complication"),
    BREATHING_EXERCISE("serene-complication"),
    CALORIES("calories-complication"),
    COMPASS("compass-complication"),
    COUNTDOWN_TIMER("countdownTimer-complication"),
    DATE("date-complication"),
    DAYLIGHT("daylight-complication"),
    ECG("ecg-complication"),
    EMPTY(""),
    FLASHLIGHT("flashlight-complication"),
    HEART_RATE("heart-rate-complication"),
    JUMP_TEST("jump-test-complication"),
    LATEST_TRAINING("latest-training-complication"),
    NAVIGATION("navigation-complication"),
    NIGHTLY_RECHARGE("nightly-recharge-complication"),
    POLAR_LOGO("polar-logo-complication"),
    SECONDS_ANALOG("analog-seconds-complication"),
    SECONDS_DIGITAL("digital-seconds-complication"),
    SPO2("spo2-complication"),
    TIMER("timer-complication"),
    USER_NAME("user-name-complication"),
    WEATHER("weather-complication"),
    WEEKLY_SUMMARY("weeklysummary-complication");

    val id: Int get() = complicationId.javaStringHashCode()

    companion object {
        fun fromId(id: Int): PolarWatchFaceComplicationName? {
            return entries.firstOrNull { complication -> complication.id == id }
        }

        fun idFor(complicationId: String): Int {
            return complicationId.javaStringHashCode()
        }
    }
}

private fun String.javaStringHashCode(): Int {
    var hash = 0
    for (char in this) {
        hash = hash * 31 + char.code
    }
    return hash
}
