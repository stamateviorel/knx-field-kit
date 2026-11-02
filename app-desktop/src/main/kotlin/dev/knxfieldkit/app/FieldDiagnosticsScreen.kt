package dev.knxfieldkit.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.model.*

@Composable
fun FieldDiagnosticsScreen(
    state: AppUiState,
    controller: AppController,
    onExportHtmlReport: () -> Unit = {},
) {
    fun ensureBus() {
        if (state.connectionState !is ConnectionState.Connected) controller.toggleConnectDialog(true)
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Top One-Click Commissioning & Site Readiness Banner
        Surface(
            color = WorkbenchColors.BgPanel,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, WorkbenchColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val rep = state.siteCheckReport
                        if (rep != null) {
                            val scoreColor = if (rep.readinessScorePercent >= 85) WorkbenchColors.AccentEmerald else WorkbenchColors.AccentAmber
                            StatusPill("READINESS ${rep.readinessScorePercent}%", scoreColor)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "One-Click Site Health Check & Printable Commissioning Handover",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WorkbenchColors.TextPrimary,
                            )
                            val rep2 = state.siteCheckReport
                            if (state.isRunningFullSiteCheck) {
                                Text(state.siteCheckProgressText, fontSize = 11.sp, color = WorkbenchColors.AccentSky, fontFamily = FontFamily.Monospace)
                            } else if (rep2 != null) {
                                Text(
                                    text = "Online: ${rep2.onlineDevices}/${rep2.totalDevicesExpected} · Offline: ${rep2.unreachableDevices.size} · Polled GAs: ${rep2.polledGroupsResponded}/${rep2.polledGroupsTotal} · Active Prog-LEDs: ${rep2.activeProgrammingLeds.size}  |  ${rep2.findings.joinToString(" · ")}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = WorkbenchColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = "Audits all project lines, polls every group address at safe TP1 rate, detects stuck Programming LEDs, and exports HTML handover documentation.",
                                    fontSize = 11.sp,
                                    color = WorkbenchColors.TextMuted,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DesktopPrimaryButton(
                            text = if (state.isRunningFullSiteCheck) "Checking Site..." else "Run Full Site Check",
                            icon = Icons.Default.Verified,
                            onClick = controller::runFullSiteCheck,
                            enabled = !state.isRunningFullSiteCheck,
                        )
                        DesktopButton(
                            text = "Export HTML Report",
                            icon = Icons.Default.Description,
                            onClick = onExportHtmlReport,
                        )
                    }
                }
                if (state.isRunningFullSiteCheck) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = WorkbenchColors.AccentEmerald)
                }
            }
        }

        // Row 1: Device Ping / BCU Inspector + Project-vs-Bus Line Audit
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 1. Device Ping (DD0), BCU Inspector & Remote Restart
            var targetIa by remember { mutableStateOf("1.1.10") }
            WorkbenchPanel(
                title = "1. Device Ping (DD0), BCU Inspector & Remote Restart",
                icon = Icons.Default.NetworkCheck,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactInputField(
                            value = targetIa,
                            onValueChange = { targetIa = it },
                            prefixLabel = "Indiv. Address",
                            placeholder = "1.1.10",
                            modifier = Modifier.weight(1f),
                        )
                        DesktopPrimaryButton("Ping DD0", onClick = { ensureBus(); controller.pingIndividualDevice(targetIa) }, enabled = !state.isPinging, compact = true)
                        DesktopButton("Inspect BCU", onClick = { ensureBus(); controller.inspectDeviceDiagnostics(targetIa) }, enabled = !state.isReadingDiagnostics, compact = true)
                        DesktopButton("Restart", icon = Icons.Default.RestartAlt, onClick = { ensureBus(); controller.restartRemoteDevice(targetIa) }, compact = true)
                    }

                    state.deviceDiagnostics?.let { diag ->
                        Surface(
                            color = WorkbenchColors.BgCanvas,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, WorkbenchColors.BorderSubtle),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${diag.address}: ${if (diag.reachable) "ONLINE" else "UNREACHABLE"} · Mask=${diag.maskVersionHex ?: "-"} · SN=${diag.serialNumberHex ?: "-"}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (diag.reachable) WorkbenchColors.AccentEmerald else WorkbenchColors.AccentRed,
                                )
                                Text(
                                    text = "LoadState=${diag.loadState ?: "-"} · RunState=${diag.runState ?: "-"} · ProgLED=${diag.programmingModeActive}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = WorkbenchColors.TextSecondary,
                                )
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(state.pingResults) { res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WorkbenchColors.BgCanvas, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (res.reachable) "✔ ${res.address} ONLINE (Mask ${res.maskVersionHex})" else "✖ ${res.address} UNREACHABLE (${res.errorMessage ?: "timeout"})",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (res.reachable) WorkbenchColors.AccentEmerald else WorkbenchColors.AccentRed,
                                )
                                Text("${res.latencyMs} ms", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WorkbenchColors.TextMuted)
                            }
                        }
                    }
                }
            }

            // 2. Project-vs-Bus Line Audit
            val lines = state.project?.allLines.orEmpty()
            var selectedLineId by remember(state.project) { mutableStateOf(lines.firstOrNull()?.id ?: "1.1") }
            var faultsOnly by remember { mutableStateOf(false) }
            val selectedLine = lines.firstOrNull { it.id == selectedLineId } ?: lines.firstOrNull()

            WorkbenchPanel(
                title = "2. Project-vs-Bus TP Line Audit",
                icon = Icons.AutoMirrored.Filled.FactCheck,
                badgeText = state.lineAuditReport?.let { "Line ${it.lineId}: ${it.onlineCount}/${it.entries.size} OK" },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                headerActions = {
                    DesktopButton(
                        text = "Faults Only",
                        onClick = { faultsOnly = !faultsOnly },
                        active = faultsOnly,
                        accentColor = WorkbenchColors.AccentRed,
                        compact = true,
                    )
                    DesktopPrimaryButton(
                        text = if (state.isAuditingLine) "Auditing..." else "Audit Line ${selectedLine?.id.orEmpty()}",
                        onClick = {
                            ensureBus()
                            selectedLine?.let { controller.runProjectLineAudit(it.area, it.line) }
                        },
                        enabled = selectedLine != null && !state.isAuditingLine,
                        compact = true,
                    )
                },
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        lines.forEach { ln ->
                            DesktopButton(
                                text = "Line ${ln.id} · ${ln.name} (${ln.devices.size})",
                                onClick = { selectedLineId = ln.id },
                                active = selectedLine?.id == ln.id,
                                compact = true,
                            )
                        }
                    }

                    val entries = (state.lineAuditReport?.entries ?: state.lineAuditInProgressEntries).filter {
                        !faultsOnly || it.status != AuditStatus.ONLINE
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        itemsIndexed(entries) { _, entry ->
                            val color = when (entry.status) {
                                AuditStatus.ONLINE -> WorkbenchColors.AccentEmerald
                                AuditStatus.ONLINE_MASK_MISMATCH -> WorkbenchColors.AccentAmber
                                AuditStatus.UNREACHABLE -> WorkbenchColors.AccentRed
                            }
                            val loc = state.project?.findDeviceLocationPath(entry.expectedDevice.address)?.substringAfterLast("›")?.trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WorkbenchColors.BgCanvas, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${entry.expectedDevice.address} · ${entry.expectedDevice.name}${loc?.let { " ($it)" }.orEmpty()}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = WorkbenchColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                                )
                                StatusPill(
                                    text = if (entry.status == AuditStatus.ONLINE) "OK ${entry.pingResult.latencyMs}ms" else entry.status.name,
                                    color = color,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Row 2: Unknown-Site Subnet Scanner + Programming-LED Finder & Safe Address Writer
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 3. Unknown-Site Subnet Scanner
            var areaText by remember { mutableStateOf("1") }
            var lineText by remember { mutableStateOf("1") }
            var fromText by remember { mutableStateOf("0") }
            var toText by remember { mutableStateOf("30") }

            WorkbenchPanel(
                title = "3. Unknown-Site TP Subnet Scanner (No .knxproj Needed)",
                icon = Icons.Default.Radar,
                badgeText = if (state.subnetScanResults.isNotEmpty()) "${state.subnetScanResults.size} found" else null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                headerActions = {
                    if (state.isScanningSubnet) {
                        DesktopButton("Stop Scan", onClick = controller::cancelSubnetScan, accentColor = WorkbenchColors.AccentRed, active = true, compact = true)
                    }
                },
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactInputField(value = areaText, onValueChange = { areaText = it }, prefixLabel = "Area", modifier = Modifier.width(74.dp))
                        CompactInputField(value = lineText, onValueChange = { lineText = it }, prefixLabel = "Line", modifier = Modifier.width(74.dp))
                        CompactInputField(value = fromText, onValueChange = { fromText = it }, prefixLabel = "From", modifier = Modifier.width(82.dp))
                        CompactInputField(value = toText, onValueChange = { toText = it }, prefixLabel = "To", modifier = Modifier.width(76.dp))
                        DesktopPrimaryButton(
                            text = if (state.isScanningSubnet) "Scanning..." else "Scan Range",
                            icon = Icons.Default.Search,
                            onClick = {
                                ensureBus()
                                controller.startSubnetScan(
                                    areaText.toIntOrNull() ?: 1,
                                    lineText.toIntOrNull() ?: 1,
                                    fromText.toIntOrNull() ?: 0,
                                    toText.toIntOrNull() ?: 30,
                                )
                            },
                            enabled = !state.isScanningSubnet,
                            compact = true,
                        )
                    }
                    if (state.subnetScanProgressText.isNotBlank()) {
                        Text(state.subnetScanProgressText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WorkbenchColors.AccentSky)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(state.subnetScanResults) { r ->
                            val devName = state.project?.findDevice(r.address)?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WorkbenchColors.BgCanvas, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${r.address} ${devName?.let { "· $it" }.orEmpty()}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = WorkbenchColors.AccentEmerald,
                                )
                                Text("Mask ${r.maskVersionHex} · ${r.latencyMs}ms", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WorkbenchColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            // 4. Programming-LED Finder & Safe Individual Address Writer
            var newAddressInput by remember { mutableStateOf("1.1.50") }
            WorkbenchPanel(
                title = "4. Programming-LED Finder & Address Writer",
                icon = Icons.Default.LightMode,
                badgeText = "${state.programmingModeDevices.size} LED",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                headerActions = {
                    DesktopPrimaryButton(
                        text = if (state.isCheckingProgrammingMode) "Scanning..." else "Scan LEDs",
                        onClick = { ensureBus(); controller.scanProgrammingModeLeds() },
                        enabled = !state.isCheckingProgrammingMode,
                        color = WorkbenchColors.AccentAmber,
                        compact = true,
                    )
                },
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactInputField(
                            value = newAddressInput,
                            onValueChange = { newAddressInput = it },
                            prefixLabel = "Assign New IA (1 LED only)",
                            placeholder = "1.1.50",
                            modifier = Modifier.weight(1f),
                        )
                        DesktopButton(
                            text = "Write Address",
                            icon = Icons.Default.EditLocationAlt,
                            onClick = { ensureBus(); controller.commissionProgrammingDevice(newAddressInput) },
                            accentColor = WorkbenchColors.AccentAmber,
                            active = state.programmingModeDevices.size == 1,
                            compact = true,
                        )
                    }
                    HorizontalDivider(color = WorkbenchColors.BorderSubtle)
                    if (state.programmingModeDevices.isEmpty()) {
                        Text(
                            text = "Press the physical Programming Button on a KNX device, click 'Scan Active LEDs', then safely write its Individual Address (refuses if 0 or >1 LEDs are active).",
                            fontSize = 11.sp,
                            color = WorkbenchColors.TextMuted,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.programmingModeDevices) { addr ->
                                val devName = state.project?.findDevice(addr)?.name ?: "Unmapped / New Replacement Device"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(WorkbenchColors.AccentAmber.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "● PROG-LED ACTIVE: $addr — $devName",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WorkbenchColors.AccentAmber,
                                    )
                                    StatusPill("READY", WorkbenchColors.AccentAmber)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
