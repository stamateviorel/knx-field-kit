package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.app.AppController
import dev.knxfieldkit.app.AppTab
import dev.knxfieldkit.app.ExplorerSelection
import dev.knxfieldkit.app.ThemeMode
import dev.knxfieldkit.model.ConnectionState
import dev.knxfieldkit.model.EnrichedTelegram
import dev.knxfieldkit.model.GroupAddressInfo

@Composable
fun MobileFieldApp(
    controller: AppController,
    modifier: Modifier = Modifier,
    initialDiagTab: EtsDiagnosticsTab = EtsDiagnosticsTab.LINE_DIAGNOSTICS,
    onOpenProjectFile: () -> Unit = {},
    onShareHtmlReport: () -> Unit = {},
    onShareMonitorCsv: () -> Unit = {},
    onShareMarkdownSummary: () -> Unit = {},
) {
    val state by controller.uiState.collectAsState()
    var showProjectSheet by remember { mutableStateOf(false) }
    var showFastCommander by remember { mutableStateOf(false) }
    var inspectedGroup by remember { mutableStateOf<GroupAddressInfo?>(null) }
    var inspectedTelegram by remember { mutableStateOf<EnrichedTelegram?>(null) }
    var activeExplorerPane by remember { mutableStateOf(MobileExplorerPane.ROOMS) }

    LaunchedEffect(state.explorerSelection) {
        when (state.explorerSelection) {
            is ExplorerSelection.Area,
            is ExplorerSelection.Line,
            is ExplorerSelection.Device,
            is ExplorerSelection.TopologyRoot -> {
                activeExplorerPane = MobileExplorerPane.TOPOLOGY
            }
            is ExplorerSelection.GroupRange,
            is ExplorerSelection.AllGroupAddresses -> {
                activeExplorerPane = MobileExplorerPane.GROUPS
            }
            is ExplorerSelection.BuildingSpace -> {
                activeExplorerPane = MobileExplorerPane.ROOMS
            }
        }
    }

    MobileFieldKitTheme(themeMode = state.themeMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Official Material 3 Top App Bar
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProjectSheet = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = state.project?.name ?: "KNX Field Kit",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                MobileBadge(
                                    text = APP_VERSION,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            val proj = state.project
                            val secDevCount = remember(proj, state.keyring) {
                                proj?.allDevices?.values?.count { it.isSecure || state.keyring?.isDeviceSecure(it.address) == true } ?: 0
                            }
                            val plainDevCount = (proj?.allDevices?.size ?: 0) - secDevCount
                            Text(
                                text = if (proj != null) {
                                    if (secDevCount > 0) {
                                        "${proj.allDevices.size} dev ($secDevCount 🛡️ Sec • $plainDevCount Plain) • ${proj.groupAddresses.size} GA"
                                    } else {
                                        "${proj.allDevices.size} dev (All Plain) • ${proj.groupAddresses.size} GA"
                                    }
                                } else {
                                    "Tap to open .knxproj / .knxkeys"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 8.dp)
                                .size(32.dp)
                                .clip(EtsShapes.Badge)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "KNX",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    },
                    actions = {
                        // Connection Status (M3 AssistChip)
                        val isConnected = state.connectionState is ConnectionState.Connected
                        val statusDotColor = when {
                            state.isSimulatorActive || isConnected -> MaterialTheme.colorScheme.primary
                            state.connectionState is ConnectionState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        }
                        val connectedHost = if (isConnected) state.savedGatewayHost else null
                        val statusText = when {
                            state.isSimulatorActive -> "Sim Bus"
                            connectedHost != null -> "Live: $connectedHost"
                            isConnected -> "Live IP"
                            state.connectionState is ConnectionState.Connecting -> "Linking..."
                            else -> "Connect"
                        }
                        AssistChip(
                            onClick = { controller.toggleConnectDialog(true) },
                            shape = EtsShapes.Button,
                            label = { Text(statusText, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                            },
                            modifier = Modifier.height(32.dp),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )

                        state.keyring?.let { kr ->
                            Spacer(Modifier.width(2.dp))
                            Surface(
                                shape = EtsShapes.Badge,
                                color = Color(0xFF005596),
                                modifier = Modifier.clickable { showProjectSheet = true },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = "KNX Secure Keyring",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = "KEYRING (${kr.groupKeysHex.size + kr.deviceToolKeysHex.size})",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(2.dp))

                        // ETS Fast Commander (Schnell-Steuerung)
                        IconButton(onClick = { showFastCommander = true }) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Fast Commander (Schnell-Steuerung)",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // Theme Toggle
                        IconButton(onClick = controller::toggleThemeMode) {
                            val themeIcon = when (state.themeMode) {
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            }
                            Icon(
                                imageVector = themeIcon,
                                contentDescription = "Theme: ${state.themeMode.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Project / File Picker
                        IconButton(onClick = { showProjectSheet = true }) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = "Project Files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Active Walk-Test High-Visibility Strip
                if (state.walkTestActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "WALK-TEST BLINKING ${state.walkTestGroupAddress} (#${state.walkTestStepCount})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Button(
                            onClick = { controller.stopWalkTestBlink() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            shape = EtsShapes.Button,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(26.dp),
                        ) {
                            Text("STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Active Tab Screen
                Box(modifier = Modifier.weight(1f)) {
                    when (state.activeTab) {
                        AppTab.PROJECT -> MobileExplorerScreen(
                            state = state,
                            controller = controller,
                            onInspectGroupSheet = { inspectedGroup = it },
                            onOpenProjectFile = onOpenProjectFile,
                            activePane = activeExplorerPane,
                            onPaneChange = { activeExplorerPane = it },
                        )
                        AppTab.MONITOR -> MobileMonitorScreen(
                            state = state,
                            controller = controller,
                            onShareCsv = onShareMonitorCsv,
                            onInspectTelegram = { inspectedTelegram = it },
                        )
                        AppTab.SENDER -> MobileSenderScreen(
                            state = state,
                            controller = controller,
                        )
                        AppTab.DIAGNOSTICS -> MobileDiagnosticsScreen(
                            state = state,
                            controller = controller,
                            initialTab = initialDiagTab,
                            onShareHtmlReport = onShareHtmlReport,
                            onShareMarkdownSummary = onShareMarkdownSummary,
                        )
                    }
                }

                // Compact Live Status Strip
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = state.statusMessage,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${state.telegrams.size} frm",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Authentic ETS Material 3 Navigation Bar (5 Core ETS Pillars)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    data class NavItem(
                        val tab: AppTab,
                        val pane: MobileExplorerPane?,
                        val label: String,
                        val icon: androidx.compose.ui.graphics.vector.ImageVector,
                    )
                    val navItems = listOf(
                        NavItem(AppTab.PROJECT, MobileExplorerPane.ROOMS, "Buildings", Icons.Default.Apartment),
                        NavItem(AppTab.PROJECT, MobileExplorerPane.TOPOLOGY, "Topology", Icons.Default.AccountTree),
                        NavItem(AppTab.PROJECT, MobileExplorerPane.GROUPS, "Group Addr", Icons.Default.Folder),
                        NavItem(AppTab.MONITOR, null, "Monitor", Icons.Default.GraphicEq),
                        NavItem(AppTab.DIAGNOSTICS, null, "Diagnostics", Icons.Default.VerifiedUser),
                    )
                    navItems.forEach { item ->
                        val selected = when (item.tab) {
                            AppTab.PROJECT -> state.activeTab == AppTab.PROJECT && activeExplorerPane == item.pane
                            else -> state.activeTab == item.tab
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                controller.selectTab(item.tab)
                                if (item.pane != null) {
                                    activeExplorerPane = item.pane
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

            // Modal Bottom Sheets
            if (showProjectSheet) {
                MobileProjectMenuSheet(
                    state = state,
                    onOpenFile = onOpenProjectFile,
                    onOpenRecent = { controller.openProjectOrKeyringFile(it) },
                    onCloseProject = { controller.closeProject() },
                    onShareMarkdown = onShareMarkdownSummary,
                    onShareHtml = onShareHtmlReport,
                    onDismiss = { showProjectSheet = false },
                )
            }

            if (state.showConnectDialog) {
                MobileGatewayConnectSheet(
                    state = state,
                    controller = controller,
                    onDismiss = { controller.toggleConnectDialog(false) },
                )
            }

            state.passwordPrompt?.let { prompt ->
                MobilePasswordSheet(
                    prompt = prompt,
                    onUnlock = controller::submitFilePassword,
                    onDismiss = controller::dismissPasswordPrompt,
                )
            }

            inspectedGroup?.let { ga ->
                MobileGroupInspectorSheet(
                    ga = ga,
                    project = state.project,
                    liveEntry = state.groupLiveValues[ga.address],
                    controller = controller,
                    onDismiss = { inspectedGroup = null },
                )
            }

            inspectedTelegram?.let { tel ->
                MobileTelegramDetailSheet(
                    item = tel,
                    onReplay = { controller.replayTelegram(tel.telegram) },
                    onOpenSender = { ga ->
                        controller.selectGroupAddressForSender(ga)
                        controller.selectTab(AppTab.SENDER)
                    },
                    onLocateSender = { addr -> controller.locateDevice(addr) },
                    onDismiss = { inspectedTelegram = null },
                )
            }

            if (showFastCommander) {
                MobileFastCommanderSheet(
                    state = state,
                    controller = controller,
                    onDismiss = { showFastCommander = false },
                )
            }
        }
    }
}
