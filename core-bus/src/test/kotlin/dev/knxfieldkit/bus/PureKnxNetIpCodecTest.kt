package dev.knxfieldkit.bus

import dev.knxfieldkit.model.ApciType
import dev.knxfieldkit.model.BusConnectionConfig
import dev.knxfieldkit.model.DptCodec
import dev.knxfieldkit.model.GroupAddress
import dev.knxfieldkit.model.IndividualAddress
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PureKnxNetIpCodecTest {

    @Test
    fun `encode and decode compact 6-bit and extended float16 CEMI group frames`() {
        val src = IndividualAddress(1, 1, 20)
        val gaSwitch = GroupAddress.ofThreeLevel(1, 1, 1)
        val switchCemi = KnxNetIpCodec.buildCemiGroupFrame(
            msgCode = KnxNetIpCodec.CEMI_L_DATA_IND,
            src = src,
            dst = gaSwitch,
            apci = ApciType.GROUP_WRITE,
            payload = DptCodec.encodeBool(true),
            compact6Bit = true,
        )
        val decodedSwitch = KnxNetIpCodec.parseCemiFrame(switchCemi, 1L)
        assertNotNull(decodedSwitch)
        assertEquals(src, decodedSwitch.source)
        assertEquals(gaSwitch, decodedSwitch.destinationGroup)
        assertEquals(ApciType.GROUP_WRITE, decodedSwitch.apci)
        assertEquals("ON", DptCodec.decode("1.001", decodedSwitch.payload))

        val gaTemp = GroupAddress.ofThreeLevel(1, 3, 1)
        val tempCemi = KnxNetIpCodec.buildCemiGroupFrame(
            msgCode = KnxNetIpCodec.CEMI_L_DATA_IND,
            src = src,
            dst = gaTemp,
            apci = ApciType.GROUP_RESPONSE,
            payload = DptCodec.encodeFloat16(21.8),
            compact6Bit = false,
        )
        val decodedTemp = KnxNetIpCodec.parseCemiFrame(tempCemi, 2L)
        assertNotNull(decodedTemp)
        assertEquals(gaTemp, decodedTemp.destinationGroup)
        assertEquals(ApciType.GROUP_RESPONSE, decodedTemp.apci)
        assertTrue(DptCodec.decode("9.001", decodedTemp.payload).contains("21.8"))
    }

    @Test
    fun `parse KNXnet IP SearchResponse DIB device info and service families`() {
        val body = ByteArrayOutputStream()
        // Control HPAI 192.168.1.50:3671
        body.write(KnxNetIpCodec.buildHpai(byteArrayOf(192.toByte(), 168.toByte(), 1, 50), 3671))
        // DIB 0x01 Device Info (54 bytes)
        val dibDev = ByteArray(54)
        dibDev[0] = 54
        dibDev[1] = 0x01
        dibDev[2] = 0x02 // KNX TP1
        dibDev[4] = 0x11 // 1.1.0
        dibDev[5] = 0x00
        byteArrayOf(0x00, 0xC5.toByte(), 0x01, 0x02, 0x03, 0x04).copyInto(dibDev, 8)
        byteArrayOf(0x00, 0x24, 0x6D, 0x11, 0x22, 0x33).copyInto(dibDev, 18)
        "Weinzierl IP Router 752".toByteArray(StandardCharsets.ISO_8859_1).copyInto(dibDev, 24)
        body.write(dibDev)
        // DIB 0x02 Service Families: Core(02), DeviceMgmt(03), Tunneling(04), Routing(05), Security(09)
        body.write(byteArrayOf(12, 0x02, 0x02, 1, 0x03, 1, 0x04, 1, 0x05, 1, 0x09, 1))

        val payload = body.toByteArray()
        val total = 6 + payload.size
        val pkt = byteArrayOf(0x06, 0x10, 0x02, 0x02, ((total ushr 8) and 0xFF).toByte(), (total and 0xFF).toByte()) + payload

        val parsed = KnxNetIpCodec.parseSearchResponse(pkt, pkt.size, "192.168.1.50")
        assertNotNull(parsed)
        assertEquals("Weinzierl IP Router 752", parsed.name)
        assertEquals("192.168.1.50", parsed.host)
        assertEquals(3671, parsed.port)
        assertEquals(IndividualAddress(1, 1, 0), parsed.individualAddress)
        assertTrue(parsed.supportsTunneling)
        assertTrue(parsed.supportsRouting)
        assertTrue(parsed.supportsSecurity)
    }

    @Test
    fun `loopback UDP KNXnet IP tunneling handshake, GroupRead and DD0 ping`() = runBlocking {
        val serverSock = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        serverSock.soTimeout = 3_000
        val serverPort = serverSock.localPort

        val serverThread = thread(start = true) {
            val rxBuf = ByteArray(512)
            var running = true
            var gatewayTxSeq = 0
            while (running && !serverSock.isClosed) {
                val pkt = DatagramPacket(rxBuf, rxBuf.size)
                try {
                    serverSock.receive(pkt)
                } catch (_: Exception) {
                    break
                }
                val bytes = pkt.data.copyOf(pkt.length)
                val svc = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
                when (svc) {
                    KnxNetIpCodec.SERVICE_CONNECT_REQUEST -> {
                        // Reply CONNECT_RESPONSE: channel=7, status=0, HPAI=0.0.0.0:0 (NAT), CRD=1.1.250
                        val resp = byteArrayOf(
                            0x06, 0x10, 0x02, 0x06, 0x00, 0x14,
                            0x07, 0x00,
                            0x08, 0x01, 0, 0, 0, 0, 0, 0,
                            0x04, 0x04, 0x11, 0xFA.toByte(),
                        )
                        serverSock.send(DatagramPacket(resp, resp.size, pkt.address, pkt.port))
                    }
                    KnxNetIpCodec.SERVICE_TUNNELING_REQUEST -> {
                        val ch = bytes[7].toInt() and 0xFF
                        val seq = bytes[8].toInt() and 0xFF
                        val ack = KnxNetIpCodec.buildTunnelingAck(ch, seq, 0)
                        serverSock.send(DatagramPacket(ack, ack.size, pkt.address, pkt.port))

                        val cemi = bytes.copyOfRange(10, bytes.size)
                        val decoded = KnxNetIpCodec.parseCemiFrame(cemi, 99L)
                        if (decoded?.apci == ApciType.GROUP_READ && decoded.destinationGroup != null) {
                            val replyCemi = KnxNetIpCodec.buildCemiGroupFrame(
                                msgCode = KnxNetIpCodec.CEMI_L_DATA_IND,
                                src = IndividualAddress(1, 1, 10),
                                dst = decoded.destinationGroup!!,
                                apci = ApciType.GROUP_RESPONSE,
                                payload = DptCodec.encodeBool(true),
                                compact6Bit = true,
                            )
                            val tunInd = KnxNetIpCodec.buildTunnelingRequest(ch, gatewayTxSeq++, replyCemi)
                            serverSock.send(DatagramPacket(tunInd, tunInd.size, pkt.address, pkt.port))
                        } else if (decoded?.apci == ApciType.DEVICE_DESCRIPTOR_READ && decoded.destinationIndividual != null) {
                            val respCemi = KnxNetIpCodec.buildCemiIndividualApdu(
                                src = decoded.destinationIndividual!!,
                                dst = IndividualAddress(1, 1, 250),
                                numbered = true,
                                seq = 0,
                                apciService = KnxNetIpCodec.APCI_DEVICE_DESC_RESPONSE,
                                asdu = byteArrayOf(0x07, 0xB0.toByte()),
                            ).also { it[0] = KnxNetIpCodec.CEMI_L_DATA_IND.toByte() }
                            val tunInd = KnxNetIpCodec.buildTunnelingRequest(ch, gatewayTxSeq++, respCemi)
                            serverSock.send(DatagramPacket(tunInd, tunInd.size, pkt.address, pkt.port))
                        }
                    }
                    KnxNetIpCodec.SERVICE_DISCONNECT_REQUEST -> {
                        val resp = KnxNetIpCodec.buildDisconnectResponse(bytes[6].toInt() and 0xFF, 0)
                        serverSock.send(DatagramPacket(resp, resp.size, pkt.address, pkt.port))
                        running = false
                    }
                }
            }
        }

        val client = PureKnxNetIpClient()
        try {
            client.connect(BusConnectionConfig.IpTunneling("127.0.0.1", serverPort, useNat = true))
            val readRes = client.sendGroupRead(GroupAddress.ofThreeLevel(1, 1, 1), timeoutMs = 1_500L)
            assertTrue(readRes.responded)
            assertEquals("ON", readRes.decodedValue("1.001"))

            val pingRes = client.pingDevice(IndividualAddress(1, 1, 10), timeoutMs = 1_500L)
            assertTrue(pingRes.reachable)
            assertEquals("07B0", pingRes.maskVersionHex)

            client.disconnect()
        } finally {
            client.close()
            serverSock.close()
            serverThread.join(1_000L)
        }
    }
}
