//  Copyright © 2022 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class PpgData {
    let timeStamp: UInt64
    var samples: [PpgSample]
    
    protocol PpgSample {
        var timeStamp: UInt64! { get }
        var frameType: PmdDataFrameType! { get }
    }
    
    struct PpgDataFrameType0: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let ppgDataSamples: [Int32]!
        let ambientSample: Int32!
    }

    struct PpgDataFrameType4: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let ppgDataSamples: [Int32]!
    }
    
    struct PpgDataFrameType5: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let operationMode: UInt64!
    }
    
    struct PpgDataFrameType6: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let sportId: Int32!
    }
    
    struct PpgDataFrameType7: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let ppgDataSamples: [Int32]!
    }
    
    struct PpgDataFrameType8: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let ppgDataSamples: [Int32]!
        let statusBits: [Int8]?
    }
    
    struct PpgDataFrameType9: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let ppgDataSamples: [Int32]!
    }
    
    struct PpgDataFrameType10: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let greenSamples: [Int32]!
        let redSamples: [Int32]!
        let irSamples: [Int32]!
        let statusBits: [Int8]?
    }
    
    struct PpgDataFrameType13: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let ppgDataSamples: [Int32]!
        let statusBits: [Int8]?
    }
    
    struct PpgDataFrameType14: PpgSample {
        let timeStamp: UInt64!
        let frameType: PmdDataFrameType!
        let ppgDataSamples: [Int32]!
    }
    
    init(timeStamp: UInt64 = 0, samples: [PpgSample] = []) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 4
    private static let TYPE_4_NUM_INTS_SIZE = 12
    private static let TYPE_4_CHANNEL_0_AND_1_SIZE = 24
    private static let TYPE_4_SAMPLE_SIZE_IN_BYTES =
        TYPE_4_NUM_INTS_SIZE + TYPE_4_CHANNEL_0_AND_1_SIZE
    private static let TYPE_5_SAMPLE_SIZE_IN_BYTES = 4
    private static let TYPE_6_SAMPLE_SIZE_IN_BYTES: Int = 8
    private static let TYPE_7_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_7_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_7_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_7_CHANNELS_IN_SAMPLE: UInt8 = 17
    private static let TYPE_8_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_8_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_8_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_8_CHANNELS_IN_SAMPLE: UInt8 = 25
    private static let TYPE_9_NUM_INTS_SIZE = 12
    private static let TYPE_9_CHANNEL_0_AND_1_SIZE = 24
    private static let TYPE_9_SAMPLE_SIZE_IN_BYTES =
    TYPE_9_NUM_INTS_SIZE + TYPE_9_CHANNEL_0_AND_1_SIZE
    private static let TYPE_10_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_10_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_10_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_10_STATUS_SIZE: UInt8 = 20
    private static let TYPE_10_CHANNELS_IN_SAMPLE: UInt8 = 21
    
    private static let TYPE_13_SAMPLE_SIZE_IN_BYTES: UInt8 = 3
    private static let TYPE_13_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_13_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_13_CHANNELS_IN_SAMPLE: UInt8 = 3

    private static let TYPE_14_NUM_INTS_SIZE = 1
    private static let TYPE_14_CHANNEL_0_AND_1_SIZE = 2
    private static let TYPE_14_SAMPLE_SIZE_IN_BYTES =
        TYPE_14_NUM_INTS_SIZE + TYPE_14_CHANNEL_0_AND_1_SIZE
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> PpgData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            case PmdDataFrameType.type_7: return try dataFromCompressedType7(frame: frame)
            case PmdDataFrameType.type_8: return try dataFromCompressedType8(frame: frame)
            case PmdDataFrameType.type_10: return try dataFromCompressedType10(frame: frame)
            case PmdDataFrameType.type_13: return try dataFromCompressedType13(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by PPG data parser")
            }
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            case PmdDataFrameType.type_4: return try dataFromRawType4(frame: frame)
            case PmdDataFrameType.type_5: return try dataFromRawType5(frame: frame)
            case PmdDataFrameType.type_6: return try dataFromRawType6(frame: frame)
            case PmdDataFrameType.type_9: return try dataFromRawType9(frame: frame)
            case PmdDataFrameType.type_14: return try dataFromRawType14(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by PPG data parser")
            }
        }
    }

    private static func dataFromRawType0(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType0Data(frame: frame) {
            return sharedData
        }
        #endif
        var offset = 0
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step * TYPE_0_CHANNELS_IN_SAMPLE))
        
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)

        var timeStampIndex = 0
        var ppgSamples = [PpgSample]()
        while offset < frame.dataContent.count {
            let ppg0 = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let ppg1 = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let ppg2 = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            let ambient = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += Int(step)
            
            ppgSamples.append( PpgDataFrameType0( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: [ppg0, ppg1, ppg2], ambientSample: ambient))
            timeStampIndex += 1
        }
        
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType0Data(frame: PmdDataFrame) -> PpgData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_0,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.rawType0Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 3,
                  let timeStamp = UInt64(fields[0]),
                  let ambientSample = Int32(fields[2]) else {
                return nil
            }
            let ppgDataSamples = fields[1].split(separator: ";").compactMap { Int32($0) }
            guard ppgDataSamples.count == 3 else {
                return nil
            }
            return PpgDataFrameType0(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: ppgDataSamples, ambientSample: ambientSample)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
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
    
    private static func dataFromRawType4(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType4Data(frame: frame) {
            return sharedData
        }
        #endif
        
        let samplesSize = Int(Double(frame.dataContent.count) / Double(TYPE_4_SAMPLE_SIZE_IN_BYTES))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        var ppgSamples = [PpgSample]()
        var timeStampIndex = 0
        
        var offset = 0
        while offset < frame.dataContent.count {
            let numIntTs =
            frame.dataContent[offset..<(offset + TYPE_4_NUM_INTS_SIZE)].map(Int32.init)
            
            offset += TYPE_9_NUM_INTS_SIZE
            var channel1GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_4_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 0) {
                    channel1GainTs.append(Int32(value & 0x07))
                }
            }
            
            var channel2GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_4_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 1) {
                    channel2GainTs.append(Int32(value & 0x07))
                }
            }
            offset += TYPE_4_CHANNEL_0_AND_1_SIZE

            ppgSamples.append( PpgDataFrameType4( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: numIntTs))
            ppgSamples.append( PpgDataFrameType4( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: channel1GainTs))
            ppgSamples.append( PpgDataFrameType4( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: channel2GainTs))
            timeStampIndex += 1
        }
        
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType4Data(frame: PmdDataFrame) -> PpgData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_4,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.rawType4Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.flatMap { row -> [PpgSample] in
            let fields = row.split(separator: ",")
            guard fields.count == 4,
                  let timeStamp = UInt64(fields[0]) else {
                return []
            }
            let numIntTs = fields[1].split(separator: ";").compactMap { Int32($0) }
            let channel1GainTs = fields[2].split(separator: ";").compactMap { Int32($0) }
            let channel2GainTs = fields[3].split(separator: ";").compactMap { Int32($0) }
            guard numIntTs.count == 12,
                  channel1GainTs.count == 12,
                  channel2GainTs.count == 12 else {
                return []
            }
            return [
                PpgDataFrameType4(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: numIntTs),
                PpgDataFrameType4(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: channel1GainTs),
                PpgDataFrameType4(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: channel2GainTs)
            ]
        }
        guard samples.count == rowValues.count * 3 else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
    
    private static func dataFromRawType5(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType5Data(frame: frame) {
            return sharedData
        }
        #endif
        let samplesSize = Int(Double(frame.dataContent.count) / Double(TYPE_0_CHANNELS_IN_SAMPLE))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        var ppgSamples = [PpgSample]()
        var timeStampIndex = 0
        var offset = 0

        while (offset < frame.dataContent.count) {
            let operationMode = TypeUtils.convertArrayToUnsignedInt64(frame.dataContent, offset: 0, size: offset + TYPE_5_SAMPLE_SIZE_IN_BYTES)
            offset += TYPE_5_SAMPLE_SIZE_IN_BYTES
            ppgSamples.append(PpgDataFrameType5(timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, operationMode: operationMode))
            timeStampIndex+=1
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }

    private static func dataFromRawType6(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType6Data(frame: frame) {
            return sharedData
        }
        #endif
        var ppgSamples = [PpgSample]()
        
        let sportId = TypeUtils.convertArrayToUnsignedInt64(frame.dataContent, offset: 0, size: TYPE_6_SAMPLE_SIZE_IN_BYTES)
        let samplesSize = frame.dataContent.count / TYPE_6_SAMPLE_SIZE_IN_BYTES
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        ppgSamples.append(PpgDataFrameType6( timeStamp: timeStamps.first!, frameType: frame.frameType, sportId: Int32(sportId)))
        
        return PpgData(timeStamp: timeStamps.first!, samples: ppgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType5Data(frame: PmdDataFrame) -> PpgData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_5,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.rawType5Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 2,
                  let timeStamp = UInt64(fields[0]),
                  let operationMode = UInt64(fields[1]) else {
                return nil
            }
            return PpgDataFrameType5(timeStamp: timeStamp, frameType: frame.frameType, operationMode: operationMode)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }

    private static func sharedRawType6Data(frame: PmdDataFrame) -> PpgData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_6,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.rawType6Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 2,
                  let timeStamp = UInt64(fields[0]),
                  let sportId = Int32(fields[1]) else {
                return nil
            }
            return PpgDataFrameType6(timeStamp: timeStamp, frameType: frame.frameType, sportId: sportId)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
    
    private static func dataFromRawType9(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let shared = sharedRawType9Data(frame: frame) {
            return shared
        }
        #endif
        
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step * TYPE_0_CHANNELS_IN_SAMPLE))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        var ppgSamples = [PpgSample]()
        var timeStampIndex = 0
        
        var offset = 0
        while offset < frame.dataContent.count {
            let numIntTs =
            frame.dataContent[offset..<(offset + TYPE_9_NUM_INTS_SIZE)].map(Int32.init)
            
            offset += TYPE_9_NUM_INTS_SIZE
            var channel1GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_9_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 0) {
                    channel1GainTs.append(Int32(value & 0x07))
                }
            }
            
            var channel2GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_9_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 1) {
                    channel2GainTs.append(Int32(value & 0x07))
                }
            }
            offset += TYPE_9_CHANNEL_0_AND_1_SIZE

            ppgSamples.append( PpgDataFrameType9( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: numIntTs))
            ppgSamples.append( PpgDataFrameType9( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: channel1GainTs))
            ppgSamples.append( PpgDataFrameType9( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: channel2GainTs))
            timeStampIndex += 1
        }
        
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType9Data(frame: PmdDataFrame) -> PpgData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_9,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.rawType9Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.flatMap { row -> [PpgSample] in
            let fields = row.split(separator: ",")
            guard fields.count == 4,
                  let timeStamp = UInt64(fields[0]) else {
                return []
            }
            let numIntTs = fields[1].split(separator: ";").compactMap { Int32($0) }
            let channel1GainTs = fields[2].split(separator: ";").compactMap { Int32($0) }
            let channel2GainTs = fields[3].split(separator: ";").compactMap { Int32($0) }
            guard numIntTs.count == 12,
                  channel1GainTs.count == 12,
                  channel2GainTs.count == 12 else {
                return []
            }
            return [
                PpgDataFrameType9(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: numIntTs),
                PpgDataFrameType9(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: channel1GainTs),
                PpgDataFrameType9(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: channel2GainTs)
            ]
        }
        guard samples.count == rowValues.count * 3 else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif

    private static func dataFromRawType14(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType14Data(frame: frame) {
            return sharedData
        }
        #endif

        let samplesSize = Int(Double(frame.dataContent.count) / Double(TYPE_14_SAMPLE_SIZE_IN_BYTES))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        var ppgSamples = [PpgSample]()
        var timeStampIndex = 0

        var offset = 0
        var channelSamples: [Int32] = []
        while offset < frame.dataContent.count {
            let numIntTs = frame.dataContent[offset..<(offset + TYPE_14_NUM_INTS_SIZE)].map(Int32.init)
            channelSamples.append(contentsOf: numIntTs)
            offset += TYPE_14_NUM_INTS_SIZE
            var channel1GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_14_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 0) {
                    channel1GainTs.append(Int32(value & 0x07))
                }
            }
            channelSamples.append(contentsOf: channel1GainTs)
            var channel2GainTs = [Int32]()
            for (index, value) in frame.dataContent[offset..<(offset + TYPE_14_CHANNEL_0_AND_1_SIZE)]
                .enumerated() {
                if (index % 2 == 1) {
                    channel2GainTs.append(Int32(value & 0x07))
                }
            }
            channelSamples.append(contentsOf: channel2GainTs)
            offset += TYPE_14_CHANNEL_0_AND_1_SIZE

            ppgSamples.append(PpgDataFrameType14( timeStamp: timeStamps[timeStampIndex], frameType: frame.frameType, ppgDataSamples: channelSamples))
            timeStampIndex += 1
        }

        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType14Data(frame: PmdDataFrame) -> PpgData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_14,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.rawType14Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 4,
                  let timeStamp = UInt64(fields[0]) else {
                return nil
            }
            let numIntTs = fields[1].split(separator: ";").compactMap { Int32($0) }
            let channel1GainTs = fields[2].split(separator: ";").compactMap { Int32($0) }
            let channel2GainTs = fields[3].split(separator: ";").compactMap { Int32($0) }
            guard !numIntTs.isEmpty,
                  !channel1GainTs.isEmpty,
                  !channel2GainTs.isEmpty else {
                return nil
            }
            return PpgDataFrameType14(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: numIntTs + channel1GainTs + channel2GainTs)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif

    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedCompressedType0Data(frame: frame) {
            return sharedData
        }
        #endif
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        
        var ppgSamples = [PpgSample]()
        for (index, sample) in samples.enumerated() {
            let ppg0:Int32 = sample[0]
            let ppg1:Int32 = sample[1]
            let ppg2:Int32 = sample[2]
            let ambient: Int32 = sample[3]
            ppgSamples.append( PpgDataFrameType0( timeStamp: timeStamps[index], frameType: frame.frameType, ppgDataSamples: [ppg0, ppg1, ppg2], ambientSample: ambient))
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedCompressedType0Data(frame: PmdDataFrame) -> PpgData? {
        guard frame.isCompressedFrame,
              frame.frameType == .type_0,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.compressedType0Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 3,
                  let timeStamp = UInt64(fields[0]),
                  let ambientSample = Int32(fields[2]) else {
                return nil
            }
            let ppgDataSamples = fields[1].split(separator: ";").compactMap { Int32($0) }
            guard ppgDataSamples.count == 3 else {
                return nil
            }
            return PpgDataFrameType0(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: ppgDataSamples, ambientSample: ambientSample)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
    
    private static func dataFromCompressedType7(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedCompressedType7Data(frame: frame) {
            return sharedData
        }
        #endif
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_7_CHANNELS_IN_SAMPLE, resolution: TYPE_7_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        var ppgSamplesFrameType7 = [PpgSample]()
        var statusBits = [Int8]()
        for (index, sample) in samples.enumerated() {
            let channelSamples = sample.map{ item in
                if (frame.factor != 1.0) {
                    return Int32(Float(item) * frame.factor)
                }
                else {
                    return item
                }
            }
            let _ = String(Int32(sample[16] & 0xFFFFFF), radix: 2).map(String.init).forEach { statusBits.append(Int8($0)!) }
            
            ppgSamplesFrameType7.append( PpgDataFrameType7( timeStamp: timeStamps[index], frameType: frame.frameType, ppgDataSamples: channelSamples))
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamplesFrameType7)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedCompressedType7Data(frame: PmdDataFrame) -> PpgData? {
        guard frame.isCompressedFrame,
              frame.frameType == .type_7,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.compressedType7Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 2,
                  let timeStamp = UInt64(fields[0]) else {
                return nil
            }
            let ppgDataSamples = fields[1].split(separator: ";").compactMap { Int32($0) }
            guard ppgDataSamples.count == 17 else {
                return nil
            }
            return PpgDataFrameType7(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: ppgDataSamples)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
    
    private static func dataFromCompressedType8(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedCompressedType8Data(frame: frame) {
            return sharedData
        }
        #endif
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_8_CHANNELS_IN_SAMPLE, resolution: TYPE_8_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        var ppgSamplesFrameType8 = [PpgSample]()
        var statusBits = [Int8]()
        
        for (index, sample) in samples.enumerated() {
            let channelSamples = sample[0..<24].map{ item in
                if (frame.factor != 1.0) {
                    return Int32(Float(item) * frame.factor)
                }
                else {
                    return item
                }
            }
            let _ = String(Int32(sample[24] & 0xFFFFFF), radix: 2).map(String.init).forEach { statusBits.append(Int8($0)!) }
            ppgSamplesFrameType8.append( PpgDataFrameType8( timeStamp: timeStamps[index], frameType: frame.frameType, ppgDataSamples: channelSamples, statusBits: statusBits))
        }

        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamplesFrameType8)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedCompressedType8Data(frame: PmdDataFrame) -> PpgData? {
        guard frame.isCompressedFrame,
              frame.frameType == .type_8,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.compressedType8Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 3,
                  let timeStamp = UInt64(fields[0]) else {
                return nil
            }
            let ppgDataSamples = fields[1].split(separator: ";").compactMap { Int32($0) }
            let statusBits = fields[2].split(separator: ";").compactMap { Int8($0) }
            guard ppgDataSamples.count == 24,
                  !statusBits.isEmpty else {
                return nil
            }
            return PpgDataFrameType8(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: ppgDataSamples, statusBits: statusBits)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
    
    private static func dataFromCompressedType10(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedCompressedType10Data(frame: frame) {
            return sharedData
        }
        #endif
        
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_10_CHANNELS_IN_SAMPLE, resolution: TYPE_10_SAMPLE_SIZE_IN_BITS)

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samples.count),
            sampleRate: frame.sampleRate
        )
        var ppgSamples = [PpgSample]()
        var timeStampIndex = 0
        var statusBits = [Int8]()
        for (index, sample) in samples.enumerated() {
            
            let greenSamples = sample[0..<8].map { sample in
                if (frame.factor != Float(1.0)) {
                    Int32((Float(sample) * frame.factor))
                } else {
                    sample
                }
            }
            
            let redSamples = sample[8..<14].map { sample in
                if (frame.factor != Float(1.0)) {
                    Int32((Float(sample) * frame.factor))
                } else {
                    sample
                }
            }
            
            let irSamples = sample[14..<20].map { sample in
                if (frame.factor != Float(1.0)) {
                    Int32((Float(sample) * frame.factor))
                } else {
                    sample
                }
            }

            let _ = String(Int32(sample[sample.endIndex - 1]), radix: 2).map(String.init).forEach { statusBits.append(Int8($0)!) }
            
            // Frame type10 status bits are expected to be 20-bit of length but may come in
            // with less bits (e.g. 18-bit status data) as wrist units can omit MSB zero bits.
            // We will append the missing bits here.
            while statusBits.count < 20 {
                statusBits.insert(0, at: 0)
            }

            let ppgSample = PpgDataFrameType10(timeStamp: timeStamps[index], frameType: frame.frameType, greenSamples: greenSamples, redSamples: redSamples, irSamples: irSamples, statusBits: statusBits)
            
            ppgSamples.append(ppgSample)
            timeStampIndex+=1
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedCompressedType10Data(frame: PmdDataFrame) -> PpgData? {
        guard frame.isCompressedFrame,
              frame.frameType == .type_10,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.compressedType10IosSamples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 5,
                  let timeStamp = UInt64(fields[0]) else {
                return nil
            }
            let greenSamples = fields[1].split(separator: ";").compactMap { Int32($0) }
            let redSamples = fields[2].split(separator: ";").compactMap { Int32($0) }
            let irSamples = fields[3].split(separator: ";").compactMap { Int32($0) }
            let statusBits = fields[4].split(separator: ";").compactMap { Int8($0) }
            guard greenSamples.count == 8,
                  redSamples.count == 6,
                  irSamples.count == 6,
                  statusBits.count == 20 else {
                return nil
            }
            return PpgDataFrameType10(timeStamp: timeStamp, frameType: frame.frameType, greenSamples: greenSamples, redSamples: redSamples, irSamples: irSamples, statusBits: statusBits)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
    
    private static func dataFromCompressedType13(frame: PmdDataFrame) throws -> PpgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedCompressedType13Data(frame: frame) {
            return sharedData
        }
        #endif

        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_13_CHANNELS_IN_SAMPLE, resolution: TYPE_13_SAMPLE_SIZE_IN_BITS)

        let timeStamps = try PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp: frame.previousTimeStamp,
            frameTimeStamp: frame.timeStamp,
            samplesSize: UInt(samples.count),
            sampleRate: frame.sampleRate
        )

        var ppgSamplesFrameType13 = [PpgSample]()
        var statusBits = [Int8]()

        for (index, sample) in samples.enumerated() {
            let channelSamples = sample[0..<2].map{ item in
                if (frame.factor != 1.0) {
                    return Int32(Float(item) * frame.factor)
                }
                else {
                    return item
                }
            }
            let _ = String(Int32(sample[2] & 0xFFFFFF), radix: 2).map(String.init).forEach { statusBits.append(Int8($0)!) }
            ppgSamplesFrameType13.append( PpgDataFrameType13( timeStamp: timeStamps[index], frameType: frame.frameType, ppgDataSamples: channelSamples, statusBits: statusBits))
        }
        return PpgData(timeStamp: frame.timeStamp, samples: ppgSamplesFrameType13)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedCompressedType13Data(frame: PmdDataFrame) -> PpgData? {
        guard frame.isCompressedFrame,
              frame.frameType == .type_13,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PpgDataRuntimePlanner.compressedType13Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 4,
                  let timeStamp = UInt64(fields[0]) else {
                return nil
            }
            let channel0 = fields[1].split(separator: ";").compactMap { Int32($0) }
            let channel1 = fields[2].split(separator: ";").compactMap { Int32($0) }
            let statusBits = fields[3].split(separator: ";").compactMap { Int8($0) }
            guard !channel0.isEmpty,
                  !channel1.isEmpty,
                  !statusBits.isEmpty else {
                return nil
            }
            return PpgDataFrameType13(timeStamp: timeStamp, frameType: frame.frameType, ppgDataSamples: channel0 + channel1, statusBits: statusBits)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
}

enum PpgDataRuntimePlanner {
    static func rawType0Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgRawType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func rawType4Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgRawType4Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func rawType5Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgRawType5Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func rawType6Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgRawType6Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func rawType9Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        // Raw type 9 currently has platform-specific timestamp semantics in the golden vectors.
        return nil
    }

    static func rawType14Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgRawType14Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func compressedType0Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgCompressedType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func compressedType7Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgCompressedType7Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func compressedType8Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgCompressedType8Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func compressedType10IosSamples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgCompressedType10IosSamples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }

    static func compressedType13Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.ppgCompressedType13Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }
}
