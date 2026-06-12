//  Copyright © 2022 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import PolarBleSdk

class PolarTimeUtilsTests: XCTestCase {
    func testProductionTimeUtilityUsesSharedKmpPolicyWhenLinked() throws {
        XCTAssertEqual(999, PolarTimeUtils.nanosToMillis(nanoseconds: 999_000_000))
        XCTAssertEqual(90_061_002, PolarTimeUtils.pbDurationToMillis(pbDuration: PbDuration.with {
            $0.hours = 25
            $0.minutes = 1
            $0.seconds = 1
            $0.millis = 2
        }))
        XCTAssertEqual("01:02:03.04", PolarTimeUtils.pbTimeToTimeString(PbTime.with {
            $0.hour = 1
            $0.minute = 2
            $0.seconds = 3
            $0.millis = 4
        }))
        XCTAssertNoThrow(try PolarTimeUtils.pbDateToDate(pbDate: PbDate.with {
            $0.year = 2024
            $0.month = 2
            $0.day = 29
        }))
    }

    func testBasicDateRangeUsesSharedInclusivePolicyForIosFacadeLoops() throws {
        let range = PolarTimeUtils.basicDateRange(
            fromDate: try makeDate(year: 2024, month: 2, day: 28, hour: 10, minute: 15),
            toDate: try makeDate(year: 2024, month: 3, day: 1, hour: 10, minute: 15)
        )
        XCTAssertEqual(["20240228 10:15:00", "20240229 10:15:00", "20240301 10:15:00"], range.map(formatBasicDateTime))
        XCTAssertEqual([], PolarTimeUtils.basicDateRange(
            fromDate: try makeDate(year: 2024, month: 3, day: 2, hour: 10, minute: 15),
            toDate: try makeDate(year: 2024, month: 3, day: 1, hour: 10, minute: 15)
        ))
        let clippedRange = PolarTimeUtils.basicDateRange(
            fromDate: try makeDate(year: 2024, month: 2, day: 28, hour: 10, minute: 15),
            toDate: try makeDate(year: 2024, month: 2, day: 29, hour: 9, minute: 15)
        )
        XCTAssertEqual(["20240228 10:15:00"], clippedRange.map(formatBasicDateTime))
        XCTAssertEqual(["20240228", "20240229", "20240301"], PolarTimeUtils.basicDateRangeStrings(
            fromDate: try makeDate(year: 2024, month: 2, day: 28, hour: 10, minute: 15),
            toDate: try makeDate(year: 2024, month: 3, day: 1, hour: 10, minute: 15)
        ))
    }

    func testConversionToPftpSystemTimeFromTimeZoneGMT() throws {
        // Arrange
        let localTime = "2022-01-01T11:59:01.999+00:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
        
    }
    
    func testConversionToPftpSystemTimeFromTimeZoneGMT1() throws {
        // Arrange
        let localTime = "2022-01-01T11:59:01.999+01:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testConversionToPftpSystemTimeFromTimeZoneGmtMinus1() throws {
        // Arrange
        let localTime = "2022-01-01T11:59:01.999-01:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testConversionToPftpSystemTimeWhenLocalTimeIsTomorrow() throws {
        // Arrange
        let localTime = "2022-01-01T01:59:01.099+03:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testConversionToPftpSystemTimeWhenLocalTimeIsLeapYear() throws {
        // Arrange
        let localTime = "2024-02-29T22:59:01.001+01:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    
    func testConversionToPftpSystemTimeWhenLocalTimeIsYesterday() throws {
        // Arrange
        let localTime = "2021-12-31T22:59:01.999-03:00"
        let utcDateComponents = try getDateComponentsInUTC(localTime)
        let expectedMillis = PolarTimeUtils.nanosToMillis(nanoseconds: utcDateComponents.nanosecond!)
        let date = try dateFromISO8601(localTime)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetSystemTime(time: date)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.trusted)
        XCTAssertEqual(UInt32(utcDateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(utcDateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(utcDateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(utcDateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(utcDateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(utcDateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(expectedMillis), result.time.millis)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMT() throws {
        // Arrange
        let timeZone = TimeZone.init(secondsFromGMT: 0)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 1
        dateComponents.day = 1
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        dateComponents.nanosecond = 0
        dateComponents.timeZone = timeZone
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(dateComponents.nanosecond!), result.time.millis)
        
        let timeZoneInMinutes = ((timeZone!.secondsFromGMT() % 3600) / 60)
        XCTAssertEqual(Int32(timeZoneInMinutes), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMT1() throws {
        // Arrange
        let minutesFromGMT = 1 * 60 //GMT+1hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        dateComponents.nanosecond = 0
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(dateComponents.nanosecond!), result.time.millis)
        
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMTMinus1() throws {
        // Arrange
        let minutesFromGMT = -1 * 60 //GMT-1hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        let milliSeconds = 100 // 100ms
        let nanoSeconds = milliSeconds * 1000 * 1000
        dateComponents.nanosecond = nanoSeconds
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(milliSeconds), result.time.millis)
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMT14() throws {
        
        // Arrange
        let minutesFromGMT = 14 * 60 //GMT+14hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        let milliSeconds = 999 // 999ms
        let nanoSeconds = milliSeconds * 1000 * 1000
        dateComponents.nanosecond = nanoSeconds
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(milliSeconds), result.time.millis)
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    func testDateAndTimeZoneConversionToPftpLocalTime_timeZoneGMTMinus12() throws {
        
        // Arrange
        let minutesFromGMT = -12 * 60 //GMT-12hour
        let secondsFromGMT = minutesFromGMT * 60
        let timeZone = TimeZone.init(secondsFromGMT: secondsFromGMT)
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        let milliSeconds = 999 // 999ms
        let nanoSeconds = milliSeconds * 1000 * 1000
        dateComponents.nanosecond = nanoSeconds
        dateComponents.timeZone = timeZone
        
        let date = Calendar(identifier: .gregorian).date(from: dateComponents)
        
        // Act
        let result = PolarTimeUtils.dateToPbPftpSetLocalTime(time: date!, zone: timeZone!)
        
        // Assert
        guard let result = result else {
            XCTAssert(false, "result is nil")
            return
        }
        XCTAssertTrue(result.hasDate)
        XCTAssertTrue(result.hasTime)
        XCTAssertTrue(result.hasTzOffset)
        XCTAssertEqual(UInt32(dateComponents.year!), result.date.year)
        XCTAssertEqual(UInt32(dateComponents.month!), result.date.month)
        XCTAssertEqual(UInt32(dateComponents.day!), result.date.day)
        XCTAssertEqual(UInt32(dateComponents.hour!), result.time.hour)
        XCTAssertEqual(UInt32(dateComponents.minute!), result.time.minute)
        XCTAssertEqual(UInt32(dateComponents.second!), result.time.seconds)
        XCTAssertEqual(UInt32(milliSeconds), result.time.millis)
        XCTAssertEqual(Int32(minutesFromGMT), result.tzOffset)
    }
    
    func testDateConversionToPftpLocalTime_noTimeZone() throws {

        // Arrange
        var dateComponents = DateComponents()
        dateComponents.year = 2022
        dateComponents.month = 12
        dateComponents.day = 30
        dateComponents.hour = 11
        dateComponents.minute = 59
        dateComponents.second = 1
        let milliSeconds = 999 // 999ms
        let nanoSeconds = milliSeconds * 1000 * 1000
        dateComponents.nanosecond = nanoSeconds

        // Act
        do {
            let pbPFtpSetLocalTimeParams = Protocol_PbPFtpSetLocalTimeParams.with {
                $0.date = PbDate.with {
                    $0.year = UInt32(dateComponents.year!)
                    $0.month = UInt32(dateComponents.month!)
                    $0.day = UInt32(dateComponents.day!)
                }
                $0.time = PbTime.with {
                    $0.hour = UInt32(dateComponents.hour!)
                    $0.minute = UInt32(dateComponents.minute!)
                    $0.seconds = UInt32(dateComponents.second!)
                    $0.millis = UInt32(milliSeconds)
                }
            }

            let result = try PolarTimeUtils.dateFromPbPftpLocalDateTime(pbPFtpSetLocalTimeParams)

            // Assert
            let calendar = Calendar.current
            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second, .nanosecond], from: result)

            XCTAssertEqual(components.year, dateComponents.year)
            XCTAssertEqual(components.month, dateComponents.month)
            XCTAssertEqual(components.day, dateComponents.day)
            XCTAssertEqual(components.hour, dateComponents.hour)
            XCTAssertEqual(components.minute, dateComponents.minute)
            XCTAssertEqual(components.second, dateComponents.second)
        } catch {
            XCTFail("Error: \(error)")
        }
    }
    
    func testPbLocalDateTimeConversionToDate() throws {
        
        var pbLocalDateTime = PbLocalDateTime()
        pbLocalDateTime.date.year = 2525
        pbLocalDateTime.date.month = 1
        pbLocalDateTime.date.day = 2
        pbLocalDateTime.time.hour = 3
        pbLocalDateTime.time.minute = 4
        pbLocalDateTime.time.seconds = 5
        pbLocalDateTime.time.millis = 6
        pbLocalDateTime.timeZoneOffset = 2 // Two minutes --> expect value 2 in assertion
    
        let result = try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: pbLocalDateTime)
        
        // Assert
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: result)
        
        XCTAssertEqual(components.year, 2525)
        XCTAssertEqual(components.month, 1)
        XCTAssertEqual(components.day, 2)
        XCTAssertEqual(components.hour, 3)
        XCTAssertEqual(components.minute, 2)
        XCTAssertEqual(components.second, 5)
    }

    func testpbSystemDateTimeConversionToDate () throws {
        
        var pbSystemDateTime = PbSystemDateTime()
        pbSystemDateTime.date.year = 2525
        pbSystemDateTime.date.month = 1
        pbSystemDateTime.date.day = 2
        pbSystemDateTime.time.hour = 3
        pbSystemDateTime.time.minute = 4
        pbSystemDateTime.time.seconds = 5
        pbSystemDateTime.time.millis = 6
    
        let result = try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: pbSystemDateTime)
        
        // Assert
        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second, .nanosecond], from: result)
        
        XCTAssertEqual(components.year, 2525)
        XCTAssertEqual(components.month, 1)
        XCTAssertEqual(components.day, 2)
        XCTAssertEqual(components.hour, 3)
        XCTAssertEqual(components.minute,4)
        XCTAssertEqual(components.second, 5)
    }
    
    func testpbDateConversionToDate() throws {

        var pbDate = PbDate()
        pbDate.year = 2525
        pbDate.month = 1
        pbDate.day = 2

        let result = try PolarTimeUtils.pbDateToDate(pbDate: pbDate)

        // Assert
        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month, .day], from: result)

        XCTAssertEqual(components.year, 2525)
        XCTAssertEqual(components.month, 1)
        XCTAssertEqual(components.day, 2)
    }

    func testPbDateToDateUTC_returnsMidnightUTC() throws {
        // Arrange
        var pbDate = PbDate()
        pbDate.year = 2525
        pbDate.month = 1
        pbDate.day = 2

        // Act
        let result = try PolarTimeUtils.pbDateToDateUTC(pbDate: pbDate)

        // Assert
        var utcCalendar = Calendar(identifier: .gregorian)
        utcCalendar.timeZone = TimeZone(identifier: "UTC")!
        let components = utcCalendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: result)

        XCTAssertEqual(components.year, 2525)
        XCTAssertEqual(components.month, 1)
        XCTAssertEqual(components.day, 2)
        XCTAssertEqual(components.hour, 0)
        XCTAssertEqual(components.minute, 0)
        XCTAssertEqual(components.second, 0)
    }

    func testPbDurationToMillis () throws {

        // Arrange
        var pbDuration = PbDuration()
        pbDuration.hours = 23
        pbDuration.minutes = 59
        pbDuration.seconds = 59
        pbDuration.millis = 999

        //Act
        let result = PolarTimeUtils.pbDurationToMillis(pbDuration: pbDuration)

        // Assert
        XCTAssertEqual(result, 23*60*60*1000 + 59*60*1000 + 59*1000 + 999)
    }

    func testTimeDateGoldenVectorsPinIOSUtilityMigration() throws {
        for relativePath in timeDateUtilityVectorPaths {
            let vector = try GoldenVectorTestData.loadObject(relativePath)
            let input = try objectValue(vector, "input")
            let expected = try objectValue(vector, "expected")
            switch try stringValue(input, "kind") {
            case "dateTimeFields":
                XCTAssertEqual(try intValue(expected, "nanos"), PolarTimeUtils.nanosToMillis(nanoseconds: try intValue(input, "millis") * 1_000_000) * 1_000_000)
                XCTAssertEqual(try boolValue(expected, "trusted"), try boolValue(input, "trusted"))
            case "durationToMillis":
                var pbDuration = PbDuration()
                pbDuration.hours = UInt32(try intValue(input, "hours"))
                pbDuration.minutes = UInt32(try intValue(input, "minutes"))
                pbDuration.seconds = UInt32(try intValue(input, "seconds"))
                pbDuration.millis = UInt32(try intValue(input, "millis"))
                XCTAssertEqual(try intValue(expected, "millis"), PolarTimeUtils.pbDurationToMillis(pbDuration: pbDuration))
            case "nanosToMillis":
                for sample in try objectArray(input, "cases") {
                    XCTAssertEqual(try intValue(sample, "expectedMillis"), PolarTimeUtils.nanosToMillis(nanoseconds: try intValue(sample, "nanoseconds")))
                }
            case "timezoneOffset":
                for sample in try objectArray(input, "cases") {
                    let seconds = try intValue(sample, "seconds")
                    let timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: seconds))
                    var components = DateComponents()
                    components.year = 2024
                    components.month = 2
                    components.day = 29
                    components.hour = 23
                    components.minute = 59
                    components.second = 58
                    components.nanosecond = 999_000_000
                    components.timeZone = timeZone
                    let date = try XCTUnwrap(Calendar(identifier: .gregorian).date(from: components))
                    let result = try XCTUnwrap(PolarTimeUtils.dateToPbPftpSetLocalTime(time: date, zone: timeZone))
                    XCTAssertEqual(Int32(try intValue(sample, "expectedMinutes")), result.tzOffset)
                }
            case "timeString":
                for sample in try objectArray(input, "cases") {
                    var pbTime = PbTime()
                    pbTime.hour = UInt32(try intValue(sample, "hour"))
                    pbTime.minute = UInt32(try intValue(sample, "minute"))
                    pbTime.seconds = UInt32(try intValue(sample, "second"))
                    pbTime.millis = UInt32(try intValue(sample, "millis"))
                    XCTAssertEqual(try stringValue(sample, "expected"), PolarTimeUtils.pbTimeToTimeString(pbTime))
                }
            default:
                XCTFail("Unsupported time/date vector kind")
            }
        }
    }

    func testTimeDateReadinessManifestIsPinnedBeforeUtilityMigration() throws {
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

    private func getDateComponentsInUTC(_ iso8061: String) throws -> DateComponents {
        var calendar = Calendar(identifier: Calendar.Identifier.iso8601)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar.dateComponents([.day, .month, .year, .hour, .minute, .second, .nanosecond], from: try dateFromISO8601(iso8061))
    }
    
    private func dateFromISO8601(_ iso8061: String) throws -> Date  {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds, .withTimeZone]
        guard let date = dateFormatter.date(from: iso8061) else {
            throw TestError.dateConversionFromISO8601Error
        }
        return date
    }

    private func makeDate(year: Int, month: Int, day: Int, hour: Int, minute: Int) throws -> Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = 0
        components.nanosecond = 0
        return try XCTUnwrap(Calendar.current.date(from: components))
    }

    private func formatBasicDateTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar.current
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyyMMdd HH:mm:ss"
        return formatter.string(from: date)
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

    private func intValue(_ object: [String: Any], _ field: String) throws -> Int {
        try XCTUnwrap(object[field] as? Int)
    }

    private func boolValue(_ object: [String: Any], _ field: String) throws -> Bool {
        try XCTUnwrap(object[field] as? Bool)
    }

    private func stringArrayValue(_ object: [String: Any], _ field: String) throws -> [String] {
        try XCTUnwrap(object[field] as? [String])
    }

    private var timeDateUtilityVectorPaths: [String] {
        Array(timeDatePolicyVectorPaths.dropLast())
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

enum TestError: Error {
    case dateConversionFromISO8601Error
}
