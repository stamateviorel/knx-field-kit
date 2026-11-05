package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.app.ThemeMode
import dev.knxfieldkit.model.ComObjectFlags

data class MobileColorScheme(
    val bgCanvas: Color,
    val bgTopBar: Color,
    val bgCard: Color,
    val bgCardElevated: Color,
    val bgInput: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentEmerald: Color,
    val accentSky: Color,
    val accentAmber: Color,
    val accentRed: Color,
    val accentPurple: Color,
    val isDark: Boolean,
)

val MobileDarkPalette = MobileColorScheme(
    bgCanvas = Color(0xFF0B0F14),
    bgTopBar = Color(0xFF0F141C),
    bgCard = Color(0xFF131922),
    bgCardElevated = Color(0xFF1A2230),
    bgInput = Color(0xFF222C3D),
    borderSubtle = Color(0xFF243042),
    borderStrong = Color(0xFF334155),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    accentEmerald = Color(0xFF22C55E),
    accentSky = Color(0xFF388BFD),
    accentAmber = Color(0xFFF5BE48),
    accentRed = Color(0xFFFF6B6B),
    accentPurple = Color(0xFFA78BFA),
    isDark = true,
)

val MobileLightPalette = MobileColorScheme(
    bgCanvas = Color(0xFFF3F5F8),
    bgTopBar = Color(0xFFFFFFFF),
    bgCard = Color(0xFFFFFFFF),
    bgCardElevated = Color(0xFFE8EEF5),
    bgInput = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    accentEmerald = Color(0xFF16A34A),
    accentSky = Color(0xFF005596),
    accentAmber = Color(0xFFD97706),
    accentRed = Color(0xFFDC2626),
    accentPurple = Color(0xFF7C3AED),
    isDark = false,
)

val LocalMobileColors = staticCompositionLocalOf { MobileDarkPalette }

object MobileColors {
    val BgCanvas: Color @Composable get() = LocalMobileColors.current.bgCanvas
    val BgTopBar: Color @Composable get() = LocalMobileColors.current.bgTopBar
    val BgCard: Color @Composable get() = LocalMobileColors.current.bgCard
    val BgCardElevated: Color @Composable get() = LocalMobileColors.current.bgCardElevated
    val BgInput: Color @Composable get() = LocalMobileColors.current.bgInput
    val BorderSubtle: Color @Composable get() = LocalMobileColors.current.borderSubtle
    val BorderStrong: Color @Composable get() = LocalMobileColors.current.borderStrong
    val TextPrimary: Color @Composable get() = LocalMobileColors.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalMobileColors.current.textSecondary
    val TextMuted: Color @Composable get() = LocalMobileColors.current.textMuted
    val AccentEmerald: Color @Composable get() = LocalMobileColors.current.accentEmerald
    val AccentSky: Color @Composable get() = LocalMobileColors.current.accentSky
    val AccentAmber: Color @Composable get() = LocalMobileColors.current.accentAmber
    val AccentRed: Color @Composable get() = LocalMobileColors.current.accentRed
    val AccentPurple: Color @Composable get() = LocalMobileColors.current.accentPurple
    val IsDark: Boolean @Composable get() = LocalMobileColors.current.isDark
    val OnAccent: Color @Composable get() = Color.White
}

@Composable
fun MobileFieldKitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (isDark) MobileDarkPalette else MobileLightPalette

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accentSky,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1E3A8A),
            onPrimaryContainer = Color(0xFFDBEAFE),
            secondary = palette.accentSky,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF1E293B),
            onSecondaryContainer = Color(0xFFCBD5E1),
            tertiary = palette.accentEmerald,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFF064E3B),
            onTertiaryContainer = Color(0xFFA7F3D0),
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.bgCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.bgCardElevated,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainer = palette.bgCardElevated,
            surfaceContainerHigh = palette.bgInput,
            outline = palette.borderStrong,
            outlineVariant = palette.borderSubtle,
            error = palette.accentRed,
        )
    } else {
        lightColorScheme(
            primary = palette.accentSky,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBFA),
            onPrimaryContainer = Color(0xFF0C2B54),
            secondary = Color(0xFF475569),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE2E8F0),
            onSecondaryContainer = Color(0xFF1E293B),
            tertiary = palette.accentEmerald,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDCFCE7),
            onTertiaryContainer = Color(0xFF14532D),
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.bgCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.bgCardElevated,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainer = palette.bgCardElevated,
            surfaceContainerHigh = palette.bgInput,
            outline = palette.borderStrong,
            outlineVariant = palette.borderSubtle,
            error = palette.accentRed,
        )
    }

    CompositionLocalProvider(LocalMobileColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

@Composable
fun MobileBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1,
        )
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
fun MobileComFlagsStrip(flags: ComObjectFlags, modifier: Modifier = Modifier) {
    val entries = listOf(
        "C" to flags.c,
        "R" to flags.r,
        "W" to flags.w,
        "T" to flags.t,
        "U" to flags.u,
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((letter, enabled) in entries) {
            val fg = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            val bg = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            val border = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(4.dp))
                    .border(1.dp, border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = fg,
                )
            }
        }
    }
}

@Composable
fun MobileMetricTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MobileSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}
