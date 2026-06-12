package com.polar.shared.sdk

private const val PROTOBUF_WIRE_VARINT = 0
private const val PROTOBUF_WIRE_FIXED64 = 1
private const val PROTOBUF_WIRE_LENGTH_DELIMITED = 2
private const val PROTOBUF_WIRE_FIXED32 = 5
private const val AUTOMATIC_MEASUREMENT_OFF = 0
private const val AUTOMATIC_MEASUREMENT_ALWAYS_ON = 1

data class PolarUserDeviceSettingsFields(
    val deviceLocation: Int? = null,
    val usbConnectionMode: Boolean? = null,
    val automaticTrainingDetectionMode: Boolean? = null,
    val automaticTrainingDetectionSensitivity: Int? = null,
    val minimumTrainingDurationSeconds: Int? = null,
    val telemetryEnabled: Boolean? = null,
    val autosFilesEnabled: Boolean? = null
)

data class PolarSerializedUserDeviceSettingsFields(
    val deviceLocation: Int?,
    val hasLastModified: Boolean,
    val lastModifiedTrusted: Boolean,
    val usbConnectionMode: String?,
    val automaticTrainingDetectionMode: String?,
    val automaticTrainingDetectionSensitivity: Int?,
    val minimumTrainingDurationSeconds: Int?,
    val hasTelemetryEnabled: Boolean,
    val telemetryEnabled: Boolean?,
    val autosFilesEnabled: Boolean?,
    val omittedOptionalPolicy: String = "preserve-protobuf-presence",
    val telemetryWritePolicy: String = "write-explicit-telemetry"
)

data class PolarUserDeviceSettingsTimestamp(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val seconds: Int,
    val millis: Int,
    val trusted: Boolean
)

object PolarUserDeviceSettingsModels {
    fun parseProtoBytes(bytes: ByteArray): PolarUserDeviceSettingsFields {
        var deviceLocation: Int? = null
        var usbConnectionMode: String? = null
        var automaticTrainingDetectionMode: String? = null
        var automaticTrainingDetectionSensitivity: Int? = null
        var minimumTrainingDurationSeconds: Int? = null
        var telemetryEnabled: Boolean? = null
        var autosFilesEnabled: Boolean? = null
        ProtobufReader(bytes).forEachField { fieldNumber, wireType, reader ->
            when (fieldNumber) {
                1 -> reader.readLengthDelimitedField(wireType) { general ->
                    general.forEachField { generalFieldNumber, generalWireType, generalReader ->
                        if (generalFieldNumber == 15) {
                            deviceLocation = generalReader.readVarintField(generalWireType).toInt()
                        } else {
                            generalReader.skip(generalWireType)
                        }
                    }
                }
                21 -> reader.readLengthDelimitedField(wireType) { automaticMeasurement ->
                    automaticMeasurement.forEachField { automaticFieldNumber, automaticWireType, automaticReader ->
                        when (automaticFieldNumber) {
                            1 -> automaticReader.readLengthDelimitedField(automaticWireType) { automaticOhr ->
                                automaticOhr.forEachField { ohrFieldNumber, ohrWireType, ohrReader ->
                                    if (ohrFieldNumber == 1) {
                                        autosFilesEnabled = ohrReader.readVarintField(ohrWireType).toInt() != AUTOMATIC_MEASUREMENT_OFF
                                    } else {
                                        ohrReader.skip(ohrWireType)
                                    }
                                }
                            }
                            2 -> automaticReader.readLengthDelimitedField(automaticWireType) { trainingDetection ->
                                trainingDetection.forEachField { trainingFieldNumber, trainingWireType, trainingReader ->
                                    when (trainingFieldNumber) {
                                        1 -> automaticTrainingDetectionMode = automaticTrainingDetectionModeName(trainingReader.readVarintField(trainingWireType).toInt())
                                        2 -> automaticTrainingDetectionSensitivity = trainingReader.readVarintField(trainingWireType).toInt()
                                        3 -> minimumTrainingDurationSeconds = trainingReader.readVarintField(trainingWireType).toInt()
                                        else -> trainingReader.skip(trainingWireType)
                                    }
                                }
                            }
                            else -> automaticReader.skip(automaticWireType)
                        }
                    }
                }
                26 -> reader.readLengthDelimitedField(wireType) { telemetry ->
                    telemetry.forEachField { telemetryFieldNumber, telemetryWireType, telemetryReader ->
                        if (telemetryFieldNumber == 1) {
                            telemetryEnabled = telemetryReader.readVarintField(telemetryWireType) != 0L
                        } else {
                            telemetryReader.skip(telemetryWireType)
                        }
                    }
                }
                27 -> reader.readLengthDelimitedField(wireType) { usb ->
                    usb.forEachField { usbFieldNumber, usbWireType, usbReader ->
                        if (usbFieldNumber == 1) {
                            usbConnectionMode = usbConnectionModeName(usbReader.readVarintField(usbWireType).toInt())
                        } else {
                            usbReader.skip(usbWireType)
                        }
                    }
                }
                else -> reader.skip(wireType)
            }
        }
        return parsePresencePreservingFields(
            deviceLocation = deviceLocation,
            usbConnectionMode = usbConnectionMode,
            automaticTrainingDetectionMode = automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = minimumTrainingDurationSeconds,
            telemetryEnabled = telemetryEnabled,
            autosFilesEnabled = autosFilesEnabled
        )
    }

    fun buildProtoBytes(model: PolarUserDeviceSettingsFields, timestamp: PolarUserDeviceSettingsTimestamp, includeTelemetry: Boolean = true): ByteArray {
        val serialized = serializePresencePreservingFields(model)
        return ProtobufWriter().apply {
            writeMessage(1) {
                serialized.deviceLocation?.let { writeVarint(15, it.toLong()) }
            }
            val automaticMeasurement = ProtobufWriter().apply {
                serialized.autosFilesEnabled?.let { enabled ->
                    writeMessage(1) {
                        writeVarint(1, if (enabled) AUTOMATIC_MEASUREMENT_ALWAYS_ON.toLong() else AUTOMATIC_MEASUREMENT_OFF.toLong())
                    }
                }
                if (serialized.automaticTrainingDetectionMode != null || serialized.automaticTrainingDetectionSensitivity != null || serialized.minimumTrainingDurationSeconds != null) {
                    writeMessage(2) {
                        serialized.automaticTrainingDetectionMode?.let { mode -> writeVarint(1, automaticTrainingDetectionModeValue(mode)?.toLong() ?: error("Unexpected automatic training detection mode $mode")) }
                        serialized.automaticTrainingDetectionSensitivity?.let { writeVarint(2, it.toLong()) }
                        serialized.minimumTrainingDurationSeconds?.let { writeVarint(3, it.toLong()) }
                    }
                }
            }.toByteArray()
            if (automaticMeasurement.isNotEmpty()) {
                writeMessage(21, automaticMeasurement)
            }
            if (includeTelemetry && serialized.telemetryEnabled != null) {
                writeMessage(26) {
                    writeVarint(1, if (serialized.telemetryEnabled) 1L else 0L)
                }
            }
            serialized.usbConnectionMode?.let { mode ->
                writeMessage(27) {
                    writeVarint(1, usbConnectionModeValue(mode)?.toLong() ?: error("Unexpected USB connection mode $mode"))
                }
            }
            writeMessage(101) {
                writeMessage(1) {
                    writeVarint(1, timestamp.year.toLong())
                    writeVarint(2, timestamp.month.toLong())
                    writeVarint(3, timestamp.day.toLong())
                }
                writeMessage(2) {
                    writeVarint(1, timestamp.hour.toLong())
                    writeVarint(2, timestamp.minute.toLong())
                    writeVarint(3, timestamp.seconds.toLong())
                    writeVarint(4, timestamp.millis.toLong())
                }
                writeVarint(3, if (timestamp.trusted) 1L else 0L)
            }
        }.toByteArray()
    }

    fun parsePresencePreservingFields(
        deviceLocation: Int? = null,
        usbConnectionMode: String? = null,
        automaticTrainingDetectionMode: String? = null,
        automaticTrainingDetectionSensitivity: Int? = null,
        minimumTrainingDurationSeconds: Int? = null,
        telemetryEnabled: Boolean? = null,
        autosFilesEnabled: Boolean? = null
    ): PolarUserDeviceSettingsFields {
        return PolarUserDeviceSettingsFields(
            deviceLocation = deviceLocation,
            usbConnectionMode = usbConnectionMode?.toOnOffBoolean(),
            automaticTrainingDetectionMode = automaticTrainingDetectionMode?.toOnOffBoolean(),
            automaticTrainingDetectionSensitivity = automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = minimumTrainingDurationSeconds,
            telemetryEnabled = telemetryEnabled,
            autosFilesEnabled = autosFilesEnabled
        )
    }

    fun serializePresencePreservingFields(model: PolarUserDeviceSettingsFields): PolarSerializedUserDeviceSettingsFields {
        return PolarSerializedUserDeviceSettingsFields(
            deviceLocation = model.deviceLocation,
            hasLastModified = true,
            lastModifiedTrusted = true,
            usbConnectionMode = model.usbConnectionMode?.toOnOffName(),
            automaticTrainingDetectionMode = model.automaticTrainingDetectionMode?.toOnOffName(),
            automaticTrainingDetectionSensitivity = model.automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = model.minimumTrainingDurationSeconds,
            hasTelemetryEnabled = model.telemetryEnabled != null,
            telemetryEnabled = model.telemetryEnabled,
            autosFilesEnabled = model.autosFilesEnabled
        )
    }

    fun deviceLocationName(value: Int): String? {
        return when (value) {
            0 -> "UNDEFINED"
            1 -> "OTHER"
            2 -> "WRIST_LEFT"
            3 -> "WRIST_RIGHT"
            4 -> "NECKLACE"
            5 -> "CHEST"
            6 -> "UPPER_BACK"
            7 -> "FOOT_LEFT"
            8 -> "FOOT_RIGHT"
            9 -> "LOWER_ARM_LEFT"
            10 -> "LOWER_ARM_RIGHT"
            11 -> "UPPER_ARM_LEFT"
            12 -> "UPPER_ARM_RIGHT"
            13 -> "BIKE_MOUNT"
            else -> null
        }
    }

    fun deviceLocationValue(name: String): Int? {
        return when (name) {
            "UNDEFINED" -> 0
            "OTHER" -> 1
            "WRIST_LEFT" -> 2
            "WRIST_RIGHT" -> 3
            "NECKLACE" -> 4
            "CHEST" -> 5
            "UPPER_BACK" -> 6
            "FOOT_LEFT" -> 7
            "FOOT_RIGHT" -> 8
            "LOWER_ARM_LEFT" -> 9
            "LOWER_ARM_RIGHT" -> 10
            "UPPER_ARM_LEFT" -> 11
            "UPPER_ARM_RIGHT" -> 12
            "BIKE_MOUNT" -> 13
            else -> null
        }
    }

    fun usbConnectionModeName(value: Int): String? {
        return when (value) {
            1 -> "OFF"
            2 -> "ON"
            else -> null
        }
    }

    fun usbConnectionModeValue(name: String): Int? {
        return when (name) {
            "OFF" -> 1
            "ON" -> 2
            else -> null
        }
    }

    fun automaticTrainingDetectionModeName(value: Int): String? {
        return when (value) {
            0 -> "OFF"
            1 -> "ON"
            else -> null
        }
    }

    fun automaticTrainingDetectionModeValue(name: String): Int? {
        return when (name) {
            "OFF" -> 0
            "ON" -> 1
            else -> null
        }
    }

    fun automaticMeasurementStateName(enabled: Boolean): String {
        return if (enabled) "ALWAYS_ON" else "OFF"
    }

    fun automaticMeasurementStateEnabled(name: String): Boolean? {
        return when (name) {
            "ALWAYS_ON" -> true
            "OFF" -> false
            else -> null
        }
    }

    fun protobufPayloadFields(): List<String> {
        return listOf("protobufPayload=platform-built")
    }

    fun telemetryPayloadFields(enabled: Boolean): List<String> {
        return listOf("telemetryEnabled=$enabled")
    }

    fun deviceLocationPayloadFields(value: Int): List<String> {
        return listOf("deviceLocation=${deviceLocationName(value) ?: value}")
    }

    fun usbConnectionModePayloadFields(enabled: Boolean): List<String> {
        return listOf("usbConnectionMode=${usbConnectionModeName(if (enabled) 2 else 1) ?: if (enabled) "ON" else "OFF"}")
    }

    fun automaticTrainingDetectionPayloadFields(enabled: Boolean, sensitivity: Int, minimumDurationSeconds: Int): List<String> {
        return listOf(
            "automaticTrainingDetectionMode=${automaticTrainingDetectionModeName(if (enabled) 1 else 0) ?: if (enabled) "ON" else "OFF"}",
            "automaticTrainingDetectionSensitivity=$sensitivity",
            "minimumTrainingDurationSeconds=$minimumDurationSeconds"
        )
    }

    fun automaticOhrPayloadFields(enabled: Boolean): List<String> {
        return listOf("automaticOhrMeasurement=${automaticMeasurementStateName(enabled)}")
    }

    fun daylightSavingPayloadFields(): List<String> {
        return listOf("daylightSaving.nextDaylightSavingTime=present", "daylightSaving.offset=nonzero")
    }

    private fun String.toOnOffBoolean(): Boolean {
        return when (this) {
            "ON" -> true
            "OFF" -> false
            else -> error("Unexpected ON/OFF value $this")
        }
    }

    private fun Boolean.toOnOffName(): String {
        return if (this) "ON" else "OFF"
    }

}

private class ProtobufReader(private val bytes: ByteArray) {
    private var offset = 0

    fun forEachField(block: (fieldNumber: Int, wireType: Int, reader: ProtobufReader) -> Unit) {
        while (offset < bytes.size) {
            val tag = readRawVarint()
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            block(fieldNumber, wireType, this)
        }
    }

    fun readVarintField(wireType: Int): Long {
        require(wireType == PROTOBUF_WIRE_VARINT) { "Expected protobuf varint wire type but got $wireType" }
        return readRawVarint()
    }

    fun readLengthDelimitedField(wireType: Int, block: (ProtobufReader) -> Unit) {
        require(wireType == PROTOBUF_WIRE_LENGTH_DELIMITED) { "Expected protobuf length-delimited wire type but got $wireType" }
        val length = readRawVarint().toInt()
        require(length >= 0 && offset + length <= bytes.size) { "Invalid protobuf length $length at offset $offset" }
        block(ProtobufReader(bytes.copyOfRange(offset, offset + length)))
        offset += length
    }

    fun skip(wireType: Int) {
        when (wireType) {
            PROTOBUF_WIRE_VARINT -> readRawVarint()
            PROTOBUF_WIRE_FIXED64 -> offset += 8
            PROTOBUF_WIRE_LENGTH_DELIMITED -> {
                val length = readRawVarint().toInt()
                require(length >= 0 && offset + length <= bytes.size) { "Invalid protobuf length $length at offset $offset" }
                offset += length
            }
            PROTOBUF_WIRE_FIXED32 -> offset += 4
            else -> error("Unsupported protobuf wire type $wireType")
        }
        require(offset <= bytes.size) { "Protobuf field extends past payload end" }
    }

    private fun readRawVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            require(offset < bytes.size) { "Truncated protobuf varint" }
            val value = bytes[offset++].toInt() and 0xff
            result = result or ((value and 0x7f).toLong() shl shift)
            if ((value and 0x80) == 0) {
                return result
            }
            shift += 7
        }
        error("Malformed protobuf varint")
    }
}

private class ProtobufWriter {
    private val bytes = mutableListOf<Byte>()

    fun writeVarint(fieldNumber: Int, value: Long) {
        writeRawVarint((fieldNumber.toLong() shl 3) or PROTOBUF_WIRE_VARINT.toLong())
        writeRawVarint(value)
    }

    fun writeMessage(fieldNumber: Int, block: ProtobufWriter.() -> Unit) {
        writeMessage(fieldNumber, ProtobufWriter().apply(block).toByteArray())
    }

    fun writeMessage(fieldNumber: Int, payload: ByteArray) {
        writeRawVarint((fieldNumber.toLong() shl 3) or PROTOBUF_WIRE_LENGTH_DELIMITED.toLong())
        writeRawVarint(payload.size.toLong())
        payload.forEach { bytes += it }
    }

    fun toByteArray(): ByteArray {
        return bytes.toByteArray()
    }

    private fun writeRawVarint(value: Long) {
        var remaining = value
        while ((remaining and 0x7fL.inv()) != 0L) {
            bytes += (((remaining and 0x7fL) or 0x80L).toInt()).toByte()
            remaining = remaining ushr 7
        }
        bytes += remaining.toByte()
    }
}
