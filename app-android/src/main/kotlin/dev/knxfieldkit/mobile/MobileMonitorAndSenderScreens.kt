package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.app.AppController
import dev.knxfieldkit.app.AppTab
import dev.knxfieldkit.app.AppUiState
import dev.knxfieldkit.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MOBILE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

@Composable
fun MobileMonitorScreen(
    state: AppUiState,
    controller: AppController,
    onShareCsv: () -> Unit,
    onInspectTelegram: (EnrichedTelegram) -> Unit,
) {
    val health = remember(state.telegrams) { BusHealthAnalyzer.analyze(state.telegrams) }
    val q = state.monitorSearchQuery.trim().lowercase()
    val filtered = remember(state.telegrams, q, state.monitorGhostOnly) {
        state.telegrams.filter { row ->
            if (state.monitorGhostOnly && !row.isUnmappedGroup) return@filter false
            if (q.isEmpty()) return@filter true
            val dst = row.telegram.destinationGroup?.toString() ?: row.telegram.destinationIndividual?.toString().orEmpty()
            row.telegram.source.toString().contains(q) ||
                dst.contains(q) ||
                (row.sourceDeviceName?.lowercase()?.contains(q) == true) ||
                (row.destinationName?.lowercase()?.contains(q) == true) ||
                row.decodedValue.lowercase().contains(q) ||
                row.telegram.apci.label.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MobileColors.BgCanvas)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 4-Tile Live TP1 Health Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val loadAccent = when (health.loadLevel) {
                BusLoadLevel.STORM -> MobileColors.AccentRed
                BusLoadLevel.ELEVATED -> MobileColors.AccentAmber
                else -> MobileColors.AccentEmerald
            }
            MobileMetricTile(
                label = "TP1 Load",
                value = "${String.format(Locale.US, "%.1f", health.estimatedTp1LoadPercent)}%",
                accent = loadAccent,
                modifier = Modifier.weight(1f),
            )
            MobileMetricTile(
                label = "Rate",
                value = "${String.format(Locale.US, "%.1f", health.telegramsPerSecond)}/s",
                accent = MobileColors.AccentSky,
                modifier = Modifier.weight(1f),
            )
            MobileMetricTile(
                label = "Ghost GA",
                value = health.ghostGroupCount.toString(),
                accent = if (health.ghostGroupCount > 0) MobileColors.AccentAmber else MobileColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            MobileMetricTile(
                label = "Frames",
                value = state.telegrams.size.toString(),
                accent = MobileColors.AccentPurple,
                modifier = Modifier.weight(1f),
            )
        }

        // Search & Action Controls
        MobileSearchField(
            value = state.monitorSearchQuery,
            onValueChange = controller::setMonitorSearch,
            placeholder = "Filter GA, source, value, APCI...",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MobileActionPill(
                    label = if (state.monitorPaused) "Resume" else "Pause",
                    accent = if (state.monitorPaused) MobileColors.AccentAmber else MobileColors.AccentEmerald,
                ) {
                    controller.setMonitorPaused(!state.monitorPaused)
                }
                MobileActionPill(
                    label = if (state.monitorGhostOnly) "Ghost: ON" else "Ghost Only",
                    accent = if (state.monitorGhostOnly) MobileColors.AccentAmber else MobileColors.TextSecondary,
                ) {
                    controller.setMonitorGhostOnly(!state.monitorGhostOnly)
                }
                MobileActionPill("Clear", MobileColors.TextSecondary) {
                    controller.clearMonitorLog()
                }
            }
            MobileActionPill("Share CSV", MobileColors.AccentSky, onClick = onShareCsv)
        }

        if (filtered.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MobileColors.BgCard,
                border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = MobileColors.AccentSky, modifier = Modifier.size(28.dp))
                    Text("Waiting for KNX Bus Traffic", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                    Text(
                        "Connect to a Wi-Fi KNXnet/IP Gateway to monitor live telegrams in real time.",
                        fontSize = 12.sp,
                        color = MobileColors.TextSecondary,
                    )
                    MobileActionPill("Connect Gateway", MobileColors.AccentSky) {
                        controller.toggleConnectDialog(true)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.telegram.id }) { row ->
                    val t = row.telegram
                    val dstAddr = t.destinationGroup?.toString() ?: t.destinationIndividual?.toString() ?: "Broadcast"
                    val apciShort = when (t.apci) {
                        ApciType.GROUP_WRITE -> "WRITE"
                        ApciType.GROUP_READ -> "READ"
                        ApciType.GROUP_RESPONSE -> "RESP"
                        ApciType.DEVICE_DESCRIPTOR_READ -> "DD0?"
                        ApciType.DEVICE_DESCRIPTOR_RESPONSE -> "DD0!"
                        ApciType.INDIVIDUAL_ADDR_READ -> "PROG?"
                        ApciType.INDIVIDUAL_ADDR_RESPONSE -> "PROG!"
                        ApciType.OTHER -> "MGMT"
                    }
                    val apciAccent = when (t.apci) {
                        ApciType.GROUP_WRITE -> MobileColors.AccentEmerald
                        ApciType.GROUP_RESPONSE -> MobileColors.AccentSky
                        ApciType.GROUP_READ -> MobileColors.AccentAmber
                        else -> MobileColors.AccentPurple
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MobileColors.BgCard,
                        border = BorderStroke(
                            1.dp,
                            if (row.isUnmappedGroup) MobileColors.AccentAmber.copy(alpha = 0.6f) else MobileColors.BorderSubtle,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectTelegram(row) },
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = MOBILE_TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs)),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MobileColors.TextMuted,
                                )
                                MobileBadge(apciShort, apciAccent)
                                MobileBadge("${t.source} → $dstAddr", MobileColors.AccentSky)
                                Spacer(Modifier.weight(1f))
                                if (row.isUnmappedGroup) {
                                    MobileBadge("GHOST", MobileColors.AccentAmber)
                                }
                                if (row.decodedValue.isNotBlank()) {
                                    MobileBadge(row.decodedValue, MobileColors.AccentEmerald)
                                }
                            }
                            val dstDesc = row.destinationName
                                ?: if (t.destinationGroup != null) "Unmapped Group" else "Tunnel / Management"
                            Text(
                                text = "${row.sourceDeviceName ?: "Bus Device"} → $dstDesc",
                                fontSize = 11.sp,
                                color = MobileColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileSenderScreen(
    state: AppUiState,
    controller: AppController,
) {
    val gaText = state.selectedGroupAddressInput
    val parsedGa = GroupAddress.parseOrNull(gaText)
    val gaInfo = parsedGa?.let { state.project?.findGroupAddress(it) }
    val liveVal = parsedGa?.let { state.groupLiveValues[it] }
    val recommendedDpt = DptCodec.resolveMainDpt(gaInfo?.dpt)

    var showGaPicker by remember { mutableStateOf(false) }
    var pctSlider by remember { mutableStateOf(65f) }
    var floatText by remember { mutableStateOf("21.5") }
    var asciiText by remember { mutableStateOf("FIELD OK") }
    var rawHexText by remember { mutableStateOf("01") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MobileColors.BgCanvas)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Target Group Address Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MobileColors.BgCardElevated,
                border = BorderStroke(1.dp, MobileColors.BorderStrong),
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
                        Text("TARGET GROUP ADDRESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextSecondary)
                        MobileActionPill("Pick Project GA", MobileColors.AccentSky) { showGaPicker = true }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = gaText,
                            onValueChange = controller::selectGroupAddressForSender,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MobileColors.TextPrimary,
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MobileColors.BgInput,
                                unfocusedContainerColor = MobileColors.BgInput,
                                focusedBorderColor = MobileColors.AccentEmerald,
                                unfocusedBorderColor = MobileColors.BorderSubtle,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { controller.readGroupAddress(gaText) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = Color(0xFF082F49)),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("GroupRead", fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = gaInfo?.name ?: "Manual / Unmapped GA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MobileColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        MobileBadge("DPT ${gaInfo?.dpt ?: "any"}", MobileColors.AccentPurple)
                        if (liveVal != null) {
                            MobileBadge("Bus: ${liveVal.valueText}", MobileColors.AccentEmerald)
                        }
                    }
                }
            }

            // Walk-Test Solo Technician Circuit Blinker
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (state.walkTestActive) MobileColors.AccentAmber.copy(alpha = 0.14f) else MobileColors.BgCard,
                border = BorderStroke(1.dp, if (state.walkTestActive) MobileColors.AccentAmber else MobileColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = MobileColors.AccentAmber)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Walk-Test Circuit Blinker", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                        Text(
                            text = if (state.walkTestActive) "Toggling ${state.walkTestGroupAddress} (#${state.walkTestStepCount})" else "Auto-toggle light/relay every 1.8s while walking floor",
                            fontSize = 11.sp,
                            color = MobileColors.TextSecondary,
                        )
                    }
                    if (state.walkTestActive) {
                        MobileActionPill("STOP", MobileColors.AccentRed) { controller.stopWalkTestBlink() }
                    } else {
                        MobileActionPill("Start Blink", MobileColors.AccentAmber) { controller.startWalkTestBlink(gaText) }
                    }
                }
            }

            // DPT 1.x Switch & DPT 3.007 Relative Dimming
            MobileSenderSectionCard(
                title = "DPT 1.001 Binary Switch & DPT 3.007 Step Dim",
                recommended = recommendedDpt == MainDpt.DPT_1_BOOL || recommendedDpt == MainDpt.DPT_3_DIM_STEP,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { controller.writeGroupSwitch(gaText, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MobileColors.BgCardElevated, contentColor = MobileColors.TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Write OFF (0)", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { controller.writeGroupSwitch(gaText, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = Color(0xFF042F2E)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Write ON (1)", fontWeight = FontWeight.Bold)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MobileActionPill("Dim −", MobileColors.TextSecondary) { controller.writeGroupDimStep(gaText, false) }
                    MobileActionPill("Dim STOP", MobileColors.AccentAmber) { controller.writeGroupDimStep(gaText, false, 0) }
                    MobileActionPill("Dim +", MobileColors.AccentSky) { controller.writeGroupDimStep(gaText, true) }
                }
            }

            // DPT 5.001 Percentage / Dimmer / Shutter
            MobileSenderSectionCard(
                title = "DPT 5.001 Percentage (0..100%)",
                recommended = recommendedDpt == MainDpt.DPT_5_PERCENT || recommendedDpt == MainDpt.DPT_5_UNSIGNED,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = pctSlider,
                        onValueChange = { pctSlider = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                    )
                    MobileBadge("${pctSlider.toInt()}%", MobileColors.AccentEmerald)
                    MobileActionPill("Send", MobileColors.AccentEmerald) {
                        controller.writeGroupPercent(gaText, pctSlider.toDouble())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 25, 50, 75, 100).forEach { p ->
                        MobileActionPill("$p%", MobileColors.AccentSky) {
                            pctSlider = p.toFloat()
                            controller.writeGroupPercent(gaText, p.toDouble())
                        }
                    }
                }
            }

            // DPT 9.x 2-Byte Float & DPT 17/20 Scene + HVAC
            MobileSenderSectionCard(
                title = "DPT 9.001 Float (°C / Lux) • Scene • HVAC",
                recommended = recommendedDpt == MainDpt.DPT_9_FLOAT || recommendedDpt == MainDpt.DPT_17_SCENE || recommendedDpt == MainDpt.DPT_20_HVAC,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = floatText,
                        onValueChange = { floatText = it },
                        label = { Text("2-Byte Float") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { floatText.toDoubleOrNull()?.let { controller.writeGroupFloat16(gaText, it) } },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Send Float")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { sc ->
                        MobileActionPill("Scene $sc", MobileColors.AccentPurple) { controller.writeGroupScene(gaText, sc) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HvacMode.entries.filter { it != HvacMode.AUTO }.forEach { mode ->
                        MobileActionPill(mode.label.substringBefore(" "), MobileColors.AccentSky) {
                            controller.writeGroupHvac(gaText, mode)
                        }
                    }
                }
            }

            // DPT 16.001 Text & Raw Hex
            MobileSenderSectionCard(
                title = "DPT 16.001 Display Text & Raw APDU Hex",
                recommended = recommendedDpt == MainDpt.DPT_16_STRING,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = asciiText,
                        onValueChange = { asciiText = it.take(14) },
                        label = { Text("14-Char ASCII") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    MobileActionPill("Send Text", MobileColors.AccentEmerald) {
                        controller.writeGroupString14(gaText, asciiText)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = rawHexText,
                        onValueChange = { rawHexText = it },
                        label = { Text("Raw Hex Bytes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    MobileActionPill("Send Raw", MobileColors.AccentAmber) {
                        controller.writeGroupRawHex(gaText, rawHexText, compact6Bit = false)
                    }
                }
            }
        }

        if (showGaPicker) {
            val allGas = state.project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty()
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                color = MobileColors.BgTopBar,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Select Project Group Address", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                        MobileActionPill("Close", MobileColors.TextSecondary) { showGaPicker = false }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allGas, key = { it.address.raw }) { ga ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MobileColors.BgCard,
                                border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        controller.selectGroupAddressForSender(ga.address.toString())
                                        showGaPicker = false
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MobileBadge(ga.address.toString(), MobileColors.AccentSky)
                                    Text(ga.name, fontSize = 13.sp, color = MobileColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    MobileBadge("DPT ${ga.dpt ?: "-"}", MobileColors.AccentPurple)
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
private fun MobileSenderSectionCard(
    title: String,
    recommended: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MobileColors.BgCard,
        border = BorderStroke(
            1.dp,
            if (recommended) MobileColors.AccentEmerald.copy(alpha = 0.7f) else MobileColors.BorderSubtle,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MobileColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (recommended) {
                    MobileBadge("MATCHES DPT", MobileColors.AccentEmerald)
                }
            }
            content()
        }
    }
}
