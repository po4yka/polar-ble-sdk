package com.polar.shared.sdk

enum class PolarActivityClassName(val value: Int) {
    SLEEP(1),
    SEDENTARY(2),
    LIGHT(3),
    CONTINUOUS_MODERATE(4),
    INTERMITTENT_MODERATE(5),
    CONTINUOUS_VIGOROUS(6),
    INTERMITTENT_VIGOROUS(7),
    NON_WEAR(8);

    companion object {
        fun fromValue(value: Int): PolarActivityClassName? {
            return entries.firstOrNull { activityClass -> activityClass.value == value }
        }
    }
}

object PolarActivityModels {
    fun activityDirectoryPath(day: String): String {
        return "/U/0/$day/ACT/"
    }

    fun dailySummaryPath(day: String): String {
        return "/U/0/$day/DSUM/DSUM.BPB"
    }

    fun automaticSamplesDirectoryPath(): String {
        return "/U/0/AUTOS/"
    }

    fun automaticSamplesFilePath(fileName: String): String {
        return "${automaticSamplesDirectoryPath()}$fileName"
    }
}

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

enum class PolarDailyBalanceFeedbackName(val value: Int) {
    NOT_CALCULATED(-1),
    SICK(0),
    FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD_INJURED(1),
    FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD(2),
    LIMITED_TRAINING_RESPONSE_OTHER_INJURED(3),
    LIMITED_TRAINING_RESPONSE_OTHER(4),
    RESPONDING_WELL_CAN_CONTINUE_IF_INJURY_ALLOWS(5),
    RESPONDING_WELL_CAN_CONTINUE(6),
    YOU_COULD_DO_MORE_TRAINING_IF_INJURY_ALLOWS(7),
    YOU_COULD_DO_MORE_TRAINING(8),
    YOU_SEEM_TO_BE_STRAINED_INJURED(9),
    YOU_SEEM_TO_BE_STRAINED(10);

    companion object {
        fun fromValue(value: Int): PolarDailyBalanceFeedbackName? {
            return entries.firstOrNull { feedback -> feedback.value == value }
        }
    }
}

enum class PolarTrainingReadinessName(val value: Int) {
    NOT_CALCULATED(-1),
    RECOVERED_READY_FOR_ALL_TRAINING(0),
    RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_NIGHTLY_RECHARGE_COMPROMISED(1),
    RECOVERED_READY_FOR_ALL_TRAINING_IF_FEELING_OK_POSSIBLY_STRESSED(2),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING(3),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO(4),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_NIGHTLY_RECHARGE(5),
    RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING_AND_LIGHT_CARDIO_POOR_CARDIO_RECOVERY(6),
    NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO(7),
    NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE(8),
    NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO(9),
    NOT_RECOVERED_NO_STRENGTH_OR_INTENSIVE_CARDIO_POOR_NIGHTLY_RECHARGE(10),
    RECOVERED_BUT_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING(11),
    NOT_RECOVERED_AND_INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING(12);

    companion object {
        fun fromValue(value: Int): PolarTrainingReadinessName? {
            return entries.firstOrNull { readiness -> readiness.value == value }
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
