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
    bgCanvas = Color(0xFF101413),
    bgTopBar = Color(0xFF101413),
    bgCard = Color(0xFF191C1B),
    bgCardElevated = Color(0xFF1D201F),
    bgInput = Color(0xFF272A29),
    borderSubtle = Color(0xFF2E3130),
    borderStrong = Color(0xFF414944),
    textPrimary = Color(0xFFE1E3DF),
    textSecondary = Color(0xFFC0C9C2),
    textMuted = Color(0xFF8A938C),
    accentEmerald = Color(0xFF7DD9BC),
    accentSky = Color(0xFFB1CCBE),
    accentAmber = Color(0xFFF5BE48),
    accentRed = Color(0xFFFFB4AB),
    accentPurple = Color(0xFFB8C8DA),
    isDark = true,
)

val MobileLightPalette = MobileColorScheme(
    bgCanvas = Color(0xFFF8FAF6),
    bgTopBar = Color(0xFFF8FAF6),
    bgCard = Color(0xFFFFFFFF),
    bgCardElevated = Color(0xFFEDF1EC),
    bgInput = Color(0xFFE7EBE6),
    borderSubtle = Color(0xFFE0E4DF),
    borderStrong = Color(0xFFC0C5BF),
    textPrimary = Color(0xFF191C1A),
    textSecondary = Color(0xFF414944),
    textMuted = Color(0xFF717973),
    accentEmerald = Color(0xFF006C4C),
    accentSky = Color(0xFF4A635B),
    accentAmber = Color(0xFF7B5800),
    accentRed = Color(0xFFBA1A1A),
    accentPurple = Color(0xFF50606E),
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
            onPrimary = Color(0xFF003825),
            primaryContainer = Color(0xFF005138),
            onPrimaryContainer = Color(0xFF9CF2D8),
            secondary = palette.accentSky,
            onSecondary = Color(0xFF1C352B),
            secondaryContainer = Color(0xFF334B41),
            onSecondaryContainer = Color(0xFFCDE8DA),
            tertiary = palette.accentAmber,
            onTertiary = Color(0xFF412D00),
            tertiaryContainer = Color(0xFF5D4200),
            onTertiaryContainer = Color(0xFFFFDF9E),
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
            primary = palette.accentEmerald,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF9CF2D8),
            onPrimaryContainer = Color(0xFF002114),
            secondary = palette.accentSky,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCDE8DA),
            onSecondaryContainer = Color(0xFF072019),
            tertiary = palette.accentAmber,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFDF9E),
            onTertiaryContainer = Color(0xFF261900),
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
