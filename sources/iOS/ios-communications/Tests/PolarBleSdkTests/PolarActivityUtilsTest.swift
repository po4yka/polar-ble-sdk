//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

class PolarActivityUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testActivityReadHeadersUseSharedFileFacadePlanning() throws {
        let date = try XCTUnwrap(DateComponents(calendar: Calendar(identifier: .gregorian), year: 2026, month: 1, day: 2).date)

        let activityDirectoryOperation = PolarActivityUtils.activityDirectoryReadOperation(date: date)
        XCTAssertEqual(activityDirectoryOperation.command, .get)
        XCTAssertEqual(activityDirectoryOperation.path, "/U/0/20260102/ACT/")

        let activitySampleOperation = PolarActivityUtils.activitySampleFileReadOperation(path: "/U/0/20260102/ACT/ASAMPL0.BPB")
        XCTAssertEqual(activitySampleOperation.command, .get)
        XCTAssertEqual(activitySampleOperation.path, "/U/0/20260102/ACT/ASAMPL0.BPB")

        let dailySummaryOperation = PolarActivityUtils.dailySummaryReadOperation(date: date)
        XCTAssertEqual(dailySummaryOperation.command, .get)
        XCTAssertEqual(dailySummaryOperation.path, "/U/0/20260102/DSUM/DSUM.BPB")
    }

    func testReadStepsFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "ASAMPL.BPB"; $0.size = 123 }]
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))
        
        let date = Date()
        var proto = Data_PbActivitySamples()
        proto.stepsSamples = [10000, 5000, 8000]
        mockClient.requestReturnValues.append(.success(try proto.serializedData()))
        
        // Act
        let steps = try await PolarActivityUtils.readStepsFromDayDirectory(client: mockClient, date: date)
        
        // Assert
        XCTAssertEqual(steps, 23000)
    }

    func testReadStepsFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = []
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))
        mockClient.requestReturnValues.append(.failure(NSError(domain: "File not found", code: 103, userInfo: nil)))
        
        // Act
        let steps = try await PolarActivityUtils.readStepsFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertEqual(steps, 0)
    }

    func testReadDistanceFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        proto.activityDistance = 1234.56
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let distance = try await PolarActivityUtils.readDistanceFromDayDirectory(client: mockClient, date: date)
        
        // Assert
        XCTAssertEqual(distance, Float(1234.56))
    }

    func testReadDistanceFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act
        let distance = try await PolarActivityUtils.readDistanceFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertEqual(distance, 0)
    }
    
    func testReadActiveTimeFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        var activityClassTimes = Data_PbActivityClassTimes()
        activityClassTimes.timeNonWear = PbDuration.with { $0.hours = 1; $0.seconds = 30; $0.millis = 500 }
        activityClassTimes.timeSleep = PbDuration.with { $0.hours = 7; $0.minutes = 45; $0.seconds = 30; $0.millis = 200 }
        activityClassTimes.timeSedentary = PbDuration.with { $0.hours = 3; $0.minutes = 15; $0.seconds = 20 }
        activityClassTimes.timeLightActivity = PbDuration.with { $0.hours = 2; $0.seconds = 45 }
        activityClassTimes.timeContinuousModerate = PbDuration.with { $0.hours = 1; $0.minutes = 45; $0.seconds = 10; $0.millis = 100 }
        activityClassTimes.timeIntermittentModerate = PbDuration.with { $0.hours = 1; $0.minutes = 15; $0.seconds = 5 }
        activityClassTimes.timeContinuousVigorous = PbDuration.with { $0.minutes = 45; $0.seconds = 30 }
        activityClassTimes.timeIntermittentVigorous = PbDuration.with { $0.minutes = 30; $0.seconds = 15; $0.millis = 50 }
        proto.activityClassTimes = activityClassTimes
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let activeTimeData = try await PolarActivityUtils.readActiveTimeFromDayDirectory(client: mockClient, date: date)
        
        // Assert
        XCTAssertEqual(activeTimeData.timeNonWear.hours, 1)
        XCTAssertEqual(activeTimeData.timeNonWear.minutes, 0)
        XCTAssertEqual(activeTimeData.timeNonWear.seconds, 30)
        XCTAssertEqual(activeTimeData.timeNonWear.millis, 500)
        XCTAssertEqual(activeTimeData.timeSleep.hours, 7)
        XCTAssertEqual(activeTimeData.timeSleep.minutes, 45)
        XCTAssertEqual(activeTimeData.timeSleep.seconds, 30)
        XCTAssertEqual(activeTimeData.timeSleep.millis, 200)
        XCTAssertEqual(activeTimeData.timeSedentary.hours, 3)
        XCTAssertEqual(activeTimeData.timeSedentary.minutes, 15)
        XCTAssertEqual(activeTimeData.timeSedentary.seconds, 20)
        XCTAssertEqual(activeTimeData.timeSedentary.millis, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.hours, 2)
        XCTAssertEqual(activeTimeData.timeLightActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.seconds, 45)
        XCTAssertEqual(activeTimeData.timeLightActivity.millis, 0)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.hours, 1)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.minutes, 45)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.seconds, 10)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.millis, 100)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.hours, 1)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.minutes, 15)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.seconds, 5)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.millis, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.minutes, 45)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.seconds, 30)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.millis, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.minutes, 30)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.seconds, 15)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.millis, 50)
    }

    func testReadActiveTimeFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act
        let activeTimeData = try await PolarActivityUtils.readActiveTimeFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertEqual(activeTimeData.timeNonWear.hours, 0)
        XCTAssertEqual(activeTimeData.timeNonWear.minutes, 0)
        XCTAssertEqual(activeTimeData.timeSleep.hours, 0)
        XCTAssertEqual(activeTimeData.timeSleep.minutes, 0)
        XCTAssertEqual(activeTimeData.timeSedentary.hours, 0)
        XCTAssertEqual(activeTimeData.timeSedentary.minutes, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeLightActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeContinuousModerateActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentModerateActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeContinuousVigorousActivity.minutes, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.hours, 0)
        XCTAssertEqual(activeTimeData.timeIntermittentVigorousActivity.minutes, 0)
    }

    func testReadCaloriesFromDayDirectory_ActivityCalories() async throws {
        // Arrange
        var proto = Data_PbDailySummary()
        proto.activityCalories = 2000
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .activity)
        
        // Assert
        XCTAssertEqual(calories, 2000)
    }
    
    func testReadCaloriesFromDayDirectory_TrainingCalories() async throws {
        // Arrange
        var proto = Data_PbDailySummary()
        proto.trainingCalories = 1500
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .training)
        
        // Assert
        XCTAssertEqual(calories, 1500)
    }

    func testReadCaloriesFromDayDirectory_BmrCalories() async throws {
        // Arrange
        var proto = Data_PbDailySummary()
        proto.bmrCalories = 1200
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .bmr)
        
        // Assert
        XCTAssertEqual(calories, 1200)
    }
    
    func testReadCaloriesFromDayDirectory_FileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act
        let calories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: mockClient, date: Date(), caloriesType: .activity)
        
        // Assert
        XCTAssertEqual(calories, 0)
    }

    func testReadActivitySamplesDataFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = [Protocol_PbPFtpEntry.with { $0.name = "ASAMPL.BPB"; $0.size = 123 }]
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))

        let date = Date()
        var proto = Data_PbActivitySamples()
        proto.stepsSamples = [10000, 5000, 8000]
        proto.metSamples = [10000, 5000, 8000]
        proto.metRecordingInterval = PbDuration.with { $0.seconds = 30 }
        proto.stepsRecordingInterval = PbDuration.with { $0.seconds = 60 }
        proto.startTime = PbLocalDateTime.with {
            $0.date = PbDate.with { $0.day = 1; $0.month = 2; $0.year = 2525 }
            $0.time = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 4 }
            $0.timeZoneOffset = 5
            $0.obsoleteTrusted = true
        }
        var activityInfo = Data_PbActivityInfo()
        activityInfo.factor = 100
        activityInfo.value = .continuousModerate
        activityInfo.timeStamp = PbLocalDateTime.with {
            $0.date = PbDate.with { $0.day = 1; $0.month = 2; $0.year = 2525 }
            $0.time = PbTime.with { $0.hour = 1; $0.minute = 2; $0.seconds = 3; $0.millis = 0 }
            $0.timeZoneOffset = 0
            $0.obsoleteTrusted = true
        }
        proto.activityInfo.append(activityInfo)
        mockClient.requestReturnValues.append(.success(try proto.serializedData()))

        // Act
        let samplesData = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: mockClient, date: date)

        // Assert
        XCTAssertEqual(samplesData.polarActivityDataList.count, 1)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metRecordingInterval, 30)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples.count, 3)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples[0], 10000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples[1], 5000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.metSamples[2], 8000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepRecordingInterval, 60)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples.count, 3)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples[0], 10000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples[1], 5000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.stepSamples[2], 8000)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.activityInfoList.count, 1)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.activityInfoList[0].activityClass, .CONTINUOUS_MODERATE)
        XCTAssertEqual(samplesData.polarActivityDataList[0].samples?.activityInfoList[0].factor, 100)
    }

    func testReadActivitySamplesDataFromDayDirectory_ActivityFileNotFound() async throws {
        // Arrange
        let mockRecordingDirectoryContent = try Protocol_PbPFtpDirectory.with {
            $0.entries = []
        }.serializedData()
        mockClient.requestReturnValues.append(.success(mockRecordingDirectoryContent))
        mockClient.requestReturnValues.append(.failure(NSError(domain: "File not found", code: 103, userInfo: nil)))

        // Act
        let samples = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: mockClient, date: Date())

        // Assert
        XCTAssertEqual(samples.polarActivityDataList.count, 0)
    }
    
    func testReadDailySummaryFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_PbDailySummary()
        var activityClassTimes = Data_PbDailySummary().activityClassTimes
        activityClassTimes.timeNonWear = PbDuration.with { $0.hours = 1; $0.seconds = 30; $0.millis = 500 }
        activityClassTimes.timeSleep = PbDuration.with { $0.hours = 7; $0.minutes = 45; $0.seconds = 30; $0.millis = 200 }
        activityClassTimes.timeSedentary = PbDuration.with { $0.hours = 3; $0.minutes = 15; $0.seconds = 20 }
        activityClassTimes.timeLightActivity = PbDuration.with { $0.hours = 2; $0.seconds = 45 }
        activityClassTimes.timeContinuousModerate = PbDuration.with { $0.hours = 1; $0.minutes = 45; $0.seconds = 10; $0.millis = 100 }
        activityClassTimes.timeIntermittentModerate = PbDuration.with { $0.hours = 1; $0.minutes = 15; $0.seconds = 5 }
        activityClassTimes.timeContinuousVigorous = PbDuration.with { $0.minutes = 45; $0.seconds = 30 }
        activityClassTimes.timeIntermittentVigorous = PbDuration.with { $0.minutes = 30; $0.seconds = 15; $0.millis = 50 }
        proto.activityClassTimes = activityClassTimes
        proto.activityGoalSummary = Data_PbActivityGoalSummary.with {
            $0.achievedActivity = 100
            $0.activityGoal = 1000
            $0.timeToGoJog = PbDuration.with { $0.hours = 1; $0.minutes = 1; $0.seconds = 1; $0.millis = 1 }
            $0.timeToGoUp = PbDuration.with { $0.hours = 1; $0.minutes = 1; $0.seconds = 1; $0.millis = 1 }
            $0.timeToGoWalk = PbDuration.with { $0.hours = 1; $0.minutes = 1; $0.seconds = 1; $0.millis = 1 }
        }
        proto.dailyBalanceFeedback = .dbFatigueTryToReduceTrainingLoad
        proto.readinessForSpeedAndStrengthTraining = .rsstA1RecoveredReadyForAllTraining
        proto.activityDistance = 100
        proto.activityCalories = 100
        proto.bmrCalories = 100
        proto.trainingCalories = 500
        proto.date = PbDate.with { $0.day = 1; $0.month = 1; $0.year = 2525 }
        mockClient.requestReturnValue = .success(try proto.serializedData())

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(abbreviation: "UTC")!
        let expectedDate = calendar.date(from: DateComponents(year: 2525, month: 1, day: 1, hour: 0, minute: 0, second: 0))!

        // Act
        let testResult = try await PolarActivityUtils.readDailySummaryDataFromDayDirectory(client: mockClient, date: date)

        // Assert
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.hours, 1)
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.minutes, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.seconds, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeNonWear.millis, 500)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.hours, 7)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.minutes, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.seconds, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeSleep.millis, 200)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.hours, 3)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.minutes, 15)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.seconds, 20)
        XCTAssertEqual(testResult?.activityClassTimes.timeSedentary.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.hours, 2)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.minutes, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.seconds, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeLightActivity.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.hours, 1)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.minutes, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.seconds, 10)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousModerateActivity.millis, 100)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.hours, 1)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.minutes, 15)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.seconds, 5)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentModerateActivity.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.hours, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.minutes, 45)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.seconds, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeContinuousVigorousActivity.millis, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.hours, 0)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.minutes, 30)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.seconds, 15)
        XCTAssertEqual(testResult?.activityClassTimes.timeIntermittentVigorousActivity.millis, 50)
        XCTAssertEqual(testResult?.activityGoalSummary.timeToGoJog, PolarActiveTime(hours: 1, minutes: 1, seconds: 1, millis: 1))
        XCTAssertEqual(testResult?.activityGoalSummary.timeToGoUp, PolarActiveTime(hours: 1, minutes: 1, seconds: 1, millis: 1))
        XCTAssertEqual(testResult?.activityGoalSummary.timeToGoWalk, PolarActiveTime(hours: 1, minutes: 1, seconds: 1, millis: 1))
        XCTAssertEqual(testResult?.activityDistance, 100)
        XCTAssertEqual(testResult?.activityCalories, 100)
        XCTAssertEqual(testResult?.bmrCalories, 100)
        XCTAssertEqual(testResult?.trainingCalories, 500)
        XCTAssertEqual(testResult?.readinessForSpeedAndStrengthTraining, .RECOVERED_READY_FOR_ALL_TRAINING)
        XCTAssertEqual(testResult?.dailyBalanceFeedback, .FATIGUE_TRY_TO_REDUCE_TRAINING_LOAD)
        XCTAssertEqual(testResult?.date, expectedDate)
    }
    
    func testReadDailySummaryFromDayDirectory_FileNotFound() async throws {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))
        
        // Act — error 103 is swallowed; method returns nil
        let result = try await PolarActivityUtils.readDailySummaryDataFromDayDirectory(client: mockClient, date: Date())
        
        // Assert
        XCTAssertNil(result, "Expected nil when daily summary file is not found")
    }

    func testDailySummaryGoldenVectorsMapProtoToPublicModel() async throws {
        let vector = try loadDailySummaryVector("full-summary.json")
        let id = try stringValue(vector, "id")
        let input = try dictionaryValue(vector, "input")
        let proto = try buildDailySummaryProto(from: try dictionaryValue(input, "proto"), id: id)
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let requestDate = try dateValue(try stringValue(input, "requestDate"))
        let result = try await PolarActivityUtils.readDailySummaryDataFromDayDirectory(client: mockClient, date: requestDate)

        let summary = try XCTUnwrap(result)
        try assertDailySummary(summary, expected: try dictionaryValue(vector, "expected"), id: id)
        XCTAssertEqual(mockClient.requestCalls.count, 1)
        let operation = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(operation.path, try stringValue(input, "expectedPath"))
    }

    func testDailySummaryGoldenVectorsCoverConvenienceReaders() async throws {
        let vector = try loadDailySummaryVector("full-summary.json")
        let id = try stringValue(vector, "id")
        let input = try dictionaryValue(vector, "input")
        let proto = try buildDailySummaryProto(from: try dictionaryValue(input, "proto"), id: id)
        let protoData = try proto.serializedData()
        let requestDate = try dateValue(try stringValue(input, "requestDate"))
        let expected = try dictionaryValue(vector, "expected")
        let expectedPath = try stringValue(input, "expectedPath")

        func makeClient() -> MockBlePsFtpClient {
            let client = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            client.requestReturnValue = .success(protoData)
            return client
        }

        func assertRequestedPath(_ client: MockBlePsFtpClient) throws {
            XCTAssertEqual(client.requestCalls.count, 1)
            let operation = try Protocol_PbPFtpOperation(serializedBytes: client.requestCalls[0])
            XCTAssertEqual(operation.path, expectedPath)
        }

        let distanceClient = makeClient()
        let distance = try await PolarActivityUtils.readDistanceFromDayDirectory(client: distanceClient, date: requestDate)
        XCTAssertEqual(distance, try floatValue(expected, "activityDistance"), accuracy: 0.00001, "\(id) distance")
        try assertRequestedPath(distanceClient)

        let activityCaloriesClient = makeClient()
        let activityCalories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: activityCaloriesClient, date: requestDate, caloriesType: .activity)
        XCTAssertEqual(activityCalories, try intValue(expected, "activityCalories"), "\(id) activityCalories")
        try assertRequestedPath(activityCaloriesClient)

        let trainingCaloriesClient = makeClient()
        let trainingCalories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: trainingCaloriesClient, date: requestDate, caloriesType: .training)
        XCTAssertEqual(trainingCalories, try intValue(expected, "trainingCalories"), "\(id) trainingCalories")
        try assertRequestedPath(trainingCaloriesClient)

        let bmrCaloriesClient = makeClient()
        let bmrCalories = try await PolarActivityUtils.readCaloriesFromDayDirectory(client: bmrCaloriesClient, date: requestDate, caloriesType: .bmr)
        XCTAssertEqual(bmrCalories, try intValue(expected, "bmrCalories"), "\(id) bmrCalories")
        try assertRequestedPath(bmrCaloriesClient)

        let activeTimeClient = makeClient()
        let activeTime = try await PolarActivityUtils.readActiveTimeFromDayDirectory(client: activeTimeClient, date: requestDate)
        XCTAssertEqual(activeTime.date, requestDate, "\(id) activeTime date")
        let times = try dictionaryValue(expected, "activityClassTimes")
        try assertActiveTime(activeTime.timeNonWear, expected: try dictionaryValue(times, "timeNonWear"), id: "\(id) timeNonWear")
        try assertActiveTime(activeTime.timeSleep, expected: try dictionaryValue(times, "timeSleep"), id: "\(id) timeSleep")
        try assertActiveTime(activeTime.timeSedentary, expected: try dictionaryValue(times, "timeSedentary"), id: "\(id) timeSedentary")
        try assertActiveTime(activeTime.timeLightActivity, expected: try dictionaryValue(times, "timeLightActivity"), id: "\(id) timeLightActivity")
        try assertActiveTime(activeTime.timeContinuousModerateActivity, expected: try dictionaryValue(times, "timeContinuousModerate"), id: "\(id) timeContinuousModerate")
        try assertActiveTime(activeTime.timeIntermittentModerateActivity, expected: try dictionaryValue(times, "timeIntermittentModerate"), id: "\(id) timeIntermittentModerate")
        try assertActiveTime(activeTime.timeContinuousVigorousActivity, expected: try dictionaryValue(times, "timeContinuousVigorous"), id: "\(id) timeContinuousVigorous")
        try assertActiveTime(activeTime.timeIntermittentVigorousActivity, expected: try dictionaryValue(times, "timeIntermittentVigorous"), id: "\(id) timeIntermittentVigorous")
        try assertRequestedPath(activeTimeClient)
    }

    func testDailySummaryGoldenVectorsFollowNeutralKmpShape() throws {
        let vector = try loadDailySummaryVector("full-summary.json")
        let id = try stringValue(vector, "id")
        XCTAssertNotNil(vector["area"], id)
        XCTAssertNotNil(vector["case"], id)
        XCTAssertNotNil(vector["source"], id)
        XCTAssertNotNil(vector["input"], id)
        XCTAssertNotNil(vector["expected"], id)
        let input = try dictionaryValue(vector, "input")
        XCTAssertNotNil(input["proto"], id)
        XCTAssertNotNil(input["requestDate"], id)
        XCTAssertNotNil(input["expectedPath"], id)
        let platforms = try dictionaryValue(vector, "platforms")
        XCTAssertEqual(platforms["android"] as? Bool, true, id)
        XCTAssertEqual(platforms["ios"] as? Bool, true, id)
        XCTAssertEqual(platforms["common"] as? Bool, true, id)
    }

    func testActivitySampleGoldenVectorsCoverStepAggregationAndSampleMapping() async throws {
        let vector = try loadActivitySamplesVector("two-files-step-aggregation.json")
        let input = try dictionaryValue(vector, "input")
        let requestDate = try dateValue(try stringValue(input, "requestDate"))
        let vectorId = try stringValue(vector, "id")

        let stepsClient = try makeActivitySamplesClient(vector)
        let steps = try await PolarActivityUtils.readStepsFromDayDirectory(client: stepsClient, date: requestDate)
        XCTAssertEqual(steps, try intValue(try dictionaryValue(vector, "expected"), "totalSteps"), "\(vectorId) totalSteps")
        try assertActivitySampleRequests(stepsClient, vector: vector)

        let samplesClient = try makeActivitySamplesClient(vector)
        let samples = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: samplesClient, date: requestDate)
        try assertActivitySamplesData(samples, vector: vector)
        try assertActivitySampleRequests(samplesClient, vector: vector)
    }

    func testActivitySampleGoldenVectorsPreserveMalformedFilePolicy() async throws {
        let vector = try loadActivitySamplesVector("malformed-sample-file-platform-policy.json")
        let id = try stringValue(vector, "id")
        let input = try dictionaryValue(vector, "input")
        let requestDate = try dateValue(try stringValue(input, "requestDate"))

        let stepsClient = try makeActivitySamplesClient(vector)
        let steps = try await PolarActivityUtils.readStepsFromDayDirectory(client: stepsClient, date: requestDate)
        XCTAssertEqual(steps, try intValue(try dictionaryValue(vector, "expected"), "steps"), "\(id) steps")
        try assertActivitySampleRequests(stepsClient, vector: vector)

        let samplesClient = try makeActivitySamplesClient(vector)
        do {
            _ = try await PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client: samplesClient, date: requestDate)
            XCTFail("\(id) should throw for malformed iOS sample payload")
        } catch {
            XCTAssertTrue(try boolValue(try dictionaryValue(try dictionaryValue(vector, "expected"), "iosSamples"), "throws"), "\(id) iOS malformed policy")
        }
        try assertActivitySampleRequests(samplesClient, vector: vector)
    }

    func testActivitySampleGoldenVectorsFollowNeutralKmpShape() throws {
        for fileName in ["two-files-step-aggregation.json", "malformed-sample-file-platform-policy.json"] {
            let vector = try loadActivitySamplesVector(fileName)
            let id = try stringValue(vector, "id")
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try dictionaryValue(vector, "input")
            XCTAssertNotNil(input["requestDate"], id)
            XCTAssertNotNil(input["directoryPath"], id)
            XCTAssertNotNil(input["directoryEntries"], id)
            XCTAssertNotNil(input["files"], id)
            let platforms = try dictionaryValue(vector, "platforms")
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testActivitySummaryReadinessManifestIsPinnedBeforeModelMigration() throws {
        let readiness = try loadActivitySamplesVector("activity-summary-readiness.json")
        let input = try dictionaryValue(readiness, "input")
        let expected = try dictionaryValue(readiness, "expected")
        let consumerTests = try dictionaryValue(readiness, "consumerTests")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(try stringValue(readiness, "id"), "activity-summary-readiness")
        XCTAssertEqual(try stringValue(input, "kind"), "activitySummaryReadiness")
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
            "unsupported-field-deferral",
            "public-model-shape-gate",
            "facade-request-error-boundary",
            "platform-activity-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Activity, automatic-sample, and daily-summary migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS activity/automatic/daily tests continue to reference the same vectors, activity request paths, aggregation, intervals, activity-info projection, malformed activity-sample behavior, automatic HR trigger and heart-rate arrays, PPI delta/status decoding, daily-summary path/scalar/duration projection, unsupported-field deferral, public model shape, facade request/error boundaries, and compile verification remain explicit before production model mapping moves.")
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

    private func buildDailySummaryProto(from fields: [String: Any], id: String) throws -> Data_PbDailySummary {
        var proto = Data_PbDailySummary()
        proto.date = try buildPbDate(from: try dictionaryValue(fields, "date"), id: id)
        proto.activityCalories = try uint32Value(fields, "activityCalories")
        proto.trainingCalories = try uint32Value(fields, "trainingCalories")
        proto.bmrCalories = try uint32Value(fields, "bmrCalories")
        proto.steps = try uint32Value(fields, "steps")
        proto.activityDistance = try floatValue(fields, "activityDistance")
        proto.dailyBalanceFeedback = try dailyBalanceFeedback(named: try stringValue(fields, "dailyBalanceFeedback"), id: id)
        proto.readinessForSpeedAndStrengthTraining = try readinessForSpeedAndStrengthTraining(named: try stringValue(fields, "readinessForSpeedAndStrengthTraining"), id: id)
        proto.activityGoalSummary = try buildActivityGoalSummary(from: try dictionaryValue(fields, "activityGoalSummary"), id: id)
        proto.activityClassTimes = try buildActivityClassTimes(from: try dictionaryValue(fields, "activityClassTimes"), id: id)
        return proto
    }

    private func buildActivityGoalSummary(from fields: [String: Any], id: String) throws -> Data_PbActivityGoalSummary {
        return try Data_PbActivityGoalSummary.with {
            $0.activityGoal = try floatValue(fields, "activityGoal")
            $0.achievedActivity = try floatValue(fields, "achievedActivity")
            $0.timeToGoUp = try buildDuration(from: try dictionaryValue(fields, "timeToGoUp"), id: id)
            $0.timeToGoWalk = try buildDuration(from: try dictionaryValue(fields, "timeToGoWalk"), id: id)
            $0.timeToGoJog = try buildDuration(from: try dictionaryValue(fields, "timeToGoJog"), id: id)
        }
    }

    private func buildActivityClassTimes(from fields: [String: Any], id: String) throws -> Data_PbActivityClassTimes {
        return try Data_PbActivityClassTimes.with {
            $0.timeNonWear = try buildDuration(from: try dictionaryValue(fields, "timeNonWear"), id: id)
            $0.timeSleep = try buildDuration(from: try dictionaryValue(fields, "timeSleep"), id: id)
            $0.timeSedentary = try buildDuration(from: try dictionaryValue(fields, "timeSedentary"), id: id)
            $0.timeLightActivity = try buildDuration(from: try dictionaryValue(fields, "timeLightActivity"), id: id)
            $0.timeContinuousModerate = try buildDuration(from: try dictionaryValue(fields, "timeContinuousModerate"), id: id)
            $0.timeIntermittentModerate = try buildDuration(from: try dictionaryValue(fields, "timeIntermittentModerate"), id: id)
            $0.timeContinuousVigorous = try buildDuration(from: try dictionaryValue(fields, "timeContinuousVigorous"), id: id)
            $0.timeIntermittentVigorous = try buildDuration(from: try dictionaryValue(fields, "timeIntermittentVigorous"), id: id)
        }
    }

    private func buildPbDate(from fields: [String: Any], id: String) throws -> PbDate {
        return try PbDate.with {
            $0.year = try uint32Value(fields, "year")
            $0.month = try uint32Value(fields, "month")
            $0.day = try uint32Value(fields, "day")
        }
    }

    private func buildDuration(from fields: [String: Any], id: String) throws -> PbDuration {
        return try PbDuration.with {
            $0.hours = try uint32Value(fields, "hours")
            $0.minutes = try uint32Value(fields, "minutes")
            $0.seconds = try uint32Value(fields, "seconds")
            $0.millis = try uint32Value(fields, "millis")
        }
    }

    private func makeActivitySamplesClient(_ vector: [String: Any]) throws -> MockBlePsFtpClient {
        let input = try dictionaryValue(vector, "input")
        let client = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        client.requestReturnValues.append(.success(try buildActivityDirectory(from: vector).serializedData()))
        for file in try arrayValue(input, "files") {
            let fields = try dictionaryValue(file)
            if let responseHex = fields["responseHex"] as? String {
                client.requestReturnValues.append(.success(try dataFromHex(responseHex)))
            } else {
                client.requestReturnValues.append(.success(try buildActivitySamplesProto(from: try dictionaryValue(fields, "proto"), id: try stringValue(vector, "id")).serializedData()))
            }
        }
        return client
    }

    private func buildActivityDirectory(from vector: [String: Any]) throws -> Protocol_PbPFtpDirectory {
        let input = try dictionaryValue(vector, "input")
        var directory = Protocol_PbPFtpDirectory()
        directory.entries = try arrayValue(input, "directoryEntries").map { entry in
            let fields = try dictionaryValue(entry)
            return Protocol_PbPFtpEntry.with {
                $0.name = try! stringValue(fields, "name")
                $0.size = UInt64(try! intValue(fields, "size"))
            }
        }
        return directory
    }

    private func buildActivitySamplesProto(from fields: [String: Any], id: String) throws -> Data_PbActivitySamples {
        return try Data_PbActivitySamples.with {
            $0.startTime = try buildPbLocalDateTime(from: try dictionaryValue(fields, "startTime"), id: id)
            $0.metRecordingInterval = try buildDuration(from: try dictionaryValue(fields, "metRecordingInterval"), id: id)
            $0.stepsRecordingInterval = try buildDuration(from: try dictionaryValue(fields, "stepsRecordingInterval"), id: id)
            $0.metSamples = try arrayValue(fields, "metSamples").map { try floatValue($0) }
            $0.stepsSamples = try arrayValue(fields, "stepsSamples").map { UInt32(try intValue($0)) }
            $0.activityInfo = try arrayValue(fields, "activityInfo").map { item in
                let info = try dictionaryValue(item)
                return try Data_PbActivityInfo.with {
                    $0.value = try activityClass(named: try stringValue(info, "value"), id: id)
                    $0.timeStamp = try buildPbLocalDateTime(from: try dictionaryValue(info, "timeStamp"), id: id)
                    $0.factor = try floatValue(info, "factor")
                }
            }
        }
    }

    private func buildPbLocalDateTime(from fields: [String: Any], id: String) throws -> PbLocalDateTime {
        let time = try dictionaryValue(fields, "time")
        return try PbLocalDateTime.with {
            $0.date = try buildPbDate(from: try dictionaryValue(fields, "date"), id: id)
            $0.time = PbTime.with {
                $0.hour = try! uint32Value(time, "hour")
                $0.minute = try! uint32Value(time, "minute")
                $0.seconds = try! uint32Value(time, "seconds")
                $0.millis = try! uint32Value(time, "millis")
            }
            $0.timeZoneOffset = try int32Value(fields, "timeZoneOffset")
            $0.obsoleteTrusted = try boolValue(fields, "trusted")
        }
    }

    private func assertActivitySamplesData(_ actual: PolarActivityDayData, vector: [String: Any]) throws {
        let files = try arrayValue(try dictionaryValue(vector, "input"), "files")
        let vectorId = try stringValue(vector, "id")
        XCTAssertEqual(actual.polarActivityDataList.count, files.count, "\(vectorId) sample count")
        for (index, file) in files.enumerated() {
            let expected = try dictionaryValue(try dictionaryValue(file), "expected")
            let caseId = "\(vectorId)[\(index)]"
            let samples = try XCTUnwrap(actual.polarActivityDataList[index].samples, "\(caseId) samples")
            XCTAssertEqual(samples.startTime, try localDateTimeValue(try stringValue(expected, "startTime")), "\(caseId) startTime")
            XCTAssertEqual(samples.metRecordingInterval, try intValue(expected, "metRecordingIntervalSeconds"), "\(caseId) met interval")
            XCTAssertEqual(samples.stepRecordingInterval, try intValue(expected, "stepRecordingIntervalSeconds"), "\(caseId) step interval")
            XCTAssertEqual(samples.metSamples, try arrayValue(expected, "metSamples").map { try floatValue($0) }, "\(caseId) met samples")
            XCTAssertEqual(samples.stepSamples, try arrayValue(expected, "stepSamples").map { UInt32(try intValue($0)) }, "\(caseId) step samples")
            let expectedInfo = try arrayValue(expected, "activityInfo")
            XCTAssertEqual(samples.activityInfoList.count, expectedInfo.count, "\(caseId) info count")
            for (infoIndex, infoItem) in expectedInfo.enumerated() {
                let info = try dictionaryValue(infoItem)
                let actualInfo = samples.activityInfoList[infoIndex]
                let infoCaseId = "\(caseId)[\(infoIndex)]"
                XCTAssertEqual(actualInfo.activityClass.rawValue, try stringValue(info, "activityClass"), "\(infoCaseId) class")
                XCTAssertEqual(actualInfo.timeStamp, try localDateTimeValue(try stringValue(info, "timeStamp")), "\(infoCaseId) timestamp")
                XCTAssertEqual(actualInfo.factor, try floatValue(info, "factor"), accuracy: 0.00001, "\(infoCaseId) factor")
            }
        }
    }

    private func assertActivitySampleRequests(_ client: MockBlePsFtpClient, vector: [String: Any]) throws {
        let input = try dictionaryValue(vector, "input")
        let filePaths = try arrayValue(try dictionaryValue(vector, "expected"), "filePaths").map { try stringValue($0) }
        XCTAssertEqual(client.requestCalls.count, filePaths.count + 1)
        let directoryOperation = try Protocol_PbPFtpOperation(serializedBytes: client.requestCalls[0])
        XCTAssertEqual(directoryOperation.path, try stringValue(input, "directoryPath"))
        for (index, path) in filePaths.enumerated() {
            let operation = try Protocol_PbPFtpOperation(serializedBytes: client.requestCalls[index + 1])
            XCTAssertEqual(operation.path, path)
        }
    }

    private func assertDailySummary(_ actual: PolarDailySummary, expected: [String: Any], id: String) throws {
        XCTAssertEqual(actual.date, try dateValue(try stringValue(expected, "date")), "\(id) date")
        XCTAssertEqual(actual.activityCalories, try uint32Value(expected, "activityCalories"), "\(id) activityCalories")
        XCTAssertEqual(actual.trainingCalories, try uint32Value(expected, "trainingCalories"), "\(id) trainingCalories")
        XCTAssertEqual(actual.bmrCalories, try uint32Value(expected, "bmrCalories"), "\(id) bmrCalories")
        XCTAssertEqual(actual.steps, try uint32Value(expected, "steps"), "\(id) steps")
        XCTAssertEqual(actual.activityDistance, try floatValue(expected, "activityDistance"), accuracy: 0.00001, "\(id) activityDistance")
        XCTAssertEqual(actual.dailyBalanceFeedback.rawValue, try stringValue(expected, "dailyBalanceFeedback"), "\(id) dailyBalanceFeedback")
        XCTAssertEqual(actual.readinessForSpeedAndStrengthTraining.rawValue, try stringValue(expected, "readinessForSpeedAndStrengthTraining"), "\(id) readiness")
        let goal = try dictionaryValue(expected, "activityGoalSummary")
        XCTAssertEqual(actual.activityGoalSummary.activityGoal, try floatValue(goal, "activityGoal"), accuracy: 0.00001, "\(id) activityGoal")
        XCTAssertEqual(actual.activityGoalSummary.achievedActivity, try floatValue(goal, "achievedActivity"), accuracy: 0.00001, "\(id) achievedActivity")
        try assertActiveTime(actual.activityGoalSummary.timeToGoUp, expected: try dictionaryValue(goal, "timeToGoUp"), id: "\(id) timeToGoUp")
        try assertActiveTime(actual.activityGoalSummary.timeToGoWalk, expected: try dictionaryValue(goal, "timeToGoWalk"), id: "\(id) timeToGoWalk")
        try assertActiveTime(actual.activityGoalSummary.timeToGoJog, expected: try dictionaryValue(goal, "timeToGoJog"), id: "\(id) timeToGoJog")
        let times = try dictionaryValue(expected, "activityClassTimes")
        try assertActiveTime(actual.activityClassTimes.timeNonWear, expected: try dictionaryValue(times, "timeNonWear"), id: "\(id) timeNonWear")
        try assertActiveTime(actual.activityClassTimes.timeSleep, expected: try dictionaryValue(times, "timeSleep"), id: "\(id) timeSleep")
        try assertActiveTime(actual.activityClassTimes.timeSedentary, expected: try dictionaryValue(times, "timeSedentary"), id: "\(id) timeSedentary")
        try assertActiveTime(actual.activityClassTimes.timeLightActivity, expected: try dictionaryValue(times, "timeLightActivity"), id: "\(id) timeLightActivity")
        try assertActiveTime(actual.activityClassTimes.timeContinuousModerateActivity, expected: try dictionaryValue(times, "timeContinuousModerate"), id: "\(id) timeContinuousModerate")
        try assertActiveTime(actual.activityClassTimes.timeIntermittentModerateActivity, expected: try dictionaryValue(times, "timeIntermittentModerate"), id: "\(id) timeIntermittentModerate")
        try assertActiveTime(actual.activityClassTimes.timeContinuousVigorousActivity, expected: try dictionaryValue(times, "timeContinuousVigorous"), id: "\(id) timeContinuousVigorous")
        try assertActiveTime(actual.activityClassTimes.timeIntermittentVigorousActivity, expected: try dictionaryValue(times, "timeIntermittentVigorous"), id: "\(id) timeIntermittentVigorous")
    }

    private func assertActiveTime(_ actual: PolarActiveTime?, expected: [String: Any], id: String) throws {
        let activeTime = try XCTUnwrap(actual, id)
        XCTAssertEqual(activeTime.hours, try intValue(expected, "hours"), "\(id) hours")
        XCTAssertEqual(activeTime.minutes, try intValue(expected, "minutes"), "\(id) minutes")
        XCTAssertEqual(activeTime.seconds, try intValue(expected, "seconds"), "\(id) seconds")
        XCTAssertEqual(activeTime.millis, try intValue(expected, "millis"), "\(id) millis")
    }

    private func loadDailySummaryVector(_ fileName: String) throws -> [String: Any] {
        let url = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/daily-summary/\(fileName)")
        let data = try Data(contentsOf: url)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 1, userInfo: [NSLocalizedDescriptionKey: "Daily summary vector \(fileName) is not a JSON object"])
        }
        return object
    }

    private func loadActivitySamplesVector(_ fileName: String) throws -> [String: Any] {
        let url = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/activity-samples/\(fileName)")
        let data = try Data(contentsOf: url)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 11, userInfo: [NSLocalizedDescriptionKey: "Activity samples vector \(fileName) is not a JSON object"])
        }
        return object
    }


    private func dateValue(_ value: String) throws -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let parts = value.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, let date = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2])) else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid date \(value)"])
        }
        return date
    }

    private func dictionaryValue(_ object: [String: Any], _ key: String) throws -> [String: Any] {
        guard let value = object[key] as? [String: Any] else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 4, userInfo: [NSLocalizedDescriptionKey: "Missing dictionary value for \(key)"])
        }
        return value
    }

    private func stringValue(_ object: [String: Any], _ key: String) throws -> String {
        guard let value = object[key] as? String else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 5, userInfo: [NSLocalizedDescriptionKey: "Missing string value for \(key)"])
        }
        return value
    }

    private func intValue(_ object: [String: Any], _ key: String) throws -> Int {
        guard let value = object[key] as? NSNumber else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 6, userInfo: [NSLocalizedDescriptionKey: "Missing numeric value for \(key)"])
        }
        return value.intValue
    }

    private func uint32Value(_ object: [String: Any], _ key: String) throws -> UInt32 {
        let value = try intValue(object, key)
        guard value >= 0 else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 7, userInfo: [NSLocalizedDescriptionKey: "Negative UInt32 value for \(key)"])
        }
        return UInt32(value)
    }

    private func int32Value(_ object: [String: Any], _ key: String) throws -> Int32 {
        let value = try intValue(object, key)
        return Int32(value)
    }

    private func floatValue(_ object: [String: Any], _ key: String) throws -> Float {
        guard let value = object[key] as? NSNumber else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 8, userInfo: [NSLocalizedDescriptionKey: "Missing numeric value for \(key)"])
        }
        return value.floatValue
    }

    private func boolValue(_ object: [String: Any], _ key: String) throws -> Bool {
        guard let value = object[key] as? Bool else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 12, userInfo: [NSLocalizedDescriptionKey: "Missing bool value for \(key)"])
        }
        return value
    }

    private func dictionaryValue(_ object: Any) throws -> [String: Any] {
        guard let value = object as? [String: Any] else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 13, userInfo: [NSLocalizedDescriptionKey: "Expected dictionary value"])
        }
        return value
    }

    private func arrayValue(_ object: [String: Any], _ key: String) throws -> [Any] {
        guard let value = object[key] as? [Any] else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 14, userInfo: [NSLocalizedDescriptionKey: "Missing array value for \(key)"])
        }
        return value
    }

    private func intValue(_ object: Any) throws -> Int {
        guard let value = object as? NSNumber else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 15, userInfo: [NSLocalizedDescriptionKey: "Expected numeric value"])
        }
        return value.intValue
    }

    private func floatValue(_ object: Any) throws -> Float {
        guard let value = object as? NSNumber else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 16, userInfo: [NSLocalizedDescriptionKey: "Expected numeric value"])
        }
        return value.floatValue
    }

    private func stringValue(_ object: Any) throws -> String {
        guard let value = object as? String else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 17, userInfo: [NSLocalizedDescriptionKey: "Expected string value"])
        }
        return value
    }

    private func localDateTimeValue(_ value: String) throws -> Date {
        let pattern = #"^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3}))?$"#
        guard let match = value.range(of: pattern, options: .regularExpression) else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 18, userInfo: [NSLocalizedDescriptionKey: "Invalid local date time \(value)"])
        }
        let matched = String(value[match])
        let groups = try NSRegularExpression(pattern: pattern)
            .matches(in: matched, range: NSRange(matched.startIndex..., in: matched))
        guard let first = groups.first else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 18, userInfo: [NSLocalizedDescriptionKey: "Invalid local date time \(value)"])
        }
        func group(_ index: Int) -> Int {
            let range = first.range(at: index)
            guard range.location != NSNotFound, let swiftRange = Range(range, in: matched) else {
                return 0
            }
            return Int(matched[swiftRange]) ?? 0
        }
        var components = DateComponents()
        components.calendar = Calendar(identifier: .gregorian)
        components.timeZone = TimeZone(secondsFromGMT: 0)
        components.year = group(1)
        components.month = group(2)
        components.day = group(3)
        components.hour = group(4)
        components.minute = group(5)
        components.second = group(6)
        components.nanosecond = group(7) * 1_000_000
        guard let date = components.date else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 18, userInfo: [NSLocalizedDescriptionKey: "Invalid local date time \(value)"])
        }
        return date
    }

    private func dataFromHex(_ value: String) throws -> Data {
        guard value.count.isMultiple(of: 2) else {
            throw NSError(domain: "PolarActivityUtilsTest", code: 20, userInfo: [NSLocalizedDescriptionKey: "Hex string must have even length"])
        }
        var data = Data()
        var index = value.startIndex
        while index < value.endIndex {
            let nextIndex = value.index(index, offsetBy: 2)
            guard let byte = UInt8(value[index..<nextIndex], radix: 16) else {
                throw NSError(domain: "PolarActivityUtilsTest", code: 21, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte"])
            }
            data.append(byte)
            index = nextIndex
        }
        return data
    }

    private func dailyBalanceFeedback(named name: String, id: String) throws -> PbDailyBalanceFeedback {
        switch name {
        case "DB_YOU_SEEM_TO_BE_STRAINED":
            return .dbYouSeemToBeStrained
        default:
            throw NSError(domain: "PolarActivityUtilsTest", code: 9, userInfo: [NSLocalizedDescriptionKey: "Unsupported daily balance feedback \(name) in \(id)"])
        }
    }

    private func readinessForSpeedAndStrengthTraining(named name: String, id: String) throws -> PbReadinessForSpeedAndStrengthTraining {
        switch name {
        case "RSST_B4_NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO":
            return .rsstB4NotRecoveredNoLegTrainingOrIntensiveCardio
        default:
            throw NSError(domain: "PolarActivityUtilsTest", code: 10, userInfo: [NSLocalizedDescriptionKey: "Unsupported readiness \(name) in \(id)"])
        }
    }

    private func activityClass(named name: String, id: String) throws -> Data_PbActivityInfo.ActivityClass {
        switch name {
        case "CONTINUOUS_MODERATE":
            return .continuousModerate
        case "LIGHT":
            return .light
        default:
            throw NSError(domain: "PolarActivityUtilsTest", code: 19, userInfo: [NSLocalizedDescriptionKey: "Unsupported activity class \(name) in \(id)"])
        }
    }
}
