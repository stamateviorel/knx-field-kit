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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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

enum class BuildingViewMode(val label: String, val icon: ImageVector) {
    TREE("Tree View", Icons.Default.AccountTree),
    CARDS("Folders", Icons.Default.FolderOpen),
}

sealed interface BuildingTreeNode {
    val id: String
    val depth: Int
    val name: String

    data class BuildingNode(
        override val id: String,
        override val depth: Int,
        override val name: String,
        val location: BuildingLocation,
        val totalDevices: Int,
        val isExpanded: Boolean,
        val childCount: Int,
    ) : BuildingTreeNode

    data class SpaceNode(
        override val id: String,
        override val depth: Int,
        override val name: String,
        val location: BuildingLocation,
        val totalDevices: Int,
        val directDevicesCount: Int,
        val isExpanded: Boolean,
        val childCount: Int,
        val path: List<BuildingLocation>,
    ) : BuildingTreeNode

    data class DeviceNode(
        override val id: String,
        override val depth: Int,
        override val name: String,
        val device: ProjectDevice,
        val path: List<BuildingLocation>,
    ) : BuildingTreeNode
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
    var locationPath by remember(project) { mutableStateOf<List<BuildingLocation>>(emptyList()) }
    var selectedBuildingDeviceAddress by remember(project) { mutableStateOf<IndividualAddress?>(null) }
    var selectedBuildingDeviceBreadcrumb by remember(project) { mutableStateOf<List<String>>(emptyList()) }
    var buildingViewMode by remember { mutableStateOf(BuildingViewMode.TREE) }
    var expandedTreeIds by remember(project) {
        mutableStateOf(
            project?.locations?.firstOrNull()?.let { setOf(it.name) } ?: emptySet()
        )
    }

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
                val isMain = project?.effectiveGroupRanges?.any { it.id == sel.rangeId } == true
                if (isMain) {
                    activeMainRangeId = sel.rangeId
                    activeSubRangeId = null
                } else {
                    val parentMain = project?.effectiveGroupRanges?.firstOrNull { main ->
                        main.children.any { it.id == sel.rangeId }
                    }
                    if (parentMain != null) {
                        activeMainRangeId = parentMain.id
                        activeSubRangeId = sel.rangeId
                    }
                }
            }
            is ExplorerSelection.AllGroupAddresses -> {
                pane = MobileExplorerPane.GROUPS
                activeMainRangeId = null
                activeSubRangeId = null
            }
            is ExplorerSelection.BuildingSpace -> {
                pane = MobileExplorerPane.ROOMS
                buildingViewMode = BuildingViewMode.TREE
                if (sel.path == "__ALL__" && project != null) {
                    val all = mutableSetOf<String>()
                    fun collect(loc: BuildingLocation, prefix: String) {
                        val path = if (prefix.isEmpty()) loc.name else "$prefix/${loc.name}"
                        all += path
                        loc.children.forEach { collect(it, path) }
                    }
                    project.locations.forEach { collect(it, "") }
                    expandedTreeIds = all
                } else if (sel.path.isNotBlank() && project != null) {
                    fun findByPath(loc: BuildingLocation, stack: List<BuildingLocation>): List<BuildingLocation>? {
                        val current = stack + loc
                        val currentPath = current.joinToString(" › ") { it.name }
                        if (currentPath.equals(sel.path, ignoreCase = true) || loc.name.equals(sel.name, ignoreCase = true)) return current
                        for (child in loc.children) {
                            findByPath(child, current)?.let { return it }
                        }
                        return null
                    }
                    val matched = project.locations.firstNotNullOfOrNull { findByPath(it, emptyList()) }
                    if (matched != null) {
                        locationPath = matched
                        selectedBuildingDeviceAddress = null
                        val allParents = mutableSetOf<String>()
                        var cur = ""
                        for (l in matched) {
                            cur = if (cur.isEmpty()) l.name else "$cur/${l.name}"
                            allParents += cur
                        }
                        expandedTreeIds = expandedTreeIds + allParents
                    }
                } else if (sel.path.isBlank()) {
                    locationPath = emptyList()
                    selectedBuildingDeviceAddress = null
                }
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. Google Material 3 Perspective Tabs (Topology, Groups, Buildings)
        PrimaryTabRow(
            selectedTabIndex = pane.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
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
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
            placeholder = "Search addresses (1/1/1), names, DPTs...",
        )

        // Empty Project State
        if (project == null) {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "No KNX Project Loaded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Open an ETS4, ETS5, or ETS6 project (.knxproj) or keyring (.knxkeys) from your phone storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Button(
                        onClick = onOpenProjectFile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open .knxproj / .knxkeys File", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            return@Column
        }

        val q = searchQuery.trim().lowercase()

        // 3. Instant Unified Search Mode
        if (q.isNotEmpty()) {
            val matchingGas = remember(project, q) {
                project.groupAddresses.values
                    .filter { ga ->
                        ga.address.toString().contains(q) ||
                            ga.name.lowercase().contains(q) ||
                            ga.description.lowercase().contains(q) ||
                            (ga.dpt?.lowercase()?.contains(q) == true)
                    }
                    .sortedBy { it.address }
            }

            val matchingDevs = remember(project, q) {
                project.allDevices.values
                    .filter { dev ->
                        dev.address.toString().contains(q) ||
                            dev.name.lowercase().contains(q) ||
                            dev.productName.lowercase().contains(q) ||
                            dev.orderNumber.lowercase().contains(q)
                    }
                    .sortedBy { it.address }
            }

            val matchingLines = remember(project, q) {
                project.allLines.filter { it.id.contains(q) || it.name.lowercase().contains(q) }
            }

            val matchingSpaces = remember(project, q) {
                val out = mutableListOf<Pair<List<BuildingLocation>, BuildingLocation>>()
                fun search(loc: BuildingLocation, stack: List<BuildingLocation>) {
                    if (loc.name.lowercase().contains(q) || loc.type.lowercase().contains(q)) {
                        out += (stack to loc)
                    }
                    loc.children.forEach { search(it, stack + loc) }
                }
                project.locations.forEach { search(it, emptyList()) }
                out
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "search-summary") {
                    Text(
                        text = "SEARCH RESULTS: ${matchingGas.size} GAs • ${matchingDevs.size} Devs • ${matchingLines.size} Lines • ${matchingSpaces.size} Spaces",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (matchingSpaces.isNotEmpty()) {
                    item(key = "search-spaces-header") {
                        Text(
                            text = "BUILDINGS & ROOMS (${matchingSpaces.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(matchingSpaces, key = { (stack, loc) -> (stack.map { it.name } + loc.name).joinToString("/") }) { (stack, loc) ->
                        MobileBuildingSpaceCard(
                            space = loc,
                            subtitleOverride = if (stack.isNotEmpty()) stack.joinToString(" › ") { it.name } else null,
                            onClick = {
                                pane = MobileExplorerPane.ROOMS
                                buildingViewMode = BuildingViewMode.TREE
                                val allParents = mutableSetOf<String>()
                                var cur = ""
                                for (l in stack + loc) {
                                    cur = if (cur.isEmpty()) l.name else "$cur/${l.name}"
                                    allParents += cur
                                }
                                expandedTreeIds = expandedTreeIds + allParents
                                locationPath = stack + loc
                                selectedBuildingDeviceAddress = null
                                searchQuery = ""
                            },
                        )
                    }
                }

                if (matchingGas.isNotEmpty()) {
                    item(key = "search-gas-header") {
                        Text(
                            text = "GROUP ADDRESSES (${matchingGas.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(matchingGas, key = { "search-ga-${it.address.raw}" }) { ga ->
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

                if (matchingDevs.isNotEmpty()) {
                    item(key = "search-devs-header") {
                        Text(
                            text = "DEVICES (${matchingDevs.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(matchingDevs, key = { "search-dev-${it.address.raw}" }) { dev ->
                        MobileDeviceCard(
                            project = project,
                            device = dev,
                            ping = state.deviceStatuses[dev.address],
                            onOpenObjects = {
                                pane = MobileExplorerPane.TOPOLOGY
                                controller.openDeviceObjectsInExplorer(dev.address)
                            },
                            onPing = { controller.pingIndividualDevice(dev.address.toString()) },
                            onJumpToLocation = { stack ->
                                pane = MobileExplorerPane.ROOMS
                                locationPath = stack
                                selectedBuildingDeviceAddress = null
                                searchQuery = ""
                            },
                        )
                    }
                }

                if (matchingLines.isNotEmpty()) {
                    item(key = "search-lines-header") {
                        Text(
                            text = "LINES (${matchingLines.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(matchingLines, key = { "search-line-${it.id}" }) { line ->
                        MobileLineCard(
                            line = line,
                            state = state,
                            controller = controller,
                            onClick = {
                                pane = MobileExplorerPane.TOPOLOGY
                                selectedLineId = line.id
                                selectedAreaNumber = line.area
                            },
                        )
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
                        onJumpToLocation = { stack ->
                            pane = MobileExplorerPane.ROOMS
                            locationPath = stack
                            selectedBuildingDeviceAddress = null
                            controller.selectExplorerNode(ExplorerSelection.TopologyRoot)
                        },
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
                                FilledTonalButton(
                                    onClick = {
                                        controller.runProjectLineAudit(activeLine.area, activeLine.line)
                                        controller.selectTab(AppTab.DIAGNOSTICS)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Audit Line", style = MaterialTheme.typography.labelMedium)
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
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${activeLine.mediumType} bus",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
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
                                    onJumpToLocation = { stack ->
                                        pane = MobileExplorerPane.ROOMS
                                        locationPath = stack
                                        selectedBuildingDeviceAddress = null
                                        controller.selectExplorerNode(ExplorerSelection.TopologyRoot)
                                    },
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
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${activeArea.lines.sumOf { it.devices.size }} devices total",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
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
                    // Level 0: Topology Areas Root
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
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${project.allDevices.size} devs • ${project.allLines.size} lines",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
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

                if (subRange != null) {
                    // Level 3: Sub Groups (Group Addresses in Middle Group)
                    MobileBreadcrumbBar(
                        crumbs = listOf(
                            mainRange?.name ?: "Groups",
                            "${subRange.prefixLabel} ${subRange.name}",
                        ),
                        onBack = { activeSubRangeId = null },
                        trailingAction = {
                            val addrs = subRange.collectAllAddresses()
                            FilledTonalButton(
                                onClick = { addrs.forEach { controller.readGroupAddress(it.toString()) } },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Poll Group", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )

                    val gas = subRange.collectAllAddresses().mapNotNull { project.findGroupAddress(it) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "subrange-header") {
                            Text(
                                text = "GROUP ADDRESSES IN ${subRange.name.uppercase()} (${gas.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

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
                            FilledTonalButton(
                                onClick = { addrs.take(40).forEach { controller.readGroupAddress(it.toString()) } },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Poll All in Main", style = MaterialTheme.typography.labelSmall)
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
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AssistChip(
                                    onClick = { controller.pollAllProjectGroupAddresses() },
                                    label = { Text("Poll All GAs", fontSize = 10.sp) },
                                    leadingIcon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(13.dp)) },
                                    modifier = Modifier.height(28.dp),
                                )
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
                val activeBuildingDev = selectedBuildingDeviceAddress?.let { project.findDevice(it) }

                if (activeBuildingDev != null) {
                    // Level 3/4: Device Group Objects Detail inside Building hierarchy
                    val deviceCrumbs = listOf("Buildings") +
                        (if (selectedBuildingDeviceBreadcrumb.isNotEmpty()) selectedBuildingDeviceBreadcrumb else locationPath.map { it.name }) +
                        listOf("${activeBuildingDev.address} ${activeBuildingDev.name}")

                    MobileDeviceObjectsDetail(
                        project = project,
                        device = activeBuildingDev,
                        state = state,
                        controller = controller,
                        crumbs = deviceCrumbs,
                        onBack = { selectedBuildingDeviceAddress = null },
                        onInspectGa = { ga -> onInspectGroupSheet(ga) },
                        onJumpToLocation = { stack ->
                            locationPath = stack
                            selectedBuildingDeviceAddress = null
                        },
                    )
                } else if (buildingViewMode == BuildingViewMode.TREE) {
                    // ETS Expandable Tree Hierarchy View
                    val visibleTreeNodes = remember(project, expandedTreeIds) {
                        buildVisibleBuildingTree(project.locations, project, expandedTreeIds)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(key = "tree-toolbar") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "ETS BUILDING TREE (${project.locations.size} Blds • ${project.locations.sumOf { it.collectAllDeviceAddresses().size }} Devs)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        AssistChip(
                                            onClick = {
                                                val all = mutableSetOf<String>()
                                                fun collect(loc: BuildingLocation, prefix: String) {
                                                    val path = if (prefix.isEmpty()) loc.name else "$prefix/${loc.name}"
                                                    all += path
                                                    loc.children.forEach { collect(it, path) }
                                                }
                                                project.locations.forEach { collect(it, "") }
                                                expandedTreeIds = all
                                            },
                                            label = { Text("Expand All", fontSize = 9.5.sp) },
                                            leadingIcon = { Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(12.dp)) },
                                            modifier = Modifier.height(26.dp),
                                        )
                                        AssistChip(
                                            onClick = { expandedTreeIds = emptySet() },
                                            label = { Text("Collapse", fontSize = 9.5.sp) },
                                            leadingIcon = { Icon(Icons.Default.UnfoldLess, null, modifier = Modifier.size(12.dp)) },
                                            modifier = Modifier.height(26.dp),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // View Mode Toggle
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(modifier = Modifier.padding(2.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (buildingViewMode == BuildingViewMode.TREE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                modifier = Modifier.clickable { buildingViewMode = BuildingViewMode.TREE },
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Default.AccountTree,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = if (buildingViewMode == BuildingViewMode.TREE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        "Tree View",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (buildingViewMode == BuildingViewMode.TREE) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (buildingViewMode == BuildingViewMode.TREE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (buildingViewMode == BuildingViewMode.CARDS) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                modifier = Modifier.clickable { buildingViewMode = BuildingViewMode.CARDS },
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Default.FolderOpen,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = if (buildingViewMode == BuildingViewMode.CARDS) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        "Folders",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (buildingViewMode == BuildingViewMode.CARDS) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (buildingViewMode == BuildingViewMode.CARDS) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "${visibleTreeNodes.size} visible items",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        if (visibleTreeNodes.isEmpty()) {
                            item(key = "empty-tree") {
                                OutlinedCard(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Default.Apartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                        Text("No Buildings Defined", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("This project does not contain ETS building locations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            items(visibleTreeNodes, key = { it.id }) { treeNode ->
                                when (treeNode) {
                                    is BuildingTreeNode.BuildingNode -> {
                                        BuildingTreeBuildingItem(
                                            node = treeNode,
                                            onToggle = {
                                                expandedTreeIds = if (treeNode.isExpanded) {
                                                    expandedTreeIds - treeNode.id
                                                } else {
                                                    expandedTreeIds + treeNode.id
                                                }
                                            },
                                        )
                                    }
                                    is BuildingTreeNode.SpaceNode -> {
                                        BuildingTreeSpaceItem(
                                            node = treeNode,
                                            project = project,
                                            controller = controller,
                                            onToggle = {
                                                expandedTreeIds = if (treeNode.isExpanded) {
                                                    expandedTreeIds - treeNode.id
                                                } else {
                                                    expandedTreeIds + treeNode.id
                                                }
                                            },
                                        )
                                    }
                                    is BuildingTreeNode.DeviceNode -> {
                                        BuildingTreeDeviceItem(
                                            node = treeNode,
                                            state = state,
                                            controller = controller,
                                            onOpenObjects = {
                                                selectedBuildingDeviceAddress = treeNode.device.address
                                                selectedBuildingDeviceBreadcrumb = treeNode.path.map { it.name }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (locationPath.isNotEmpty()) {
                    // Level 1 / 2: Inside a Building, Floor, Room, or Space (Folders View)
                    val currentLoc = locationPath.last()
                    val totalBranchDevices = remember(currentLoc) { currentLoc.collectAllDeviceAddresses() }
                    val directDevices = remember(currentLoc, project) {
                        currentLoc.deviceAddresses.mapNotNull { project.findDevice(it) }
                    }

                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(modifier = Modifier.padding(2.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (buildingViewMode == BuildingViewMode.TREE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        modifier = Modifier.clickable { buildingViewMode = BuildingViewMode.TREE },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Text("Tree View", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (buildingViewMode == BuildingViewMode.CARDS) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        modifier = Modifier.clickable { buildingViewMode = BuildingViewMode.CARDS },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Text("Folders", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        MobileBreadcrumbBar(
                            crumbs = listOf("Buildings") + locationPath.map { it.name },
                            onBack = { locationPath = locationPath.dropLast(1) },
                            trailingAction = {
                                if (totalBranchDevices.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = {
                                            totalBranchDevices.take(30).forEach { addr ->
                                                controller.pingIndividualDevice(addr.toString())
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ping All (${totalBranchDevices.size})", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Header summary
                            item(key = "space-summary-${locationPath.size}-${currentLoc.name}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = currentLoc.type.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                        Text(
                                            text = currentLoc.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Text(
                                        text = "${currentLoc.children.size} spaces • ${totalBranchDevices.size} devs",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            // Child Spaces (Floors, Rooms, Sub-spaces)
                            if (currentLoc.children.isNotEmpty()) {
                                item(key = "subspaces-header") {
                                    val label = when {
                                        currentLoc.type.equals("Building", ignoreCase = true) -> "FLOORS & SPACES IN ${currentLoc.name.uppercase()}"
                                        currentLoc.type.equals("Floor", ignoreCase = true) -> "ROOMS ON ${currentLoc.name.uppercase()}"
                                        else -> "SUB-SPACES IN ${currentLoc.name.uppercase()}"
                                    }
                                    Text(
                                        text = "$label (${currentLoc.children.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }

                                items(currentLoc.children, key = { "subspace-${it.name}-${it.type}" }) { child ->
                                    MobileBuildingSpaceCard(
                                        space = child,
                                        onClick = { locationPath = locationPath + child },
                                    )
                                }
                            }

                            // Direct Devices in this Space
                            if (directDevices.isNotEmpty()) {
                                item(key = "space-devs-header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "DEVICES IN ${currentLoc.name.uppercase()} (${directDevices.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AssistChip(
                                                onClick = {
                                                    directDevices.forEach { dev ->
                                                        controller.pingIndividualDevice(dev.address.toString())
                                                    }
                                                },
                                                label = { Text("Ping All", fontSize = 10.sp) },
                                                leadingIcon = { Icon(Icons.Default.Bolt, null, modifier = Modifier.size(12.dp)) },
                                                modifier = Modifier.height(26.dp),
                                            )
                                            AssistChip(
                                                onClick = {
                                                    directDevices.flatMap { it.groupAddresses }.distinct().take(40).forEach { ga ->
                                                        controller.readGroupAddress(ga.toString())
                                                    }
                                                },
                                                label = { Text("Read GAs", fontSize = 10.sp) },
                                                leadingIcon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(12.dp)) },
                                                modifier = Modifier.height(26.dp),
                                            )
                                        }
                                    }
                                }

                                items(directDevices, key = { "space-dev-${it.address.raw}" }) { dev ->
                                    MobileDeviceCard(
                                        project = project,
                                        device = dev,
                                        ping = state.deviceStatuses[dev.address],
                                        onOpenObjects = {
                                            selectedBuildingDeviceAddress = dev.address
                                            selectedBuildingDeviceBreadcrumb = locationPath.map { it.name }
                                        },
                                        onPing = { controller.pingIndividualDevice(dev.address.toString()) },
                                        onJumpToLocation = { stack ->
                                            locationPath = stack
                                            selectedBuildingDeviceAddress = null
                                        },
                                    )
                                }
                            }

                            if (currentLoc.children.isEmpty() && directDevices.isEmpty()) {
                                item(key = "space-empty") {
                                    OutlinedCard(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                            Text("Empty Space", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                "No child spaces or devices are assigned to ${currentLoc.name} in ETS.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Level 0: Buildings Root (Folders View)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "buildings-header") {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "ETS BUILDINGS (${project.locations.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${project.locations.sumOf { it.collectAllDeviceAddresses().size }} devices total",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(modifier = Modifier.padding(2.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (buildingViewMode == BuildingViewMode.TREE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier.clickable { buildingViewMode = BuildingViewMode.TREE },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Text("Tree View", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (buildingViewMode == BuildingViewMode.CARDS) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier.clickable { buildingViewMode = BuildingViewMode.CARDS },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Text("Folders", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (project.locations.isNotEmpty()) {
                            items(project.locations, key = { "bld-${it.name}-${it.type}" }) { building ->
                                MobileBuildingSpaceCard(
                                    space = building,
                                    onClick = { locationPath = listOf(building) },
                                )
                            }
                        } else {
                            item(key = "no-buildings-card") {
                                OutlinedCard(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Apartment,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp),
                                        )
                                        Text(
                                            text = "No Building Structure in ETS",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "This project does not define Building or Room spaces in ETS. You can still explore and control all devices and lines in Topology.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        Button(
                                            onClick = { pane = MobileExplorerPane.TOPOLOGY },
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Go to Topology", style = MaterialTheme.typography.labelLarge)
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
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "Area ${area.area}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = area.name.ifBlank { "Area ${area.area}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${area.lines.size} TP/IP Lines • ${area.lines.sumOf { it.devices.size }} Devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = line.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name.ifBlank { "Line ${line.id}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${line.mediumType} • ${line.devices.size} devices" +
                        (if (onlineCount > 0) " • $onlineCount OK" else "") +
                        (if (unreachCount > 0) " • $unreachCount ERR" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unreachCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalButton(
                onClick = {
                    controller.runProjectLineAudit(line.area, line.line)
                    controller.selectTab(AppTab.DIAGNOSTICS)
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text("Audit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    crumbs: List<String>? = null,
    onJumpToLocation: ((List<BuildingLocation>) -> Unit)? = null,
) {
    val ping = state.deviceStatuses[device.address]
    val room = project.findDeviceLocationPath(device.address) ?: "Unassigned Room"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MobileBreadcrumbBar(
            crumbs = crumbs ?: listOf(
                "Line ${device.address.area}.${device.address.line}",
                "${device.address} ${device.name}",
            ),
            onBack = onBack,
        )

        // Device Summary Card
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = device.address.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${device.manufacturerName} • ${device.productName} (${device.orderNumber})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(
                            text = "Mask ${device.maskVersion ?: "N/A"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = if (onJumpToLocation != null) {
                            Modifier.clickable {
                                val stack = project.findLocationStackForDevice(device.address)
                                if (!stack.isNullOrEmpty()) {
                                    onJumpToLocation(stack)
                                }
                            }
                        } else Modifier,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Default.MeetingRoom,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = room,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AssistChip(
                        onClick = { controller.pingIndividualDevice(device.address.toString()) },
                        label = { Text("Ping DD0", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Bolt, null, modifier = Modifier.size(13.dp)) },
                        modifier = Modifier.height(30.dp),
                    )
                    AssistChip(
                        onClick = {
                            controller.inspectDeviceDiagnostics(device.address.toString())
                            controller.selectTab(AppTab.DIAGNOSTICS)
                        },
                        label = { Text("BCU Info", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(13.dp)) },
                        modifier = Modifier.height(30.dp),
                    )
                    AssistChip(
                        onClick = { controller.restartRemoteDevice(device.address.toString()) },
                        label = { Text("Restart", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(13.dp)) },
                        modifier = Modifier.height(30.dp),
                    )
                }
            }
        }

        Text(
            text = "COMMUNICATION OBJECTS (${device.comObjects.size}) — FLAGS & LIVE TEST CONTROLS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "#${obj.number}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                            Text(
                                text = obj.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = obj.sizeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        if (obj.functionText.isNotBlank()) {
                            Text(
                                text = obj.functionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "Live: $liveVal",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }

                        // Linked Group Addresses with Click-to-Inspect
                        if (obj.linkedGroupAddresses.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Linked GA:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                obj.linkedGroupAddresses.forEach { gaAddr ->
                                    val gaInfo = project.findGroupAddress(gaAddr)
                                    SuggestionChip(
                                        onClick = {
                                            if (gaInfo != null) onInspectGa(gaInfo)
                                        },
                                        label = { Text(gaAddr.toString(), style = MaterialTheme.typography.labelSmall) },
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
                                        FilledTonalButton(
                                            onClick = { controller.writeGroupSwitch(gaText, false) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("OFF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { controller.writeGroupSwitch(gaText, true) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("ON", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
                                        FilledTonalButton(
                                            onClick = { controller.writeGroupPercent(gaText, 0.0) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("0%", style = MaterialTheme.typography.labelSmall)
                                        }
                                        FilledTonalButton(
                                            onClick = { controller.writeGroupPercent(gaText, 50.0) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("50%", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Button(
                                            onClick = { controller.writeGroupPercent(gaText, 100.0) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("100%", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    else -> Unit
                                }

                                OutlinedButton(
                                    onClick = { controller.readGroupAddress(gaText) },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Read?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = node.prefixLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Text(
                text = node.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "${node.totalCount} GA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = gaText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = ga.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (liveEntry != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = liveEntry.valueText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Subtitle Row: DPT, Description, Links count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "DPT ${ga.dpt ?: "1.x"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }

                    SuggestionChip(
                        onClick = { showLinks = !showLinks },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${ga.linkedObjects.size} Linked Objs", style = MaterialTheme.typography.labelSmall)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // In-line Testing Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (mainDpt) {
                    MainDpt.DPT_1_BOOL -> {
                        FilledTonalButton(
                            onClick = { controller.writeGroupSwitch(gaText, false) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text("OFF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { controller.writeGroupSwitch(gaText, true) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text("ON", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { controller.readGroupAddress(gaText) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Read?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { controller.startWalkTestBlink(gaText, intervalMs = 1200L) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Blink", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
                        FilledTonalButton(
                            onClick = { controller.writeGroupPercent(gaText, 0.0) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("0%", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { controller.writeGroupPercent(gaText, 50.0) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("50%", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { controller.writeGroupPercent(gaText, 100.0) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("100%", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { controller.readGroupAddress(gaText) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Read?", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    MainDpt.DPT_3_DIM_STEP -> {
                        FilledTonalButton(
                            onClick = { controller.writeGroupDimStep(gaText, increase = false) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("−", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { controller.writeGroupDimStep(gaText, increase = false, stepCode = 0) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Stop", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { controller.writeGroupDimStep(gaText, increase = true) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("+", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { controller.readGroupAddress(gaText) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Read?", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    MainDpt.DPT_17_SCENE -> {
                        FilledTonalButton(
                            onClick = { controller.writeGroupScene(gaText, 1) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("S1", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { controller.writeGroupScene(gaText, 2) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("S2", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { controller.writeGroupScene(gaText, 3) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("S3", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = { controller.readGroupAddress(gaText) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Read?", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = {
                                controller.selectGroupAddressForSender(gaText)
                                controller.selectTab(AppTab.SENDER)
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Send…", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Expandable Connected Communication Objects (Linked Physical Devices)
            if (showLinks && ga.linkedObjects.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "CONNECTED COMMUNICATION OBJECTS:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ga.linkedObjects.forEach { coRef ->
                        val devAddr = coRef.deviceAddress
                        val dev = devAddr?.let { project.findDevice(it) }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (devAddr != null) onJumpToDevice(devAddr) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = devAddr?.toString() ?: "N/A",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                                Text(
                                    text = dev?.name ?: "Device",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = "#${coRef.objectNumber ?: 0} ${coRef.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
// Device Card Component (Topology Line Devices)
// ---------------------------------------------------------------------------
@Composable
fun MobileDeviceCard(
    project: KnxProject,
    device: ProjectDevice,
    ping: dev.knxfieldkit.model.DevicePingResult?,
    onOpenObjects: () -> Unit,
    onPing: () -> Unit,
    onJumpToLocation: ((List<BuildingLocation>) -> Unit)? = null,
) {
    val room = project.findDeviceLocationPath(device.address)

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = device.address.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ping != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (ping.reachable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = if (ping.reachable) "OK ${ping.latencyMs}ms" else "UNREACHABLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (ping.reachable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Text(
                text = "${device.manufacturerName} • ${device.productName}" +
                    (if (device.orderNumber.isNotBlank()) " (${device.orderNumber})" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "${device.comObjects.size} objs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    if (!room.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = if (onJumpToLocation != null) {
                                Modifier.clickable {
                                    val stack = project.findLocationStackForDevice(device.address)
                                    if (!stack.isNullOrEmpty()) {
                                        onJumpToLocation(stack)
                                    }
                                }
                            } else Modifier,
                        ) {
                            Text(
                                text = room.substringAfterLast("›").trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onPing,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp),
                    ) {
                        Text("Ping DD0", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ETS Building Tree Helpers & Items
// ---------------------------------------------------------------------------
private fun buildVisibleBuildingTree(
    locations: List<BuildingLocation>,
    project: KnxProject,
    expandedIds: Set<String>,
): List<BuildingTreeNode> {
    val result = mutableListOf<BuildingTreeNode>()

    fun traverseSpace(
        loc: BuildingLocation,
        depth: Int,
        parentPath: List<BuildingLocation>,
    ) {
        val currentPath = parentPath + loc
        val nodeId = currentPath.joinToString("/") { it.name }
        val isExpanded = nodeId in expandedIds
        val isBuilding = depth == 0 && (loc.type.equals("Building", ignoreCase = true) || loc.type.equals("BuildingPart", ignoreCase = true))

        if (isBuilding) {
            result += BuildingTreeNode.BuildingNode(
                id = nodeId,
                depth = depth,
                name = loc.name.ifBlank { "Building" },
                location = loc,
                totalDevices = loc.collectAllDeviceAddresses().size,
                isExpanded = isExpanded,
                childCount = loc.children.size + loc.deviceAddresses.size,
            )
        } else {
            result += BuildingTreeNode.SpaceNode(
                id = nodeId,
                depth = depth,
                name = loc.name.ifBlank { "Space" },
                location = loc,
                totalDevices = loc.collectAllDeviceAddresses().size,
                directDevicesCount = loc.deviceAddresses.size,
                isExpanded = isExpanded,
                childCount = loc.children.size + loc.deviceAddresses.size,
                path = currentPath,
            )
        }

        if (isExpanded) {
            // Traverse child spaces first
            for (child in loc.children) {
                traverseSpace(child, depth + 1, currentPath)
            }
            // Direct devices assigned to this space
            for (devAddr in loc.deviceAddresses) {
                val dev = project.findDevice(devAddr)
                if (dev != null) {
                    val devNodeId = "$nodeId/dev:${dev.address.raw}"
                    result += BuildingTreeNode.DeviceNode(
                        id = devNodeId,
                        depth = depth + 1,
                        name = dev.name,
                        device = dev,
                        path = currentPath,
                    )
                }
            }
        }
    }

    for (root in locations) {
        traverseSpace(root, depth = 0, parentPath = emptyList())
    }

    return result
}

@Composable
private fun BuildingTreeBuildingItem(
    node: BuildingTreeNode.BuildingNode,
    onToggle: () -> Unit,
) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (node.isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Apartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "BUILDING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                        )
                    }
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${node.location.children.size} spaces • ${node.totalDevices} devices total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = "${node.totalDevices}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Icon(
                imageVector = if (node.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BuildingTreeSpaceItem(
    node: BuildingTreeNode.SpaceNode,
    project: KnxProject,
    controller: AppController,
    onToggle: () -> Unit,
) {
    val loc = node.location
    val isPart = loc.type.equals("BuildingPart", ignoreCase = true)
    val isFloor = loc.type.equals("Floor", ignoreCase = true)
    val isRoom = loc.type.equals("Room", ignoreCase = true)
    val isBoard = loc.type.contains("Distribution", ignoreCase = true) || loc.name.contains("Cabinet", ignoreCase = true) || loc.name.contains("Board", ignoreCase = true)

    val icon = when {
        isPart -> Icons.Default.Domain
        isFloor -> Icons.Default.Layers
        isRoom -> Icons.Default.MeetingRoom
        isBoard -> Icons.Default.ElectricalServices
        loc.type.contains("Corridor", ignoreCase = true) || loc.name.contains("Corridor", ignoreCase = true) || loc.name.contains("Hall", ignoreCase = true) ->
            Icons.AutoMirrored.Filled.DirectionsWalk
        loc.type.contains("Stair", ignoreCase = true) || loc.name.contains("Stair", ignoreCase = true) ->
            Icons.Default.Stairs
        loc.type.contains("Outdoor", ignoreCase = true) || loc.name.contains("Garden", ignoreCase = true) || loc.name.contains("Pool", ignoreCase = true) ->
            Icons.Default.Park
        else -> if (node.childCount > 0) Icons.Default.Folder else Icons.Default.MeetingRoom
    }

    val indent = (node.depth * 14).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Subtle branch marker
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(4.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (node.isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = loc.type.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 8.5.sp,
                        )
                    }

                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    val devCount = if (node.directDevicesCount > 0) node.directDevicesCount else node.totalDevices
                    Text(
                        text = "$devCount dev",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (node.childCount > 0) {
                        Icon(
                            imageVector = if (node.isExpanded) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // If expanded and has direct devices, show quick test chips
                if (node.isExpanded && node.directDevicesCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(
                            onClick = {
                                loc.deviceAddresses.forEach { addr ->
                                    controller.pingIndividualDevice(addr.toString())
                                }
                            },
                            label = { Text("Ping All (${node.directDevicesCount})", fontSize = 9.sp) },
                            leadingIcon = { Icon(Icons.Default.Bolt, null, modifier = Modifier.size(11.dp)) },
                            modifier = Modifier.height(24.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        AssistChip(
                            onClick = {
                                loc.deviceAddresses.mapNotNull { project.findDevice(it) }
                                    .flatMap { it.groupAddresses }
                                    .distinct()
                                    .take(30)
                                    .forEach { ga ->
                                        controller.readGroupAddress(ga.toString())
                                    }
                            },
                            label = { Text("Read GAs", fontSize = 9.sp) },
                            leadingIcon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(11.dp)) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildingTreeDeviceItem(
    node: BuildingTreeNode.DeviceNode,
    state: AppUiState,
    controller: AppController,
    onOpenObjects: () -> Unit,
) {
    val dev = node.device
    val ping = state.deviceStatuses[dev.address]
    val indent = (node.depth * 14 + 6).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Subtle branch marker
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(4.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenObjects),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = dev.address.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dev.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${dev.productName} • ${dev.comObjects.size} objs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (ping != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (ping.reachable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = if (ping.reachable) "OK" else "FAULT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (ping.reachable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }

                val isLocatingThis = state.locatingDeviceAddress == dev.address
                FilledTonalButton(
                    onClick = { controller.locateDevice(dev.address) },
                    enabled = state.locatingDeviceAddress == null || isLocatingThis,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isLocatingThis) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isLocatingThis) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(if (isLocatingThis) "Blink..." else "Locate", style = MaterialTheme.typography.labelSmall, fontSize = 8.5.sp)
                }

                FilledTonalButton(
                    onClick = { controller.pingIndividualDevice(dev.address.toString()) },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp),
                ) {
                    Text("Ping", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Objects",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ETS Building Space Card Component
// ---------------------------------------------------------------------------
@Composable
fun MobileBuildingSpaceCard(
    space: BuildingLocation,
    subtitleOverride: String? = null,
    onClick: () -> Unit,
) {
    val totalDevs = remember(space) { space.collectAllDeviceAddresses().size }
    val directDevs = space.deviceAddresses.size
    val subSpaces = space.children.size

    val icon = when {
        space.type.equals("Building", ignoreCase = true) || space.type.equals("BuildingPart", ignoreCase = true) ->
            Icons.Default.Apartment
        space.type.equals("Floor", ignoreCase = true) ->
            Icons.Default.Layers
        space.type.equals("Room", ignoreCase = true) ->
            Icons.Default.MeetingRoom
        space.type.contains("Distribution", ignoreCase = true) || space.name.contains("Distribution", ignoreCase = true) || space.name.contains("Cabinet", ignoreCase = true) || space.name.contains("Board", ignoreCase = true) || space.name.contains("Panel", ignoreCase = true) ->
            Icons.Default.ElectricalServices
        space.type.contains("Corridor", ignoreCase = true) || space.name.contains("Corridor", ignoreCase = true) || space.name.contains("Hall", ignoreCase = true) ->
            Icons.Default.DirectionsWalk
        space.type.contains("Stair", ignoreCase = true) || space.name.contains("Stair", ignoreCase = true) ->
            Icons.Default.Stairs
        space.type.contains("Outdoor", ignoreCase = true) || space.name.contains("Garden", ignoreCase = true) || space.name.contains("Terrace", ignoreCase = true) || space.name.contains("Pool", ignoreCase = true) ->
            Icons.Default.Park
        else ->
            if (subSpaces > 0) Icons.Default.Folder else Icons.Default.MeetingRoom
    }

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = space.type.ifBlank { "Space" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = space.name.ifBlank { "Unnamed Space" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                val subtitle = subtitleOverride ?: when {
                    subSpaces > 0 && directDevs > 0 ->
                        "$subSpaces sub-spaces • $directDevs direct / $totalDevs total devs"
                    subSpaces > 0 ->
                        "$subSpaces sub-spaces • $totalDevs devs total"
                    else ->
                        "$totalDevs devices assigned"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "$totalDevs",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Standard Material 3 Breadcrumb Bar
// ---------------------------------------------------------------------------
@Composable
private fun MobileBreadcrumbBar(
    crumbs: List<String>,
    onBack: () -> Unit,
    trailingAction: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = crumbs.joinToString(" › "),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}
