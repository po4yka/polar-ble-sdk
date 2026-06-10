package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerMode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerStatus
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineTrigger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSecret
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.EcgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GnssLocationData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.OfflineHrData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PressureData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.SkinTemperatureData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.TemperatureData
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.api.model.*
import com.polar.shared.pmd.PolarPmdMeasurementTypeName
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
import com.polar.shared.sdk.PolarSdkModelMappers
import java.util.Collections

internal object PolarDataUtils {
    private const val TAG = "PolarDataUtils"

    fun mapPMDClientPpgDataToPolarPpg(ppgData: PpgData): PolarPpgData {
        var type: PolarPpgData.PpgDataType = PolarPpgData.PpgDataType.UNKNOWN
        val listOfSamples = mutableListOf<PolarPpgData.PolarPpgSample>()
        for (sample in ppgData.ppgSamples) {
            when (sample) {
                is PpgData.PpgDataFrameType0 -> {
                    type = PolarPpgData.PpgDataType.PPG3_AMBIENT1
                    val channelsData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>() // No channel status available for PPG frame type 0. Put empty into listOfSamples.
                    channelsData.addAll(sample.ppgDataSamples)
                    channelsData.add(sample.ambientSample)
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), channelsData, statusData))
                }
                is PpgData.PpgDataFrameType7 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_7
                    val channelsData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>()
                    channelsData.addAll(sample.ppgDataSamples)
                    statusData.addAll(sample.statusBits)
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), channelsData, statusData))
                }
                is PpgData.PpgDataFrameType8 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_8
                    val channelsData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>()
                    channelsData.addAll(sample.ppgDataSamples)
                    statusData.addAll(sample.statusBits)
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), channelsData, statusData))
                }
                is PpgData.PpgDataSampleSportId -> {
                    type = PolarPpgData.PpgDataType.SPORT_ID
                    val samplesData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>() // No channel status available for PPG frame type 6. Put empty into listOfSamples.
                    samplesData.addAll(listOf(sample.sportId.toInt()))
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData, statusData))
                }
                is PpgData.PpgDataFrameType4 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_4
                    val samplesData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>() // No channel status available for PPG frame type 4. Put empty into listOfSamples.
                    samplesData.addAll(sample.channel1GainTs.map { it.toInt() })
                    samplesData.addAll(sample.channel2GainTs.map { it.toInt() })
                    samplesData.addAll(sample.numIntTs.map { it.toInt() })
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData, statusData))
                }
                is PpgData.PpgDataFrameType5 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_5
                    val samplesData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>() // No channel status available for PPG frame type 5. Put empty into listOfSamples.
                    samplesData.addAll(listOf((sample.operationMode).toInt()))
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData, statusData))
                }
                is PpgData.PpgDataFrameType9 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_9
                    val samplesData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>() // No channel status available for PPG frame type 9. Put empty into listOfSamples.
                    samplesData.addAll(sample.channel1GainTs.map { it.toInt() })
                    samplesData.addAll(sample.channel2GainTs.map { it.toInt() })
                    samplesData.addAll(sample.numIntTs.map { it.toInt() })
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData,statusData))
                }
                is PpgData.PpgDataFrameType10 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_10
                    val samplesData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>()
                    samplesData.addAll(sample.greenSamples.map { it })
                    samplesData.addAll(sample.redSamples.map { it })
                    samplesData.addAll(sample.irSamples.map { it })
                    statusData.addAll(sample.statusBits)
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData, statusData))
                }
                is PpgData.PpgDataFrameType13 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_13
                    val samplesData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>()
                    samplesData.addAll(sample.ppgChannel0.map { it })
                    samplesData.addAll(sample.ppgChannel1.map { it })
                    statusData.addAll(sample.statusBits)
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData, statusData))
                }

                is PpgData.PpgDataFrameType14 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_14
                    val samplesData = mutableListOf<Int>()
                    val statusData = mutableListOf<Int>() // No channel status available for PPG frame type 14. Put empty into listOfSamples.
                    samplesData.addAll(sample.channel1GainTs1.map { it.toInt() })
                    samplesData.addAll(sample.channel2GainTs1.map { it.toInt() })
                    samplesData.addAll(sample.numIntTs1.map { it.toInt() })
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData, statusData))
                }
            }
        }
        return PolarPpgData(listOfSamples, type)
    }

    fun mapPMDClientPpiDataToPolarPpiData(ppiData: PpiData): PolarPpiData {
        val samples: MutableList<PolarPpiData.PolarPpiSample> = mutableListOf()
        for ((hr, ppInMs, ppErrorEstimate, blockerBit, skinContactStatus, skinContactSupported, timeStamp) in ppiData.ppiSamples) {
            samples.add(
                PolarPpiData.PolarPpiSample(ppInMs, ppErrorEstimate, hr, blockerBit != 0, skinContactStatus != 0, skinContactSupported != 0, timeStamp)
            )
        }
        return PolarPpiData(samples)
    }

    fun mapPMDClientOfflineHrDataToPolarHrData(offlineHrData: OfflineHrData): PolarHrData {
        val samples: MutableList<PolarHrData.PolarHrSample> = mutableListOf()
        for (sample in offlineHrData.hrSamples) {
            samples.add(
                PolarHrData.PolarHrSample(
                    hr = sample.hr,
                    ppgQuality = sample.ppgQuality,
                    correctedHr = sample.correctedHr,
                    rrsMs = emptyList(),
                    rrAvailable = false,
                    contactStatus = false,
                    contactStatusSupported = false
                )
            )
        }
        return PolarHrData(samples)
    }

    fun mapPMDClientLocationDataToPolarLocationData(location: GnssLocationData): PolarLocationData {
        return PolarLocationData(samples = PolarLocationDataProjection.fromGnssSamples(location.toSharedGnssSamples()).map { it.toPolarLocationDataSample() })
    }

    private fun GnssLocationData.toSharedGnssSamples(): List<PolarGnssLocationSample> {
        return gnssLocationDataSamples.map { sample ->
            when (sample) {
                is GnssLocationData.GnssCoordinateSample -> PolarGnssCoordinateSample(
                    timeStamp = sample.timeStamp,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    date = sample.date,
                    cumulativeDistance = sample.cumulativeDistance,
                    speed = sample.speed,
                    usedAccelerationSpeed = sample.usedAccelerationSpeed,
                    coordinateSpeed = sample.coordinateSpeed,
                    accelerationSpeedFactor = sample.accelerationSpeedFactor,
                    course = sample.course,
                    gpsChipSpeed = sample.gpsChipSpeed,
                    fix = sample.fix,
                    speedFlag = sample.speedFlag,
                    fusionState = sample.fusionState
                )
                is GnssLocationData.GnssGpsNMEASample -> PolarGnssNmeaSample(
                    timeStamp = sample.timeStamp,
                    measurementPeriod = sample.measurementPeriod,
                    messageLength = sample.messageLength,
                    statusFlags = sample.statusFlags,
                    nmeaMessage = sample.nmeaMessage
                )
                is GnssLocationData.GnssSatelliteDilutionSample -> PolarGnssSatelliteDilutionSample(
                    timeStamp = sample.timeStamp,
                    dilution = sample.dilution,
                    altitude = sample.altitude,
                    numberOfSatellites = sample.numberOfSatellites,
                    fix = sample.fix
                )
                is GnssLocationData.GnssSatelliteSummarySample -> PolarGnssSatelliteSummarySample(
                    timeStamp = sample.timeStamp,
                    seenGnssSatelliteSummaryBand1 = sample.seenGnssSatelliteSummaryBand1.toSharedGnssSatelliteSummary(),
                    usedGnssSatelliteSummaryBand1 = sample.usedGnssSatelliteSummaryBand1.toSharedGnssSatelliteSummary(),
                    seenGnssSatelliteSummaryBand2 = sample.seenGnssSatelliteSummaryBand2.toSharedGnssSatelliteSummary(),
                    usedGnssSatelliteSummaryBand2 = sample.usedGnssSatelliteSummaryBand2.toSharedGnssSatelliteSummary(),
                    maxSnr = sample.maxSnr
                )
            }
        }
    }

    private fun GnssLocationData.GnssSatelliteSummary.toSharedGnssSatelliteSummary(): PolarGnssSatelliteSummary {
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

    private fun PolarLocationDataProjectionSample.toPolarLocationDataSample(): PolarLocationDataSample {
        return when (this) {
            is PolarLocationCoordinatesProjectionSample -> GpsCoordinatesSample(
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
            is PolarLocationNmeaProjectionSample -> GpsNMEASample(
                timeStamp = timeStamp,
                measurementPeriod = measurementPeriod,
                statusFlags = statusFlags,
                nmeaMessage = nmeaMessage
            )
            is PolarLocationSatelliteDilutionProjectionSample -> GpsSatelliteDilutionSample(
                timeStamp = timeStamp,
                dilution = dilution,
                altitude = altitude,
                numberOfSatellites = numberOfSatellites,
                fix = fix
            )
            is PolarLocationSatelliteSummaryProjectionSample -> GpsSatelliteSummarySample(
                timeStamp = timeStamp,
                seenSatelliteSummaryBand1 = seenSatelliteSummaryBand1.toPolarSatelliteSummary(),
                usedSatelliteSummaryBand1 = usedSatelliteSummaryBand1.toPolarSatelliteSummary(),
                seenSatelliteSummaryBand2 = seenSatelliteSummaryBand2.toPolarSatelliteSummary(),
                usedSatelliteSummaryBand2 = usedSatelliteSummaryBand2.toPolarSatelliteSummary(),
                maxSnr = maxSnr
            )
        }
    }

    private fun PolarLocationSatelliteSummaryProjection.toPolarSatelliteSummary(): SatelliteSummary {
        return SatelliteSummary(
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

    fun mapPmdClientEcgDataToPolarEcg(ecgData: EcgData): PolarEcgData {
        val ecgDataSamples = mutableListOf<PolarEcgDataSample>()
        for (sample in ecgData.ecgSamples) {
            when (sample) {
                is EcgData.EcgSample -> {
                    val ecgSample = EcgSample(timeStamp = sample.timeStamp.toLong(), voltage = sample.microVolts)
                    ecgDataSamples.add(ecgSample)
                }
                is EcgData.EcgSampleFrameType3 -> {
                    val ecgSample = FecgSample(timeStamp = sample.timeStamp.toLong(), ecg = sample.data0, bioz = sample.data1, status = sample.status)
                    ecgDataSamples.add(ecgSample)
                }
            }
        }
        return PolarEcgData(ecgDataSamples)
    }

    fun mapPmdClientAccDataToPolarAcc(accData: AccData): PolarAccelerometerData {
        val samples: MutableList<PolarAccelerometerData.PolarAccelerometerDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in accData.accSamples) {
            samples.add(PolarAccelerometerData.PolarAccelerometerDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarAccelerometerData(samples)
    }

    fun mapPmdClientGyroDataToPolarGyro(gyroData: GyrData): PolarGyroData {
        val samples: MutableList<PolarGyroData.PolarGyroDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in gyroData.gyrSamples) {
            samples.add(PolarGyroData.PolarGyroDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarGyroData(samples)
    }

    fun mapPmdClientMagDataToPolarMagnetometer(magData: MagData): PolarMagnetometerData {
        val samples: MutableList<PolarMagnetometerData.PolarMagnetometerDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in magData.magSamples) {
            samples.add(PolarMagnetometerData.PolarMagnetometerDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarMagnetometerData(samples)
    }

    fun mapPmdClientPressureDataToPolarPressure(pressureData: PressureData): PolarPressureData {
        val samples: MutableList<PolarPressureData.PolarPressureDataSample> = mutableListOf()
        for ((timeStamp, pressure) in pressureData.pressureSamples) {
            samples.add(PolarPressureData.PolarPressureDataSample(timeStamp.toLong(), pressure))
        }
        return PolarPressureData(samples)
    }

    fun mapPolarFeatureToPmdClientMeasurementType(polarFeature: PolarBleApi.PolarDeviceDataType): PmdMeasurementType {
        val sharedTypeName = PolarSdkModelMappers.pmdMeasurementTypeNameForPublicDataTypeName(polarFeature.name)
            ?: throw PolarBleSdkInternalException("Error when map Polar feature $polarFeature to measurement type")
        return when (sharedTypeName) {
            "ECG" -> PmdMeasurementType.ECG
            "ACC" -> PmdMeasurementType.ACC
            "PPG" -> PmdMeasurementType.PPG
            "PPI" -> PmdMeasurementType.PPI
            "GYRO" -> PmdMeasurementType.GYRO
            "MAG" -> PmdMeasurementType.MAGNETOMETER
            "PRESSURE" -> PmdMeasurementType.PRESSURE
            "LOCATION" -> PmdMeasurementType.LOCATION
            "TEMPERATURE" -> PmdMeasurementType.TEMPERATURE
            "SKIN_TEMP" -> PmdMeasurementType.SKIN_TEMP
            "OFFLINE_HR" -> PmdMeasurementType.OFFLINE_HR
            else -> throw PolarBleSdkInternalException("Error when map shared measurement type $sharedTypeName to PMD feature")
        }
    }

    fun mapPmdClientFeatureToPolarFeature(pmdMeasurementType: PmdMeasurementType): PolarBleApi.PolarDeviceDataType {
        return when (PolarPmdMeasurementTypeName.fromRawValue(pmdMeasurementType.numVal.toInt())?.name) {
            "ECG" -> PolarBleApi.PolarDeviceDataType.ECG
            "PPG" -> PolarBleApi.PolarDeviceDataType.PPG
            "ACC" -> PolarBleApi.PolarDeviceDataType.ACC
            "PPI" -> PolarBleApi.PolarDeviceDataType.PPI
            "GYRO" -> PolarBleApi.PolarDeviceDataType.GYRO
            "MAG" -> PolarBleApi.PolarDeviceDataType.MAGNETOMETER
            "LOCATION" -> PolarBleApi.PolarDeviceDataType.LOCATION
            "PRESSURE" -> PolarBleApi.PolarDeviceDataType.PRESSURE
            "TEMPERATURE" -> PolarBleApi.PolarDeviceDataType.TEMPERATURE
            "OFFLINE_HR" -> PolarBleApi.PolarDeviceDataType.HR
            "SKIN_TEMP" -> PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE
            else -> throw PolarBleSdkInternalException("Error when map measurement type $pmdMeasurementType to Polar feature")
        }
    }

    private fun mapPolarOfflineModeTriggerToPmdOfflineTriggerMode(offlineTrigger: PolarOfflineRecordingTriggerMode): PmdOfflineRecTriggerMode {
        return when (offlineTrigger) {
            PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START -> PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START
            PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START -> PmdOfflineRecTriggerMode.TRIGGER_EXERCISE_START
            PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED -> PmdOfflineRecTriggerMode.TRIGGER_DISABLE
        }
    }

    private fun mapPmdOfflineTriggerModeToPolarOfflineTriggerMode(pmdTriggerType: PmdOfflineRecTriggerMode): PolarOfflineRecordingTriggerMode {
        return when (pmdTriggerType) {
            PmdOfflineRecTriggerMode.TRIGGER_DISABLE -> PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED
            PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START -> PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START
            PmdOfflineRecTriggerMode.TRIGGER_EXERCISE_START -> PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START
        }
    }

    /**
     * Helper to map from PolarSensorSetting to PmdSetting
     *
     * @return PmdSetting
     */
    fun mapPolarSettingsToPmdSettings(polarSensorSetting: PolarSensorSetting?): PmdSetting {
        val selected: MutableMap<PmdSetting.PmdSettingType, Int> = mutableMapOf()
        if (polarSensorSetting != null) {
            for ((key, value) in polarSensorSetting.settings) {
                selected[PmdSetting.PmdSettingType.values()[key.numVal]] = Collections.max(value)
            }
        }
        return PmdSetting(selected)
    }

    /**
     * Helper to map from PolarSensorSetting to PmdSetting
     *
     * @return PmdSetting
     */
    fun mapPmdSettingsToPolarSettings(pmd: PmdSetting, fromSelected: Boolean): PolarSensorSetting {
        return if (fromSelected) {
            val settings: MutableMap<PolarSensorSetting.SettingType, Int> = mutableMapOf()
            for ((key, value) in pmd.selected) {
                when (key) {
                    PmdSetting.PmdSettingType.SAMPLE_RATE -> settings[PolarSensorSetting.SettingType.SAMPLE_RATE] = value
                    PmdSetting.PmdSettingType.RESOLUTION -> settings[PolarSensorSetting.SettingType.RESOLUTION] = value
                    PmdSetting.PmdSettingType.RANGE -> settings[PolarSensorSetting.SettingType.RANGE] = value
                    PmdSetting.PmdSettingType.CHANNELS -> settings[PolarSensorSetting.SettingType.CHANNELS] = value
                    else -> {
                        //nop
                    }
                }
            }
            PolarSensorSetting(settings.toMap())
        } else {
            val settings: MutableMap<PolarSensorSetting.SettingType, Set<Int>> = mutableMapOf()
            for ((key, value) in pmd.settings) {
                when (key) {
                    PmdSetting.PmdSettingType.SAMPLE_RATE -> settings[PolarSensorSetting.SettingType.SAMPLE_RATE] = value
                    PmdSetting.PmdSettingType.RESOLUTION -> settings[PolarSensorSetting.SettingType.RESOLUTION] = value
                    PmdSetting.PmdSettingType.RANGE -> settings[PolarSensorSetting.SettingType.RANGE] = value
                    PmdSetting.PmdSettingType.CHANNELS -> settings[PolarSensorSetting.SettingType.CHANNELS] = value
                    else -> {
                        //nop
                    }
                }
            }
            PolarSensorSetting(settings.toList())
        }
    }

    fun mapPmdTriggerToPolarTrigger(pmdOfflineTrigger: PmdOfflineTrigger): PolarOfflineRecordingTrigger {
        val triggerMode = mapPmdOfflineTriggerModeToPolarOfflineTriggerMode(pmdOfflineTrigger.triggerMode)
        val polarTriggerSettings: MutableMap<PolarBleApi.PolarDeviceDataType, PolarSensorSetting?> = mutableMapOf()

        for (setting in pmdOfflineTrigger.triggers) {
            val dataType = mapPmdClientFeatureToPolarFeature(setting.key)
            val triggerStatus = setting.value.first

            if (triggerStatus == PmdOfflineRecTriggerStatus.TRIGGER_ENABLED) {
                // Map only the enabled
                val polarSettings = setting.value.second?.let {
                    mapPmdSettingsToPolarSettings(it, false)
                }
                polarTriggerSettings[dataType] = polarSettings
            }
        }
        return PolarOfflineRecordingTrigger(
            triggerMode = triggerMode,
            triggerFeatures = polarTriggerSettings
        )
    }

    fun mapPolarOfflineTriggerToPmdOfflineTrigger(polarTrigger: PolarOfflineRecordingTrigger): PmdOfflineTrigger {
        val pmdTriggerMode = mapPolarOfflineModeTriggerToPmdOfflineTriggerMode(polarTrigger.triggerMode)
        val pmdTriggers: MutableMap<PmdMeasurementType, Pair<PmdOfflineRecTriggerStatus, PmdSetting?>> = mutableMapOf()

        for (trigger in polarTrigger.triggerFeatures) {
            val pmdMeasurementType = mapPolarFeatureToPmdClientMeasurementType(trigger.key)
            val pmdSettings = trigger.value?.let { mapPolarSettingsToPmdSettings(it) }
            pmdTriggers[pmdMeasurementType] = Pair(PmdOfflineRecTriggerStatus.TRIGGER_ENABLED, pmdSettings)
        }

        return PmdOfflineTrigger(triggerMode = pmdTriggerMode, triggers = pmdTriggers)
    }

    fun mapPolarSecretToPmdSecret(polarSecret: PolarRecordingSecret): PmdSecret {
        return PmdSecret(
            strategy = PmdSecret.SecurityStrategy.AES128,
            key = polarSecret.secret
        )
    }

    fun mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineTemperatureData: TemperatureData): PolarTemperatureData {
        val samples: MutableList<PolarTemperatureData.PolarTemperatureDataSample> = mutableListOf()
        for (sample in offlineTemperatureData.temperatureSamples) {
            samples.add(
                PolarTemperatureData.PolarTemperatureDataSample(
                    timeStamp = sample.timeStamp.toLong(),
                    temperature = sample.temperature
                )
            )
        }
        return PolarTemperatureData(samples)
    }

    fun mapPmdClientTemperatureDataToPolarTemperature(temperatureData: TemperatureData): PolarTemperatureData {
        val samples: MutableList<PolarTemperatureData.PolarTemperatureDataSample> = mutableListOf()
        for ((timeStamp, temperature) in temperatureData.temperatureSamples) {
            samples.add(PolarTemperatureData.PolarTemperatureDataSample(timeStamp.toLong(), temperature))
        }
        return PolarTemperatureData(samples)
    }

    fun mapPmdClientSkinTemperatureDataToPolarTemperatureData(skinTemperatureData: SkinTemperatureData): PolarTemperatureData {
        val samples: MutableList<PolarTemperatureData.PolarTemperatureDataSample> = mutableListOf()
        for ((timeStamp, temperature) in skinTemperatureData.skinTemperatureSamples) {
            samples.add(PolarTemperatureData.PolarTemperatureDataSample(timeStamp.toLong(), temperature))
        }
        return PolarTemperatureData(samples)
    }
}
