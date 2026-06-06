// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarStoredDataOfflineRuntimePlanner {
    @discardableResult
    static func storedDataCleanup(kind: String, rootPath: String, cutoffDate: String? = nil) -> String {
        #if canImport(PolarBleSdkShared)
        if let cutoffDate = cutoffDate {
            return PolarIosSharedBridge.shared.planRuntimeStoredDataCleanupWithCutoff(kind: kind, rootPath: rootPath, cutoffDate: cutoffDate)
        }
        return PolarIosSharedBridge.shared.planRuntimeStoredDataCleanup(kind: kind, rootPath: rootPath)
        #else
        return "platform-owned"
        #endif
    }

    static func storedDataEntryMatchesFilter(entry: String, includePrefixes: [String] = [], includeSuffixes: [String] = []) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.storedDataEntryMatchesFilter(
            entry: entry,
            includePrefixesCsv: includePrefixes.joined(separator: ","),
            includeSuffixesCsv: includeSuffixes.joined(separator: ",")
        )
        #else
        return nil
        #endif
    }

    static func shouldPruneStoredDataEmptyParents(dataType: String) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.shouldPruneStoredDataEmptyParents(dataType: dataType)
        #else
        return nil
        #endif
    }

    static func storedDataDateIsOnOrBefore(day: String, cutoffDate: String) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.storedDataDateIsOnOrBefore(day: day, cutoffDate: cutoffDate)
        #else
        return nil
        #endif
    }

    static func storedDataEmptyParentDirectories(filePath: String, rootPath: String = "/U/0", trailingSlash: Bool = true) -> [String]? {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.storedDataEmptyParentDirectories(filePath: filePath, rootPath: rootPath, trailingSlash: trailingSlash)
        return csv.isEmpty ? [] : csv.split(separator: ",").map(String.init)
        #else
        return nil
        #endif
    }

    @discardableResult
    static func offlineTriggerSet(currentTypes: [String], desiredTypes: [String], secretPresent: Bool) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeOfflineTrigger(operation: "setOfflineRecordingTrigger", currentTypesCsv: currentTypes.joined(separator: ","), desiredTypesCsv: desiredTypes.joined(separator: ","), secretPresent: secretPresent)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func offlineTriggerGet(currentTypes: [String]) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeOfflineTrigger(operation: "getOfflineRecordingTriggerSetup", currentTypesCsv: currentTypes.joined(separator: ","), desiredTypesCsv: "", secretPresent: false)
        #else
        return "platform-owned"
        #endif
    }
}
