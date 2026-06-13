package com.polar.polarsdkecghrdemo

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.StepMode
import com.androidplot.xy.XYPlot
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.*

class ECGActivity : AppCompatActivity(), PlotterListener {
    companion object {
        private const val TAG = "ECGActivity"
    }

    private lateinit var api: PolarBleApi
    private lateinit var textViewHR: TextView
    private lateinit var textViewRR: TextView
    private lateinit var textViewDeviceId: TextView
    private lateinit var textViewBattery: TextView
    private lateinit var textViewFwVersion: TextView
    private lateinit var plot: XYPlot
    private lateinit var ecgPlotter: EcgPlotter
    private val scope = MainScope()
    private var ecgJob: Job? = null
    private var hrJob: Job? = null

    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ecg)
        deviceId = intent.getStringExtra("id") ?: throw Exception("ECGActivity couldn't be created, no deviceId given")
        textViewHR = findViewById(R.id.hr)
        textViewRR = findViewById(R.id.rr)
        textViewDeviceId = findViewById(R.id.deviceId)
        textViewBattery = findViewById(R.id.battery_level)
        textViewFwVersion = findViewById(R.id.fw_version)
        plot = findViewById(R.id.plot)

        api = defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connected " + polarDeviceInfo.deviceId)
                Toast.makeText(applicationContext, R.string.connected, Toast.LENGTH_SHORT).show()
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connecting ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device disconnected ${polarDeviceInfo.deviceId}")
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d(TAG, "feature ready $feature")

                when (feature) {
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                        streamECG()
                        streamHR()
                    }
                    else -> {}
                }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                if (uuid == UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) {
                    val msg = "Firmware: " + value.trim { it <= ' ' }
                    Log.d(TAG, "Firmware: " + identifier + " " + value.trim { it <= ' ' })
                    textViewFwVersion.append(msg.trimIndent())
                }
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                if (disInfo.key == "00002a28-0000-1000-8000-00805f9b34fb") {
                    val msg = "Firmware: " + disInfo.value.trim { it <= ' ' }
                    Log.d(TAG, "Firmware: $identifier ${disInfo.value.trim { it <= ' ' }}")
                    textViewFwVersion.append(msg.trimIndent())
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "Battery level $identifier $level%")
                val batteryLevelText = "Battery level: $level%"
                textViewBattery.append(batteryLevelText)
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                // deprecated
            }

            override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) {}
        })
        try {
            api.connectToDevice(deviceId)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }
        val deviceIdText = "ID: $deviceId"
        textViewDeviceId.text = deviceIdText

        ecgPlotter = EcgPlotter("ECG", 130)
        ecgPlotter.setListener(this)

        plot.addSeries(ecgPlotter.getSeries(), ecgPlotter.formatter)
        plot.setRangeBoundaries(-1.5, 1.5, BoundaryMode.FIXED)
        plot.setRangeStep(StepMode.INCREMENT_BY_FIT, 0.25)
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 130.0)
        plot.setDomainBoundaries(0, 650, BoundaryMode.FIXED)
        plot.linesPerRangeLabel = 2
    }

    public override fun onDestroy() {
        super.onDestroy()
        ecgJob?.cancel()
        hrJob?.cancel()
        scope.cancel()
        api.shutDown()
    }

    fun streamECG() {
        if (ecgJob?.isActive != true) {
            ecgJob = scope.launch {
                try {
                    val sensorSetting = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
                    api.startEcgStreaming(deviceId, sensorSetting.maxSettings())
                        .catch { error ->
                            Log.e(TAG, "Ecg stream failed $error")
                            ecgJob = null
                        }
                        .collect { polarEcgData: PolarEcgData ->
                        Log.d(TAG, "ecg update")
                        for (data in polarEcgData.samples) {
                            if (data is EcgSample) {
                                ecgPlotter.sendSingleSample(data.voltage.toFloat() / 1000.0f)
                            }
                        }
                    }
                    Log.d(TAG, "Ecg stream complete")
                } catch (error: Throwable) {
                    Log.e(TAG, "Ecg stream failed $error")
                    ecgJob = null
                }
            }
        } else {
            ecgJob?.cancel()
            ecgJob = null
        }
    }

    fun streamHR() {
        if (hrJob?.isActive != true) {
            hrJob = scope.launch {
                api.startHrStreaming(deviceId)
                    .catch { error ->
                        Log.e(TAG, "HR stream failed. Reason $error")
                        hrJob = null
                    }
                    .collect { hrData: PolarHrData ->
                        for (sample in hrData.samples) {
                            Log.d(TAG, "HR " + sample.hr)
                            if (sample.rrsMs.isNotEmpty()) {
                                val rrText = "(${sample.rrsMs.joinToString(separator = "ms, ")}ms)"
                                textViewRR.text = rrText
                            }

                            textViewHR.text = sample.hr.toString()

                        }
                    }
                Log.d(TAG, "HR stream complete")
            }
        } else {
            hrJob?.cancel()
            hrJob = null
        }
    }

    override fun update() {
        runOnUiThread { plot.redraw() }
    }
}
