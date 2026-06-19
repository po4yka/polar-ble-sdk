package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.common.ble.TypeUtils
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter

internal class PpiData {
    data class PpiSample(
        val hr: Int,
        val ppInMs: Int,
        val ppErrorEstimate: Int,
        val blockerBit: Int,
        val skinContactStatus: Int,
        val skinContactSupported: Int,
        var timeStamp: ULong
    )

    val ppiSamples: MutableList<PpiSample> = mutableListOf()

    companion object {
        fun parseDataFromDataFrame(frame: PmdDataFrame): PpiData {
            val ppiData = PpiData()
            PolarRuntimePlannerAdapter.pmdPpiSamples(
                frameType = frame.frameType.id.toInt(),
                compressed = frame.isCompressedFrame,
                timeStamp = frame.timeStamp,
                previousTimeStamp = frame.previousTimeStamp,
                factor = frame.factor,
                sampleRate = frame.sampleRate,
                dataContent = frame.dataContent
            ).forEach { sample ->
                ppiData.ppiSamples.add(
                    PpiSample(
                        hr = sample.hr,
                        ppInMs = sample.ppInMs,
                        ppErrorEstimate = sample.ppErrorEstimate,
                        blockerBit = sample.blockerBit,
                        skinContactStatus = sample.skinContactStatus,
                        skinContactSupported = sample.skinContactSupported,
                        timeStamp = sample.timeStamp
                    )
                )
            }
            return ppiData
        }

        private fun dataFromType0(frame: PmdDataFrame): PpiData {
            val ppiData = PpiData()
            var offset = 0
            while (offset < frame.dataContent.size) {
                val finalOffset = offset
                if (finalOffset + 6 > frame.dataContent.size) break
                val sample = frame.dataContent.copyOfRange(finalOffset, finalOffset + 6)

                val hr = sample[0].toInt() and 0xFF
                val ppInMs = TypeUtils.convertArrayToUnsignedLong(sample, 1, 2).toInt()
                val ppErrorEstimate = TypeUtils.convertArrayToUnsignedLong(sample, 3, 2).toInt()
                val blockerBit: Int = sample[5].toInt() and 0x01
                val skinContactStatus: Int = sample[5].toInt() and 0x02 shr 1
                val skinContactSupported: Int = sample[5].toInt() and 0x04 shr 2

                ppiData.ppiSamples.add(
                    PpiSample(
                        hr = hr,
                        ppInMs = ppInMs,
                        ppErrorEstimate = ppErrorEstimate,
                        blockerBit = blockerBit,
                        skinContactStatus = skinContactStatus,
                        skinContactSupported = skinContactSupported,
                        timeStamp = 0u // Set actual timestamp later.
                    )
                )
                offset += 6
            }

            if (frame.timeStamp != 0uL) {
                var currentTimestamp = frame.timeStamp

                for (i in ppiData.ppiSamples.indices.reversed()) {
                    val sample = ppiData.ppiSamples[i]
                    sample.timeStamp = currentTimestamp
                    currentTimestamp -= (sample.ppInMs.toULong() * 1_000_000UL)
                }
            }

            return ppiData
        }
    }
}
