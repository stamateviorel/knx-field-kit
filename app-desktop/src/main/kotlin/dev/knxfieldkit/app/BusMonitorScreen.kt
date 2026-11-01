package dev.knxfieldkit.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 1. Sleek 4-Tile Bus Health & TP1 Telemetry Strip
        Row(modifier = Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val loadColor = when (health.loadLevel) {
                BusLoadLevel.IDLE -> WorkbenchColors.AccentSky
                BusLoadLevel.HEALTHY -> WorkbenchColors.AccentEmerald
                BusLoadLevel.ELEVATED -> WorkbenchColors.AccentAmber
                BusLoadLevel.STORM -> WorkbenchColors.AccentRed
            }
            KpiStatTile(
                label = "TP1 BUS RATE & LOAD",
                value = String.format(Locale.US, "%.1f tel/s · %.0f%% TP1 (%s)", health.telegramsPerSecond, health.estimatedTp1LoadPercent, health.loadLevel.label),
                valueColor = loadColor,
                modifier = Modifier.weight(1f),
            )
            val topDev = health.topSourceDevices.firstOrNull()
            KpiStatTile(
                label = "TOP TALKER DEVICE",
                value = topDev?.let { "${it.address} (${it.label}) ×${it.telegramCount}" } ?: "—",
                valueColor = WorkbenchColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            KpiStatTile(
                label = "UNANSWERED GROUP READS",
                value = if (health.unansweredReadAddresses.isEmpty()) "0 (All Answered)" else "${health.unansweredReadAddresses.size}: ${health.unansweredReadAddresses.take(3).joinToString()}",
                valueColor = if (health.unansweredReadAddresses.isEmpty()) WorkbenchColors.AccentEmerald else WorkbenchColors.AccentRed,
                modifier = Modifier.weight(1f),
            )
            KpiStatTile(
                label = "GHOST GAS & REPEATED FRAMES",
                value = "${health.ghostGroupCount} Ghost · ${health.repeatedCount} Repeat",
                valueColor = if (health.ghostGroupCount > 0 || health.repeatedCount > 0) WorkbenchColors.AccentAmber else WorkbenchColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
        }

        // 2. Main Split: Live CEMI Stream Table + Right Frame Inspector
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WorkbenchPanel(
                title = "Live KNX Bus & Group Monitor",
                icon = Icons.Default.Terminal,
                badgeText = "${filteredTelegrams.size} / ${state.telegrams.size}",
                modifier = Modifier.weight(if (selectedTelegram != null) 0.68f else 1f).fillMaxHeight(),
                headerActions = {
                    CompactSearchField(
                        value = state.monitorSearchQuery,
                        onValueChange = controller::setMonitorSearch,
                        placeholder = "Filter source, GA, name, APCI, value...",
                        modifier = Modifier.width(240.dp),
                    )
                    DesktopButton(
                        text = "Ghost GAs (${health.ghostGroupCount})",
                        icon = Icons.Default.WarningAmber,
                        onClick = { controller.setMonitorGhostOnly(!state.monitorGhostOnly) },
                        active = state.monitorGhostOnly,
                        accentColor = WorkbenchColors.AccentAmber,
                        compact = true,
                    )
                    DesktopButton(
                        text = if (state.monitorPaused) "Resume" else "Pause",
                        icon = if (state.monitorPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        onClick = { controller.setMonitorPaused(!state.monitorPaused) },
                        active = state.monitorPaused,
                        compact = true,
                    )
                    DesktopButton(
                        text = "Clear",
                        icon = Icons.Default.DeleteSweep,
                        onClick = controller::clearMonitorLog,
                        compact = true,
                    )
                    DesktopPrimaryButton(
                        text = "Export CSV",
                        icon = Icons.Default.Download,
                        onClick = onExportCsv,
                        enabled = state.telegrams.isNotEmpty(),
                        color = WorkbenchColors.AccentSky,
                        compact = true,
                    )
                },
            ) {
                DenseTableHeader(
                    listOf(
                        "Timestamp" to 0.12f,
                        "Source Device" to 0.24f,
                        "Destination" to 0.27f,
                        "Service" to 0.11f,
                        "DPT" to 0.08f,
                        "Decoded Value & Flags" to 0.18f,
                    )
                )
                if (filteredTelegrams.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No KNX telegrams captured yet.", fontSize = 12.sp, color = WorkbenchColors.TextSecondary)
                            if (state.connectionState !is ConnectionState.Connected) {
                                DesktopPrimaryButton(
                                    text = "Start Virtual KNX Bus Simulator",
                                    icon = Icons.Default.Bolt,
                                    onClick = controller::connectSimulator,
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredTelegrams, key = { _, r -> r.telegram.id }) { idx, row ->
                            val t = row.telegram
                            val dstAddr = t.destinationGroup?.toString() ?: t.destinationIndividual?.toString() ?: "broadcast"
                            val isSelected = state.selectedMonitorTelegramId == t.id
                            val (apciText, apciColor) = apciBadgeStyle(t.apci)
                            val rowBg = when {
                                isSelected -> WorkbenchColors.BgSelected
                                idx % 2 == 1 -> WorkbenchColors.BgRowAlt
                                else -> WorkbenchColors.BgPanel
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(rowBg)
                                    .clickable { controller.selectMonitorTelegram(if (isSelected) null else t.id) }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = MONITOR_TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs)),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = WorkbenchColors.TextMuted,
                                    modifier = Modifier.weight(0.12f),
                                )
                                Text(
                                    text = if (!row.sourceDeviceName.isNullOrBlank()) "${t.source} · ${row.sourceDeviceName}" else t.source.toString(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = WorkbenchColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.24f).padding(end = 6.dp),
                                )
                                Text(
                                    text = if (!row.destinationName.isNullOrBlank()) "$dstAddr · ${row.destinationName}" else dstAddr,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (row.isUnmappedGroup) WorkbenchColors.AccentAmber else WorkbenchColors.AccentSky,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.27f).padding(end = 6.dp),
                                )
                                Box(modifier = Modifier.weight(0.11f)) {
                                    StatusPill(apciText, apciColor)
                                }
                                Box(modifier = Modifier.weight(0.08f)) {
                                    DptBadge(row.dptId)
                                }
                                Row(
                                    modifier = Modifier.weight(0.18f),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = row.decodedValue.ifBlank { "—" },
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WorkbenchColors.AccentEmerald,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (row.isUnmappedGroup) {
                                        StatusPill("GHOST", WorkbenchColors.AccentAmber)
                                    }
                                    if (t.repeated) {
                                        StatusPill("RPT", WorkbenchColors.AccentRed)
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle.copy(alpha = 0.35f))
                        }
                    }
                }
            }

            // Right CEMI Frame Inspector & 1-Click Replay Panel
            if (selectedTelegram != null) {
                val t = selectedTelegram.telegram
                val dstGroup = t.destinationGroup
                val srcDev = state.project?.findDevice(t.source)
                val gaInfo = dstGroup?.let { state.project?.findGroupAddress(it) }

                WorkbenchPanel(
                    title = "Frame #${t.id} Inspector",
                    icon = Icons.Default.DataObject,
                    modifier = Modifier.weight(0.32f).fillMaxHeight(),
                    headerActions = {
                        DesktopButton("Close", onClick = { controller.selectMonitorTelegram(null) }, compact = true)
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PropertyKeyValueRow("Timestamp", MONITOR_TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs)), monospace = true)
                        PropertyKeyValueRow("APCI Service", "${t.apci.label} (${t.priority}, Hop=${t.hopCount})", monospace = true)
                        PropertyKeyValueRow("Source IA", "${t.source} — ${srcDev?.name ?: "Unknown Device"}", WorkbenchColors.AccentSky)
                        srcDev?.let {
                            PropertyKeyValueRow("Location", state.project?.findDeviceLocationPath(it.address) ?: "Unassigned")
                            PropertyKeyValueRow("Hardware", "${it.manufacturerName} ${it.productName}")
                        }
                        if (dstGroup != null) {
                            PropertyKeyValueRow("Group Addr", "$dstGroup — ${gaInfo?.name ?: "Unmapped Ghost GA"}", WorkbenchColors.AccentEmerald)
                            PropertyKeyValueRow("Decoded (${selectedTelegram.dptId ?: "raw"})", selectedTelegram.decodedValue.ifBlank { "—" }, WorkbenchColors.AccentEmerald, monospace = true)
                        } else if (t.destinationIndividual != null) {
                            PropertyKeyValueRow("Target IA", t.destinationIndividual.toString(), monospace = true)
                        }
                        PropertyKeyValueRow("Payload Hex", HexBytes.toHex(t.payload).ifBlank { "(empty)" }, monospace = true)
                        if (t.rawFrameHex.isNotBlank()) {
                            PropertyKeyValueRow("Raw CEMI", t.rawFrameHex, monospace = true)
                        }

                        HorizontalDivider(color = WorkbenchColors.BorderSubtle)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (dstGroup != null) {
                                DesktopPrimaryButton(
                                    text = "Replay Frame",
                                    icon = Icons.Default.Replay,
                                    onClick = { controller.replayTelegram(t) },
                                    compact = true,
                                )
                                DesktopButton(
                                    text = "Poll GA",
                                    icon = Icons.Default.Sync,
                                    onClick = { controller.readGroupAddress(dstGroup.toString()) },
                                    compact = true,
                                )
                                DesktopButton(
                                    text = "In Explorer",
                                    onClick = {
                                        controller.inspectGroupInExplorer(dstGroup)
                                        controller.selectExplorerNode(ExplorerSelection.AllGroupAddresses)
                                        controller.selectTab(AppTab.PROJECT)
                                    },
                                    compact = true,
                                )
                            }
                            DesktopButton(
                                text = "Ping ${t.source}",
                                onClick = { controller.pingIndividualDevice(t.source.toString()) },
                                compact = true,
                            )
                        }

                        if (gaInfo != null && gaInfo.linkedObjects.isNotEmpty()) {
                            HorizontalDivider(color = WorkbenchColors.BorderSubtle)
                            Text(
                                text = "LINKED COMOBJECTS ON ${gaInfo.address} (${gaInfo.linkedObjects.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WorkbenchColors.TextMuted,
                            )
                            gaInfo.linkedObjects.forEach { link ->
                                val devName = link.deviceAddress?.let { state.project?.findDevice(it)?.name }.orEmpty()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(WorkbenchColors.BgCanvas, RoundedCornerShape(4.dp))
                                        .clickable {
                                            link.deviceAddress?.let {
                                                controller.openDeviceObjectsInExplorer(it)
                                                controller.selectTab(AppTab.PROJECT)
                                            }
                                        }
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${link.deviceAddress ?: "?"} · $devName", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WorkbenchColors.AccentSky, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("#${link.objectNumber ?: 0} ${link.name}", fontSize = 10.sp, color = WorkbenchColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    ComFlagsBadges(link.flags)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = WorkbenchColors.BgPanel,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, WorkbenchColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WorkbenchColors.TextMuted, maxLines = 1)
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun apciBadgeStyle(apci: ApciType): Pair<String, Color> = when (apci) {
    ApciType.GROUP_WRITE -> "WRITE" to WorkbenchColors.AccentEmerald
    ApciType.GROUP_READ -> "READ?" to WorkbenchColors.AccentSky
    ApciType.GROUP_RESPONSE -> "RESP" to WorkbenchColors.AccentTeal
    ApciType.DEVICE_DESCRIPTOR_READ -> "DD0?" to WorkbenchColors.AccentPurple
    ApciType.DEVICE_DESCRIPTOR_RESPONSE -> "DD0!" to WorkbenchColors.AccentPurple
    ApciType.INDIVIDUAL_ADDR_READ, ApciType.INDIVIDUAL_ADDR_RESPONSE -> "PROG" to WorkbenchColors.AccentAmber
    ApciType.OTHER -> "MGMT" to WorkbenchColors.TextSecondary
}
