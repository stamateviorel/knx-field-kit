package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import dev.knxfieldkit.app.AppUiState
import dev.knxfieldkit.model.*

private enum class MobileDiagTool(val label: String) {
    SITE_AUDIT("Site & Line"),
    PING_BCU("Ping & BCU"),
    PROG_LED("Prog LED"),
    SUBNET("Subnet & Lab"),
}

@Composable
fun MobileDiagnosticsScreen(
    state: AppUiState,
    controller: AppController,
    onShareHtmlReport: () -> Unit,
) {
    var activeTool by remember { mutableStateOf(MobileDiagTool.SITE_AUDIT) }
    var targetDeviceText by remember(state.selectedDeviceAddress) {
        mutableStateOf(state.selectedDeviceAddress?.toString() ?: "1.1.10")
    }
    var newProgAddrText by remember { mutableStateOf("1.1.21") }
    var scanAreaText by remember { mutableStateOf("1") }
    var scanLineText by remember { mutableStateOf("1") }
    var scanEndText by remember { mutableStateOf("30") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MobileColors.BgCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Hero Card: 1-Tap Full Site Health Check
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MobileColors.BgCardElevated,
            border = BorderStroke(1.dp, MobileColors.AccentEmerald.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MobileColors.AccentEmerald)
                        Column {
                            Text("1-Tap Full Site Health Check", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                            Text("Audits lines, polls GAs, scans Prog LEDs & Ghost GAs", fontSize = 11.sp, color = MobileColors.TextSecondary)
                        }
                    }
                    state.siteCheckReport?.let { rep ->
                        MobileBadge(
                            text = "${rep.readinessScorePercent}%",
                            color = if (rep.readinessScorePercent >= 85) MobileColors.AccentEmerald else MobileColors.AccentAmber,
                        )
                    }
                }

                if (state.isRunningFullSiteCheck) {
                    LinearProgressIndicator(
                        color = MobileColors.AccentEmerald,
                        trackColor = MobileColors.BgInput,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(state.siteCheckProgressText, fontSize = 11.sp, color = MobileColors.AccentSky)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { controller.runFullSiteCheck() },
                        enabled = !state.isRunningFullSiteCheck,
                        colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = Color(0xFF042F2E)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f),
                    ) {
                        Text(if (state.isRunningFullSiteCheck) "Auditing..." else "Run Full Site Check", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onShareHtmlReport,
                        border = BorderStroke(1.dp, MobileColors.AccentSky),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MobileColors.AccentSky, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("HTML Report", fontSize = 12.sp, color = MobileColors.AccentSky)
                    }
                }

                state.siteCheckReport?.let { rep ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MobileBadge("${rep.onlineDevices}/${rep.totalDevicesExpected} Online", MobileColors.AccentEmerald)
                        MobileBadge("${rep.polledGroupsResponded}/${rep.polledGroupsTotal} GAs", MobileColors.AccentSky)
                        if (rep.unreachableDevices.isNotEmpty()) {
                            MobileBadge("${rep.unreachableDevices.size} Fault", MobileColors.AccentRed)
                        }
                        if (rep.activeProgrammingLeds.isNotEmpty()) {
                            MobileBadge("${rep.activeProgrammingLeds.size} LED ON", MobileColors.AccentAmber)
                        }
                    }
                    rep.findings.forEach { finding ->
                        Text("• $finding", fontSize = 11.sp, color = MobileColors.TextSecondary)
                    }
                }
            }
        }

        // Sub-Tool Selector Bar
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MobileColors.BgTopBar,
            border = BorderStroke(1.dp, MobileColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MobileDiagTool.entries.forEach { tool ->
                    val selected = activeTool == tool
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selected) MobileColors.AccentSky.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) MobileColors.AccentSky.copy(alpha = 0.6f) else Color.Transparent,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable { activeTool = tool }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tool.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MobileColors.AccentSky else MobileColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        when (activeTool) {
            MobileDiagTool.SITE_AUDIT -> {
                val lines = state.project?.allLines.orEmpty()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MobileColors.BgCard,
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PROJECT TP LINE AUDIT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextSecondary)
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MobileColors.BgInput)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MobileBadge(line.id, MobileColors.AccentSky)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(line.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                                    Text("${line.devices.size} expected devices (${line.mediumType})", fontSize = 11.sp, color = MobileColors.TextSecondary)
                                }
                                MobileActionPill(
                                    label = if (state.isAuditingLine) "Running..." else "Audit Line",
                                    accent = MobileColors.AccentEmerald,
                                ) {
                                    if (!state.isAuditingLine) controller.runProjectLineAudit(line.area, line.line)
                                }
                            }
                        }

                        val entries = state.lineAuditReport?.entries ?: state.lineAuditInProgressEntries
                        if (entries.isNotEmpty()) {
                            HorizontalDivider(color = MobileColors.BorderSubtle)
                            state.lineAuditReport?.let { rep ->
                                Text(
                                    text = "Line ${rep.lineId}: ${rep.onlineCount} online • ${rep.unreachableCount} unreachable • ${rep.mismatchCount} mask mismatch",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rep.unreachableCount > 0) MobileColors.AccentRed else MobileColors.AccentEmerald,
                                )
                            }
                            entries.forEach { entry ->
                                val ok = entry.status == AuditStatus.ONLINE
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MobileColors.BgInput)
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MobileBadge(
                                        text = entry.expectedDevice.address.toString(),
                                        color = if (ok) MobileColors.AccentEmerald else MobileColors.AccentRed,
                                    )
                                    Text(
                                        text = entry.expectedDevice.name,
                                        fontSize = 12.sp,
                                        color = MobileColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    MobileBadge(
                                        text = if (entry.pingResult.reachable) "${entry.pingResult.maskVersionHex ?: "OK"} ${entry.pingResult.latencyMs}ms" else "UNREACHABLE",
                                        color = if (entry.pingResult.reachable) MobileColors.AccentEmerald else MobileColors.AccentRed,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            MobileDiagTool.PING_BCU -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MobileColors.BgCard,
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DD0 PING, BCU PROPERTY READOUT & REMOTE RESTART", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextSecondary)
                        OutlinedTextField(
                            value = targetDeviceText,
                            onValueChange = { targetDeviceText = it },
                            label = { Text("Individual Address (e.g. 1.1.10 or 1.1.40)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1.1.10", "1.1.20", "1.1.40", "1.2.10").forEach { quick ->
                                MobileActionPill(quick, MobileColors.AccentSky) { targetDeviceText = quick }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MobileActionPill("Ping DD0", MobileColors.AccentEmerald) {
                                controller.pingIndividualDevice(targetDeviceText)
                            }
                            MobileActionPill("Read BCU Info", MobileColors.AccentSky) {
                                controller.inspectDeviceDiagnostics(targetDeviceText)
                            }
                            MobileActionPill("A_Restart", MobileColors.AccentAmber) {
                                controller.restartRemoteDevice(targetDeviceText)
                            }
                        }

                        state.deviceDiagnostics?.let { diag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MobileColors.BgInput,
                                border = BorderStroke(1.dp, if (diag.reachable) MobileColors.AccentEmerald else MobileColors.AccentRed),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${diag.address} — ${if (diag.reachable) "ONLINE" else "UNREACHABLE"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (diag.reachable) MobileColors.AccentEmerald else MobileColors.AccentRed,
                                    )
                                    if (diag.reachable) {
                                        Text("Mask DD0: ${diag.maskVersionHex ?: "-"} • Serial: ${diag.serialNumberHex ?: "-"}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MobileColors.TextPrimary)
                                        Text("Load State: ${diag.loadState ?: "-"} • Run State: ${diag.runState ?: "-"} • Prog LED: ${diag.programmingModeActive}", fontSize = 11.sp, color = MobileColors.TextSecondary)
                                    }
                                    diag.notes.forEach { Text(it, fontSize = 11.sp, color = MobileColors.AccentSky) }
                                }
                            }
                        }

                        state.pingResults.take(5).forEach { pr ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(pr.address.toString(), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MobileColors.TextPrimary)
                                MobileBadge(
                                    text = if (pr.reachable) "ONLINE ${pr.maskVersionHex} (${pr.latencyMs}ms)" else "TIMEOUT",
                                    color = if (pr.reachable) MobileColors.AccentEmerald else MobileColors.AccentRed,
                                )
                            }
                        }
                    }
                }
            }

            MobileDiagTool.PROG_LED -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MobileColors.BgCard,
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PROGRAMMING-LED FINDER & SAFE ADDRESS WRITER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextSecondary)
                        Text(
                            "Press the physical Programming Button on a replacement KNX actuator/sensor, scan active LEDs, then write its Individual Address safely.",
                            fontSize = 11.sp,
                            color = MobileColors.TextSecondary,
                        )
                        Button(
                            onClick = { controller.scanProgrammingModeLeds() },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = Color(0xFF082F49)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.isCheckingProgrammingMode) "Scanning Bus..." else "Scan Active Programming LEDs", fontWeight = FontWeight.Bold)
                        }

                        if (state.programmingModeDevices.isEmpty()) {
                            Text("Active Programming LEDs: None detected yet", fontSize = 12.sp, color = MobileColors.TextMuted)
                        } else {
                            state.programmingModeDevices.forEach { ia ->
                                val devName = state.project?.findDevice(ia)?.name ?: "Bus Device"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MobileColors.AccentRed.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, MobileColors.AccentRed),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        MobileBadge("PROG LED ON", MobileColors.AccentRed)
                                        Text("$ia — $devName", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MobileColors.BorderSubtle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newProgAddrText,
                                onValueChange = { newProgAddrText = it },
                                label = { Text("New Individual Address") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { controller.commissionProgrammingDevice(newProgAddrText) },
                                colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentAmber, contentColor = Color(0xFF1F1300)),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Write Addr", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            MobileDiagTool.SUBNET -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MobileColors.BgCard,
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("UNKNOWN-SITE SUBNET SCANNER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = scanAreaText, onValueChange = { scanAreaText = it }, label = { Text("Area") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = scanLineText, onValueChange = { scanLineText = it }, label = { Text("Line") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = scanEndText, onValueChange = { scanEndText = it }, label = { Text("Max Dev") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MobileActionPill("Start Scan", MobileColors.AccentEmerald) {
                                controller.startSubnetScan(
                                    area = scanAreaText.toIntOrNull() ?: 1,
                                    line = scanLineText.toIntOrNull() ?: 1,
                                    startDevice = 0,
                                    endDevice = scanEndText.toIntOrNull() ?: 30,
                                )
                            }
                            if (state.isScanningSubnet) {
                                MobileActionPill("Stop", MobileColors.AccentRed) { controller.cancelSubnetScan() }
                                Text(state.subnetScanProgressText, fontSize = 11.sp, color = MobileColors.AccentSky)
                            }
                        }
                        state.subnetScanResults.forEach { found ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${found.address} (${state.project?.findDevice(found.address)?.name ?: "Discovered"})", fontSize = 12.sp, color = MobileColors.TextPrimary)
                                MobileBadge("Mask ${found.maskVersionHex}", MobileColors.AccentEmerald)
                            }
                        }

                        HorizontalDivider(color = MobileColors.BorderSubtle)
                        Text("VIRTUAL BUS FAULT LAB CONTROLS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobileColors.AccentAmber)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MobileActionPill("Fix 1.1.40 Wiring", MobileColors.AccentEmerald) {
                                controller.toggleSimulatedDeviceOnline(IndividualAddress(1, 1, 40), true)
                            }
                            MobileActionPill("Break 1.1.40", MobileColors.AccentRed) {
                                controller.toggleSimulatedDeviceOnline(IndividualAddress(1, 1, 40), false)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MobileActionPill("1.1.20 LED ON", MobileColors.AccentAmber) {
                                controller.toggleSimulatedProgrammingLed(IndividualAddress(1, 1, 20), true)
                            }
                            MobileActionPill("1.1.20 LED OFF", MobileColors.TextSecondary) {
                                controller.toggleSimulatedProgrammingLed(IndividualAddress(1, 1, 20), false)
                            }
                        }
                    }
                }
            }
        }
    }
}
