package dev.knxfieldkit.mobile



import androidx.compose.animation.AnimatedVisibility

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.LazyRow

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.itemsIndexed

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

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.Path

import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.ui.graphics.drawscope.Stroke

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
    var securityFilter by remember { mutableStateOf("ALL") } // "ALL", "SECURE", "PLAIN"

    var showTopTalkers by remember { mutableStateOf(false) }

    var trafficHistory by remember { mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)) }



    LaunchedEffect(health.telegramsPerSecond) {

        val current = health.telegramsPerSecond.toFloat()

        trafficHistory = (trafficHistory + current).takeLast(40)

    }



    var quickTestGaText by remember(state.selectedGroupAddressInput) {

        mutableStateOf(state.selectedGroupAddressInput.ifBlank { "1/1/1" })

    }



    val filtered = remember(state.telegrams, q, state.monitorGhostOnly, apciFilter, securityFilter, state.keyring) {

        state.telegrams.filter { row ->

            if (state.monitorGhostOnly && !row.isUnmappedGroup) return@filter false

            if (apciFilter == "WRITE" && row.telegram.apci != ApciType.GROUP_WRITE) return@filter false

            if (apciFilter == "READ" && row.telegram.apci != ApciType.GROUP_READ) return@filter false

            if (apciFilter == "RESP" && row.telegram.apci != ApciType.GROUP_RESPONSE) return@filter false

            val isSecTg = row.telegram.isSecure || (row.telegram.destinationGroup?.let { state.project?.findGroupAddress(it)?.isSecure == true || state.keyring?.isGroupSecure(it) == true } == true)
            if (securityFilter == "SECURE" && !isSecTg) return@filter false
            if (securityFilter == "PLAIN" && isSecTg) return@filter false



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

            val retryColor = when {

                health.retryRatePercent > 4.0 -> MaterialTheme.colorScheme.error

                health.repeatedCount > 0 -> MaterialTheme.colorScheme.tertiary

                else -> MaterialTheme.colorScheme.primary

            }

            MobileMetricTile(

                label = "Retries",

                value = if (health.repeatedCount > 0) "${String.format(Locale.US, "%.1f", health.retryRatePercent)}%" else "0%",

                accent = retryColor,

                modifier = Modifier.weight(1f),

            )

            MobileMetricTile(

                label = "Frames",

                value = state.telegrams.size.toString(),

                accent = MaterialTheme.colorScheme.primary,

                modifier = Modifier.weight(1f),

            )

        }



        // Physical Signal Quality & Frame Retry Diagnostic Alert

        val retryRate = health.retryRatePercent

        val repeatedFrames = health.repeatedCount

        if (retryRate > 4.0) {

            Surface(

                shape = EtsShapes.Card,

                color = MaterialTheme.colorScheme.errorContainer,

                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),

                modifier = Modifier.fillMaxWidth(),

            ) {

                Row(

                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),

                    verticalAlignment = Alignment.CenterVertically,

                    horizontalArrangement = Arrangement.spacedBy(8.dp),

                ) {

                    Icon(

                        imageVector = Icons.Default.Warning,

                        contentDescription = null,

                        tint = MaterialTheme.colorScheme.error,

                        modifier = Modifier.size(20.dp),

                    )

                    Column(modifier = Modifier.weight(1f)) {

                        Text(

                            text = "TP1 PHYSICAL BUS INTEGRITY WARNING",

                            style = MaterialTheme.typography.labelSmall,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onErrorContainer,

                        )

                        Text(

                            text = "${String.format(Locale.US, "%.1f", retryRate)}% frame retries ($repeatedFrames repeated). Possible missing choke on PSU, cable reflection, or device collision.",

                            style = MaterialTheme.typography.bodySmall,

                            fontSize = 10.5.sp,

                            color = MaterialTheme.colorScheme.onErrorContainer,

                        )

                    }

                }

            }

        } else if (repeatedFrames > 0) {

            Surface(

                shape = EtsShapes.Card,

                color = MaterialTheme.colorScheme.tertiaryContainer,

                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),

                modifier = Modifier.fillMaxWidth(),

            ) {

                Row(

                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),

                    verticalAlignment = Alignment.CenterVertically,

                    horizontalArrangement = Arrangement.spacedBy(8.dp),

                ) {

                    Icon(

                        imageVector = Icons.Default.Info,

                        contentDescription = null,

                        tint = MaterialTheme.colorScheme.onTertiaryContainer,

                        modifier = Modifier.size(16.dp),

                    )

                    Text(

                        text = "Minor TP1 retries: ${String.format(Locale.US, "%.1f", retryRate)}% ($repeatedFrames frames repeated). Bus operational.",

                        style = MaterialTheme.typography.bodySmall,

                        fontSize = 11.sp,

                        color = MaterialTheme.colorScheme.onTertiaryContainer,

                    )

                }

            }

        }



        // Interactive TP1 Bus Traffic Flow Visualizer Bar

        val isStorm = health.loadLevel == BusLoadLevel.STORM

        val isElevated = health.loadLevel == BusLoadLevel.ELEVATED

        Surface(

            shape = EtsShapes.Card,

            color = when {

                isStorm -> MaterialTheme.colorScheme.errorContainer

                isElevated -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)

                else -> MaterialTheme.colorScheme.surfaceContainer

            },

            border = BorderStroke(

                1.dp,

                when {

                    isStorm -> MaterialTheme.colorScheme.error

                    isElevated -> MaterialTheme.colorScheme.tertiary

                    else -> MaterialTheme.colorScheme.outlineVariant

                }

            ),

            modifier = Modifier.fillMaxWidth(),

        ) {

            Column(

                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),

                verticalArrangement = Arrangement.spacedBy(4.dp),

            ) {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically,

                ) {

                    Row(

                        verticalAlignment = Alignment.CenterVertically,

                        horizontalArrangement = Arrangement.spacedBy(6.dp),

                    ) {

                        Box(

                            modifier = Modifier

                                .size(8.dp)

                                .clip(CircleShape)

                                .background(

                                    when {

                                        isStorm -> MaterialTheme.colorScheme.error

                                        isElevated -> MaterialTheme.colorScheme.tertiary

                                        else -> MaterialTheme.colorScheme.primary

                                    }

                                )

                        )

                        Text(

                            text = when {

                                isStorm -> "TELEGRAM STORM (${String.format(Locale.US, "%.1f", health.telegramsPerSecond)} msg/s)"

                                isElevated -> "ELEVATED TRAFFIC (${String.format(Locale.US, "%.1f", health.telegramsPerSecond)} msg/s)"

                                else -> "NORMAL BUS TRAFFIC (${String.format(Locale.US, "%.1f", health.telegramsPerSecond)} msg/s)"

                            },

                            style = MaterialTheme.typography.labelSmall,

                            fontWeight = FontWeight.Bold,

                            color = when {

                                isStorm -> MaterialTheme.colorScheme.onErrorContainer

                                isElevated -> MaterialTheme.colorScheme.onTertiaryContainer

                                else -> MaterialTheme.colorScheme.onSurface

                            },

                        )

                    }



                    TextButton(

                        onClick = { showTopTalkers = !showTopTalkers },

                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),

                        modifier = Modifier.height(24.dp),

                    ) {

                        Text(

                            text = if (showTopTalkers) "Hide Talkers" else "Top Talkers (${health.topSourceDevices.size})",

                            fontSize = 10.sp,

                            fontWeight = FontWeight.Bold,

                        )

                        Icon(

                            imageVector = if (showTopTalkers) Icons.Default.ExpandLess else Icons.Default.ExpandMore,

                            contentDescription = null,

                            modifier = Modifier.size(14.dp),

                        )

                    }

                }



                // Traffic Load Progress Bar

                LinearProgressIndicator(

                    progress = { (health.estimatedTp1LoadPercent / 100.0).toFloat().coerceIn(0.01f, 1f) },

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(4.dp)

                        .clip(RoundedCornerShape(2.dp)),

                    color = when {

                        isStorm -> MaterialTheme.colorScheme.error

                        isElevated -> MaterialTheme.colorScheme.tertiary

                        else -> MaterialTheme.colorScheme.primary

                    },

                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,

                )



                // Real-Time Bus Load Waveform Canvas Sparkline

                BusLoadWaveformChart(

                    history = trafficHistory,

                    isStorm = isStorm,

                    isElevated = isElevated,

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(44.dp)

                        .padding(vertical = 2.dp),

                )



                // Expandable Top Talkers Tray

                AnimatedVisibility(visible = showTopTalkers) {

                    Column(

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(top = 4.dp),

                        verticalArrangement = Arrangement.spacedBy(4.dp),

                    ) {

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(

                            text = "TOP SOURCE DEVICES ON BUS",

                            style = MaterialTheme.typography.labelSmall,

                            fontSize = 9.sp,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                        )

                        health.topSourceDevices.forEach { talker ->

                            Row(

                                modifier = Modifier.fillMaxWidth(),

                                horizontalArrangement = Arrangement.SpaceBetween,

                                verticalAlignment = Alignment.CenterVertically,

                            ) {

                                Row(

                                    verticalAlignment = Alignment.CenterVertically,

                                    horizontalArrangement = Arrangement.spacedBy(6.dp),

                                    modifier = Modifier.weight(1f),

                                ) {

                                    Surface(

                                        shape = EtsShapes.Badge,

                                        color = MaterialTheme.colorScheme.primaryContainer,

                                    ) {

                                        Text(

                                            text = talker.address,

                                            style = MaterialTheme.typography.labelSmall,

                                            fontFamily = FontFamily.Monospace,

                                            fontWeight = FontWeight.Bold,

                                            color = MaterialTheme.colorScheme.onPrimaryContainer,

                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),

                                            fontSize = 9.sp,

                                        )

                                    }

                                    Text(

                                        text = talker.label,

                                        style = MaterialTheme.typography.bodySmall,

                                        fontSize = 11.sp,

                                        maxLines = 1,

                                        overflow = TextOverflow.Ellipsis,

                                    )

                                }



                                Row(

                                    verticalAlignment = Alignment.CenterVertically,

                                    horizontalArrangement = Arrangement.spacedBy(4.dp),

                                ) {

                                    Text(

                                        text = "${talker.telegramCount} msgs",

                                        style = MaterialTheme.typography.labelSmall,

                                        fontFamily = FontFamily.Monospace,

                                        fontSize = 10.sp,

                                    )

                                    FilledTonalButton(

                                        onClick = { controller.setMonitorSearch(talker.address) },

                                        shape = EtsShapes.Button,

                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),

                                        modifier = Modifier.height(20.dp),

                                    ) {

                                        Text("Filter", fontSize = 9.sp)

                                    }

                                }

                            }

                        }



                        if (health.unansweredReadAddresses.isNotEmpty()) {

                            Surface(

                                shape = EtsShapes.Button,

                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),

                                modifier = Modifier.fillMaxWidth(),

                            ) {

                                Row(

                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),

                                    verticalAlignment = Alignment.CenterVertically,

                                    horizontalArrangement = Arrangement.spacedBy(6.dp),

                                ) {

                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))

                                    Text(

                                        text = "Unanswered Read Requests: ${health.unansweredReadAddresses.joinToString()}",

                                        style = MaterialTheme.typography.labelSmall,

                                        color = MaterialTheme.colorScheme.error,

                                        fontSize = 9.5.sp,

                                    )

                                }

                            }

                        }

                    }

                }

            }

        }



        // ETS Pinned Inline Quick-Test / Quick-Send Bar

        ElevatedCard(

            shape = EtsShapes.Card,

            colors = CardDefaults.elevatedCardColors(

                containerColor = MaterialTheme.colorScheme.surfaceContainer,

            ),

            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),

            modifier = Modifier.fillMaxWidth(),

        ) {

            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 8.dp, vertical = 5.dp),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.spacedBy(5.dp),

            ) {

                OutlinedTextField(

                    value = quickTestGaText,

                    onValueChange = { quickTestGaText = it },

                    placeholder = { Text("GA (1/1/1)", fontSize = 11.sp) },

                    singleLine = true,

                    textStyle = LocalTextStyle.current.copy(

                        fontSize = 11.5.sp,

                        fontFamily = FontFamily.Monospace,

                        fontWeight = FontWeight.Bold,

                    ),

                    modifier = Modifier.weight(1.1f),

                    shape = EtsShapes.Button,

                )



                // Quick Read

                Button(

                    onClick = {

                        controller.selectGroupAddressForSender(quickTestGaText)

                        controller.readGroupAddress(quickTestGaText)

                    },

                    shape = EtsShapes.Button,

                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),

                    colors = ButtonDefaults.buttonColors(

                        containerColor = MaterialTheme.colorScheme.secondaryContainer,

                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,

                    ),

                ) {

                    Text("Read", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)

                }



                // Quick OFF

                Button(

                    onClick = {

                        controller.selectGroupAddressForSender(quickTestGaText)

                        controller.writeGroupSwitch(quickTestGaText, false)

                    },

                    shape = EtsShapes.Button,

                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),

                    colors = ButtonDefaults.buttonColors(

                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,

                        contentColor = MaterialTheme.colorScheme.onSurface,

                    ),

                ) {

                    Text("OFF", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)

                }



                // Quick ON

                Button(

                    onClick = {

                        controller.selectGroupAddressForSender(quickTestGaText)

                        controller.writeGroupSwitch(quickTestGaText, true)

                    },

                    shape = EtsShapes.Button,

                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),

                    colors = ButtonDefaults.buttonColors(

                        containerColor = MaterialTheme.colorScheme.primary,

                        contentColor = MaterialTheme.colorScheme.onPrimary,

                    ),

                ) {

                    Text("ON", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)

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
                    VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }

                val secFilters = listOf("ALL", "SECURE", "PLAIN")
                items(secFilters) { sf ->
                    val selected = securityFilter == sf
                    FilterChip(
                        selected = selected,
                        onClick = { securityFilter = sf },
                        leadingIcon = if (sf == "SECURE") { { EtsSecureShield(sizeDp = 10.dp) } } else null,
                        label = { Text(if (sf == "ALL") "All Sec" else sf, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                    )
                }

                item {

                    FilterChip(

                        selected = state.monitorGhostOnly,

                        onClick = { controller.setMonitorGhostOnly(!state.monitorGhostOnly) },

                        label = {

                            Text(

                                text = if (health.ghostGroupCount > 0) "Ghost (${health.ghostGroupCount})" else "Ghost Only",

                                fontSize = 10.sp,

                            )

                        },

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

                shape = EtsShapes.Card,

                color = MaterialTheme.colorScheme.surfaceContainerLow,

                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),

                modifier = Modifier.fillMaxWidth(),

            ) {

                Column(

                    modifier = Modifier.padding(16.dp),

                    horizontalAlignment = Alignment.CenterHorizontally,

                    verticalArrangement = Arrangement.spacedBy(6.dp),

                ) {

                    Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))

                    Text("Waiting for KNX Bus Traffic", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Text(

                        "Connect to a Wi-Fi KNXnet/IP Gateway or test a Group Address above to view live telegrams.",

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                    )

                    Button(

                        onClick = { controller.toggleConnectDialog(true) },

                        shape = EtsShapes.Button,

                    ) {

                        Text("Connect Gateway")

                    }

                }

            }

        } else {

            // Authentic ETS Group Monitor Table Header

            Surface(

                color = MaterialTheme.colorScheme.surfaceContainerHigh,

                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),

                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),

                modifier = Modifier.fillMaxWidth(),

            ) {

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(horizontal = 8.dp, vertical = 4.dp),

                    verticalAlignment = Alignment.CenterVertically,

                    horizontalArrangement = Arrangement.SpaceBetween,

                ) {

                    Row(

                        verticalAlignment = Alignment.CenterVertically,

                        horizontalArrangement = Arrangement.spacedBy(6.dp),

                    ) {

                        Text(

                            text = "#",

                            fontSize = 9.sp,

                            fontFamily = FontFamily.Monospace,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.width(22.dp),

                        )

                        Text(

                            text = "TIME",

                            fontSize = 9.sp,

                            fontFamily = FontFamily.Monospace,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.width(58.dp),

                        )

                        Text(

                            text = "SEC",

                            fontSize = 9.sp,

                            fontFamily = FontFamily.Monospace,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.width(36.dp),

                        )

                        Text(

                            text = "SERVICE",

                            fontSize = 9.sp,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.width(42.dp),

                        )

                        Text(

                            text = "SRC → DST",

                            fontSize = 9.sp,

                            fontFamily = FontFamily.Monospace,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                        )

                    }

                    Text(

                        text = "VALUE / INFO",

                        fontSize = 9.sp,

                        fontWeight = FontWeight.Bold,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                    )

                }

            }



            LazyColumn(

                modifier = Modifier.fillMaxSize(),

                verticalArrangement = Arrangement.spacedBy(1.dp),

            ) {

                itemsIndexed(filtered, key = { _, row -> row.telegram.id }) { index, row ->

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

                        ApciType.GROUP_RESPONSE -> Color(0xFFDCFCE7)

                        ApciType.GROUP_READ -> Color(0xFFFEF3C7)

                        else -> MaterialTheme.colorScheme.surfaceContainerHigh

                    }

                    val badgeFg = when (t.apci) {

                        ApciType.GROUP_WRITE -> MaterialTheme.colorScheme.onPrimaryContainer

                        ApciType.GROUP_RESPONSE -> Color(0xFF14532D)

                        ApciType.GROUP_READ -> Color(0xFF92400E)

                        else -> MaterialTheme.colorScheme.onSurface

                    }



                    val isAlt = index % 2 == 1

                    val rowBg = if (isAlt) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface



                    Surface(

                        color = rowBg,

                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),

                        modifier = Modifier

                            .fillMaxWidth()

                            .clickable { onInspectTelegram(row) },

                    ) {

                        Column(

                            modifier = Modifier

                                .fillMaxWidth()

                                .padding(horizontal = 8.dp, vertical = 4.dp),

                            verticalArrangement = Arrangement.spacedBy(2.dp),

                        ) {

                            // Row Line 1: Sequence #, Time, APCI Badge, Source -> Destination, Decoded Value Badge

                            Row(

                                modifier = Modifier.fillMaxWidth(),

                                verticalAlignment = Alignment.CenterVertically,

                                horizontalArrangement = Arrangement.SpaceBetween,

                            ) {

                                Row(

                                    verticalAlignment = Alignment.CenterVertically,

                                    horizontalArrangement = Arrangement.spacedBy(6.dp),

                                    modifier = Modifier.weight(1f),

                                ) {

                                    Text(

                                        text = "${index + 1}",

                                        fontSize = 9.sp,

                                        fontFamily = FontFamily.Monospace,

                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),

                                        modifier = Modifier.width(22.dp),

                                    )



                                    Text(

                                        text = MOBILE_TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs)),

                                        fontSize = 9.5.sp,

                                        fontFamily = FontFamily.Monospace,

                                        fontWeight = FontWeight.SemiBold,

                                        color = MaterialTheme.colorScheme.onSurface,

                                        modifier = Modifier.width(58.dp),

                                    )

                                    val isSecureTg = t.isSecure || (t.destinationGroup?.let { state.project?.findGroupAddress(it)?.isSecure == true || state.keyring?.isGroupSecure(it) == true } == true)
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                                        if (isSecureTg) {
                                            EtsSecureBadge(label = "SEC")
                                        } else {
                                            EtsPlainBadge(label = "PLAIN")
                                        }
                                    }

                                    Surface(shape = EtsShapes.Badge, color = badgeColor) {

                                        Text(

                                            text = apciShort,

                                            style = MaterialTheme.typography.labelSmall,

                                            fontSize = 8.sp,

                                            fontWeight = FontWeight.Bold,

                                            color = badgeFg,

                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),

                                        )

                                    }



                                    Text(

                                        text = "${t.source} → $dstAddr",

                                        style = MaterialTheme.typography.labelSmall,

                                        fontSize = 10.sp,

                                        fontFamily = FontFamily.Monospace,

                                        fontWeight = FontWeight.Bold,

                                        color = MaterialTheme.colorScheme.primary,

                                        maxLines = 1,

                                    )







                                    if (row.isUnmappedGroup) {

                                        Surface(shape = EtsShapes.Badge, color = MaterialTheme.colorScheme.tertiaryContainer) {

                                            Text(

                                                text = "GHOST",

                                                style = MaterialTheme.typography.labelSmall,

                                                fontSize = 7.5.sp,

                                                fontWeight = FontWeight.Bold,

                                                color = MaterialTheme.colorScheme.onTertiaryContainer,

                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),

                                            )

                                        }

                                    }

                                }



                                if (row.decodedValue.isNotBlank()) {

                                    Surface(

                                        shape = EtsShapes.Badge,

                                        color = MaterialTheme.colorScheme.secondaryContainer,

                                    ) {

                                        Text(

                                            text = row.decodedValue,

                                            style = MaterialTheme.typography.labelSmall,

                                            fontSize = 9.5.sp,

                                            fontWeight = FontWeight.Bold,

                                            fontFamily = FontFamily.Monospace,

                                            color = MaterialTheme.colorScheme.onSecondaryContainer,

                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),

                                        )

                                    }

                                }

                            }



                            // Row Line 2: Source Device Name → Destination GA Name & DPT

                            val dstDesc = row.destinationName

                                ?: if (t.destinationGroup != null) "Unmapped Group" else "Tunnel / Management"

                            Row(

                                modifier = Modifier.fillMaxWidth().padding(start = 28.dp),

                                verticalAlignment = Alignment.CenterVertically,

                                horizontalArrangement = Arrangement.SpaceBetween,

                            ) {

                                Text(

                                    text = "${row.sourceDeviceName ?: "Device"} › $dstDesc",

                                    style = MaterialTheme.typography.bodySmall,

                                    fontSize = 9.sp,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                                    maxLines = 1,

                                    overflow = TextOverflow.Ellipsis,

                                    modifier = Modifier.weight(1f),

                                )



                                row.dptId?.let { dpt ->

                                    Text(

                                        text = "[$dpt]",

                                        fontSize = 8.5.sp,

                                        fontFamily = FontFamily.Monospace,

                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),

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

            shape = EtsShapes.Card,

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

                        shape = EtsShapes.Card,

                    )

                    Button(

                        onClick = { controller.readGroupAddress(gaText) },

                        colors = ButtonDefaults.buttonColors(

                            containerColor = MaterialTheme.colorScheme.secondaryContainer,

                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,

                        ),

                        shape = EtsShapes.Card,

                    ) {

                        Text("GroupRead", fontWeight = FontWeight.Bold)

                    }

                }

                val parsedTargetGa = GroupAddress.parseOrNull(gaText)
                val isTargetGaSecure = (parsedTargetGa != null && state.project?.findGroupAddress(parsedTargetGa)?.isSecure == true) ||
                    (parsedTargetGa != null && state.keyring?.isGroupSecure(parsedTargetGa) == true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isTargetGaSecure) {
                        EtsSecureBadge(label = "KNX SECURE GA (ENCRYPTED BUS TRANSMISSION)")
                    } else {
                        EtsPlainBadge(label = "STANDARD PLAIN TEXT GA (UNENCRYPTED)")
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

                        shape = EtsShapes.Button,

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

                            shape = EtsShapes.Button,

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

            shape = EtsShapes.Card,

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

                        shape = EtsShapes.Card,

                    ) {

                        Text("STOP", fontWeight = FontWeight.Bold)

                    }

                } else {

                    OutlinedButton(

                        onClick = { controller.startWalkTestBlink(gaText) },

                        shape = EtsShapes.Card,

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

                    shape = EtsShapes.Card,

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

                    shape = EtsShapes.Card,

                    modifier = Modifier.weight(1f),

                ) {

                    Text("Write ON (1)", fontWeight = FontWeight.Bold)

                }

            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                OutlinedButton(

                    onClick = { controller.writeGroupDimStep(gaText, false) },

                    modifier = Modifier.weight(1f),

                    shape = EtsShapes.Card,

                ) {

                    Text("Dim −")

                }

                OutlinedButton(

                    onClick = { controller.writeGroupDimStep(gaText, false, 0) },

                    modifier = Modifier.weight(1f),

                    shape = EtsShapes.Card,

                ) {

                    Text("Dim STOP")

                }

                OutlinedButton(

                    onClick = { controller.writeGroupDimStep(gaText, true) },

                    modifier = Modifier.weight(1f),

                    shape = EtsShapes.Card,

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

                    shape = EtsShapes.Card,

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

                    shape = EtsShapes.Card,

                )

                Button(

                    onClick = { floatText.toDoubleOrNull()?.let { controller.writeGroupFloat16(gaText, it) } },

                    shape = EtsShapes.Card,

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

                    shape = EtsShapes.Card,

                )

                Button(

                    onClick = { controller.writeGroupString14(gaText, asciiText) },

                    shape = EtsShapes.Card,

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

                    shape = EtsShapes.Card,

                )

                Button(

                    onClick = { controller.writeGroupRawHex(gaText, rawHexText, compact6Bit = false) },

                    shape = EtsShapes.Card,

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

                    shape = EtsShapes.Card,

                )



                LazyColumn(

                    modifier = Modifier

                        .fillMaxWidth()

                        .heightIn(max = 380.dp),

                    verticalArrangement = Arrangement.spacedBy(6.dp),

                ) {

                    items(filteredPickerGas, key = { it.address.raw }) { ga ->

                        Surface(

                            shape = EtsShapes.Card,

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

                                    shape = EtsShapes.Button,

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

                                    shape = EtsShapes.Button,

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

        shape = EtsShapes.Card,

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

                        shape = EtsShapes.Button,

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



@Composable

private fun BusLoadWaveformChart(

    history: List<Float>,

    isStorm: Boolean,

    isElevated: Boolean,

    modifier: Modifier = Modifier,

) {

    val accentColor = when {

        isStorm -> MaterialTheme.colorScheme.error

        isElevated -> MaterialTheme.colorScheme.tertiary

        else -> MaterialTheme.colorScheme.primary

    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)



    Column(modifier = modifier) {

        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {

            val width = size.width

            val height = size.height

            if (width <= 0 || height <= 0 || history.size < 2) return@Canvas



            val maxVal = maxOf(40f, (history.maxOrNull() ?: 10f) * 1.2f)



            // Draw horizontal reference lines (e.g. 10 msg/s, 25 msg/s)

            val y25 = height - (25f / maxVal) * height

            val y10 = height - (10f / maxVal) * height

            drawLine(

                color = gridColor,

                start = androidx.compose.ui.geometry.Offset(0f, y25),

                end = androidx.compose.ui.geometry.Offset(width, y25),

                strokeWidth = 1f,

            )

            drawLine(

                color = gridColor,

                start = androidx.compose.ui.geometry.Offset(0f, y10),

                end = androidx.compose.ui.geometry.Offset(width, y10),

                strokeWidth = 1f,

            )



            val stepX = width / (history.size - 1)

            val strokePath = Path()

            val fillPath = Path()



            val points = history.mapIndexed { idx, v ->

                val x = idx * stepX

                val y = height - (v.coerceIn(0f, maxVal) / maxVal) * (height - 4f) - 2f

                androidx.compose.ui.geometry.Offset(x, y)

            }



            strokePath.moveTo(points.first().x, points.first().y)

            fillPath.moveTo(points.first().x, height)

            fillPath.lineTo(points.first().x, points.first().y)



            for (i in 0 until points.size - 1) {

                val p0 = points[i]

                val p1 = points[i + 1]

                val cx = (p0.x + p1.x) / 2f

                strokePath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)

                fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)

            }



            fillPath.lineTo(points.last().x, height)

            fillPath.close()



            // Draw gradient fill

            drawPath(

                path = fillPath,

                brush = Brush.verticalGradient(

                    colors = listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.02f)),

                    startY = 0f,

                    endY = height,

                ),

            )



            // Draw smooth curve stroke

            drawPath(

                path = strokePath,

                color = accentColor,

                style = Stroke(

                    width = 2.dp.toPx(),

                    cap = StrokeCap.Round,

                ),

            )



            // Draw pulsating dot on current reading

            val latest = points.last()

            drawCircle(

                color = accentColor,

                radius = 3.5.dp.toPx(),

                center = latest,

            )

            drawCircle(

                color = accentColor.copy(alpha = 0.3f),

                radius = 6.dp.toPx(),

                center = latest,

            )

        }



        Row(

            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),

            horizontalArrangement = Arrangement.SpaceBetween,

        ) {

            Text(

                text = "-40s",

                style = MaterialTheme.typography.labelSmall,

                fontSize = 8.sp,

                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),

            )

            Text(

                text = "TP1 Bus Load Waveform (Real-time)",

                style = MaterialTheme.typography.labelSmall,

                fontSize = 8.sp,

                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),

            )

            Text(

                text = "Live",

                style = MaterialTheme.typography.labelSmall,

                fontSize = 8.sp,

                fontWeight = FontWeight.Bold,

                color = accentColor,

            )

        }

    }

}

