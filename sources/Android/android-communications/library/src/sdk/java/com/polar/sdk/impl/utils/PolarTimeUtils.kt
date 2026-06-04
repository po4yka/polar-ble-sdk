package com.polar.sdk.impl.utils

import com.polar.shared.time.PolarDateFields
import com.polar.shared.time.PolarDateTimeFields
import com.polar.shared.time.PolarDurationFields
import com.polar.shared.time.PolarTimeFields
import com.polar.shared.time.PolarTimeUtils as SharedPolarTimeUtils
import com.polar.services.datamodels.protobuf.Types.PbDateProto3
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import java.time.LocalDate
import java.time.LocalDateTime
import protocol.PftpRequest
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal object PolarTimeUtils {
    fun javaCalendarToPbPftpSetLocalTime(calendar: Calendar): PftpRequest.PbPFtpSetLocalTimeParams {
        return toPbPftpSetLocalTimeParams(calendar.localDateTimeFields())
    }

    fun javaLocalDateTimeToPbPftpSetLocalTime(localDateTime: LocalDateTime): PftpRequest.PbPFtpSetLocalTimeParams {
        return toPbPftpSetLocalTimeParams(
            PolarDateTimeFields(
                date = PolarDateFields(localDateTime.year, localDateTime.monthValue, localDateTime.dayOfMonth),
                time = PolarTimeFields(localDateTime.hour, localDateTime.minute, localDateTime.second, (localDateTime.nano / 1_000_000) % 1000),
                timeZoneOffsetMinutes = SharedPolarTimeUtils.secondsToMinutes(localDateTime.atZone(ZoneId.systemDefault()).offset.totalSeconds)
            )
        )
    }

    fun javaCalendarToPbPftpSetSystemTime(calendar: Calendar): PftpRequest.PbPFtpSetSystemTimeParams {
        val utcTime = calendar.toInstant().atZone(ZoneId.of("UTC"))
        return toPbPftpSetSystemTimeParams(utcTime.systemDateTimeFields())
    }

    fun javaInstantToPbPftpSetSystemTime(instant: Instant): PbSystemDateTime {
        val utcTime = instant.atZone(ZoneId.of("UTC"))
        return toPbSystemDateTime(utcTime.systemDateTimeFields())
    }

    fun pbLocalTimeToJavaCalendar(pbLocalTime: PftpRequest.PbPFtpSetLocalTimeParams): Calendar {
        val offsetInMillis =
            TimeUnit.MILLISECONDS.convert(pbLocalTime.tzOffset.toLong(), TimeUnit.MINUTES)
        val zoneOffset = ZoneOffset.ofTotalSeconds((offsetInMillis / 1000).toInt())
        val zdt = ZonedDateTime.of(
            pbLocalTime.date.year,
            pbLocalTime.date.month,
            pbLocalTime.date.day,
            pbLocalTime.time.hour,
            pbLocalTime.time.minute,
            pbLocalTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbLocalTime.time.millis),
            zoneOffset
        )
        return Calendar.getInstance(TimeZone.getTimeZone(zoneOffset)).apply {
            timeInMillis = zdt.toInstant().toEpochMilli()
        }
    }

    fun pbLocalTimeToJavaLocalDateTime(pbLocalTime: PftpRequest.PbPFtpSetLocalTimeParams): LocalDateTime {
        val zoneId = ZoneOffset.ofTotalSeconds(pbLocalTime.tzOffset * 60)
        return ZonedDateTime.of(
            pbLocalTime.date.year,
            pbLocalTime.date.month,
            pbLocalTime.date.day,
            pbLocalTime.time.hour,
            pbLocalTime.time.minute,
            pbLocalTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbLocalTime.time.millis),
            zoneId
        ).toLocalDateTime()
    }

    fun pbLocalTimeToZonedDateTime(pbLocalTime: PftpRequest.PbPFtpSetLocalTimeParams): ZonedDateTime {
        val zoneOffset = ZoneOffset.ofTotalSeconds(pbLocalTime.tzOffset * 60)
        return ZonedDateTime.of(
            pbLocalTime.date.year,
            pbLocalTime.date.month,
            pbLocalTime.date.day,
            pbLocalTime.time.hour,
            pbLocalTime.time.minute,
            pbLocalTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbLocalTime.time.millis),
            zoneOffset
        )
    }

    fun pbLocalDateTimeToZonedDateTime(pbDateTime: PbLocalDateTime): ZonedDateTime {
        val zoneId = ZoneOffset.ofTotalSeconds(pbDateTime.timeZoneOffset * 60)
        return ZonedDateTime.of(
            pbDateTime.date.year,
            pbDateTime.date.month,
            pbDateTime.date.day,
            pbDateTime.time.hour,
            pbDateTime.time.minute,
            pbDateTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbDateTime.time.millis),
            zoneId
        )
    }

    /**
     * Converts a [PbLocalDateTime] to a [LocalDateTime], using the embedded timezone offset to
     * normalize to local wall-clock time. Falls back to UTC if no timezone offset is present.
     */
    fun pbLocalDateTimeToLocalDateTimeWithOptionalTz(pbDateTime: PbLocalDateTime): LocalDateTime {
        val tz = if (pbDateTime.hasTimeZoneOffset()) {
            ZoneOffset.ofTotalSeconds(pbDateTime.timeZoneOffset * 60)
        } else {
            ZoneOffset.UTC
        }
        return LocalDateTime.of(
            pbDateTime.date.year,
            pbDateTime.date.month,
            pbDateTime.date.day,
            pbDateTime.time.hour,
            pbDateTime.time.minute,
            pbDateTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbDateTime.time.millis)
        ).atZone(tz).toLocalDateTime()
    }

    fun pbLocalDateTimeToLocalDateTime(pbDateTime: PbLocalDateTime): LocalDateTime {
        return LocalDateTime.of(
            pbDateTime.date.year,
            pbDateTime.date.month,
            pbDateTime.date.day,
            pbDateTime.time.hour,
            pbDateTime.time.minute,
            pbDateTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbDateTime.time.millis)
        )
    }

    fun pbSystemDateTimeToLocalDateTime(pbSystemDateTime: PbSystemDateTime): LocalDateTime {
        return LocalDateTime.of(
            pbSystemDateTime.date.year,
            pbSystemDateTime.date.month,
            pbSystemDateTime.date.day,
            pbSystemDateTime.time.hour,
            pbSystemDateTime.time.minute,
            pbSystemDateTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbSystemDateTime.time.millis)
        )
    }

    fun pbSystemDateTimeToZonedDateTime(pbSystemDateTime: PbSystemDateTime): ZonedDateTime {
        return ZonedDateTime.of(
            pbSystemDateTime.date.year,
            pbSystemDateTime.date.month,
            pbSystemDateTime.date.day,
            pbSystemDateTime.time.hour,
            pbSystemDateTime.time.minute,
            pbSystemDateTime.time.seconds,
            SharedPolarTimeUtils.millisToNanos(pbSystemDateTime.time.millis),
            ZoneOffset.UTC
        )
    }

    fun pbDateToLocalDate(pbDate: PbDate): LocalDate {
        return LocalDate.of(pbDate.year, pbDate.month, pbDate.day)
    }

    fun pbTimeToLocalTime(pbTime: PbTime): LocalTime {
        return LocalTime.of(
            pbTime.hour,
            pbTime.minute,
            pbTime.seconds,
            SharedPolarTimeUtils.millisToNanos(pbTime.millis)
        )
    }

    // Returns duration in milliseconds
    fun pbDurationToInt(pbDuration: PbDuration): Int {
        return SharedPolarTimeUtils.durationToMillis(
            PolarDurationFields(pbDuration.hours, pbDuration.minutes, pbDuration.seconds, pbDuration.millis)
        )
    }

    fun pbDateToLocalDate(pbDate: PbDateProto3): LocalDate {
        return LocalDate.of(
            pbDate.year,
            pbDate.month,
            pbDate.day,
        )
    }

    private fun Calendar.localDateTimeFields(): PolarDateTimeFields {
        return PolarDateTimeFields(
            date = PolarDateFields(
                year = this[Calendar.YEAR],
                month = this[Calendar.MONTH] + 1,
                day = this[Calendar.DAY_OF_MONTH]
            ),
            time = PolarTimeFields(
                hour = this[Calendar.HOUR_OF_DAY],
                minute = this[Calendar.MINUTE],
                second = this[Calendar.SECOND],
                millis = this[Calendar.MILLISECOND]
            ),
            timeZoneOffsetMinutes = TimeUnit.MINUTES.convert(
                this[Calendar.ZONE_OFFSET].toLong() + this[Calendar.DST_OFFSET].toLong(),
                TimeUnit.MILLISECONDS
            ).toInt()
        )
    }

    private fun ZonedDateTime.systemDateTimeFields(): PolarDateTimeFields {
        return PolarDateTimeFields(
            date = PolarDateFields(year, monthValue, dayOfMonth),
            time = PolarTimeFields(hour, minute, second, nano / 1_000_000),
            trusted = true
        )
    }

    private fun toPbPftpSetLocalTimeParams(fields: PolarDateTimeFields): PftpRequest.PbPFtpSetLocalTimeParams {
        return PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
            .setDate(fields.date.toPbDate())
            .setTime(fields.time.toPbTime())
            .setTzOffset(fields.timeZoneOffsetMinutes ?: 0)
            .build()
    }

    private fun toPbPftpSetSystemTimeParams(fields: PolarDateTimeFields): PftpRequest.PbPFtpSetSystemTimeParams {
        return PftpRequest.PbPFtpSetSystemTimeParams.newBuilder()
            .setDate(fields.date.toPbDate())
            .setTime(fields.time.toPbTime())
            .setTrusted(fields.trusted)
            .build()
    }

    private fun toPbSystemDateTime(fields: PolarDateTimeFields): PbSystemDateTime {
        return PbSystemDateTime.newBuilder()
            .setDate(fields.date.toPbDate())
            .setTime(fields.time.toPbTime())
            .setTrusted(fields.trusted)
            .build()
    }

    private fun PolarDateFields.toPbDate(): PbDate.Builder {
        return PbDate.newBuilder()
            .setYear(year)
            .setMonth(month)
            .setDay(day)
    }

    private fun PolarTimeFields.toPbTime(): PbTime.Builder {
        return PbTime.newBuilder()
            .setHour(hour)
            .setMinute(minute)
            .setSeconds(second)
            .setMillis(millis)
    }
}
