package dev.knxfieldkit.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dev.knxfieldkit.model.*
import java.io.File

private sealed interface FlatTreeRow {
    val key: String
    val depth: Int
    val label: String
    val prefixBadge: String?
    val countBadge: String?
    val icon: ImageVector
    val iconTint: Color?
    val statusDot: Color?
    val expandable: Boolean
    val expanded: Boolean
    val selected: Boolean
    val onSelect: () -> Unit
    val onToggle: (() -> Unit)?
}

private data class SimpleTreeRow(
    override val key: String,
    override val depth: Int,
    override val label: String,
    override val prefixBadge: String? = null,
    override val countBadge: String? = null,
    override val icon: ImageVector,
    override val iconTint: Color? = null,
    override val statusDot: Color? = null,
    override val expandable: Boolean = false,
    override val expanded: Boolean = false,
    override val selected: Boolean = false,
    override val onSelect: () -> Unit,
    override val onToggle: (() -> Unit)? = null,
) : FlatTreeRow

@Composable
fun ProjectExplorerScreen(
    state: AppUiState,
    controller: AppController,
    onPickFile: () -> Unit,
    onExportReport: () -> Unit,
) {
    val proj = state.project
    var treeSearch by remember { mutableStateOf("") }
    var gridSearch by remember { mutableStateOf("") }

    // Expanded folder state initialized so Topology & Group Ranges open nicely like ETS6
    val expandedNodes = remember(proj?.projectId) {
        mutableStateMapOf<String, Boolean>().apply {
            put("topo-root", true)
            proj?.areas?.forEach { area ->
                put("area-${area.area}", true)
                area.lines.forEach { ln ->
                    put("line-${ln.id}", false)
                }
            }
            put("gr-root", true)
            proj?.effectiveGroupRanges?.forEach { gr ->
                put("gr-${gr.id}", true)
            }
            put("loc-root", true)
            proj?.locations?.forEachIndexed { idx, loc ->
                put("loc-$idx-${loc.name}", true)
            }
        }
    }

    fun isExpanded(id: String, default: Boolean = false): Boolean = expandedNodes[id] ?: default
    fun toggleExpand(id: String, default: Boolean = false) {
        expandedNodes[id] = !(expandedNodes[id] ?: default)
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 1. Sleek ETS6 Project Context & Workspace Ribbon Bar (32dp)
        Surface(
            color = WorkbenchColors.BgHeader,
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(1.dp, WorkbenchColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth().height(34.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (proj?.passwordProtected == true) Icons.Default.LockOpen else Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = if (proj?.passwordProtected == true) WorkbenchColors.AccentAmber else WorkbenchColors.AccentSky,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = proj?.name ?: "No Project Loaded",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WorkbenchColors.TextPrimary,
                    maxLines = 1,
                )
                if (proj != null) {
                    StatusPill(
                        text = "${proj.projectId} • ${proj.etsVersion.ifBlank { "ETS" }} • ${proj.allDevices.size} Dev • ${proj.groupAddresses.size} GA",
                        color = if (proj.passwordProtected) WorkbenchColors.AccentAmber else WorkbenchColors.AccentSky,
                    )
                }
                state.keyring?.let { kr ->
                    StatusPill(
                        text = "Keyring: ${kr.groupKeysHex.size} Keys",
                        color = WorkbenchColors.AccentEmerald,
                    )
                }

                DesktopPrimaryButton(
                    text = "Open .knxproj / .knxkeys",
                    icon = Icons.Default.FileOpen,
                    onClick = onPickFile,
                    color = WorkbenchColors.AccentSky,
                    compact = true,
                )

                state.recentFiles.take(2).forEach { rf ->
                    DesktopButton(
                        text = rf.name.take(24),
                        icon = Icons.Default.History,
                        onClick = { controller.openProjectOrKeyringFile(rf) },
                        compact = true,
                    )
                }

                Spacer(Modifier.weight(1f))

                DesktopButton(
                    text = if (state.isPollingBatch) "Polling..." else "Poll All GAs",
                    icon = Icons.Default.Sync,
                    onClick = controller::pollAllProjectGroupAddresses,
                    enabled = proj != null && !state.isPollingBatch,
                    compact = true,
                )
                DesktopButton(
                    text = "Export MD",
                    icon = Icons.Default.Description,
                    onClick = onExportReport,
                    enabled = proj != null,
                    compact = true,
                )
                DesktopButton(
                    text = "Monitor",
                    icon = Icons.Default.Terminal,
                    onClick = controller::toggleExplorerBottomMonitor,
                    active = state.showExplorerBottomMonitor,
                    compact = true,
                )
                DesktopButton(
                    text = "Properties",
                    icon = Icons.Default.Tune,
                    onClick = controller::toggleExplorerRightInspector,
                    active = state.showExplorerRightInspector,
                    compact = true,
                )
            }
        }

        if (proj == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Drop any .knxproj or .knxkeys file onto the window to begin.", color = WorkbenchColors.TextSecondary)
            }
            return@Column
        }

        // 2. Main ETS Multi-Pane Workspace (Left Tree + Center Data Grid + Right Inspector)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // LEFT PANE: Hierarchical Collapsible ETS Tree
            WorkbenchPanel(
                title = "Project Navigator",
                icon = Icons.Default.AccountTree,
                modifier = Modifier.width(292.dp).fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val modeOptions = listOf(
                        "Split" to Icons.Default.VerticalSplit,
                        "Topo" to Icons.Default.Hub,
                        "Groups" to Icons.Default.Folder,
                        "Rooms" to Icons.Default.Apartment,
                    )
                    SegmentedTabs(
                        options = modeOptions,
                        selectedIndex = state.explorerTreeMode.ordinal,
                        onSelect = { idx -> controller.setExplorerTreeMode(ExplorerTreeMode.entries[idx]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompactSearchField(
                        value = treeSearch,
                        onValueChange = { treeSearch = it },
                        placeholder = "Filter tree nodes...",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle)

                val currentColors = LocalWorkbenchColors.current
                val topoRows = buildTopologyTreeRows(
                    proj = proj,
                    state = state,
                    controller = controller,
                    filter = treeSearch,
                    colors = currentColors,
                    isExpanded = ::isExpanded,
                    onToggle = ::toggleExpand,
                )
                val groupRows = buildGroupRangeTreeRows(
                    proj = proj,
                    state = state,
                    controller = controller,
                    filter = treeSearch,
                    colors = currentColors,
                    isExpanded = ::isExpanded,
                    onToggle = ::toggleExpand,
                )
                val buildingRows = buildBuildingTreeRows(
                    proj = proj,
                    state = state,
                    controller = controller,
                    filter = treeSearch,
                    colors = currentColors,
                    isExpanded = ::isExpanded,
                    onToggle = ::toggleExpand,
                )

                when (state.explorerTreeMode) {
                    ExplorerTreeMode.DUAL_SPLIT -> {
                        Column(Modifier.fillMaxSize()) {
                            TreeSectionSubheader("TOPOLOGY (AREAS & LINES)", "${proj.allDevices.size} dev")
                            LazyColumn(modifier = Modifier.weight(0.48f).fillMaxWidth()) {
                                items(topoRows, key = { it.key }) { row -> RenderTreeRow(row) }
                            }
                            HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle)
                            TreeSectionSubheader("GROUP ADDRESSES (3-LEVEL)", "${proj.groupAddresses.size} GA")
                            LazyColumn(modifier = Modifier.weight(0.52f).fillMaxWidth()) {
                                items(groupRows, key = { it.key }) { row -> RenderTreeRow(row) }
                            }
                        }
                    }
                    ExplorerTreeMode.TOPOLOGY -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(topoRows, key = { it.key }) { row -> RenderTreeRow(row) }
                        }
                    }
                    ExplorerTreeMode.GROUP_ADDRESSES -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(groupRows, key = { it.key }) { row -> RenderTreeRow(row) }
                        }
                    }
                    ExplorerTreeMode.BUILDING -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(buildingRows, key = { it.key }) { row -> RenderTreeRow(row) }
                        }
                    }
                }
            }

            // CENTER PANE: Context-Sensitive ETS Tabular Data Grid
            ExplorerCenterDataGrid(
                proj = proj,
                state = state,
                controller = controller,
                gridSearch = gridSearch,
                onGridSearchChange = { gridSearch = it },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )

            // RIGHT PANE: Collapsible Properties & Cross-Reference Inspector
            if (state.showExplorerRightInspector) {
                ExplorerRightInspectorPane(
                    state = state,
                    controller = controller,
                    modifier = Modifier.width(296.dp).fillMaxHeight(),
                )
            }
        }

        // 3. Bottom-Docked Live Group & Bus Monitor (Collapsible)
        if (state.showExplorerBottomMonitor) {
            ExplorerDockedMonitorPane(
                state = state,
                controller = controller,
                modifier = Modifier.fillMaxWidth().height(178.dp),
            )
        }
    }
}

@Composable
private fun TreeSectionSubheader(title: String, rightText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(21.dp)
            .background(WorkbenchColors.BgPanelAlt)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WorkbenchColors.TextMuted)
        Text(rightText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = WorkbenchColors.AccentSky)
    }
}

@Composable
private fun RenderTreeRow(row: FlatTreeRow) {
    TreeRowItem(
        label = row.label,
        depth = row.depth,
        selected = row.selected,
        onClick = row.onSelect,
        icon = row.icon,
        iconTint = row.iconTint,
        prefixBadge = row.prefixBadge,
        countBadge = row.countBadge,
        statusDotColor = row.statusDot,
        expandable = row.expandable,
        expanded = row.expanded,
        onToggleExpand = row.onToggle,
    )
}

private fun buildTopologyTreeRows(
    proj: KnxProject,
    state: AppUiState,
    controller: AppController,
    filter: String,
    colors: WorkbenchColorScheme,
    isExpanded: (String, Boolean) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
): List<FlatTreeRow> {
    val q = filter.trim().lowercase()
    val rows = mutableListOf<FlatTreeRow>()
    val rootExp = isExpanded("topo-root", true) || q.isNotEmpty()
    rows += SimpleTreeRow(
        key = "topo-root",
        depth = 0,
        label = "Backbone Topology",
        countBadge = proj.allDevices.size.toString(),
        icon = Icons.Default.Hub,
        iconTint = colors.accentSky,
        expandable = true,
        expanded = rootExp,
        selected = state.explorerSelection == ExplorerSelection.TopologyRoot,
        onSelect = { controller.selectExplorerNode(ExplorerSelection.TopologyRoot) },
        onToggle = { onToggle("topo-root", true) },
    )
    if (!rootExp) return rows

    for (area in proj.areas) {
        val areaKey = "area-${area.area}"
        val areaDevs = area.lines.sumOf { it.devices.size }
        val areaExp = isExpanded(areaKey, true) || q.isNotEmpty()
        rows += SimpleTreeRow(
            key = areaKey,
            depth = 1,
            prefixBadge = "${area.area}",
            label = area.name,
            countBadge = areaDevs.toString(),
            icon = Icons.Default.Dns,
            iconTint = colors.accentTeal,
            expandable = area.lines.isNotEmpty(),
            expanded = areaExp,
            selected = state.explorerSelection == ExplorerSelection.Area(area.area),
            onSelect = { controller.selectExplorerNode(ExplorerSelection.Area(area.area)) },
            onToggle = { onToggle(areaKey, true) },
        )
        if (!areaExp) continue

        for (line in area.lines) {
            val lineKey = "line-${line.id}"
            val lineExp = isExpanded(lineKey, false) || q.isNotEmpty()
            val matchingDevs = line.devices.filter {
                q.isEmpty() ||
                    it.address.toString().contains(q) ||
                    it.name.lowercase().contains(q) ||
                    it.productName.lowercase().contains(q)
            }
            if (q.isNotEmpty() && matchingDevs.isEmpty() && !line.name.lowercase().contains(q)) continue

            val anyOffline = line.devices.any { state.deviceStatuses[it.address]?.reachable == false }
            val anyOnline = line.devices.any { state.deviceStatuses[it.address]?.reachable == true }
            val lineDot = when {
                anyOffline -> colors.accentRed
                anyOnline -> colors.accentEmerald
                else -> null
            }

            rows += SimpleTreeRow(
                key = lineKey,
                depth = 2,
                prefixBadge = line.id,
                label = line.name,
                countBadge = line.devices.size.toString(),
                icon = Icons.Default.AccountTree,
                iconTint = colors.accentSky,
                statusDot = lineDot,
                expandable = line.devices.isNotEmpty(),
                expanded = lineExp,
                selected = state.explorerSelection == ExplorerSelection.Line(area.area, line.line),
                onSelect = { controller.selectExplorerNode(ExplorerSelection.Line(area.area, line.line)) },
                onToggle = { onToggle(lineKey, false) },
            )
            if (lineExp) {
                for (dev in matchingDevs) {
                    val st = state.deviceStatuses[dev.address]
                    val dot = when {
                        st == null -> null
                        st.reachable -> colors.accentEmerald
                        else -> colors.accentRed
                    }
                    rows += SimpleTreeRow(
                        key = "dev-${dev.address}",
                        depth = 3,
                        prefixBadge = dev.address.toString(),
                        label = dev.name,
                        countBadge = if (dev.comObjects.isNotEmpty()) "${dev.comObjects.size}co" else null,
                        icon = if (dev.address.device == 0) Icons.Default.Router else Icons.Default.Memory,
                        iconTint = if (dev.address.device == 0) colors.accentAmber else colors.textSecondary,
                        statusDot = dot,
                        expandable = false,
                        selected = state.explorerSelection == ExplorerSelection.Device(dev.address),
                        onSelect = { controller.openDeviceObjectsInExplorer(dev.address) },
                    )
                }
            }
        }
    }
    return rows
}

private fun buildGroupRangeTreeRows(
    proj: KnxProject,
    state: AppUiState,
    controller: AppController,
    filter: String,
    colors: WorkbenchColorScheme,
    isExpanded: (String, Boolean) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
): List<FlatTreeRow> {
    val q = filter.trim().lowercase()
    val rows = mutableListOf<FlatTreeRow>()
    val rootExp = isExpanded("gr-root", true) || q.isNotEmpty()
    rows += SimpleTreeRow(
        key = "gr-root",
        depth = 0,
        label = "All Group Addresses",
        countBadge = proj.groupAddresses.size.toString(),
        icon = Icons.Default.FolderSpecial,
        iconTint = colors.accentEmerald,
        expandable = true,
        expanded = rootExp,
        selected = state.explorerSelection == ExplorerSelection.AllGroupAddresses,
        onSelect = { controller.selectExplorerNode(ExplorerSelection.AllGroupAddresses) },
        onToggle = { onToggle("gr-root", true) },
    )
    if (!rootExp) return rows

    fun addRange(node: GroupRangeNode, depth: Int) {
        val matchesSelf = q.isEmpty() ||
            node.name.lowercase().contains(q) ||
            node.prefixLabel.lowercase().contains(q) ||
            node.collectAllAddresses().any { ga ->
                ga.toString().contains(q) || (proj.findGroupAddress(ga)?.name?.lowercase()?.contains(q) == true)
            }
        if (!matchesSelf) return

        val key = "gr-${node.id}"
        val exp = isExpanded(key, depth <= 1) || q.isNotEmpty()
        val hasChildren = node.children.isNotEmpty()
        rows += SimpleTreeRow(
            key = key,
            depth = depth,
            prefixBadge = node.prefixLabel.takeIf { it.isNotBlank() },
            label = node.name,
            countBadge = node.totalCount.toString(),
            icon = if (hasChildren && exp) Icons.Default.FolderOpen else Icons.Default.Folder,
            iconTint = if (depth == 1) colors.accentAmber else colors.accentSky,
            expandable = hasChildren,
            expanded = exp,
            selected = state.explorerSelection == ExplorerSelection.GroupRange(node.id),
            onSelect = { controller.selectExplorerNode(ExplorerSelection.GroupRange(node.id)) },
            onToggle = if (hasChildren) ({ onToggle(key, depth <= 1) }) else null,
        )
        if (hasChildren && exp) {
            node.children.forEach { child -> addRange(child, depth + 1) }
        }
    }

    proj.effectiveGroupRanges.forEach { addRange(it, 1) }
    return rows
}

private fun buildBuildingTreeRows(
    proj: KnxProject,
    state: AppUiState,
    controller: AppController,
    filter: String,
    colors: WorkbenchColorScheme,
    isExpanded: (String, Boolean) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
): List<FlatTreeRow> {
    val q = filter.trim().lowercase()
    val rows = mutableListOf<FlatTreeRow>()
    if (proj.locations.isEmpty()) {
        rows += SimpleTreeRow(
            key = "loc-empty",
            depth = 0,
            label = "No Building Locations in Project",
            icon = Icons.Default.Apartment,
            iconTint = colors.textMuted,
            onSelect = { controller.selectExplorerNode(ExplorerSelection.TopologyRoot) },
        )
        return rows
    }

    fun addLoc(loc: BuildingLocation, depth: Int, pathPrefix: String, idx: Int) {
        val fullPath = if (pathPrefix.isEmpty()) loc.name else "$pathPrefix › ${loc.name}"
        val allDevs = loc.collectAllDeviceAddresses()
        if (q.isNotEmpty() && !loc.name.lowercase().contains(q) && allDevs.none { it.toString().contains(q) }) return

        val key = "loc-$depth-$idx-${loc.name}"
        val hasNested = loc.children.isNotEmpty() || loc.deviceAddresses.isNotEmpty()
        val exp = isExpanded(key, depth == 0) || q.isNotEmpty()
        val locIcon = when (loc.type.lowercase()) {
            "floor" -> Icons.Default.Layers
            "cabinet" -> Icons.Default.Dns
            "outdoor", "zone" -> Icons.Default.WbSunny
            else -> Icons.Default.MeetingRoom
        }
        val selObj = ExplorerSelection.BuildingSpace(fullPath, loc.name, loc.type, allDevs)
        rows += SimpleTreeRow(
            key = key,
            depth = depth,
            label = loc.name,
            countBadge = allDevs.size.toString(),
            icon = locIcon,
            iconTint = if (depth == 0) colors.accentAmber else colors.accentSky,
            expandable = hasNested,
            expanded = exp,
            selected = (state.explorerSelection as? ExplorerSelection.BuildingSpace)?.path == fullPath,
            onSelect = { controller.selectExplorerNode(selObj) },
            onToggle = if (hasNested) ({ onToggle(key, depth == 0) }) else null,
        )
        if (exp) {
            loc.children.forEachIndexed { cIdx, child -> addLoc(child, depth + 1, fullPath, cIdx) }
            loc.deviceAddresses.mapNotNull { proj.findDevice(it) }.forEach { dev ->
                rows += SimpleTreeRow(
                    key = "loc-dev-$fullPath-${dev.address}",
                    depth = depth + 1,
                    prefixBadge = dev.address.toString(),
                    label = dev.name,
                    countBadge = "${dev.comObjects.size}co",
                    icon = Icons.Default.Memory,
                    iconTint = colors.textSecondary,
                    selected = state.explorerSelection == ExplorerSelection.Device(dev.address),
                    onSelect = { controller.openDeviceObjectsInExplorer(dev.address) },
                )
            }
        }
    }

    proj.locations.forEachIndexed { idx, root -> addLoc(root, 0, "", idx) }
    return rows
}

@Composable
private fun ExplorerCenterDataGrid(
    proj: KnxProject,
    state: AppUiState,
    controller: AppController,
    gridSearch: String,
    onGridSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sel = state.explorerSelection
    when (sel) {
        is ExplorerSelection.Device -> {
            val dev = proj.findDevice(sel.address)
            if (dev != null) {
                DeviceComObjectsGridPanel(
                    proj = proj,
                    dev = dev,
                    state = state,
                    controller = controller,
                    search = gridSearch,
                    onSearchChange = onGridSearchChange,
                    modifier = modifier,
                )
                return
            }
        }
        is ExplorerSelection.AllGroupAddresses -> {
            val gas = proj.groupAddresses.values.sortedBy { it.address }
            GroupAddressesGridPanel(
                breadcrumb = "Group Addresses › All (${gas.size})",
                items = gas,
                state = state,
                controller = controller,
                search = gridSearch,
                onSearchChange = onGridSearchChange,
                modifier = modifier,
            )
            return
        }
        is ExplorerSelection.GroupRange -> {
            val rangeNode = findGroupRangeById(proj.effectiveGroupRanges, sel.rangeId)
            val gas = rangeNode?.collectAllAddresses()?.distinct()?.mapNotNull { proj.findGroupAddress(it) }?.sortedBy { it.address }
                ?: proj.groupAddresses.values.sortedBy { it.address }
            val title = if (rangeNode != null) {
                "Group Addresses › ${rangeNode.prefixLabel} ${rangeNode.name}".trim()
            } else {
                "Group Addresses"
            }
            GroupAddressesGridPanel(
                breadcrumb = title,
                items = gas,
                state = state,
                controller = controller,
                search = gridSearch,
                onSearchChange = onGridSearchChange,
                modifier = modifier,
            )
            return
        }
        else -> Unit
    }

    // Default: ETS Devices Data Grid (TopologyRoot, Area, Line, BuildingSpace)
    val (breadcrumb, devices, auditLineTarget) = when (sel) {
        is ExplorerSelection.Area -> {
            val area = proj.areas.firstOrNull { it.area == sel.area }
            Triple(
                "Topology › Area ${sel.area} — ${area?.name.orEmpty()}",
                area?.lines?.flatMap { it.devices }.orEmpty(),
                area?.lines?.firstOrNull(),
            )
        }
        is ExplorerSelection.Line -> {
            val ln = proj.findLine(sel.area, sel.line)
            Triple(
                "Topology › Line ${sel.area}.${sel.line} — ${ln?.name.orEmpty()}",
                ln?.devices.orEmpty(),
                ln,
            )
        }
        is ExplorerSelection.BuildingSpace -> {
            Triple(
                "Building › ${sel.path}",
                sel.deviceAddresses.mapNotNull { proj.findDevice(it) },
                null,
            )
        }
        else -> {
            Triple(
                "Topology › All Project Devices",
                proj.allDevices.values.sortedBy { it.address },
                proj.allLines.firstOrNull(),
            )
        }
    }

    val q = gridSearch.trim().lowercase()
    val filteredDevices = remember(devices, q) {
        if (q.isEmpty()) devices else devices.filter {
            it.address.toString().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.manufacturerName.lowercase().contains(q) ||
                it.productName.lowercase().contains(q) ||
                it.orderNumber.lowercase().contains(q)
        }
    }

    WorkbenchPanel(
        title = breadcrumb,
        icon = Icons.Default.Memory,
        badgeText = "${filteredDevices.size} devices",
        modifier = modifier,
        headerActions = {
            if (auditLineTarget != null) {
                DesktopPrimaryButton(
                    text = if (state.isAuditingLine) "Auditing..." else "Audit Line ${auditLineTarget.id}",
                    icon = Icons.AutoMirrored.Filled.FactCheck,
                    onClick = { controller.runProjectLineAudit(auditLineTarget.area, auditLineTarget.line) },
                    enabled = !state.isAuditingLine,
                    compact = true,
                )
            }
            DesktopButton(
                text = "View All Group Addresses",
                icon = Icons.Default.Folder,
                onClick = { controller.selectExplorerNode(ExplorerSelection.AllGroupAddresses) },
                compact = true,
            )
            CompactSearchField(
                value = gridSearch,
                onValueChange = onGridSearchChange,
                placeholder = "Filter devices...",
                modifier = Modifier.width(165.dp),
            )
        },
    ) {
        DenseTableHeader(
            listOf(
                "Address" to 0.10f,
                "Device Name" to 0.25f,
                "Location / Room" to 0.18f,
                "Manufacturer & Product" to 0.21f,
                "Mask" to 0.06f,
                "Status" to 0.08f,
                "Actions" to 0.12f,
            )
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(filteredDevices, key = { _, d -> d.address.raw }) { idx, dev ->
                val isSelected = state.selectedDeviceAddress == dev.address && state.selectedInspectorGroup == null
                val rowBg = when {
                    isSelected -> WorkbenchColors.BgSelected
                    idx % 2 == 1 -> WorkbenchColors.BgRowAlt
                    else -> WorkbenchColors.BgPanel
                }
                val pingRes = state.deviceStatuses[dev.address]
                val roomPath = proj.findDeviceLocationPath(dev.address)?.substringAfterLast("›")?.trim() ?: "—"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(31.dp)
                        .background(rowBg)
                        .clickable { controller.inspectDeviceInExplorer(dev.address) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dev.address.toString(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = WorkbenchColors.AccentSky,
                        modifier = Modifier.weight(0.10f),
                    )
                    Row(
                        modifier = Modifier.weight(0.25f).padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = if (dev.address.device == 0) Icons.Default.Router else Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (dev.address.device == 0) WorkbenchColors.AccentAmber else WorkbenchColors.TextSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = dev.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WorkbenchColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = roomPath,
                        fontSize = 11.sp,
                        color = WorkbenchColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.18f).padding(end = 6.dp),
                    )
                    Text(
                        text = listOf(dev.manufacturerName, dev.productName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
                        fontSize = 11.sp,
                        color = WorkbenchColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.21f).padding(end = 6.dp),
                    )
                    Text(
                        text = dev.maskVersion ?: "—",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = WorkbenchColors.TextMuted,
                        modifier = Modifier.weight(0.06f),
                    )
                    Box(modifier = Modifier.weight(0.08f)) {
                        when {
                            pingRes == null -> Text("—", fontSize = 10.sp, color = WorkbenchColors.TextMuted)
                            pingRes.reachable -> StatusPill("${pingRes.latencyMs}ms", WorkbenchColors.AccentEmerald)
                            else -> StatusPill("FAULT", WorkbenchColors.AccentRed)
                        }
                    }
                    Row(
                        modifier = Modifier.weight(0.12f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopButton(
                            text = "Objs (${dev.comObjects.size})",
                            onClick = { controller.openDeviceObjectsInExplorer(dev.address) },
                            active = dev.comObjects.isNotEmpty(),
                            compact = true,
                        )
                        DesktopButton(
                            text = "Ping",
                            onClick = { controller.pingIndividualDevice(dev.address.toString()) },
                            compact = true,
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun DeviceComObjectsGridPanel(
    proj: KnxProject,
    dev: ProjectDevice,
    state: AppUiState,
    controller: AppController,
    search: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = search.trim().lowercase()
    val objects = remember(dev, q) {
        if (q.isEmpty()) dev.comObjects else dev.comObjects.filter { co ->
            co.name.lowercase().contains(q) ||
                co.functionText.lowercase().contains(q) ||
                co.linkedGroupAddresses.any { it.toString().contains(q) }
        }
    }

    WorkbenchPanel(
        title = "Device ${dev.address} — ${dev.name} › Group Objects",
        icon = Icons.Default.Schema,
        badgeText = "${objects.size} objects",
        modifier = modifier,
        headerActions = {
            DesktopButton(
                text = "← Line ${dev.address.area}.${dev.address.line}",
                onClick = { controller.selectExplorerNode(ExplorerSelection.Line(dev.address.area, dev.address.line)) },
                compact = true,
            )
            DesktopPrimaryButton(
                text = "Ping DD0",
                icon = Icons.Default.NetworkCheck,
                onClick = { controller.pingIndividualDevice(dev.address.toString()) },
                compact = true,
            )
            DesktopButton(
                text = "Restart",
                icon = Icons.Default.RestartAlt,
                onClick = { controller.restartRemoteDevice(dev.address.toString()) },
                compact = true,
            )
            CompactSearchField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = "Filter objects...",
                modifier = Modifier.width(150.dp),
            )
        },
    ) {
        DenseTableHeader(
            listOf(
                "Obj #" to 0.06f,
                "Object Name" to 0.19f,
                "Function" to 0.15f,
                "Len" to 0.07f,
                "Flags (C R W T U)" to 0.14f,
                "DPT" to 0.07f,
                "Assigned GAs" to 0.11f,
                "Live" to 0.11f,
                "Test" to 0.10f,
            )
        )
        if (objects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ComObject instances linked on ${dev.address}.", fontSize = 12.sp, color = WorkbenchColors.TextMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(objects, key = { idx, co -> "${co.id}-$idx" }) { idx, co ->
                    val primaryGa = co.linkedGroupAddresses.firstOrNull()
                    val live = primaryGa?.let { state.groupLiveValues[it] }
                    val rowBg = if (idx % 2 == 1) WorkbenchColors.BgRowAlt else WorkbenchColors.BgPanel

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(rowBg)
                            .clickable { primaryGa?.let(controller::inspectGroupInExplorer) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#${co.number}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = WorkbenchColors.AccentAmber,
                            modifier = Modifier.weight(0.06f),
                        )
                        Text(
                            text = co.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WorkbenchColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.19f).padding(end = 6.dp),
                        )
                        Text(
                            text = co.functionText.ifBlank { "—" },
                            fontSize = 11.sp,
                            color = WorkbenchColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.15f).padding(end = 6.dp),
                        )
                        Text(
                            text = co.sizeLabel,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = WorkbenchColors.TextMuted,
                            modifier = Modifier.weight(0.07f),
                        )
                        Box(modifier = Modifier.weight(0.14f)) {
                            ComFlagsBadges(co.flags)
                        }
                        Box(modifier = Modifier.weight(0.07f)) {
                            DptBadge(co.dpt)
                        }
                        Row(
                            modifier = Modifier.weight(0.11f).padding(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (co.linkedGroupAddresses.isEmpty()) {
                                Text("Unlinked", fontSize = 10.sp, color = WorkbenchColors.TextMuted)
                            } else {
                                co.linkedGroupAddresses.take(2).forEach { ga ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(WorkbenchColors.AccentSky.copy(alpha = 0.16f))
                                            .border(1.dp, WorkbenchColors.AccentSky.copy(alpha = 0.40f), RoundedCornerShape(3.dp))
                                            .clickable { controller.inspectGroupInExplorer(ga) }
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(ga.toString(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WorkbenchColors.AccentSky)
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(0.11f)) {
                            LiveValueBadge(live?.valueText)
                        }
                        Row(
                            modifier = Modifier.weight(0.10f),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (primaryGa != null) {
                                InlineGroupQuickControls(primaryGa, co.dpt, state, controller, ultraCompact = true)
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun GroupAddressesGridPanel(
    breadcrumb: String,
    items: List<GroupAddressInfo>,
    state: AppUiState,
    controller: AppController,
    search: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = search.trim().lowercase()
    val filtered = remember(items, q) {
        if (q.isEmpty()) items else items.filter {
            it.address.toString().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                (it.dpt?.lowercase()?.contains(q) == true)
        }
    }

    WorkbenchPanel(
        title = breadcrumb,
        icon = Icons.Default.FolderOpen,
        badgeText = "${filtered.size} GAs",
        modifier = modifier,
        headerActions = {
            DesktopPrimaryButton(
                text = if (state.isPollingBatch) "Polling..." else "Poll All",
                icon = Icons.Default.Sync,
                onClick = controller::pollAllProjectGroupAddresses,
                enabled = !state.isPollingBatch,
                compact = true,
            )
            CompactSearchField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = "Filter group addresses...",
                modifier = Modifier.width(175.dp),
            )
        },
    ) {
        DenseTableHeader(
            listOf(
                "Address" to 0.10f,
                "Group Address Name" to 0.25f,
                "Description" to 0.18f,
                "DPT" to 0.08f,
                "Links" to 0.07f,
                "Live Value" to 0.11f,
                "Quick Bus Actions" to 0.21f,
            )
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(filtered, key = { _, g -> g.address.raw }) { idx, ga ->
                val isSelected = state.selectedInspectorGroup == ga.address
                val rowBg = when {
                    isSelected -> WorkbenchColors.BgSelected
                    idx % 2 == 1 -> WorkbenchColors.BgRowAlt
                    else -> WorkbenchColors.BgPanel
                }
                val live = state.groupLiveValues[ga.address]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(31.dp)
                        .background(rowBg)
                        .clickable { controller.inspectGroupInExplorer(ga.address) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ga.address.toString(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = WorkbenchColors.AccentEmerald,
                        modifier = Modifier.weight(0.10f),
                    )
                    Text(
                        text = ga.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = WorkbenchColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.25f).padding(end = 6.dp),
                    )
                    Text(
                        text = ga.description.ifBlank { "—" },
                        fontSize = 11.sp,
                        color = WorkbenchColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.18f).padding(end = 6.dp),
                    )
                    Box(modifier = Modifier.weight(0.08f)) {
                        DptBadge(ga.dpt)
                    }
                    Text(
                        text = "${ga.linkedObjects.size} obj",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = WorkbenchColors.TextSecondary,
                        modifier = Modifier.weight(0.07f),
                    )
                    Box(modifier = Modifier.weight(0.11f)) {
                        LiveValueBadge(live?.valueText, live?.latencyMs)
                    }
                    Row(
                        modifier = Modifier.weight(0.21f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineGroupQuickControls(ga.address, ga.dpt, state, controller, ultraCompact = false)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun InlineGroupQuickControls(
    ga: GroupAddress,
    dpt: String?,
    state: AppUiState,
    controller: AppController,
    ultraCompact: Boolean,
) {
    val addrStr = ga.toString()
    when (DptCodec.resolveMainDpt(dpt)) {
        MainDpt.DPT_1_BOOL -> {
            DesktopPrimaryButton("ON", onClick = { controller.writeGroupSwitch(addrStr, true) }, compact = true)
            DesktopButton("OFF", onClick = { controller.writeGroupSwitch(addrStr, false) }, compact = true)
        }
        MainDpt.DPT_5_PERCENT, MainDpt.DPT_5_UNSIGNED -> {
            DesktopButton("0%", onClick = { controller.writeGroupPercent(addrStr, 0.0) }, compact = true)
            if (!ultraCompact) {
                DesktopButton("50%", onClick = { controller.writeGroupPercent(addrStr, 50.0) }, compact = true)
            }
            DesktopPrimaryButton("100%", onClick = { controller.writeGroupPercent(addrStr, 100.0) }, compact = true)
        }
        MainDpt.DPT_3_DIM_STEP -> {
            DesktopButton("-25%", onClick = { controller.writeGroupDimStep(addrStr, false, 3) }, compact = true)
            DesktopButton("+25%", onClick = { controller.writeGroupDimStep(addrStr, true, 3) }, compact = true)
        }
        MainDpt.DPT_17_SCENE -> {
            DesktopButton("S1", onClick = { controller.writeGroupScene(addrStr, 1) }, compact = true)
            if (!ultraCompact) {
                DesktopButton("S2", onClick = { controller.writeGroupScene(addrStr, 2) }, compact = true)
            }
        }
        else -> Unit
    }
    if (!ultraCompact) {
        DesktopButton("Read", onClick = { controller.readGroupAddress(addrStr) }, compact = true)
    }
}

private fun findGroupRangeById(nodes: List<GroupRangeNode>, id: String): GroupRangeNode? {
    for (n in nodes) {
        if (n.id == id) return n
        findGroupRangeById(n.children, id)?.let { return it }
    }
    return null
}
