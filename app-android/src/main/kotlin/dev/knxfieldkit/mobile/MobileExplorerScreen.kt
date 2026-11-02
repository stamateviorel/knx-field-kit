package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

enum class MobileExplorerPane(val label: String) {
    TOPOLOGY("Topology"),
    GROUPS("Groups"),
    ROOMS("Rooms"),
}

@Composable
fun MobileExplorerScreen(
    state: AppUiState,
    controller: AppController,
    onInspectGroupSheet: (GroupAddressInfo) -> Unit,
    onOpenProjectFile: () -> Unit = {},
) {
    val project = state.project
    var pane by remember { mutableStateOf(MobileExplorerPane.TOPOLOGY) }
    var searchQuery by remember { mutableStateOf("") }

    // Drill-down states for crisp single-hand phone navigation
    var selectedLineId by remember(project) { mutableStateOf<String?>(null) }
    var activeMainRangeId by remember(project) { mutableStateOf<String?>(null) }
    var activeSubRangeId by remember(project) { mutableStateOf<String?>(null) }
    var flattenAllGroups by remember(project) { mutableStateOf(false) }

    // Sync external device drill-down if ExplorerSelection.Device was set on controller
    val drilledDeviceAddress = (state.explorerSelection as? dev.knxfieldkit.app.ExplorerSelection.Device)?.address

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MobileColors.BgCanvas)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Segmented Topology / Groups / Rooms Switcher
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MobileColors.BgTopBar,
            border = BorderStroke(1.dp, MobileColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MobileExplorerPane.entries.forEach { mode ->
                    val selected = pane == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selected) MobileColors.AccentEmerald.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (selected) MobileColors.AccentEmerald.copy(alpha = 0.65f) else Color.Transparent,
                                shape = RoundedCornerShape(7.dp),
                            )
                            .clickable {
                                pane = mode
                                if (mode != MobileExplorerPane.TOPOLOGY && drilledDeviceAddress != null) {
                                    controller.selectExplorerNode(dev.knxfieldkit.app.ExplorerSelection.TopologyRoot)
                                }
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MobileColors.AccentEmerald else MobileColors.TextSecondary,
                        )
                    }
                }
            }
        }

        // Instant Search Bar
        MobileSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search address (1.1.10, 1/1/1), device, room, DPT...",
        )

        if (project == null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MobileColors.BgCard,
                border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MobileColors.AccentEmerald, modifier = Modifier.size(36.dp))
                    Text("No KNX Project Loaded", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                    Text(
                        "Tap below or use the project icon at the top to select an ETS project or keyring.",
                        fontSize = 12.sp,
                        color = MobileColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onOpenProjectFile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MobileColors.AccentEmerald,
                            contentColor = MobileColors.BgCanvas,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open .knxproj / .knxkeys File", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }

        val q = searchQuery.trim().lowercase()

        when (pane) {
            MobileExplorerPane.TOPOLOGY -> {
                val activeDevice = drilledDeviceAddress?.let { project.findDevice(it) }
                if (activeDevice != null && q.isEmpty()) {
                    MobileDeviceObjectsDetail(
                        project = project,
                        device = activeDevice,
                        state = state,
                        controller = controller,
                        onBack = {
                            selectedLineId = "${activeDevice.address.area}.${activeDevice.address.line}"
                            controller.selectExplorerNode(dev.knxfieldkit.app.ExplorerSelection.TopologyRoot)
                        },
                    )
                } else {
                    val activeLine = selectedLineId?.let { id -> project.allLines.firstOrNull { it.id == id } }
                    if (activeLine != null && q.isEmpty()) {
                        MobileBreadcrumbBar(
                            crumbs = listOf("All Lines", "Line ${activeLine.id} ${activeLine.name}"),
                            onBack = { selectedLineId = null },
                            trailingAction = {
                                MobileActionPill("Audit Line", MobileColors.AccentEmerald) {
                                    controller.runProjectLineAudit(activeLine.area, activeLine.line)
                                    controller.selectTab(AppTab.DIAGNOSTICS)
                                }
                            },
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(activeLine.devices, key = { it.address.raw }) { dev ->
                                MobileDeviceCard(
                                    project = project,
                                    device = dev,
                                    ping = state.deviceStatuses[dev.address],
                                    onOpenObjects = { controller.openDeviceObjectsInExplorer(dev.address) },
                                    onPing = { controller.pingIndividualDevice(dev.address.toString()) },
                                )
                            }
                        }
                    } else {
                        // Show Line overview cards + matching devices
                        val filteredDevices = project.allDevices.values
                            .sortedBy { it.address }
                            .filter { dev ->
                                q.isEmpty() ||
                                    dev.address.toString().contains(q) ||
                                    dev.name.lowercase().contains(q) ||
                                    dev.productName.lowercase().contains(q) ||
                                    dev.manufacturerName.lowercase().contains(q) ||
                                    (project.findDeviceLocationPath(dev.address)?.lowercase()?.contains(q) == true)
                            }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (q.isEmpty()) {
                                item(key = "lines-header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "TP / IP LINES (${project.allLines.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MobileColors.TextSecondary,
                                        )
                                        Text(
                                            text = "${project.allDevices.size} devices total",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MobileColors.AccentSky,
                                        )
                                    }
                                }
                                items(project.allLines, key = { "line-${it.id}" }) { line ->
                                    val onlineCount = line.devices.count { state.deviceStatuses[it.address]?.reachable == true }
                                    val unreachCount = line.devices.count { state.deviceStatuses[it.address]?.reachable == false }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MobileColors.BgCard,
                                        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedLineId = line.id },
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            MobileBadge(line.id, MobileColors.AccentSky)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = line.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MobileColors.TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "${line.mediumType} • ${line.devices.size} devices" +
                                                        (if (onlineCount > 0) " • $onlineCount online" else "") +
                                                        (if (unreachCount > 0) " • $unreachCount fault" else ""),
                                                    fontSize = 11.sp,
                                                    color = if (unreachCount > 0) MobileColors.AccentRed else MobileColors.TextSecondary,
                                                )
                                            }
                                            MobileActionPill("Audit", MobileColors.AccentEmerald) {
                                                controller.runProjectLineAudit(line.area, line.line)
                                                controller.selectTab(AppTab.DIAGNOSTICS)
                                            }
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MobileColors.TextSecondary,
                                            )
                                        }
                                    }
                                }
                                item(key = "all-devs-header") {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "ALL PROJECT DEVICES (${filteredDevices.size}) — TAP FOR GROUP OBJECTS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MobileColors.TextSecondary,
                                    )
                                }
                            }

                            items(filteredDevices, key = { "dev-${it.address.raw}" }) { dev ->
                                MobileDeviceCard(
                                    project = project,
                                    device = dev,
                                    ping = state.deviceStatuses[dev.address],
                                    onOpenObjects = {
                                        searchQuery = ""
                                        controller.openDeviceObjectsInExplorer(dev.address)
                                    },
                                    onPing = { controller.pingIndividualDevice(dev.address.toString()) },
                                )
                            }
                        }
                    }
                }
            }

            MobileExplorerPane.GROUPS -> {
                val ranges = project.effectiveGroupRanges
                val mainRange = activeMainRangeId?.let { id -> ranges.firstOrNull { it.id == id } }
                val subRange = activeSubRangeId?.let { id -> mainRange?.children?.firstOrNull { it.id == id } }

                if (q.isNotEmpty() || flattenAllGroups) {
                    if (flattenAllGroups && q.isEmpty()) {
                        MobileBreadcrumbBar(
                            crumbs = listOf("Group Ranges", "All ${project.groupAddresses.size} Group Addresses"),
                            onBack = { flattenAllGroups = false },
                            trailingAction = {
                                MobileActionPill("Poll All", MobileColors.AccentSky) {
                                    controller.pollAllProjectGroupAddresses()
                                }
                            },
                        )
                    }
                    val matchingGas = project.groupAddresses.values
                        .sortedBy { it.address }
                        .filter { ga ->
                            q.isEmpty() ||
                                ga.address.toString().contains(q) ||
                                ga.name.lowercase().contains(q) ||
                                (ga.dpt?.lowercase()?.contains(q) == true) ||
                                ga.description.lowercase().contains(q)
                        }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(matchingGas, key = { it.address.raw }) { ga ->
                            MobileGroupAddressCard(
                                ga = ga,
                                liveEntry = state.groupLiveValues[ga.address],
                                controller = controller,
                                onInspect = { onInspectGroupSheet(ga) },
                            )
                        }
                    }
                } else if (subRange != null) {
                    MobileBreadcrumbBar(
                        crumbs = listOf(mainRange?.name ?: "Groups", "${subRange.prefixLabel} ${subRange.name}"),
                        onBack = { activeSubRangeId = null },
                    )
                    val gas = subRange.collectAllAddresses().mapNotNull { project.findGroupAddress(it) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(gas, key = { it.address.raw }) { ga ->
                            MobileGroupAddressCard(
                                ga = ga,
                                liveEntry = state.groupLiveValues[ga.address],
                                controller = controller,
                                onInspect = { onInspectGroupSheet(ga) },
                            )
                        }
                    }
                } else if (mainRange != null) {
                    MobileBreadcrumbBar(
                        crumbs = listOf("Group Ranges", "${mainRange.prefixLabel} ${mainRange.name}"),
                        onBack = { activeMainRangeId = null },
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(mainRange.children, key = { it.id }) { child ->
                            MobileGroupRangeFolderCard(node = child) { activeSubRangeId = child.id }
                        }
                        val direct = mainRange.directAddresses.mapNotNull { project.findGroupAddress(it) }
                        items(direct, key = { it.address.raw }) { ga ->
                            MobileGroupAddressCard(
                                ga = ga,
                                liveEntry = state.groupLiveValues[ga.address],
                                controller = controller,
                                onInspect = { onInspectGroupSheet(ga) },
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ETS GROUP RANGES (${project.groupAddresses.size} GAs)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobileColors.TextSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MobileActionPill("Show All GAs", MobileColors.AccentSky) { flattenAllGroups = true }
                            MobileActionPill("Poll All", MobileColors.AccentEmerald) { controller.pollAllProjectGroupAddresses() }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ranges, key = { it.id }) { node ->
                            MobileGroupRangeFolderCard(node = node) { activeMainRangeId = node.id }
                        }
                        // Also show top quick-control Group Addresses right below folders for immediate field use
                        item(key = "quick-ga-header") {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "QUICK FIELD CONTROLS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MobileColors.TextSecondary,
                            )
                        }
                        items(
                            project.groupAddresses.values.sortedBy { it.address }.take(8),
                            key = { "quick-${it.address.raw}" },
                        ) { ga ->
                            MobileGroupAddressCard(
                                ga = ga,
                                liveEntry = state.groupLiveValues[ga.address],
                                controller = controller,
                                onInspect = { onInspectGroupSheet(ga) },
                            )
                        }
                    }
                }
            }

            MobileExplorerPane.ROOMS -> {
                val roomSpaces = remember(project) {
                    val out = mutableListOf<Triple<String, BuildingLocation, List<ProjectDevice>>>()
                    fun visit(loc: BuildingLocation, prefix: String) {
                        val path = if (prefix.isEmpty()) loc.name else "$prefix › ${loc.name}"
                        if (loc.deviceAddresses.isNotEmpty()) {
                            val devs = loc.deviceAddresses.mapNotNull { project.findDevice(it) }
                            out += Triple(path, loc, devs)
                        }
                        loc.children.forEach { visit(it, path) }
                    }
                    project.locations.forEach { visit(it, "") }
                    out
                }.filter { (path, loc, devs) ->
                    q.isEmpty() ||
                        path.lowercase().contains(q) ||
                        loc.type.lowercase().contains(q) ||
                        devs.any { it.name.lowercase().contains(q) || it.address.toString().contains(q) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(roomSpaces, key = { it.first }) { (path, loc, devs) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MobileColors.BgCard,
                            border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = if (loc.type.equals("Cabinet", ignoreCase = true)) Icons.Default.Dns else Icons.Default.MeetingRoom,
                                        contentDescription = null,
                                        tint = MobileColors.AccentAmber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = path,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MobileColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    MobileBadge("${devs.size} dev", MobileColors.AccentAmber)
                                }
                                devs.forEach { dev ->
                                    val ping = state.deviceStatuses[dev.address]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MobileColors.BgInput)
                                            .clickable {
                                                pane = MobileExplorerPane.TOPOLOGY
                                                controller.openDeviceObjectsInExplorer(dev.address)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        MobileBadge(dev.address.toString(), MobileColors.AccentEmerald)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dev.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MobileColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${dev.manufacturerName} ${dev.productName} • ${dev.comObjects.size} ComObj",
                                                fontSize = 10.sp,
                                                color = MobileColors.TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (ping != null) {
                                            MobileBadge(
                                                text = if (ping.reachable) "OK" else "ERR",
                                                color = if (ping.reachable) MobileColors.AccentEmerald else MobileColors.AccentRed,
                                            )
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MobileColors.TextMuted, modifier = Modifier.size(16.dp))
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

@Composable
private fun MobileBreadcrumbBar(
    crumbs: List<String>,
    onBack: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MobileColors.BgTopBar)
            .border(1.dp, MobileColors.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MobileColors.BgCardElevated)
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MobileColors.AccentSky, modifier = Modifier.size(14.dp))
            Text("Back", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MobileColors.AccentSky)
        }
        Text(
            text = crumbs.joinToString(" › "),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MobileColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailingAction?.invoke()
    }
}

@Composable
fun MobileActionPill(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun MobileDeviceCard(
    project: KnxProject,
    device: ProjectDevice,
    ping: DevicePingResult?,
    onOpenObjects: () -> Unit,
    onPing: () -> Unit,
) {
    val roomPath = project.findDeviceLocationPath(device.address)
    val statusColor = when {
        ping == null -> MobileColors.TextMuted
        ping.reachable -> MobileColors.AccentEmerald
        else -> MobileColors.AccentRed
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MobileColors.BgCard,
        border = BorderStroke(
            1.dp,
            if (ping?.reachable == false) MobileColors.AccentRed.copy(alpha = 0.55f) else MobileColors.BorderSubtle,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenObjects),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                MobileBadge(device.address.toString(), MobileColors.AccentEmerald)
                Text(
                    text = device.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MobileColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MobileActionPill("Ping", MobileColors.AccentSky, onClick = onPing)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("${device.manufacturerName} ${device.productName}".trim())
                        if (!roomPath.isNullOrBlank()) append(" • $roomPath")
                    },
                    fontSize = 11.sp,
                    color = MobileColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ping != null) {
                        MobileBadge(
                            text = if (ping.reachable) "${ping.latencyMs}ms" else "FAULT",
                            color = statusColor,
                        )
                    }
                    MobileBadge("${device.comObjects.size} Obj", MobileColors.AccentPurple)
                }
            }
        }
    }
}

@Composable
private fun MobileDeviceObjectsDetail(
    project: KnxProject,
    device: ProjectDevice,
    state: AppUiState,
    controller: AppController,
    onBack: () -> Unit,
) {
    val ping = state.deviceStatuses[device.address]
    val room = project.findDeviceLocationPath(device.address) ?: "Unassigned Location"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MobileBreadcrumbBar(
            crumbs = listOf("Line ${device.address.area}.${device.address.line}", "${device.address} ${device.name}"),
            onBack = onBack,
        )

        // Device Summary Header Card
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MobileBadge(device.address.toString(), MobileColors.AccentEmerald)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobileColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${device.manufacturerName} • ${device.productName} (${device.orderNumber})",
                            fontSize = 11.sp,
                            color = MobileColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MobileBadge("Mask ${device.maskVersion ?: "N/A"}", MobileColors.AccentSky)
                    MobileBadge(room.substringAfterLast("›").trim(), MobileColors.AccentAmber)
                    if (ping != null) {
                        MobileBadge(
                            text = if (ping.reachable) "ONLINE ${ping.latencyMs}ms" else "UNREACHABLE",
                            color = if (ping.reachable) MobileColors.AccentEmerald else MobileColors.AccentRed,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MobileActionPill("Ping DD0", MobileColors.AccentEmerald) {
                        controller.pingIndividualDevice(device.address.toString())
                    }
                    MobileActionPill("BCU Info", MobileColors.AccentSky) {
                        controller.inspectDeviceDiagnostics(device.address.toString())
                        controller.selectTab(AppTab.DIAGNOSTICS)
                    }
                    MobileActionPill("Restart BCU", MobileColors.AccentAmber) {
                        controller.restartRemoteDevice(device.address.toString())
                    }
                }
            }
        }

        Text(
            text = "ETS GROUP OBJECTS (${device.comObjects.size}) — C R W T U FLAGS & LIVE CONTROL",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MobileColors.TextSecondary,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(device.comObjects, key = { "${it.number}-${it.id}" }) { obj ->
                val primaryGa = obj.linkedGroupAddresses.firstOrNull()
                val liveVal = primaryGa?.let { state.groupLiveValues[it]?.valueText }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MobileColors.BgCard,
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MobileBadge("#${obj.number}", MobileColors.AccentPurple)
                            Text(
                                text = obj.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MobileColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            MobileBadge(obj.sizeLabel, MobileColors.TextSecondary)
                            obj.dpt?.let { MobileBadge(it, MobileColors.AccentSky) }
                        }

                        if (obj.functionText.isNotBlank()) {
                            Text(
                                text = obj.functionText,
                                fontSize = 11.sp,
                                color = MobileColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MobileComFlagsStrip(obj.flags)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (primaryGa != null) {
                                    MobileBadge("GA $primaryGa", MobileColors.AccentEmerald)
                                }
                                if (!liveVal.isNullOrBlank()) {
                                    MobileBadge(liveVal, MobileColors.AccentAmber)
                                }
                            }
                        }

                        if (primaryGa != null) {
                            val gaText = primaryGa.toString()
                            val mainDpt = DptCodec.resolveMainDpt(obj.dpt)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                when (mainDpt) {
                                    MainDpt.DPT_1_BOOL -> {
                                        MobileActionPill("OFF", MobileColors.TextSecondary) {
                                            controller.writeGroupSwitch(gaText, false)
                                        }
                                        MobileActionPill("ON", MobileColors.AccentEmerald) {
                                            controller.writeGroupSwitch(gaText, true)
                                        }
                                    }
                                    MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
                                        MobileActionPill("0%", MobileColors.TextSecondary) {
                                            controller.writeGroupPercent(gaText, 0.0)
                                        }
                                        MobileActionPill("50%", MobileColors.AccentSky) {
                                            controller.writeGroupPercent(gaText, 50.0)
                                        }
                                        MobileActionPill("100%", MobileColors.AccentEmerald) {
                                            controller.writeGroupPercent(gaText, 100.0)
                                        }
                                    }
                                    else -> Unit
                                }
                                MobileActionPill("Read", MobileColors.AccentSky) {
                                    controller.readGroupAddress(gaText)
                                }
                                MobileActionPill("Sender →", MobileColors.AccentAmber) {
                                    controller.selectGroupAddressForSender(gaText)
                                    controller.selectTab(AppTab.SENDER)
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
private fun MobileGroupRangeFolderCard(
    node: GroupRangeNode,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MobileColors.BgCard,
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MobileColors.AccentSky, modifier = Modifier.size(20.dp))
            MobileBadge(node.prefixLabel, MobileColors.AccentSky)
            Text(
                text = node.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MobileColors.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MobileBadge("${node.totalCount} GA", MobileColors.AccentEmerald)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MobileColors.TextSecondary)
        }
    }
}

@Composable
private fun MobileGroupAddressCard(
    ga: GroupAddressInfo,
    liveEntry: dev.knxfieldkit.app.GroupValueEntry?,
    controller: AppController,
    onInspect: () -> Unit,
) {
    val gaText = ga.address.toString()
    val mainDpt = DptCodec.resolveMainDpt(ga.dpt)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MobileColors.BgCard,
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MobileBadge(gaText, MobileColors.AccentSky)
                Text(
                    text = ga.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MobileColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (liveEntry != null) {
                    MobileBadge(liveEntry.valueText, MobileColors.AccentEmerald)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MobileBadge("DPT ${ga.dpt ?: "raw"}", MobileColors.AccentPurple)
                    MobileBadge("${ga.linkedObjects.size} Obj", MobileColors.TextSecondary)
                }

                // Thumb-friendly 1-tap controls right in Explorer!
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (mainDpt) {
                        MainDpt.DPT_1_BOOL -> {
                            MobileActionPill("OFF", MobileColors.TextSecondary) {
                                controller.writeGroupSwitch(gaText, false)
                            }
                            MobileActionPill("ON", MobileColors.AccentEmerald) {
                                controller.writeGroupSwitch(gaText, true)
                            }
                            MobileActionPill("Read", MobileColors.AccentSky) {
                                controller.readGroupAddress(gaText)
                            }
                        }
                        MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
                            MobileActionPill("0%", MobileColors.TextSecondary) {
                                controller.writeGroupPercent(gaText, 0.0)
                            }
                            MobileActionPill("50%", MobileColors.AccentSky) {
                                controller.writeGroupPercent(gaText, 50.0)
                            }
                            MobileActionPill("100%", MobileColors.AccentEmerald) {
                                controller.writeGroupPercent(gaText, 100.0)
                            }
                        }
                        MainDpt.DPT_3_DIM_STEP -> {
                            MobileActionPill("−", MobileColors.TextSecondary) {
                                controller.writeGroupDimStep(gaText, increase = false)
                            }
                            MobileActionPill("Stop", MobileColors.AccentAmber) {
                                controller.writeGroupDimStep(gaText, increase = false, stepCode = 0)
                            }
                            MobileActionPill("+", MobileColors.AccentEmerald) {
                                controller.writeGroupDimStep(gaText, increase = true)
                            }
                        }
                        MainDpt.DPT_17_SCENE -> {
                            MobileActionPill("S1", MobileColors.AccentEmerald) { controller.writeGroupScene(gaText, 1) }
                            MobileActionPill("S2", MobileColors.AccentSky) { controller.writeGroupScene(gaText, 2) }
                            MobileActionPill("S3", MobileColors.AccentAmber) { controller.writeGroupScene(gaText, 3) }
                        }
                        else -> {
                            MobileActionPill("Read", MobileColors.AccentSky) {
                                controller.readGroupAddress(gaText)
                            }
                            MobileActionPill("Send…", MobileColors.AccentAmber) {
                                controller.selectGroupAddressForSender(gaText)
                                controller.selectTab(AppTab.SENDER)
                            }
                        }
                    }
                }
            }
        }
    }
}
