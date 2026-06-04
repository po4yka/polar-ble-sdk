import XCTest

final class GoldenVectorTestDataTest: XCTestCase {
    func testLoadsSharedGoldenVectorDirectories() throws {
        let vectorRoot = try GoldenVectorTestData.root()
        let vectors = try GoldenVectorTestData.loadObjects(in: "protocol/device-id")
        let emptyDeviceId = try GoldenVectorTestData.loadObject("protocol/device-id/empty-device-id-platform-difference.json")

        XCTAssertTrue(FileManager.default.fileExists(atPath: vectorRoot.appendingPathComponent("schema/golden-vector.schema.json").path))
        XCTAssertFalse(vectors.isEmpty)
        XCTAssertEqual(emptyDeviceId["id"] as? String, "empty-device-id-platform-difference")
    }

    func testMissingSharedGoldenVectorPathFailsFast() {
        XCTAssertThrowsError(try GoldenVectorTestData.loadObject("protocol/device-id/does-not-exist.json"))
    }
}
