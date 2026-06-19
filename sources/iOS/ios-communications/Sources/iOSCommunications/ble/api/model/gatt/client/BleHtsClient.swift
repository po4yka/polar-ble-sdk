import CoreBluetooth
import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class HealthThermometer {
    public static let HTS_SERVICE = CBUUID(string: "00001809-0000-1000-8000-00805f9b34fb")
    public static let TEMPERATURE_MEASUREMENT = CBUUID(string: "2A1C")
    public static let TEMPERATURE_TYPE = CBUUID(string: "2A1D")
}

public class BleHtsClient: BleGattClientBase, @unchecked Sendable {
    public struct TemperatureMeasurement {
        public let temperatureCelsius: Float
        public let temperatureFahrenheit: Float
    }

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: HealthThermometer.HTS_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: HealthThermometer.TEMPERATURE_MEASUREMENT, disableOnDisconnect: true)
    }

    static let TEMP_ACCURACY: Int = 100

    private let streamLock = NSLock()
    private var continuations: [UUID: AsyncThrowingStream<TemperatureMeasurement, Error>.Continuation] = [:]

    override public func disconnected() {
        super.disconnected()
        streamLock.lock()
        let conts = continuations
        continuations.removeAll()
        streamLock.unlock()
        for cont in conts.values {
            cont.finish(throwing: BleGattException.gattDisconnected)
        }
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        BleLogger.trace("processServiceData uuid=\(chr), err=\(err) len(data)=\(data.count)")
        if err == 0 {
            if chr == HealthThermometer.TEMPERATURE_MEASUREMENT {
                BleLogger.trace_hex("TEMPERATURE_MEASUREMENT ", data: data)
                let measurement = HtsTemperatureRuntimePlanner.temperatureMeasurement(data: data)
                streamLock.lock()
                let conts = continuations
                streamLock.unlock()
                for cont in conts.values {
                    cont.yield(measurement)
                }
            }
            if chr == HealthThermometer.TEMPERATURE_TYPE {
                BleLogger.trace_hex("TEMPERATURE_TYPE ", data: data)
            }
        }
    }

    public func observeHtsNotifications(checkConnection: Bool) -> AsyncThrowingStream<TemperatureMeasurement, Error> {
        if checkConnection && !(gattServiceTransmitter?.isConnected() ?? false) {
            return AsyncThrowingStream { $0.finish(throwing: BleGattException.gattDisconnected) }
        }
        let id = UUID()
        var capturedCont: AsyncThrowingStream<TemperatureMeasurement, Error>.Continuation!
        let stream = AsyncThrowingStream<TemperatureMeasurement, Error> { cont in
            capturedCont = cont
        }
        streamLock.lock()
        continuations[id] = capturedCont
        streamLock.unlock()
        capturedCont.onTermination = { [weak self] _ in
            self?.streamLock.lock()
            self?.continuations.removeValue(forKey: id)
            self?.streamLock.unlock()
        }
        return stream
    }
}

private enum HtsTemperatureRuntimePlanner {
    static func temperatureMeasurement(data: Data) -> BleHtsClient.TemperatureMeasurement {
        #if canImport(PolarBleSdkShared)
        if let sharedMeasurement = sharedTemperatureMeasurement(data: data) {
            return sharedMeasurement
        }
        #endif
        return localTemperatureMeasurement(data: data)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedTemperatureMeasurement(data: Data) -> BleHtsClient.TemperatureMeasurement? {
        guard let csv = PolarIosSharedBridge.shared.htsTemperatureMeasurementCsv(payloadHex: data.hexString()) else {
            return nil
        }
        let fields = csv.split(separator: ",")
        guard fields.count == 5,
              let celsius = Float(fields[0]),
              let fahrenheit = Float(fields[1]) else {
            return nil
        }
        return BleHtsClient.TemperatureMeasurement(temperatureCelsius: celsius, temperatureFahrenheit: fahrenheit)
    }
    #endif

    private static func localTemperatureMeasurement(data: Data) -> BleHtsClient.TemperatureMeasurement {
        guard data.count >= 5 else {
            BleLogger.error("localTemperatureMeasurement: data too short (\(data.count) bytes), expected >= 5")
            return BleHtsClient.TemperatureMeasurement(temperatureCelsius: 0.0, temperatureFahrenheit: 0.0)
        }
        let flags = UInt8(data[0])
        let isFahrenheit = (flags & 0x01) != 0
        let exponent = Int8(bitPattern: data[4])
        let value = UInt32(data[1]) | (UInt32(data[2]) << 8) | (UInt32(data[3]) << 16)
        let temperature = (Float(value) * pow(10.0, Float(exponent)) * Float(BleHtsClient.TEMP_ACCURACY)).rounded() / Float(BleHtsClient.TEMP_ACCURACY)
        let celsius = !isFahrenheit ? temperature : (temperature - 32.0) * 5.0 / 9.0
        let fahrenheit = isFahrenheit ? temperature : temperature * 9.0 / 5.0 + 32.0
        return BleHtsClient.TemperatureMeasurement(temperatureCelsius: celsius, temperatureFahrenheit: fahrenheit)
    }
}

private extension Data {
    func hexString() -> String {
        map { String(format: "%02x", $0) }.joined()
    }
}
