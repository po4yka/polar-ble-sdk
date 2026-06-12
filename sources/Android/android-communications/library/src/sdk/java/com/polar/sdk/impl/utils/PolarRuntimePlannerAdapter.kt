package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.shared.ble.PolarAdvertisementModels
import com.polar.shared.ble.PolarAtomicSet
import com.polar.shared.ble.PolarTypeUtils
import com.polar.shared.runtime.PolarDiskTimeOperation
import com.polar.shared.runtime.PolarFacadeCommandOperation
import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarFileRuntimeErrorOperation
import com.polar.shared.runtime.PolarH10StartRecordingFields
import com.polar.shared.runtime.PolarRestFacadeOperation
import com.polar.shared.runtime.PolarRestRequestTransportOperation
import com.polar.shared.runtime.PolarResetNotificationFields
import com.polar.shared.runtime.PolarRuntimePlan
import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarSyncStopNotificationFields
import com.polar.shared.runtime.PolarUserDeviceSettingsOperation
import com.polar.shared.runtime.PolarBackupRestoreFile
import com.polar.shared.runtime.PolarD2hRuntimePlanning
import com.polar.shared.runtime.PolarFirmwareWorkflowScenario
import com.polar.shared.runtime.PolarOfflineTriggerDesiredFeature
import com.polar.shared.runtime.PolarOfflineTriggerDeviceTrigger
import com.polar.shared.runtime.PolarOfflineTriggerTransport
import com.polar.shared.runtime.PolarStoredDataCleanupScenario
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import com.polar.shared.device.PolarDeviceId
import com.polar.shared.pmd.PolarPmdControlPoint
import com.polar.shared.pmd.PolarPmdMeasurementTypeName
import com.polar.shared.pmd.PolarPmdRecordingType
import com.polar.shared.pmd.PolarPmdSecret
import com.polar.shared.pmd.sensors.PolarGnssCoordinateSample
import com.polar.shared.pmd.sensors.PolarGnssLocationSample
import com.polar.shared.pmd.sensors.PolarGnssNmeaSample
import com.polar.shared.pmd.sensors.PolarGnssSatelliteDilutionSample
import com.polar.shared.pmd.sensors.PolarGnssSatelliteSummary
import com.polar.shared.pmd.sensors.PolarGnssSatelliteSummarySample
import com.polar.shared.pmd.sensors.PolarLocationCoordinatesProjectionSample
import com.polar.shared.pmd.sensors.PolarLocationDataProjection
import com.polar.shared.pmd.sensors.PolarLocationDataProjectionSample
import com.polar.shared.pmd.sensors.PolarLocationNmeaProjectionSample
import com.polar.shared.pmd.sensors.PolarLocationSatelliteDilutionProjectionSample
import com.polar.shared.pmd.sensors.PolarLocationSatelliteSummaryProjection
import com.polar.shared.pmd.sensors.PolarLocationSatelliteSummaryProjectionSample
import com.polar.shared.sdk.PolarActivityModels
import com.polar.shared.sdk.PolarFirmwareUpdateModels
import com.polar.shared.sdk.PolarKvtxScriptCodec
import com.polar.shared.sdk.PolarOfflineRecordingFileEntry
import com.polar.shared.sdk.PolarOfflineRecordingModels
import com.polar.shared.sdk.PolarRestServiceModels
import com.polar.shared.sdk.PolarSdkFeatureAvailability
import com.polar.shared.sdk.PolarSdkModelMappers
import com.polar.shared.sdk.PolarSleepModels
import com.polar.shared.sdk.PolarSpo2Models
import com.polar.shared.sdk.PolarTrainingPayloadFields
import com.polar.shared.sdk.PolarTrainingPayloadResponse
import com.polar.shared.sdk.PolarTrainingSessionFileEntry
import com.polar.shared.sdk.PolarTrainingSessionModels
import com.polar.shared.sdk.PolarTrainingSessionReference
import com.polar.shared.sdk.PolarUserDeviceSettingsModels
import com.polar.shared.sdk.PolarWatchFaceConfigFlatBuffer
import com.polar.shared.sdk.PolarWatchFaceFields
import com.polar.shared.time.PolarDateFields
import com.polar.shared.time.PolarDateTimeFields
import com.polar.shared.time.PolarDurationFields
import com.polar.shared.time.PolarTimeFields
import com.polar.shared.time.PolarTimeUtils
import protocol.PftpNotification
import protocol.PftpRequest

internal object PolarRuntimePlannerAdapter {
    data class PlannedFirmwareWorkflow(
        val statuses: List<String>,
        val writes: List<String>,
        val terminal: String,
        val terminalError: String?,
        val retryDelaysMillis: List<Long>,
        val cleanupCallbackCount: Int = 0
    )
    data class PlannedOfflineTriggerRuntime(
        val commands: List<String>,
        val enabledFeatures: List<String>,
        val excludedFeatures: List<String>,
        val terminal: String
    )
    data class PlannedOfflineTriggerDeviceTrigger(
        val type: String,
        val enabled: Boolean
    )
    data class PlannedOfflineTriggerDesiredFeature(
        val type: String,
        val hasSelectedSettings: Boolean
    )
    data class PlannedBackupRestoreWrite(
        val operation: Pair<PftpRequest.PbPFtpOperation.Command, String>,
        val payloadHex: String
    )
    data class PlannedBackupRestoreFile(
        val directory: String,
        val fileName: String,
        val dataHex: String,
        val writeResult: String = "success"
    )
    data class PlannedPmdControlPointResponse(
        val responseCode: Int,
        val opCodeValue: Int,
        val measurementType: Int,
        val statusValue: Int,
        val more: Boolean,
        val parameters: ByteArray
    )
    data class BackupTraversalPlan(
        val path: String,
        val wildcardRootPath: String?,
        val wildcardSubFolder: String?
    )
    data class PlannedD2hNotification(
        val notificationType: String,
        val parsedProtoName: String?
    )
    data class PlannedWatchFaceFields(
        val timeStyleId: Int,
        val complicationLayoutId: Int,
        val backgroundStyleId: Int,
        val accentColor: Long,
        val complicationIds: List<Int>,
        val fontfaceId: Int
    )
    data class PlannedOfflineRecordingEntry(
        val type: String,
        val androidPath: String,
        val size: Long,
        val dateTime: String
    )
    data class PlannedSpo2TestProjection(
        val recordingDevice: String?,
        val timeZoneOffsetMinutes: Int,
        val testStatus: String?,
        val bloodOxygenPercent: Int?,
        val spo2Class: String?,
        val spo2ValueDeviationFromBaseline: String?,
        val spo2QualityAveragePercent: Float?,
        val averageHeartRateBpm: Int?,
        val heartRateVariabilityMs: Float?,
        val spo2HrvDeviationFromBaseline: String?,
        val altitudeMeters: Float?
    )
    data class PlannedGnssSatelliteSummary(
        val gpsNbrOfSat: UByte,
        val gpsMaxSnr: UByte,
        val glonassNbrOfSat: UByte,
        val glonassMaxSnr: UByte,
        val galileoNbrOfSat: UByte,
        val galileoMaxSnr: UByte,
        val beidouNbrOfSat: UByte,
        val beidouMaxSnr: UByte,
        val nbrOfSat: UByte,
        val snrTop5Avg: UByte
    )
    sealed class PlannedGnssLocationSample {
        abstract val timeStamp: ULong
    }
    data class PlannedGnssCoordinateSample(
        override val timeStamp: ULong,
        val latitude: Double,
        val longitude: Double,
        val date: String,
        val cumulativeDistance: Double,
        val speed: Float,
        val usedAccelerationSpeed: Float,
        val coordinateSpeed: Float,
        val accelerationSpeedFactor: Float,
        val course: Float,
        val gpsChipSpeed: Float,
        val fix: Boolean,
        val speedFlag: Int,
        val fusionState: UInt
    ) : PlannedGnssLocationSample()
    data class PlannedGnssNmeaSample(
        override val timeStamp: ULong,
        val measurementPeriod: UInt,
        val messageLength: UInt,
        val statusFlags: UByte,
        val nmeaMessage: String
    ) : PlannedGnssLocationSample()
    data class PlannedGnssSatelliteDilutionSample(
        override val timeStamp: ULong,
        val dilution: Float,
        val altitude: Int,
        val numberOfSatellites: UInt,
        val fix: Boolean
    ) : PlannedGnssLocationSample()
    data class PlannedGnssSatelliteSummarySample(
        override val timeStamp: ULong,
        val seenGnssSatelliteSummaryBand1: PlannedGnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand1: PlannedGnssSatelliteSummary,
        val seenGnssSatelliteSummaryBand2: PlannedGnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand2: PlannedGnssSatelliteSummary,
        val maxSnr: UInt
    ) : PlannedGnssLocationSample()
    data class PlannedLocationSatelliteSummary(
        val gpsNbrOfSat: UByte,
        val gpsMaxSnr: UByte,
        val glonassNbrOfSat: UByte,
        val glonassMaxSnr: UByte,
        val galileoNbrOfSat: UByte,
        val galileoMaxSnr: UByte,
        val beidouNbrOfSat: UByte,
        val beidouMaxSnr: UByte,
        val nbrOfSat: UByte,
        val snrTop5Avg: UByte
    )
    sealed class PlannedLocationDataProjectionSample {
        abstract val timeStamp: Long
    }
    data class PlannedLocationCoordinatesProjectionSample(
        override val timeStamp: Long,
        val latitude: Double,
        val longitude: Double,
        val time: String,
        val cumulativeDistance: Double,
        val speed: Float,
        val usedAccelerationSpeed: Float,
        val coordinateSpeed: Float,
        val accelerationSpeedFactor: Float,
        val course: Float,
        val gpsChipSpeed: Float,
        val fix: Boolean,
        val speedFlag: Int,
        val fusionState: UInt
    ) : PlannedLocationDataProjectionSample()
    data class PlannedLocationNmeaProjectionSample(
        override val timeStamp: Long,
        val measurementPeriod: UInt,
        val statusFlags: UByte,
        val nmeaMessage: String
    ) : PlannedLocationDataProjectionSample()
    data class PlannedLocationSatelliteDilutionProjectionSample(
        override val timeStamp: Long,
        val dilution: Float,
        val altitude: Int,
        val numberOfSatellites: UInt,
        val fix: Boolean
    ) : PlannedLocationDataProjectionSample()
    data class PlannedLocationSatelliteSummaryProjectionSample(
        override val timeStamp: Long,
        val seenSatelliteSummaryBand1: PlannedLocationSatelliteSummary,
        val usedSatelliteSummaryBand1: PlannedLocationSatelliteSummary,
        val seenSatelliteSummaryBand2: PlannedLocationSatelliteSummary,
        val usedSatelliteSummaryBand2: PlannedLocationSatelliteSummary,
        val maxSnr: UInt
    ) : PlannedLocationDataProjectionSample()
    data class PlannedDateFields(
        val year: Int,
        val month: Int,
        val day: Int
    )
    data class PlannedTimeFields(
        val hour: Int,
        val minute: Int,
        val second: Int,
        val millis: Int
    )
    data class PlannedDateTimeFields(
        val date: PlannedDateFields,
        val time: PlannedTimeFields,
        val timeZoneOffsetMinutes: Int? = null,
        val trusted: Boolean = false
    )
    data class PlannedTrainingSessionFileEntry(
        val path: String,
        val size: Long
    )
    data class PlannedTrainingExerciseReference(
        val index: Int,
        val androidPath: String,
        val iosPath: String,
        val exerciseDataTypes: List<String>,
        val fileSizes: Map<String, Long>
    )
    data class PlannedTrainingSessionReference(
        val dateTime: String,
        val date: String,
        val path: String,
        val trainingDataTypes: List<String>,
        val exercises: List<PlannedTrainingExerciseReference>,
        val fileSize: Long
    )
    data class PlannedTrainingPayloadReadPlanEntry(
        val path: String,
        val fileName: String,
        val publicModelSlot: String,
        val exerciseIndex: Int?
    )
    data class PlannedTrainingPayloadResponse(
        val kind: String,
        val fileName: String,
        val byteSize: Int,
        val malformed: Boolean = false,
        val modelName: String? = null,
        val durationSeconds: Int? = null,
        val distanceMeters: Int? = null,
        val calories: Int? = null,
        val heartRateSamples: List<Int> = emptyList(),
        val intervalledSampleLists: List<PlannedTrainingIntervalledSampleList> = emptyList()
    )
    data class PlannedTrainingIntervalledSampleList(
        val sampleType: String,
        val heartRateSamples: List<Int> = emptyList()
    )
    data class PlannedTrainingPayloadReadResult(
        val totalBytes: Int,
        val completedBytes: Int,
        val progressPercent: Int,
        val currentFileName: String,
        val modelName: String,
        val durationSeconds: Int,
        val distanceMeters: Int,
        val calories: Int,
        val exercises: List<PlannedTrainingPayloadExercise>
    )
    data class PlannedTrainingPayloadExercise(
        val index: Int,
        val exerciseSummaryPresent: Boolean,
        val routePresent: Boolean,
        val routeAdvancedPresent: Boolean,
        val samplesHeartRate: List<Int>,
        val samplesAdvancedHeartRate: List<Int>,
        val unknownAdvancedSampleListsIgnored: Int,
        val malformedFilesIgnored: List<String>
    )
    class PlannedAtomicSet<T : Any> {
        private val items: PolarAtomicSet<T> = PolarAtomicSet()

        fun clear() {
            items.clear()
        }

        fun add(item: T?): Boolean {
            return items.add(item)
        }

        fun remove(item: T?) {
            items.remove(item)
        }

        fun accessAll(visitor: (T) -> Unit) {
            items.accessAll(visitor)
        }

        fun fetch(predicate: (T) -> Boolean): T? {
            return items.fetch(predicate)
        }

        fun objects(): Set<T> {
            return items.objects()
        }

        fun size(): Int {
            return items.size()
        }

        fun contains(item: T): Boolean {
            return items.contains(item)
        }
    }

    fun availableOfflineRecordingDataTypes(features: Set<PmdMeasurementType>): Set<PolarDeviceDataType> {
        return PolarSdkModelMappers.availableOfflineRecordingDataTypeNames(features.sharedMeasurementTypeNames())
            .mapToPolarDeviceDataTypes()
    }

    fun availableOnlineStreamDataTypes(features: Set<PmdMeasurementType>, hasHrService: Boolean): Set<PolarDeviceDataType> {
        return PolarSdkModelMappers.availableOnlineStreamDataTypeNames(
            pmdMeasurementTypeNames = features.sharedMeasurementTypeNames(),
            hasHrService = hasHrService
        ).mapToPolarDeviceDataTypes()
    }

    fun availableHrServiceDataTypes(hasHrService: Boolean): Set<PolarDeviceDataType> {
        return PolarSdkModelMappers.availableHrServiceDataTypeNames(hasHrService).mapToPolarDeviceDataTypes()
    }

    fun pmdMeasurementTypeNameForPublicDataTypeName(publicDataTypeName: String): String? {
        return PolarSdkModelMappers.pmdMeasurementTypeNameForPublicDataTypeName(publicDataTypeName)
    }

    fun pmdMeasurementTypeNameFromRawValue(value: Int): String? {
        return PolarPmdMeasurementTypeName.fromRawValue(value)?.name
    }

    fun pmdMeasurementTypeNameFromMaskedId(id: Byte): String? {
        return PolarPmdMeasurementTypeName.fromMaskedId(id.toInt())?.name
    }

    fun pmdRecordingTypeBitField(recordingTypeName: String): Int {
        return PolarPmdRecordingType.valueOf(recordingTypeName).asBitField()
    }

    fun pmdActiveMeasurementBits(responseByte: Byte): Int {
        return PolarPmdControlPoint.parseActiveMeasurement(responseByte.toInt() and 0xFF).activeBits
    }

    fun pmdControlPointResponse(data: ByteArray): PlannedPmdControlPointResponse {
        val parsed = PolarPmdControlPoint.parseControlPointResponse(data).response ?: throw IndexOutOfBoundsException("invalidPMDData")
        return PlannedPmdControlPointResponse(
            responseCode = parsed.responseCode,
            opCodeValue = parsed.opCodeValue,
            measurementType = parsed.measurementType,
            statusValue = parsed.statusValue,
            more = parsed.more,
            parameters = parsed.parameters
        )
    }

    fun pmdSecretSerializeBytes(strategyName: String, key: ByteArray): ByteArray {
        return PolarPmdSecret.from(strategyName, key).serializeBytes()
    }

    fun pmdSecretDecryptBytes(strategyName: String, key: ByteArray, cipherBytes: ByteArray): ByteArray? {
        return PolarPmdSecret.from(strategyName, key).decryptBytes(cipherBytes)
    }

    fun pmdSecretStrategyNameFromByte(strategyByte: Int): String? {
        return PolarPmdSecret.strategyNameFromByte(strategyByte)
    }

    fun convertArrayToUnsignedByte(data: ByteArray): UByte {
        return PolarTypeUtils.requireUnsignedByte(data).toUByte()
    }

    fun convertArrayToUnsignedInt(data: ByteArray, offset: Int, length: Int): UInt {
        return PolarTypeUtils.convertArrayToUnsignedInt(data, offset, length).requireValue().toUInt()
    }

    fun convertArrayToUnsignedInt(data: ByteArray): UInt {
        return PolarTypeUtils.requireUnsignedInt(data).toUInt()
    }

    fun convertArrayToUnsignedLong(data: ByteArray, offset: Int, length: Int): ULong {
        return PolarTypeUtils.convertArrayToUnsignedLong(data, offset, length).requireValue().toULong()
    }

    fun convertArrayToUnsignedLong(data: ByteArray): ULong {
        return PolarTypeUtils.requireUnsignedLong(data).toULong()
    }

    fun convertArrayToSignedInt(data: ByteArray, offset: Int, length: Int): Int {
        return convertArrayToSignedInt(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToSignedInt(data: ByteArray): Int {
        var result = PolarTypeUtils.requireUnsignedInt(data).toUInt()
        if (data.last() < 0) {
            val mask: UInt = 0xFFFFFFFFu shl data.size * 8
            result = result or mask
        }
        return result.toInt()
    }

    fun convertUnsignedByteToInt(byte: Byte): Int {
        return PolarTypeUtils.convertUnsignedByteToInt(byte)
    }

    fun locationDataProjection(samples: List<PlannedGnssLocationSample>): List<PlannedLocationDataProjectionSample> {
        return PolarLocationDataProjection.fromGnssSamples(samples.map { it.toShared() }).map { it.toPlanned() }
    }

    fun featureAvailabilityPreconditionsMet(
        featureName: String,
        discoveredServices: Set<String>,
        capabilities: Set<String>
    ): Boolean {
        return PolarSdkFeatureAvailability.preconditionsMet(
            featureName = featureName,
            discoveredServices = discoveredServices,
            capabilities = capabilities
        )
    }

    fun featureAvailabilityServiceNames(
        hasHr: Boolean = false,
        hasDeviceInfo: Boolean = false,
        hasBattery: Boolean = false,
        hasPmd: Boolean = false,
        hasPsftp: Boolean = false,
        hasHts: Boolean = false,
        hasPfc: Boolean = false
    ): Set<String> {
        val names = mutableSetOf<String>()
        if (hasHr) names += PolarSdkFeatureAvailability.SERVICE_HR
        if (hasDeviceInfo) names += PolarSdkFeatureAvailability.SERVICE_DEVICE_INFO
        if (hasBattery) names += PolarSdkFeatureAvailability.SERVICE_BATTERY
        if (hasPmd) names += PolarSdkFeatureAvailability.SERVICE_PMD
        if (hasPsftp) names += PolarSdkFeatureAvailability.SERVICE_PSFTP
        if (hasHts) names += PolarSdkFeatureAvailability.SERVICE_HTS
        if (hasPfc) names += PolarSdkFeatureAvailability.SERVICE_PFC
        return names
    }

    fun featureAvailabilityCapabilityNames(
        recordingSupported: Boolean = false,
        activityDataSupported: Boolean = false,
        firmwareUpdateSupported: Boolean = false,
        h10FileSystem: Boolean = false,
        notSensor: Boolean = false
    ): Set<String> {
        val names = mutableSetOf<String>()
        if (recordingSupported) names += PolarSdkFeatureAvailability.CAPABILITY_RECORDING
        if (activityDataSupported) names += PolarSdkFeatureAvailability.CAPABILITY_ACTIVITY_DATA
        if (firmwareUpdateSupported) names += PolarSdkFeatureAvailability.CAPABILITY_FIRMWARE_UPDATE
        if (h10FileSystem) names += PolarSdkFeatureAvailability.CAPABILITY_H10_FILE_SYSTEM
        if (notSensor) names += PolarSdkFeatureAvailability.CAPABILITY_NOT_SENSOR
        return names
    }

    fun deviceModelNameFromLocalName(localName: String): String {
        return PolarAdvertisementModels.deviceModelNameFromLocalName(localName)
    }

    fun planCommandQuery(id: String, query: String, parameters: List<String> = emptyList()): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "query",
                query = query,
                parameters = parameters,
                notifications = emptyList(),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planCommandReset(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = sleep,
                factoryDefaults = factoryDefaults,
                otaFirmwareUpdate = otaFirmwareUpdate
            )
        )
    }

    fun resetNotificationFields(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean): PolarResetNotificationFields {
        return PolarRuntimeOrchestration.resetNotificationFields(
            PolarFacadeCommandOperation(
                id = id,
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = sleep,
                factoryDefaults = factoryDefaults,
                otaFirmwareUpdate = otaFirmwareUpdate
            )
        )
    }

    fun h10StartRecordingFields(id: String, sampleDataIdentifier: String, sampleType: String, recordingIntervalSeconds: Int): PolarH10StartRecordingFields {
        return PolarRuntimeOrchestration.h10StartRecordingFields(
            PolarFacadeCommandOperation(
                id = id,
                kind = "query",
                query = "REQUEST_START_RECORDING",
                parameters = listOf(
                    "sampleDataIdentifier=$sampleDataIdentifier",
                    "sampleType=$sampleType",
                    "recordingIntervalSeconds=$recordingIntervalSeconds"
                ),
                notifications = emptyList(),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun syncStopNotificationFields(id: String): PolarSyncStopNotificationFields {
        return PolarRuntimeOrchestration.syncStopNotificationFields(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStop",
                query = null,
                parameters = emptyList(),
                notifications = listOf("STOP_SYNC:completed=true", "TERMINATE_SESSION"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planCommandSyncStart(id: String): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStart",
                query = "REQUEST_SYNCHRONIZATION",
                parameters = emptyList(),
                notifications = listOf("INITIALIZE_SESSION", "START_SYNC"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planCommandSyncStop(id: String): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStop",
                query = null,
                parameters = emptyList(),
                notifications = listOf("STOP_SYNC:completed=true", "TERMINATE_SESSION"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planDiskTimeQuery(id: String, query: String): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = id,
                kind = "query",
                query = query,
                queries = emptyList(),
                parameters = emptyList(),
                expectedFields = emptyList()
            )
        )
    }

    fun planSetLocalTimeV2(systemTimeHour: Int, localTimeHour: Int): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-v2",
                kind = "setLocalTimeV2",
                query = null,
                queries = listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("systemTimeHour=$systemTimeHour", "localTimeHour=$localTimeHour", "systemTimeTrusted=true")
            )
        )
    }

    fun planSetLocalTimeH10(localTimeHour: Int): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-h10",
                kind = "setLocalTimeH10",
                query = null,
                queries = listOf("SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("localTimeHour=$localTimeHour")
            )
        )
    }

    fun planRestFacadeGet(id: String, path: String, payloadShape: String? = null): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planRestFacade(
            PolarRestFacadeOperation(
                id = id,
                command = "GET",
                path = path,
                payloadShape = payloadShape,
                expectedFields = emptyList(),
                transportMode = null,
                responseErrorStatus = null,
                responseErrorMessage = null,
                expectedPlatformTerminal = null
            )
        )
    }

    fun planRestRequestTransportGet(path: String, payloadHex: String): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planRestRequestTransport(
            PolarRestRequestTransportOperation(
                id = "platform-rest-request-transport",
                path = path,
                transportMode = "success",
                status = null,
                message = null,
                payloadHex = payloadHex
            )
        )
    }

    fun planFileFacade(id: String, command: String, path: String, payloadHex: String? = null, responseHex: String? = null): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planFileFacade(
            PolarFileFacadeOperation(
                id = id,
                command = command,
                path = path,
                payloadHex = payloadHex,
                responseHex = responseHex,
                progress = emptyList(),
                transportMode = null
            )
        )
    }

    fun normalizeFileListFolderPath(folderPath: String): String {
        return PolarRuntimeOrchestration.normalizeFileListFolderPath(folderPath)
    }

    fun planFileRuntimeError(operation: String, path: String, error: Throwable) {
        PolarRuntimeOrchestration.planFileRuntimeError(
            PolarFileRuntimeErrorOperation(
                id = "platform-file-runtime-error",
                operation = operation,
                path = path,
                payloadHex = null,
                transportMode = "transportError",
                status = null,
                message = null,
                error = error.javaClass.simpleName.ifEmpty { error.toString() },
                responsePayloadHex = null
            )
        )
    }

    fun planUserDeviceSettingsRead(path: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return requireNotNull(
            planUserDeviceSettingsOperations(
                id = "get-user-device-settings",
                kind = "read",
                path = path
            ).firstOrNull()
        ) { "Shared user-device-settings read plan did not produce a PSFTP operation" }
    }

    fun planUserDeviceSettingsReadThenWrite(id: String, path: String, payloadFields: List<String>): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return planUserDeviceSettingsOperations(
            id = id,
            kind = "readThenWrite",
            path = path,
            payloadFields = payloadFields
        )
    }

    fun planUserDeviceSettingsWrite(path: String, payloadFields: List<String>): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return requireNotNull(
            planUserDeviceSettingsOperations(
                id = "set-user-device-settings",
                kind = "write",
                path = path,
                payloadFields = payloadFields
            ).firstOrNull()
        ) { "Shared user-device-settings write plan did not produce a PSFTP operation" }
    }

    fun userDeviceSettingsProtobufPayloadFields(): List<String> {
        return PolarUserDeviceSettingsModels.protobufPayloadFields()
    }

    fun userDeviceSettingsTelemetryPayloadFields(enabled: Boolean): List<String> {
        return PolarUserDeviceSettingsModels.telemetryPayloadFields(enabled)
    }

    fun userDeviceSettingsDeviceLocationPayloadFields(value: Int): List<String> {
        return PolarUserDeviceSettingsModels.deviceLocationPayloadFields(value)
    }

    fun userDeviceSettingsUsbConnectionModePayloadFields(enabled: Boolean): List<String> {
        return PolarUserDeviceSettingsModels.usbConnectionModePayloadFields(enabled)
    }

    fun userDeviceSettingsAutomaticTrainingDetectionPayloadFields(enabled: Boolean, sensitivity: Int, minimumDurationSeconds: Int): List<String> {
        return PolarUserDeviceSettingsModels.automaticTrainingDetectionPayloadFields(enabled, sensitivity, minimumDurationSeconds)
    }

    fun userDeviceSettingsAutomaticOhrPayloadFields(enabled: Boolean): List<String> {
        return PolarUserDeviceSettingsModels.automaticOhrPayloadFields(enabled)
    }

    fun userDeviceSettingsDaylightSavingPayloadFields(): List<String> {
        return PolarUserDeviceSettingsModels.daylightSavingPayloadFields()
    }

    fun userDeviceSettingsAutomaticMeasurementStateName(enabled: Boolean): String? {
        return PolarUserDeviceSettingsModels.automaticMeasurementStateName(enabled)
    }

    fun userDeviceSettingsDeviceLocationName(value: Int): String? {
        return PolarUserDeviceSettingsModels.deviceLocationName(value)
    }

    fun userDeviceSettingsDeviceLocationValue(name: String): Int? {
        return PolarUserDeviceSettingsModels.deviceLocationValue(name)
    }

    fun userDeviceSettingsUsbConnectionModeName(enabled: Boolean): String? {
        return PolarUserDeviceSettingsModels.usbConnectionModeName(if (enabled) 2 else 1)
    }

    fun userDeviceSettingsAutomaticTrainingDetectionModeName(enabled: Boolean): String? {
        return PolarUserDeviceSettingsModels.automaticTrainingDetectionModeName(if (enabled) 1 else 0)
    }

    fun planUserDeviceSettingsOperations(id: String, kind: String, path: String, payloadFields: List<String> = emptyList()): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = id,
                kind = kind,
                path = path,
                payloadFields = payloadFields
            )
        ).commands.mapNotNull { command ->
            val parts = command.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            when (parts[0]) {
                "read" -> PftpRequest.PbPFtpOperation.Command.GET to parts[1]
                "write" -> PftpRequest.PbPFtpOperation.Command.PUT to parts[1]
                else -> null
            }
        }
    }

    fun userDeviceSettingsPath(fileSystemType: String, deviceSettingsPath: String, sensorSettingsPath: String, unknownSettingsPath: String?): String? {
        return PolarRuntimeOrchestration.userDeviceSettingsPath(fileSystemType, deviceSettingsPath, sensorSettingsPath, unknownSettingsPath)
    }

    fun planStoredDataCleanup(kind: String, rootPath: String, cutoffDate: String? = null): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath,
                cutoffDate = cutoffDate
            )
        ).commands.mapNotNull(::cleanupCommandOperation)
    }

    fun planStoredDataCleanupOperations(
        kind: String,
        rootPath: String,
        cutoffDate: String? = null,
        entries: List<String> = emptyList(),
        includePrefixes: List<String> = emptyList(),
        includeSuffixes: List<String> = emptyList()
    ): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath,
                cutoffDate = cutoffDate,
                entries = entries,
                includePrefixes = includePrefixes,
                includeSuffixes = includeSuffixes
            )
        ).commands.mapNotNull(::cleanupCommandOperation)
    }

    fun planStoredDataCleanupRemoveOperation(rootPath: String, filePath: String): Pair<PftpRequest.PbPFtpOperation.Command, String>? {
        val normalizedRoot = rootPath.trimEnd('/')
        val entry = filePath.removePrefix(if (normalizedRoot.isEmpty()) "/" else "$normalizedRoot/")
        return planStoredDataCleanupOperations(
            kind = "filterDirectoryEntries",
            rootPath = rootPath,
            entries = listOf(entry)
        ).lastOrNull()
    }

    fun storedDataEntryMatchesFilter(entry: String, includePrefixes: List<String> = emptyList(), includeSuffixes: List<String> = emptyList()): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter(entry, includePrefixes, includeSuffixes)
    }

    fun storedDataCleanupDirectoryEntryMatches(dataType: String, entry: String, cutoffFolder: String? = null): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataCleanupDirectoryEntryMatches(dataType, entry, cutoffFolder)
    }

    fun shouldPruneStoredDataEmptyParents(dataType: String): Boolean {
        return PolarWorkflowRuntimePlanning.shouldPruneStoredDataEmptyParents(dataType)
    }

    fun storedDataCleanupRootPath(dataType: String, defaultRoot: String): String {
        return PolarWorkflowRuntimePlanning.storedDataCleanupRootPath(dataType, defaultRoot)
    }

    fun storedDataDateIsOnOrBefore(day: String, cutoffDate: String): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataDateIsOnOrBefore(day, cutoffDate)
    }

    fun storedDataEmptyParentDirectories(filePath: String, trailingSlash: Boolean): List<String> {
        return PolarWorkflowRuntimePlanning.storedDataEmptyParentDirectories(filePath, trailingSlash = trailingSlash)
    }

    fun planOfflineTriggerSet(currentTypes: List<String>, desiredFeatures: List<PlannedOfflineTriggerDesiredFeature>, secretPresent: Boolean): PlannedOfflineTriggerRuntime {
        val plan = PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = "setOfflineRecordingTrigger",
            currentDeviceTriggers = currentTypes.map { type -> PolarOfflineTriggerDeviceTrigger(type, "enabled") },
            desiredFeatures = desiredFeatures.map { feature -> PolarOfflineTriggerDesiredFeature(feature.type, feature.hasSelectedSettings) },
            secretPresent = secretPresent,
            transport = PolarOfflineTriggerTransport()
        )
        return PlannedOfflineTriggerRuntime(plan.commands, plan.enabledFeatures, plan.excludedFeatures, plan.terminal)
    }

    fun planOfflineTriggerGet(currentTriggers: List<PlannedOfflineTriggerDeviceTrigger>): PlannedOfflineTriggerRuntime {
        val plan = PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = "getOfflineRecordingTriggerSetup",
            currentDeviceTriggers = currentTriggers.map { trigger ->
                PolarOfflineTriggerDeviceTrigger(
                    type = trigger.type,
                    status = if (trigger.enabled) "enabled" else "disabled"
                )
            }
        )
        return PlannedOfflineTriggerRuntime(plan.commands, plan.enabledFeatures, plan.excludedFeatures, plan.terminal)
    }

    fun planFirmwareWorkflow(id: String, statuses: List<String> = emptyList(), firmwareFiles: List<String> = emptyList()): PlannedFirmwareWorkflow {
        val plan = PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = id,
                expectedStatuses = statuses,
                expectedTerminalStatus = statuses.lastOrNull(),
                expectedTerminalError = when (id) {
                    "retryable-server-failure" -> "retryable-server-failure"
                    "client-request-failure" -> "client-request-failure"
                    else -> null
                },
                expectedStatusOrder = statuses,
                firmwareFiles = firmwareFiles
            )
        )
        return PlannedFirmwareWorkflow(
            statuses = plan.statuses,
            writes = plan.writes,
            terminal = plan.terminal,
            terminalError = plan.terminalError,
            retryDelaysMillis = plan.retryDelaysMillis,
            cleanupCallbackCount = plan.cleanupCallbackCount
        )
    }

    fun firmwareRetryDelaysMillis(maxRetries: Int): List<Long> {
        return PolarWorkflowRuntimePlanning.firmwareRetryDelaysMillis(maxRetries)
    }

    fun firmwareAvailabilityFailureIsRetryable(details: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwareAvailabilityFailureIsRetryable(details)
    }

    fun planFirmwareWriteOperations(firmwareFiles: List<String>): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return planFirmwareWorkflow(
            id = "write-package-success-with-system-update-last",
            statuses = listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", "fwUpdateCompletedSuccessfully"),
            firmwareFiles = firmwareFiles
        ).writes.map { path -> PftpRequest.PbPFtpOperation.Command.PUT to path }
    }

    fun planInvalidFirmwarePackageWorkflow(): PlannedFirmwareWorkflow {
        return planFirmwareWorkflow(
            id = "empty-or-invalid-zip",
            statuses = listOf("fetchingFwUpdatePackage", "fwUpdateNotAvailable")
        )
    }

    fun planFirmwarePackageDownloadFailureWorkflow(): PlannedFirmwareWorkflow {
        return planFirmwareWorkflow(
            id = "download-failure",
            statuses = listOf("fetchingFwUpdatePackage", "fwUpdateFailed")
        )
    }

    fun planFirmwareCheckUpdateAvailableWorkflow(): PlannedFirmwareWorkflow {
        return planFirmwareWorkflow(
            id = "check-update-available",
            statuses = listOf("checkFwUpdateAvailable")
        )
    }

    fun planFirmwareCheckUpdateNotAvailableWorkflow(): PlannedFirmwareWorkflow {
        return planFirmwareWorkflow(
            id = "check-update-not-available",
            statuses = listOf("checkFwUpdateNotAvailable")
        )
    }

    fun planFirmwareRetryableServerFailureWorkflow(): PlannedFirmwareWorkflow {
        return planFirmwareWorkflow(
            id = "retryable-server-failure",
            statuses = listOf("fwUpdateFailed")
        )
    }

    fun planFirmwarePackageFetchCancellationWorkflow(): PlannedFirmwareWorkflow {
        return PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = "cancel-after-package-fetch-cleans-up-before-ble-write",
                expectedStatuses = listOf("fetchingFwUpdatePackage", "fwUpdateCancelled"),
                expectedTerminalError = "cancelled",
                expectedCleanupCallbackCount = 1
            )
        ).let { plan ->
            PlannedFirmwareWorkflow(
                statuses = plan.statuses,
                writes = plan.writes,
                terminal = plan.terminal,
                terminalError = plan.terminalError,
                retryDelaysMillis = plan.retryDelaysMillis,
                cleanupCallbackCount = plan.cleanupCallbackCount
            )
        }
    }

    fun planFirmwareSystemUpdateRebootSuccessWorkflow(firmwareFiles: List<String>): PlannedFirmwareWorkflow {
        return planFirmwareWorkflow(
            id = "system-update-reboot-response-is-success",
            statuses = listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", "fwUpdateCompletedSuccessfully"),
            firmwareFiles = firmwareFiles.map { it.trimStart('/') }
        )
    }

    fun planFirmwareBatteryTooLowTerminalWorkflow(firmwareFiles: List<String>): PlannedFirmwareWorkflow {
        return PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = "battery-too-low-response-is-terminal-failure",
                firmwareFiles = firmwareFiles.map { it.trimStart('/') }
            )
        ).let { plan ->
            PlannedFirmwareWorkflow(
                statuses = plan.statuses,
                writes = plan.writes,
                terminal = plan.terminal,
                terminalError = plan.terminalError,
                retryDelaysMillis = plan.retryDelaysMillis,
                cleanupCallbackCount = plan.cleanupCallbackCount
            )
        }
    }

    fun orderFirmwareFiles(fileNames: List<String>): List<String> {
        return PolarWorkflowRuntimePlanning.orderFirmwareFiles(fileNames)
    }

    fun firmwarePackageEntryIsPayload(fileName: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwarePackageEntryIsPayload(fileName)
    }

    fun firmwarePayloadFileNames(fileNames: List<String>): List<String> {
        return PolarWorkflowRuntimePlanning.firmwarePayloadFileNames(fileNames)
    }

    fun firmwareFileTriggersRebootWait(fileName: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwareFileTriggersRebootWait(fileName)
    }

    fun firmwareWriteTerminal(errorCode: Int, fileName: String): String {
        return PolarWorkflowRuntimePlanning.firmwareWriteTerminal(errorCode, fileName)
    }

    fun firmwareFinalizationSteps(hasH10FileSystem: Boolean, isDeviceSensor: Boolean): List<String> {
        return PolarWorkflowRuntimePlanning.firmwareFinalizationSteps(hasH10FileSystem, isDeviceSensor)
    }

    fun firmwareWriteProgressPercent(bytesWritten: Long, payloadSize: Int): Long {
        return PolarWorkflowRuntimePlanning.firmwareWriteProgressPercent(bytesWritten.toInt(), payloadSize).toLong()
    }

    fun firmwareFilePriority(fileName: String): Int {
        return PolarFirmwareUpdateModels.firmwareFilePriority(fileName)
    }

    fun isAvailableFirmwareVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        return PolarFirmwareUpdateModels.isAvailableFirmwareVersionHigher(currentVersion, availableVersion)
    }

    fun firmwareUpdateIsAvailable(currentVersion: String, availableVersion: String, fileUrl: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwareUpdateIsAvailable(currentVersion, availableVersion, fileUrl)
    }

    fun ledConfigPayloadBytes(sdkModeLedEnabled: Boolean, ppiModeLedEnabled: Boolean): ByteArray {
        return PolarWorkflowRuntimePlanning.ledConfigPayloadBytes(sdkModeLedEnabled, ppiModeLedEnabled)
            .map { value -> value.toByte() }
            .toByteArray()
    }

    fun firmwareDeviceVersion(major: Int, minor: Int, patch: Int): String {
        return PolarFirmwareUpdateModels.deviceVersionToString(major, minor, patch)
    }

    fun shouldEmitFirmwareWriteProgress(
        lastBytesWritten: Long,
        bytesWritten: Long,
        payloadSize: Int,
        minPercentageIncrement: Long,
        timeSinceLastEmitMs: Long,
        maxEmitIntervalMs: Long = 5_000L
    ): Boolean {
        return PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(
            lastBytesWritten = lastBytesWritten.toInt(),
            bytesWritten = bytesWritten.toInt(),
            payloadSize = payloadSize,
            minPercentageIncrement = minPercentageIncrement.toInt(),
            timeSinceLastEmitMs = timeSinceLastEmitMs,
            maxEmitIntervalMs = maxEmitIntervalMs
        )
    }

    fun planBackupRestore(path: String, payloadHex: String, writeResult: String = "success") {
        PolarWorkflowRuntimePlanning.planBackupRestore(
            listOf(
                PolarBackupRestoreFile(
                    directory = path.substringBeforeLast('/', missingDelimiterValue = "") + "/",
                    fileName = path.substringAfterLast('/'),
                    dataHex = payloadHex,
                    writeResult = writeResult
                )
            )
        )
    }

    fun planBackupRestoreWrites(files: List<PlannedBackupRestoreFile>): List<PlannedBackupRestoreWrite> {
        return PolarWorkflowRuntimePlanning.planBackupRestore(
            files.map { file ->
                PolarBackupRestoreFile(
                    directory = file.directory,
                    fileName = file.fileName,
                    dataHex = file.dataHex,
                    writeResult = file.writeResult
                )
            }
        ).commands.mapNotNull { command ->
            val parts = command.split(":", limit = 3)
            if (parts.size != 3 || parts[0] != "PUT") return@mapNotNull null
            PlannedBackupRestoreWrite(
                operation = PftpRequest.PbPFtpOperation.Command.PUT to parts[1],
                payloadHex = parts[2]
            )
        }
    }

    fun defaultBackupPaths(): List<String> {
        return PolarWorkflowRuntimePlanning.defaultBackupPaths()
    }

    fun backupRootPaths(entries: Collection<String>): List<String> {
        return PolarWorkflowRuntimePlanning.backupRootPaths(entries.toList())
    }

    fun parseBackupTextForAndroid(backupText: String): List<String> {
        return PolarWorkflowRuntimePlanning.parseBackupTextForAndroid(backupText)
    }

    fun backupTraversalRootPath(path: String): String {
        return PolarWorkflowRuntimePlanning.backupTraversalRootPath(path)
    }

    fun backupTraversalPlan(path: String): BackupTraversalPlan {
        val plan = PolarWorkflowRuntimePlanning.backupTraversalPlan(path)
        return BackupTraversalPlan(
            path = plan.path,
            wildcardRootPath = plan.wildcardRootPath,
            wildcardSubFolder = plan.wildcardSubFolder
        )
    }

    fun backupFilePath(path: String): Pair<String, String> {
        val filePath = PolarWorkflowRuntimePlanning.backupFilePath(path)
        return filePath.directory to filePath.fileName
    }

    fun sleepAnalysisPath(day: String): String {
        return PolarSleepModels.sleepAnalysisPath(day)
    }

    fun sleepSkinTemperaturePath(day: String): String {
        return PolarSleepModels.sleepSkinTemperaturePath(day)
    }

    fun sleepStartOffsetSeconds(value: Int): Int {
        return PolarSleepModels.sleepStartOffsetSeconds(value)
    }

    fun sleepEndOffsetSeconds(value: Int): Int {
        return PolarSleepModels.sleepEndOffsetSeconds(value)
    }

    fun shouldIncludeOriginalSleepRange(hasOriginalSleepRange: Boolean): Boolean {
        return PolarSleepModels.shouldIncludeOriginalSleepRange(hasOriginalSleepRange)
    }

    fun shouldIncludeSleepSkinTemperatureResult(hasSleepDate: Boolean): Boolean {
        return PolarSleepModels.shouldIncludeSleepSkinTemperatureResult(hasSleepDate)
    }

    fun sleepRestApiPath(): String {
        return PolarRestServiceModels.sleepApiPath()
    }

    fun sleepRecordingStateSubscribePath(): String {
        return PolarRestServiceModels.sleepRecordingStateSubscribePath()
    }

    fun stopSleepRecordingPath(): String {
        return PolarRestServiceModels.stopSleepRecordingPath()
    }

    fun restEventPayloads(uncompressed: Boolean, payloads: List<ByteArray>): List<ByteArray> {
        return PolarRestServiceModels.restEventPayloads(uncompressed, payloads)
    }

    fun offlineRecordingMeasurementTypeName(fileName: String): String {
        return PolarOfflineRecordingModels.measurementTypeFromFileName(fileName).name
    }

    fun groupedOfflineRecordingEntries(entries: List<Pair<String, Long>>): List<PlannedOfflineRecordingEntry> {
        return PolarOfflineRecordingModels.groupedRecordingEntries(
            entries.map { entry -> PolarOfflineRecordingFileEntry(path = entry.first, size = entry.second) }
        ).map { entry ->
            PlannedOfflineRecordingEntry(
                type = entry.type,
                androidPath = entry.androidPath,
                size = entry.size,
                dateTime = entry.dateTime
            )
        }
    }

    fun parsePmdFilesV2(text: String): List<PlannedOfflineRecordingEntry> {
        return PolarOfflineRecordingModels.parsePmdFilesV2(text).map { entry ->
            PlannedOfflineRecordingEntry(
                type = entry.type,
                androidPath = entry.androidPath,
                size = entry.size,
                dateTime = entry.dateTime
            )
        }
    }

    fun watchFaceConfigFields(
        timeStyleId: Int? = null,
        complicationLayoutId: Int? = null,
        backgroundStyleId: Int? = null,
        accentColor: Long? = null,
        complicationIds: List<Int>? = null,
        fontfaceId: Int? = null
    ): PlannedWatchFaceFields {
        val fields = PolarWatchFaceFields.fromNullableFields(
            timeStyleId = timeStyleId,
            complicationLayoutId = complicationLayoutId,
            backgroundStyleId = backgroundStyleId,
            accentColor = accentColor,
            complicationIds = complicationIds,
            fontfaceId = fontfaceId
        )
        return PlannedWatchFaceFields(
            timeStyleId = fields.timeStyleId,
            complicationLayoutId = fields.complicationLayoutId,
            backgroundStyleId = fields.backgroundStyleId,
            accentColor = fields.accentColor,
            complicationIds = fields.complicationIds,
            fontfaceId = fields.fontfaceId
        )
    }

    fun parseWatchFaceConfigFlatBuffer(raw: ByteArray): PlannedWatchFaceFields {
        val fields = PolarWatchFaceConfigFlatBuffer.parse(raw)
        return PlannedWatchFaceFields(
            timeStyleId = fields.timeStyleId,
            complicationLayoutId = fields.complicationLayoutId,
            backgroundStyleId = fields.backgroundStyleId,
            accentColor = fields.accentColor,
            complicationIds = fields.complicationIds,
            fontfaceId = fields.fontfaceId
        )
    }

    fun buildWatchFaceConfigFlatBuffer(fields: PlannedWatchFaceFields): ByteArray {
        return PolarWatchFaceConfigFlatBuffer.build(
            PolarWatchFaceFields(
                timeStyleId = fields.timeStyleId,
                complicationLayoutId = fields.complicationLayoutId,
                backgroundStyleId = fields.backgroundStyleId,
                accentColor = fields.accentColor,
                complicationIds = fields.complicationIds,
                fontfaceId = fields.fontfaceId
            )
        )
    }

    fun kvtxBuildWriteAndCommit(kvKey: Int, data: ByteArray): ByteArray {
        return PolarKvtxScriptCodec.buildWriteAndCommit(kvKey.toLong() and 0xFFFF_FFFFL, data)
    }

    fun kvtxExtractValueForKey(script: ByteArray, kvKey: Int): ByteArray? {
        return PolarKvtxScriptCodec.extractValueForKey(script, kvKey.toLong() and 0xFFFF_FFFFL)
    }

    fun kvtxU32Le(value: Int): ByteArray {
        return PolarKvtxScriptCodec.u32Le(value.toLong() and 0xFFFF_FFFFL)
    }

    fun nightlyRechargePath(day: String): String {
        return PolarSleepModels.nightlyRechargePath(day)
    }

    fun skinTemperaturePath(day: String): String {
        return PolarSdkModelMappers.skinTemperaturePath(day)
    }

    fun activityDirectoryPath(day: String): String {
        return PolarActivityModels.activityDirectoryPath(day)
    }

    fun dailySummaryPath(day: String): String {
        return PolarActivityModels.dailySummaryPath(day)
    }

    fun automaticSamplesDirectoryPath(): String {
        return PolarActivityModels.automaticSamplesDirectoryPath()
    }

    fun automaticSamplesFilePath(fileName: String): String {
        return PolarActivityModels.automaticSamplesFilePath(fileName)
    }

    fun spo2TestDirectoryPath(day: String): String {
        return PolarSpo2Models.testDirectoryPath(day)
    }

    fun spo2TestResultPath(directoryPath: String, subDirectoryName: String): String {
        return PolarSpo2Models.testResultPath(directoryPath, subDirectoryName)
    }

    fun spo2TestTimeFromFolderNames(date: String, timeDirName: String): String? {
        return PolarSpo2Models.testTimeFromFolderNames(date, timeDirName)
    }

    fun spo2TestDataProjection(
        date: String,
        timeDirName: String,
        recordingDevice: String?,
        timeZoneOffsetMinutes: Int,
        testStatus: Int,
        bloodOxygenPercent: Int?,
        spo2Class: Int?,
        spo2ValueDeviationFromBaseline: Int?,
        spo2QualityAveragePercent: Float?,
        averageHeartRateBpm: Int?,
        heartRateVariabilityMs: Float?,
        spo2HrvDeviationFromBaseline: Int?,
        altitudeMeters: Float?
    ): PlannedSpo2TestProjection {
        val projection = PolarSpo2Models.projectAndroidTestData(
            date = date,
            timeDirName = timeDirName,
            recordingDevice = recordingDevice,
            timeZoneOffsetMinutes = timeZoneOffsetMinutes,
            testStatus = testStatus,
            bloodOxygenPercent = bloodOxygenPercent,
            spo2Class = spo2Class,
            spo2ValueDeviationFromBaseline = spo2ValueDeviationFromBaseline,
            spo2QualityAveragePercent = spo2QualityAveragePercent,
            averageHeartRateBpm = averageHeartRateBpm,
            heartRateVariabilityMs = heartRateVariabilityMs,
            spo2HrvDeviationFromBaseline = spo2HrvDeviationFromBaseline,
            altitudeMeters = altitudeMeters
        )
        return PlannedSpo2TestProjection(
            recordingDevice = projection.recordingDevice,
            timeZoneOffsetMinutes = projection.timeZoneOffsetMinutes,
            testStatus = projection.testStatus,
            bloodOxygenPercent = projection.bloodOxygenPercent,
            spo2Class = projection.spo2Class,
            spo2ValueDeviationFromBaseline = projection.spo2ValueDeviationFromBaseline,
            spo2QualityAveragePercent = projection.spo2QualityAveragePercent,
            averageHeartRateBpm = projection.averageHeartRateBpm,
            heartRateVariabilityMs = projection.heartRateVariabilityMs,
            spo2HrvDeviationFromBaseline = projection.spo2HrvDeviationFromBaseline,
            altitudeMeters = projection.altitudeMeters
        )
    }

    fun trainingSessionRootPath(): String {
        return PolarTrainingSessionModels.ROOT_PATH
    }

    fun trainingSessionReferences(entries: List<PlannedTrainingSessionFileEntry>): List<PlannedTrainingSessionReference> {
        return PolarTrainingSessionModels.buildReferences(
            entries.map { entry -> PolarTrainingSessionFileEntry(path = entry.path, size = entry.size) }
        ).map { reference -> reference.toPlanned() }
    }

    fun trainingSessionPayloadFetchOrder(reference: PlannedTrainingSessionReference): List<String> {
        return PolarTrainingSessionModels.payloadFetchOrder(reference.toShared())
    }

    fun trainingSessionPayloadReadPlan(reference: PlannedTrainingSessionReference): List<PlannedTrainingPayloadReadPlanEntry> {
        return PolarTrainingSessionModels.payloadReadPlan(reference.toShared()).map { entry ->
            PlannedTrainingPayloadReadPlanEntry(
                path = entry.path,
                fileName = entry.fileName,
                publicModelSlot = entry.publicModelSlot,
                exerciseIndex = entry.exerciseIndex
            )
        }
    }

    fun trainingSessionPayloadReadResult(
        reference: PlannedTrainingSessionReference,
        responsesByPath: Map<String, PlannedTrainingPayloadResponse>,
        fetchOrder: List<String> = trainingSessionPayloadFetchOrder(reference)
    ): PlannedTrainingPayloadReadResult {
        val result = PolarTrainingSessionModels.assemblePayloadReadResult(
            reference = reference.toShared(),
            responsesByPath = responsesByPath.mapValues { entry -> entry.value.toShared() },
            fetchOrder = fetchOrder
        )
        return PlannedTrainingPayloadReadResult(
            totalBytes = result.totalBytes,
            completedBytes = result.completedBytes,
            progressPercent = result.progressPercent,
            currentFileName = result.currentFileName,
            modelName = result.modelName,
            durationSeconds = result.durationSeconds,
            distanceMeters = result.distanceMeters,
            calories = result.calories,
            exercises = result.exercises.map { exercise ->
                PlannedTrainingPayloadExercise(
                    index = exercise.index,
                    exerciseSummaryPresent = exercise.exerciseSummaryPresent,
                    routePresent = exercise.routePresent,
                    routeAdvancedPresent = exercise.routeAdvancedPresent,
                    samplesHeartRate = exercise.samplesHeartRate,
                    samplesAdvancedHeartRate = exercise.samplesAdvancedHeartRate,
                    unknownAdvancedSampleListsIgnored = exercise.unknownAdvancedSampleListsIgnored,
                    malformedFilesIgnored = exercise.malformedFilesIgnored
                )
            }
        )
    }

    fun parseTrainingSessionPayloadResponse(fileName: String, payload: ByteArray): PlannedTrainingPayloadResponse {
        return PolarTrainingSessionModels.parseDecodedPayloadResponse(fileName, payload).toPlanned()
    }

    fun trainingSessionPayloadEncoding(fileName: String): String? {
        return PolarTrainingSessionModels.payloadParserCase(fileName)?.encoding
    }

    fun decodeTrainingSessionPayloadBytes(fileName: String, payload: ByteArray): ByteArray {
        return PolarTrainingSessionModels.decodePayloadBytes(fileName, payload)
    }

    fun trainingSessionPayloadMalformed(fileName: String, payload: ByteArray): Boolean {
        return PolarTrainingSessionModels.parseDecodedPayloadResponse(fileName, payload).malformed
    }

    fun trainingSessionPayloadParser(fileName: String): String? {
        return PolarTrainingSessionModels.payloadParserCase(fileName)?.parser
    }

    fun trainingSessionPublicModelSlot(fileName: String): String? {
        return PolarTrainingSessionModels.publicModelSlot(fileName)
    }

    fun trainingSessionExerciseDataTypeFileName(typeName: String): String? {
        return PolarTrainingSessionModels.exerciseDataTypeFileName(typeName)
    }

    fun trainingSessionDeleteParentPath(referencePath: String): String {
        return PolarTrainingSessionModels.deleteParentPath(referencePath)
    }

    fun trainingSessionDeleteRemovePath(referencePath: String, parentEntryCount: Int): String {
        return PolarTrainingSessionModels.deleteRemovePath(referencePath, parentEntryCount)
    }

    fun trainingSessionProgressPercent(completedBytes: Long, totalBytes: Long): Int {
        return PolarTrainingSessionModels.progressPercent(completedBytes, totalBytes)
    }

    fun trainingSessionReferenceDateMatches(date: String, fromDate: String?, toDate: String?): Boolean {
        return PolarTrainingSessionModels.referenceDateMatches(date, fromDate, toDate)
    }

    fun basicDateRange(startInclusive: String, endInclusive: String): List<String> {
        return PolarTimeUtils.basicDateRange(startInclusive, endInclusive)
    }

    fun dateTimeFields(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int,
        timeZoneOffsetMinutes: Int? = null,
        trusted: Boolean = false
    ): PlannedDateTimeFields {
        return PolarDateTimeFields(
            date = PolarDateFields(year, month, day),
            time = PolarTimeFields(hour, minute, second, millis),
            timeZoneOffsetMinutes = timeZoneOffsetMinutes,
            trusted = trusted
        ).toPlanned()
    }

    fun millisToNanos(milliseconds: Int): Int {
        return PolarTimeUtils.millisToNanos(milliseconds)
    }

    fun secondsToMinutes(seconds: Int): Int {
        return PolarTimeUtils.secondsToMinutes(seconds)
    }

    fun durationMillis(hours: Int, minutes: Int, seconds: Int, millis: Int): Int {
        return PolarTimeUtils.durationToMillis(PolarDurationFields(hours, minutes, seconds, millis))
    }

    fun identifierClassification(identifier: String): String {
        return when (PolarDeviceId.classifyIdentifier(identifier)) {
            PolarDeviceId.IdentifierClassification.DeviceId -> "deviceId"
            PolarDeviceId.IdentifierClassification.PlatformSpecific -> "platformSpecific"
            PolarDeviceId.IdentifierClassification.Invalid -> "invalid"
        }
    }

    fun d2hNotificationTypeName(notificationId: Int): String? {
        return PolarD2hRuntimePlanning.notificationTypeOrNull(notificationId)
    }

    fun d2hParsedProtoName(notificationType: String, parametersHex: String): String? {
        return PolarD2hRuntimePlanning.parsedProtoName(notificationType, parametersHex)
    }

    fun d2hNotificationPlan(notificationId: Int, parametersHex: String): PlannedD2hNotification? {
        return PolarD2hRuntimePlanning.planNotificationEmission(notificationId, parametersHex)?.let { plan ->
            PlannedD2hNotification(
                notificationType = plan.notificationType,
                parsedProtoName = plan.parsedProto
            )
        }
    }

    fun firmwareDeviceInfoPath(): String {
        return PolarFirmwareUpdateModels.deviceInfoPath()
    }

    fun firmwareSystemUpdateFilePath(): String {
        return PolarFirmwareUpdateModels.systemUpdateFilePath()
    }

    fun planBackupRestoreOperation(path: String, payloadHex: String, writeResult: String = "success"): Pair<PftpRequest.PbPFtpOperation.Command, String>? {
        val command = PolarWorkflowRuntimePlanning.planBackupRestore(
            listOf(
                PolarBackupRestoreFile(
                    directory = path.substringBeforeLast('/', missingDelimiterValue = "") + "/",
                    fileName = path.substringAfterLast('/'),
                    dataHex = payloadHex,
                    writeResult = writeResult
                )
            )
        ).commands.firstOrNull { it.startsWith("PUT:") } ?: return null
        val parts = command.split(":", limit = 3)
        if (parts.size != 3 || parts[0] != "PUT") return null
        return PftpRequest.PbPFtpOperation.Command.PUT to parts[1]
    }

    fun planPsFtpWriteProgress(payloadSize: Int, platform: String): List<Int> {
        return PolarWorkflowRuntimePlanning.planPsFtpWriteProgress(payloadSize, platform)
    }

    fun planPsFtpWriteAck(payloadSize: Int, writeAck: String = "success"): String {
        return PolarWorkflowRuntimePlanning.psFtpWriteAckTerminal(payloadSize, writeAck)
    }

    fun ensurePsFtpWriteRuntimePlan(payloadSize: Int, platform: String = "android", writeAck: String = "success") {
        planPsFtpWriteProgress(payloadSize, platform)
        val terminal = planPsFtpWriteAck(payloadSize, writeAck)
        require(terminal == "success") { "PSFTP write ACK planning failed: $terminal" }
    }

    private fun PolarDateTimeFields.toPlanned(): PlannedDateTimeFields {
        return PlannedDateTimeFields(
            date = PlannedDateFields(date.year, date.month, date.day),
            time = PlannedTimeFields(time.hour, time.minute, time.second, time.millis),
            timeZoneOffsetMinutes = timeZoneOffsetMinutes,
            trusted = trusted
        )
    }

    private fun PolarTrainingSessionReference.toPlanned(): PlannedTrainingSessionReference {
        return PlannedTrainingSessionReference(
            dateTime = dateTime,
            date = date,
            path = path,
            trainingDataTypes = trainingDataTypes,
            exercises = exercises.map { exercise ->
                PlannedTrainingExerciseReference(
                    index = exercise.index,
                    androidPath = exercise.androidPath,
                    iosPath = exercise.iosPath,
                    exerciseDataTypes = exercise.exerciseDataTypes,
                    fileSizes = exercise.fileSizes
                )
            },
            fileSize = fileSize
        )
    }

    private fun PlannedTrainingSessionReference.toShared(): PolarTrainingSessionReference {
        return PolarTrainingSessionReference(
            dateTime = dateTime,
            date = date,
            path = path,
            trainingDataTypes = trainingDataTypes,
            exercises = exercises.map { exercise ->
                com.polar.shared.sdk.PolarTrainingExerciseReference(
                    index = exercise.index,
                    androidPath = exercise.androidPath,
                    iosPath = exercise.iosPath,
                    exerciseDataTypes = exercise.exerciseDataTypes,
                    fileSizes = exercise.fileSizes
                )
            },
            fileSize = fileSize
        )
    }

    private fun PlannedTrainingPayloadResponse.toShared(): PolarTrainingPayloadResponse {
        return PolarTrainingPayloadResponse(
            kind = kind,
            fileName = fileName,
            byteSize = byteSize,
            malformed = malformed,
            payload = PolarTrainingPayloadFields(
                modelName = modelName,
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                calories = calories,
                heartRateSamples = heartRateSamples,
                intervalledSampleLists = intervalledSampleLists.map { sampleList ->
                    com.polar.shared.sdk.PolarTrainingIntervalledSampleList(
                        sampleType = sampleList.sampleType,
                        heartRateSamples = sampleList.heartRateSamples
                    )
                }
            )
        )
    }

    private fun PolarTrainingPayloadResponse.toPlanned(): PlannedTrainingPayloadResponse {
        return PlannedTrainingPayloadResponse(
            kind = kind,
            fileName = fileName,
            byteSize = byteSize,
            malformed = malformed,
            modelName = payload.modelName,
            durationSeconds = payload.durationSeconds,
            distanceMeters = payload.distanceMeters,
            calories = payload.calories,
            heartRateSamples = payload.heartRateSamples,
            intervalledSampleLists = payload.intervalledSampleLists.map { sampleList ->
                PlannedTrainingIntervalledSampleList(
                    sampleType = sampleList.sampleType,
                    heartRateSamples = sampleList.heartRateSamples
                )
            }
        )
    }

    fun queryValue(plan: PolarRuntimePlan): Int {
        return queryValue(queryName(plan))
    }

    fun queryValue(queryName: String): Int {
        return when (queryName) {
            "GET_DISK_SPACE" -> PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE
            "GET_LOCAL_TIME" -> PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE
            "REQUEST_START_RECORDING" -> PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE
            "REQUEST_STOP_RECORDING" -> PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE
            "REQUEST_RECORDING_STATUS" -> PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE
            "START_EXERCISE" -> PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE
            "PAUSE_EXERCISE" -> PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE
            "RESUME_EXERCISE" -> PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE
            "START_DM_EXERCISE" -> PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE
            "STOP_EXERCISE" -> PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE
            "GET_EXERCISE_STATUS" -> PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE
            "PREPARE_FIRMWARE_UPDATE" -> PftpRequest.PbPFtpQuery.PREPARE_FIRMWARE_UPDATE_VALUE
            "REQUEST_SYNCHRONIZATION" -> PftpRequest.PbPFtpQuery.REQUEST_SYNCHRONIZATION_VALUE
            "SET_LOCAL_TIME" -> PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE
            "SET_SYSTEM_TIME" -> PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE
            else -> error("Unsupported shared runtime query command $queryName")
        }
    }

    fun notificationValue(notificationName: String): Int {
        val normalized = notificationName.substringBefore(':')
        return when (normalized) {
            "RESET" -> PftpNotification.PbPFtpHostToDevNotification.RESET_VALUE
            "INITIALIZE_SESSION" -> PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE
            "START_SYNC" -> PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE
            "STOP_SYNC" -> PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE
            "TERMINATE_SESSION" -> PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE
            else -> error("Unsupported shared runtime notification command $notificationName")
        }
    }

    fun queryName(plan: PolarRuntimePlan): String {
        return plan.commands.first { command -> command.startsWith("query:") }.substringAfter("query:")
    }

    fun queryNames(plan: PolarRuntimePlan): List<String> {
        return plan.commands
            .filter { command -> command.startsWith("query:") }
            .map { command -> command.substringAfter("query:") }
    }

    fun notificationNames(plan: PolarRuntimePlan): List<String> {
        return plan.commands
            .filter { command -> command.startsWith("notification:") }
            .map { command -> command.substringAfter("notification:") }
    }

    fun fileOperationCommand(plan: PolarRuntimePlan): PftpRequest.PbPFtpOperation.Command {
        return when (fileOperationCommandName(plan)) {
            "GET" -> PftpRequest.PbPFtpOperation.Command.GET
            "PUT" -> PftpRequest.PbPFtpOperation.Command.PUT
            "REMOVE" -> PftpRequest.PbPFtpOperation.Command.REMOVE
            else -> error("Unsupported shared runtime file operation command ${fileOperationCommandName(plan)}")
        }
    }

    fun fileOperationPath(plan: PolarRuntimePlan): String {
        return fileOperationToken(plan).substringAfter(':')
    }

    fun fileOperationBytes(plan: PolarRuntimePlan): ByteArray {
        return fileOperationBytes(fileOperationCommand(plan) to fileOperationPath(plan))
    }

    fun fileOperationBytes(operation: Pair<PftpRequest.PbPFtpOperation.Command, String>): ByteArray {
        return PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(operation.first)
            .setPath(operation.second)
            .build()
            .toByteArray()
    }

    private fun fileOperationCommandName(plan: PolarRuntimePlan): String {
        return fileOperationToken(plan).substringBefore(':')
    }

    private fun fileOperationToken(plan: PolarRuntimePlan): String {
        return plan.commands.first { command ->
            command.startsWith("GET:") ||
                command.startsWith("PUT:") ||
                command.startsWith("REMOVE:")
        }
    }

    private fun cleanupCommandOperation(command: String): Pair<PftpRequest.PbPFtpOperation.Command, String>? {
        val parts = command.split(":", limit = 2)
        if (parts.size != 2) return null
        return when (parts[0]) {
            "GET" -> PftpRequest.PbPFtpOperation.Command.GET to parts[1]
            "REMOVE" -> PftpRequest.PbPFtpOperation.Command.REMOVE to parts[1]
            else -> null
        }
    }

    private fun Set<PmdMeasurementType>.sharedMeasurementTypeNames(): Set<String> {
        return mapNotNull { feature ->
            PolarPmdMeasurementTypeName.fromRawValue(feature.numVal.toInt())?.name
        }.toSet()
    }

    private fun PlannedGnssLocationSample.toShared(): PolarGnssLocationSample {
        return when (this) {
            is PlannedGnssCoordinateSample -> PolarGnssCoordinateSample(
                timeStamp = timeStamp,
                latitude = latitude,
                longitude = longitude,
                date = date,
                cumulativeDistance = cumulativeDistance,
                speed = speed,
                usedAccelerationSpeed = usedAccelerationSpeed,
                coordinateSpeed = coordinateSpeed,
                accelerationSpeedFactor = accelerationSpeedFactor,
                course = course,
                gpsChipSpeed = gpsChipSpeed,
                fix = fix,
                speedFlag = speedFlag,
                fusionState = fusionState
            )
            is PlannedGnssNmeaSample -> PolarGnssNmeaSample(
                timeStamp = timeStamp,
                measurementPeriod = measurementPeriod,
                messageLength = messageLength,
                statusFlags = statusFlags,
                nmeaMessage = nmeaMessage
            )
            is PlannedGnssSatelliteDilutionSample -> PolarGnssSatelliteDilutionSample(
                timeStamp = timeStamp,
                dilution = dilution,
                altitude = altitude,
                numberOfSatellites = numberOfSatellites,
                fix = fix
            )
            is PlannedGnssSatelliteSummarySample -> PolarGnssSatelliteSummarySample(
                timeStamp = timeStamp,
                seenGnssSatelliteSummaryBand1 = seenGnssSatelliteSummaryBand1.toShared(),
                usedGnssSatelliteSummaryBand1 = usedGnssSatelliteSummaryBand1.toShared(),
                seenGnssSatelliteSummaryBand2 = seenGnssSatelliteSummaryBand2.toShared(),
                usedGnssSatelliteSummaryBand2 = usedGnssSatelliteSummaryBand2.toShared(),
                maxSnr = maxSnr
            )
        }
    }

    private fun PlannedGnssSatelliteSummary.toShared(): PolarGnssSatelliteSummary {
        return PolarGnssSatelliteSummary(
            gpsNbrOfSat = gpsNbrOfSat,
            gpsMaxSnr = gpsMaxSnr,
            glonassNbrOfSat = glonassNbrOfSat,
            glonassMaxSnr = glonassMaxSnr,
            galileoNbrOfSat = galileoNbrOfSat,
            galileoMaxSnr = galileoMaxSnr,
            beidouNbrOfSat = beidouNbrOfSat,
            beidouMaxSnr = beidouMaxSnr,
            nbrOfSat = nbrOfSat,
            snrTop5Avg = snrTop5Avg
        )
    }

    private fun PolarLocationDataProjectionSample.toPlanned(): PlannedLocationDataProjectionSample {
        return when (this) {
            is PolarLocationCoordinatesProjectionSample -> PlannedLocationCoordinatesProjectionSample(
                timeStamp = timeStamp,
                latitude = latitude,
                longitude = longitude,
                time = time,
                cumulativeDistance = cumulativeDistance,
                speed = speed,
                usedAccelerationSpeed = usedAccelerationSpeed,
                coordinateSpeed = coordinateSpeed,
                accelerationSpeedFactor = accelerationSpeedFactor,
                course = course,
                gpsChipSpeed = gpsChipSpeed,
                fix = fix,
                speedFlag = speedFlag,
                fusionState = fusionState
            )
            is PolarLocationNmeaProjectionSample -> PlannedLocationNmeaProjectionSample(
                timeStamp = timeStamp,
                measurementPeriod = measurementPeriod,
                statusFlags = statusFlags,
                nmeaMessage = nmeaMessage
            )
            is PolarLocationSatelliteDilutionProjectionSample -> PlannedLocationSatelliteDilutionProjectionSample(
                timeStamp = timeStamp,
                dilution = dilution,
                altitude = altitude,
                numberOfSatellites = numberOfSatellites,
                fix = fix
            )
            is PolarLocationSatelliteSummaryProjectionSample -> PlannedLocationSatelliteSummaryProjectionSample(
                timeStamp = timeStamp,
                seenSatelliteSummaryBand1 = seenSatelliteSummaryBand1.toPlanned(),
                usedSatelliteSummaryBand1 = usedSatelliteSummaryBand1.toPlanned(),
                seenSatelliteSummaryBand2 = seenSatelliteSummaryBand2.toPlanned(),
                usedSatelliteSummaryBand2 = usedSatelliteSummaryBand2.toPlanned(),
                maxSnr = maxSnr
            )
        }
    }

    private fun PolarLocationSatelliteSummaryProjection.toPlanned(): PlannedLocationSatelliteSummary {
        return PlannedLocationSatelliteSummary(
            gpsNbrOfSat = gpsNbrOfSat,
            gpsMaxSnr = gpsMaxSnr,
            glonassNbrOfSat = glonassNbrOfSat,
            glonassMaxSnr = glonassMaxSnr,
            galileoNbrOfSat = galileoNbrOfSat,
            galileoMaxSnr = galileoMaxSnr,
            beidouNbrOfSat = beidouNbrOfSat,
            beidouMaxSnr = beidouMaxSnr,
            nbrOfSat = nbrOfSat,
            snrTop5Avg = snrTop5Avg
        )
    }

    private fun Set<String>.mapToPolarDeviceDataTypes(): Set<PolarDeviceDataType> {
        return mapTo(linkedSetOf()) { typeName ->
            when (typeName) {
                "ECG" -> PolarDeviceDataType.ECG
                "ACC" -> PolarDeviceDataType.ACC
                "PPG" -> PolarDeviceDataType.PPG
                "PPI" -> PolarDeviceDataType.PPI
                "GYRO" -> PolarDeviceDataType.GYRO
                "MAGNETOMETER" -> PolarDeviceDataType.MAGNETOMETER
                "PRESSURE" -> PolarDeviceDataType.PRESSURE
                "LOCATION" -> PolarDeviceDataType.LOCATION
                "TEMPERATURE" -> PolarDeviceDataType.TEMPERATURE
                "HR" -> PolarDeviceDataType.HR
                "SKIN_TEMPERATURE" -> PolarDeviceDataType.SKIN_TEMPERATURE
                else -> error("Unsupported shared runtime data type $typeName")
            }
        }
    }
}
