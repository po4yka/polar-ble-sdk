package com.polar.sharedtest

import com.polar.shared.pmd.sensors.PolarAccSample
import com.polar.shared.pmd.sensors.PolarEcgType0Sample
import com.polar.shared.pmd.sensors.PolarGnssCoordinateSample
import com.polar.shared.pmd.sensors.PolarGnssNmeaSample
import com.polar.shared.pmd.sensors.PolarGnssSatelliteDilutionSample
import com.polar.shared.pmd.sensors.PolarGnssSatelliteSummary
import com.polar.shared.pmd.sensors.PolarGnssSatelliteSummarySample
import com.polar.shared.pmd.sensors.PolarPmdDataFrame
import com.polar.shared.pmd.sensors.PolarPpgType0Sample
import com.polar.shared.pmd.sensors.PolarPpgType5Sample
import com.polar.shared.pmd.sensors.PolarPpgSportIdSample
import com.polar.shared.pmd.sensors.PolarSensorDataParser
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SensorParserProductionCommonPolicyTest {
    @Test
    fun productionSharedParserCoversCoreEcgAccGyrMagAndPpgGoldenVectors() {
        assertEcgType0("protocol/sensors/ecg-raw-type0-two-samples.json")
        assertAcc("protocol/sensors/acc-raw-type1-two-samples.json")
        assertGyr("protocol/sensors/gyr-compressed-type0-two-samples.json")
        assertMag("protocol/sensors/mag-compressed-type1-calibration-status.json")
        assertPpgType0("protocol/sensors/ppg-raw-type0-two-samples.json")
        assertPpgType5("protocol/sensors/ppg-raw-type5-operation-mode-max.json")
        assertPpgType6("protocol/sensors/ppg-raw-type6-sport-id.json")
    }

    @Test
    fun productionSharedParserCoversPpiPressureTemperatureAndSkinTemperatureGoldenVectors() {
        assertPpi("protocol/sensors/ppi-raw-type0-two-samples.json")
        assertPressure("protocol/sensors/pressure-raw-type0-single-sample.json")
        assertTemperature("protocol/sensors/temperature-raw-type0-single-sample.json")
        assertSkinTemperature("protocol/sensors/skin-temperature-raw-type0-single-sample.json")
        assertOfflineHr("protocol/sensors/offline-hr-raw-type1-two-samples.json")
    }

    @Test
    fun productionSharedParserCoversGnssLocationGoldenVectors() {
        assertGnssCoordinate("protocol/sensors/gnss-location-raw-type0-coordinate.json")
        assertGnssSatelliteDilution("protocol/sensors/gnss-location-raw-type1-satellite-dilution.json")
        assertGnssSatelliteSummary("protocol/sensors/gnss-location-raw-type2-satellite-summary.json")
        assertGnssNmea("protocol/sensors/gnss-location-raw-type3-nmea.json")
    }

    @Test
    fun productionSharedParserKeepsMalformedAndUnsupportedPolicyExecutable() {
        assertFailsWith<IllegalArgumentException> {
            PolarSensorDataParser.parseAcc(frame("protocol/sensors/acc-raw-type0-truncated-sample-android-error.json"))
        }
        assertFailsWith<IllegalArgumentException> {
            PolarSensorDataParser.parsePpg(frame("protocol/sensors/ppg-compressed-type2-unsupported.json"))
        }
        assertFailsWith<IllegalArgumentException> {
            PolarSensorDataParser.parseSkinTemperature(frame("protocol/sensors/skin-temperature-raw-type1-unsupported.json"))
        }
        assertFailsWith<IllegalArgumentException> {
            PolarSensorDataParser.parseOfflineHr(frame("protocol/sensors/offline-hr-raw-type1-truncated-tuple-android-error.json"))
        }
    }

    private fun assertGnssCoordinate(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parseGnssLocation(frameFromVector(vector)).filterIsInstance<PolarGnssCoordinateSample>().single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertDouble(expected.numericValue("latitude"), actual.latitude)
        assertDouble(expected.numericValue("longitude"), actual.longitude)
        assertEquals(expected.stringValue("date"), actual.date)
        assertDouble(expected.numericValue("cumulativeDistance"), actual.cumulativeDistance)
        assertFloat(expected.numericValue("speed"), actual.speed)
        assertFloat(expected.numericValue("usedAccelerationSpeed"), actual.usedAccelerationSpeed)
        assertFloat(expected.numericValue("coordinateSpeed"), actual.coordinateSpeed)
        assertFloat(expected.numericValue("accelerationSpeedFactor"), actual.accelerationSpeedFactor)
        assertFloat(expected.numericValue("course"), actual.course)
        assertFloat(expected.numericValue("gpsChipSpeed"), actual.gpsChipSpeed)
        assertEquals(expected.booleanValue("fix"), actual.fix)
        assertEquals(expected.intValue("speedFlag"), actual.speedFlag)
        assertEquals(expected.jsonNumberString("fusionState"), actual.fusionState.toString())
    }

    private fun assertGnssSatelliteDilution(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parseGnssLocation(frameFromVector(vector)).filterIsInstance<PolarGnssSatelliteDilutionSample>().single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertFloat(expected.numericValue("dilution"), actual.dilution)
        assertEquals(expected.intValue("altitude"), actual.altitude)
        assertEquals(expected.jsonNumberString("numberOfSatellites"), actual.numberOfSatellites.toString())
        assertEquals(expected.booleanValue("fix"), actual.fix)
    }

    private fun assertGnssSatelliteSummary(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parseGnssLocation(frameFromVector(vector)).filterIsInstance<PolarGnssSatelliteSummarySample>().single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertGnssSatelliteSummary(expected.objectValue("seenBand1"), actual.seenGnssSatelliteSummaryBand1)
        assertGnssSatelliteSummary(expected.objectValue("usedBand1"), actual.usedGnssSatelliteSummaryBand1)
        assertGnssSatelliteSummary(expected.objectValue("seenBand2"), actual.seenGnssSatelliteSummaryBand2)
        assertGnssSatelliteSummary(expected.objectValue("usedBand2"), actual.usedGnssSatelliteSummaryBand2)
        assertEquals(expected.jsonNumberString("maxSnr"), actual.maxSnr.toString())
    }

    private fun assertGnssNmea(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parseGnssLocation(frameFromVector(vector)).filterIsInstance<PolarGnssNmeaSample>().single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertEquals(expected.jsonNumberString("measurementPeriod"), actual.measurementPeriod.toString())
        assertEquals(expected.jsonNumberString("messageLength"), actual.messageLength.toString())
        assertEquals(expected.jsonNumberString("statusFlags"), actual.statusFlags.toString())
        assertEquals(expected.stringValue("nmeaMessage"), actual.nmeaMessage)
    }

    private fun assertEcgType0(path: String) {
        val vector = loadGoldenVectorText(path)
        val samples = PolarSensorDataParser.parseEcg(frameFromVector(vector)).filterIsInstance<PolarEcgType0Sample>()
        vector.expectedSamples().forEachIndexed { index, expected ->
            assertEquals(expected.jsonNumberString("timeStamp"), samples[index].timeStamp.toString(), "${vector.stringValue("id")} sample $index timestamp")
            assertEquals(expected.intValue("microVolts"), samples[index].microVolts, "${vector.stringValue("id")} sample $index microVolts")
        }
    }

    private fun assertAcc(path: String) {
        val vector = loadGoldenVectorText(path)
        val samples = PolarSensorDataParser.parseAcc(frameFromVector(vector))
        vector.expectedSamples().forEachIndexed { index, expected -> assertXyz(expected, samples[index], vector.stringValue("id"), index) }
    }

    private fun assertGyr(path: String) {
        val vector = loadGoldenVectorText(path)
        val samples = PolarSensorDataParser.parseGyr(frameFromVector(vector))
        vector.expectedSamples().forEachIndexed { index, expected ->
            val actual = samples[index]
            assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
            assertFloat(expected.doubleValue("x"), actual.x)
            assertFloat(expected.doubleValue("y"), actual.y)
            assertFloat(expected.doubleValue("z"), actual.z)
        }
    }

    private fun assertMag(path: String) {
        val vector = loadGoldenVectorText(path)
        val samples = PolarSensorDataParser.parseMag(frameFromVector(vector))
        vector.expectedSamples().forEachIndexed { index, expected ->
            val actual = samples[index]
            assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
            assertFloat(expected.doubleValue("x"), actual.x)
            assertFloat(expected.doubleValue("y"), actual.y)
            assertFloat(expected.doubleValue("z"), actual.z)
            assertEquals(expected.stringValue("calibrationStatus"), actual.calibrationStatus.name)
        }
    }

    private fun assertPpgType0(path: String) {
        val vector = loadGoldenVectorText(path)
        val samples = PolarSensorDataParser.parsePpg(frameFromVector(vector)).filterIsInstance<PolarPpgType0Sample>()
        vector.expectedSamples().forEachIndexed { index, expected ->
            val actual = samples[index]
            assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
            assertEquals(expected.signedIntArrayValue("ppg"), actual.ppgDataSamples)
            assertEquals(expected.intValue("ambient"), actual.ambientSample)
        }
    }

    private fun assertPpgType5(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parsePpg(frameFromVector(vector)).filterIsInstance<PolarPpgType5Sample>().single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertEquals(expected.jsonNumberString("operationMode"), actual.operationMode.toString())
    }

    private fun assertPpgType6(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parsePpg(frameFromVector(vector)).filterIsInstance<PolarPpgSportIdSample>().single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertEquals(expected.jsonNumberString("sportId"), actual.sportId.toString())
    }

    private fun assertPpi(path: String) {
        val vector = loadGoldenVectorText(path)
        val samples = PolarSensorDataParser.parsePpi(frameFromVector(vector))
        vector.expectedSamples().forEachIndexed { index, expected ->
            val actual = samples[index]
            assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
            assertEquals(expected.intValue("hr"), actual.hr)
            assertEquals(expected.intValue("ppInMs"), actual.ppInMs)
            assertEquals(expected.intValue("ppErrorEstimate"), actual.ppErrorEstimate)
            assertEquals(expected.intValue("blockerBit"), actual.blockerBit)
            assertEquals(expected.intValue("skinContactStatus"), actual.skinContactStatus)
            assertEquals(expected.intValue("skinContactSupported"), actual.skinContactSupported)
        }
    }

    private fun assertPressure(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parsePressure(frameFromVector(vector)).single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertFloat(expected.doubleValue("pressure"), actual.pressure)
    }

    private fun assertTemperature(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parseTemperature(frameFromVector(vector)).single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertFloat(expected.doubleValue("temperature"), actual.temperature)
    }

    private fun assertSkinTemperature(path: String) {
        val vector = loadGoldenVectorText(path)
        val actual = PolarSensorDataParser.parseSkinTemperature(frameFromVector(vector)).single()
        val expected = vector.expectedSamples().single()
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString())
        assertFloat(expected.doubleValue("skinTemperature"), actual.skinTemperature)
    }

    private fun assertOfflineHr(path: String) {
        val vector = loadGoldenVectorText(path)
        val samples = PolarSensorDataParser.parseOfflineHr(frameFromVector(vector))
        vector.expectedSamples().forEachIndexed { index, expected ->
            val actual = samples[index]
            assertEquals(expected.intValue("hr"), actual.hr)
            assertEquals(expected.intValue("ppgQuality"), actual.ppgQuality)
            assertEquals(expected.intValue("correctedHr"), actual.correctedHr)
        }
    }

    private fun assertXyz(expected: String, actual: PolarAccSample, caseId: String, index: Int) {
        assertEquals(expected.jsonNumberString("timeStamp"), actual.timeStamp.toString(), "$caseId sample $index timestamp")
        assertEquals(expected.intValue("x"), actual.x, "$caseId sample $index x")
        assertEquals(expected.intValue("y"), actual.y, "$caseId sample $index y")
        assertEquals(expected.intValue("z"), actual.z, "$caseId sample $index z")
    }

    private fun frame(path: String): PolarPmdDataFrame {
        return frameFromVector(loadGoldenVectorText(path))
    }

    private fun frameFromVector(vector: String): PolarPmdDataFrame {
        val input = vector.objectValue("input")
        return PolarPmdDataFrame.fromByteArray(
            data = hexToBytes(input.stringValue("dataFrameHex")),
            previousTimeStamp = input.jsonLong("previousTimeStamp"),
            factor = input.doubleValue("factor").toFloat(),
            sampleRate = input.intValue("sampleRate")
        )
    }

    private fun String.expectedSamples(): List<String> {
        return objectValue("expected").objectArray("samples")
    }

    private fun String.jsonLong(field: String): Long {
        return jsonNumberString(field).toLong()
    }

    private fun String.jsonNumberString(field: String): String {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)").find(this)?.groupValues?.get(1) ?: error("Missing numeric field $field in $this")
    }

    private fun String.numericValue(field: String): Double {
        return jsonNumberString(field).toDouble()
    }

    private fun assertFloat(expected: Double, actual: Float) {
        assertTrue(abs(expected - actual.toDouble()) < 0.0001, "Expected $expected, got $actual")
    }

    private fun assertDouble(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 0.00001, "Expected $expected, got $actual")
    }

    private fun assertGnssSatelliteSummary(expected: String, actual: PolarGnssSatelliteSummary) {
        assertEquals(expected.jsonNumberString("gpsNbrOfSat"), actual.gpsNbrOfSat.toString())
        assertEquals(expected.jsonNumberString("gpsMaxSnr"), actual.gpsMaxSnr.toString())
        assertEquals(expected.jsonNumberString("glonassNbrOfSat"), actual.glonassNbrOfSat.toString())
        assertEquals(expected.jsonNumberString("glonassMaxSnr"), actual.glonassMaxSnr.toString())
        assertEquals(expected.jsonNumberString("galileoNbrOfSat"), actual.galileoNbrOfSat.toString())
        assertEquals(expected.jsonNumberString("galileoMaxSnr"), actual.galileoMaxSnr.toString())
        assertEquals(expected.jsonNumberString("beidouNbrOfSat"), actual.beidouNbrOfSat.toString())
        assertEquals(expected.jsonNumberString("beidouMaxSnr"), actual.beidouMaxSnr.toString())
        assertEquals(expected.jsonNumberString("nbrOfSat"), actual.nbrOfSat.toString())
        assertEquals(expected.jsonNumberString("snrTop5Avg"), actual.snrTop5Avg.toString())
    }
}
