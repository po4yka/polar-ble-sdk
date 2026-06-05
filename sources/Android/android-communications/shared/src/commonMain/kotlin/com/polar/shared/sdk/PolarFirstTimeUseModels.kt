package com.polar.shared.sdk

enum class PolarFirstTimeUseGenderName(val value: Int) {
    MALE(1),
    FEMALE(2);

    companion object {
        fun fromValue(value: Int): PolarFirstTimeUseGenderName? {
            return entries.firstOrNull { gender -> gender.value == value }
        }

        fun fromName(name: String): PolarFirstTimeUseGenderName? {
            return entries.firstOrNull { gender -> gender.name == name }
        }
    }
}

enum class PolarFirstTimeUseTrainingBackgroundName(val value: Int) {
    OCCASIONAL(10),
    REGULAR(20),
    FREQUENT(30),
    HEAVY(40),
    SEMI_PRO(50),
    PRO(60);

    companion object {
        fun fromValue(value: Int): PolarFirstTimeUseTrainingBackgroundName? {
            return entries.firstOrNull { background -> background.value == value }
        }
    }
}

enum class PolarFirstTimeUseTypicalDayName(val value: Int) {
    MOSTLY_SITTING(1),
    MOSTLY_STANDING(2),
    MOSTLY_MOVING(3);

    companion object {
        fun fromValue(value: Int): PolarFirstTimeUseTypicalDayName? {
            return entries.firstOrNull { typicalDay -> typicalDay.value == value }
        }
    }
}
