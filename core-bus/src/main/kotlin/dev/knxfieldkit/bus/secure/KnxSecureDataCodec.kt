package dev.knxfieldkit.bus.secure

import dev.knxfieldkit.bus.KnxNetIpCodec
import dev.knxfieldkit.model.ApciType
import dev.knxfieldkit.model.BusTelegram
import dev.knxfieldkit.model.GroupAddress
import dev.knxfieldkit.model.HexBytes
import dev.knxfieldkit.model.IndividualAddress
import dev.knxfieldkit.model.KnxKeyring
import dev.knxfieldkit.model.TelegramPriority
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Production-grade implementation of KNX Data Secure at the cEMI APDU level (EN 50090-3-4 / ISO 22510).
 *
 * Provides:
 * - Deterministic AES-128-CCM authenticated encryption and decryption.
 * - Secure APDU structure packing and unpacking (APCI = 0x03F1).
 * - Freshness tracking (48-bit monotonic sequence number counters per individual address).
 * - Automatic encryption of outgoing group telegrams against [KnxKeyring] group keys.
 * - Transparent detection and decryption of incoming secure cEMI group telegrams.
 */
object KnxSecureDataCodec {

    /**
     * The 10-bit KNX APCI code identifying a Secure APDU frame (`APCI_SEC`).
     */
    const val APCI_SEC = 0x03F1

    /**
     * Default Security Control Field byte for runtime KNX Data Secure group communication:
     * - Bit 7 (Tool access) = 0
     * - Bits 6..4 (Security mode / MIC-32 AES-CCM)
     * - Bit 3 (System Broadcast) = 0
     */
    const val DEFAULT_SCF: Byte = 0x00

    private val txSequenceCounters = ConcurrentHashMap<IndividualAddress, AtomicLong>()
    private val rxSequenceTrackers = ConcurrentHashMap<IndividualAddress, Long>()

    data class DecryptedSecureApdu(
        val sequenceNumber: Long,
        val scf: Byte,
        val plainApdu: ByteArray,
        val apciType: ApciType,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DecryptedSecureApdu
            return sequenceNumber == other.sequenceNumber &&
                scf == other.scf &&
                plainApdu.contentEquals(other.plainApdu) &&
                apciType == other.apciType &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = sequenceNumber.hashCode()
            result = 31 * result + scf.hashCode()
            result = 31 * result + plainApdu.contentHashCode()
            result = 31 * result + apciType.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * Obtains the next monotonic 48-bit sequence number for [sender].
     */
    fun nextSequenceNumber(sender: IndividualAddress): Long =
        txSequenceCounters.computeIfAbsent(sender) { AtomicLong(1L) }.getAndIncrement()

    /**
     * Resets or sets the sequence number for [sender] (useful for testing or synchronization).
     */
    fun setSequenceNumber(sender: IndividualAddress, seq: Long) {
        txSequenceCounters.computeIfAbsent(sender) { AtomicLong(seq) }.set(seq)
    }

    /**
     * Validates that [seq] from [sender] is strictly greater than the last seen sequence number.
     * Updates the last seen counter if valid.
     */
    fun verifyAndTrackFreshness(sender: IndividualAddress, seq: Long): Boolean {
        synchronized(rxSequenceTrackers) {
            val last = rxSequenceTrackers[sender] ?: 0L
            if (seq > last) {
                rxSequenceTrackers[sender] = seq
                return true
            }
            return false
        }
    }

    /**
     * Resets the freshness tracker for [sender].
     */
    fun resetFreshnessTracker(sender: IndividualAddress) {
        rxSequenceTrackers.remove(sender)
    }

    /**
     * Constructs the standard 13-byte Nonce for KNX Data Secure AES-128-CCM:
     * - `bytes 0..1`: Source Individual Address (raw 16-bit, big-endian)
     * - `bytes 2..3`: Destination Address (raw 16-bit GA or IA, big-endian)
     * - `bytes 4..9`: Monotonic Sequence Number / Freshness Counter (6 bytes / 48-bit integer, big-endian)
     * - `byte 10`: Security Control Field (SCF)
     * - `bytes 11..12`: 0x00, 0x00 (padding / reserved)
     */
    fun buildDataSecureNonce(
        source: IndividualAddress,
        destinationRaw: Int,
        sequenceNumber: Long,
        scf: Byte = DEFAULT_SCF,
    ): ByteArray {
        val nonce = ByteArray(13)
        nonce[0] = ((source.raw ushr 8) and 0xFF).toByte()
        nonce[1] = (source.raw and 0xFF).toByte()
        nonce[2] = ((destinationRaw ushr 8) and 0xFF).toByte()
        nonce[3] = (destinationRaw and 0xFF).toByte()
        for (i in 0 until 6) {
            val shift = (5 - i) * 8
            nonce[4 + i] = ((sequenceNumber ushr shift) and 0xFF).toByte()
        }
        nonce[10] = scf
        nonce[11] = 0x00
        nonce[12] = 0x00
        return nonce
    }

    /**
     * Encodes a standard KNX plain APDU for group communication.
     */
    fun buildPlainGroupApdu(
        apci: ApciType,
        payload: ByteArray,
        compact6Bit: Boolean = false,
    ): ByteArray {
        val serviceCode = when (apci) {
            ApciType.GROUP_READ -> KnxNetIpCodec.APCI_GROUP_READ
            ApciType.GROUP_RESPONSE -> KnxNetIpCodec.APCI_GROUP_RESPONSE
            else -> KnxNetIpCodec.APCI_GROUP_WRITE
        }
        return when {
            apci == ApciType.GROUP_READ -> byteArrayOf(
                ((serviceCode ushr 8) and 0x03).toByte(),
                (serviceCode and 0xC0).toByte(),
            )
            compact6Bit && payload.size == 1 && (payload[0].toInt() and 0xFF) <= 0x3F -> byteArrayOf(
                ((serviceCode ushr 8) and 0x03).toByte(),
                ((serviceCode and 0xC0) or (payload[0].toInt() and 0x3F)).toByte(),
            )
            else -> byteArrayOf(
                ((serviceCode ushr 8) and 0x03).toByte(),
                (serviceCode and 0xC0).toByte(),
            ) + payload
        }
    }

    /**
     * Parses a standard KNX plain APDU into an [ApciType] and payload [ByteArray].
     */
    fun parsePlainGroupApdu(plainApdu: ByteArray): Pair<ApciType, ByteArray> {
        if (plainApdu.size < 2) return ApciType.OTHER to plainApdu
        val rawApci = ((plainApdu[0].toInt() and 0x03) shl 8) or (plainApdu[1].toInt() and 0xFF)
        val maskedService = rawApci and 0x03C0
        return when (maskedService) {
            KnxNetIpCodec.APCI_GROUP_READ -> ApciType.GROUP_READ to ByteArray(0)
            KnxNetIpCodec.APCI_GROUP_RESPONSE -> {
                val payload = if (plainApdu.size == 2) {
                    byteArrayOf((plainApdu[1].toInt() and 0x3F).toByte())
                } else {
                    plainApdu.copyOfRange(2, plainApdu.size)
                }
                ApciType.GROUP_RESPONSE to payload
            }
            KnxNetIpCodec.APCI_GROUP_WRITE -> {
                val payload = if (plainApdu.size == 2) {
                    byteArrayOf((plainApdu[1].toInt() and 0x3F).toByte())
                } else {
                    plainApdu.copyOfRange(2, plainApdu.size)
                }
                ApciType.GROUP_WRITE to payload
            }
            else -> ApciType.OTHER to plainApdu.copyOfRange(2, plainApdu.size)
        }
    }

    /**
     * Packs an inner [plainApdu] into a full Secure APDU using AES-128-CCM with a 4-byte MAC.
     *
     * Format:
     * - Byte 0: 0x03 (APCI high bits)
     * - Byte 1: 0xF1 (APCI low byte)
     * - Byte 2: SCF (1 byte)
     * - Bytes 3..8: Sequence Number (6 bytes / 48-bit big-endian)
     * - Bytes 9..N: Encrypted payload (same length as plainApdu)
     * - Last 4 bytes: Message Authentication Code (MAC)
     */
    fun packSecureApdu(
        source: IndividualAddress,
        destinationRaw: Int,
        key: ByteArray,
        sequenceNumber: Long,
        plainApdu: ByteArray,
        scf: Byte = DEFAULT_SCF,
    ): ByteArray {
        val nonce = buildDataSecureNonce(source, destinationRaw, sequenceNumber, scf)
        val ccm = KnxAesCcm.encrypt(
            key = key,
            nonce = nonce,
            plaintext = plainApdu,
            aad = ByteArray(0),
            macLength = 4,
        )

        val out = ByteArray(2 + 1 + 6 + ccm.ciphertext.size + 4)
        out[0] = ((APCI_SEC ushr 8) and 0x03).toByte() // 0x03
        out[1] = (APCI_SEC and 0xFF).toByte()          // 0xF1
        out[2] = scf
        for (i in 0 until 6) {
            val shift = (5 - i) * 8
            out[3 + i] = ((sequenceNumber ushr shift) and 0xFF).toByte()
        }
        System.arraycopy(ccm.ciphertext, 0, out, 9, ccm.ciphertext.size)
        System.arraycopy(ccm.macTag, 0, out, 9 + ccm.ciphertext.size, 4)
        return out
    }

    /**
     * Unpacks and decrypts a Secure APDU, verifying its 4-byte AES-128-CCM MAC tag.
     */
    fun unpackSecureApdu(
        source: IndividualAddress,
        destinationRaw: Int,
        key: ByteArray,
        secureApdu: ByteArray,
    ): DecryptedSecureApdu {
        require(secureApdu.size >= 13) { "Secure APDU too short (${secureApdu.size} bytes, min 13)" }

        val hasApciHeader = secureApdu.size >= 15 &&
            (secureApdu[0].toInt() and 0x03) == 0x03 &&
            (secureApdu[1].toInt() and 0xFF) == 0xF1
        val offset = if (hasApciHeader) 2 else 0

        val scf = secureApdu[offset]
        var seq = 0L
        for (i in 0 until 6) {
            seq = (seq shl 8) or (secureApdu[offset + 1 + i].toLong() and 0xFF)
        }

        val cipherAndMacLen = secureApdu.size - (offset + 7)
        require(cipherAndMacLen >= 4) { "Secure APDU missing MAC tag" }
        val cipherLen = cipherAndMacLen - 4
        val ciphertext = secureApdu.copyOfRange(offset + 7, offset + 7 + cipherLen)
        val macTag = secureApdu.copyOfRange(secureApdu.size - 4, secureApdu.size)

        val nonce = buildDataSecureNonce(source, destinationRaw, seq, scf)
        val plainApdu = KnxAesCcm.decrypt(
            key = key,
            nonce = nonce,
            ciphertext = ciphertext,
            macTag = macTag,
            aad = ByteArray(0),
            macLength = 4,
        )

        val (apciType, payload) = parsePlainGroupApdu(plainApdu)
        return DecryptedSecureApdu(
            sequenceNumber = seq,
            scf = scf,
            plainApdu = plainApdu,
            apciType = apciType,
            payload = payload,
        )
    }

    /**
     * Builds a full KNX cEMI `L_Data.req` or `L_Data.ind` frame carrying a Data Secure APDU.
     */
    fun encodeSecureGroupTelegram(
        source: IndividualAddress,
        destination: GroupAddress,
        apci: ApciType,
        payload: ByteArray,
        compact6Bit: Boolean,
        key: ByteArray,
        sequenceNumber: Long = nextSequenceNumber(source),
        msgCode: Int = KnxNetIpCodec.CEMI_L_DATA_REQ,
        scf: Byte = DEFAULT_SCF,
    ): ByteArray {
        val plainApdu = buildPlainGroupApdu(apci, payload, compact6Bit)
        val secureApdu = packSecureApdu(
            source = source,
            destinationRaw = destination.raw,
            key = key,
            sequenceNumber = sequenceNumber,
            plainApdu = plainApdu,
            scf = scf,
        )
        val npduLen = (secureApdu.size - 1).coerceAtLeast(1)
        return byteArrayOf(
            msgCode.toByte(),
            0x00, // addInfoLen = 0
            0xBC.toByte(), // Standard frame, broadcast, LOW priority
            0xE0.toByte(), // Group address (bit 7=1), hop count = 6
            ((source.raw ushr 8) and 0xFF).toByte(),
            (source.raw and 0xFF).toByte(),
            ((destination.raw ushr 8) and 0xFF).toByte(),
            (destination.raw and 0xFF).toByte(),
            npduLen.toByte(),
        ) + secureApdu
    }

    /**
     * Checks if the given cEMI frame has an APCI of `0x03F1` (`APCI_SEC`).
     */
    fun isSecureCemi(cemi: ByteArray): Boolean {
        if (cemi.size < 9) return false
        val addInfoLen = cemi[1].toInt() and 0xFF
        val base = 2 + addInfoLen
        if (base + 8 >= cemi.size) return false
        val tpciByte = cemi[base + 7].toInt() and 0xFF
        val apciLow = cemi[base + 8].toInt() and 0xFF
        val rawApci = ((tpciByte and 0x03) shl 8) or apciLow
        return rawApci == APCI_SEC
    }

    /**
     * Transparently parses a cEMI frame. If the frame is Data Secure (`0x03F1`), it attempts to decrypt
     * the payload using keys provided in [keyring]. If decrypted, it sets [BusTelegram.isSecure] = true
     * and extracts the original application payload and APCI.
     */
    fun decodeCemi(
        cemi: ByteArray,
        id: Long,
        keyring: KnxKeyring? = null,
    ): BusTelegram? {
        val decoded = KnxNetIpCodec.parseCemiDetailed(cemi, id) ?: return null
        val baseTelegram = decoded.telegram

        if (!isSecureCemi(cemi)) {
            return baseTelegram
        }

        val dstGroup = baseTelegram.destinationGroup
        val keyHex = if (dstGroup != null && keyring != null) {
            keyring.groupKeysHex[dstGroup]
        } else null

        val addInfoLen = cemi[1].toInt() and 0xFF
        val base = 2 + addInfoLen
        val npduLen = cemi[base + 6].toInt() and 0xFF
        val apduEnd = (base + 7 + 1 + npduLen).coerceAtMost(cemi.size)
        val secureApdu = cemi.copyOfRange(base + 7, apduEnd)

        if (keyHex != null) {
            try {
                val key = HexBytes.fromHex(keyHex)
                val unpacked = unpackSecureApdu(
                    source = baseTelegram.source,
                    destinationRaw = dstGroup?.raw ?: 0,
                    key = key,
                    secureApdu = secureApdu,
                )
                verifyAndTrackFreshness(baseTelegram.source, unpacked.sequenceNumber)
                return baseTelegram.copy(
                    apci = unpacked.apciType,
                    payload = unpacked.payload,
                    isSecure = true,
                )
            } catch (_: Exception) {
                // If decryption fails (e.g. wrong key or corrupt MAC), mark as secure with un-decrypted payload
            }
        }

        // Secure frame whose key is unknown or decryption failed
        return baseTelegram.copy(
            apci = ApciType.OTHER,
            payload = if (secureApdu.size > 2) secureApdu.copyOfRange(2, secureApdu.size) else secureApdu,
            isSecure = true,
        )
    }
}
