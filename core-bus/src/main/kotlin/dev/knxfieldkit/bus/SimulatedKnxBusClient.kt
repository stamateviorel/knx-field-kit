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
        val gaWallCoveSwitch = GroupAddress.ofThreeLevel(1, 1, 3)
        val gaKitchenSwitch = GroupAddress.ofThreeLevel(1, 2, 1)
        val gaKitchenDim = GroupAddress.ofThreeLevel(1, 2, 2)
        val gaKitchenStep = GroupAddress.ofThreeLevel(1, 2, 3)
        val gaMasterSwitch = GroupAddress.ofThreeLevel(1, 7, 1)
        val gaMasterDim = GroupAddress.ofThreeLevel(1, 7, 2)
        val gaLivingTemp = GroupAddress.ofThreeLevel(1, 3, 1)
        val gaOutdoorTemp = GroupAddress.ofThreeLevel(1, 3, 2)
        val gaMasterTemp = GroupAddress.ofThreeLevel(1, 3, 3)
        val gaWindSpeed = GroupAddress.ofThreeLevel(1, 3, 4)
        val gaLivingScene = GroupAddress.ofThreeLevel(1, 4, 1)
        val gaLivingHvac = GroupAddress.ofThreeLevel(1, 5, 1)
        val gaMasterValve = GroupAddress.ofThreeLevel(1, 5, 2)
        val gaPanelText = GroupAddress.ofThreeLevel(1, 6, 1)

        val flWriteInput = ComObjectFlags(c = true, r = false, w = true, t = false, u = false)
        val flStatusReadTransmit = ComObjectFlags(c = true, r = true, w = false, t = true, u = false)
        val flSensorTransmit = ComObjectFlags(c = true, r = true, w = false, t = true, u = false)
        val flRockerToggle = ComObjectFlags(c = true, r = false, w = true, t = true, u = true)
        val flLedUpdate = ComObjectFlags(c = true, r = false, w = true, t = false, u = true)

        val line1Devices = listOf(
            ProjectDevice(
                address = IndividualAddress(1, 1, 0),
                name = "IP Router 752 Secure",
                description = "Main cabinet backbone router & tunneling gateway",
                manufacturerName = "Weinzierl",
                productName = "KNX IP Router 752 Secure",
                orderNumber = "752",
                hardwareSerial = "00C501000001",
                maskVersion = "091A",
                comObjects = listOf(
                    DeviceComObject(0, "O-1", "Routing Heartbeat", "IP/TP Coupler Status", "1.001", "1 bit", flStatusReadTransmit),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 10),
                name = "Living Room Switch Actuator 8x",
                description = "16A C-Load relay actuator — Ground Floor DB",
                manufacturerName = "MDT technologies",
                productName = "AKS-0816.03 Switch Actuator 8-fold",
                orderNumber = "AKS-0816.03",
                hardwareSerial = "008300112233",
                maskVersion = "07B0",
                groupAddresses = listOf(gaLightSwitch, gaLightStatus, gaWallCoveSwitch, gaLivingScene),
                comObjects = listOf(
                    DeviceComObject(0, "O-10", "Channel A: Switch", "Ceiling Light Relay", "1.001", "1 bit", flWriteInput, listOf(gaLightSwitch)),
                    DeviceComObject(1, "O-11", "Channel A: Status", "State Feedback On/Off", "1.001", "1 bit", flStatusReadTransmit, listOf(gaLightStatus)),
                    DeviceComObject(6, "O-16", "Channel B: Switch", "Wall Cove LED Strip", "1.001", "1 bit", flWriteInput, listOf(gaWallCoveSwitch)),
                    DeviceComObject(18, "O-18", "Scene Extension A/B", "Recall / Save Scene", "17.001", "1 byte", flWriteInput, listOf(gaLivingScene)),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 15),
                name = "Kitchen Dimming Actuator 4x",
                description = "4x250W trailing-edge universal LED dimmer",
                manufacturerName = "MDT technologies",
                productName = "AKD-0401.02 Universal Dimmer",
                orderNumber = "AKD-0401.02",
                hardwareSerial = "008300445566",
                maskVersion = "07B0",
                groupAddresses = listOf(gaKitchenSwitch, gaKitchenDim, gaKitchenStep),
                comObjects = listOf(
                    DeviceComObject(0, "O-30", "Ch.1 Switch", "Island Spots On/Off", "1.001", "1 bit", flWriteInput, listOf(gaKitchenSwitch)),
                    DeviceComObject(1, "O-31", "Ch.1 Relative Dimming", "Brighter / Darker / Stop", "3.007", "4 bit", flWriteInput, listOf(gaKitchenStep)),
                    DeviceComObject(2, "O-32", "Ch.1 Brightness Value", "Absolute Dimming %", "5.001", "1 byte", ComObjectFlags(c = true, r = true, w = true, t = true, u = false), listOf(gaKitchenDim)),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 20),
                name = "Living Room Glass Push Button II",
                description = "Color OLED wall controller with integrated temp sensor",
                manufacturerName = "MDT technologies",
                productName = "BE-GT20W.01 Smart Touch",
                orderNumber = "BE-GT20W.01",
                hardwareSerial = "008300778899",
                maskVersion = "07B0",
                groupAddresses = listOf(gaLightSwitch, gaLightStatus, gaWallCoveSwitch, gaLivingTemp, gaLivingScene, gaLivingHvac, gaPanelText),
                comObjects = listOf(
                    DeviceComObject(0, "O-201", "Button 1: Rocker", "Toggle Ceiling Light", "1.001", "1 bit", flRockerToggle, listOf(gaLightSwitch)),
                    DeviceComObject(1, "O-202", "Button 1: Status LED", "Ceiling Status Input", "1.001", "1 bit", flLedUpdate, listOf(gaLightStatus)),
                    DeviceComObject(2, "O-203", "Button 2: Rocker", "Toggle Wall Cove", "1.001", "1 bit", flRockerToggle, listOf(gaWallCoveSwitch)),
                    DeviceComObject(10, "O-210", "Internal Temp Sensor", "Measured Room Temp °C", "9.001", "2 bytes", flSensorTransmit, listOf(gaLivingTemp)),
                    DeviceComObject(15, "O-215", "Scene Button 4", "Recall Evening / Cinema", "17.001", "1 byte", ComObjectFlags(c = true, r = false, w = false, t = true, u = false), listOf(gaLivingScene)),
                    DeviceComObject(20, "O-220", "HVAC Operating Mode", "Comfort / Standby / Night", "20.102", "1 byte", ComObjectFlags(c = true, r = true, w = true, t = true, u = true), listOf(gaLivingHvac)),
                    DeviceComObject(30, "O-230", "Display Line 1 Text", "14-Byte Status Message", "16.001", "14 bytes", flLedUpdate, listOf(gaPanelText)),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 25),
                name = "Outdoor Weather Station",
                description = "Roof mast wind / sun / facade temperature sensor",
                manufacturerName = "Theben",
                productName = "Meteodata 140 S KNX",
                orderNumber = "1409201",
                hardwareSerial = "004800998877",
                maskVersion = "07B0",
                groupAddresses = listOf(gaOutdoorTemp, gaWindSpeed),
                comObjects = listOf(
                    DeviceComObject(0, "O-401", "Outdoor Air Temp", "Cyclical 5 min / 0.3 K", "9.001", "2 bytes", flSensorTransmit, listOf(gaOutdoorTemp)),
                    DeviceComObject(1, "O-402", "Wind Speed Anemometer", "m/s Facade Protection", "9.005", "2 bytes", flSensorTransmit, listOf(gaWindSpeed)),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 1, 40),
                name = "Garage Blind Actuator (Faulty Wiring)",
                description = "4-fold shutter actuator — TP connector loose",
                manufacturerName = "ABB",
                productName = "JRA/S 4.230.2.1",
                orderNumber = "2CDG110120R0011",
                hardwareSerial = "000200AABBCC",
                maskVersion = "0705",
                comObjects = listOf(
                    DeviceComObject(0, "O-501", "Blind Ch.1 Up/Down", "Move Garage Louvers", "1.008", "1 bit", flWriteInput),
                    DeviceComObject(1, "O-502", "Blind Ch.1 Stop/Step", "Slat Angle Step", "1.007", "1 bit", flWriteInput),
                ),
            ),
        )

        val line2Devices = listOf(
            ProjectDevice(
                address = IndividualAddress(1, 2, 0),
                name = "Upper Floor Line Coupler",
                description = "Galvanic TP/TP line coupler with filter table",
                manufacturerName = "MDT technologies",
                productName = "SCN-LK001.03 Line Coupler",
                orderNumber = "SCN-LK001.03",
                hardwareSerial = "008302000001",
                maskVersion = "091A",
                comObjects = listOf(
                    DeviceComObject(0, "O-600", "Subline Diagnostic", "TP Bus Voltage OK", "1.001", "1 bit", flStatusReadTransmit),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 2, 10),
                name = "Master Suite Dimming Actuator 4x",
                description = "Upper riser DB — bedroom & bath lighting",
                manufacturerName = "Jung",
                productName = "3904 REGHE Universal LED Dimmer",
                orderNumber = "3904REGHE",
                hardwareSerial = "000402101122",
                maskVersion = "07B0",
                groupAddresses = listOf(gaMasterSwitch, gaMasterDim),
                comObjects = listOf(
                    DeviceComObject(0, "O-610", "Master Pendant Switch", "Switch On/Off", "1.001", "1 bit", flWriteInput, listOf(gaMasterSwitch)),
                    DeviceComObject(2, "O-612", "Master Pendant Value", "Brightness 0..100%", "5.001", "1 byte", ComObjectFlags(c = true, r = true, w = true, t = true, u = false), listOf(gaMasterDim)),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 2, 15),
                name = "Upper Floor Heating Actuator 6x",
                description = "Electrothermal manifold valve controller 24V",
                manufacturerName = "MDT technologies",
                productName = "AKH-0600.03 Heating Actuator",
                orderNumber = "AKH-0600.03",
                hardwareSerial = "008302153344",
                maskVersion = "07B0",
                groupAddresses = listOf(gaMasterTemp, gaMasterValve),
                comObjects = listOf(
                    DeviceComObject(0, "O-620", "Ch.1 Actual Room Temp", "PI Controller Input", "9.001", "2 bytes", flWriteInput, listOf(gaMasterTemp)),
                    DeviceComObject(3, "O-623", "Ch.1 Valve Command Value", "Continuous 0..100%", "5.001", "1 byte", flStatusReadTransmit, listOf(gaMasterValve)),
                ),
            ),
            ProjectDevice(
                address = IndividualAddress(1, 2, 20),
                name = "Master Suite Room Controller",
                description = "Bedside glass touch controller + thermostat",
                manufacturerName = "Gira",
                productName = "Tastsensor 4 Komfort 3-gang",
                orderNumber = "500300",
                hardwareSerial = "000802205566",
                maskVersion = "07B0",
                groupAddresses = listOf(gaMasterSwitch, gaMasterDim, gaMasterTemp),
                comObjects = listOf(
                    DeviceComObject(0, "O-630", "Rocker 1: Master Light", "Switch Bedside Pendant", "1.001", "1 bit", flRockerToggle, listOf(gaMasterSwitch)),
                    DeviceComObject(2, "O-632", "Rocker 2: Dimming Preset", "Send Dimming Value", "5.001", "1 byte", flRockerToggle, listOf(gaMasterDim)),
                    DeviceComObject(8, "O-638", "Master Suite Temperature", "Internal NTC Probe", "9.001", "2 bytes", flSensorTransmit, listOf(gaMasterTemp)),
                ),
            ),
        )

        val groupAddresses = listOf(
            GroupAddressInfo(
                gaLightSwitch,
                "Living Room Ceiling Light Switch",
                description = "Main ceiling LED circuit relay A",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-10", "Channel A: Switch", "Ceiling Light Relay", "1.001", IndividualAddress(1, 1, 10), 0, flWriteInput),
                    ComObjectLink("O-201", "Button 1: Rocker", "Toggle Ceiling Light", "1.001", IndividualAddress(1, 1, 20), 0, flRockerToggle),
                ),
            ),
            GroupAddressInfo(
                gaLightStatus,
                "Living Room Ceiling Light Status",
                description = "Relay A state feedback",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-11", "Channel A: Status", "State Feedback On/Off", "1.001", IndividualAddress(1, 1, 10), 1, flStatusReadTransmit),
                    ComObjectLink("O-202", "Button 1: Status LED", "Ceiling Status Input", "1.001", IndividualAddress(1, 1, 20), 1, flLedUpdate),
                ),
            ),
            GroupAddressInfo(
                gaWallCoveSwitch,
                "Living Room Wall Cove LED Switch",
                description = "Indirect perimeter cove strip",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-16", "Channel B: Switch", "Wall Cove LED Strip", "1.001", IndividualAddress(1, 1, 10), 6, flWriteInput),
                    ComObjectLink("O-203", "Button 2: Rocker", "Toggle Wall Cove", "1.001", IndividualAddress(1, 1, 20), 2, flRockerToggle),
                ),
            ),
            GroupAddressInfo(
                gaKitchenSwitch,
                "Kitchen Island Spots On/Off",
                description = "Dimmer Ch.1 switching",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-30", "Ch.1 Switch", "Island Spots On/Off", "1.001", IndividualAddress(1, 1, 15), 0, flWriteInput),
                ),
            ),
            GroupAddressInfo(
                gaKitchenDim,
                "Kitchen Island Dimming Value",
                description = "Absolute brightness 0..100%",
                dpt = "5.001",
                linkedObjects = listOf(
                    ComObjectLink("O-32", "Ch.1 Brightness Value", "Absolute Dimming %", "5.001", IndividualAddress(1, 1, 15), 2, ComObjectFlags(c = true, r = true, w = true, t = true, u = false)),
                ),
            ),
            GroupAddressInfo(
                gaKitchenStep,
                "Kitchen Island Relative Dimming Step",
                description = "4-bit step brighter/darker/stop",
                dpt = "3.007",
                linkedObjects = listOf(
                    ComObjectLink("O-31", "Ch.1 Relative Dimming", "Brighter / Darker / Stop", "3.007", "4 bit".let { flWriteInput }.let { IndividualAddress(1, 1, 15) }, 1, flWriteInput),
                ),
            ),
            GroupAddressInfo(
                gaMasterSwitch,
                "Master Suite Pendant Switch",
                description = "Upper floor master bedroom ceiling pendant",
                dpt = "1.001",
                linkedObjects = listOf(
                    ComObjectLink("O-610", "Master Pendant Switch", "Switch On/Off", "1.001", IndividualAddress(1, 2, 10), 0, flWriteInput),
                    ComObjectLink("O-630", "Rocker 1: Master Light", "Switch Bedside Pendant", "1.001", IndividualAddress(1, 2, 20), 0, flRockerToggle),
                ),
            ),
            GroupAddressInfo(
                gaMasterDim,
                "Master Suite Pendant Dimming %",
                description = "Master bedroom brightness value",
                dpt = "5.001",
                linkedObjects = listOf(
                    ComObjectLink("O-612", "Master Pendant Value", "Brightness 0..100%", "5.001", IndividualAddress(1, 2, 10), 2, ComObjectFlags(c = true, r = true, w = true, t = true, u = false)),
                    ComObjectLink("O-632", "Rocker 2: Dimming Preset", "Send Dimming Value", "5.001", IndividualAddress(1, 2, 20), 2, flRockerToggle),
                ),
            ),
            GroupAddressInfo(
                gaLivingTemp,
                "Living Room Temperature",
                description = "Room air temperature sensor",
                dpt = "9.001",
                linkedObjects = listOf(
                    ComObjectLink("O-210", "Internal Temp Sensor", "Measured Room Temp °C", "9.001", IndividualAddress(1, 1, 20), 10, flSensorTransmit),
                ),
            ),
            GroupAddressInfo(
                gaOutdoorTemp,
                "Outdoor Facade Temperature",
                description = "Weather station north-shaded probe",
                dpt = "9.001",
                linkedObjects = listOf(
                    ComObjectLink("O-401", "Outdoor Air Temp", "Cyclical 5 min / 0.3 K", "9.001", IndividualAddress(1, 1, 25), 0, flSensorTransmit),
                ),
            ),
            GroupAddressInfo(
                gaMasterTemp,
                "Master Suite Temperature",
                description = "Upper floor bedroom temperature",
                dpt = "9.001",
                linkedObjects = listOf(
                    ComObjectLink("O-638", "Master Suite Temperature", "Internal NTC Probe", "9.001", IndividualAddress(1, 2, 20), 8, flSensorTransmit),
                    ComObjectLink("O-620", "Ch.1 Actual Room Temp", "PI Controller Input", "9.001", IndividualAddress(1, 2, 15), 0, flWriteInput),
                ),
            ),
            GroupAddressInfo(
                gaWindSpeed,
                "Roof Wind Speed",
                description = "Blind storm protection threshold",
                dpt = "9.005",
                linkedObjects = listOf(
                    ComObjectLink("O-402", "Wind Speed Anemometer", "m/s Facade Protection", "9.005", IndividualAddress(1, 1, 25), 1, flSensorTransmit),
                ),
            ),
            GroupAddressInfo(
                gaLivingScene,
                "Living Room Scene",
                description = "1=Evening, 2=Cinema, 3=All Off",
                dpt = "17.001",
                linkedObjects = listOf(
                    ComObjectLink("O-215", "Scene Button 4", "Recall Evening / Cinema", "17.001", IndividualAddress(1, 1, 20), 15, ComObjectFlags(c = true, r = false, w = false, t = true, u = false)),
                    ComObjectLink("O-18", "Scene Extension A/B", "Recall / Save Scene", "17.001", IndividualAddress(1, 1, 10), 18, flWriteInput),
                ),
            ),
            GroupAddressInfo(
                gaLivingHvac,
                "Living Room HVAC Mode",
                description = "Room operating mode",
                dpt = "20.102",
                linkedObjects = listOf(
                    ComObjectLink("O-220", "HVAC Operating Mode", "Comfort / Standby / Night", "20.102", IndividualAddress(1, 1, 20), 20, ComObjectFlags(c = true, r = true, w = true, t = true, u = true)),
                ),
            ),
            GroupAddressInfo(
                gaMasterValve,
                "Master Suite Underfloor Valve %",
                description = "Manifold actuator output",
                dpt = "5.001",
                linkedObjects = listOf(
                    ComObjectLink("O-623", "Ch.1 Valve Command Value", "Continuous 0..100%", "5.001", IndividualAddress(1, 2, 15), 3, flStatusReadTransmit),
                ),
            ),
            GroupAddressInfo(
                gaPanelText,
                "Glass Panel Info Text (14-Char)",
                description = "OLED status text display",
                dpt = "16.001",
                linkedObjects = listOf(
                    ComObjectLink("O-230", "Display Line 1 Text", "14-Byte Status Message", "16.001", IndividualAddress(1, 1, 20), 30, flLedUpdate),
                ),
            ),
        ).associateBy { it.address }

        val groupRanges = listOf(
            GroupRangeNode(
                id = "gr-lighting",
                prefixLabel = "1",
                name = "Lighting & Dimming",
                children = listOf(
                    GroupRangeNode("gr-1-1", "1/1", "Living Room Lighting", listOf(gaLightSwitch, gaLightStatus, gaWallCoveSwitch)),
                    GroupRangeNode("gr-1-2", "1/2", "Kitchen & Dining", listOf(gaKitchenSwitch, gaKitchenDim, gaKitchenStep)),
                    GroupRangeNode("gr-1-7", "1/7", "Master Suite Lighting", listOf(gaMasterSwitch, gaMasterDim)),
                ),
            ),
            GroupRangeNode(
                id = "gr-climate",
                prefixLabel = "1/3..5",
                name = "Climate, HVAC & Weather",
                children = listOf(
                    GroupRangeNode("gr-1-3", "1/3", "Temperatures & Weather", listOf(gaLivingTemp, gaOutdoorTemp, gaMasterTemp, gaWindSpeed)),
                    GroupRangeNode("gr-1-5", "1/5", "HVAC Modes & Valves", listOf(gaLivingHvac, gaMasterValve)),
                ),
            ),
            GroupRangeNode(
                id = "gr-central",
                prefixLabel = "1/4..6",
                name = "Scenes & Panel Displays",
                children = listOf(
                    GroupRangeNode("gr-1-4", "1/4", "Room Scenes", listOf(gaLivingScene)),
                    GroupRangeNode("gr-1-6", "1/6", "Touch Panel Displays", listOf(gaPanelText)),
                ),
            ),
        )

        val locations = listOf(
            BuildingLocation(
                name = "Ground Floor",
                type = "Floor",
                children = listOf(
                    BuildingLocation("Main Distribution Board", "Cabinet", listOf(IndividualAddress(1, 1, 0), IndividualAddress(1, 1, 10), IndividualAddress(1, 1, 15))),
                    BuildingLocation("Living Room", "Room", listOf(IndividualAddress(1, 1, 20))),
                    BuildingLocation("Garage", "Room", listOf(IndividualAddress(1, 1, 40))),
                ),
            ),
            BuildingLocation(
                name = "Upper Floor",
                type = "Floor",
                children = listOf(
                    BuildingLocation("Upper Riser Sub-DB", "Cabinet", listOf(IndividualAddress(1, 2, 0), IndividualAddress(1, 2, 10), IndividualAddress(1, 2, 15))),
                    BuildingLocation("Master Suite", "Room", listOf(IndividualAddress(1, 2, 20))),
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
            name = "Villa Horizon (ETS6 Demo)",
            etsVersion = "ETS6 6.2.2",
            groupAddressStyle = GroupAddressStyle.THREE_LEVEL,
            passwordProtected = false,
            areas = listOf(
                TopologyArea(
                    area = 1,
                    name = "Main Area (IP Backbone)",
                    lines = listOf(
                        TopologyLine(area = 1, line = 1, name = "Ground Floor TP", mediumType = "TP", devices = line1Devices),
                        TopologyLine(area = 1, line = 2, name = "Upper Floor TP", mediumType = "TP", devices = line2Devices),
                    ),
                )
            ),
            groupAddresses = groupAddresses,
            groupRanges = groupRanges,
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
        groupValues[GroupAddress.ofThreeLevel(1, 1, 3)] = IndividualAddress(1, 1, 10) to DptCodec.encodeBool(false)
        groupValues[GroupAddress.ofThreeLevel(1, 2, 1)] = IndividualAddress(1, 1, 15) to DptCodec.encodeBool(true)
        groupValues[GroupAddress.ofThreeLevel(1, 2, 2)] = IndividualAddress(1, 1, 15) to DptCodec.encodePercent(65.0)
        groupValues[GroupAddress.ofThreeLevel(1, 7, 1)] = IndividualAddress(1, 2, 10) to DptCodec.encodeBool(true)
        groupValues[GroupAddress.ofThreeLevel(1, 7, 2)] = IndividualAddress(1, 2, 10) to DptCodec.encodePercent(40.0)
        groupValues[GroupAddress.ofThreeLevel(1, 3, 1)] = IndividualAddress(1, 1, 20) to DptCodec.encodeFloat16(21.8)
        groupValues[GroupAddress.ofThreeLevel(1, 3, 2)] = IndividualAddress(1, 1, 25) to DptCodec.encodeFloat16(11.4)
        groupValues[GroupAddress.ofThreeLevel(1, 3, 3)] = IndividualAddress(1, 2, 20) to DptCodec.encodeFloat16(20.6)
        groupValues[GroupAddress.ofThreeLevel(1, 3, 4)] = IndividualAddress(1, 1, 25) to DptCodec.encodeFloat16(4.2)
        groupValues[GroupAddress.ofThreeLevel(1, 4, 1)] = IndividualAddress(1, 1, 20) to DptCodec.encodeScene(1)
        groupValues[GroupAddress.ofThreeLevel(1, 5, 1)] = IndividualAddress(1, 1, 20) to DptCodec.encodeHvacMode(HvacMode.COMFORT)
        groupValues[GroupAddress.ofThreeLevel(1, 5, 2)] = IndividualAddress(1, 2, 15) to DptCodec.encodePercent(34.0)
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
                macAddress = "00:50:C2:11:22:%02X".format(address.device),
                ipAddress = "192.168.1.${100 + address.device}",
                firmwareVersion = "v2.4",
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

    override suspend fun locateDevice(address: IndividualAddress, flashCount: Int): Boolean {
        ensureConnected()
        val dev = virtualDevices[address] ?: return false
        val wasProg = dev.programmingMode
        for (i in 0 until flashCount.coerceIn(1, 10)) {
            dev.programmingMode = true
            emitTelegram(address, apci = ApciType.INDIVIDUAL_ADDR_RESPONSE, payload = byteArrayOf(0x01))
            delay(280L)
            dev.programmingMode = false
            emitTelegram(address, apci = ApciType.INDIVIDUAL_ADDR_RESPONSE, payload = byteArrayOf(0x00))
            delay(280L)
        }
        dev.programmingMode = wasProg
        return true
    }

    override suspend fun readRawProperty(
        address: IndividualAddress,
        objectIndex: Int,
        propertyId: Int,
        timeoutMs: Long,
    ): RawPropertyReadResult {
        ensureConnected()
        val dev = virtualDevices[address]
        delay(30L)
        if (dev == null || !dev.online) {
            return RawPropertyReadResult(
                address = address,
                objectIndex = objectIndex,
                propertyId = propertyId,
                success = false,
                errorMessage = "Device $address is offline or not responding",
            )
        }
        val bytes = when (objectIndex) {
            0 -> when (propertyId) {
                11 -> HexBytes.fromHex(dev.serialHex)
                12 -> byteArrayOf(((dev.manufacturerId ushr 8) and 0xFF).toByte(), (dev.manufacturerId and 0xFF).toByte())
                54 -> byteArrayOf(if (dev.programmingMode) 0x01 else 0x00)
                56 -> byteArrayOf(0x00, 0x50, 0xC2.toByte(), 0x11, 0x22, address.device.toByte())
                58 -> byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), (100 + address.device).toByte())
                78 -> "BCU-TP-07B0".toByteArray(Charsets.ISO_8859_1)
                83 -> byteArrayOf(0x02, 0x04)
                else -> byteArrayOf(0x00, 0x01)
            }
            else -> byteArrayOf(0x00)
        }
        return RawPropertyReadResult(
            address = address,
            objectIndex = objectIndex,
            propertyId = propertyId,
            success = true,
            rawBytes = bytes,
        )
    }

    override suspend fun checkCouplerFilterTable(
        couplerAddress: IndividualAddress,
        targetGroup: GroupAddress,
    ): CouplerFilterTableResult {
        ensureConnected()
        delay(40L)
        val dev = virtualDevices[couplerAddress]
        val isCouplerOnline = dev?.online ?: true
        val isKnownGa = groupValues.containsKey(targetGroup)
        return if (!isCouplerOnline) {
            CouplerFilterTableResult(
                couplerAddress = couplerAddress,
                targetGroup = targetGroup,
                isRouted = false,
                routingMode = "Coupler Offline",
                explanation = "Line Coupler $couplerAddress did not answer on TP1 main line.",
            )
        } else if (isKnownGa) {
            CouplerFilterTableResult(
                couplerAddress = couplerAddress,
                targetGroup = targetGroup,
                isRouted = true,
                routingMode = "Filter (Normalbetrieb)",
                explanation = "Telegram allowed: Group address $targetGroup is explicitly configured in project and present in coupler 8 kB filter table bitmask.",
            )
        } else {
            CouplerFilterTableResult(
                couplerAddress = couplerAddress,
                targetGroup = targetGroup,
                isRouted = false,
                routingMode = "Filter (Normalbetrieb)",
                explanation = "Telegram filtered / blocked: Group address $targetGroup is unmapped in project; coupler filter table blocks inter-line transmission.",
            )
        }
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
