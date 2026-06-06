//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class PolarFileUtilsTest: XCTestCase {
    
    private var identifier = "E123456F"
    private var mockClient: MockBlePsFtpClient!
    private var mockListener: MockCBDeviceListenerImpl!
    private var mockSession: MockBleDeviceSession!
    private var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    private var mockServiceClientUtils: MockPolarServiceClientUtils!
    private var fileUtils: PolarFileUtils!
    private let dateFormatter = DateFormatter()
    
    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        mockListener = MockCBDeviceListenerImpl()
        mockServiceClientUtils = MockPolarServiceClientUtils(listener: mockListener, session: mockSession)
        fileUtils = PolarFileUtils(listener: mockListener, serviceClientUtils: mockServiceClientUtils)
        dateFormatter.dateFormat = "yyyyMMdd"
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
    }
    
    func testListFiles_success() async throws {
        // Arrange
        let condition: (_ p: String) -> Bool = { $0.contains(".") }
        let expectedFiles = ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260201/R/101010/PPG0.REC", "/U/0/20260201/R/101010/HR0.REC"]
        
        let responses = makeDirectoryResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        var emitted: [String] = []
        for try await file in fileUtils.listFiles(identifier: identifier, folderPath: "/U/0/", condition: condition) {
            emitted.append(file)
        }
        
        // Assert
        XCTAssertEqual(emitted.count, expectedFiles.count)
        XCTAssert(emitted.contains(expectedFiles[0]))
        XCTAssert(emitted.contains(expectedFiles[1]))
        XCTAssert(emitted.contains(expectedFiles[2]))
    }
    
    func testListFiles_failure() async throws {
        // Arrange
        let condition: (_ p: String) -> Bool = { $0.contains(".") }
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            for try await _ in fileUtils.listFiles(identifier: identifier, folderPath: "/U/0/", condition: condition) {}
            XCTFail("listFiles method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    func testCheckAutoSampleFile_can_delete_file() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        let date = DateFormatter().apply { $0.dateFormat = "yyyyMMdd" }.date(from: "25251118")!
        
        // Act
        let result = try await fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date)
        
        // Assert
        XCTAssertTrue(result)
    }
    
    func testCheckAutoSampleFile_cannot_delete_file() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        let date = dateFormatter.date(from: "25250225")!
        
        // Act
        let result = try await fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date)
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertFalse(result)
    }
    
    func testCheckAutoSampleFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        let date = dateFormatter.date(from: "25250225")!
        
        // Act & Assert
        do {
            _ = try await fileUtils.checkAutoSampleFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB", until: date)
            XCTFail("Expected error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    func testCheckIfDirectoryIsEmpty_is_empty() async throws {
        // Arrange
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/20260101/": []
        ]
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            XCTAssertEqual(op.command, .get)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let result = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "U/0/20260101", client: mockClient)
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertTrue(result)
    }
    
    func testCheckIfDirectoryIsEmpty_is_not_empty() async throws {
        // Arrange
        let dsumEntry = Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }
        let responses: [String: [Protocol_PbPFtpEntry]] = [
            "/U/0/20260101/DSUM/": [dsumEntry]
        ]
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            XCTAssertEqual(op.command, .get)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let result = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient)
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertFalse(result)
    }
    
    func testCheckIfDirectoryIsEmpty_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient)
            XCTFail("Expected error")
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testCheckIfDirectoryIsEmpty_error103_file_not_found() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw BlePsFtpException.responseError(errorCode: 103) }
        
        // Act
        let result = try await fileUtils.checkIfDirectoryIsEmpty(directoryPath: "/U/0/20260101/DSUM/", client: mockClient)
        
        // Assert
        XCTAssertTrue(result, "Error code 103 MUST return true")
    }

    func testRemoveSingleFile_success() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(Data([0x00]))]
        
        // Act
        let res = try await fileUtils.removeSingleFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        XCTAssertEqual([UInt8](res as Data)[0], 0)
    }
    
    func testRemoveSingleFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.removeSingleFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("removeSingleFile method did not throw an error")
        } catch {
            XCTAssertEqual(mockClient.requestCalls.count, 1)
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testRemoveMultipleFile_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = [Protocol_PbPFtpEntry.with { $0.name = ""; $0.size = 1024 }]
            }
            return try dir.serializedData()
        }
        
        // Act
        try await fileUtils.removeMultipleFiles(identifier: identifier, filePaths: ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260102/DSUM/DSUM.BPB"])
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 2)
    }
    
    func testRemoveMultipleFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            try await fileUtils.removeMultipleFiles(identifier: identifier, filePaths: ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260102/DSUM/DSUM.BPB"])
            XCTFail("removeMultipleFiles method did not throw an error")
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testGetFile_success() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        
        // Act
        let result = try await fileUtils.getFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB")
        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedBytes: result as Data)
        
        // Assert
        XCTAssertEqual(2, sampleSessions.samples.count)
        XCTAssertEqual(3, sampleSessions.samples[0].heartRate.count)
        XCTAssertEqual(3, sampleSessions.samples[1].heartRate.count)
        XCTAssertEqual(2, sampleSessions.ppiSamples.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[0].ppi.ppiDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[1].ppi.ppiDelta.count)
    }
    
    func testGetFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.getFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("getFile method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    // MARK: - Low level API tests

    func testWriteFile_success() async throws {
        // Arrange
        mockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }
        
        // Act
        try await fileUtils.writeFile(identifier: identifier, filePath: "/U/0/2525/DSUM/DSUM.BPB", fileData: mockFileContent())
        
        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 1)
    }
    
    func testWriteFile_failure() async throws {
        // Arrange
        mockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: PolarErrors.deviceNotConnected)
        }
        
        // Act & Assert
        do {
            try await fileUtils.writeFile(identifier: identifier, filePath: "/U/0/2525/DSUM/DSUM.BPB", fileData: mockFileContent())
            XCTFail("writeFile method did not throw an error")
        } catch {
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }
    
    func testReadFile_success() async throws {
        // Arrange
        mockClient.requestReturnValues = [.success(mockFileContent())]
        
        // Act
        let result = try await fileUtils.readFile(identifier: identifier, filePath: "/U/0/AUTOS000.BPB")
        let sampleSessions = try Data_PbAutomaticSampleSessions(serializedBytes: result)
        
        // Assert
        XCTAssertEqual(2, sampleSessions.samples.count)
        XCTAssertEqual(3, sampleSessions.samples[0].heartRate.count)
        XCTAssertEqual(3, sampleSessions.samples[1].heartRate.count)
        XCTAssertEqual(2, sampleSessions.ppiSamples.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[0].ppi.ppiDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[0].ppi.ppiErrorEstimateDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[1].ppi.ppiDelta.count)
        XCTAssertEqual(4, sampleSessions.ppiSamples[1].ppi.ppiErrorEstimateDelta.count)
    }
    
    func testReadFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.readFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("readFile method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }
    
    func testListFiles_low_level_recurseDeepFalse_success() async throws {
        // Arrange
        let expectedFiles = ["/U/0/20260101/", "/U/0/20260201/"]
        let responses = makeDirectoryResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let emitted = try await fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: false)
        
        // Assert
        XCTAssertTrue(emitted.contains(expectedFiles[0]))
        XCTAssertTrue(emitted.contains(expectedFiles[1]))
    }
    
    func testListFiles_low_level_recurseDeepTrue_success() async throws {
        // Arrange
        let expectedFiles = ["/U/0/20260101/DSUM/DSUM.BPB", "/U/0/20260201/R/101010/PPG0.REC", "/U/0/20260201/R/101010/HR0.REC"]
        let responses = makeDirectoryResponses()
        mockClient.requestReturnValueClosure = { header in
            let op = try Protocol_PbPFtpOperation(serializedBytes: header)
            let dir = Protocol_PbPFtpDirectory.with { $0.entries = responses[op.path, default: []] }
            return try dir.serializedData()
        }
        
        // Act
        let emitted = try await fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: true)
        
        // Assert
        XCTAssertTrue(emitted.contains(expectedFiles[0]))
        XCTAssertTrue(emitted.contains(expectedFiles[1]))
        XCTAssertTrue(emitted.contains(expectedFiles[2]))
    }
    
    func testListFiles_low_level_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            _ = try await fileUtils.listFiles(identifier: identifier, directoryPath: "/U/0/", recurseDeep: true)
            XCTFail("listFiles method did not throw an error")
        } catch {
            XCTAssertNotNil(error)
        }
    }

    func testFileUtilityGoldenVectorsListExpectedPaths() async throws {
        XCTAssertEqual(PolarRuntimePlanner.normalizeFileListFolderPath("U/0"), "/U/0/")
        XCTAssertEqual(PolarRuntimePlanner.normalizeFileListFolderPath(""), "/")
        for vector in try loadFileUtilityGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            guard input["directories"] != nil else { continue }
            let directories = try XCTUnwrap(input["directories"] as? [String: [[String: Any]]], id)
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
            mockServiceClientUtils = MockPolarServiceClientUtils(listener: mockListener, session: mockSession)
            fileUtils = PolarFileUtils(listener: mockListener, serviceClientUtils: mockServiceClientUtils)
            mockClient.requestReturnValueClosure = { header in
                let op = try Protocol_PbPFtpOperation(serializedBytes: header)
                return try self.directoryData(from: directories[op.path] ?? [])
            }

            let condition = conditionFromVector(try XCTUnwrap(input["condition"] as? String, id))
            var emitted: [String] = []
            for try await path in fileUtils.listFiles(
                identifier: identifier,
                folderPath: try XCTUnwrap(input["rootPath"] as? String, id),
                condition: condition,
                recurseDeep: try XCTUnwrap(input["recurseDeep"] as? Bool, id)
            ) {
                emitted.append(path)
            }

            let expected = try XCTUnwrap((vector["expected"] as? [String: Any])?["paths"] as? [String], id)
            XCTAssertEqual(emitted, expected, id)
        }
    }

    func testFileUtilityGoldenVectorsPreserveLowLevelFileOperations() async throws {
        let vector = try XCTUnwrap(try loadFileUtilityGoldenVectors().first { ($0["id"] as? String) == "file-read-write-delete-operations" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let inputOperations = try XCTUnwrap(input["operations"] as? [[String: Any]])
        let expectedOperations = try XCTUnwrap(expected["operations"] as? [[String: Any]])

        for (inputOperation, expectedOperation) in zip(inputOperations, expectedOperations) {
            let action = try XCTUnwrap(inputOperation["action"] as? String)
            let caseId = "\(try XCTUnwrap(vector["id"] as? String)):\(action)"
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
            mockServiceClientUtils = MockPolarServiceClientUtils(listener: mockListener, session: mockSession)
            fileUtils = PolarFileUtils(listener: mockListener, serviceClientUtils: mockServiceClientUtils)

            switch action {
            case "read":
                mockClient.requestReturnValues = [.success(try dataFromHex(try XCTUnwrap(inputOperation["responseHex"] as? String)))]

                let result = try await fileUtils.readFile(identifier: identifier, filePath: try XCTUnwrap(inputOperation["path"] as? String))

                XCTAssertEqual(result.hexString, try XCTUnwrap(expectedOperation["resultHex"] as? String), caseId)
                let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
                XCTAssertEqual(operation.command, try expectedCommand(from: expectedOperation), caseId)
                XCTAssertEqual(operation.path, try XCTUnwrap(expectedOperation["path"] as? String), caseId)
            case "write":
                mockClient.writeReturnValue = AsyncThrowingStream { continuation in
                    continuation.yield(0)
                    continuation.finish()
                }

                try await fileUtils.writeFile(
                    identifier: identifier,
                    filePath: try XCTUnwrap(inputOperation["path"] as? String),
                    fileData: try dataFromHex(try XCTUnwrap(inputOperation["payloadHex"] as? String))
                )

                let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.writeCalls[0].header as Data)
                XCTAssertEqual(operation.command, try expectedCommand(from: expectedOperation), caseId)
                XCTAssertEqual(operation.path, try XCTUnwrap(expectedOperation["path"] as? String), caseId)
                XCTAssertEqual(try data(from: mockClient.writeCalls[0].data).hexString, try XCTUnwrap(expectedOperation["writtenHex"] as? String), caseId)
            case "delete":
                mockClient.requestReturnValues = [.success(try dataFromHex(try XCTUnwrap(inputOperation["responseHex"] as? String)))]

                let result = try await fileUtils.removeSingleFile(identifier: identifier, filePath: try XCTUnwrap(inputOperation["path"] as? String))

                XCTAssertEqual((result as Data).hexString, try XCTUnwrap(expectedOperation["resultHex"] as? String), caseId)
                let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
                XCTAssertEqual(operation.command, try expectedCommand(from: expectedOperation), caseId)
                XCTAssertEqual(operation.path, try XCTUnwrap(expectedOperation["path"] as? String), caseId)
            default:
                XCTFail("Unsupported file utility action \(action)")
            }
        }
    }

    func testFileUtilityGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadFileUtilityGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }
    
    func testDeleteFile_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in
            let dir = Protocol_PbPFtpDirectory.with {
                $0.entries = [Protocol_PbPFtpEntry.with { $0.name = ""; $0.size = 0 }]
            }
            return try dir.serializedData()
        }
        
        // Act
        try await fileUtils.deleteFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
        
        // Assert
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }
    
    func testDeleteFile_failure() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in throw PolarErrors.deviceNotConnected }
        
        // Act & Assert
        do {
            try await fileUtils.deleteFile(identifier: identifier, filePath: "/U/0/20260101/DSUM/DSUM.BPB")
            XCTFail("deleteFile method did not throw an error")
        } catch {
            XCTAssertEqual(mockClient.requestCalls.count, 1)
            XCTAssertEqual((error as? PolarErrors)?.localizedDescription, PolarErrors.deviceNotConnected.localizedDescription)
        }
    }

    // MARK: - Helpers

    private func makeDirectoryResponses() -> [String: [Protocol_PbPFtpEntry]] {
        return [
            "/U/0/": [
                Protocol_PbPFtpEntry.with { $0.name = "20260101/"; $0.size = 0 },
                Protocol_PbPFtpEntry.with { $0.name = "20260201/"; $0.size = 0 }
            ],
            "/U/0/20260101/": [Protocol_PbPFtpEntry.with { $0.name = "DSUM/"; $0.size = 0 }],
            "/U/0/20260101/DSUM/": [Protocol_PbPFtpEntry.with { $0.name = "DSUM.BPB"; $0.size = 1024 }],
            "/U/0/20260201/": [Protocol_PbPFtpEntry.with { $0.name = "R/"; $0.size = 0 }],
            "/U/0/20260201/R/": [Protocol_PbPFtpEntry.with { $0.name = "101010/"; $0.size = 0 }],
            "/U/0/20260201/R/101010/": [
                Protocol_PbPFtpEntry.with { $0.name = "PPG0.REC"; $0.size = 1024 },
                Protocol_PbPFtpEntry.with { $0.name = "HR0.REC"; $0.size = 1024 }
            ]
        ]
    }

    private func directoryData(from entries: [[String: Any]]) throws -> Data {
        return try Protocol_PbPFtpDirectory.with {
            $0.entries = entries.map { entry in
                Protocol_PbPFtpEntry.with {
                    $0.name = entry["name"] as? String ?? ""
                    $0.size = (entry["size"] as? NSNumber)?.uint64Value ?? 0
                }
            }
        }.serializedData()
    }

    func testRuntimeErrorPolicyVectorIsPinnedBeforeRuntimeMigration() throws {
        let vector = try XCTUnwrap(try loadFileUtilityGoldenVectors().first { ($0["id"] as? String) == "runtime-error-policy" })
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let expectedCases = try XCTUnwrap((expected["commonRuntimePrototype"] as? [String: Any])?["cases"] as? [[String: Any]], "runtime-error-policy")
        let expectedCaseIds = expectedCases.compactMap { $0["id"] as? String }

        XCTAssertNotNil(vector["execution"], "runtime-error-policy")
        XCTAssertEqual(expectedCaseIds, FILE_RUNTIME_ERROR_POLICY_CASE_IDS, "runtime-error-policy")
        XCTAssertEqual(expected["migrationRequirement"] as? String, FILE_RUNTIME_ERROR_MIGRATION_REQUIREMENT, "runtime-error-policy")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "runtime-error-policy"), ["com.polar.sdk.api.model.utils.PolarFileUtilsTest", "com.polar.sdk.api.model.utils.RestAndFileCommonFakeRuntimeTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "runtime-error-policy"), ["PolarFileUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "runtime-error-policy"), ["com.polar.sdk.api.model.utils.RestAndFileCommonFakeRuntimeTest", "com.polar.sharedtest.FileRuntimeErrorPolicyCommonTest"])
    }

    func testRuntimeErrorReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        let vector = try XCTUnwrap(try loadFileUtilityGoldenVectors().first { ($0["id"] as? String) == "runtime-error-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])

        XCTAssertEqual(vector["id"] as? String, "runtime-error-readiness")
        XCTAssertEqual(input["kind"] as? String, "fileRuntimeErrorReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/file-utils/runtime-error-policy.json")
        XCTAssertEqual(FILE_RUNTIME_ERROR_READINESS_FAMILIES, requiredFamilies, "runtime-error-readiness")
        XCTAssertEqual(FILE_RUNTIME_ERROR_READINESS_FAMILIES, coveredFamilies, "runtime-error-readiness")
        XCTAssertEqual(expected["commonDecision"] as? String, FILE_RUNTIME_ERROR_READINESS_COMMON_DECISION, "runtime-error-readiness")
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "runtime-error-readiness")
        XCTAssertEqual(commonRuntimePrototype["status"] as? String, "executable shared commonTest runtime planning guard", "runtime-error-readiness")
        XCTAssertEqual(commonRuntimePrototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", "runtime-error-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "runtime-error-readiness"), ["com.polar.sdk.api.model.utils.PolarFileUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "runtime-error-readiness"), ["PolarFileUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "runtime-error-readiness"), ["com.polar.sharedtest.FileRuntimeErrorPolicyCommonTest"])
    }

    func testFileFacadeRuntimePolicyVectorIsPinnedBeforeRuntimeMigration() throws {
        let vector = try XCTUnwrap(try loadFileUtilityGoldenVectors().first { ($0["id"] as? String) == "file-facade-runtime-policy" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let operations = try XCTUnwrap(input["operations"] as? [[String: Any]], "file-facade-runtime-policy")
        let runtimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "file-facade-runtime-policy")
        let expectedCases = try XCTUnwrap(runtimePrototype["cases"] as? [[String: Any]], "file-facade-runtime-policy")
        let operationIds = try operations.map { try XCTUnwrap($0["id"] as? String, "file-facade-runtime-policy") }
        let expectedCaseIds = try expectedCases.map { try XCTUnwrap($0["id"] as? String, "file-facade-runtime-policy") }

        XCTAssertEqual(input["kind"] as? String, "fileFacadeRuntimePolicy", "file-facade-runtime-policy")
        XCTAssertEqual(operationIds, FILE_FACADE_RUNTIME_OPERATION_IDS, "file-facade-runtime-policy")
        XCTAssertEqual(expectedCaseIds, FILE_FACADE_RUNTIME_OPERATION_IDS, "file-facade-runtime-policy")
        XCTAssertEqual(expected["commonDecision"] as? String, FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION, "file-facade-runtime-policy")
        XCTAssertEqual(vector["commonDecision"] as? String, FILE_FACADE_RUNTIME_MIGRATION_DECISION, "file-facade-runtime-policy")
        XCTAssertEqual(execution["kind"] as? String, "fake-file-facade-runtime-policy", "file-facade-runtime-policy")
        XCTAssertEqual(execution["transport"] as? String, "public-facade-psftp-command-capture", "file-facade-runtime-policy")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "file-facade-runtime-policy"), ["com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "file-facade-runtime-policy"), ["PolarBleApiImplTests", "PolarFileUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "file-facade-runtime-policy"), ["com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest"])
    }

    func testFileFacadeRuntimeReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        let vector = try XCTUnwrap(try loadFileUtilityGoldenVectors().first { ($0["id"] as? String) == "file-facade-runtime-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "file-facade-runtime-readiness")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "file-facade-runtime-readiness")

        XCTAssertEqual(vector["id"] as? String, "file-facade-runtime-readiness")
        XCTAssertEqual(input["kind"] as? String, "fileFacadeRuntimeReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/file-utils/file-facade-runtime-policy.json")
        XCTAssertEqual(requiredFamilies, FILE_FACADE_RUNTIME_READINESS_FAMILIES, "file-facade-runtime-readiness")
        XCTAssertEqual(coveredFamilies, FILE_FACADE_RUNTIME_READINESS_FAMILIES, "file-facade-runtime-readiness")
        XCTAssertEqual(expected["commonDecision"] as? String, FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION, "file-facade-runtime-readiness")
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "file-facade-runtime-readiness")
        XCTAssertEqual(commonRuntimePrototype["status"] as? String, "executable shared commonTest runtime planning guard", "file-facade-runtime-readiness")
        XCTAssertEqual(commonRuntimePrototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", "file-facade-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "file-facade-runtime-readiness"), ["com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "file-facade-runtime-readiness"), ["PolarBleApiImplTests", "PolarFileUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "file-facade-runtime-readiness"), ["com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest"])
    }

    private func conditionFromVector(_ name: String) -> (String) -> Bool {
        switch name {
        case "entry-name-contains-dot":
            return { $0.contains(".") }
        case "include-all":
            return { _ in true }
        default:
            return { _ in false }
        }
    }

    private func loadFileUtilityGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/file-utils")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

    private func expectedCommand(from operation: [String: Any]) throws -> Protocol_PbPFtpOperation.Command {
        switch try XCTUnwrap(operation["command"] as? String) {
        case "GET":
            return .get
        case "PUT":
            return .put
        case "REMOVE":
            return .remove
        default:
            throw NSError(domain: "PolarFileUtilsTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unsupported command \(operation)"])
        }
    }

    private func dataFromHex(_ hex: String) throws -> Data {
        guard hex.count.isMultiple(of: 2) else {
            throw NSError(domain: "PolarFileUtilsTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Hex string must have even length"])
        }
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                throw NSError(domain: "PolarFileUtilsTest", code: 4, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte"])
            }
            data.append(byte)
            index = next
        }
        return data
    }

    private func data(from stream: InputStream) throws -> Data {
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count < 0 {
                throw stream.streamError ?? NSError(domain: "PolarFileUtilsTest", code: 5)
            }
            if count == 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }

    
    private func mockFileContent() -> Data {
        return try! Data_PbAutomaticSampleSessions.with {
            $0.samples = [
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [70, 72, 74].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 16; $0.minute = 49; $0.seconds = 36; $0.millis = 0 }
                    $0.triggerType = .triggerTypeLowActivity
                },
                Data_PbAutomaticHeartRateSamples.with {
                    $0.heartRate = [90, 91, 93].map { UInt32($0) }
                    $0.time = PbTime.with { $0.hour = 18; $0.minute = 0; $0.seconds = 0; $0.millis = 0 }
                    $0.triggerType = .triggerTypeTimed
                }
            ]
            $0.ppiSamples = [
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 3; $0.minute = 4; $0.seconds = 5; $0.millis = 6 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = .ppiTriggerTypeAutomatic
                },
                Data_PbPpIntervalAutoSamples.with {
                    $0.recordingTime = PbTime.with { $0.hour = 5; $0.minute = 6; $0.seconds = 7; $0.millis = 8 }
                    $0.ppi = Data_PbPpIntervalSamples.with {
                        $0.ppiDelta = [2000, -100, -200, -300].map { Int32($0) }
                        $0.ppiErrorEstimateDelta = [10, 1, 2, 3].map { Int32($0) }
                        $0.status = [1, 2, 3, 4].map { UInt32($0) }
                    }
                    $0.triggerType = .ppiTriggerTypeAutomatic
                }
            ]
            $0.day = PbDate.with { $0.year = 2525; $0.month = 2; $0.day = 26 }
        }.serializedData()
    }
}

private let FILE_RUNTIME_ERROR_READINESS_FAMILIES = [
    "directory-missing-status-103",
    "directory-malformed-payload-parse-failure",
    "read-file-transport-error",
    "write-file-put-header-before-stream-error",
    "write-file-payload-capture-before-stream-error",
    "delete-file-response-error-status-message",
    "command-path-capture-for-every-operation",
    "facade-error-mapping-deferred",
    "platform-runtime-vector-reference-gate",
    "compile-verification-gate"
]

private let FILE_RUNTIME_ERROR_POLICY_CASE_IDS = [
    "directory-list-response-error-103",
    "directory-list-malformed-payload",
    "read-file-transport-error",
    "write-file-stream-error-after-header",
    "delete-file-response-error"
]

private let FILE_RUNTIME_ERROR_MIGRATION_REQUIREMENT = "Before moving file utility orchestration into common KMP code, implement fake PFTP request and write-stream tests that cover malformed directory payloads, request-level transport errors, response-error status mapping, and write-stream failures after the PUT header is prepared."

private let FILE_RUNTIME_ERROR_READINESS_COMMON_DECISION = "File runtime error migration may proceed only after runtime-error-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS file tests continue to reference the same vectors, directory missing status 103, malformed directory payload parse failure, read transport errors, write PUT header and payload capture before stream failure, delete response-error status/message mapping, command/path capture, public facade error mapping, and the shared tests are compile-verified."

private let FILE_FACADE_RUNTIME_OPERATION_IDS = [
    "read-low-level-file-success",
    "read-low-level-file-empty-success",
    "read-low-level-file-request-failure",
    "read-low-level-file-response-error",
    "write-low-level-file-success",
    "write-low-level-file-progress-success",
    "write-low-level-file-stream-failure",
    "write-low-level-file-response-error",
    "delete-low-level-file-success",
    "delete-low-level-file-request-failure",
    "delete-low-level-file-response-error"
]

private let FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION = "A shared file facade runtime may own deterministic GET/PUT/REMOVE planning, empty read payloads, write progress consumption, and payload capture only after platform facades keep public error mapping, read/write/delete request and response-error propagation, and directory-list traversal policies pinned."

private let FILE_FACADE_RUNTIME_MIGRATION_DECISION = "Promote low-level file facade planning only after read/write/delete public APIs reference this vector, directory traversal remains covered by list-files vectors, and runtime-error-policy.json keeps malformed directory, response-error, transport-error, empty read payload, delete request failure, write progress success, and write-stream failure behavior pinned."

private let FILE_FACADE_RUNTIME_READINESS_FAMILIES = [
    "low-level-file-path-gate",
    "read-file-get-success",
    "read-file-empty-success",
    "read-file-request-failure",
    "read-file-response-error",
    "write-file-put-success",
    "write-file-payload-capture",
    "write-file-progress-before-completion",
    "write-file-stream-failure-after-payload",
    "write-file-response-error-after-payload",
    "delete-file-remove-success",
    "delete-file-request-failure",
    "delete-file-response-error",
    "directory-list-shallow-vector-reference-gate",
    "directory-list-recursive-vector-reference-gate",
    "read-write-delete-model-vector-reference-gate",
    "runtime-error-policy-reference-gate",
    "malformed-directory-policy-gate",
    "response-error-policy-gate",
    "facade-error-mapping-gate",
    "platform-facade-vector-reference-gate",
    "compile-verification-gate"
]

private let FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION = "File facade runtime migration may proceed only after file-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, directory-list traversal vectors remain linked, runtime-error-policy.json keeps malformed-directory, response-error, transport-error, empty read payload, delete request failure, write progress before completion, read/write/delete response-error, and write-stream failure behavior covered, public facade error mapping is pinned, and the shared tests are compile-verified."

// MARK: - DateFormatter helper
private extension DateFormatter {
    @discardableResult
    func apply(_ configure: (DateFormatter) -> Void) -> DateFormatter {
        configure(self)
        return self
    }
}
