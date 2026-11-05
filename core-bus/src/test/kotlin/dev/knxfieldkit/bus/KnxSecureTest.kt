package dev.knxfieldkit.bus

import dev.knxfieldkit.bus.secure.KnxAesCcm
import dev.knxfieldkit.bus.secure.KnxSecureDataCodec
import dev.knxfieldkit.bus.secure.KnxSecureIpSession
import dev.knxfieldkit.model.ApciType
import dev.knxfieldkit.model.BusConnectionConfig
import dev.knxfieldkit.model.BusTelegram
import dev.knxfieldkit.model.DptCodec
import dev.knxfieldkit.model.GroupAddress
import dev.knxfieldkit.model.HexBytes
import dev.knxfieldkit.model.IndividualAddress
import dev.knxfieldkit.model.KeyringInterface
import dev.knxfieldkit.model.KnxKeyring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnxSecureTest {

    @Test
    fun `RFC 3610 AES-128-CCM Packet Vector 1 verification`() {
        val key = HexBytes.fromHex("C0C1C2C3C4C5C6C7C8C9CACBCCCDCECF")
        val nonce = HexBytes.fromHex("00000003020100A0A1A2A3A4A5")
        val adata = HexBytes.fromHex("0001020304050607")
        val payload = HexBytes.fromHex("08090A0B0C0D0E0F101112131415161718191A1B1C1D1E")

        val expectedCiphertext = HexBytes.fromHex("588C979A61C663D2F066D0C2C0F989806D5F6B61DAC384")
        val expectedMac = HexBytes.fromHex("17E8D12CFDF926E0")

        val result = KnxAesCcm.encrypt(
            key = key,
            nonce = nonce,
            plaintext = payload,
            aad = adata,
            macLength = 8,
        )

        assertContentEquals(expectedCiphertext, result.ciphertext)
        assertContentEquals(expectedMac, result.macTag)

        val decrypted = KnxAesCcm.decrypt(
            key = key,
            nonce = nonce,
            ciphertext = result.ciphertext,
            macTag = result.macTag,
            aad = adata,
            macLength = 8,
        )

        assertContentEquals(payload, decrypted)
    }

    @Test
    fun `RFC 3610 AES-128-CCM Packet Vector 2 verification`() {
        val key = HexBytes.fromHex("C0C1C2C3C4C5C6C7C8C9CACBCCCDCECF")
        val nonce = HexBytes.fromHex("00000004030201A0A1A2A3A4A5")
        val adata = HexBytes.fromHex("0001020304050607")
        val payload = HexBytes.fromHex("08090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F")

        val expectedCiphertext = HexBytes.fromHex("72C91A36E135F8CF291CA894085C87E3CC15C439C9E43A3B")
        val expectedMac = HexBytes.fromHex("A091D56E10400916")

        val result = KnxAesCcm.encrypt(
            key = key,
            nonce = nonce,
            plaintext = payload,
            aad = adata,
            macLength = 8,
        )

        assertContentEquals(expectedCiphertext, result.ciphertext)
        assertContentEquals(expectedMac, result.macTag)

        val decrypted = KnxAesCcm.decrypt(
            key = key,
            nonce = nonce,
            ciphertext = result.ciphertext,
            macTag = result.macTag,
            aad = adata,
            macLength = 8,
        )

        assertContentEquals(payload, decrypted)
    }

    @Test
    fun `AES-CCM tampering detection throws SecurityException`() {
        val key = HexBytes.fromHex("00112233445566778899AABBCCDDEEFF")
        val nonce = ByteArray(13) { it.toByte() }
        val plaintext = "KNX Secure Secret".toByteArray(Charsets.UTF_8)

        val result = KnxAesCcm.encrypt(key, nonce, plaintext, macLength = 4)

        // Tamper with ciphertext
        val tamperedCipher = result.ciphertext.copyOf()
        tamperedCipher[0] = (tamperedCipher[0].toInt() xor 0x01).toByte()

        assertFailsWith<SecurityException> {
            KnxAesCcm.decrypt(key, nonce, tamperedCipher, result.macTag, macLength = 4)
        }

        // Tamper with MAC tag
        val tamperedMac = result.macTag.copyOf()
        tamperedMac[3] = (tamperedMac[3].toInt() xor 0xFF).toByte()

        assertFailsWith<SecurityException> {
            KnxAesCcm.decrypt(key, nonce, result.ciphertext, tamperedMac, macLength = 4)
        }
    }

    @Test
    fun `KNX Data Secure Nonce generation structure`() {
        val src = IndividualAddress(1, 1, 20)
        val dstGa = GroupAddress.ofThreeLevel(1, 1, 1)
        val seq = 0x00000000002AL // 42 decimal

        val nonce = KnxSecureDataCodec.buildDataSecureNonce(
            source = src,
            destinationRaw = dstGa.raw,
            sequenceNumber = seq,
            scf = 0x00,
        )

        assertEquals(13, nonce.size)
        // src raw = (1 << 12) | (1 << 8) | 20 = 0x1114
        assertEquals(0x11.toByte(), nonce[0])
        assertEquals(0x14.toByte(), nonce[1])
        // dst GA raw = 1/1/1 = (1 << 11) | (1 << 8) | 1 = 0x0901
        assertEquals(0x09.toByte(), nonce[2])
        assertEquals(0x01.toByte(), nonce[3])
        // 6 bytes sequence
        assertEquals(0x00.toByte(), nonce[4])
        assertEquals(0x00.toByte(), nonce[5])
        assertEquals(0x00.toByte(), nonce[6])
        assertEquals(0x00.toByte(), nonce[7])
        assertEquals(0x00.toByte(), nonce[8])
        assertEquals(0x2A.toByte(), nonce[9])
        // SCF
        assertEquals(0x00.toByte(), nonce[10])
        // padding
        assertEquals(0x00.toByte(), nonce[11])
        assertEquals(0x00.toByte(), nonce[12])
    }

    @Test
    fun `Data Secure APDU pack and unpack roundtrip`() {
        val src = IndividualAddress(1, 1, 20)
        val dstGa = GroupAddress.ofThreeLevel(1, 1, 1)
        val key = HexBytes.fromHex("0102030405060708090A0B0C0D0E0F10")
        val seq = 12345L
        val payload = DptCodec.encodeBool(true)

        val plainApdu = KnxSecureDataCodec.buildPlainGroupApdu(
            apci = ApciType.GROUP_WRITE,
            payload = payload,
            compact6Bit = true,
        )

        val secureApdu = KnxSecureDataCodec.packSecureApdu(
            source = src,
            destinationRaw = dstGa.raw,
            key = key,
            sequenceNumber = seq,
            plainApdu = plainApdu,
        )

        // Verify header
        assertEquals(0x03.toByte(), secureApdu[0])
        assertEquals(0xF1.toByte(), secureApdu[1])
        assertEquals(0x00.toByte(), secureApdu[2]) // SCF

        val unpacked = KnxSecureDataCodec.unpackSecureApdu(
            source = src,
            destinationRaw = dstGa.raw,
            key = key,
            secureApdu = secureApdu,
        )

        assertEquals(seq, unpacked.sequenceNumber)
        assertEquals(ApciType.GROUP_WRITE, unpacked.apciType)
        assertContentEquals(payload, unpacked.payload)
        assertEquals("ON", DptCodec.decode("1.001", unpacked.payload))
    }

    @Test
    fun `Data Secure complete cEMI telegram encode and decode`() {
        val src = IndividualAddress(1, 1, 20)
        val dstGa = GroupAddress.ofThreeLevel(1, 3, 1) // Temperature
        val key = HexBytes.fromHex("A0A1A2A3A4A5A6A7A8A9AAABACADAEAF")
        val tempPayload = DptCodec.encodeFloat16(22.5)

        val cemi = KnxSecureDataCodec.encodeSecureGroupTelegram(
            source = src,
            destination = dstGa,
            apci = ApciType.GROUP_RESPONSE,
            payload = tempPayload,
            compact6Bit = false,
            key = key,
            sequenceNumber = 9999L,
        )

        assertTrue(KnxSecureDataCodec.isSecureCemi(cemi))

        val keyring = KnxKeyring(
            projectName = "Test Secure Project",
            groupKeysHex = mapOf(dstGa to HexBytes.toHex(key)),
        )
        assertTrue(keyring.isGroupSecure(dstGa))
        assertFalse(keyring.isGroupSecure(GroupAddress.ofThreeLevel(1, 1, 1)))

        val decoded = KnxSecureDataCodec.decodeCemi(cemi, 101L, keyring)
        assertNotNull(decoded)
        assertTrue(decoded.isSecure)
        assertEquals(src, decoded.source)
        assertEquals(dstGa, decoded.destinationGroup)
        assertEquals(ApciType.GROUP_RESPONSE, decoded.apci)
        assertContentEquals(tempPayload, decoded.payload)
        assertTrue(DptCodec.decode("9.001", decoded.payload).contains("22.5"))

        // Decode without keyring: returns BusTelegram with isSecure=true and un-decrypted payload
        val unkeyedDecoded = KnxSecureDataCodec.decodeCemi(cemi, 102L, keyring = null)
        assertNotNull(unkeyedDecoded)
        assertTrue(unkeyedDecoded.isSecure)
        assertEquals(ApciType.OTHER, unkeyedDecoded.apci)
    }

    @Test
    fun `Freshness counter tracking accepts increasing sequences and rejects stale`() {
        val sender = IndividualAddress(1, 1, 55)
        KnxSecureDataCodec.resetFreshnessTracker(sender)

        assertTrue(KnxSecureDataCodec.verifyAndTrackFreshness(sender, 10L))
        assertTrue(KnxSecureDataCodec.verifyAndTrackFreshness(sender, 15L))
        // Stale or replay attempts
        assertFalse(KnxSecureDataCodec.verifyAndTrackFreshness(sender, 15L))
        assertFalse(KnxSecureDataCodec.verifyAndTrackFreshness(sender, 12L))
        assertFalse(KnxSecureDataCodec.verifyAndTrackFreshness(sender, 5L))
        // Valid advancement
        assertTrue(KnxSecureDataCodec.verifyAndTrackFreshness(sender, 16L))
    }

    @Test
    fun `KNX IP Secure session wrap and unwrap with replay protection`() {
        val sessionKey = HexBytes.fromHex("FEDCBA9876543210FEDCBA9876543210")
        val session = KnxSecureIpSession(
            sessionId = 0x1234,
            sessionKey = sessionKey,
            userId = 1,
            macLength = 8,
        )

        val innerFrame = KnxNetIpCodec.buildTunnelingAck(channelId = 1, seqCounter = 5, status = 0)
        val wrapped = session.wrap(innerFrame)

        // Verify SECURE_WRAPPER header (0x06, 0x10, 0x09, 0x50)
        assertEquals(0x06.toByte(), wrapped[0])
        assertEquals(0x10.toByte(), wrapped[1])
        assertEquals(0x09.toByte(), wrapped[2])
        assertEquals(0x50.toByte(), wrapped[3])

        // Unwrap
        val unwrapped = session.unwrap(wrapped)
        assertContentEquals(innerFrame, unwrapped)

        // Replay attempt throws SecurityException
        assertFailsWith<SecurityException> {
            session.unwrap(wrapped)
        }
    }

    @Test
    fun `KNX IP Secure handshake and timer notify frames`() {
        val req = KnxSecureIpSession.buildSessionRequest()
        assertEquals(0x06.toByte(), req[0])
        assertEquals(0x10.toByte(), req[1])
        assertEquals(0x09.toByte(), req[2])
        assertEquals(0x51.toByte(), req[3]) // 0x0951 SESSION_REQUEST

        val serverRand = ByteArray(16) { 0x42 }
        val respBytes = KnxSecureIpSession.buildSessionResponse(
            sessionId = 0x00A1,
            serverRandom = serverRand,
            mac = ByteArray(8) { 0x01 },
        )
        val parsedResp = KnxSecureIpSession.parseSessionResponse(respBytes)
        assertNotNull(parsedResp)
        assertEquals(0x00A1, parsedResp.sessionId)
        assertContentEquals(serverRand, parsedResp.serverRandom)

        val timerBytes = KnxSecureIpSession.buildTimerNotify(
            sessionId = 0x00A1,
            timestampCounter = 123456789L,
            serialNumber = byteArrayOf(0x00, 0xC5.toByte(), 0x01, 0x02, 0x03, 0x04),
        )
        val parsedTimer = KnxSecureIpSession.parseTimerNotify(timerBytes)
        assertNotNull(parsedTimer)
        assertEquals(0x00A1, parsedTimer.sessionId)
        assertEquals(123456789L, parsedTimer.timestampCounter)
        assertEquals("00C501020304", parsedTimer.serialNumberHex)
    }

    @Test
    fun `SimulatedKnxBusClient emits secure telegrams when keyring configured`() = runBlocking(Dispatchers.Default) {
        val simClient = SimulatedKnxBusClient()
        val secGa = GroupAddress.ofThreeLevel(1, 1, 1)
        val keyHex = "11223344556677889900AABBCCDDEEFF"
        val keyring = KnxKeyring(
            projectName = "Virtual Secure Villa",
            groupKeysHex = mapOf(secGa to keyHex),
        )
        simClient.setKeyring(keyring)
        simClient.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = false))

        val received = CopyOnWriteArrayList<BusTelegram>()
        val job = launch {
            simClient.incomingTelegrams.collect { received.add(it) }
        }
        delay(50L)

        simClient.sendGroupWrite(secGa, DptCodec.encodeBool(true), compact6Bit = true)
        delay(100L)
        job.cancel()

        val rxTelegram = received.firstOrNull { it.destinationGroup == secGa }
        assertNotNull(rxTelegram, "Telegram should be received by subscriber")
        assertTrue(rxTelegram.isSecure, "Telegram on secure GA must be flagged isSecure=true")
        assertEquals(ApciType.GROUP_WRITE, rxTelegram.apci)
        assertEquals("ON", DptCodec.decode("1.001", rxTelegram.payload))
        assertTrue(rxTelegram.rawFrameHex.isNotEmpty(), "Secure frame hex should be populated")

        simClient.close()
    }
}
