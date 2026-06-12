//  Copyright © 2022 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class GyrData {
    let timeStamp: UInt64
    
    struct GyrSample {
        let timeStamp: UInt64
        let x: Float
        let y: Float
        let z: Float
    }
    
    var samples: [GyrSample]
    
    init(timeStamp: UInt64 = 0, samples: [GyrSample] = []) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let TYPE_0_SAMPLE_SIZE_IN_BYTES : UInt8 = 2
    private static let TYPE_0_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_0_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_0_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    private static let TYPE_1_SAMPLE_SIZE_IN_BYTES: UInt8 = 4
    private static let TYPE_1_SAMPLE_SIZE_IN_BITS: UInt8 = TYPE_1_SAMPLE_SIZE_IN_BYTES * 8
    private static let TYPE_1_CHANNELS_IN_SAMPLE: UInt8 = 3
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> GyrData {
        if (frame.isCompressedFrame) {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromCompressedType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by Gyro data parser")
            }
        } else {
            throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Gyro data parser")
        }
    }
    
    private static func dataFromCompressedType0(frame: PmdDataFrame) throws -> GyrData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedCompressedType0Data(frame: frame) {
            return sharedData
        }
        #endif
        let samples = Pmd.parseDeltaFramesToSamples(frame.dataContent, channels: TYPE_0_CHANNELS_IN_SAMPLE, resolution: TYPE_0_SAMPLE_SIZE_IN_BITS)
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samples.count), sampleRate: frame.sampleRate)
        
        var gyrSamples = [GyrSample]()
        for (index, sample) in samples.enumerated() {
            gyrSamples.append( GyrSample( timeStamp: timeStamps[index],
                                          x: (Float(sample[0]) * frame.factor),
                                          y: (Float(sample[1]) * frame.factor),
                                          z: (Float(sample[2]) * frame.factor))
            )
        }
        return GyrData(timeStamp: frame.timeStamp, samples: gyrSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedCompressedType0Data(frame: PmdDataFrame) -> GyrData? {
        guard frame.frameType == .type_0,
              frame.isCompressedFrame,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = GyrDataRuntimePlanner.compressedType0Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> GyrSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 4,
                  let timeStamp = UInt64(fields[0]),
                  let x = Float(fields[1]),
                  let y = Float(fields[2]),
                  let z = Float(fields[3]) else {
                return nil
            }
            return GyrSample(timeStamp: timeStamp, x: x, y: y, z: z)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return GyrData(timeStamp: frame.timeStamp, samples: samples)
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

enum GyrDataRuntimePlanner {
    static func compressedType0Samples(dataFrameHex: String, previousTimeStamp: Int64, factor: Float, sampleRate: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.gyrCompressedType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: previousTimeStamp, factor: factor, sampleRate: sampleRate)
        #else
        return nil
        #endif
    }
}
