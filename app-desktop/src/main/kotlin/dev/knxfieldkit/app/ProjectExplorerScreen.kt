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
import java.io.File

private enum class GaQuickFilter(val label: String) {
    ALL("All"),
    SWITCH("Lights / Switch"),
    DIM("Dim / Blinds"),
    SENSORS("Sensors (°C/Lux)"),
}

private fun flattenLocations(roots: List<BuildingLocation>, prefix: String = ""): List<Pair<String, List<IndividualAddress>>> {
    val out = mutableListOf<Pair<String, List<IndividualAddress>>>()
    for (loc in roots) {
        val path = if (prefix.isEmpty()) loc.name else "$prefix › ${loc.name}"
        if (loc.deviceAddresses.isNotEmpty()) {
            out += path to loc.deviceAddresses
        }
        out += flattenLocations(loc.children, path)
    }
    return out
}

@Composable
fun ProjectExplorerScreen(
    state: AppUiState,
    controller: AppController,
    onPickFile: () -> Unit,
    onExportReport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Project Header & Quick Switcher Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.project?.name ?: "No Project Loaded",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "${state.projectSourceLabel} • ETS: ${state.project?.etsVersion?.ifBlank { "N/A" } ?: "N/A"} • Devices: ${state.project?.allDevices?.size ?: 0} • Group Addresses: ${state.project?.groupAddresses?.size ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.project?.passwordProtected == true) {
                            Surface(color = MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.small) {
                                Text(
                                    text = "UNLOCKED .KNXPROJ",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        state.keyring?.let { kr ->
                            Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
                                Text(
                                    text = "KEYRING (${kr.groupKeysHex.size} KEYS)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPickFile) {
                        Text("Open .knxproj / .knxkeys")
                    }
                    OutlinedButton(onClick = controller::loadDemoProject) {
                        Text("Demo Villa")
                    }
                    OutlinedButton(onClick = {
                        controller.generateAndPromptEncryptedSample(File("samples", "penthouse_ets6_encrypted.knxproj"))
                    }) {
                        Text("Test Encrypted ETS6 Sample")
                    }
                    OutlinedButton(onClick = onExportReport, enabled = state.project != null) {
                        Text("Export Markdown")
                    }
                }

                if (state.recentFiles.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent:", style = MaterialTheme.typography.labelSmall)
                        state.recentFiles.take(4).forEach { recent ->
                            AssistChip(
                                onClick = { controller.openProjectOrKeyringFile(recent) },
                                label = { Text(recent.name) },
                            )
                        }
                    }
                }
            }
        }

        // Main 3-Column Interactive Explorer
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Left Column: Topology vs Rooms/Floors
            Card(modifier = Modifier.weight(0.30f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = !state.explorerLocationView,
                            onClick = { controller.setExplorerLocationView(false) },
                            label = { Text("Topology") },
                        )
                        FilterChip(
                            selected = state.explorerLocationView,
                            onClick = { controller.setExplorerLocationView(true) },
                            label = { Text("Rooms / Floors") },
                        )
                    }

                    if (!state.explorerLocationView) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.project?.allLines.orEmpty()) { line ->
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Line ${line.id} — ${line.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                            TextButton(
                                                onClick = { controller.runProjectLineAudit(line.area, line.line) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text("Audit Line")
                                            }
                                        }
                                        line.devices.forEach { dev ->
                                            DeviceTreeRow(
                                                dev = dev,
                                                ping = state.deviceStatuses[dev.address],
                                                selected = state.selectedInspectorGroup == null && state.selectedDeviceAddress == dev.address,
                                                onClick = { controller.inspectDeviceInExplorer(dev.address) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val roomEntries = remember(state.project) {
                            flattenLocations(state.project?.locations.orEmpty())
                        }
                        if (roomEntries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No room locations defined in this .knxproj.", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(roomEntries) { (roomPath, addrs) ->
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = roomPath,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                            addrs.mapNotNull { state.project?.findDevice(it) }.forEach { dev ->
                                                DeviceTreeRow(
                                                    dev = dev,
                                                    ping = state.deviceStatuses[dev.address],
                                                    selected = state.selectedInspectorGroup == null && state.selectedDeviceAddress == dev.address,
                                                    onClick = { controller.inspectDeviceInExplorer(dev.address) },
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

            // Center Column: Group Addresses with Direct 1-Click Inline Controls
            Card(modifier = Modifier.weight(0.41f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Group Addresses & 1-Click Controls", style = MaterialTheme.typography.titleMedium)
                    var query by remember { mutableStateOf("") }
                    var quickFilter by remember { mutableStateOf(GaQuickFilter.ALL) }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search GA (1/1/1), name, or DPT (9.001)...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GaQuickFilter.entries.forEach { f ->
                            FilterChip(
                                selected = quickFilter == f,
                                onClick = { quickFilter = f },
                                label = { Text(f.label) },
                            )
                        }
                    }

                    val filteredGas = remember(state.project, query, quickFilter) {
                        state.project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty().filter { ga ->
                            val matchesText = query.isBlank() ||
                                ga.address.toString().contains(query, ignoreCase = true) ||
                                ga.name.contains(query, ignoreCase = true) ||
                                (ga.dpt?.contains(query, ignoreCase = true) == true)
                            val mainDpt = DptCodec.resolveMainDpt(ga.dpt)
                            val matchesCat = when (quickFilter) {
                                GaQuickFilter.ALL -> true
                                GaQuickFilter.SWITCH -> mainDpt == MainDpt.DPT_1_BOOL
                                GaQuickFilter.DIM -> mainDpt == MainDpt.DPT_5_PERCENT || mainDpt == MainDpt.DPT_3_DIM_STEP
                                GaQuickFilter.SENSORS -> mainDpt == MainDpt.DPT_9_FLOAT || mainDpt == MainDpt.DPT_14_FLOAT32
                            }
                            matchesText && matchesCat
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredGas, key = { it.address.raw }) { ga ->
                            val isSelected = state.selectedInspectorGroup == ga.address
                            val live = state.groupLiveValues[ga.address]
                            val mainDpt = DptCodec.resolveMainDpt(ga.dpt)
                            val addrStr = ga.address.toString()

                            Surface(
                                onClick = { controller.inspectGroupInExplorer(ga.address) },
                                color = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${ga.address} — ${ga.name}", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "DPT ${ga.dpt ?: "raw"} • Live: ${live?.valueText ?: "—"}${live?.latencyMs?.let { " (${it}ms)" }.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        when (mainDpt) {
                                            MainDpt.DPT_1_BOOL -> {
                                                Button(
                                                    onClick = { controller.writeGroupSwitch(addrStr, true) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                ) { Text("ON") }
                                                OutlinedButton(
                                                    onClick = { controller.writeGroupSwitch(addrStr, false) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                ) { Text("OFF") }
                                                TextButton(
                                                    onClick = { controller.readGroupAddress(addrStr) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                                ) { Text("Read") }
                                            }
                                            MainDpt.DPT_5_PERCENT -> {
                                                OutlinedButton(
                                                    onClick = { controller.writeGroupPercent(addrStr, 0.0) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                ) { Text("0%") }
                                                OutlinedButton(
                                                    onClick = { controller.writeGroupPercent(addrStr, 50.0) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                ) { Text("50%") }
                                                Button(
                                                    onClick = { controller.writeGroupPercent(addrStr, 100.0) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                ) { Text("100%") }
                                            }
                                            MainDpt.DPT_3_DIM_STEP -> {
                                                OutlinedButton(
                                                    onClick = { controller.writeGroupDimStep(addrStr, increase = false, stepCode = 3) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                ) { Text("-25%") }
                                                Button(
                                                    onClick = { controller.writeGroupDimStep(addrStr, increase = true, stepCode = 3) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                ) { Text("+25%") }
                                            }
                                            MainDpt.DPT_17_SCENE -> {
                                                Button(
                                                    onClick = { controller.writeGroupScene(addrStr, 1) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                ) { Text("Scene 1") }
                                                TextButton(
                                                    onClick = { controller.readGroupAddress(addrStr) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                                ) { Text("Read") }
                                            }
                                            else -> {
                                                OutlinedButton(
                                                    onClick = { controller.readGroupAddress(addrStr) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                ) { Text("Read") }
                                                Button(
                                                    onClick = {
                                                        controller.selectGroupAddressForSender(addrStr)
                                                        controller.selectTab(AppTab.SENDER)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                ) { Text("Send...") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Column: Cross-Reference Inspector (Device OR Group Address)
            Card(modifier = Modifier.weight(0.29f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val inspGa = state.selectedInspectorGroup?.let { state.project?.findGroupAddress(it) }
                    val inspDev = state.selectedDeviceAddress?.let { state.project?.findDevice(it) }

                    if (inspGa != null) {
                        val live = state.groupLiveValues[inspGa.address]
                        val blinkingThis = state.walkTestActive && state.walkTestGroupAddress == inspGa.address
                        Text("Group Address Inspector", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("${inspGa.address} — ${inspGa.name}", style = MaterialTheme.typography.titleMedium)
                        if (inspGa.description.isNotBlank()) {
                            Text(inspGa.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "DPT: ${inspGa.dpt ?: "raw"} • Live: ${live?.valueText ?: "—"} ${live?.latencyMs?.let { "(${it} ms)" }.orEmpty()}",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { controller.readGroupAddress(inspGa.address.toString()) }) {
                                Text("Poll Bus")
                            }
                            OutlinedButton(onClick = {
                                if (blinkingThis) controller.stopWalkTestBlink()
                                else controller.startWalkTestBlink(inspGa.address.toString())
                            }) {
                                Text(if (blinkingThis) "Stop Blink" else "Walk-Test Blink")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                controller.selectGroupAddressForSender(inspGa.address.toString())
                                controller.selectTab(AppTab.SENDER)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Full DPT Controls...")
                        }

                        HorizontalDivider()
                        Text("Linked Hardware (${inspGa.linkedObjects.size} ComObjects)", style = MaterialTheme.typography.labelLarge)
                        if (inspGa.linkedObjects.isEmpty()) {
                            Text("No ComObject links listed in .knxproj for this GA.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            inspGa.linkedObjects.forEach { link ->
                                val linkedDev = link.deviceAddress?.let { state.project?.findDevice(it) }
                                Surface(
                                    onClick = { link.deviceAddress?.let(controller::inspectDeviceInExplorer) },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(
                                            text = "${link.deviceAddress ?: "?"} — ${linkedDev?.name ?: "Device"}",
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        Text(
                                            text = "${link.name}${if (link.functionText.isNotBlank()) " (${link.functionText})" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (inspDev != null) {
                        val ping = state.deviceStatuses[inspDev.address]
                        val room = state.project?.findDeviceLocationPath(inspDev.address) ?: "Unassigned Room"
                        Text("Device Cross-Reference", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("${inspDev.address} — ${inspDev.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Location: $room", style = MaterialTheme.typography.bodySmall)
                        Text("Hardware: ${inspDev.manufacturerName} ${inspDev.productName}".trim(), style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Order: ${inspDev.orderNumber.ifBlank { "-" }} • Mask: ${inspDev.maskVersion ?: "-"} • SN: ${inspDev.hardwareSerial ?: "-"}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val statusText = when {
                            ping == null -> "● Bus Status: Not pinged yet"
                            ping.reachable -> "● ONLINE — Mask ${ping.maskVersionHex} (${ping.latencyMs} ms)"
                            else -> "● UNREACHABLE — ${ping.errorMessage ?: "timeout"}"
                        }
                        val statusColor = when {
                            ping == null -> Color(0xFF94A3B8)
                            ping.reachable -> Color(0xFF2ECC71)
                            else -> Color(0xFFEF4444)
                        }
                        Text(statusText, color = statusColor, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { controller.pingIndividualDevice(inspDev.address.toString()) }) {
                                Text("Ping")
                            }
                            OutlinedButton(onClick = { controller.restartRemoteDevice(inspDev.address.toString()) }) {
                                Text("Restart")
                            }
                        }
                        if (state.isSimulatorActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { controller.toggleSimulatedDeviceOnline(inspDev.address, true) }) {
                                    Text("Sim: Online")
                                }
                                OutlinedButton(onClick = { controller.toggleSimulatedDeviceOnline(inspDev.address, false) }) {
                                    Text("Sim: Fault")
                                }
                            }
                        }

                        HorizontalDivider()
                        Text("Linked Group Addresses (${inspDev.groupAddresses.size})", style = MaterialTheme.typography.labelLarge)
                        if (inspDev.groupAddresses.isEmpty()) {
                            Text("No linked Group Addresses (system/coupler device or unprogrammed).", style = MaterialTheme.typography.bodySmall)
                        } else {
                            inspDev.groupAddresses.forEach { gaAddr ->
                                val ga = state.project?.findGroupAddress(gaAddr)
                                val lv = state.groupLiveValues[gaAddr]?.valueText ?: "—"
                                Surface(
                                    onClick = { controller.inspectGroupInExplorer(gaAddr) },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("$gaAddr — ${ga?.name ?: "Group"}", style = MaterialTheme.typography.bodySmall)
                                            Text("Live: $lv", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { controller.readGroupAddress(gaAddr.toString()) }) {
                                            Text("Read")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Click any KNX device or Group Address to inspect linked ComObjects, room location, and live bus controls.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceTreeRow(
    dev: ProjectDevice,
    ping: DevicePingResult?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val badgeText = when {
        ping == null -> "○"
        ping.reachable -> "● ${ping.latencyMs}ms"
        else -> "✖ OFF"
    }
    val badgeColor = when {
        ping == null -> Color(0xFF94A3B8)
        ping.reachable -> Color(0xFF2ECC71)
        else -> Color(0xFFEF4444)
    }
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else Color.Transparent,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${dev.address}  ${dev.name}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "${dev.manufacturerName} ${dev.productName}".trim(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = badgeText,
                color = badgeColor,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
