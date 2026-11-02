package dev.knxfieldkit.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.model.ComObjectFlags

data class WorkbenchColorScheme(
    val bgCanvas: Color,
    val bgPanel: Color,
    val bgPanelAlt: Color,
    val bgHeader: Color,
    val bgRowAlt: Color,
    val bgSelected: Color,
    val bgHover: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentEmerald: Color,
    val accentSky: Color,
    val accentTeal: Color,
    val accentAmber: Color,
    val accentRed: Color,
    val accentPurple: Color,
    val isDark: Boolean,
)

val WorkbenchDarkPalette = WorkbenchColorScheme(
    bgCanvas = Color(0xFF090D16),
    bgPanel = Color(0xFF0F1624),
    bgPanelAlt = Color(0xFF131C2E),
    bgHeader = Color(0xFF162033),
    bgRowAlt = Color(0xFF0C121E),
    bgSelected = Color(0xFF193254),
    bgHover = Color(0xFF18243A),
    borderSubtle = Color(0xFF223047),
    borderStrong = Color(0xFF314566),
    textPrimary = Color(0xFFE6EDF7),
    textSecondary = Color(0xFF94A6C0),
    textMuted = Color(0xFF63748F),
    accentEmerald = Color(0xFF10B981),
    accentSky = Color(0xFF38BDF8),
    accentTeal = Color(0xFF14B8A6),
    accentAmber = Color(0xFFF59E0B),
    accentRed = Color(0xFFEF4444),
    accentPurple = Color(0xFFA78BFA),
    isDark = true,
)

val WorkbenchLightPalette = WorkbenchColorScheme(
    bgCanvas = Color(0xFFEDF2F7),
    bgPanel = Color(0xFFFFFFFF),
    bgPanelAlt = Color(0xFFF8FAFC),
    bgHeader = Color(0xFFF1F5F9),
    bgRowAlt = Color(0xFFF8FAFC),
    bgSelected = Color(0xFFE0F2FE),
    bgHover = Color(0xFFF1F5F9),
    borderSubtle = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    accentEmerald = Color(0xFF059669),
    accentSky = Color(0xFF0284C7),
    accentTeal = Color(0xFF0D9488),
    accentAmber = Color(0xFFD97706),
    accentRed = Color(0xFFDC2626),
    accentPurple = Color(0xFF7C3AED),
    isDark = false,
)

val LocalWorkbenchColors = staticCompositionLocalOf { WorkbenchDarkPalette }

object WorkbenchColors {
    val BgCanvas: Color @Composable get() = LocalWorkbenchColors.current.bgCanvas
    val BgPanel: Color @Composable get() = LocalWorkbenchColors.current.bgPanel
    val BgPanelAlt: Color @Composable get() = LocalWorkbenchColors.current.bgPanelAlt
    val BgHeader: Color @Composable get() = LocalWorkbenchColors.current.bgHeader
    val BgRowAlt: Color @Composable get() = LocalWorkbenchColors.current.bgRowAlt
    val BgSelected: Color @Composable get() = LocalWorkbenchColors.current.bgSelected
    val BgHover: Color @Composable get() = LocalWorkbenchColors.current.bgHover
    val BorderSubtle: Color @Composable get() = LocalWorkbenchColors.current.borderSubtle
    val BorderStrong: Color @Composable get() = LocalWorkbenchColors.current.borderStrong
    val TextPrimary: Color @Composable get() = LocalWorkbenchColors.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalWorkbenchColors.current.textSecondary
    val TextMuted: Color @Composable get() = LocalWorkbenchColors.current.textMuted
    val AccentEmerald: Color @Composable get() = LocalWorkbenchColors.current.accentEmerald
    val AccentSky: Color @Composable get() = LocalWorkbenchColors.current.accentSky
    val AccentTeal: Color @Composable get() = LocalWorkbenchColors.current.accentTeal
    val AccentAmber: Color @Composable get() = LocalWorkbenchColors.current.accentAmber
    val AccentRed: Color @Composable get() = LocalWorkbenchColors.current.accentRed
    val AccentPurple: Color @Composable get() = LocalWorkbenchColors.current.accentPurple
    val IsDark: Boolean @Composable get() = LocalWorkbenchColors.current.isDark
    val OnAccent: Color @Composable get() = if (LocalWorkbenchColors.current.isDark) Color(0xFF042F2E) else Color.White
}

@Composable
fun WorkbenchPanel(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badgeText: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = WorkbenchColors.BgPanel,
        border = BorderStroke(1.dp, WorkbenchColors.BorderSubtle),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(WorkbenchColors.BgHeader)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = WorkbenchColors.AccentSky,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WorkbenchColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(WorkbenchColors.AccentSky.copy(alpha = 0.16f))
                                .border(1.dp, WorkbenchColors.AccentSky.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = WorkbenchColors.AccentSky,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    content = headerActions,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle)
            Column(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
fun DesktopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    active: Boolean = false,
    accentColor: Color = WorkbenchColors.AccentSky,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(4.dp)
    val bg = when {
        active -> accentColor.copy(alpha = 0.20f)
        else -> WorkbenchColors.BgHeader
    }
    val borderCol = if (active) accentColor else WorkbenchColors.BorderStrong
    val textCol = if (active) accentColor else WorkbenchColors.TextPrimary

    Row(
        modifier = modifier
            .height(if (compact) 21.dp else 26.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderCol, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) accentColor else WorkbenchColors.TextSecondary,
                modifier = Modifier.size(if (compact) 11.dp else 13.dp),
            )
        }
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            color = textCol,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun DesktopPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = WorkbenchColors.AccentEmerald,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(4.dp)
    val fg = Color(0xFF04130E)
    Row(
        modifier = modifier
            .height(if (compact) 21.dp else 26.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(shape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(if (compact) 11.dp else 13.dp),
            )
        }
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun SegmentedTabs(
    options: List<Pair<String, ImageVector?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .background(WorkbenchColors.BgCanvas)
            .border(1.dp, WorkbenchColors.BorderSubtle, shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, (label, icon) ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (selected) WorkbenchColors.BgSelected else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) WorkbenchColors.AccentSky else WorkbenchColors.TextMuted,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) WorkbenchColors.TextPrimary else WorkbenchColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun TreeRowItem(
    label: String,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    iconTint: Color? = null,
    prefixBadge: String? = null,
    countBadge: String? = null,
    statusDotColor: Color? = null,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(25.dp)
            .background(if (selected) WorkbenchColors.BgSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (selected) WorkbenchColors.AccentSky else Color.Transparent),
        )
        Spacer(Modifier.width((depth * 13 + 3).dp))
        if (expandable) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { (onToggleExpand ?: onClick).invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = WorkbenchColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint ?: WorkbenchColors.AccentSky,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        if (!prefixBadge.isNullOrBlank()) {
            Text(
                text = prefixBadge,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) WorkbenchColors.AccentSky else WorkbenchColors.TextSecondary,
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) WorkbenchColors.TextPrimary else WorkbenchColors.TextPrimary.copy(alpha = 0.90f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (statusDotColor != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusDotColor),
            )
        }
        if (!countBadge.isNullOrBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = countBadge,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = WorkbenchColors.TextMuted,
            )
        }
    }
}

@Composable
fun ComFlagsBadges(flags: ComObjectFlags) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        ComSingleFlag('C', flags.c, WorkbenchColors.AccentSky)
        ComSingleFlag('R', flags.r, WorkbenchColors.AccentEmerald)
        ComSingleFlag('W', flags.w, WorkbenchColors.AccentAmber)
        ComSingleFlag('T', flags.t, WorkbenchColors.AccentPurple)
        ComSingleFlag('U', flags.u, WorkbenchColors.AccentTeal)
    }
}

@Composable
private fun ComSingleFlag(letter: Char, active: Boolean, color: Color) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .size(width = 14.dp, height = 15.dp)
            .clip(shape)
            .background(if (active) color.copy(alpha = 0.18f) else WorkbenchColors.BgCanvas)
            .border(1.dp, if (active) color.copy(alpha = 0.55f) else WorkbenchColors.BorderSubtle, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (active) letter.toString() else "·",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (active) color else WorkbenchColors.TextMuted.copy(alpha = 0.55f),
        )
    }
}

@Composable
fun DptBadge(dpt: String?) {
    val label = dpt?.takeIf { it.isNotBlank() } ?: "raw"
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(WorkbenchColors.BgCanvas)
            .border(1.dp, WorkbenchColors.BorderSubtle, shape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = WorkbenchColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun LiveValueBadge(valueText: String?, latencyMs: Long? = null) {
    if (valueText.isNullOrBlank()) {
        Text(
            text = "—",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = WorkbenchColors.TextMuted,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(WorkbenchColors.AccentEmerald.copy(alpha = 0.16f))
                    .border(1.dp, WorkbenchColors.AccentEmerald.copy(alpha = 0.40f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = valueText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = WorkbenchColors.AccentEmerald,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (latencyMs != null) {
                Text(
                    text = "${latencyMs}ms",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = WorkbenchColors.TextMuted,
                )
            }
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .background(WorkbenchColors.BgCanvas)
            .border(1.dp, WorkbenchColors.BorderSubtle, shape)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = WorkbenchColors.TextMuted,
            modifier = Modifier.size(13.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 11.sp,
                    color = WorkbenchColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 11.sp,
                    color = WorkbenchColors.TextPrimary,
                ),
                cursorBrush = SolidColor(WorkbenchColors.AccentSky),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear",
                tint = WorkbenchColors.TextMuted,
                modifier = Modifier
                    .size(12.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}

@Composable
fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefixLabel: String? = null,
    placeholder: String = "",
    monospace: Boolean = true,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .background(WorkbenchColors.BgCanvas)
            .border(1.dp, WorkbenchColors.BorderStrong, shape)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!prefixLabel.isNullOrBlank()) {
            Text(
                text = prefixLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = WorkbenchColors.TextMuted,
                maxLines = 1,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 11.sp,
                    color = WorkbenchColors.TextMuted,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 11.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    color = WorkbenchColors.TextPrimary,
                ),
                cursorBrush = SolidColor(WorkbenchColors.AccentSky),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PropertyKeyValueRow(key: String, value: String, valueColor: Color = WorkbenchColors.TextPrimary, monospace: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            fontSize = 11.sp,
            color = WorkbenchColors.TextMuted,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value.ifBlank { "—" },
            fontSize = 11.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DenseTableHeader(columns: List<Pair<String, Float>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(WorkbenchColors.BgPanelAlt)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((title, weight) in columns) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WorkbenchColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weight),
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = WorkbenchColors.BorderSubtle)
}
