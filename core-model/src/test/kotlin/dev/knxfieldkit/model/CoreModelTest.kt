package dev.knxfieldkit.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

class CoreModelTest {

    @Test
    fun testIndividualAddress() {
        val address = IndividualAddress.parse("1.1.15")
        assertEquals(1, address.area)
        assertEquals(1, address.line)
        assertEquals(15, address.device)
        assertEquals(0x110F, address.raw)
        assertEquals("1.1.15", address.toString())

        val addressFromRaw = IndividualAddress.fromRaw(0x110F)
        assertEquals(address, addressFromRaw)

        assertEquals(IndividualAddress(0, 0, 0), IndividualAddress.parse("0.0.0"))
        assertEquals(IndividualAddress(15, 15, 255), IndividualAddress.parse("15.15.255"))

        assertFailsWith<IllegalArgumentException> { IndividualAddress.parse("16.0.1") }
        assertFailsWith<IllegalArgumentException> { IndividualAddress.parse("1.1.256") }
        assertFailsWith<IllegalArgumentException> { IndividualAddress.parse("invalid") }
    }

    @Test
    fun testGroupAddress() {
        val address = GroupAddress.parse("1/2/3")
        assertEquals(GroupAddressStyle.THREE_LEVEL, address.style)
        assertEquals(1, address.main3)
        assertEquals(2, address.middle3)
        assertEquals(3, address.sub3)
        assertEquals(2563, address.raw)

        val address2 = GroupAddress.parse("1/515")
        assertEquals(GroupAddressStyle.TWO_LEVEL, address2.style)
        assertEquals(1, address2.main2)
        assertEquals(515, address2.sub2)
        assertEquals(2563, address2.raw)

        val address3 = GroupAddress.parse("2563")
        assertEquals(GroupAddressStyle.FREE, address3.style)
        assertEquals(2563, address3.raw)

        assertTrue(address == address2)
        assertEquals(address.hashCode(), address2.hashCode())

        assertEquals("1/515", address.format(GroupAddressStyle.TWO_LEVEL))
    }

    @Test
    fun testDptCodecAndHexBytes() {
        val hex = HexBytes.fromHex("0C 1A")
        assertEquals("0C 1A", HexBytes.toHex(hex))

        assertEquals("9.001", DptCodec.normalizeDptId("DPST-9-1"))
        assertEquals("1.001", DptCodec.normalizeDptId("DPST-1-1"))

        val encodedBool = DptCodec.encodeBool(true)
        assertEquals("ON", DptCodec.decode("1.001", encodedBool))
        assertEquals("OFF", DptCodec.decode("1.001", byteArrayOf(0)))

        val encodedPercent = DptCodec.encodePercent(100.0)
        assertEquals("100.0 %", DptCodec.decode("5.001", encodedPercent))
        assertEquals("0.0 %", DptCodec.decode("5.001", byteArrayOf(0)))

        val encodedFloat = DptCodec.encodeFloat16(21.5)
        val decodedFloat = DptCodec.decodeFloat16(encodedFloat)
        assertTrue(decodedFloat in 21.48..21.52)
        assertEquals("21.50 °C", DptCodec.decode("9.001", encodedFloat))

        val encodedNegativeFloat = DptCodec.encodeFloat16(-5.5)
        val decodedNegativeFloat = DptCodec.decodeFloat16(encodedNegativeFloat)
        assertTrue(decodedNegativeFloat in -5.52..-5.48)
        assertEquals("-5.50 °C", DptCodec.decode("9.001", encodedNegativeFloat))

        val encodedInt32 = DptCodec.encodeInt32(-123456)
        assertEquals(-123456, DptCodec.decodeInt32(encodedInt32))

        val encodedFloat32 = DptCodec.encodeFloat32(230.5f)
        assertEquals(230.5f, DptCodec.decodeFloat32(encodedFloat32))

        val encodedString = DptCodec.encodeString14("Kitchen")
        assertEquals("\"Kitchen\"", DptCodec.decode("16.001", encodedString))

        val encodedScene = DptCodec.encodeScene(5)
        assertEquals("Scene 5", DptCodec.decode("17.001", encodedScene))

        val encodedHvacMode = DptCodec.encodeHvacMode(HvacMode.COMFORT)
        assertEquals("Comfort", DptCodec.decode("20.102", encodedHvacMode))

        val encodedDimUp = DptCodec.encodeDimmingStep(increase = true, stepCode = 3)
        assertEquals("UP 25.0%", DptCodec.decode("3.007", encodedDimUp))
        assertEquals("STOP", DptCodec.decode("3.007", DptCodec.encodeDimmingStep(increase = false, stepCode = 0)))
    }

    @Test
    fun testBusHealthAnalyzerDetectsUnansweredReadsAndGhostTraffic() {
        val now = 1_700_000_010_000L
        val gaKnown = GroupAddress.parse("1/1/1")
        val gaDead = GroupAddress.parse("1/1/9")
        val gaGhost = GroupAddress.parse("9/7/99")
        val project = KnxProject(
            projectId = "P-1",
            name = "Health Test",
            groupAddresses = mapOf(
                gaKnown to GroupAddressInfo(gaKnown, "Light", dpt = "1.001"),
                gaDead to GroupAddressInfo(gaDead, "Broken Sensor", dpt = "9.001"),
            ),
        )
        val rows = listOf(
            EnrichedTelegram.enrich(BusTelegram(1, now - 3000L, IndividualAddress(1, 1, 20), dstGroup(gaDead), null, ApciType.GROUP_READ), project),
            EnrichedTelegram.enrich(BusTelegram(2, now - 2000L, IndividualAddress(1, 1, 20), dstGroup(gaKnown), null, ApciType.GROUP_READ), project),
            EnrichedTelegram.enrich(BusTelegram(3, now - 1950L, IndividualAddress(1, 1, 10), dstGroup(gaKnown), null, ApciType.GROUP_RESPONSE, byteArrayOf(1)), project),
            EnrichedTelegram.enrich(BusTelegram(4, now - 500L, IndividualAddress(1, 1, 25), dstGroup(gaGhost), null, ApciType.GROUP_WRITE, byteArrayOf(1)), project),
        )
        val stats = BusHealthAnalyzer.analyze(rows, nowEpochMs = now, windowMs = 10_000L)
        assertEquals(1, stats.ghostGroupCount)
        assertEquals(listOf(gaDead), stats.unansweredReadAddresses)
        assertTrue(stats.telegramsPerSecond > 0.0)
    }

    private fun dstGroup(ga: GroupAddress): GroupAddress = ga

    @Test
    fun testEnrichedTelegram() {
        val gaTemp = GroupAddress.parse("1/1/1")
        val project = KnxProject(
            projectId = "P-1",
            name = "Test Project",
            areas = listOf(
                TopologyArea(
                    area = 1,
                    name = "Main Area",
                    lines = listOf(
                        TopologyLine(
                            area = 1,
                            line = 1,
                            name = "Main Line",
                            devices = listOf(
                                ProjectDevice(
                                    address = IndividualAddress(1, 1, 10),
                                    name = "Living Room Sensor",
                                    groupAddresses = listOf(gaTemp),
                                )
                            ),
                        )
                    ),
                )
            ),
            groupAddresses = mapOf(
                gaTemp to GroupAddressInfo(
                    address = gaTemp,
                    name = "Living Temp",
                    dpt = "9.001",
                )
            ),
        )

        val telegram = BusTelegram(
            id = 1L,
            timestampEpochMs = 1_700_000_000_000L,
            source = IndividualAddress(1, 1, 10),
            destinationGroup = gaTemp,
            apci = ApciType.GROUP_WRITE,
            payload = DptCodec.encodeFloat16(22.0),
        )

        val enriched = EnrichedTelegram.enrich(telegram, project)
        assertNotNull(enriched)
        assertEquals("Living Room Sensor", enriched.sourceDeviceName)
        assertEquals("Living Temp", enriched.destinationName)
        assertFalse(enriched.isUnmappedGroup)
        assertEquals("22.00 °C", enriched.decodedValue)

        val unknownGroupTelegram = BusTelegram(
            id = 2L,
            timestampEpochMs = 1_700_000_000_100L,
            source = IndividualAddress(1, 1, 10),
            destinationGroup = GroupAddress.parse("9/7/99"),
            apci = ApciType.GROUP_WRITE,
            payload = DptCodec.encodeFloat16(22.0),
        )

        val unknownGroupEnriched = EnrichedTelegram.enrich(unknownGroupTelegram, project)
        assertNotNull(unknownGroupEnriched)
        assertTrue(unknownGroupEnriched.isUnmappedGroup)
    }
}
