//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class TypeUtilsTest: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    func testArrayConversionToSignedIntMaxValue() throws {
        // Arrange
        let byteArray = Data([0xFF, 0xFF, 0xFF, 0xFF])
        let expectedValue:Int32 = -1

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }
    
    func testArrayConversionToSignedIntMinValue() throws {
        // Arrange
        let byteArray = Data([0x00, 0x00, 0x00, 0x00])
        let expectedValue:Int32 = 0

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }
    
    func testArrayConversionToSignedIntMaxPositiveInt() throws {
        // Arrange
        let byteArray = Data([0xFF, 0xFF, 0xFF, 0x7F])
        let expectedValue = Int32.max

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }

    func testArrayConversionToSignedIntSmallArray() throws {
        // Arrange
        let byteArray = Data([0xFF])
        let expectedValue:Int32 = -1

        // Act
        let result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        XCTAssertEqual(expectedValue, result)
    }

    func testTypeUtilsUsesProductionConversionPolicy() throws {
        XCTAssertEqual(Int32(-32768), TypeUtils.convertArrayToSignedInt(Data([0x00, 0x80])))
        XCTAssertEqual(Int32(-32768), TypeUtils.convertArrayToSignedInt(Data([0x7f, 0x00, 0x80, 0x01]), offset: 1, size: 2))
        XCTAssertEqual(UInt64.max, TypeUtils.convertArrayToUnsignedInt64(Data([0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff])))
        XCTAssertEqual(UInt64.max, TypeUtils.convertArrayToUnsignedInt64(Data([0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff]), offset: 1, size: 8))
    }

    func testTypeUtilsGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadTypeUtilsGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected type utility golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try platformExpected(vector, platform: "ios")
            let hex = try XCTUnwrap(input["hex"] as? String, id)
            let data = try Data(hexString: hex)
            let offset = input["offset"] as? Int
            let size = input["size"] as? Int

            if let expectedUnsignedInt = expected["unsignedInt"] as? NSNumber {
                XCTAssertEqual(UInt(expectedUnsignedInt.uint64Value), convertArrayToUnsignedInt(data, offset: offset, size: size), id)
            } else if let expectedUnsignedInt = expected["unsignedInt"] as? String {
                XCTAssertEqual(expectedUnsignedInt, String(convertArrayToUnsignedInt(data, offset: offset, size: size)), id)
            }

            if let expectedUnsignedLong = expected["unsignedLong"] as? NSNumber {
                XCTAssertEqual(UInt64(expectedUnsignedLong.uint64Value), convertArrayToUnsignedInt64(data, offset: offset, size: size), id)
            } else if let expectedUnsignedLong = expected["unsignedLong"] as? String {
                XCTAssertEqual(UInt64(expectedUnsignedLong), convertArrayToUnsignedInt64(data, offset: offset, size: size), id)
            }

            if let expectedSignedInt = expected["signedInt"] as? NSNumber {
                XCTAssertEqual(expectedSignedInt.int32Value, convertArrayToSignedInt(data, offset: offset, size: size), id)
            }

            if expected["unsignedIntError"] != nil {
                assertConversionErrorPrecondition(data: data, expected: expected["unsignedIntError"], maxSize: 4, id: id)
            }

            if expected["unsignedLongError"] != nil {
                assertConversionErrorPrecondition(data: data, expected: expected["unsignedLongError"], maxSize: 8, id: id)
            }

            if expected["signedIntError"] != nil {
                assertConversionErrorPrecondition(data: data, expected: expected["signedIntError"], maxSize: 4, id: id)
            }
        }
    }

    func testTypeUtilsGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadTypeUtilsGoldenVectors() {
            let id = vector["id"] as? String ?? "unknown-vector"

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

    func testTypeUtilsReadinessManifestIsPinnedBeforeParserPrimitiveMigration() throws {
        let readiness = try loadTypeUtilsReadinessManifest()
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(readiness["platforms"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(readiness["id"] as? String, "type-utils-readiness")
        XCTAssertEqual(input["kind"] as? String, "typeUtilsReadiness")
        XCTAssertEqual(policyVectorPaths, [
            "protocol/type-utils/empty-payload-platform-difference.json",
            "protocol/type-utils/offset-signed-int-negative-boundary.json",
            "protocol/type-utils/offset-unsigned-int-little-endian.json",
            "protocol/type-utils/signed-int-24bit-negative-one.json",
            "protocol/type-utils/signed-int-max.json",
            "protocol/type-utils/signed-int-min-16bit.json",
            "protocol/type-utils/signed-int-min-24bit.json",
            "protocol/type-utils/signed-int-min-32bit.json",
            "protocol/type-utils/signed-int-negative-one.json",
            "protocol/type-utils/signed-int-too-long.json",
            "protocol/type-utils/unsigned-byte-max.json",
            "protocol/type-utils/unsigned-int-high-bit-16bit-platform-difference.json",
            "protocol/type-utils/unsigned-int-high-bit-platform-difference.json",
            "protocol/type-utils/unsigned-int-little-endian.json",
            "protocol/type-utils/unsigned-int-too-long.json",
            "protocol/type-utils/unsigned-long-max.json",
            "protocol/type-utils/unsigned-long-too-long.json"
        ])
        let expectedFamilies = [
            "unsigned-byte-conversion",
            "little-endian-unsigned-int-conversion",
            "little-endian-unsigned-long-conversion",
            "signed-int-sign-extension",
            "offset-and-size-selection",
            "signed-minimum-boundaries",
            "unsigned-high-bit-platform-decision",
            "empty-payload-error-policy",
            "payload-too-long-error-policy",
            "uint64-max-decimal-preservation",
            "platform-type-utils-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Type utility migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS type utility tests continue to reference the same vectors, unsigned byte/int/long conversion, signed sign extension, offset and size selection, signed-minimum boundaries, high-bit unsigned platform decisions, empty payload and payload-too-long typed errors, UInt64 max decimal preservation, and compile verification remain explicit before production parser primitives move.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.common.ble.TypeUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["TypeUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.TypeUtilsCommonPolicyTest"])
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    private func loadTypeUtilsGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/type-utils")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                let input = vector["input"] as? [String: Any]
                return input?["kind"] as? String != "typeUtilsReadiness"
            }
    }

    private func loadTypeUtilsReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/type-utils/type-utils-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private func platformExpected(_ vector: [String: Any], platform: String) throws -> [String: Any] {
        if let platformExpectations = vector["platformExpectations"] as? [String: Any] {
            return try XCTUnwrap(platformExpectations[platform] as? [String: Any], vector["id"] as? String ?? "unknown-vector")
        }
        return try XCTUnwrap(vector["expected"] as? [String: Any], vector["id"] as? String ?? "unknown-vector")
    }

    private func convertArrayToUnsignedInt(_ data: Data, offset: Int?, size: Int?) -> UInt {
        if let offset, let size {
            return TypeUtils.convertArrayToUnsignedInt(data, offset: offset, size: size)
        }
        return TypeUtils.convertArrayToUnsignedInt(data)
    }

    private func convertArrayToUnsignedInt64(_ data: Data, offset: Int?, size: Int?) -> UInt64 {
        if let offset, let size {
            return TypeUtils.convertArrayToUnsignedInt64(data, offset: offset, size: size)
        }
        return TypeUtils.convertArrayToUnsignedInt64(data)
    }

    private func convertArrayToSignedInt(_ data: Data, offset: Int?, size: Int?) -> Int32 {
        if let offset, let size {
            return TypeUtils.convertArrayToSignedInt(data, offset: offset, size: size)
        }
        return TypeUtils.convertArrayToSignedInt(data)
    }

    private func assertConversionErrorPrecondition(data: Data, expected: Any?, maxSize: Int, id: String) {
        switch expected as? String {
        case "payloadTooLong":
            XCTAssertGreaterThan(data.count, maxSize, id)
        case "emptyPayloadPrecondition":
            XCTAssertTrue(data.isEmpty, id)
        case "emptyPayload":
            XCTAssertTrue(data.isEmpty, id)
        default:
            XCTFail("Unsupported conversion error expectation in \(id): \(String(describing: expected))")
        }
    }
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "TypeUtilsTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "TypeUtilsTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
