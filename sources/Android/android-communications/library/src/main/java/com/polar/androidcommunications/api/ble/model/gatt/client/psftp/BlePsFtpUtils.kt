package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import org.apache.commons.io.IOUtils
import protocol.PftpError.PbPFtpError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/**
 * RFC76 and RFC 60 related utils
 */
object BlePsFtpUtils {
    private val TAG: String = BlePsFtpUtils::class.java.simpleName
    val RFC77_PFTP_SERVICE: UUID = UUID.fromString("0000FEEE-0000-1000-8000-00805f9b34fb")
    const val PFTP_SERVICE_16BIT_UUID: String = "FEEE"
    val RFC77_PFTP_MTU_CHARACTERISTIC: UUID =
        UUID.fromString("FB005C51-02E7-F387-1CAD-8ACD2D8DF0C8")
    val RFC77_PFTP_D2H_CHARACTERISTIC: UUID =
        UUID.fromString("FB005C52-02E7-F387-1CAD-8ACD2D8DF0C8")
    val RFC77_PFTP_H2D_CHARACTERISTIC: UUID =
        UUID.fromString("FB005C53-02E7-F387-1CAD-8ACD2D8DF0C8")

    const val RFC76_HEADER_SIZE: Int = 1
    const val RFC76_STATUS_MORE: Int = 0x03
    const val RFC76_STATUS_LAST: Int = 0x01
    const val RFC76_STATUS_ERROR_OR_RESPONSE: Int = 0x00

    /**
     * Compines header(protobuf typically) and data(for write operation only, for other operations = null)
     *
     * @param header typically protocol buffer data
     * @param data   content to be transmitted
     * @param type   @see MessageType
     * @param id     for query or notification only
     * @return complete message stream
     * @throws IOException thrown by IOUtils.copy
     */
    @Throws(IOException::class)
    fun makeCompleteMessageStream(
        header: ByteArrayInputStream?,
        data: ByteArrayInputStream?,
        type: MessageType,
        id: Int
    ): ByteArrayInputStream {
        val sharedHeader = header?.let { IOUtils.toByteArray(it) } ?: ByteArray(0)
        val sharedData = data?.let { IOUtils.toByteArray(it) } ?: ByteArray(0)
        return ByteArrayInputStream(
            SharedPsFtpByteCodec.encodeCompleteMessageStream(
                type = type,
                header = sharedHeader,
                idValue = id,
                data = sharedData
            )
        )

    }

    /**
     * Generate single air packet from data content
     *
     * @param data           content to be transmitted
     * @param next           bit to indicate 0=first or 1=next air packet
     * @param mtuSize        att mtu size used
     * @param sequenceNumber RFC76 ring counter
     * @return air packet
     */
    fun buildRfc76MessageFrame(
        data: ByteArrayInputStream,
        next: Int,
        mtuSize: Int,
        sequenceNumber: Rfc76SequenceNumber
    ): ByteArray {
        val payloadSize = mtuSize - RFC76_HEADER_SIZE
        val packet: ByteArray = if (data.available() > payloadSize) {
            val chunk = ByteArray(payloadSize)
            data.read(chunk, 0, chunk.size)
            SharedPsFtpByteCodec.encodeRfc76FrameChunk(
                chunk = chunk,
                hasMore = true,
                next = next,
                sequenceNumber = sequenceNumber.seq.toInt()
            )
        } else if (data.available() > 0) {
            val chunk = ByteArray(data.available())
            data.read(chunk, 0, chunk.size)
            SharedPsFtpByteCodec.encodeRfc76FrameChunk(
                chunk = chunk,
                hasMore = false,
                next = next,
                sequenceNumber = sequenceNumber.seq.toInt()
            )
        } else {
            SharedPsFtpByteCodec.encodeRfc76FrameChunk(
                chunk = ByteArray(0),
                hasMore = false,
                next = next,
                sequenceNumber = sequenceNumber.seq.toInt()
            )
        }
        sequenceNumber.increment()
        return packet
    }

    /**
     * Generate list of air packets from data stream
     *
     * @param data           content to be split into air packets
     * @param mtuSize        att mtu size
     * @param sequenceNumber RFC76 ring counter
     * @return list of air packets
     */
    fun buildRfc76MessageFrameAll(
        data: ByteArrayInputStream,
        mtuSize: Int,
        sequenceNumber: Rfc76SequenceNumber
    ): MutableList<ByteArray> {
        if (sequenceNumber.seq == 0L) {
            val packets = SharedPsFtpByteCodec.splitRfc76Frames(IOUtils.toByteArray(data), mtuSize).toMutableList()
            repeat(packets.size) {
                sequenceNumber.increment()
            }
            return packets
        }
        val packets: MutableList<ByteArray> = ArrayList()
        var next = 0
        do {
            val temp = next // workaround for stupid java translator idiotisim
            val packet = buildRfc76MessageFrame(data, temp, mtuSize, sequenceNumber)
            packets.add(packet)
            next = 1
        } while (data.available() > 0)
        return packets
    }

    /**
     * Function to process RFC76 message header check rfc spec for more details
     *
     * @param packet air packet
     * @return @see PftpRfc76ResponseHeader
     */
    fun processRfc76MessageFrameHeader(packet: ByteArray): PftpRfc76ResponseHeader {
        val header = PftpRfc76ResponseHeader()
        processRfc76MessageFrameHeader(header, packet)
        return header
    }

    /**
     * @param header RF76 header container
     * @param packet air packet
     */
    fun processRfc76MessageFrameHeader(header: PftpRfc76ResponseHeader, packet: ByteArray) {
        val decoded = SharedPsFtpByteCodec.decodeRfc76Frame(packet)
        header.next = decoded.next
        header.status = decoded.status
        header.sequenceNumber = decoded.sequenceNumber.toLong()
        if (decoded.status == RFC76_STATUS_ERROR_OR_RESPONSE) {
            header.error = decoded.androidErrorCode ?: 0
        } else {
            header.payload = decoded.payload
        }
    }

    class Rfc76SequenceNumber {
        var seq: Long = 0

        fun increment() {
            if (seq < 0x0F) {
                this.seq += 1
            } else {
                this.seq = 0
            }
        }
    }

    /**
     * PSFTP EXCEPTIONS
     */
    class PftpOperationTimeout(detailMessage: String?) : Exception(detailMessage)

    /**
     * one of PbPftpError codes
     */
    class PftpResponseError(detailMessage: String, val error: Int) :
        Exception(formatMessage(detailMessage, error)) {
        val errorCode: PbPFtpError?
            /**
             * Return typed enum for the error code, or null if the code is unknown
             */
            get() = PbPFtpError.forNumber(error)

        val errorName: String?
            /**
             * Return enum name or null if unknown.
             */
            get() {
                val errorEnum = errorCode
                return errorEnum?.name
            }

        companion object {
            private fun formatMessage(detailMessage: String, error: Int): String {
                val errorEnum = PbPFtpError.forNumber(error)
                if (errorEnum != null) {
                    return detailMessage + " Error: " + error + " (" + errorEnum.name + ")"
                }
                return "$detailMessage Error: $error"
            }
        }
    }

    class PftpNotificationMessage {
        /**
         * One of PbPftpDevToHostNotifications
         */
        @JvmField var id: Int = 0
        @JvmField var byteArrayOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()
    }

    class PftpRfc76ResponseHeader {
        var next: Int = 0
        var status: Int = 0
        var error: Int = 0
        var payload: ByteArray? = null
        var sequenceNumber: Long = 0

        override fun toString(): String {
            return "first: $next length: $status error: $error payload: " + (if (payload != null) String(
                payload!!
            ) else "null seq: $sequenceNumber")
        }
    }

    enum class MessageType {
        REQUEST,
        QUERY,
        NOTIFICATION
    }
}

private object SharedPsFtpByteCodec {
    fun encodeCompleteMessageStream(type: BlePsFtpUtils.MessageType, header: ByteArray, idValue: Int, data: ByteArray): ByteArray {
        return PolarRuntimePlannerAdapter.psFtpEncodeCompleteMessageStream(
            type = when (type) {
                BlePsFtpUtils.MessageType.REQUEST -> "request"
                BlePsFtpUtils.MessageType.QUERY -> "query"
                BlePsFtpUtils.MessageType.NOTIFICATION -> "notification"
            },
            header = header,
            idValue = idValue,
            data = data
        )
    }

    fun splitRfc76Frames(payload: ByteArray, mtuSize: Int): List<ByteArray> {
        return PolarRuntimePlannerAdapter.psFtpSplitRfc76Frames(payload, mtuSize)
    }

    fun encodeRfc76FrameChunk(chunk: ByteArray, hasMore: Boolean, next: Int, sequenceNumber: Int): ByteArray {
        return PolarRuntimePlannerAdapter.psFtpEncodeRfc76FrameChunk(chunk, hasMore, next, sequenceNumber)
    }

    fun decodeRfc76Frame(packet: ByteArray): PolarRuntimePlannerAdapter.PlannedPsFtpFrame {
        return PolarRuntimePlannerAdapter.psFtpDecodeRfc76Frame(packet)
    }
}
