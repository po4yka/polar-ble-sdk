import Foundation
import XCTest
@testable import PolarBleSdk

class PolarAutomaticSamplesUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testAutomaticSampleReadHeadersUseSharedFileFacadePlanning() {
        let directoryOperation = PolarAutomaticSamplesUtils.automaticSamplesDirectoryReadOperation()
        XCTAssertEqual(directoryOperation.command, .get)
        XCTAssertEqual(directoryOperation.path, "/U/0/AUTOS/")

        let fileOperation = PolarAutomaticSamplesUtils.automaticSamplesFileReadOperation(fileName: "AUTOS001.BPB")
        XCTAssertEqual(fileOperation.command, .get)
        XCTAssertEqual(fileOperation.path, "/U/0/AUTOS/AUTOS001.BPB")
    }

    func testPpiSampleStatusMappingIgnoresHighBitsThroughSharedKmpPolicy() {
        let status = Polar247PPiSamplesData.PPiSampleStatus.fromStatusByte(byte: 0xFF)
        XCTAssertEqual(status.skinContact, .SKIN_CONTACT_DETECTED)
        XCTAssertEqual(status.movement, .MOVING_DETECTED)
        XCTAssertEqual(status.intervalStatus, .INTERVAL_DENOTES_OFFLINE_PERIOD)
    }

    func testRead247HrSamples_SuccessfulResponse() async throws {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 10, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 19, hour: 0, minute: 0, second: 0))!

        let mockDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()

        let mockFileContent1 = try Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [60, 61, 63].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 10; $0.minute = 12; $0.seconds = 34; $0.millis = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeHighActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [80, 81, 83].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 12; $0.minute = 0; $0.seconds = 0; $0.millis = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeManual
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 15 }
        }.serializedData()

        let mockFileContent2 = try Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [70, 72, 74].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 16; $0.minute = 49; $0.seconds = 36; $0.millis = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeLowActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [90, 91, 93].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 18; $0.minute = 0; $0.seconds = 0; $0.millis = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeTimed
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 18 }
        }.serializedData()

        mockClient.requestReturnValues = [
            .success(mockDirectoryContent),
            .success(mockFileContent1),
            .success(mockFileContent2)
        ]

        // Act
        let samples = try await PolarAutomaticSamplesUtils.read247HrSamples(client: mockClient, fromDate: fromDate, toDate: toDate)

        // Assert
        XCTAssertEqual(samples.count, 2)

        let date1 = DateComponents(year: 2024, month: 11, day: 15)
        XCTAssertEqual(samples[0].date, date1)
        XCTAssertEqual(samples[0].samples[0].hrSamples, [60, 61, 63])
        XCTAssertEqual(samples[0].samples[0].triggerType, .highActivity)

        let date2 = DateComponents(year: 2024, month: 11, day: 15)
        XCTAssertEqual(samples[0].date, date2)
        XCTAssertEqual(samples[0].samples[1].hrSamples, [80, 81, 83])
        XCTAssertEqual(samples[0].samples[1].triggerType, .manual)

        let date3 = DateComponents(year: 2024, month: 11, day: 18)
        XCTAssertEqual(samples[1].date, date3)
        XCTAssertEqual(samples[1].samples[0].hrSamples, [70, 72, 74])
        XCTAssertEqual(samples[1].samples[0].triggerType, .lowActivity)

        let date4 = DateComponents(year: 2024, month: 11, day: 18)
        XCTAssertEqual(samples[1].date, date4)
        XCTAssertEqual(samples[1].samples[1].hrSamples, [90, 91, 93])
        XCTAssertEqual(samples[1].samples[1].triggerType, .timed)
    }

    func testRead247HrSamples_FilterOutSamplesOutsideDateRange() async throws {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 20, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(year: 2024, month: 11, day: 9, hour: 0, minute: 0, second: 0))!

        let mockDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()

        let mockFileContent1 = try Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [60, 61, 63].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 10; $0.minute = 12; $0.seconds = 34 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeHighActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [80, 81, 83].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 12; $0.minute = 0; $0.seconds = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeManual
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 15 }
        }.serializedData()

        let mockFileContent2 = try Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [70, 72, 74].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 16; $0.minute = 49; $0.seconds = 36 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeLowActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [90, 91, 93].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 18; $0.minute = 0; $0.seconds = 0 }
                    $0.triggerType = Data_PbMeasTriggerType.triggerTypeTimed
                }
            ]
            $0.day = PbDate.with { $0.year = 2024; $0.month = 11; $0.day = 18 }
        }.serializedData()

        mockClient.requestReturnValues = [
            .success(mockDirectoryContent),
            .success(mockFileContent1),
            .success(mockFileContent2)
        ]

        // Act
        let samples = try await PolarAutomaticSamplesUtils.read247HrSamples(client: mockClient, fromDate: fromDate, toDate: toDate)

        // Assert
        XCTAssertEqual(samples.count, 0)
    }

    func testRead247PPiSamples_SuccessfulResponse() async throws {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 24, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 27, hour: 0, minute: 0, second: 0))!

        let mockDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()

        let mockFileContent1 = try Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 4 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 2; $0.minute = 3; $0.seconds = 4; $0.millis = 5 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 25 }
        }.serializedData()

        let mockFileContent2 = try Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 3; $0.minute = 4; $0.seconds = 5; $0.millis = 6 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 5; $0.minute = 6; $0.seconds = 7; $0.millis = 8 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 26 }
        }.serializedData()

        mockClient.requestReturnValues = [
            .success(mockDirectoryContent),
            .success(mockFileContent1),
            .success(mockFileContent2)
        ]

        // Act
        let samples = try await PolarAutomaticSamplesUtils.read247PPiSamples(client: mockClient, fromDate: fromDate, toDate: toDate)

        // Assert
        XCTAssertEqual(samples.count, 2)

        let date1 = DateComponents(year: 2525, month: 2, day: 25)
        let date2 = DateComponents(year: 2525, month: 2, day: 26)

        XCTAssertEqual(samples[0].date, date1)
        XCTAssertEqual(samples[1].date, date2)
        XCTAssertEqual(samples[0].samples.count, 2)
        XCTAssertEqual(samples[0].samples[0].ppiValueList, [2000, 1900, 1700, 1400])
        XCTAssertEqual(samples[0].samples[0].ppiErrorEstimateList, [10, 11, 13, 16])
        XCTAssertEqual(samples[0].samples[0].statusList[0].intervalStatus, Polar247PPiSamplesData.IntervalStatus.INTERVAL_IS_ONLINE)
        XCTAssertEqual(samples[0].samples[0].statusList[0].movement, Polar247PPiSamplesData.Movement.NO_MOVING_DETECTED)
        XCTAssertEqual(samples[0].samples[0].triggerType, Polar247PPiSamplesData.PPiSampleTriggerType.TRIGGER_TYPE_AUTOMATIC)
        XCTAssertEqual(samples[0].samples[0].startTime, "01:02:03.04")
    }

    func testRead247PPiSamples_filterOutSamplesOutsideDateRange() async throws {
        // Arrange
        let calendar = Calendar(identifier: .gregorian)
        let fromDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 24, hour: 0, minute: 0, second: 0))!
        let toDate = calendar.date(from: DateComponents(timeZone: TimeZone.gmt, year: 2525, month: 2, day: 25, hour: 0, minute: 0, second: 0))!

        let mockDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS000.BPB"; $0.size = 333 },
                Protocol_PbPFtpEntry.with { $0.name = "AUTOS001.BPB"; $0.size = 444 }
            ]
        }.serializedData()

        let mockFileContent1 = try Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 4 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 2; $0.minute = 3; $0.seconds = 4; $0.millis = 5 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 25 }
        }.serializedData()

        let mockFileContent2 = try Data_PbAutomaticSampleSessions.with {
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 3; $0.minute = 4; $0.seconds = 5; $0.millis = 6 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 5; $0.minute = 6; $0.seconds = 7; $0.millis = 8 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 26 }
        }.serializedData()

        mockClient.requestReturnValues = [
            .success(mockDirectoryContent),
            .success(mockFileContent1),
            .success(mockFileContent2)
        ]

        // Act
        let samples = try await PolarAutomaticSamplesUtils.read247PPiSamples(client: mockClient, fromDate: fromDate, toDate: toDate)

        // Assert
        XCTAssertEqual(samples.count, 1)
    }

    func testAutomaticSampleGoldenVectorsMapProtoModels() throws {
        for vector in try loadAutomaticSampleGoldenVectors() {
            let input = try XCTUnwrap(vector["input"] as? [String: Any])
            let kind = try XCTUnwrap(input["kind"] as? String)
            switch kind {
            case "hr":
                try assertHrVector(vector)
            case "ppi":
                try assertPpiVector(vector)
            default:
                XCTFail("Unknown automatic sample vector kind: \(kind)")
            }
        }
    }

    func testPpiStatusValueHelpersPreservePublicMapping() {
        XCTAssertEqual(.NO_SKIN_CONTACT, Polar247PPiSamplesData.SkinContact.getByValue(value: 0))
        XCTAssertEqual(.SKIN_CONTACT_DETECTED, Polar247PPiSamplesData.SkinContact.getByValue(value: 1))
        XCTAssertNil(Polar247PPiSamplesData.SkinContact.getByValue(value: 2))
        XCTAssertEqual(.NO_MOVING_DETECTED, Polar247PPiSamplesData.Movement.getByValue(value: 0))
        XCTAssertEqual(.MOVING_DETECTED, Polar247PPiSamplesData.Movement.getByValue(value: 1))
        XCTAssertNil(Polar247PPiSamplesData.Movement.getByValue(value: 2))
        XCTAssertEqual(.INTERVAL_IS_ONLINE, Polar247PPiSamplesData.IntervalStatus.getByValue(value: 0))
        XCTAssertEqual(.INTERVAL_DENOTES_OFFLINE_PERIOD, Polar247PPiSamplesData.IntervalStatus.getByValue(value: 1))
        XCTAssertNil(Polar247PPiSamplesData.IntervalStatus.getByValue(value: 2))
    }

    func testAutomaticSampleGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadAutomaticSampleGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["kind"], id)
            XCTAssertNotNil(input["proto"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testActivitySummaryReadinessManifestIsPinnedBeforeAutomaticSampleMigration() throws {
        let readiness = try loadActivitySummaryReadinessManifest()
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(readiness["id"] as? String, "activity-summary-readiness")
        XCTAssertEqual(input["kind"] as? String, "activitySummaryReadiness")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/activity-samples/two-files-step-aggregation.json",
            "sdk/activity-samples/malformed-sample-file-platform-policy.json",
            "sdk/automatic-samples/hr-all-trigger-types.json",
            "sdk/automatic-samples/ppi-deltas-statuses.json",
            "sdk/daily-summary/full-summary.json"
        ])
        let expectedFamilies = [
            "activity-file-request-paths",
            "activity-step-aggregation",
            "activity-interval-projection",
            "activity-info-projection",
            "malformed-activity-sample-platform-policy",
            "automatic-hr-trigger-mapping",
            "automatic-hr-array-preservation",
            "automatic-ppi-delta-decompression",
            "automatic-ppi-status-bit-mapping",
            "daily-summary-request-path",
            "daily-summary-scalar-projection",
            "daily-summary-duration-projection",
            "platform-activity-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Activity, automatic-sample, and daily-summary migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS activity/automatic/daily tests continue to reference the same vectors, activity request paths, aggregation, intervals, activity-info projection, malformed activity-sample behavior, automatic HR trigger and heart-rate arrays, PPI delta/status decoding, daily-summary path/scalar/duration projection, and compile verification remain explicit before production model mapping moves.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), [
            "com.polar.sdk.api.model.utils.PolarActivityUtilsTest",
            "com.polar.sdk.api.model.utils.PolarAutomaticSamplesUtilsTest"
        ])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), [
            "PolarActivityUtilsTest",
            "PolarAutomaticSamplesUnitTest"
        ])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.ActivitySummaryCommonPolicyTest"])
    }

    private func assertHrVector(_ vector: [String: Any]) throws {
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let protoFields = try XCTUnwrap(input["proto"] as? [String: Any])
        let proto = try buildAutomaticSampleSessions(from: protoFields)
        let actualSamples = try Polar247HrSamplesData.fromPbHrDataSamples(samples: proto.samples)
        let expected = try expectedForIOS(vector)
        let expectedSamples = try XCTUnwrap(expected["samples"] as? [[String: Any]])
        let vectorId = try XCTUnwrap(vector["id"] as? String)

        XCTAssertEqual(actualSamples.count, expectedSamples.count, vectorId)
        for (index, expectedSample) in expectedSamples.enumerated() {
            let actual = actualSamples[index]
            let expectedTime = try XCTUnwrap(expectedSample["time"] as? [String: Any])
            XCTAssertEqual(actual.time.hour, try number(expectedTime, "hour"))
            XCTAssertEqual(actual.time.minute, try number(expectedTime, "minute"))
            XCTAssertEqual(actual.time.second, try number(expectedTime, "second"))
            XCTAssertEqual(actual.time.nanosecond ?? 0, try number(expectedTime, "nanosecond"))
            XCTAssertEqual(actual.hrSamples.map { Int($0) }, try XCTUnwrap(expectedSample["heartRate"] as? [Int]))
            XCTAssertEqual(actual.triggerType?.rawValue, try XCTUnwrap(expectedSample["triggerType"] as? String))
        }
    }

    private func assertPpiVector(_ vector: [String: Any]) throws {
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let protoFields = try XCTUnwrap(input["proto"] as? [String: Any])
        let sampleFields = try XCTUnwrap(protoFields["sample"] as? [String: Any])
        let actual = Polar247PPiSamplesData.fromPbPPiDataSamples(ppiData: try buildPpiSample(from: sampleFields))
        let expected = try expectedForIOS(vector)

        XCTAssertEqual(actual.startTime, try XCTUnwrap(expected["startTime"] as? String))
        XCTAssertEqual(actual.triggerType.rawValue, try XCTUnwrap(expected["triggerType"] as? String))
        XCTAssertEqual(actual.ppiValueList.map { Int($0) }, try XCTUnwrap(expected["ppiValueList"] as? [Int]))
        XCTAssertEqual(actual.ppiErrorEstimateList.map { Int($0) }, try XCTUnwrap(expected["ppiErrorEstimateList"] as? [Int]))
        let expectedStatuses = try XCTUnwrap(expected["statusList"] as? [[String: Any]])
        XCTAssertEqual(actual.statusList.count, expectedStatuses.count)
        for (index, expectedStatus) in expectedStatuses.enumerated() {
            XCTAssertEqual(actual.statusList[index].skinContact.rawValue, try XCTUnwrap(expectedStatus["skinContact"] as? String))
            XCTAssertEqual(actual.statusList[index].movement.rawValue, try XCTUnwrap(expectedStatus["movement"] as? String))
            XCTAssertEqual(actual.statusList[index].intervalStatus.rawValue, try XCTUnwrap(expectedStatus["intervalStatus"] as? String))
        }
    }

    private func buildAutomaticSampleSessions(from fields: [String: Any]) throws -> Data_PbAutomaticSampleSessions {
        var proto = Data_PbAutomaticSampleSessions()
        let day = try XCTUnwrap(fields["day"] as? [String: Any])
        proto.day = PbDate.with {
            $0.year = UInt32(try! number(day, "year"))
            $0.month = UInt32(try! number(day, "month"))
            $0.day = UInt32(try! number(day, "day"))
        }
        proto.samples = try XCTUnwrap(fields["samples"] as? [[String: Any]]).map { sample in
            Data_PbAutomaticHeartRateSamples.with {
                $0.heartRate = (sample["heartRate"] as? [Int] ?? []).map { UInt32($0) }
                $0.time = buildPbTime(from: sample["time"] as? [String: Any] ?? [:])
                $0.triggerType = Data_PbMeasTriggerType(rawValue: (sample["triggerType"] as? NSNumber)?.intValue ?? 1) ?? .triggerTypeHighActivity
            }
        }
        return proto
    }

    private func buildPpiSample(from sample: [String: Any]) throws -> Data_PbPpIntervalAutoSamples {
        return Data_PbPpIntervalAutoSamples.with {
            $0.recordingTime = buildPbTime(from: sample["recordingTime"] as? [String: Any] ?? [:])
            $0.triggerType = Data_PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType(rawValue: (sample["triggerType"] as? NSNumber)?.intValue ?? 0) ?? .ppiTriggerTypeUndefined
            $0.ppi = Data_PbPpIntervalSamples.with {
                $0.ppiDelta = (sample["ppiDelta"] as? [Int] ?? []).map { Int32($0) }
                $0.ppiErrorEstimateDelta = (sample["ppiErrorEstimateDelta"] as? [Int] ?? []).map { Int32($0) }
                $0.status = (sample["status"] as? [Int] ?? []).map { UInt32($0) }
            }
        }
    }

    private func buildPbTime(from time: [String: Any]) -> PbTime {
        return PbTime.with {
            $0.hour = UInt32((time["hour"] as? NSNumber)?.intValue ?? 0)
            $0.minute = UInt32((time["minute"] as? NSNumber)?.intValue ?? 0)
            $0.seconds = UInt32((time["second"] as? NSNumber)?.intValue ?? 0)
            $0.millis = UInt32((time["millis"] as? NSNumber)?.intValue ?? 0)
        }
    }

    private func loadAutomaticSampleGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/automatic-samples")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

    private func loadActivitySummaryReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/activity-samples/activity-summary-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private func expectedForIOS(_ vector: [String: Any]) throws -> [String: Any] {
        let platforms = try XCTUnwrap(vector["platformExpectations"] as? [String: Any])
        return try XCTUnwrap(platforms["ios"] as? [String: Any])
    }

    private func number(_ object: [String: Any], _ key: String) throws -> Int {
        return try XCTUnwrap(object[key] as? NSNumber).intValue
    }
}
