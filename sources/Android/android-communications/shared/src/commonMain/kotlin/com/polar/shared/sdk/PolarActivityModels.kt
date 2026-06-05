package com.polar.shared.sdk

enum class PolarAutomaticHrTriggerName(val value: Int) {
    TRIGGER_TYPE_HIGH_ACTIVITY(1),
    TRIGGER_TYPE_LOW_ACTIVITY(2),
    TRIGGER_TYPE_TIMED(3),
    TRIGGER_TYPE_MANUAL(4);

    companion object {
        fun fromValue(value: Int): PolarAutomaticHrTriggerName? {
            return entries.firstOrNull { trigger -> trigger.value == value }
        }
    }
}

enum class PolarPpiSampleTriggerName(val value: Int) {
    TRIGGER_TYPE_UNDEFINED(0),
    TRIGGER_TYPE_AUTOMATIC(1),
    TRIGGER_TYPE_MANUAL(2);

    companion object {
        fun fromValue(value: Int): PolarPpiSampleTriggerName? {
            return entries.firstOrNull { trigger -> trigger.value == value }
        }
    }
}

enum class PolarPpiSkinContactName(val value: Int) {
    NO_SKIN_CONTACT(0),
    SKIN_CONTACT_DETECTED(1);

    companion object {
        fun fromValue(value: Int): PolarPpiSkinContactName? {
            return entries.firstOrNull { status -> status.value == value }
        }
    }
}

enum class PolarPpiMovementName(val value: Int) {
    NO_MOVING_DETECTED(0),
    MOVING_DETECTED(1);

    companion object {
        fun fromValue(value: Int): PolarPpiMovementName? {
            return entries.firstOrNull { status -> status.value == value }
        }
    }
}

enum class PolarPpiIntervalStatusName(val value: Int) {
    INTERVAL_IS_ONLINE(0),
    INTERVAL_DENOTES_OFFLINE_PERIOD(1);

    companion object {
        fun fromValue(value: Int): PolarPpiIntervalStatusName? {
            return entries.firstOrNull { status -> status.value == value }
        }
    }
}

data class PolarPpiStatusNames(
    val skinContact: String,
    val movement: String,
    val intervalStatus: String
) {
    companion object {
        fun fromStatusByte(value: Int): PolarPpiStatusNames? {
            val skinContact = PolarPpiSkinContactName.fromValue(value and 0x01) ?: return null
            val movement = PolarPpiMovementName.fromValue((value shr 1) and 0x01) ?: return null
            val intervalStatus = PolarPpiIntervalStatusName.fromValue((value shr 2) and 0x01) ?: return null
            return PolarPpiStatusNames(
                skinContact = skinContact.name,
                movement = movement.name,
                intervalStatus = intervalStatus.name
            )
        }
    }
}
