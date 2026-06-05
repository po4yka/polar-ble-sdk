//  Copyright © 2023 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class OfflineHrData {
    
    struct OfflineHrSample {
        let hr: UInt8
        let ppgQuality: UInt8
        let correctedHr: UInt8
    }
    
    var samples: [OfflineHrSample]
    
    init(samples: [OfflineHrSample] = []) {
        self.samples = samples
    }
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> OfflineHrData {
        if (frame.isCompressedFrame) {
            throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by Offline HR data parser")
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            case PmdDataFrameType.type_1: return try dataFromRawType1(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by Offline HR data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> OfflineHrData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawData(frame: frame) {
            return sharedData
        }
        #endif
        let offlineHrData = OfflineHrData()
        var offset = 0
        while (offset < frame.dataContent.count) {
            offlineHrData.samples.append(OfflineHrSample(hr: UInt8(frame.dataContent[offset]), ppgQuality: 0, correctedHr: 0))
            offset += 1
        }
        return offlineHrData
    }

    private static func dataFromRawType1(frame: PmdDataFrame) throws -> OfflineHrData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawData(frame: frame) {
            return sharedData
        }
        #endif
        let offlineHrData = OfflineHrData()
        var offset = 0
        while (offset < frame.dataContent.count) {
            let hr = UInt8(frame.dataContent[offset])
            offset += 1
            let ppgQual = UInt8(frame.dataContent[offset])
            offset += 1
            let correctedHr = UInt8(frame.dataContent[offset])
            offlineHrData.samples.append(OfflineHrSample(hr: hr, ppgQuality: ppgQual, correctedHr: correctedHr))
            offset += 1
        }
        return offlineHrData
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawData(frame: PmdDataFrame) -> OfflineHrData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_0 || frame.frameType == .type_1,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PolarIosSharedBridge.shared.offlineHrRawSamples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty || frame.dataContent.isEmpty else {
            return nil
        }
        if sharedRows.isEmpty {
            return OfflineHrData()
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> OfflineHrSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 3,
                  let hr = UInt8(fields[0]),
                  let ppgQuality = UInt8(fields[1]),
                  let correctedHr = UInt8(fields[2]) else {
                return nil
            }
            return OfflineHrSample(hr: hr, ppgQuality: ppgQuality, correctedHr: correctedHr)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return OfflineHrData(samples: samples)
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
