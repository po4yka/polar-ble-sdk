package com.polar.shared.pmd.sensors

import kotlin.math.ceil
import kotlin.math.round

data class PolarPmdDataFrame(
    val frameType: Int,
    val compressed: Boolean,
    val timeStamp: ULong,
    val previousTimeStamp: ULong,
    val factor: Float,
    val sampleRate: Int,
    val dataContent: ByteArray
) {
    companion object {
        private const val HEADER_SIZE = 10
        private const val COMPRESSED_MASK = 0x80
        private const val FRAME_TYPE_MASK = 0x7F

        fun fromByteArray(data: ByteArray, previousTimeStamp: Long, factor: Float, sampleRate: Int): PolarPmdDataFrame {
            return fromByteArray(
                data = data,
                previousTimeStamp = previousTimeStamp.toULong(),
                factor = factor,
                sampleRate = sampleRate
            )
        }

        fun fromByteArray(data: ByteArray, previousTimeStamp: ULong, factor: Float, sampleRate: Int): PolarPmdDataFrame {
            require(data.size >= HEADER_SIZE) { "malformedFrame" }
            val frameTypeField = data[9].toInt() and BYTE_MASK
            return PolarPmdDataFrame(
                frameType = frameTypeField and FRAME_TYPE_MASK,
                compressed = (frameTypeField and COMPRESSED_MASK) != 0,
                timeStamp = data.readUnsignedLong(1, 8),
                previousTimeStamp = previousTimeStamp,
                factor = factor,
                sampleRate = sampleRate,
                dataContent = data.copyOfRange(HEADER_SIZE, data.size)
            )
        }
    }
}

sealed class PolarEcgSample

data class PolarEcgType0Sample(
    val timeStamp: ULong,
    val microVolts: Int,
    val overSampling: Boolean = false,
    val skinContactBit: Byte = 0,
    val contactImpedance: Byte = 0,
    val ecgDataTag: Byte = 0,
    val paceDataTag: Byte = 0
) : PolarEcgSample()

data class PolarEcgType3Sample(
    val timeStamp: ULong,
    val data0: Int,
    val data1: Int,
    val status: UByte
) : PolarEcgSample()

data class PolarAccSample(val timeStamp: ULong, val x: Int, val y: Int, val z: Int)
data class PolarGyrSample(val timeStamp: ULong, val x: Float, val y: Float, val z: Float)
data class PolarMagSample(val timeStamp: ULong, val x: Float, val y: Float, val z: Float, val calibrationStatus: PolarMagCalibrationStatus = PolarMagCalibrationStatus.NOT_AVAILABLE)
data class PolarPpiSample(val hr: Int, val ppInMs: Int, val ppErrorEstimate: Int, val blockerBit: Int, val skinContactStatus: Int, val skinContactSupported: Int, val timeStamp: ULong)
data class PolarPressureSample(val timeStamp: ULong, val pressure: Float)
data class PolarTemperatureSample(val timeStamp: ULong, val temperature: Float)
data class PolarSkinTemperatureSample(val timeStamp: ULong, val skinTemperature: Float)
data class PolarOfflineHrSample(val hr: Int, val ppgQuality: Int, val correctedHr: Int)
sealed class PolarGnssLocationSample
data class PolarGnssCoordinateSample(
    val timeStamp: ULong,
    val latitude: Double,
    val longitude: Double,
    val date: String,
    val cumulativeDistance: Double,
    val speed: Float,
    val usedAccelerationSpeed: Float,
    val coordinateSpeed: Float,
    val accelerationSpeedFactor: Float,
    val course: Float,
    val gpsChipSpeed: Float,
    val fix: Boolean,
    val speedFlag: Int,
    val fusionState: UInt
) : PolarGnssLocationSample()
data class PolarGnssSatelliteDilutionSample(
    val timeStamp: ULong,
    val dilution: Float,
    val altitude: Int,
    val numberOfSatellites: UInt,
    val fix: Boolean
) : PolarGnssLocationSample()
data class PolarGnssSatelliteSummary(
    val gpsNbrOfSat: UByte,
    val gpsMaxSnr: UByte,
    val glonassNbrOfSat: UByte,
    val glonassMaxSnr: UByte,
    val galileoNbrOfSat: UByte,
    val galileoMaxSnr: UByte,
    val beidouNbrOfSat: UByte,
    val beidouMaxSnr: UByte,
    val nbrOfSat: UByte,
    val snrTop5Avg: UByte
)
data class PolarGnssSatelliteSummarySample(
    val timeStamp: ULong,
    val seenGnssSatelliteSummaryBand1: PolarGnssSatelliteSummary,
    val usedGnssSatelliteSummaryBand1: PolarGnssSatelliteSummary,
    val seenGnssSatelliteSummaryBand2: PolarGnssSatelliteSummary,
    val usedGnssSatelliteSummaryBand2: PolarGnssSatelliteSummary,
    val maxSnr: UInt
) : PolarGnssLocationSample()
data class PolarGnssNmeaSample(
    val timeStamp: ULong,
    val measurementPeriod: UInt,
    val messageLength: UInt,
    val statusFlags: UByte,
    val nmeaMessage: String
) : PolarGnssLocationSample()

sealed class PolarLocationDataProjectionSample
data class PolarLocationCoordinatesProjectionSample(
    val timeStamp: Long,
    val latitude: Double,
    val longitude: Double,
    val time: String,
    val cumulativeDistance: Double,
    val speed: Float,
    val usedAccelerationSpeed: Float,
    val coordinateSpeed: Float,
    val accelerationSpeedFactor: Float,
    val course: Float,
    val gpsChipSpeed: Float,
    val fix: Boolean,
    val speedFlag: Int,
    val fusionState: UInt
) : PolarLocationDataProjectionSample()
data class PolarLocationSatelliteDilutionProjectionSample(
    val timeStamp: Long,
    val dilution: Float,
    val altitude: Int,
    val numberOfSatellites: UInt,
    val fix: Boolean
) : PolarLocationDataProjectionSample()
data class PolarLocationSatelliteSummaryProjection(
    val gpsNbrOfSat: UByte,
    val gpsMaxSnr: UByte,
    val glonassNbrOfSat: UByte,
    val glonassMaxSnr: UByte,
    val galileoNbrOfSat: UByte,
    val galileoMaxSnr: UByte,
    val beidouNbrOfSat: UByte,
    val beidouMaxSnr: UByte,
    val nbrOfSat: UByte,
    val snrTop5Avg: UByte
)
data class PolarLocationSatelliteSummaryProjectionSample(
    val timeStamp: Long,
    val seenSatelliteSummaryBand1: PolarLocationSatelliteSummaryProjection,
    val usedSatelliteSummaryBand1: PolarLocationSatelliteSummaryProjection,
    val seenSatelliteSummaryBand2: PolarLocationSatelliteSummaryProjection,
    val usedSatelliteSummaryBand2: PolarLocationSatelliteSummaryProjection,
    val maxSnr: UInt
) : PolarLocationDataProjectionSample()
data class PolarLocationNmeaProjectionSample(
    val timeStamp: Long,
    val measurementPeriod: UInt,
    val statusFlags: UByte,
    val nmeaMessage: String
) : PolarLocationDataProjectionSample()

object PolarLocationDataProjection {
    fun fromGnssSamples(samples: List<PolarGnssLocationSample>): List<PolarLocationDataProjectionSample> {
        return samples.map { sample ->
            when (sample) {
                is PolarGnssCoordinateSample -> PolarLocationCoordinatesProjectionSample(
                    timeStamp = sample.timeStamp.toLong(),
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    time = sample.date,
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
                is PolarGnssSatelliteDilutionSample -> PolarLocationSatelliteDilutionProjectionSample(
                    timeStamp = sample.timeStamp.toLong(),
                    dilution = sample.dilution,
                    altitude = sample.altitude,
                    numberOfSatellites = sample.numberOfSatellites,
                    fix = sample.fix
                )
                is PolarGnssSatelliteSummarySample -> PolarLocationSatelliteSummaryProjectionSample(
                    timeStamp = sample.timeStamp.toLong(),
                    seenSatelliteSummaryBand1 = sample.seenGnssSatelliteSummaryBand1.toLocationProjection(),
                    usedSatelliteSummaryBand1 = sample.usedGnssSatelliteSummaryBand1.toLocationProjection(),
                    seenSatelliteSummaryBand2 = sample.seenGnssSatelliteSummaryBand2.toLocationProjection(),
                    usedSatelliteSummaryBand2 = sample.usedGnssSatelliteSummaryBand2.toLocationProjection(),
                    maxSnr = sample.maxSnr
                )
                is PolarGnssNmeaSample -> PolarLocationNmeaProjectionSample(
                    timeStamp = sample.timeStamp.toLong(),
                    measurementPeriod = sample.measurementPeriod,
                    statusFlags = sample.statusFlags,
                    nmeaMessage = sample.nmeaMessage
                )
            }
        }
    }

    private fun PolarGnssSatelliteSummary.toLocationProjection(): PolarLocationSatelliteSummaryProjection {
        return PolarLocationSatelliteSummaryProjection(
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

enum class PolarMagCalibrationStatus(val id: Int) {
    NOT_AVAILABLE(-1),
    UNKNOWN(0),
    POOR(1),
    OK(2),
    GOOD(3);

    companion object {
        fun fromId(id: Int): PolarMagCalibrationStatus {
            return entries.firstOrNull { it.id == id } ?: NOT_AVAILABLE
        }
    }
}

sealed class PolarPpgSample

data class PolarPpgType0Sample(val timeStamp: ULong, val ppgDataSamples: List<Int>, val ambientSample: Int) : PolarPpgSample()
data class PolarPpgType4Sample(val timeStamp: ULong, val numIntTs: List<UInt>, val channel1GainTs: List<UInt>, val channel2GainTs: List<UInt>) : PolarPpgSample()
data class PolarPpgType5Sample(val timeStamp: ULong, val operationMode: UInt) : PolarPpgSample()
data class PolarPpgType7Sample(val timeStamp: ULong, val ppgDataSamples: List<Int>, val statusBits: List<Int>) : PolarPpgSample()
data class PolarPpgType8Sample(val timeStamp: ULong, val ppgDataSamples: List<Int>, val statusBits: List<Int>) : PolarPpgSample()
data class PolarPpgType9Sample(val timeStamp: ULong, val numIntTs: List<UInt>, val channel1GainTs: List<UInt>, val channel2GainTs: List<UInt>) : PolarPpgSample()
data class PolarPpgType10Sample(val timeStamp: ULong, val greenSamples: List<Int>, val redSamples: List<Int>, val irSamples: List<Int>, val statusBits: List<Int>) : PolarPpgSample()
data class PolarPpgType13Sample(val timeStamp: ULong, val ppgChannel0: List<Int>, val ppgChannel1: List<Int>, val statusBits: List<Int>) : PolarPpgSample()
data class PolarPpgType14Sample(val timeStamp: ULong, val numIntTs1: List<UInt>, val channel1GainTs1: List<UInt>, val channel2GainTs1: List<UInt>) : PolarPpgSample()
data class PolarPpgSportIdSample(val timeStamp: ULong, val sportId: ULong) : PolarPpgSample()

object PolarSensorDataParser {
    fun parseEcg(frame: PolarPmdDataFrame): List<PolarEcgSample> {
        if (frame.compressed) throw IllegalArgumentException("unsupportedCompressedFrame")
        return when (frame.frameType) {
            0 -> parseEcgRawType0(frame)
            1 -> parseEcgRawType1(frame)
            2 -> parseEcgRawType2(frame)
            3 -> parseEcgRawType3(frame)
            else -> throw IllegalArgumentException("unsupportedFrame")
        }
    }

    fun parseAcc(frame: PolarPmdDataFrame): List<PolarAccSample> {
        val samples = if (frame.compressed) {
            when (frame.frameType) {
                0 -> parseAccCompressed(frame, factor = frame.factor * 1000f, resolutionBits = 16)
                1 -> parseAccCompressed(frame, factor = frame.factor, resolutionBits = 16)
                else -> throw IllegalArgumentException("unsupportedCompressedFrame")
            }
        } else {
            when (frame.frameType) {
                0 -> parseAccRaw(frame, step = 1)
                1 -> parseAccRaw(frame, step = 2)
                2 -> parseAccRaw(frame, step = 3)
                else -> throw IllegalArgumentException("unsupportedFrame")
            }
        }
        return samples.withAccTimeStamps(frame)
    }

    fun parseGyr(frame: PolarPmdDataFrame): List<PolarGyrSample> {
        if (!frame.compressed) throw IllegalArgumentException("unsupportedFrame")
        val rawSamples = when (frame.frameType) {
            0 -> parseDeltaFramesAll(frame.dataContent, channels = 3, resolution = 16, fieldEncoding = FieldEncoding.SIGNED_INT).map { sample ->
                PolarGyrSample(0uL, sample[0].scaled(frame.factor), sample[1].scaled(frame.factor), sample[2].scaled(frame.factor))
            }
            1 -> parseDeltaFramesAll(frame.dataContent, channels = 3, resolution = 32, fieldEncoding = FieldEncoding.FLOAT_IEEE754).map { sample ->
                PolarGyrSample(0uL, sample[0].floatScaled(frame.factor), sample[1].floatScaled(frame.factor), sample[2].floatScaled(frame.factor))
            }
            else -> throw IllegalArgumentException("unsupportedCompressedFrame")
        }
        return rawSamples.withGyrTimeStamps(frame)
    }

    fun parseMag(frame: PolarPmdDataFrame): List<PolarMagSample> {
        if (!frame.compressed) throw IllegalArgumentException("unsupportedFrame")
        val rawSamples = when (frame.frameType) {
            0 -> parseDeltaFramesAll(frame.dataContent, channels = 3, resolution = 16, fieldEncoding = FieldEncoding.SIGNED_INT).map { sample ->
                PolarMagSample(0uL, sample[0].scaled(frame.factor), sample[1].scaled(frame.factor), sample[2].scaled(frame.factor))
            }
            1 -> parseDeltaFramesAll(frame.dataContent, channels = 4, resolution = 16, fieldEncoding = FieldEncoding.SIGNED_INT).map { sample ->
                PolarMagSample(
                    timeStamp = 0uL,
                    x = sample[0].scaled(frame.factor) / 1000f,
                    y = sample[1].scaled(frame.factor) / 1000f,
                    z = sample[2].scaled(frame.factor) / 1000f,
                    calibrationStatus = PolarMagCalibrationStatus.fromId(sample[3])
                )
            }
            else -> throw IllegalArgumentException("unsupportedCompressedFrame")
        }
        return rawSamples.withMagTimeStamps(frame)
    }

    fun parsePpg(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        return if (frame.compressed) {
            when (frame.frameType) {
                0 -> parsePpgCompressedType0(frame)
                7 -> parsePpgCompressedType7(frame)
                8 -> parsePpgCompressedType8(frame)
                10 -> parsePpgCompressedType10(frame)
                13 -> parsePpgCompressedType13(frame)
                else -> throw IllegalArgumentException("unsupportedCompressedFrame")
            }
        } else {
            when (frame.frameType) {
                0 -> parsePpgRawType0(frame)
                4 -> parsePpgRawType4(frame)
                5 -> parsePpgRawType5(frame)
                6 -> parsePpgRawType6(frame)
                9 -> parsePpgRawType9(frame)
                14 -> parsePpgRawType14(frame)
                else -> throw IllegalArgumentException("unsupportedFrame")
            }
        }
    }

    fun parsePpi(frame: PolarPmdDataFrame): List<PolarPpiSample> {
        if (frame.compressed) throw IllegalArgumentException("unsupportedCompressedFrame")
        if (frame.frameType != 0) throw IllegalArgumentException("unsupportedFrame")
        if (frame.dataContent.size % 6 != 0) throw IllegalArgumentException("malformedFrame")
        val samples = mutableListOf<PolarPpiSample>()
        var offset = 0
        while (offset < frame.dataContent.size) {
            val sample = frame.dataContent.copyOfRange(offset, offset + 6)
            samples += PolarPpiSample(
                hr = sample[0].toInt() and BYTE_MASK,
                ppInMs = sample.readUnsignedLong(1, 2).toInt(),
                ppErrorEstimate = sample.readUnsignedLong(3, 2).toInt(),
                blockerBit = sample[5].toInt() and 0x01,
                skinContactStatus = sample[5].toInt() and 0x02 shr 1,
                skinContactSupported = sample[5].toInt() and 0x04 shr 2,
                timeStamp = 0uL
            )
            offset += 6
        }
        if (frame.timeStamp == 0uL) return samples
        var currentTimestamp = frame.timeStamp
        return samples.asReversed().map { sample ->
            val stamped = sample.copy(timeStamp = currentTimestamp)
            currentTimestamp -= sample.ppInMs.toULong() * 1_000_000uL
            stamped
        }.asReversed()
    }

    fun parsePressure(frame: PolarPmdDataFrame): List<PolarPressureSample> {
        val rawSamples = parseFloatScalar(frame, rawName = "Pressure").map { PolarPressureSample(0uL, it) }
        return rawSamples.withPressureTimeStamps(frame)
    }

    fun parseTemperature(frame: PolarPmdDataFrame): List<PolarTemperatureSample> {
        val rawSamples = parseFloatScalar(frame, rawName = "Temperature").map { PolarTemperatureSample(0uL, it) }
        return rawSamples.withTemperatureTimeStamps(frame)
    }

    fun parseSkinTemperature(frame: PolarPmdDataFrame): List<PolarSkinTemperatureSample> {
        val rawSamples = parseFloatScalar(frame, rawName = "Skin Temperature").map { PolarSkinTemperatureSample(0uL, it) }
        return rawSamples.withSkinTemperatureTimeStamps(frame)
    }

    fun parseOfflineHr(frame: PolarPmdDataFrame): List<PolarOfflineHrSample> {
        if (frame.compressed) throw IllegalArgumentException("unsupportedCompressedFrame")
        return when (frame.frameType) {
            0 -> frame.dataContent.map { byte ->
                PolarOfflineHrSample(hr = byte.toInt() and BYTE_MASK, ppgQuality = 0, correctedHr = 0)
            }
            1 -> {
                val sampleSize = 3
                if (frame.dataContent.size % sampleSize != 0) throw IllegalArgumentException("malformedFrame")
                (0 until frame.dataContent.size / sampleSize).map { index ->
                    val offset = index * sampleSize
                    PolarOfflineHrSample(
                        hr = frame.dataContent[offset].toInt() and BYTE_MASK,
                        ppgQuality = frame.dataContent[offset + 1].toInt() and BYTE_MASK,
                        correctedHr = frame.dataContent[offset + 2].toInt() and BYTE_MASK
                    )
                }
            }
            else -> throw IllegalArgumentException("unsupportedFrame")
        }
    }

    fun parseGnssLocation(frame: PolarPmdDataFrame): List<PolarGnssLocationSample> {
        if (frame.compressed) throw IllegalArgumentException("unsupportedCompressedFrame")
        return when (frame.frameType) {
            0 -> parseGnssRawType0(frame)
            1 -> parseGnssRawType1(frame)
            2 -> parseGnssRawType2(frame)
            3 -> parseGnssRawType3(frame)
            else -> throw IllegalArgumentException("unsupportedFrame")
        }
    }

    private fun parseGnssRawType0(frame: PolarPmdDataFrame): List<PolarGnssLocationSample> {
        val sampleSize = 51
        if (frame.dataContent.size % sampleSize != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / sampleSize)
        return (0 until frame.dataContent.size / sampleSize).map { index ->
            var offset = index * sampleSize
            val latitude = frame.dataContent.readDouble(offset)
            offset += 8
            val longitude = frame.dataContent.readDouble(offset)
            offset += 8
            val year = frame.dataContent.readUnsignedLong(offset, 2).toInt()
            offset += 2
            val month = frame.dataContent.readUnsignedLong(offset, 1).toInt()
            offset += 1
            val day = frame.dataContent.readUnsignedLong(offset, 1).toInt()
            offset += 1
            val time = frame.dataContent.readUnsignedLong(offset, 4).toUInt()
            offset += 4
            val milliseconds = time and 0x3FFu
            val hours = (time and 0x7C00u) shr 10
            val minutes = (time and 0x1F8000u) shr 15
            val seconds = (time and 0x7E00000u) shr 21
            val cumulativeDistance = frame.dataContent.readUnsignedLong(offset, 4).toDouble() / 10.0
            offset += 4
            val speed = frame.dataContent.readFloat(offset)
            offset += 4
            val usedAccelerationSpeed = frame.dataContent.readFloat(offset)
            offset += 4
            val coordinateSpeed = frame.dataContent.readFloat(offset)
            offset += 4
            val accelerationSpeedFactor = frame.dataContent.readFloat(offset)
            offset += 4
            val course = frame.dataContent.readUnsignedLong(offset, 2).toFloat() / 100f
            offset += 2
            val gpsChipSpeed = frame.dataContent.readUnsignedLong(offset, 2).toFloat() / 100f
            offset += 2
            val fix = frame.dataContent[offset].toInt() != 0
            offset += 1
            val speedFlag = frame.dataContent.readSignedInt(offset, 1)
            offset += 1
            val fusionState = frame.dataContent.readUnsignedLong(offset, 1).toUInt()
            PolarGnssCoordinateSample(
                timeStamp = timeStamps[index],
                latitude = latitude,
                longitude = longitude,
                date = "${year.padded(4)}-${month.padded(2)}-${day.padded(2)}T${hours.toInt().padded(2)}:${minutes.toInt().padded(2)}:${seconds.toInt().padded(2)}.${milliseconds.toInt().padded(3)}",
                cumulativeDistance = cumulativeDistance,
                speed = speed,
                usedAccelerationSpeed = usedAccelerationSpeed,
                coordinateSpeed = coordinateSpeed,
                accelerationSpeedFactor = accelerationSpeedFactor,
                course = course,
                gpsChipSpeed = gpsChipSpeed,
                fix = fix,
                speedFlag = speedFlag,
                fusionState = fusionState
            )
        }
    }

    private fun parseGnssRawType1(frame: PolarPmdDataFrame): List<PolarGnssLocationSample> {
        val sampleSize = 6
        if (frame.dataContent.size % sampleSize != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / sampleSize)
        return (0 until frame.dataContent.size / sampleSize).map { index ->
            val offset = index * sampleSize
            PolarGnssSatelliteDilutionSample(
                timeStamp = timeStamps[index],
                dilution = frame.dataContent.readUnsignedLong(offset, 2).toFloat() / 100f,
                altitude = frame.dataContent.readSignedInt(offset + 2, 2),
                numberOfSatellites = frame.dataContent.readUnsignedLong(offset + 4, 1).toUInt(),
                fix = frame.dataContent[offset + 5].toInt() != 0
            )
        }
    }

    private fun parseGnssRawType2(frame: PolarPmdDataFrame): List<PolarGnssLocationSample> {
        val sampleSize = 41
        if (frame.dataContent.size % sampleSize != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / sampleSize)
        return (0 until frame.dataContent.size / sampleSize).map { index ->
            val offset = index * sampleSize
            PolarGnssSatelliteSummarySample(
                timeStamp = timeStamps[index],
                seenGnssSatelliteSummaryBand1 = frame.dataContent.readGnssSatelliteSummary(offset),
                usedGnssSatelliteSummaryBand1 = frame.dataContent.readGnssSatelliteSummary(offset + 10),
                seenGnssSatelliteSummaryBand2 = frame.dataContent.readGnssSatelliteSummary(offset + 20),
                usedGnssSatelliteSummaryBand2 = frame.dataContent.readGnssSatelliteSummary(offset + 30),
                maxSnr = frame.dataContent.readUnsignedLong(offset + 40, 1).toUInt()
            )
        }
    }

    private fun parseGnssRawType3(frame: PolarPmdDataFrame): List<PolarGnssLocationSample> {
        val samples = mutableListOf<PolarGnssLocationSample>()
        var offset = 0
        while (offset < frame.dataContent.size) {
            if (offset + 7 > frame.dataContent.size) throw IllegalArgumentException("malformedFrame")
            val measurementPeriod = frame.dataContent.readUnsignedLong(offset, 4).toUInt()
            offset += 4
            val messageLength = frame.dataContent.readUnsignedLong(offset, 2).toUInt()
            offset += 2
            val statusFlags = frame.dataContent.readUnsignedLong(offset, 1).toUByte()
            offset += 1
            if (offset + messageLength.toInt() > frame.dataContent.size) throw IllegalArgumentException("malformedFrame")
            val nmeaMessage = frame.dataContent.copyOfRange(offset, offset + messageLength.toInt()).decodeToString()
            offset += messageLength.toInt()
            samples += PolarGnssNmeaSample(frame.timeStamp, measurementPeriod, messageLength, statusFlags, nmeaMessage)
        }
        return samples
    }

    private fun parseEcgRawType0(frame: PolarPmdDataFrame): List<PolarEcgSample> {
        val step = 3
        if (frame.dataContent.size % step != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / step)
        return (0 until frame.dataContent.size / step).map { index ->
            PolarEcgType0Sample(timeStamp = timeStamps[index], microVolts = frame.dataContent.readSignedInt(index * step, step))
        }
    }

    private fun parseEcgRawType1(frame: PolarPmdDataFrame): List<PolarEcgSample> {
        val step = 3
        if (frame.dataContent.size % step != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / step)
        return (0 until frame.dataContent.size / step).map { index ->
            val offset = index * step
            PolarEcgType0Sample(
                timeStamp = timeStamps[index],
                microVolts = ((frame.dataContent[offset].toInt() and BYTE_MASK) or ((frame.dataContent[offset + 1].toInt() and 0x3F) shl 8)) and 0x3FFF,
                overSampling = (frame.dataContent[offset + 2].toInt() and 0x01) != 0,
                skinContactBit = ((frame.dataContent[offset + 2].toInt() and 0x06) shr 1).toByte(),
                contactImpedance = ((frame.dataContent[offset + 2].toInt() and 0x18) shr 3).toByte()
            )
        }
    }

    private fun parseEcgRawType2(frame: PolarPmdDataFrame): List<PolarEcgSample> {
        val step = 3
        if (frame.dataContent.size % step != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / step)
        return (0 until frame.dataContent.size / step).map { index ->
            val offset = index * step
            PolarEcgType0Sample(
                timeStamp = timeStamps[index],
                microVolts = (frame.dataContent[offset].toInt() and BYTE_MASK) or ((frame.dataContent[offset + 1].toInt() and BYTE_MASK) shl 8) or ((frame.dataContent[offset + 2].toInt() and 0x03) shl 16) and 0x3FFFFF,
                ecgDataTag = ((frame.dataContent[offset + 2].toInt() and 0x1C) shr 2).toByte(),
                paceDataTag = ((frame.dataContent[offset + 2].toInt() and 0xE0) shr 5).toByte()
            )
        }
    }

    private fun parseEcgRawType3(frame: PolarPmdDataFrame): List<PolarEcgSample> {
        val step = 7
        if (frame.dataContent.size % step != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / step)
        return (0 until frame.dataContent.size / step).map { index ->
            val offset = index * step
            PolarEcgType3Sample(
                timeStamp = timeStamps[index],
                data0 = frame.dataContent.readSignedInt(offset, 3),
                data1 = frame.dataContent.readSignedInt(offset + 3, 3),
                status = (frame.dataContent[offset + 6].toInt() and BYTE_MASK).toUByte()
            )
        }
    }

    private fun parseAccRaw(frame: PolarPmdDataFrame, step: Int): List<PolarAccSample> {
        val sampleSize = step * 3
        if (frame.dataContent.size % sampleSize != 0) throw IllegalArgumentException("malformedFrame")
        return (0 until frame.dataContent.size / sampleSize).map { index ->
            val offset = index * sampleSize
            PolarAccSample(0uL, frame.dataContent.readSignedInt(offset, step), frame.dataContent.readSignedInt(offset + step, step), frame.dataContent.readSignedInt(offset + step * 2, step))
        }
    }

    private fun parseAccCompressed(frame: PolarPmdDataFrame, factor: Float, resolutionBits: Int): List<PolarAccSample> {
        return parseDeltaFramesAll(frame.dataContent, channels = 3, resolution = resolutionBits, fieldEncoding = FieldEncoding.SIGNED_INT).map { sample ->
            PolarAccSample(0uL, (sample[0] * factor).toInt(), (sample[1] * factor).toInt(), (sample[2] * factor).toInt())
        }
    }

    private fun parsePpgRawType0(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val step = 3
        val sampleSize = step * 4
        if (frame.dataContent.size % sampleSize != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / sampleSize)
        return (0 until frame.dataContent.size / sampleSize).map { index ->
            var offset = index * sampleSize
            val samples = (0 until 4).map {
                val value = frame.dataContent.readSignedInt(offset, step)
                offset += step
                value
            }
            PolarPpgType0Sample(timeStamps[index], samples.subList(0, 3), samples[3])
        }
    }

    private fun parsePpgRawType4(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        return parseIntegrationGainSamples(frame, sampleSize = 36, numIntSize = 12, channelSize = 24) { timestamp, numInt, channel1, channel2 ->
            PolarPpgType4Sample(timestamp, numInt, channel1, channel2)
        }
    }

    private fun parsePpgRawType5(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val step = 4
        if (frame.dataContent.size % step != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / step)
        return (0 until frame.dataContent.size / step).map { index ->
            PolarPpgType5Sample(timeStamps[index], frame.dataContent.readUnsignedLong(index * step, step).toUInt())
        }
    }

    private fun parsePpgRawType6(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val step = 8
        if (frame.dataContent.size % step != 0) throw IllegalArgumentException("malformedFrame")
        val timeStamps = getTimeStamps(frame, frame.dataContent.size / step)
        return listOf(PolarPpgSportIdSample(timeStamps.first(), frame.dataContent.readUnsignedLong(0, step)))
    }

    private fun parsePpgRawType9(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        return parseIntegrationGainSamples(frame, sampleSize = 36, numIntSize = 12, channelSize = 24) { timestamp, numInt, channel1, channel2 ->
            PolarPpgType9Sample(timestamp, numInt, channel1, channel2)
        }
    }

    private fun parsePpgRawType14(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        return parseIntegrationGainSamples(frame, sampleSize = 3, numIntSize = 1, channelSize = 2) { timestamp, numInt, channel1, channel2 ->
            PolarPpgType14Sample(timestamp, numInt, channel1, channel2)
        }
    }

    private fun parsePpgCompressedType0(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val samples = parseDeltaFramesAll(frame.dataContent, channels = 4, resolution = 24, fieldEncoding = FieldEncoding.SIGNED_INT)
        val timeStamps = getTimeStamps(frame, samples.size)
        return samples.mapIndexed { index, sample ->
            PolarPpgType0Sample(timeStamps[index], sample.subList(0, 3).map { (it.toFloat() * frame.factor).toInt() }, (sample[3].toFloat() * frame.factor).toInt())
        }
    }

    private fun parsePpgCompressedType7(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val samples = parseDeltaFramesAll(frame.dataContent, channels = 17, resolution = 24, fieldEncoding = FieldEncoding.SIGNED_INT)
        val timeStamps = getTimeStamps(frame, samples.size)
        return samples.mapIndexed { index, sample ->
            PolarPpgType7Sample(timeStamps[index], sample.subList(0, 16).map { it.scaledInt(frame.factor) }, statusBits(sample[16] and 0xFFFFFF))
        }
    }

    private fun parsePpgCompressedType8(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val samples = parseDeltaFramesAll(frame.dataContent, channels = 25, resolution = 24, fieldEncoding = FieldEncoding.SIGNED_INT)
        val timeStamps = getTimeStamps(frame, samples.size)
        return samples.mapIndexed { index, sample ->
            PolarPpgType8Sample(timeStamps[index], sample.subList(0, 24).map { it.scaledInt(frame.factor) }, statusBits(sample[24] and 0xFFFFFF))
        }
    }

    private fun parsePpgCompressedType10(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val samples = parseDeltaFramesAll(frame.dataContent, channels = 21, resolution = 24, fieldEncoding = FieldEncoding.SIGNED_INT)
        val timeStamps = getTimeStamps(frame, samples.size)
        return samples.mapIndexed { index, sample ->
            PolarPpgType10Sample(
                timeStamp = timeStamps[index],
                redSamples = sample.subList(0, 8).map { it.scaledInt(frame.factor) },
                greenSamples = sample.subList(8, 14).map { it.scaledInt(frame.factor) },
                irSamples = sample.subList(14, 20).map { it.scaledInt(frame.factor) },
                statusBits = statusBits(sample.last(), minimumSize = 20)
            )
        }
    }

    private fun parsePpgCompressedType13(frame: PolarPmdDataFrame): List<PolarPpgSample> {
        val samples = parseDeltaFramesAll(frame.dataContent, channels = 3, resolution = 24, fieldEncoding = FieldEncoding.SIGNED_INT)
        val timeStamps = getTimeStamps(frame, samples.size)
        return samples.mapIndexed { index, sample ->
            PolarPpgType13Sample(timeStamps[index], sample.subList(0, 1).map { it.scaledInt(frame.factor) }, sample.subList(1, 2).map { it.scaledInt(frame.factor) }, statusBits(sample[2] and 0xFFFFFF))
        }
    }

    private fun parseFloatScalar(frame: PolarPmdDataFrame, rawName: String): List<Float> {
        if (frame.compressed) {
            if (frame.frameType != 0) throw IllegalArgumentException("unsupportedCompressedFrame")
            return parseDeltaFramesAll(frame.dataContent, channels = 1, resolution = 32, fieldEncoding = FieldEncoding.FLOAT_IEEE754).map { sample ->
                sample[0].floatScaled(frame.factor)
            }
        }
        if (frame.frameType != 0) throw IllegalArgumentException("unsupportedFrame")
        if (frame.dataContent.size % 4 != 0) throw IllegalArgumentException("malformedFrame")
        return (0 until frame.dataContent.size / 4).map { index -> frame.dataContent.readFloat(index * 4) }
    }
}

private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8

private enum class FieldEncoding {
    SIGNED_INT,
    FLOAT_IEEE754
}

private fun parseDeltaFramesAll(value: ByteArray, channels: Int, resolution: Int, fieldEncoding: FieldEncoding): List<List<Int>> {
    val referenceBytes = channels * ceil(resolution / 8.0).toInt()
    if (value.size < referenceBytes) throw IllegalArgumentException("malformedFrame")
    var offset = 0
    val referenceSamples = parseDeltaFrameRefSamples(value, channels, resolution, fieldEncoding)
    offset += referenceBytes
    val samples = mutableListOf(referenceSamples)
    while (offset < value.size) {
        if (offset + 2 > value.size) throw IllegalArgumentException("malformedFrame")
        val deltaSize = value[offset++].toInt() and BYTE_MASK
        val sampleCount = value[offset++].toInt() and BYTE_MASK
        val bitLength = sampleCount * deltaSize * channels
        val length = ceil(bitLength / 8.0).toInt()
        if (offset + length > value.size) throw IllegalArgumentException("malformedFrame")
        val deltaSamples = parseDeltaFrame(value.copyOfRange(offset, offset + length), channels, deltaSize, bitLength)
        deltaSamples.forEach { delta ->
            val lastSample = samples.last()
            samples += (0 until channels).map { index -> lastSample[index] + delta[index] }
        }
        offset += length
    }
    return samples
}

private fun parseDeltaFrameRefSamples(value: ByteArray, channels: Int, resolution: Int, fieldEncoding: FieldEncoding): List<Int> {
    val resolutionBytes = ceil(resolution / 8.0).toInt()
    return (0 until channels).map { channel ->
        val offset = channel * resolutionBytes
        when (fieldEncoding) {
            FieldEncoding.SIGNED_INT -> value.readSignedInt(offset, resolutionBytes)
            FieldEncoding.FLOAT_IEEE754 -> value.readUnsignedLong(offset, resolutionBytes).toInt()
        }
    }
}

private fun parseDeltaFrame(bytes: ByteArray, channels: Int, bitWidth: Int, totalBitLength: Int): List<List<Int>> {
    var offset = 0
    val samples = mutableListOf<List<Int>>()
    val signMask = (-1 shl (bitWidth - 1))
    while (offset < totalBitLength) {
        samples += (0 until channels).map {
            var value = 0
            for (bitIndex in 0 until bitWidth) {
                val absoluteBit = offset + bitIndex
                val byte = bytes[absoluteBit / BITS_PER_BYTE].toInt() and BYTE_MASK
                if ((byte and (1 shl (absoluteBit % BITS_PER_BYTE))) != 0) {
                    value = value or (1 shl bitIndex)
                }
            }
            offset += bitWidth
            if ((value and signMask) != 0) value or signMask else value
        }
    }
    return samples
}

private fun List<PolarAccSample>.withAccTimeStamps(frame: PolarPmdDataFrame): List<PolarAccSample> {
    val timestamps = getTimeStamps(frame, size)
    return mapIndexed { index, sample -> sample.copy(timeStamp = timestamps[index]) }
}

private fun List<PolarGyrSample>.withGyrTimeStamps(frame: PolarPmdDataFrame): List<PolarGyrSample> {
    val timestamps = getTimeStamps(frame, size)
    return mapIndexed { index, sample -> sample.copy(timeStamp = timestamps[index]) }
}

private fun List<PolarMagSample>.withMagTimeStamps(frame: PolarPmdDataFrame): List<PolarMagSample> {
    val timestamps = getTimeStamps(frame, size)
    return mapIndexed { index, sample -> sample.copy(timeStamp = timestamps[index]) }
}

private fun List<PolarPressureSample>.withPressureTimeStamps(frame: PolarPmdDataFrame): List<PolarPressureSample> {
    val timestamps = getTimeStamps(frame, size)
    return mapIndexed { index, sample -> sample.copy(timeStamp = timestamps[index]) }
}

private fun List<PolarTemperatureSample>.withTemperatureTimeStamps(frame: PolarPmdDataFrame): List<PolarTemperatureSample> {
    val timestamps = getTimeStamps(frame, size)
    return mapIndexed { index, sample -> sample.copy(timeStamp = timestamps[index]) }
}

private fun List<PolarSkinTemperatureSample>.withSkinTemperatureTimeStamps(frame: PolarPmdDataFrame): List<PolarSkinTemperatureSample> {
    val timestamps = getTimeStamps(frame, size)
    return mapIndexed { index, sample -> sample.copy(timeStamp = timestamps[index]) }
}

private fun getTimeStamps(frame: PolarPmdDataFrame, samplesSize: Int): List<ULong> {
    require(samplesSize > 0) { "malformedFrame" }
    return if (frame.previousTimeStamp == 0uL) {
        require(frame.sampleRate > 0) { "malformedFrame" }
        // Use integer nanosecond arithmetic to avoid Double precision loss.
        // step = 1_000_000_000 / sampleRate in nanoseconds (integer division).
        // The remainder is distributed one extra nanosecond per sample so that
        // the last sample lands exactly on frame.timeStamp.
        val stepNs = 1_000_000_000uL / frame.sampleRate.toULong()
        val remainder = 1_000_000_000uL % frame.sampleRate.toULong()
        require(frame.timeStamp >= stepNs * (samplesSize - 1).toULong()) { "malformedFrame" }
        val start = frame.timeStamp - stepNs * (samplesSize - 1).toULong()
        require(start > 0uL) { "malformedFrame" }
        List(samplesSize) { index ->
            // Distribute remainder: add 1 ns to the first `remainder` intervals.
            val extraNs = if (index.toULong() < remainder) index.toULong() else remainder
            start + stepNs * index.toULong() + extraNs
        }
    } else {
        require(frame.timeStamp > frame.previousTimeStamp) { "malformedFrame" }
        val span = frame.timeStamp - frame.previousTimeStamp
        val stepNs = span / samplesSize.toULong()
        val remainder = span % samplesSize.toULong()
        require(frame.timeStamp >= span) { "malformedFrame" }
        val start = frame.previousTimeStamp + stepNs
        require(start > 0uL) { "malformedFrame" }
        // Build samplesSize - 1 interpolated timestamps then append frame.timeStamp
        // so the last sample is always exactly aligned with the frame boundary.
        val result = MutableList(samplesSize - 1) { index ->
            val extraNs = if ((index + 1).toULong() <= remainder) index.toULong() else remainder
            start + stepNs * index.toULong() + extraNs
        }
        result += frame.timeStamp
        result
    }
}

private fun parseIntegrationGainSamples(frame: PolarPmdDataFrame, sampleSize: Int, numIntSize: Int, channelSize: Int, factory: (ULong, List<UInt>, List<UInt>, List<UInt>) -> PolarPpgSample): List<PolarPpgSample> {
    if (frame.dataContent.size % sampleSize != 0) throw IllegalArgumentException("malformedFrame")
    val timeStamps = getTimeStamps(frame, frame.dataContent.size / sampleSize)
    return (0 until frame.dataContent.size / sampleSize).map { index ->
        var offset = index * sampleSize
        val numInt = frame.dataContent.copyOfRange(offset, offset + numIntSize).map { it.toUByte().toUInt() }
        offset += numIntSize
        val gainBytes = frame.dataContent.copyOfRange(offset, offset + channelSize)
        val channel1 = gainBytes.toList().mapIndexedNotNull { idx, value -> if (idx % 2 == 0) (value.toInt() and 0x07).toUInt() else null }
        val channel2 = gainBytes.toList().mapIndexedNotNull { idx, value -> if (idx % 2 == 1) (value.toInt() and 0x07).toUInt() else null }
        factory(timeStamps[index], numInt, channel1, channel2)
    }
}

private fun statusBits(value: Int, minimumSize: Int = 0): List<Int> {
    val bits = value.toString(2).map { it.digitToInt() }.toMutableList()
    while (bits.size < minimumSize) {
        bits.add(0, 0)
    }
    return bits
}

private fun ByteArray.readSignedInt(offset: Int, size: Int): Int {
    if (offset < 0 || size <= 0 || offset + size > this.size || size > 4) throw IllegalArgumentException("malformedFrame")
    var value = 0
    for (index in 0 until size) {
        value = value or ((this[offset + index].toInt() and BYTE_MASK) shl (index * BITS_PER_BYTE))
    }
    val signBit = 1 shl (size * BITS_PER_BYTE - 1)
    val mask = -1 shl (size * BITS_PER_BYTE - 1)
    return if ((value and signBit) != 0) value or mask else value
}

private fun ByteArray.readUnsignedLong(offset: Int, size: Int): ULong {
    if (offset < 0 || size <= 0 || offset + size > this.size || size > 8) throw IllegalArgumentException("malformedFrame")
    var value = 0uL
    for (index in 0 until size) {
        value = value or ((this[offset + index].toULong() and 0xFFuL) shl (index * BITS_PER_BYTE))
    }
    return value
}

private fun ByteArray.readFloat(offset: Int): Float {
    return readUnsignedLong(offset, 4).toInt().let { Float.fromBits(it) }
}

private fun ByteArray.readDouble(offset: Int): Double {
    return readUnsignedLong(offset, 8).toLong().let { Double.fromBits(it) }
}

private fun ByteArray.readGnssSatelliteSummary(offset: Int): PolarGnssSatelliteSummary {
    if (offset < 0 || offset + 10 > size) throw IllegalArgumentException("malformedFrame")
    return PolarGnssSatelliteSummary(
        gpsNbrOfSat = this[offset].toUByte(),
        gpsMaxSnr = this[offset + 1].toUByte(),
        glonassNbrOfSat = this[offset + 2].toUByte(),
        glonassMaxSnr = this[offset + 3].toUByte(),
        galileoNbrOfSat = this[offset + 4].toUByte(),
        galileoMaxSnr = this[offset + 5].toUByte(),
        beidouNbrOfSat = this[offset + 6].toUByte(),
        beidouMaxSnr = this[offset + 7].toUByte(),
        nbrOfSat = this[offset + 8].toUByte(),
        snrTop5Avg = this[offset + 9].toUByte()
    )
}

private fun Int.padded(size: Int): String {
    return toString().padStart(size, '0')
}

private fun Int.scaled(factor: Float): Float {
    return if (factor != 1.0f) this * factor else toFloat()
}

private fun Int.scaledInt(factor: Float): Int {
    return if (factor != 1.0f) (toFloat() * factor).toInt() else this
}

private fun Int.floatScaled(factor: Float): Float {
    val value = Float.fromBits(this)
    return if (factor != 1.0f) value * factor else value
}
