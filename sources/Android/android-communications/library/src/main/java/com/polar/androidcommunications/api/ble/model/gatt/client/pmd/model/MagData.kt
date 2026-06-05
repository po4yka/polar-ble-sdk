package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.shared.pmd.sensors.PolarMagCalibrationStatus
import com.polar.shared.pmd.sensors.PolarSensorDataParser

internal class MagData() {

    enum class CalibrationStatus(val id: Int) {
        NOT_AVAILABLE(-1),
        UNKNOWN(0),
        POOR(1),
        OK(2),
        GOOD(3);

        companion object {
            fun getById(id: Int): CalibrationStatus {
                return PolarMagCalibrationStatus.fromId(id).toAndroidCalibrationStatus()
            }
        }
    }

    data class MagSample internal constructor(
        val timeStamp: ULong = 0uL,
        // Sample contains signed x,y,z axis values in Gauss
        val x: Float,
        val y: Float,
        val z: Float,
        val calibrationStatus: CalibrationStatus = CalibrationStatus.NOT_AVAILABLE
    )

    val magSamples: MutableList<MagSample> = ArrayList()

    companion object {
        private const val TYPE_0_SAMPLE_SIZE_IN_BYTES = 2
        private const val TYPE_0_SAMPLE_SIZE_IN_BITS = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_0_CHANNELS_IN_SAMPLE = 3

        private const val TYPE_1_SAMPLE_SIZE_IN_BYTES = 2
        private const val TYPE_1_SAMPLE_SIZE_IN_BITS = TYPE_1_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_1_CHANNELS_IN_SAMPLE = 4

        fun parseDataFromDataFrame(frame: PmdDataFrame): MagData {
            val magData = MagData()
            PolarSensorDataParser.parseMag(frame.toPolarSharedFrame()).forEach { sample ->
                magData.magSamples.add(
                    MagSample(
                        timeStamp = sample.timeStamp,
                        x = sample.x,
                        y = sample.y,
                        z = sample.z,
                        calibrationStatus = sample.calibrationStatus.toAndroidCalibrationStatus()
                    )
                )
            }
            return magData
        }

        private fun PolarMagCalibrationStatus.toAndroidCalibrationStatus(): CalibrationStatus {
            return CalibrationStatus.valueOf(name)
        }

        private fun dataCompressedFromType0(frame: PmdDataFrame): MagData {
            val samples = BlePMDClient.parseDeltaFramesAll(frame.dataContent, TYPE_0_CHANNELS_IN_SAMPLE, TYPE_0_SAMPLE_SIZE_IN_BITS, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val magData = MagData()

            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)

            for ((index, sample) in samples.withIndex()) {
                val x = if (frame.factor != 1.0f) sample[0] * frame.factor else sample[0].toFloat()
                val y = if (frame.factor != 1.0f) sample[1] * frame.factor else sample[1].toFloat()
                val z = if (frame.factor != 1.0f) sample[2] * frame.factor else sample[2].toFloat()
                magData.magSamples.add(MagSample(timeStamp = timeStamps[index], x, y, z))
            }
            return magData
        }

        private fun dataCompressedFromType1(frame: PmdDataFrame): MagData {
            val samples = BlePMDClient.parseDeltaFramesAll(frame.dataContent, TYPE_1_CHANNELS_IN_SAMPLE, TYPE_1_SAMPLE_SIZE_IN_BITS, BlePMDClient.PmdDataFieldEncoding.SIGNED_INT)
            val magData = MagData()

            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)

            val unitConversionFactor = 1000 // type 1 data arrives in milliGauss units
            for ((index, sample) in samples.withIndex()) {
                val x = (if (frame.factor != 1.0f) sample[0] * frame.factor else sample[0].toFloat()) / unitConversionFactor
                val y = (if (frame.factor != 1.0f) sample[1] * frame.factor else sample[1].toFloat()) / unitConversionFactor
                val z = (if (frame.factor != 1.0f) sample[2] * frame.factor else sample[2].toFloat()) / unitConversionFactor
                val status = CalibrationStatus.getById(sample[3])
                magData.magSamples.add(MagSample(timeStamps[index], x = x, y = y, z = z, calibrationStatus = status))
            }
            return magData
        }
    }
}
