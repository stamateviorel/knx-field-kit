package dev.knxfieldkit.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.knxfieldkit.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONITOR_TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

@Composable
fun BusMonitorScreen(state: AppUiState, controller: AppController, onExportCsv: () -> Unit) {
    val health = remember(state.telegrams) {
        BusHealthAnalyzer.analyze(state.telegrams)
    }

    val filteredTelegrams = remember(state.telegrams, state.monitorGhostOnly, state.monitorSearchQuery) {
        val q = state.monitorSearchQuery.trim().lowercase()
        state.telegrams.filter { row ->
            if (state.monitorGhostOnly && !row.isUnmappedGroup) return@filter false
            if (q.isEmpty()) return@filter true
            val t = row.telegram
            t.source.toString().lowercase().contains(q) ||
                (row.sourceDeviceName?.lowercase()?.contains(q) == true) ||
                (t.destinationGroup?.toString()?.lowercase()?.contains(q) == true) ||
                (t.destinationIndividual?.toString()?.lowercase()?.contains(q) == true) ||
                (row.destinationName?.lowercase()?.contains(q) == true) ||
                row.decodedValue.lowercase().contains(q) ||
                t.apci.label.lowercase().contains(q)
        }
    }

    val selectedTelegram = remember(state.telegrams, state.selectedMonitorTelegramId) {
        state.selectedMonitorTelegramId?.let { id -> state.telegrams.firstOrNull { it.telegram.id == id } }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Live Bus Health & TP1 Load Strip
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val loadColor = when (health.loadLevel) {
                    BusLoadLevel.IDLE -> MaterialTheme.colorScheme.secondary
                    BusLoadLevel.HEALTHY -> Color(0xFF2ECC71)
                    BusLoadLevel.ELEVATED -> Color(0xFFF59E0B)
                    BusLoadLevel.STORM -> Color(0xFFEF4444)
                }
                Column {
                    Text("TP1 Bus Rate & Load", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = String.format(Locale.US, "%.1f tel/s • %.0f%% TP1 (%s)", health.telegramsPerSecond, health.estimatedTp1LoadPercent, health.loadLevel.label),
                        color = loadColor,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column {
                    Text("Top Talker Device", style = MaterialTheme.typography.labelSmall)
                    val topDev = health.topSourceDevices.firstOrNull()
                    Text(
                        text = topDev?.let { "${it.address} (${it.label}) ×${it.telegramCount}" } ?: "—",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column {
                    Text("Unanswered Reads", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = if (health.unansweredReadAddresses.isEmpty()) "0 (All OK)" else "${health.unansweredReadAddresses.size}: ${health.unansweredReadAddresses.take(3).joinToString()}",
                        color = if (health.unansweredReadAddresses.isEmpty()) Color(0xFF2ECC71) else Color(0xFFEF4444),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column {
                    Text("Ghost GAs / Repeats", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "${health.ghostGroupCount} Ghost • ${health.repeatedCount} Repeat",
                        color = if (health.ghostGroupCount > 0 || health.repeatedCount > 0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Search & Filter Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.monitorSearchQuery,
                onValueChange = controller::setMonitorSearch,
                placeholder = { Text("Filter by address (1/1/1 or 1.1.10), device, GA name, APCI, or decoded value...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = state.monitorGhostOnly,
                onClick = { controller.setMonitorGhostOnly(!state.monitorGhostOnly) },
                label = { Text("Ghost GAs (${health.ghostGroupCount})") },
            )
            OutlinedButton(onClick = { controller.setMonitorPaused(!state.monitorPaused) }) {
                Text(if (state.monitorPaused) "Resume" else "Pause")
            }
            OutlinedButton(onClick = controller::clearMonitorLog) {
                Text("Clear")
            }
            Button(onClick = onExportCsv, enabled = state.telegrams.isNotEmpty()) {
                Text("Export CSV")
            }
        }

        // Split Table + Click-to-Inspect Telegram Drawer
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(modifier = Modifier.weight(if (selectedTelegram != null) 0.68f else 1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("Time", modifier = Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium)
                        Text("Source", modifier = Modifier.weight(0.24f), style = MaterialTheme.typography.labelMedium)
                        Text("Destination", modifier = Modifier.weight(0.28f), style = MaterialTheme.typography.labelMedium)
                        Text("Service", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium)
                        Text("DPT", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
                        Text("Decoded / Flags (Click row to inspect)", modifier = Modifier.weight(0.24f), style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider()
                    if (filteredTelegrams.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("No telegrams captured yet.")
                                if (state.connectionState !is ConnectionState.Connected) {
                                    Button(onClick = controller::connectSimulator) {
                                        Text("Start Virtual KNX Bus Simulator")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(filteredTelegrams, key = { it.telegram.id }) { row ->
                                val t = row.telegram
                                val dstAddr = t.destinationGroup?.toString() ?: t.destinationIndividual?.toString() ?: "broadcast"
                                val isSelected = state.selectedMonitorTelegramId == t.id
                                val apciBadge = when (t.apci) {
                                    ApciType.GROUP_WRITE -> "WRITE" to Color(0xFF2ECC71)
                                    ApciType.GROUP_READ -> "READ?" to Color(0xFF38BDF8)
                                    ApciType.GROUP_RESPONSE -> "RESP" to Color(0xFF14B8A6)
                                    ApciType.DEVICE_DESCRIPTOR_READ -> "DD0?" to Color(0xFFA78BFA)
                                    ApciType.DEVICE_DESCRIPTOR_RESPONSE -> "DD0!" to Color(0xFFC084FC)
                                    ApciType.INDIVIDUAL_ADDR_READ, ApciType.INDIVIDUAL_ADDR_RESPONSE -> "PROG" to Color(0xFFF59E0B)
                                    ApciType.OTHER -> "MGMT" to Color(0xFF94A3B8)
                                }
                                Surface(
                                    onClick = { controller.selectMonitorTelegram(if (isSelected) null else t.id) },
                                    color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                    shape = MaterialTheme.shapes.extraSmall,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = MONITOR_TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs)),
                                            modifier = Modifier.width(96.dp),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = if (!row.sourceDeviceName.isNullOrBlank()) "${t.source} (${row.sourceDeviceName})" else t.source.toString(),
                                            modifier = Modifier.weight(0.24f),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = if (!row.destinationName.isNullOrBlank()) "$dstAddr (${row.destinationName})" else dstAddr,
                                            modifier = Modifier.weight(0.28f),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = apciBadge.first,
                                            color = apciBadge.second,
                                            modifier = Modifier.width(120.dp),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Text(
                                            text = row.dptId ?: "-",
                                            modifier = Modifier.width(56.dp),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Row(
                                            modifier = Modifier.weight(0.24f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = row.decodedValue.ifBlank { "—" },
                                                color = MaterialTheme.colorScheme.primary,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (row.isUnmappedGroup) {
                                                Text(
                                                    text = "[GHOST]",
                                                    color = Color(0xFFF59E0B),
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                            if (t.repeated) {
                                                Text(
                                                    text = "[RPT]",
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right-Hand Telegram Inspector & 1-Click Replay Panel
            if (selectedTelegram != null) {
                val t = selectedTelegram.telegram
                val dstGroup = t.destinationGroup
                val srcDev = state.project?.findDevice(t.source)
                val gaInfo = dstGroup?.let { state.project?.findGroupAddress(it) }
                Card(modifier = Modifier.weight(0.32f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Telegram #${t.id}", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { controller.selectMonitorTelegram(null) }) { Text("Close") }
                        }
                        HorizontalDivider()
                        Text("Service: ${t.apci.label} (${t.priority}, Hop=${t.hopCount})", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("Source: ${t.source} — ${srcDev?.name ?: "Unknown Device"}", style = MaterialTheme.typography.bodyMedium)
                        srcDev?.let {
                            val loc = state.project?.findDeviceLocationPath(it.address) ?: "Unassigned Room"
                            Text("Room: $loc • ${it.manufacturerName} ${it.productName}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (dstGroup != null) {
                            Text("Destination GA: $dstGroup — ${gaInfo?.name ?: "Unmapped Ghost GA"}", style = MaterialTheme.typography.bodyMedium)
                            Text("Decoded (${selectedTelegram.dptId ?: "raw"}): ${selectedTelegram.decodedValue.ifBlank { "—" }}", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                        } else if (t.destinationIndividual != null) {
                            Text("Target Device: ${t.destinationIndividual}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Payload Hex: ${HexBytes.toHex(t.payload).ifBlank { "(empty)" }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        if (t.rawFrameHex.isNotBlank()) {
                            Text("Raw CEMI: ${t.rawFrameHex}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }

                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (dstGroup != null) {
                                Button(onClick = { controller.replayTelegram(t) }) {
                                    Text("Replay Telegram")
                                }
                                OutlinedButton(onClick = { controller.readGroupAddress(dstGroup.toString()) }) {
                                    Text("Poll GA")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { controller.pingIndividualDevice(t.source.toString()) }) {
                                Text("Ping ${t.source}")
                            }
                            if (dstGroup != null) {
                                TextButton(onClick = {
                                    controller.inspectGroupInExplorer(dstGroup)
                                    controller.selectTab(AppTab.PROJECT)
                                }) {
                                    Text("Show in Project")
                                }
                            }
                        }

                        if (gaInfo != null && gaInfo.linkedObjects.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Linked Devices on ${gaInfo.address}:", style = MaterialTheme.typography.labelLarge)
                            gaInfo.linkedObjects.forEach { link ->
                                val devName = link.deviceAddress?.let { state.project?.findDevice(it)?.name } ?: ""
                                Text(
                                    text = "• ${link.deviceAddress ?: "?"} $devName — ${link.name}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
