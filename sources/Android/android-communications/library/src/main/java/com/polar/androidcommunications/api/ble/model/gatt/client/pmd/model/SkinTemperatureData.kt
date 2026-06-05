package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrameUtils
import com.polar.shared.pmd.sensors.PolarSensorDataParser
import java.lang.Float.intBitsToFloat

internal class SkinTemperatureData {

    data class SkinTemperatureSample(
        // timeStamp ns since epoch time
        val timeStamp: ULong,
        // Sample contains signed temperature value in celsius
        val skinTemperature: Float
    )

    val skinTemperatureSamples: MutableList<SkinTemperatureSample> = mutableListOf()

    companion object {
        private const val TYPE_0_SAMPLE_SIZE_IN_BYTES = 4
        private const val TYPE_0_SAMPLE_SIZE_IN_BITS = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
        private const val TYPE_0_CHANNELS_IN_SAMPLE = 1

        fun parseDataFromDataFrame(frame: PmdDataFrame): SkinTemperatureData {
            val skinTemperatureData = SkinTemperatureData()
            PolarSensorDataParser.parseSkinTemperature(frame.toPolarSharedFrame()).forEach { sample ->
                skinTemperatureData.skinTemperatureSamples.add(SkinTemperatureSample(timeStamp = sample.timeStamp, skinTemperature = sample.skinTemperature))
            }
            return skinTemperatureData
        }

        private fun dataFromCompressedType0(frame: PmdDataFrame): SkinTemperatureData {
            val skinTemperatureData = SkinTemperatureData()
            val samples = BlePMDClient.parseDeltaFramesAll(frame.dataContent, TYPE_0_CHANNELS_IN_SAMPLE, TYPE_0_SAMPLE_SIZE_IN_BITS, PmdDataFieldEncoding.FLOAT_IEEE754)

            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samples.size, frame.sampleRate)
            for ((index, sample) in samples.withIndex()) {
                val pressure = if (frame.factor != 1.0f) intBitsToFloat(sample[0]) * frame.factor else intBitsToFloat(sample[0])
                skinTemperatureData.skinTemperatureSamples.add(SkinTemperatureSample(timeStamp = timeStamps[index], pressure))
            }
            return skinTemperatureData
        }

        private fun dataFromRawType0(frame: PmdDataFrame): SkinTemperatureData {
            val skinTemperatureData = SkinTemperatureData()
            var offset = 0
            val step = TYPE_0_SAMPLE_SIZE_IN_BYTES

            val samplesSize = frame.dataContent.size / step
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val temperature = PmdDataFrameUtils.parseFrameDataField(frame.dataContent.sliceArray(offset until (offset + TYPE_0_SAMPLE_SIZE_IN_BYTES)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += step
                skinTemperatureData.skinTemperatureSamples.add(SkinTemperatureSample(timeStamp = timeStamps[timeStampIndex], temperature))
                timeStampIndex++
            }
            return skinTemperatureData
        }
    }
}
