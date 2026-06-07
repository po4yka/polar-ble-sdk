package com.polar.shared.sdk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

internal actual object PolarRestEventCompressionCodec {
    actual fun compressedPayloads(payloads: List<ByteArray>): List<ByteArray> {
        return payloads.map(::inflateGzip)
    }

    private fun inflateGzip(input: ByteArray): ByteArray {
        val buffer = ByteArray(10 * 1024)
        ByteArrayInputStream(input).use { inputStream ->
            GZIPInputStream(inputStream).use { gzipStream ->
                ByteArrayOutputStream().use { outputStream ->
                    while (true) {
                        val read = gzipStream.read(buffer)
                        if (read == -1) break
                        outputStream.write(buffer, 0, read)
                    }
                    return outputStream.toByteArray()
                }
            }
        }
    }
}
