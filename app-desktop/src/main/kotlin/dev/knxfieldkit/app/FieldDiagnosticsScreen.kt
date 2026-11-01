package dev.knxfieldkit.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.knxfieldkit.model.*

@Composable
fun FieldDiagnosticsScreen(
    state: AppUiState,
    controller: AppController,
    onExportHtmlReport: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Top One-Click Full Site Commissioning Wizard Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("One-Click Full Site Health Check & Commissioning Report", style = MaterialTheme.typography.titleMedium)
                        val rep = state.siteCheckReport
                        if (state.isRunningFullSiteCheck) {
                            Text(state.siteCheckProgressText, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        } else if (rep != null) {
                            Text(
                                text = "Score: ${rep.readinessScorePercent}% • Online: ${rep.onlineDevices}/${rep.totalDevicesExpected} • Offline: ${rep.unreachableDevices.size} • Polled GAs: ${rep.polledGroupsResponded}/${rep.polledGroupsTotal} • Prog-LEDs: ${rep.activeProgrammingLeds.size}",
                                color = if (rep.readinessScorePercent >= 85) Color(0xFF2ECC71) else Color(0xFFF59E0B),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = rep.findings.joinToString("  |  "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = "Audits every expected device, polls all group addresses, checks stuck Programming LEDs, and builds a printable HTML handover report.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = controller::runFullSiteCheck,
                            enabled = !state.isRunningFullSiteCheck,
                        ) {
                            Text(if (state.isRunningFullSiteCheck) "Checking Bus..." else "Run Full Site Check")
                        }
                        OutlinedButton(onClick = onExportHtmlReport) {
                            Text("Export HTML Report")
                        }
                    }
                }
                if (state.isRunningFullSiteCheck) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Individual Address Ping, BCU Inspector & Remote Restart
            Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Device Ping (DD0), BCU Inspector & Remote Restart", style = MaterialTheme.typography.titleMedium)
                    var targetIa by remember { mutableStateOf("1.1.10") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = targetIa,
                            onValueChange = { targetIa = it },
                            label = { Text("Individual Address") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { controller.pingIndividualDevice(targetIa) }, enabled = !state.isPinging) {
                            Text("Ping")
                        }
                        OutlinedButton(
                            onClick = { controller.inspectDeviceDiagnostics(targetIa) },
                            enabled = !state.isReadingDiagnostics,
                        ) {
                            Text("Inspect")
                        }
                        OutlinedButton(onClick = { controller.restartRemoteDevice(targetIa) }) {
                            Text("Restart")
                        }
                    }
                    if (state.isSimulatorActive) {
                        IndividualAddress.parseOrNull(targetIa)?.let { ia ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { controller.toggleSimulatedDeviceOnline(ia, true) }) {
                                    Text("Sim: Set Online")
                                }
                                OutlinedButton(onClick = { controller.toggleSimulatedDeviceOnline(ia, false) }) {
                                    Text("Sim: Simulate Wiring Fault")
                                }
                            }
                        }
                    }
                    state.deviceDiagnostics?.let { diag ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text(
                                    text = "${diag.address}: ${if (diag.reachable) "ONLINE" else "UNREACHABLE"} • Mask=${diag.maskVersionHex ?: "-"} • SN=${diag.serialNumberHex ?: "-"}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "Load=${diag.loadState ?: "-"} • Run=${diag.runState ?: "-"} • ProgLED=${diag.programmingModeActive}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.pingResults) { res ->
                            Text(
                                text = if (res.reachable) {
                                    "✔ ${res.address} ONLINE — Mask ${res.maskVersionHex} (${res.latencyMs} ms)"
                                } else {
                                    "✖ ${res.address} UNREACHABLE — ${res.errorMessage ?: "timeout"}"
                                },
                                color = if (res.reachable) Color(0xFF2ECC71) else Color(0xFFEF4444),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // 2. Project-vs-Bus Line Audit
            Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Project-vs-Bus Line Audit", style = MaterialTheme.typography.titleMedium)
                    val lines = state.project?.allLines.orEmpty()
                    var selectedLineId by remember(state.project) { mutableStateOf(lines.firstOrNull()?.id ?: "1.1") }
                    var faultsOnly by remember { mutableStateOf(false) }
                    val selectedLine = lines.firstOrNull { it.id == selectedLineId } ?: lines.firstOrNull()

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        lines.take(3).forEach { ln ->
                            FilterChip(
                                selected = selectedLine?.id == ln.id,
                                onClick = { selectedLineId = ln.id },
                                label = { Text("Line ${ln.id} (${ln.devices.size})") },
                            )
                        }
                        FilterChip(
                            selected = faultsOnly,
                            onClick = { faultsOnly = !faultsOnly },
                            label = { Text("Faults Only") },
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { selectedLine?.let { controller.runProjectLineAudit(it.area, it.line) } },
                            enabled = selectedLine != null && !state.isAuditingLine,
                        ) {
                            Text(if (state.isAuditingLine) "Auditing..." else "Audit Line")
                        }
                    }
                    state.lineAuditReport?.let { rep ->
                        Text(
                            text = "Line ${rep.lineId} — Online: ${rep.onlineCount} | Unreachable: ${rep.unreachableCount} | Mask Mismatch: ${rep.mismatchCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val entries = (state.lineAuditReport?.entries ?: state.lineAuditInProgressEntries).filter {
                        !faultsOnly || it.status != AuditStatus.ONLINE
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(entries) { entry ->
                            val color = when (entry.status) {
                                AuditStatus.ONLINE -> Color(0xFF2ECC71)
                                AuditStatus.ONLINE_MASK_MISMATCH -> Color(0xFFF59E0B)
                                AuditStatus.UNREACHABLE -> Color(0xFFEF4444)
                            }
                            val loc = state.project?.findDeviceLocationPath(entry.expectedDevice.address)
                            Text(
                                text = "[${entry.status}] ${entry.expectedDevice.address} — ${entry.expectedDevice.name}${loc?.let { " ($it)" }.orEmpty()} [${entry.pingResult.maskVersionHex ?: "no reply"}, ${entry.pingResult.latencyMs} ms]",
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 3. Unknown Installation Subnet Scanner
            Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Unknown-Site Subnet Scanner (No .knxproj Needed)", style = MaterialTheme.typography.titleMedium)
                    var areaText by remember { mutableStateOf("1") }
                    var lineText by remember { mutableStateOf("1") }
                    var fromText by remember { mutableStateOf("0") }
                    var toText by remember { mutableStateOf("30") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = areaText, onValueChange = { areaText = it }, label = { Text("Area") }, singleLine = true, modifier = Modifier.width(68.dp))
                        OutlinedTextField(value = lineText, onValueChange = { lineText = it }, label = { Text("Line") }, singleLine = true, modifier = Modifier.width(68.dp))
                        OutlinedTextField(value = fromText, onValueChange = { fromText = it }, label = { Text("Start") }, singleLine = true, modifier = Modifier.width(78.dp))
                        OutlinedTextField(value = toText, onValueChange = { toText = it }, label = { Text("End") }, singleLine = true, modifier = Modifier.width(78.dp))
                        Button(
                            onClick = {
                                controller.startSubnetScan(
                                    areaText.toIntOrNull() ?: 1,
                                    lineText.toIntOrNull() ?: 1,
                                    fromText.toIntOrNull() ?: 0,
                                    toText.toIntOrNull() ?: 30,
                                )
                            },
                            enabled = !state.isScanningSubnet,
                        ) {
                            Text("Scan")
                        }
                        if (state.isScanningSubnet) {
                            OutlinedButton(onClick = controller::cancelSubnetScan) {
                                Text("Stop")
                            }
                        }
                    }
                    if (state.subnetScanProgressText.isNotBlank()) {
                        Text(state.subnetScanProgressText, style = MaterialTheme.typography.bodySmall)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.subnetScanResults) { r ->
                            val devName = state.project?.findDevice(r.address)?.name
                            Text(
                                text = "Found ${r.address} ${devName?.let { "($it)" }.orEmpty()} — Mask ${r.maskVersionHex} (${r.latencyMs} ms)",
                                color = Color(0xFF2ECC71),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // 4. Programming Mode LED Finder & Safe Address Commissioning
            Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("4. Programming-LED Finder & Safe Address Writer", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = controller::scanProgrammingModeLeds,
                            enabled = !state.isCheckingProgrammingMode,
                        ) {
                            Text(if (state.isCheckingProgrammingMode) "Scanning..." else "Scan Active Prog-LEDs")
                        }
                        if (state.isSimulatorActive) {
                            val testIa = IndividualAddress(1, 1, 15)
                            OutlinedButton(onClick = {
                                controller.toggleSimulatedProgrammingLed(testIa, testIa !in state.programmingModeDevices)
                            }) {
                                Text("Sim: Toggle LED on 1.1.15")
                            }
                        }
                    }
                    var newAddressInput by remember { mutableStateOf("1.1.50") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newAddressInput,
                            onValueChange = { newAddressInput = it },
                            label = { Text("Assign New Address (requires 1 active LED)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { controller.commissionProgrammingDevice(newAddressInput) }) {
                            Text("Write Address")
                        }
                    }
                    if (state.programmingModeDevices.isEmpty()) {
                        Text(
                            text = "Press the physical Programming Button on a KNX device, click 'Scan Active Prog-LEDs', or assign its Individual Address safely.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.programmingModeDevices) { addr ->
                                val devName = state.project?.findDevice(addr)?.name ?: "Unmapped / New Replacement Device"
                                Text(
                                    text = "● ACTIVE PROG-LED: $addr — $devName",
                                    color = Color(0xFFF59E0B),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
