package com.polar.shared.sdk

internal expect object PolarRestEventCompressionCodec {
    fun compressedPayloads(payloads: List<ByteArray>): List<ByteArray>
}
