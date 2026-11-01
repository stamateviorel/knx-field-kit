package dev.knxfieldkit.bus

import dev.knxfieldkit.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object DemoKnxProjectFactory {
    fun createDemoVillaProject(): KnxProject {
        val gaLightSwitch = GroupAddress.ofThreeLevel(1, 1, 1)
        val gaLightStatus = GroupAddress.ofThreeLevel(1, 1, 2)
        val gaKitchenSwitch = GroupAddress.ofThreeLevel(1, 2, 1)
        val gaKitchenDim = GroupAddress.ofThreeLevel(1, 2, 2)
        val gaKitchenStep = GroupAddress.ofThreeLevel(1, 2, 3)
        val gaLivingTemp = GroupAddress.ofThreeLevel(1, 3, 1)
        val gaOutdoorTemp = GroupAddress.ofThreeLevel(1, 3, 2)
        val gaLivingScene = GroupAddress.ofThreeLevel(1, 4, 1)
        val gaLivingHvac = GroupAddress.ofThreeLevel(1, 5, 1)
        val gaPanelText = GroupAddress.ofThreeLevel(1, 6, 1)

        val devices = listOf(
            ProjectDevice(
                address = IndividualAddress(1, 1, 0),
                name = "IP Router Secure",
                manufacturerName = "Weinzierl",
                productName = "KNX IP Router 752 Secure",
                orderNumber = "752",
                hardwareSerial = "00C501000001",
                maskVersion = "091A",
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 10),
                name = "Living Room Switch Actuator 8x",
                manufacturerName = "MDT technologies",
                productName = "AKS-0816.03 Switch Actuator",
                orderNumber = "AKS-0816.03",
                hardwareSerial = "008300112233",
                maskVersion = "07B0",
                groupAddresses = listOf(gaLightSwitch, gaLightStatus),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 15),
                name = "Kitchen Dimming Actuator 4x",
                manufacturerName = "MDT technologies",
                productName = "AKD-0401.02 Dimmer",
                orderNumber = "AKD-0401.02",
                hardwareSerial = "008300445566",
                maskVersion = "07B0",
                groupAddresses = listOf(gaKitchenSwitch, gaKitchenDim, gaKitchenStep),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 20),
                name = "Living Room Glass Push Button II",
                manufacturerName = "MDT technologies",
                productName = "BE-GT20W.01 Smart Touch",
                orderNumber = "BE-GT20W.01",
                hardwareSerial = "008300778899",
                maskVersion = "07B0",
                groupAddresses = listOf(gaLightSwitch, gaLightStatus, gaLivingTemp, gaLivingScene, gaLivingHvac, gaPanelText),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 25),
                name = "Outdoor Weather Station",
                manufacturerName = "Theben",
                productName = "Meteodata 140 S KNX",
                orderNumber = "1409201",
                hardwareSerial = "004800998877",
                maskVersion = "07B0",
                groupAddresses = listOf(gaOutdoorTemp),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 40),
                name = "Garage Blind Actuator (Faulty Wiring)",
                manufacturerName = "ABB",
                productName = "JRA/S 4.230.2.1",
                orderNumber = "2CDG110120R0011",
                hardwareSerial = "000200AABBCC",
                maskVersion = "0705",
            ),
        )

        val groupAddresses = listOf(
            GroupAddressInfo(
                gaLightSwitch,
                "Living Room Ceiling Light Switch",
                description = "Main ceiling LED circuit relay A",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-10", "Channel A Switch", "On/Off Output", "1.001", IndividualAddress(1, 1, 10)),
                    ComObjectLink("O-201", "Rocker 1 Left", "Toggle Ceiling Light", "1.001", IndividualAddress(1, 1, 20)),
                ),
            ),
            GroupAddressInfo(
                gaLightStatus,
                "Living Room Ceiling Light Status",
                description = "Relay A feedback status",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-11", "Channel A Status", "State Feedback", "1.001", IndividualAddress(1, 1, 10)),
                    ComObjectLink("O-202", "Rocker 1 LED", "Status Indicator", "1.001", IndividualAddress(1, 1, 20)),
                ),
            ),
            GroupAddressInfo(
                gaKitchenSwitch,
                "Kitchen Island Spots On/Off",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-30", "Dimmer Ch.1 Switch", "Switch On/Off", "1.001", IndividualAddress(1, 1, 15)),
                ),
            ),
            GroupAddressInfo(
                gaKitchenDim,
                "Kitchen Island Dimming Value",
                dpt = "5.001",
                linkedObjects = listOf(
                    ComObjectLink("O-32", "Dimmer Ch.1 Absolute %", "Brightness Value", "5.001", IndividualAddress(1, 1, 15)),
                ),
            ),
            GroupAddressInfo(
                gaKitchenStep,
                "Kitchen Island Relative Dimming Step",
                dpt = "3.007",
                linkedObjects = listOf(
                    ComObjectLink("O-31", "Dimmer Ch.1 Relative", "Brighter / Darker / Stop", "3.007", IndividualAddress(1, 1, 15)),
                ),
            ),
            GroupAddressInfo(
                gaLivingTemp,
                "Living Room Temperature",
                dpt = "9.001",
                linkedObjects = listOf(
                    ComObjectLink("O-210", "Internal Temp Sensor", "Measured Room Temp", "9.001", IndividualAddress(1, 1, 20)),
                ),
            ),
            GroupAddressInfo(
                gaOutdoorTemp,
                "Outdoor Temperature",
                dpt = "9.001",
                linkedObjects = listOf(
                    ComObjectLink("O-401", "Facade Temp Probe", "Outdoor Air Temp", "9.001", IndividualAddress(1, 1, 25)),
                ),
            ),
            GroupAddressInfo(
                gaLivingScene,
                "Living Room Scene",
                dpt = "17.001",
                linkedObjects = listOf(
                    ComObjectLink("O-215", "Scene Recall", "1=Evening, 2=Cinema, 3=All Off", "17.001", IndividualAddress(1, 1, 20)),
                    ComObjectLink("O-18", "Scene Input A", "Actuator Scene", "17.001", IndividualAddress(1, 1, 10)),
                ),
            ),
            GroupAddressInfo(
                gaLivingHvac,
                "Living Room HVAC Mode",
                dpt = "20.102",
                linkedObjects = listOf(
                    ComObjectLink("O-220", "Room Operating Mode", "Comfort / Standby / Night", "20.102", IndividualAddress(1, 1, 20)),
                ),
            ),
            GroupAddressInfo(
                gaPanelText,
                "Glass Panel Info Text (14-Char)",
                dpt = "16.001",
                linkedObjects = listOf(
                    ComObjectLink("O-230", "Display Line 1 Text", "Status Message Display", "16.001", IndividualAddress(1, 1, 20)),
                ),
            ),
        ).associateBy { it.address }

        val locations = listOf(
            BuildingLocation(
                name = "Ground Floor",
                type = "Floor",
                children = listOf(
                    BuildingLocation("Distribution Board", "Cabinet", listOf(IndividualAddress(1, 1, 0))),
                    BuildingLocation("Living Room", "Room", listOf(IndividualAddress(1, 1, 10), IndividualAddress(1, 1, 20))),
                    BuildingLocation("Kitchen", "Room", listOf(IndividualAddress(1, 1, 15))),
                    BuildingLocation("Garage", "Room", listOf(IndividualAddress(1, 1, 40))),
                ),
            ),
            BuildingLocation(
                name = "Exterior",
                type = "Outdoor",
                children = listOf(
                    BuildingLocation("South Roof / Facade", "Zone", listOf(IndividualAddress(1, 1, 25))),
                ),
            ),
        )

        return KnxProject(
            projectId = "P-04A1",
            name = "Villa Horizon (Demo)",
            etsVersion = "ETS6 6.2",
            groupAddressStyle = GroupAddressStyle.THREE_LEVEL,
            passwordProtected = false,
            areas = listOf(
                TopologyArea(
                    area = 1,
                    name = "Main Area",
                    lines = listOf(TopologyLine(area = 1, line = 1, name = "Ground Floor TP", mediumType = "TP", devices = devices)),
                )
            ),
            groupAddresses = groupAddresses,
            locations = locations,
        )
    }
}

class SimulatedKnxBusClient(
    parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : KnxBusClient {

    private data class VirtualDevice(
        val maskVersion: Int,
        val serialHex: String,
        val manufacturerId: Int,
        var online: Boolean,
        var programmingMode: Boolean,
    )

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val telegramSeq = AtomicLong(0L)
    private val localTunnelAddr = IndividualAddress(1, 1, 250)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingTelegrams = MutableSharedFlow<BusTelegram>(replay = 0, extraBufferCapacity = 512)
    override val incomingTelegrams: SharedFlow<BusTelegram> = _incomingTelegrams.asSharedFlow()

    private val virtualDevices = ConcurrentHashMap<IndividualAddress, VirtualDevice>()
    private val groupValues = ConcurrentHashMap<GroupAddress, Pair<IndividualAddress, ByteArray>>()
    private var periodicJob: Job? = null

    init {
        syncWithProject(DemoKnxProjectFactory.createDemoVillaProject())
        seedDemoGroupValues()
    }

    private fun seedDemoGroupValues() {
        groupValues[GroupAddress.ofThreeLevel(1, 1, 1)] = IndividualAddress(1, 1, 10) to DptCodec.encodeBool(true)
        groupValues[GroupAddress.ofThreeLevel(1, 1, 2)] = IndividualAddress(1, 1, 10) to DptCodec.encodeBool(true)
        groupValues[GroupAddress.ofThreeLevel(1, 2, 1)] = IndividualAddress(1, 1, 15) to DptCodec.encodeBool(false)
        groupValues[GroupAddress.ofThreeLevel(1, 2, 2)] = IndividualAddress(1, 1, 15) to DptCodec.encodePercent(65.0)
        groupValues[GroupAddress.ofThreeLevel(1, 3, 1)] = IndividualAddress(1, 1, 20) to DptCodec.encodeFloat16(21.8)
        groupValues[GroupAddress.ofThreeLevel(1, 3, 2)] = IndividualAddress(1, 1, 25) to DptCodec.encodeFloat16(11.4)
        groupValues[GroupAddress.ofThreeLevel(1, 4, 1)] = IndividualAddress(1, 1, 20) to DptCodec.encodeScene(1)
        groupValues[GroupAddress.ofThreeLevel(1, 5, 1)] = IndividualAddress(1, 1, 20) to DptCodec.encodeHvacMode(HvacMode.COMFORT)
        groupValues[GroupAddress.ofThreeLevel(1, 6, 1)] = IndividualAddress(1, 1, 20) to DptCodec.encodeString14("VILLA OK")
    }

    fun syncWithProject(
        project: KnxProject,
        offlineAddresses: Set<IndividualAddress> = setOf(IndividualAddress(1, 1, 40)),
        progModeAddresses: Set<IndividualAddress> = setOf(IndividualAddress(1, 1, 20)),
    ) {
        virtualDevices.clear()
        for (dev in project.allDevices.values) {
            val mask = dev.maskVersion?.removePrefix("MV-")?.toIntOrNull(16) ?: 0x07B0
            val serial = dev.hardwareSerial ?: "0083%08X".format(dev.address.raw)
            virtualDevices[dev.address] = VirtualDevice(
                maskVersion = mask,
                serialHex = serial,
                manufacturerId = 0x0083,
                online = dev.address !in offlineAddresses,
                programmingMode = dev.address in progModeAddresses,
            )
        }
        for ((ga, info) in project.groupAddresses) {
            if (!groupValues.containsKey(ga)) {
                val responder = info.linkedObjects.firstNotNullOfOrNull { it.deviceAddress }
                    ?: project.allDevices.keys.firstOrNull { it !in offlineAddresses }
                    ?: IndividualAddress(1, 1, 10)
                val initialBytes = when (DptCodec.resolveMainDpt(info.dpt)) {
                    MainDpt.DPT_1_BOOL -> DptCodec.encodeBool(false)
                    MainDpt.DPT_5_PERCENT -> DptCodec.encodePercent(50.0)
                    MainDpt.DPT_9_FLOAT -> DptCodec.encodeFloat16(21.0)
                    MainDpt.DPT_17_SCENE -> DptCodec.encodeScene(1)
                    MainDpt.DPT_20_HVAC -> DptCodec.encodeHvacMode(HvacMode.COMFORT)
                    else -> byteArrayOf(0x00)
                }
                groupValues[ga] = responder to initialBytes
            }
        }
    }

    fun setProgrammingMode(address: IndividualAddress, active: Boolean) {
        virtualDevices.compute(address) { _, existing ->
            (existing ?: VirtualDevice(0x07B0, "008300000001", 0x0083, online = true, programmingMode = active))
                .apply { programmingMode = active }
        }
    }

    fun setDeviceOnline(address: IndividualAddress, online: Boolean) {
        virtualDevices.compute(address) { _, existing ->
            (existing ?: VirtualDevice(0x07B0, "008300000001", 0x0083, online = online, programmingMode = false))
                .apply { this.online = online }
        }
    }

    override suspend fun discoverGateways(timeoutMs: Long): List<GatewayEndpoint> = listOf(
        GatewayEndpoint(
            name = "Simulated KNX IP Router 752",
            host = "127.0.0.1",
            port = 3671,
            individualAddress = IndividualAddress(1, 1, 0),
            macAddress = "00:24:6D:01:02:03",
            serialNumber = "00C501000001",
            supportsTunneling = true,
            supportsRouting = true,
            supportsSecurity = true,
        )
    )

    override suspend fun connect(config: BusConnectionConfig) {
        periodicJob?.cancel()
        val label = (config as? BusConnectionConfig.Simulator)?.scenarioName ?: "Virtual TP1 Bus"
        _connectionState.value = ConnectionState.Connected("Virtual KNX Bus ($label)", config)

        if ((config as? BusConnectionConfig.Simulator)?.enablePeriodicTraffic == true) {
            periodicJob = scope.launch {
                var tick = 0
                while (isActive) {
                    delay(2_500L)
                    tick++
                    when (tick % 3) {
                        1 -> {
                            val temp = 21.5 + (tick % 5) * 0.1
                            val payload = DptCodec.encodeFloat16(temp)
                            val ga = GroupAddress.ofThreeLevel(1, 3, 1)
                            groupValues[ga] = IndividualAddress(1, 1, 20) to payload
                            emitTelegram(IndividualAddress(1, 1, 20), dstGroup = ga, apci = ApciType.GROUP_WRITE, payload = payload)
                        }
                        2 -> {
                            val outTemp = 11.2 + (tick % 4) * 0.2
                            val payload = DptCodec.encodeFloat16(outTemp)
                            val ga = GroupAddress.ofThreeLevel(1, 3, 2)
                            groupValues[ga] = IndividualAddress(1, 1, 25) to payload
                            emitTelegram(IndividualAddress(1, 1, 25), dstGroup = ga, apci = ApciType.GROUP_WRITE, payload = payload)
                        }
                        else -> {
                            // Unmapped ghost group address traffic for diagnostic visibility
                            emitTelegram(
                                source = IndividualAddress(1, 1, 25),
                                dstGroup = GroupAddress.ofThreeLevel(9, 7, 99),
                                apci = ApciType.GROUP_WRITE,
                                payload = byteArrayOf(0x01),
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun disconnect() {
        periodicJob?.cancel()
        periodicJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendGroupRead(address: GroupAddress, timeoutMs: Long): GroupReadResult {
        ensureConnected()
        emitTelegram(localTunnelAddr, dstGroup = address, apci = ApciType.GROUP_READ, payload = ByteArray(0))
        val entry = groupValues[address]
        val responder = entry?.first
        val responderOnline = responder?.let { virtualDevices[it]?.online ?: true } ?: false
        return if (entry != null && responder != null && responderOnline) {
            delay(18L)
            val copy = entry.second.copyOf()
            emitTelegram(responder, dstGroup = address, apci = ApciType.GROUP_RESPONSE, payload = copy)
            GroupReadResult(
                address = address,
                responded = true,
                responder = responder,
                payload = copy,
                latencyMs = 18L,
            )
        } else {
            delay(minOf(timeoutMs, 60L))
            GroupReadResult(
                address = address,
                responded = false,
                latencyMs = minOf(timeoutMs, 60L),
                errorMessage = "No responder for $address",
            )
        }
    }

    override suspend fun sendGroupWrite(address: GroupAddress, payload: ByteArray, compact6Bit: Boolean) {
        ensureConnected()
        val copy = payload.copyOf()
        emitTelegram(localTunnelAddr, dstGroup = address, apci = ApciType.GROUP_WRITE, payload = copy)
        val existingResponder = groupValues[address]?.first ?: IndividualAddress(1, 1, 10)
        groupValues[address] = existingResponder to copy

        if (address == GroupAddress.ofThreeLevel(1, 1, 1)) {
            val statusGa = GroupAddress.ofThreeLevel(1, 1, 2)
            groupValues[statusGa] = IndividualAddress(1, 1, 10) to copy
            scope.launch {
                delay(25L)
                emitTelegram(IndividualAddress(1, 1, 10), dstGroup = statusGa, apci = ApciType.GROUP_WRITE, payload = copy)
            }
        } else if (address == GroupAddress.ofThreeLevel(1, 2, 3) && copy.isNotEmpty()) {
            val nibble = copy.last().toInt() and 0x0F
            val step = nibble and 0x07
            if (step > 0) {
                val dimGa = GroupAddress.ofThreeLevel(1, 2, 2)
                val currentPct = groupValues[dimGa]?.second?.let { runCatching { DptCodec.decodePercent(it) }.getOrDefault(50.0) } ?: 50.0
                val delta = 100.0 / (1 shl (step - 1))
                val nextPct = (if ((nibble and 0x08) != 0) currentPct + delta else currentPct - delta).coerceIn(0.0, 100.0)
                val nextPayload = DptCodec.encodePercent(nextPct)
                groupValues[dimGa] = IndividualAddress(1, 1, 15) to nextPayload
                scope.launch {
                    delay(25L)
                    emitTelegram(IndividualAddress(1, 1, 15), dstGroup = dimGa, apci = ApciType.GROUP_WRITE, payload = nextPayload)
                }
            }
        }
    }

    override suspend fun pingDevice(address: IndividualAddress, timeoutMs: Long): DevicePingResult {
        ensureConnected()
        emitTelegram(localTunnelAddr, dstInd = address, apci = ApciType.DEVICE_DESCRIPTOR_READ, payload = ByteArray(0))
        val dev = virtualDevices[address]
        return if (dev != null && dev.online) {
            delay(14L)
            val dd0Bytes = byteArrayOf(((dev.maskVersion ushr 8) and 0xFF).toByte(), (dev.maskVersion and 0xFF).toByte())
            emitTelegram(address, dstInd = localTunnelAddr, apci = ApciType.DEVICE_DESCRIPTOR_RESPONSE, payload = dd0Bytes)
            DevicePingResult(address = address, reachable = true, maskVersion = dev.maskVersion, latencyMs = 14L)
        } else {
            delay(minOf(timeoutMs, 35L))
            DevicePingResult(
                address = address,
                reachable = false,
                latencyMs = minOf(timeoutMs, 35L),
                errorMessage = "Timeout waiting for DeviceDescriptor_Response",
            )
        }
    }

    override suspend fun findProgrammingModeDevices(timeoutMs: Long): List<IndividualAddress> {
        ensureConnected()
        emitTelegram(localTunnelAddr, apci = ApciType.INDIVIDUAL_ADDR_READ, payload = ByteArray(0))
        delay(20L)
        val active = virtualDevices.entries
            .filter { it.value.online && it.value.programmingMode }
            .map { it.key }
            .sorted()
        for (addr in active) {
            emitTelegram(addr, apci = ApciType.INDIVIDUAL_ADDR_RESPONSE, payload = ByteArray(0))
        }
        return active
    }

    override suspend fun readDeviceDiagnostics(address: IndividualAddress, timeoutMs: Long): DeviceDiagnosticDetails {
        ensureConnected()
        val dev = virtualDevices[address]
        return if (dev != null && dev.online) {
            delay(20L)
            DeviceDiagnosticDetails(
                address = address,
                reachable = true,
                maskVersionHex = "%04X".format(dev.maskVersion and 0xFFFF),
                serialNumberHex = dev.serialHex,
                manufacturerId = dev.manufacturerId,
                programmingModeActive = dev.programmingMode,
                loadState = "Loaded",
                runState = "Running",
                notes = listOf("Simulated BCU health OK"),
            )
        } else {
            delay(35L)
            DeviceDiagnosticDetails(
                address = address,
                reachable = false,
                notes = listOf("Device did not respond on TP1 line (check bus power / choke / TP connector)"),
            )
        }
    }

    override suspend fun restartDevice(address: IndividualAddress, timeoutMs: Long): Boolean {
        ensureConnected()
        val dev = virtualDevices[address]
        delay(25L)
        if (dev != null && dev.online) {
            emitTelegram(localTunnelAddr, dstInd = address, apci = ApciType.OTHER, payload = byteArrayOf(0x03, 0x80.toByte()))
            return true
        }
        return false
    }

    override suspend fun writeProgrammingAddress(
        newAddress: IndividualAddress,
        timeoutMs: Long,
    ): IndividualAddressWriteResult {
        ensureConnected()
        val active = findProgrammingModeDevices(timeoutMs)
        if (active.isEmpty()) {
            return IndividualAddressWriteResult(
                success = false,
                newAddress = newAddress,
                message = "Refused: No device currently has its Programming LED active.",
            )
        }
        if (active.size > 1) {
            return IndividualAddressWriteResult(
                success = false,
                newAddress = newAddress,
                message = "Refused for safety: ${active.size} devices have Programming LEDs active (${active.joinToString()}). Leave only 1 LED active.",
            )
        }
        val oldAddr = active.single()
        val moved = virtualDevices.remove(oldAddr)
        if (moved != null) {
            moved.programmingMode = false
            virtualDevices[newAddress] = moved
        }
        delay(25L)
        return IndividualAddressWriteResult(
            success = true,
            newAddress = newAddress,
            message = "Programmed $oldAddr -> $newAddress (Programming LED turned off)",
        )
    }

    override fun close() {
        periodicJob?.cancel()
        scope.cancel()
    }

    private fun ensureConnected() {
        check(_connectionState.value is ConnectionState.Connected) { "KNX bus is not connected" }
    }

    private fun emitTelegram(
        source: IndividualAddress,
        dstGroup: GroupAddress? = null,
        dstInd: IndividualAddress? = null,
        apci: ApciType,
        payload: ByteArray,
    ) {
        _incomingTelegrams.tryEmit(
            BusTelegram(
                id = telegramSeq.incrementAndGet(),
                timestampEpochMs = System.currentTimeMillis(),
                source = source,
                destinationGroup = dstGroup,
                destinationIndividual = dstInd,
                apci = apci,
                payload = payload,
            )
        )
    }
}
