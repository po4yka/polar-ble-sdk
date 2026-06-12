//  Copyright © 2024 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let NIGHTLY_RECOVERY_DIRECTORY = "NR/"
private let NIGHTLY_RECOVERY_PROTO = "NR.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarNightlyRechargeUtils"

internal class PolarNightlyRechargeUtils {
    enum PolarNightlyRechargeError: Error { case missingOrInvalidRecoveryDate }

    static func nightlyRechargeReadOperation(date: Date) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        let path = nightlyRechargePath(day: dateFormat.string(from: date))
        if let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: "nightly-recharge-read", command: "GET", path: path) {
            return plannedOperation
        }
        return (.get, path)
    }

    private static func nightlyRechargePath(day: String) -> String {
        return PolarNightlyRechargeRuntimePlanner.nightlyRechargePath(day: day) ?? "\(ARABICA_USER_ROOT_FOLDER)\(day)/\(NIGHTLY_RECOVERY_DIRECTORY)\(NIGHTLY_RECOVERY_PROTO)"
    }

    static func readNightlyRechargeData(client: BlePsFtpClient, date: Date) async -> PolarNightlyRechargeData? {
        BleLogger.trace(TAG, "readNightlyRechargeData: \(date)")
        let plannedOperation = nightlyRechargeReadOperation(date: date)
        let filePath = plannedOperation.path
        do {
            let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(plannedOperation))
            let recoveryStatus = try Data_PbNightlyRecoveryStatus(serializedBytes: Data(response))
            guard let recoveryDate = try? PolarTimeUtils.pbDateToDateComponents(pbDate: recoveryStatus.sleepResultDate) else {
                throw PolarNightlyRechargeError.missingOrInvalidRecoveryDate
            }
            let createdTimestamp = try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: recoveryStatus.createdTimestamp)
            let modifiedTimestamp = recoveryStatus.hasModifiedTimestamp ? try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: recoveryStatus.modifiedTimestamp) : nil
            return PolarNightlyRechargeData(
                createdTimestamp: createdTimestamp, modifiedTimestamp: modifiedTimestamp,
                ansStatus: Float(recoveryStatus.ansStatus), recoveryIndicator: Int(recoveryStatus.recoveryIndicator),
                recoveryIndicatorSubLevel: Int(recoveryStatus.recoveryIndicatorSubLevel), ansRate: Int(recoveryStatus.ansRate),
                scoreRateObsolete: Int(recoveryStatus.scoreRateObsolete), meanNightlyRecoveryRRI: Int(recoveryStatus.meanNightlyRecoveryRri),
                meanNightlyRecoveryRMSSD: Int(recoveryStatus.meanNightlyRecoveryRmssd),
                meanNightlyRecoveryRespirationInterval: Int(recoveryStatus.meanNightlyRecoveryRespirationInterval),
                meanBaselineRRI: Int(recoveryStatus.meanBaselineRri), sdBaselineRRI: Int(recoveryStatus.sdBaselineRri),
                meanBaselineRMSSD: Int(recoveryStatus.meanBaselineRmssd), sdBaselineRMSSD: Int(recoveryStatus.sdBaselineRmssd),
                meanBaselineRespirationInterval: Int(recoveryStatus.meanBaselineRespirationInterval),
                sdBaselineRespirationInterval: Int(recoveryStatus.sdBaselineRespirationInterval),
                sleepTip: recoveryStatus.sleepTip, vitalityTip: recoveryStatus.vitalityTip,
                exerciseTip: recoveryStatus.exerciseTip, sleepResultDate: recoveryDate
            )
        } catch {
            BleLogger.error("readNightlyRechargeData() failed for path: \(filePath), error: \(error)")
            return nil
        }
    }
}

enum PolarNightlyRechargeRuntimePlanner {
    static func nightlyRechargePath(day: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.nightlyRechargePath(day: day)
        #else
        return nil
        #endif
    }
}
