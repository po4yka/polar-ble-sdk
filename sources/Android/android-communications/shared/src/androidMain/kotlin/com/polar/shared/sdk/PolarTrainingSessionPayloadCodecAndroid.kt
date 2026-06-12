package com.polar.shared.sdk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

internal actual object PolarTrainingSessionPayloadCodec {
    actual fun decodeGzipPayload(payload: ByteArray): ByteArray {
        val buffer = ByteArray(10 * 1024)
        ByteArrayInputStream(payload).use { inputStream ->
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
