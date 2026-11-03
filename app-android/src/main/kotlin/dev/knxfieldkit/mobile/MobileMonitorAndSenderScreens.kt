package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import dev.knxfieldkit.app.AppTab
import dev.knxfieldkit.app.AppUiState
import dev.knxfieldkit.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MOBILE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

/**
 * Authentic ETS Group Monitor Screen (Gruppenmonitor).
 *
 * Includes live telegram recording, real-time traffic statistics, service filtering,
 * and an inline ETS Quick-Test bar to read and write group values without leaving the stream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMonitorScreen(
    state: AppUiState,
    controller: AppController,
    onShareCsv: () -> Unit,
    onInspectTelegram: (EnrichedTelegram) -> Unit,
) {
    val health = remember(state.telegrams) { BusHealthAnalyzer.analyze(state.telegrams) }
    val q = state.monitorSearchQuery.trim().lowercase()
    var apciFilter by remember { mutableStateOf("ALL") } // "ALL", "WRITE", "READ", "RESP"

    var quickTestGaText by remember(state.selectedGroupAddressInput) {
        mutableStateOf(state.selectedGroupAddressInput.ifBlank { "1/1/1" })
    }

    val filtered = remember(state.telegrams, q, state.monitorGhostOnly, apciFilter) {
        state.telegrams.filter { row ->
            if (state.monitorGhostOnly && !row.isUnmappedGroup) return@filter false
            if (apciFilter == "WRITE" && row.telegram.apci != ApciType.GROUP_WRITE) return@filter false
            if (apciFilter == "READ" && row.telegram.apci != ApciType.GROUP_READ) return@filter false
            if (apciFilter == "RESP" && row.telegram.apci != ApciType.GROUP_RESPONSE) return@filter false

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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 4-Tile Live TP1 Health Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val loadColor = when (health.loadLevel) {
                BusLoadLevel.STORM -> MaterialTheme.colorScheme.error
                BusLoadLevel.ELEVATED -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            MobileMetricTile(
                label = "TP1 Load",
                value = "${String.format(Locale.US, "%.1f", health.estimatedTp1LoadPercent)}%",
                accent = loadColor,
                modifier = Modifier.weight(1f),
            )
            MobileMetricTile(
                label = "Rate",
                value = "${String.format(Locale.US, "%.1f", health.telegramsPerSecond)}/s",
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            MobileMetricTile(
                label = "Ghost GA",
                value = health.ghostGroupCount.toString(),
                accent = if (health.ghostGroupCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MobileMetricTile(
                label = "Frames",
                value = state.telegrams.size.toString(),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }

        // ETS Pinned Inline Quick-Test / Quick-Send Bar
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = quickTestGaText,
                    onValueChange = { quickTestGaText = it },
                    placeholder = { Text("GA (1/1/1)", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(8.dp),
                )

                // Quick Read
                Button(
                    onClick = {
                        controller.selectGroupAddressForSender(quickTestGaText)
                        controller.readGroupAddress(quickTestGaText)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text("Read", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Quick OFF
                Button(
                    onClick = {
                        controller.selectGroupAddressForSender(quickTestGaText)
                        controller.writeGroupSwitch(quickTestGaText, false)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Quick ON
                Button(
                    onClick = {
                        controller.selectGroupAddressForSender(quickTestGaText)
                        controller.writeGroupSwitch(quickTestGaText, true)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("ON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search Field
        MobileSearchField(
            value = state.monitorSearchQuery,
            onValueChange = controller::setMonitorSearch,
            placeholder = "Filter GA, source device, value, APCI...",
        )

        // Filter Chips & Action Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                val filters = listOf("ALL", "WRITE", "READ", "RESP")
                items(filters) { f ->
                    val selected = apciFilter == f
                    FilterChip(
                        selected = selected,
                        onClick = { apciFilter = f },
                        label = { Text(f, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = state.monitorGhostOnly,
                        onClick = { controller.setMonitorGhostOnly(!state.monitorGhostOnly) },
                        label = { Text("Ghost Only", fontSize = 10.sp) },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { controller.setMonitorPaused(!state.monitorPaused) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (state.monitorPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.monitorPaused) "Resume" else "Pause",
                        tint = if (state.monitorPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { controller.clearMonitorLog() },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onShareCsv,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share CSV", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Live Telegrams List or Empty State
        if (filtered.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text("Waiting for KNX Bus Traffic", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Connect to a Wi-Fi KNXnet/IP Gateway or test a Group Address above to view live telegrams.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { controller.toggleConnectDialog(true) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Connect Gateway")
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
                    val badgeColor = when (t.apci) {
                        ApciType.GROUP_WRITE -> MaterialTheme.colorScheme.primaryContainer
                        ApciType.GROUP_RESPONSE -> MaterialTheme.colorScheme.secondaryContainer
                        ApciType.GROUP_READ -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val badgeFg = when (t.apci) {
                        ApciType.GROUP_WRITE -> MaterialTheme.colorScheme.onPrimaryContainer
                        ApciType.GROUP_RESPONSE -> MaterialTheme.colorScheme.onSecondaryContainer
                        ApciType.GROUP_READ -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectTelegram(row) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (row.isUnmappedGroup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                        ),
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Surface(shape = RoundedCornerShape(4.dp), color = badgeColor) {
                                    Text(
                                        text = apciShort,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeFg,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }

                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Text(
                                        text = "${t.source} → $dstAddr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                if (row.isUnmappedGroup) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text(
                                            text = "GHOST",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                if (row.decodedValue.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(
                                            text = row.decodedValue,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            val dstDesc = row.destinationName
                                ?: if (t.destinationGroup != null) "Unmapped Group" else "Tunnel / Management"
                            Text(
                                text = "${row.sourceDeviceName ?: "Bus Device"} → $dstDesc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * Authentic ETS Smart Sender Screen.
 *
 * Dedicated tool for technician testing of individual DPT datatypes:
 * Binary switches (DPT 1.x), step dimming (DPT 3.x), percent dimmers (DPT 5.x),
 * 2-byte floats (DPT 9.x), scenes (DPT 17.x), HVAC (DPT 20.x), and raw hex APDU.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Target Group Address Card
        ElevatedCard(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "TARGET GROUP ADDRESS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { showGaPicker = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pick Project GA", fontSize = 11.sp)
                    }
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
                        placeholder = { Text("e.g. 1/1/10") },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Button(
                        onClick = { controller.readGroupAddress(gaText) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
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
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "DPT ${gaInfo?.dpt ?: "any"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (liveVal != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "Bus: ${liveVal.valueText}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // Walk-Test Circuit Blinker Card
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (state.walkTestActive) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(
                1.dp,
                if (state.walkTestActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (state.walkTestActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Walk-Test Circuit Blinker", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (state.walkTestActive) "Toggling ${state.walkTestGroupAddress} (#${state.walkTestStepCount})" else "Auto-toggle circuit every 1.8s while walking site",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.walkTestActive) {
                    Button(
                        onClick = { controller.stopWalkTestBlink() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("STOP", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { controller.startWalkTestBlink(gaText) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Start Blink")
                    }
                }
            }
        }

        // DPT 1.001 Switch & DPT 3.007 Relative Dimming
        MobileSenderSectionCard(
            title = "DPT 1.001 Binary Switch & DPT 3.007 Step Dim",
            recommended = recommendedDpt == MainDpt.DPT_1_BOOL || recommendedDpt == MainDpt.DPT_3_DIM_STEP,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { controller.writeGroupSwitch(gaText, false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Write OFF (0)", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { controller.writeGroupSwitch(gaText, true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Write ON (1)", fontWeight = FontWeight.Bold)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { controller.writeGroupDimStep(gaText, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Dim −")
                }
                OutlinedButton(
                    onClick = { controller.writeGroupDimStep(gaText, false, 0) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Dim STOP")
                }
                OutlinedButton(
                    onClick = { controller.writeGroupDimStep(gaText, true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Dim +")
                }
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
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${pctSlider.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Button(
                    onClick = { controller.writeGroupPercent(gaText, pctSlider.toDouble()) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Send")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 25, 50, 75, 100).forEach { p ->
                    FilterChip(
                        selected = pctSlider.toInt() == p,
                        onClick = {
                            pctSlider = p.toFloat()
                            controller.writeGroupPercent(gaText, p.toDouble())
                        },
                        label = { Text("$p%", fontSize = 11.sp) },
                        shape = RoundedCornerShape(6.dp),
                    )
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
                    shape = RoundedCornerShape(8.dp),
                )
                Button(
                    onClick = { floatText.toDoubleOrNull()?.let { controller.writeGroupFloat16(gaText, it) } },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Send Float")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { sc ->
                    OutlinedButton(
                        onClick = { controller.writeGroupScene(gaText, sc) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Scene $sc", fontSize = 11.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HvacMode.entries.filter { it != HvacMode.AUTO }.forEach { mode ->
                    OutlinedButton(
                        onClick = { controller.writeGroupHvac(gaText, mode) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(mode.label.substringBefore(" "), fontSize = 11.sp)
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
                    shape = RoundedCornerShape(8.dp),
                )
                Button(
                    onClick = { controller.writeGroupString14(gaText, asciiText) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Send Text")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rawHexText,
                    onValueChange = { rawHexText = it },
                    label = { Text("Raw Hex Bytes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                )
                Button(
                    onClick = { controller.writeGroupRawHex(gaText, rawHexText, compact6Bit = false) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Send Raw")
                }
            }
        }
    }

    // Modal GA Picker Bottom Sheet
    if (showGaPicker) {
        val allGas = state.project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty()
        var pickerSearch by remember { mutableStateOf("") }
        val filteredPickerGas = remember(allGas, pickerSearch) {
            if (pickerSearch.isBlank()) allGas
            else allGas.filter { it.address.toString().contains(pickerSearch) || it.name.contains(pickerSearch, ignoreCase = true) }
        }

        ModalBottomSheet(
            onDismissRequest = { showGaPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Select Project Group Address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${filteredPickerGas.size} GAs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                OutlinedTextField(
                    value = pickerSearch,
                    onValueChange = { pickerSearch = it },
                    placeholder = { Text("Search GA or name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredPickerGas, key = { it.address.raw }) { ga ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = ga.address.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    text = ga.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = "DPT ${ga.dpt ?: "-"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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

@Composable
private fun MobileSenderSectionCard(
    title: String,
    recommended: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (recommended) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "MATCHES DPT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            content()
        }
    }
}
