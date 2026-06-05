package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.shared.pmd.sensors.PolarGnssCoordinateSample
import com.polar.shared.pmd.sensors.PolarGnssNmeaSample
import com.polar.shared.pmd.sensors.PolarGnssSatelliteDilutionSample
import com.polar.shared.pmd.sensors.PolarGnssSatelliteSummary
import com.polar.shared.pmd.sensors.PolarGnssSatelliteSummarySample
import com.polar.shared.pmd.sensors.PolarSensorDataParser

/**
 * Sealed class to represent Location data sample
 */
internal sealed class GnssLocationDataSample

internal class GnssLocationData {

    /**
     * GPS Coordinates, Speed, and Distance Data
     */
    data class GnssCoordinateSample(
        val timeStamp: ULong,
        val latitude: Double,
        val longitude: Double,
        // time in format "yyyy-MM-dd'T'HH:mm:ss.SSS"
        val date: String,
        // cumulative distance in m
        val cumulativeDistance: Double,
        // speed in km/h
        val speed: Float,
        val usedAccelerationSpeed: Float,
        val coordinateSpeed: Float,
        val accelerationSpeedFactor: Float,
        // course in degrees
        val course: Float,
        // speed in knots
        val gpsChipSpeed: Float,
        val fix: Boolean,
        val speedFlag: Int,
        val fusionState: UInt
    ) : GnssLocationDataSample()

    /**
     * GPS Satellite Dilution, and Altitude Data
     */
    data class GnssSatelliteDilutionSample(
        val timeStamp: ULong,
        // dilution distance in 0.01 precision
        val dilution: Float,
        // altitude in meters
        val altitude: Int,
        val numberOfSatellites: UInt,
        val fix: Boolean,
    ) : GnssLocationDataSample()

    data class GnssSatelliteSummary(
        val gpsNbrOfSat: UByte,
        val gpsMaxSnr: UByte,
        val glonassNbrOfSat: UByte,
        val glonassMaxSnr: UByte,
        val galileoNbrOfSat: UByte,
        val galileoMaxSnr: UByte,
        val beidouNbrOfSat: UByte,
        val beidouMaxSnr: UByte,
        val nbrOfSat: UByte,
        val snrTop5Avg: UByte,
    )

    /**
     * GPS Satellite Summary Data
     */
    data class GnssSatelliteSummarySample(
        val timeStamp: ULong,
        val seenGnssSatelliteSummaryBand1: GnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand1: GnssSatelliteSummary,
        val seenGnssSatelliteSummaryBand2: GnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand2: GnssSatelliteSummary,
        val maxSnr: UInt
    ) : GnssLocationDataSample()

    /**
     *  GPS NMEA Data
     */
    data class GnssGpsNMEASample(
        val timeStamp: ULong,
        val measurementPeriod: UInt,
        val messageLength: UInt,
        val statusFlags: UByte,
        val nmeaMessage: String
    ) : GnssLocationDataSample()

    val gnssLocationDataSamples: MutableList<GnssLocationDataSample> = mutableListOf()

    companion object {
        fun parseDataFromDataFrame(frame: PmdDataFrame): GnssLocationData {
            val locationData = GnssLocationData()
            PolarSensorDataParser.parseGnssLocation(frame.toPolarSharedFrame()).forEach { sample ->
                locationData.gnssLocationDataSamples.add(
                    when (sample) {
                        is PolarGnssCoordinateSample -> GnssCoordinateSample(
                            timeStamp = sample.timeStamp,
                            latitude = sample.latitude,
                            longitude = sample.longitude,
                            date = sample.date,
                            cumulativeDistance = sample.cumulativeDistance,
                            speed = sample.speed,
                            usedAccelerationSpeed = sample.usedAccelerationSpeed,
                            coordinateSpeed = sample.coordinateSpeed,
                            accelerationSpeedFactor = sample.accelerationSpeedFactor,
                            course = sample.course,
                            gpsChipSpeed = sample.gpsChipSpeed,
                            fix = sample.fix,
                            speedFlag = sample.speedFlag,
                            fusionState = sample.fusionState
                        )
                        is PolarGnssSatelliteDilutionSample -> GnssSatelliteDilutionSample(
                            timeStamp = sample.timeStamp,
                            dilution = sample.dilution,
                            altitude = sample.altitude,
                            numberOfSatellites = sample.numberOfSatellites,
                            fix = sample.fix
                        )
                        is PolarGnssSatelliteSummarySample -> GnssSatelliteSummarySample(
                            timeStamp = sample.timeStamp,
                            seenGnssSatelliteSummaryBand1 = sample.seenGnssSatelliteSummaryBand1.toAndroidSummary(),
                            usedGnssSatelliteSummaryBand1 = sample.usedGnssSatelliteSummaryBand1.toAndroidSummary(),
                            seenGnssSatelliteSummaryBand2 = sample.seenGnssSatelliteSummaryBand2.toAndroidSummary(),
                            usedGnssSatelliteSummaryBand2 = sample.usedGnssSatelliteSummaryBand2.toAndroidSummary(),
                            maxSnr = sample.maxSnr
                        )
                        is PolarGnssNmeaSample -> GnssGpsNMEASample(
                            timeStamp = sample.timeStamp,
                            measurementPeriod = sample.measurementPeriod,
                            messageLength = sample.messageLength,
                            statusFlags = sample.statusFlags,
                            nmeaMessage = sample.nmeaMessage
                        )
                    }
                )
            }
            return locationData
        }

        private fun PolarGnssSatelliteSummary.toAndroidSummary(): GnssSatelliteSummary {
            return GnssSatelliteSummary(
                gpsNbrOfSat = gpsNbrOfSat,
                gpsMaxSnr = gpsMaxSnr,
                glonassNbrOfSat = glonassNbrOfSat,
                glonassMaxSnr = glonassMaxSnr,
                galileoNbrOfSat = galileoNbrOfSat,
                galileoMaxSnr = galileoMaxSnr,
                beidouNbrOfSat = beidouNbrOfSat,
                beidouMaxSnr = beidouMaxSnr,
                nbrOfSat = nbrOfSat,
                snrTop5Avg = snrTop5Avg
            )
        }
    }
}
