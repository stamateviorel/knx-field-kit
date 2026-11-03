package dev.knxfieldkit.mobile

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
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
 * Implements the 5 core ETS diagnostics workbenches:
 * 1. Line Scan & 16x16 Visual Address Matrix (Linien-Scan & Adress-Matrix)
 * 2. Device Info & BCU Properties (ETS Geräteinfo & DD0/BCU)
 * 3. Programming Mode & Address Commissioning (Physikalische Adressen)
 * 4. Commissioning Walk-Test & Channel Sign-Off (Inbetriebnahme-Gehtest)
 * 5. Site Commissioning & Handover Report (Inbetriebnahme-Abnahme)
 */
enum class EtsDiagnosticsTab(val label: String, val icon: ImageVector) {
    LINE_DIAGNOSTICS("Line Scan", Icons.Default.Route),
    DEVICE_INFO("Device Info", Icons.Default.Memory),
    PROGRAMMING_MODE("Prog Mode", Icons.Default.Lightbulb),
    WALK_TEST("Walk Test", Icons.AutoMirrored.Filled.DirectionsWalk),
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
    var lineViewMode by remember { mutableStateOf("MATRIX") } // "MATRIX" or "LIST"
    var selectedMatrixAddress by remember { mutableStateOf<IndividualAddress?>(null) }

    // Device Info state
    var targetDeviceText by remember(state.selectedDeviceAddress) {
        mutableStateOf(state.selectedDeviceAddress?.toString() ?: projectLines.flatMap { it.devices }.firstOrNull()?.address?.toString() ?: "1.1.1")
    }
    var showDevicePickerSheet by remember { mutableStateOf(false) }
    var deviceSearchQuery by remember { mutableStateOf("") }

    // Programming Mode state
    var newProgAddrText by remember { mutableStateOf("") }
    var addressCheckStatus by remember { mutableStateOf<String?>(null) }

    // Walk Test state
    var selectedWalkTestRoom by remember { mutableStateOf<String?>(null) }
    var flagIssueDialogGa by remember { mutableStateOf<GroupAddress?>(null) }
    var flagIssueNoteText by remember { mutableStateOf("") }
    var showTroubleshootSheet by remember { mutableStateOf(false) }

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
            onOpenTroubleshoot = { showTroubleshootSheet = true },
            onShareHtml = onShareHtmlReport,
            onShareMarkdown = onShareMarkdownSummary,
        )

        // Google Material 3 Scrollable Diagnostics Suite Tabs
        PrimaryScrollableTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 8.dp,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
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
                        lineViewMode = lineViewMode,
                        onViewModeChange = { lineViewMode = it },
                        lineAuditFilter = lineAuditFilter,
                        onFilterChange = { lineAuditFilter = it },
                        selectedMatrixAddress = selectedMatrixAddress,
                        onSelectMatrixAddress = { selectedMatrixAddress = it },
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

                EtsDiagnosticsTab.WALK_TEST -> {
                    CommissioningWalkTestContent(
                        state = state,
                        controller = controller,
                        selectedRoom = selectedWalkTestRoom,
                        onSelectRoom = { selectedWalkTestRoom = it },
                        onFlagIssue = { ga ->
                            flagIssueDialogGa = ga
                            flagIssueNoteText = state.walkTestFlaggedChannels[ga] ?: ""
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

    // Flag Issue Dialog for Walk-Test
    flagIssueDialogGa?.let { ga ->
        AlertDialog(
            onDismissRequest = { flagIssueDialogGa = null },
            title = { Text("Flag Commissioning Issue: $ga") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a diagnostic defect note for channel $ga (e.g. 'Actuator clicking but light remains off' or 'Inverted polarity').",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = flagIssueNoteText,
                        onValueChange = { flagIssueNoteText = it },
                        label = { Text("Defect Note") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.flagChannelWalkTestIssue(ga, flagIssueNoteText.ifBlank { "Defect reported during walk-test" })
                        flagIssueDialogGa = null
                    },
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { flagIssueDialogGa = null }) {
                    Text("Cancel")
                }
            },
        )
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

    // 1-Click Circuit Diagnostics & Trouble-Shooter Assistant Sheet
    if (showTroubleshootSheet) {
        MobileTroubleshootAssistantSheet(
            state = state,
            controller = controller,
            onDismiss = { showTroubleshootSheet = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Hero Banner: 1-Tap Site Commissioning & Health Check
// ---------------------------------------------------------------------------

@Composable
private fun DiagnosticsHeroBanner(
    state: AppUiState,
    onRunSiteCheck: () -> Unit,
    onOpenTroubleshoot: () -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (state.isRunningFullSiteCheck) "Auditing..." else "Audit Site",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }

                OutlinedButton(
                    onClick = onOpenTroubleshoot,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Diagnose", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Report", fontSize = 11.sp)
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
// Tab 1: Line Diagnostics & 16x16 Visual Address Matrix
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
    lineViewMode: String,
    onViewModeChange: (String) -> Unit,
    lineAuditFilter: String,
    onFilterChange: (String) -> Unit,
    selectedMatrixAddress: IndividualAddress?,
    onSelectMatrixAddress: (IndividualAddress?) -> Unit,
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
                                onClick = {
                                    onSelectLineId(line.id)
                                    onSelectMatrixAddress(null)
                                },
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

        // Line Specifications & Commissioning Action Card
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
                            "${selectedLine.devices.size} devices in project on this line. Coupler: $activeArea.$activeLineNum.0"
                        } else {
                            "Scan individual addresses on this physical line to detect online devices."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Line Coupler Status Card
                    val couplerAddr = remember(activeArea, activeLineNum) { IndividualAddress(activeArea, activeLineNum, 0) }
                    val couplerStatus = state.deviceStatuses[couplerAddr]
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.Router,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column {
                                    Text(
                                        text = "Coupler $couplerAddr",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = if (couplerStatus?.reachable == true) "Online (${couplerStatus.latencyMs}ms • ${couplerStatus.maskVersionHex ?: "Coupler"})"
                                        else if (couplerStatus != null && !couplerStatus.reachable) "Coupler Unreachable (Check Bus Power)"
                                        else "Coupler .0 / Router on Medium ${selectedLine?.mediumType ?: "TP1"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = if (couplerStatus?.reachable == true) MaterialTheme.colorScheme.primary
                                        else if (couplerStatus != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { controller.pingIndividualDevice(couplerAddr.toString()) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp),
                            ) {
                                Text("Ping .0", fontSize = 10.sp)
                            }
                        }
                    }

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

        // View Mode Switcher: 16x16 Matrix Grid vs Device List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (lineViewMode == "MATRIX") "16×16 ADDRESS MATRIX (0..255)" else "CONFIGURED DEVICES LIST",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = lineViewMode == "MATRIX",
                        onClick = { onViewModeChange("MATRIX") },
                        label = { Text("Matrix Grid", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                    )
                    FilterChip(
                        selected = lineViewMode == "LIST",
                        onClick = { onViewModeChange("LIST") },
                        label = { Text("List View", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                    )
                }
            }
        }

        // VIEW MODE A: Official ETS 16x16 Address Matrix Grid
        if (lineViewMode == "MATRIX") {
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
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Matrix Color Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MatrixLegendItem(color = MaterialTheme.colorScheme.secondary, label = "Coupler .0")
                            MatrixLegendItem(color = MaterialTheme.colorScheme.primary, label = "Online")
                            MatrixLegendItem(color = MaterialTheme.colorScheme.error, label = "Fault")
                            MatrixLegendItem(color = MaterialTheme.colorScheme.tertiary, label = "Uncommissioned")
                            MatrixLegendItem(color = MaterialTheme.colorScheme.surfaceContainerHigh, label = "Empty")
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 16x16 Interactive Address Matrix
                        val totalCells = 256
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (row in 0 until 16) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    for (col in 0 until 16) {
                                        val devNum = row * 16 + col
                                        val addr = IndividualAddress(activeArea, activeLineNum, devNum)
                                        val isSelected = selectedMatrixAddress == addr

                                        val isCoupler = devNum == 0
                                        val projDev = proj?.findDevice(addr)
                                        val status = state.deviceStatuses[addr]
                                        val isProgLed = addr in state.programmingModeDevices
                                        val isDiscovered = state.subnetScanResults.any { it.address == addr }

                                        val cellColor = when {
                                            isProgLed -> MaterialTheme.colorScheme.tertiary
                                            isCoupler -> MaterialTheme.colorScheme.secondaryContainer
                                            status?.reachable == true -> MaterialTheme.colorScheme.primary
                                            status != null && !status.reachable -> MaterialTheme.colorScheme.error
                                            isDiscovered && projDev == null -> MaterialTheme.colorScheme.tertiary
                                            projDev != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        }

                                        val contentColor = when {
                                            isProgLed -> MaterialTheme.colorScheme.onTertiary
                                            isCoupler -> MaterialTheme.colorScheme.onSecondaryContainer
                                            status?.reachable == true -> MaterialTheme.colorScheme.onPrimary
                                            status != null && !status.reachable -> MaterialTheme.colorScheme.onError
                                            isDiscovered && projDev == null -> MaterialTheme.colorScheme.onTertiary
                                            projDev != null -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(cellColor)
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.5.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = RoundedCornerShape(3.dp),
                                                )
                                                .clickable { onSelectMatrixAddress(addr) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = if (devNum == 0) "C" else "$devNum",
                                                fontSize = 7.sp,
                                                fontWeight = if (projDev != null || isCoupler) FontWeight.Bold else FontWeight.Normal,
                                                color = contentColor,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Selected Matrix Address Inspector Drawer
                        selectedMatrixAddress?.let { addr ->
                            val pDev = proj?.findDevice(addr)
                            val st = state.deviceStatuses[addr]
                            val isLed = addr in state.programmingModeDevices
                            val loc = proj?.findDeviceLocationPath(addr) ?: "Unplaced"

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                                    text = addr.toString(),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                            Text(
                                                text = if (addr.device == 0) "Line Coupler / Router"
                                                else pDev?.name ?: "Uncommissioned Address",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when {
                                                isLed -> MaterialTheme.colorScheme.tertiary
                                                st?.reachable == true -> MaterialTheme.colorScheme.primary
                                                st != null && !st.reachable -> MaterialTheme.colorScheme.error
                                                pDev != null -> MaterialTheme.colorScheme.secondary
                                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                        ) {
                                            Text(
                                                text = when {
                                                    isLed -> "PROG LED ON"
                                                    st?.reachable == true -> "ONLINE ${st.latencyMs}ms"
                                                    st != null && !st.reachable -> "OFFLINE"
                                                    pDev != null -> "IN PROJECT"
                                                    else -> "AVAILABLE"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isLed -> MaterialTheme.colorScheme.onTertiary
                                                    st?.reachable == true -> MaterialTheme.colorScheme.onPrimary
                                                    st != null && !st.reachable -> MaterialTheme.colorScheme.onError
                                                    pDev != null -> MaterialTheme.colorScheme.onSecondary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }

                                    if (pDev != null) {
                                        Text(
                                            text = "${pDev.productName.ifBlank { pDev.manufacturerName }} • $loc (${pDev.comObjects.size} objs)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    // Action buttons for selected address
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Button(
                                            onClick = { controller.pingIndividualDevice(addr.toString()) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Ping", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { onInspectDevice(addr) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Info", fontSize = 11.sp)
                                        }
                                        pDev?.let { dev ->
                                            OutlinedButton(
                                                onClick = {
                                                    controller.openDeviceObjectsInExplorer(dev.address)
                                                    controller.selectTab(AppTab.PROJECT)
                                                },
                                                modifier = Modifier.weight(1.2f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                            ) {
                                                Text("Objects", fontSize = 11.sp)
                                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }

                                    // Device Inline Communication Objects Quick-Test
                                    if (pDev != null && pDev.comObjects.isNotEmpty()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "COMMUNICATION OBJECTS (${pDev.comObjects.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = "Test Channel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        pDev.comObjects.take(3).forEach { obj ->
                                            val linkedGa = obj.linkedGroupAddresses.firstOrNull()?.toString()
                                            val liveVal = linkedGa?.let { gaStr ->
                                                GroupAddress.parseOrNull(gaStr)?.let { state.groupLiveValues[it]?.valueText }
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = "#${obj.number}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = obj.name.ifBlank { "Object ${obj.number}" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        text = "${obj.functionText.ifBlank { "Channel" }}${linkedGa?.let { " • $it" } ?: ""}${liveVal?.let { " • $it" } ?: ""}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                if (linkedGa != null) {
                                                    IconButton(
                                                        onClick = { controller.readGroupAddress(linkedGa) },
                                                        modifier = Modifier.size(24.dp),
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Read", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                                                    }
                                                    IconButton(
                                                        onClick = { controller.writeGroupSwitch(linkedGa, true) },
                                                        modifier = Modifier.size(24.dp),
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "ON", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Quick Commissioning for Available Empty Address
                                    if (pDev == null && addr.device != 0) {
                                        val progLeds = state.programmingModeDevices
                                        if (progLeds.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                                                        Column {
                                                            Text("Device in Prog Mode!", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                                            Text("Assign address $addr with 1 tap.", style = MaterialTheme.typography.bodySmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                                        }
                                                    }
                                                    Button(
                                                        onClick = { controller.commissionProgrammingDevice(addr.toString()) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                                            contentColor = MaterialTheme.colorScheme.onTertiary,
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp),
                                                    ) {
                                                        Text("Assign", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        }

        // VIEW MODE B: Device Cards List View
        if (lineViewMode == "LIST") {
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
}

@Composable
private fun MatrixLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Tab 2: Device Info & BCU Properties (ETS Geräteinfo)
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
            val decodedMfr = KnxHardwareCatalog.resolveManufacturer(d.manufacturerId)
            val decodedMask = KnxHardwareCatalog.resolveMask(d.maskVersionHex)

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

                        // 2-Column ETS Properties Table with Hardware Catalog Lookup
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PropertyRow(
                                name = "Mask Version (DD0)",
                                value = if (decodedMask != null) "${d.maskVersionHex} — $decodedMask" else (d.maskVersionHex ?: "Unknown"),
                            )
                            PropertyRow(
                                name = "Hardware Serial No.",
                                value = d.serialNumberHex ?: "Unknown",
                            )
                            PropertyRow(
                                name = "Manufacturer",
                                value = if (decodedMfr != null) "$decodedMfr (0x%04X)".format(d.manufacturerId)
                                else d.manufacturerId?.let { "0x%04X".format(it) } ?: "Unknown",
                            )
                            PropertyRow(
                                name = "Operational Run State",
                                value = d.runState ?: "Normal",
                            )
                            PropertyRow(
                                name = "Memory Load State",
                                value = d.loadState ?: "Loaded",
                            )
                            PropertyRow(
                                name = "Programming Mode LED",
                                value = if (d.programmingModeActive) "ACTIVE (RED ON)" else "OFF (Normal)",
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

                        // Device Communication Objects Preview (if in project)
                        projectDevice?.let { dev ->
                            if (dev.comObjects.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "COMMUNICATION OBJECTS (${dev.comObjects.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "C R W T U",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                dev.comObjects.take(5).forEach { obj ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "#${obj.number}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        val linkedGa = obj.linkedGroupAddresses.firstOrNull()?.toString()
                                        val liveVal = linkedGa?.let { gaStr ->
                                            GroupAddress.parseOrNull(gaStr)?.let { state.groupLiveValues[it]?.valueText }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = obj.name.ifBlank { "Object ${obj.number}" },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${obj.functionText.ifBlank { "Channel" }}${linkedGa?.let { " • $it" } ?: ""}${liveVal?.let { " • $it" } ?: ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (linkedGa != null) {
                                            IconButton(
                                                onClick = { controller.readGroupAddress(linkedGa) },
                                                modifier = Modifier.size(26.dp),
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Read", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                                            }
                                            IconButton(
                                                onClick = { controller.writeGroupSwitch(linkedGa, true) },
                                                modifier = Modifier.size(26.dp),
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "ON", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                            }
                                        }
                                        Text(
                                            text = obj.flags.formatCompact(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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

                    // Quick pick from unverified project devices
                    val unverifiedDevices = remember(proj, state.deviceStatuses) {
                        proj?.allLines?.flatMap { it.devices }
                            ?.filter { dev -> state.deviceStatuses[dev.address]?.reachable != true }
                            ?.take(6)
                            .orEmpty()
                    }
                    if (unverifiedDevices.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "QUICK PICK FROM UNCOMMISSIONED DEVICES",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(unverifiedDevices) { dev ->
                                    val isSelected = newProgAddrText == dev.address.toString()
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            onNewProgAddrChange(dev.address.toString())
                                            onCheckAddress(dev.address.toString())
                                        },
                                        label = {
                                            Text(
                                                text = "${dev.address} ${dev.name.take(14)}",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                            }
                        }
                    }

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
// Tab 4: Commissioning Walk-Test & Channel Sign-Off (Inbetriebnahme-Gehtest)
// ---------------------------------------------------------------------------

@Composable
private fun CommissioningWalkTestContent(
    state: AppUiState,
    controller: AppController,
    selectedRoom: String?,
    onSelectRoom: (String?) -> Unit,
    onFlagIssue: (GroupAddress) -> Unit,
) {
    val proj = state.project
    val allGas = remember(proj) { proj?.groupAddresses?.values.orEmpty().toList() }

    // Collect all spaces from project
    val availableRooms = remember(proj) {
        val spaces = mutableListOf<String>()
        fun scan(loc: BuildingLocation) {
            if (loc.type.equals("Room", ignoreCase = true) || loc.deviceAddresses.isNotEmpty()) {
                spaces.add(loc.name)
            }
            loc.children.forEach { scan(it) }
        }
        proj?.locations?.forEach { scan(it) }
        spaces.distinct()
    }

    // Filter GAs for selected room
    val activeGas = remember(allGas, selectedRoom, proj) {
        if (selectedRoom == null) allGas
        else {
            allGas.filter { ga ->
                ga.linkedObjects.any { link ->
                    link.deviceAddress?.let { devAddr ->
                        proj?.findDeviceLocationPath(devAddr)?.contains(selectedRoom, ignoreCase = true) == true
                    } ?: false
                }
            }
        }
    }

    val verifiedCount = activeGas.count { it.address in state.walkTestVerifiedChannels }
    val flaggedCount = activeGas.count { it.address in state.walkTestFlaggedChannels }
    val completionPercent = if (activeGas.isNotEmpty()) (verifiedCount * 100) / activeGas.size else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Room Filter Carousel
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "COMMISSIONING ROOM / SPACE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${verifiedCount}/${activeGas.size} Verified ($completionPercent%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        FilterChip(
                            selected = selectedRoom == null,
                            onClick = { onSelectRoom(null) },
                            label = { Text("All Rooms (${allGas.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                    items(availableRooms) { room ->
                        val isSelected = selectedRoom == room
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectRoom(room) },
                            label = { Text(room, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }
        }

        // Room Commissioning Progress Meter Card
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = selectedRoom ?: "Entire Building Commissioning",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Test switching, dimming, & blinds. Sign off each channel as functional.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$completionPercent%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { if (activeGas.isNotEmpty()) verifiedCount.toFloat() / activeGas.size.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }

        // Channels List for the Room
        items(activeGas) { gaInfo ->
            val ga = gaInfo.address
            val isVerified = ga in state.walkTestVerifiedChannels
            val issueNote = state.walkTestFlaggedChannels[ga]
            val isBlinking = state.walkTestActive && state.walkTestGroupAddress == ga
            val liveVal = state.groupLiveValues[ga]?.valueText

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isVerified) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    1.dp,
                    when {
                        isVerified -> MaterialTheme.colorScheme.primary
                        issueNote != null -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Header: GA, Name & Sign-Off Status Chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = ga.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gaInfo.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${gaInfo.dpt ?: "DPT 1.001"} • ${liveVal?.let { "Live: $it" } ?: "Not read yet"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Commissioning Sign-off Chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isVerified -> MaterialTheme.colorScheme.primary
                                issueNote != null -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.clickable { controller.toggleChannelWalkTestVerified(ga) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isVerified) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (isVerified) "VERIFIED" else if (issueNote != null) "ISSUE" else "SIGN OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVerified) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Issue Note Banner if flagged
                    issueNote?.let { note ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "⚠️ Issue: $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                    }

                    // Direct Field Controls (Tailored for Shutter, Dimmer, or Switch)
                    val isShutter = remember(gaInfo) {
                        gaInfo.name.contains("shutter", ignoreCase = true) ||
                            gaInfo.name.contains("blind", ignoreCase = true) ||
                            gaInfo.name.contains("rollo", ignoreCase = true) ||
                            gaInfo.name.contains("jalousie", ignoreCase = true) ||
                            gaInfo.dpt?.startsWith("1.008") == true
                    }
                    val isDimmer = remember(gaInfo) {
                        gaInfo.name.contains("dimm", ignoreCase = true) ||
                            gaInfo.dpt?.startsWith("5.001") == true
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Quick Read Button
                        IconButton(
                            onClick = { controller.readGroupAddress(ga.toString()) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Read", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        }

                        if (isShutter) {
                            Button(
                                onClick = { controller.writeGroupSwitch(ga.toString(), false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text("▲ UP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { controller.writeGroupDimStep(ga.toString(), false, 0) },
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Text("■ STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { controller.writeGroupSwitch(ga.toString(), true) },
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("▼ DOWN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (isDimmer) {
                            Button(
                                onClick = { controller.writeGroupSwitch(ga.toString(), false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text("OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { controller.writeGroupPercent(ga.toString(), 50.0) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Text("50%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { controller.writeGroupSwitch(ga.toString(), true) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("ON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { controller.writeGroupSwitch(ga.toString(), false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text("OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { controller.writeGroupSwitch(ga.toString(), true) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("ON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (isBlinking) controller.stopWalkTestBlink()
                                else controller.startWalkTestBlink(ga.toString())
                            },
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = if (isBlinking) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            else ButtonDefaults.outlinedButtonColors(),
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isBlinking) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = if (isBlinking) "Blink (${state.walkTestStepCount})" else "Blink",
                                fontSize = 11.sp,
                            )
                        }

                        IconButton(
                            onClick = { onFlagIssue(ga) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "Flag Issue",
                                tint = if (issueNote != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 5: Site Commissioning & Handover Report
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
    val totalGas = proj?.groupAddresses?.size ?: 0
    val verifiedCount = state.walkTestVerifiedChannels.size

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
                                    text = "${report.onlineDevices} of ${report.totalDevicesExpected} devices verified online\n" +
                                        "${verifiedCount} of $totalGas channels walk-tested & signed off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
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


// ---------------------------------------------------------------------------
// 1-Click Circuit Troubleshooter & Diagnostic Assistant (ETS Diagnose-Assistent)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileTroubleshootAssistantSheet(
    state: AppUiState,
    controller: AppController,
    onDismiss: () -> Unit,
) {
    val proj = state.project
    var targetInput by remember { mutableStateOf("") }
    var isDiagnosing by remember { mutableStateOf(false) }
    var diagnosisSteps by remember { mutableStateOf<List<Triple<String, String, Boolean?>>>(emptyList()) }
    var diagnosisVerdict by remember { mutableStateOf<String?>(null) }
    var isVerdictOk by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column {
                        Text(
                            text = "ETS 1-Click Circuit Troubleshooter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Automated 4-point health verification for any GA or Device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = targetInput,
                onValueChange = { targetInput = it },
                label = { Text("Group Address (1/1/10) or Device (1.1.2)") },
                placeholder = { Text("Enter GA or Individual Address to test") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )

            // Quick Suggestions from Project GAs
            val sampleGas = remember(proj) { proj?.groupAddresses?.values?.take(4).orEmpty() }
            if (sampleGas.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sampleGas) { ga ->
                        FilterChip(
                            selected = targetInput == ga.address.toString(),
                            onClick = { targetInput = ga.address.toString() },
                            label = { Text("${ga.address} ${ga.name.take(12)}", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val input = targetInput.trim()
                    if (input.isBlank()) return@Button
                    isDiagnosing = true
                    diagnosisSteps = emptyList()
                    diagnosisVerdict = null

                    coroutineScope.launch {
                        val steps = mutableListOf<Triple<String, String, Boolean?>>()
                        val parsedGa = GroupAddress.parseOrNull(input)
                        val parsedIa = IndividualAddress.parseOrNull(input)

                        if (parsedGa != null) {
                            val gaInfo = proj?.findGroupAddress(parsedGa)
                            val linkedDevs = gaInfo?.linkedObjects?.mapNotNull { it.deviceAddress }?.distinct().orEmpty()
                            val area = linkedDevs.firstOrNull()?.area ?: 1
                            val line = linkedDevs.firstOrNull()?.line ?: 1
                            val coupler = IndividualAddress(area, line, 0)

                            // Step 1: Coupler Check
                            steps.add(Triple("1. Line Coupler", "Pinging line coupler $coupler...", null))
                            diagnosisSteps = steps.toList()
                            controller.pingIndividualDevice(coupler.toString())
                            kotlinx.coroutines.delay(400)
                            val couplerOk = state.deviceStatuses[coupler]?.reachable == true
                            steps[0] = Triple("1. Line Coupler $coupler", if (couplerOk) "ONLINE (${state.deviceStatuses[coupler]?.latencyMs}ms)" else "UNREACHABLE (Timeout)", couplerOk)
                            diagnosisSteps = steps.toList()

                            // Step 2: Actuator / Devices Ping
                            if (linkedDevs.isNotEmpty()) {
                                steps.add(Triple("2. Controlling Devices", "Verifying ${linkedDevs.size} linked hardware units...", null))
                                diagnosisSteps = steps.toList()
                                var allDevsOk = true
                                for (dev in linkedDevs) {
                                    controller.pingIndividualDevice(dev.toString())
                                    kotlinx.coroutines.delay(300)
                                    val devOk = state.deviceStatuses[dev]?.reachable == true
                                    if (!devOk) allDevsOk = false
                                }
                                val onlineCount = linkedDevs.count { state.deviceStatuses[it]?.reachable == true }
                                steps[1] = Triple("2. Linked Hardware", "$onlineCount/${linkedDevs.size} devices responding online", allDevsOk)
                                diagnosisSteps = steps.toList()
                            } else {
                                steps.add(Triple("2. Linked Hardware", "No device links found in project XML", false))
                                diagnosisSteps = steps.toList()
                            }

                            // Step 3: Bus Group Value Read
                            steps.add(Triple("3. Live Group Value", "Sending A_GroupValue_Read to $parsedGa...", null))
                            diagnosisSteps = steps.toList()
                            controller.readGroupAddress(input)
                            kotlinx.coroutines.delay(500)
                            val liveVal = state.groupLiveValues[parsedGa]?.valueText
                            val valOk = liveVal != null
                            steps[2] = Triple("3. Group Value Response", if (valOk) "Received reply: '$liveVal'" else "No device replied to GroupRead", valOk)
                            diagnosisSteps = steps.toList()

                            // Verdict
                            isVerdictOk = couplerOk && (valOk || linkedDevs.any { state.deviceStatuses[it]?.reachable == true })
                            diagnosisVerdict = if (isVerdictOk) {
                                "✓ Circuit communication is verified. Hardware devices are online and acknowledging telegrams on the bus."
                            } else {
                                "⚠️ Fault detected on circuit $input. Check physical bus cable connection, line power, and coupler routing."
                            }
                        } else if (parsedIa != null) {
                            val coupler = IndividualAddress(parsedIa.area, parsedIa.line, 0)
                            // Step 1: Coupler Check
                            steps.add(Triple("1. Line Coupler", "Pinging line coupler $coupler...", null))
                            diagnosisSteps = steps.toList()
                            controller.pingIndividualDevice(coupler.toString())
                            kotlinx.coroutines.delay(400)
                            val couplerOk = state.deviceStatuses[coupler]?.reachable == true
                            steps[0] = Triple("1. Line Coupler $coupler", if (couplerOk) "ONLINE" else "TIMEOUT", couplerOk)
                            diagnosisSteps = steps.toList()

                            // Step 2: Device Ping
                            steps.add(Triple("2. Physical Ping (DD0)", "Pinging device $parsedIa...", null))
                            diagnosisSteps = steps.toList()
                            controller.pingIndividualDevice(input)
                            kotlinx.coroutines.delay(400)
                            val devOk = state.deviceStatuses[parsedIa]?.reachable == true
                            val lat = state.deviceStatuses[parsedIa]?.latencyMs
                            steps[1] = Triple("2. Device Response", if (devOk) "ONLINE (${lat}ms)" else "UNREACHABLE", devOk)
                            diagnosisSteps = steps.toList()

                            // Step 3: BCU Info
                            steps.add(Triple("3. BCU & Mask Inspection", "Reading mask and load state...", null))
                            diagnosisSteps = steps.toList()
                            controller.inspectDeviceDiagnostics(input)
                            kotlinx.coroutines.delay(500)
                            val diag = state.deviceDiagnostics
                            val bcuOk = diag?.reachable == true
                            steps[2] = Triple("3. BCU & Mask", if (bcuOk) "Mask ${diag?.maskVersionHex ?: "-"}, Run: ${diag?.runState ?: "Normal"}" else "Could not read BCU", bcuOk)
                            diagnosisSteps = steps.toList()

                            isVerdictOk = devOk
                            diagnosisVerdict = if (devOk) {
                                "✓ Device $input is healthy and operating normally on Line ${parsedIa.area}.${parsedIa.line}."
                            } else {
                                "⚠️ Device $input is unreachable. Verify physical connection to line ${parsedIa.area}.${parsedIa.line} and bus power."
                            }
                        } else {
                            diagnosisVerdict = "Invalid target format. Enter a Group Address (e.g. 1/1/10) or Individual Address (e.g. 1.1.2)."
                            isVerdictOk = false
                        }
                        isDiagnosing = false
                    }
                },
                enabled = !isDiagnosing && targetInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isDiagnosing) "Running 4-Point Health Diagnosis..." else "Run 4-Point Health Diagnosis", fontWeight = FontWeight.Bold)
            }

            if (isDiagnosing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Diagnostic Steps Progress List
            if (diagnosisSteps.isNotEmpty()) {
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DIAGNOSTIC VERIFICATION SEQUENCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        diagnosisSteps.forEach { (title, detail, status) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                when (status) {
                                    true -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    false -> Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    null -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(detail, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Final Diagnostic Verdict Card
            diagnosisVerdict?.let { verdict ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isVerdictOk) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, if (isVerdictOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (isVerdictOk) Icons.Default.VerifiedUser else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isVerdictOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = verdict,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}
