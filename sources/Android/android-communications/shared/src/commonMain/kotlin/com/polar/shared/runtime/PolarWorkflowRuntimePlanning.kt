package com.polar.shared.runtime

import com.polar.shared.sdk.PolarFirmwareUpdateModels

data class PolarStoredDataCleanupDateFolder(
    val path: String,
    val beforeCutoff: Boolean,
    val removeFiles: List<String>,
    val pruneEmptyParents: Boolean
)

data class PolarStoredDataCleanupSampleFile(
    val path: String,
    val embeddedDay: String
)

data class PolarStoredDataCleanupScenario(
    val id: String,
    val kind: String,
    val rootPath: String? = null,
    val includePrefixes: List<String> = emptyList(),
    val includeSuffixes: List<String> = emptyList(),
    val entries: List<String> = emptyList(),
    val cutoffDate: String? = null,
    val dateFolders: List<PolarStoredDataCleanupDateFolder> = emptyList(),
    val sampleFiles: List<PolarStoredDataCleanupSampleFile> = emptyList()
)

data class PolarWorkflowPlan(
    val commands: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val writes: List<String> = emptyList(),
    val terminal: String = "success",
    val terminalError: String? = null,
    val downloadAttempted: Boolean = false,
    val zipExtractionAttempted: Boolean = false,
    val cleanupCallbackCount: Int = 0,
    val enabledFeatures: List<String> = emptyList(),
    val excludedFeatures: List<String> = emptyList()
)

data class PolarOfflineTriggerDeviceTrigger(
    val type: String,
    val status: String
)

data class PolarOfflineTriggerDesiredFeature(
    val type: String,
    val hasSelectedSettings: Boolean
)

data class PolarOfflineTriggerTransport(
    val setMode: String = "success",
    val getStatus: String = "success",
    val setSettings: String = "success"
)

data class PolarFirmwareWorkflowScenario(
    val id: String,
    val expectedStatuses: List<String> = emptyList(),
    val expectedTerminalStatus: String? = null,
    val expectedTerminalError: String? = null,
    val downloadAttempted: Boolean = false,
    val zipExtractionAttempted: Boolean = false,
    val expectedCleanupCallbackCount: Int = 0,
    val expectedWrites: List<String> = emptyList(),
    val expectedStatusOrder: List<String> = emptyList(),
    val firmwareFiles: List<String> = emptyList(),
    val writeTerminalError: String? = null
)

data class PolarBackupFile(
    val path: String,
    val dataHex: String
)

data class PolarBackupRestoreFile(
    val directory: String,
    val fileName: String,
    val dataHex: String,
    val writeResult: String = "success"
)

data class PolarPsFtpFrame(
    val next: Int,
    val status: Int,
    val sequenceNumber: Int,
    val payload: ByteArray?,
    val androidErrorCode: Int?,
    val iosErrorCode: Int?
)

data class PolarPsFtpNotificationPacket(
    val frame: ByteArray,
    val transportStatus: Int
)

data class PolarPsFtpNotification(
    val id: Int,
    val parameters: ByteArray
)

class PolarPsFtpResponseError(val errorCode: Int) : IllegalStateException("PSFTP response error $errorCode")
class PolarPsFtpContinuationTimeout(val caseId: String) : IllegalStateException("PSFTP continuation timeout $caseId")
class PolarPsFtpTransportWriteFailure(message: String) : IllegalStateException(message)
class PolarPsFtpWriteAckTimeout(val point: String) : IllegalStateException("PSFTP write acknowledgement timeout at $point")

object PolarWorkflowRuntimePlanning {
    fun planStoredDataCleanup(scenario: PolarStoredDataCleanupScenario): PolarWorkflowPlan {
        return when (scenario.kind) {
            "filterDirectoryEntries" -> PolarWorkflowPlan(commands = cleanupFilterDirectoryEntries(scenario))
            "activityPrune" -> PolarWorkflowPlan(commands = cleanupActivityPrune(scenario), terminal = "platform-path-split")
            "automaticSamplePrune" -> PolarWorkflowPlan(commands = cleanupAutomaticSamplePrune(scenario))
            "emptyDayFolderRemoval" -> PolarWorkflowPlan(commands = listOf("REMOVE:${requireNotNull(scenario.rootPath).trimEnd('/')}"), terminal = "platform-path-split")
            "listFailure" -> PolarWorkflowPlan(commands = listOf("GET:${requireNotNull(scenario.rootPath)}"), terminal = "platform-split")
            else -> error("Unsupported cleanup scenario ${scenario.kind}")
        }
    }

    fun storedDataEntryMatchesFilter(entry: String, includePrefixes: List<String> = emptyList(), includeSuffixes: List<String> = emptyList()): Boolean {
        return (includePrefixes.isEmpty() || includePrefixes.any { prefix -> entry.startsWith(prefix) }) &&
            (includeSuffixes.isEmpty() || includeSuffixes.any { suffix -> entry.endsWith(suffix) })
    }

    fun shouldPruneStoredDataEmptyParents(dataType: String): Boolean {
        return dataType !in setOf("AUTOS", "SDLOGS", "UNDEFINED")
    }

    fun storedDataEmptyParentDirectories(filePath: String, rootPath: String = "/U/0", trailingSlash: Boolean = false): List<String> {
        val normalizedRoot = rootPath.trimEnd('/')
        val directories = mutableListOf<String>()
        var currentDir = filePath.substringBeforeLast("/", missingDelimiterValue = "")
        while (currentDir.isNotEmpty() && currentDir.trimEnd('/') != normalizedRoot) {
            directories += if (trailingSlash) currentDir.withTrailingSlash() else currentDir.trimEnd('/')
            currentDir = currentDir.trimEnd('/').substringBeforeLast("/", missingDelimiterValue = "")
        }
        return directories
    }

    fun planOfflineTriggerRuntime(
        operation: String,
        currentDeviceTriggers: List<PolarOfflineTriggerDeviceTrigger>,
        desiredMode: String = "TRIGGER_SYSTEM_START",
        desiredFeatures: List<PolarOfflineTriggerDesiredFeature> = emptyList(),
        secretPresent: Boolean = false,
        transport: PolarOfflineTriggerTransport = PolarOfflineTriggerTransport()
    ): PolarWorkflowPlan {
        return when (operation) {
            "setOfflineRecordingTrigger" -> planSetOfflineTrigger(currentDeviceTriggers, desiredMode, desiredFeatures, secretPresent, transport)
            "getOfflineRecordingTriggerSetup" -> planGetOfflineTrigger(currentDeviceTriggers, transport)
            else -> error("Unsupported offline trigger operation $operation")
        }
    }

    fun planFirmwareWorkflow(scenario: PolarFirmwareWorkflowScenario): PolarWorkflowPlan {
        return when (scenario.id) {
            "check-update-not-available",
            "check-update-available" -> PolarWorkflowPlan(statuses = scenario.expectedStatuses)
            "download-failure" -> PolarWorkflowPlan(statuses = listOf("fetchingFwUpdatePackage", requireNotNull(scenario.expectedTerminalStatus)), downloadAttempted = true)
            "retryable-server-failure" -> PolarWorkflowPlan(statuses = scenario.expectedStatuses, terminalError = scenario.expectedTerminalError, downloadAttempted = scenario.downloadAttempted)
            "empty-or-invalid-zip" -> PolarWorkflowPlan(statuses = listOf("fetchingFwUpdatePackage", requireNotNull(scenario.expectedTerminalStatus)), downloadAttempted = true, zipExtractionAttempted = true)
            "cancel-after-package-fetch-cleans-up-before-ble-write" -> PolarWorkflowPlan(statuses = scenario.expectedStatuses, writes = scenario.expectedWrites, terminalError = scenario.expectedTerminalError, downloadAttempted = true, zipExtractionAttempted = true, cleanupCallbackCount = scenario.expectedCleanupCallbackCount)
            "write-package-success-with-system-update-last" -> PolarWorkflowPlan(statuses = scenario.expectedStatusOrder, writes = orderFirmwareFiles(scenario.firmwareFiles).map { "/$it" })
            "system-update-reboot-response-is-success" -> PolarWorkflowPlan(statuses = listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", requireNotNull(scenario.expectedTerminalStatus)), writes = orderFirmwareFiles(scenario.firmwareFiles).map { "/$it" })
            "battery-too-low-response-is-terminal-failure" -> PolarWorkflowPlan(statuses = listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "fwUpdateFailed"), writes = orderFirmwareFiles(scenario.firmwareFiles).map { "/$it" }, terminalError = "battery-too-low")
            else -> error("Unsupported firmware workflow scenario ${scenario.id}")
        }
    }

    fun orderFirmwareFiles(fileNames: List<String>): List<String> {
        return PolarFirmwareUpdateModels.orderFirmwareFiles(fileNames)
    }

    fun expandBackupEntries(backupText: String, availablePaths: List<String>): List<String> {
        return backupText.split("\n").filter { it.isNotEmpty() }.flatMap { path ->
            if (path.endsWith("/")) {
                availablePaths.filter { available -> available.startsWith(path) && available != path }
            } else {
                listOf(path)
            }
        }
    }

    fun defaultBackupPaths(): List<String> = listOf(
        "/U/0/S/PHYSDATA.BPB",
        "/U/0/S/UDEVSET.BPB",
        "/U/0/S/PREFS.BPB",
        "/U/0/USERID.BPB"
    )

    fun readBackupFiles(paths: List<String>, filesByPath: Map<String, String>): List<PolarBackupFile> {
        return paths.map { path -> PolarBackupFile(path, filesByPath.getValue(path)) }
    }

    fun planBackupRestore(restoreFiles: List<PolarBackupRestoreFile>): PolarWorkflowPlan {
        val writes = restoreFiles.map { file -> file.directory + file.fileName }
        val failures = restoreFiles.filter { file -> file.writeResult != "success" }.map { file -> file.directory + file.fileName }
        return PolarWorkflowPlan(writes = writes, commands = writes.mapIndexed { index, path -> "PUT:$path:${restoreFiles[index].dataHex}" }, terminal = if (failures.isEmpty()) "success" else "platform-split", terminalError = failures.joinToString(",").ifEmpty { null })
    }

    fun decodeRfc76Frame(frame: ByteArray): PolarPsFtpFrame {
        val header = frame.first().toInt() and 0xff
        val status = (header shr 1) and 0x03
        val payload = frame.drop(1).toByteArray()
        val errorCode = if (status == RFC76_STATUS_ERROR_OR_RESPONSE && payload.size >= 2) {
            (payload[0].toInt() and 0xff) or ((payload[1].toInt() and 0xff) shl 8)
        } else {
            null
        }
        return PolarPsFtpFrame(
            next = header and 0x01,
            status = status,
            sequenceNumber = (header shr 4) and 0x0f,
            payload = if (status == RFC76_STATUS_ERROR_OR_RESPONSE) null else payload,
            androidErrorCode = errorCode?.let { payload[0].toInt() and 0xff },
            iosErrorCode = errorCode
        )
    }

    fun encodeCompleteMessageStream(type: String, header: ByteArray, idValue: Int, data: ByteArray = ByteArray(0)): ByteArray {
        return when (type) {
            "request" -> byteArrayOf((header.size and 0xff).toByte(), ((header.size shr 8) and 0xff).toByte()) + header + data
            "query" -> byteArrayOf((idValue and 0xff).toByte(), (((idValue shr 8) and 0x7f) or 0x80).toByte()) + header
            "notification" -> byteArrayOf((idValue and 0xff).toByte()) + header
            else -> error("Unsupported PSFTP complete message stream type $type")
        }
    }

    fun splitRfc76Frames(payload: ByteArray, mtu: Int): List<ByteArray> {
        require(mtu > 1) { "MTU must leave room for an RFC76 header byte" }
        val payloadSize = mtu - 1
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        var sequenceNumber = 0
        do {
            val end = minOf(offset + payloadSize, payload.size)
            val chunk = payload.copyOfRange(offset, end)
            val hasMore = end < payload.size
            val next = if (sequenceNumber == 0) 0 else 1
            val status = if (hasMore) RFC76_STATUS_MORE else RFC76_STATUS_LAST
            val header = (sequenceNumber shl 4) or (status shl 1) or next
            frames += byteArrayOf(header.toByte()) + chunk
            offset = end
            sequenceNumber = (sequenceNumber + 1) and 0x0f
        } while (offset < payload.size || frames.isEmpty())
        return frames
    }

    fun reassembleRequestResponse(responseFrames: List<ByteArray>): ByteArray {
        val payload = mutableListOf<Byte>()
        responseFrames.forEach { frame ->
            val decoded = decodeRfc76Frame(frame)
            if (decoded.status == RFC76_STATUS_ERROR_OR_RESPONSE) {
                throw PolarPsFtpResponseError(requireNotNull(decoded.iosErrorCode))
            }
            payload += requireNotNull(decoded.payload).toList()
        }
        return payload.toByteArray()
    }

    fun reassembleNotifications(packets: List<PolarPsFtpNotificationPacket>): List<PolarPsFtpNotification> {
        var waitingForLastFrame = false
        val notifications = mutableListOf<PolarPsFtpNotification>()
        val payload = mutableListOf<Byte>()
        packets.forEachIndexed { index, packet ->
            if (packet.transportStatus != 0) {
                return@forEachIndexed
            }
            val decoded = decodeRfc76Frame(packet.frame)
            if (decoded.status == RFC76_STATUS_MORE) {
                waitingForLastFrame = true
                payload += requireNotNull(decoded.payload).toList()
            }
            if (decoded.status == RFC76_STATUS_LAST) {
                payload += requireNotNull(decoded.payload).toList()
                val payloadBytes = payload.toByteArray()
                notifications += PolarPsFtpNotification(
                    id = payloadBytes.first().toInt() and 0xff,
                    parameters = payloadBytes.drop(1).toByteArray()
                )
                payload.clear()
                waitingForLastFrame = false
            }
            if (index == packets.lastIndex && waitingForLastFrame) {
                throw PolarPsFtpContinuationTimeout("missing-last-frame-after-more")
            }
        }
        return notifications
    }

    fun planPsFtpWrite(payload: ByteArray, transportTransmit: String = "success", writeAck: String = "success", failureMessage: String = "transport write failure"): PolarWorkflowPlan {
        require(payload.isNotEmpty()) { "payload must not be empty" }
        if (transportTransmit != "success") {
            throw PolarPsFtpTransportWriteFailure(failureMessage)
        }
        if (writeAck == "never") {
            throw PolarPsFtpWriteAckTimeout("firstMtuWriteAck")
        }
        return PolarWorkflowPlan(commands = listOf("write:${payload.size}"), terminal = "success")
    }

    fun planPsFtpWriteProgress(payloadSize: Int, platform: String): List<Int> {
        return when (platform) {
            "android" -> listOf(-14, payloadSize)
            "ios" -> listOf(0, payloadSize - 1, payloadSize)
            else -> error("Unsupported PSFTP progress platform $platform")
        }
    }

    fun planConsumerTimeoutObserverCleanup(timeoutMs: Int, advanceMs: Int): PolarWorkflowPlan {
        return if (advanceMs >= timeoutMs) {
            PolarWorkflowPlan(commands = listOf("consumer-timeout:$timeoutMs", "cleanup-observer"), terminal = "timeout")
        } else {
            PolarWorkflowPlan(commands = listOf("consumer-timeout:$timeoutMs"), terminal = "waiting")
        }
    }

    private fun cleanupFilterDirectoryEntries(scenario: PolarStoredDataCleanupScenario): List<String> {
        val root = requireNotNull(scenario.rootPath)
        val commands = mutableListOf("GET:$root")
        scenario.entries
            .filter { entry -> storedDataEntryMatchesFilter(entry, scenario.includePrefixes, scenario.includeSuffixes) }
            .mapTo(commands) { entry -> "REMOVE:${root.withTrailingSlash()}$entry" }
        return commands
    }

    private fun cleanupActivityPrune(scenario: PolarStoredDataCleanupScenario): List<String> {
        val commands = mutableListOf("GET:/U/0/")
        scenario.dateFolders
            .filter { folder -> folder.beforeCutoff }
            .forEach { folder ->
                val folderPath = folder.path
                val actPath = "${folderPath}ACT/"
                commands += "GET:$folderPath"
                commands += "GET:$actPath"
                folder.removeFiles.forEach { fileName -> commands += "REMOVE:$actPath$fileName" }
                if (folder.pruneEmptyParents) {
                    commands += "GET:$actPath"
                    commands += "REMOVE_EMPTY_DIRECTORY:${actPath.removeSuffix("/")}"
                    commands += "GET:$folderPath"
                    commands += "REMOVE_EMPTY_DIRECTORY:${folderPath.removeSuffix("/")}"
                }
            }
        return commands
    }

    private fun cleanupAutomaticSamplePrune(scenario: PolarStoredDataCleanupScenario): List<String> {
        val cutoff = requireNotNull(scenario.cutoffDate)
        val commands = mutableListOf("GET:/U/0/AUTOS/")
        scenario.sampleFiles.forEach { sample ->
            val parent = sample.path.substringBeforeLast('/') + "/"
            commands += "GET:$parent"
            commands += "GET:${sample.path}"
            if (sample.embeddedDay < cutoff) {
                commands += "REMOVE:${sample.path}"
            }
        }
        return commands
    }

    private fun planSetOfflineTrigger(
        currentDeviceTriggers: List<PolarOfflineTriggerDeviceTrigger>,
        desiredMode: String,
        desiredFeatures: List<PolarOfflineTriggerDesiredFeature>,
        secretPresent: Boolean,
        transport: PolarOfflineTriggerTransport
    ): PolarWorkflowPlan {
        val operations = mutableListOf("setMode:$desiredMode")
        if (transport.setMode != "success") {
            return PolarWorkflowPlan(commands = operations, terminal = triggerTerminal(transport.setMode))
        }
        operations += "getStatus"
        if (transport.getStatus != "success") {
            return PolarWorkflowPlan(commands = operations, terminal = triggerTerminal(transport.getStatus))
        }
        val desiredByType = desiredFeatures.associateBy { it.type }
        currentDeviceTriggers.forEach { current ->
            val desired = desiredByType[current.type] ?: desiredByType[current.type.toPublicFeature()]
            operations += if (desired != null) {
                "setSetting:${current.type}:enabled:${if (desired.hasSelectedSettings) "settings" else "no-settings"}:${if (secretPresent) "secret" else "no-secret"}"
            } else {
                "setSetting:${current.type}:disabled"
            }
            if (transport.setSettings != "success") {
                return PolarWorkflowPlan(commands = operations, terminal = triggerTerminal(transport.setSettings))
            }
        }
        return PolarWorkflowPlan(commands = operations)
    }

    private fun planGetOfflineTrigger(
        currentDeviceTriggers: List<PolarOfflineTriggerDeviceTrigger>,
        transport: PolarOfflineTriggerTransport
    ): PolarWorkflowPlan {
        val operations = listOf("getStatus")
        if (transport.getStatus != "success") {
            return PolarWorkflowPlan(commands = operations, terminal = triggerTerminal(transport.getStatus))
        }
        return PolarWorkflowPlan(
            commands = operations,
            enabledFeatures = currentDeviceTriggers.filter { trigger -> trigger.status == "enabled" && trigger.type != "GYRO" }.map { it.type.toPublicFeature() },
            excludedFeatures = listOf("GYRO")
        )
    }

    private fun triggerTerminal(result: String): String {
        return when (result) {
            "success" -> "success"
            "controlPointError" -> "control-point-error"
            "transportError" -> "transport-error"
            else -> error("Unsupported trigger transport result $result")
        }
    }

    private fun String.toPublicFeature(): String = if (this == "OFFLINE_HR") "HR" else this

    private fun String.withTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    private const val RFC76_STATUS_ERROR_OR_RESPONSE = 0
    private const val RFC76_STATUS_MORE = 3
    private const val RFC76_STATUS_LAST = 1
}
