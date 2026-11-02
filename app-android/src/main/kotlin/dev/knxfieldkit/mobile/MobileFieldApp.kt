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
import dev.knxfieldkit.app.ThemeMode
import dev.knxfieldkit.model.ConnectionState
import dev.knxfieldkit.model.EnrichedTelegram
import dev.knxfieldkit.model.GroupAddressInfo

@Composable
fun MobileFieldApp(
    controller: AppController,
    modifier: Modifier = Modifier,
    onOpenProjectFile: () -> Unit = {},
    onShareHtmlReport: () -> Unit = {},
    onShareMonitorCsv: () -> Unit = {},
    onShareMarkdownSummary: () -> Unit = {},
) {
    val state by controller.uiState.collectAsState()
    var showProjectSheet by remember { mutableStateOf(false) }
    var inspectedGroup by remember { mutableStateOf<GroupAddressInfo?>(null) }
    var inspectedTelegram by remember { mutableStateOf<EnrichedTelegram?>(null) }

    MobileFieldKitTheme(themeMode = state.themeMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MobileColors.BgCanvas),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Compact Industrial Mobile Top Header
                Surface(
                    color = MobileColors.BgTopBar,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Emerald KNX Mark
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MobileColors.AccentEmerald.copy(alpha = 0.2f))
                                    .border(1.dp, MobileColors.AccentEmerald, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "KNX",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MobileColors.AccentEmerald,
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showProjectSheet = true },
                            ) {
                                Text(
                                    text = state.project?.name ?: "KNX Field Kit",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MobileColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val proj = state.project
                                Text(
                                    text = if (proj != null) {
                                        "${proj.allDevices.size} Dev • ${proj.groupAddresses.size} GA • ${proj.etsVersion.ifBlank { "ETS" }}"
                                    } else {
                                        "Tap to open .knxproj / .knxkeys"
                                    },
                                    fontSize = 10.sp,
                                    color = MobileColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // Live Bus Connection Pill
                            val isConnected = state.connectionState is ConnectionState.Connected
                            val connColor = when {
                                state.isSimulatorActive -> MobileColors.AccentEmerald
                                isConnected -> MobileColors.AccentSky
                                state.connectionState is ConnectionState.Error -> MobileColors.AccentRed
                                else -> MobileColors.TextSecondary
                            }
                            val connLabel = when {
                                state.isSimulatorActive -> "Sim Bus"
                                isConnected -> "IP Live"
                                state.connectionState is ConnectionState.Connecting -> "Linking"
                                else -> "Connect"
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(connColor.copy(alpha = 0.16f))
                                    .border(1.dp, connColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .clickable { controller.toggleConnectDialog(true) }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(connColor),
                                )
                                Text(
                                    text = connLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = connColor,
                                )
                            }

                            // Theme Switcher Button (Dark / Light / Auto)
                            IconButton(
                                onClick = controller::toggleThemeMode,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MobileColors.BgCardElevated),
                            ) {
                                val themeIcon = when (state.themeMode) {
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                }
                                Icon(
                                    imageVector = themeIcon,
                                    contentDescription = "Theme: ${state.themeMode.label}",
                                    tint = MobileColors.AccentAmber,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            // Project / File Picker Menu Icon
                            IconButton(
                                onClick = { showProjectSheet = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MobileColors.BgCardElevated),
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = "Project & Files",
                                    tint = MobileColors.AccentSky,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = MobileColors.BorderSubtle)
                    }
                }

                // Active Walk-Test High-Visibility Strip
                if (state.walkTestActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MobileColors.AccentAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "WALK-TEST BLINKING ${state.walkTestGroupAddress} (#${state.walkTestStepCount})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobileColors.AccentAmber,
                        )
                        MobileActionPill("STOP", MobileColors.AccentRed) {
                            controller.stopWalkTestBlink()
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
                            onShareHtmlReport = onShareHtmlReport,
                        )
                    }
                }

                // Compact Live Status Strip
                HorizontalDivider(color = MobileColors.BorderSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MobileColors.BgTopBar)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MobileColors.AccentEmerald,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = state.statusMessage,
                        fontSize = 10.sp,
                        color = MobileColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${state.telegrams.size} frm",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MobileColors.AccentSky,
                    )
                }

                // Thumb-Friendly Bottom Navigation Bar
                HorizontalDivider(color = MobileColors.BorderSubtle)
                NavigationBar(
                    containerColor = MobileColors.BgTopBar,
                    tonalElevation = 0.dp,
                ) {
                    val navItems = listOf(
                        Triple(AppTab.PROJECT, "Explorer", Icons.Default.AccountTree),
                        Triple(AppTab.MONITOR, "Monitor", Icons.Default.GraphicEq),
                        Triple(AppTab.SENDER, "Send", Icons.Default.Tune),
                        Triple(AppTab.DIAGNOSTICS, "Field Tests", Icons.Default.VerifiedUser),
                    )
                    navItems.forEach { (tab, label, icon) ->
                        val selected = state.activeTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { controller.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MobileColors.AccentEmerald,
                                selectedTextColor = MobileColors.AccentEmerald,
                                indicatorColor = MobileColors.AccentEmerald.copy(alpha = 0.16f),
                                unselectedIconColor = MobileColors.TextSecondary,
                                unselectedTextColor = MobileColors.TextSecondary,
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
                    onDismiss = { inspectedTelegram = null },
                )
            }
        }
    }
}
