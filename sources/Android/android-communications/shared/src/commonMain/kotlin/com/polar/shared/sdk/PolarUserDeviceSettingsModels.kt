package com.polar.shared.sdk

object PolarUserDeviceSettingsModels {
    fun deviceLocationName(value: Int): String? {
        return when (value) {
            0 -> "UNDEFINED"
            1 -> "OTHER"
            2 -> "WRIST_LEFT"
            3 -> "WRIST_RIGHT"
            4 -> "NECKLACE"
            5 -> "CHEST"
            6 -> "UPPER_BACK"
            7 -> "FOOT_LEFT"
            8 -> "FOOT_RIGHT"
            9 -> "LOWER_ARM_LEFT"
            10 -> "LOWER_ARM_RIGHT"
            11 -> "UPPER_ARM_LEFT"
            12 -> "UPPER_ARM_RIGHT"
            13 -> "BIKE_MOUNT"
            else -> null
        }
    }

    fun deviceLocationValue(name: String): Int? {
        return when (name) {
            "UNDEFINED" -> 0
            "OTHER" -> 1
            "WRIST_LEFT" -> 2
            "WRIST_RIGHT" -> 3
            "NECKLACE" -> 4
            "CHEST" -> 5
            "UPPER_BACK" -> 6
            "FOOT_LEFT" -> 7
            "FOOT_RIGHT" -> 8
            "LOWER_ARM_LEFT" -> 9
            "LOWER_ARM_RIGHT" -> 10
            "UPPER_ARM_LEFT" -> 11
            "UPPER_ARM_RIGHT" -> 12
            "BIKE_MOUNT" -> 13
            else -> null
        }
    }

    fun usbConnectionModeName(value: Int): String? {
        return when (value) {
            1 -> "OFF"
            2 -> "ON"
            else -> null
        }
    }

    fun automaticTrainingDetectionModeName(value: Int): String? {
        return when (value) {
            0 -> "OFF"
            1 -> "ON"
            else -> null
        }
    }
}
