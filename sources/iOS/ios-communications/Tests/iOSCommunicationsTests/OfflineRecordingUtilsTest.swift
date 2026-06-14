import XCTest
@testable import iOSCommunications
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

final class OfflineRecordingUtilsTest: XCTestCase {
    
    func testMapOfflineRecordingFileNameToMeasurementType() throws {
        try XCTAssertEqual(
            PmdMeasurementType.acc,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "ACC.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.gyro,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "GYRO.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.mgn,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "MAG.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.ppg,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "PPG.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.ppi,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "PPI.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.offline_hr,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "HR.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.acc,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "ACC0.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.gyro,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "GYRO5.REC")
        )
        try XCTAssertEqual(
            PmdMeasurementType.mgn,
            OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "MAG18.REC")
        )
        try XCTAssertThrowsError(
      OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "INVALID.REC"),
            "Invalid file name"
        )
    }

    func testOfflineRecordingFilenameMappingUsesSharedPlannerWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("ACC", OfflineRecordingRuntimePlanner.measurementTypeName(fileName: "ACC0.REC"))
        XCTAssertEqual("SKIN_TEMP", OfflineRecordingRuntimePlanner.measurementTypeName(fileName: "SKINTEMP.REC"))
        XCTAssertNil(OfflineRecordingRuntimePlanner.measurementTypeName(fileName: "INVALID.REC"))
        XCTAssertEqual(.acc, try OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "ACC0.REC"))
        XCTAssertEqual(.skinTemperature, try OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: "SKINTEMP.REC"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testOfflineRecordingFilenameMappingGoldenVectorsMatchIOSBehavior() throws {
        let vector = try loadOfflineRecordingVector("filename-mapping.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])
        for testCase in cases {
            let fileName = try XCTUnwrap(testCase["fileName"] as? String)
            if testCase["error"] != nil {
                XCTAssertThrowsError(try OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: fileName), fileName)
            } else {
                let measurementType = try XCTUnwrap(testCase["measurementType"] as? [String: Any], fileName)
                XCTAssertEqual(try pmdMeasurementType(named: try XCTUnwrap(measurementType["ios"] as? String, fileName)), try OfflineRecordingUtils.mapOfflineRecordingFileNameToMeasurementType(fileName: fileName), fileName)
            }
        }
    }

    func testOfflineRecordingGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadOfflineRecordingVectors() {
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

    func testOfflineRecordingMetadataReadinessManifestPinsMetadataOwnership() throws {
        let readiness = try loadOfflineRecordingVector("metadata-readiness.json")
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(readiness["id"] as? String, "offline-recording-metadata-readiness")
        XCTAssertEqual(input["kind"] as? String, "offlineRecordingMetadataReadiness")
        XCTAssertEqual(policyVectorPaths, OFFLINE_RECORDING_METADATA_POLICY_VECTOR_PATHS)
        XCTAssertEqual(requiredFamilies, OFFLINE_RECORDING_METADATA_READINESS_FAMILIES)
        XCTAssertEqual(coveredFamilies, OFFLINE_RECORDING_METADATA_READINESS_FAMILIES)
        XCTAssertEqual(expected["commonDecision"] as? String, OFFLINE_RECORDING_METADATA_READINESS_COMMON_DECISION)
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtilityTest", "com.polar.sdk.impl.utils.PolarDataUtilsTest", "com.polar.sdk.api.model.utils.PolarOfflineRecordingUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["OfflineRecordingUtilsTest", "PolarDataUtilsTest", "PolarOfflineRecordingUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.OfflineRecordingMetadataCommonPolicyTest"])
    }

    private func pmdMeasurementType(named name: String) throws -> PmdMeasurementType {
        switch name {
        case "acc": return .acc
        case "gyro": return .gyro
        case "mgn", "magnetometer": return .mgn
        case "ppg": return .ppg
        case "ppi": return .ppi
        case "offline_hr": return .offline_hr
        case "temperature": return .temperature
        case "skinTemperature": return .skinTemperature
        default: throw NSError(domain: "OfflineRecordingUtilsTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unknown measurement type \(name)"])
        }
    }

    private func loadOfflineRecordingVector(_ fileName: String) throws -> [String: Any] {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/offline-recording")
            .appendingPathComponent(fileName)
        let data = try Data(contentsOf: file)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
    }

    private func loadOfflineRecordingVectors() throws -> [[String: Any]] {
        let directory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/offline-recording")
        return try FileManager.default
            .contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

}

private let OFFLINE_RECORDING_METADATA_POLICY_VECTOR_PATHS = [
    "sdk/offline-recording/filename-mapping.json",
    "sdk/offline-recording/pmdfiles-v2-grouping.json",
    "sdk/offline-recording/trigger-mapping.json"
]

private let OFFLINE_RECORDING_METADATA_READINESS_FAMILIES = [
    "filename-to-measurement-type-mapping",
    "split-file-index-stripping",
    "invalid-filename-boundary",
    "pmdfiles-grouping",
    "zero-size-recording-filtering",
    "invalid-entry-filtering",
    "representative-path-platform-policy",
    "trigger-model-projection",
    "disabled-trigger-filtering",
    "platform-offline-recording-vector-reference-gate",
    "compile-verification-gate"
]

private let OFFLINE_RECORDING_METADATA_READINESS_COMMON_DECISION = "Offline recording metadata shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS metadata tests continue to reference the same vectors, filename classification, split-file normalization, invalid filename handling, PMDFILES grouping, zero-size and invalid-entry filtering, representative path policy, trigger model projection, disabled-trigger filtering, and compile verification remain explicit before production metadata mapping moves."
