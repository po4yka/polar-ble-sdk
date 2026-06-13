import Foundation
import XCTest
import Combine
import zlib
@testable import PolarBleSdk

final class PolarTrainingSessionUtilsTests: XCTestCase {

    private var mockClient: MockBlePsFtpClient!
    private var cancellables = Set<AnyCancellable>()

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
        cancellables.removeAll()
    }

    func testTrainingSessionReadAndDeleteHeadersUseSharedFileFacadePlanning() throws {
        let reference = PolarTrainingSessionReference(
            date: try XCTUnwrap(DateComponents(calendar: Calendar(identifier: .gregorian), year: 2026, month: 1, day: 2, hour: 12, minute: 34, second: 56).date),
            path: "/U/0/20260102/E/123456/TSESS.BPB",
            trainingDataTypes: [.trainingSessionSummary],
            exercises: [],
            fileSize: 1024
        )

        let summaryOperation = PolarTrainingSessionUtils.trainingSessionSummaryReadOperation(path: reference.path)
        XCTAssertEqual(summaryOperation.command, .get)
        XCTAssertEqual(summaryOperation.path, "/U/0/20260102/E/123456/TSESS.BPB")

        let exerciseOperation = PolarTrainingSessionUtils.trainingSessionExerciseFileReadOperation(path: "/U/0/20260102/E/123456/00/BASE.BPB")
        XCTAssertEqual(exerciseOperation.command, .get)
        XCTAssertEqual(exerciseOperation.path, "/U/0/20260102/E/123456/00/BASE.BPB")

        let directoryOperation = PolarTrainingSessionUtils.trainingSessionDirectoryReadOperation(path: "/U/0/20260102/E/123456/")
        XCTAssertEqual(directoryOperation.command, .get)
        XCTAssertEqual(directoryOperation.path, "/U/0/20260102/E/123456/")

        let parentOperation = PolarTrainingSessionUtils.trainingSessionDeleteParentReadOperation(reference: reference)
        XCTAssertEqual(parentOperation.command, .get)
        XCTAssertEqual(parentOperation.path, "/U/0/20260102/E/")

        let removeWholeDayOperation = PolarTrainingSessionUtils.trainingSessionDeleteRemoveOperation(reference: reference, parentEntryCount: 1)
        XCTAssertEqual(removeWholeDayOperation.command, .remove)
        XCTAssertEqual(removeWholeDayOperation.path, "/U/0/20260102/E/")

        let removeSessionOperation = PolarTrainingSessionUtils.trainingSessionDeleteRemoveOperation(reference: reference, parentEntryCount: 2)
        XCTAssertEqual(removeSessionOperation.command, .remove)
        XCTAssertEqual(removeSessionOperation.path, "/U/0/20260102/E/123456/")
    }

    func testTrainingSessionFileClassificationUsesSharedBridgeWhenLinked() async throws {
        let date = "20250101"
        let time = "123000"
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [.with { $0.name = "\(date)/"; $0.size = 0 }],
            "/U/0/\(date)/": [.with { $0.name = "E/"; $0.size = 0 }],
            "/U/0/\(date)/E/": [.with { $0.name = "\(time)/"; $0.size = 0 }],
            "/U/0/\(date)/E/\(time)/": [
                .with { $0.name = "TSESS.BPB"; $0.size = 1024 },
                .with { $0.name = "00/"; $0.size = 0 }
            ],
            "/U/0/\(date)/E/\(time)/00/": [
                .with { $0.name = "BASE.BPB"; $0.size = 2048 },
                .with { $0.name = "SAMPLES2.GZB"; $0.size = 4096 }
            ]
        ]
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            return try Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }.serializedData()
        }

        let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(client: mockClient)

        XCTAssertEqual(references.first?.trainingDataTypes, [.trainingSessionSummary])
        XCTAssertEqual(references.first?.exercises.first?.exerciseDataTypes, [.exerciseSummary, .samplesAdvancedFormatGzip])
    }

    func testTrainingSessionExerciseFilenamesUseSharedBridgeWhenLinked() throws {
        XCTAssertEqual(PolarExerciseDataTypes.exerciseSummary.rawValue, "BASE.BPB")
        XCTAssertEqual(PolarExerciseDataTypes.samplesAdvancedFormatGzip.rawValue, "SAMPLES2.GZB")
        XCTAssertEqual(PolarTrainingSessionUtils.trainingSessionExerciseDataTypeFileName(dataType: .exerciseSummary), PolarExerciseDataTypes.exerciseSummary.rawValue)
        XCTAssertEqual(PolarTrainingSessionUtils.trainingSessionExerciseDataTypeFileName(dataType: .samplesAdvancedFormatGzip), PolarExerciseDataTypes.samplesAdvancedFormatGzip.rawValue)
    }

    func testTrainingSessionPayloadFetchOrderUsesSharedPlanner() throws {
        let reference = PolarTrainingSessionReference(
            date: try XCTUnwrap(DateComponents(calendar: Calendar(identifier: .gregorian), year: 2026, month: 1, day: 2, hour: 12, minute: 34, second: 56).date),
            path: "/U/0/20260102/E/123456/TSESS.BPB",
            trainingDataTypes: [.trainingSessionSummary],
            exercises: [
                PolarExercise(
                    index: 0,
                    path: "/U/0/20260102/E/123456/00",
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .samplesAdvancedFormatGzip],
                    fileSizes: [
                        "BASE.BPB": 10,
                        "ROUTE.BPB": 20,
                        "ROUTE.GZB": 30,
                        "SAMPLES2.GZB": 40
                    ]
                )
            ],
            fileSize: 100
        )

        XCTAssertEqual(
            [
                "/U/0/20260102/E/123456/TSESS.BPB",
                "/U/0/20260102/E/123456/00/BASE.BPB",
                "/U/0/20260102/E/123456/00/ROUTE.BPB",
                "/U/0/20260102/E/123456/00/ROUTE.GZB",
                "/U/0/20260102/E/123456/00/SAMPLES2.GZB"
            ],
            PolarTrainingSessionUtils.trainingSessionPayloadFetchOrder(reference: reference)
        )
        let readPlan = PolarTrainingSessionUtils.trainingSessionPayloadReadPlan(reference: reference)
        XCTAssertEqual(readPlan.map(\.path), [
            "/U/0/20260102/E/123456/TSESS.BPB",
            "/U/0/20260102/E/123456/00/BASE.BPB",
            "/U/0/20260102/E/123456/00/ROUTE.BPB",
            "/U/0/20260102/E/123456/00/ROUTE.GZB",
            "/U/0/20260102/E/123456/00/SAMPLES2.GZB"
        ])
        XCTAssertEqual(readPlan.map(\.fileName), ["TSESS.BPB", "BASE.BPB", "ROUTE.BPB", "ROUTE.GZB", "SAMPLES2.GZB"])
        XCTAssertEqual(readPlan.map(\.publicModelSlot), ["sessionSummary", "exerciseSummary", "route", "route", "samplesAdvanced"])
        XCTAssertEqual(readPlan.map(\.exerciseIndex), [nil, 0, 0, 0, 0])
    }

    func testTrainingSessionProgressPercentUsesSharedClampPolicy() throws {
        XCTAssertEqual(PolarRuntimePlanner.trainingSessionProgressPercent(completedBytes: 0, totalBytes: 0), 0)
        XCTAssertEqual(PolarRuntimePlanner.trainingSessionProgressPercent(completedBytes: 25, totalBytes: 100), 25)
        XCTAssertEqual(PolarRuntimePlanner.trainingSessionProgressPercent(completedBytes: 125, totalBytes: 100), 100)
        XCTAssertEqual(PolarRuntimePlanner.trainingSessionProgressPercent(completedBytes: -5, totalBytes: 100), 0)
        XCTAssertEqual(PolarRuntimePlanner.trainingSessionReferenceDateMatches(date: "2024-02-29", fromDate: "2024-02-28", toDate: "2024-03-01"), true)
        XCTAssertEqual(PolarRuntimePlanner.trainingSessionReferenceDateMatches(date: "2024-03-02", fromDate: "2024-02-28", toDate: "2024-03-01"), false)
    }

    // MARK: - Helpers

    private func awaitFirst<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 5) throws -> T? {
        var result: T?
        var receivedError: Error?
        let expectation = XCTestExpectation(description: "publisher completes")
        publisher
            .first()
            .sink(receiveCompletion: { completion in
                if case .failure(let e) = completion { receivedError = e }
                expectation.fulfill()
            }, receiveValue: { result = $0 })
            .store(in: &cancellables)
        wait(for: [expectation], timeout: timeout)
        if let e = receivedError { throw e }
        return result
    }

    // MARK: - Tests

    func test_getTrainingSessionReferences_shouldReturnAllTrainingSessionReferences() async throws {
        // Arrange
        let date1 = "20250101"
        let time1 = "123000"
        let path1 = "/U/0/\(date1)/E/\(time1)/TSESS.BPB"

        let date2 = "20250201"
        let time2 = "134500"
        let path2 = "/U/0/\(date2)/E/\(time2)/TSESS.BPB"

        let entry1 = Protocol_PbPFtpEntry.with { $0.name = "20250101/"; $0.size = 0 }
        let entry2 = Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }
        let entry3 = Protocol_PbPFtpEntry.with { $0.name = "123000/"; $0.size = 0 }
        let entry4 = Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 1024 }

        let exerciseFolder00 = Protocol_PbPFtpEntry.with { $0.name = "00/"; $0.size = 0 }
        let exerciseFile00 = Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 2048 }
        let routeFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.BPB"; $0.size = 2048 }
        let routeGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.GZB"; $0.size = 2048 }
        let routeAdvancedFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.BPB"; $0.size = 2048 }
        let routeAdvancedGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.GZB"; $0.size = 2048 }
        let samplesFile00 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.BPB"; $0.size = 2048 }
        let samplesGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.GZB"; $0.size = 2048 }
        let samplesAdvancedGzipFile00 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES2.GZB"; $0.size = 2048 }

        let exerciseFolder01 = Protocol_PbPFtpEntry.with { $0.name = "01/"; $0.size = 0 }
        let exerciseFile01 = Protocol_PbPFtpEntry.with { $0.name = "BASE.BPB"; $0.size = 4096 }
        let routeFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.BPB"; $0.size = 2048 }
        let routeGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE.GZB"; $0.size = 2048 }
        let routeAdvancedFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.BPB"; $0.size = 2048 }
        let routeAdvancedGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "ROUTE2.GZB"; $0.size = 2048 }
        let samplesFile01 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.BPB"; $0.size = 2048 }
        let samplesGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES.GZB"; $0.size = 2048 }
        let samplesAdvancedGzipFile01 = Protocol_PbPFtpEntry.with { $0.name = "SAMPLES2.GZB"; $0.size = 2048 }

        let entry5 = Protocol_PbPFtpEntry.with { $0.name = "20250201/"; $0.size = 0 }
        let entry6 = Protocol_PbPFtpEntry.with { $0.name = "E/"; $0.size = 0 }
        let entry7 = Protocol_PbPFtpEntry.with { $0.name = "134500/"; $0.size = 0 }
        let entry8 = Protocol_PbPFtpEntry.with { $0.name = "TSESS.BPB"; $0.size = 1024 }

        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/": [entry1, entry5],
            "/U/0/20250101/": [entry2],
            "/U/0/20250101/E/": [entry3],
            "/U/0/20250101/E/123000/": [entry4, exerciseFolder00, exerciseFolder01],
            "/U/0/20250101/E/123000/00/": [
                exerciseFile00, routeFile00, routeGzipFile00, routeAdvancedFile00, routeAdvancedGzipFile00,
                samplesFile00, samplesGzipFile00, samplesAdvancedGzipFile00
            ],
            "/U/0/20250101/E/123000/01/": [
                exerciseFile01, routeFile01, routeGzipFile01, routeAdvancedFile01, routeAdvancedGzipFile01,
                samplesFile01, samplesGzipFile01, samplesAdvancedGzipFile01
            ],
            "/U/0/20250201/": [entry6],
            "/U/0/20250201/E/": [entry7],
            "/U/0/20250201/E/134500/": [entry8]
        ]

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            let path = op.path
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = responses[path, default: []]
            }
            return try dir.serializedData()
        }

        // Act
        let references = try await PolarTrainingSessionUtils
            .getTrainingSessionReferences(client: mockClient)

        // Assert
        XCTAssertEqual(references.count, 2)

        let session1 = references[0]
        XCTAssertEqual(session1.path, path1)
        XCTAssertEqual(session1.trainingDataTypes, [PolarTrainingSessionDataTypes.trainingSessionSummary])
        XCTAssertEqual(session1.exercises.count, 2)

        XCTAssertEqual(session1.exercises[0].path, "/U/0/20250101/E/123000/00")
        XCTAssertEqual(session1.exercises[0].exerciseDataTypes, [
            PolarExerciseDataTypes.exerciseSummary,
            .route,
            .routeGzip,
            .routeAdvancedFormat,
            .routeAdvancedFormatGzip,
            .samples,
            .samplesGzip,
            .samplesAdvancedFormatGzip
        ])

        XCTAssertEqual(session1.exercises[1].path, "/U/0/20250101/E/123000/01")
        XCTAssertEqual(session1.exercises[1].exerciseDataTypes, [
            PolarExerciseDataTypes.exerciseSummary,
            .route,
            .routeGzip,
            .routeAdvancedFormat,
            .routeAdvancedFormatGzip,
            .samples,
            .samplesGzip,
            .samplesAdvancedFormatGzip
        ])

        let session2 = references[1]
        XCTAssertEqual(session2.path, path2)
        XCTAssertEqual(session2.trainingDataTypes, [PolarTrainingSessionDataTypes.trainingSessionSummary])
        XCTAssertTrue(session2.exercises.isEmpty)
    }

    func testTrainingSessionReferenceDiscoveryGoldenVectorsMatchIOSBehavior() async throws {
        let vectors = try loadTrainingSessionGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected training-session golden vectors")

        for vector in vectors {
            let caseId = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], caseId)
            guard let directories = input["directories"] as? [String: [[String: Any]]] else {
                continue
            }
            mockClient.requestReturnValueClosure = { header in
                let op = try Protocol_PbPFtpOperation(serializedBytes: header)
                return try self.buildDirectory(entries: directories[op.path, default: []]).serializedData()
            }

            let references = try await PolarTrainingSessionUtils.getTrainingSessionReferences(client: mockClient)

            try assertTrainingSessionReferences(references, expected: try XCTUnwrap(vector["expected"] as? [String: Any], caseId), id: caseId)
        }
    }

    func testTrainingSessionReadGoldenVectorsPreserveMissingExerciseFilePolicy() async throws {
        let vector = try loadTrainingSessionGoldenVectors().first { ($0["id"] as? String) == "missing-exercise-file-platform-policy" }
        let caseId = try XCTUnwrap(vector?["id"] as? String)
        let input = try XCTUnwrap(vector?["input"] as? [String: Any], caseId)
        let reference = try buildTrainingSessionReference(from: try XCTUnwrap(input["reference"] as? [String: Any], caseId))
        let responses = try XCTUnwrap(input["responses"] as? [String: String], caseId)

        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            guard let responseType = responses[op.path] else {
                throw NSError(domain: "TrainingSessionVector", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing file: \(op.path)"])
            }
            return try self.trainingSessionFixtureData(responseType)
        }

        do {
            _ = try await PolarTrainingSessionUtils.readTrainingSession(client: mockClient, reference: reference)
            XCTFail("Expected readTrainingSession to throw", file: #filePath, line: #line)
        } catch {
            XCTAssertFalse(error.localizedDescription.isEmpty, caseId)
        }
    }

    func testTrainingSessionGoldenVectorsFollowNeutralKmpShape() throws {
        let vectors = try loadTrainingSessionGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected training-session golden vectors")
        for vector in vectors {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["kind"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testPayloadParserPolicyVectorIsPinnedBeforeByteLevelParserMigration() throws {
        let vector = try XCTUnwrap(try loadTrainingSessionGoldenVectors().first { ($0["id"] as? String) == "payload-parser-policy" })
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        let commonParserPrototype = try XCTUnwrap(expected["commonParserPrototype"] as? [String: Any], "payload-parser-policy")
        let prototypeCases = try XCTUnwrap(commonParserPrototype["cases"] as? [[String: Any]], "payload-parser-policy")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "payload-parser-policy")

        XCTAssertEqual(input["kind"] as? String, "payloadParserPolicy", "payload-parser-policy")
        XCTAssertEqual(cases.compactMap { $0["id"] as? String }, TRAINING_SESSION_PAYLOAD_PARSER_CASE_IDS, "payload-parser-policy")
        XCTAssertEqual(prototypeCases.compactMap { $0["id"] as? String }, TRAINING_SESSION_PAYLOAD_PARSER_CASE_IDS, "payload-parser-policy")
        for (inputCase, prototypeCase) in zip(cases, prototypeCases) {
            let id = try XCTUnwrap(inputCase["id"] as? String, "payload-parser-policy")
            let fileName = try XCTUnwrap(inputCase["fileName"] as? String, id)
            let planned = try XCTUnwrap(PolarTrainingSessionUtils.trainingSessionPayloadParserCase(fileName: fileName), id)
            XCTAssertEqual(inputCase["parser"] as? String, prototypeCase["parser"] as? String, id)
            XCTAssertEqual(inputCase["encoding"] as? String, prototypeCase["encoding"] as? String, id)
            XCTAssertEqual(inputCase["publicModelSlot"] as? String, prototypeCase["publicModelSlot"] as? String, id)
            XCTAssertEqual(inputCase["expectedFields"] as? [String], prototypeCase["fields"] as? [String], id)
            XCTAssertEqual(planned.parser, inputCase["parser"] as? String, id)
            XCTAssertEqual(planned.encoding, inputCase["encoding"] as? String, id)
            XCTAssertEqual(PolarTrainingSessionUtils.trainingSessionPayloadEncoding(fileName: fileName), inputCase["encoding"] as? String, id)
            XCTAssertEqual(PolarTrainingSessionUtils.trainingSessionPublicModelSlot(fileName: fileName), inputCase["publicModelSlot"] as? String, id)
        }
        XCTAssertEqual(cases.filter { ($0["encoding"] as? String) == "gzip-protobuf" }.count, 4, "payload-parser-policy")
        XCTAssertEqual(cases.compactMap { $0["publicModelSlot"] as? String }, ["sessionSummary", "exerciseSummary", "route", "route", "routeAdvanced", "routeAdvanced", "samples", "samples", "samplesAdvanced"], "payload-parser-policy")
        XCTAssertEqual(commonParserPrototype["status"] as? String, "executable shared parser-policy coverage; gzip decoding and selected protobuf field parsing are shared while generated public model reconstruction remains platform-owned", "payload-parser-policy")
        XCTAssertEqual(expected["commonDecision"] as? String, TRAINING_SESSION_PAYLOAD_PARSER_COMMON_DECISION, "payload-parser-policy")
        XCTAssertEqual(consumerTests["android"] as? [String], ["com.polar.sdk.api.model.utils.PolarTrainingSessionUtilsTest"], "payload-parser-policy")
        XCTAssertEqual(consumerTests["ios"] as? [String], ["PolarTrainingSessionUtilsTest"], "payload-parser-policy")
        XCTAssertEqual(consumerTests["commonPrototype"] as? [String], ["com.polar.sharedtest.TrainingSessionCommonPolicyTest"], "payload-parser-policy")
    }

    func testTrainingSessionGzipPayloadDecodingDelegatesToSharedCodecAndPreservesMalformedThrowing() throws {
        let compressed = try data(hex: "1f8b0800d7bd2a6a02ff2b294acccccbcc4bd72d4e2d2ececccfd34dafca2cd02d48acccc94f4c0100a58206c51d000000")
        let expected = Data("training-session-gzip-payload".utf8)
        let decoded = try PolarTrainingSessionUtils.decodePayload(fileName: "ROUTE.GZB", data: compressed)

        XCTAssertEqual(decoded, expected)
        XCTAssertEqual(try PolarTrainingSessionUtils.decodePayload(fileName: "BASE.BPB", data: expected), expected)
        XCTAssertThrowsError(try PolarTrainingSessionUtils.decodePayload(fileName: "ROUTE.GZB", data: Data([0x01, 0x02, 0x03])))
    }

    func testTrainingSessionDecodedProtobufMalformedPreflightUsesSharedBridgeWhenLinked() throws {
        let validSummaryNameOnly = Data([0x22, 0x01, 0x50])
        let truncatedSummaryName = Data([0x22, 0x05, 0x50])

        XCTAssertFalse(PolarRuntimePlanner.trainingSessionPayloadMalformed(fileName: "TSESS.BPB", payload: validSummaryNameOnly))
        XCTAssertTrue(PolarRuntimePlanner.trainingSessionPayloadMalformed(fileName: "TSESS.BPB", payload: truncatedSummaryName))
    }

    func testTrainingSessionPayloadReadResultUsesSharedBridgeWhenLinked() throws {
        let referenceText = [
            "R|2026-01-02T12:34:56|/U/0/20260102/E/123456/TSESS.BPB|TRAINING_SESSION_SUMMARY|12",
            "E|0|/U/0/20260102/E/123456/00/BASE.BPB|/U/0/20260102/E/123456/00|SAMPLES|SAMPLES.BPB:2"
        ].joined(separator: "\n")
        let result = PolarRuntimePlanner.trainingSessionPayloadReadResult(
            referenceText: referenceText,
            responses: [
                (path: "/U/0/20260102/E/123456/TSESS.BPB", fileName: "TSESS.BPB", payload: Data([0x22, 0x01, 0x50])),
                (path: "/U/0/20260102/E/123456/00/SAMPLES.BPB", fileName: "SAMPLES.BPB", payload: Data([0x10, 0x78]))
            ],
            fetchOrder: [
                "/U/0/20260102/E/123456/TSESS.BPB",
                "/U/0/20260102/E/123456/00/SAMPLES.BPB"
            ]
        )

        XCTAssertTrue(result.hasPrefix("5|5|100|SAMPLES.BPB|P|0|0|0"), result)
    }

    func testTrainingSessionReconstructionPlanPreservesIosGeneratedPublicModelFieldsWhenLinked() throws {
        let referenceText = [
            "R|2026-01-02T12:34:56|/U/0/20260102/E/123456/TSESS.BPB|TRAINING_SESSION_SUMMARY|12",
            "E|0|/U/0/20260102/E/123456/00/BASE.BPB|/U/0/20260102/E/123456/00|SAMPLES|SAMPLES.BPB:2"
        ].joined(separator: "\n")
        let sessionPayload = try Data_PbTrainingSession.with {
            $0.start = fixtureLocalDateTime()
            $0.exerciseCount = 1
            $0.deviceID = "ios-public-device"
            $0.calories = 88
        }.serializedData()
        let samplesPayload = try Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 1 }
            $0.heartRateSamples = [120, 121]
        }.serializedData()
        let plan = PolarRuntimePlanner.trainingSessionPayloadReconstructionPlan(
            referenceText: referenceText,
            responses: [
                (path: "/U/0/20260102/E/123456/TSESS.BPB", fileName: "TSESS.BPB", payload: sessionPayload),
                (path: "/U/0/20260102/E/123456/00/SAMPLES.BPB", fileName: "SAMPLES.BPB", payload: samplesPayload)
            ],
            fetchOrder: [
                "/U/0/20260102/E/123456/TSESS.BPB",
                "/U/0/20260102/E/123456/00/SAMPLES.BPB"
            ]
        )
        let rows = plan.split(separator: "\n").map(String.init)
        let summaryRow: String = try XCTUnwrap(rows.first(where: { $0.hasPrefix("S|") }))
        let samplesRow: String = try XCTUnwrap(rows.first(where: { $0.hasPrefix("P|0|") }))
        let summaryFields = summaryRow.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        let samplesFields = samplesRow.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        let sessionSummary = try Data_PbTrainingSession(serializedBytes: try data(hex: summaryFields[4]))
        let samples = try Data_PbExerciseSamples(serializedBytes: try data(hex: samplesFields[5]))

        XCTAssertEqual(summaryFields[2], "sessionSummary")
        XCTAssertEqual(sessionSummary.deviceID, "ios-public-device")
        XCTAssertEqual(sessionSummary.calories, 88)
        XCTAssertEqual(samplesFields[3], "samples")
        XCTAssertEqual(samples.heartRateSamples, [120, 121])
    }

    func testTrainingSessionReadinessManifestIsPinnedBeforeMigration() throws {
        let readiness = try loadTrainingSessionReadinessManifest()
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(readiness["id"] as? String, "training-session-readiness")
        XCTAssertEqual(input["kind"] as? String, "trainingSessionReadiness")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/training-session/reference-discovery-two-sessions.json",
            "sdk/training-session/missing-exercise-file-platform-policy.json",
            "sdk/training-session/payload-read-policy.json",
            "sdk/training-session/payload-parser-policy.json"
        ])
        let expectedFamilies = [
            "reference-directory-traversal",
            "training-summary-discovery",
            "exercise-file-classification",
            "unknown-file-ignoring",
            "aggregate-file-size-policy",
            "exercise-path-shape-policy",
            "missing-exercise-file-platform-policy",
            "payload-fetch-order",
            "payload-progress-calculation",
            "malformed-component-isolation",
            "unknown-advanced-sample-list-ignoring",
            "known-sample-preservation",
            "payload-parser-family-ownership",
            "selected-protobuf-field-parser-ownership",
            "shared-gzip-payload-codec",
            "public-model-read-plan",
            "generated-public-protobuf-construction-boundary",
            "platform-training-session-vector-reference-gate",
            "public-model-slot-planning",
            "public-generated-model-reconstruction-boundary",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Training-session migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS training-session tests continue to reference the same vectors, directory traversal, summary discovery, exercise classification, unknown-file ignoring, aggregate size, exercise path policy, missing exercise-file policy, payload fetch order, progress, malformed component isolation, unknown advanced sample-list handling, known sample preservation, parser-family ownership, shared gzip payload decoding, shared selected protobuf field parsing, shared public-model read planning, shared public-model slot planning, generated public protobuf construction boundaries, public generated-model reconstruction boundaries, and compile verification remain explicit before production discovery/read orchestration moves.")
    }

    func test_readTrainingSession_shouldReturnTrainingSessionDataWithExercises() async throws {
        // Arrange
        let basePath = "/U/0/20250101/E/123000/TSESS.BPB"

        let dateTime1 = PbLocalDateTime.with {
            $0.date.year = 2025
            $0.date.month = 1
            $0.date.day = 1
            $0.time.hour = 12
            $0.time.minute = 30
            $0.time.seconds = 45
            $0.time.millis = 888
            $0.obsoleteTrusted = true
        }

        let dateTime2 = PbLocalDateTime.with {
            $0.date.year = 2025
            $0.date.month = 1
            $0.date.day = 1
            $0.time.hour = 14
            $0.time.minute = 1
            $0.time.seconds = 30
            $0.time.millis = 400
            $0.obsoleteTrusted = true
        }

        let duration1 = PbDuration.with {
            $0.hours = 1
            $0.minutes = 30
            $0.seconds = 45
            $0.millis = 400
        }

        let duration2 = PbDuration.with {
            $0.hours = 0
            $0.minutes = 55
            $0.seconds = 11
            $0.millis = 111
        }

        let sport1 = PbSportIdentifier.with { $0.value = 5 }
        let sport2 = PbSportIdentifier.with { $0.value = 25 }

        let exerciseProto1 = Data_PbExerciseBase.with {
            $0.start = dateTime1; $0.duration = duration1; $0.sport = sport1; $0.walkingDistance = 10000
        }

        let exerciseProto2 = Data_PbExerciseBase.with {
            $0.start = dateTime2; $0.duration = duration2; $0.sport = sport2; $0.walkingDistance = 12000
        }

        var routeProto = Data_PbExerciseRouteSamples()
        routeProto.duration = [1000]
        routeProto.latitude = [10]
        routeProto.longitude = [20]
        routeProto.gpsAltitude = [5]
        routeProto.satelliteAmount = [6]
        routeProto.obsoleteFix = [true, true]
        routeProto.obsoleteGpsOffline = []
        routeProto.obsoleteGpsDateTime = []
        routeProto.firstLocationTime = PbSystemDateTime.with {
            $0.date.year = 2025; $0.date.month = 1; $0.date.day = 1
            $0.time.hour = 12; $0.time.minute = 30; $0.time.seconds = 45; $0.time.millis = 0
            $0.trusted = true
        }

        var sampleProto = Data_PbExerciseSamples()
        sampleProto.recordingInterval = PbDuration.with { $0.seconds = 1 }
        sampleProto.heartRateSamples = [120, 125, 130]
        sampleProto.speedSamples = [2000, 2100]
        sampleProto.altitudeSamples = [300]

        var sampleProto2 = Data_PbExerciseSamples2()
        var intervalledSample = Data_PbExerciseIntervalledSample2List()
        intervalledSample.sampleType = PbSampleType.sampleTypeHeartRate
        intervalledSample.recordingIntervalMs = 1000
        intervalledSample.heartRateSamples = [131, 132, 133]
        sampleProto2.exerciseIntervalledSample2List = [intervalledSample]

        func gzipCompress(_ data: Data) throws -> Data {
            var stream = z_stream()
            var status: Int32 = Z_OK
            let bufferSize = 16384
            var output = Data()

            status = data.withUnsafeBytes { (srcPointer: UnsafeRawBufferPointer) -> Int32 in
                stream.next_in = UnsafeMutablePointer<Bytef>(mutating: srcPointer.bindMemory(to: Bytef.self).baseAddress!)
                stream.avail_in = uInt(data.count)
                return deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
            }

            guard status == Z_OK else {
                throw NSError(domain: "CompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to init zlib deflate stream"])
            }

            defer { deflateEnd(&stream) }

            let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
            defer { buffer.deallocate() }

            repeat {
                stream.next_out = buffer
                stream.avail_out = uInt(bufferSize)
                status = deflate(&stream, stream.avail_in == 0 ? Z_FINISH : Z_NO_FLUSH)
                if status == Z_STREAM_ERROR {
                    throw NSError(domain: "CompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Compression failed with zlib error"])
                }
                let have = bufferSize - Int(stream.avail_out)
                output.append(buffer, count: have)
            } while status != Z_STREAM_END

            return output
        }

        let routeGzipData = try gzipCompress(try routeProto.serializedData())

        var syncPoint = Data_PbExerciseRouteSyncPoint()
        syncPoint.index = 0
        var location = Data_PbLocationSyncPoint()
        location.latitude = 10
        location.longitude = 20
        syncPoint.location = location

        var route2Proto = Data_PbExerciseRouteSamples2()
        route2Proto.syncPoint = [syncPoint]
        route2Proto.latitude = [0]
        route2Proto.longitude = [0]
        route2Proto.timestamp = [0]
        route2Proto.altitude = [0]
        route2Proto.satelliteAmount = [3]

        let route2GzipData = try gzipCompress(try route2Proto.serializedData())
        let samplesGzipData = try gzipCompress(try sampleProto.serializedData())
        let samples2GzipData = try gzipCompress(try sampleProto2.serializedData())

        let sessionProto = Data_PbTrainingSession.with {
            $0.start = dateTime1
            $0.exerciseCount = 2
        }

        let routeFiles = ["ROUTE.BPB", "ROUTE.GZB", "ROUTE2.BPB", "ROUTE2.GZB"]

        mockClient.requestReturnValueClosure = { headerData in
            let op = try Protocol_PbPFtpOperation(serializedBytes: headerData)
            let path = op.path
            switch path {
            case basePath:
                return try sessionProto.serializedData()
            case "/U/0/20250101/E/123000/00/BASE.BPB":
                return try exerciseProto1.serializedData()
            case "/U/0/20250101/E/123000/01/BASE.BPB":
                return try exerciseProto2.serializedData()
            case "/U/0/20250101/E/123000/00/ROUTE.BPB", "/U/0/20250101/E/123000/01/ROUTE.BPB":
                return try routeProto.serializedData()
            case "/U/0/20250101/E/123000/00/ROUTE.GZB", "/U/0/20250101/E/123000/01/ROUTE.GZB":
                return routeGzipData
            case "/U/0/20250101/E/123000/00/ROUTE2.BPB", "/U/0/20250101/E/123000/01/ROUTE2.BPB":
                return try route2Proto.serializedData()
            case "/U/0/20250101/E/123000/00/ROUTE2.GZB", "/U/0/20250101/E/123000/01/ROUTE2.GZB":
                return route2GzipData
            case "/U/0/20250101/E/123000/00/SAMPLES.BPB", "/U/0/20250101/E/123000/01/SAMPLES.BPB":
                return try sampleProto.serializedData()
            case "/U/0/20250101/E/123000/00/SAMPLES.GZB", "/U/0/20250101/E/123000/01/SAMPLES.GZB":
                return samplesGzipData
            case "/U/0/20250101/E/123000/00/SAMPLES2.GZB", "/U/0/20250101/E/123000/01/SAMPLES2.GZB":
                return samples2GzipData
            default:
                throw NSError(domain: "UnexpectedPath", code: 2,
                              userInfo: [NSLocalizedDescriptionKey: "Unexpected path: \(path)"])
            }
        }

        for routeFile in routeFiles {
            let exercisesWithRoute = [
                PolarExercise(
                    index: 0,
                    path: "/U/0/20250101/E/123000/00",
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip, .samples, .samplesGzip, .samplesAdvancedFormatGzip]
                ),
                PolarExercise(
                    index: 1,
                    path: "/U/0/20250101/E/123000/01",
                    exerciseDataTypes: [.exerciseSummary, .route, .routeGzip, .routeAdvancedFormat, .routeAdvancedFormatGzip, .samples, .samplesGzip, .samplesAdvancedFormatGzip]
                )
            ]

            let reference = PolarTrainingSessionReference(
                date: Date(),
                path: basePath,
                trainingDataTypes: [.trainingSessionSummary],
                exercises: exercisesWithRoute
            )

            // Act
            let session = try await PolarTrainingSessionUtils
                .readTrainingSession(client: mockClient, reference: reference)

            // Assert
            XCTAssertEqual(session.exercises.count, 2, "Expected 2 exercises for route file \(routeFile)")

            XCTAssertEqual(session.sessionSummary.start.date.year, 2025)
            XCTAssertEqual(session.sessionSummary.start.date.month, 1)
            XCTAssertEqual(session.sessionSummary.start.date.day, 1)
            XCTAssertEqual(session.sessionSummary.start.time.hour, 12)
            XCTAssertEqual(session.sessionSummary.start.time.minute, 30)

            // Sort exercises by start hour so assertions are order-independent.
            let sortedExercises = session.exercises.sorted {
                ($0.exerciseSummary?.start.time.hour ?? 0) < ($1.exerciseSummary?.start.time.hour ?? 0)
            }

            let firstExercise: PolarExercise? = sortedExercises[0]
            XCTAssertEqual(firstExercise?.exerciseSummary?.start.time.hour, 12)
            XCTAssertEqual(firstExercise?.exerciseSummary?.walkingDistance, 10000)
            XCTAssertEqual(firstExercise?.exerciseSummary?.sport.value, 5)
            XCTAssertEqual(firstExercise?.samples?.heartRateSamples, [120, 125, 130])
            XCTAssertEqual(firstExercise?.samplesAdvanced?.exerciseIntervalledSample2List.map { $0.heartRateSamples }, [[131, 132, 133]])

            let secondExercise: PolarExercise? = sortedExercises[1]
            XCTAssertEqual(secondExercise?.exerciseSummary?.start.time.hour, 14)
            XCTAssertEqual(secondExercise?.exerciseSummary?.walkingDistance, 12000)
            XCTAssertEqual(secondExercise?.exerciseSummary?.sport.value, 25)
            XCTAssertEqual(secondExercise?.samples?.heartRateSamples, [120, 125, 130])
            XCTAssertEqual(secondExercise?.samplesAdvanced?.exerciseIntervalledSample2List.map { $0.heartRateSamples }, [[131, 132, 133]])

            let firstRoute = firstExercise?.route
            XCTAssertNotNil(firstRoute, "First route should not be nil for route file \(routeFile)")

            let secondRoute = secondExercise?.route
            XCTAssertNotNil(secondRoute, "Second route should not be nil for route file \(routeFile)")

            if routeFile.starts(with: "ROUTE") && !routeFile.contains("2") {
                XCTAssertEqual(firstRoute?.latitude, [10], "Latitude mismatch for \(routeFile)")
                XCTAssertEqual(firstRoute?.longitude, [20], "Longitude mismatch for \(routeFile)")
                XCTAssertEqual(firstRoute?.duration, [1000], "Duration mismatch for \(routeFile)")
                XCTAssertEqual(secondRoute?.gpsAltitude, [5], "GpsAltitude mismatch for \(routeFile)")
                XCTAssertEqual(secondRoute?.satelliteAmount, [6], "SatelliteAmount mismatch for \(routeFile)")
            } else {
                XCTAssertEqual(firstRoute?.latitude, [10], "Latitude mismatch for advanced route \(routeFile)")
                XCTAssertEqual(firstRoute?.longitude, [20], "Longitude mismatch for advanced route \(routeFile)")
                XCTAssertEqual(firstRoute?.satelliteAmount, [6], "SatelliteAmount mismatch for advanced route \(routeFile)")
            }
        }
    }

    private func buildDirectory(entries: [[String: Any]]) throws -> Protocol_PbPFtpDirectory {
        var directory = Protocol_PbPFtpDirectory()
        directory.entries = entries.map { entry in
            Protocol_PbPFtpEntry.with {
                $0.name = try! XCTUnwrap(entry["name"] as? String)
                $0.size = UInt64(try! number(entry, "size", id: $0.name))
            }
        }
        return directory
    }

    private func assertTrainingSessionReferences(_ actual: [PolarTrainingSessionReference], expected: [String: Any], id: String) throws {
        let expectedReferences = try XCTUnwrap(expected["references"] as? [[String: Any]], id)
        XCTAssertEqual(actual.count, expectedReferences.count, "\(id) reference count")
        for (index, expectedReference) in expectedReferences.enumerated() {
            let actualReference = actual[index]
            try assertDate(actualReference.date, expected: try XCTUnwrap(expectedReference["dateTime"] as? String, id), id: "\(id) reference \(index) date")
            XCTAssertEqual(actualReference.path, try XCTUnwrap(expectedReference["path"] as? String, id), "\(id) reference \(index) path")
            XCTAssertEqual(actualReference.fileSize, Int64(try number(expectedReference, "fileSize", id: id)), "\(id) reference \(index) fileSize")
            XCTAssertEqual(actualReference.trainingDataTypes.map(\.rawValue), try XCTUnwrap(expectedReference["trainingDataTypes"] as? [String], id).map { androidTrainingTypeToIOSRawValue($0) }, "\(id) reference \(index) trainingDataTypes")
            let expectedExercises = try XCTUnwrap(expectedReference["exercises"] as? [[String: Any]], id)
            XCTAssertEqual(actualReference.exercises.count, expectedExercises.count, "\(id) reference \(index) exercises")
            for (exerciseIndex, expectedExercise) in expectedExercises.enumerated() {
                let actualExercise = actualReference.exercises[exerciseIndex]
                XCTAssertEqual(actualExercise.index, try number(expectedExercise, "iosIndex", id: id), "\(id) exercise \(exerciseIndex) index")
                XCTAssertEqual(actualExercise.path, try XCTUnwrap(expectedExercise["iosPath"] as? String, id), "\(id) exercise \(exerciseIndex) path")
                XCTAssertEqual(actualExercise.exerciseDataTypes.map(\.rawValue), try XCTUnwrap(expectedExercise["exerciseDataTypes"] as? [String], id).map { androidExerciseTypeToIOSRawValue($0) }, "\(id) exercise \(exerciseIndex) dataTypes")
                XCTAssertEqual(actualExercise.fileSizes ?? [:], try fileSizes(expectedExercise, id: id), "\(id) exercise \(exerciseIndex) fileSizes")
            }
        }
    }

    private func fileSizes(_ fields: [String: Any], id: String) throws -> [String: Int64] {
        let values = try XCTUnwrap(fields["fileSizes"] as? [String: Any], id)
        var result: [String: Int64] = [:]
        for (key, value) in values {
            result[key] = Int64(try number(["value": value], "value", id: id))
        }
        return result
    }

    private func buildTrainingSessionReference(from fields: [String: Any]) throws -> PolarTrainingSessionReference {
        let exercises = try XCTUnwrap(fields["exercises"] as? [[String: Any]]).map { exercise in
            PolarExercise(
                index: try! number(exercise, "index", id: "training-session exercise"),
                path: try! XCTUnwrap(exercise["iosPath"] as? String),
                exerciseDataTypes: (try! XCTUnwrap(exercise["exerciseDataTypes"] as? [String])).map { androidExerciseTypeToIOSType($0) }
            )
        }
        return PolarTrainingSessionReference(
            date: try dateValue(try XCTUnwrap(fields["date"] as? String)),
            path: try XCTUnwrap(fields["path"] as? String),
            trainingDataTypes: (try XCTUnwrap(fields["trainingDataTypes"] as? [String])).map { androidTrainingTypeToIOSType($0) },
            exercises: exercises
        )
    }

    private func trainingSessionFixtureData(_ responseType: String) throws -> Data {
        switch responseType {
        case "trainingSessionSummary":
            return try Data_PbTrainingSession.with {
                $0.start = fixtureLocalDateTime()
                $0.exerciseCount = 1
                $0.modelName = "Polar 360"
            }.serializedData()
        case "exerciseSummary":
            return try Data_PbExerciseBase.with {
                $0.start = fixtureLocalDateTime()
                $0.duration = PbDuration.with { $0.seconds = 3600 }
                $0.walkingDistance = 1000
                $0.sport = PbSportIdentifier.with { $0.value = 3 }
            }.serializedData()
        default:
            throw NSError(domain: "TrainingSessionVector", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unknown fixture response type \(responseType)"])
        }
    }

    private func fixtureLocalDateTime() -> PbLocalDateTime {
        return PbLocalDateTime.with {
            $0.date = PbDate.with { $0.year = 2025; $0.month = 1; $0.day = 1 }
            $0.time = PbTime.with { $0.hour = 10; $0.minute = 12; $0.seconds = 0 }
            $0.obsoleteTrusted = true
        }
    }

    private func dateValue(_ value: String) throws -> Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        guard let date = formatter.date(from: value) else {
            throw NSError(domain: "TrainingSessionVector", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid date \(value)"])
        }
        return date
    }

    private func assertDate(_ actual: Date, expected: String, id: String) throws {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        XCTAssertEqual(formatter.string(from: actual), expected, id)
    }

    private func androidTrainingTypeToIOSRawValue(_ type: String) -> String {
        switch type {
        case "TRAINING_SESSION_SUMMARY": return PolarTrainingSessionDataTypes.trainingSessionSummary.rawValue
        default: return type
        }
    }

    private func androidTrainingTypeToIOSType(_ type: String) -> PolarTrainingSessionDataTypes {
        switch type {
        case "TRAINING_SESSION_SUMMARY": return .trainingSessionSummary
        default: return PolarTrainingSessionDataTypes(rawValue: type)!
        }
    }

    private func androidExerciseTypeToIOSRawValue(_ type: String) -> String {
        switch type {
        case "EXERCISE_SUMMARY": return PolarExerciseDataTypes.exerciseSummary.rawValue
        case "ROUTE": return PolarExerciseDataTypes.route.rawValue
        case "ROUTE_GZIP": return PolarExerciseDataTypes.routeGzip.rawValue
        case "ROUTE_ADVANCED_FORMAT": return PolarExerciseDataTypes.routeAdvancedFormat.rawValue
        case "ROUTE_ADVANCED_FORMAT_GZIP": return PolarExerciseDataTypes.routeAdvancedFormatGzip.rawValue
        case "SAMPLES": return PolarExerciseDataTypes.samples.rawValue
        case "SAMPLES_GZIP": return PolarExerciseDataTypes.samplesGzip.rawValue
        case "SAMPLES_ADVANCED_FORMAT_GZIP": return PolarExerciseDataTypes.samplesAdvancedFormatGzip.rawValue
        default: return type
        }
    }

    private func androidExerciseTypeToIOSType(_ type: String) -> PolarExerciseDataTypes {
        return PolarExerciseDataTypes(rawValue: androidExerciseTypeToIOSRawValue(type))!
    }

    private func loadTrainingSessionGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/training-session")
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
                return input?["kind"] as? String != "trainingSessionReadiness"
            }
    }

    private func loadTrainingSessionReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/training-session/training-session-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private func number(_ object: [String: Any], _ key: String, id: String) throws -> Int {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").intValue
    }

    private func data(hex: String) throws -> Data {
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let nextIndex = hex.index(index, offsetBy: 2, limitedBy: hex.endIndex) ?? hex.endIndex
            let byte = try XCTUnwrap(UInt8(hex[index..<nextIndex], radix: 16))
            data.append(byte)
            index = nextIndex
        }
        return data
    }
}

private let TRAINING_SESSION_PAYLOAD_PARSER_CASE_IDS = [
    "training-session-summary-protobuf",
    "exercise-summary-protobuf",
    "route-protobuf",
    "route-gzip-protobuf",
    "route-advanced-protobuf",
    "route-advanced-gzip-protobuf",
    "samples-protobuf",
    "samples-gzip-protobuf",
    "samples-advanced-gzip-protobuf"
]

private let TRAINING_SESSION_PAYLOAD_PARSER_COMMON_DECISION = "Selected training payload protobuf field parsing now executes in shared KMP for these parser cases; generated public protobuf object construction remains platform-owned while neutral reconstruction planning maps decoded payload bytes to Android and iOS adapters. Gzip decompression and public-model slot planning are shared KMP production code, and this vector remains the shared parser ownership contract consumed by commonTest and pinned by Android/iOS byte-level characterization tests."
