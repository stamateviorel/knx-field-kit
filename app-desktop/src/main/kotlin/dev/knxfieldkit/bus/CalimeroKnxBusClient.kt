package dev.knxfieldkit.bus

import dev.knxfieldkit.model.ApciType
import dev.knxfieldkit.model.BusConnectionConfig
import dev.knxfieldkit.model.BusTelegram
import dev.knxfieldkit.model.ConnectionState
import dev.knxfieldkit.model.DeviceDiagnosticDetails
import dev.knxfieldkit.model.DevicePingResult
import dev.knxfieldkit.model.GatewayEndpoint
import dev.knxfieldkit.model.GroupAddress
import dev.knxfieldkit.model.GroupReadResult
import dev.knxfieldkit.model.HexBytes
import dev.knxfieldkit.model.IndividualAddress
import dev.knxfieldkit.model.IndividualAddressWriteResult
import dev.knxfieldkit.model.TelegramPriority
import io.calimero.CloseEvent
import io.calimero.DataUnitBuilder
import io.calimero.DeviceDescriptor
import io.calimero.FrameEvent
import io.calimero.KNXException
import io.calimero.KNXTimeoutException
import io.calimero.Priority
import io.calimero.cemi.CEMILData
import io.calimero.knxnetip.Discoverer
import io.calimero.knxnetip.SecureConnection
import io.calimero.knxnetip.util.ServiceFamiliesDIB.ServiceFamily
import io.calimero.link.KNXNetworkLink
import io.calimero.link.KNXNetworkLinkIP
import io.calimero.link.NetworkLinkListener
import io.calimero.link.medium.TPSettings
import io.calimero.mgmt.ManagementClientImpl
import io.calimero.mgmt.ManagementProceduresImpl
import io.calimero.mgmt.PropertyAccess
import io.calimero.secure.Security
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import io.calimero.GroupAddress as CalimeroGroupAddress
import io.calimero.IndividualAddress as CalimeroIndividualAddress

/**
 * Production KNXnet/IP & Management bus client powered by Calimero 3.x.
 *
 * Supports:
 * - KNXnet/IP Multicast Gateway Discovery (`SearchRequest` / `SearchResponse`)
 * - Unicast/NAT KNXnet/IP Tunneling, Secure Tunneling, and Multicast Routing
 * - Live `CEMILData` bus/group monitoring
 * - `GroupValue_Read` / `GroupValue_Write` (6-bit compact and standard extended APDUs)
 * - `DeviceDescriptor_Read` (DD0) reachability ping and BCU mask readout
 * - `IndividualAddress_Read` Programming-LED active device scan
 * - Interface Object property diagnostics (Serial Number, Manufacturer ID, Load/Run state)
 */
class CalimeroKnxBusClient(
    parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : KnxBusClient {

    private companion object {
        const val APCI_GROUP_READ = 0x0000
        const val APCI_GROUP_RESPONSE = 0x0040
        const val APCI_GROUP_WRITE = 0x0080
        const val APCI_INDIVIDUAL_ADDR_WRITE = 0x00C0
        const val APCI_INDIVIDUAL_ADDR_READ = 0x0100
        const val APCI_INDIVIDUAL_ADDR_RESPONSE = 0x0140
        const val APCI_DEVICE_DESC_READ = 0x0300
        const val APCI_DEVICE_DESC_RESPONSE = 0x0340

        const val DEVICE_OBJECT_INDEX = 0
        const val APPLICATION_OBJECT_INDEX = 3
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val connectionMutex = Mutex()
    private val telegramCounter = AtomicLong(0L)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingTelegrams = MutableSharedFlow<BusTelegram>(replay = 0, extraBufferCapacity = 512)
    override val incomingTelegrams: SharedFlow<BusTelegram> = _incomingTelegrams.asSharedFlow()

    private val pendingGroupReads = ConcurrentHashMap<GroupAddress, CompletableDeferred<BusTelegram>>()

    @Volatile
    private var activeLink: KNXNetworkLink? = null

    private val linkListener = object : NetworkLinkListener {
        override fun indication(e: FrameEvent) {
            handleFrameEvent(e)
        }

        override fun confirmation(e: FrameEvent) {
            handleFrameEvent(e)
        }

        override fun linkClosed(e: CloseEvent) {
            activeLink = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun discoverGateways(timeoutMs: Long): List<GatewayEndpoint> = withContext(Dispatchers.IO) {
        val discoverer = Discoverer.udp(true).timeout(Duration.ofMillis(timeoutMs.coerceAtLeast(500L)))
        try {
            val results = discoverer.search().await()
            results.map { res ->
                val sr = res.response()
                val dev = sr.device
                val endpoint = sr.controlEndpoint.endpoint()
                val families = sr.serviceFamilies.families()
                val calAddr = dev.address
                GatewayEndpoint(
                    name = dev.name.ifBlank { "KNXnet/IP Gateway" },
                    host = (endpoint?.address ?: res.remoteEndpoint().address).hostAddress,
                    port = endpoint?.port?.takeIf { it > 0 } ?: res.remoteEndpoint().port,
                    individualAddress = IndividualAddress(calAddr.area, calAddr.line, calAddr.device),
                    macAddress = dev.macAddressString,
                    serialNumber = dev.serialNumber().toString(),
                    supportsTunneling = families.containsKey(ServiceFamily.Tunneling),
                    supportsRouting = families.containsKey(ServiceFamily.Routing),
                    supportsSecurity = families.containsKey(ServiceFamily.Security),
                )
            }.distinctBy { "${it.host}:${it.port}:${it.serialNumber}" }
        } catch (_: Exception) {
            emptyList()
        } finally {
            discoverer.stopSearch()
        }
    }

    override suspend fun connect(config: BusConnectionConfig) {
        connectionMutex.withLock {
            closeActiveLinkInternal()
            _connectionState.value = ConnectionState.Connecting(describeConfig(config))
            try {
                val link = withContext(Dispatchers.IO) {
                    when (config) {
                        is BusConnectionConfig.IpTunneling -> {
                            val localEp = config.localHost
                                ?.takeIf { it.isNotBlank() }
                                ?.let { InetSocketAddress(InetAddress.getByName(it), 0) }
                            val remoteEp = InetSocketAddress(InetAddress.getByName(config.gatewayHost), config.gatewayPort)
                            val userPwd = config.userPassword
                            val authCode = config.deviceAuthCode
                            if (!userPwd.isNullOrEmpty()) {
                                val userKey = SecureConnection.hashUserPassword(userPwd.toCharArray())
                                val devAuth = if (!authCode.isNullOrEmpty()) {
                                    SecureConnection.hashDeviceAuthenticationPassword(authCode.toCharArray())
                                } else {
                                    ByteArray(16)
                                }
                                KNXNetworkLinkIP.newSecureTunnelingLink(
                                    localEp,
                                    remoteEp,
                                    config.useNat,
                                    devAuth,
                                    1,
                                    userKey,
                                    TPSettings(),
                                )
                            } else {
                                KNXNetworkLinkIP.newTunnelingLink(localEp, remoteEp, config.useNat, TPSettings())
                            }
                        }

                        is BusConnectionConfig.IpRouting -> {
                            val netIf = config.networkInterfaceName
                                ?.takeIf { it.isNotBlank() }
                                ?.let { NetworkInterface.getByName(it) }
                            val mcast = InetAddress.getByName(config.multicastAddress)
                            KNXNetworkLinkIP.newRoutingLink(netIf, mcast, TPSettings())
                        }

                        is BusConnectionConfig.Simulator -> {
                            throw IllegalArgumentException("Use SimulatedKnxBusClient for Simulator configurations.")
                        }
                    }
                }
                link.addLinkListener(linkListener)
                activeLink = link
                _connectionState.value = ConnectionState.Connected(link.name, config)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: e.javaClass.simpleName)
                throw e
            }
        }
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            closeActiveLinkInternal()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun sendGroupRead(address: GroupAddress, timeoutMs: Long): GroupReadResult =
        withContext(Dispatchers.IO) {
            val link = requireConnectedLink()
            val deferred = CompletableDeferred<BusTelegram>()
            pendingGroupReads[address] = deferred
            val startNanos = System.nanoTime()
            try {
                val apdu = DataUnitBuilder.createLengthOptimizedAPDU(APCI_GROUP_READ)
                link.sendRequestWait(CalimeroGroupAddress(address.raw), Priority.LOW, *apdu)
                val reply = withTimeoutOrNull(timeoutMs) { deferred.await() }
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                if (reply != null) {
                    GroupReadResult(
                        address = address,
                        responded = true,
                        responder = reply.source,
                        payload = reply.payload,
                        latencyMs = elapsedMs,
                    )
                } else {
                    GroupReadResult(
                        address = address,
                        responded = false,
                        latencyMs = elapsedMs,
                        errorMessage = "Timeout after ${timeoutMs}ms waiting for GroupValue_Response",
                    )
                }
            } catch (e: Exception) {
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                GroupReadResult(
                    address = address,
                    responded = false,
                    latencyMs = elapsedMs,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            } finally {
                pendingGroupReads.remove(address, deferred)
            }
        }

    override suspend fun sendGroupWrite(address: GroupAddress, payload: ByteArray, compact6Bit: Boolean) =
        withContext(Dispatchers.IO) {
            val link = requireConnectedLink()
            val apdu = if (compact6Bit && payload.size == 1 && (payload[0].toInt() and 0xFF) <= 0x3F) {
                DataUnitBuilder.createLengthOptimizedAPDU(APCI_GROUP_WRITE, *payload)
            } else {
                DataUnitBuilder.createAPDU(APCI_GROUP_WRITE, *payload)
            }
            link.sendRequestWait(CalimeroGroupAddress(address.raw), Priority.LOW, *apdu)
        }

    override suspend fun pingDevice(address: IndividualAddress, timeoutMs: Long): DevicePingResult =
        withContext(Dispatchers.IO) {
            val link = requireConnectedLink()
            val startNanos = System.nanoTime()
            val mgmt = ManagementClientImpl(link)
            try {
                mgmt.responseTimeout(Duration.ofMillis(timeoutMs.coerceAtLeast(300L)))
                val dst = mgmt.createDestination(CalimeroIndividualAddress(address.raw), false)
                val descBytes = mgmt.readDeviceDesc(dst, 0)
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                val dd0 = DeviceDescriptor.DD0.from(descBytes)
                DevicePingResult(
                    address = address,
                    reachable = true,
                    maskVersion = dd0.maskVersion(),
                    latencyMs = elapsedMs,
                )
            } catch (e: KNXTimeoutException) {
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                DevicePingResult(
                    address = address,
                    reachable = false,
                    latencyMs = elapsedMs,
                    errorMessage = "No response (timeout)",
                )
            } catch (e: Exception) {
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                DevicePingResult(
                    address = address,
                    reachable = false,
                    latencyMs = elapsedMs,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            } finally {
                mgmt.detach()
            }
        }

    override suspend fun findProgrammingModeDevices(timeoutMs: Long): List<IndividualAddress> =
        withContext(Dispatchers.IO) {
            val link = requireConnectedLink()
            val procedures = ManagementProceduresImpl(link)
            try {
                val found = procedures.readAddress()
                found.map { IndividualAddress(it.area, it.line, it.device) }.sorted()
            } catch (_: KNXTimeoutException) {
                emptyList()
            } catch (_: KNXException) {
                emptyList()
            } finally {
                procedures.detach()
            }
        }

    override suspend fun readDeviceDiagnostics(
        address: IndividualAddress,
        timeoutMs: Long,
    ): DeviceDiagnosticDetails = withContext(Dispatchers.IO) {
        val link = requireConnectedLink()
        val mgmt = ManagementClientImpl(link)
        val notes = mutableListOf<String>()
        try {
            mgmt.responseTimeout(Duration.ofMillis(timeoutMs.coerceAtLeast(500L)))
            val dst = mgmt.createDestination(CalimeroIndividualAddress(address.raw), true)
            try {
                val descBytes = mgmt.readDeviceDesc(dst, 0)
                val dd0 = DeviceDescriptor.DD0.from(descBytes)
                val maskHex = String.format(java.util.Locale.US, "%04X", dd0.maskVersion() and 0xFFFF)
                notes += "Profile: ${dd0.deviceProfile()}"

                val serialHex = runCatching {
                    val bytes = mgmt.readProperty(dst, DEVICE_OBJECT_INDEX, PropertyAccess.PID.SERIAL_NUMBER, 1, 1)
                    HexBytes.toHex(bytes, "")
                }.getOrNull()

                val mfrId = runCatching {
                    val bytes = mgmt.readProperty(dst, DEVICE_OBJECT_INDEX, PropertyAccess.PID.MANUFACTURER_ID, 1, 1)
                    if (bytes.size >= 2) ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF) else null
                }.getOrNull()

                val progMode = runCatching {
                    val bytes = mgmt.readProperty(dst, DEVICE_OBJECT_INDEX, PropertyAccess.PID.PROGMODE, 1, 1)
                    bytes.isNotEmpty() && (bytes.last().toInt() and 0x01) != 0
                }.getOrDefault(false)

                val loadState = runCatching {
                    val bytes = mgmt.readProperty(dst, APPLICATION_OBJECT_INDEX, PropertyAccess.PID.LOAD_STATE_CONTROL, 1, 1)
                    decodeStateMachine(bytes.lastOrNull()?.toInt()?.and(0x0F))
                }.getOrNull()

                val runState = runCatching {
                    val bytes = mgmt.readProperty(dst, APPLICATION_OBJECT_INDEX, PropertyAccess.PID.RUN_STATE_CONTROL, 1, 1)
                    decodeRunState(bytes.lastOrNull()?.toInt()?.and(0x0F))
                }.getOrNull()

                dst.destroy()
                DeviceDiagnosticDetails(
                    address = address,
                    reachable = true,
                    maskVersionHex = maskHex,
                    serialNumberHex = serialHex,
                    manufacturerId = mfrId,
                    programmingModeActive = progMode,
                    loadState = loadState,
                    runState = runState,
                    notes = notes,
                )
            } catch (e: Exception) {
                dst.destroy()
                DeviceDiagnosticDetails(
                    address = address,
                    reachable = false,
                    notes = listOf("Unreachable: ${e.message ?: e.javaClass.simpleName}"),
                )
            }
        } finally {
            mgmt.detach()
        }
    }

    override suspend fun restartDevice(address: IndividualAddress, timeoutMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            val link = requireConnectedLink()
            val mgmt = ManagementClientImpl(link)
            try {
                mgmt.responseTimeout(Duration.ofMillis(timeoutMs.coerceAtLeast(500L)))
                val dst = mgmt.createDestination(CalimeroIndividualAddress(address.raw), false)
                try {
                    mgmt.restart(dst)
                    true
                } finally {
                    dst.destroy()
                }
            } catch (_: Exception) {
                false
            } finally {
                mgmt.detach()
            }
        }

    override suspend fun locateDevice(address: IndividualAddress, flashCount: Int): Boolean =
        withContext(Dispatchers.IO) {
            val link = requireConnectedLink()
            val mgmt = ManagementClientImpl(link)
            try {
                mgmt.responseTimeout(Duration.ofMillis(600L))
                val dst = mgmt.createDestination(CalimeroIndividualAddress(address.raw), false)
                try {
                    val count = flashCount.coerceIn(1, 10)
                    for (i in 0 until count) {
                        runCatching { mgmt.writeProperty(dst, 0, 54, 1, 1, byteArrayOf(0x01)) }
                        delay(300L)
                        runCatching { mgmt.writeProperty(dst, 0, 54, 1, 1, byteArrayOf(0x00)) }
                        delay(300L)
                    }
                    true
                } finally {
                    dst.destroy()
                }
            } catch (_: Exception) {
                repeat(flashCount.coerceIn(1, 10)) {
                    pingDevice(address, 500L)
                    delay(200L)
                }
                true
            } finally {
                mgmt.detach()
            }
        }

    override suspend fun writeProgrammingAddress(
        newAddress: IndividualAddress,
        timeoutMs: Long,
    ): IndividualAddressWriteResult = withContext(Dispatchers.IO) {
        val activeLeds = findProgrammingModeDevices(timeoutMs)
        if (activeLeds.isEmpty()) {
            return@withContext IndividualAddressWriteResult(
                success = false,
                newAddress = newAddress,
                message = "Refused: No device on the bus currently has its Programming LED active.",
            )
        }
        if (activeLeds.size > 1) {
            return@withContext IndividualAddressWriteResult(
                success = false,
                newAddress = newAddress,
                message = "Refused for safety: ${activeLeds.size} devices have Programming LEDs active (${activeLeds.joinToString()}). Press button so only 1 is active.",
            )
        }
        val link = requireConnectedLink()
        val procedures = ManagementProceduresImpl(link)
        try {
            val ok = procedures.writeAddress(CalimeroIndividualAddress(newAddress.raw))
            if (ok) {
                IndividualAddressWriteResult(
                    success = true,
                    newAddress = newAddress,
                    message = "Successfully programmed ${activeLeds.single()} -> $newAddress",
                )
            } else {
                IndividualAddressWriteResult(
                    success = false,
                    newAddress = newAddress,
                    message = "Device did not confirm new address $newAddress",
                )
            }
        } catch (e: Exception) {
            IndividualAddressWriteResult(
                success = false,
                newAddress = newAddress,
                message = "Write address failed: ${e.message ?: e.javaClass.simpleName}",
            )
        } finally {
            procedures.detach()
        }
    }

    override fun close() {
        closeActiveLinkInternal()
        scope.cancel()
    }

    private fun requireConnectedLink(): KNXNetworkLink =
        activeLink?.takeIf { it.isOpen } ?: throw IllegalStateException("KNX bus link is not connected.")

    private fun closeActiveLinkInternal() {
        val link = activeLink
        activeLink = null
        if (link != null) {
            runCatching { link.removeLinkListener(linkListener) }
            runCatching { link.close() }
        }
    }

    private fun handleFrameEvent(e: FrameEvent) {
        val cemi = e.frame as? CEMILData ?: return
        val apdu = cemi.payload ?: return
        if (apdu.size < 2) return

        val service = DataUnitBuilder.getAPDUService(apdu)
        val apci = when (service) {
            APCI_GROUP_READ -> ApciType.GROUP_READ
            APCI_GROUP_RESPONSE -> ApciType.GROUP_RESPONSE
            APCI_GROUP_WRITE -> ApciType.GROUP_WRITE
            APCI_INDIVIDUAL_ADDR_READ -> ApciType.INDIVIDUAL_ADDR_READ
            APCI_INDIVIDUAL_ADDR_RESPONSE, APCI_INDIVIDUAL_ADDR_WRITE -> ApciType.INDIVIDUAL_ADDR_RESPONSE
            APCI_DEVICE_DESC_READ -> ApciType.DEVICE_DESCRIPTOR_READ
            APCI_DEVICE_DESC_RESPONSE -> ApciType.DEVICE_DESCRIPTOR_RESPONSE
            else -> ApciType.OTHER
        }

        val asdu = if (apci == ApciType.GROUP_READ) ByteArray(0) else DataUnitBuilder.extractASDU(apdu)
        val src = IndividualAddress(cemi.source.area, cemi.source.line, cemi.source.device)
        val dstGroup = (cemi.destination as? CalimeroGroupAddress)?.let { GroupAddress(it.rawAddress) }
        val dstInd = (cemi.destination as? CalimeroIndividualAddress)?.let {
            IndividualAddress(it.area, it.line, it.device)
        }

        val prio = when (cemi.priority) {
            Priority.SYSTEM -> TelegramPriority.SYSTEM
            Priority.NORMAL -> TelegramPriority.NORMAL
            Priority.URGENT -> TelegramPriority.URGENT
            else -> TelegramPriority.LOW
        }

        val telegram = BusTelegram(
            id = telegramCounter.incrementAndGet(),
            timestampEpochMs = System.currentTimeMillis(),
            source = src,
            destinationGroup = dstGroup,
            destinationIndividual = dstInd,
            apci = apci,
            payload = asdu,
            priority = prio,
            hopCount = cemi.hopCount,
            repeated = cemi.isRepetition,
            rawFrameHex = HexBytes.toHex(cemi.toByteArray()),
        )

        if (apci == ApciType.GROUP_RESPONSE && dstGroup != null) {
            pendingGroupReads[dstGroup]?.complete(telegram)
        }

        _incomingTelegrams.tryEmit(telegram)
    }

    private fun decodeStateMachine(code: Int?): String? = when (code) {
        0 -> "Unloaded"
        1 -> "Loaded"
        2 -> "Loading"
        3 -> "Error"
        4 -> "Unloading"
        else -> null
    }

    private fun decodeRunState(code: Int?): String? = when (code) {
        0 -> "Halted"
        1 -> "Running"
        2 -> "Ready"
        3 -> "Terminated"
        else -> null
    }

    private fun describeConfig(config: BusConnectionConfig): String = when (config) {
        is BusConnectionConfig.IpTunneling -> "Tunneling ${config.gatewayHost}:${config.gatewayPort}"
        is BusConnectionConfig.IpRouting -> "Routing ${config.multicastAddress}:${config.port}"
        is BusConnectionConfig.Simulator -> "Simulator (${config.scenarioName})"
    }
}
