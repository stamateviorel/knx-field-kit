package dev.knxfieldkit.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.knxfieldkit.model.*
import java.io.File

@Composable
fun ProjectExplorerScreen(
    state: AppUiState,
    controller: AppController,
    onPickFile: () -> Unit,
    onExportReport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = state.project?.name ?: "No Project Loaded",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "${state.projectSourceLabel} • ETS: ${state.project?.etsVersion?.ifBlank { "N/A" } ?: "N/A"} • Devices: ${state.project?.allDevices?.size ?: 0} • Group Addresses: ${state.project?.groupAddresses?.size ?: 0}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.project?.passwordProtected == true) {
                        Surface(color = MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.small) {
                            Text(
                                text = "UNLOCKED PROTECTED .KNXPROJ",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.background,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickFile) {
                        Text("Open .knxproj / .knxkeys")
                    }
                    OutlinedButton(onClick = {
                        controller.generateAndPromptEncryptedSample(
                            File(System.getProperty("java.io.tmpdir"), "ets6_protected_sample.knxproj")
                        )
                    }) {
                        Text("Test Encrypted ETS6 Sample (pw: knx2026)")
                    }
                    OutlinedButton(onClick = onExportReport, enabled = state.project != null) {
                        Text("Export Markdown Report")
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(0.44f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Topology (Areas / Lines / Devices)", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.project?.allLines.orEmpty()) { line ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Line ${line.id} — ${line.name} (${line.mediumType})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    line.devices.forEach { dev ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${dev.address}  ${dev.name}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                                Text(
                                                    text = "${dev.manufacturerName} ${dev.productName} [MV: ${dev.maskVersion ?: "?"}]",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            TextButton(onClick = { controller.pingIndividualDevice(dev.address.toString()) }) {
                                                Text("Ping")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.weight(0.56f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Group Addresses & Live Bus Values", style = MaterialTheme.typography.titleMedium)
                    var query by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search by address (1/2/3), name, or DPT (9.001)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val filteredGas = remember(state.project, query) {
                        state.project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty().filter { ga ->
                            query.isBlank() ||
                                ga.address.toString().contains(query, ignoreCase = true) ||
                                ga.name.contains(query, ignoreCase = true) ||
                                (ga.dpt?.contains(query, ignoreCase = true) == true)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredGas) { ga ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${ga.address} — ${ga.name}",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = "DPT: ${ga.dpt ?: "raw"} • Live: ${state.groupLiveValues[ga.address]?.valueText ?: "—"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { controller.readGroupAddress(ga.address.toString()) }) {
                                            Text("Read")
                                        }
                                        Button(onClick = {
                                            controller.selectGroupAddressForSender(ga.address.toString())
                                            controller.selectTab(AppTab.SENDER)
                                        }) {
                                            Text("Send...")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
