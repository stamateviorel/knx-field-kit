package dev.knxfieldkit.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DOCK_TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

@Composable
fun ExplorerRightInspectorPane(
    state: AppUiState,
    controller: AppController,
    modifier: Modifier = Modifier,
) {
    val inspectedGa = state.selectedInspectorGroup
    val gaInfo = inspectedGa?.let { state.project?.findGroupAddress(it) }

    WorkbenchPanel(
        title = if (gaInfo != null) "Group Properties" else "Device Properties",
        icon = Icons.Default.Tune,
        badgeText = gaInfo?.address?.toString() ?: state.selectedDeviceAddress?.toString(),
        modifier = modifier,
        headerActions = {
            if (gaInfo != null) {
                DesktopButton(
                    text = "Device",
                    onClick = { state.selectedDeviceAddress?.let(controller::inspectDeviceInExplorer) },
                    compact = true,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (gaInfo != null && inspectedGa != null) {
                val live = state.groupLiveValues[inspectedGa]
                val hasKey = state.keyring?.groupKeysHex?.containsKey(inspectedGa) == true
                PropertyKeyValueRow("Group Addr", inspectedGa.toString(), WorkbenchColors.AccentEmerald, monospace = true)
                PropertyKeyValueRow("Name", gaInfo.name)
                PropertyKeyValueRow("Description", gaInfo.description)
                PropertyKeyValueRow("Datapoint", "${gaInfo.dpt ?: "raw"} (${DptCodec.resolveMainDpt(gaInfo.dpt).label})", monospace = true)
                PropertyKeyValueRow(
                    key = "Live Value",
                    value = live?.let { "${it.valueText} ${it.latencyMs?.let { ms -> "(${ms}ms)" }.orEmpty()}" } ?: "Not polled",
                    valueColor = if (live != null) WorkbenchColors.AccentEmerald else WorkbenchColors.TextMuted,
                    monospace = true,
                )
                PropertyKeyValueRow(
                    key = "KNX Secure",
                    value = if (hasKey) "AES-128 Key Loaded" else "Plain KNX TP1/IP",
                    valueColor = if (hasKey) WorkbenchColors.AccentEmerald else WorkbenchColors.TextSecondary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    DesktopPrimaryButton(
                        text = "Poll",
                        icon = Icons.Default.Sync,
                        onClick = { controller.readGroupAddress(inspectedGa.toString()) },
                        compact = true,
                    )
                    DesktopButton(
                        text = if (state.walkTestActive && state.walkTestGroupAddress == inspectedGa) "Stop Blink" else "Walk-Test",
                        icon = Icons.Default.FlashOn,
                        active = state.walkTestActive && state.walkTestGroupAddress == inspectedGa,
                        accentColor = WorkbenchColors.AccentAmber,
                        onClick = {
                            if (state.walkTestActive) controller.stopWalkTestBlink() else controller.startWalkTestBlink(inspectedGa.toString())
                        },
                        compact = true,
                    )
                    DesktopButton(
                        text = "Sender",
                        icon = Icons.Default.Send,
                        onClick = {
                            controller.selectGroupAddressForSender(inspectedGa.toString())
                            controller.selectTab(AppTab.SENDER)
                        },
                        compact = true,
                    )
                }

                HorizontalDivider(color = WorkbenchColors.BorderSubtle)
                Text(
                    text = "CONNECTED COMOBJECTS (${gaInfo.linkedObjects.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WorkbenchColors.TextMuted,
                )
                if (gaInfo.linkedObjects.isEmpty()) {
                    Text("No linked ComObjects in project.", fontSize = 11.sp, color = WorkbenchColors.TextMuted)
                } else {
                    gaInfo.linkedObjects.forEach { link ->
                        val devName = link.deviceAddress?.let { state.project?.findDevice(it)?.name } ?: "Device"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(WorkbenchColors.BgCanvas)
                                .border(1.dp, WorkbenchColors.BorderSubtle, RoundedCornerShape(4.dp))
                                .clickable { link.deviceAddress?.let(controller::openDeviceObjectsInExplorer) }
                                .padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${link.deviceAddress ?: "?"} · $devName",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WorkbenchColors.AccentSky,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "#${link.objectNumber ?: 0} ${link.name}",
                                    fontSize = 10.sp,
                                    color = WorkbenchColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                                )
                                ComFlagsBadges(link.flags)
                            }
                        }
                    }
                }
            } else {
                val dev = state.selectedDeviceAddress?.let { state.project?.findDevice(it) }
                if (dev == null) {
                    Text("Select any device or group address to inspect properties.", fontSize = 11.sp, color = WorkbenchColors.TextMuted)
                } else {
                    val ping = state.deviceStatuses[dev.address]
                    val hasToolKey = state.keyring?.deviceToolKeysHex?.containsKey(dev.address) == true
                    PropertyKeyValueRow("Indiv. Addr", dev.address.toString(), WorkbenchColors.AccentSky, monospace = true)
                    PropertyKeyValueRow("Name", dev.name)
                    PropertyKeyValueRow("Location", state.project?.findDeviceLocationPath(dev.address) ?: "Unassigned")
                    PropertyKeyValueRow("Manufacturer", dev.manufacturerName)
                    PropertyKeyValueRow("Product", dev.productName)
                    PropertyKeyValueRow("Order #", dev.orderNumber, monospace = true)
                    PropertyKeyValueRow("Mask / BCU", dev.maskVersion ?: "—", monospace = true)
                    PropertyKeyValueRow("Serial #", dev.hardwareSerial ?: "—", monospace = true)
                    PropertyKeyValueRow(
                        key = "Bus Status",
                        value = when {
                            ping == null -> "Not polled"
                            ping.reachable -> "ONLINE (${ping.latencyMs}ms, Mask ${ping.maskVersionHex})"
                            else -> "UNREACHABLE (Check TP wiring)"
                        },
                        valueColor = when {
                            ping == null -> WorkbenchColors.TextMuted
                            ping.reachable -> WorkbenchColors.AccentEmerald
                            else -> WorkbenchColors.AccentRed
                        },
                        monospace = true,
                    )
                    if (hasToolKey) {
                        PropertyKeyValueRow("Tool Key", "FD ToolKey Unlocked", WorkbenchColors.AccentEmerald)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopPrimaryButton(
                            text = "Objects (${dev.comObjects.size})",
                            onClick = { controller.openDeviceObjectsInExplorer(dev.address) },
                            compact = true,
                        )
                        DesktopButton(
                            text = "Ping",
                            onClick = { controller.pingIndividualDevice(dev.address.toString()) },
                            compact = true,
                        )
                        DesktopButton(
                            text = "BCU",
                            onClick = { controller.inspectDeviceDiagnostics(dev.address.toString()) },
                            compact = true,
                        )
                        DesktopButton(
                            text = "Restart",
                            onClick = { controller.restartRemoteDevice(dev.address.toString()) },
                            compact = true,
                        )
                    }

                    state.deviceDiagnostics?.takeIf { it.address == dev.address }?.let { diag ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(WorkbenchColors.BgCanvas)
                                .border(1.dp, WorkbenchColors.AccentSky.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(6.dp),
                        ) {
                            Text(
                                text = "BCU: Load=${diag.loadState ?: "-"} · Run=${diag.runState ?: "-"} · LED=${diag.programmingModeActive}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = WorkbenchColors.AccentSky,
                            )
                        }
                    }

                    HorizontalDivider(color = WorkbenchColors.BorderSubtle)
                    Text(
                        text = "ASSIGNED GROUP ADDRESSES (${dev.groupAddresses.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WorkbenchColors.TextMuted,
                    )
                    if (dev.groupAddresses.isEmpty()) {
                        Text("No group addresses linked.", fontSize = 11.sp, color = WorkbenchColors.TextMuted)
                    } else {
                        dev.groupAddresses.forEach { ga ->
                            val gName = state.project?.findGroupAddress(ga)?.name.orEmpty()
                            val live = state.groupLiveValues[ga]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WorkbenchColors.BgCanvas)
                                    .border(1.dp, WorkbenchColors.BorderSubtle, RoundedCornerShape(4.dp))
                                    .clickable { controller.inspectGroupInExplorer(ga) }
                                    .padding(horizontal = 7.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                    Text(ga.toString(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = WorkbenchColors.AccentEmerald)
                                    Text(gName.ifBlank { "Group Address" }, fontSize = 10.sp, color = WorkbenchColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                LiveValueBadge(live?.valueText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExplorerDockedMonitorPane(
    state: AppUiState,
    controller: AppController,
    modifier: Modifier = Modifier,
) {
    WorkbenchPanel(
        title = "Docked Group & Bus Monitor (Live)",
        icon = Icons.Default.Terminal,
        badgeText = "${state.telegrams.size} frames",
        modifier = modifier,
        headerActions = {
            if (state.connectionState !is ConnectionState.Connected) {
                DesktopPrimaryButton(
                    text = "Connect Gateway...",
                    icon = Icons.Default.Router,
                    onClick = { controller.toggleConnectDialog(true) },
                    compact = true,
                )
            }
            DesktopButton(
                text = if (state.monitorPaused) "Resume" else "Pause",
                onClick = { controller.setMonitorPaused(!state.monitorPaused) },
                active = state.monitorPaused,
                compact = true,
            )
            DesktopButton(
                text = "Clear",
                onClick = controller::clearMonitorLog,
                compact = true,
            )
            DesktopButton(
                text = "Full Monitor ↗",
                onClick = { controller.selectTab(AppTab.MONITOR) },
                compact = true,
            )
        },
    ) {
        DenseTableHeader(
            listOf(
                "Time" to 0.12f,
                "Source Device" to 0.24f,
                "Destination" to 0.28f,
                "Service" to 0.11f,
                "DPT" to 0.08f,
                "Decoded Value" to 0.17f,
            )
        )
        if (state.telegrams.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Live bus stream idle — connect to a KNXnet/IP gateway to monitor telegrams in real time.",
                    fontSize = 11.sp,
                    color = WorkbenchColors.TextMuted,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.telegrams.take(60), key = { _, r -> r.telegram.id }) { idx, row ->
                    val t = row.telegram
                    val dst = t.destinationGroup?.toString() ?: t.destinationIndividual?.toString() ?: "broadcast"
                    val (apciText, apciCol) = apciBadgeStyle(t.apci)
                    val rowBg = if (idx % 2 == 1) WorkbenchColors.BgRowAlt else WorkbenchColors.BgPanel

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(rowBg)
                            .clickable {
                                controller.selectMonitorTelegram(t.id)
                                controller.selectTab(AppTab.MONITOR)
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DOCK_TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs)),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = WorkbenchColors.TextMuted,
                            modifier = Modifier.weight(0.12f),
                        )
                        Text(
                            text = if (!row.sourceDeviceName.isNullOrBlank()) "${t.source} · ${row.sourceDeviceName}" else t.source.toString(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = WorkbenchColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.24f).padding(end = 6.dp),
                        )
                        Text(
                            text = if (!row.destinationName.isNullOrBlank()) "$dst · ${row.destinationName}" else dst,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (row.isUnmappedGroup) WorkbenchColors.AccentAmber else WorkbenchColors.AccentSky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.28f).padding(end = 6.dp),
                        )
                        Box(modifier = Modifier.weight(0.11f)) {
                            StatusPill(apciText, apciCol)
                        }
                        Text(
                            text = row.dptId ?: "raw",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = WorkbenchColors.TextSecondary,
                            modifier = Modifier.weight(0.08f),
                        )
                        Text(
                            text = row.decodedValue.ifBlank { "—" },
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = WorkbenchColors.AccentEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.17f),
                        )
                    }
                }
            }
        }
    }
}
