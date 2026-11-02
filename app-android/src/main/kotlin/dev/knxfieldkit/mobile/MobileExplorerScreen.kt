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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.app.AppController
import dev.knxfieldkit.app.AppTab
import dev.knxfieldkit.app.AppUiState
import dev.knxfieldkit.app.ExplorerSelection
import dev.knxfieldkit.app.GroupValueEntry
import dev.knxfieldkit.model.*

enum class MobileExplorerPane(val label: String, val icon: ImageVector) {
    TOPOLOGY("Topology", Icons.Default.AccountTree),
    GROUPS("Group Addresses", Icons.Default.Folder),
    ROOMS("Buildings", Icons.Default.Apartment),
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

    // ETS Hierarchical Drill-Down State
    var selectedAreaNumber by remember(project) { mutableStateOf<Int?>(null) }
    var selectedLineId by remember(project) { mutableStateOf<String?>(null) }
    var activeMainRangeId by remember(project) { mutableStateOf<String?>(null) }
    var activeSubRangeId by remember(project) { mutableStateOf<String?>(null) }
    var flattenAllGroups by remember(project) { mutableStateOf(false) }

    // Sync external device drill-down if ExplorerSelection.Device was set on controller
    val drilledDeviceAddress = (state.explorerSelection as? ExplorerSelection.Device)?.address

    LaunchedEffect(state.explorerSelection) {
        when (val sel = state.explorerSelection) {
            is ExplorerSelection.Area -> {
                pane = MobileExplorerPane.TOPOLOGY
                selectedAreaNumber = sel.area
                selectedLineId = null
            }
            is ExplorerSelection.Line -> {
                pane = MobileExplorerPane.TOPOLOGY
                selectedAreaNumber = sel.area
                selectedLineId = "${sel.area}.${sel.line}"
            }
            is ExplorerSelection.Device -> {
                pane = MobileExplorerPane.TOPOLOGY
                selectedAreaNumber = sel.address.area
                selectedLineId = "${sel.address.area}.${sel.address.line}"
            }
            is ExplorerSelection.GroupRange -> {
                pane = MobileExplorerPane.GROUPS
                activeMainRangeId = sel.rangeId
            }
            is ExplorerSelection.AllGroupAddresses -> {
                pane = MobileExplorerPane.GROUPS
                flattenAllGroups = true
            }
            is ExplorerSelection.BuildingSpace -> {
                pane = MobileExplorerPane.ROOMS
            }
            is ExplorerSelection.TopologyRoot -> {
                pane = MobileExplorerPane.TOPOLOGY
                selectedAreaNumber = null
                selectedLineId = null
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MobileColors.BgCanvas)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. Google Material 3 Perspective Tabs (Topology, Groups, Buildings)
        PrimaryTabRow(
            selectedTabIndex = pane.ordinal,
            containerColor = MobileColors.BgCard,
            contentColor = MobileColors.AccentEmerald,
            divider = {},
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            MobileExplorerPane.entries.forEach { mode ->
                val selected = pane == mode
                Tab(
                    selected = selected,
                    onClick = {
                        pane = mode
                        if (mode != MobileExplorerPane.TOPOLOGY && drilledDeviceAddress != null) {
                            controller.selectExplorerNode(ExplorerSelection.TopologyRoot)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = mode.icon,
                            contentDescription = mode.label,
                            tint = if (selected) MobileColors.AccentEmerald else MobileColors.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MobileColors.AccentEmerald else MobileColors.TextSecondary,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        // 2. Google Material 3 Search Bar
        MobileSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search Topology, Lines, Devices, GAs, DPTs...",
        )

        // Empty Project State
        if (project == null) {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
                border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MobileColors.AccentEmerald,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "No KNX Project Loaded",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MobileColors.TextPrimary,
                    )
                    Text(
                        text = "Open an ETS4, ETS5, or ETS6 project (.knxproj) or keyring (.knxkeys) from your phone storage.",
                        fontSize = 13.sp,
                        color = MobileColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Button(
                        onClick = onOpenProjectFile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MobileColors.AccentEmerald,
                            contentColor = MobileColors.OnAccent,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 6.dp),
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

        // 3. Global Instant Search Results (when searching)
        if (q.isNotEmpty()) {
            val matchingDevs = project.allDevices.values.filter {
                it.address.toString().contains(q) ||
                    it.name.lowercase().contains(q) ||
                    it.productName.lowercase().contains(q) ||
                    it.manufacturerName.lowercase().contains(q)
            }
            val matchingGas = project.groupAddresses.values.filter {
                it.address.toString().contains(q) ||
                    it.name.lowercase().contains(q) ||
                    (it.dpt?.lowercase()?.contains(q) == true)
            }
            val matchingLines = project.allLines.filter {
                it.id.contains(q) || it.name.lowercase().contains(q)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "search-summary") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "SEARCH RESULTS FOR \"$q\"",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobileColors.TextSecondary,
                        )
                        Text(
                            text = "${matchingDevs.size} devs • ${matchingGas.size} GAs",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MobileColors.AccentSky,
                        )
                    }
                }

                if (matchingGas.isNotEmpty()) {
                    item(key = "hdr-gas") {
                        Text(
                            text = "GROUP ADDRESSES (${matchingGas.size}) — IN-LINE TEST CONTROLS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobileColors.AccentSky,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(matchingGas, key = { "ga-${it.address.raw}" }) { ga ->
                        MobileGroupAddressCard(
                            ga = ga,
                            project = project,
                            liveEntry = state.groupLiveValues[ga.address],
                            controller = controller,
                            onInspect = { onInspectGroupSheet(ga) },
                            onJumpToDevice = { devAddr ->
                                searchQuery = ""
                                pane = MobileExplorerPane.TOPOLOGY
                                controller.openDeviceObjectsInExplorer(devAddr)
                            },
                        )
                    }
                }

                if (matchingDevs.isNotEmpty()) {
                    item(key = "hdr-devs") {
                        Text(
                            text = "DEVICES (${matchingDevs.size}) — TAP FOR ETS GROUP OBJECTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobileColors.AccentEmerald,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(matchingDevs, key = { "dev-${it.address.raw}" }) { dev ->
                        MobileDeviceCard(
                            project = project,
                            device = dev,
                            ping = state.deviceStatuses[dev.address],
                            onOpenObjects = {
                                searchQuery = ""
                                pane = MobileExplorerPane.TOPOLOGY
                                controller.openDeviceObjectsInExplorer(dev.address)
                            },
                            onPing = { controller.pingIndividualDevice(dev.address.toString()) },
                        )
                    }
                }

                if (matchingLines.isNotEmpty()) {
                    item(key = "hdr-lines") {
                        Text(
                            text = "TOPOLOGY LINES (${matchingLines.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MobileColors.AccentAmber,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(matchingLines, key = { "line-${it.id}" }) { line ->
                        MobileLineCard(
                            line = line,
                            state = state,
                            controller = controller,
                            onClick = {
                                searchQuery = ""
                                pane = MobileExplorerPane.TOPOLOGY
                                selectedAreaNumber = line.area
                                selectedLineId = line.id
                            },
                        )
                    }
                }

                if (matchingDevs.isEmpty() && matchingGas.isEmpty() && matchingLines.isEmpty()) {
                    item(key = "no-search-results") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No devices, group addresses, or lines matching \"$q\"",
                                fontSize = 13.sp,
                                color = MobileColors.TextMuted,
                            )
                        }
                    }
                }
            }
            return@Column
        }

        // 4. Perspective Content (Topology, Groups, Buildings)
        when (pane) {
            MobileExplorerPane.TOPOLOGY -> {
                val activeDevice = drilledDeviceAddress?.let { project.findDevice(it) }

                if (activeDevice != null) {
                    // Level 3: Device Group Objects (C R W T U Flags & In-line Controls)
                    MobileDeviceObjectsDetail(
                        project = project,
                        device = activeDevice,
                        state = state,
                        controller = controller,
                        onBack = {
                            selectedLineId = "${activeDevice.address.area}.${activeDevice.address.line}"
                            selectedAreaNumber = activeDevice.address.area
                            controller.selectExplorerNode(ExplorerSelection.TopologyRoot)
                        },
                        onInspectGa = { ga -> onInspectGroupSheet(ga) },
                    )
                } else if (selectedLineId != null) {
                    // Level 2: Devices on Selected Line
                    val activeLine = project.allLines.firstOrNull { it.id == selectedLineId }
                    if (activeLine != null) {
                        MobileBreadcrumbBar(
                            crumbs = listOf(
                                "Area ${activeLine.area}",
                                "Line ${activeLine.id} ${activeLine.name}",
                            ),
                            onBack = { selectedLineId = null },
                            trailingAction = {
                                Button(
                                    onClick = {
                                        controller.runProjectLineAudit(activeLine.area, activeLine.line)
                                        controller.selectTab(AppTab.DIAGNOSTICS)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MobileColors.AccentEmerald,
                                        contentColor = MobileColors.OnAccent,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Audit Line", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "line-dev-header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "DEVICES ON LINE ${activeLine.id} (${activeLine.devices.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MobileColors.TextSecondary,
                                    )
                                    Text(
                                        text = "${activeLine.mediumType} bus",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MobileColors.AccentSky,
                                    )
                                }
                            }

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
                        selectedLineId = null
                    }
                } else if (selectedAreaNumber != null) {
                    // Level 1: Lines in Selected Area
                    val activeArea = project.areas.firstOrNull { it.area == selectedAreaNumber }
                    if (activeArea != null) {
                        MobileBreadcrumbBar(
                            crumbs = listOf("Topology", "Area ${activeArea.area} ${activeArea.name}"),
                            onBack = { selectedAreaNumber = null },
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "area-lines-header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "LINES IN AREA ${activeArea.area} (${activeArea.lines.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MobileColors.TextSecondary,
                                    )
                                    Text(
                                        text = "${activeArea.lines.sumOf { it.devices.size }} devices total",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MobileColors.AccentSky,
                                    )
                                }
                            }

                            items(activeArea.lines, key = { "line-${it.id}" }) { line ->
                                MobileLineCard(
                                    line = line,
                                    state = state,
                                    controller = controller,
                                    onClick = { selectedLineId = line.id },
                                )
                            }
                        }
                    } else {
                        selectedAreaNumber = null
                    }
                } else {
                    // Level 0: ETS Topology Root (Areas)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "topology-header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "ETS TOPOLOGY — AREAS (${project.areas.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MobileColors.TextSecondary,
                                )
                                Text(
                                    text = "${project.allDevices.size} devs • ${project.allLines.size} lines",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MobileColors.AccentSky,
                                )
                            }
                        }

                        items(project.areas, key = { "area-${it.area}" }) { area ->
                            MobileAreaCard(
                                area = area,
                                onClick = {
                                    if (area.lines.size == 1) {
                                        selectedAreaNumber = area.area
                                        selectedLineId = area.lines.first().id
                                    } else {
                                        selectedAreaNumber = area.area
                                    }
                                },
                            )
                        }
                    }
                }
            }

            MobileExplorerPane.GROUPS -> {
                val ranges = project.effectiveGroupRanges
                val mainRange = activeMainRangeId?.let { id -> ranges.firstOrNull { it.id == id } }
                val subRange = activeSubRangeId?.let { id -> mainRange?.children?.firstOrNull { it.id == id } }

                if (flattenAllGroups) {
                    // Flat View (All Group Addresses in Project)
                    MobileBreadcrumbBar(
                        crumbs = listOf("Groups", "All ${project.groupAddresses.size} Group Addresses"),
                        onBack = { flattenAllGroups = false },
                        trailingAction = {
                            Button(
                                onClick = { controller.pollAllProjectGroupAddresses() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MobileColors.AccentSky,
                                    contentColor = MobileColors.OnAccent,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Poll All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                    )

                    val allGas = project.groupAddresses.values.sortedBy { it.address }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(allGas, key = { it.address.raw }) { ga ->
                            MobileGroupAddressCard(
                                ga = ga,
                                project = project,
                                liveEntry = state.groupLiveValues[ga.address],
                                controller = controller,
                                onInspect = { onInspectGroupSheet(ga) },
                                onJumpToDevice = { devAddr ->
                                    pane = MobileExplorerPane.TOPOLOGY
                                    controller.openDeviceObjectsInExplorer(devAddr)
                                },
                            )
                        }
                    }
                } else if (subRange != null) {
                    // Level 3: Sub Groups (Group Addresses in Middle Group)
                    MobileBreadcrumbBar(
                        crumbs = listOf(
                            mainRange?.name ?: "Groups",
                            "${subRange.prefixLabel} ${subRange.name}",
                        ),
                        onBack = { activeSubRangeId = null },
                        trailingAction = {
                            val addrs = subRange.collectAllAddresses()
                            Button(
                                onClick = { addrs.forEach { controller.readGroupAddress(it.toString()) } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MobileColors.AccentSky,
                                    contentColor = MobileColors.OnAccent,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Poll Group", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                    )

                    val gas = subRange.collectAllAddresses().mapNotNull { project.findGroupAddress(it) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(gas, key = { it.address.raw }) { ga ->
                            MobileGroupAddressCard(
                                ga = ga,
                                project = project,
                                liveEntry = state.groupLiveValues[ga.address],
                                controller = controller,
                                onInspect = { onInspectGroupSheet(ga) },
                                onJumpToDevice = { devAddr ->
                                    pane = MobileExplorerPane.TOPOLOGY
                                    controller.openDeviceObjectsInExplorer(devAddr)
                                },
                            )
                        }
                    }
                } else if (mainRange != null) {
                    // Level 2: Middle Groups in Main Group
                    MobileBreadcrumbBar(
                        crumbs = listOf("All Groups", "${mainRange.prefixLabel} ${mainRange.name}"),
                        onBack = { activeMainRangeId = null },
                        trailingAction = {
                            val addrs = mainRange.collectAllAddresses()
                            Button(
                                onClick = { addrs.take(40).forEach { controller.readGroupAddress(it.toString()) } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MobileColors.AccentSky,
                                    contentColor = MobileColors.OnAccent,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Poll All in Main", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "middle-groups-header") {
                            Text(
                                text = "MIDDLE GROUPS IN ${mainRange.name.uppercase()} (${mainRange.children.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MobileColors.TextSecondary,
                            )
                        }

                        items(mainRange.children, key = { it.id }) { child ->
                            MobileGroupRangeFolderCard(
                                node = child,
                                controller = controller,
                                onClick = { activeSubRangeId = child.id },
                            )
                        }

                        val direct = mainRange.directAddresses.mapNotNull { project.findGroupAddress(it) }
                        if (direct.isNotEmpty()) {
                            item(key = "direct-gas-header") {
                                Text(
                                    text = "DIRECT GROUP ADDRESSES (${direct.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MobileColors.TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items(direct, key = { it.address.raw }) { ga ->
                                MobileGroupAddressCard(
                                    ga = ga,
                                    project = project,
                                    liveEntry = state.groupLiveValues[ga.address],
                                    controller = controller,
                                    onInspect = { onInspectGroupSheet(ga) },
                                    onJumpToDevice = { devAddr ->
                                        pane = MobileExplorerPane.TOPOLOGY
                                        controller.openDeviceObjectsInExplorer(devAddr)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    // Level 1: Main Groups (ETS 3-Level Root)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "main-groups-header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "ETS MAIN GROUPS (${ranges.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MobileColors.TextSecondary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(
                                        onClick = { flattenAllGroups = true },
                                        label = { Text("Flat View (All GAs)", fontSize = 10.sp) },
                                        leadingIcon = { Icon(Icons.Default.List, null, modifier = Modifier.size(13.dp)) },
                                        modifier = Modifier.height(28.dp),
                                    )
                                    AssistChip(
                                        onClick = { controller.pollAllProjectGroupAddresses() },
                                        label = { Text("Poll All", fontSize = 10.sp) },
                                        leadingIcon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(13.dp)) },
                                        modifier = Modifier.height(28.dp),
                                    )
                                }
                            }
                        }

                        items(ranges, key = { it.id }) { node ->
                            MobileGroupRangeFolderCard(
                                node = node,
                                controller = controller,
                                onClick = { activeMainRangeId = node.id },
                            )
                        }
                    }
                }
            }

            MobileExplorerPane.ROOMS -> {
                // Buildings › Floors › Rooms Semantic Hierarchy
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
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "rooms-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "ETS BUILDINGS & ROOMS (${roomSpaces.size} Spaces)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MobileColors.TextSecondary,
                            )
                            Text(
                                text = "${project.locations.size} Buildings",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MobileColors.AccentAmber,
                            )
                        }
                    }

                    items(roomSpaces, key = { it.first }) { (path, loc, devs) ->
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
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
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MobileColors.BgInput,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                pane = MobileExplorerPane.TOPOLOGY
                                                controller.openDeviceObjectsInExplorer(dev.address)
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                                                    text = "${dev.manufacturerName} ${dev.productName} • ${dev.comObjects.size} objs",
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
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MobileColors.TextMuted,
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
        }
    }
}

// ---------------------------------------------------------------------------
// Area Card Component
// ---------------------------------------------------------------------------
@Composable
private fun MobileAreaCard(
    area: TopologyArea,
    onClick: () -> Unit,
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MobileColors.AccentEmerald.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = MobileColors.AccentEmerald,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MobileBadge("Area ${area.area}", MobileColors.AccentEmerald)
                    Text(
                        text = area.name.ifBlank { "Area ${area.area}" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MobileColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${area.lines.size} TP/IP Lines • ${area.lines.sumOf { it.devices.size }} Devices",
                    fontSize = 11.sp,
                    color = MobileColors.TextSecondary,
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MobileColors.TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Line Card Component
// ---------------------------------------------------------------------------
@Composable
private fun MobileLineCard(
    line: TopologyLine,
    state: AppUiState,
    controller: AppController,
    onClick: () -> Unit,
) {
    val onlineCount = line.devices.count { state.deviceStatuses[it.address]?.reachable == true }
    val unreachCount = line.devices.count { state.deviceStatuses[it.address]?.reachable == false }

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MobileBadge(line.id, MobileColors.AccentSky)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name.ifBlank { "Line ${line.id}" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MobileColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${line.mediumType} • ${line.devices.size} devices" +
                        (if (onlineCount > 0) " • $onlineCount OK" else "") +
                        (if (unreachCount > 0) " • $unreachCount ERR" else ""),
                    fontSize = 11.sp,
                    color = if (unreachCount > 0) MobileColors.AccentRed else MobileColors.TextSecondary,
                )
            }

            Button(
                onClick = {
                    controller.runProjectLineAudit(line.area, line.line)
                    controller.selectTab(AppTab.DIAGNOSTICS)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MobileColors.AccentEmerald,
                    contentColor = MobileColors.OnAccent,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text("Audit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MobileColors.TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Device Objects Detail Screen (ETS ComObjects Table & Live Controls)
// ---------------------------------------------------------------------------
@Composable
private fun MobileDeviceObjectsDetail(
    project: KnxProject,
    device: ProjectDevice,
    state: AppUiState,
    controller: AppController,
    onBack: () -> Unit,
    onInspectGa: (GroupAddressInfo) -> Unit,
) {
    val ping = state.deviceStatuses[device.address]
    val room = project.findDeviceLocationPath(device.address) ?: "Unassigned Room"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MobileBreadcrumbBar(
            crumbs = listOf(
                "Line ${device.address.area}.${device.address.line}",
                "${device.address} ${device.name}",
            ),
            onBack = onBack,
        )

        // Device Summary Card
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCardElevated),
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AssistChip(
                        onClick = { controller.pingIndividualDevice(device.address.toString()) },
                        label = { Text("Ping DD0", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Bolt, null, modifier = Modifier.size(13.dp)) },
                        modifier = Modifier.height(30.dp),
                    )
                    AssistChip(
                        onClick = {
                            controller.inspectDeviceDiagnostics(device.address.toString())
                            controller.selectTab(AppTab.DIAGNOSTICS)
                        },
                        label = { Text("BCU Info", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(13.dp)) },
                        modifier = Modifier.height(30.dp),
                    )
                    AssistChip(
                        onClick = { controller.restartRemoteDevice(device.address.toString()) },
                        label = { Text("Restart", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(13.dp)) },
                        modifier = Modifier.height(30.dp),
                    )
                }
            }
        }

        Text(
            text = "COMMUNICATION OBJECTS (${device.comObjects.size}) — FLAGS & LIVE TEST CONTROLS",
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

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
                            if (!liveVal.isNullOrBlank()) {
                                MobileBadge("Live: $liveVal", MobileColors.AccentEmerald)
                            }
                        }

                        // Linked Group Addresses with Click-to-Inspect
                        if (obj.linkedGroupAddresses.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Linked GA:", fontSize = 10.sp, color = MobileColors.TextMuted)
                                obj.linkedGroupAddresses.forEach { gaAddr ->
                                    val gaInfo = project.findGroupAddress(gaAddr)
                                    SuggestionChip(
                                        onClick = {
                                            if (gaInfo != null) onInspectGa(gaInfo)
                                        },
                                        label = { Text(gaAddr.toString(), fontSize = 10.sp) },
                                        modifier = Modifier.height(24.dp),
                                    )
                                }
                            }
                        }

                        // In-line Testing Action Controls
                        if (primaryGa != null) {
                            val gaText = primaryGa.toString()
                            val mainDpt = DptCodec.resolveMainDpt(obj.dpt)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when (mainDpt) {
                                    MainDpt.DPT_1_BOOL -> {
                                        Button(
                                            onClick = { controller.writeGroupSwitch(gaText, false) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MobileColors.BgInput,
                                                contentColor = MobileColors.TextPrimary,
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("OFF", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { controller.writeGroupSwitch(gaText, true) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MobileColors.AccentEmerald,
                                                contentColor = MobileColors.OnAccent,
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("ON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
                                        Button(
                                            onClick = { controller.writeGroupPercent(gaText, 0.0) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.BgInput, contentColor = MobileColors.TextPrimary),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("0%", fontSize = 10.sp)
                                        }
                                        Button(
                                            onClick = { controller.writeGroupPercent(gaText, 50.0) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = MobileColors.OnAccent),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("50%", fontSize = 10.sp)
                                        }
                                        Button(
                                            onClick = { controller.writeGroupPercent(gaText, 100.0) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = MobileColors.OnAccent),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("100%", fontSize = 10.sp)
                                        }
                                    }
                                    else -> Unit
                                }

                                Button(
                                    onClick = { controller.readGroupAddress(gaText) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MobileColors.AccentSky,
                                        contentColor = MobileColors.OnAccent,
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Read?", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
// Group Range Folder Card (ETS Main & Middle Group Folder)
// ---------------------------------------------------------------------------
@Composable
private fun MobileGroupRangeFolderCard(
    node: GroupRangeNode,
    controller: AppController,
    onClick: () -> Unit,
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MobileColors.AccentSky,
                modifier = Modifier.size(22.dp),
            )
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

            // 1-Tap Group Test: Poll all GAs in this group!
            IconButton(
                onClick = {
                    val addrs = node.collectAllAddresses()
                    addrs.take(30).forEach { controller.readGroupAddress(it.toString()) }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Test/Poll Group",
                    tint = MobileColors.AccentSky,
                    modifier = Modifier.size(16.dp),
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MobileColors.TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Group Address Card (In-line Testing + Connected ComObjects Link)
// ---------------------------------------------------------------------------
@Composable
private fun MobileGroupAddressCard(
    ga: GroupAddressInfo,
    project: KnxProject,
    liveEntry: GroupValueEntry?,
    controller: AppController,
    onInspect: () -> Unit,
    onJumpToDevice: (IndividualAddress) -> Unit,
) {
    val gaText = ga.address.toString()
    val mainDpt = DptCodec.resolveMainDpt(ga.dpt)
    var showLinks by remember { mutableStateOf(false) }

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header Row: Address Badge, GA Name, Live Value
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

            // Subtitle Row: DPT, Description, Links count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MobileBadge("DPT ${ga.dpt ?: "1.x"}", MobileColors.AccentPurple)
                    SuggestionChip(
                        onClick = { showLinks = !showLinks },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${ga.linkedObjects.size} Linked Objs", fontSize = 10.sp)
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    if (showLinks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        },
                        modifier = Modifier.height(24.dp),
                    )
                }

                if (ga.description.isNotBlank()) {
                    Text(
                        text = ga.description,
                        fontSize = 11.sp,
                        color = MobileColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // In-line Testing Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (mainDpt) {
                    MainDpt.DPT_1_BOOL -> {
                        Button(
                            onClick = { controller.writeGroupSwitch(gaText, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.BgInput, contentColor = MobileColors.TextPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("OFF", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { controller.writeGroupSwitch(gaText, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = MobileColors.OnAccent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("ON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { controller.readGroupAddress(gaText) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = MobileColors.OnAccent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Read?", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { controller.startWalkTestBlink(gaText, intervalMs = 1200L) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentAmber, contentColor = MobileColors.OnAccent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Blink", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
                        Button(
                            onClick = { controller.writeGroupPercent(gaText, 0.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.BgInput, contentColor = MobileColors.TextPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("0%", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { controller.writeGroupPercent(gaText, 50.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = MobileColors.OnAccent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("50%", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { controller.writeGroupPercent(gaText, 100.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = MobileColors.OnAccent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("100%", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { controller.readGroupAddress(gaText) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = MobileColors.OnAccent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Read?", fontSize = 10.sp)
                        }
                    }
                    else -> {
                        Button(
                            onClick = { controller.readGroupAddress(gaText) },
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = MobileColors.OnAccent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Read?", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onInspect,
                            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.BgInput, contentColor = MobileColors.TextPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Inspect", fontSize = 10.sp)
                        }
                    }
                }
            }

            // Expandable Connected Communication Objects (ETS Cross-Reference)
            if (showLinks && ga.linkedObjects.isNotEmpty()) {
                HorizontalDivider(color = MobileColors.BorderSubtle)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "CONNECTED COMMUNICATION OBJECTS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MobileColors.TextSecondary,
                    )
                    ga.linkedObjects.forEach { coRef ->
                        val devAddr = coRef.deviceAddress
                        val dev = devAddr?.let { project.findDevice(it) }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MobileColors.BgInput,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (devAddr != null) onJumpToDevice(devAddr) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MobileBadge(devAddr?.toString() ?: "N/A", MobileColors.AccentEmerald)
                                Text(
                                    text = dev?.name ?: "Device",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MobileColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MobileBadge("#${coRef.objectNumber ?: 0} ${coRef.name}", MobileColors.AccentSky)
                                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp), tint = MobileColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Device Card Component (Topology Line Devices)
// ---------------------------------------------------------------------------
@Composable
fun MobileDeviceCard(
    project: KnxProject,
    device: ProjectDevice,
    ping: dev.knxfieldkit.model.DevicePingResult?,
    onOpenObjects: () -> Unit,
    onPing: () -> Unit,
) {
    val room = project.findDeviceLocationPath(device.address)

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MobileColors.BgCard),
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenObjects),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                if (ping != null) {
                    MobileBadge(
                        text = if (ping.reachable) "OK ${ping.latencyMs}ms" else "UNREACHABLE",
                        color = if (ping.reachable) MobileColors.AccentEmerald else MobileColors.AccentRed,
                    )
                }
            }

            Text(
                text = "${device.manufacturerName} • ${device.productName}" +
                    (if (device.orderNumber.isNotBlank()) " (${device.orderNumber})" else ""),
                fontSize = 11.sp,
                color = MobileColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MobileBadge("${device.comObjects.size} objs", MobileColors.AccentPurple)
                    if (room != null) {
                        MobileBadge(room.substringAfterLast("›").trim(), MobileColors.AccentAmber)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = onPing,
                        label = { Text("Ping DD0", fontSize = 10.sp) },
                        modifier = Modifier.height(26.dp),
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open ComObjects",
                        tint = MobileColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Breadcrumb Bar
// ---------------------------------------------------------------------------
@Composable
private fun MobileBreadcrumbBar(
    crumbs: List<String>,
    onBack: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MobileColors.BgCard,
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MobileColors.AccentSky,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = crumbs.joinToString(" › "),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MobileColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailingAction?.invoke()
        }
    }
}
