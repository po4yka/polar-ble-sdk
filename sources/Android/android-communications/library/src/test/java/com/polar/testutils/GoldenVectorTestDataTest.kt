package com.polar.testutils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.FileNotFoundException

class GoldenVectorTestDataTest {
    @Test
    fun `loads shared golden vector directories from Android tests`() {
        val vectorRoot = GoldenVectorTestData.root()
        val deviceIdVectors = GoldenVectorTestData.loadObjects("protocol/device-id")
        val emptyDeviceId = GoldenVectorTestData.loadObject("protocol/device-id/empty-device-id-platform-difference.json")

        assertTrue(vectorRoot.resolve("schema/golden-vector.schema.json").isFile)
        assertTrue(deviceIdVectors.isNotEmpty())
        assertEquals("empty-device-id-platform-difference", emptyDeviceId.get("id").asString)
    }

    @Test
    fun `missing shared golden vector path fails fast from Android tests`() {
        try {
            GoldenVectorTestData.loadObject("protocol/device-id/does-not-exist.json")
            fail("Missing golden vector path should throw FileNotFoundException")
        } catch (_: FileNotFoundException) {
            // Expected: missing fixtures must fail the consuming characterization test immediately.
        }
    }
}
