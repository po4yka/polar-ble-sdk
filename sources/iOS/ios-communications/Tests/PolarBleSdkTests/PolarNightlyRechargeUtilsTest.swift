import XCTest
@testable import PolarBleSdk

class PolarNightlyRechargeUtilsTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    private static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testNightlyRechargeReadHeaderUsesSharedFileFacadePlanning() throws {
        let date = try XCTUnwrap(Self.utcCalendar.date(from: DateComponents(year: 2026, month: 1, day: 2)))

        let operation = PolarNightlyRechargeUtils.nightlyRechargeReadOperation(date: date)
        XCTAssertEqual(operation.command, .get)
        XCTAssertEqual(operation.path, "/U/0/20260102/NR/NR.BPB")
    }

    func testReadNightlyRechargeData_shouldReturnNightlyRechargeData() async throws {
        // Arrange
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        let date = Date()
        let expectedPath = "/U/0/\(dateFormatter.string(from: date))/NR/NR.BPB"

        let proto = Data_PbNightlyRecoveryStatus.with {
            $0.sleepResultDate = PbDate.with { $0.year = 2024; $0.month = 12; $0.day = 5 }
            $0.createdTimestamp = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.year = 2023; $0.month = 12; $0.day = 5 }
                $0.time = PbTime.with { $0.hour = 10; $0.minute = 0; $0.seconds = 0; $0.millis = 0 }
                $0.trusted = true
            }
            $0.modifiedTimestamp = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.year = 2023; $0.month = 12; $0.day = 5 }
                $0.time = PbTime.with { $0.hour = 10; $0.minute = 30; $0.seconds = 0; $0.millis = 0 }
                $0.trusted = true
            }
            $0.ansStatus = 5.5
            $0.recoveryIndicator = 3
            $0.recoveryIndicatorSubLevel = 50
            $0.ansRate = 4
            $0.scoreRateObsolete = 2
            $0.meanNightlyRecoveryRri = 800
            $0.meanNightlyRecoveryRmssd = 50
            $0.meanNightlyRecoveryRespirationInterval = 1000
            $0.meanBaselineRri = 750
            $0.sdBaselineRri = 30
            $0.meanBaselineRmssd = 45
            $0.sdBaselineRmssd = 20
            $0.meanBaselineRespirationInterval = 950
            $0.sdBaselineRespirationInterval = 25
            $0.sleepTip = "Sleep tip 1"
            $0.vitalityTip = "Vitality tip 2"
            $0.exerciseTip = "Exercise tip 3"
        }

        mockClient.requestReturnValue = .success(try proto.serializedData())

        let createdTimestamp = DateComponents(calendar: Calendar.current, year: 2023, month: 12, day: 5, hour: 10, minute: 0).date!
        let modifiedTimestamp = DateComponents(calendar: Calendar.current, year: 2023, month: 12, day: 5, hour: 10, minute: 30).date!
        let sleepResultDate = DateComponents(year: 2024, month: 12, day: 5)

        let expectedResult = PolarNightlyRechargeData(
            createdTimestamp: createdTimestamp,
            modifiedTimestamp: modifiedTimestamp,
            ansStatus: 5.5,
            recoveryIndicator: 3,
            recoveryIndicatorSubLevel: 50,
            ansRate: 4,
            scoreRateObsolete: 2,
            meanNightlyRecoveryRRI: 800,
            meanNightlyRecoveryRMSSD: 50,
            meanNightlyRecoveryRespirationInterval: 1000,
            meanBaselineRRI: 750,
            sdBaselineRRI: 30,
            meanBaselineRMSSD: 45,
            sdBaselineRMSSD: 20,
            meanBaselineRespirationInterval: 950,
            sdBaselineRespirationInterval: 25,
            sleepTip: "Sleep tip 1",
            vitalityTip: "Vitality tip 2",
            exerciseTip: "Exercise tip 3",
            sleepResultDate: sleepResultDate
        )

        // Act
        let testResult = await PolarNightlyRechargeUtils.readNightlyRechargeData(client: mockClient, date: date)

        // Assert
        XCTAssertNotNil(testResult)
        XCTAssertEqual(testResult?.createdTimestamp, expectedResult.createdTimestamp)
        XCTAssertEqual(testResult?.modifiedTimestamp, expectedResult.modifiedTimestamp)
        XCTAssertEqual(testResult?.ansStatus, expectedResult.ansStatus)
        XCTAssertEqual(testResult?.recoveryIndicator, expectedResult.recoveryIndicator)
        XCTAssertEqual(testResult?.recoveryIndicatorSubLevel, expectedResult.recoveryIndicatorSubLevel)
        XCTAssertEqual(testResult?.ansRate, expectedResult.ansRate)
        XCTAssertEqual(testResult?.scoreRateObsolete, expectedResult.scoreRateObsolete)
        XCTAssertEqual(testResult?.meanNightlyRecoveryRRI, expectedResult.meanNightlyRecoveryRRI)
        XCTAssertEqual(testResult?.meanNightlyRecoveryRMSSD, expectedResult.meanNightlyRecoveryRMSSD)
        XCTAssertEqual(testResult?.meanNightlyRecoveryRespirationInterval, expectedResult.meanNightlyRecoveryRespirationInterval)
        XCTAssertEqual(testResult?.meanBaselineRRI, expectedResult.meanBaselineRRI)
        XCTAssertEqual(testResult?.sdBaselineRRI, expectedResult.sdBaselineRRI)
        XCTAssertEqual(testResult?.meanBaselineRMSSD, expectedResult.meanBaselineRMSSD)
        XCTAssertEqual(testResult?.sdBaselineRMSSD, expectedResult.sdBaselineRMSSD)
        XCTAssertEqual(testResult?.meanBaselineRespirationInterval, expectedResult.meanBaselineRespirationInterval)
        XCTAssertEqual(testResult?.sdBaselineRespirationInterval, expectedResult.sdBaselineRespirationInterval)
        XCTAssertEqual(testResult?.sleepTip, expectedResult.sleepTip)
        XCTAssertEqual(testResult?.vitalityTip, expectedResult.vitalityTip)
        XCTAssertEqual(testResult?.exerciseTip, expectedResult.exerciseTip)
        XCTAssertEqual(testResult?.sleepResultDate, expectedResult.sleepResultDate)
        XCTAssertEqual(mockClient.requestCalls.count, 1)

        let actualPath = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0]).path
        XCTAssertEqual(actualPath, expectedPath)
    }
    
    func testReadNightlyRechargeFromDayDirectory_FileNotFound() async {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))

        // Act — errors are swallowed; method returns nil
        let result = await PolarNightlyRechargeUtils.readNightlyRechargeData(client: mockClient, date: Date())

        // Assert
        XCTAssertNil(result, "Expected nil when nightly recovery file is not found")
    }

    func testNightlyRechargeGoldenVectorsMapProtoToPublicModel() async throws {
        let vectors = try loadNightlyRechargeGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected nightly recharge golden vectors")
        let date = try XCTUnwrap(Self.utcCalendar.date(from: DateComponents(year: 2026, month: 1, day: 2)))
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Self.utcCalendar
        dateFormatter.timeZone = TimeZone(secondsFromGMT: 0)!
        dateFormatter.dateFormat = "yyyyMMdd"
        let expectedPath = "/U/0/\(dateFormatter.string(from: date))/NR/NR.BPB"

        for vector in vectors {
            let caseId = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], caseId)
            if let responseHex = input["responseHex"] as? String {
                mockClient.requestReturnValue = .success(try Data(hexString: responseHex))
            } else {
                let proto = try buildNightlyRechargeProto(from: try XCTUnwrap(input["proto"] as? [String: Any], caseId), id: caseId)
                mockClient.requestReturnValue = .success(try proto.serializedData())
            }
            mockClient.requestCalls.removeAll()

            let result = await PolarNightlyRechargeUtils.readNightlyRechargeData(client: mockClient, date: date)

            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], caseId)
            if expected["result"] is NSNull {
                XCTAssertNil(result, caseId)
            } else {
                try assertNightlyRechargeResult(result, expected: expected, id: caseId)
            }
            XCTAssertEqual(mockClient.requestCalls.count, 1, caseId)
            let actualPath = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0]).path
            XCTAssertEqual(actualPath, expectedPath, caseId)
        }
    }

    func testNightlyRechargeGoldenVectorsFollowNeutralKmpShape() throws {
        let vectors = try loadNightlyRechargeGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected nightly recharge golden vectors")
        for vector in vectors {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertTrue(input["proto"] != nil || input["responseHex"] != nil, id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testSleepNightlyReadinessManifestPinsModelOwnership() throws {
        let readiness = try loadSleepNightlyReadinessManifest()
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(readiness["id"] as? String, "sleep-nightly-readiness")
        XCTAssertEqual(input["kind"] as? String, "sleepNightlyReadiness")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/nightly-recharge/full-status.json",
            "sdk/nightly-recharge/malformed-response.json",
            "sdk/nightly-recharge/missing-modified-default-metrics.json",
            "sdk/sleep/partial-night-omitted-optionals.json",
            "sdk/sleep/sleep-offset-platform-policy.json",
            "sdk/sleep/sleep-stage-hypnogram.json",
            "sdk/sleep/sleep-timezone-offsets.json"
        ])
        let expectedFamilies = [
            "nightly-result-date-formatting",
            "nightly-created-modified-timestamp-formatting",
            "nightly-proto3-default-preservation",
            "nightly-malformed-payload-null-policy",
            "sleep-end-offset-field-policy",
            "sleep-timezone-to-utc-instant-policy",
            "sleep-hypnogram-order-preservation",
            "sleep-cycle-order-preservation",
            "sleep-stage-enum-mapping",
            "sleep-partial-night-optional-policy",
            "platform-sleep-nightly-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Sleep and nightly recharge model shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS sleep/nightly tests continue to reference the same vectors, nightly date/timestamp/default and malformed-payload behavior stays covered, sleep end-offset, timezone, hypnogram, cycle, enum, and partial-night optional policies remain explicit, and the shared tests are compile-verified.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarNightlyRechargeUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarNightlyRechargeUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.SleepNightlyRechargeCommonPolicyTest"])
    }

    private func buildNightlyRechargeProto(from fields: [String: Any], id: String) throws -> Data_PbNightlyRecoveryStatus {
        var proto = Data_PbNightlyRecoveryStatus()
        proto.sleepResultDate = try buildPbDate(from: try XCTUnwrap(fields["sleepResultDate"] as? [String: Any], id), id: id)
        proto.createdTimestamp = try buildPbSystemDateTime(from: try XCTUnwrap(fields["createdTimestamp"] as? [String: Any], id), id: id)
        if let modifiedTimestamp = fields["modifiedTimestamp"] as? [String: Any] {
            proto.modifiedTimestamp = try buildPbSystemDateTime(from: modifiedTimestamp, id: id)
        }
        if let value = fields["ansStatus"] as? NSNumber { proto.ansStatus = value.floatValue }
        if let value = fields["recoveryIndicator"] as? NSNumber { proto.recoveryIndicator = value.int32Value }
        if let value = fields["recoveryIndicatorSubLevel"] as? NSNumber { proto.recoveryIndicatorSubLevel = value.int32Value }
        if let value = fields["ansRate"] as? NSNumber { proto.ansRate = value.int32Value }
        if let value = fields["scoreRateObsolete"] as? NSNumber { proto.scoreRateObsolete = value.int32Value }
        if let value = fields["meanNightlyRecoveryRRI"] as? NSNumber { proto.meanNightlyRecoveryRri = value.int32Value }
        if let value = fields["meanNightlyRecoveryRMSSD"] as? NSNumber { proto.meanNightlyRecoveryRmssd = value.int32Value }
        if let value = fields["meanNightlyRecoveryRespirationInterval"] as? NSNumber { proto.meanNightlyRecoveryRespirationInterval = value.int32Value }
        if let value = fields["meanBaselineRRI"] as? NSNumber { proto.meanBaselineRri = value.int32Value }
        if let value = fields["sdBaselineRRI"] as? NSNumber { proto.sdBaselineRri = value.int32Value }
        if let value = fields["meanBaselineRMSSD"] as? NSNumber { proto.meanBaselineRmssd = value.int32Value }
        if let value = fields["sdBaselineRMSSD"] as? NSNumber { proto.sdBaselineRmssd = value.int32Value }
        if let value = fields["meanBaselineRespirationInterval"] as? NSNumber { proto.meanBaselineRespirationInterval = value.int32Value }
        if let value = fields["sdBaselineRespirationInterval"] as? NSNumber { proto.sdBaselineRespirationInterval = value.int32Value }
        if let value = fields["sleepTip"] as? String { proto.sleepTip = value }
        if let value = fields["vitalityTip"] as? String { proto.vitalityTip = value }
        if let value = fields["exerciseTip"] as? String { proto.exerciseTip = value }
        return proto
    }

    private func buildPbSystemDateTime(from fields: [String: Any], id: String) throws -> PbSystemDateTime {
        var proto = PbSystemDateTime()
        proto.date = try buildPbDate(from: try XCTUnwrap(fields["date"] as? [String: Any], id), id: id)
        let time = try XCTUnwrap(fields["time"] as? [String: Any], id)
        proto.time = PbTime.with {
            $0.hour = UInt32(try! number(time, "hour", id: id))
            $0.minute = UInt32(try! number(time, "minute", id: id))
            $0.seconds = UInt32(try! number(time, "second", id: id))
            $0.millis = UInt32(try! number(time, "millis", id: id))
        }
        proto.trusted = try XCTUnwrap(fields["trusted"] as? Bool, id)
        return proto
    }

    private func buildPbDate(from fields: [String: Any], id: String) throws -> PbDate {
        return PbDate.with {
            $0.year = UInt32(try! number(fields, "year", id: id))
            $0.month = UInt32(try! number(fields, "month", id: id))
            $0.day = UInt32(try! number(fields, "day", id: id))
        }
    }

    private func assertNightlyRechargeResult(_ actual: PolarNightlyRechargeData?, expected: [String: Any], id: String) throws {
        let result = try XCTUnwrap(actual, id)
        try assertDate(result.createdTimestamp, expected: try XCTUnwrap(expected["createdTimestamp"] as? String, id), id: "\(id) createdTimestamp")
        if expected["modifiedTimestamp"] is NSNull {
            XCTAssertNil(result.modifiedTimestamp, "\(id) modifiedTimestamp")
        } else {
            try assertDate(result.modifiedTimestamp, expected: try XCTUnwrap(expected["modifiedTimestamp"] as? String, id), id: "\(id) modifiedTimestamp")
        }
        XCTAssertEqual(try XCTUnwrap(result.ansStatus, id), try float(expected, "ansStatus", id: id), accuracy: 0.00001, id)
        XCTAssertEqual(result.recoveryIndicator, try number(expected, "recoveryIndicator", id: id), id)
        XCTAssertEqual(result.recoveryIndicatorSubLevel, try number(expected, "recoveryIndicatorSubLevel", id: id), id)
        XCTAssertEqual(result.ansRate, try number(expected, "ansRate", id: id), id)
        XCTAssertEqual(result.scoreRateObsolete, try number(expected, "scoreRateObsolete", id: id), id)
        XCTAssertEqual(result.meanNightlyRecoveryRRI, try number(expected, "meanNightlyRecoveryRRI", id: id), id)
        XCTAssertEqual(result.meanNightlyRecoveryRMSSD, try number(expected, "meanNightlyRecoveryRMSSD", id: id), id)
        XCTAssertEqual(result.meanNightlyRecoveryRespirationInterval, try number(expected, "meanNightlyRecoveryRespirationInterval", id: id), id)
        XCTAssertEqual(result.meanBaselineRRI, try number(expected, "meanBaselineRRI", id: id), id)
        XCTAssertEqual(result.sdBaselineRRI, try number(expected, "sdBaselineRRI", id: id), id)
        XCTAssertEqual(result.meanBaselineRMSSD, try number(expected, "meanBaselineRMSSD", id: id), id)
        XCTAssertEqual(result.sdBaselineRMSSD, try number(expected, "sdBaselineRMSSD", id: id), id)
        XCTAssertEqual(result.meanBaselineRespirationInterval, try number(expected, "meanBaselineRespirationInterval", id: id), id)
        XCTAssertEqual(result.sdBaselineRespirationInterval, try number(expected, "sdBaselineRespirationInterval", id: id), id)
        XCTAssertEqual(result.sleepTip, try XCTUnwrap(expected["sleepTip"] as? String, id), id)
        XCTAssertEqual(result.vitalityTip, try XCTUnwrap(expected["vitalityTip"] as? String, id), id)
        XCTAssertEqual(result.exerciseTip, try XCTUnwrap(expected["exerciseTip"] as? String, id), id)
        try assertDateComponents(result.sleepResultDate, expected: try XCTUnwrap(expected["sleepResultDate"] as? String, id), id: "\(id) sleepResultDate")
    }

    private func assertDate(_ actual: Date?, expected: String, id: String) throws {
        let date = try XCTUnwrap(actual, id)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
        XCTAssertEqual(formatter.string(from: date), expected, id)
    }

    private func assertDateComponents(_ actual: DateComponents?, expected: String, id: String) throws {
        let result = try XCTUnwrap(actual, id)
        let parts = expected.split(separator: "-").map(String.init)
        XCTAssertEqual(result.year, Int(try XCTUnwrap(parts[safe: 0], id)), id)
        XCTAssertEqual(result.month, Int(try XCTUnwrap(parts[safe: 1], id)), id)
        XCTAssertEqual(result.day, Int(try XCTUnwrap(parts[safe: 2], id)), id)
    }

    private func loadNightlyRechargeGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/nightly-recharge")
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
                return input?["kind"] as? String != "sleepNightlyReadiness"
            }
    }

    private func loadSleepNightlyReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/nightly-recharge/sleep-nightly-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private func number(_ object: [String: Any], _ key: String, id: String) throws -> Int {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").intValue
    }

    private func float(_ object: [String: Any], _ key: String, id: String) throws -> Float {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").floatValue
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        return indices.contains(index) ? self[index] : nil
    }
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "PolarNightlyRechargeUtilsTests", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "PolarNightlyRechargeUtilsTests", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
