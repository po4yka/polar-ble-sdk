import XCTest
import Foundation
@testable import PolarBleSdk

class PolarBackupManagerTest: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    var backupManager: PolarBackupManager!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        backupManager = PolarBackupManager(client: mockClient)
    }

    override func tearDownWithError() throws {
        mockClient = nil
        backupManager = nil
    }

    func testBackupDevice() async throws {
        // Arrange
        let mockBackupFileContent = "/SYS/BT/\n/U/*/USERID.BPB\n/RANDOM/FILE.TXT\n"

        var backupEntry = Protocol_PbPFtpEntry()
        backupEntry.name = "BACKUP.TXT"
        backupEntry.size = 1234

        var btEntry = Protocol_PbPFtpEntry()
        btEntry.name = "BT/"
        btEntry.size = 0

        var directory = Protocol_PbPFtpDirectory()
        directory.entries = [backupEntry, btEntry]
        let mockDirectoryContent = try directory.serializedData()

        var btDevEntry = Protocol_PbPFtpEntry()
        btDevEntry.name = "BTDEV.BPB"
        btDevEntry.size = 1234

        var svStatusEntry = Protocol_PbPFtpEntry()
        svStatusEntry.name = "SVSTATUS.BPB"
        svStatusEntry.size = 5678

        var btDetailDirectory = Protocol_PbPFtpDirectory()
        btDetailDirectory.entries = [btDevEntry, svStatusEntry]
        let mockBTDetailContent = try btDetailDirectory.serializedData()

        mockClient.requestReturnValueClosure = { requestData in
            let request = try Protocol_PbPFtpOperation(serializedBytes: requestData, partial: false)
            if request.path.contains("/SYS/BACKUP.TXT") {
                return mockBackupFileContent.data(using: .utf8)!
            } else if request.path.contains("/SYS/BT/") {
                return mockBTDetailContent
            } else if request.path.contains("/SYS/") {
                return mockDirectoryContent
            } else {
                return Data()
            }
        }

        // Act
        let files = try await backupManager.backupDevice()

        // Assert
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/U/0/USERID.BPB" })
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/SYS/BT/BTDEV.BPB" })
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/SYS/BT/SVSTATUS.BPB" })
        XCTAssertTrue(files.contains { $0.directory + $0.fileName == "/RANDOM/FILE.TXT" })
        XCTAssertEqual(mockClient.requestCalls.count, 10)
    }

    func testRestoreBackup() async throws {
        // Arrange
        let mockFileData = [
            PolarBackupManager.BackupFileData(data: Data(), directory: "/SYS/BT/", fileName: "BTDEV.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/SYS/BT/", fileName: "SVSTATUS.BPB"),
            PolarBackupManager.BackupFileData(data: Data(), directory: "/RANDOM/", fileName: "FILE.TXT")
        ]

        mockClient.writeReturnValue = AsyncThrowingStream { $0.finish() }

        // Act
        try await backupManager.restoreBackup(backupFiles: mockFileData)

        // Assert
        XCTAssertEqual(mockClient.writeCalls.count, 3)
    }

    func testBackupGoldenVectorsExpandFilesAndPreserveRestoreWrites() async throws {
        let vector = try loadBackupVector(id: "backup-expansion-and-restore-writes")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let directories = try XCTUnwrap(input["directories"] as? [String: [[String: Any]]])
        let files = try XCTUnwrap(input["files"] as? [String: String])

        mockClient.requestReturnValueClosure = { requestData in
            let request = try Protocol_PbPFtpOperation(serializedBytes: requestData)
            if let entries = directories[request.path] {
                return try self.directoryData(from: entries)
            }
            if let hex = files[request.path] {
                return try self.dataFromHex(hex)
            }
            throw NSError(domain: "PolarBackupManagerTest", code: 10, userInfo: [NSLocalizedDescriptionKey: "Unexpected backup request path \(request.path)"])
        }

        let backupFiles = try await backupManager.backupDevice()
        let actualBackupFiles = Dictionary(uniqueKeysWithValues: backupFiles.map { ($0.directory + $0.fileName, $0.data.hexString) })

        let expectedBackupFiles = try XCTUnwrap(expected["backupFiles"] as? [[String: Any]])
        XCTAssertEqual(actualBackupFiles.count, expectedBackupFiles.count)
        for expectedFile in expectedBackupFiles {
            let path = try XCTUnwrap(expectedFile["path"] as? String)
            XCTAssertEqual(actualBackupFiles[path], try XCTUnwrap(expectedFile["dataHex"] as? String), path)
        }

        mockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        let restoreFiles = try XCTUnwrap(input["restoreFiles"] as? [[String: Any]]).map { file in
            PolarBackupManager.BackupFileData(
                data: try dataFromHex(try XCTUnwrap(file["dataHex"] as? String)),
                directory: try XCTUnwrap(file["directory"] as? String),
                fileName: try XCTUnwrap(file["fileName"] as? String)
            )
        }

        try await backupManager.restoreBackup(backupFiles: restoreFiles)

        let expectedWrites = try XCTUnwrap(expected["restoreWrites"] as? [[String: Any]])
        XCTAssertEqual(mockClient.writeCalls.count, expectedWrites.count)
        for (index, expectedWrite) in expectedWrites.enumerated() {
            let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.writeCalls[index].header as Data)
            XCTAssertEqual(operation.command, try expectedCommand(from: expectedWrite))
            XCTAssertEqual(operation.path, try XCTUnwrap(expectedWrite["path"] as? String))
            XCTAssertEqual(try data(from: mockClient.writeCalls[index].data).hexString, try XCTUnwrap(expectedWrite["dataHex"] as? String))
        }
    }

    func testBackupGoldenVectorsPreserveIOSRestoreFailurePolicy() async throws {
        let vector = try loadBackupVector(id: "restore-failure-platform-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let restoreFileInputs = try XCTUnwrap(input["restoreFiles"] as? [[String: Any]])
        let expectedPolicy = try XCTUnwrap(expected["ios"] as? [String: Any])
        let restoreFiles = try restoreFileInputs.map { file in
            PolarBackupManager.BackupFileData(
                data: try dataFromHex(try XCTUnwrap(file["dataHex"] as? String)),
                directory: try XCTUnwrap(file["directory"] as? String),
                fileName: try XCTUnwrap(file["fileName"] as? String)
            )
        }

        mockClient.writeReturnValues = restoreFileInputs.map { file in
            if (file["writeResult"] as? String) == "failure" {
                return AsyncThrowingStream { continuation in
                    continuation.finish(throwing: NSError(domain: "PolarBackupManagerTest", code: 20, userInfo: [NSLocalizedDescriptionKey: "restore write failed"]))
                }
            }
            return AsyncThrowingStream { continuation in
                continuation.yield(0)
                continuation.finish()
            }
        }

        do {
            try await backupManager.restoreBackup(backupFiles: restoreFiles)
            if try XCTUnwrap(expectedPolicy["throws"] as? Bool) {
                XCTFail("Expected restoreBackup to throw")
            }
        } catch {
            XCTAssertTrue(try XCTUnwrap(expectedPolicy["throws"] as? Bool))
            let expectedError = try XCTUnwrap(expectedPolicy["errorContains"] as? String)
            XCTAssertEqual(error.localizedDescription, "Restore failed for 1 file(s): \(expectedError): Error Domain=PolarBackupManagerTest Code=20 \"restore write failed\" UserInfo={NSLocalizedDescription=restore write failed}")
        }

        let expectedWrites = try XCTUnwrap(expected["writes"] as? [[String: Any]])
        XCTAssertEqual(mockClient.writeCalls.count, expectedWrites.count)
        for (index, expectedWrite) in expectedWrites.enumerated() {
            let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.writeCalls[index].header as Data)
            XCTAssertEqual(operation.command, try expectedCommand(from: expectedWrite))
            XCTAssertEqual(operation.path, try XCTUnwrap(expectedWrite["path"] as? String))
            XCTAssertEqual(try data(from: mockClient.writeCalls[index].data).hexString, try XCTUnwrap(expectedWrite["dataHex"] as? String))
        }
    }

    func testBackupGoldenVectorsFollowNeutralKmpShape() throws {
        for id in ["backup-expansion-and-restore-writes", "restore-failure-platform-policy", "backup-workflow-readiness"] {
            let vector = try loadBackupVector(id: id)
            XCTAssertEqual(vector["id"] as? String, id)
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

    func testBackupWorkflowReadinessManifestIsPinnedBeforeWorkflowMigration() throws {
        let vector = try loadBackupVector(id: "backup-workflow-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(input["kind"] as? String, "backupWorkflowReadiness")
        XCTAssertEqual(BACKUP_WORKFLOW_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "backup-workflow-readiness")
        XCTAssertEqual(BACKUP_WORKFLOW_READINESS_FAMILIES, requiredFamilies, "backup-workflow-readiness")
        XCTAssertEqual(BACKUP_WORKFLOW_READINESS_FAMILIES, coveredFamilies, "backup-workflow-readiness")
        XCTAssertEqual(expected["commonDecision"] as? String, BACKUP_WORKFLOW_READINESS_COMMON_DECISION, "backup-workflow-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarBackupManagerTest"], "backup-workflow-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarBackupManagerTest"], "backup-workflow-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.BackupUtilityCommonPolicyTest"], "backup-workflow-readiness")
    }

    private func loadBackupVector(id: String) throws -> [String: Any] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/backup-utils")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .first { ($0["id"] as? String) == id }!
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

    private func expectedCommand(from operation: [String: Any]) throws -> Protocol_PbPFtpOperation.Command {
        switch try XCTUnwrap(operation["command"] as? String) {
        case "PUT":
            return .put
        default:
            throw NSError(domain: "PolarBackupManagerTest", code: 11, userInfo: [NSLocalizedDescriptionKey: "Unsupported command \(operation)"])
        }
    }

    private func dataFromHex(_ hex: String) throws -> Data {
        guard hex.count.isMultiple(of: 2) else {
            throw NSError(domain: "PolarBackupManagerTest", code: 12, userInfo: [NSLocalizedDescriptionKey: "Hex string must have even length"])
        }
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                throw NSError(domain: "PolarBackupManagerTest", code: 13, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte"])
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
                throw stream.streamError ?? NSError(domain: "PolarBackupManagerTest", code: 14)
            }
            if count == 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }

}

private let BACKUP_WORKFLOW_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/backup-utils/backup-expansion-and-restore-writes.json",
    "sdk/backup-utils/restore-failure-platform-policy.json"
]

private let BACKUP_WORKFLOW_READINESS_FAMILIES = [
    "backup-txt-expansion",
    "backup-directory-expansion",
    "default-user-file-inclusion",
    "backup-file-read-order",
    "restore-put-command-planning",
    "restore-payload-preservation",
    "restore-write-order",
    "restore-failure-platform-split",
    "restore-failure-aggregation-decision-gate",
    "platform-backup-vector-reference-gate",
    "compile-verification-gate"
]

private let BACKUP_WORKFLOW_READINESS_COMMON_DECISION = "Backup workflow migration may proceed only after backup-expansion-and-restore-writes.json, restore-failure-platform-policy.json, and this readiness manifest are executable from shared commonTest, Android and iOS backup tests continue to reference the same vectors, BACKUP.TXT expansion and default user-file inclusion stay covered, restore PUT command order and payload bytes remain pinned, restore failure aggregation is deliberately standardized or deliberately preserved as a platform split, and the shared tests are compile-verified."

extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}
