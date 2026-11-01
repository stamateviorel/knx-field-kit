package dev.knxfieldkit.model

data class ComObjectLink(val id: String, val name: String, val functionText: String = "", val dpt: String? = null, val deviceAddress: IndividualAddress? = null)

data class GroupAddressInfo(val address: GroupAddress, val name: String, val description: String = "", val dpt: String? = null, val linkedObjects: List<ComObjectLink> = emptyList())

data class ProjectDevice(val address: IndividualAddress, val name: String, val description: String = "", val manufacturerName: String = "", val productName: String = "", val orderNumber: String = "", val hardwareSerial: String? = null, val maskVersion: String? = null, val groupAddresses: List<GroupAddress> = emptyList())

data class TopologyLine(val area: Int, val line: Int, val name: String, val mediumType: String = "TP", val devices: List<ProjectDevice> = emptyList()) {
    val id: String get() = "$area.$line"
}

data class TopologyArea(val area: Int, val name: String, val lines: List<TopologyLine> = emptyList())

data class BuildingLocation(val name: String, val type: String = "Room", val deviceAddresses: List<IndividualAddress> = emptyList(), val children: List<BuildingLocation> = emptyList())

data class KeyringInterface(val individualAddress: IndividualAddress, val host: String? = null, val userId: Int? = null, val userPasswordHashOrPlain: String? = null, val authCodePlain: String? = null)

data class KnxKeyring(val projectName: String, val createdBy: String = "", val backboneKeyHex: String? = null, val interfaces: List<KeyringInterface> = emptyList(), val groupKeysHex: Map<GroupAddress, String> = emptyMap(), val deviceToolKeysHex: Map<IndividualAddress, String> = emptyMap())

data class KnxProject(val projectId: String, val name: String, val etsVersion: String = "", val groupAddressStyle: GroupAddressStyle = GroupAddressStyle.THREE_LEVEL, val passwordProtected: Boolean = false, val areas: List<TopologyArea> = emptyList(), val groupAddresses: Map<GroupAddress, GroupAddressInfo> = emptyMap(), val locations: List<BuildingLocation> = emptyList()) {
    val allLines: List<TopologyLine> by lazy { areas.flatMap { it.lines } }
    val allDevices: Map<IndividualAddress, ProjectDevice> by lazy { allLines.flatMap { it.devices }.associateBy { it.address } }

    fun findDevice(address: IndividualAddress): ProjectDevice? = allDevices[address]

    fun findGroupAddress(address: GroupAddress): GroupAddressInfo? = groupAddresses[address]

    fun findLine(area: Int, line: Int): TopologyLine? = areas.firstOrNull { it.area == area }?.lines?.firstOrNull { it.line == line }

    fun findDeviceLocationPath(address: IndividualAddress): String? {
        fun search(loc: BuildingLocation, prefix: List<String>): String? {
            val current = prefix + loc.name
            if (address in loc.deviceAddresses) return current.joinToString(" › ")
            for (child in loc.children) {
                search(child, current)?.let { return it }
            }
            return null
        }
        for (root in locations) {
            search(root, emptyList())?.let { return it }
        }
        return null
    }
}
