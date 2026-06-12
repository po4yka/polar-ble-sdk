package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame.PmdDataFrameType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrameUtils
import com.polar.androidcommunications.common.ble.TypeUtils
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter

sealed class EcgDataSample

internal class EcgData {

    data class EcgSample(
        val timeStamp: ULong,
        val microVolts: Int,
        val overSampling: Boolean = false,
        val skinContactBit: Byte = 0,
        val contactImpedance: Byte = 0,
        val ecgDataTag: Byte = 0,
        val paceDataTag: Byte = 0,
    ) : EcgDataSample()

    data class EcgSampleFrameType3(
        val timeStamp: ULong,
        val data0: Int,
        val data1: Int,
        val status: UByte
    ) : EcgDataSample()

    val ecgSamples: MutableList<EcgDataSample> = mutableListOf()

    companion object {
        private const val TYPE_0_SAMPLE_SIZE_IN_BYTES = 3
        private const val TYPE_1_SAMPLE_SIZE_IN_BYTES = 3
        private const val TYPE_2_SAMPLE_SIZE_IN_BYTES = 3

        private const val TYPE_3_DATA_0_SIZE = 3
        private const val TYPE_3_DATA_1_SIZE = 3
        private const val TYPE_3_STATUS_SIZE = 1
        private const val TYPE_3_SAMPLE_SIZE_IN_BYTES = TYPE_3_DATA_0_SIZE + TYPE_3_DATA_1_SIZE + TYPE_3_STATUS_SIZE

        fun parseDataFromDataFrame(frame: PmdDataFrame): EcgData {
            val ecgData = EcgData()
            PolarRuntimePlannerAdapter.pmdEcgSamples(
                frameType = frame.frameType.id.toInt(),
                compressed = frame.isCompressedFrame,
                timeStamp = frame.timeStamp,
                previousTimeStamp = frame.previousTimeStamp,
                factor = frame.factor,
                sampleRate = frame.sampleRate,
                dataContent = frame.dataContent
            ).forEach { sample ->
                when (sample) {
                    is PolarRuntimePlannerAdapter.PlannedEcgType0Sample -> ecgData.ecgSamples.add(
                        EcgSample(
                            timeStamp = sample.timeStamp,
                            microVolts = sample.microVolts,
                            overSampling = sample.overSampling,
                            skinContactBit = sample.skinContactBit,
                            contactImpedance = sample.contactImpedance,
                            ecgDataTag = sample.ecgDataTag,
                            paceDataTag = sample.paceDataTag
                        )
                    )
                    is PolarRuntimePlannerAdapter.PlannedEcgType3Sample -> ecgData.ecgSamples.add(
                        EcgSampleFrameType3(
                            timeStamp = sample.timeStamp,
                            data0 = sample.data0,
                            data1 = sample.data1,
                            status = sample.status
                        )
                    )
                }
            }
            return ecgData
        }

        private fun dataFromRawType0(frame: PmdDataFrame): EcgData {
            val ecgData = EcgData()
            var offset = 0
            val step = TYPE_0_SAMPLE_SIZE_IN_BYTES
            val samplesSize = frame.dataContent.size / step
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0
            while (offset < frame.dataContent.size) {
                val microVolts = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset, TYPE_0_SAMPLE_SIZE_IN_BYTES)
                offset += step
                ecgData.ecgSamples.add(EcgSample(timeStamp = timeStamps[timeStampIndex], microVolts = microVolts))
                timeStampIndex++
            }
            return ecgData
        }

        private fun dataFromRawType1(frame: PmdDataFrame): EcgData {
            val ecgData = EcgData()
            var offset = 0
            val step = TYPE_1_SAMPLE_SIZE_IN_BYTES

            val samplesSize = frame.dataContent.size / step
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0
            while (offset < frame.dataContent.size) {
                val microVolts = (((frame.dataContent[offset]).toInt() and 0xFF) or (((frame.dataContent[offset + 1]).toInt() and 0x3F) shl 8)) and 0x3FFF
                val overSampling = (frame.dataContent[offset + 2].toInt() and 0x01) != 0
                val skinContactBit = ((frame.dataContent[offset + 2].toInt() and 0x06) shr 1).toByte()
                val contactImpedance = ((frame.dataContent[offset + 2].toInt() and 0x18) shr 3).toByte()
                offset += step
                ecgData.ecgSamples.add(
                    EcgSample(
                        timeStamp = timeStamps[timeStampIndex],
                        microVolts = microVolts,
                        overSampling = overSampling,
                        skinContactBit = skinContactBit,
                        contactImpedance = contactImpedance
                    )
                )
                timeStampIndex++
            }
            return ecgData
        }

        private fun dataFromRawType2(frame: PmdDataFrame): EcgData {
            val ecgData = EcgData()
            var offset = 0
            val step = TYPE_2_SAMPLE_SIZE_IN_BYTES

            val samplesSize = frame.dataContent.size / step
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0
            while (offset < frame.dataContent.size) {
                val microVolts = (frame.dataContent[offset].toInt() and 0xFF) or ((frame.dataContent[offset + 1].toInt() and 0xFF) shl 8) or ((frame.dataContent[offset + 2].toInt() and 0x03) shl 16) and 0x3FFFFF
                val ecgDataTag = ((frame.dataContent[offset + 2].toInt() and 0x1C) shr 2).toByte()
                val paceDataTag = ((frame.dataContent[offset + 2].toInt() and 0xE0) shr 5).toByte()
                offset += step
                ecgData.ecgSamples.add(
                    EcgSample(
                        timeStamp = timeStamps[timeStampIndex],
                        microVolts = microVolts,
                        ecgDataTag = ecgDataTag,
                        paceDataTag = paceDataTag
                    )
                )
                timeStampIndex++
            }
            return ecgData
        }

        private fun dataFromRawType3(frame: PmdDataFrame): EcgData {
            val ecgData = EcgData()
            var offset = 0

            val samplesSize = frame.dataContent.size / TYPE_3_SAMPLE_SIZE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0
            while (offset < frame.dataContent.size) {
                val data0 = PmdDataFrameUtils.parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 2)), BlePMDClient.PmdDataFieldEncoding.SIGNED_INT) as Int
                offset += TYPE_3_DATA_0_SIZE
                val data1 = PmdDataFrameUtils.parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 2)), BlePMDClient.PmdDataFieldEncoding.SIGNED_INT) as Int
                offset += TYPE_3_DATA_1_SIZE
                val status = PmdDataFrameUtils.parseFrameDataField(frame.dataContent.sliceArray(offset..offset), BlePMDClient.PmdDataFieldEncoding.UNSIGNED_BYTE) as UByte
                offset += TYPE_3_STATUS_SIZE

                ecgData.ecgSamples.add(
                    EcgSampleFrameType3(
                        timeStamp = timeStamps[timeStampIndex],
                        data0 = data0,
                        data1 = data1,
                        status = status,
                    )
                )
                timeStampIndex++
            }
            return ecgData
        }
    }
}
