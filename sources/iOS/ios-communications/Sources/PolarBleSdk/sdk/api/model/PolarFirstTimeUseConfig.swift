import Foundation
import SwiftProtobuf
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public struct PolarFirstTimeUseConfig {

    public enum Gender {
        case male
        case female
    }

    static func protobufValue(for gender: Gender) -> Data_PbUserGender.Gender {
        #if canImport(PolarBleSdkShared)
        let sharedName = gender == .male ? "MALE" : "FEMALE"
        if let sharedValue = PolarFirstTimeUseRuntimePlanner.genderValue(name: sharedName),
           let proto = Data_PbUserGender.Gender(rawValue: Int(truncating: sharedValue)) {
            return proto
        }
        #endif
        return gender == .male ? .male : .female
    }

    public enum TypicalDay: Int, CaseIterable {
        case mostlySitting = 1
        case mostlyStanding = 2
        case mostlyMoving = 3

        public var description: String {
            switch self {
            case .mostlySitting:
                return "Mostly Sitting"
            case .mostlyStanding:
                return "Mostly Standing"
            case .mostlyMoving:
                return "Mostly Moving"
            }
        }
    }

    static func protobufValue(for typicalDay: TypicalDay) -> Data_PbUserTypicalDay.TypicalDay {
        #if canImport(PolarBleSdkShared)
        if let sharedValue = PolarFirstTimeUseRuntimePlanner.typicalDayValue(value: Int32(typicalDay.rawValue)),
           let proto = Data_PbUserTypicalDay.TypicalDay(rawValue: Int(truncating: sharedValue)) {
            return proto
        }
        #endif
        return Data_PbUserTypicalDay.TypicalDay(rawValue: typicalDay.rawValue) ?? .mostlySitting
    }

    public enum TrainingBackground: Int {
            case occasional = 10
            case regular = 20
            case frequent = 30
            case heavy = 40
            case semiPro = 50
            case pro = 60

            static func protobufValue(for trainingBackground: TrainingBackground) -> Data_PbUserTrainingBackground.TrainingBackground {
                #if canImport(PolarBleSdkShared)
                if let sharedValue = PolarFirstTimeUseRuntimePlanner.trainingBackgroundValue(value: Int32(trainingBackground.rawValue)),
                   let proto = Data_PbUserTrainingBackground.TrainingBackground(rawValue: Int(truncating: sharedValue)) {
                    return proto
                }
                #endif
                return Data_PbUserTrainingBackground.TrainingBackground(rawValue: trainingBackground.rawValue) ?? .occasional
            }
        }

    public let gender: Gender
    public let birthDate: Date
    public let height: Float
    public let weight: Float
    public let maxHeartRate: Int
    public let vo2Max: Int
    public let restingHeartRate: Int
    public let trainingBackground: TrainingBackground
    public let deviceTime: String
    public let typicalDay: TypicalDay
    public let sleepGoalMinutes: Int

    static let FTU_CONFIG_FILEPATH = "/U/0/S/PHYSDATA.BPB"

    public init(
        gender: Gender,
        birthDate: Date,
        height: Float,
        weight: Float,
        maxHeartRate: Int,
        vo2Max: Int,
        restingHeartRate: Int,
        trainingBackground: TrainingBackground,
        deviceTime: String,
        typicalDay: TypicalDay,
        sleepGoalMinutes: Int
    ) {
        assert(height >= 90.0 && height <= 240.0, "Height must be between 90 and 240 cm")
        assert(weight >= 15.0 && weight <= 300.0, "Weight must be between 15 and 300 kg")
        assert(maxHeartRate >= 100 && maxHeartRate <= 240, "Max heart rate must be between 100 and 240 bpm")
        assert(restingHeartRate >= 20 && restingHeartRate <= 120, "Resting heart rate must be between 20 and 120 bpm")
        assert(trainingBackground.rawValue >= 10 && trainingBackground.rawValue <= 60, "Training background out of range")
        assert(vo2Max >= 10 && vo2Max <= 95, "VO2 max must be between 10 and 95")
        assert(sleepGoalMinutes >= 300 && sleepGoalMinutes <= 660, "Sleep goal must be between 300 and 660 minutes")

        self.gender = gender
        self.birthDate = birthDate
        self.height = height
        self.weight = weight
        self.maxHeartRate = maxHeartRate
        self.vo2Max = vo2Max
        self.restingHeartRate = restingHeartRate
        self.trainingBackground = trainingBackground
        self.deviceTime = deviceTime
        self.typicalDay = typicalDay
        self.sleepGoalMinutes = sleepGoalMinutes
    }

    func toProto() -> Data_PbUserPhysData? {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime]
        dateFormatter.timeZone = TimeZone(secondsFromGMT: 0)

        guard let deviceTimeDate = dateFormatter.date(from: deviceTime) else {
            BleLogger.error("Failed to parse deviceTime from: \(deviceTime)")
            return nil
        }

        let calendar = Calendar(identifier: .gregorian)
        let utcTimeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents(in: utcTimeZone, from: deviceTimeDate)

        let lastModified = PbSystemDateTime.with {
            $0.date = PbDate.with {
                $0.year = UInt32(components.year!)
                $0.month = UInt32(components.month!)
                $0.day = UInt32(components.day!)
            }
            $0.time = PbTime.with {
                $0.hour = UInt32(components.hour!)
                $0.minute = UInt32(components.minute!)
                $0.seconds = UInt32(components.second!)
            }
            $0.trusted = true
        }

        let birthday = Data_PbUserBirthday.with {
            $0.value = PbDate.with {
                $0.year = UInt32(Calendar.current.component(.year, from: birthDate))
                $0.month = UInt32(Calendar.current.component(.month, from: birthDate))
                $0.day = UInt32(Calendar.current.component(.day, from: birthDate))
            }
            $0.lastModified = lastModified
        }

        let genderPb = Data_PbUserGender.with {
            $0.value = PolarFirstTimeUseConfig.protobufValue(for: gender)
            $0.lastModified = lastModified
        }

        let weightPb = Data_PbUserWeight.with {
            $0.value = weight
            $0.lastModified = lastModified
        }

        let heightPb = Data_PbUserHeight.with {
            $0.value = height
            $0.lastModified = lastModified
        }

        let maxHeartRatePb = Data_PbUserHrAttribute.with {
            $0.value = UInt32(maxHeartRate)
            $0.lastModified = lastModified
        }

        let restingHeartRatePb = Data_PbUserHrAttribute.with {
            $0.value = UInt32(restingHeartRate)
            $0.lastModified = lastModified
        }

        let trainingBackgroundPb = Data_PbUserTrainingBackground.with {
            $0.value = TrainingBackground.protobufValue(for: trainingBackground)
            $0.lastModified = lastModified
        }

        let vo2MaxPb = Data_PbUserVo2Max.with {
            $0.value = UInt32(vo2Max)
            $0.lastModified = lastModified
        }

        let typicalDayPb = Data_PbUserTypicalDay.with {
            $0.value = PolarFirstTimeUseConfig.protobufValue(for: typicalDay)
            $0.lastModified = lastModified
        }

        let sleepGoalPb = Data_PbSleepGoal.with {
            $0.sleepGoalMinutes = UInt32(sleepGoalMinutes)
            $0.lastModified = lastModified
        }

        return Data_PbUserPhysData.with {
            $0.birthday = birthday
            $0.gender = genderPb
            $0.weight = weightPb
            $0.height = heightPb
            $0.maximumHeartrate = maxHeartRatePb
            $0.restingHeartrate = restingHeartRatePb
            $0.trainingBackground = trainingBackgroundPb
            $0.vo2Max = vo2MaxPb
            $0.typicalDay = typicalDayPb
            $0.sleepGoal = sleepGoalPb
            $0.lastModified = lastModified
        }
    }
}

extension Data_PbUserPhysData {
    func toPolarPhysicalConfiguration() -> PolarPhysicalConfiguration {
        let gender = PolarFirstTimeUseConfig.physicalGender(from: self.gender.value)

        let birthDate: Date = {
            let date = self.birthday.value
            var components = DateComponents()
            components.year = Int(date.year)
            components.month = Int(date.month)
            components.day = Int(date.day)
            return Calendar(identifier: .gregorian).date(from: components) ?? Date()
        }()

        let deviceTime: String = {
            let lastModified = self.lastModified
            if lastModified.hasDate && lastModified.hasTime {
                var components = DateComponents()
                components.year = Int(lastModified.date.year)
                components.month = Int(lastModified.date.month)
                components.day = Int(lastModified.date.day)
                components.hour = Int(lastModified.time.hour)
                components.minute = Int(lastModified.time.minute)
                components.second = Int(lastModified.time.seconds)
                components.timeZone = TimeZone(secondsFromGMT: 0)

                let calendar = Calendar(identifier: .gregorian)
                if let date = calendar.date(from: components) {
                    return ISO8601DateFormatter().string(from: date)
                }
            }
            return ISO8601DateFormatter().string(from: Date())
        }()

        let typicalDay = PolarFirstTimeUseConfig.physicalTypicalDay(from: self.typicalDay.value)

        return PolarPhysicalConfiguration(
            gender: gender,
            birthDate: birthDate,
            height: self.height.value,
            weight: self.weight.value,
            maxHeartRate: Int(self.maximumHeartrate.value),
            vo2Max: Int(self.vo2Max.value),
            restingHeartRate: Int(self.restingHeartrate.value),
            trainingBackground: PolarFirstTimeUseConfig.physicalTrainingBackgroundValue(from: self.trainingBackground.value),
            deviceTime: deviceTime,
            typicalDay: typicalDay,
            sleepGoalMinutes: Int(self.sleepGoal.sleepGoalMinutes)
        )
    }
}

private extension PolarFirstTimeUseConfig {
    static func physicalGender(from value: Data_PbUserGender.Gender) -> PolarPhysicalConfiguration.Gender {
        #if canImport(PolarBleSdkShared)
        if let sharedName = PolarFirstTimeUseRuntimePlanner.genderName(value: Int32(value.rawValue)) {
            switch sharedName {
            case "MALE": return .male
            case "FEMALE": return .female
            default: break
            }
        }
        #endif
        switch value {
        case .male:
            return .male
        case .female:
            return .female
        }
    }

    static func physicalTypicalDay(from value: Data_PbUserTypicalDay.TypicalDay) -> PolarPhysicalConfiguration.TypicalDay {
        #if canImport(PolarBleSdkShared)
        if let sharedName = PolarFirstTimeUseRuntimePlanner.typicalDayName(value: Int32(value.rawValue)) {
            switch sharedName {
            case "MOSTLY_SITTING": return .mostlySitting
            case "MOSTLY_STANDING": return .mostlyStanding
            case "MOSTLY_MOVING": return .mostlyMoving
            default: break
            }
        }
        #endif
        switch value {
        case .mostlySitting:
            return .mostlySitting
        case .mostlyStanding:
            return .mostlyStanding
        case .mostlyMoving:
            return .mostlyMoving
        }
    }

    static func physicalTrainingBackgroundValue(from value: Data_PbUserTrainingBackground.TrainingBackground) -> Int {
        #if canImport(PolarBleSdkShared)
        if let sharedName = PolarFirstTimeUseRuntimePlanner.trainingBackgroundName(value: Int32(value.rawValue)) {
            switch sharedName {
            case "OCCASIONAL": return PolarFirstTimeUseConfig.TrainingBackground.occasional.rawValue
            case "REGULAR": return PolarFirstTimeUseConfig.TrainingBackground.regular.rawValue
            case "FREQUENT": return PolarFirstTimeUseConfig.TrainingBackground.frequent.rawValue
            case "HEAVY": return PolarFirstTimeUseConfig.TrainingBackground.heavy.rawValue
            case "SEMI_PRO": return PolarFirstTimeUseConfig.TrainingBackground.semiPro.rawValue
            case "PRO": return PolarFirstTimeUseConfig.TrainingBackground.pro.rawValue
            default: break
            }
        }
        #endif
        return Int(value.rawValue)
    }
}

enum PolarFirstTimeUseRuntimePlanner {
    static func genderValue(name: String) -> NSNumber? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firstTimeUseGenderValue(name: name)
        #else
        return nil
        #endif
    }

    static func typicalDayValue(value: Int32) -> NSNumber? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firstTimeUseTypicalDayValue(value: value)
        #else
        return nil
        #endif
    }

    static func trainingBackgroundValue(value: Int32) -> NSNumber? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firstTimeUseTrainingBackgroundValue(value: value)
        #else
        return nil
        #endif
    }

    static func genderName(value: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firstTimeUseGenderName(value: value)
        #else
        return nil
        #endif
    }

    static func typicalDayName(value: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firstTimeUseTypicalDayName(value: value)
        #else
        return nil
        #endif
    }

    static func trainingBackgroundName(value: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firstTimeUseTrainingBackgroundName(value: value)
        #else
        return nil
        #endif
    }
}

public struct PolarPhysicalConfiguration: Codable {
    public enum Gender: String, Codable {
        case male
        case female
    }

    public enum TypicalDay: Int, Codable {
        case mostlySitting = 1
        case mostlyStanding = 2
        case mostlyMoving = 3
    }

    public let gender: Gender
    public let birthDate: Date
    public let height: Float
    public let weight: Float
    public let maxHeartRate: Int
    public let vo2Max: Int
    public let restingHeartRate: Int
    public let trainingBackground: Int
    public let deviceTime: String
    public let typicalDay: TypicalDay
    public let sleepGoalMinutes: Int
}
