package dev.knxfieldkit.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.app.AppController
import dev.knxfieldkit.app.AppUiState
import dev.knxfieldkit.model.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileFastCommanderSheet(
    state: AppUiState,
    controller: AppController,
    onDismiss: () -> Unit,
) {
    val project = state.project
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var inputGaText by remember { mutableStateOf(state.selectedGroupAddressInput.ifBlank { "1/1/1" }) }
    var tempInputVal by remember { mutableStateOf(21.0) }
    var dimmerVal by remember { mutableStateOf(50f) }
    var rawHexVal by remember { mutableStateOf("01") }

    val parsedGa = remember(inputGaText) { GroupAddress.parseOrNull(inputGaText) }
    val gaInfo = remember(parsedGa, project) { parsedGa?.let { project?.findGroupAddress(it) } }
    val liveEntry = remember(parsedGa, state.groupLiveValues) { parsedGa?.let { state.groupLiveValues[it] } }

    val resolvedDpt = gaInfo?.dpt
    val mainDpt = remember(resolvedDpt) { DptCodec.resolveMainDpt(resolvedDpt) }

    val isSwitch = mainDpt == MainDpt.DPT_1_BOOL ||
        gaInfo?.name?.contains("Schalten", ignoreCase = true) == true ||
        gaInfo?.name?.contains("Switch", ignoreCase = true) == true

    val isShutter = mainDpt == MainDpt.DPT_3_DIM_STEP ||
        gaInfo?.name?.contains("Jal", ignoreCase = true) == true ||
        gaInfo?.name?.contains("Blind", ignoreCase = true) == true ||
        gaInfo?.name?.contains("Shutter", ignoreCase = true) == true

    val isDimmer = mainDpt == MainDpt.DPT_5_PERCENT ||
        gaInfo?.name?.contains("Dimm", ignoreCase = true) == true ||
        gaInfo?.name?.contains("Wert", ignoreCase = true) == true

    val isTemperature = mainDpt == MainDpt.DPT_9_FLOAT ||
        gaInfo?.name?.contains("Temp", ignoreCase = true) == true ||
        gaInfo?.name?.contains("Sollwert", ignoreCase = true) == true

    val isScene = mainDpt == MainDpt.DPT_17_SCENE ||
        gaInfo?.name?.contains("Szene", ignoreCase = true) == true ||
        gaInfo?.name?.contains("Scene", ignoreCase = true) == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "ETS FAST GROUP COMMANDER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "1-tap live testing & direct bus telegram dispatch",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Target Group Address Input Field
            OutlinedTextField(
                value = inputGaText,
                onValueChange = { inputGaText = it.trim() },
                label = { Text("Group Address (e.g. 1/1/1, 1/2, or 2049)", fontSize = 11.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                trailingIcon = {
                    if (inputGaText.isNotEmpty()) {
                        IconButton(onClick = { inputGaText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )

            // Project Suggestions or Quick Picker Chips
            if (project != null && project.groupAddresses.isNotEmpty()) {
                val matchingGas = remember(inputGaText, project) {
                    val q = inputGaText.trim().lowercase()
                    if (q.isEmpty()) project.groupAddresses.values.take(8).toList()
                    else project.groupAddresses.values.filter {
                        it.address.toString().contains(q) || it.name.lowercase().contains(q)
                    }.take(8)
                }

                if (matchingGas.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(matchingGas, key = { it.address.raw }) { ga ->
                            val isSelected = ga.address.toString() == inputGaText
                            FilterChip(
                                selected = isSelected,
                                onClick = { inputGaText = ga.address.toString() },
                                label = {
                                    Text(
                                        text = "${ga.address} • ${ga.name.take(16)}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp),
                            )
                        }
                    }
                }
            }

            // Resolved Group Address Details Card
            if (parsedGa != null) {
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = parsedGa.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }

                                gaInfo?.dpt?.let { dpt ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = dpt,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            // Live Value Pill
                            val liveVal = liveEntry?.valueText
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (liveVal != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = liveVal ?: "No Response",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (liveVal != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Name & Description
                        Text(
                            text = gaInfo?.name ?: "Direct Bus / Unmapped Address",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (gaInfo != null && gaInfo.description.isNotBlank()) {
                            Text(
                                text = gaInfo.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Linked Communication Objects & Room Location
                        if (project != null && gaInfo != null) {
                            val linkedDevs = gaInfo.linkedObjects.mapNotNull { link -> link.deviceAddress?.let { project.findDevice(it) } }.distinct()
                            if (linkedDevs.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.DeviceHub, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "${linkedDevs.size} linked actuator(s): ${linkedDevs.joinToString { it.address.toString() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive 1-Tap Control Strip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "INTERACTIVE FIELD CONTROLS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // 1. DPT 1: Switch (ON / OFF / Toggle / Read)
                        if (isSwitch || (!isShutter && !isDimmer && !isTemperature && !isScene)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { controller.writeGroupSwitch(parsedGa.toString(), false) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                ) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("OFF", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { controller.writeGroupSwitch(parsedGa.toString(), true) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                ) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ON", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val isCurrentOn = liveEntry?.valueText?.equals("ON", ignoreCase = true) == true
                                        controller.writeGroupSwitch(parsedGa.toString(), !isCurrentOn)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                ) {
                                    Text("Toggle", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // 2. DPT 3 / Blinds: UP / STOP / DOWN
                        if (isShutter) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { controller.writeGroupSwitch(parsedGa.toString(), false) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(40.dp),
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("▲ UP", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { controller.writeGroupSwitch(parsedGa.toString(), true) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(40.dp),
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("STOP")
                                }
                                FilledTonalButton(
                                    onClick = { controller.writeGroupSwitch(parsedGa.toString(), true) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(40.dp),
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("▼ DOWN", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. DPT 5: Dimmer / Scaling (0..100%)
                        if (isDimmer) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Scaling Level", style = MaterialTheme.typography.bodySmall)
                                    Text("${dimmerVal.toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = dimmerVal,
                                    onValueChange = { dimmerVal = it },
                                    onValueChangeFinished = { controller.writeGroupPercent(parsedGa.toString(), dimmerVal.toDouble()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    listOf(0, 25, 50, 75, 100).forEach { pct ->
                                        FilledTonalButton(
                                            onClick = {
                                                dimmerVal = pct.toFloat()
                                                controller.writeGroupPercent(parsedGa.toString(), pct.toDouble())
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(28.dp),
                                        ) {
                                            Text("$pct%", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 4. DPT 9: Temperature / Float Stepper
                        if (isTemperature) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        tempInputVal = (tempInputVal - 0.5).coerceAtLeast(10.0)
                                        controller.writeGroupFloat16(parsedGa.toString(), tempInputVal)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text("-0.5°C", fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = String.format(Locale.US, "%.1f°C", tempInputVal),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                FilledTonalButton(
                                    onClick = {
                                        tempInputVal = (tempInputVal + 0.5).coerceAtMost(35.0)
                                        controller.writeGroupFloat16(parsedGa.toString(), tempInputVal)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text("+0.5°C", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 5. DPT 17: Scene Recall
                        if (isScene) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                (1..6).forEach { scn ->
                                    Button(
                                        onClick = { controller.writeGroupScene(parsedGa.toString(), scn) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(32.dp),
                                    ) {
                                        Text("S$scn", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Direct Bus Read Button
                        Button(
                            onClick = { controller.readGroupAddress(parsedGa.toString()) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("GroupValue_Read (Poll Bus Value)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
