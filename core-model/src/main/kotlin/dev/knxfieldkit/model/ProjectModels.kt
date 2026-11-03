package dev.knxfieldkit.model

data class ComObjectFlags(
    val c: Boolean = true,
    val r: Boolean = false,
    val w: Boolean = true,
    val t: Boolean = true,
    val u: Boolean = false,
) {
    fun formatCompact(): String = buildString {
        append(if (c) 'C' else '-')
        append(if (r) 'R' else '-')
        append(if (w) 'W' else '-')
        append(if (t) 'T' else '-')
        append(if (u) 'U' else '-')
    }
}

data class DeviceComObject(
    val number: Int,
    val id: String,
    val name: String,
    val functionText: String = "",
    val dpt: String? = null,
    val sizeLabel: String = "1 bit",
    val flags: ComObjectFlags = ComObjectFlags(),
    val linkedGroupAddresses: List<GroupAddress> = emptyList(),
)

data class ComObjectLink(
    val id: String,
    val name: String,
    val functionText: String = "",
    val dpt: String? = null,
    val deviceAddress: IndividualAddress? = null,
    val objectNumber: Int? = null,
    val flags: ComObjectFlags = ComObjectFlags(),
)

data class GroupAddressInfo(
    val address: GroupAddress,
    val name: String,
    val description: String = "",
    val dpt: String? = null,
    val linkedObjects: List<ComObjectLink> = emptyList(),
)

data class GroupRangeNode(
    val id: String,
    val prefixLabel: String,
    val name: String,
    val directAddresses: List<GroupAddress> = emptyList(),
    val children: List<GroupRangeNode> = emptyList(),
) {
    val totalCount: Int
        get() = directAddresses.size + children.sumOf { it.totalCount }

    fun collectAllAddresses(): List<GroupAddress> =
        directAddresses + children.flatMap { it.collectAllAddresses() }
}

data class ProjectDevice(
    val address: IndividualAddress,
    val name: String,
    val description: String = "",
    val manufacturerName: String = "",
    val productName: String = "",
    val orderNumber: String = "",
    val hardwareSerial: String? = null,
    val maskVersion: String? = null,
    val groupAddresses: List<GroupAddress> = emptyList(),
    val comObjects: List<DeviceComObject> = emptyList(),
)

data class TopologyLine(
    val area: Int,
    val line: Int,
    val name: String,
    val mediumType: String = "TP",
    val devices: List<ProjectDevice> = emptyList(),
) {
    val id: String get() = "$area.$line"
}

data class TopologyArea(
    val area: Int,
    val name: String,
    val lines: List<TopologyLine> = emptyList(),
)

data class BuildingLocation(
    val name: String,
    val type: String = "Room",
    val deviceAddresses: List<IndividualAddress> = emptyList(),
    val children: List<BuildingLocation> = emptyList(),
) {
    fun collectAllDeviceAddresses(): List<IndividualAddress> =
        (deviceAddresses + children.flatMap { it.collectAllDeviceAddresses() }).distinct()
}

data class KeyringInterface(
    val individualAddress: IndividualAddress,
    val host: String? = null,
    val userId: Int? = null,
    val userPasswordHashOrPlain: String? = null,
    val authCodePlain: String? = null,
)

data class KnxKeyring(
    val projectName: String,
    val createdBy: String = "",
    val backboneKeyHex: String? = null,
    val interfaces: List<KeyringInterface> = emptyList(),
    val groupKeysHex: Map<GroupAddress, String> = emptyMap(),
    val deviceToolKeysHex: Map<IndividualAddress, String> = emptyMap(),
)

data class KnxProject(
    val projectId: String,
    val name: String,
    val etsVersion: String = "",
    val groupAddressStyle: GroupAddressStyle = GroupAddressStyle.THREE_LEVEL,
    val passwordProtected: Boolean = false,
    val areas: List<TopologyArea> = emptyList(),
    val groupAddresses: Map<GroupAddress, GroupAddressInfo> = emptyMap(),
    val groupRanges: List<GroupRangeNode> = emptyList(),
    val locations: List<BuildingLocation> = emptyList(),
) {
    val allLines: List<TopologyLine> by lazy { areas.flatMap { it.lines } }
    val allDevices: Map<IndividualAddress, ProjectDevice> by lazy {
        allLines.flatMap { it.devices }.associateBy { it.address }
    }

    val effectiveGroupRanges: List<GroupRangeNode> by lazy {
        if (groupRanges.isNotEmpty()) {
            groupRanges
        } else {
            val sortedGas = groupAddresses.keys.sorted()
            sortedGas.groupBy { it.main3 }.map { (main, mainList) ->
                val midChildren = mainList.groupBy { it.middle3 }.map { (mid, midList) ->
                    GroupRangeNode(
                        id = "mg-$main-$mid",
                        prefixLabel = "$main/$mid",
                        name = "Middle Group $main/$mid",
                        directAddresses = midList,
                    )
                }
                GroupRangeNode(
                    id = "main-$main",
                    prefixLabel = "$main",
                    name = "Main Group $main",
                    children = midChildren,
                )
            }
        }
    }

    fun findDevice(address: IndividualAddress): ProjectDevice? = allDevices[address]

    fun findGroupAddress(address: GroupAddress): GroupAddressInfo? = groupAddresses[address]

    fun findLine(area: Int, line: Int): TopologyLine? =
        areas.firstOrNull { it.area == area }?.lines?.firstOrNull { it.line == line }

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

    fun findLocationStackForDevice(address: IndividualAddress): List<BuildingLocation>? {
        fun search(loc: BuildingLocation, stack: List<BuildingLocation>): List<BuildingLocation>? {
            val current = stack + loc
            if (address in loc.deviceAddresses) return current
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
