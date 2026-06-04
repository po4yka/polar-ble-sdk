package com.polar.shared.ios

import com.polar.shared.device.PolarDeviceId
import com.polar.shared.time.PolarDurationFields
import com.polar.shared.time.PolarTimeFields
import com.polar.shared.time.PolarTimeUtils

object PolarIosSharedBridge {
    fun isValidDeviceId(deviceId: String): Boolean {
        return PolarDeviceId.isValid(deviceId)
    }

    fun assembleFullDeviceId(deviceId: String): String {
        return PolarDeviceId.assembleFull(deviceId)
    }

    fun uuidFromDeviceId(deviceId: String): String {
        return PolarDeviceId.uuidFromDeviceId(deviceId)
    }

    fun millisToNanos(milliseconds: Int): Int {
        return PolarTimeUtils.millisToNanos(milliseconds)
    }

    fun nanosToMillis(nanoseconds: Int): Int {
        return PolarTimeUtils.nanosToMillis(nanoseconds)
    }

    fun secondsToMinutes(seconds: Int): Int {
        return PolarTimeUtils.secondsToMinutes(seconds)
    }

    fun minutesToSeconds(minutes: Int): Int {
        return PolarTimeUtils.minutesToSeconds(minutes)
    }

    fun durationToMillis(hours: Int, minutes: Int, seconds: Int, millis: Int): Int {
        return PolarTimeUtils.durationToMillis(
            PolarDurationFields(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                millis = millis
            )
        )
    }

    fun timeString(hour: Int, minute: Int, second: Int, millis: Int): String {
        return PolarTimeUtils.timeString(
            PolarTimeFields(
                hour = hour,
                minute = minute,
                second = second,
                millis = millis
            )
        )
    }

    fun isValidPlainDate(value: String): Boolean {
        return PolarTimeUtils.isValidPlainDate(value)
    }
}
