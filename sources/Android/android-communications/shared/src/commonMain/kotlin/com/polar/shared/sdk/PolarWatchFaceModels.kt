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
