//  Copyright © 2023 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class TemperatureData {

    struct TemperatureSample {
        let timeStamp: UInt64
        let temperature: Float
    }

    var samples: [TemperatureSample]

    init(samples: [TemperatureSample] = []) {
        self.samples = samples
    }

    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 1

    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> TemperatureData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Temperature data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Temperature data parser")
            }
        }
    }

    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> TemperatureData {

        let temperatureData = TemperatureData()
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)

        var temperatureSamples = [TemperatureSample]()

        for (index, sample) in samples.enumerated() {
            let temperature = Float.init(bitPattern: UInt32(sample.first!))
            temperatureSamples.append(TemperatureSample(timeStamp: timeStamps[index], temperature: temperature))
        }
        temperatureData.samples = temperatureSamples
        return temperatureData
    }

    private static func dataFromRawType0(frame: PmdDataFrame) throws -> TemperatureData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType0Data(frame: frame) {
            return sharedData
        }
        #endif

        let temperatureData = TemperatureData()
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step))
        var offset = 0
        var timeStampIndex = 0

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
    
        while (offset < frame.dataContent.count) {
            temperatureData.samples.append(TemperatureSample(timeStamp: timeStamps[timeStampIndex], temperature: Float(bitPattern: UInt32( frame.dataContent[offset ..< (offset + Int(TYPE_0_SAMPLE_SIZE_IN_BYTES))].withUnsafeBytes { $0.load(as: UInt32.self) }))))
            offset += Int(step)
            timeStampIndex += 1
        }
        return temperatureData
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType0Data(frame: PmdDataFrame) -> TemperatureData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_0,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = TemperatureDataRuntimePlanner.rawType0Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> TemperatureSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 2,
                  let timeStamp = UInt64(fields[0]),
                  let temperature = Float(fields[1]) else {
                return nil
            }
            return TemperatureSample(timeStamp: timeStamp, temperature: temperature)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return TemperatureData(samples: samples)
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

enum TemperatureDataRuntimePlanner {
    static func rawType0Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.temperatureRawType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }
}
