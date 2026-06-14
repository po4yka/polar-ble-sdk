
import UIKit
import PolarBleSdk
import CoreBluetooth

class ViewController: UIViewController,
                      PolarBleApiObserver,
                      PolarBleApiPowerStateObserver,
                      PolarBleApiDeviceHrObserver,
                      PolarBleApiDeviceInfoObserver,
                      PolarBleApiDeviceFeaturesObserver,
                      PolarBleApiLogger {
    func deviceDisconnected(_ identifier: PolarBleSdk.PolarDeviceInfo, pairingError: Bool) {
        return
    }
    
    func hrValueReceived(_ identifier: String, data: (hr: UInt8, rrs: [Int], rrsMs: [Int], contact: Bool, contactSupported: Bool)) {
        return
    }
    
    func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String) {
        return
    }
    
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<PolarBleSdk.PolarDeviceDataType>) {
        return
    }
    
    func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdk.PolarBleSdkFeature) {
        return
    }
    
    
    @IBOutlet weak var ppgSelectButton: UIButton!
    @IBOutlet weak var accSelectButton: UIButton!
    @IBOutlet weak var connectionsButton: UIButton!
    @IBOutlet weak var disconnectButton: UIButton!
    @IBOutlet weak var sensorDatalogButton: UIButton!
    
    var connectedDevices: [PolarDeviceInfo] = [] {
        didSet {
            disconnectButton.isEnabled = !connectedDevices.isEmpty
        }
    }
    var selectedDevice: PolarDeviceInfo?
    
    var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: [PolarBleSdkFeature.feature_polar_online_streaming,
                                                                                        PolarBleSdkFeature.feature_hr,
                                                                                        PolarBleSdkFeature.feature_battery_info,
                                                                                        PolarBleSdkFeature.feature_device_info,
                                                                                        PolarBleSdkFeature.feature_polar_offline_recording
                                                                                        ]
    )
    var accSelected = false, ppgSelected = false
    var previousAccData: PolarAccData?
    var previousPpgData: PolarPpgData?
    var previousPpiData: PolarPpiData?
    var previousEcgData: PolarEcgData?
    let collector = DataCollector()
    var elapsedTimer: DispatchSourceTimer?
    var logConfig: SDLogConfig?
    var supportsSdLog = false
    
    @IBOutlet weak var ecgSwitch: UISwitch!
    @IBOutlet weak var ppiSwitch: UISwitch!
    @IBOutlet weak var ppgSwitch: UISwitch!
    @IBOutlet weak var accSwitch: UISwitch!
    @IBOutlet weak var deviceState: UILabel!
    @IBOutlet weak var accZ: UILabel!
    @IBOutlet weak var accY: UILabel!
    @IBOutlet weak var accX: UILabel!
    @IBOutlet weak var btState: UILabel!
    @IBOutlet weak var sdkVersion: UILabel!
    @IBOutlet weak var ppg0: UILabel!
    @IBOutlet weak var ppg1: UILabel!
    @IBOutlet weak var ppg2: UILabel!
    @IBOutlet weak var batteryLevel: UILabel!
    @IBOutlet weak var firmwareVersion: UILabel!
    @IBOutlet weak var hr: UILabel!
    @IBOutlet weak var rrsMs: UILabel!
    @IBOutlet weak var ecg: UILabel!
    @IBOutlet weak var ppi: UILabel!
    @IBOutlet weak var bioz: UILabel!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        connectionsButton.layer.cornerRadius = 10
        connectionsButton.clipsToBounds = true
        disconnectButton.layer.cornerRadius = 10
        disconnectButton.clipsToBounds = true
        disconnectButton.isEnabled = !connectedDevices.isEmpty
        accSelectButton.layer.cornerRadius = 10
        accSelectButton.clipsToBounds = true
        ppgSelectButton.layer.cornerRadius = 10
        ppgSelectButton.clipsToBounds = true
        sensorDatalogButton.layer.cornerRadius = 10
        sensorDatalogButton.clipsToBounds = true
        sensorDatalogButton.isEnabled = false
        api.observer = self
        api.deviceFeaturesObserver = self
        api.deviceHrObserver = self
        api.deviceInfoObserver = self
        api.logger = self
        api.powerStateObserver = self
        self.btState.text = api.isBlePowered ? "BT ON" : "BT OFF"
        sdkVersion.text = PolarBleApiDefaultImpl.versionInfo()
        api.automaticReconnection = false
        UIApplication.shared.isIdleTimerDisabled = true
    }
    
    @IBAction func startToggled(_ sender: Any) {
        let storyboard = UIStoryboard(name: "SensorSelectPopup", bundle: nil)
        let secondViewController = storyboard.instantiateViewController(withIdentifier: "SensorSelectPopupViewController") as! SensorSelectPopupViewController
        secondViewController.delegate = self
        secondViewController.api = api
        secondViewController.connectedDevices = connectedDevices
        navigationController?.pushViewController(secondViewController, animated: true)
        self.modalPresentationStyle = UIModalPresentationStyle.currentContext
        self.present(secondViewController, animated: true, completion: nil)
    }
    
    @IBAction func disconnectToggled(_ sender: Any) {
        do {
            try self.api.disconnectFromDevice(selectedDevice?.deviceId ?? "")
        } catch( _) {
        }
        
        self.batteryLevel.text = "-"
        self.firmwareVersion.text = "-"
        
        if let device = selectedDevice, let index = connectedDevices.firstIndex(where: { $0.deviceId == device.deviceId }) {
            elapsedTimer?.cancel()
            elapsedTimer = nil
            connectedDevices.remove(at: index)
        }
        selectedDevice = nil
    }
    
    @IBAction func sensorSettings(_ sender: Any) {
        if selectedDevice == nil {
            sensorDatalogButton.isEnabled = false
        } else {
            checkLogConfigAvailability()
            if self.supportsSdLog {
                let storyboard = UIStoryboard(name: "SensorDatalogSettingsPopup", bundle: nil)
                let datalogSettingsController = storyboard.instantiateViewController(withIdentifier: "SensorDatalogSettingsViewController") as! SensorDatalogSettingsViewController
                datalogSettingsController.delegate = self
                datalogSettingsController.api = api
                datalogSettingsController.deviceId = self.selectedDevice!.deviceId
                navigationController?.pushViewController(datalogSettingsController, animated: true)
                self.modalPresentationStyle = UIModalPresentationStyle.currentContext
                self.present(datalogSettingsController, animated: true, completion: nil)
            } else {
                sensorDatalogButton.isEnabled = false
                sensorDatalogButton.setTitle("Sensor Datalog not supported", for: .disabled)
            }
        }
    }
    
    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        super.prepare(for: segue, sender: sender)
    }
    
    func setDevice(_ device: PolarDeviceInfo) {
        print("device set \(device.name)")
        selectedDevice = device
        connectedDevices.append(device)
        self.connectionsButton.setTitle("CONNECTIONS", for: .normal)
        do{
            try api.connectToDevice(device.deviceId)
            sensorDatalogButton.isEnabled = true
        } catch let err {
            print("connect error: \(err)")
        }
    }
    
    func checkLogConfigAvailability() {
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                _ = try await api.getSDLogConfiguration(selectedDevice!.deviceId)
                supportsSdLog = true
            } catch {
                supportsSdLog = false
                dismiss(animated: false)
            }
        }
    }
    
    @IBAction func ppgSelection(_ sender: Any) {
        ppgSelected = !ppgSelected
        ppgSelectButton.setTitle(ppgSelected ? "User selects" : "ACC default max", for: .normal)
    }
    
    @IBAction func accSelection(_ sender: Any) {
        accSelected = !accSelected
        accSelectButton.setTitle(accSelected ? "User selects" : "ACC default max", for: .normal)
    }
    
    func showSettingsSelection(_ title: String, settings: Set<UInt16>) async -> UInt16 {
        let storyboard = UIStoryboard(name: "SensorSettingsDialog", bundle: nil)
        let secondViewController = storyboard.instantiateViewController(withIdentifier: "SensorSettingsDialogViewController") as! SensorSettingsDialogViewController
        navigationController?.pushViewController(secondViewController, animated: true)
        self.modalPresentationStyle = UIModalPresentationStyle.currentContext
        self.present(secondViewController, animated: true, completion: nil)
        return await secondViewController.start(title, set: settings)
    }

    func startTimer(for device: PolarDeviceInfo) {
        elapsedTimer?.cancel()
        var elapsed = 0
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now(), repeating: .seconds(1))
        timer.setEventHandler { [weak self] in
            elapsed += 1
            let hours = elapsed / 3600
            let minutes = elapsed / 60 % 60
            let seconds = elapsed % 60
            if let selectedDevice = self?.selectedDevice, selectedDevice.deviceId == device.deviceId {
                self?.deviceState.text = String(format: "%02d:%02d:%02d", hours, minutes, seconds)
            }
        }
        elapsedTimer = timer
        timer.resume()
        if !connectedDevices.contains(where: { $0.deviceId == device.deviceId }) {
            connectedDevices.append(device)
        }
    }
    
    //BLE
    func hrFeatureReady(_ identifier: String) {
        
    }
    
    func ecgFeatureReady(_ identifier: String) {
        if ecgSwitch.isOn {
            Task { [weak self] in
                guard let self else { return }
                do {
                    let settings = try await api.requestStreamSettings(identifier, feature: PolarDeviceDataType.ecg)
                    await MainActor.run { collector.startEcgStream(selectedDevice!.name) }
                    for try await data in api.startEcgStreaming(identifier, settings: settings.maxSettings()) {
                        await MainActor.run {
                            if let prevData = previousEcgData {
                                let delta = (data.timeStamp - prevData.timeStamp) / UInt64(data.samples.count)
                                var base = prevData.timeStamp - (UInt64(prevData.samples.count-1)*delta)
                                prevData.samples.forEach { arg0 in
                                    collector.streamEcg(base, ecg: arg0.voltage)
                                    base += delta
                                }
                            }
                            previousEcgData = data
                            for sample in data.samples { ecg.text = "\(sample)" }
                        }
                    }
                } catch {
                    print("ECG error: \(error)")
                    await MainActor.run { self.ecg.text = "-" }
                }
            }
        }
    }
    
    func accFeatureReady(_ identifier: String) {
        guard accSwitch.isOn else { return }
        Task { [weak self] in
            guard let self else { return }
            do {
                let settings = try await api.requestStreamSettings(identifier, feature: PolarDeviceDataType.acc)
                let selected: PolarSensorSetting
                if accSelected {
                    let sampleRates = settings.settings[PolarSensorSetting.SettingType.sampleRate] ?? Set()
                    let selectedSampleRate = await showSettingsSelection("ACC", settings: sampleRates)
                    var maxSettings = settings.settings.mapValues { values in values.max() ?? 0 }
                    maxSettings[PolarSensorSetting.SettingType.sampleRate] = UInt32(selectedSampleRate)
                    selected = PolarSensorSetting(maxSettings)
                } else {
                    selected = settings.maxSettings()
                }
                await MainActor.run { collector.startACCStream(selectedDevice!.name) }
                for try await data in api.startAccStreaming(identifier, settings: selected) {
                    await MainActor.run {
                        if let previousAccData {
                            let delta = (data.timeStamp - previousAccData.timeStamp) / UInt64(data.samples.count)
                            var base = previousAccData.timeStamp - (UInt64(previousAccData.samples.count - 1) * delta)
                            previousAccData.samples.forEach { sample in
                                collector.streamAcc(base, x: sample.x, y: sample.y, z: sample.z)
                                base += delta
                            }
                        }
                        previousAccData = data
                        data.samples.forEach { sample in
                            accX.text = "\(sample.x)"
                            accY.text = "\(sample.y)"
                            accZ.text = "\(sample.z)"
                        }
                    }
                }
            } catch {
                print("ACC error: \(error)")
                await MainActor.run {
                    self.accX.text = "-"
                    self.accY.text = "-"
                    self.accZ.text = "-"
                }
            }
        }
    }
    
    func calculateBaseAndDelta(_ timeStamp0: UInt64, timeStamp1: UInt64, count0: UInt64, count1: UInt64) -> (base :UInt64, delta: UInt64) {
        let delta = (timeStamp1 - timeStamp0) / UInt64(count1)
        let base = timeStamp0 - (UInt64(count0-1)*delta)
        return (base,delta)
    }
    
    func ohrPPGFeatureReady(_ identifier: String) {
        guard ppgSwitch.isOn else { return }
        Task { [weak self] in
            guard let self else { return }
            do {
                let settings = try await api.requestStreamSettings(identifier, feature: PolarDeviceDataType.ppg)
                let selected: PolarSensorSetting
                if ppgSelected {
                    let sampleRates = settings.settings[PolarSensorSetting.SettingType.sampleRate] ?? Set()
                    let selectedSampleRate = await showSettingsSelection("PPG", settings: sampleRates)
                    var maxSettings = settings.settings.mapValues { values in values.max() ?? 0 }
                    maxSettings[PolarSensorSetting.SettingType.sampleRate] = UInt32(selectedSampleRate)
                    selected = PolarSensorSetting(maxSettings)
                } else {
                    selected = settings.maxSettings()
                }
                await MainActor.run { collector.startPPGStream(selectedDevice!.name) }
                for try await data in api.startPpgStreaming(identifier, settings: selected) {
                    await MainActor.run {
                        if let previousPpgData,
                           let firstPreviousSample = previousPpgData.samples.first,
                           let firstCurrentSample = data.samples.first {
                            let delta = (firstCurrentSample.timeStamp - firstPreviousSample.timeStamp) / UInt64(data.samples.count)
                            var base = firstPreviousSample.timeStamp - (UInt64(previousPpgData.samples.count - 1) * delta)
                            previousPpgData.samples.forEach { sample in
                                collector.streamPpg(base, ppg0: sample.ppg0, ppg1: sample.ppg1, ppg2: sample.ppg2, ambient: sample.ambient)
                                base += delta
                            }
                        }
                        previousPpgData = data
                        data.samples.forEach { sample in
                            ppg0.text = "\(sample.ppg0)"
                            ppg1.text = "\(sample.ppg1)"
                            ppg2.text = "\(sample.ppg2)"
                        }
                    }
                }
            } catch {
                print("PPG error: \(error)")
                await MainActor.run {
                    self.ppg0.text = "-"
                    self.ppg1.text = "-"
                    self.ppg2.text = "-"
                }
            }
        }
    }
    
    func ohrPPIFeatureReady(_ identifier: String) {
        if ppiSwitch.isOn {
            collector.startPPIStream(selectedDevice!.name)
            Task { [weak self] in
                guard let self else { return }
                do {
                    for try await data in api.startPpiStreaming(identifier) {
                        await MainActor.run {
                            if let prevData = previousPpiData {
                                let delta = (data.timeStamp - prevData.timeStamp) / UInt64(data.samples.count)
                                var base = prevData.timeStamp - (UInt64(prevData.samples.count-1)*delta)
                                prevData.samples.forEach { arg0 in
                                    collector.streamPpi(base, ppi: arg0.ppInMs, errorEstimate: arg0.ppErrorEstimate, blocker: arg0.blockerBit, contact: arg0.skinContactStatus, contactSupported: arg0.skinContactSupported, hr: arg0.hr)
                                    base += delta
                                }
                            }
                            previousPpiData = data
                            for item in data.samples {
                                ppi.text = "\(item.ppInMs)"
                            }
                        }
                    }
                } catch {
                    NSLog("PPI error: \(error)")
                    await MainActor.run { self.ppi.text = "-" }
                }
            }
        }
    }
    
    func ftpFeatureReady(_ identifier: String) {
        // do nothing
    }
    
    func biozFeatureReady(_ identifier: String) {
        if self.ppgSwitch.isOn {
            Task { [weak self] in
                guard let self else { return }
                do {
                    let settings = try await api.requestStreamSettings(identifier, feature: PolarDeviceDataType.ppg)
                    for try await value in api.startPpgStreaming(identifier, settings: settings.maxSettings()) {
                        await MainActor.run {
                            value.samples.forEach { bioz in self.bioz.text = "\(bioz)" }
                        }
                    }
                } catch {
                    print("BIOZ error: \(error)")
                    await MainActor.run { self.bioz.text = "-" }
                }
            }
        }
    }
    
    func message(_ str: String) {
        NSLog(str)
    }
    
    func deviceConnecting(_ identifier: PolarDeviceInfo) {
        print("connecting")
        self.btState.text = "CONNECTING: \(identifier.deviceId)"
    }
    
    func deviceConnected(_ identifier: PolarDeviceInfo) {
        print("connected")
        let deviceIds = connectedDevices.map { $0.deviceId }
        let connectedDevicesText = "CONNECTED: \(deviceIds.joined(separator: ", "))"
        self.btState.text = connectedDevicesText
        selectedDevice = identifier
        startTimer(for: identifier)
    }
    
    func deviceDisconnected(_ identifier: PolarDeviceInfo) {
        print("disconnected")
        self.connectionsButton.setTitle("START", for: .normal)
        hr.text = "-"
        rrsMs.text = "-"
        if let index = connectedDevices.firstIndex(where: { $0.deviceId == identifier.deviceId }) {
            elapsedTimer?.cancel()
            elapsedTimer = nil
            connectedDevices.remove(at: index)
        }
        if let newDevice = connectedDevices.first?.0 {
            selectedDevice = newDevice
        } else {
            selectedDevice = nil
        }
        if (connectedDevices.isEmpty) {
            self.btState.text = "NO DEVICES CONNECTED"
        } else {
            let deviceIds = connectedDevices.map { $0.deviceId }
            let connectedDevicesText = "CONNECTED: \(deviceIds.joined(separator: ", "))"
            self.btState.text = connectedDevicesText
        }

        collector.finalizeStreams(identifier.name, view: self)
    }
    
    func blePowerOn() {
        btState.text = "BT ON"
    }
    
    func blePowerOff() {
        btState.text = "BT OFF"
    }
    
    func hrValueReceived(_ identifier: String, data: PolarHrData) {
        self.hr.text = "\(data[0].hr)"
        self.rrsMs.text = "\(data[0].rrsMs[0])"
    }

    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        self.batteryLevel.text = "\(batteryLevel)%"
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        if BleDisClient.FIRMWARE_REVISION_STRING.uuidString.isEqual(uuid.uuidString.uppercased()) {
            self.firmwareVersion.text = "Firmware version: \(value)"
        }
    }
}
