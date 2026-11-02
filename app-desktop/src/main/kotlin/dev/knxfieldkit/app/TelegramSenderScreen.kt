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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
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
fun TelegramSenderScreen(state: AppUiState, controller: AppController) {
    val parsedGa = GroupAddress.parseOrNull(state.selectedGroupAddressInput)
    val gaInfo = parsedGa?.let { state.project?.findGroupAddress(it) }
    val matchedMainDpt = DptCodec.resolveMainDpt(gaInfo?.dpt)

    fun ensureBus() {
        if (state.connectionState !is ConnectionState.Connected) controller.toggleConnectDialog(true)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Left Workbench Panel: Interactive DPT Encoder & Walk-Test Blinker
        WorkbenchPanel(
            title = "Smart DPT Telegram Transmitter, Poller & Walk-Test Blinker",
            icon = Icons.Default.Send,
            modifier = Modifier.weight(0.56f).fillMaxHeight(),
            headerActions = {
                DesktopPrimaryButton(
                    text = "Poll GA",
                    icon = Icons.Default.Sync,
                    onClick = { ensureBus(); controller.readGroupAddress(state.selectedGroupAddressInput) },
                    compact = true,
                )
                DesktopButton(
                    text = if (state.isPollingBatch) "Polling All..." else "Poll All Project GAs",
                    onClick = { ensureBus(); controller.pollAllProjectGroupAddresses() },
                    enabled = !state.isPollingBatch,
                    compact = true,
                )
                if (state.walkTestActive) {
                    DesktopPrimaryButton(
                        text = "Stop Blink (#${state.walkTestStepCount})",
                        icon = Icons.Default.Stop,
                        color = WorkbenchColors.AccentAmber,
                        onClick = controller::stopWalkTestBlink,
                        compact = true,
                    )
                } else {
                    DesktopButton(
                        text = "Walk-Test Blink (1.8s)",
                        icon = Icons.Default.FlashOn,
                        onClick = { controller.startWalkTestBlink(state.selectedGroupAddressInput) },
                        accentColor = WorkbenchColors.AccentAmber,
                        compact = true,
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Target GA Bar + Context Card
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactInputField(
                        value = state.selectedGroupAddressInput,
                        onValueChange = controller::selectGroupAddressForSender,
                        prefixLabel = "Target Group Address",
                        placeholder = "1/1/1",
                        modifier = Modifier.weight(1f),
                    )
                    if (gaInfo != null) {
                        DptBadge(gaInfo.dpt)
                        LiveValueBadge(state.groupLiveValues[gaInfo.address]?.valueText, state.groupLiveValues[gaInfo.address]?.latencyMs)
                    }
                }

                if (gaInfo != null) {
                    Surface(
                        color = WorkbenchColors.BgCanvas,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, WorkbenchColors.BorderSubtle),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "${gaInfo.address} — ${gaInfo.name}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WorkbenchColors.AccentSky,
                            )
                            Text(
                                text = "Detected Datapoint: ${matchedMainDpt.label} [${gaInfo.dpt ?: "raw"}] · ${gaInfo.description.ifBlank { "No description" }}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = WorkbenchColors.TextSecondary,
                            )
                            if (gaInfo.linkedObjects.isNotEmpty()) {
                                Text(
                                    text = "Connected Hardware: " + gaInfo.linkedObjects.joinToString { "${it.deviceAddress} (#${it.objectNumber ?: 0} ${it.name})" },
                                    fontSize = 10.sp,
                                    color = WorkbenchColors.TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // 1. DPT 1.001 Switch & DPT 3.007 Relative Dimming / Shutter Step
                SenderSectionBox(
                    title = "DPT 1.001 Switch & DPT 3.007 Relative Dimming / Blinds Step",
                    highlighted = matchedMainDpt == MainDpt.DPT_1_BOOL || matchedMainDpt == MainDpt.DPT_3_DIM_STEP,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopPrimaryButton("ON (1)", icon = Icons.Default.Lightbulb, onClick = { ensureBus(); controller.writeGroupSwitch(state.selectedGroupAddressInput, true) })
                        DesktopButton("OFF (0)", onClick = { ensureBus(); controller.writeGroupSwitch(state.selectedGroupAddressInput, false) })
                        Spacer(Modifier.width(6.dp))
                        DesktopButton("Brighter +25%", onClick = { ensureBus(); controller.writeGroupDimStep(state.selectedGroupAddressInput, increase = true, stepCode = 3) })
                        DesktopButton("Darker -25%", onClick = { ensureBus(); controller.writeGroupDimStep(state.selectedGroupAddressInput, increase = false, stepCode = 3) })
                        DesktopButton("STOP", onClick = { ensureBus(); controller.writeGroupDimStep(state.selectedGroupAddressInput, increase = false, stepCode = 0) })
                    }
                }

                // 2. DPT 5.001 Percentage Scaling
                var sliderVal by remember { mutableStateOf(65f) }
                SenderSectionBox(
                    title = "DPT 5.001 Absolute Scaling (${sliderVal.toInt()}%)",
                    highlighted = matchedMainDpt == MainDpt.DPT_5_PERCENT || matchedMainDpt == MainDpt.DPT_5_UNSIGNED,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(0, 25, 50, 75, 100).forEach { preset ->
                            DesktopButton(
                                text = "$preset%",
                                onClick = {
                                    ensureBus()
                                    sliderVal = preset.toFloat()
                                    controller.writeGroupPercent(state.selectedGroupAddressInput, preset.toDouble())
                                },
                                active = sliderVal.toInt() == preset,
                                compact = true,
                            )
                        }
                        Slider(
                            value = sliderVal,
                            onValueChange = { sliderVal = it },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f).height(24.dp),
                        )
                        DesktopPrimaryButton("Send %", onClick = { ensureBus(); controller.writeGroupPercent(state.selectedGroupAddressInput, sliderVal.toDouble()) }, compact = true)
                    }
                }

                // 3. DPT 9.001 2-Byte Float & DPT 16.001 14-Byte ASCII/ISO String
                var floatText by remember { mutableStateOf("21.5") }
                var displayStr by remember { mutableStateOf("VILLA OK") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SenderSectionBox(
                        title = "DPT 9.001 2-Byte Float (°C / Lux)",
                        highlighted = matchedMainDpt == MainDpt.DPT_9_FLOAT,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactInputField(value = floatText, onValueChange = { floatText = it }, prefixLabel = "Val", modifier = Modifier.weight(1f))
                            DesktopPrimaryButton("Send", onClick = { ensureBus(); controller.writeGroupFloat16(state.selectedGroupAddressInput, floatText.toDoubleOrNull() ?: 21.5) }, compact = true)
                        }
                    }
                    SenderSectionBox(
                        title = "DPT 16.001 Panel Text (14-Char)",
                        highlighted = matchedMainDpt == MainDpt.DPT_16_STRING,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactInputField(value = displayStr, onValueChange = { displayStr = it.take(14) }, prefixLabel = "Txt", monospace = true, modifier = Modifier.weight(1f))
                            DesktopPrimaryButton("Send", onClick = { ensureBus(); controller.writeGroupString14(state.selectedGroupAddressInput, displayStr) }, compact = true)
                        }
                    }
                }

                // 4. DPT 17.001 Scene & DPT 20.102 HVAC Operating Mode
                var sceneText by remember { mutableStateOf("1") }
                SenderSectionBox(
                    title = "DPT 17.001 Scene (1..64) & DPT 20.102 HVAC Mode",
                    highlighted = matchedMainDpt == MainDpt.DPT_17_SCENE || matchedMainDpt == MainDpt.DPT_20_HVAC,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CompactInputField(value = sceneText, onValueChange = { sceneText = it }, prefixLabel = "Scene", modifier = Modifier.width(96.dp))
                        DesktopPrimaryButton("Recall", onClick = { ensureBus(); controller.writeGroupScene(state.selectedGroupAddressInput, sceneText.toIntOrNull() ?: 1) }, compact = true)
                        Spacer(Modifier.width(4.dp))
                        listOf(HvacMode.AUTO, HvacMode.COMFORT, HvacMode.STANDBY, HvacMode.ECONOMY).forEach { mode ->
                            DesktopButton(
                                text = mode.label,
                                onClick = { ensureBus(); controller.writeGroupHvac(state.selectedGroupAddressInput, mode) },
                                compact = true,
                            )
                        }
                    }
                }

                // 5. Raw CEMI APDU Hex
                var hexPayload by remember { mutableStateOf("01") }
                var compact6Bit by remember { mutableStateOf(false) }
                SenderSectionBox(
                    title = "Raw APDU Hex Payload Injection",
                    highlighted = matchedMainDpt == MainDpt.RAW_BYTES,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactInputField(value = hexPayload, onValueChange = { hexPayload = it }, prefixLabel = "Hex", placeholder = "0C 1A", modifier = Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { compact6Bit = !compact6Bit },
                        ) {
                            Checkbox(checked = compact6Bit, onCheckedChange = { compact6Bit = it }, modifier = Modifier.size(16.dp))
                            Text("6-bit APCI", fontSize = 10.sp, color = WorkbenchColors.TextSecondary)
                        }
                        DesktopButton("Send Raw Hex", onClick = { ensureBus(); controller.writeGroupRawHex(state.selectedGroupAddressInput, hexPayload, compact6Bit) }, compact = true)
                    }
                }
            }
        }

        // Right Workbench Panel: Project Group Address Picker Table
        var filter by remember { mutableStateOf("") }
        val itemsList = remember(state.project, filter) {
            state.project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty().filter {
                filter.isBlank() ||
                    it.address.toString().contains(filter, ignoreCase = true) ||
                    it.name.contains(filter, ignoreCase = true)
            }
        }
        WorkbenchPanel(
            title = "Project Group Addresses",
            icon = Icons.Default.FolderOpen,
            badgeText = "${itemsList.size} GAs",
            modifier = Modifier.weight(0.44f).fillMaxHeight(),
            headerActions = {
                CompactSearchField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = "Filter GAs...",
                    modifier = Modifier.width(155.dp),
                )
            },
        ) {
            DenseTableHeader(
                listOf(
                    "Address" to 0.16f,
                    "Group Name" to 0.44f,
                    "DPT" to 0.16f,
                    "Live Value" to 0.24f,
                )
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(itemsList, key = { _, g -> g.address.raw }) { idx, ga ->
                    val live = state.groupLiveValues[ga.address]
                    val selected = parsedGa == ga.address
                    val rowBg = when {
                        selected -> WorkbenchColors.BgSelected
                        idx % 2 == 1 -> WorkbenchColors.BgRowAlt
                        else -> WorkbenchColors.BgPanel
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(29.dp)
                            .background(rowBg)
                            .clickable { controller.selectGroupAddressForSender(ga.address.toString()) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = ga.address.toString(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = WorkbenchColors.AccentEmerald,
                            modifier = Modifier.weight(0.16f),
                        )
                        Text(
                            text = ga.name,
                            fontSize = 11.sp,
                            color = WorkbenchColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.44f).padding(end = 6.dp),
                        )
                        Box(modifier = Modifier.weight(0.16f)) {
                            DptBadge(ga.dpt)
                        }
                        Box(modifier = Modifier.weight(0.24f)) {
                            LiveValueBadge(live?.valueText, live?.latencyMs)
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun SenderSectionBox(
    title: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (highlighted) WorkbenchColors.AccentEmerald.copy(alpha = 0.06f) else WorkbenchColors.BgCanvas,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(
            1.dp,
            if (highlighted) WorkbenchColors.AccentEmerald.copy(alpha = 0.50f) else WorkbenchColors.BorderSubtle,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (highlighted) {
                    StatusPill("RECOMMENDED", WorkbenchColors.AccentEmerald)
                }
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlighted) WorkbenchColors.AccentEmerald else WorkbenchColors.TextSecondary,
                )
            }
            content()
        }
    }
}
