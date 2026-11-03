package dev.knxfieldkit.bus

import dev.knxfieldkit.model.AuditStatus
import dev.knxfieldkit.model.BusConnectionConfig
import dev.knxfieldkit.model.BusTelegram
import dev.knxfieldkit.model.ConnectionState
import dev.knxfieldkit.model.DeviceDiagnosticDetails
import dev.knxfieldkit.model.DevicePingResult
import dev.knxfieldkit.model.GatewayEndpoint
import dev.knxfieldkit.model.GroupAddress
import dev.knxfieldkit.model.GroupReadResult
import dev.knxfieldkit.model.IndividualAddress
import dev.knxfieldkit.model.IndividualAddressWriteResult
import dev.knxfieldkit.model.LineAuditEntry
import dev.knxfieldkit.model.LineAuditReport
import dev.knxfieldkit.model.TopologyLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * Unified asynchronous KNX bus interface supporting both live KNXnet/IP hardware (via Calimero)
 * and deterministic virtual bus simulation for offline field workflow testing.
 */
interface KnxBusClient : AutoCloseable {
    val connectionState: StateFlow<ConnectionState>
    val incomingTelegrams: SharedFlow<BusTelegram>

    suspend fun discoverGateways(timeoutMs: Long = 3_000L): List<GatewayEndpoint>

    suspend fun connect(config: BusConnectionConfig)

    suspend fun disconnect()

    suspend fun sendGroupRead(address: GroupAddress, timeoutMs: Long = 2_500L): GroupReadResult

    suspend fun sendGroupWrite(address: GroupAddress, payload: ByteArray, compact6Bit: Boolean = false)

    suspend fun pingDevice(address: IndividualAddress, timeoutMs: Long = 1_500L): DevicePingResult

    suspend fun findProgrammingModeDevices(timeoutMs: Long = 2_000L): List<IndividualAddress>

    suspend fun readDeviceDiagnostics(address: IndividualAddress, timeoutMs: Long = 2_500L): DeviceDiagnosticDetails

    suspend fun restartDevice(address: IndividualAddress, timeoutMs: Long = 2_000L): Boolean

    suspend fun locateDevice(address: IndividualAddress, flashCount: Int = 4): Boolean

    suspend fun readRawProperty(
        address: IndividualAddress,
        objectIndex: Int,
        propertyId: Int,
        timeoutMs: Long = 2_000L,
    ): dev.knxfieldkit.model.RawPropertyReadResult

    suspend fun checkCouplerFilterTable(
        couplerAddress: IndividualAddress,
        targetGroup: GroupAddress,
    ): dev.knxfieldkit.model.CouplerFilterTableResult

    suspend fun writeProgrammingAddress(newAddress: IndividualAddress, timeoutMs: Long = 3_000L): IndividualAddressWriteResult

    /**
     * Rate-limited sequential scan across a list of [IndividualAddress] targets (safe for 9600 bps TP1 lines).
     */
    fun scanAddresses(
        addresses: List<IndividualAddress>,
        delayBetweenMs: Long = 80L,
        timeoutPerDeviceMs: Long = 1_000L,
    ): Flow<DevicePingResult> = flow {
        for ((index, addr) in addresses.withIndex()) {
            emit(pingDevice(addr, timeoutPerDeviceMs))
            if (index < addresses.lastIndex && delayBetweenMs > 0) {
                delay(delayBetweenMs)
            }
        }
    }

    /**
     * Audits every expected device on [line] against the live KNX bus, flagging unreachable devices
     * and BCU mask version mismatches.
     */
    suspend fun auditLine(
        line: TopologyLine,
        delayBetweenMs: Long = 80L,
        timeoutPerDeviceMs: Long = 1_000L,
        onProgress: (suspend (LineAuditEntry) -> Unit)? = null,
    ): LineAuditReport {
        val started = System.currentTimeMillis()
        val entries = mutableListOf<LineAuditEntry>()

        for ((index, device) in line.devices.withIndex()) {
            val ping = pingDevice(device.address, timeoutPerDeviceMs)
            val expectedMask = device.maskVersion
                ?.uppercase()
                ?.removePrefix("MV-")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val status = when {
                !ping.reachable -> AuditStatus.UNREACHABLE
                expectedMask != null && ping.maskVersionHex != null && expectedMask != ping.maskVersionHex ->
                    AuditStatus.ONLINE_MASK_MISMATCH
                else -> AuditStatus.ONLINE
            }

            val entry = LineAuditEntry(
                expectedDevice = device,
                pingResult = ping,
                status = status,
            )
            entries += entry
            onProgress?.invoke(entry)

            if (index < line.devices.lastIndex && delayBetweenMs > 0) {
                delay(delayBetweenMs)
            }
        }

        return LineAuditReport(
            lineId = line.id,
            lineName = line.name,
            startedAtEpochMs = started,
            completedAtEpochMs = System.currentTimeMillis(),
            entries = entries,
        )
    }

    override fun close()
}
