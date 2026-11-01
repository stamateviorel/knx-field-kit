package dev.knxfieldkit.bus

import dev.knxfieldkit.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SimulatedBusFieldTest {

    private val client = SimulatedKnxBusClient()

    @Test
    fun gatewayDiscoveryFindsSimulatedRouter() = runTest {
        val gateways = client.discoverGateways()
        assertTrue(gateways.isNotEmpty())
        val router = gateways.find { it.individualAddress == IndividualAddress(1, 1, 0) }
        assertNotNull(router)
        assertTrue(router.supportsTunneling)
    }

    @Test
    fun groupWriteAndGroupReadRoundTrip() = runTest {
        client.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = false))
        val payload = DptCodec.encodePercent(80.0)
        client.sendGroupWrite(GroupAddress.parse("1/2/2"), payload)

        val readResult = client.sendGroupRead(GroupAddress.parse("1/2/2"))
        assertTrue(readResult.responded)
        val decodedValue = DptCodec.decodePercent(readResult.payload!!)
        assertTrue(decodedValue in 79.0..81.0)

        val unassignedReadResult = client.sendGroupRead(GroupAddress.parse("7/7/7"))
        assertFalse(unassignedReadResult.responded)
    }

    @Test
    fun pingOnlineAndOfflineDevices() = runTest {
        client.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = false))
        val onlinePing = client.pingDevice(IndividualAddress.parse("1.1.10"))
        assertTrue(onlinePing.reachable)
        assertEquals("07B0", onlinePing.maskVersionHex)

        val offlinePing = client.pingDevice(IndividualAddress.parse("1.1.40"))
        assertFalse(offlinePing.reachable)
    }

    @Test
    fun lineAuditDetectsFaultyDevice() = runTest {
        client.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = false))
        val project = DemoKnxProjectFactory.createDemoVillaProject()
        val report = client.auditLine(project.findLine(1, 1)!!, delayBetweenMs = 0L)
        assertEquals(5, report.onlineCount)
        assertEquals(1, report.unreachableCount)
        val unreachableEntry = report.entries.find { it.status == AuditStatus.UNREACHABLE }
        assertNotNull(unreachableEntry)
        assertEquals(IndividualAddress(1, 1, 40), unreachableEntry.expectedDevice.address)
    }

    @Test
    fun programmingModeFinderDetectsActiveLed() = runTest {
        client.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = false))
        assertTrue(client.findProgrammingModeDevices().contains(IndividualAddress(1, 1, 20)))
        client.setProgrammingMode(IndividualAddress(1, 1, 15), true)
        val programmingDevices = client.findProgrammingModeDevices()
        assertTrue(programmingDevices.contains(IndividualAddress(1, 1, 15)))
        assertTrue(programmingDevices.contains(IndividualAddress(1, 1, 20)))
    }

    @Test
    fun deviceDiagnosticsReadout() = runTest {
        client.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = false))
        val diagnostics = client.readDeviceDiagnostics(IndividualAddress(1, 1, 10))
        assertTrue(diagnostics.reachable)
        assertEquals("07B0", diagnostics.maskVersionHex)
        assertEquals("Loaded", diagnostics.loadState)
        assertEquals("Running", diagnostics.runState)
    }

    @Test
    fun restartAndSafeProgrammingAddressCommissioning() = runTest {
        val sim = SimulatedKnxBusClient()
        try {
            sim.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = false))
            assertTrue(sim.restartDevice(IndividualAddress(1, 1, 10)))
            assertFalse(sim.restartDevice(IndividualAddress(1, 1, 40))) // offline

            // Two LEDs active -> safely refused
            sim.setProgrammingMode(IndividualAddress(1, 1, 15), true)
            val refused = sim.writeProgrammingAddress(IndividualAddress(1, 1, 55))
            assertFalse(refused.success)

            // Single LED active (1.1.20) -> succeeds and moves device to 1.1.55
            sim.setProgrammingMode(IndividualAddress(1, 1, 15), false)
            val written = sim.writeProgrammingAddress(IndividualAddress(1, 1, 55))
            assertTrue(written.success)
            assertTrue(sim.pingDevice(IndividualAddress(1, 1, 55)).reachable)
            assertTrue(sim.findProgrammingModeDevices().isEmpty())
        } finally {
            sim.close()
        }
    }
}
