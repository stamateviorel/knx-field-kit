package dev.knxfieldkit.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.knxfieldkit.bus.CalimeroKnxBusClient
import dev.knxfieldkit.mobile.MobileFieldApp
import dev.knxfieldkit.model.ConnectionState
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

fun pickOpenFile(title: String): File? {
    val dlg = FileDialog(null as Frame?, title, FileDialog.LOAD).apply { isVisible = true }
    val dir = dlg.directory ?: return null
    val name = dlg.file ?: return null
    return File(dir, name)
}

fun pickSaveFile(title: String, defaultName: String): File? {
    val dlg = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
        file = defaultName
        isVisible = true
    }
    val dir = dlg.directory ?: return null
    val name = dlg.file ?: return null
    return File(dir, name)
}

@Composable
fun KnxFieldKitApp(
    controller: AppController,
    onOpenMobilePreview: (() -> Unit)? = null,
) {
    val state by controller.uiState.collectAsState()
    val isConnected = state.connectionState is ConnectionState.Connected
    val connectionLabel = when (val cs = state.connectionState) {
        is ConnectionState.Connected -> cs.description
        is ConnectionState.Connecting -> "Connecting..."
        is ConnectionState.Error -> "Bus Error"
        ConnectionState.Disconnected -> "Bus Disconnected"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = WorkbenchColors.BgCanvas) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top ETS6-Style IDE Workbench Bar (42dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(WorkbenchColors.BgPanel)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Brand Emblem + Title
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(WorkbenchColors.AccentEmerald.copy(alpha = 0.20f))
                            .border(1.dp, WorkbenchColors.AccentEmerald, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = WorkbenchColors.AccentEmerald,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "KNX FIELD KIT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WorkbenchColors.TextPrimary,
                        )
                        Text(
                            text = "ETS4/5/6 Unlocker · Bus Workbench",
                            fontSize = 9.sp,
                            color = WorkbenchColors.TextMuted,
                        )
                    }
                }

                Box(modifier = Modifier.width(1.dp).height(22.dp).background(WorkbenchColors.BorderSubtle))

                // Active Project Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(WorkbenchColors.BgCanvas)
                        .border(1.dp, WorkbenchColors.BorderSubtle, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = WorkbenchColors.AccentSky, modifier = Modifier.size(13.dp))
                    Text(
                        text = state.projectSourceLabel,
                        fontSize = 11.sp,
                        color = WorkbenchColors.TextSecondary,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 1-Click Full Site Health Check
                DesktopButton(
                    text = if (state.isRunningFullSiteCheck) "Auditing Site..." else "1-Click Site Check",
                    icon = Icons.Default.Verified,
                    onClick = {
                        controller.selectTab(AppTab.DIAGNOSTICS)
                        controller.runFullSiteCheck()
                    },
                    enabled = !state.isRunningFullSiteCheck,
                    active = state.siteCheckReport != null,
                    accentColor = WorkbenchColors.AccentEmerald,
                )

                // Connection Status Indicator Pill
                Row(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(WorkbenchColors.BgCanvas)
                        .border(
                            1.dp,
                            if (isConnected) WorkbenchColors.AccentEmerald.copy(alpha = 0.55f) else WorkbenchColors.BorderSubtle,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) WorkbenchColors.AccentEmerald else WorkbenchColors.AccentAmber),
                    )
                    Text(
                        text = connectionLabel,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isConnected) WorkbenchColors.AccentEmerald else WorkbenchColors.TextSecondary,
                        maxLines = 1,
                    )
                }

                if (isConnected) {
                    DesktopButton(
                        text = "Disconnect",
                        icon = Icons.Default.LinkOff,
                        onClick = controller::disconnectBus,
                    )
                } else {
                    DesktopPrimaryButton(
                        text = "Connect Gateway...",
                        icon = Icons.Default.Router,
                        onClick = { controller.toggleConnectDialog(true) },
                    )
                }
                DesktopButton(
                    text = "Gateways...",
                    icon = Icons.Default.SettingsEthernet,
                    onClick = { controller.toggleConnectDialog(true) },
                )
                DesktopButton(
                    text = state.themeMode.label,
                    icon = when (state.themeMode) {
                        ThemeMode.DARK -> Icons.Default.DarkMode
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    },
                    onClick = controller::toggleThemeMode,
                )
                if (onOpenMobilePreview != null) {
                    DesktopButton(
                        text = "Phone UI",
                        icon = Icons.Default.Smartphone,
                        onClick = onOpenMobilePreview,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle)

            // Walk-Test Banner (when active)
            if (state.walkTestActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(WorkbenchColors.AccentAmber.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = WorkbenchColors.AccentAmber, modifier = Modifier.size(14.dp))
                        Text(
                            text = "WALK-TEST BLINKER ACTIVE on ${state.walkTestGroupAddress} — Toggle #${state.walkTestStepCount} (walk to room to verify circuit)",
                            color = WorkbenchColors.AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    DesktopPrimaryButton(
                        text = "Stop Blinker",
                        onClick = controller::stopWalkTestBlink,
                        color = WorkbenchColors.AccentAmber,
                        compact = true,
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.AccentAmber.copy(alpha = 0.45f))
            }

            // Main Workspace Area: Slim Vector Activity Bar + Active Screen
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Slim 64dp IDE Activity Bar
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .fillMaxHeight()
                        .background(WorkbenchColors.BgPanel)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = state.activeTab == tab
                        val icon = when (tab) {
                            AppTab.PROJECT -> Icons.Default.AccountTree
                            AppTab.MONITOR -> Icons.Default.Terminal
                            AppTab.SENDER -> Icons.AutoMirrored.Filled.Send
                            AppTab.DIAGNOSTICS -> Icons.Default.BuildCircle
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clickable { controller.selectTab(tab) },
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .align(Alignment.CenterStart)
                                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                                        .background(WorkbenchColors.AccentSky),
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) WorkbenchColors.BgSelected.copy(alpha = 0.70f) else Color.Transparent),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tab.title,
                                    tint = if (selected) WorkbenchColors.AccentSky else WorkbenchColors.TextMuted,
                                    modifier = Modifier.size(19.dp),
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 9.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) WorkbenchColors.TextPrimary else WorkbenchColors.TextMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                VerticalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle)

                // Active Screen Content
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                    when (state.activeTab) {
                        AppTab.PROJECT -> ProjectExplorerScreen(
                            state = state,
                            controller = controller,
                            onPickFile = { pickOpenFile("Open .knxproj or .knxkeys")?.let(controller::openProjectOrKeyringFile) },
                            onExportReport = { pickSaveFile("Export Markdown Report", "knx-project-report.md")?.let(controller::exportProjectMarkdown) },
                        )
                        AppTab.MONITOR -> BusMonitorScreen(
                            state = state,
                            controller = controller,
                            onExportCsv = { pickSaveFile("Export Bus Traffic CSV", "knx-traffic.csv")?.let(controller::exportMonitorCsv) },
                        )
                        AppTab.SENDER -> TelegramSenderScreen(state = state, controller = controller)
                        AppTab.DIAGNOSTICS -> FieldDiagnosticsScreen(
                            state = state,
                            controller = controller,
                            onExportHtmlReport = {
                                pickSaveFile("Export HTML Commissioning Report", "knx-commissioning-report.html")
                                    ?.let(controller::exportHtmlCommissioningReport)
                            },
                        )
                    }
                }
            }

            // Compact 24dp IDE Bottom Status Bar
            HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(WorkbenchColors.BgHeader)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = WorkbenchColors.AccentSky, modifier = Modifier.size(12.dp))
                Text(
                    text = state.statusMessage,
                    fontSize = 11.sp,
                    color = WorkbenchColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                state.siteCheckReport?.let { rep ->
                    Text(
                        text = "Readiness: ${rep.readinessScorePercent}%",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = WorkbenchColors.AccentEmerald,
                    )
                }
                Text(
                    text = "Frames: ${state.telegrams.size}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = WorkbenchColors.TextSecondary,
                )
            }
        }
    }

    state.passwordPrompt?.let {
        PasswordUnlockDialog(it, controller::submitFilePassword, controller::dismissPasswordPrompt)
    }
    if (state.showConnectDialog) {
        GatewayConnectDialog(state, controller)
    }
}

fun main(args: Array<String>) = application {
    val controller = remember { AppController(hardwareClientFactory = { CalimeroKnxBusClient(it) }) }
    var showMobileWindow by remember { mutableStateOf("--mobile-preview" in args) }
    LaunchedEffect(Unit) {
        args.firstOrNull { !it.startsWith("--") }?.let { path ->
            val target = File(path)
            if (target.isFile) controller.openProjectOrKeyringFile(target)
        }
    }
    DisposableEffect(Unit) { onDispose { controller.close() } }
    Window(
        onCloseRequest = ::exitApplication,
        title = "KNX Field Kit — ETS4/5/6 Project Explorer & Bus Workbench",
        state = rememberWindowState(width = 1480.dp, height = 920.dp),
    ) {
        DisposableEffect(window) {
            val dropTarget = DropTarget(window, object : DropTargetAdapter() {
                override fun drop(dtde: DropTargetDropEvent) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                        val knxFile = files?.filterIsInstance<File>()?.firstOrNull {
                            it.extension.equals("knxproj", ignoreCase = true) ||
                                it.extension.equals("knxkeys", ignoreCase = true)
                        }
                        if (knxFile != null) {
                            controller.openProjectOrKeyringFile(knxFile)
                            dtde.dropComplete(true)
                        } else {
                            dtde.dropComplete(false)
                        }
                    } catch (_: Exception) {
                        dtde.dropComplete(false)
                    }
                }
            })
            onDispose { dropTarget.isActive = false }
        }
        val uiState by controller.uiState.collectAsState()
        KnxFieldKitTheme(themeMode = uiState.themeMode) {
            KnxFieldKitApp(
                controller = controller,
                onOpenMobilePreview = { showMobileWindow = true },
            )
        }
    }
    if (showMobileWindow) {
        Window(
            onCloseRequest = { showMobileWindow = false },
            title = "KNX Field Kit — Android Mobile Companion (412×915)",
            state = rememberWindowState(width = 412.dp, height = 915.dp),
        ) {
            MobileFieldApp(
                controller = controller,
                onOpenProjectFile = {
                    pickOpenFile("Open .knxproj or .knxkeys")?.let(controller::openProjectOrKeyringFile)
                },
                onShareHtmlReport = {
                    pickSaveFile("Export HTML Commissioning Report", "knx-commissioning-report.html")
                        ?.let { controller.exportHtmlCommissioningReport(it) }
                },
                onShareMonitorCsv = {
                    pickSaveFile("Export Bus Monitor CSV", "knx-bus-monitor.csv")
                        ?.let { controller.exportMonitorCsv(it) }
                },
                onShareMarkdownSummary = {
                    pickSaveFile("Export Project Summary Markdown", "knx-project-summary.md")
                        ?.let { controller.exportProjectMarkdown(it) }
                },
            )
        }
    }
}
