package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter

/**
 * Offline hr data
 */
internal class OfflineHrData {

    data class OfflineHrSample(
        val hr: Int,
        val ppgQuality: Int,
        val correctedHr: Int
    )

    val hrSamples: MutableList<OfflineHrSample> = mutableListOf()

    companion object {
        fun parseDataFromDataFrame(frame: PmdDataFrame): OfflineHrData {
            return if (frame.isCompressedFrame) {
                throw java.lang.Exception("Compressed FrameType: ${frame.frameType} is not supported by Offline HR data parser")
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0,
                    PmdDataFrame.PmdDataFrameType.TYPE_1 -> dataFromSharedParser(frame)
                    else -> throw java.lang.Exception("Raw FrameType: ${frame.frameType} is not supported by Offline HR data parser")
                }
            }
        }

        private fun dataFromSharedParser(frame: PmdDataFrame): OfflineHrData {
            val offlineHrData = OfflineHrData()
            PolarRuntimePlannerAdapter.pmdOfflineHrSamples(
                frameType = frame.frameType.id.toInt(),
                compressed = frame.isCompressedFrame,
                timeStamp = frame.timeStamp,
                previousTimeStamp = frame.previousTimeStamp,
                factor = frame.factor,
                sampleRate = frame.sampleRate,
                dataContent = frame.dataContent
            ).forEach { sample ->
                offlineHrData.hrSamples.add(
                    OfflineHrSample(
                        hr = sample.hr,
                        ppgQuality = sample.ppgQuality,
                        correctedHr = sample.correctedHr
                    )
                )
            }
            return offlineHrData
        }
    }
}
