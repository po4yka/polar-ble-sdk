// Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

final class PolarPlainDateTest: XCTestCase {

    // MARK: - Helpers

    /// A fixed UTC calendar used throughout to keep tests timezone-independent.
    private var utcCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }

    // MARK: - init(from:calendar:)

    func testInitFromString_validDate_succeeds() {
        let date = PolarPlainDate(from: "2022-03-02", calendar: utcCalendar)
        XCTAssertNotNil(date)
    }

    func testInitFromString_validDate_descriptionRoundtrips() {
        let date = PolarPlainDate(from: "2022-03-02", calendar: utcCalendar)
        XCTAssertEqual("2022-03-02", date?.description)
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("2022,3,2", PolarPlainDateRuntimePlanner.plainDateFieldsCsv(value: "2022-03-02"))
        XCTAssertEqual("2022-03-02", PolarPlainDateRuntimePlanner.formatPlainDate(year: 2022, month: 3, day: 2))
        #endif
    }

    func testInitFromString_leapDay_succeeds() {
        let date = PolarPlainDate(from: "2024-02-29", calendar: utcCalendar)
        XCTAssertNotNil(date)
        XCTAssertEqual("2024-02-29", date?.description)
    }

    func testInitFromString_firstDayOfYear_roundtrips() {
        let date = PolarPlainDate(from: "2023-01-01", calendar: utcCalendar)
        XCTAssertEqual("2023-01-01", date?.description)
    }

    func testInitFromString_lastDayOfYear_roundtrips() {
        let date = PolarPlainDate(from: "2023-12-31", calendar: utcCalendar)
        XCTAssertEqual("2023-12-31", date?.description)
    }

    func testInitFromString_invalidDate_returnsNil() {
        XCTAssertNil(PolarPlainDate(from: "not-a-date", calendar: utcCalendar))
    }

    func testInitFromString_emptyString_returnsNil() {
        XCTAssertNil(PolarPlainDate(from: "", calendar: utcCalendar))
    }

    func testInitFromString_invalidMonth_returnsNil() {
        XCTAssertNil(PolarPlainDate(from: "2022-13-01", calendar: utcCalendar))
    }

    func testInitFromString_invalidDay_returnsNil() {
        XCTAssertNil(PolarPlainDate(from: "2022-03-32", calendar: utcCalendar))
    }

    func testInitFromString_nonLeapDay_returnsNil() {
        // 2023 is not a leap year
        XCTAssertNil(PolarPlainDate(from: "2023-02-29", calendar: utcCalendar))
    }

    func testInitFromString_dateWithTime_returnsNil() {
        // ISO 8601 with time component should not be accepted as a plain date
        XCTAssertNil(PolarPlainDate(from: "2022-03-02T12:00:00Z", calendar: utcCalendar))
    }

    // MARK: - init(date:calendar:)

    func testInitFromDate_producesCorrectDescription() {
        var components = DateComponents()
        components.year = 2022
        components.month = 3
        components.day = 2
        let date = utcCalendar.date(from: components)!

        let polarDate = PolarPlainDate(date: date, calendar: utcCalendar)
        XCTAssertEqual("2022-03-02", polarDate.description)
    }

    func testInitFromDate_epoch_producesCorrectDescription() {
        let epoch = Date(timeIntervalSince1970: 0) // 1970-01-01 UTC
        let polarDate = PolarPlainDate(date: epoch, calendar: utcCalendar)
        XCTAssertEqual("1970-01-01", polarDate.description)
    }

    // MARK: - description

    func testDescription_isoFormat() {
        let date: PolarPlainDate = "2026-04-24"
        XCTAssertEqual("2026-04-24", date.description)
    }

    func testDescription_singleDigitMonthAndDay_zeroPadded() {
        let date = PolarPlainDate(from: "2022-01-05", calendar: utcCalendar)!
        XCTAssertEqual("2022-01-05", date.description)
    }

    // MARK: - ExpressibleByStringLiteral

    func testStringLiteral_producesCorrectDescription() {
        let date: PolarPlainDate = "2025-06-15"
        XCTAssertEqual("2025-06-15", date.description)
    }

    // MARK: - Codable — Encodable

    func testEncode_producesISOString() throws {
        let date = PolarPlainDate(from: "2022-03-02", calendar: utcCalendar)!
        let encoded = try JSONEncoder().encode(date)
        let json = try XCTUnwrap(String(data: encoded, encoding: .utf8))
        // JSON string literal includes surrounding quotes
        XCTAssertEqual("\"2022-03-02\"", json)
    }

    // MARK: - Codable — Decodable

    func testDecode_validISOString_succeeds() throws {
        let json = "\"2022-03-02\"".data(using: .utf8)!
        let date = try JSONDecoder().decode(PolarPlainDate.self, from: json)
        XCTAssertEqual("2022-03-02", date.description)
    }

    func testDecode_leapDay_succeeds() throws {
        let json = "\"2024-02-29\"".data(using: .utf8)!
        let date = try JSONDecoder().decode(PolarPlainDate.self, from: json)
        XCTAssertEqual("2024-02-29", date.description)
    }

    // MARK: - Codable round-trip

    func testRoundTrip_encodeThenDecode_preservesDate() throws {
        let original = PolarPlainDate(from: "2023-11-30", calendar: utcCalendar)!
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(PolarPlainDate.self, from: data)
        XCTAssertEqual(original.description, decoded.description)
    }

    func testRoundTrip_insideStruct_preservesDate() throws {
        struct Wrapper: Codable {
            let day: PolarPlainDate
        }
        let original = Wrapper(day: PolarPlainDate(from: "2023-07-04", calendar: utcCalendar)!)
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(Wrapper.self, from: data)
        XCTAssertEqual(original.day.description, decoded.day.description)
    }

    func testPlainDateGoldenVectorPinsMigrationPolicy() throws {
        let vector = try GoldenVectorTestData.loadObject("sdk/time-date/plain-date-validation.json")
        let input = try objectValue(vector, "input")
        XCTAssertEqual("plain-date-validation", try stringValue(vector, "id"))
        XCTAssertEqual("plainDateValidation", try stringValue(input, "kind"))
        for sample in try objectArray(input, "cases") {
            let value = try stringValue(sample, "value")
            let expectedValid = try boolValue(sample, "valid")
            let date = PolarPlainDate(from: value, calendar: utcCalendar)
            XCTAssertEqual(expectedValid, date != nil, value)
            if expectedValid {
                XCTAssertEqual(value, date?.description)
            }
        }
    }

    func testTimeDateReadinessManifestPinsPlainDateConsumer() throws {
        let vector = try GoldenVectorTestData.loadObject("sdk/time-date/time-date-readiness.json")
        let input = try objectValue(vector, "input")
        let expected = try objectValue(vector, "expected")
        let consumerTests = try objectValue(vector, "consumerTests")
        XCTAssertEqual("time-date-readiness", try stringValue(vector, "id"))
        XCTAssertEqual("timeDateReadiness", try stringValue(input, "kind"))
        XCTAssertEqual(timeDatePolicyVectorPaths, try stringArrayValue(input, "policyVectorPaths"))
        XCTAssertEqual(requiredTimeDateFamilies, try stringArrayValue(input, "requiredBehaviorFamilies"))
        XCTAssertEqual(requiredTimeDateFamilies, try stringArrayValue(expected, "coveredBehaviorFamilies"))
        XCTAssertEqual(timeDateReadinessCommonDecision, try stringValue(expected, "commonDecision"))
        XCTAssertEqual(["com.polar.sdk.api.model.utils.PolarTimeUtilsTest"], try stringArrayValue(consumerTests, "android"))
        XCTAssertEqual(["PolarTimeUtilsTests", "PolarPlainDateTest"], try stringArrayValue(consumerTests, "ios"))
        XCTAssertEqual(["com.polar.sharedtest.TimeDateCommonPolicyTest"], try stringArrayValue(consumerTests, "commonPrototype"))
    }

    private func objectValue(_ object: [String: Any], _ field: String) throws -> [String: Any] {
        try XCTUnwrap(object[field] as? [String: Any])
    }

    private func objectArray(_ object: [String: Any], _ field: String) throws -> [[String: Any]] {
        try XCTUnwrap(object[field] as? [[String: Any]])
    }

    private func stringValue(_ object: [String: Any], _ field: String) throws -> String {
        try XCTUnwrap(object[field] as? String)
    }

    private func boolValue(_ object: [String: Any], _ field: String) throws -> Bool {
        try XCTUnwrap(object[field] as? Bool)
    }

    private func stringArrayValue(_ object: [String: Any], _ field: String) throws -> [String] {
        try XCTUnwrap(object[field] as? [String])
    }

    private var timeDatePolicyVectorPaths: [String] {
        [
            "sdk/time-date/local-date-time-field-mapping.json",
            "sdk/time-date/duration-to-millis.json",
            "sdk/time-date/nanos-to-millis-rounding.json",
            "sdk/time-date/timezone-offset-conversion.json",
            "sdk/time-date/time-string-formatting.json",
            "sdk/time-date/plain-date-validation.json"
        ]
    }

    private var requiredTimeDateFamilies: [String] {
        [
            "local-date-time-field-mapping",
            "trusted-system-time-flag",
            "timezone-offset-minutes",
            "millis-nanos-conversion",
            "nanos-to-millis-rounding",
            "duration-to-millis",
            "time-string-formatting",
            "plain-date-validation",
            "platform-timezone-calendar-boundary",
            "platform-vector-reference-gate",
            "compile-verification-gate"
        ]
    }

    private var timeDateReadinessCommonDecision: String {
        "Time/date migration owns portable field mapping, timezone-offset minute conversion, millisecond/nanosecond policy, duration-to-millis math, time-string formatting, and plain-date validation in shared KMP code while platform calendar, timezone database, Date, Calendar, LocalDateTime, protobuf, and public facade conversion remain platform adapters until shared artifacts are consumed by iOS production code."
    }
}
