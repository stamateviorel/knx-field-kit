package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
    ROOMS("Buildings", Icons.Default.Apartment),
    TOPOLOGY("Topology", Icons.Default.AccountTree),
    GROUPS("Group Addresses", Icons.Default.Folder),
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
        val totalFunctions: Int,
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
        val directFunctionsCount: Int,
        val isExpanded: Boolean,
        val childCount: Int,
        val path: List<BuildingLocation>,
    ) : BuildingTreeNode

    data class FunctionNode(
        override val id: String,
        override val depth: Int,
        override val name: String,
        val function: BuildingFunction,
        val location: BuildingLocation,
        val isExpanded: Boolean,
        val gaCount: Int,
        val path: List<BuildingLocation>,
    ) : BuildingTreeNode

    data class FunctionGaLeafNode(
        override val id: String,
        override val depth: Int,
        override val name: String,
        val function: BuildingFunction,
        val ga: GroupAddressInfo,
        val liveEntry: GroupValueEntry?,
    ) : BuildingTreeNode

    data class DeviceNode(
        override val id: String,
        override val depth: Int,
        override val name: String,
        val device: ProjectDevice,
        val path: List<BuildingLocation>,
        val isOnline: Boolean? = null,
        val isProgMode: Boolean = false,
    ) : BuildingTreeNode
}

enum class TopologyViewMode(val label: String, val icon: ImageVector) {
    TREE("Tree View", Icons.Default.AccountTree),
    FOLDERS("Folders", Icons.Default.FolderOpen),
}

sealed interface TopologyTreeNode {
    val id: String
    val depth: Int

    data class AreaNode(
        override val id: String,
        override val depth: Int,
        val area: TopologyArea,
        val isExpanded: Boolean,
        val lineCount: Int,
        val totalDevices: Int,
    ) : TopologyTreeNode

    data class LineNode(
        override val id: String,
        override val depth: Int,
        val area: TopologyArea,
        val line: TopologyLine,
        val isExpanded: Boolean,
        val deviceCount: Int,
        val couplerAddress: IndividualAddress,
        val couplerReachable: Boolean?,
    ) : TopologyTreeNode

    data class DeviceNode(
        override val id: String,
        override val depth: Int,
        val device: ProjectDevice,
        val line: TopologyLine,
        val isOnline: Boolean?,
        val isProgMode: Boolean,
    ) : TopologyTreeNode
}

enum class GroupViewMode(val label: String, val icon: ImageVector) {
    TREE("Tree View", Icons.Default.AccountTree),
    FOLDERS("Folders", Icons.Default.FolderOpen),
    FLAT("Flat List", Icons.Default.ViewList),
}

sealed interface GroupTreeNode {
    val id: String
    val depth: Int

    data class MainGroupNode(
        override val id: String,
        override val depth: Int,
        val range: GroupRangeNode,
        val isExpanded: Boolean,
        val childCount: Int,
        val totalGas: Int,
    ) : GroupTreeNode

    data class MiddleGroupNode(
        override val id: String,
        override val depth: Int,
        val mainRange: GroupRangeNode,
        val subRange: GroupRangeNode,
        val isExpanded: Boolean,
        val childCount: Int,
        val totalGas: Int,
    ) : GroupTreeNode

    data class GroupLeafNode(
        override val id: String,
        override val depth: Int,
        val ga: GroupAddressInfo,
        val liveEntry: GroupValueEntry?,
    ) : GroupTreeNode
}

@Composable
fun MobileExplorerScreen(
    state: AppUiState,
    controller: AppController,
    onInspectGroupSheet: (GroupAddressInfo) -> Unit,
    onOpenProjectFile: () -> Unit = {},
    activePane: MobileExplorerPane = MobileExplorerPane.ROOMS,
    onPaneChange: (MobileExplorerPane) -> Unit = {},
) {
    val project = state.project
    var pane by remember(activePane) { mutableStateOf(activePane) }
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
            project?.locations?.firstOrNull()?.let { bld ->
                val set = mutableSetOf(bld.name)
                bld.children.firstOrNull()?.let { part ->
                    set += "${bld.name}/${part.name}"
                    part.children.firstOrNull()?.let { floor ->
                        set += "${bld.name}/${part.name}/${floor.name}"
                    }
                }
                set
            } ?: emptySet()
        )
    }
    var groupViewMode by remember { mutableStateOf(GroupViewMode.TREE) }
    var expandedGroupIds by remember(project) {
        mutableStateOf(
            project?.effectiveGroupRanges?.firstOrNull()?.let { setOf(it.id) } ?: emptySet()
        )
    }
    var topologyViewMode by remember { mutableStateOf(TopologyViewMode.TREE) }
    var expandedTopologyIds by remember(project) {
        mutableStateOf(
            project?.areas?.firstOrNull()?.let { area ->
                setOf("area-${area.area}") + (area.lines.firstOrNull()?.let { setOf("line-${it.id}") } ?: emptySet())
            } ?: emptySet()
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
        // 1. Sleek ETS Unified Command Bar (Search + Quick Action Toggles)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MobileSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = when (pane) {
                    MobileExplorerPane.ROOMS -> "Search rooms, functions, circuits..."
                    MobileExplorerPane.TOPOLOGY -> "Search 1.1.1, name, line..."
                    MobileExplorerPane.GROUPS -> "Search 0/1/1, DPTs, names..."
                },
                modifier = Modifier.weight(1f),
            )

            // Quick Actions: Expand/Collapse All + View Mode
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val isAnyExpanded = when (pane) {
                        MobileExplorerPane.ROOMS -> expandedTreeIds.isNotEmpty()
                        MobileExplorerPane.GROUPS -> expandedGroupIds.isNotEmpty()
                        MobileExplorerPane.TOPOLOGY -> expandedTopologyIds.isNotEmpty()
                    }

                    IconButton(
                        onClick = {
                            when (pane) {
                                MobileExplorerPane.ROOMS -> {
                                    if (expandedTreeIds.isNotEmpty()) {
                                        expandedTreeIds = emptySet()
                                    } else {
                                        val all = mutableSetOf<String>()
                                        fun collect(loc: BuildingLocation, prefix: String) {
                                            val path = if (prefix.isEmpty()) loc.name else "$prefix/${loc.name}"
                                            all += path
                                            loc.functions.forEach { all += "$path/fn:${it.id}" }
                                            loc.children.forEach { collect(it, path) }
                                        }
                                        project?.locations?.forEach { collect(it, "") }
                                        expandedTreeIds = all
                                    }
                                }
                                MobileExplorerPane.GROUPS -> {
                                    if (expandedGroupIds.isNotEmpty()) {
                                        expandedGroupIds = emptySet()
                                    } else {
                                        val all = mutableSetOf<String>()
                                        project?.effectiveGroupRanges?.forEach { main ->
                                            all += main.id
                                            main.children.forEach { sub ->
                                                all += sub.id
                                            }
                                        }
                                        expandedGroupIds = all
                                    }
                                }
                                MobileExplorerPane.TOPOLOGY -> {
                                    if (expandedTopologyIds.isNotEmpty()) {
                                        expandedTopologyIds = emptySet()
                                    } else {
                                        val all = mutableSetOf<String>()
                                        project?.areas?.forEach { area ->
                                            all += "area:${area.area}"
                                            area.lines.forEach { line ->
                                                all += "line:${line.id}"
                                            }
                                        }
                                        expandedTopologyIds = all
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (isAnyExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                            contentDescription = if (isAnyExpanded) "Collapse All" else "Expand All",
                            modifier = Modifier.size(18.dp),
                            tint = if (isAnyExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (pane == MobileExplorerPane.ROOMS) {
                        IconButton(
                            onClick = {
                                buildingViewMode = if (buildingViewMode == BuildingViewMode.TREE) BuildingViewMode.CARDS else BuildingViewMode.TREE
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (buildingViewMode == BuildingViewMode.TREE) Icons.Default.AccountTree else Icons.Default.FolderOpen,
                                contentDescription = "Switch View Mode",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else if (pane == MobileExplorerPane.GROUPS) {
                        IconButton(
                            onClick = {
                                groupViewMode = if (groupViewMode == GroupViewMode.TREE) GroupViewMode.FLAT else GroupViewMode.TREE
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (groupViewMode == GroupViewMode.TREE) Icons.Default.AccountTree else Icons.Default.ViewList,
                                contentDescription = "Switch Group View Mode",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

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

                        val lineCoupler = activeLine.devices.firstOrNull { it.address.device == 0 }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Line Diagnostics & Coupler Health Launchpad Card
                            item(key = "line-launchpad") {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Router,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    text = "LINE ${activeLine.id} COMMISSIONING BENCH",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                            ) {
                                                Text(
                                                    text = "${activeLine.devices.size} devices • ${activeLine.mediumType}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 9.sp,
                                                )
                                            }
                                        }

                                        lineCoupler?.let { c ->
                                            val cPing = state.deviceStatuses[c.address]
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
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
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                                        ) {
                                                            Text(
                                                                text = c.address.toString(),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                            )
                                                        }
                                                        Text(
                                                            text = c.name,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        if (cPing != null) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = if (cPing.reachable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                            ) {
                                                                Text(
                                                                    text = if (cPing.reachable) "${cPing.latencyMs}ms" else "TIMEOUT",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (cPing.reachable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    fontSize = 9.sp,
                                                                )
                                                            }
                                                        }

                                                        FilledTonalButton(
                                                            onClick = { controller.pingIndividualDevice(c.address.toString()) },
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                                            modifier = Modifier.height(24.dp),
                                                        ) {
                                                            Text("Ping .0", fontSize = 9.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 1-Tap Diagnostic Launchers
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    controller.selectTab(AppTab.DIAGNOSTICS)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text("16×16 Matrix", fontSize = 10.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    activeLine.devices.forEach { dev ->
                                                        controller.pingIndividualDevice(dev.address.toString())
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text("Ping All (${activeLine.devices.size})", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }

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
                    // Level 0: Topology Tree or Folders
                    if (topologyViewMode == TopologyViewMode.TREE) {
                        val visibleTopologyTreeNodes = remember(project.areas, project, expandedTopologyIds, state.deviceStatuses, state.programmingModeDevices) {
                            flattenTopologyTree(project.areas, project, state, expandedTopologyIds)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item(key = "topology-tree-toolbar") {
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
                                            text = "ETS TOPOLOGY TREE (${project.areas.size} Areas • ${project.allLines.size} Lines • ${project.allDevices.size} Devs)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            AssistChip(
                                                onClick = {
                                                    val all = mutableSetOf<String>()
                                                    for (area in project.areas) {
                                                        all += "area-${area.area}"
                                                        area.lines.forEach { all += "line-${it.id}" }
                                                    }
                                                    expandedTopologyIds = all
                                                },
                                                label = { Text("Expand All", fontSize = 9.5.sp) },
                                                leadingIcon = { Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(12.dp)) },
                                                modifier = Modifier.height(26.dp),
                                            )
                                            AssistChip(
                                                onClick = { expandedTopologyIds = emptySet() },
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
                                        // View Mode Toggle (Tree View vs Folders)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {
                                            Row(modifier = Modifier.padding(2.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                                        Text("Tree View", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                                    }
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.Transparent,
                                                    modifier = Modifier.clickable { topologyViewMode = TopologyViewMode.FOLDERS },
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("Folders", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                project.areas.firstOrNull()?.lines?.firstOrNull()?.let { line ->
                                                    controller.runProjectLineAudit(line.area, line.line)
                                                    controller.selectTab(AppTab.DIAGNOSTICS)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Audit Lines", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            items(visibleTopologyTreeNodes, key = { it.id }) { node ->
                                when (node) {
                                    is TopologyTreeNode.AreaNode -> {
                                        TopologyTreeAreaItem(
                                            node = node,
                                            onToggle = {
                                                expandedTopologyIds = if (node.isExpanded) expandedTopologyIds - node.id else expandedTopologyIds + node.id
                                            },
                                            onSelectArea = {
                                                selectedAreaNumber = node.area.area
                                            },
                                        )
                                    }
                                    is TopologyTreeNode.LineNode -> {
                                        TopologyTreeLineItem(
                                            node = node,
                                            onToggle = {
                                                expandedTopologyIds = if (node.isExpanded) expandedTopologyIds - node.id else expandedTopologyIds + node.id
                                            },
                                            onSelectLine = {
                                                selectedAreaNumber = node.line.area
                                                selectedLineId = node.line.id
                                            },
                                            onAuditLine = {
                                                controller.runProjectLineAudit(node.line.area, node.line.line)
                                                controller.selectTab(AppTab.DIAGNOSTICS)
                                            },
                                        )
                                    }
                                    is TopologyTreeNode.DeviceNode -> {
                                        TopologyTreeDeviceItem(
                                            node = node,
                                            project = project,
                                            onOpenObjects = { controller.openDeviceObjectsInExplorer(node.device.address) },
                                            onPing = { controller.pingIndividualDevice(node.device.address.toString()) },
                                            onLocate = { controller.locateDevice(node.device.address) },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Folders View Mode
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "topology-folders-header") {
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

                                    // View Mode Toggle (Tree View vs Folders)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(modifier = Modifier.padding(2.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.Transparent,
                                                modifier = Modifier.clickable { topologyViewMode = TopologyViewMode.TREE },
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("Tree View", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                                    Text("Folders", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                                }
                                            }
                                        }
                                    }
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
            }

            MobileExplorerPane.GROUPS -> {
                val ranges = project.effectiveGroupRanges
                val mainRange = activeMainRangeId?.let { id -> ranges.firstOrNull { it.id == id } }
                val subRange = activeSubRangeId?.let { id -> mainRange?.children?.firstOrNull { it.id == id } }

                if (groupViewMode == GroupViewMode.TREE) {
                    val visibleGroupTreeNodes = remember(ranges, project, expandedGroupIds, state.groupLiveValues) {
                        flattenGroupTree(ranges, project, state, expandedGroupIds)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(key = "group-tree-header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "ETS GROUP ADDRESSES (${ranges.size} Main Groups • ${project.groupAddresses.size} GAs)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                FilledTonalButton(
                                    onClick = { controller.pollAllProjectGroupAddresses() },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
                                    modifier = Modifier.height(24.dp),
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Poll All GAs", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        items(visibleGroupTreeNodes, key = { it.id }) { node ->
                            when (node) {
                                is GroupTreeNode.MainGroupNode -> {
                                    GroupTreeMainItem(
                                        node = node,
                                        onToggle = {
                                            expandedGroupIds = if (node.isExpanded) expandedGroupIds - node.id else expandedGroupIds + node.id
                                        },
                                        onPoll = {
                                            val addrs = node.range.collectAllAddresses()
                                            addrs.take(40).forEach { controller.readGroupAddress(it.toString()) }
                                        },
                                    )
                                }
                                is GroupTreeNode.MiddleGroupNode -> {
                                    GroupTreeMiddleItem(
                                        node = node,
                                        onToggle = {
                                            expandedGroupIds = if (node.isExpanded) expandedGroupIds - node.id else expandedGroupIds + node.id
                                        },
                                        onPoll = {
                                            val addrs = node.subRange.collectAllAddresses()
                                            addrs.forEach { controller.readGroupAddress(it.toString()) }
                                        },
                                    )
                                }
                                is GroupTreeNode.GroupLeafNode -> {
                                    GroupTreeLeafItem(
                                        node = node,
                                        controller = controller,
                                        onInspect = { onInspectGroupSheet(node.ga) },
                                    )
                                }
                            }
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
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
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
                                                color = Color.Transparent,
                                                modifier = Modifier.clickable { groupViewMode = GroupViewMode.TREE },
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("Tree View", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                                    Text("Folders", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                                }
                                            }
                                        }
                                    }
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
                    val visibleTreeNodes = remember(project, expandedTreeIds, state.groupLiveValues, state.deviceStatuses, state.programmingModeDevices) {
                        buildVisibleBuildingTree(project.locations, project, state, expandedTreeIds)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
                                            onSelectSpace = {
                                                locationPath = treeNode.path
                                                buildingViewMode = BuildingViewMode.CARDS
                                            },
                                        )
                                    }
                                    is BuildingTreeNode.FunctionNode -> {
                                        BuildingTreeFunctionItem(
                                            node = treeNode,
                                            project = project,
                                            state = state,
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
                                    is BuildingTreeNode.FunctionGaLeafNode -> {
                                        BuildingTreeGaLeafItem(
                                            node = treeNode,
                                            controller = controller,
                                            onInspect = { onInspectGroupSheet(treeNode.ga) },
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
                    val totalBranchFunctions = remember(currentLoc) { currentLoc.collectAllFunctions() }
                    val directDevices = remember(currentLoc, project) {
                        currentLoc.deviceAddresses.mapNotNull { project.findDevice(it) }
                    }
                    val spaceGas = remember(currentLoc, project) {
                        val fromFuncs = currentLoc.collectAllGroupAddresses()
                        val fromDevs = currentLoc.collectAllDeviceAddresses().mapNotNull { project.findDevice(it) }.flatMap { it.groupAddresses }
                        (fromFuncs + fromDevs).distinct().mapNotNull { project.findGroupAddress(it) }
                    }

                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        text = buildString {
                                            if (currentLoc.children.isNotEmpty()) append("${currentLoc.children.size} spaces • ")
                                            if (totalBranchFunctions.isNotEmpty()) append("${totalBranchFunctions.size} funcs • ")
                                            append("${totalBranchDevices.size} devs")
                                        },
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

                            // Room Functions & Trade Circuits (ETS Functions: lights, blinds, heating, etc.)
                            if (currentLoc.functions.isNotEmpty()) {
                                item(key = "space-functions-header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "ROOM FUNCTIONS & CIRCUITS (${currentLoc.functions.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        AssistChip(
                                            onClick = {
                                                currentLoc.functions.flatMap { it.groupAddresses }.take(40).forEach { ga ->
                                                    controller.readGroupAddress(ga.toString())
                                                }
                                            },
                                            label = { Text("Read All GAs", fontSize = 10.sp) },
                                            leadingIcon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(12.dp)) },
                                            modifier = Modifier.height(26.dp),
                                        )
                                    }
                                }

                                items(currentLoc.functions, key = { "fn-${it.id}" }) { fn ->
                                    MobileRoomFunctionCard(
                                        function = fn,
                                        project = project,
                                        state = state,
                                        controller = controller,
                                        onInspectGa = { ga ->
                                            controller.readGroupAddress(ga.address.toString())
                                        },
                                    )
                                }
                            }

                            // Room Commissioning Cockpit & Live Circuit Controls
                            if (spaceGas.isNotEmpty()) {
                                item(key = "space-cockpit") {
                                    MobileRoomCockpitCard(
                                        location = currentLoc,
                                        groupAddresses = spaceGas,
                                        state = state,
                                        controller = controller,
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

                            if (currentLoc.children.isEmpty() && directDevices.isEmpty() && currentLoc.functions.isEmpty()) {
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
                                                "No child spaces, functions, or devices are assigned to ${currentLoc.name} in ETS.",
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
                            val gaInfo = remember(primaryGa, obj, project) {
                                project.findGroupAddress(primaryGa) ?: GroupAddressInfo(primaryGa, obj.name, dpt = obj.dpt)
                            }
                            EtsGaInlineControlBar(
                                ga = gaInfo,
                                liveValue = state.groupLiveValues[primaryGa]?.valueText,
                                controller = controller,
                            )
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

                if (ga.isSecure) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF047857),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "KNX Secure", tint = Color.White, modifier = Modifier.size(9.dp))
                            Text(
                                text = "SECURE",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
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

            // ETS Standard Inline KNX Control Bar (Switches, Absolute Dimmers, Blinds, HVAC, Read)
            EtsGaInlineControlBar(
                ga = ga,
                liveValue = liveEntry?.valueText,
                controller = controller,
            )

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
    state: AppUiState,
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
        val isExpanded = nodeId in expandedIds || loc.name in expandedIds
        val isBuilding = depth == 0 && (loc.type.equals("Building", ignoreCase = true) || loc.type.equals("BuildingPart", ignoreCase = true))

        if (isBuilding) {
            result += BuildingTreeNode.BuildingNode(
                id = nodeId,
                depth = depth,
                name = loc.name.ifBlank { "Building" },
                location = loc,
                totalDevices = loc.collectAllDeviceAddresses().size,
                totalFunctions = loc.collectAllFunctions().size,
                isExpanded = isExpanded,
                childCount = loc.children.size + loc.deviceAddresses.size + loc.functions.size,
            )
        } else {
            result += BuildingTreeNode.SpaceNode(
                id = nodeId,
                depth = depth,
                name = loc.name.ifBlank { "Space" },
                location = loc,
                totalDevices = loc.collectAllDeviceAddresses().size,
                directDevicesCount = loc.deviceAddresses.size,
                directFunctionsCount = loc.functions.size,
                isExpanded = isExpanded,
                childCount = loc.children.size + loc.deviceAddresses.size + loc.functions.size,
                path = currentPath,
            )
        }

        if (isExpanded) {
            // 1. Traverse child spaces first (Floors, Rooms, Sub-spaces, Distribution Boards)
            for (child in loc.children) {
                traverseSpace(child, depth + 1, currentPath)
            }

            // 2. Room Functions & Circuits (Lighting, Blinds, HVAC, etc.)
            for (fn in loc.functions) {
                val fnNodeId = "$nodeId/fn:${fn.id}"
                val isFnExpanded = fnNodeId in expandedIds
                result += BuildingTreeNode.FunctionNode(
                    id = fnNodeId,
                    depth = depth + 1,
                    name = fn.name,
                    function = fn,
                    location = loc,
                    isExpanded = isFnExpanded,
                    gaCount = fn.groupAddresses.size,
                    path = currentPath,
                )

                if (isFnExpanded) {
                    for (gaAddr in fn.groupAddresses) {
                        val gaInfo = project.findGroupAddress(gaAddr) ?: GroupAddressInfo(gaAddr, gaAddr.toString())
                        val gaLeafId = "$fnNodeId/ga:${gaAddr.raw}"
                        result += BuildingTreeNode.FunctionGaLeafNode(
                            id = gaLeafId,
                            depth = depth + 2,
                            name = gaInfo.name,
                            function = fn,
                            ga = gaInfo,
                            liveEntry = state.groupLiveValues[gaAddr],
                        )
                    }
                }
            }

            // 3. Direct physical devices assigned to this space
            for (devAddr in loc.deviceAddresses) {
                val dev = project.findDevice(devAddr)
                if (dev != null) {
                    val devNodeId = "$nodeId/dev:${dev.address.raw}"
                    val isOnline = state.deviceStatuses[dev.address]?.reachable
                    val isProg = dev.address in state.programmingModeDevices
                    result += BuildingTreeNode.DeviceNode(
                        id = devNodeId,
                        depth = depth + 1,
                        name = dev.name,
                        device = dev,
                        path = currentPath,
                        isOnline = isOnline,
                        isProgMode = isProg,
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
                    text = "${node.location.children.size} spaces • ${node.totalFunctions} functions • ${node.totalDevices} devices",
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
    onSelectSpace: () -> Unit,
) {
    val loc = node.location
    val isPart = loc.type.equals("BuildingPart", ignoreCase = true)
    val isFloor = loc.type.equals("Floor", ignoreCase = true)
    val isRoom = loc.type.equals("Room", ignoreCase = true)
    val isBoard = loc.type.contains("Distribution", ignoreCase = true) || loc.name.contains("Cabinet", ignoreCase = true) || loc.name.contains("Board", ignoreCase = true) || loc.name.contains("Panel", ignoreCase = true)

    val icon = when {
        isPart -> Icons.Default.Domain
        isFloor -> Icons.Default.Layers
        isRoom -> Icons.Default.MeetingRoom
        isBoard -> Icons.Default.DeveloperBoard
        loc.type.contains("Corridor", ignoreCase = true) || loc.name.contains("Corridor", ignoreCase = true) || loc.name.contains("Hall", ignoreCase = true) ->
            Icons.AutoMirrored.Filled.DirectionsWalk
        loc.type.contains("Stair", ignoreCase = true) || loc.name.contains("Stair", ignoreCase = true) ->
            Icons.Default.Stairs
        loc.type.contains("Outdoor", ignoreCase = true) || loc.name.contains("Garden", ignoreCase = true) || loc.name.contains("Pool", ignoreCase = true) ->
            Icons.Default.Park
        else -> if (node.childCount > 0) Icons.Default.Folder else Icons.Default.MeetingRoom
    }

    val indent = (node.depth * 8).dp

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
                        val badgeText = when {
                            isBoard || loc.type.equals("DistributionBoard", ignoreCase = true) -> "CABINET"
                            isPart || loc.type.equals("BuildingPart", ignoreCase = true) -> "PART"
                            else -> loc.type.uppercase()
                        }
                        Text(
                            text = badgeText,
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

                    val fnCount = loc.functions.size
                    val devCount = loc.deviceAddresses.size
                    val countText = when {
                        fnCount > 0 && devCount > 0 -> "$fnCount fn • $devCount dev"
                        fnCount > 0 -> "$fnCount fn"
                        devCount > 0 -> "$devCount dev"
                        node.totalDevices > 0 -> "${node.totalDevices} dev total"
                        else -> "${node.location.children.size} spaces"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )

                    IconButton(
                        onClick = onSelectSpace,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Launch,
                            contentDescription = "Drill into space",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }

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
private fun BuildingTreeFunctionItem(
    node: BuildingTreeNode.FunctionNode,
    project: KnxProject,
    state: AppUiState,
    controller: AppController,
    onToggle: () -> Unit,
) {
    val fn = node.function
    val indent = (node.depth * 8).dp
    val fnNameLower = fn.name.lowercase()

    val linkedGas = remember(fn, project) {
        fn.groupAddresses.mapNotNull { project.findGroupAddress(it) }
    }

    val icon = when {
        fnNameLower.contains("spot") || fnNameLower.contains("light") || fnNameLower.contains("licht") || fn.type == "FT-1" -> Icons.Default.Lightbulb
        fnNameLower.contains("dimm") || fn.type == "FT-2" -> Icons.Default.Tune
        fnNameLower.contains("blind") || fnNameLower.contains("shutter") || fnNameLower.contains("jal") || fnNameLower.contains("window") || fn.type == "FT-3" -> Icons.Default.Window
        fnNameLower.contains("temp") || fnNameLower.contains("heat") || fnNameLower.contains("klima") || fn.type == "FT-4" -> Icons.Default.Thermostat
        fnNameLower.contains("detect") || fnNameLower.contains("motion") || fnNameLower.contains("sensor") -> Icons.Default.Sensors
        fnNameLower.contains("scene") || fnNameLower.contains("szene") -> Icons.Default.AutoAwesome
        else -> Icons.Default.Widgets
    }

    val isBlindFunction = fn.type == "FT-3" || fnNameLower.contains("blind") || fnNameLower.contains("shutter") || fnNameLower.contains("jal") || fnNameLower.contains("rollladen")

    // Resolve Primary Absolute Dimmer GA
    val primaryDimmerGa = remember(linkedGas, fn, isBlindFunction) {
        if (isBlindFunction) null
        else {
            linkedGas.firstOrNull { ga ->
                val d = DptCodec.resolveMainDpt(ga.dpt)
                val dptStr = ga.dpt?.lowercase() ?: ""
                val nameL = ga.name.lowercase()
                (d == MainDpt.DPT_5_PERCENT || d == MainDpt.DPT_5_UNSIGNED ||
                    dptStr.contains("5.001") || dptStr.contains("5.004") || dptStr.contains("5.010") ||
                    dptStr.startsWith("5.") || dptStr.startsWith("dpst-5-") ||
                    nameL.contains("dimm") || nameL.contains("helligkeit") || nameL.contains("brightness") ||
                    nameL.contains("wert") || nameL.contains("value") || nameL.contains("%") ||
                    nameL.contains("absolut") || nameL.contains("level") ||
                    nameL.contains("prozent") || nameL.contains("percent")) &&
                    !nameL.contains("schalt") && !nameL.contains("switch") && !nameL.contains("step") &&
                    !nameL.contains("relativ") && !nameL.contains("status")
            } ?: linkedGas.firstOrNull { ga ->
                val nameL = ga.name.lowercase()
                (nameL.contains("dimm") || nameL.contains("helligkeit") || nameL.contains("wert")) &&
                    !nameL.contains("schalt") && !nameL.contains("step")
            }
        }
    }

    // Resolve Primary Blind Position GA (DPT 5.001 / position %)
    val primaryBlindPosGa = remember(linkedGas, isBlindFunction) {
        if (!isBlindFunction) null
        else {
            linkedGas.firstOrNull { ga ->
                val d = DptCodec.resolveMainDpt(ga.dpt)
                val dptStr = ga.dpt?.lowercase() ?: ""
                val nameL = ga.name.lowercase()
                (d == MainDpt.DPT_5_PERCENT || d == MainDpt.DPT_5_UNSIGNED || dptStr.contains("5.001") ||
                    nameL.contains("position") || nameL.contains("höhe") || nameL.contains("pos") ||
                    nameL.contains("lamelle") || nameL.contains("%")) &&
                    !nameL.contains("status") && !nameL.contains("rückmeldung")
            } ?: linkedGas.firstOrNull { ga ->
                val d = DptCodec.resolveMainDpt(ga.dpt)
                (d == MainDpt.DPT_5_PERCENT || d == MainDpt.DPT_5_UNSIGNED) && !ga.name.contains("status", ignoreCase = true)
            }
        }
    }

    // Resolve Primary Switch GA
    val primarySwitchGa = remember(linkedGas) {
        linkedGas.firstOrNull { ga ->
            val d = DptCodec.resolveMainDpt(ga.dpt)
            val nameL = ga.name.lowercase()
            (d == MainDpt.DPT_1_BOOL || nameL.contains("schalt") || nameL.contains("switch") || nameL.contains("ein/aus") || nameL.contains("on/off")) &&
                !nameL.contains("blind") && !nameL.contains("jalousie") && !nameL.contains("status") && !nameL.contains("rückmeldung")
        } ?: linkedGas.firstOrNull { ga ->
            val d = DptCodec.resolveMainDpt(ga.dpt)
            d == MainDpt.DPT_1_BOOL && !ga.name.contains("status", ignoreCase = true)
        }
    }

    val blindMoveGa = if (isBlindFunction) {
        linkedGas.firstOrNull { ga -> ga.dpt?.contains("1.008") == true || ga.name.contains("auf/ab", ignoreCase = true) || ga.name.contains("up/down", ignoreCase = true) || ga.name.contains("fahren", ignoreCase = true) } ?: primarySwitchGa
    } else null
    val blindStopGa = if (isBlindFunction) {
        linkedGas.firstOrNull { ga -> ga.dpt?.contains("1.007") == true || ga.name.contains("stop", ignoreCase = true) || ga.name.contains("schritt", ignoreCase = true) || ga.name.contains("lamelle", ignoreCase = true) }
    } else null

    val isHvacFunction = fn.type == "FT-4" || fnNameLower.contains("temp") || fnNameLower.contains("heat") || fnNameLower.contains("klima")
    val tempGa = if (isHvacFunction) {
        linkedGas.firstOrNull { ga -> DptCodec.resolveMainDpt(ga.dpt) == MainDpt.DPT_9_FLOAT || ga.name.contains("temp", ignoreCase = true) || ga.name.contains("sollwert", ignoreCase = true) }
    } else null

    val switchStatusGa = remember(linkedGas) {
        linkedGas.firstOrNull { ga ->
            val nameL = ga.name.lowercase()
            (nameL.contains("status") || nameL.contains("rück")) &&
                (nameL.contains("schalt") || nameL.contains("switch") || DptCodec.resolveMainDpt(ga.dpt) == MainDpt.DPT_1_BOOL) &&
                !nameL.contains("absolut") && !nameL.contains("dimm") && !nameL.contains("wert") && !nameL.contains("%")
        }
    }

    val dimStatusGa = remember(linkedGas) {
        linkedGas.firstOrNull { ga ->
            val nameL = ga.name.lowercase()
            (nameL.contains("status") || nameL.contains("rück")) &&
                (nameL.contains("dimm") || nameL.contains("absolut") || nameL.contains("wert") || nameL.contains("hellig") || nameL.contains("bright") || nameL.contains("%") ||
                    DptCodec.resolveMainDpt(ga.dpt) == MainDpt.DPT_5_PERCENT || DptCodec.resolveMainDpt(ga.dpt) == MainDpt.DPT_5_UNSIGNED)
        }
    }

    val statusGa = remember(linkedGas) {
        switchStatusGa ?: dimStatusGa ?: linkedGas.firstOrNull { ga -> ga.name.contains("status", ignoreCase = true) || ga.name.contains("rückmeldung", ignoreCase = true) }
    }

    val isFunctionSecure = remember(linkedGas, state.keyring) {
        linkedGas.any { it.isSecure || state.keyring?.isGroupSecure(it.address) == true }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (node.isExpanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Header row: Icon, Name, Quick Switch / Blinds / Temp buttons, and Chevron to toggle expand
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onToggle),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "${node.gaCount} GAs" + (if (primaryDimmerGa != null) " • Dimmer" else if (primaryBlindPosGa != null) " • Blind Pos" else if (primarySwitchGa != null) " • Switch" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isFunctionSecure) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color(0xFF047857),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Icon(Icons.Default.Shield, contentDescription = "KNX Secure", tint = Color.White, modifier = Modifier.size(8.dp))
                                            Text(
                                                text = "SECURE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Direct Action Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (primarySwitchGa != null) {
                            val swText = primarySwitchGa.address.toString()
                            val swLiveVal = (switchStatusGa ?: primarySwitchGa).let { state.groupLiveValues[it.address]?.valueText }
                            val isSwOn = swLiveVal?.equals("ON", ignoreCase = true) == true || swLiveVal?.contains("1") == true

                            FilledTonalButton(
                                onClick = { controller.writeGroupSwitch(swText, false) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text("OFF", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { controller.writeGroupSwitch(swText, true) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                colors = if (isSwOn) {
                                    ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White)
                                } else ButtonDefaults.buttonColors(),
                            ) {
                                Text("ON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (blindMoveGa != null) {
                            val moveText = blindMoveGa.address.toString()
                            FilledTonalButton(
                                onClick = { controller.writeGroupSwitch(moveText, false) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(12.dp))
                                Text("UP", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            blindStopGa?.let { stopGa ->
                                FilledTonalButton(
                                    onClick = { controller.writeGroupSwitch(stopGa.address.toString(), false) },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Text("STOP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            FilledTonalButton(
                                onClick = { controller.writeGroupSwitch(moveText, true) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(12.dp))
                                Text("DN", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (tempGa != null) {
                            val tText = tempGa.address.toString()
                            val liveT = state.groupLiveValues[tempGa.address]?.valueText
                            val parsedT = liveT?.replace("°C", "")?.replace("°", "")?.trim()?.toDoubleOrNull() ?: 21.0
                            FilledTonalButton(
                                onClick = { controller.writeGroupFloat16(tText, parsedT - 0.5) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text("-0.5°", fontSize = 9.5.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = liveT ?: "21.0°C",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                            FilledTonalButton(
                                onClick = { controller.writeGroupFloat16(tText, parsedT + 0.5) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text("+0.5°", fontSize = 9.5.sp)
                            }
                        }

                        // Chevron Icon to expand underlying GAs
                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (node.isExpanded) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
                                contentDescription = if (node.isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // If Dimmer: Direct Integrated Dimmer Slider and Preset Strip!
                if (primaryDimmerGa != null) {
                    val dimText = primaryDimmerGa.address.toString()
                    val dimLiveVal = (dimStatusGa ?: primaryDimmerGa).let { state.groupLiveValues[it.address]?.valueText }
                    val livePct = remember(dimLiveVal) {
                        dimLiveVal?.replace("%", "")?.trim()?.toDoubleOrNull()?.toFloat() ?: 0f
                    }
                    var sliderPos by remember(livePct) { mutableFloatStateOf(livePct.coerceIn(0f, 100f)) }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Presets Row + Read
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf(0, 25, 50, 75, 100).forEach { pct ->
                                        FilledTonalButton(
                                            onClick = {
                                                sliderPos = pct.toFloat()
                                                controller.writeGroupPercent(dimText, pct.toDouble())
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                            modifier = Modifier.height(24.dp),
                                            colors = if (sliderPos.toInt() == pct) {
                                                ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                )
                                            } else ButtonDefaults.filledTonalButtonColors(),
                                        ) {
                                            Text("$pct%", fontSize = 9.sp, fontWeight = if (sliderPos.toInt() == pct) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { controller.readGroupAddress(dimText) },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                    modifier = Modifier.height(24.dp),
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("R", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Slider Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Slider(
                                    value = sliderPos,
                                    onValueChange = { sliderPos = it },
                                    onValueChangeFinished = { controller.writeGroupPercent(dimText, sliderPos.toDouble()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f).height(24.dp),
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "${sliderPos.toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // If Blind with Position Control: Direct Integrated Position Slider and Presets!
                if (primaryBlindPosGa != null) {
                    val posText = primaryBlindPosGa.address.toString()
                    val posLiveVal = (statusGa ?: primaryBlindPosGa).let { state.groupLiveValues[it.address]?.valueText }
                    val livePct = remember(posLiveVal) {
                        posLiveVal?.replace("%", "")?.trim()?.toDoubleOrNull()?.toFloat() ?: 0f
                    }
                    var sliderPos by remember(livePct) { mutableFloatStateOf(livePct.coerceIn(0f, 100f)) }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Presets Row + Read
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf(0, 25, 50, 75, 100).forEach { pct ->
                                        FilledTonalButton(
                                            onClick = {
                                                sliderPos = pct.toFloat()
                                                controller.writeGroupPercent(posText, pct.toDouble())
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                                            modifier = Modifier.height(24.dp),
                                            colors = if (sliderPos.toInt() == pct) {
                                                ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                )
                                            } else ButtonDefaults.filledTonalButtonColors(),
                                        ) {
                                            Text(
                                                text = when (pct) {
                                                    0 -> "UP (0%)"
                                                    100 -> "DN (100%)"
                                                    else -> "$pct%"
                                                },
                                                fontSize = 8.5.sp,
                                                fontWeight = if (sliderPos.toInt() == pct) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { controller.readGroupAddress(posText) },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                                    modifier = Modifier.height(24.dp),
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("R", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Slider Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.Window, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                Slider(
                                    value = sliderPos,
                                    onValueChange = { sliderPos = it },
                                    onValueChangeFinished = { controller.writeGroupPercent(posText, sliderPos.toDouble()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f).height(24.dp),
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "${sliderPos.toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
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
// ETS Standard Inline KNX Control Bar (Switches, Absolute Dimmers, Blinds, HVAC, Read)
// ---------------------------------------------------------------------------
@Composable
fun EtsGaInlineControlBar(
    ga: GroupAddressInfo,
    liveValue: String?,
    controller: AppController,
    modifier: Modifier = Modifier,
) {
    val gaText = ga.address.toString()
    val mainDpt = DptCodec.resolveMainDpt(ga.dpt)
    val nameLower = ga.name.lowercase()
    val dptStr = ga.dpt?.lowercase() ?: ""

    val isDpt5 = mainDpt == MainDpt.DPT_5_PERCENT || mainDpt == MainDpt.DPT_5_UNSIGNED ||
        dptStr.contains("5.001") || dptStr.contains("5.004") || dptStr.contains("5.010") ||
        dptStr.startsWith("5.") || dptStr.startsWith("dpst-5-") || dptStr.startsWith("dpt-5")

    val isDpt3 = mainDpt == MainDpt.DPT_3_DIM_STEP || dptStr.startsWith("3.") || dptStr.startsWith("dpst-3-")

    val hasDimKeyword = nameLower.contains("dimm") || nameLower.contains("helligkeit") ||
        nameLower.contains("brightness") || nameLower.contains("level") ||
        nameLower.contains("wert") || nameLower.contains("value") || nameLower.contains("%") ||
        nameLower.contains("absolut") || nameLower.contains("position") || nameLower.contains("prozent") ||
        nameLower.contains("percent") || nameLower.contains("scale") || nameLower.contains("stufe")

    val isStatusFeedback = nameLower.contains("status") || nameLower.contains("rück")

    val isRelativeDimmer = isDpt3 || (hasDimKeyword && (nameLower.contains("relativ") || nameLower.contains("schritt") || nameLower.contains("step") || nameLower.contains("4-bit") || nameLower.contains("4 bit")))

    val isAbsoluteDimmer = !isRelativeDimmer && !isStatusFeedback && (isDpt5 || (hasDimKeyword && !nameLower.contains("schalt") && !nameLower.contains("switch")) || nameLower.contains("absolut") || nameLower.contains("position") || nameLower.contains("prozent") || nameLower.contains("percent"))

    val isSwitch = !isAbsoluteDimmer && !isRelativeDimmer && !isStatusFeedback &&
        ((mainDpt == MainDpt.DPT_1_BOOL || nameLower.contains("schalt") || nameLower.contains("switch") || nameLower.contains("ein/aus") || nameLower.contains("on/off")) &&
        !nameLower.contains("blind") && !nameLower.contains("jalousie") && !nameLower.contains("rollladen") &&
        !nameLower.contains("shutter") && !nameLower.contains("auf/ab") && !nameLower.contains("up/down") &&
        !nameLower.contains("stop") && !nameLower.contains("lamelle") && !nameLower.contains("step"))

    val isShutterMove = ga.dpt?.contains("1.008") == true ||
        nameLower.contains("auf/ab") || nameLower.contains("up/down") || nameLower.contains("fahren") ||
        ((nameLower.contains("blind") || nameLower.contains("jalousie") || nameLower.contains("rollladen") || nameLower.contains("shutter")) &&
            (nameLower.contains("move") || nameLower.contains("auf") || nameLower.contains("ab") || nameLower.contains("up") || nameLower.contains("down") || mainDpt == MainDpt.DPT_1_BOOL))

    val isShutterStep = ga.dpt?.contains("1.007") == true ||
        nameLower.contains("stop") || nameLower.contains("lamelle") || nameLower.contains("schritt")

    val isTemperature = mainDpt == MainDpt.DPT_9_FLOAT ||
        nameLower.contains("temp") || nameLower.contains("sollwert") || nameLower.contains("thermostat") || nameLower.contains("istwert")

    val isScene = mainDpt == MainDpt.DPT_17_SCENE ||
        nameLower.contains("szene") || nameLower.contains("scene")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Main Action & Preset Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isSwitch -> {
                    val isLiveOn = liveValue?.equals("ON", ignoreCase = true) == true || liveValue?.contains("1") == true
                    val isLiveOff = liveValue?.equals("OFF", ignoreCase = true) == true || liveValue?.contains("0") == true

                    FilledTonalButton(
                        onClick = { controller.writeGroupSwitch(gaText, false) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                        colors = if (isLiveOff) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Text("0 OFF", fontSize = 9.sp, fontWeight = if (isLiveOff) FontWeight.Bold else FontWeight.Normal)
                    }

                    Spacer(Modifier.width(4.dp))

                    Button(
                        onClick = { controller.writeGroupSwitch(gaText, true) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                        colors = if (isLiveOn) {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A),
                                contentColor = Color.White,
                            )
                        } else ButtonDefaults.buttonColors(),
                    ) {
                        Text("1 ON", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                isAbsoluteDimmer -> {
                    listOf(0, 25, 50, 75, 100).forEach { pct ->
                        FilledTonalButton(
                            onClick = { controller.writeGroupPercent(gaText, pct.toDouble()) },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                            modifier = Modifier.height(24.dp).padding(horizontal = 1.5.dp),
                        ) {
                            Text("$pct%", fontSize = 8.5.sp, fontWeight = if (pct == 0 || pct == 100) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                isShutterMove -> {
                    FilledTonalButton(
                        onClick = { controller.writeGroupSwitch(gaText, false) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("UP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = { controller.writeGroupSwitch(gaText, true) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("DOWN", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                isShutterStep -> {
                    FilledTonalButton(
                        onClick = { controller.writeGroupSwitch(gaText, false) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("STOP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                isRelativeDimmer -> {
                    FilledTonalButton(
                        onClick = { controller.writeGroupDimStep(gaText, increase = false, stepCode = 2) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text("DIM -", fontSize = 9.sp)
                    }
                    Spacer(Modifier.width(3.dp))
                    FilledTonalButton(
                        onClick = { controller.writeGroupDimStep(gaText, increase = false, stepCode = 0) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text("STOP", fontSize = 8.5.sp)
                    }
                    Spacer(Modifier.width(3.dp))
                    FilledTonalButton(
                        onClick = { controller.writeGroupDimStep(gaText, increase = true, stepCode = 2) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text("DIM +", fontSize = 9.sp)
                    }
                }

                isTemperature -> {
                    val parsedVal = liveValue?.replace("°C", "")?.replace("°", "")?.trim()?.toDoubleOrNull() ?: 21.0
                    FilledTonalButton(
                        onClick = { controller.writeGroupFloat16(gaText, parsedVal - 0.5) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text("-0.5°", fontSize = 9.sp)
                    }
                    Spacer(Modifier.width(3.dp))
                    FilledTonalButton(
                        onClick = { controller.writeGroupFloat16(gaText, parsedVal + 0.5) },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Text("+0.5°", fontSize = 9.sp)
                    }
                }

                isScene -> {
                    listOf(1, 2, 3, 4).forEach { sNum ->
                        FilledTonalButton(
                            onClick = { controller.writeGroupScene(gaText, sNum) },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                            modifier = Modifier.height(24.dp).padding(horizontal = 1.5.dp),
                        ) {
                            Text("S$sNum", fontSize = 8.5.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // ETS Read Button
            OutlinedButton(
                onClick = { controller.readGroupAddress(gaText) },
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                modifier = Modifier.height(24.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(2.dp))
                Text("Read", fontSize = 9.sp)
            }
        }

        // Secondary continuous slider row for Absolute Dimmer / Position
        if (isAbsoluteDimmer) {
            val livePct = remember(liveValue) {
                liveValue?.replace("%", "")?.trim()?.toDoubleOrNull()?.toFloat() ?: 0f
            }
            var sliderVal by remember(livePct) { mutableFloatStateOf(livePct.coerceIn(0f, 100f)) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Slider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = { controller.writeGroupPercent(gaText, sliderVal.toDouble()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f).height(24.dp),
                )
                Text(
                    text = "${sliderVal.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

@Composable
private fun BuildingTreeGaLeafItem(
    node: BuildingTreeNode.FunctionGaLeafNode,
    controller: AppController,
    onInspect: () -> Unit,
) {
    val ga = node.ga
    val gaText = ga.address.toString()
    val indent = (node.depth * 8).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(4.dp))

        OutlinedCard(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f).clickable(onClick = onInspect),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = gaText,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }

                        ga.dpt?.let { dpt ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = dpt,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                )
                            }
                        }

                        if (ga.isSecure) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF047857),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(Icons.Default.Shield, contentDescription = "KNX Secure", tint = Color.White, modifier = Modifier.size(8.dp))
                                    Text(
                                        text = "SECURE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        Text(
                            text = ga.name.ifBlank { "Group Address" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Live Value Badge
                    val liveVal = node.liveEntry?.valueText
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (liveVal != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = liveVal ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (liveVal != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }

                // Inline ETS Engineering Controls (Switches, Absolute Dimmers, Blinds, HVAC, Read)
                EtsGaInlineControlBar(
                    ga = ga,
                    liveValue = node.liveEntry?.valueText,
                    controller = controller,
                )
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
    val indent = (node.depth * 8 + 4).dp

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

                if (dev.isSecure || state.keyring?.isDeviceSecure(dev.address) == true) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFF047857),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "KNX Secure", tint = Color.White, modifier = Modifier.size(8.dp))
                            Text(
                                text = "SECURE",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
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
// ETS Room Function Card Component
// ---------------------------------------------------------------------------
@Composable
fun MobileRoomFunctionCard(
    function: BuildingFunction,
    project: KnxProject,
    state: AppUiState,
    controller: AppController,
    onInspectGa: (GroupAddressInfo) -> Unit,
) {
    val icon = when {
        function.name.contains("spot", ignoreCase = true) || function.name.contains("light", ignoreCase = true) || function.name.contains("licht", ignoreCase = true) || function.type == "FT-1" -> Icons.Default.Lightbulb
        function.name.contains("dimm", ignoreCase = true) || function.type == "FT-2" -> Icons.Default.Tune
        function.name.contains("blind", ignoreCase = true) || function.name.contains("shutter", ignoreCase = true) || function.name.contains("jal", ignoreCase = true) || function.name.contains("window", ignoreCase = true) || function.type == "FT-3" -> Icons.Default.Window
        function.name.contains("temp", ignoreCase = true) || function.name.contains("heat", ignoreCase = true) || function.name.contains("klima", ignoreCase = true) || function.type == "FT-4" -> Icons.Default.Thermostat
        function.name.contains("detect", ignoreCase = true) || function.name.contains("motion", ignoreCase = true) || function.name.contains("sensor", ignoreCase = true) -> Icons.Default.Sensors
        function.name.contains("scene", ignoreCase = true) || function.name.contains("szene", ignoreCase = true) -> Icons.Default.AutoAwesome
        else -> Icons.Default.Widgets
    }

    val allGas = remember(function, project) {
        function.groupAddresses.mapNotNull { project.findGroupAddress(it) }
    }
    val primaryDimmerGa = remember(allGas) {
        allGas.firstOrNull { ga ->
            val d = DptCodec.resolveMainDpt(ga.dpt)
            val nameL = ga.name.lowercase()
            !nameL.contains("status") && !nameL.contains("rück") &&
                (d == MainDpt.DPT_5_PERCENT || d == MainDpt.DPT_5_UNSIGNED ||
                    ga.dpt?.contains("5.001") == true || ga.dpt?.contains("5.004") == true ||
                    ga.name.contains("Dimmwert", ignoreCase = true) ||
                    ga.name.contains("Helligkeit", ignoreCase = true) ||
                    ga.name.contains("Brightness", ignoreCase = true) ||
                    ga.name.contains("Value", ignoreCase = true) ||
                    ga.name.contains("Wert", ignoreCase = true) ||
                    ga.name.contains("Absolut", ignoreCase = true) ||
                    ga.name.contains("%"))
        }
    }
    val primarySwitchGa = remember(allGas) {
        allGas.firstOrNull { ga ->
            val d = DptCodec.resolveMainDpt(ga.dpt)
            val nameL = ga.name.lowercase()
            !nameL.contains("status") && !nameL.contains("rück") &&
                (d == MainDpt.DPT_1_BOOL || nameL.contains("schalt") || nameL.contains("switch")) &&
                !nameL.contains("blind") && !nameL.contains("jalousie")
        }
    }

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header Row: Icon, Function Name, GA Count
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
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = function.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "${function.groupAddresses.size} GAs",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // ETS Unified Dimmer Circuit Master Cockpit (Direct Dimming Control from Building)
            if (primaryDimmerGa != null) {
                val dimText = primaryDimmerGa.address.toString()
                val dimLiveVal = state.groupLiveValues[primaryDimmerGa.address]?.valueText
                val livePct = remember(dimLiveVal) {
                    dimLiveVal?.replace("%", "")?.trim()?.toDoubleOrNull()?.toFloat() ?: 0f
                }
                var sliderPos by remember(livePct) { mutableFloatStateOf(livePct.coerceIn(0f, 100f)) }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "DIMMER: ${primaryDimmerGa.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            if (primarySwitchGa != null) {
                                val swText = primarySwitchGa.address.toString()
                                val isSwOn = state.groupLiveValues[primarySwitchGa.address]?.valueText?.equals("ON", ignoreCase = true) == true
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledTonalButton(
                                        onClick = { controller.writeGroupSwitch(swText, false) },
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp),
                                    ) {
                                        Text("OFF", fontSize = 8.5.sp)
                                    }
                                    Button(
                                        onClick = { controller.writeGroupSwitch(swText, true) },
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp),
                                        colors = if (isSwOn) ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)) else ButtonDefaults.buttonColors(),
                                    ) {
                                        Text("ON", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Dimmer Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Slider(
                                value = sliderPos,
                                onValueChange = { sliderPos = it },
                                onValueChangeFinished = { controller.writeGroupPercent(dimText, sliderPos.toDouble()) },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f).height(24.dp),
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "${sliderPos.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        // Quick Presets Row & Read
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(0, 25, 50, 75, 100).forEach { pct ->
                                    FilledTonalButton(
                                        onClick = { controller.writeGroupPercent(dimText, pct.toDouble()) },
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp),
                                    ) {
                                        Text("$pct%", fontSize = 8.5.sp)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { controller.readGroupAddress(dimText) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(22.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Read", fontSize = 8.5.sp)
                            }
                        }
                    }
                }
            }

            // GAs inside this function with full ETS Inline Control Bars
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (gaAddr in function.groupAddresses) {
                    val ga = project.findGroupAddress(gaAddr) ?: GroupAddressInfo(gaAddr, gaAddr.toString())
                    val gaText = ga.address.toString()
                    val liveVal = state.groupLiveValues[ga.address]?.valueText

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onInspectGa(ga) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
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
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = gaText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            fontSize = 10.sp,
                                        )
                                    }

                                    ga.dpt?.let { dpt ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            Text(
                                                text = dpt,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                            )
                                        }
                                    }

                                    Text(
                                        text = ga.name.ifBlank { gaText },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (liveVal != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = liveVal ?: "—",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (liveVal != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }

                            // Dedicated ETS Inline Control Bar for each Group Address
                            EtsGaInlineControlBar(
                                ga = ga,
                                liveValue = liveVal,
                                controller = controller,
                            )
                        }
                    }
                }
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
    val directFuncs = space.functions.size
    val totalFuncs = remember(space) { space.collectAllFunctions().size }

    val icon = when {
        space.type.equals("Building", ignoreCase = true) || space.type.equals("BuildingPart", ignoreCase = true) ->
            Icons.Default.Apartment
        space.type.equals("Floor", ignoreCase = true) ->
            Icons.Default.Layers
        space.type.equals("Room", ignoreCase = true) ->
            Icons.Default.MeetingRoom
        space.type.contains("Distribution", ignoreCase = true) || space.name.contains("Distribution", ignoreCase = true) || space.name.contains("Cabinet", ignoreCase = true) || space.name.contains("Board", ignoreCase = true) || space.name.contains("Panel", ignoreCase = true) ->
            Icons.Default.Router
        space.type.contains("Corridor", ignoreCase = true) || space.name.contains("Corridor", ignoreCase = true) || space.name.contains("Hall", ignoreCase = true) ->
            Icons.AutoMirrored.Filled.DirectionsWalk
        space.type.contains("Stair", ignoreCase = true) || space.name.contains("Stair", ignoreCase = true) ->
            Icons.Default.Stairs
        space.type.contains("Outdoor", ignoreCase = true) || space.name.contains("Garden", ignoreCase = true) || space.name.contains("Terrace", ignoreCase = true) || space.name.contains("Pool", ignoreCase = true) ->
            Icons.Default.Park
        else ->
            if (subSpaces > 0) Icons.Default.Folder else Icons.Default.MeetingRoom
    }

    val autoSubtitle = when {
        subSpaces > 0 && totalFuncs > 0 -> "$subSpaces sub-spaces • $totalFuncs functions • $totalDevs devices"
        subSpaces > 0 -> "$subSpaces sub-spaces • $totalDevs devices"
        directFuncs > 0 && directDevs > 0 -> "$directFuncs functions • $directDevs devices"
        directFuncs > 0 -> "$directFuncs functions (${space.functions.sumOf { it.groupAddresses.size }} GAs)"
        directDevs > 0 -> "$directDevs devices"
        else -> "Empty space"
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

// ---------------------------------------------------------------------------
// Room Commissioning Cockpit Card (ETS Inside / ETS6 Raum-Bedienfeld)
// ---------------------------------------------------------------------------
@Composable
private fun MobileRoomCockpitCard(
    location: BuildingLocation,
    groupAddresses: List<GroupAddressInfo>,
    state: AppUiState,
    controller: AppController,
) {
    var expanded by remember { mutableStateOf(true) }
    var filterCategory by remember { mutableStateOf("ALL") }

    val switchGas = remember(groupAddresses) {
        groupAddresses.filter {
            DptCodec.resolveMainDpt(it.dpt) == MainDpt.DPT_1_BOOL &&
                !it.name.contains("Blind", ignoreCase = true) &&
                !it.name.contains("Jalousie", ignoreCase = true) &&
                !it.name.contains("Rollladen", ignoreCase = true) &&
                !it.name.contains("Status", ignoreCase = true) &&
                !it.name.contains("Rückmeldung", ignoreCase = true)
        }
    }

    val dimmerGas = remember(groupAddresses) {
        groupAddresses.filter {
            val d = DptCodec.resolveMainDpt(it.dpt)
            val nameL = it.name.lowercase()
            !nameL.contains("status") && !nameL.contains("rück") &&
                (d == MainDpt.DPT_5_PERCENT || d == MainDpt.DPT_5_UNSIGNED ||
                    nameL.contains("dimm") || nameL.contains("absolut") || nameL.contains("wert") || nameL.contains("bright") || nameL.contains("%"))
        }
    }

    val shutterGas = remember(groupAddresses) {
        groupAddresses.filter {
            it.name.contains("Blind", ignoreCase = true) ||
                it.name.contains("Jalousie", ignoreCase = true) ||
                it.name.contains("Rollladen", ignoreCase = true) ||
                it.name.contains("Louver", ignoreCase = true) ||
                it.name.contains("Shutter", ignoreCase = true)
        }
    }

    val climateGas = remember(groupAddresses) {
        groupAddresses.filter {
            DptCodec.resolveMainDpt(it.dpt) == MainDpt.DPT_9_FLOAT ||
                it.name.contains("Temp", ignoreCase = true) ||
                it.name.contains("Thermostat", ignoreCase = true) ||
                it.name.contains("Heizen", ignoreCase = true) ||
                it.name.contains("HVAC", ignoreCase = true)
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header Row with collapse/expand toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "ROOM COMMISSIONING COCKPIT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${location.name} • ${groupAddresses.size} functional circuits",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Batch Room Actions Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (switchGas.isNotEmpty()) {
                            Button(
                                onClick = { switchGas.forEach { controller.writeGroupSwitch(it.address.toString(), true) } },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("All ON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { switchGas.forEach { controller.writeGroupSwitch(it.address.toString(), false) } },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text("All OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (shutterGas.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { shutterGas.forEach { controller.writeGroupSwitch(it.address.toString(), false) } },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Blinds ▲", fontSize = 11.sp)
                            }
                        }
                    }

                    // Category Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item {
                            FilterChip(
                                selected = filterCategory == "ALL",
                                onClick = { filterCategory = "ALL" },
                                label = { Text("All (${groupAddresses.size})", fontSize = 10.sp) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(26.dp),
                            )
                        }
                        if (switchGas.isNotEmpty()) {
                            item {
                                FilterChip(
                                    selected = filterCategory == "LIGHTS",
                                    onClick = { filterCategory = "LIGHTS" },
                                    label = { Text("Lights (${switchGas.size})", fontSize = 10.sp) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp),
                                )
                            }
                        }
                        if (dimmerGas.isNotEmpty()) {
                            item {
                                FilterChip(
                                    selected = filterCategory == "DIMMERS",
                                    onClick = { filterCategory = "DIMMERS" },
                                    label = { Text("Dimmers (${dimmerGas.size})", fontSize = 10.sp) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp),
                                )
                            }
                        }
                        if (shutterGas.isNotEmpty()) {
                            item {
                                FilterChip(
                                    selected = filterCategory == "BLINDS",
                                    onClick = { filterCategory = "BLINDS" },
                                    label = { Text("Blinds (${shutterGas.size})", fontSize = 10.sp) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp),
                                )
                            }
                        }
                        if (climateGas.isNotEmpty()) {
                            item {
                                FilterChip(
                                    selected = filterCategory == "CLIMATE",
                                    onClick = { filterCategory = "CLIMATE" },
                                    label = { Text("Climate (${climateGas.size})", fontSize = 10.sp) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp),
                                )
                            }
                        }
                    }

                    val displayedGas = when (filterCategory) {
                        "LIGHTS" -> switchGas
                        "DIMMERS" -> dimmerGas
                        "BLINDS" -> shutterGas
                        "CLIMATE" -> climateGas
                        else -> groupAddresses
                    }

                    // Display up to 12 room functional circuit cards
                    displayedGas.take(12).forEach { ga ->
                        val gaText = ga.address.toString()
                        val liveVal = state.groupLiveValues[ga.address]
                        val isSwitch = switchGas.contains(ga)
                        val isDimmer = dimmerGas.contains(ga)
                        val isShutter = shutterGas.contains(ga)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = gaText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontSize = 9.sp,
                                            )
                                        }
                                        Text(
                                            text = ga.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    liveVal?.let { lv ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (lv.valueText.equals("ON", ignoreCase = true) || lv.valueText.contains("1"))
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        ) {
                                            Text(
                                                text = lv.valueText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (lv.valueText.equals("ON", ignoreCase = true) || lv.valueText.contains("1"))
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 9.5.sp,
                                            )
                                        }
                                    }
                                }

                                // Dedicated ETS Inline Control Bar (Switches, Dimmers, Blinds, HVAC, Read)
                                EtsGaInlineControlBar(
                                    ga = ga,
                                    liveValue = liveVal?.valueText,
                                    controller = controller,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun flattenGroupTree(
    ranges: List<GroupRangeNode>,
    project: KnxProject,
    state: AppUiState,
    expandedIds: Set<String>,
): List<GroupTreeNode> {
    val result = mutableListOf<GroupTreeNode>()

    for (mainRange in ranges) {
        val isExpanded = mainRange.id in expandedIds
        val allAddrs = mainRange.collectAllAddresses()
        result += GroupTreeNode.MainGroupNode(
            id = mainRange.id,
            depth = 0,
            range = mainRange,
            isExpanded = isExpanded,
            childCount = mainRange.children.size,
            totalGas = allAddrs.size,
        )

        if (isExpanded) {
            for (subRange in mainRange.children) {
                val isSubExpanded = subRange.id in expandedIds
                val subAddrs = subRange.collectAllAddresses()
                result += GroupTreeNode.MiddleGroupNode(
                    id = subRange.id,
                    depth = 1,
                    mainRange = mainRange,
                    subRange = subRange,
                    isExpanded = isSubExpanded,
                    childCount = subAddrs.size,
                    totalGas = subAddrs.size,
                )

                if (isSubExpanded) {
                    val gas = subAddrs.mapNotNull { project.findGroupAddress(it) }
                    for (ga in gas) {
                        result += GroupTreeNode.GroupLeafNode(
                            id = "${subRange.id}/${ga.address.raw}",
                            depth = 2,
                            ga = ga,
                            liveEntry = state.groupLiveValues[ga.address],
                        )
                    }
                }
            }
            for (addr in mainRange.directAddresses) {
                val ga = project.findGroupAddress(addr)
                if (ga != null) {
                    result += GroupTreeNode.GroupLeafNode(
                        id = "${mainRange.id}/${ga.address.raw}",
                        depth = 1,
                        ga = ga,
                        liveEntry = state.groupLiveValues[ga.address],
                    )
                }
            }
        }
    }

    return result
}

@Composable
private fun GroupTreeMainItem(
    node: GroupTreeNode.MainGroupNode,
    onToggle: () -> Unit,
    onPoll: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = node.range.prefixLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        text = node.range.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${node.childCount} middle groups • ${node.totalGas} GAs",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(
                    onClick = onPoll,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp),
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Poll", fontSize = 10.sp)
                }

                IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (node.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupTreeMiddleItem(
    node: GroupTreeNode.MiddleGroupNode,
    onToggle: () -> Unit,
    onPoll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "├── ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        OutlinedCard(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (node.isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f).clickable(onClick = onToggle),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(15.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = node.subRange.prefixLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            )
                        }
                        Text(
                            text = node.subRange.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${node.totalGas} group addresses",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onPoll,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Poll", fontSize = 9.5.sp)
                    }

                    IconButton(onClick = onToggle, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = if (node.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTreeLeafItem(
    node: GroupTreeNode.GroupLeafNode,
    controller: AppController,
    onInspect: () -> Unit,
) {
    val ga = node.ga
    val gaText = ga.address.toString()
    val isSwitch = DptCodec.resolveMainDpt(ga.dpt) == MainDpt.DPT_1_BOOL ||
        ga.name.contains("Schalten", ignoreCase = true) ||
        ga.name.contains("Switch", ignoreCase = true)

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "│   └── ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        OutlinedCard(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f).clickable(onClick = onInspect),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = gaText,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }

                        ga.dpt?.let { dpt ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = dpt,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                )
                            }
                        }

                        if (ga.isSecure) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF047857),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(Icons.Default.Shield, contentDescription = "KNX Secure", tint = Color.White, modifier = Modifier.size(8.dp))
                                    Text(
                                        text = "SECURE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        Text(
                            text = ga.name.ifBlank { "Group Address" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Live Value Badge
                    val liveVal = node.liveEntry?.valueText
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (liveVal != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = liveVal ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (liveVal != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }

                // Inline Test Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSwitch) {
                        FilledTonalButton(
                            onClick = { controller.writeGroupSwitch(gaText, false) },
                            shape = RoundedCornerShape(5.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                            modifier = Modifier.height(22.dp),
                        ) {
                            Text("OFF", fontSize = 9.sp)
                        }
                        Spacer(Modifier.width(3.dp))
                        Button(
                            onClick = { controller.writeGroupSwitch(gaText, true) },
                            shape = RoundedCornerShape(5.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                            modifier = Modifier.height(22.dp),
                        ) {
                            Text("ON", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(3.dp))
                    OutlinedButton(
                        onClick = { controller.readGroupAddress(gaText) },
                        shape = RoundedCornerShape(5.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(22.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Read", fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun flattenTopologyTree(
    areas: List<TopologyArea>,
    project: KnxProject,
    state: AppUiState,
    expandedIds: Set<String>,
): List<TopologyTreeNode> {
    val result = mutableListOf<TopologyTreeNode>()
    for (area in areas) {
        val areaId = "area-${area.area}"
        val isAreaExpanded = areaId in expandedIds
        val lineCount = area.lines.size
        val totalDevices = area.lines.sumOf { it.devices.size }
        result += TopologyTreeNode.AreaNode(
            id = areaId,
            depth = 0,
            area = area,
            isExpanded = isAreaExpanded,
            lineCount = lineCount,
            totalDevices = totalDevices,
        )

        if (isAreaExpanded) {
            for (line in area.lines) {
                val lineId = "line-${line.id}"
                val isLineExpanded = lineId in expandedIds
                val couplerAddr = IndividualAddress(line.area, line.line, 0)
                val couplerReachable = state.deviceStatuses[couplerAddr]?.reachable

                result += TopologyTreeNode.LineNode(
                    id = lineId,
                    depth = 1,
                    area = area,
                    line = line,
                    isExpanded = isLineExpanded,
                    deviceCount = line.devices.size,
                    couplerAddress = couplerAddr,
                    couplerReachable = couplerReachable,
                )

                if (isLineExpanded) {
                    for (dev in line.devices) {
                        val devId = "dev-${dev.address.raw}"
                        val isOnline = state.deviceStatuses[dev.address]?.reachable
                        val isProg = dev.address in state.programmingModeDevices
                        result += TopologyTreeNode.DeviceNode(
                            id = devId,
                            depth = 2,
                            device = dev,
                            line = line,
                            isOnline = isOnline,
                            isProgMode = isProg,
                        )
                    }
                }
            }
        }
    }
    return result
}

@Composable
private fun TopologyTreeAreaItem(
    node: TopologyTreeNode.AreaNode,
    onToggle: () -> Unit,
    onSelectArea: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = "Area ${node.area.area}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }

                    Text(
                        text = node.area.name.ifBlank { "Backbone / Area" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = "${node.lineCount} lines • ${node.totalDevices} devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onSelectArea,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Drill into area",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
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
}

@Composable
private fun TopologyTreeLineItem(
    node: TopologyTreeNode.LineNode,
    onToggle: () -> Unit,
    onSelectLine: () -> Unit,
    onAuditLine: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "├── ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        OutlinedCard(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (node.isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f).clickable(onClick = onToggle),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary,
                        ) {
                            Text(
                                text = "Line ${node.line.id}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = node.line.mediumType,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            )
                        }

                        Text(
                            text = node.line.name.ifBlank { "Line ${node.line.id}" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${node.deviceCount} devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp,
                        )
                        node.couplerReachable?.let { online ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            )
                            Text(
                                text = if (online) "Coupler online" else "Coupler offline",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FilledTonalButton(
                        onClick = onAuditLine,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp),
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Audit", fontSize = 9.5.sp)
                    }

                    IconButton(
                        onClick = onSelectLine,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Drill into line",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    Icon(
                        imageVector = if (node.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopologyTreeDeviceItem(
    node: TopologyTreeNode.DeviceNode,
    project: KnxProject,
    onOpenObjects: () -> Unit,
    onPing: () -> Unit,
    onLocate: () -> Unit,
) {
    val dev = node.device
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "│   └── ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        OutlinedCard(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f).clickable(onClick = onOpenObjects),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = dev.address.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }

                        if (node.isProgMode) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = "PROG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        Text(
                            text = dev.name.ifBlank { dev.productName.ifBlank { "Device ${dev.address}" } },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Online Status Dot
                    node.isOnline?.let { online ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (online) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = if (online) "ONLINE" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (online) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                // Subtitle: Product name / Order number + Communication Objects count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${dev.productName.ifBlank { dev.orderNumber }} • ${dev.comObjects.size} objects",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onPing,
                            shape = RoundedCornerShape(5.dp),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                            modifier = Modifier.height(22.dp),
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Ping", fontSize = 9.sp)
                        }

                        FilledTonalButton(
                            onClick = onLocate,
                            shape = RoundedCornerShape(5.dp),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                            modifier = Modifier.height(22.dp),
                        ) {
                            Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Flash LED", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}
