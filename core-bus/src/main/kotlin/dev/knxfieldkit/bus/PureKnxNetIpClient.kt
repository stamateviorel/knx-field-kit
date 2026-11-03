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
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure Kotlin + Java/Android standard UDP (`java.net.DatagramSocket` / `MulticastSocket`) implementation
 * of ISO 22510 KNXnet/IP Discovery, Tunneling, Routing, and CEMI `L_Data` Bus Management.
 *
 * Compatible with both Android API 26+ (D8/ART) and Desktop JVM 17+ with zero reflection or desktop-only JRE modules.
 */
class PureKnxNetIpClient(
    parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : KnxBusClient {

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val connectionMutex = Mutex()
    private val sendMutex = Mutex()
    private val telegramSeq = AtomicLong(0L)
    private val txSeqCounter = AtomicInteger(0)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingTelegrams = MutableSharedFlow<BusTelegram>(replay = 0, extraBufferCapacity = 512)
    override val incomingTelegrams: SharedFlow<BusTelegram> = _incomingTelegrams.asSharedFlow()

    private val pendingGroupReads = ConcurrentHashMap<GroupAddress, CompletableDeferred<BusTelegram>>()
    private val pendingTunnelAcks = ConcurrentHashMap<Int, CompletableDeferred<Int>>()
    private val pendingDeviceDescReads = ConcurrentHashMap<IndividualAddress, CompletableDeferred<Int>>()
    private val pendingPropertyReads = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    private val progModeCollector = CopyOnWriteArrayList<IndividualAddress>()
    @Volatile
    private var collectingProgMode: Boolean = false

    @Volatile
    private var udpSocket: DatagramSocket? = null
    @Volatile
    private var activeConfig: BusConnectionConfig? = null
    @Volatile
    private var controlEndpoint: InetSocketAddress? = null
    @Volatile
    private var dataEndpoint: InetSocketAddress? = null
    @Volatile
    private var channelId: Int = -1
    @Volatile
    private var useNat: Boolean = false
    @Volatile
    private var assignedAddress: IndividualAddress = IndividualAddress(0, 0, 0)

    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null

    override suspend fun discoverGateways(timeoutMs: Long): List<GatewayEndpoint> = withContext(Dispatchers.IO) {
        val discovered = LinkedHashMap<String, GatewayEndpoint>()
        val socket = DatagramSocket(0)
        try {
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = 250
            val localIp = resolveSiteLocalIpv4()?.address ?: ByteArray(4)
            val reqBytes = KnxNetIpCodec.buildSearchRequest(localIp, socket.localPort)
            val mcastAddr = InetAddress.getByName(KnxNetIpCodec.MULTICAST_GROUP)
            val packet = DatagramPacket(reqBytes, reqBytes.size, mcastAddr, KnxNetIpCodec.DEFAULT_PORT)
            runCatching { socket.send(packet) }

            // Also send SearchRequest with NAT (0.0.0.0:0) so strict or routed Wi-Fi gateways reply unicast to sender port
            val natReq = KnxNetIpCodec.buildSearchRequest(ByteArray(4), 0)
            runCatching { socket.send(DatagramPacket(natReq, natReq.size, mcastAddr, KnxNetIpCodec.DEFAULT_PORT)) }

            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(400L, 10_000L)
            val rxBuf = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                val rx = DatagramPacket(rxBuf, rxBuf.size)
                try {
                    socket.receive(rx)
                    val ep = KnxNetIpCodec.parseSearchResponse(
                        data = rx.data,
                        length = rx.length,
                        senderIp = rx.address.hostAddress ?: "0.0.0.0",
                    )
                    if (ep != null) {
                        discovered["${ep.host}:${ep.port}:${ep.serialNumber.orEmpty()}"] = ep
                    }
                } catch (_: SocketTimeoutException) {
                    // Continue waiting until deadline
                }
            }
        } catch (_: Exception) {
            // Return any gateways collected before error
        } finally {
            runCatching { socket.close() }
        }
        discovered.values.toList()
    }

    override suspend fun connect(config: BusConnectionConfig) {
        connectionMutex.withLock {
            disconnectInternal()
            _connectionState.value = ConnectionState.Connecting(describeConfig(config))
            try {
                withContext(Dispatchers.IO) {
                    when (config) {
                        is BusConnectionConfig.IpTunneling -> connectTunnelingInternal(config)
                        is BusConnectionConfig.IpRouting -> connectRoutingInternal(config)
                        is BusConnectionConfig.Simulator -> {
                            throw IllegalArgumentException("Use SimulatedKnxBusClient for Simulator mode.")
                        }
                    }
                }
                activeConfig = config
                _connectionState.value = ConnectionState.Connected(describeConfig(config), config)
            } catch (e: Exception) {
                disconnectInternal()
                _connectionState.value = ConnectionState.Error(e.message ?: e.javaClass.simpleName)
                throw e
            }
        }
    }

    private fun connectTunnelingInternal(config: BusConnectionConfig.IpTunneling) {
        val remoteCtrl = InetSocketAddress(InetAddress.getByName(config.gatewayHost.trim()), config.gatewayPort)
        val localBind = config.localHost?.takeIf { it.isNotBlank() }?.let { InetAddress.getByName(it) }
        val sock = if (localBind != null) DatagramSocket(0, localBind) else DatagramSocket(0)
        sock.soTimeout = 2_500

        val advertisedIp = if (config.useNat) {
            ByteArray(4)
        } else {
            (localBind ?: resolveLocalRouteAddress(remoteCtrl) ?: resolveSiteLocalIpv4())?.address ?: ByteArray(4)
        }
        val advertisedPort = if (config.useNat) 0 else sock.localPort
        val effectiveNat = config.useNat || advertisedIp.all { it == 0.toByte() }

        val req = KnxNetIpCodec.buildConnectRequest(effectiveNat, advertisedIp, advertisedPort)
        sock.send(DatagramPacket(req, req.size, remoteCtrl))

        val rxBuf = ByteArray(512)
        val rx = DatagramPacket(rxBuf, rxBuf.size)
        sock.receive(rx)

        val connectRes = KnxNetIpCodec.parseConnectResponse(rx.data, rx.length, remoteCtrl)
            ?: throw IllegalStateException("Invalid CONNECT_RESPONSE from ${config.gatewayHost}:${config.gatewayPort}")
        if (connectRes.status != 0) {
            sock.close()
            throw IllegalStateException("Gateway rejected tunnel connection (status=0x${String.format(Locale.US, "%02X", connectRes.status)})")
        }

        sock.soTimeout = 400
        udpSocket = sock
        controlEndpoint = remoteCtrl
        dataEndpoint = connectRes.dataEndpoint
        channelId = connectRes.channelId
        useNat = effectiveNat
        assignedAddress = connectRes.assignedAddress ?: IndividualAddress(0, 0, 0)
        txSeqCounter.set(0)

        startReceiveLoop(sock, isRouting = false)
        startHeartbeatLoop(sock)
    }

    @Suppress("DEPRECATION")
    private fun connectRoutingInternal(config: BusConnectionConfig.IpRouting) {
        val mcastGroup = InetAddress.getByName(config.multicastAddress.trim())
        val sock = MulticastSocket(config.port)
        sock.reuseAddress = true
        sock.timeToLive = 16
        sock.soTimeout = 400

        val netIf = config.networkInterfaceName?.takeIf { it.isNotBlank() }?.let { NetworkInterface.getByName(it) }
            ?: resolveWifiOrLanInterface()
        if (netIf != null) {
            runCatching { sock.joinGroup(InetSocketAddress(mcastGroup, config.port), netIf) }
                .onFailure { sock.joinGroup(mcastGroup) }
        } else {
            sock.joinGroup(mcastGroup)
        }

        val target = InetSocketAddress(mcastGroup, config.port)
        udpSocket = sock
        controlEndpoint = target
        dataEndpoint = target
        channelId = -1
        assignedAddress = IndividualAddress(0, 0, 0)

        startReceiveLoop(sock, isRouting = true)
    }

    private fun startReceiveLoop(sock: DatagramSocket, isRouting: Boolean) {
        receiveJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(1024)
            while (isActive && !sock.isClosed) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    sock.receive(pkt)
                    handleIncomingPacket(sock, pkt.data.copyOf(pkt.length), pkt.address, pkt.port, isRouting)
                } catch (_: SocketTimeoutException) {
                    // Normal non-blocking poll tick
                } catch (_: Exception) {
                    if (!isActive || sock.isClosed) break
                }
            }
        }
    }

    private fun startHeartbeatLoop(sock: DatagramSocket) {
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && !sock.isClosed && channelId >= 0) {
                delay(50_000L)
                val ctrl = controlEndpoint ?: break
                val localIp = if (useNat) ByteArray(4) else (resolveLocalRouteAddress(ctrl)?.address ?: ByteArray(4))
                val localPort = if (useNat) 0 else sock.localPort
                val req = KnxNetIpCodec.buildConnectionStateRequest(channelId, useNat, localIp, localPort)
                runCatching { sock.send(DatagramPacket(req, req.size, ctrl)) }
            }
        }
    }

    private fun handleIncomingPacket(
        sock: DatagramSocket,
        bytes: ByteArray,
        senderAddr: InetAddress,
        senderPort: Int,
        isRouting: Boolean,
    ) {
        if (bytes.size < 6 || bytes[0] != 0x06.toByte() || bytes[1] != 0x10.toByte()) return
        val serviceType = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        when (serviceType) {
            KnxNetIpCodec.SERVICE_TUNNELING_REQUEST -> {
                if (bytes.size < 11) return
                val ch = bytes[7].toInt() and 0xFF
                val seq = bytes[8].toInt() and 0xFF
                // Immediately send TUNNELING_ACK per ISO 22510
                val ack = KnxNetIpCodec.buildTunnelingAck(ch, seq, 0)
                val replyTarget = dataEndpoint ?: InetSocketAddress(senderAddr, senderPort)
                runCatching { sock.send(DatagramPacket(ack, ack.size, replyTarget)) }

                val connHeaderLen = bytes[6].toInt() and 0xFF
                val cemiStart = 6 + connHeaderLen
                if (cemiStart < bytes.size) {
                    val cemi = bytes.copyOfRange(cemiStart, bytes.size)
                    processIncomingCemi(cemi)
                }
            }

            KnxNetIpCodec.SERVICE_TUNNELING_ACK -> {
                if (bytes.size >= 10) {
                    val seq = bytes[8].toInt() and 0xFF
                    val status = bytes[9].toInt() and 0xFF
                    pendingTunnelAcks.remove(seq)?.complete(status)
                }
            }

            KnxNetIpCodec.SERVICE_ROUTING_INDICATION -> {
                if (isRouting && bytes.size > 6) {
                    val cemi = bytes.copyOfRange(6, bytes.size)
                    processIncomingCemi(cemi)
                }
            }

            KnxNetIpCodec.SERVICE_DISCONNECT_REQUEST -> {
                if (bytes.size >= 8) {
                    val ch = bytes[6].toInt() and 0xFF
                    val resp = KnxNetIpCodec.buildDisconnectResponse(ch, 0)
                    runCatching { sock.send(DatagramPacket(resp, resp.size, senderAddr, senderPort)) }
                    if (ch == channelId) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }
    }

    private fun processIncomingCemi(cemi: ByteArray) {
        val decoded = KnxNetIpCodec.parseCemiDetailed(cemi, telegramSeq.incrementAndGet()) ?: return
        val telegram = decoded.telegram

        if (telegram.apci == ApciType.GROUP_RESPONSE && telegram.destinationGroup != null) {
            pendingGroupReads[telegram.destinationGroup]?.complete(telegram)
        }
        if (telegram.apci == ApciType.DEVICE_DESCRIPTOR_RESPONSE && telegram.payload.size >= 2) {
            val mask = ((telegram.payload[0].toInt() and 0xFF) shl 8) or (telegram.payload[1].toInt() and 0xFF)
            pendingDeviceDescReads[telegram.source]?.complete(mask)
        }
        if (telegram.apci == ApciType.INDIVIDUAL_ADDR_RESPONSE && collectingProgMode) {
            if (!progModeCollector.contains(telegram.source)) {
                progModeCollector.add(telegram.source)
            }
        }
        if (decoded.apciServiceCode == KnxNetIpCodec.APCI_PROPERTY_VALUE_RESPONSE && telegram.payload.size >= 4) {
            val objIdx = telegram.payload[0].toInt() and 0xFF
            val propId = telegram.payload[1].toInt() and 0xFF
            val propData = telegram.payload.copyOfRange(4, telegram.payload.size)
            pendingPropertyReads["${telegram.source}:$objIdx:$propId"]?.complete(propData)
        }

        _incomingTelegrams.tryEmit(telegram)
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            disconnectInternal()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun disconnectInternal() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        receiveJob?.cancel()
        receiveJob = null

        val sock = udpSocket
        val ctrl = controlEndpoint
        val ch = channelId
        udpSocket = null
        controlEndpoint = null
        dataEndpoint = null
        channelId = -1

        if (sock != null && !sock.isClosed) {
            if (ch >= 0 && ctrl != null) {
                runCatching {
                    val localIp = if (useNat) ByteArray(4) else (resolveLocalRouteAddress(ctrl)?.address ?: ByteArray(4))
                    val localPort = if (useNat) 0 else sock.localPort
                    val disc = KnxNetIpCodec.buildDisconnectRequest(ch, useNat, localIp, localPort)
                    sock.send(DatagramPacket(disc, disc.size, ctrl))
                }
            }
            runCatching { sock.close() }
        }
    }

    private suspend fun sendCemiFrame(cemi: ByteArray) = sendMutex.withLock {
        withContext(Dispatchers.IO) {
            val sock = udpSocket?.takeIf { !it.isClosed }
                ?: throw IllegalStateException("KNXnet/IP client is not connected.")
            val dst = dataEndpoint ?: throw IllegalStateException("Missing KNXnet/IP data endpoint.")

            if (channelId >= 0) {
                val seq = txSeqCounter.getAndUpdate { (it + 1) and 0xFF }
                val ackDeferred = CompletableDeferred<Int>()
                pendingTunnelAcks[seq] = ackDeferred
                try {
                    val pktBytes = KnxNetIpCodec.buildTunnelingRequest(channelId, seq, cemi)
                    sock.send(DatagramPacket(pktBytes, pktBytes.size, dst))
                    val ackStatus = withTimeoutOrNull(1_000L) { ackDeferred.await() }
                    if (ackStatus == null) {
                        // ISO 22510 single retransmission on missed TUNNELING_ACK
                        sock.send(DatagramPacket(pktBytes, pktBytes.size, dst))
                        withTimeoutOrNull(1_000L) { ackDeferred.await() }
                    }
                } finally {
                    pendingTunnelAcks.remove(seq, ackDeferred)
                }
            } else {
                val pktBytes = KnxNetIpCodec.buildRoutingIndication(cemi)
                sock.send(DatagramPacket(pktBytes, pktBytes.size, dst))
            }
        }
    }

    override suspend fun sendGroupRead(address: GroupAddress, timeoutMs: Long): GroupReadResult =
        withContext(Dispatchers.IO) {
            val deferred = CompletableDeferred<BusTelegram>()
            pendingGroupReads[address] = deferred
            val startNanos = System.nanoTime()
            try {
                val cemi = KnxNetIpCodec.buildCemiGroupFrame(
                    msgCode = KnxNetIpCodec.CEMI_L_DATA_REQ,
                    src = assignedAddress,
                    dst = address,
                    apci = ApciType.GROUP_READ,
                    payload = ByteArray(0),
                    compact6Bit = true,
                )
                sendCemiFrame(cemi)
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

    override suspend fun sendGroupWrite(address: GroupAddress, payload: ByteArray, compact6Bit: Boolean) {
        val cemi = KnxNetIpCodec.buildCemiGroupFrame(
            msgCode = KnxNetIpCodec.CEMI_L_DATA_REQ,
            src = assignedAddress,
            dst = address,
            apci = ApciType.GROUP_WRITE,
            payload = payload,
            compact6Bit = compact6Bit,
        )
        sendCemiFrame(cemi)
    }

    override suspend fun pingDevice(address: IndividualAddress, timeoutMs: Long): DevicePingResult =
        withContext(Dispatchers.IO) {
            val deferred = CompletableDeferred<Int>()
            pendingDeviceDescReads[address] = deferred
            val startNanos = System.nanoTime()
            try {
                // Send T_Connect -> Numbered T_Data_Connected(seq=0) DeviceDescriptor_Read(0)
                sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x80))
                delay(20L)
                sendCemiFrame(
                    KnxNetIpCodec.buildCemiIndividualApdu(
                        src = assignedAddress,
                        dst = address,
                        numbered = true,
                        seq = 0,
                        apciService = KnxNetIpCodec.APCI_DEVICE_DESC_READ,
                        compactData6Bit = 0,
                    )
                )
                val mask = withTimeoutOrNull(timeoutMs) { deferred.await() }
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                if (mask != null) {
                    // Send T_ACK(seq=0) and T_Disconnect cleanly
                    runCatching {
                        sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0xC2))
                        sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x81))
                    }
                    DevicePingResult(
                        address = address,
                        reachable = true,
                        maskVersion = mask,
                        latencyMs = elapsedMs,
                    )
                } else {
                    runCatching {
                        sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x81))
                    }
                    DevicePingResult(
                        address = address,
                        reachable = false,
                        latencyMs = elapsedMs,
                        errorMessage = "No response (timeout)",
                    )
                }
            } catch (e: Exception) {
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                DevicePingResult(
                    address = address,
                    reachable = false,
                    latencyMs = elapsedMs,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            } finally {
                pendingDeviceDescReads.remove(address, deferred)
            }
        }

    override suspend fun findProgrammingModeDevices(timeoutMs: Long): List<IndividualAddress> =
        withContext(Dispatchers.IO) {
            progModeCollector.clear()
            collectingProgMode = true
            try {
                val cemi = KnxNetIpCodec.buildCemiBroadcastApdu(
                    src = assignedAddress,
                    apciService = KnxNetIpCodec.APCI_INDIVIDUAL_ADDR_READ,
                    asdu = ByteArray(0),
                )
                sendCemiFrame(cemi)
                delay(timeoutMs.coerceIn(300L, 4_000L))
                progModeCollector.distinct().sorted()
            } catch (_: Exception) {
                emptyList()
            } finally {
                collectingProgMode = false
            }
        }

    override suspend fun readDeviceDiagnostics(
        address: IndividualAddress,
        timeoutMs: Long,
    ): DeviceDiagnosticDetails = withContext(Dispatchers.IO) {
        val ping = pingDevice(address, timeoutMs)
        if (!ping.reachable) {
            return@withContext DeviceDiagnosticDetails(
                address = address,
                reachable = false,
                notes = listOf("Unreachable: ${ping.errorMessage ?: "timeout"}"),
            )
        }

        // Open connected management session to read standard Interface Object properties
        var seq = 0
        suspend fun readProp(objIdx: Int, propId: Int): ByteArray? {
            val key = "$address:$objIdx:$propId"
            val def = CompletableDeferred<ByteArray>()
            pendingPropertyReads[key] = def
            return try {
                val asdu = byteArrayOf(
                    objIdx.toByte(),
                    propId.toByte(),
                    0x10, // count=1 (upper nibble), startIndex high=0
                    0x01, // startIndex=1
                )
                val frame = KnxNetIpCodec.buildCemiIndividualApdu(
                    src = assignedAddress,
                    dst = address,
                    numbered = true,
                    seq = seq,
                    apciService = KnxNetIpCodec.APCI_PROPERTY_VALUE_READ,
                    asdu = asdu,
                )
                sendCemiFrame(frame)
                val res = withTimeoutOrNull(800L) { def.await() }
                if (res != null) {
                    runCatching {
                        sendCemiFrame(
                            KnxNetIpCodec.buildCemiTransportControl(
                                assignedAddress,
                                address,
                                tpci = 0xC2 or ((seq and 0x0F) shl 2),
                            )
                        )
                    }
                    seq = (seq + 1) and 0x0F
                }
                res
            } finally {
                pendingPropertyReads.remove(key, def)
            }
        }

        try {
            sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x80))
            delay(20L)
            val serialHex = readProp(0, 11)?.let { HexBytes.toHex(it, "") }
            val mfrId = readProp(0, 12)?.let {
                if (it.size >= 2) ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) else null
            }
            val progMode = readProp(0, 54)?.let { it.isNotEmpty() && (it.last().toInt() and 0x01) != 0 } ?: false
            val loadState = readProp(3, 5)?.lastOrNull()?.toInt()?.and(0x0F)?.let(::decodeStateMachine)
            val runState = readProp(3, 6)?.lastOrNull()?.toInt()?.and(0x0F)?.let(::decodeRunState)

            DeviceDiagnosticDetails(
                address = address,
                reachable = true,
                maskVersionHex = ping.maskVersionHex,
                serialNumberHex = serialHex,
                manufacturerId = mfrId,
                programmingModeActive = progMode,
                loadState = loadState,
                runState = runState,
                notes = listOf("Mask DD0=${ping.maskVersionHex} (${ping.latencyMs} ms)"),
            )
        } finally {
            runCatching {
                sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x81))
            }
        }
    }

    override suspend fun restartDevice(address: IndividualAddress, timeoutMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x80))
                delay(20L)
                sendCemiFrame(
                    KnxNetIpCodec.buildCemiIndividualApdu(
                        src = assignedAddress,
                        dst = address,
                        numbered = true,
                        seq = 0,
                        apciService = KnxNetIpCodec.APCI_RESTART,
                    )
                )
                delay(40L)
                runCatching {
                    sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x81))
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun locateDevice(address: IndividualAddress, flashCount: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x80))
                delay(20L)
                val count = flashCount.coerceIn(1, 10)
                for (i in 0 until count) {
                    val asduOn = byteArrayOf(0x00, 54.toByte(), 0x10, 0x01, 0x01)
                    sendCemiFrame(
                        KnxNetIpCodec.buildCemiIndividualApdu(
                            src = assignedAddress,
                            dst = address,
                            numbered = true,
                            seq = (i * 2) and 0x0F,
                            apciService = KnxNetIpCodec.APCI_PROPERTY_VALUE_WRITE,
                            asdu = asduOn,
                        )
                    )
                    delay(300L)
                    val asduOff = byteArrayOf(0x00, 54.toByte(), 0x10, 0x01, 0x00)
                    sendCemiFrame(
                        KnxNetIpCodec.buildCemiIndividualApdu(
                            src = assignedAddress,
                            dst = address,
                            numbered = true,
                            seq = (i * 2 + 1) and 0x0F,
                            apciService = KnxNetIpCodec.APCI_PROPERTY_VALUE_WRITE,
                            asdu = asduOff,
                        )
                    )
                    delay(300L)
                }
                runCatching {
                    sendCemiFrame(KnxNetIpCodec.buildCemiTransportControl(assignedAddress, address, tpci = 0x81))
                }
                true
            } catch (_: Exception) {
                repeat(flashCount.coerceIn(1, 10)) {
                    pingDevice(address, 500L)
                    delay(200L)
                }
                true
            }
        }

    override suspend fun writeProgrammingAddress(
        newAddress: IndividualAddress,
        timeoutMs: Long,
    ): IndividualAddressWriteResult = withContext(Dispatchers.IO) {
        val activeLeds = findProgrammingModeDevices(minOf(timeoutMs, 1_500L))
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
                message = "Refused for safety: ${activeLeds.size} devices have Programming LEDs active (${activeLeds.joinToString()}). Leave only 1 LED active.",
            )
        }
        try {
            val addrBytes = byteArrayOf(
                ((newAddress.raw ushr 8) and 0xFF).toByte(),
                (newAddress.raw and 0xFF).toByte(),
            )
            val writeFrame = KnxNetIpCodec.buildCemiBroadcastApdu(
                src = assignedAddress,
                apciService = KnxNetIpCodec.APCI_INDIVIDUAL_ADDR_WRITE,
                asdu = addrBytes,
            )
            sendCemiFrame(writeFrame)
            delay(250L)
            val verify = pingDevice(newAddress, 1_200L)
            if (verify.reachable) {
                IndividualAddressWriteResult(
                    success = true,
                    newAddress = newAddress,
                    message = "Successfully programmed ${activeLeds.single()} -> $newAddress",
                )
            } else {
                IndividualAddressWriteResult(
                    success = false,
                    newAddress = newAddress,
                    message = "Device did not verify at new address $newAddress",
                )
            }
        } catch (e: Exception) {
            IndividualAddressWriteResult(
                success = false,
                newAddress = newAddress,
                message = "Write address failed: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    override fun close() {
        disconnectInternal()
        scope.cancel()
    }

    private fun decodeStateMachine(code: Int): String? = when (code) {
        0 -> "Unloaded"
        1 -> "Loaded"
        2 -> "Loading"
        3 -> "Error"
        4 -> "Unloading"
        else -> null
    }

    private fun decodeRunState(code: Int): String? = when (code) {
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

    private fun resolveLocalRouteAddress(remote: InetSocketAddress): Inet4Address? = runCatching {
        DatagramSocket().use { probe ->
            probe.connect(remote)
            probe.localAddress as? Inet4Address
        }
    }.getOrNull()

    private fun resolveSiteLocalIpv4(): Inet4Address? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        interfaces
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
    }.getOrNull()

    private fun resolveWifiOrLanInterface(): NetworkInterface? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        interfaces.firstOrNull { nif ->
            nif.isUp && !nif.isLoopback && nif.supportsMulticast() &&
                nif.inetAddresses.toList().any { it is Inet4Address && it.isSiteLocalAddress }
        }
    }.getOrNull()
}

/**
 * Pure, stateless ISO 22510 KNXnet/IP & CEMI `L_Data` packet encoder/decoder.
 */
object KnxNetIpCodec {
    const val MULTICAST_GROUP = "224.0.23.12"
    const val DEFAULT_PORT = 3671

    const val SERVICE_SEARCH_REQUEST = 0x0201
    const val SERVICE_SEARCH_RESPONSE = 0x0202
    const val SERVICE_CONNECT_REQUEST = 0x0205
    const val SERVICE_CONNECT_RESPONSE = 0x0206
    const val SERVICE_CONNECTIONSTATE_REQUEST = 0x0207
    const val SERVICE_CONNECTIONSTATE_RESPONSE = 0x0208
    const val SERVICE_DISCONNECT_REQUEST = 0x0209
    const val SERVICE_DISCONNECT_RESPONSE = 0x020A
    const val SERVICE_TUNNELING_REQUEST = 0x0420
    const val SERVICE_TUNNELING_ACK = 0x0421
    const val SERVICE_ROUTING_INDICATION = 0x0530

    const val CEMI_L_DATA_REQ = 0x11
    const val CEMI_L_DATA_IND = 0x29
    const val CEMI_L_DATA_CON = 0x2E

    const val APCI_GROUP_READ = 0x0000
    const val APCI_GROUP_RESPONSE = 0x0040
    const val APCI_GROUP_WRITE = 0x0080
    const val APCI_INDIVIDUAL_ADDR_WRITE = 0x00C0
    const val APCI_INDIVIDUAL_ADDR_READ = 0x0100
    const val APCI_INDIVIDUAL_ADDR_RESPONSE = 0x0140
    const val APCI_DEVICE_DESC_READ = 0x0300
    const val APCI_DEVICE_DESC_RESPONSE = 0x0340
    const val APCI_RESTART = 0x0380
    const val APCI_PROPERTY_VALUE_READ = 0x03D5
    const val APCI_PROPERTY_VALUE_RESPONSE = 0x03D6
    const val APCI_PROPERTY_VALUE_WRITE = 0x03D7

    data class ConnectResponseData(
        val channelId: Int,
        val status: Int,
        val dataEndpoint: InetSocketAddress,
        val assignedAddress: IndividualAddress?,
    )

    data class DecodedCemi(
        val telegram: BusTelegram,
        val apciServiceCode: Int,
    )

    fun buildHpai(ip4: ByteArray, port: Int): ByteArray {
        val addr = if (ip4.size == 4) ip4 else ByteArray(4)
        return byteArrayOf(
            0x08,
            0x01, // IPV4_UDP
            addr[0],
            addr[1],
            addr[2],
            addr[3],
            ((port ushr 8) and 0xFF).toByte(),
            (port and 0xFF).toByte(),
        )
    }

    fun buildSearchRequest(localIp: ByteArray = ByteArray(4), localPort: Int = 0): ByteArray {
        val hpai = buildHpai(localIp, localPort)
        return wrapHeader(SERVICE_SEARCH_REQUEST, hpai)
    }

    fun parseSearchResponse(data: ByteArray, length: Int, senderIp: String): GatewayEndpoint? {
        if (length < 14 || data.size < length) return null
        if (data[0] != 0x06.toByte() || data[1] != 0x10.toByte()) return null
        val service = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if (service != SERVICE_SEARCH_RESPONSE) return null

        val hpaiLen = data[6].toInt() and 0xFF
        if (hpaiLen < 8 || 6 + hpaiLen > length) return null
        val ipBytes = data.copyOfRange(8, 12)
        val advertisedHost = ipBytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
        val advertisedPort = ((data[12].toInt() and 0xFF) shl 8) or (data[13].toInt() and 0xFF)
        val host = if (advertisedHost == "0.0.0.0") senderIp else advertisedHost
        val port = if (advertisedPort == 0) DEFAULT_PORT else advertisedPort

        var pos = 6 + hpaiLen
        var friendlyName = "KNXnet/IP Gateway"
        var ia: IndividualAddress? = null
        var serialHex: String? = null
        var macText: String? = null
        var supportsTunneling = false
        var supportsRouting = false
        var supportsSecurity = false

        while (pos + 2 <= length) {
            val dibLen = data[pos].toInt() and 0xFF
            if (dibLen < 2 || pos + dibLen > length) break
            val dibType = data[pos + 1].toInt() and 0xFF
            when (dibType) {
                0x01 -> { // DIB_DEVICE_INFO (typically 54 bytes)
                    if (dibLen >= 54) {
                        val rawIa = ((data[pos + 4].toInt() and 0xFF) shl 8) or (data[pos + 5].toInt() and 0xFF)
                        ia = IndividualAddress.fromRaw(rawIa)
                        serialHex = HexBytes.toHex(data.copyOfRange(pos + 8, pos + 14), "")
                        macText = data.copyOfRange(pos + 18, pos + 24)
                            .joinToString(":") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
                        val nameBytes = data.copyOfRange(pos + 24, pos + 54)
                        val zeroIdx = nameBytes.indexOf(0.toByte()).let { if (it >= 0) it else nameBytes.size }
                        val parsedName = String(nameBytes, 0, zeroIdx, StandardCharsets.ISO_8859_1).trim()
                        if (parsedName.isNotEmpty()) friendlyName = parsedName
                    }
                }
                0x02 -> { // DIB_SUPP_SVC_FAMILIES
                    var i = pos + 2
                    while (i + 1 < pos + dibLen) {
                        val familyId = data[i].toInt() and 0xFF
                        when (familyId) {
                            0x04 -> supportsTunneling = true
                            0x05 -> supportsRouting = true
                            0x09 -> supportsSecurity = true
                        }
                        i += 2
                    }
                }
            }
            pos += dibLen
        }

        return GatewayEndpoint(
            name = friendlyName,
            host = host,
            port = port,
            individualAddress = ia,
            macAddress = macText,
            serialNumber = serialHex,
            supportsTunneling = supportsTunneling || (!supportsTunneling && !supportsRouting),
            supportsRouting = supportsRouting,
            supportsSecurity = supportsSecurity,
        )
    }

    fun buildConnectRequest(useNat: Boolean, localIp: ByteArray, localPort: Int): ByteArray {
        val hpai = if (useNat) buildHpai(ByteArray(4), 0) else buildHpai(localIp, localPort)
        val cri = byteArrayOf(0x04, 0x04, 0x02, 0x00) // TUNNEL_CONNECTION, TUNNEL_LINKLAYER
        val body = ByteArrayOutputStream(20).apply {
            write(hpai)
            write(hpai)
            write(cri)
        }.toByteArray()
        return wrapHeader(SERVICE_CONNECT_REQUEST, body)
    }

    fun parseConnectResponse(
        data: ByteArray,
        length: Int,
        fallbackControlEndpoint: InetSocketAddress,
    ): ConnectResponseData? {
        if (length < 8 || data.size < length) return null
        if (data[0] != 0x06.toByte() || data[1] != 0x10.toByte()) return null
        val service = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if (service != SERVICE_CONNECT_RESPONSE) return null

        val channelId = data[6].toInt() and 0xFF
        val status = data[7].toInt() and 0xFF
        if (status != 0 || length < 16) {
            return ConnectResponseData(channelId, status, fallbackControlEndpoint, null)
        }

        val hpaiLen = data[8].toInt() and 0xFF
        val dataEp = if (hpaiLen >= 8 && 8 + hpaiLen <= length) {
            val ip = data.copyOfRange(10, 14).joinToString(".") { (it.toInt() and 0xFF).toString() }
            val port = ((data[14].toInt() and 0xFF) shl 8) or (data[15].toInt() and 0xFF)
            if (ip == "0.0.0.0" || port == 0) {
                fallbackControlEndpoint
            } else {
                InetSocketAddress(InetAddress.getByName(ip), port)
            }
        } else {
            fallbackControlEndpoint
        }

        val crdPos = 8 + hpaiLen
        val assigned = if (crdPos + 4 <= length && (data[crdPos].toInt() and 0xFF) >= 4) {
            val rawIa = ((data[crdPos + 2].toInt() and 0xFF) shl 8) or (data[crdPos + 3].toInt() and 0xFF)
            IndividualAddress.fromRaw(rawIa)
        } else {
            null
        }
        return ConnectResponseData(channelId, status, dataEp, assigned)
    }

    fun buildConnectionStateRequest(channelId: Int, useNat: Boolean, localIp: ByteArray, localPort: Int): ByteArray {
        val hpai = if (useNat) buildHpai(ByteArray(4), 0) else buildHpai(localIp, localPort)
        val body = byteArrayOf(channelId.toByte(), 0x00) + hpai
        return wrapHeader(SERVICE_CONNECTIONSTATE_REQUEST, body)
    }

    fun buildDisconnectRequest(channelId: Int, useNat: Boolean, localIp: ByteArray, localPort: Int): ByteArray {
        val hpai = if (useNat) buildHpai(ByteArray(4), 0) else buildHpai(localIp, localPort)
        val body = byteArrayOf(channelId.toByte(), 0x00) + hpai
        return wrapHeader(SERVICE_DISCONNECT_REQUEST, body)
    }

    fun buildDisconnectResponse(channelId: Int, status: Int = 0): ByteArray =
        wrapHeader(SERVICE_DISCONNECT_RESPONSE, byteArrayOf(channelId.toByte(), status.toByte()))

    fun buildTunnelingAck(channelId: Int, seqCounter: Int, status: Int = 0): ByteArray =
        wrapHeader(
            SERVICE_TUNNELING_ACK,
            byteArrayOf(0x04, channelId.toByte(), seqCounter.toByte(), status.toByte()),
        )

    fun buildTunnelingRequest(channelId: Int, seqCounter: Int, cemi: ByteArray): ByteArray {
        val connHeader = byteArrayOf(0x04, channelId.toByte(), seqCounter.toByte(), 0x00)
        return wrapHeader(SERVICE_TUNNELING_REQUEST, connHeader + cemi)
    }

    fun buildRoutingIndication(cemi: ByteArray): ByteArray =
        wrapHeader(SERVICE_ROUTING_INDICATION, cemi)

    fun buildCemiGroupFrame(
        msgCode: Int = CEMI_L_DATA_REQ,
        src: IndividualAddress,
        dst: GroupAddress,
        apci: ApciType,
        payload: ByteArray,
        compact6Bit: Boolean,
    ): ByteArray {
        val service = when (apci) {
            ApciType.GROUP_READ -> APCI_GROUP_READ
            ApciType.GROUP_RESPONSE -> APCI_GROUP_RESPONSE
            else -> APCI_GROUP_WRITE
        }
        val apdu = when {
            apci == ApciType.GROUP_READ -> byteArrayOf(
                ((service ushr 8) and 0x03).toByte(),
                (service and 0xC0).toByte(),
            )
            compact6Bit && payload.size == 1 && (payload[0].toInt() and 0xFF) <= 0x3F -> byteArrayOf(
                ((service ushr 8) and 0x03).toByte(),
                ((service and 0xC0) or (payload[0].toInt() and 0x3F)).toByte(),
            )
            else -> byteArrayOf(
                ((service ushr 8) and 0x03).toByte(),
                (service and 0xC0).toByte(),
            ) + payload
        }
        val npduLen = (apdu.size - 1).coerceAtLeast(1)
        return byteArrayOf(
            msgCode.toByte(),
            0x00, // Additional Info Length = 0
            0xBC.toByte(), // Standard frame, no repeat, broadcast, LOW priority
            0xE0.toByte(), // Group address (bit 7=1), hop count = 6
            ((src.raw ushr 8) and 0xFF).toByte(),
            (src.raw and 0xFF).toByte(),
            ((dst.raw ushr 8) and 0xFF).toByte(),
            (dst.raw and 0xFF).toByte(),
            npduLen.toByte(),
        ) + apdu
    }

    fun buildCemiTransportControl(
        src: IndividualAddress,
        dst: IndividualAddress,
        tpci: Int,
    ): ByteArray = byteArrayOf(
        CEMI_L_DATA_REQ.toByte(),
        0x00,
        0xB0.toByte(), // Standard frame, SYSTEM priority
        0x60.toByte(), // Individual address (bit 7=0), hop count = 6
        ((src.raw ushr 8) and 0xFF).toByte(),
        (src.raw and 0xFF).toByte(),
        ((dst.raw ushr 8) and 0xFF).toByte(),
        (dst.raw and 0xFF).toByte(),
        0x00, // Length = 0 for transport control TPDU
        tpci.toByte(),
    )

    fun buildCemiIndividualApdu(
        src: IndividualAddress,
        dst: IndividualAddress,
        numbered: Boolean,
        seq: Int,
        apciService: Int,
        compactData6Bit: Int = 0,
        asdu: ByteArray = ByteArray(0),
    ): ByteArray {
        val tpciBits = if (numbered) (0x40 or ((seq and 0x0F) shl 2)) else 0x00
        val apdu = byteArrayOf(
            (tpciBits or ((apciService ushr 8) and 0x03)).toByte(),
            ((apciService and 0xFF) or (compactData6Bit and 0x3F)).toByte(),
        ) + asdu
        return byteArrayOf(
            CEMI_L_DATA_REQ.toByte(),
            0x00,
            0xB0.toByte(),
            0x60.toByte(),
            ((src.raw ushr 8) and 0xFF).toByte(),
            (src.raw and 0xFF).toByte(),
            ((dst.raw ushr 8) and 0xFF).toByte(),
            (dst.raw and 0xFF).toByte(),
            (apdu.size - 1).toByte(),
        ) + apdu
    }

    fun buildCemiBroadcastApdu(
        src: IndividualAddress,
        apciService: Int,
        asdu: ByteArray,
    ): ByteArray {
        val apdu = byteArrayOf(
            ((apciService ushr 8) and 0x03).toByte(),
            (apciService and 0xFF).toByte(),
        ) + asdu
        return byteArrayOf(
            CEMI_L_DATA_REQ.toByte(),
            0x00,
            0xB0.toByte(),
            0xE0.toByte(), // Broadcast uses group/address-type bit=1 with destination 0x0000
            ((src.raw ushr 8) and 0xFF).toByte(),
            (src.raw and 0xFF).toByte(),
            0x00,
            0x00,
            (apdu.size - 1).toByte(),
        ) + apdu
    }

    fun parseCemiFrame(cemi: ByteArray, id: Long): BusTelegram? =
        parseCemiDetailed(cemi, id)?.telegram

    fun parseCemiDetailed(cemi: ByteArray, id: Long): DecodedCemi? {
        if (cemi.size < 9) return null
        val msgCode = cemi[0].toInt() and 0xFF
        if (msgCode != CEMI_L_DATA_IND && msgCode != CEMI_L_DATA_CON && msgCode != CEMI_L_DATA_REQ) return null

        val addInfoLen = cemi[1].toInt() and 0xFF
        val base = 2 + addInfoLen
        if (base + 7 > cemi.size) return null

        val ctrl1 = cemi[base].toInt() and 0xFF
        val ctrl2 = cemi[base + 1].toInt() and 0xFF
        val rawSrc = ((cemi[base + 2].toInt() and 0xFF) shl 8) or (cemi[base + 3].toInt() and 0xFF)
        val rawDst = ((cemi[base + 4].toInt() and 0xFF) shl 8) or (cemi[base + 5].toInt() and 0xFF)
        val npduLen = cemi[base + 6].toInt() and 0xFF

        val tpciByte = cemi[base + 7].toInt() and 0xFF
        if ((tpciByte and 0x80) != 0) {
            // Transport layer control frame (T_Connect, T_Disconnect, T_ACK, T_NAK) — no APDU
            return null
        }
        if (base + 8 >= cemi.size || npduLen < 1) return null

        val apduEnd = (base + 7 + 1 + npduLen).coerceAtMost(cemi.size)
        val apciLow = cemi[base + 8].toInt() and 0xFF
        val raw10BitApci = ((tpciByte and 0x03) shl 8) or apciLow
        val masked6BitService = raw10BitApci and 0x03C0

        val isGroupBit = (ctrl2 and 0x80) != 0
        val isBroadcastZero = isGroupBit && rawDst == 0

        val (apciType, serviceCode, payload) = when {
            isGroupBit && !isBroadcastZero && masked6BitService == APCI_GROUP_READ -> {
                Triple(ApciType.GROUP_READ, APCI_GROUP_READ, ByteArray(0))
            }
            isGroupBit && !isBroadcastZero && (masked6BitService == APCI_GROUP_RESPONSE || masked6BitService == APCI_GROUP_WRITE) -> {
                val type = if (masked6BitService == APCI_GROUP_RESPONSE) ApciType.GROUP_RESPONSE else ApciType.GROUP_WRITE
                val data = if (npduLen == 1) {
                    byteArrayOf((apciLow and 0x3F).toByte())
                } else {
                    cemi.copyOfRange(base + 9, apduEnd)
                }
                Triple(type, masked6BitService, data)
            }
            raw10BitApci == APCI_INDIVIDUAL_ADDR_READ -> {
                Triple(ApciType.INDIVIDUAL_ADDR_READ, APCI_INDIVIDUAL_ADDR_READ, ByteArray(0))
            }
            raw10BitApci == APCI_INDIVIDUAL_ADDR_RESPONSE || raw10BitApci == APCI_INDIVIDUAL_ADDR_WRITE -> {
                Triple(ApciType.INDIVIDUAL_ADDR_RESPONSE, raw10BitApci, cemi.copyOfRange(base + 9, apduEnd))
            }
            masked6BitService == APCI_DEVICE_DESC_READ -> {
                Triple(ApciType.DEVICE_DESCRIPTOR_READ, APCI_DEVICE_DESC_READ, byteArrayOf((apciLow and 0x3F).toByte()))
            }
            masked6BitService == APCI_DEVICE_DESC_RESPONSE -> {
                Triple(ApciType.DEVICE_DESCRIPTOR_RESPONSE, APCI_DEVICE_DESC_RESPONSE, cemi.copyOfRange(base + 9, apduEnd))
            }
            else -> {
                Triple(ApciType.OTHER, raw10BitApci, cemi.copyOfRange(base + 9, apduEnd))
            }
        }

        val priority = when ((ctrl1 ushr 2) and 0x03) {
            0 -> TelegramPriority.SYSTEM
            1 -> TelegramPriority.NORMAL
            2 -> TelegramPriority.URGENT
            else -> TelegramPriority.LOW
        }
        val repeated = (ctrl1 and 0x20) == 0
        val hopCount = (ctrl2 ushr 4) and 0x07

        val dstGroup = if (isGroupBit && !isBroadcastZero) GroupAddress.fromRaw(rawDst) else null
        val dstInd = if (!isGroupBit) IndividualAddress.fromRaw(rawDst) else null

        return DecodedCemi(
            telegram = BusTelegram(
                id = id,
                timestampEpochMs = System.currentTimeMillis(),
                source = IndividualAddress.fromRaw(rawSrc),
                destinationGroup = dstGroup,
                destinationIndividual = dstInd,
                apci = apciType,
                payload = payload,
                priority = priority,
                hopCount = hopCount,
                repeated = repeated,
                rawFrameHex = HexBytes.toHex(cemi),
            ),
            apciServiceCode = serviceCode,
        )
    }

    private fun wrapHeader(serviceType: Int, body: ByteArray): ByteArray {
        val totalLen = 6 + body.size
        return byteArrayOf(
            0x06,
            0x10,
            ((serviceType ushr 8) and 0xFF).toByte(),
            (serviceType and 0xFF).toByte(),
            ((totalLen ushr 8) and 0xFF).toByte(),
            (totalLen and 0xFF).toByte(),
        ) + body
    }
}
