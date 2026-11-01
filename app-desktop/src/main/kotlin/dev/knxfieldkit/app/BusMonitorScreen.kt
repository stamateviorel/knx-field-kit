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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONITOR_TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

@Composable
fun BusMonitorScreen(state: AppUiState, controller: AppController, onExportCsv: () -> Unit) {
    val filteredTelegrams = remember(state.telegrams, state.monitorGhostOnly, state.monitorSearchQuery) {
        val q = state.monitorSearchQuery.trim().lowercase()
        state.telegrams.filter { row ->
            if (state.monitorGhostOnly && !row.isUnmappedGroup) return@filter false
            if (q.isEmpty()) return@filter true
            val t = row.telegram
            t.source.toString().lowercase().contains(q) ||
                (row.sourceDeviceName?.lowercase()?.contains(q) == true) ||
                (t.destinationGroup?.toString()?.lowercase()?.contains(q) == true) ||
                (t.destinationIndividual?.toString()?.lowercase()?.contains(q) == true) ||
                (row.destinationName?.lowercase()?.contains(q) == true) ||
                row.decodedValue.lowercase().contains(q) ||
                t.apci.label.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.monitorSearchQuery,
                onValueChange = controller::setMonitorSearch,
                placeholder = { Text("Filter by address, device, GA name, APCI, or value...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = state.monitorGhostOnly,
                onClick = { controller.setMonitorGhostOnly(!state.monitorGhostOnly) },
                label = { Text("Ghost GAs Only") },
            )
            OutlinedButton(onClick = { controller.setMonitorPaused(!state.monitorPaused) }) {
                Text(if (state.monitorPaused) "Resume" else "Pause")
            }
            OutlinedButton(onClick = controller::clearMonitorLog) {
                Text("Clear")
            }
            Button(onClick = onExportCsv, enabled = state.telegrams.isNotEmpty()) {
                Text("Export CSV")
            }
        }

        Card(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text("Time", modifier = Modifier.width(105.dp), style = MaterialTheme.typography.labelMedium)
                    Text("Source", modifier = Modifier.weight(0.24f), style = MaterialTheme.typography.labelMedium)
                    Text("Destination", modifier = Modifier.weight(0.28f), style = MaterialTheme.typography.labelMedium)
                    Text("APCI", modifier = Modifier.width(155.dp), style = MaterialTheme.typography.labelMedium)
                    Text("DPT", modifier = Modifier.width(65.dp), style = MaterialTheme.typography.labelMedium)
                    Text("Decoded / Flags", modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
                if (filteredTelegrams.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No telegrams captured yet — Connect to Virtual Bus or a KNXnet/IP Gateway.")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredTelegrams, key = { it.telegram.id }) { row ->
                            val t = row.telegram
                            val dstAddr = t.destinationGroup?.toString() ?: t.destinationIndividual?.toString() ?: "broadcast"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = MONITOR_TIME_FMT.format(Instant.ofEpochMilli(t.timestampEpochMs)),
                                    modifier = Modifier.width(105.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = if (!row.sourceDeviceName.isNullOrBlank()) "${t.source} (${row.sourceDeviceName})" else t.source.toString(),
                                    modifier = Modifier.weight(0.24f),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = if (!row.destinationName.isNullOrBlank()) "$dstAddr (${row.destinationName})" else dstAddr,
                                    modifier = Modifier.weight(0.28f),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = t.apci.label,
                                    modifier = Modifier.width(155.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = row.dptId ?: "-",
                                    modifier = Modifier.width(65.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(
                                    modifier = Modifier.weight(0.25f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = row.decodedValue,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (row.isUnmappedGroup) {
                                        Text(
                                            text = "[GHOST GA]",
                                            color = Color(0xFFF59E0B),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    if (t.repeated) {
                                        Text(
                                            text = "[REPEAT]",
                                            color = MaterialTheme.colorScheme.error,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelSmall,
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
}
