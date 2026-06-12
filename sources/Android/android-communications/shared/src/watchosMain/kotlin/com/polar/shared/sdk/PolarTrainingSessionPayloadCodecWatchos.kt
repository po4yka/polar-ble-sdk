package com.polar.shared.sdk

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.MAX_WBITS
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
internal actual object PolarTrainingSessionPayloadCodec {
    actual fun decodeGzipPayload(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "Empty gzip payload" }
        return memScoped {
            val stream = alloc<z_stream>()
            payload.usePinned { pinnedInput ->
                stream.next_in = pinnedInput.addressOf(0).reinterpret()
                stream.avail_in = payload.size.convert()
                val initStatus = inflateInit2(stream.ptr, MAX_WBITS + 32)
                require(initStatus == Z_OK) { "Failed to initialize gzip decoder: $initStatus" }
                val buffer = ByteArray(1024)
                var output = ByteArray(0)
                try {
                    var status = Z_OK
                    do {
                        buffer.usePinned { pinnedBuffer ->
                            stream.next_out = pinnedBuffer.addressOf(0).reinterpret<UByteVar>()
                            stream.avail_out = buffer.size.convert()
                            status = inflate(stream.ptr, Z_NO_FLUSH)
                            require(status == Z_OK || status == Z_STREAM_END) { "Failed to decode gzip payload: $status" }
                            val decodedBytes = buffer.size - stream.avail_out.toInt()
                            if (decodedBytes > 0) {
                                output += buffer.copyOf(decodedBytes)
                            }
                        }
                    } while (status != Z_STREAM_END && stream.avail_out.toInt() == 0)
                    output
                } finally {
                    inflateEnd(stream.ptr)
                }
            }
        }
    }
}
