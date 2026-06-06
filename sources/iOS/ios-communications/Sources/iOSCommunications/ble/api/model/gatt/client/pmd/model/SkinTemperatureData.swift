//  Copyright © 2024 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class SkinTemperatureData {

    struct SkinTemperatureSample {
        let timeStamp: UInt64
        let skinTemperature: Float
        let isTimestampEstimated: Bool

        init(timeStamp: UInt64, skinTemperature: Float, isTimestampEstimated: Bool = false) {
            self.timeStamp = timeStamp
            self.skinTemperature = skinTemperature
            self.isTimestampEstimated = isTimestampEstimated
        }
    }

    var samples: [SkinTemperatureSample]

    init(samples: [SkinTemperatureSample] = []) {
        self.samples = samples
    }

    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 1
    private static let DEFAULT_SKIN_TEMP_SAMPLE_RATE: UInt = 4

    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> SkinTemperatureData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by SkinTemperature data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by SkinTemperature data parser")
            }
        }
    }

    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> SkinTemperatureData {

        let skinTemperatureData = SkinTemperatureData()
        let samples = Pmd.parseDeltaFramesToSamples(
            frame.dataContent,
            channels: TYPE_0_CHANNELS_IN_SAMPLE,
            resolution: TYPE_0_SAMPLE_SIZE_IN_BITS
        )

        if samples.isEmpty {
            return skinTemperatureData
        }

        let sampleRate: UInt
        let isTimestampEstimated: Bool
        if frame.sampleRate > 0 {
            sampleRate = frame.sampleRate
            isTimestampEstimated = false
        } else {
            sampleRate = DEFAULT_SKIN_TEMP_SAMPLE_RATE
            isTimestampEstimated = true
            BleLogger.trace("SkinTemperatureData sampleRate was 0, using default \(DEFAULT_SKIN_TEMP_SAMPLE_RATE)")
        }

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samples.count),
            sampleRate: sampleRate
        )

        var skinTemperatureSamples = [SkinTemperatureSample]()

        for (index, sample) in samples.enumerated() {
            let skinTemperature = Float(bitPattern: UInt32(sample.first!))
            skinTemperatureSamples.append(
                SkinTemperatureSample(
                    timeStamp: timeStamps[index],
                    skinTemperature: skinTemperature,
                    isTimestampEstimated: isTimestampEstimated
                )
            )
        }

        skinTemperatureData.samples = skinTemperatureSamples
        return skinTemperatureData
    }

    private static func dataFromRawType0(frame: PmdDataFrame) throws -> SkinTemperatureData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType0Data(frame: frame) {
            return sharedData
        }
        #endif

        let skinTemperatureData = SkinTemperatureData()
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step))

        if samplesSize == 0 {
            return skinTemperatureData
        }

        let sampleRate: UInt
        let isTimestampEstimated: Bool
        if frame.sampleRate > 0 {
            sampleRate = frame.sampleRate
            isTimestampEstimated = false
        } else {
            sampleRate = DEFAULT_SKIN_TEMP_SAMPLE_RATE
            isTimestampEstimated = true
            BleLogger.trace("SkinTemperatureData sampleRate was 0, using default \(DEFAULT_SKIN_TEMP_SAMPLE_RATE)")
        }

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samplesSize),
            sampleRate: sampleRate
        )

        var offset = 0
        var timeStampIndex = 0

        while (offset + Int(step) <= frame.dataContent.count) {
            skinTemperatureData.samples.append(
                SkinTemperatureSample(
                    timeStamp: timeStamps[timeStampIndex],
                    skinTemperature: Float(
                        bitPattern: UInt32(
                            frame.dataContent[offset ..< (offset + Int(TYPE_0_SAMPLE_SIZE_IN_BYTES))]
                                .withUnsafeBytes { $0.load(as: UInt32.self) }
                        )
                    ),
                    isTimestampEstimated: isTimestampEstimated
                )
            )
            offset += Int(step)
            timeStampIndex += 1
        }

        return skinTemperatureData
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType0Data(frame: PmdDataFrame) -> SkinTemperatureData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_0,
              frame.sampleRate > 0,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = SkinTemperatureDataRuntimePlanner.rawType0Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> SkinTemperatureSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 2,
                  let timeStamp = UInt64(fields[0]),
                  let skinTemperature = Float(fields[1]) else {
                return nil
            }
            return SkinTemperatureSample(timeStamp: timeStamp, skinTemperature: skinTemperature)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return SkinTemperatureData(samples: samples)
    }

    private static func sharedDataFrameHex(frame: PmdDataFrame) -> String {
        var data = Data([frame.measurementType.rawValue])
        var littleEndianTimestamp = frame.timeStamp.littleEndian
        withUnsafeBytes(of: &littleEndianTimestamp) { data.append(contentsOf: $0) }
        let frameTypeByte = frame.frameType.rawValue | (frame.isCompressedFrame ? 0x80 : 0)
        data.append(frameTypeByte)
        data.append(frame.dataContent)
        return data.map { String(format: "%02x", $0) }.joined()
    }
    #endif
}

enum SkinTemperatureDataRuntimePlanner {
    static func rawType0Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.skinTemperatureRawType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }
}
