import Foundation
import CoreBluetooth
import Combine
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public class BleRscClient: BleGattClientBase, @unchecked Sendable {

    public static let RSC_SERVICE = CBUUID(string: "1814")
    let RSC_FEATURE     = CBUUID(string: "2a54")
    let RSC_MEASUREMENT = CBUUID(string: "2a53")

    public typealias BleRscNotification = (speed: Double, candence: UInt8, strideLength: Int, distance: Double, running: Bool, flags: UInt8)

    private let rscStreams = StreamContinuationList<BleRscNotification>()

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleRscClient.RSC_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: RSC_MEASUREMENT)
        addCharacteristicRead(RSC_FEATURE)
    }

    override public func disconnected() {
        super.disconnected()
        rscStreams.finish(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if err == 0 {
            if chr.isEqual(RSC_MEASUREMENT) {
                rscStreams.yield(RscMeasurementRuntimePlanner.rscMeasurement(data: data))
            }
        }
    }

    /// AsyncThrowingStream for observing RSC notifications.
    public func observeRscNotifications(_ checkConnection: Bool) -> AsyncThrowingStream<BleRscNotification, Error> {
        return rscStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
    }

    public override func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        waitNotificationEnabled(RSC_MEASUREMENT, checkConnection: checkConnection)
    }
}

private enum RscMeasurementRuntimePlanner {
    static func rscMeasurement(data: Data) -> BleRscClient.BleRscNotification {
        #if canImport(PolarBleSdkShared)
        if let sharedMeasurement = sharedRscMeasurement(data: data) {
            return sharedMeasurement
        }
        #endif
        return localRscMeasurement(data: data)
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedRscMeasurement(data: Data) -> BleRscClient.BleRscNotification? {
        guard let csv = PolarIosSharedBridge.shared.rscMeasurementCsv(payloadHex: data.rscHexString()) else {
            return nil
        }
        let fields = csv.split(separator: ",", omittingEmptySubsequences: false)
        guard fields.count == 6,
              let speed = Double(fields[0]),
              let cadence = UInt8(fields[1]),
              let strideLength = Int(fields[2]),
              let distance = Double(fields[3]),
              let running = boolValue(String(fields[4])),
              let flags = UInt8(fields[5]) else {
            return nil
        }
        return BleRscClient.BleRscNotification(speed: speed, candence: cadence, strideLength: strideLength, distance: distance, running: running, flags: flags)
    }

    private static func boolValue(_ value: String) -> Bool? {
        switch value {
        case "true": return true
        case "false": return false
        default: return nil
        }
    }
    #endif

    private static func localRscMeasurement(data: Data) -> BleRscClient.BleRscNotification {
        var index = 0
        let flags = data[0]
        index += 1
        let strideLenPresent = (flags & 0x01) == 0x01
        let totalDistancePresent = (flags & 0x02) == 0x02
        let running = (flags & 0x04) == 0x04
        let speedMask = UInt16(data[index]) | UInt16(UInt16(data[index + 1]) << 8)
        let speed = (Double(speedMask) / 256.0) * 3.6
        index += 2
        let cadence = data[index]
        index += 1
        var strideLength = 0
        var totalDistance = 0.0
        if strideLenPresent {
            strideLength = Int(UInt16(data[index]) | UInt16(UInt16(data[index + 1]) << 8))
            index += 2
        }
        if totalDistancePresent {
            var distance = 0
            memcpy(&distance, (data.subdata(in: index..<(index + 4)) as NSData).bytes, 4)
            totalDistance = Double(distance) * 0.1
        }
        return BleRscClient.BleRscNotification(speed: speed, candence: cadence, strideLength: strideLength, distance: totalDistance, running: running, flags: flags)
    }
}

private extension Data {
    func rscHexString() -> String {
        map { String(format: "%02x", $0) }.joined()
    }
}
