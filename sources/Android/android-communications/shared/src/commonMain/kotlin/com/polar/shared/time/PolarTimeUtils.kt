package com.polar.shared.time

import kotlin.math.roundToInt

data class PolarDateFields(
    val year: Int,
    val month: Int,
    val day: Int
)

data class PolarTimeFields(
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millis: Int
)

data class PolarDateTimeFields(
    val date: PolarDateFields,
    val time: PolarTimeFields,
    val timeZoneOffsetMinutes: Int? = null,
    val trusted: Boolean = false
)

data class PolarDurationFields(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val millis: Int
)

object PolarTimeUtils {
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val SECONDS_PER_MINUTE = 60
    private const val MILLIS_PER_SECOND = 1_000
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE

    fun millisToNanos(milliseconds: Int): Long {
        return milliseconds.toLong() * NANOS_PER_MILLI
    }

    fun nanosToMillis(nanoseconds: Int): Int {
        return (nanoseconds.toDouble() / NANOS_PER_MILLI.toDouble()).roundToInt()
    }

    fun secondsToMinutes(seconds: Int): Int {
        return (seconds.toDouble() / SECONDS_PER_MINUTE.toDouble()).roundToInt()
    }

    fun minutesToSeconds(minutes: Int): Int {
        return minutes * SECONDS_PER_MINUTE
    }

    fun durationToMillis(duration: PolarDurationFields): Int {
        return duration.hours * MILLIS_PER_HOUR +
            duration.minutes * MILLIS_PER_MINUTE +
            duration.seconds * MILLIS_PER_SECOND +
            duration.millis
    }

    fun timeString(time: PolarTimeFields): String {
        return "${time.hour.twoDigits()}:${time.minute.twoDigits()}:${time.second.twoDigits()}.${time.millis.twoDigits()}"
    }

    fun parsePlainDate(value: String): PolarDateFields? {
        if (value.length != 10 || value[4] != '-' || value[7] != '-') return null
        val year = value.substring(0, 4).toIntOrNull() ?: return null
        val month = value.substring(5, 7).toIntOrNull() ?: return null
        val day = value.substring(8, 10).toIntOrNull() ?: return null
        val date = PolarDateFields(year, month, day)
        return if (date.isValid()) date else null
    }

    fun isValidPlainDate(value: String): Boolean {
        return parsePlainDate(value) != null
    }

    fun formatPlainDate(date: PolarDateFields): String {
        require(date.isValid()) { "Invalid plain date fields: $date" }
        return "${date.year.fourDigits()}-${date.month.twoDigits()}-${date.day.twoDigits()}"
    }

    fun basicDateRange(startInclusive: String, endInclusive: String): List<String> {
        var current = parseBasicDate(startInclusive) ?: return emptyList()
        val end = parseBasicDate(endInclusive) ?: return emptyList()
        val dates = mutableListOf<String>()
        while (!current.isAfter(end)) {
            dates += current.basicString()
            current = current.nextDay()
        }
        return dates
    }

    private fun parseBasicDate(value: String): PolarDateFields? {
        if (value.length != 8) return null
        val year = value.substring(0, 4).toIntOrNull() ?: return null
        val month = value.substring(4, 6).toIntOrNull() ?: return null
        val day = value.substring(6, 8).toIntOrNull() ?: return null
        val date = PolarDateFields(year, month, day)
        return if (date.isValid()) date else null
    }

    private fun PolarDateFields.isAfter(other: PolarDateFields): Boolean {
        return year > other.year ||
            (year == other.year && month > other.month) ||
            (year == other.year && month == other.month && day > other.day)
    }

    private fun PolarDateFields.nextDay(): PolarDateFields {
        val monthDays = daysInMonth(year, month)
        if (day < monthDays) return copy(day = day + 1)
        if (month < 12) return copy(month = month + 1, day = 1)
        return PolarDateFields(year + 1, 1, 1)
    }

    private fun PolarDateFields.basicString(): String {
        return "${year.fourDigits()}${month.twoDigits()}${day.twoDigits()}"
    }

    private fun PolarDateFields.isValid(): Boolean {
        if (month !in 1..12) return false
        return day in 1..daysInMonth(year, month)
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }
    }

    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }

    private fun Int.twoDigits(): String {
        return if (this in 0..9) "0$this" else toString()
    }

    private fun Int.fourDigits(): String {
        return toString().padStart(4, '0')
    }
}
