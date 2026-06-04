//  Copyright © 2021 Polar. All rights reserved.

import XCTest
import iOSCommunications

private let PSFTP_BYTE_CODEC_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/psftp-rfc76/error-frame-ffff.json",
    "sdk/psftp-rfc76/final-last-frame.json",
    "sdk/psftp-rfc76/first-more-frame.json",
    "sdk/psftp-rfc76/header-only-last-frame.json",
    "sdk/psftp-rfc76/header-only-more-frame.json",
    "sdk/psftp-rfc76/middle-more-frame.json",
    "sdk/psftp-rfc76/single-last-frame.json",
    "sdk/psftp-message-stream/complete-message-streams.json",
    "sdk/psftp-message-stream/rfc76-frame-splitting.json"
]

private let PSFTP_BYTE_CODEC_READINESS_FAMILIES = [
    "rfc76-header-next-bit",
    "rfc76-status-decoding",
    "rfc76-sequence-number-decoding",
    "rfc76-payload-slicing",
    "rfc76-error-frame-platform-split",
    "rfc60-request-stream-encoding",
    "rfc60-query-stream-encoding",
    "rfc60-notification-stream-encoding",
    "android-request-file-data-append-policy",
    "rfc76-mtu-frame-splitting",
    "rfc76-sequence-wrap",
    "platform-codec-vector-reference-gate",
    "compile-verification-gate"
]

private let COMPLETE_MESSAGE_CASE_IDS = [
    "request-header-only",
    "android-request-with-file-data",
    "query-with-header",
    "notification-with-header",
    "notification-empty-header"
]

private let RFC76_FRAME_SPLITTING_CASE_IDS = [
    "empty-payload",
    "exactly-one-frame",
    "two-frames",
    "sequence-wraps-after-fifteen"
]

private let PSFTP_BYTE_CODEC_READINESS_COMMON_DECISION = "PSFTP byte-codec migration may proceed only after every RFC76 and RFC60 vector listed in this readiness manifest is executable from shared commonTest, Android and iOS codec tests continue to reference the same vectors, header next/status/sequence/payload decoding, RFC76 error-frame platform split, complete-message stream encoding, Android file-data append behavior, MTU frame splitting, sequence wrap, and the shared tests are compile-verified."

class BlePsFtpUtilityTest: XCTestCase {
   
    func test_processSingleFrame() throws {
        // Arrange
        // HEX 02 FF 00
        // index    type                                            data:
        // 0        header                                          0x02
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  01b (last frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_LAST
        let expectedSequenceNumber = 0
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x02,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }

    func test_processFirstFrameInLongSequence() throws {
        // Arrange
        // HEX 06 FF 00
        // index    type                                            data:
        // 0        header                                          0x06
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  11b (more frames to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_MORE
        let expectedSequenceNumber = 0
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x06,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processSecondFrameInLongSequence() throws {
        // Arrange
        // HEX 17 FF 00
        // index    type                                            data:
        // 0        header                                          0x17
        //      bit0 :       next                                     1b (frame inside sequence)
        //      bit1..2 :    status                                  11b (more frames to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0001b (second frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 1
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_MORE
        let expectedSequenceNumber = 1
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x17,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processLastFrameInLongSequence() throws {
        // Arrange
        // HEX 23 FF 00
        // index    type                                            data:
        // 0        header                                          0x23
        //      bit0 :       next                                     1b (frame inside sequence)
        //      bit1..2 :    status                                  01b (last frame in sequence)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0010b (third frame)
        // 1..MAX   payload                                         0xFF 0x00
        let expectedNext = 1
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_LAST
        let expectedSequenceNumber = 2
        let expectedPayload = Data([0xFF,0x00])
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x23,0xFF,0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload.count, expectedPayload.count)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processErrorFrame() throws {
        // Arrange
        // HEX 00 FE FF
        // index    type                                            data:
        // 0        header                                          0x00
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  00b (error frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         0xFF, 0xFF
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE
        let expectedSequenceNumber = 0
        let expectedPayload = Data()
        let expectedError = 0xFFFE

        // Act
        let deviceNotifyingData = Data([0x00, 0xFE, 0xFF])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertTrue(resultFrame.payload == expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }
    
    func test_processHeaderOnlyMoreFrame() throws {
        // Arrange
        // HEX 16
        // index    type                                            data:
        // 0        header                                          0x16
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  11b (MORE - more frames to come)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0001b (sequence 1)
        // 1..MAX   payload                                         N/A (0 bytes)
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_MORE
        let expectedSequenceNumber = 1
        let expectedPayload = Data()
        let expectedError: Int? = nil

        // Act
        let deviceNotifyingData = Data([0x16])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)

        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }

    func test_processHeaderOnlyLastFrame() throws {
        // Arrange
        // A header-only LAST frame (status=01, no payload) — valid EOF signal from device.
        //
        // HEX 02
        // index    type                                            data:
        // 0        header                                          0x02
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  01b (LAST)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b
        // 1..MAX   payload                                         N/A (0 bytes)
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_LAST
        let expectedSequenceNumber = 0
        let expectedPayload = Data()
        let expectedError: Int? = nil

        // Act
        let deviceNotifyingData = Data([0x02])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)

        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertEqual(resultFrame.payload, expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }

    func test_processInteruptFrame() throws {
        // Arrange
        // HEX 00
        // index    type                                            data:
        // 0        header                                          0x00
        //      bit0 :       next                                     0b (first frame)
        //      bit1..2 :    status                                  00b (error frame)
        //      bit3 :       RFU
        //      bit4..7:     sequence number                       0000b (first frame)
        // 1..MAX   payload                                         N/A
        let expectedNext = 0
        let expectedStatus = BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE
        let expectedSequenceNumber = 0
        let expectedPayload = Data()
        let expectedError : Int? = nil

        // Act
        let deviceNotifyingData = Data([0x00])
        let resultFrame = try BlePsFtpUtility.processRfc76MessageFrame(deviceNotifyingData)
    
        // Assert
        XCTAssertEqual(resultFrame.next, expectedNext)
        XCTAssertEqual(resultFrame.status, expectedStatus)
        XCTAssertEqual(resultFrame.sequenceNumber, expectedSequenceNumber)
        XCTAssertTrue(resultFrame.payload == expectedPayload)
        XCTAssertEqual(resultFrame.error, expectedError)
    }

    func testRfc76GoldenVectorsDecodeFrameHeaders() throws {
        let vectors = try loadRfc76GoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected PSFTP RFC76 golden vectors")

        for vector in vectors {
            let caseId = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], caseId)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], caseId)
            let frame = try BlePsFtpUtility.processRfc76MessageFrame(Data(hex: try XCTUnwrap(input["frameHex"] as? String, caseId)))

            XCTAssertEqual(frame.next, try number(expected, "next", id: caseId), "\(caseId) next")
            XCTAssertEqual(frame.status, try number(expected, "status", id: caseId), "\(caseId) status")
            XCTAssertEqual(frame.sequenceNumber, try number(expected, "sequenceNumber", id: caseId), "\(caseId) sequenceNumber")
            if expected["payloadHex"] is NSNull {
                XCTAssertEqual(frame.payload, Data(), "\(caseId) payload")
            } else {
                XCTAssertEqual(frame.payload, try Data(hex: try XCTUnwrap(expected["payloadHex"] as? String, caseId)), "\(caseId) payload")
            }
            if expected["error"] is NSNull {
                XCTAssertNil(frame.error, "\(caseId) error")
            } else {
                let error = try XCTUnwrap(expected["error"] as? [String: Any], caseId)
                XCTAssertEqual(frame.error, try number(error, "ios", id: caseId), "\(caseId) error")
            }
        }
    }

    func testRfc76GoldenVectorsFollowNeutralKmpShape() throws {
        let vectors = try loadRfc76GoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected PSFTP RFC76 golden vectors")
        for vector in vectors {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["frameHex"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testMessageStreamGoldenVectorsEncodeRfc60Messages() throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-message-stream", fileName: "complete-message-streams.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        XCTAssertEqual(cases.compactMap { $0["id"] as? String }, COMPLETE_MESSAGE_CASE_IDS)
        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            if let platforms = testCase["platforms"] as? [String: Any],
               let ios = platforms["ios"] as? Bool,
               !ios {
                continue
            }
            let type = try messageType(named: try XCTUnwrap(testCase["type"] as? String, id))
            let header = try Data(hex: try XCTUnwrap(testCase["headerHex"] as? String, id))
            let stream = BlePsFtpUtility.makeCompleteMessageStream(
                header,
                type: type,
                id: try number(testCase, "idValue", id: id)
            )
            XCTAssertEqual(try Data(reading: stream), try Data(hex: try XCTUnwrap(testCase["expectedHex"] as? String, id)), id)
        }
    }

    func testMessageStreamGoldenVectorsSplitRfc76Frames() throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-message-stream", fileName: "rfc76-frame-splitting.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        XCTAssertEqual(cases.compactMap { $0["id"] as? String }, RFC76_FRAME_SPLITTING_CASE_IDS)
        for testCase in cases {
            let id = try XCTUnwrap(testCase["id"] as? String)
            let frames = BlePsFtpUtility.buildRfc76MessageFrameAll(
                InputStream(data: try Data(hex: try XCTUnwrap(testCase["payloadHex"] as? String, id))),
                mtuSize: try number(testCase, "mtu", id: id),
                sequenceNumber: BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
            )
            let expectedFrames: [String]
            if let iosExpectedFrames = testCase["iosExpectedFramesHex"] as? [String] {
                expectedFrames = iosExpectedFrames
            } else {
                expectedFrames = try XCTUnwrap(testCase["expectedFramesHex"] as? [String], id)
            }
            XCTAssertEqual(frames.map { $0.hexString }, expectedFrames, id)
        }
    }

    func testMessageStreamGoldenVectorsFollowNeutralKmpShape() throws {
        for fileName in ["complete-message-streams.json", "rfc76-frame-splitting.json"] {
            let vector = try loadPsFtpVector(directoryName: "psftp-message-stream", fileName: fileName)
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["cases"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testPsFtpByteCodecReadinessManifestIsPinnedBeforeCodecMigration() throws {
        let vector = try loadPsFtpVector(directoryName: "psftp-message-stream", fileName: "byte-codec-readiness.json")
        XCTAssertEqual(vector["id"] as? String, "psftp-byte-codec-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "psftp-byte-codec-readiness")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "psftp-byte-codec-readiness")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "psftp-byte-codec-readiness")
        XCTAssertEqual(input["kind"] as? String, "psFtpByteCodecReadiness")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "psftp-byte-codec-readiness")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "psftp-byte-codec-readiness")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "psftp-byte-codec-readiness")
        XCTAssertEqual(PSFTP_BYTE_CODEC_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "psftp-byte-codec-readiness")
        XCTAssertEqual(PSFTP_BYTE_CODEC_READINESS_FAMILIES, requiredFamilies, "psftp-byte-codec-readiness")
        XCTAssertEqual(PSFTP_BYTE_CODEC_READINESS_FAMILIES, coveredFamilies, "psftp-byte-codec-readiness")
        XCTAssertEqual(expected["commonDecision"] as? String, PSFTP_BYTE_CODEC_READINESS_COMMON_DECISION, "psftp-byte-codec-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "psftp-byte-codec-readiness"), ["com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "psftp-byte-codec-readiness"), ["BlePsFtpUtilityTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "psftp-byte-codec-readiness"), ["com.polar.sharedtest.PsFtpByteCodecCommonPolicyTest"])
    }

    private func loadRfc76GoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/psftp-rfc76")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

    private func loadPsFtpVector(directoryName: String, fileName: String) throws -> [String: Any] {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk")
            .appendingPathComponent(directoryName)
            .appendingPathComponent(fileName)
        let data = try Data(contentsOf: file)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
    }

    private func messageType(named name: String) throws -> BlePsFtpUtility.MessageType {
        switch name {
        case "request": return .request
        case "query": return .query
        case "notification": return .notification
        default: throw NSError(domain: "BlePsFtpUtilityTest", code: 4, userInfo: [NSLocalizedDescriptionKey: "Unknown message type \(name)"])
        }
    }


    private func number(_ object: [String: Any], _ key: String, id: String) throws -> Int {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").intValue
    }
}

private extension Data {
    init(hex: String) throws {
        guard hex.count % 2 == 0 else {
            throw NSError(domain: "BlePsFtpUtilityTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                throw NSError(domain: "BlePsFtpUtilityTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte"])
            }
            data.append(byte)
            index = next
        }
        self = data
    }

    init(reading stream: InputStream) throws {
        var data = Data()
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }
        stream.open()
        defer { stream.close() }
        while stream.hasBytesAvailable {
            let count = stream.read(buffer, maxLength: bufferSize)
            if count < 0 {
                throw stream.streamError ?? NSError(domain: "BlePsFtpUtilityTest", code: 5, userInfo: [NSLocalizedDescriptionKey: "Failed to read stream"])
            }
            if count == 0 { break }
            data.append(buffer, count: count)
        }
        self = data
    }

    var hexString: String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
