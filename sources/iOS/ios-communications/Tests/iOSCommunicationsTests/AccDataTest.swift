//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class AccDataTest: XCTestCase {
    
    func testProcessAccRawDataFrameType1() throws {
        // Arrange
        // HEX: 02 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           02 (ACC)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     01
        
        let accDataFrameHeader = Data([
            0x02,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x01
        ])
        
        // HEX: 01 F7 FF FF FF E7 03 F8 FF FE FF E5 03 F9 FF FF FF E5 03 FA FF FF FF E6 03 FA FF FE FF E6 03 F9 FF FF FF E5 03 F8 FF FF FF E6 03 F8 FF FE FF E6 03 FA FF FF FF E5 03 FA FF FF FF E7 03 FA FF FF FF E5 03 F8 FF FF FF E6 03 F7 FF FF FF E6 03 F8 FF FE FF E6 03 F9 FF FE FF E7 03 F9 FF 00 00 E6 03 F9 FF FF FF E6 03 F7 FF FE FF E5 03 F9 FF FF FF E5 03 F9 FF FF FF E5 03 FA FF 00 00 E6 03 F9 FF FE FF E6 03 F8 FF FF FF E6 03 F8 FF FF FF E5 03 F9 FF FF FF E6 03 F9 FF FF FF E5 03 FA FF FF FF E6 03 F9 FF FF FF E5 03 F9 FF FF FF E5 03 F8 FF FE FF E6 03 F9 FF FF FF E6 03 F9 FF FF FF E6 03 F9 FF 00 00 E5 03 F9 FF FE FF E6 03 F8 FF FE FF E6 03 F7 FF FE FF E6 03
        // index                                                   data:
        // 0        frame type                                     01
        // 1..2     x value                                        F7 FF (-9)
        let xValue1:Int32 = -9
        // 3..4     y value                                        FF FF (-1)
        let yValue1:Int32 = -1
        // 5..6     z value                                        E7 03 (999)
        let zValue1:Int32 = 999
        // 7..8     x value                                        F8 FF (-8)
        let xValue2:Int32 = -8
        // 9..10    y value                                        FF FE (-2)
        let yValue2:Int32 = -2
        // 11..12   z value                                        E5 03 (997)
        let zValue2:Int32 = 997
        let accDataFrameContent = Data([
            0xF7, 0xFF, 0xFF, 0xFF, 0xE7, 0x03, 0xF8, 0xFF, 0xFE, 0xFF,
            0xE5, 0x03, 0xF9, 0xFF, 0xFF, 0xFF, 0xE5, 0x03, 0xFA, 0xFF,
            0xFF, 0xFF, 0xE6, 0x03, 0xFA, 0xFF, 0xFE, 0xFF, 0xE6, 0x03,
            0xF9, 0xFF, 0xFF, 0xFF, 0xE5, 0x03, 0xF8, 0xFF, 0xFF, 0xFF,
            0xE6, 0x03, 0xF8, 0xFF, 0xFE, 0xFF, 0xE6, 0x03, 0xFA, 0xFF,
            0xFF, 0xFF, 0xE5, 0x03, 0xFA, 0xFF, 0xFF, 0xFF, 0xE7, 0x03,
            0xFA, 0xFF, 0xFF, 0xFF, 0xE5, 0x03, 0xF8, 0xFF, 0xFF, 0xFF,
            0xE6, 0x03, 0xF7, 0xFF, 0xFF, 0xFF, 0xE6, 0x03, 0xF8, 0xFF,
            0xFE, 0xFF, 0xE6, 0x03, 0xF9, 0xFF, 0xFE, 0xFF, 0xE7, 0x03,
            0xF9, 0xFF, 0x00, 0x00, 0xE6, 0x03, 0xF9, 0xFF, 0xFF, 0xFF,
            0xE6, 0x03, 0xF7, 0xFF, 0xFE, 0xFF, 0xE5, 0x03, 0xF9, 0xFF,
            0xFF, 0xFF, 0xE5, 0x03, 0xF9, 0xFF, 0xFF, 0xFF, 0xE5, 0x03,
            0xFA, 0xFF, 0x00, 0x00, 0xE6, 0x03, 0xF9, 0xFF, 0xFE, 0xFF,
            0xE6, 0x03, 0xF8, 0xFF, 0xFF, 0xFF, 0xE6, 0x03, 0xF8, 0xFF,
            0xFF, 0xFF, 0xE5, 0x03, 0xF9, 0xFF, 0xFF, 0xFF, 0xE6, 0x03,
            0xF9, 0xFF, 0xFF, 0xFF, 0xE5, 0x03, 0xFA, 0xFF, 0xFF, 0xFF,
            0xE6, 0x03, 0xF9, 0xFF, 0xFF, 0xFF, 0xE5, 0x03, 0xF9, 0xFF,
            0xFF, 0xFF, 0xE5, 0x03, 0xF8, 0xFF, 0xFE, 0xFF, 0xE6, 0x03,
            0xF9, 0xFF, 0xFF, 0xFF, 0xE6, 0x03, 0xF9, 0xFF, 0xFF, 0xFF,
            0xE6, 0x03, 0xF9, 0xFF, 0x00, 0x00, 0xE5, 0x03, 0xF9, 0xFF,
            0xFE, 0xFF, 0xE6, 0x03, 0xF8, 0xFF, 0xFE, 0xFF, 0xE6, 0x03,
            0xF7, 0xFF, 0xFE, 0xFF, 0xE6, 0x03
        ])
        
        let amountOfSamples:Int = accDataFrameContent.count / 2 / 3 // measurement frame size / resolution in bytes / channels
        let sampleRate = 52
     
        let dataFrame = try PmdDataFrame(
            data: accDataFrameHeader + accDataFrameContent,
            { _,_ in 0 }  ,
            { _ in 1.0 },
            { _ in UInt(sampleRate) })
        
        // Act
        let accData = try AccData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(xValue1, accData.samples[0].x)
        XCTAssertEqual(yValue1, accData.samples[0].y)
        XCTAssertEqual(zValue1, accData.samples[0].z)
        XCTAssertEqual(xValue2, accData.samples[1].x)
        XCTAssertEqual(yValue2, accData.samples[1].y)
        XCTAssertEqual(zValue2, accData.samples[1].z)
        XCTAssertEqual(timeStamp, accData.samples.last?.timeStamp)
        
        // validate data size
        XCTAssertEqual(amountOfSamples, accData.samples.count)
    }
    
    func testProcessAccCompressedFrameType0() throws {
        // Arrange
        // HEX: 02 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           02 (ACC)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     80 (compressed, type 0)
        
        let accDataFrameHeader = Data([
            0x02,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x80
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: 71 07 F0 6A 9E 8D 0A 38 BE 5C BE BA 2F 96 B3 EE 4B E5 AD FB 42 B9 EB BE 4C FE BA 2F 92 BF EE 4B E4 B1 FB 12 B9 EC BD 3C 3E BB 2F 8F D3 DE 4B E3 B5 F7 D2 B8 ED BD 30 7E 7B 2F 8B E3 CE 8B E2 BA F7 A2 B8 EE BC 20 BE 7B 2F 88 F3 CE CB E1 BD EF 52 F8 EF BC 18 FE 3B 2F 84 03 BF CB E0 C2 EF 32 B8 F0 BB 04 4E BC 2E 81 13 AF 0B E0 C6 EF F2 F7 F1 B9 FC 7D BC 2E 7D 27 9F 4B DF CA EB C2 F7 F2 B8 EC CD 7C 2E 7B 37 8F 4B DE CE E3 92 F7 F3 B8 E0 0D FD 2D 77 4B 7F CB DD D2 DF 62 37 F5 B7 D4 4D BD 2D 74 5B 6F CB DC D7 D7 32 37 F6 B5 C8 8D 7D 2D 71 6B 4F 4B DC DC D3 F2 36 F7 B4 BC DD FD 2C 6F 7B 3F 4B DB E0 CF D2 36 F8 B2 B0 2D BE 2C 6C 8F 1F CB DA E3 C7 A2 76 F9
        // index    type                                            data:
        // 0-5:    Reference sample                                 0x71 0x07 0xF0 0x6A 0x9E 0x8D
        //      Sample 0 (aka. reference sample):
        //      channel 0: 71 07 => 0x0771 => 1905
        let sample0Channel0:Int32 = 1905
        //      channel 1: F0 6A => 0x6AF0 => 27376
        let sample0Channel1:Int32 = 27376
        //      channel 2: 9E 8D => 0x8D9E => -29282
        let sample0Channel2:Int32 = -29282
        // Delta dump: 0A 38 | BE 5C BE BA 2F 96 B3 EE 4B E5 AD ...
        // 6:      Delta size                           size 1:    0x0A (10 bits)
        // 7:      Sample amount                        size 1:    0x38 (Delta block contains 56 samples)
        // 8:                                                      0xBE (binary: 1011 1110)
        // 9:                                                      0x5C (binary: 0101 11 | 00)
        // 10:                                                     0xBE (binary: 1011 | 1110)
        //      Sample 1 - channel 0, size 10 bits: 00 1011 1110
        //      Sample 1 - channel 1, size 10 bits: 11 1001 0111
        // 11:                                                     0xBA (binary: 10 | 11 1010)
        //      Sample 1 - channel 2, size 10 bits: 11 1010 1011
        let sample1Channel0 = sample0Channel0 + 190
        let sample1Channel1 = sample0Channel1 - 105
        let sample1Channel2 = sample0Channel2 - 85
        let amountOfSamples:Int = 1 + 56 // reference sample + delta samples
        
        let accDataFrameContent = Data([
            0x71, 0x07, 0xF0, 0x6A, 0x9E, 0x8D, 0x0A, 0x38,
            0xBE, 0x5C, 0xBE, 0xBA, 0x2F, 0x96, 0xB3, 0xEE,
            0x4B, 0xE5, 0xAD, 0xFB, 0x42, 0xB9, 0xEB, 0xBE,
            0x4C, 0xFE, 0xBA, 0x2F, 0x92, 0xBF, 0xEE, 0x4B,
            0xE4, 0xB1, 0xFB, 0x12, 0xB9, 0xEC, 0xBD, 0x3C,
            0x3E, 0xBB, 0x2F, 0x8F, 0xD3, 0xDE, 0x4B, 0xE3,
            0xB5, 0xF7, 0xD2, 0xB8, 0xED, 0xBD, 0x30, 0x7E,
            0x7B, 0x2F, 0x8B, 0xE3, 0xCE, 0x8B, 0xE2, 0xBA,
            0xF7, 0xA2, 0xB8, 0xEE, 0xBC, 0x20, 0xBE, 0x7B,
            0x2F, 0x88, 0xF3, 0xCE, 0xCB, 0xE1, 0xBD, 0xEF,
            0x52, 0xF8, 0xEF, 0xBC, 0x18, 0xFE, 0x3B, 0x2F,
            0x84, 0x03, 0xBF, 0xCB, 0xE0, 0xC2, 0xEF, 0x32,
            0xB8, 0xF0, 0xBB, 0x04, 0x4E, 0xBC, 0x2E, 0x81,
            0x13, 0xAF, 0x0B, 0xE0, 0xC6, 0xEF, 0xF2, 0xF7,
            0xF1, 0xB9, 0xFC, 0x7D, 0xBC, 0x2E, 0x7D, 0x27,
            0x9F, 0x4B, 0xDF, 0xCA, 0xEB, 0xC2, 0xF7, 0xF2,
            0xB8, 0xEC, 0xCD, 0x7C, 0x2E, 0x7B, 0x37, 0x8F,
            0x4B, 0xDE, 0xCE, 0xE3, 0x92, 0xF7, 0xF3, 0xB8,
            0xE0, 0x0D, 0xFD, 0x2D, 0x77, 0x4B, 0x7F, 0xCB,
            0xDD, 0xD2, 0xDF, 0x62, 0x37, 0xF5, 0xB7, 0xD4,
            0x4D, 0xBD, 0x2D, 0x74, 0x5B, 0x6F, 0xCB, 0xDC,
            0xD7, 0xD7, 0x32, 0x37, 0xF6, 0xB5, 0xC8, 0x8D,
            0x7D, 0x2D, 0x71, 0x6B, 0x4F, 0x4B, 0xDC, 0xDC,
            0xD3, 0xF2, 0x36, 0xF7, 0xB4, 0xBC, 0xDD, 0xFD,
            0x2C, 0x6F, 0x7B, 0x3F, 0x4B, 0xDB, 0xE0, 0xCF,
            0xD2, 0x36, 0xF8, 0xB2, 0xB0, 0x2D, 0xBE, 0x2C,
            0x6C, 0x8F, 0x1F, 0xCB, 0xDA, 0xE3, 0xC7, 0xA2,
            0x76, 0xF9
        ])
        let delta = try PmdTimeStampUtils.deltaFromTimeStamps(previousTimeStamp, timeStamp, UInt(amountOfSamples))
        let expectedFirstSampleTimeStamp:UInt64 = UInt64(round(Double(previousTimeStamp) + delta))
        
        let range = 8
        let factor:Float = 2.44E-4
            
        let dataFrame = try PmdDataFrame(
            data: accDataFrameHeader + accDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let accData = try AccData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(Int32(factor * Float(sample0Channel0) * 1000), accData.samples[0].x)
        XCTAssertEqual(Int32(factor * Float(sample0Channel1) * 1000), accData.samples[0].y)
        XCTAssertEqual(Int32(factor * Float(sample0Channel2) * 1000), accData.samples[0].z)
        XCTAssertEqual(Int32(factor * Float(sample1Channel0) * 1000), accData.samples[1].x)
        XCTAssertEqual(Int32(factor * Float(sample1Channel1) * 1000), accData.samples[1].y)
        XCTAssertEqual(Int32(factor * Float(sample1Channel2) * 1000), accData.samples[1].z)
        
        // validate data in range
        for sample in accData.samples {
            XCTAssertTrue(abs(sample.x) <= range * 1000)
            XCTAssertTrue(abs(sample.y) <= range * 1000)
            XCTAssertTrue(abs(sample.z) <= range * 1000)
        }
        
        // validate time stamps
        XCTAssertEqual(expectedFirstSampleTimeStamp, accData.samples.first?.timeStamp)
        XCTAssertEqual(timeStamp, accData.samples.last?.timeStamp)
        
        // validate data size
        XCTAssertEqual(amountOfSamples, accData.samples.count)
    }
    
    func testProcessAccCompressedFrameType1() throws {
        // Arrange
        // HEX: 02 66 00 00 00 00 00 00 00 81
        // index                                                   data:
        // 0        type                                           02 (ACC)
        // 1..9     timestamp                                      66 00 00 00 00 00 00 00
        let timeStamp:UInt64 = 102
        // 10       frame type                                     81 (compressed, type 1)
        
        let accDataFrameHeader = Data([
            0x02,
            0x66, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x81
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: F1 FF 14 00 F0 03 06 01 7B 0F 08
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  F1 FF (0xFFF1 = -22)
        // 2..3     Sample 0 - channel 1 (ref. sample)  14 00 (0x0014 = 20)
        // 4..5     Sample 0 - channel 2 (ref. sample)  F0 03 (0x03F0 = 1008)
        // 6        Delta size                          06 (6 bit)
        // 7        Sample amount                       01 (10 samples)
        // 8..      Delta data                          7B (binary: 01 111011) 0F (binary: 0000 1111) 08 (binary: 0000 1000)
        // Delta channel 0                              111011b
        // Delta channel 1                              111101b
        // Delta channel 2                              000000b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        let sample0channel0:Int32 = -15
        let sample0channel1:Int32 = 20
        let sample0channel2:Int32 = 1008
        let sample1channel0 = sample0channel0 - 5
        let sample1channel1 = sample0channel1 - 3
        let sample1channel2 = sample0channel2 + 0
                
        let accDataFrameContent = Data([
            0xF1, 0xFF, 0x14, 0x00, 0xF0, 0x03, 0x06,
            0x01, 0x7B, 0x0F, 0x08
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: accDataFrameHeader + accDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let accData = try AccData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, accData.samples.count)
        
        XCTAssertEqual(sample0channel0, accData.samples[0].x)
        XCTAssertEqual(sample0channel1, accData.samples[0].y)
        XCTAssertEqual(sample0channel2, accData.samples[0].z)
        
        XCTAssertEqual(sample1channel0, accData.samples[1].x)
        XCTAssertEqual(sample1channel1, accData.samples[1].y)
        XCTAssertEqual(sample1channel2, accData.samples[1].z)
        
        XCTAssertEqual(101, accData.samples[0].timeStamp)
        XCTAssertEqual(timeStamp, accData.samples[1].timeStamp)
    }

    func testAccGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadAccGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected ACC golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            if let platforms = vector["platforms"] as? [String: Any],
               let supported = platforms["ios"] as? Bool,
               !supported {
                continue
            }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let dataFrame = try PmdDataFrame(
                data: Data(hexString: try XCTUnwrap(input["dataFrameHex"] as? String, id)),
                { _, _ in UInt64(truncating: input["previousTimeStamp"] as? NSNumber ?? 0) },
                { _ in Float(truncating: input["factor"] as? NSNumber ?? 1.0) },
                { _ in UInt(truncating: input["sampleRate"] as? NSNumber ?? 0) })

            if let parseError = expected["parseError"] as? String {
                XCTAssertThrowsError(try AccData.parseDataFromDataFrame(frame: dataFrame), id) { error in
                    switch parseError {
                    case "unsupportedCompressedFrame":
                        guard case BleGattException.gattDataError = error else {
                            return XCTFail("Expected gattDataError for \(id), got \(error)")
                        }
                    default:
                        XCTFail("Unsupported parse error expectation in \(id): \(parseError)")
                    }
                }
                XCTAssertEqual(try XCTUnwrap(expected["timeStamp"] as? NSNumber, id).uint64Value, dataFrame.timeStamp, id)
                continue
            }

            let accData = try AccData.parseDataFromDataFrame(frame: dataFrame)

            XCTAssertEqual(try XCTUnwrap(expected["timeStamp"] as? NSNumber, id).uint64Value, accData.timeStamp, id)
            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, accData.samples.count, id)
            for (index, expectedSample) in samples.enumerated() {
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, accData.samples[index].timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["x"] as? NSNumber, id).int32Value, accData.samples[index].x, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["y"] as? NSNumber, id).int32Value, accData.samples[index].y, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["z"] as? NSNumber, id).int32Value, accData.samples[index].z, id)
            }
        }
    }

    func testAccGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadAccGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertNotNil(platforms["android"], id)
            XCTAssertNotNil(platforms["ios"], id)
            XCTAssertNotNil(platforms["common"], id)
        }
    }

    func testAccReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadAccReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("acc-readiness", id)
        XCTAssertEqual("accReadiness", input["kind"] as? String, id)
        XCTAssertEqual(ACC_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(ACC_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(ACC_READINESS_FAMILIES, coveredFamilies, id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], id), ["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccDataTest"], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], id), ["AccDataTest"], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], id), ["com.polar.sharedtest.AccParserCommonPolicyTest"], id)
    }

    private func loadAccGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("acc-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "accReadiness"
            }
    }

    private func loadAccReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/acc-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private let ACC_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/acc-compressed-type0-factor-half.json",
        "protocol/sensors/acc-compressed-type0-truncated-delta-header-android-error.json",
        "protocol/sensors/acc-compressed-type0-truncated-delta-header-ios-reference-only.json",
        "protocol/sensors/acc-compressed-type0-truncated-delta-payload-android-error.json",
        "protocol/sensors/acc-compressed-type0-truncated-delta-payload-ios-reference-only.json",
        "protocol/sensors/acc-compressed-type1-two-samples.json",
        "protocol/sensors/acc-compressed-type2-unsupported.json",
        "protocol/sensors/acc-raw-type0-signed-boundaries.json",
        "protocol/sensors/acc-raw-type0-truncated-sample-android-error.json",
        "protocol/sensors/acc-raw-type1-signed-boundaries.json",
        "protocol/sensors/acc-raw-type1-truncated-sample-android-error.json",
        "protocol/sensors/acc-raw-type1-two-samples.json",
        "protocol/sensors/acc-raw-type2-android-only.json",
        "protocol/sensors/acc-raw-type2-truncated-sample-android-error.json"
    ]

    private let ACC_READINESS_FAMILIES = [
        "raw-type0-signed-axis-boundaries",
        "raw-type1-signed-axis-parsing",
        "raw-type1-timestamp-interpolation",
        "compressed-type0-millig-factor-scaling",
        "compressed-type1-reference-delta-decoding",
        "unsupported-compressed-type-policy",
        "raw-type2-android-ownership",
        "truncated-raw-sample-policy",
        "truncated-compressed-delta-header-policy",
        "truncated-compressed-delta-payload-policy",
        "platform-acc-vector-reference-gate",
        "compile-verification-gate"
    ]
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "AccDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "AccDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
