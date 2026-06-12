package com.polar.shared.sdk

internal expect object PolarTrainingSessionPayloadCodec {
    fun decodeGzipPayload(payload: ByteArray): ByteArray
}
