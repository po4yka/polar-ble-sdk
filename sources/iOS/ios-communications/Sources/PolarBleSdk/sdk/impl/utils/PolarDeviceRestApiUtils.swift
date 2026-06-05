///  Copyright © 2025 Polar. All rights reserved.

import Foundation
import Combine
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

extension BlePsFtpClient {

    func receiveRestApiEventData(identifier: String) -> AsyncThrowingStream<[Data], Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await notification in self.waitNotification() {
                        guard notification.id == Protocol_PbPFtpDevToHostNotification.restApiEvent.rawValue else { continue }
                        guard let params = try? Protocol_PbPftpDHRestApiEvent(serializedBytes: notification.parameters as Data) else { continue }
                        let events: [Data]
                        if params.hasUncompressed && params.uncompressed {
                            events = RestEventSharedProjection.uncompressedPayloads(params.event) ?? params.event
                        } else {
                            events = params.event.compactMap { data in
                                guard let uncompressedData = data.inflated() else {
                                    BleLogger.trace_hex("Failed to decompress API event parameters, data: ", data: data)
                                    return data
                                }
                                return uncompressedData
                            }
                        }
                        continuation.yield(events)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    func receiveRestApiEvents<T: Decodable>(identifier: String) -> AsyncThrowingStream<[T], Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await eventDataList in self.receiveRestApiEventData(identifier: identifier) {
                        let decoded = eventDataList.compactMap { data -> T? in
                            BleLogger.trace("Received REST API event, JSON: \(String(data: data, encoding: .utf8) ?? "<binary>")")
                            return try? JSONDecoder().decode(T.self, from: data)
                        }
                        continuation.yield(decoded)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}

private enum RestEventSharedProjection {
    static func uncompressedPayloads(_ payloads: [Data]) -> [Data]? {
        #if canImport(PolarBleSdkShared)
        let encoded = payloads.map { $0.map { String(format: "%02x", $0) }.joined() }.joined(separator: ",")
        return PolarIosSharedBridge.shared.restUncompressedEventPayloadsHex(payloadsHex: encoded)
            .split(separator: "|", omittingEmptySubsequences: false)
            .map { Data(hexString: String($0)) }
        #else
        return nil
        #endif
    }
}

private extension Data {
    init(hexString: String) {
        var bytes: [UInt8] = []
        bytes.reserveCapacity(hexString.count / 2)
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            bytes.append(UInt8(hexString[index..<nextIndex], radix: 16) ?? 0)
            index = nextIndex
        }
        self.init(bytes)
    }
}
