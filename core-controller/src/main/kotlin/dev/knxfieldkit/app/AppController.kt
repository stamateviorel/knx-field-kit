package dev.knxfieldkit.app

import dev.knxfieldkit.bus.DemoKnxProjectFactory
import dev.knxfieldkit.bus.KnxBusClient
import dev.knxfieldkit.bus.PureKnxNetIpClient
import dev.knxfieldkit.bus.SimulatedKnxBusClient
import dev.knxfieldkit.knxproj.EtsCrypto
import dev.knxfieldkit.knxproj.InvalidPasswordException
import dev.knxfieldkit.knxproj.KnxKeyringReader
import dev.knxfieldkit.knxproj.KnxProjArchiveReader
import dev.knxfieldkit.knxproj.KnxProjParser
import dev.knxfieldkit.knxproj.PasswordRequiredException
import dev.knxfieldkit.model.ApciType
import dev.knxfieldkit.model.BusConnectionConfig
import dev.knxfieldkit.model.BusHealthAnalyzer
import dev.knxfieldkit.model.BusTelegram
import dev.knxfieldkit.model.ConnectionState
import dev.knxfieldkit.model.DeviceDiagnosticDetails
import dev.knxfieldkit.model.DevicePingResult
import dev.knxfieldkit.model.DptCodec
import dev.knxfieldkit.model.DuplicateAddressIssue
import dev.knxfieldkit.model.DuplicateIssueType
import dev.knxfieldkit.model.DuplicateScanReport
import dev.knxfieldkit.model.EnrichedTelegram
import dev.knxfieldkit.model.GatewayEndpoint
import dev.knxfieldkit.model.GroupAddress
import dev.knxfieldkit.model.HexBytes
import dev.knxfieldkit.model.HvacMode
import dev.knxfieldkit.model.IndividualAddress
import dev.knxfieldkit.model.KnxKeyring
import dev.knxfieldkit.model.KnxProject
import dev.knxfieldkit.model.LineAuditEntry
import dev.knxfieldkit.model.LineAuditReport
import dev.knxfieldkit.model.MainDpt
import dev.knxfieldkit.model.SiteCheckReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AppTab(val title: String) {
    PROJECT("Explorer"),
    MONITOR("Bus Monitor"),
    SENDER("Telegrams"),
    DIAGNOSTICS("Diagnostics"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Auto"),
    DARK("Dark"),
    LIGHT("Light"),
}

enum class ExplorerTreeMode(val label: String) {
    DUAL_SPLIT("Split"),
    TOPOLOGY("Topology"),
    GROUP_ADDRESSES("Groups"),
    BUILDING("Rooms"),
}

sealed interface ExplorerSelection {
    data object TopologyRoot : ExplorerSelection
    data class Area(val area: Int) : ExplorerSelection
    data class Line(val area: Int, val line: Int) : ExplorerSelection
    data class Device(val address: IndividualAddress) : ExplorerSelection
    data object AllGroupAddresses : ExplorerSelection
    data class GroupRange(val rangeId: String) : ExplorerSelection
    data class BuildingSpace(
        val path: String,
        val name: String,
        val type: String,
        val deviceAddresses: List<IndividualAddress>,
    ) : ExplorerSelection
}

data class PasswordPromptState(
    val file: File,
    val isKeyring: Boolean,
    val errorMessage: String? = null,
)

data class GroupValueEntry(
    val valueText: String,
    val latencyMs: Long? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class AppUiState(
    val activeTab: AppTab = AppTab.PROJECT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val project: KnxProject? = null,
    val projectSourceLabel: String = "No Project Loaded",
    val recentFiles: List<File> = emptyList(),
    val savedGatewayHost: String = "192.168.1.10",
    val savedGatewayPort: Int = 3671,
    val savedGatewayUseNat: Boolean = false,
    val keyring: KnxKeyring? = null,
    val passwordPrompt: PasswordPromptState? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isSimulatorActive: Boolean = false,
    val discoveredGateways: List<GatewayEndpoint> = emptyList(),
    val isDiscoveringGateways: Boolean = false,
    val showConnectDialog: Boolean = false,
    val telegrams: List<EnrichedTelegram> = emptyList(),
    val monitorPaused: Boolean = false,
    val monitorSearchQuery: String = "",
    val monitorGhostOnly: Boolean = false,
    val selectedMonitorTelegramId: Long? = null,
    val groupLiveValues: Map<GroupAddress, GroupValueEntry> = emptyMap(),
    val deviceStatuses: Map<IndividualAddress, DevicePingResult> = emptyMap(),
    val explorerLocationView: Boolean = false,
    val explorerTreeMode: ExplorerTreeMode = ExplorerTreeMode.DUAL_SPLIT,
    val explorerSelection: ExplorerSelection = ExplorerSelection.TopologyRoot,
    val showExplorerBottomMonitor: Boolean = true,
    val showExplorerRightInspector: Boolean = true,
    val selectedDeviceAddress: IndividualAddress? = null,
    val selectedInspectorGroup: GroupAddress? = null,
    val selectedGroupAddressInput: String = "",
    val isPollingBatch: Boolean = false,
    val walkTestActive: Boolean = false,
    val walkTestGroupAddress: GroupAddress? = null,
    val walkTestStepCount: Int = 0,
    val walkTestVerifiedChannels: Set<GroupAddress> = emptySet(),
    val walkTestFlaggedChannels: Map<GroupAddress, String> = emptyMap(),
    val pingResults: List<DevicePingResult> = emptyList(),
    val isPinging: Boolean = false,
    val lineAuditReport: LineAuditReport? = null,
    val lineAuditInProgressEntries: List<LineAuditEntry> = emptyList(),
    val isAuditingLine: Boolean = false,
    val subnetScanResults: List<DevicePingResult> = emptyList(),
    val isScanningSubnet: Boolean = false,
    val subnetScanProgressText: String = "",
    val programmingModeDevices: List<IndividualAddress> = emptyList(),
    val isCheckingProgrammingMode: Boolean = false,
    val deviceDiagnostics: DeviceDiagnosticDetails? = null,
    val isReadingDiagnostics: Boolean = false,
    val isRunningFullSiteCheck: Boolean = false,
    val siteCheckProgressText: String = "",
    val siteCheckReport: SiteCheckReport? = null,
    val locatingDeviceAddress: IndividualAddress? = null,
    val duplicateScanReport: DuplicateScanReport? = null,
    val isScanningDuplicates: Boolean = false,
    val rawPropertyResult: dev.knxfieldkit.model.RawPropertyReadResult? = null,
    val isReadingRawProperty: Boolean = false,
    val couplerFilterResult: dev.knxfieldkit.model.CouplerFilterTableResult? = null,
    val isCheckingCouplerFilter: Boolean = false,
    val statusMessage: String = "Ready — Open a .knxproj / .knxkeys file to begin or connect to KNXnet/IP",
)

class AppController(
    parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    hardwareClientFactory: (CoroutineScope) -> KnxBusClient = { PureKnxNetIpClient(it) },
    private val multicastAcquireCallback: (() -> Unit)? = null,
    private val multicastReleaseCallback: (() -> Unit)? = null,
) : AutoCloseable {

    private companion object {
        const val MAX_TELEGRAM_HISTORY = 1_000
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val simulatedClient = SimulatedKnxBusClient(scope)
    private val hardwareClient = hardwareClientFactory(scope)

    @Volatile
    private var activeClient: KnxBusClient = hardwareClient

    private var subnetScanJob: Job? = null
    private var batchPollJob: Job? = null
    private var walkTestJob: Job? = null

    private val initialPrefs = UserPrefsStore.load()
    private val initialTheme = when (initialPrefs.themeMode.lowercase()) {
        "dark" -> ThemeMode.DARK
        "light" -> ThemeMode.LIGHT
        else -> ThemeMode.SYSTEM
    }

    private val _uiState = MutableStateFlow(
        AppUiState(
            themeMode = initialTheme,
            project = null,
            projectSourceLabel = "No Project Loaded",
            recentFiles = initialPrefs.recentFiles,
            savedGatewayHost = initialPrefs.lastGatewayHost,
            savedGatewayPort = initialPrefs.lastGatewayPort,
            savedGatewayUseNat = initialPrefs.lastUseNat,
            selectedDeviceAddress = null,
            selectedGroupAddressInput = "",
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        observeClient(simulatedClient, isSim = true)
        observeClient(hardwareClient, isSim = false)
    }

    private fun observeClient(client: KnxBusClient, isSim: Boolean) {
        scope.launch {
            client.connectionState.collect { st ->
                if (activeClient === client) {
                    _uiState.update {
                        it.copy(
                            connectionState = st,
                            isSimulatorActive = isSim && st is ConnectionState.Connected,
                        )
                    }
                }
            }
        }
        scope.launch {
            client.incomingTelegrams.collect { raw ->
                if (activeClient !== client) return@collect
                val current = _uiState.value
                val enriched = EnrichedTelegram.enrich(raw, current.project)
                _uiState.update { state ->
                    val updatedList = if (state.monitorPaused) {
                        state.telegrams
                    } else {
                        (listOf(enriched) + state.telegrams).take(MAX_TELEGRAM_HISTORY)
                    }
                    val updatedValues = if (raw.destinationGroup != null && enriched.decodedValue.isNotBlank()) {
                        state.groupLiveValues + (raw.destinationGroup!! to GroupValueEntry(
                            valueText = enriched.decodedValue,
                            latencyMs = state.groupLiveValues[raw.destinationGroup]?.latencyMs,
                        ))
                    } else {
                        state.groupLiveValues
                    }
                    state.copy(
                        telegrams = updatedList,
                        groupLiveValues = updatedValues,
                    )
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleThemeMode() {
        val next = when (_uiState.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
    }

    fun setThemeMode(mode: ThemeMode) {
        UserPrefsStore.saveThemeMode(mode.name.lowercase())
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleConnectDialog(show: Boolean) {
        _uiState.update { it.copy(showConnectDialog = show) }
    }

    fun openProjectOrKeyringFile(file: File) {
        scope.launch {
            if (!file.isFile) {
                setStatus("File not found: ${file.absolutePath}")
                return@launch
            }
            if (file.extension.equals("knxkeys", ignoreCase = true)) {
                _uiState.update {
                    it.copy(passwordPrompt = PasswordPromptState(file = file, isKeyring = true))
                }
                return@launch
            }

            println("KNX: openProjectOrKeyringFile -> ${file.absolutePath} (${file.length()} bytes)")
            try {
                val protected = withContext(Dispatchers.IO) {
                    KnxProjArchiveReader.isPasswordProtected(file)
                }
                println("KNX: isPasswordProtected -> $protected")
                if (protected) {
                    _uiState.update {
                        it.copy(passwordPrompt = PasswordPromptState(file = file, isKeyring = false))
                    }
                } else {
                    println("KNX: parsing archive...")
                    val parsed = withContext(Dispatchers.IO) {
                        KnxProjParser.openAndParse(file, password = null)
                    }
                    println("KNX: parsed project '${parsed.name}', devices=${parsed.allDevices.size}, GAs=${parsed.groupAddresses.size}")
                    val prefs = UserPrefsStore.recordRecentFile(file)
                    applyLoadedProject(parsed, file.name, prefs.recentFiles)
                }
            } catch (_: PasswordRequiredException) {
                println("KNX: PasswordRequiredException for ${file.name}")
                _uiState.update {
                    it.copy(passwordPrompt = PasswordPromptState(file = file, isKeyring = false))
                }
            } catch (e: Exception) {
                println("KNX: Exception opening ${file.name}: ${e.javaClass.simpleName} - ${e.message}")
                e.printStackTrace()
                setStatus("Failed to open ${file.name}: ${e.message}")
            }
        }
    }

    fun submitFilePassword(password: String) {
        val prompt = _uiState.value.passwordPrompt ?: return
        scope.launch {
            try {
                if (prompt.isKeyring) {
                    val kr = withContext(Dispatchers.IO) {
                        KnxKeyringReader.parseKeyring(prompt.file, password)
                    }
                    simulatedClient.setKeyring(kr)
                    hardwareClient.setKeyring(kr)
                    val prefs = UserPrefsStore.recordRecentFile(prompt.file)
                    _uiState.update {
                        it.copy(
                            keyring = kr,
                            recentFiles = prefs.recentFiles,
                            passwordPrompt = null,
                            statusMessage = "Unlocked keyring '${kr.projectName}' (${kr.groupKeysHex.size} group keys, ${kr.interfaces.size} interfaces)",
                        )
                    }
                } else {
                    val parsed = withContext(Dispatchers.IO) {
                        KnxProjParser.openAndParse(prompt.file, password)
                    }
                    val prefs = UserPrefsStore.recordRecentFile(prompt.file)
                    applyLoadedProject(parsed, "${prompt.file.name} (Unlocked)", prefs.recentFiles)
                }
            } catch (_: InvalidPasswordException) {
                _uiState.update {
                    it.copy(
                        passwordPrompt = prompt.copy(
                            errorMessage = "Incorrect password for '${prompt.file.name}'. Checked ETS6 PBKDF2-SHA256 & ETS5 formats."
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(passwordPrompt = prompt.copy(errorMessage = e.message ?: "Decryption failed"))
                }
            }
        }
    }

    fun dismissPasswordPrompt() {
        _uiState.update { it.copy(passwordPrompt = null) }
    }

    fun loadDemoProject() {
        applyLoadedProject(DemoKnxProjectFactory.createDemoVillaProject(), "Built-in Demo (Villa Horizon)")
    }

    fun setExplorerLocationView(byRoom: Boolean) {
        _uiState.update {
            it.copy(
                explorerLocationView = byRoom,
                explorerTreeMode = if (byRoom) ExplorerTreeMode.BUILDING else ExplorerTreeMode.DUAL_SPLIT,
            )
        }
    }

    fun setExplorerTreeMode(mode: ExplorerTreeMode) {
        _uiState.update {
            it.copy(
                explorerTreeMode = mode,
                explorerLocationView = mode == ExplorerTreeMode.BUILDING,
            )
        }
    }

    fun selectExplorerNode(selection: ExplorerSelection) {
        _uiState.update { st ->
            when (selection) {
                is ExplorerSelection.Device -> st.copy(
                    explorerSelection = selection,
                    selectedDeviceAddress = selection.address,
                    selectedInspectorGroup = null,
                )
                is ExplorerSelection.GroupRange, ExplorerSelection.AllGroupAddresses -> st.copy(
                    explorerSelection = selection,
                )
                else -> st.copy(explorerSelection = selection)
            }
        }
    }

    fun openDeviceObjectsInExplorer(address: IndividualAddress) {
        _uiState.update {
            it.copy(
                explorerSelection = ExplorerSelection.Device(address),
                selectedDeviceAddress = address,
                selectedInspectorGroup = null,
            )
        }
    }

    fun toggleExplorerBottomMonitor() {
        _uiState.update { it.copy(showExplorerBottomMonitor = !it.showExplorerBottomMonitor) }
    }

    fun toggleExplorerRightInspector() {
        _uiState.update { it.copy(showExplorerRightInspector = !it.showExplorerRightInspector) }
    }

    fun inspectDeviceInExplorer(address: IndividualAddress) {
        _uiState.update { it.copy(selectedDeviceAddress = address, selectedInspectorGroup = null) }
    }

    fun inspectGroupInExplorer(address: GroupAddress) {
        _uiState.update {
            it.copy(
                selectedInspectorGroup = address,
                selectedGroupAddressInput = address.toString(),
            )
        }
    }

    fun selectMonitorTelegram(id: Long?) {
        _uiState.update { it.copy(selectedMonitorTelegramId = id) }
    }

    private fun applyLoadedProject(
        project: KnxProject,
        label: String,
        updatedRecent: List<File>? = null,
    ) {
        simulatedClient.syncWithProject(project)
        val firstGa = project.groupAddresses.keys.firstOrNull()?.toString() ?: "1/1/1"
        val firstDev = project.allDevices.keys.firstOrNull()
        _uiState.update {
            it.copy(
                project = project,
                projectSourceLabel = label,
                recentFiles = updatedRecent ?: it.recentFiles,
                passwordPrompt = null,
                explorerSelection = if (it.explorerSelection is ExplorerSelection.BuildingSpace || it.explorerSelection is ExplorerSelection.AllGroupAddresses || it.explorerSelection is ExplorerSelection.GroupRange) it.explorerSelection else ExplorerSelection.TopologyRoot,
                selectedGroupAddressInput = firstGa,
                selectedDeviceAddress = firstDev,
                selectedInspectorGroup = null,
                deviceStatuses = emptyMap(),
                lineAuditReport = null,
                siteCheckReport = null,
                statusMessage = "Loaded '${project.name}': ${project.allDevices.size} devices, ${project.groupAddresses.size} group addresses",
            )
        }
    }

    /**
     * Generates a genuine ETS6 AES-256 PBKDF2-encrypted `.knxproj` sample file and prompts to unlock it.
     */
    fun generateAndPromptEncryptedSample(targetFile: File, samplePassword: String = "knx2026") {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val baseTmp = targetFile.parentFile ?: File(System.getProperty("java.io.tmpdir", "."))
                    val tmpDir = File(baseTmp, "ets6-sample-${System.currentTimeMillis()}").apply { mkdirs() }
                    try {
                        val masterXml = """
                            <?xml version="1.0" encoding="utf-8"?>
                            <KNX xmlns="http://knx.org/xml/project/21">
                              <MasterData>
                                <Manufacturers>
                                  <Manufacturer Id="M-0083" Name="MDT technologies" />
                                  <Manufacturer Id="M-00C5" Name="Weinzierl Engineering" />
                                </Manufacturers>
                              </MasterData>
                            </KNX>
                        """.trimIndent()
                        val zeroXml = """
                            <?xml version="1.0" encoding="utf-8"?>
                            <KNX xmlns="http://knx.org/xml/project/21" CreatedBy="ETS6" ToolVersion="6.2.2">
                              <Project Id="P-0999">
                                <ProjectInformation Name="Encrypted Penthouse ETS6 Sample" GroupAddressStyle="ThreeLevel" />
                                <Installations>
                                  <Installation Name="">
                                    <Topology>
                                      <Area Address="1" Name="Backbone">
                                        <Line Address="1" Name="Penthouse Main TP">
                                          <DeviceInstance Id="D-1" Address="1" Name="KNX IP Router 752 Secure" ProductRefId="M-00C5_H-1_P-1" />
                                          <DeviceInstance Id="D-2" Address="10" Name="MDT Switch Actuator 8-fold" ProductRefId="M-0083_H-1_P-1">
                                            <ComObjectInstanceRefs>
                                              <ComObjectInstanceRef RefId="O-1" Text="Living Spots" FunctionText="Switch" DatapointType="DPST-1-1" Links="GA-1" />
                                            </ComObjectInstanceRefs>
                                          </DeviceInstance>
                                          <DeviceInstance Id="D-3" Address="20" Name="MDT Smart Glass Push Button" ProductRefId="M-0083_H-1_P-2">
                                            <ComObjectInstanceRefs>
                                              <ComObjectInstanceRef RefId="O-2" Text="Room Temp" FunctionText="Temperature" DatapointType="DPST-9-1" Links="GA-2" />
                                            </ComObjectInstanceRefs>
                                          </DeviceInstance>
                                        </Line>
                                      </Area>
                                    </Topology>
                                    <GroupAddresses>
                                      <GroupRanges>
                                        <GroupRange Name="Lighting &amp; HVAC" RangeStart="2048" RangeEnd="4095">
                                          <GroupAddress Id="P-0999-0_GA-1" Address="2305" Name="Penthouse Living Spots On/Off" DatapointType="DPST-1-1" />
                                          <GroupAddress Id="P-0999-0_GA-2" Address="2306" Name="Penthouse Room Temperature" DatapointType="DPST-9-1" />
                                        </GroupRange>
                                      </GroupRanges>
                                    </GroupAddresses>
                                  </Installation>
                                </Installations>
                              </Project>
                            </KNX>
                        """.trimIndent()

                        val masterFile = File(tmpDir, "knx_master.xml").apply { writeText(masterXml) }
                        val zeroFile = File(tmpDir, "0.xml").apply { writeText(zeroXml) }
                        val innerZipFile = File(tmpDir, "P-0999.zip")
                        val ets6Derived = EtsCrypto.deriveEts6ZipPassword(samplePassword)
                        val encParams = ZipParameters().apply {
                            isEncryptFiles = true
                            encryptionMethod = EncryptionMethod.AES
                            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                        }
                        ZipFile(innerZipFile, ets6Derived).use { iz ->
                            iz.addFile(zeroFile, encParams)
                        }

                        targetFile.parentFile?.mkdirs()
                        if (targetFile.exists()) targetFile.delete()
                        ZipFile(targetFile).use { oz ->
                            oz.addFile(masterFile)
                            oz.addFile(innerZipFile)
                        }
                    } finally {
                        tmpDir.deleteRecursively()
                    }
                }
                setStatus("Created ETS6 password-protected sample '${targetFile.name}' (password: $samplePassword)")
                openProjectOrKeyringFile(targetFile)
            } catch (e: Exception) {
                setStatus("Failed creating sample: ${e.message}")
            }
        }
    }

    fun discoverGateways() {
        scope.launch {
            _uiState.update { it.copy(isDiscoveringGateways = true) }
            multicastAcquireCallback?.invoke()
            try {
                val realGateways = runCatching { hardwareClient.discoverGateways(2_500L) }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        discoveredGateways = realGateways,
                        isDiscoveringGateways = false,
                        statusMessage = if (realGateways.isEmpty()) {
                            "No KNXnet/IP gateways discovered on Wi-Fi/LAN"
                        } else {
                            "Discovered ${realGateways.size} KNXnet/IP gateway(s) on Wi-Fi/LAN"
                        },
                    )
                }
            } finally {
                if (_uiState.value.connectionState !is ConnectionState.Connected) {
                    multicastReleaseCallback?.invoke()
                }
            }
        }
    }

    fun connectSimulator() {
        scope.launch {
            runCatching { activeClient.disconnect() }
            multicastReleaseCallback?.invoke()
            activeClient = simulatedClient
            simulatedClient.setKeyring(_uiState.value.keyring)
            _uiState.value.project?.let { simulatedClient.syncWithProject(it) }
            simulatedClient.connect(BusConnectionConfig.Simulator(enablePeriodicTraffic = true))
            _uiState.update {
                it.copy(
                    showConnectDialog = false,
                    statusMessage = "Connected to Virtual KNX Bus Simulator (live periodic sensor traffic enabled)",
                )
            }
        }
    }

    fun connectTunneling(
        host: String,
        port: Int,
        useNat: Boolean,
        userPassword: String? = null,
        deviceAuthCode: String? = null,
    ) {
        scope.launch {
            runCatching { activeClient.disconnect() }
            multicastReleaseCallback?.invoke()
            activeClient = hardwareClient
            hardwareClient.setKeyring(_uiState.value.keyring)
            val prefs = UserPrefsStore.saveGateway(host.trim(), port, useNat)
            try {
                hardwareClient.connect(
                    BusConnectionConfig.IpTunneling(
                        gatewayHost = host.trim(),
                        gatewayPort = port,
                        useNat = useNat,
                        userPassword = userPassword?.takeIf { it.isNotBlank() },
                        deviceAuthCode = deviceAuthCode?.takeIf { it.isNotBlank() },
                    )
                )
                _uiState.update {
                    it.copy(
                        showConnectDialog = false,
                        savedGatewayHost = prefs.lastGatewayHost,
                        savedGatewayPort = prefs.lastGatewayPort,
                        savedGatewayUseNat = prefs.lastUseNat,
                        statusMessage = "Connected via KNXnet/IP Tunneling to $host:$port",
                    )
                }
            } catch (e: Exception) {
                setStatus("KNXnet/IP Tunneling failed: ${e.message}")
            }
        }
    }

    fun connectRouting(multicastAddress: String = "224.0.23.12", port: Int = 3671) {
        scope.launch {
            runCatching { activeClient.disconnect() }
            multicastAcquireCallback?.invoke()
            activeClient = hardwareClient
            hardwareClient.setKeyring(_uiState.value.keyring)
            try {
                hardwareClient.connect(BusConnectionConfig.IpRouting(multicastAddress = multicastAddress.trim(), port = port))
                _uiState.update {
                    it.copy(
                        showConnectDialog = false,
                        statusMessage = "Connected via KNXnet/IP Routing ($multicastAddress:$port)",
                    )
                }
            } catch (e: Exception) {
                multicastReleaseCallback?.invoke()
                setStatus("KNXnet/IP Routing failed: ${e.message}")
            }
        }
    }

    fun disconnectBus() {
        scope.launch {
            runCatching { activeClient.disconnect() }
            multicastReleaseCallback?.invoke()
            setStatus("Disconnected from KNX bus")
        }
    }

    fun setMonitorPaused(paused: Boolean) = _uiState.update { it.copy(monitorPaused = paused) }
    fun setMonitorSearch(query: String) = _uiState.update { it.copy(monitorSearchQuery = query) }
    fun setMonitorGhostOnly(ghostOnly: Boolean) = _uiState.update { it.copy(monitorGhostOnly = ghostOnly) }
    fun clearMonitorLog() = _uiState.update { it.copy(telegrams = emptyList()) }

    fun selectGroupAddressForSender(addressText: String) {
        _uiState.update { it.copy(selectedGroupAddressInput = addressText) }
    }

    fun readGroupAddress(addressText: String) {
        val ga = GroupAddress.parseOrNull(addressText) ?: run {
            setStatus("Invalid Group Address: '$addressText'")
            return
        }
        scope.launch {
            try {
                val res = activeClient.sendGroupRead(ga)
                val dpt = _uiState.value.project?.findGroupAddress(ga)?.dpt
                if (res.responded && res.payload != null) {
                    val decoded = DptCodec.decode(dpt, res.payload!!)
                    _uiState.update { st ->
                        st.copy(
                            groupLiveValues = st.groupLiveValues + (ga to GroupValueEntry(decoded, res.latencyMs)),
                            statusMessage = "Read $ga -> $decoded (from ${res.responder}, ${res.latencyMs} ms)",
                        )
                    }
                } else {
                    setStatus("Read $ga: No response (${res.errorMessage ?: "timeout"})")
                }
            } catch (e: Exception) {
                setStatus("GroupRead failed: ${e.message}")
            }
        }
    }

    fun pollAllProjectGroupAddresses() {
        val gas = _uiState.value.project?.groupAddresses?.keys?.toList().orEmpty()
        if (gas.isEmpty()) return
        batchPollJob?.cancel()
        batchPollJob = scope.launch {
            _uiState.update { it.copy(isPollingBatch = true, statusMessage = "Polling ${gas.size} group addresses...") }
            var answered = 0
            try {
                for (ga in gas) {
                    val res = activeClient.sendGroupRead(ga, timeoutMs = 1_200L)
                    if (res.responded && res.payload != null) {
                        answered++
                        val dpt = _uiState.value.project?.findGroupAddress(ga)?.dpt
                        val decoded = DptCodec.decode(dpt, res.payload!!)
                        _uiState.update { st ->
                            st.copy(groupLiveValues = st.groupLiveValues + (ga to GroupValueEntry(decoded, res.latencyMs)))
                        }
                    }
                    delay(125L) // Safe ~8 telegrams/s TP1 rate limit
                }
                setStatus("Batch poll complete: $answered / ${gas.size} group addresses responded")
            } catch (_: CancellationException) {
                setStatus("Batch poll cancelled")
            } catch (e: Exception) {
                setStatus("Batch poll error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isPollingBatch = false) }
            }
        }
    }

    fun writeGroupSwitch(addressText: String, value: Boolean) =
        writeGroupInternal(addressText, DptCodec.encodeBool(value), compact6Bit = true, summary = if (value) "ON" else "OFF")

    fun writeGroupDimStep(addressText: String, increase: Boolean, stepCode: Int = 2) =
        writeGroupInternal(
            addressText,
            DptCodec.encodeDimmingStep(increase, stepCode),
            compact6Bit = true,
            summary = if (stepCode == 0) "DIM STOP" else if (increase) "DIM UP" else "DIM DOWN",
        )

    fun writeGroupPercent(addressText: String, percent: Double) =
        writeGroupInternal(addressText, DptCodec.encodePercent(percent), compact6Bit = false, summary = "${String.format(Locale.US, "%.0f", percent)} %")

    fun writeGroupFloat16(addressText: String, value: Double) =
        writeGroupInternal(addressText, DptCodec.encodeFloat16(value), compact6Bit = false, summary = String.format(Locale.US, "%.2f", value))

    fun writeGroupInt32(addressText: String, value: Int) =
        writeGroupInternal(addressText, DptCodec.encodeInt32(value), compact6Bit = false, summary = value.toString())

    fun writeGroupString14(addressText: String, text: String) =
        writeGroupInternal(addressText, DptCodec.encodeString14(text), compact6Bit = false, summary = "\"${text.take(14)}\"")

    fun writeGroupScene(addressText: String, scene1Based: Int) =
        writeGroupInternal(addressText, DptCodec.encodeScene(scene1Based.coerceIn(1, 64)), compact6Bit = false, summary = "Scene $scene1Based")

    fun writeGroupHvac(addressText: String, mode: HvacMode) =
        writeGroupInternal(addressText, DptCodec.encodeHvacMode(mode), compact6Bit = false, summary = mode.label)

    fun replayTelegram(telegram: BusTelegram) {
        val dstGroup = telegram.destinationGroup ?: run {
            setStatus("Only Group Address telegrams can be replayed directly")
            return
        }
        when (telegram.apci) {
            ApciType.GROUP_READ -> readGroupAddress(dstGroup.toString())
            else -> {
                val compact = telegram.payload.size == 1 && (telegram.payload[0].toInt() and 0xFF) <= 0x3F
                writeGroupInternal(dstGroup.toString(), telegram.payload, compact6Bit = compact, summary = "Replayed 0x${HexBytes.toHex(telegram.payload)}")
            }
        }
    }

    fun startWalkTestBlink(addressText: String, intervalMs: Long = 1_800L) {
        val ga = GroupAddress.parseOrNull(addressText) ?: run {
            setStatus("Invalid Group Address for Walk-Test: '$addressText'")
            return
        }
        walkTestJob?.cancel()
        walkTestJob = scope.launch {
            if (_uiState.value.connectionState !is ConnectionState.Connected) {
                setStatus("Bus not connected — Connect to a KNXnet/IP gateway first")
                return@launch
            }
            val dpt = DptCodec.resolveMainDpt(_uiState.value.project?.findGroupAddress(ga)?.dpt)
            _uiState.update {
                it.copy(
                    walkTestActive = true,
                    walkTestGroupAddress = ga,
                    walkTestStepCount = 0,
                    statusMessage = "Walk-Test Blinker ACTIVE on $ga — toggling circuit every ${intervalMs}ms",
                )
            }
            var toggle = true
            var count = 0
            try {
                while (true) {
                    if (_uiState.value.connectionState !is ConnectionState.Connected) {
                        setStatus("Bus disconnected — stopping Walk-Test")
                        break
                    }
                    when (dpt) {
                        MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
                            activeClient.sendGroupWrite(ga, DptCodec.encodePercent(if (toggle) 100.0 else 10.0), compact6Bit = false)
                        }
                        else -> {
                            activeClient.sendGroupWrite(ga, DptCodec.encodeBool(toggle), compact6Bit = true)
                        }
                    }
                    count++
                    toggle = !toggle
                    _uiState.update { it.copy(walkTestStepCount = count) }
                    delay(intervalMs.coerceAtLeast(600L))
                }
            } catch (_: CancellationException) {
                // Stopped cleanly
            } catch (e: Exception) {
                setStatus("Walk-Test stopped: ${e.message}")
            } finally {
                _uiState.update { it.copy(walkTestActive = false, walkTestGroupAddress = null) }
            }
        }
    }

    fun stopWalkTestBlink() {
        walkTestJob?.cancel()
        walkTestJob = null
        _uiState.update {
            it.copy(
                walkTestActive = false,
                walkTestGroupAddress = null,
                statusMessage = "Walk-Test Blinker stopped",
            )
        }
    }

    fun toggleChannelWalkTestVerified(ga: GroupAddress) {
        _uiState.update { st ->
            val nowVerified = if (ga in st.walkTestVerifiedChannels) {
                st.walkTestVerifiedChannels - ga
            } else {
                st.walkTestVerifiedChannels + ga
            }
            st.copy(
                walkTestVerifiedChannels = nowVerified,
                walkTestFlaggedChannels = st.walkTestFlaggedChannels - ga,
                statusMessage = if (ga in nowVerified) "Channel $ga marked Verified OK" else "Channel $ga unchecked",
            )
        }
    }

    fun flagChannelWalkTestIssue(ga: GroupAddress, note: String) {
        _uiState.update { st ->
            st.copy(
                walkTestFlaggedChannels = st.walkTestFlaggedChannels + (ga to note),
                walkTestVerifiedChannels = st.walkTestVerifiedChannels - ga,
                statusMessage = "Channel $ga flagged: $note",
            )
        }
    }

    fun clearWalkTestChannels() {
        _uiState.update { it.copy(walkTestVerifiedChannels = emptySet(), walkTestFlaggedChannels = emptyMap()) }
    }

    fun writeGroupRawHex(addressText: String, hexPayload: String, compact6Bit: Boolean) {
        val bytes = runCatching { HexBytes.fromHex(hexPayload) }.getOrElse {
            setStatus("Invalid hex payload: ${it.message}")
            return
        }
        writeGroupInternal(addressText, bytes, compact6Bit = compact6Bit, summary = "0x${HexBytes.toHex(bytes)}")
    }

    private fun writeGroupInternal(addressText: String, payload: ByteArray, compact6Bit: Boolean, summary: String) {
        val ga = GroupAddress.parseOrNull(addressText) ?: run {
            setStatus("Invalid Group Address: '$addressText'")
            return
        }
        scope.launch {
            try {
                activeClient.sendGroupWrite(ga, payload, compact6Bit)
                setStatus("Sent GroupValue_Write $ga = $summary")
            } catch (e: Exception) {
                setStatus("Write failed (ensure bus is connected): ${e.message}")
            }
        }
    }

    fun pingIndividualDevice(addressText: String) {
        val ia = IndividualAddress.parseOrNull(addressText) ?: run {
            setStatus("Invalid Individual Address: '$addressText'")
            return
        }
        scope.launch {
            _uiState.update { it.copy(isPinging = true) }
            try {
                val res = activeClient.pingDevice(ia)
                _uiState.update { st ->
                    st.copy(
                        deviceStatuses = st.deviceStatuses + (ia to res),
                        pingResults = (listOf(res) + st.pingResults).take(50),
                        statusMessage = if (res.reachable) {
                            "Device $ia is ONLINE (Mask ${res.maskVersionHex}, ${res.latencyMs} ms)"
                        } else {
                            "Device $ia UNREACHABLE (${res.errorMessage ?: "no reply"})"
                        },
                    )
                }
            } catch (e: Exception) {
                setStatus("Ping error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isPinging = false) }
            }
        }
    }

    fun inspectDeviceDiagnostics(addressText: String) {
        val ia = IndividualAddress.parseOrNull(addressText) ?: run {
            setStatus("Invalid Individual Address: '$addressText'")
            return
        }
        scope.launch {
            _uiState.update { it.copy(isReadingDiagnostics = true) }
            try {
                val diag = activeClient.readDeviceDiagnostics(ia)
                _uiState.update {
                    it.copy(
                        deviceDiagnostics = diag,
                        statusMessage = if (diag.reachable) "Read diagnostics from $ia" else "Device $ia did not respond",
                    )
                }
            } catch (e: Exception) {
                setStatus("Diagnostics readout failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isReadingDiagnostics = false) }
            }
        }
    }

    fun runProjectLineAudit(area: Int, line: Int) {
        val topoLine = _uiState.value.project?.findLine(area, line) ?: run {
            setStatus("Line $area.$line not found in loaded project")
            return
        }
        scope.launch {
            _uiState.update {
                it.copy(
                    isAuditingLine = true,
                    lineAuditReport = null,
                    lineAuditInProgressEntries = emptyList(),
                    statusMessage = "Auditing ${topoLine.devices.size} devices on Line ${topoLine.id} (${topoLine.name})...",
                )
            }
            try {
                val report = activeClient.auditLine(
                    line = topoLine,
                    delayBetweenMs = 60L,
                    onProgress = { entry ->
                        _uiState.update { st ->
                            st.copy(
                                deviceStatuses = st.deviceStatuses + (entry.expectedDevice.address to entry.pingResult),
                                lineAuditInProgressEntries = st.lineAuditInProgressEntries + entry,
                            )
                        }
                    },
                )
                _uiState.update {
                    it.copy(
                        lineAuditReport = report,
                        statusMessage = "Line ${report.lineId} Audit finished: ${report.onlineCount} online, ${report.unreachableCount} unreachable, ${report.mismatchCount} mask mismatch",
                    )
                }
            } catch (e: Exception) {
                setStatus("Line audit failed (ensure bus is connected): ${e.message}")
            } finally {
                _uiState.update { it.copy(isAuditingLine = false) }
            }
        }
    }

    fun startSubnetScan(area: Int, line: Int, startDevice: Int = 1, endDevice: Int = 32) {
        val safeArea = area.coerceIn(0, 15)
        val safeLine = line.coerceIn(0, 15)
        val from = startDevice.coerceIn(0, 255)
        val to = endDevice.coerceIn(from, 255)
        val targets = (from..to).map { IndividualAddress(safeArea, safeLine, it) }

        subnetScanJob?.cancel()
        subnetScanJob = scope.launch {
            _uiState.update {
                it.copy(
                    isScanningSubnet = true,
                    subnetScanResults = emptyList(),
                    subnetScanProgressText = "Scanning $safeArea.$safeLine.$from..$to",
                )
            }
            try {
                var idx = 0
                activeClient.scanAddresses(targets, delayBetweenMs = 40L, timeoutPerDeviceMs = 600L).collect { res ->
                    idx++
                    _uiState.update { st ->
                        st.copy(
                            deviceStatuses = if (res.reachable) st.deviceStatuses + (res.address to res) else st.deviceStatuses,
                            subnetScanResults = if (res.reachable) st.subnetScanResults + res else st.subnetScanResults,
                            subnetScanProgressText = "Scanned ${res.address} ($idx/${targets.size})",
                        )
                    }
                }
                setStatus("Subnet scan complete on $safeArea.$safeLine.$from..$to: ${_uiState.value.subnetScanResults.size} active device(s) found")
            } catch (_: CancellationException) {
                setStatus("Subnet scan cancelled")
            } catch (e: Exception) {
                setStatus("Subnet scan failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isScanningSubnet = false) }
            }
        }
    }

    fun cancelSubnetScan() {
        subnetScanJob?.cancel()
    }

    fun scanProgrammingModeLeds() {
        scope.launch {
            _uiState.update { it.copy(isCheckingProgrammingMode = true) }
            try {
                val active = activeClient.findProgrammingModeDevices()
                _uiState.update {
                    it.copy(
                        programmingModeDevices = active,
                        statusMessage = if (active.isEmpty()) {
                            "No devices currently have Programming LED active"
                        } else {
                            "Found ${active.size} device(s) in Programming Mode: ${active.joinToString()}"
                        },
                    )
                }
            } catch (e: Exception) {
                setStatus("Programming Mode scan failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isCheckingProgrammingMode = false) }
            }
        }
    }

    fun toggleSimulatedDeviceOnline(address: IndividualAddress, online: Boolean) {
        simulatedClient.setDeviceOnline(address, online)
        setStatus("Simulator: Set device $address ${if (online) "ONLINE" else "OFFLINE (Simulated Wiring Fault)"}")
    }

    fun toggleSimulatedProgrammingLed(address: IndividualAddress, active: Boolean) {
        simulatedClient.setProgrammingMode(address, active)
        setStatus("Simulator: Set Programming LED on $address ${if (active) "ON" else "OFF"}")
    }

    fun restartRemoteDevice(addressText: String) {
        val ia = IndividualAddress.parseOrNull(addressText) ?: run {
            setStatus("Invalid Individual Address: '$addressText'")
            return
        }
        scope.launch {
            try {
                val ok = activeClient.restartDevice(ia)
                setStatus(if (ok) "Sent KNX A_Restart to $ia (device rebooting)" else "Device $ia did not accept A_Restart")
            } catch (e: Exception) {
                setStatus("Restart failed: ${e.message}")
            }
        }
    }

    fun locateDevice(address: IndividualAddress, flashCount: Int = 4) {
        scope.launch {
            _uiState.update {
                it.copy(
                    locatingDeviceAddress = address,
                    statusMessage = "💡 Flashing Programming LED on $address (${flashCount}x)...",
                )
            }
            try {
                activeClient.locateDevice(address, flashCount)
                _uiState.update {
                    it.copy(
                        locatingDeviceAddress = null,
                        statusMessage = "Finished flashing Programming LED on $address",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        locatingDeviceAddress = null,
                        statusMessage = "Locate $address failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun scanDuplicatePhysicalAddresses(area: Int, line: Int) {
        scope.launch {
            _uiState.update {
                it.copy(
                    isScanningDuplicates = true,
                    statusMessage = "ETS Duplicate Check: Scanning Line $area.$line for address collisions & conflicts...",
                )
            }
            try {
                val issues = mutableListOf<DuplicateAddressIssue>()
                val proj = _uiState.value.project
                val lineDevices = proj?.areas?.find { it.area == area }?.lines?.find { it.line == line }?.devices.orEmpty()

                // 1. Check for duplicate assignments inside the loaded project file
                val groupedByAddress = lineDevices.groupBy { it.address }
                for ((addr, devs) in groupedByAddress) {
                    if (devs.size > 1) {
                        issues.add(
                            DuplicateAddressIssue(
                                address = addr,
                                type = DuplicateIssueType.PROJECT_DUPLICATE,
                                description = "Address $addr assigned to ${devs.size} devices in project: ${devs.joinToString { it.name }}",
                                projectDevice = devs.first(),
                            )
                        )
                    }
                }

                // 2. Ping active line devices to verify hardware mask against project records
                var pingCount = 0
                for (dev in lineDevices) {
                    pingCount++
                    val ping = activeClient.pingDevice(dev.address, 1_000L)
                    if (ping.reachable) {
                        val expMask = dev.maskVersion?.uppercase()?.removePrefix("MV-")?.trim()
                        if (expMask != null && ping.maskVersionHex != null && expMask != ping.maskVersionHex) {
                            issues.add(
                                DuplicateAddressIssue(
                                    address = dev.address,
                                    type = DuplicateIssueType.HARDWARE_CONFLICT,
                                    description = "Hardware mask ${ping.maskVersionHex} does not match ETS project mask $expMask on '${dev.name}'",
                                    projectDevice = dev,
                                    livePingResult = ping,
                                )
                            )
                        }
                    }
                    delay(50L)
                }

                val report = DuplicateScanReport(
                    area = area,
                    line = line,
                    totalChecked = maxOf(lineDevices.size, pingCount),
                    issues = issues,
                )
                _uiState.update {
                    it.copy(
                        isScanningDuplicates = false,
                        duplicateScanReport = report,
                        statusMessage = if (issues.isEmpty()) "Duplicate scan on $area.$line complete: 0 collisions detected (All ${report.totalChecked} devices OK)"
                        else "Duplicate scan on $area.$line: Detected ${issues.size} address/hardware conflict(s)!",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanningDuplicates = false,
                        statusMessage = "Duplicate check failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun readRawProperty(address: IndividualAddress, objectIndex: Int, propertyId: Int) {
        scope.launch {
            _uiState.update {
                it.copy(
                    isReadingRawProperty = true,
                    rawPropertyResult = null,
                    statusMessage = "Reading Object $objectIndex PID $propertyId from $address...",
                )
            }
            try {
                val res = activeClient.readRawProperty(address, objectIndex, propertyId)
                _uiState.update {
                    it.copy(
                        isReadingRawProperty = false,
                        rawPropertyResult = res,
                        statusMessage = if (res.success) "Read PID $propertyId: ${res.hexValue}" else "Failed to read PID $propertyId: ${res.errorMessage}",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isReadingRawProperty = false,
                        rawPropertyResult = dev.knxfieldkit.model.RawPropertyReadResult(address, objectIndex, propertyId, false, errorMessage = e.message),
                        statusMessage = "Error reading PID $propertyId: ${e.message}",
                    )
                }
            }
        }
    }

    fun checkCouplerFilterTable(couplerAddress: IndividualAddress, targetGroup: GroupAddress) {
        scope.launch {
            _uiState.update {
                it.copy(
                    isCheckingCouplerFilter = true,
                    couplerFilterResult = null,
                    statusMessage = "Diagnosing filter table on coupler $couplerAddress for $targetGroup...",
                )
            }
            try {
                val res = activeClient.checkCouplerFilterTable(couplerAddress, targetGroup)
                _uiState.update {
                    it.copy(
                        isCheckingCouplerFilter = false,
                        couplerFilterResult = res,
                        statusMessage = "Filter diagnosis: ${if (res.isRouted) "ALLOWED" else "FILTERED"} for $targetGroup",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingCouplerFilter = false,
                        couplerFilterResult = dev.knxfieldkit.model.CouplerFilterTableResult(couplerAddress, targetGroup, false, "Error", e.message ?: "Diagnostic failed"),
                        statusMessage = "Filter diagnosis error: ${e.message}",
                    )
                }
            }
        }
    }

    fun commissionProgrammingDevice(newAddressText: String) {
        val newIa = IndividualAddress.parseOrNull(newAddressText) ?: run {
            setStatus("Invalid target Individual Address: '$newAddressText'")
            return
        }
        scope.launch {
            try {
                val res = activeClient.writeProgrammingAddress(newIa)
                val updatedLeds = runCatching { activeClient.findProgrammingModeDevices() }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        programmingModeDevices = updatedLeds,
                        statusMessage = res.message,
                    )
                }
            } catch (e: Exception) {
                setStatus("Address commissioning failed: ${e.message}")
            }
        }
    }

    fun runFullSiteCheck() {
        if (_uiState.value.isRunningFullSiteCheck) return
        scope.launch {
            if (_uiState.value.connectionState !is ConnectionState.Connected) {
                setStatus("Bus not connected — Connect to a KNXnet/IP gateway before running Site Check")
                return@launch
            }
            _uiState.update {
                it.copy(
                    isRunningFullSiteCheck = true,
                    siteCheckProgressText = "Preparing bus connection...",
                    statusMessage = "Running Full Site Health Check...",
                )
            }
            try {
                val proj = _uiState.value.project
                val statuses = _uiState.value.deviceStatuses.toMutableMap()

                for (line in proj?.allLines.orEmpty()) {
                    _uiState.update { it.copy(siteCheckProgressText = "Auditing Line ${line.id} (${line.name})...") }
                    val rep = activeClient.auditLine(
                        line = line,
                        delayBetweenMs = 25L,
                        timeoutPerDeviceMs = 800L,
                        onProgress = { entry ->
                            statuses[entry.expectedDevice.address] = entry.pingResult
                            _uiState.update { st -> st.copy(deviceStatuses = statuses.toMap()) }
                        },
                    )
                    _uiState.update { it.copy(lineAuditReport = rep) }
                }

                val gas = proj?.groupAddresses?.keys?.toList().orEmpty()
                var responded = 0
                for ((idx, ga) in gas.withIndex()) {
                    _uiState.update { it.copy(siteCheckProgressText = "Polling Group Addresses (${idx + 1}/${gas.size})...") }
                    val res = activeClient.sendGroupRead(ga, timeoutMs = 900L)
                    if (res.responded && res.payload != null) {
                        responded++
                        val dpt = proj?.findGroupAddress(ga)?.dpt
                        val decoded = DptCodec.decode(dpt, res.payload!!)
                        _uiState.update { st ->
                            st.copy(groupLiveValues = st.groupLiveValues + (ga to GroupValueEntry(decoded, res.latencyMs)))
                        }
                    }
                    delay(40L)
                }

                _uiState.update { it.copy(siteCheckProgressText = "Checking active Programming-Mode LEDs...") }
                val activeLeds = activeClient.findProgrammingModeDevices()
                val report = BusHealthAnalyzer.buildSiteCheckReport(
                    project = proj,
                    deviceStatuses = statuses,
                    polledTotal = gas.size,
                    polledResponded = responded,
                    activeProgLeds = activeLeds,
                    telegrams = _uiState.value.telegrams,
                )
                _uiState.update {
                    it.copy(
                        programmingModeDevices = activeLeds,
                        siteCheckReport = report,
                        siteCheckProgressText = "",
                        statusMessage = "Full Site Check complete — Readiness Score: ${report.readinessScorePercent}% (${report.onlineDevices}/${report.totalDevicesExpected} online)",
                    )
                }
            } catch (e: Exception) {
                setStatus("Full Site Check failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isRunningFullSiteCheck = false, siteCheckProgressText = "") }
            }
        }
    }

    fun exportHtmlCommissioningReport(targetFile: File, onDone: ((File) -> Unit)? = null) {
        val snap = _uiState.value
        scope.launch {
            withContext(Dispatchers.IO) {
                HtmlReportExporter.exportCommissioningHtml(
                    targetFile = targetFile,
                    project = snap.project,
                    siteCheck = snap.siteCheckReport,
                    deviceStatuses = snap.deviceStatuses,
                    groupLiveValues = snap.groupLiveValues,
                    walkTestVerified = snap.walkTestVerifiedChannels,
                    walkTestFlagged = snap.walkTestFlaggedChannels,
                )
            }
            setStatus("Exported HTML Commissioning Report to ${targetFile.name}")
            onDone?.invoke(targetFile)
        }
    }

    fun exportMonitorCsv(targetFile: File, onDone: ((File) -> Unit)? = null) {
        scope.launch {
            val rows = _uiState.value.telegrams
            withContext(Dispatchers.IO) {
                val sb = StringBuilder()
                sb.appendLine("Timestamp,SourceAddr,SourceName,Destination,DestinationName,APCI,DPT,DecodedValue,GhostAddress,RawPayloadHex")
                for (row in rows) {
                    val t = row.telegram
                    val ts = TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs))
                    val dst = t.destinationGroup?.toString() ?: t.destinationIndividual?.toString().orEmpty()
                    sb.appendLine(
                        listOf(
                            ts,
                            t.source.toString(),
                            csvEscape(row.sourceDeviceName.orEmpty()),
                            dst,
                            csvEscape(row.destinationName.orEmpty()),
                            t.apci.label,
                            row.dptId.orEmpty(),
                            csvEscape(row.decodedValue),
                            row.isUnmappedGroup.toString(),
                            HexBytes.toHex(t.payload),
                        ).joinToString(",")
                    )
                }
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(sb.toString(), Charsets.UTF_8)
            }
            setStatus("Exported ${rows.size} telegrams to ${targetFile.name}")
            onDone?.invoke(targetFile)
        }
    }

    fun exportProjectMarkdown(targetFile: File, onDone: ((File) -> Unit)? = null) {
        val proj = _uiState.value.project ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                val sb = StringBuilder()
                sb.appendLine("# KNX Project Field Report: ${proj.name}")
                sb.appendLine("- **Project ID**: `${proj.projectId}`")
                sb.appendLine("- **ETS Version**: `${proj.etsVersion.ifBlank { "N/A" }}`")
                sb.appendLine("- **Password Protected**: `${proj.passwordProtected}`")
                sb.appendLine("- **Total Devices**: `${proj.allDevices.size}`")
                sb.appendLine("- **Total Group Addresses**: `${proj.groupAddresses.size}`")
                sb.appendLine()
                sb.appendLine("## Topology")
                for (area in proj.areas) {
                    sb.appendLine("### Area ${area.area} — ${area.name}")
                    for (line in area.lines) {
                        sb.appendLine("#### Line ${line.id} — ${line.name} (${line.mediumType})")
                        sb.appendLine("| Address | Name | Manufacturer | Product | Mask |")
                        sb.appendLine("| :--- | :--- | :--- | :--- | :--- |")
                        for (dev in line.devices) {
                            sb.appendLine("| `${dev.address}` | ${dev.name} | ${dev.manufacturerName} | ${dev.productName} | `${dev.maskVersion ?: "-"}` |")
                        }
                        sb.appendLine()
                    }
                }
                sb.appendLine("## Group Addresses")
                sb.appendLine("| Address | Name | DPT | Linked Objects |")
                sb.appendLine("| :--- | :--- | :--- | :--- |")
                for (ga in proj.groupAddresses.values.sortedBy { it.address }) {
                    sb.appendLine("| `${ga.address}` | ${ga.name} | `${ga.dpt ?: "-"}` | ${ga.linkedObjects.size} |")
                }
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(sb.toString(), Charsets.UTF_8)
            }
            setStatus("Exported project report to ${targetFile.name}")
            onDone?.invoke(targetFile)
        }
    }

    private fun csvEscape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"${s.replace("\"", "\"\"")}\"" else s

    fun setStatus(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    override fun close() {
        simulatedClient.close()
        hardwareClient.close()
        multicastReleaseCallback?.invoke()
        scope.cancel()
    }
}
