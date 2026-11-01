package dev.knxfieldkit.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
fun KnxFieldKitApp(controller: AppController) {
    val state by controller.uiState.collectAsState()
    val connectionStatus = when (val cs = state.connectionState) {
        is ConnectionState.Connected -> "ONLINE: ${cs.description}"
        is ConnectionState.Connecting -> "CONNECTING..."
        is ConnectionState.Error -> "ERROR: ${cs.message}"
        ConnectionState.Disconnected -> "OFFLINE"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text("KNX Field Kit", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Drop any .knxproj / .knxkeys onto window • ETS4/5/6 Unlocker • Live Bus & Commissioning",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            controller.selectTab(AppTab.DIAGNOSTICS)
                            controller.runFullSiteCheck()
                        },
                        enabled = !state.isRunningFullSiteCheck,
                    ) {
                        Text(if (state.isRunningFullSiteCheck) "Checking Site..." else "1-Click Site Check")
                    }
                    Text(
                        text = connectionStatus,
                        color = if (state.connectionState is ConnectionState.Connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.connectionState is ConnectionState.Connected) {
                        OutlinedButton(onClick = controller::disconnectBus) { Text("Disconnect") }
                    } else {
                        Button(onClick = controller::connectSimulator) { Text("Quick Start Virtual Bus") }
                    }
                    OutlinedButton(onClick = { controller.toggleConnectDialog(true) }) {
                        Text("Gateways / IP Connect...")
                    }
                }
            }

            if (state.walkTestActive) {
                Surface(color = MaterialTheme.colorScheme.tertiary) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "WALK-TEST CIRCUIT BLINKER ACTIVE on ${state.walkTestGroupAddress} — Toggle #${state.walkTestStepCount} (walk to room to locate circuit)",
                            color = MaterialTheme.colorScheme.background,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Button(onClick = controller::stopWalkTestBlink) {
                            Text("Stop Walk-Test")
                        }
                    }
                }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                NavigationRail {
                    AppTab.entries.forEach { tab ->
                        val shortBadge = when (tab) {
                            AppTab.PROJECT -> "PRJ"
                            AppTab.MONITOR -> "MON"
                            AppTab.SENDER -> "SND"
                            AppTab.DIAGNOSTICS -> "TST"
                        }
                        NavigationRailItem(
                            selected = state.activeTab == tab,
                            onClick = { controller.selectTab(tab) },
                            icon = { Text(shortBadge, fontFamily = FontFamily.Monospace) },
                            label = { Text(tab.title) },
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
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

            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.statusMessage, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Telegrams: ${state.telegrams.size}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
    val controller = remember { AppController() }
    LaunchedEffect(Unit) {
        if ("--connect-sim" in args) {
            controller.connectSimulator()
        }
        if ("--prompt-ets6-sample" in args) {
            val sampleFile = File("samples", "penthouse_ets6_encrypted.knxproj")
            controller.generateAndPromptEncryptedSample(sampleFile, samplePassword = "knx2026")
        }
        args.firstOrNull { !it.startsWith("--") }?.let { path ->
            val target = File(path)
            if (target.isFile) controller.openProjectOrKeyringFile(target)
        }
    }
    DisposableEffect(Unit) { onDispose { controller.close() } }
    Window(
        onCloseRequest = ::exitApplication,
        title = "KNX Field Kit — Project & Bus Diagnostics",
        state = rememberWindowState(width = 1420.dp, height = 880.dp),
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
        KnxFieldKitTheme {
            KnxFieldKitApp(controller)
        }
    }
}
