//  Copyright © 2022 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class PpiData {
    
    struct PpiSample {
        var timeStamp: UInt64
        let hr: Int
        let ppInMs: UInt16
        let ppErrorEstimate: UInt16
        let blockerBit: Int
        let skinContactStatus: Int
        let skinContactSupported: Int
    }
    
    var samples: [PpiSample]
    
    init(samples: [PpiSample] = []) {
        self.samples = samples
    }
    
    private static let PPI_SAMPLE_CHUNK = 6
    
    static func parseDataFromDataFrame(frame: PmdDataFrame) throws -> PpiData {
        if (frame.isCompressedFrame) {
            throw BleGattException.gattDataError(description: "Compressed FrameType: \(frame.frameType) is not supported by PPI data parser")
        } else {
            switch (frame.frameType) {
            case PmdDataFrameType.type_0: return try dataFromRawType0(frame: frame)
            default: throw BleGattException.gattDataError(description: "Raw FrameType: \(frame.frameType) is not supported by PPI data parser")
            }
        }
    }
    
    private static func dataFromRawType0(frame: PmdDataFrame) throws -> PpiData {
        #if canImport(PolarBleSdkShared)
        if let sharedData = sharedRawType0Data(frame: frame) {
            return sharedData
        }
        #endif
        let data = PpiData(samples: stride(from: 0, to: frame.dataContent.count, by: PPI_SAMPLE_CHUNK)
            .map { (start) -> Data in
                return frame.dataContent.subdata(in: start..<start.advanced(by: PPI_SAMPLE_CHUNK))
            }
            .map { (data) -> PpiSample in
                let hr = Int(data[0])
                let ppInMs = UInt16(UInt16(data[2]) << 8 | UInt16(data[1]))
                let ppErrorEstimate = UInt16(UInt16(data[4]) << 8 | UInt16(data[3]))
                let blockerBit = Int(data[5]) & 0x01
                let skinContactStatus = (Int(data[5]) & 0x02) >> 1
                let skinContactSupported = (Int(data[5]) & 0x04) >> 2
                return PpiSample(
                    timeStamp: 0, // time stamp will set below
                    hr: hr,
                    ppInMs: ppInMs,
                    ppErrorEstimate: ppErrorEstimate,
                    blockerBit: blockerBit,
                    skinContactStatus: skinContactStatus,
                    skinContactSupported: skinContactSupported)
            }
        )

        if (frame.timeStamp != 0) {
            var currentTimeStamp: UInt64 = frame.timeStamp
            var currentIndex = data.samples.count - 1
            for (sample) in data.samples.reversed() {
                data.samples[currentIndex].timeStamp = currentTimeStamp
                currentIndex -= 1
                currentTimeStamp -= UInt64(sample.ppInMs) * 1000000
            }
        }

        return data
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRawType0Data(frame: PmdDataFrame) -> PpiData? {
        guard !frame.isCompressedFrame,
              frame.frameType == .type_0,
              frame.previousTimeStamp <= UInt64(Int64.max),
              frame.sampleRate <= UInt(Int32.max) else {
            return nil
        }
        guard let sharedRows = PolarIosSharedBridge.shared.ppiRawType0Samples(
            dataFrameHex: sharedDataFrameHex(frame: frame),
            previousTimeStamp: Int64(frame.previousTimeStamp),
            factor: frame.factor,
            sampleRate: Int32(frame.sampleRate)
        ), !sharedRows.isEmpty else {
            return nil
        }
        let rowValues = sharedRows.split(separator: "|")
        let samples = rowValues.compactMap { row -> PpiSample? in
            let fields = row.split(separator: ",")
            guard fields.count == 7,
                  let timeStamp = UInt64(fields[0]),
                  let hr = Int(fields[1]),
                  let ppInMs = UInt16(fields[2]),
                  let ppErrorEstimate = UInt16(fields[3]),
                  let blockerBit = Int(fields[4]),
                  let skinContactStatus = Int(fields[5]),
                  let skinContactSupported = Int(fields[6]) else {
                return nil
            }
            return PpiSample(timeStamp: timeStamp, hr: hr, ppInMs: ppInMs, ppErrorEstimate: ppErrorEstimate, blockerBit: blockerBit, skinContactStatus: skinContactStatus, skinContactSupported: skinContactSupported)
        }
        guard samples.count == rowValues.count else {
            return nil
        }
        return PpiData(samples: samples)
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
