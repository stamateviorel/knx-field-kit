package dev.knxfieldkit.bus.secure

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages a KNXnet/IP Secure session (ISO 22510) for unicast Tunneling and multicast Routing.
 *
 * Implements:
 * - SECURE_WRAPPER (service 0x0950) encapsulation and decapsulation.
 * - Monotonic 48-bit transmit sequence counter and receive freshness tracking.
 * - Session handshake frames (SESSION_REQUEST 0x0951, SESSION_RESPONSE 0x0952, SESSION_AUTHENTICATE 0x0953).
 * - Timer synchronization structures (TIMER_NOTIFY 0x0955).
 */
class KnxSecureIpSession(
    val sessionId: Int,
    val sessionKey: ByteArray,
    val userId: Int = 1,
    val macLength: Int = 8,
    initialTxSequence: Long = 1L,
) {
    init {
        require(sessionKey.size == 16) { "AES session key must be 16 bytes, got ${sessionKey.size}" }
        require(macLength in listOf(8, 16)) { "IP Secure MAC length must be 8 or 16 bytes, got $macLength" }
    }

    private val txSequenceCounter = AtomicLong(initialTxSequence)
    private val rxSequenceTracker = AtomicLong(0L)

    val currentTxSequence: Long get() = txSequenceCounter.get()
    val lastRxSequence: Long get() = rxSequenceTracker.get()

    /**
     * Builds the 13-byte Nonce for IP Secure AES-128-CCM:
     * - `bytes 0..1`: Session ID (2 bytes, big-endian)
     * - `bytes 2..7`: Sequence counter (6 bytes / 48-bit big-endian)
     * - `bytes 8..9`: User ID / Sender context (2 bytes, big-endian)
     * - `bytes 10..12`: Reserved (0x00, 0x00, 0x00)
     */
    fun buildIpSecureNonce(seq: Long): ByteArray {
        val nonce = ByteArray(13)
        nonce[0] = ((sessionId ushr 8) and 0xFF).toByte()
        nonce[1] = (sessionId and 0xFF).toByte()
        for (i in 0 until 6) {
            val shift = (5 - i) * 8
            nonce[2 + i] = ((seq ushr shift) and 0xFF).toByte()
        }
        nonce[8] = ((userId ushr 8) and 0xFF).toByte()
        nonce[9] = (userId and 0xFF).toByte()
        nonce[10] = 0x00
        nonce[11] = 0x00
        nonce[12] = 0x00
        return nonce
    }

    private fun buildAad(seq: Long): ByteArray {
        val aad = ByteArray(8)
        aad[0] = ((sessionId ushr 8) and 0xFF).toByte()
        aad[1] = (sessionId and 0xFF).toByte()
        for (i in 0 until 6) {
            val shift = (5 - i) * 8
            aad[2 + i] = ((seq ushr shift) and 0xFF).toByte()
        }
        return aad
    }

    /**
     * Encapsulates an inner plain KNXnet/IP frame into a SECURE_WRAPPER (0x0950) packet.
     */
    fun wrap(innerKnxNetIpFrame: ByteArray): ByteArray {
        val seq = txSequenceCounter.getAndIncrement()
        val nonce = buildIpSecureNonce(seq)
        val aad = buildAad(seq)
        val ccm = KnxAesCcm.encrypt(
            key = sessionKey,
            nonce = nonce,
            plaintext = innerKnxNetIpFrame,
            aad = aad,
            macLength = macLength,
        )

        val totalLen = 6 + 8 + ccm.ciphertext.size + macLength
        val out = ByteArray(totalLen)
        // KNXnet/IP Header (6 bytes)
        out[0] = 0x06
        out[1] = 0x10
        out[2] = ((SERVICE_SECURE_WRAPPER ushr 8) and 0xFF).toByte()
        out[3] = (SERVICE_SECURE_WRAPPER and 0xFF).toByte()
        out[4] = ((totalLen ushr 8) and 0xFF).toByte()
        out[5] = (totalLen and 0xFF).toByte()

        // Secure Wrapper Header (8 bytes: 2 bytes Session ID + 6 bytes Sequence counter)
        out[6] = ((sessionId ushr 8) and 0xFF).toByte()
        out[7] = (sessionId and 0xFF).toByte()
        for (i in 0 until 6) {
            val shift = (5 - i) * 8
            out[8 + i] = ((seq ushr shift) and 0xFF).toByte()
        }

        // Ciphertext + MAC
        System.arraycopy(ccm.ciphertext, 0, out, 14, ccm.ciphertext.size)
        System.arraycopy(ccm.macTag, 0, out, 14 + ccm.ciphertext.size, macLength)
        return out
    }

    /**
     * Decapsulates a SECURE_WRAPPER (0x0950) packet, verifying freshness and authenticity.
     *
     * @return Decrypted inner KNXnet/IP frame.
     * @throws SecurityException If session ID does not match, sequence is replayed, or MAC fails.
     */
    fun unwrap(secureWrapperPacket: ByteArray): ByteArray {
        require(secureWrapperPacket.size >= 14 + macLength) { "SECURE_WRAPPER packet too short" }
        require(secureWrapperPacket[0] == 0x06.toByte() && secureWrapperPacket[1] == 0x10.toByte()) { "Invalid KNXnet/IP header" }

        val serviceType = ((secureWrapperPacket[2].toInt() and 0xFF) shl 8) or (secureWrapperPacket[3].toInt() and 0xFF)
        require(serviceType == SERVICE_SECURE_WRAPPER) { "Not a SECURE_WRAPPER frame (service=0x%04X)".format(serviceType) }

        val rxSessionId = ((secureWrapperPacket[6].toInt() and 0xFF) shl 8) or (secureWrapperPacket[7].toInt() and 0xFF)
        require(rxSessionId == sessionId) { "Mismatched Session ID: expected 0x%04X, got 0x%04X".format(sessionId, rxSessionId) }

        var rxSeq = 0L
        for (i in 0 until 6) {
            rxSeq = (rxSeq shl 8) or (secureWrapperPacket[8 + i].toLong() and 0xFF)
        }

        val last = rxSequenceTracker.get()
        if (rxSeq <= last && last != 0L) {
            throw SecurityException("Replayed sequence counter: incoming $rxSeq <= last $last")
        }

        val cipherLen = secureWrapperPacket.size - 14 - macLength
        val ciphertext = secureWrapperPacket.copyOfRange(14, 14 + cipherLen)
        val macTag = secureWrapperPacket.copyOfRange(secureWrapperPacket.size - macLength, secureWrapperPacket.size)

        val nonce = buildIpSecureNonce(rxSeq)
        val aad = buildAad(rxSeq)

        val plaintext = KnxAesCcm.decrypt(
            key = sessionKey,
            nonce = nonce,
            ciphertext = ciphertext,
            macTag = macTag,
            aad = aad,
            macLength = macLength,
        )

        rxSequenceTracker.set(rxSeq)
        return plaintext
    }

    companion object {
        const val SERVICE_SECURE_WRAPPER = 0x0950
        const val SERVICE_SESSION_REQUEST = 0x0951
        const val SERVICE_SESSION_RESPONSE = 0x0952
        const val SERVICE_SESSION_AUTHENTICATE = 0x0953
        const val SERVICE_SESSION_STATUS = 0x0954
        const val SERVICE_TIMER_NOTIFY = 0x0955

        data class SessionResponse(
            val sessionId: Int,
            val serverRandom: ByteArray,
            val mac: ByteArray,
        )

        data class TimerNotifyData(
            val sessionId: Int,
            val timestampCounter: Long,
            val serialNumberHex: String,
        )

        /**
         * Builds a standard KNXnet/IP SESSION_REQUEST (0x0951) packet.
         */
        fun buildSessionRequest(
            clientRandom: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
        ): ByteArray {
            val totalLen = 6 + clientRandom.size
            return byteArrayOf(
                0x06, 0x10,
                ((SERVICE_SESSION_REQUEST ushr 8) and 0xFF).toByte(),
                (SERVICE_SESSION_REQUEST and 0xFF).toByte(),
                ((totalLen ushr 8) and 0xFF).toByte(),
                (totalLen and 0xFF).toByte(),
            ) + clientRandom
        }

        /**
         * Builds a standard KNXnet/IP SESSION_RESPONSE (0x0952) packet.
         */
        fun buildSessionResponse(
            sessionId: Int,
            serverRandom: ByteArray,
            mac: ByteArray = ByteArray(8),
        ): ByteArray {
            val totalLen = 6 + 2 + serverRandom.size + mac.size
            val out = ByteArray(totalLen)
            out[0] = 0x06
            out[1] = 0x10
            out[2] = ((SERVICE_SESSION_RESPONSE ushr 8) and 0xFF).toByte()
            out[3] = (SERVICE_SESSION_RESPONSE and 0xFF).toByte()
            out[4] = ((totalLen ushr 8) and 0xFF).toByte()
            out[5] = (totalLen and 0xFF).toByte()
            out[6] = ((sessionId ushr 8) and 0xFF).toByte()
            out[7] = (sessionId and 0xFF).toByte()
            System.arraycopy(serverRandom, 0, out, 8, serverRandom.size)
            System.arraycopy(mac, 0, out, 8 + serverRandom.size, mac.size)
            return out
        }

        /**
         * Parses a SESSION_RESPONSE (0x0952) packet.
         */
        fun parseSessionResponse(bytes: ByteArray): SessionResponse? {
            if (bytes.size < 16 || bytes[0] != 0x06.toByte() || bytes[1] != 0x10.toByte()) return null
            val svc = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            if (svc != SERVICE_SESSION_RESPONSE) return null
            val sId = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
            val random = bytes.copyOfRange(8, 24)
            val mac = if (bytes.size > 24) bytes.copyOfRange(24, bytes.size) else ByteArray(0)
            return SessionResponse(sId, random, mac)
        }

        /**
         * Builds a TIMER_NOTIFY (0x0955) synchronization frame.
         */
        fun buildTimerNotify(
            sessionId: Int,
            timestampCounter: Long,
            serialNumber: ByteArray = ByteArray(6),
        ): ByteArray {
            val totalLen = 6 + 2 + 6 + 6
            val out = ByteArray(totalLen)
            out[0] = 0x06
            out[1] = 0x10
            out[2] = ((SERVICE_TIMER_NOTIFY ushr 8) and 0xFF).toByte()
            out[3] = (SERVICE_TIMER_NOTIFY and 0xFF).toByte()
            out[4] = ((totalLen ushr 8) and 0xFF).toByte()
            out[5] = (totalLen and 0xFF).toByte()
            out[6] = ((sessionId ushr 8) and 0xFF).toByte()
            out[7] = (sessionId and 0xFF).toByte()
            for (i in 0 until 6) {
                val shift = (5 - i) * 8
                out[8 + i] = ((timestampCounter ushr shift) and 0xFF).toByte()
            }
            val copyLen = minOf(6, serialNumber.size)
            System.arraycopy(serialNumber, 0, out, 14, copyLen)
            return out
        }

        /**
         * Parses a TIMER_NOTIFY (0x0955) frame.
         */
        fun parseTimerNotify(bytes: ByteArray): TimerNotifyData? {
            if (bytes.size < 20 || bytes[0] != 0x06.toByte() || bytes[1] != 0x10.toByte()) return null
            val svc = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            if (svc != SERVICE_TIMER_NOTIFY) return null
            val sId = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
            var counter = 0L
            for (i in 0 until 6) {
                counter = (counter shl 8) or (bytes[8 + i].toLong() and 0xFF)
            }
            val serialBytes = bytes.copyOfRange(14, 20)
            val hex = buildString {
                for (b in serialBytes) append("%02X".format(b.toInt() and 0xFF))
            }
            return TimerNotifyData(sId, counter, hex)
        }
    }
}
