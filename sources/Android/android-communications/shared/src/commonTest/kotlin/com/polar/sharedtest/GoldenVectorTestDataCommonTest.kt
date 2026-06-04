package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class GoldenVectorTestDataCommonTest {
    @Test
    fun loadsSharedGoldenVectorFromCommonTest() {
        val vector = loadGoldenVectorText("protocol/device-id/polar-device-uuid-valid.json")

        assertContains(vector, "\"id\": \"polar-device-uuid-valid\"")
    }

    @Test
    fun missingSharedGoldenVectorPathFailsFastFromCommonTest() {
        assertFailsWith<Throwable> {
            loadGoldenVectorText("protocol/device-id/does-not-exist.json")
        }
    }
}
