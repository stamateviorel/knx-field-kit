package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.app.AppController
import dev.knxfieldkit.app.AppTab
import dev.knxfieldkit.app.AppUiState
import dev.knxfieldkit.model.*

/**
 * Authentic ETS Diagnostics & Commissioning Suite for KNX Field Technicians.
 *
 * Implements the 4 core ETS diagnostics workbenches:
 * 1. Line Diagnostics (Linien-Scan & Topologie-Prüfung)
 * 2. Device Info & BCU (ETS Geräteinfo & DD0/BCU)
 * 3. Programming Mode & Address Commissioning (Physikalische Adressen)
 * 4. Site Commissioning & Handover Report (Inbetriebnahme-Abnahme)
 */
enum class EtsDiagnosticsTab(val label: String, val icon: ImageVector) {
    LINE_DIAGNOSTICS("Line Scan", Icons.Default.Route),
    DEVICE_INFO("Device Info", Icons.Default.Memory),
    PROGRAMMING_MODE("Prog Mode", Icons.Default.Lightbulb),
    SITE_COMMISSIONING("Site Report", Icons.AutoMirrored.Filled.FactCheck),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDiagnosticsScreen(
    state: AppUiState,
    controller: AppController,
    initialTab: EtsDiagnosticsTab = EtsDiagnosticsTab.LINE_DIAGNOSTICS,
    onShareHtmlReport: () -> Unit = {},
    onShareMarkdownSummary: () -> Unit = {},
) {
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    // Line Diagnostics state
    val projectLines = remember(state.project) { state.project?.allLines.orEmpty() }
    var selectedLineId by remember(projectLines) {
        mutableStateOf(projectLines.firstOrNull { it.devices.isNotEmpty() }?.id ?: projectLines.firstOrNull()?.id ?: "1.1")
    }
    var showCustomLineInput by remember { mutableStateOf(false) }
    var customAreaText by remember { mutableStateOf("1") }
    var customLineText by remember { mutableStateOf("1") }
    var lineAuditFilter by remember { mutableStateOf("ALL") } // "ALL", "ONLINE", "FAULTS"

    // Device Info state
    var targetDeviceText by remember(state.selectedDeviceAddress) {
        mutableStateOf(state.selectedDeviceAddress?.toString() ?: projectLines.flatMap { it.devices }.firstOrNull()?.address?.toString() ?: "1.1.1")
    }
    var showDevicePickerSheet by remember { mutableStateOf(false) }
    var deviceSearchQuery by remember { mutableStateOf("") }

    // Programming Mode state
    var newProgAddrText by remember { mutableStateOf("") }
    var addressCheckStatus by remember { mutableStateOf<String?>(null) }

    // Selected device from project if found
    val selectedTargetAddress = remember(targetDeviceText) { IndividualAddress.parseOrNull(targetDeviceText) }
    val projectDeviceForTarget = remember(selectedTargetAddress, state.project) {
        selectedTargetAddress?.let { state.project?.findDevice(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Sticky Header: ETS Commissioning & Site Readiness Hero Banner
        DiagnosticsHeroBanner(
            state = state,
            onRunSiteCheck = { controller.runFullSiteCheck() },
            onShareHtml = onShareHtmlReport,
            onShareMarkdown = onShareMarkdownSummary,
        )

        // Google Material 3 Diagnostics Suite Tabs
        PrimaryTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            EtsDiagnosticsTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Tab(
                    selected = selected,
                    onClick = { activeTab = tab },
                    text = {
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        // Active Diagnostics Workbench Content
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                EtsDiagnosticsTab.LINE_DIAGNOSTICS -> {
                    LineDiagnosticsContent(
                        state = state,
                        controller = controller,
                        projectLines = projectLines,
                        selectedLineId = selectedLineId,
                        onSelectLineId = { selectedLineId = it },
                        showCustomLineInput = showCustomLineInput,
                        onToggleCustomLine = { showCustomLineInput = it },
                        customAreaText = customAreaText,
                        onCustomAreaChange = { customAreaText = it },
                        customLineText = customLineText,
                        onCustomLineChange = { customLineText = it },
                        lineAuditFilter = lineAuditFilter,
                        onFilterChange = { lineAuditFilter = it },
                        onInspectDevice = { addr ->
                            targetDeviceText = addr.toString()
                            activeTab = EtsDiagnosticsTab.DEVICE_INFO
                            controller.inspectDeviceDiagnostics(addr.toString())
                        },
                    )
                }

                EtsDiagnosticsTab.DEVICE_INFO -> {
                    DeviceInfoContent(
                        state = state,
                        controller = controller,
                        targetDeviceText = targetDeviceText,
                        onTargetDeviceTextChange = { targetDeviceText = it },
                        projectDevice = projectDeviceForTarget,
                        onOpenPicker = { showDevicePickerSheet = true },
                    )
                }

                EtsDiagnosticsTab.PROGRAMMING_MODE -> {
                    ProgrammingModeContent(
                        state = state,
                        controller = controller,
                        newProgAddrText = newProgAddrText,
                        onNewProgAddrChange = {
                            newProgAddrText = it
                            addressCheckStatus = null
                        },
                        addressCheckStatus = addressCheckStatus,
                        onCheckAddress = { addrStr ->
                            val parsed = IndividualAddress.parseOrNull(addrStr)
                            if (parsed == null) {
                                addressCheckStatus = "Invalid address format (e.g. 1.1.20)"
                            } else {
                                val inProj = state.project?.findDevice(parsed)
                                if (inProj != null) {
                                    addressCheckStatus = "Already in project: '${inProj.name}' (${inProj.address})"
                                } else {
                                    addressCheckStatus = "Address $parsed is available in project"
                                }
                            }
                        },
                    )
                }

                EtsDiagnosticsTab.SITE_COMMISSIONING -> {
                    SiteCommissioningContent(
                        state = state,
                        controller = controller,
                        onShareHtmlReport = onShareHtmlReport,
                        onShareMarkdownSummary = onShareMarkdownSummary,
                        onInspectDevice = { addr ->
                            targetDeviceText = addr.toString()
                            activeTab = EtsDiagnosticsTab.DEVICE_INFO
                            controller.inspectDeviceDiagnostics(addr.toString())
                        },
                    )
                }
            }
        }
    }

    // Searchable Device Picker Bottom Sheet
    if (showDevicePickerSheet) {
        val proj = state.project
        val allDevices = remember(proj) {
            proj?.allLines?.flatMap { it.devices }.orEmpty()
        }
        val filteredDevices = remember(allDevices, deviceSearchQuery, proj) {
            if (deviceSearchQuery.isBlank()) allDevices
            else allDevices.filter { dev ->
                val loc = proj?.findDeviceLocationPath(dev.address).orEmpty()
                dev.address.toString().contains(deviceSearchQuery, ignoreCase = true) ||
                    dev.name.contains(deviceSearchQuery, ignoreCase = true) ||
                    dev.productName.contains(deviceSearchQuery, ignoreCase = true) ||
                    loc.contains(deviceSearchQuery, ignoreCase = true)
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                showDevicePickerSheet = false
                deviceSearchQuery = ""
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Select Device from Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${filteredDevices.size} devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = deviceSearchQuery,
                    onValueChange = { deviceSearchQuery = it },
                    placeholder = { Text("Search by address, name, or room...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (deviceSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { deviceSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredDevices) { dev ->
                        val locPath = proj?.findDeviceLocationPath(dev.address) ?: "Unplaced"
                        OutlinedCard(
                            onClick = {
                                targetDeviceText = dev.address.toString()
                                showDevicePickerSheet = false
                                deviceSearchQuery = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = dev.address.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dev.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${dev.productName.ifBlank { dev.manufacturerName }} • $locPath",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero Banner: 1-Tap Site Commissioning & Health Check
// ---------------------------------------------------------------------------

@Composable
private fun DiagnosticsHeroBanner(
    state: AppUiState,
    onRunSiteCheck: () -> Unit,
    onShareHtml: () -> Unit,
    onShareMarkdown: () -> Unit,
) {
    val proj = state.project
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "ETS Commissioning & Health Audit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (proj != null) {
                                "${proj.name} • ${proj.allLines.sumOf { it.devices.size }} Devices • ${proj.groupAddresses.size} GAs"
                            } else {
                                "Verify bus lines, BCU info, Prog LEDs & Site Handover"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Readiness Badge
                val rep = state.siteCheckReport
                if (rep != null) {
                    val scoreColor = if (rep.readinessScorePercent >= 85) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scoreColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = "${rep.readinessScorePercent}% Ready",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Progress bar if running site check
            if (state.isRunningFullSiteCheck) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Text(
                        text = state.siteCheckProgressText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Quick Metrics Chips if report exists
            state.siteCheckReport?.let { report ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetricChip(
                        label = "${report.onlineDevices}/${report.totalDevicesExpected}",
                        sublabel = "Online",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricChip(
                        label = "${report.polledGroupsResponded}/${report.polledGroupsTotal}",
                        sublabel = "GAs",
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricChip(
                        label = "${report.unreachableDevices.size}",
                        sublabel = "Faults",
                        color = if (report.unreachableDevices.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    MetricChip(
                        label = "${report.activeProgrammingLeds.size}",
                        sublabel = "Prog LEDs",
                        color = if (report.activeProgrammingLeds.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRunSiteCheck,
                    enabled = !state.isRunningFullSiteCheck,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.isRunningFullSiteCheck) "Auditing Site..." else "Run Full Site Check",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }

                OutlinedButton(
                    onClick = onShareHtml,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Handover", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 1: Line Diagnostics (ETS Linien-Scan & Audit)
// ---------------------------------------------------------------------------

@Composable
private fun LineDiagnosticsContent(
    state: AppUiState,
    controller: AppController,
    projectLines: List<TopologyLine>,
    selectedLineId: String,
    onSelectLineId: (String) -> Unit,
    showCustomLineInput: Boolean,
    onToggleCustomLine: (Boolean) -> Unit,
    customAreaText: String,
    onCustomAreaChange: (String) -> Unit,
    customLineText: String,
    onCustomLineChange: (String) -> Unit,
    lineAuditFilter: String,
    onFilterChange: (String) -> Unit,
    onInspectDevice: (IndividualAddress) -> Unit,
) {
    val proj = state.project
    val selectedLine = remember(projectLines, selectedLineId) {
        projectLines.firstOrNull { it.id == selectedLineId }
    }

    val activeArea = if (showCustomLineInput) customAreaText.toIntOrNull() ?: 1 else selectedLine?.area ?: 1
    val activeLineNum = if (showCustomLineInput) customLineText.toIntOrNull() ?: 1 else selectedLine?.line ?: 1

    val auditEntries = state.lineAuditReport?.entries ?: state.lineAuditInProgressEntries
    val filteredEntries = remember(auditEntries, lineAuditFilter) {
        when (lineAuditFilter) {
            "ONLINE" -> auditEntries.filter { it.status == AuditStatus.ONLINE }
            "FAULTS" -> auditEntries.filter { it.status != AuditStatus.ONLINE }
            else -> auditEntries
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Line Selector Carousel
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SELECT TOPOLOGY LINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onToggleCustomLine(!showCustomLineInput) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (showCustomLineInput) "Use Project Lines" else "Custom Area.Line",
                            fontSize = 11.sp,
                        )
                    }
                }

                if (showCustomLineInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customAreaText,
                            onValueChange = onCustomAreaChange,
                            label = { Text("Area (0-15)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        OutlinedTextField(
                            value = customLineText,
                            onValueChange = onCustomLineChange,
                            label = { Text("Line (0-15)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                } else if (projectLines.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(projectLines) { line ->
                            val isSelected = line.id == selectedLineId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectLineId(line.id) },
                                label = {
                                    Text(
                                        text = "${line.id} ${line.name.take(16)} (${line.devices.size})",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
            }
        }

        // Line Overview & Diagnostic Action Card
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "Line $activeArea.$activeLineNum",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Text(
                                text = selectedLine?.name ?: "Custom Line",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = selectedLine?.mediumType ?: "TP1",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Text(
                        text = if (selectedLine != null) {
                            "${selectedLine.devices.size} devices configured in ETS project on this line. Coupler: $activeArea.$activeLineNum.0"
                        } else {
                            "Scan individual addresses on this physical line to detect online devices."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Line Audit / Scan Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { controller.runProjectLineAudit(activeArea, activeLineNum) },
                            enabled = !state.isAuditingLine,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.isAuditingLine) "Auditing Line..." else "Audit Project Line",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (state.isScanningSubnet) {
                                    controller.cancelSubnetScan()
                                } else {
                                    controller.startSubnetScan(activeArea, activeLineNum, startDevice = 0, endDevice = 64)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (state.isScanningSubnet) {
                                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                        ) {
                            Icon(
                                if (state.isScanningSubnet) Icons.Default.Stop else Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (state.isScanningSubnet) "Stop" else "Sweep 0..64",
                                fontSize = 12.sp,
                            )
                        }
                    }

                    // Progress indicator if auditing or scanning
                    if (state.isAuditingLine || state.isScanningSubnet) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (state.isScanningSubnet && state.subnetScanProgressText.isNotEmpty()) {
                                Text(
                                    text = state.subnetScanProgressText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subnet Scan Discovered Devices (Sweep results)
        if (state.subnetScanResults.isNotEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "DISCOVERED DEVICES ON WIRE (${state.subnetScanResults.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        state.subnetScanResults.forEach { found ->
                            val matchedProj = proj?.findDevice(found.address)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onInspectDevice(found.address) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = found.address.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    text = matchedProj?.name ?: "Uncommissioned Device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "Mask ${found.maskVersionHex ?: "DD0"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Line Audit Results Header & Filter Chips
        if (auditEntries.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.lineAuditReport?.let { rep ->
                            Text(
                                text = "${rep.onlineCount} Online • ${rep.unreachableCount} Unreachable",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (rep.unreachableCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        } ?: Text(
                            text = "AUDIT IN PROGRESS (${auditEntries.size} tested)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        // Filter chips
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("ALL", "ONLINE", "FAULTS").forEach { filter ->
                                val selected = lineAuditFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { onFilterChange(filter) },
                                    label = { Text(filter, fontSize = 10.sp) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Line Audit Results List
            items(filteredEntries) { entry ->
                val isOnline = entry.status == AuditStatus.ONLINE
                val locPath = proj?.findDeviceLocationPath(entry.expectedDevice.address) ?: "Unplaced"
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isOnline) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = entry.expectedDevice.address.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isOnline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.expectedDevice.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${entry.expectedDevice.productName.ifBlank { entry.expectedDevice.manufacturerName }} • $locPath",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Latency / Status Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = if (entry.pingResult.reachable) "${entry.pingResult.latencyMs}ms" else "TIMEOUT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        // Inspect / Info Action
                        IconButton(
                            onClick = { onInspectDevice(entry.expectedDevice.address) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Inspect Device",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 2: Device Info & BCU (ETS Geräteinfo & DD0 Readout)
// ---------------------------------------------------------------------------

@Composable
private fun DeviceInfoContent(
    state: AppUiState,
    controller: AppController,
    targetDeviceText: String,
    onTargetDeviceTextChange: (String) -> Unit,
    projectDevice: ProjectDevice?,
    onOpenPicker: () -> Unit,
) {
    val proj = state.project
    val diag = state.deviceDiagnostics
    val recentPings = state.pingResults.take(6)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Target Device Input & Quick Selector Card
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "INDIVIDUAL ADDRESS TO INSPECT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onOpenPicker,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Select from Project", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = targetDeviceText,
                            onValueChange = onTargetDeviceTextChange,
                            label = { Text("Individual Address (e.g. 3.5.12)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                if (targetDeviceText.isNotEmpty()) {
                                    IconButton(onClick = { onTargetDeviceTextChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                        )
                    }

                    // Project Device Metadata Card (if device matches project)
                    projectDevice?.let { dev ->
                        val locPath = proj?.findDeviceLocationPath(dev.address) ?: "Unplaced"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.Apartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dev.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${dev.productName.ifBlank { dev.manufacturerName }} • $locPath (${dev.comObjects.size} objs)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Diagnostic Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = { controller.pingIndividualDevice(targetDeviceText) },
                            enabled = !state.isPinging,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isPinging) "Ping..." else "Ping DD0", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { controller.inspectDeviceDiagnostics(targetDeviceText) },
                            enabled = !state.isReadingDiagnostics,
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isReadingDiagnostics) "Reading..." else "BCU Info", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { controller.restartRemoteDevice(targetDeviceText) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("A_Restart", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    if (state.isPinging || state.isReadingDiagnostics) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Authentic ETS Device Info Results Table Card
        diag?.let { d ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Header with Online Status
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
                                    color = if (d.reachable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        text = d.address.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (d.reachable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Text(
                                    text = if (d.reachable) "DEVICE RESPONDING (ONLINE)" else "UNREACHABLE (TIMEOUT)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (d.reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }

                            // View objects button
                            projectDevice?.let { dev ->
                                TextButton(
                                    onClick = {
                                        controller.openDeviceObjectsInExplorer(dev.address)
                                        controller.selectTab(AppTab.PROJECT)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text("View Objects", fontSize = 11.sp)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 2-Column ETS Properties Table
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PropertyRow("Mask Version (DD0)", d.maskVersionHex ?: "Unknown")
                            PropertyRow("Hardware Serial No.", d.serialNumberHex ?: "Unknown")
                            PropertyRow("Manufacturer ID", d.manufacturerId?.let { "0x%04X".format(it) } ?: "Unknown")
                            PropertyRow("Operational Run State", d.runState ?: "Normal")
                            PropertyRow("Memory Load State", d.loadState ?: "Loaded")
                            PropertyRow(
                                "Programming Mode LED",
                                if (d.programmingModeActive) "ACTIVE (RED ON)" else "OFF (Normal)",
                                highlight = d.programmingModeActive,
                            )
                        }

                        // Notes & Diagnostics Findings
                        if (d.notes.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "DIAGNOSTIC FINDINGS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            d.notes.forEach { note ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text("•", color = MaterialTheme.colorScheme.primary)
                                    Text(note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Ping Latency History
        if (recentPings.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "RECENT PING VERIFICATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    recentPings.forEach { pr ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = pr.address.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (pr.reachable) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        text = if (pr.reachable) "OK ${pr.maskVersionHex ?: ""} (${pr.latencyMs}ms)" else "TIMEOUT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pr.reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
private fun PropertyRow(name: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// Tab 3: Programming Mode & Individual Address Assignment (ETS Physikalische Adressen)
// ---------------------------------------------------------------------------

@Composable
private fun ProgrammingModeContent(
    state: AppUiState,
    controller: AppController,
    newProgAddrText: String,
    onNewProgAddrChange: (String) -> Unit,
    addressCheckStatus: String?,
    onCheckAddress: (String) -> Unit,
) {
    val proj = state.project
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ETS Workflow Step-by-Step Guide Card
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "COMMISSIONING REPLACEMENT / NEW HARDWARE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "1. Press the physical Programming Button on the KNX device in the cabinet (its red LED turns ON).\n" +
                            "2. Tap 'Scan Active Programming LEDs' below to detect it on the live bus.\n" +
                            "3. Assign the target Individual Address safely with automatic duplicate checking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { controller.scanProgrammingModeLeds() },
                        enabled = !state.isCheckingProgrammingMode,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.isCheckingProgrammingMode) "Scanning Bus for Active LEDs..." else "Scan Active Programming LEDs",
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (state.isCheckingProgrammingMode) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }

        // Detected Devices in Programming Mode
        item {
            if (state.programmingModeDevices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Column {
                            Text(
                                text = "No Active Programming LEDs Detected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Press the physical red button on the device you want to commission, then scan again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEVICES CURRENTLY IN PROGRAMMING MODE (${state.programmingModeDevices.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )

                    state.programmingModeDevices.forEach { ia ->
                        val matched = proj?.findDevice(ia)
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = ia.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                text = "PROG LED ON",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = matched?.name ?: "Uncommissioned Device (Factory Default)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Safe Individual Address Writer Section
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "SAFE INDIVIDUAL ADDRESS ASSIGNMENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = newProgAddrText,
                        onValueChange = onNewProgAddrChange,
                        label = { Text("Target Address (e.g. 3.5.15)") },
                        placeholder = { Text("Area.Line.Device") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )

                    // Conflict check status
                    addressCheckStatus?.let { status ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (status.contains("available", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onCheckAddress(newProgAddrText) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Check Conflict", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { controller.commissionProgrammingDevice(newProgAddrText) },
                            enabled = newProgAddrText.isNotBlank(),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Program Address", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 4: Site Commissioning & Handover Report
// ---------------------------------------------------------------------------

@Composable
private fun SiteCommissioningContent(
    state: AppUiState,
    controller: AppController,
    onShareHtmlReport: () -> Unit,
    onShareMarkdownSummary: () -> Unit,
    onInspectDevice: (IndividualAddress) -> Unit,
) {
    val proj = state.project
    val report = state.siteCheckReport

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (report == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Site Not Yet Audited",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Run a full site audit to verify line couplers, read responses from every configured device, check group addresses, and generate official commissioning handover documentation.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { controller.runFullSiteCheck() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Start Full Site Commissioning Audit")
                        }
                    }
                }
            }
        } else {
            // Executive Summary Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Commissioning Readiness",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${report.onlineDevices} of ${report.totalDevicesExpected} devices verified online",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(54.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${report.readinessScorePercent}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }

                        // Handover Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onShareHtmlReport,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("HTML Handover", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onShareMarkdownSummary,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Markdown", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Unreachable Devices Section (if any)
            if (report.unreachableDevices.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "UNREACHABLE DEVICES (${report.unreachableDevices.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )

                        report.unreachableDevices.forEach { dev ->
                            val locPath = proj?.findDeviceLocationPath(dev.address) ?: "Unplaced"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onInspectDevice(dev.address) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.error,
                                    ) {
                                        Text(
                                            text = dev.address.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dev.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${dev.productName.ifBlank { dev.manufacturerName }} • $locPath",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Key Findings Log
            if (report.findings.isNotEmpty()) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "COMMISSIONING AUDIT FINDINGS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            report.findings.forEach { finding ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = finding,
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
}
