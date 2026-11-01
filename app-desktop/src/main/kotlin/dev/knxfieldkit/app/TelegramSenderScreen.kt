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

@Composable
fun TelegramSenderScreen(state: AppUiState, controller: AppController) {
    val parsedGa = GroupAddress.parseOrNull(state.selectedGroupAddressInput)
    val gaInfo = parsedGa?.let { state.project?.findGroupAddress(it) }
    val matchedMainDpt = DptCodec.resolveMainDpt(gaInfo?.dpt)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.weight(0.56f).fillMaxHeight()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Smart Telegram Sender, Poller & Circuit Blinker", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = state.selectedGroupAddressInput,
                    onValueChange = controller::selectGroupAddressForSender,
                    label = { Text("Target Group Address (3-level 1/1/1, 2-level 1/257, or raw)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (gaInfo != null) {
                    val live = state.groupLiveValues[gaInfo.address]
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${gaInfo.address} — ${gaInfo.name}",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Detected Type: ${matchedMainDpt.label} [${gaInfo.dpt ?: "raw"}] • Live Value: ${live?.valueText ?: "—"} ${live?.latencyMs?.let { "(${it} ms)" }.orEmpty()}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (gaInfo.linkedObjects.isNotEmpty()) {
                                Text(
                                    text = "Linked Hardware: " + gaInfo.linkedObjects.joinToString { "${it.deviceAddress} (${it.name})" },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { controller.readGroupAddress(state.selectedGroupAddressInput) }) {
                        Text("Poll (GroupValue_Read)")
                    }
                    OutlinedButton(
                        onClick = controller::pollAllProjectGroupAddresses,
                        enabled = !state.isPollingBatch,
                    ) {
                        Text(if (state.isPollingBatch) "Polling All..." else "Poll All Project GAs")
                    }
                    if (state.walkTestActive) {
                        Button(
                            onClick = controller::stopWalkTestBlink,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("Stop Walk-Test (#${state.walkTestStepCount})")
                        }
                    } else {
                        OutlinedButton(onClick = { controller.startWalkTestBlink(state.selectedGroupAddressInput) }) {
                            Text("Start Walk-Test Blink (1.8s)")
                        }
                    }
                }

                HorizontalDivider()

                // DPT 1.001 + DPT 3.007 Lighting & Blinds Quick Row
                Text(
                    text = if (matchedMainDpt == MainDpt.DPT_1_BOOL || matchedMainDpt == MainDpt.DPT_3_DIM_STEP) "★ Recommended: Switch (DPT 1.001) & Relative Step (DPT 3.007)" else "Switch (DPT 1.001) & Relative Dim/Shutter Step (DPT 3.007)",
                    color = if (matchedMainDpt == MainDpt.DPT_1_BOOL || matchedMainDpt == MainDpt.DPT_3_DIM_STEP) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.writeGroupSwitch(state.selectedGroupAddressInput, true) }) {
                        Text("ON (1)")
                    }
                    OutlinedButton(onClick = { controller.writeGroupSwitch(state.selectedGroupAddressInput, false) }) {
                        Text("OFF (0)")
                    }
                    OutlinedButton(onClick = { controller.writeGroupDimStep(state.selectedGroupAddressInput, increase = true, stepCode = 3) }) {
                        Text("Brighter +25%")
                    }
                    OutlinedButton(onClick = { controller.writeGroupDimStep(state.selectedGroupAddressInput, increase = false, stepCode = 3) }) {
                        Text("Darker -25%")
                    }
                    TextButton(onClick = { controller.writeGroupDimStep(state.selectedGroupAddressInput, increase = false, stepCode = 0) }) {
                        Text("STOP")
                    }
                }

                // DPT 5.001 Percentage
                var sliderVal by remember { mutableStateOf(65f) }
                Text(
                    text = "${if (matchedMainDpt == MainDpt.DPT_5_PERCENT) "★ Recommended: " else ""}DPT 5.001 — Absolute Scaling (${sliderVal.toInt()} %)",
                    color = if (matchedMainDpt == MainDpt.DPT_5_PERCENT) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 25, 50, 75, 100).forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                sliderVal = preset.toFloat()
                                controller.writeGroupPercent(state.selectedGroupAddressInput, preset.toDouble())
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("$preset%")
                        }
                    }
                    Slider(
                        value = sliderVal,
                        onValueChange = { sliderVal = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { controller.writeGroupPercent(state.selectedGroupAddressInput, sliderVal.toDouble()) }) {
                        Text("Send")
                    }
                }

                // DPT 9.001 Float + DPT 16.001 Display String
                var floatText by remember { mutableStateOf("21.5") }
                var displayStr by remember { mutableStateOf("TEST OK") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${if (matchedMainDpt == MainDpt.DPT_9_FLOAT) "★ " else ""}DPT 9.001 Float (°C / Lux)",
                            color = if (matchedMainDpt == MainDpt.DPT_9_FLOAT) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = floatText,
                                onValueChange = { floatText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { controller.writeGroupFloat16(state.selectedGroupAddressInput, floatText.toDoubleOrNull() ?: 21.5) }) {
                                Text("Send")
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${if (matchedMainDpt == MainDpt.DPT_16_STRING) "★ " else ""}DPT 16.001 Panel Text (14ch)",
                            color = if (matchedMainDpt == MainDpt.DPT_16_STRING) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = displayStr,
                                onValueChange = { displayStr = it.take(14) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { controller.writeGroupString14(state.selectedGroupAddressInput, displayStr) }) {
                                Text("Send")
                            }
                        }
                    }
                }

                // DPT 17.001 Scene & DPT 20.102 HVAC
                var sceneText by remember { mutableStateOf("1") }
                Text("DPT 17.001 Scene & DPT 20.102 HVAC Mode", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = sceneText,
                        onValueChange = { sceneText = it },
                        label = { Text("Scene (1..64)") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    Button(onClick = { controller.writeGroupScene(state.selectedGroupAddressInput, sceneText.toIntOrNull() ?: 1) }) {
                        Text("Scene")
                    }
                    listOf(HvacMode.AUTO, HvacMode.COMFORT, HvacMode.STANDBY, HvacMode.ECONOMY).forEach { mode ->
                        OutlinedButton(
                            onClick = { controller.writeGroupHvac(state.selectedGroupAddressInput, mode) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(mode.label)
                        }
                    }
                }

                // Raw APDU Hex
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
                    OutlinedButton(onClick = { controller.writeGroupRawHex(state.selectedGroupAddressInput, hexPayload, compact6Bit) }) {
                        Text("Send Raw")
                    }
                }
            }
        }

        // Right Card: Quick Pick Project Group Addresses with Inline Toggle/Poll
        Card(Modifier.weight(0.44f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Project Group Addresses (Click to Select)", style = MaterialTheme.typography.titleMedium)
                var filter by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Filter GAs by address or name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val itemsList = remember(state.project, filter) {
                    state.project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty().filter {
                        filter.isBlank() ||
                            it.address.toString().contains(filter, ignoreCase = true) ||
                            it.name.contains(filter, ignoreCase = true)
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(itemsList) { ga ->
                        val live = state.groupLiveValues[ga.address]
                        val selected = parsedGa == ga.address
                        Surface(
                            onClick = { controller.selectGroupAddressForSender(ga.address.toString()) },
                            color = if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${ga.address} — ${ga.name}", style = MaterialTheme.typography.bodyMedium)
                                    Text("DPT ${ga.dpt ?: "raw"} • ${ga.linkedObjects.size} linked obj", style = MaterialTheme.typography.bodySmall)
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
