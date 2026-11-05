package dev.knxfieldkit.model

enum class TelegramPriority { SYSTEM, NORMAL, URGENT, LOW }

enum class ApciType(val label: String) {
    GROUP_READ("GroupValue_Read"),
    GROUP_RESPONSE("GroupValue_Response"),
    GROUP_WRITE("GroupValue_Write"),
    INDIVIDUAL_ADDR_READ("IndividualAddress_Read"),
    INDIVIDUAL_ADDR_RESPONSE("IndividualAddress_Response"),
    DEVICE_DESCRIPTOR_READ("DeviceDescriptor_Read"),
    DEVICE_DESCRIPTOR_RESPONSE("DeviceDescriptor_Response"),
    OTHER("Other")
}

data class BusTelegram(
    val id: Long,
    val timestampEpochMs: Long,
    val source: IndividualAddress,
    val destinationGroup: GroupAddress? = null,
    val destinationIndividual: IndividualAddress? = null,
    val apci: ApciType,
    val payload: ByteArray = ByteArray(0),
    val priority: TelegramPriority = TelegramPriority.LOW,
    val hopCount: Int = 6,
    val repeated: Boolean = false,
    val rawFrameHex: String = "",
    val isSecure: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BusTelegram
        if (id != other.id) return false
        if (timestampEpochMs != other.timestampEpochMs) return false
        if (source != other.source) return false
        if (destinationGroup != other.destinationGroup) return false
        if (destinationIndividual != other.destinationIndividual) return false
        if (apci != other.apci) return false
        if (!payload.contentEquals(other.payload)) return false
        if (priority != other.priority) return false
        if (hopCount != other.hopCount) return false
        if (repeated != other.repeated) return false
        if (rawFrameHex != other.rawFrameHex) return false
        if (isSecure != other.isSecure) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestampEpochMs.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (destinationGroup?.hashCode() ?: 0)
        result = 31 * result + (destinationIndividual?.hashCode() ?: 0)
        result = 31 * result + apci.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + hopCount
        result = 31 * result + repeated.hashCode()
        result = 31 * result + rawFrameHex.hashCode()
        result = 31 * result + isSecure.hashCode()
        return result
    }
}

data class EnrichedTelegram(
    val telegram: BusTelegram,
    val sourceDeviceName: String?,
    val destinationName: String?,
    val dptId: String?,
    val decodedValue: String,
    val isUnmappedGroup: Boolean
) {
    companion object {
        fun enrich(telegram: BusTelegram, project: KnxProject?): EnrichedTelegram {
            val sourceDeviceName = project?.findDevice(telegram.source)?.name
            val destinationName: String?
            val dptId: String?
            val isUnmappedGroup: Boolean
            val decodedValue: String

            if (telegram.destinationGroup != null) {
                val gaInfo = project?.findGroupAddress(telegram.destinationGroup)
                destinationName = gaInfo?.name
                dptId = gaInfo?.dpt
                isUnmappedGroup = (project != null && gaInfo == null)
                decodedValue = if (telegram.apci == ApciType.GROUP_READ) "" else DptCodec.decode(dptId, telegram.payload)
            } else {
                destinationName = telegram.destinationIndividual?.let { project?.findDevice(it)?.name }
                dptId = null
                isUnmappedGroup = false
                decodedValue = HexBytes.toHex(telegram.payload)
            }

            return EnrichedTelegram(telegram, sourceDeviceName, destinationName, dptId, decodedValue, isUnmappedGroup)
        }
    }
}

data class GatewayEndpoint(
    val name: String,
    val host: String,
    val port: Int = 3671,
    val individualAddress: IndividualAddress? = null,
    val macAddress: String? = null,
    val serialNumber: String? = null,
    val supportsTunneling: Boolean = true,
    val supportsRouting: Boolean = false,
    val supportsSecurity: Boolean = false
)

sealed interface BusConnectionConfig {
    data class IpTunneling(val gatewayHost: String, val gatewayPort: Int = 3671, val useNat: Boolean = false, val localHost: String? = null, val userPassword: String? = null, val deviceAuthCode: String? = null) : BusConnectionConfig
    data class IpRouting(val multicastAddress: String = "224.0.23.12", val port: Int = 3671, val networkInterfaceName: String? = null, val backboneKeyHex: String? = null) : BusConnectionConfig
    data class Simulator(val scenarioName: String = "Demo Smart Villa", val enablePeriodicTraffic: Boolean = true) : BusConnectionConfig
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val description: String) : ConnectionState
    data class Connected(val description: String, val config: BusConnectionConfig) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class GroupReadResult(
    val address: GroupAddress,
    val responded: Boolean,
    val responder: IndividualAddress? = null,
    val payload: ByteArray? = null,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
) {
    fun decodedValue(dptId: String?): String = payload?.let { DptCodec.decode(dptId, it) } ?: ""
}

data class DevicePingResult(
    val address: IndividualAddress,
    val reachable: Boolean,
    val maskVersion: Int? = null,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
) {
    val maskVersionHex: String? get() = maskVersion?.let { String.format(java.util.Locale.US, "%04X", it and 0xFFFF) }
}

enum class AuditStatus { ONLINE, ONLINE_MASK_MISMATCH, UNREACHABLE }

data class LineAuditEntry(
    val expectedDevice: ProjectDevice,
    val pingResult: DevicePingResult,
    val status: AuditStatus
)

data class LineAuditReport(
    val lineId: String,
    val lineName: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val entries: List<LineAuditEntry>
) {
    val onlineCount: Int get() = entries.count { it.status == AuditStatus.ONLINE }
    val mismatchCount: Int get() = entries.count { it.status == AuditStatus.ONLINE_MASK_MISMATCH }
    val unreachableCount: Int get() = entries.count { it.status == AuditStatus.UNREACHABLE }
}

data class DeviceDiagnosticDetails(
    val address: IndividualAddress,
    val reachable: Boolean,
    val maskVersionHex: String? = null,
    val serialNumberHex: String? = null,
    val manufacturerId: Int? = null,
    val programmingModeActive: Boolean = false,
    val macAddress: String? = null,
    val ipAddress: String? = null,
    val firmwareVersion: String? = null,
    val loadState: String? = null,
    val runState: String? = null,
    val notes: List<String> = emptyList()
)

data class RawPropertyReadResult(
    val address: IndividualAddress,
    val objectIndex: Int,
    val propertyId: Int,
    val success: Boolean,
    val rawBytes: ByteArray? = null,
    val errorMessage: String? = null,
) {
    val hexValue: String get() = rawBytes?.let { HexBytes.toHex(it) } ?: ""
    val textValue: String get() = rawBytes?.let {
        String(it, Charsets.ISO_8859_1).filter { c -> c.isLetterOrDigit() || c == '.' || c == '-' || c == '_' || c == ' ' }
    } ?: ""
    val intValue: Long? get() = rawBytes?.let {
        var res = 0L
        for (b in it) res = (res shl 8) or (b.toLong() and 0xFF)
        res
    }
}

data class CouplerFilterTableResult(
    val couplerAddress: IndividualAddress,
    val targetGroup: GroupAddress,
    val isRouted: Boolean,
    val routingMode: String,
    val explanation: String,
)

enum class DuplicateIssueType(val label: String) {
    PROJECT_DUPLICATE("Project Duplicate"),
    HARDWARE_CONFLICT("Mask / Serial Conflict"),
    ROGUE_DEVICE("Rogue Device on Free Address"),
}

data class DuplicateAddressIssue(
    val address: IndividualAddress,
    val type: DuplicateIssueType,
    val description: String,
    val projectDevice: ProjectDevice? = null,
    val livePingResult: DevicePingResult? = null,
)

data class DuplicateScanReport(
    val area: Int,
    val line: Int,
    val totalChecked: Int,
    val issues: List<DuplicateAddressIssue>,
    val scannedAtEpochMs: Long = System.currentTimeMillis(),
)
