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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.knxfieldkit.model.*

@Composable
fun TelegramSenderScreen(state: AppUiState, controller: AppController) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.weight(0.54f).fillMaxHeight()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Interactive Telegram Sender & Group Poller", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = state.selectedGroupAddressInput,
                    onValueChange = controller::selectGroupAddressForSender,
                    label = { Text("Target Group Address (3-level 1/1/1, 2-level 1/257, or raw)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val parsedGa = GroupAddress.parseOrNull(state.selectedGroupAddressInput)
                val gaInfo = parsedGa?.let { state.project?.findGroupAddress(it) }
                if (gaInfo != null) {
                    val live = state.groupLiveValues[gaInfo.address]
                    Text(
                        text = "Matched GA: ${gaInfo.name} [DPT ${gaInfo.dpt ?: "raw"}] • Live: ${live?.valueText ?: "—"} ${live?.latencyMs?.let { "(${it} ms)" }.orEmpty()}",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.readGroupAddress(state.selectedGroupAddressInput) }) {
                        Text("Send GroupValue_Read (Poll)")
                    }
                    OutlinedButton(
                        onClick = controller::pollAllProjectGroupAddresses,
                        enabled = !state.isPollingBatch,
                    ) {
                        Text(if (state.isPollingBatch) "Polling Bus..." else "Batch Poll All Project GAs")
                    }
                }

                HorizontalDivider()
                Text("DPT 1.001 — Switch / Binary Trigger", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.writeGroupSwitch(state.selectedGroupAddressInput, true) }) {
                        Text("Write ON (1)")
                    }
                    OutlinedButton(onClick = { controller.writeGroupSwitch(state.selectedGroupAddressInput, false) }) {
                        Text("Write OFF (0)")
                    }
                }

                var sliderVal by remember { mutableStateOf(65f) }
                Text("DPT 5.001 — Scaling (${sliderVal.toInt()} %)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = sliderVal,
                        onValueChange = { sliderVal = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { controller.writeGroupPercent(state.selectedGroupAddressInput, sliderVal.toDouble()) }) {
                        Text("Write %")
                    }
                }

                var floatText by remember { mutableStateOf("21.5") }
                Text("DPT 9.001 — 2-Byte Float (°C / Lux)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = floatText,
                        onValueChange = { floatText = it },
                        label = { Text("Float Value") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        controller.writeGroupFloat16(state.selectedGroupAddressInput, floatText.toDoubleOrNull() ?: 21.5)
                    }) {
                        Text("Write Float")
                    }
                }

                var sceneText by remember { mutableStateOf("1") }
                Text("DPT 17.001 Scene & DPT 20.102 HVAC Mode", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sceneText,
                        onValueChange = { sceneText = it },
                        label = { Text("Scene (1..64)") },
                        singleLine = true,
                        modifier = Modifier.width(130.dp),
                    )
                    Button(onClick = {
                        controller.writeGroupScene(state.selectedGroupAddressInput, sceneText.toIntOrNull() ?: 1)
                    }) {
                        Text("Send Scene")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(HvacMode.AUTO, HvacMode.COMFORT, HvacMode.STANDBY, HvacMode.ECONOMY).forEach { mode ->
                        OutlinedButton(onClick = { controller.writeGroupHvac(state.selectedGroupAddressInput, mode) }) {
                            Text(mode.label)
                        }
                    }
                }

                var hexPayload by remember { mutableStateOf("01") }
                var compact6Bit by remember { mutableStateOf(false) }
                Text("Raw APDU Hex Payload", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hexPayload,
                        onValueChange = { hexPayload = it },
                        label = { Text("Hex Bytes (e.g. 0C 1A)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(checked = compact6Bit, onCheckedChange = { compact6Bit = it })
                    Text("6-bit", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        controller.writeGroupRawHex(state.selectedGroupAddressInput, hexPayload, compact6Bit)
                    }) {
                        Text("Send Raw")
                    }
                }
            }
        }

        Card(Modifier.weight(0.46f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick Pick Group Address from Project", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty()) { ga ->
                        val live = state.groupLiveValues[ga.address]
                        Surface(
                            onClick = { controller.selectGroupAddressForSender(ga.address.toString()) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${ga.address} — ${ga.name}", style = MaterialTheme.typography.bodyMedium)
                                    Text("DPT ${ga.dpt ?: "raw"}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = live?.let { "${it.valueText} ${it.latencyMs?.let { ms -> "(${ms}ms)" }.orEmpty()}" } ?: "—",
                                    color = MaterialTheme.colorScheme.primary,
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
