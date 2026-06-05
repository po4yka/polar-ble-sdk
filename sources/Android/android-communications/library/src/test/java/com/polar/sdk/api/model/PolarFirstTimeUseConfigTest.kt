package com.polar.sdk.api.model

import fi.polar.remote.representation.protobuf.PhysData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PolarFirstTimeUseConfigTest {
    @Test
    fun `first time use physical config enum mapping preserves protobuf values`() {
        val config = PolarFirstTimeUseConfig(
            gender = PolarFirstTimeUseConfig.Gender.FEMALE,
            birthDate = LocalDate.of(1990, 1, 2),
            height = 170.0f,
            weight = 65.0f,
            maxHeartRate = 185,
            vo2Max = 45,
            restingHeartRate = 52,
            trainingBackground = 50,
            deviceTime = "2026-05-31T12:00:00Z",
            typicalDay = PolarFirstTimeUseConfig.TypicalDay.MOSTLY_MOVING,
            sleepGoalMinutes = 480
        )

        val proto = config.toProto()

        assertEquals(PhysData.PbUserTrainingBackground.TrainingBackground.SEMI_PRO, proto.trainingBackground.value)
        assertEquals(50, proto.trainingBackground.value.number)
        assertEquals(PhysData.PbUserTypicalDay.TypicalDay.MOSTLY_MOVING, proto.typicalDay.value)
        assertEquals(3, proto.typicalDay.value.number)
    }
}
