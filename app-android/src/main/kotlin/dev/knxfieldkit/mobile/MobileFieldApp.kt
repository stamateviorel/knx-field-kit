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
                // Official Material 3 Top App Bar
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProjectSheet = true }
                        ) {
                            Text(
                                text = state.project?.name ?: "KNX Field Kit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val proj = state.project
                            Text(
                                text = if (proj != null) {
                                    "${proj.allDevices.size} dev • ${proj.groupAddresses.size} GA • ${proj.etsVersion.ifBlank { "ETS" }}"
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
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                        val statusText = when {
                            state.isSimulatorActive -> "Sim"
                            isConnected -> "Live IP"
                            state.connectionState is ConnectionState.Connecting -> "Linking"
                            else -> "Connect"
                        }
                        AssistChip(
                            onClick = { controller.toggleConnectDialog(true) },
                            label = { Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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

                        Spacer(Modifier.width(2.dp))

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
                            onOpenProjectFile = onOpenProjectFile,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Authentic Android Material 3 Navigation Bar
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
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
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
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
