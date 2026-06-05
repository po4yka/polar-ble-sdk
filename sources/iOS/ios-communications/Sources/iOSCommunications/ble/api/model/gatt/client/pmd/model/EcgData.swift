//  Copyright © 2022 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class EcgData {
    let timeStamp: UInt64
    
    struct EcgSample {
        let timeStamp: UInt64
        let microVolts: Int32
    }
    
    var samples: [EcgSample]
 
    init(timeStamp: UInt64 = 0, samples: [EcgSample] = [EcgSample]()) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
    
    private static let  TYPE_0_SAMPLE_SIZE_IN_BYTES = 3
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> EcgData {
        if (frame.isCompressedFrame) {
            throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by ECG data parser")
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by ECG data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> EcgData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType0Data(frame: frame) {
            return sharedData
        }
        #endif
        var offset = 0
        let step = TYPE_0_SAMPLE_SIZE_IN_BYTES
        let samplesSize = Int(Double(frame.dataContent.count) / Double(step))
        let timeStamps = try PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp: frame.previousTimeStamp, frameTimeStamp: frame.timeStamp, samplesSize: UInt(samplesSize), sampleRate: frame.sampleRate)
        
        var timeStampIndex = 0
        var ecgSamples = [EcgSample]()
        while (offset < frame.dataContent.count) {
            let voltage = TypeUtils.convertArrayToSignedInt(frame.dataContent, offset: offset, size: Int(step))
            offset += step
            ecgSamples.append(EcgSample(timeStamp: timeStamps[timeStampIndex], microVolts: voltage))
            timeStampIndex += 1
        }
        return EcgData(timeStamp: frame.timeStamp, samples: ecgSamples)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType0Data(frame: PmdDataFrame) -> EcgData? {
        guard frame.frameType == .type_0,
              !frame.isCompressedFrame,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PolarIosSharedBridge.shared.ecgType0Samples(
            dataFrameHex: frame.sharedDataFrameHex,
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let samples = sharedRows.split(separator: "|").compactMap { row -> EcgSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 2,
                  let timeStamp = UInt64(fields[0]),
                  let microVolts = Int32(fields[1]) else {
                return nil
            }
            return EcgSample(timeStamp: timeStamp, microVolts: microVolts)
        }
        guard samples.count == sharedRows.split(separator: "|").count else {
            return nil
        }
        return EcgData(timeStamp: frame.timeStamp, samples: samples)
    }
    #endif
}

#if canImport(PolarBleSdkShared)
private extension PmdDataFrame {
    var sharedDataFrameHex: String {
        var data = Data([measurementType.rawValue])
        var littleEndianTimestamp = timeStamp.littleEndian
        withUnsafeBytes(of: &littleEndianTimestamp) { data.append(contentsOf: $0) }
        let frameTypeByte = frameType.rawValue | (isCompressedFrame ? 0x80 : 0)
        data.append(frameTypeByte)
        data.append(dataContent)
        return data.map { String(format: "%02x", $0) }.joined()
    }
}
#endif
