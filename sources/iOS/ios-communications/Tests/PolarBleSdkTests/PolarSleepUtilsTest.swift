//
//  Copyright © 2024 Polar. All rights reserved.
//

import Foundation
import XCTest
@testable import PolarBleSdk

class PolarSleepUtilsTests: XCTestCase {

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

    func testSleepReadHeadersUseSharedFileFacadePlanning() throws {
        let date = try XCTUnwrap(Self.utcCalendar.date(from: DateComponents(year: 2026, month: 1, day: 2)))

        let sleepOperation = PolarSleepUtils.sleepDataReadOperation(date: date)
        XCTAssertEqual(sleepOperation.command, .get)
        XCTAssertEqual(sleepOperation.path, "/U/0/20260102/SLEEP/SLEEPRES.BPB")

        let skinTemperatureOperation = PolarSleepUtils.sleepSkinTemperatureReadOperation(date: date)
        XCTAssertEqual(skinTemperatureOperation.command, .get)
        XCTAssertEqual(skinTemperatureOperation.path, "/U/0/20260102/NSTRESUL/NSTRCONT.BPB")
    }

    func testReadSleepDataFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        
        let sleepProto = Data_PbSleepAnalysisResult.with {
            $0.alarmTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 7; $0.minute = 0; $0.seconds = 0 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }
            $0.batteryRanOut = false
            $0.createdTimestamp = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 7; $0.minute = 0; $0.seconds = 0 }
                $0.trusted = true
            }
            $0.lastModified = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 4; $0.month = 3; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 4; $0.minute = 3; $0.seconds = 2 }
                $0.trusted = true
            }
            $0.originalSleepRange = PbLocalDateTimeRange.with {
                $0.startTime = PbLocalDateTime.with {
                    $0.date = PbDate.with { $0.day = 27; $0.month = 2; $0.year = 2525 }
                    $0.time = PbTime.with { $0.hour = 23; $0.minute = 59; $0.seconds = 59 }
                    $0.timeZoneOffset = 120
                    $0.obsoleteTrusted = true
                }
                $0.endTime = PbLocalDateTime.with {
                    $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                    $0.time = PbTime.with { $0.hour = 7; $0.minute = 0; $0.seconds = 0 }
                    $0.timeZoneOffset = 120
                    $0.obsoleteTrusted = true
                }
            }
            $0.recordingDevice = PbDeviceId.with { $0.deviceID = "C8D9G10F11H12" }
            $0.sleepCycles = [Data_PbSleepCycle.with {
                $0.secondsFromSleepStart = 2
                $0.sleepDepthStart = 3.0
            }]
            $0.sleepGoalMinutes = 420
            $0.sleepEndOffsetSeconds = 1
            $0.sleepStartOffsetSeconds = 1
            $0.sleepResultDate = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
            $0.sleepStartTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 27; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 23; $0.minute = 45; $0.seconds = 45 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }
            $0.sleepEndTime = PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 7; $0.minute = 5; $0.seconds = 7 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }
            $0.sleepwakePhases = [Data_PbSleepWakePhase.with {
                $0.secondsFromSleepStart = 1
                $0.sleepwakeState = .pbWake
            }]
            $0.snoozeTime = [PbLocalDateTime.with {
                $0.date = PbDate.with { $0.day = 27; $0.month = 2; $0.year = 2525 }
                $0.time = PbTime.with { $0.hour = 23; $0.minute = 59; $0.seconds = 59 }
                $0.timeZoneOffset = 120
                $0.obsoleteTrusted = true
            }]
            $0.userSleepRating = .pbSleptWell
        }
        
        let sleepSkinTemperatureProto = Data_PbSleepSkinTemperatureResult.with {
            $0.sleepDate = PbDateProto3.with { $0.day = 28; $0.month = 2; $0.year = 2525 }
            $0.deviationFromBaselineCelsius = -0.111111
            $0.sleepSkinTemperatureCelsius = 35.123456
        }
        
        mockClient.requestReturnValues.append(.success(try sleepProto.serializedData()))
        mockClient.requestReturnValues.append(.success(try sleepSkinTemperatureProto.serializedData()))

        let mockSleepData = try Self.createPolarSleepAnalysisData()

        // Act
        let sleepData = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: date)

        // Assert
        XCTAssertEqual(sleepData.alarmTime, mockSleepData.alarmTime)
        XCTAssertEqual(sleepData.lastModified, mockSleepData.lastModified)
        XCTAssertEqual(sleepData.sleepStartTime, mockSleepData.sleepStartTime)
        XCTAssertEqual(sleepData.sleepEndTime, mockSleepData.sleepEndTime)
        XCTAssertEqual(sleepData.snoozeTime?.first, mockSleepData.snoozeTime?.first)
        XCTAssertEqual(sleepData.deviceId, mockSleepData.deviceId)
        XCTAssertEqual(sleepData.sleepGoalMinutes, mockSleepData.sleepGoalMinutes)
        XCTAssertEqual(sleepData.sleepResultDate, mockSleepData.sleepResultDate)
        XCTAssertEqual(sleepData.sleepStartOffsetSeconds, mockSleepData.sleepStartOffsetSeconds)
        XCTAssertEqual(sleepData.sleepEndOffsetSeconds, mockSleepData.sleepEndOffsetSeconds)
        XCTAssertEqual(sleepData.userSleepRating, mockSleepData.userSleepRating)
        XCTAssertEqual(sleepData.originalSleepRange?.startTime, mockSleepData.originalSleepRange?.startTime)
        XCTAssertEqual(sleepData.originalSleepRange?.endTime, mockSleepData.originalSleepRange?.endTime)
        XCTAssertEqual(sleepData.sleepCycles.first?.secondsFromSleepStart, mockSleepData.sleepCycles.first?.secondsFromSleepStart)
        XCTAssertEqual(sleepData.sleepCycles.first?.sleepDepthStart, mockSleepData.sleepCycles.first?.sleepDepthStart)
        XCTAssertEqual(sleepData.sleepWakePhases.first?.secondsFromSleepStart, mockSleepData.sleepWakePhases.first?.secondsFromSleepStart)
        XCTAssertEqual(sleepData.sleepWakePhases.first?.state, mockSleepData.sleepWakePhases.first?.state)
    }

    func testSleepRatingProtoMappingUsesSharedKnownValuesAndPreservesUnknownNilPolicy() {
        XCTAssertEqual(.SLEPT_WELL, PolarSleepData.SleepRating.optionalFromProtoValue(value: PbSleepUserRating.pbSleptWell.rawValue))
        XCTAssertEqual(.SLEPT_UNDEFINED, PolarSleepData.SleepRating.getByValue(value: 99))
        XCTAssertNil(PolarSleepData.SleepRating.optionalFromProtoValue(value: 99))
    }

    func testSleepOffsetGoldenVectorsUseSharedLinkedIOSPolicy() async throws {
        let vector = try loadSleepGoldenVector(id: "sleep-offset-platform-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expectedByPlatform = try XCTUnwrap(vector["expected"] as? [String: Any])
        let expected = try XCTUnwrap(expectedByPlatform["ios"] as? [String: Any])
        let sleepProto = Data_PbSleepAnalysisResult.with {
            $0.sleepStartTime = pbLocalDateTime(hour: 22, minute: 0, second: 0, day: 1, month: 1, year: 2024, timeZoneOffset: 120)
            $0.sleepEndTime = pbLocalDateTime(hour: 6, minute: 30, second: 0, day: 2, month: 1, year: 2024, timeZoneOffset: 120)
            $0.lastModified = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 2; $0.month = 1; $0.year = 2024 }
                $0.time = PbTime.with { $0.hour = 6; $0.minute = 31; $0.seconds = 0 }
                $0.trusted = true
            }
            $0.sleepGoalMinutes = 480
            $0.sleepResultDate = PbDate.with { $0.day = 2; $0.month = 1; $0.year = 2024 }
            $0.originalSleepRange = PbLocalDateTimeRange.with {
                $0.startTime = pbLocalDateTime(hour: 22, minute: 0, second: 0, day: 1, month: 1, year: 2024, timeZoneOffset: 120)
                $0.endTime = pbLocalDateTime(hour: 6, minute: 30, second: 0, day: 2, month: 1, year: 2024, timeZoneOffset: 120)
            }
            $0.sleepStartOffsetSeconds = Int32(try! XCTUnwrap(input["sleepStartOffsetSeconds"] as? Int))
            $0.sleepEndOffsetSeconds = Int32(try! XCTUnwrap(input["sleepEndOffsetSeconds"] as? Int))
        }
        mockClient.requestReturnValues.append(.success(try sleepProto.serializedData()))
        mockClient.requestReturnValues.append(.success(try Data_PbSleepSkinTemperatureResult().serializedData()))

        let sleepData = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: Date())

        XCTAssertEqual(sleepData.sleepStartOffsetSeconds, Int32(try XCTUnwrap(expected["sleepStartOffsetSeconds"] as? Int)))
        XCTAssertEqual(sleepData.sleepEndOffsetSeconds, Int32(try XCTUnwrap(expected["sleepEndOffsetSeconds"] as? Int)))
    }

    func testSleepTimezoneGoldenVectorsPreserveIOSInstants() async throws {
        let vector = try loadSleepGoldenVector(id: "sleep-timezone-offsets")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expectedByPlatform = try XCTUnwrap(vector["expected"] as? [String: Any])
        let expected = try XCTUnwrap(expectedByPlatform["ios"] as? [String: Any])
        let sleepProto = Data_PbSleepAnalysisResult.with {
            $0.sleepStartTime = try! pbLocalDateTime(try XCTUnwrap(input["sleepStartTime"] as? [String: Any]))
            $0.sleepEndTime = try! pbLocalDateTime(try XCTUnwrap(input["sleepEndTime"] as? [String: Any]))
            $0.lastModified = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 1; $0.month = 4; $0.year = 2024 }
                $0.time = PbTime.with { $0.hour = 6; $0.minute = 31; $0.seconds = 0 }
                $0.trusted = true
            }
            $0.sleepGoalMinutes = 480
            $0.sleepResultDate = PbDate.with { $0.day = 1; $0.month = 4; $0.year = 2024 }
        }
        mockClient.requestReturnValues.append(.success(try sleepProto.serializedData()))
        mockClient.requestReturnValues.append(.success(try Data_PbSleepSkinTemperatureResult().serializedData()))

        let sleepData = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: Date())

        XCTAssertEqual(iso8601Milliseconds.string(from: try XCTUnwrap(sleepData.sleepStartTime)), try XCTUnwrap(expected["sleepStartUtcInstant"] as? String))
        XCTAssertEqual(iso8601Milliseconds.string(from: try XCTUnwrap(sleepData.sleepEndTime)), try XCTUnwrap(expected["sleepEndUtcInstant"] as? String))
    }

    func testSleepStageHypnogramGoldenVectorsPreserveIOSStageAndCycleOrder() async throws {
        let vector = try loadSleepGoldenVector(id: "sleep-stage-hypnogram")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let sleepProto = Data_PbSleepAnalysisResult.with {
            $0.sleepStartTime = pbLocalDateTime(hour: 22, minute: 0, second: 0, day: 1, month: 1, year: 2024, timeZoneOffset: 120)
            $0.sleepEndTime = pbLocalDateTime(hour: 6, minute: 0, second: 0, day: 2, month: 1, year: 2024, timeZoneOffset: 120)
            $0.lastModified = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 2; $0.month = 1; $0.year = 2024 }
                $0.time = PbTime.with { $0.hour = 6; $0.minute = 1; $0.seconds = 0 }
                $0.trusted = true
            }
            $0.sleepGoalMinutes = 480
            $0.sleepResultDate = PbDate.with { $0.day = 2; $0.month = 1; $0.year = 2024 }
            $0.sleepwakePhases = try! XCTUnwrap(input["sleepWakePhases"] as? [[String: Any]]).map { phase in
                Data_PbSleepWakePhase.with {
                    $0.secondsFromSleepStart = UInt32(try! XCTUnwrap(phase["secondsFromSleepStart"] as? Int))
                    $0.sleepwakeState = sleepWakeState(named: try! XCTUnwrap(phase["protoState"] as? String))
                }
            }
            $0.sleepCycles = try! XCTUnwrap(input["sleepCycles"] as? [[String: Any]]).map { cycle in
                Data_PbSleepCycle.with {
                    $0.secondsFromSleepStart = UInt32(try! XCTUnwrap(cycle["secondsFromSleepStart"] as? Int))
                    $0.sleepDepthStart = Float(try! XCTUnwrap(cycle["sleepDepthStart"] as? Double))
                }
            }
        }
        mockClient.requestReturnValues.append(.success(try sleepProto.serializedData()))
        mockClient.requestReturnValues.append(.success(try Data_PbSleepSkinTemperatureResult().serializedData()))

        let sleepData = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: Date())

        let expectedPhases = try XCTUnwrap(expected["sleepWakePhases"] as? [[String: Any]])
        XCTAssertEqual(sleepData.sleepWakePhases.count, expectedPhases.count)
        for (index, phase) in expectedPhases.enumerated() {
            XCTAssertEqual(sleepData.sleepWakePhases[index].secondsFromSleepStart, UInt32(try XCTUnwrap(phase["secondsFromSleepStart"] as? Int)))
            XCTAssertEqual(sleepData.sleepWakePhases[index].state.rawValue, try XCTUnwrap(phase["state"] as? String))
        }
        let expectedCycles = try XCTUnwrap(expected["sleepCycles"] as? [[String: Any]])
        XCTAssertEqual(sleepData.sleepCycles.count, expectedCycles.count)
        for (index, cycle) in expectedCycles.enumerated() {
            XCTAssertEqual(sleepData.sleepCycles[index].secondsFromSleepStart, UInt32(try XCTUnwrap(cycle["secondsFromSleepStart"] as? Int)))
            XCTAssertEqual(sleepData.sleepCycles[index].sleepDepthStart, Float(try XCTUnwrap(cycle["sleepDepthStart"] as? Double)), accuracy: 0.0001)
        }
    }

    func testPartialNightGoldenVectorsUseSharedBackedOmittedOptionalPolicy() async throws {
        let vector = try loadSleepGoldenVector(id: "partial-night-omitted-optionals")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expectedByScope = try XCTUnwrap(vector["expected"] as? [String: Any])
        let commonExpected = try XCTUnwrap(expectedByScope["common"] as? [String: Any])
        let sleepResultDate = try XCTUnwrap(input["sleepResultDate"] as? [String: Any])
        let sleepProto = Data_PbSleepAnalysisResult.with {
            $0.sleepStartTime = pbLocalDateTime(hour: 23, minute: 0, second: 0, day: 5, month: 5, year: 2024, timeZoneOffset: 180)
            $0.sleepEndTime = pbLocalDateTime(hour: 4, minute: 15, second: 0, day: 6, month: 5, year: 2024, timeZoneOffset: 180)
            $0.lastModified = PbSystemDateTime.with {
                $0.date = PbDate.with { $0.day = 6; $0.month = 5; $0.year = 2024 }
                $0.time = PbTime.with { $0.hour = 4; $0.minute = 16; $0.seconds = 0 }
                $0.trusted = true
            }
            $0.sleepGoalMinutes = UInt32(try! XCTUnwrap(input["sleepGoalMinutes"] as? Int))
            $0.sleepResultDate = PbDate.with {
                $0.day = UInt32(try! XCTUnwrap(sleepResultDate["day"] as? Int))
                $0.month = UInt32(try! XCTUnwrap(sleepResultDate["month"] as? Int))
                $0.year = UInt32(try! XCTUnwrap(sleepResultDate["year"] as? Int))
            }
        }
        mockClient.requestReturnValues.append(.success(try sleepProto.serializedData()))
        mockClient.requestReturnValues.append(.success(try Data_PbSleepSkinTemperatureResult().serializedData()))

        let sleepData = try await PolarSleepUtils.readSleepFromDayDirectory(client: mockClient, date: Date())

        XCTAssertEqual(sleepData.sleepGoalMinutes, UInt32(try XCTUnwrap(commonExpected["sleepGoalMinutes"] as? Int)))
        XCTAssertEqual(sleepData.sleepWakePhases.count, try XCTUnwrap(commonExpected["sleepWakePhaseCount"] as? Int))
        XCTAssertEqual(try XCTUnwrap(sleepData.snoozeTime).count, try XCTUnwrap(commonExpected["snoozeTimeCount"] as? Int))
        XCTAssertNil(sleepData.alarmTime)
        XCTAssertEqual(sleepData.sleepStartOffsetSeconds, Int32(try XCTUnwrap(commonExpected["sleepStartOffsetSeconds"] as? Int)))
        XCTAssertEqual(sleepData.sleepEndOffsetSeconds, Int32(try XCTUnwrap(commonExpected["sleepEndOffsetSeconds"] as? Int)))
        XCTAssertNil(sleepData.userSleepRating)
        XCTAssertEqual(sleepData.sleepCycles.count, try XCTUnwrap(commonExpected["sleepCycleCount"] as? Int))
        XCTAssertEqual(sleepData.sleepResultDate?.year, 2024)
        XCTAssertEqual(sleepData.sleepResultDate?.month, 5)
        XCTAssertEqual(sleepData.sleepResultDate?.day, 6)
        XCTAssertNil(sleepData.sleepSkinTemperatureResult)
        XCTAssertNil(sleepData.deviceId)
        XCTAssertNil(sleepData.batteryRanOut)
        XCTAssertNil(sleepData.originalSleepRange)
    }

    func testSleepGoldenVectorsFollowNeutralKmpShape() throws {
        for vectorId in ["partial-night-omitted-optionals", "sleep-offset-platform-policy", "sleep-stage-hypnogram", "sleep-timezone-offsets"] {
            let vector = try loadSleepGoldenVector(id: vectorId)
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

    // MARK: - Helpers

    private static func createPolarSleepAnalysisData() throws -> PolarSleepData.PolarSleepAnalysisResult {
        return PolarSleepData.PolarSleepAnalysisResult(
            sleepStartTime: try createDate(hour: 23, minute: 45, second: 45, day: 27, month: 2, year: 2525),
            sleepEndTime: try createDate(hour: 7, minute: 5, second: 7, day: 28, month: 2, year: 2525),
            lastModified: try createDateInCurrentTimeZone(hour: 4, minute: 3, second: 2, day: 4, month: 3, year: 2525),
            sleepGoalMinutes: 420,
            sleepWakePhases: [PolarSleepData.SleepWakePhase(secondsFromSleepStart: 1, state: .WAKE)],
            snoozeTime: [try createDate(hour: 23, minute: 59, second: 59, day: 27, month: 2, year: 2525)!],
            alarmTime: try createDate(hour: 7, minute: 0, second: 0, day: 28, month: 2, year: 2525),
            sleepStartOffsetSeconds: 1,
            sleepEndOffsetSeconds: 1,
            userSleepRating: PolarSleepData.SleepRating.SLEPT_WELL,
            deviceId: "C8D9G10F11H12",
            batteryRanOut: false,
            sleepCycles: [PolarSleepData.SleepCycle(secondsFromSleepStart: 2, sleepDepthStart: Float(3.0))],
            sleepResultDate: DateComponents(year: 2525, month: 2, day: 28),
            originalSleepRange: PolarSleepData.OriginalSleepRange(
                startTime: try createDate(hour: 23, minute: 59, second: 59, day: 27, month: 2, year: 2525),
                endTime: try createDate(hour: 7, minute: 0, second: 0, day: 28, month: 2, year: 2525)
            ),
            sleepSkinTemperatureResult: PolarSleepData.SleepSkinTemperatureResult(
                sleepResultDate: try createDate(hour: nil, minute: nil, second: nil, day: 28, month: 2, year: 2525)!,
                sleepSkinTemperatureCelsius: 35.123456,
                deviationFromBaseLine: -0.111111
            )
        )
    }

    private static func createDate(hour: Int?, minute: Int?, second: Int?, day: Int, month: Int, year: Int) throws -> Date! {
        var dateComponents = DateComponents()
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = day
        dateComponents.hour = hour
        dateComponents.minute = minute
        dateComponents.second = second
        dateComponents.timeZone = TimeZone(secondsFromGMT: 2 * 60 * 60)!
        let userCalendar = Calendar(identifier: .iso8601)
        return userCalendar.date(from: dateComponents)!
    }

    private static func createDateInCurrentTimeZone(hour: Int?, minute: Int?, second: Int?, day: Int, month: Int, year: Int) throws -> Date! {
        var dateComponents = DateComponents()
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = day
        dateComponents.hour = hour
        dateComponents.minute = minute
        dateComponents.second = second
        let userCalendar = Calendar(identifier: .gregorian)
        return userCalendar.date(from: dateComponents)!
    }

    private func loadSleepGoldenVector(id: String) throws -> [String: Any] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/sleep")
        let files = try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        for file in files {
            let data = try Data(contentsOf: file)
            let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            if vector["id"] as? String == id {
                return vector
            }
        }
        throw NSError(domain: "PolarSleepUtilsTests", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not find sleep vector \(id)"])
    }


    private func pbLocalDateTime(hour: Int32, minute: Int32, second: Int32, day: Int32, month: Int32, year: Int32, timeZoneOffset: Int32) -> PbLocalDateTime {
        PbLocalDateTime.with {
            $0.date = PbDate.with { date in date.day = UInt32(day); date.month = UInt32(month); date.year = UInt32(year) }
            $0.time = PbTime.with { time in time.hour = UInt32(hour); time.minute = UInt32(minute); time.seconds = UInt32(second) }
            $0.timeZoneOffset = timeZoneOffset
            $0.obsoleteTrusted = true
        }
    }

    private func pbLocalDateTime(_ json: [String: Any]) throws -> PbLocalDateTime {
        PbLocalDateTime.with {
            $0.date = PbDate.with { date in
                date.day = UInt32(try! XCTUnwrap(json["day"] as? Int))
                date.month = UInt32(try! XCTUnwrap(json["month"] as? Int))
                date.year = UInt32(try! XCTUnwrap(json["year"] as? Int))
            }
            $0.time = PbTime.with { time in
                time.hour = UInt32(try! XCTUnwrap(json["hour"] as? Int))
                time.minute = UInt32(try! XCTUnwrap(json["minute"] as? Int))
                time.seconds = UInt32(try! XCTUnwrap(json["second"] as? Int))
                time.millis = UInt32(try! XCTUnwrap(json["millis"] as? Int))
            }
            $0.timeZoneOffset = Int32(try! XCTUnwrap(json["timeZoneOffsetMinutes"] as? Int))
            $0.obsoleteTrusted = true
        }
    }

    private var iso8601Milliseconds: ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter
    }

    private func sleepWakeState(named name: String) -> Data_PbSleepWakeState {
        switch name {
        case "PB_WAKE": return .pbWake
        case "PB_REM": return .pbRem
        case "PB_NONREM12": return .pbNonrem12
        case "PB_NONREM3": return .pbNonrem3
        default: return .pbUnknown
        }
    }
}
