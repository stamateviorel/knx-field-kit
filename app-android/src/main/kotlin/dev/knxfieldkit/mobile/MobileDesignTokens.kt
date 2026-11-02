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
    bgCanvas = Color(0xFF0B0F17),
    bgTopBar = Color(0xFF101722),
    bgCard = Color(0xFF151E2D),
    bgCardElevated = Color(0xFF1C283C),
    bgInput = Color(0xFF0E1420),
    borderSubtle = Color(0xFF263449),
    borderStrong = Color(0xFF364966),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    accentEmerald = Color(0xFF10B981),
    accentSky = Color(0xFF38BDF8),
    accentAmber = Color(0xFFF59E0B),
    accentRed = Color(0xFFEF4444),
    accentPurple = Color(0xFFA855F7),
    isDark = true,
)

val MobileLightPalette = MobileColorScheme(
    bgCanvas = Color(0xFFF1F5F9),
    bgTopBar = Color(0xFFFFFFFF),
    bgCard = Color(0xFFFFFFFF),
    bgCardElevated = Color(0xFFF8FAFC),
    bgInput = Color(0xFFF1F5F9),
    borderSubtle = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    accentEmerald = Color(0xFF059669),
    accentSky = Color(0xFF0284C7),
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
    val OnAccent: Color @Composable get() = if (LocalMobileColors.current.isDark) Color(0xFF042F2E) else Color.White
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
            primary = palette.accentEmerald,
            secondary = palette.accentSky,
            tertiary = palette.accentAmber,
            background = palette.bgCanvas,
            surface = palette.bgCard,
            surfaceVariant = palette.bgCardElevated,
            onPrimary = Color(0xFF042F2E),
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderSubtle,
            outlineVariant = palette.borderStrong,
            error = palette.accentRed,
        )
    } else {
        lightColorScheme(
            primary = palette.accentEmerald,
            secondary = palette.accentSky,
            tertiary = palette.accentAmber,
            background = palette.bgCanvas,
            surface = palette.bgCard,
            surfaceVariant = palette.bgCardElevated,
            onPrimary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderSubtle,
            outlineVariant = palette.borderStrong,
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
            val fg = if (enabled) {
                if (letter == "R" || letter == "T") MobileColors.AccentEmerald else MobileColors.AccentSky
            } else {
                MobileColors.TextMuted.copy(alpha = 0.55f)
            }
            val bg = if (enabled) fg.copy(alpha = 0.16f) else MobileColors.BgInput
            val border = if (enabled) fg.copy(alpha = 0.5f) else MobileColors.BorderSubtle
            Box(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(4.dp))
                    .border(1.dp, border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
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
        color = MobileColors.BgCard,
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
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
                color = MobileColors.TextSecondary,
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
        shape = RoundedCornerShape(10.dp),
        color = MobileColors.BgInput,
        border = BorderStroke(1.dp, MobileColors.BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MobileColors.TextSecondary,
                modifier = Modifier.size(17.dp),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 12.sp,
                        color = MobileColors.TextMuted,
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
                        color = MobileColors.TextPrimary,
                    ),
                    cursorBrush = SolidColor(MobileColors.AccentEmerald),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = MobileColors.TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}
