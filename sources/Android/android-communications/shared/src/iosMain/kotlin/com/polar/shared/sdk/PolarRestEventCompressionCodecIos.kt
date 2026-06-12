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
internal actual object PolarRestEventCompressionCodec {
    actual fun compressedPayloads(payloads: List<ByteArray>): List<ByteArray> {
        return payloads.map { payload -> inflateZlibOrNull(payload) ?: payload }
    }

    private fun inflateZlibOrNull(input: ByteArray): ByteArray? {
        if (input.isEmpty()) return null
        return memScoped {
            val stream = alloc<z_stream>()
            input.usePinned { pinnedInput ->
                stream.next_in = pinnedInput.addressOf(0).reinterpret()
                stream.avail_in = input.size.convert()

                if (inflateInit2(stream.ptr, MAX_WBITS + 32) != Z_OK) return@memScoped null

                val buffer = ByteArray(1024)
                var output = ByteArray(0)
                try {
                    var status = Z_OK
                    do {
                        buffer.usePinned { pinnedBuffer ->
                            stream.next_out = pinnedBuffer.addressOf(0).reinterpret<UByteVar>()
                            stream.avail_out = buffer.size.convert()
                            status = inflate(stream.ptr, Z_NO_FLUSH)
                            if (status != Z_OK && status != Z_STREAM_END) return@memScoped null
                            val inflatedBytes = buffer.size - stream.avail_out.toInt()
                            if (inflatedBytes > 0) {
                                output += buffer.copyOf(inflatedBytes)
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
