
import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

// ps-ftp errors
public enum BlePsFtpException: Error {
    ///  Undefined error, after a single undefined error might still be recoverable to next operation
    case undefinedError
    /// Frame failed to receive in 30 sec, might be due to device side is unresponsive
    case operationTimeout
    /// Operation has been canceled
    case operationCanceled
    /// Response error, with protocol buffers PftpError code
    case responseError(errorCode: Int)
    /// Indicating some fatal protocol layer error, typically when streams are not in sync.
    /// File transfer should recover after this error
    case protocolError

    public var errorName: String {
        switch self {
        case .undefinedError:
            return "UndefinedError"
        case .operationTimeout:
            return "OperationTimeout"
        case .operationCanceled:
            return "OperationCanceled"
        case .protocolError:
            return "ProtocolError"
        case .responseError(let errorCode):
            if let protoError = Communications_PbPFtpError(rawValue: errorCode) {
                return "ResponseError.\(protoError)"
            } else {
                return "ResponseError.unknown(\(errorCode))"
            }
        }
    }

    public var _domain: String {
        switch self {
        case .undefinedError:
            return "UndefinedError"
        case .operationTimeout:
            return "OperationTimeout"
        case .operationCanceled:
            return "OperationCanceled"
        case .responseError:
            return "ResponseError"
        case .protocolError:
            return "ProtocolError"
        }
    }
    
    public var _code: Int
    {
        switch self
        {
        case .responseError(let errorCode):  return errorCode
        case .operationTimeout:              return 108
        case .operationCanceled:             return 500
        case .undefinedError:                return 200
        case .protocolError:                 return 200
        }
    }
}

/// RFC76, 77, 60 related helpers
public class BlePsFtpUtility {
    
    public enum MessageType{
        case
            request,
            query,
            notification
    }
    
    public static let RFC76_STATUS_MORE = 0x03
    public static let RFC76_STATUS_LAST = 0x01
    public static let RFC76_STATUS_ERROR_OR_RESPONSE = 0x00
    
    private static let RFC76_HEADER_SIZE = 1
    private static let RFC76_ERROR_DATA_SIZE = 2
    
    public enum RFC76FrameProcessError: Error {
        case frameIsEmpty
        case frameHasNoPayload
    }
    
    public static let PFTP_AIR_PACKET_LOST_ERROR = 303
    
    public class BlePsFtpRfc76Frame {
        public var next: Int=0
        public var status: Int=0
        public var sequenceNumber: Int=0
        public var error: Int?
        public var payload = Data()
    }
    
    public class BlePsFtpRfc76SequenceNumber {
        
        public init(){}
        
        var seqn: Int=0
        
        func getSeq() -> Int {
            return seqn
        }
        
        func increment() {
            if seqn < 0x0F {
                seqn += 1
            } else {
                seqn = 0
            }
        }
    }
    
    /// Compines header(protobuf typically) and data(for write operation only, for other operations = nil)
    ///
    /// - Parameters:
    ///   - header: typically protocol buffer data
    ///   - type: @see MessageType
    ///   - id: for query or notification only
    /// - Returns: complete message stream
    public static func makeCompleteMessageStream(
        _ header: Data?,
        type: MessageType ,
        id: Int) ->  InputStream  {
        #if canImport(PolarBleSdkShared)
        if type != .request || header != nil,
           let sharedData = SharedPsFtpByteCodec.completeMessageStream(type: type, header: header, id: id) {
            return InputStream(data: sharedData)
        }
        #endif
        switch type {
        case .request:
            guard let header = header else {
                fatalError("header not present")
            }
            let mutableData=NSMutableData()
            var request = [UInt8](repeating: 0, count: 2)
            // RFC60
            request[1] = UInt8((header.count & 0x7F00) >> 8)
            request[0] = UInt8(header.count & 0x00FF)
            let requestData = NSMutableData(bytes: request, length: 2)
            let ptr = UnsafeMutablePointer<UInt8>(requestData.mutableBytes.assumingMemoryBound(to: UInt8.IntegerLiteralType.self))
            mutableData.append(ptr, length: 2)
            mutableData.append((header as NSData).bytes, length: header.count)
            return InputStream(data: mutableData as Data)
        case .query:
            var request = [UInt8](repeating: 0, count: 2)
            // RFC60
            request[1] = UInt8(((id & 0x7F00) >> 8) | 0x80/*is_query=true*/)
            request[0] = UInt8(id & 0x00FF)
            let mutableData=NSMutableData()
            mutableData.append(request, length: 2)
            if let h = header {
                mutableData.append(h)
            }
            return InputStream(data: mutableData as Data)
        case .notification:
            var request = [UInt8](repeating: UInt8(id), count: 1)
            let mutableData=NSMutableData()
            mutableData.append(&request, length: 1)
            if let h = header {
                mutableData.append(h)
            }
            return InputStream(data: mutableData as Data)
        }
    }
    
    /// Generate single air packet from data content
    ///
    /// - Parameters:
    ///   - data: content to be transmitted
    ///   - next: bit to indicate 0=first or 1=next air packet
    ///   - mtuSize: att mtu size used
    ///   - sequenceNumber: RFC76 ring counter
    /// - Returns: air packet
    public static func buildRfc76MessageFrame(_ data: InputStream, next: Int, mtuSize: Int, sequenceNumber: BlePsFtpRfc76SequenceNumber) -> Data {
        var packet = Data()
        //
        if data.hasBytesAvailable {
            //
            var frameData = [UInt8](repeating: 0, count: mtuSize)
            let bytesRead = frameData.withUnsafeMutableBufferPointer{ (ptr: inout  UnsafeMutableBufferPointer<UInt8>) -> Int in
                return data.read(ptr.baseAddress!+1, maxLength: mtuSize-1)
            }
            if data.hasBytesAvailable && bytesRead == (mtuSize-1) {
                // more
                frameData[0] = 0x06 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
            }else{
                // last
                frameData[0] = 0x02 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
            }
            packet = Data(bytes: &frameData, count: bytesRead + 1)
        } else {
            // 0 payload
            var frameData = [UInt8](repeating: 0, count: 1)
            frameData[0] = 0x02 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
            packet = Data(bytes: &frameData, count: 1)
        }
        sequenceNumber.increment()
        return packet
    }
    
    /// Generate single air packet, from header content and optionally from data content
    ///
    /// - Parameters:
    ///   - header: typically protocol buffer data
    ///   - data: typically file data
    ///   - next: bit to indicate 0=first or 1=next air packet
    ///   - mtuSize: att mtu size used
    ///   - sequenceNumber: RFC76 ring counter
    /// - Returns: air packet
    public static func buildRfc76MessageFrame(_ header: InputStream, data: InputStream?, next: Int, mtuSize: Int, sequenceNumber: BlePsFtpRfc76SequenceNumber) -> Data {
        // sorry as swift(stupids) does not support bit fields, needed have this verbose style
        var packet = Data()
        if header.hasBytesAvailable {
            //
            var frameData = [UInt8](repeating: 0, count: mtuSize)
            var bytesRead = frameData.withUnsafeMutableBufferPointer{ (ptr: inout  UnsafeMutableBufferPointer<UInt8>) -> Int in
                return header.read(ptr.baseAddress!+1, maxLength: mtuSize-1)
            }
            if header.hasBytesAvailable {
                // more header payload
                frameData[0] = 0x06 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
            }else{
                // last header payload
                if data != nil && data!.hasBytesAvailable {
                    if (mtuSize-1-bytesRead != 0){
                        // NOTE added this faile safe check, if 0 payload is read from stream hasBytesAvailable will return false !
                        bytesRead = frameData.withUnsafeMutableBufferPointer{ (ptr: inout  UnsafeMutableBufferPointer<UInt8>) -> Int in
                            return bytesRead + data!.read(ptr.baseAddress!+(1+bytesRead), maxLength: mtuSize-1-bytesRead)
                        }
                    }
                    if data!.hasBytesAvailable && bytesRead == (mtuSize-1) {
                        // more data payload
                        frameData[0] = 0x06 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
                    } else {
                        frameData[0] = 0x02 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
                    }
                } else {
                    frameData[0] = 0x02 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
                }
            }
            packet = Data.init(bytes: &frameData, count: bytesRead + 1)
        } else if data != nil && data!.hasBytesAvailable {
            var frameData = [UInt8](repeating: 0, count: mtuSize)
            let bytesRead = frameData.withUnsafeMutableBufferPointer{ (ptr: inout  UnsafeMutableBufferPointer<UInt8>) -> Int in
                return data!.read(ptr.baseAddress!+1, maxLength: mtuSize-1)
            }
            // note added failsafe check for bytes actually read
            if data!.hasBytesAvailable && bytesRead == (mtuSize-1) {
                // more data payload
                frameData[0] = 0x06 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
            }else{
                // last data payload
                frameData[0] = 0x02 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
            }
            packet = Data(bytes: &frameData, count: bytesRead + 1)
        } else {
            // 0 payload
            var frameData = [UInt8](repeating: 0, count: 1)
            frameData[0] = 0x02 | UInt8(next) | UInt8(sequenceNumber.getSeq() << 4)
            packet = Data(bytes: &frameData, count: 1)
        }
        sequenceNumber.increment()
        return packet
    }
    
    /// Generate list of air packets from data stream
    ///
    /// - Parameters:
    ///   - data: content to be split into air packets
    ///   - mtuSize: att mtu size
    ///   - sequenceNumber: RFC76 ring counter
    /// - Returns: list of air packets
    public static func buildRfc76MessageFrameAll(_ data: InputStream, mtuSize: Int, sequenceNumber: BlePsFtpRfc76SequenceNumber) -> [Data] {
        #if canImport(PolarBleSdkShared)
        if sequenceNumber.getSeq() == 0,
           let sharedFrames = SharedPsFtpByteCodec.splitRfc76Frames(data, mtuSize: mtuSize) {
            for _ in sharedFrames {
                sequenceNumber.increment()
            }
            return sharedFrames
        }
        #endif
        var next: Int=0
        var requs = [Data]()
        var more = true
        repeat{
            //
            let packet = buildRfc76MessageFrame(data, next: next, mtuSize: mtuSize, sequenceNumber:  sequenceNumber)
            more = (packet[0] & 0x06) == 0x06
            requs.append(packet)
            next = 1
        } while more
        return requs
    }
    
    /// Function to process RFC76 message header check rfc spec for more details
    ///
    /// - Parameter packet: air packet
    /// - Returns: @see PftpRfc76ResponseHeader
    public static func processRfc76MessageFrame(_ packet: Data) throws -> BlePsFtpRfc76Frame {
        let rfc76Frame = BlePsFtpRfc76Frame()
        try processRfc76MessageFrame(rfc76Frame, packet: packet)
        return rfc76Frame
    }
    
    ///
    ///
    /// - Parameters:
    ///   - header: RF76 container
    ///   - packet: air packet
    private static func processRfc76MessageFrame(_ header: BlePsFtpRfc76Frame, packet: Data) throws {
        if (packet.isEmpty) {
            throw BlePsFtpUtility.RFC76FrameProcessError.frameIsEmpty
        }

        #if canImport(PolarBleSdkShared)
        if let sharedFrame = SharedPsFtpByteCodec.decodedRfc76Frame(packet) {
            header.next = sharedFrame.next
            header.status = sharedFrame.status
            header.sequenceNumber = sharedFrame.sequenceNumber
            header.error = sharedFrame.error
            header.payload = sharedFrame.payload
            return
        }
        #endif
        
        let ptr = (packet as NSData).bytes.bindMemory(to: UInt8.self, capacity: packet.count)
        header.next = (Int)(ptr[0] & 0x01)
        header.status = (Int)((ptr[0] & 0x06) >> 1)
        header.sequenceNumber = (Int)(ptr[0] >> 4)
        if( header.status == RFC76_STATUS_ERROR_OR_RESPONSE ){
            if(packet.count == RFC76_HEADER_SIZE + RFC76_ERROR_DATA_SIZE) {
                header.error = Int(ptr[1]) | (Int(ptr[2]) << 8)
            }
        } else {
            if (packet.count > RFC76_HEADER_SIZE) {
                header.payload = packet.subdata(in: 1..<(packet.count))
            } else if (packet.count == RFC76_HEADER_SIZE && (header.status == RFC76_STATUS_LAST || header.status == RFC76_STATUS_MORE)) {
                // header only packet with no payload is valid for LAST and MORE status, device may signal end of data with an empty MORE chunk before the final LAST frame.
                BleLogger.trace("Received response from device has header only, no payload. Status: \(header.status)")
            }
            else {
                throw BlePsFtpUtility.RFC76FrameProcessError.frameHasNoPayload
            }
        }
    }
}

public extension BlePsFtpException { var localizedDescription: String { return "The operation couldn't be completed. (PolarBleSdk.BleGattException.\(self)" } }

#if canImport(PolarBleSdkShared)
private extension BlePsFtpUtility.MessageType {
    var sharedName: String {
        switch self {
        case .request:
            return "request"
        case .query:
            return "query"
        case .notification:
            return "notification"
        }
    }
}

private struct SharedRfc76Frame {
    let next: Int
    let status: Int
    let sequenceNumber: Int
    let error: Int?
    let payload: Data
}

private enum SharedPsFtpByteCodec {
    static func completeMessageStream(type: BlePsFtpUtility.MessageType, header: Data?, id: Int) -> Data? {
        return Data(hexBytes: PolarIosSharedBridge.shared.psFtpCompleteMessageStreamHex(type: type.sharedName, headerHex: (header ?? Data()).hexString, dataHex: "", idValue: Int32(id)))
    }

    static func decodedRfc76Frame(_ packet: Data) -> SharedRfc76Frame? {
        let fields = PolarIosSharedBridge.shared.psFtpDecodedRfc76Frame(frameHex: packet.hexString).split(separator: ",", omittingEmptySubsequences: false)
        guard fields.count == 5,
              let next = Int(fields[0]),
              let status = Int(fields[1]),
              let sequenceNumber = Int(fields[2]),
              let payload = Data(hexBytes: String(fields[4])) else {
            return nil
        }
        return SharedRfc76Frame(
            next: next,
            status: status,
            sequenceNumber: sequenceNumber,
            error: fields[3].isEmpty ? nil : Int(fields[3]),
            payload: payload
        )
    }

    static func splitRfc76Frames(_ stream: InputStream, mtuSize: Int) -> [Data]? {
        let payload = Data(readingRemaining: stream)
        let frameHexValues = PolarIosSharedBridge.shared.psFtpSplitRfc76FramesHex(payloadHex: payload.hexString, mtu: Int32(mtuSize)).split(separator: "|", omittingEmptySubsequences: false)
        let frames = frameHexValues.compactMap { Data(hexBytes: String($0)) }
        return frames.count == frameHexValues.count ? frames : nil
    }
}

private extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }

    init?(hexBytes: String) {
        guard hexBytes.count % 2 == 0 else { return nil }
        var bytes = [UInt8]()
        var index = hexBytes.startIndex
        while index < hexBytes.endIndex {
            let nextIndex = hexBytes.index(index, offsetBy: 2)
            guard let byte = UInt8(hexBytes[index..<nextIndex], radix: 16) else {
                return nil
            }
            bytes.append(byte)
            index = nextIndex
        }
        self = Data(bytes)
    }
}

private extension Data {
    init(readingRemaining stream: InputStream) {
        var output = Data()
        var buffer = [UInt8](repeating: 0, count: 512)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count <= 0 { break }
            output.append(buffer, count: count)
        }
        self = output
    }
}
#endif
