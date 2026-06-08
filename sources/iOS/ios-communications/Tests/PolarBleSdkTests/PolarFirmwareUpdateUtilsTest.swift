//  Copyright © 2024 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk
import Zip

class PolarFirmwareUpdateUtilsTest: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        PolarFirmwareUpdateUtils.packageExtractor = ZipFirmwarePackageExtractor()
        mockClient = nil
    }

    func testReadDeviceFirmwareInfo_shouldReturnFirmwareInfo() async throws {
        // Arrange
        let expectedDeviceId = "123456"
        let expectedFirmwareVersion = "1.2.0"
        let expectedModelName = "Model"
        let expectedHardwareCode = "00.112233"
        
        let proto = Data_PbDeviceInfo.with {
            $0.deviceVersion = .with {
                $0.major = 1
                $0.minor = 2
                $0.patch = 0
            }
            $0.modelName = expectedModelName
            $0.hardwareCode = expectedHardwareCode
        }
        
        mockClient.requestReturnValue = .success(try proto.serializedData())

        // Act
        let firmwareInfo = await PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: mockClient, deviceId: expectedDeviceId)

        // Assert
        XCTAssertEqual(firmwareInfo?.deviceFwVersion, expectedFirmwareVersion)
        XCTAssertEqual(firmwareInfo?.deviceModelName, expectedModelName)
        XCTAssertEqual(firmwareInfo?.deviceHardwareCode, expectedHardwareCode)
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(PolarRuntimePlanner.firmwareDeviceInfoPath(), PolarFirmwareUpdateUtils.DEVICE_FIRMWARE_INFO_PATH)
        XCTAssertEqual(PolarFirmwareUpdateUtils.DEVICE_FIRMWARE_INFO_PATH, requestOperation.path)
    }
    
    func testIsAvailableFirmwareVersionHigher_shouldReturnTrue_whenCurrentVersionIsSmallerThanAvailableVersion() {
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "1.0.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.0.1"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.1.0"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "3.0.0"
            )
        )
    }
    
    func testIsAvailableFirmwareVersionHigher_shouldReturnFalse_whenCurrentVersionIsSameOrHigherThanAvailableVersion() {
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "1.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.1",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.1.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "3.0.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.0.0"
            )
        )
    }
    
    func testFwFileComparatorSortsFilesCorrectly() {
           // Arrange
           let btFile = "BTUPDAT.BIN"
           let sysFile = "SYSUPDAT.IMG"
           let touchFile = "TCHUPDAT.BIN"
           var files = [btFile, sysFile, touchFile]
           
           // Act
           files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }
           
           // Assert
           XCTAssertEqual(files[0], btFile, "First file should be BTUPDAT.BIN")
           XCTAssertEqual(files[1], touchFile, "Second file should be TCHUPDAT.BIN")
           XCTAssertEqual(files[2], sysFile, "Last file should be SYSUPDAT.IMG")
       }
       
       func testFwFileComparatorKeepsAlreadySortedFiles() {
           // Arrange
           let f1 = "BTUPDAT.BIN"
           let f2 = "TCHUPDAT.BIN"
           let f3 = "SYSUPDAT.IMG"
           var files = [f1, f2, f3]
           
           // Act
           files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }
           
           // Assert
           XCTAssertEqual(files[0], f1, "Files should maintain initial order if already sorted")
           XCTAssertEqual(files[1], f2, "Files should maintain initial order if already sorted")
           XCTAssertEqual(files[2], f3, "Files should maintain initial order if already sorted")
       }

    func testFirmwareUtilityUsesSharedPlannerWhenLinked() async throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("1.2.0", PolarRuntimePlanner.firmwareDeviceVersion(major: 1, minor: 2, patch: 0))
        XCTAssertTrue(PolarRuntimePlanner.isFirmwareVersionHigher(currentVersion: "1.0.0", availableVersion: "1.0.1"))
        XCTAssertFalse(PolarRuntimePlanner.isFirmwareVersionHigher(currentVersion: "2.0.0", availableVersion: "1.0.0"))
        XCTAssertEqual(0, PolarRuntimePlanner.firmwareFilePriority("BTUPDAT.BIN"))
        XCTAssertEqual(1, PolarRuntimePlanner.firmwareFilePriority("SYSUPDAT.IMG"))

        let expectedDeviceId = "123456"
        let proto = Data_PbDeviceInfo.with {
            $0.deviceVersion = .with {
                $0.major = 1
                $0.minor = 2
                $0.patch = 0
            }
            $0.modelName = "Model"
            $0.hardwareCode = "00.112233"
        }
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let firmwareInfo = await PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: mockClient, deviceId: expectedDeviceId)

        XCTAssertEqual(firmwareInfo?.deviceFwVersion, "1.2.0")
        XCTAssertTrue(PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(currentVersion: "1.0.0", availableVersion: "1.0.1"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testFirmwareDeviceInfoGoldenVectorsMapProtoToModel() async throws {
        for vector in try loadFirmwareUpdateGoldenVectors().filter({ inputKind($0) == "deviceInfo" }) {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let protoFields = try XCTUnwrap(input["proto"] as? [String: Any], id)
            let version = try XCTUnwrap(protoFields["version"] as? [String: Any], id)
            var proto = Data_PbDeviceInfo()
            proto.deviceVersion = PbVersion.with {
                $0.major = UInt32(try! int32(version, "major", id: id))
                $0.minor = UInt32(try! int32(version, "minor", id: id))
                $0.patch = UInt32(try! int32(version, "patch", id: id))
            }
            proto.modelName = try XCTUnwrap(protoFields["modelName"] as? String, id)
            proto.hardwareCode = try XCTUnwrap(protoFields["hardwareCode"] as? String, id)
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockClient.requestReturnValue = .success(try proto.serializedData())

            let firmwareInfo = await PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: mockClient, deviceId: "123456")
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)

            XCTAssertEqual(firmwareInfo?.deviceFwVersion, try XCTUnwrap(expected["deviceFwVersion"] as? String, id), id)
            XCTAssertEqual(firmwareInfo?.deviceModelName, try XCTUnwrap(expected["deviceModelName"] as? String, id), id)
            XCTAssertEqual(firmwareInfo?.deviceHardwareCode, try XCTUnwrap(expected["deviceHardwareCode"] as? String, id), id)
        }
    }

    func testFirmwareVersionComparisonGoldenVectorsMatchCurrentPolicy() throws {
        for testCase in try firmwareVectorCases(kind: "versionComparison") {
            let currentVersion = try XCTUnwrap(testCase["currentVersion"] as? String)
            let availableVersion = try XCTUnwrap(testCase["availableVersion"] as? String)
            XCTAssertEqual(
                PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(currentVersion: currentVersion, availableVersion: availableVersion),
                try XCTUnwrap(testCase["expectedHigher"] as? Bool),
                "\(currentVersion) -> \(availableVersion)"
            )
        }
    }

    func testFirmwareVersionComparisonInvalidGoldenVectorsDocumentCurrentPolicy() throws {
        for testCase in try firmwareVectorCases(kind: "versionComparisonError") {
            let expected = try XCTUnwrap(testCase["expectedError"] as? [String: Any])
            XCTAssertEqual(try XCTUnwrap(expected["ios"] as? String), "fatal")
        }
    }

    func testFirmwareFileOrderingGoldenVectorsKeepSystemUpdateLast() throws {
        for testCase in try firmwareVectorCases(kind: "fileOrdering") {
            var files = try XCTUnwrap(testCase["input"] as? [String])

            files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }

            XCTAssertEqual(files, try XCTUnwrap(testCase["expected"] as? [String]))
        }
    }

    func testFirmwareRuntimePlannerOrderingUsesSharedPolicyWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual(
            ["TCHUPDAT.BIN", "APPUPDAT.BIN", "BTUPDAT.BIN", "SYSUPDAT.IMG"],
            PolarRuntimePlanner.orderFirmwareFiles(["TCHUPDAT.BIN", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN"])
        )
        XCTAssertEqual(
            ["APPUPDAT.BIN", "BTUPDAT.BIN", "README.TXT", "SYSUPDAT.IMG"],
            PolarRuntimePlanner.firmwarePayloadFileNames(["readme.txt", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN", "README.TXT"])
        )
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testFirmwarePackageEntryFilterUsesSharedPolicyWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertFalse(PolarRuntimePlanner.firmwarePackageEntryIsPayload("readme.txt"))
        XCTAssertFalse(PolarFirmwareUpdateUtils.firmwarePackageEntryIsPayload("readme.txt"))
        XCTAssertTrue(PolarFirmwareUpdateUtils.firmwarePackageEntryIsPayload("README.TXT"))
        XCTAssertTrue(PolarFirmwareUpdateUtils.firmwarePackageEntryIsPayload("BTUPDAT.BIN"))
        XCTAssertTrue(PolarFirmwareUpdateUtils.firmwarePackageEntryIsPayload("SYSUPDAT.IMG"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testUnzipFirmwarePackageSkipsSharedNonPayloadEntries() throws {
        let sourceDirectory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let zipURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathExtension("zip")
        try FileManager.default.createDirectory(at: sourceDirectory, withIntermediateDirectories: true)
        defer {
            try? FileManager.default.removeItem(at: sourceDirectory)
            try? FileManager.default.removeItem(at: zipURL)
        }
        try Data("skip".utf8).write(to: sourceDirectory.appendingPathComponent("readme.txt"))
        try Data([0x01]).write(to: sourceDirectory.appendingPathComponent("BTUPDAT.BIN"))
        try Data([0x02]).write(to: sourceDirectory.appendingPathComponent("SYSUPDAT.IMG"))
        try Zip.zipFiles(paths: [
            sourceDirectory.appendingPathComponent("readme.txt"),
            sourceDirectory.appendingPathComponent("BTUPDAT.BIN"),
            sourceDirectory.appendingPathComponent("SYSUPDAT.IMG")
        ], zipFilePath: zipURL, password: nil, progress: nil)
        let unzipped = try XCTUnwrap(PolarFirmwareUpdateUtils.unzipFirmwarePackage(zippedData: try Data(contentsOf: zipURL)))
        XCTAssertNil(unzipped["readme.txt"])
        XCTAssertEqual(Data([0x01]), unzipped["BTUPDAT.BIN"])
        XCTAssertEqual(Data([0x02]), unzipped["SYSUPDAT.IMG"])
        XCTAssertEqual(PolarRuntimePlanner.firmwarePayloadFileNames(Array(unzipped.keys)).sorted(), Array(unzipped.keys).sorted())
    }

    func testUnzipFirmwarePackageUsesInjectedExtractor() throws {
        let extractor = CapturingFirmwarePackageExtractor(result: ["BTUPDAT.BIN": Data([0x0B])])
        PolarFirmwareUpdateUtils.packageExtractor = extractor

        let unzipped = try XCTUnwrap(PolarFirmwareUpdateUtils.unzipFirmwarePackage(zippedData: Data([0x01, 0x02])))

        XCTAssertEqual([Data([0x01, 0x02])], extractor.zippedPayloads)
        XCTAssertEqual(Data([0x0B]), unzipped["BTUPDAT.BIN"])
    }

    func testFirmwareRebootWaitFilterUsesSharedPolicyWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertTrue(PolarRuntimePlanner.firmwareFileTriggersRebootWait("/SYSUPDAT.IMG"))
        XCTAssertTrue(PolarFirmwareUpdateUtils.firmwareFileTriggersRebootWait("/SYSUPDAT.IMG"))
        XCTAssertFalse(PolarFirmwareUpdateUtils.firmwareFileTriggersRebootWait("BTUPDAT.BIN"))
        XCTAssertFalse(PolarFirmwareUpdateUtils.firmwareFileTriggersRebootWait("sysupdat.img"))
        XCTAssertEqual("success-rebooting", PolarRuntimePlanner.firmwareWriteTerminal(errorCode: 1, fileName: "/SYSUPDAT.IMG"))
        XCTAssertEqual("propagate-error", PolarRuntimePlanner.firmwareWriteTerminal(errorCode: 1, fileName: "BTUPDAT.BIN"))
        XCTAssertEqual("battery-too-low", PolarRuntimePlanner.firmwareWriteTerminal(errorCode: 209, fileName: "/SYSUPDAT.IMG"))
        XCTAssertEqual("propagate-error", PolarRuntimePlanner.firmwareWriteTerminal(errorCode: 103, fileName: "/SYSUPDAT.IMG"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testFirmwareWriteProgressPolicyUsesSharedZeroSafeThresholdsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual(0, PolarRuntimePlanner.firmwareWriteProgressPercent(bytesWritten: 0, payloadSize: 0))
        XCTAssertEqual(0, PolarRuntimePlanner.firmwareWriteProgressPercent(bytesWritten: 12, payloadSize: 0))
        XCTAssertEqual(50, PolarRuntimePlanner.firmwareWriteProgressPercent(bytesWritten: 2, payloadSize: 4))
        XCTAssertTrue(PolarRuntimePlanner.shouldEmitFirmwareWriteProgress(lastBytesWritten: 0, bytesWritten: 0, payloadSize: 0, minPercentageIncrement: 25))
        XCTAssertTrue(PolarRuntimePlanner.shouldEmitFirmwareWriteProgress(lastBytesWritten: 2, bytesWritten: 4, payloadSize: 4, minPercentageIncrement: 75))
        XCTAssertFalse(PolarRuntimePlanner.shouldEmitFirmwareWriteProgress(lastBytesWritten: 2, bytesWritten: 3, payloadSize: 100, minPercentageIncrement: 25))
        XCTAssertFalse(PolarRuntimePlanner.shouldEmitFirmwareWriteProgress(lastBytesWritten: 2, bytesWritten: 3, payloadSize: 100, minPercentageIncrement: 25, timeSinceLastEmitMs: 4_999))
        XCTAssertTrue(PolarRuntimePlanner.shouldEmitFirmwareWriteProgress(lastBytesWritten: 2, bytesWritten: 3, payloadSize: 100, minPercentageIncrement: 25, timeSinceLastEmitMs: 5_000))
        XCTAssertTrue(PolarRuntimePlanner.shouldEmitFirmwareWriteProgress(lastBytesWritten: 2, bytesWritten: 52, payloadSize: 100, minPercentageIncrement: 25))
        XCTAssertEqual(["/BTUPDAT.BIN", "/SYSUPDAT.IMG"], PolarRuntimePlanner.firmwareWritePaths(["SYSUPDAT.IMG", "BTUPDAT.BIN"]))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testFirmwareUpdateApiUsesInjectedTransportForCheckRequest() throws {
        let transport = CapturingFirmwareUpdateTransport()
        let responseData = try JSONSerialization.data(withJSONObject: [
            "version": "9.9.9",
            "fileUrl": "https://example.invalid/fw.zip"
        ])
        transport.nextResponse = (
            data: responseData,
            response: HTTPURLResponse(url: URL(string: "https://firmware-management.polar.com/api/v1/firmware-update/check")!, statusCode: 200, httpVersion: nil, headerFields: nil),
            error: nil
        )
        let api = FirmwareUpdateApi(transport: transport)
        let finished = expectation(description: "check firmware update")
        var result: Result<FirmwareUpdateResponse, Error>?

        api.checkFirmwareUpdate(
            firmwareUpdateRequest: FirmwareUpdateRequest(clientId: "sdk", uuid: "device", firmwareVersion: "1.0.0", hardwareCode: "hw")
        ) {
            result = $0
            finished.fulfill()
        }

        wait(for: [finished], timeout: 1)
        let request = try XCTUnwrap(transport.requests.first)
        XCTAssertEqual(request.url?.absoluteString, "https://firmware-management.polar.com/api/v1/firmware-update/check")
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Accept"), "application/json")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json")
        let body = try XCTUnwrap(request.httpBody)
        let bodyJson = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: String])
        XCTAssertEqual(bodyJson["clientId"], "sdk")
        XCTAssertEqual(bodyJson["uuid"], "device")
        XCTAssertEqual(bodyJson["firmwareVersion"], "1.0.0")
        XCTAssertEqual(bodyJson["hardwareCode"], "hw")
        guard case .success(let response) = try XCTUnwrap(result) else {
            return XCTFail("Expected firmware update success")
        }
        XCTAssertEqual(response.version, "9.9.9")
        XCTAssertEqual(response.fileUrl, "https://example.invalid/fw.zip")
        XCTAssertEqual(response.statusCode, 200)
    }

    func testFirmwareUpdateApiUsesInjectedTransportForPackageDownloadFailure() async throws {
        let transport = CapturingFirmwareUpdateTransport()
        let expectedError = NSError(domain: "firmware-test", code: 7)
        transport.nextResponse = (data: nil, response: nil, error: expectedError)
        let api = FirmwareUpdateApi(transport: transport)

        do {
            _ = try await api.getFirmwareUpdatePackage(url: "https://example.invalid/fw.zip")
            XCTFail("Expected package download failure")
        } catch {
            XCTAssertEqual((error as NSError).domain, expectedError.domain)
            XCTAssertEqual((error as NSError).code, expectedError.code)
        }

        XCTAssertEqual(transport.requests.first?.url?.absoluteString, "https://example.invalid/fw.zip")
    }

    func testWriteFirmwareFilesUsesInjectedBleWriterAndSharedProgressPolicy() async throws {
        let api = PolarBleApiImpl(DispatchQueue(label: "PolarFirmwareUpdateUtilsTest.firmware-writer"), features: [.feature_polar_firmware_update])
        let baseDate = Date(timeIntervalSince1970: 0)
        var dateOffsets: [TimeInterval] = [0, 0, 0, 0, 0, 0]
        var writeRequests: [(identifier: String, path: String, data: Data)] = []
        api.firmwareProgressDateProvider = {
            return baseDate.addingTimeInterval(dateOffsets.isEmpty ? 0 : dateOffsets.removeFirst())
        }
        api.firmwareFileWriteStreamFactory = { identifier, path, data in
            writeRequests.append((identifier: identifier, path: path, data: data))
            return AsyncThrowingStream { continuation in
                continuation.yield(0)
                continuation.yield(UInt(data.count))
                continuation.finish()
            }
        }

        let statuses = try await collectFirmwareStatuses(
            api.writeFirmwareFilesToDeviceAsync(
                "device-id",
                firmwareFiles: [
                    ("BTUPDAT.BIN", Data([0x01, 0x02, 0x03, 0x04])),
                    ("SYSUPDAT.IMG", Data([0x05, 0x06]))
                ],
                minPercentageIncrement: 25
            )
        )

        XCTAssertEqual(writeRequests.map { $0.identifier }, ["device-id", "device-id"])
        XCTAssertEqual(writeRequests.map { $0.path }, ["/BTUPDAT.BIN", "/SYSUPDAT.IMG"])
        XCTAssertEqual(writeRequests.map { $0.data }, [Data([0x01, 0x02, 0x03, 0x04]), Data([0x05, 0x06])])
        XCTAssertEqual(firmwareWritingDetails(statuses), [
            "Writing firmware update file BTUPDAT.BIN, (0%) bytes written: 0/4",
            "Writing firmware update file BTUPDAT.BIN, (100%) bytes written: 4/4",
            "Writing firmware update file SYSUPDAT.IMG, (0%) bytes written: 0/2",
            "Writing firmware update file SYSUPDAT.IMG, (100%) bytes written: 2/2"
        ])
    }

    func testFirmwareGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadFirmwareUpdateGoldenVectors() {
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

    func testWorkflowRuntimePolicyVectorIsPinnedBeforeWorkflowMigration() throws {
        let vector = try XCTUnwrap(try loadFirmwareUpdateGoldenVectors().first { ($0["id"] as? String) == "workflow-runtime-policy" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let commonPrototype = try XCTUnwrap(expected["commonWorkflowPrototype"] as? [String: Any])
        let scenarios = try XCTUnwrap(input["scenarios"] as? [[String: Any]])
        let scenarioIds = scenarios.compactMap { $0["id"] as? String }
        let commonPrototypeCases = try XCTUnwrap(commonPrototype["cases"] as? [[String: Any]])
        let commonPrototypeCaseIds = commonPrototypeCases.compactMap { $0["id"] as? String }
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any])
        let platformExpectations = try XCTUnwrap(vector["platformExpectations"] as? [String: Any])
        let commonDecision = try XCTUnwrap(platformExpectations["commonDecision"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])

        XCTAssertEqual(input["kind"] as? String, "firmwareWorkflowRuntimePolicy", "workflow-runtime-policy")
        XCTAssertEqual(FIRMWARE_WORKFLOW_SCENARIOS, scenarioIds, "workflow-runtime-policy")
        XCTAssertEqual(expected["policy"] as? String, "firmware-update-workflow-runtime-matrix", "workflow-runtime-policy")
        XCTAssertEqual(expected["migrationRequirement"] as? String, FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT, "workflow-runtime-policy")
        XCTAssertEqual(commonPrototype["status"] as? String, "executable shared commonTest", "workflow-runtime-policy")
        XCTAssertEqual(FIRMWARE_WORKFLOW_SCENARIOS, commonPrototypeCaseIds, "workflow-runtime-policy")
        XCTAssertEqual(execution["common"] as? String, "shared-common-test", "workflow-runtime-policy")
        XCTAssertEqual(execution["android"] as? String, "partial-production-shared-policy-consumption", "workflow-runtime-policy")
        XCTAssertEqual(execution["ios"] as? String, "partial-production-shared-policy-consumption", "workflow-runtime-policy")
        XCTAssertEqual(platformExpectations["android"] as? String, FIRMWARE_WORKFLOW_ANDROID_PRODUCTION_EVIDENCE, "workflow-runtime-policy")
        XCTAssertEqual(platformExpectations["ios"] as? String, FIRMWARE_WORKFLOW_IOS_PRODUCTION_EVIDENCE, "workflow-runtime-policy")
        XCTAssertEqual(commonDecision["workflowPolicy"] as? String, FIRMWARE_WORKFLOW_COMMON_DECISION, "workflow-runtime-policy")
        XCTAssertNotNil(vector["execution"], "workflow-runtime-policy")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "workflow-runtime-policy"), ["com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "workflow-runtime-policy"), ["PolarFirmwareUpdateUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "workflow-runtime-policy"), ["com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"])
    }

    func testWorkflowRuntimeReadinessManifestIsPinnedBeforeWorkflowMigration() throws {
        let vector = try XCTUnwrap(try loadFirmwareUpdateGoldenVectors().first { ($0["id"] as? String) == "workflow-runtime-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(input["kind"] as? String, "firmwareWorkflowRuntimeReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/firmware-update/workflow-runtime-policy.json")
        XCTAssertEqual(FIRMWARE_WORKFLOW_READINESS_FAMILIES, requiredFamilies)
        XCTAssertEqual(FIRMWARE_WORKFLOW_READINESS_FAMILIES, coveredFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION)
        XCTAssertEqual((expected["commonRuntimePrototype"] as? [String: Any])?["status"] as? String, "executable shared commonTest runtime planning guard")
        XCTAssertEqual((expected["commonRuntimePrototype"] as? [String: Any])?["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"], "workflow-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarFirmwareUpdateUtilsTest"], "workflow-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"], "workflow-runtime-readiness")
    }

    func testFirmwareUtilityReadinessManifestIsPinnedBeforeUtilityMigration() throws {
        let vector = try XCTUnwrap(try loadFirmwareUpdateGoldenVectors().first { ($0["id"] as? String) == "firmware-utility-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])

        XCTAssertEqual(input["kind"] as? String, "firmwareUtilityReadiness")
        XCTAssertEqual(expected["migrationReadiness"] as? String, "compileVerifiedPreMigrationCharacterization")
        XCTAssertEqual(FIRMWARE_UTILITY_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "firmware-utility-readiness")
        XCTAssertEqual(FIRMWARE_UTILITY_READINESS_FAMILIES, requiredFamilies, "firmware-utility-readiness")
        XCTAssertEqual(FIRMWARE_UTILITY_READINESS_FAMILIES, coveredFamilies, "firmware-utility-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"], "firmware-utility-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarFirmwareUpdateUtilsTest"], "firmware-utility-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.FirmwareUpdateUtilityCommonPolicyTest"], "firmware-utility-readiness")
    }

    private func firmwareVectorCases(kind: String) throws -> [[String: Any]] {
        return try loadFirmwareUpdateGoldenVectors()
            .filter { inputKind($0) == kind }
            .flatMap { vector -> [[String: Any]] in
                let input = try XCTUnwrap(vector["input"] as? [String: Any], kind)
                return try XCTUnwrap(input["cases"] as? [[String: Any]], kind)
            }
    }

    private func inputKind(_ vector: [String: Any]) -> String? {
        return (vector["input"] as? [String: Any])?["kind"] as? String
    }

    private func loadFirmwareUpdateGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/firmware-update")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }


    private func int32(_ object: [String: Any], _ key: String, id: String) throws -> Int32 {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").int32Value
    }

    private func collectFirmwareStatuses(_ stream: AsyncThrowingStream<FirmwareUpdateStatus, Error>) async throws -> [FirmwareUpdateStatus] {
        var statuses: [FirmwareUpdateStatus] = []
        for try await status in stream {
            statuses.append(status)
        }
        return statuses
    }

    private func firmwareWritingDetails(_ statuses: [FirmwareUpdateStatus]) -> [String] {
        return statuses.compactMap { status in
            if case .writingFwUpdatePackage(let details) = status {
                return details
            }
            return nil
        }
    }
}

private let FIRMWARE_UTILITY_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/firmware-update/device-info-basic.json",
    "sdk/firmware-update/device-info-zero-version.json",
    "sdk/firmware-update/version-comparison.json",
    "sdk/firmware-update/version-comparison-invalid.json",
    "sdk/firmware-update/file-ordering.json"
]

private let FIRMWARE_UTILITY_READINESS_FAMILIES = [
    "device-info-protobuf-mapping",
    "zero-version-preservation",
    "dotted-integer-version-comparison",
    "invalid-version-typed-parse-failure",
    "system-update-file-ordering-last",
    "platform-firmware-utility-vector-references",
    "compile-verification-gate"
]

private let FIRMWARE_WORKFLOW_READINESS_FAMILIES = [
    "fake-network-availability",
    "download-failure",
    "fake-filesystem-zip-extraction",
    "empty-or-invalid-package",
    "ble-write-progress",
    "system-update-written-last",
    "reboot-response-success",
    "terminal-device-error",
    "cleanup-gate",
    "cancellation-gate",
    "cancellation-cleanup-after-package-fetch",
    "retryable-server-failure-gate",
    "facade-error-mapping-gate",
    "compile-verification-gate"
]

private let FIRMWARE_WORKFLOW_SCENARIOS = [
    "check-update-not-available",
    "check-update-available",
    "download-failure",
    "retryable-server-failure",
    "empty-or-invalid-zip",
    "cancel-after-package-fetch-cleans-up-before-ble-write",
    "write-package-success-with-system-update-last",
    "system-update-reboot-response-is-success",
    "battery-too-low-response-is-terminal-failure"
]

private let FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT = "Before moving firmware update orchestration into common KMP code, implement injectable fake network, fake filesystem or zip extraction, and fake BLE write dependencies that can reproduce update availability, download failures, invalid packages, sorted package writes, reboot success, and terminal device errors."

private let FIRMWARE_WORKFLOW_COMMON_DECISION = "separate device-info parsing, server availability, retryable server failures, package download, zip extraction, file ordering, BLE write progress, reboot success, and terminal device errors into typed common workflow states before KMP migration"

private let FIRMWARE_WORKFLOW_ANDROID_PRODUCTION_EVIDENCE = "BDBleApiImpl and PolarFirmwareUpdateUtils consume shared planning for device-info path, payload entry filtering, firmware file ordering/write paths, PSFTP write progress throttling, reboot response success, and battery-too-low terminal write policy while keeping network, zip parsing, retry scheduling, backup, reconnect, filesystem, and BLE writes platform-owned."

private let FIRMWARE_WORKFLOW_IOS_PRODUCTION_EVIDENCE = "PolarBleApiImpl and PolarFirmwareUpdateUtils consume shared planning for device-info path, payload entry filtering, firmware file ordering/write paths, PSFTP write progress throttling, reboot response success, and battery-too-low terminal write policy while keeping network, zip parsing, retry scheduling, backup, reconnect, filesystem, and BLE writes platform-owned."

private let FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION = "Firmware workflow migration may proceed only after workflow-runtime-policy.json and this readiness manifest are executable from shared commonTest, fake network/filesystem/BLE writer dependencies are injectable, shared production file-order/progress/terminal write policy consumption remains pinned on Android and iOS, retryable fake-network server failure classification, terminal device errors, and cancellation cleanup before BLE writes are pinned, retry scheduling has explicit platform facade coverage, public facade error mapping is pinned, and the shared tests are compile-verified."

private final class CapturingFirmwareUpdateTransport: FirmwareUpdateNetworkTransport {
    var requests: [URLRequest] = []
    var nextResponse: (data: Data?, response: URLResponse?, error: Error?) = (nil, nil, nil)

    func firmwareDataTask(with request: URLRequest, completionHandler: @escaping (Data?, URLResponse?, Error?) -> Void) -> FirmwareUpdateDataTask {
        requests.append(request)
        return CapturingFirmwareUpdateDataTask {
            completionHandler(self.nextResponse.data, self.nextResponse.response, self.nextResponse.error)
        }
    }
}

private final class CapturingFirmwareUpdateDataTask: FirmwareUpdateDataTask {
    private let onResume: () -> Void

    init(onResume: @escaping () -> Void) {
        self.onResume = onResume
    }

    func resume() {
        onResume()
    }
}

private final class CapturingFirmwarePackageExtractor: FirmwarePackageExtracting {
    private let result: [String: Data]?
    private(set) var zippedPayloads: [Data] = []

    init(result: [String: Data]?) {
        self.result = result
    }

    func unzipFirmwarePackage(zippedData: Data) -> [String: Data]? {
        zippedPayloads.append(zippedData)
        return result
    }
}
