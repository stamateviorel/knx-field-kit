package dev.knxfieldkit.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.model.ComObjectFlags

/**
 * Authentic ETS6 / ETS5 Icon Suite and Badges for KNX Field Kit.
 *
 * Replicates the exact visual language of KNX Association's ETS engineering tool:
 * - Buildings View: Building, Building Part, Floor, Room, Cabinet (Distribution Board), Corridor, Stairway
 * - Trades/Functions: Switchable Light (FT-1), Dimmable Light (FT-2/FT-6), Sun Protection (FT-3/FT-7),
 *                     Heating/HVAC (FT-4/5/8/9), Scenes, Custom
 * - Topology: Area backbone, Line branch, DIN-rail Device (with active KNX Secure shield)
 * - Group Addresses: Main Group (Green Folder), Middle Group (Blue Folder), Subgroup Address (Address Tag)
 * - Communication Objects: ETS yellow barrel plug (groupobject.png)
 * - KNX Secure: Official KNX Blue (#005596) active shield with white check/key
 */
object EtsColors {
    val KnxBlue = Color(0xFF005596)        // Official KNX Blue
    val KnxBlueLight = Color(0xFF1E88E5)
    val EtsGreen = Color(0xFF16A34A)        // ETS Main Group Green
    val EtsGreenLight = Color(0xFF22C55E)
    val EtsFolderBlue = Color(0xFF2563EB)   // ETS Middle Group Folder Blue
    val ComObjectYellow = Color(0xFFEAB308) // ETS Group Object Yellow
    val SlateCabinet = Color(0xFF475569)    // ETS Distribution Board Slate
    val FloorPlate = Color(0xFF0284C7)      // ETS Floor Sky Blue
    val RoomTeal = Color(0xFF0D9488)        // ETS Room Teal
    val FunctionAmber = Color(0xFFF5BE48)   // ETS Lighting Amber
    val FunctionDimmer = Color(0xFFF97316)  // ETS Dimmer Orange
    val FunctionBlinds = Color(0xFF06B6D4)  // ETS Blinds Cyan
    val FunctionHeating = Color(0xFFEF4444) // ETS Heating Red
    val FunctionScene = Color(0xFFA78BFA)   // ETS Scene Purple
}

// ---------------------------------------------------------------------------
// 1. KNX SECURE SHIELD & BADGES
// ---------------------------------------------------------------------------

/**
 * Official KNX Blue Shield with crisp white check/key inside (device_shield_active_16.png).
 */
@Composable
fun EtsSecureShield(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 15.dp,
    shieldColor: Color = EtsColors.KnxBlue,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        drawEtsShield(shieldColor = shieldColor, width = size.width, height = size.height)
    }
}

private fun DrawScope.drawEtsShield(
    shieldColor: Color,
    width: Float,
    height: Float,
) {
    // Shield outer path
    val path = Path().apply {
        moveTo(width * 0.5f, 0f)
        lineTo(width, height * 0.18f)
        lineTo(width * 0.95f, height * 0.62f)
        cubicTo(
            width * 0.85f, height * 0.85f,
            width * 0.55f, height * 0.98f,
            width * 0.5f, height,
        )
        cubicTo(
            width * 0.45f, height * 0.98f,
            width * 0.15f, height * 0.85f,
            width * 0.05f, height * 0.62f,
        )
        lineTo(0f, height * 0.18f)
        close()
    }
    // Solid shield body
    drawPath(path, color = shieldColor, style = Fill)
    // Darker outline for contrast
    drawPath(path, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 0.8f))

    // White checkmark inside shield
    val checkPath = Path().apply {
        moveTo(width * 0.28f, height * 0.50f)
        lineTo(width * 0.45f, height * 0.68f)
        lineTo(width * 0.74f, height * 0.34f)
    }
    drawPath(
        path = checkPath,
        color = Color.White,
        style = Stroke(
            width = width * 0.16f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/**
 * Standard ETS engineering KNX Secure badge pill [SEC].
 */
@Composable
fun EtsSecureBadge(
    modifier: Modifier = Modifier,
    label: String = "SEC",
) {
    Row(
        modifier = modifier
            .background(EtsColors.KnxBlue, EtsShapes.Badge)
            .border(0.8.dp, Color.White.copy(alpha = 0.4f), EtsShapes.Badge)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        EtsSecureShield(sizeDp = 10.dp)
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * Standard ETS engineering Unsecured / Plain text badge pill [PLAIN].
 * Explicitly indicates non-encrypted, open bus communication.
 */
@Composable
fun EtsPlainBadge(
    modifier: Modifier = Modifier,
    label: String = "PLAIN",
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, EtsShapes.Badge)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, EtsShapes.Badge)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Text(
            text = label,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Unified ETS Security status badge showing either [SEC] or [PLAIN].
 */
@Composable
fun EtsSecurityStatusBadge(
    isSecure: Boolean,
    compact: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (isSecure) {
        EtsSecureBadge(
            modifier = modifier,
            label = if (compact) "SEC" else "KNX SECURE",
        )
    } else {
        EtsPlainBadge(
            modifier = modifier,
            label = if (compact) "PLAIN" else "PLAIN (UNSECURED)",
        )
    }
}

// ---------------------------------------------------------------------------
// 2. BUILDINGS HIERARCHY ICONS
// ---------------------------------------------------------------------------

/**
 * ETS Building Icon (BuildingsIcon.png): Multi-story architectural structure with lit windows and entrance.
 */
@Composable
fun EtsBuildingIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Building outer facade
        val facade = Path().apply {
            moveTo(w * 0.12f, h * 0.94f)
            lineTo(w * 0.12f, h * 0.20f)
            lineTo(w * 0.50f, h * 0.08f) // pitched architectural roof top
            lineTo(w * 0.88f, h * 0.20f)
            lineTo(w * 0.88f, h * 0.94f)
            close()
        }
        drawPath(facade, color = tint.copy(alpha = 0.22f), style = Fill)
        drawPath(facade, color = tint, style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Ground slab
        drawLine(
            color = tint,
            start = Offset(w * 0.04f, h * 0.94f),
            end = Offset(w * 0.96f, h * 0.94f),
            strokeWidth = 1.2f,
            cap = StrokeCap.Round,
        )

        // Windows (2x2 grid)
        val winW = w * 0.16f
        val winH = h * 0.14f
        val winColor = tint.copy(alpha = 0.9f)
        // Top floor windows
        drawRoundRect(winColor, Offset(w * 0.26f, h * 0.30f), Size(winW, winH), CornerRadius(1f))
        drawRoundRect(winColor, Offset(w * 0.58f, h * 0.30f), Size(winW, winH), CornerRadius(1f))
        // Middle floor windows
        drawRoundRect(winColor, Offset(w * 0.26f, h * 0.50f), Size(winW, winH), CornerRadius(1f))
        drawRoundRect(winColor, Offset(w * 0.58f, h * 0.50f), Size(winW, winH), CornerRadius(1f))

        // Entrance Door
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.42f, h * 0.72f),
            size = Size(w * 0.16f, h * 0.22f),
            cornerRadius = CornerRadius(1f),
            style = Fill,
        )
    }
}

/**
 * ETS Building Part Icon (building_part_16.png): Building wing / section.
 */
@Composable
fun EtsBuildingPartIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FloorPlate,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Main block
        drawRoundRect(
            color = tint.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = Size(w * 0.46f, h * 0.68f),
            cornerRadius = CornerRadius(1.5f),
            style = Fill,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = Size(w * 0.46f, h * 0.68f),
            cornerRadius = CornerRadius(1.5f),
            style = Stroke(width = 1.2f),
        )

        // Stepped annex wing
        drawRoundRect(
            color = tint.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.50f, h * 0.44f),
            size = Size(w * 0.42f, h * 0.48f),
            cornerRadius = CornerRadius(1.5f),
            style = Fill,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.50f, h * 0.44f),
            size = Size(w * 0.42f, h * 0.48f),
            cornerRadius = CornerRadius(1.5f),
            style = Stroke(width = 1.2f),
        )

        // Window notches
        drawRect(tint, Offset(w * 0.20f, h * 0.36f), Size(w * 0.12f, h * 0.14f))
        drawRect(tint, Offset(w * 0.20f, h * 0.56f), Size(w * 0.12f, h * 0.14f))
        drawRect(tint, Offset(w * 0.66f, h * 0.58f), Size(w * 0.14f, h * 0.14f))
    }
}

/**
 * ETS Floor Icon (floor_16.png): Stacked perspective floor slabs.
 */
@Composable
fun EtsFloorIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FloorPlate,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Top floor plate
        val plate1 = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.92f, h * 0.28f)
            lineTo(w * 0.50f, h * 0.44f)
            lineTo(w * 0.08f, h * 0.28f)
            close()
        }
        drawPath(plate1, color = tint.copy(alpha = 0.35f), style = Fill)
        drawPath(plate1, color = tint, style = Stroke(width = 1.1f))

        // Middle floor plate
        val plate2 = Path().apply {
            moveTo(w * 0.50f, h * 0.40f)
            lineTo(w * 0.92f, h * 0.56f)
            lineTo(w * 0.50f, h * 0.72f)
            lineTo(w * 0.08f, h * 0.56f)
            close()
        }
        drawPath(plate2, color = tint.copy(alpha = 0.25f), style = Fill)
        drawPath(plate2, color = tint, style = Stroke(width = 1.1f))

        // Bottom foundation plate
        val plate3 = Path().apply {
            moveTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.92f, h * 0.82f)
            lineTo(w * 0.50f, h * 0.96f)
            lineTo(w * 0.08f, h * 0.82f)
            close()
        }
        drawPath(plate3, color = tint.copy(alpha = 0.45f), style = Fill)
        drawPath(plate3, color = tint, style = Stroke(width = 1.2f))
    }
}

/**
 * ETS Room Icon (room_16.png): Architectural floor plan with door cutout and swing arc.
 */
@Composable
fun EtsRoomIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.RoomTeal,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Room perimeter walls with door opening
        val roomWalls = Path().apply {
            moveTo(w * 0.10f, h * 0.90f)
            lineTo(w * 0.10f, h * 0.12f)
            lineTo(w * 0.90f, h * 0.12f)
            lineTo(w * 0.90f, h * 0.90f)
            lineTo(w * 0.56f, h * 0.90f) // wall right of doorway
            // door gap: 0.56 to 0.24
            moveTo(w * 0.24f, h * 0.90f)
            lineTo(w * 0.10f, h * 0.90f) // wall left of doorway
        }
        drawPath(roomWalls, color = tint, style = Stroke(width = 1.6f, cap = StrokeCap.Square))

        // Open door leaf swinging inside room
        drawLine(
            color = tint,
            start = Offset(w * 0.24f, h * 0.90f),
            end = Offset(w * 0.24f, h * 0.58f),
            strokeWidth = 1.2f,
            cap = StrokeCap.Round,
        )

        // Door swing trajectory arc
        val arcPath = Path().apply {
            moveTo(w * 0.24f, h * 0.58f)
            cubicTo(
                w * 0.40f, h * 0.58f,
                w * 0.56f, h * 0.72f,
                w * 0.56f, h * 0.90f,
            )
        }
        drawPath(arcPath, color = tint.copy(alpha = 0.55f), style = Stroke(width = 0.8f, cap = StrokeCap.Round))

        // Subtle interior tint
        drawRect(
            color = tint.copy(alpha = 0.08f),
            topLeft = Offset(w * 0.12f, h * 0.14f),
            size = Size(w * 0.76f, h * 0.74f),
        )
    }
}

/**
 * ETS Cabinet / Distribution Board Icon (cabinet_16.png): Electrical switchboard panel with door and DIN rails.
 */
@Composable
fun EtsCabinetIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.SlateCabinet,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Outer metal enclosure
        drawRoundRect(
            color = tint.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.12f, h * 0.08f),
            size = Size(w * 0.76f, h * 0.84f),
            cornerRadius = CornerRadius(2f),
            style = Fill,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.08f),
            size = Size(w * 0.76f, h * 0.84f),
            cornerRadius = CornerRadius(2f),
            style = Stroke(width = 1.3f),
        )

        // Door divider seam
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, h * 0.08f),
            end = Offset(w * 0.36f, h * 0.92f),
            strokeWidth = 0.9f,
        )

        // DIN Rail modules inside right bay (3 rails)
        val railColor = tint.copy(alpha = 0.9f)
        drawRect(railColor, Offset(w * 0.44f, h * 0.22f), Size(w * 0.36f, h * 0.14f))
        drawRect(railColor, Offset(w * 0.44f, h * 0.44f), Size(w * 0.36f, h * 0.14f))
        drawRect(railColor, Offset(w * 0.44f, h * 0.66f), Size(w * 0.36f, h * 0.14f))

        // Key lock / door handle on left door
        drawCircle(
            color = tint,
            radius = w * 0.05f,
            center = Offset(w * 0.24f, h * 0.50f),
            style = Fill,
        )
    }
}

/**
 * ETS Corridor Icon (corridor_16.png): Perspective hallway.
 */
@Composable
fun EtsCorridorIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.SlateCabinet,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Outer corridor frame
        drawLine(tint, Offset(w * 0.08f, h * 0.08f), Offset(w * 0.36f, h * 0.32f), strokeWidth = 1.2f)
        drawLine(tint, Offset(w * 0.92f, h * 0.08f), Offset(w * 0.64f, h * 0.32f), strokeWidth = 1.2f)
        drawLine(tint, Offset(w * 0.08f, h * 0.92f), Offset(w * 0.36f, h * 0.68f), strokeWidth = 1.2f)
        drawLine(tint, Offset(w * 0.92f, h * 0.92f), Offset(w * 0.64f, h * 0.68f), strokeWidth = 1.2f)

        // End doorway
        drawRect(
            color = tint.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.36f, h * 0.32f),
            size = Size(w * 0.28f, h * 0.36f),
            style = Fill,
        )
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.36f, h * 0.32f),
            size = Size(w * 0.28f, h * 0.36f),
            style = Stroke(width = 1.2f),
        )
    }
}

/**
 * ETS Stairway Icon (stairways_16.png): Stepped ascending staircase.
 */
@Composable
fun EtsStairwayIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.SlateCabinet,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        val stairs = Path().apply {
            moveTo(w * 0.10f, h * 0.90f)
            lineTo(w * 0.36f, h * 0.90f)
            lineTo(w * 0.36f, h * 0.64f)
            lineTo(w * 0.62f, h * 0.64f)
            lineTo(w * 0.62f, h * 0.38f)
            lineTo(w * 0.90f, h * 0.38f)
            lineTo(w * 0.90f, h * 0.16f)
            lineTo(w * 0.90f, h * 0.90f)
            close()
        }
        drawPath(stairs, color = tint.copy(alpha = 0.25f), style = Fill)
        drawPath(stairs, color = tint, style = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Handrail line
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.62f),
            end = Offset(w * 0.88f, h * 0.08f),
            strokeWidth = 1.1f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Dispatcher to render the authentic ETS icon for any building space type.
 */
@Composable
fun EtsSpaceIcon(
    type: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 16.dp,
) {
    val norm = type.trim().lowercase()
    when {
        norm == "building" -> EtsBuildingIcon(modifier = modifier, sizeDp = sizeDp)
        norm in listOf("buildingpart", "building_part", "part", "wing") -> EtsBuildingPartIcon(modifier = modifier, sizeDp = sizeDp)
        norm in listOf("floor", "level", "storey") -> EtsFloorIcon(modifier = modifier, sizeDp = sizeDp)
        norm in listOf("distributionboard", "distribution_board", "cabinet", "panel") -> EtsCabinetIcon(modifier = modifier, sizeDp = sizeDp)
        norm in listOf("corridor", "hallway") -> EtsCorridorIcon(modifier = modifier, sizeDp = sizeDp)
        norm in listOf("stairway", "staircase", "stairs") -> EtsStairwayIcon(modifier = modifier, sizeDp = sizeDp)
        else -> EtsRoomIcon(modifier = modifier, sizeDp = sizeDp)
    }
}

// ---------------------------------------------------------------------------
// 3. TRADES / FUNCTION ICONS (FT-1, FT-2, FT-3, FT-4, etc.)
// ---------------------------------------------------------------------------

/**
 * Lighting Trade Icon (FT-1 switchable light): Incandescent bulb with screw base and beam rays.
 */
@Composable
fun EtsFunctionLightIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FunctionAmber,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Bulb glass contour
        val bulb = Path().apply {
            moveTo(w * 0.32f, h * 0.66f)
            cubicTo(
                w * 0.14f, h * 0.52f,
                w * 0.14f, h * 0.22f,
                w * 0.50f, h * 0.10f,
            )
            cubicTo(
                w * 0.86f, h * 0.10f,
                w * 0.86f, h * 0.52f,
                w * 0.68f, h * 0.66f,
            )
            close()
        }
        drawPath(bulb, color = tint.copy(alpha = 0.28f), style = Fill)
        drawPath(bulb, color = tint, style = Stroke(width = 1.2f))

        // Screw base
        drawRect(tint, Offset(w * 0.36f, h * 0.68f), Size(w * 0.28f, h * 0.10f))
        drawRect(tint, Offset(w * 0.40f, h * 0.80f), Size(w * 0.20f, h * 0.08f))

        // Filament line
        drawLine(tint, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.50f, h * 0.28f), strokeWidth = 1f)
        drawLine(tint, Offset(w * 0.50f, h * 0.28f), Offset(w * 0.58f, h * 0.42f), strokeWidth = 1f)
    }
}

/**
 * Dimmer Trade Icon (FT-2, FT-6 dimmable light): Light bulb with step dimming rays.
 */
@Composable
fun EtsFunctionDimmerIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FunctionDimmer,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Bulb glass contour
        val bulb = Path().apply {
            moveTo(w * 0.34f, h * 0.64f)
            cubicTo(
                w * 0.18f, h * 0.52f,
                w * 0.18f, h * 0.26f,
                w * 0.50f, h * 0.14f,
            )
            cubicTo(
                w * 0.82f, h * 0.14f,
                w * 0.82f, h * 0.52f,
                w * 0.66f, h * 0.64f,
            )
            close()
        }
        drawPath(bulb, color = tint.copy(alpha = 0.30f), style = Fill)
        drawPath(bulb, color = tint, style = Stroke(width = 1.2f))

        // Screw base
        drawRect(tint, Offset(w * 0.38f, h * 0.66f), Size(w * 0.24f, h * 0.10f))

        // Stepped dimming arc rays (left, top, right)
        drawLine(tint, Offset(w * 0.12f, h * 0.18f), Offset(w * 0.22f, h * 0.26f), strokeWidth = 1.1f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, h * 0.04f), Offset(w * 0.50f, h * 0.10f), strokeWidth = 1.1f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.88f, h * 0.18f), Offset(w * 0.78f, h * 0.26f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    }
}

/**
 * Blinds / Shutter Trade Icon (FT-3, FT-7 sun protection): Venetian blind louvers with movement arrow.
 */
@Composable
fun EtsFunctionBlindsIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FunctionBlinds,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Window frame
        drawRoundRect(
            color = tint.copy(alpha = 0.12f),
            topLeft = Offset(w * 0.12f, h * 0.10f),
            size = Size(w * 0.76f, h * 0.80f),
            cornerRadius = CornerRadius(1.5f),
            style = Fill,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.10f),
            size = Size(w * 0.76f, h * 0.80f),
            cornerRadius = CornerRadius(1.5f),
            style = Stroke(width = 1.2f),
        )

        // Slat lines (4 horizontal louvers)
        val slatColor = tint
        drawLine(slatColor, Offset(w * 0.20f, h * 0.26f), Offset(w * 0.80f, h * 0.26f), strokeWidth = 1.1f)
        drawLine(slatColor, Offset(w * 0.20f, h * 0.42f), Offset(w * 0.80f, h * 0.42f), strokeWidth = 1.1f)
        drawLine(slatColor, Offset(w * 0.20f, h * 0.58f), Offset(w * 0.80f, h * 0.58f), strokeWidth = 1.1f)
        drawLine(slatColor, Offset(w * 0.20f, h * 0.74f), Offset(w * 0.80f, h * 0.74f), strokeWidth = 1.1f)

        // Vertical adjustment pull cord
        drawLine(
            color = tint.copy(alpha = 0.6f),
            start = Offset(w * 0.32f, h * 0.10f),
            end = Offset(w * 0.32f, h * 0.74f),
            strokeWidth = 0.8f,
        )
    }
}

/**
 * Heating / HVAC Trade Icon (FT-4, FT-5, FT-8, FT-9): Radiator heater ribs & thermal flame.
 */
@Composable
fun EtsFunctionHeatingIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FunctionHeating,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Radiator top and bottom header pipes
        drawLine(tint, Offset(w * 0.14f, h * 0.22f), Offset(w * 0.86f, h * 0.22f), strokeWidth = 1.4f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.14f, h * 0.82f), Offset(w * 0.86f, h * 0.82f), strokeWidth = 1.4f, cap = StrokeCap.Round)

        // Radiator vertical ribs (4 column sections)
        val ribColor = tint
        val ribWidth = 1.4f
        drawLine(ribColor, Offset(w * 0.24f, h * 0.22f), Offset(w * 0.24f, h * 0.82f), strokeWidth = ribWidth)
        drawLine(ribColor, Offset(w * 0.42f, h * 0.22f), Offset(w * 0.42f, h * 0.82f), strokeWidth = ribWidth)
        drawLine(ribColor, Offset(w * 0.58f, h * 0.22f), Offset(w * 0.58f, h * 0.82f), strokeWidth = ribWidth)
        drawLine(ribColor, Offset(w * 0.76f, h * 0.22f), Offset(w * 0.76f, h * 0.82f), strokeWidth = ribWidth)

        // Thermostatic valve knob on top left
        drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.16f, h * 0.22f), style = Fill)
    }
}

/**
 * Scene Trade Icon (FT-6 scene / macro): Star / preset icon.
 */
@Composable
fun EtsFunctionSceneIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FunctionScene,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // 4-point star macro marker
        val star = Path().apply {
            moveTo(w * 0.50f, h * 0.08f)
            lineTo(w * 0.62f, h * 0.38f)
            lineTo(w * 0.92f, h * 0.50f)
            lineTo(w * 0.62f, h * 0.62f)
            lineTo(w * 0.50f, h * 0.92f)
            lineTo(w * 0.38f, h * 0.62f)
            lineTo(w * 0.08f, h * 0.50f)
            lineTo(w * 0.38f, h * 0.38f)
            close()
        }
        drawPath(star, color = tint.copy(alpha = 0.35f), style = Fill)
        drawPath(star, color = tint, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
    }
}

/**
 * Generic Function Icon (FT-0 custom): Engineering function block with terminals.
 */
@Composable
fun EtsFunctionGenericIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Functional block
        drawRoundRect(
            color = tint.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.64f),
            cornerRadius = CornerRadius(2f),
            style = Fill,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.64f),
            cornerRadius = CornerRadius(2f),
            style = Stroke(width = 1.2f),
        )

        // Input / output terminal pins
        drawLine(tint, Offset(w * 0.04f, h * 0.36f), Offset(w * 0.18f, h * 0.36f), strokeWidth = 1.2f)
        drawLine(tint, Offset(w * 0.04f, h * 0.64f), Offset(w * 0.18f, h * 0.64f), strokeWidth = 1.2f)
        drawLine(tint, Offset(w * 0.82f, h * 0.50f), Offset(w * 0.96f, h * 0.50f), strokeWidth = 1.2f)
    }
}

/**
 * Dispatcher for building trade functions (FT-0 ... FT-9).
 */
@Composable
fun EtsFunctionIcon(
    type: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 16.dp,
) {
    when (type.trim().uppercase()) {
        "FT-1" -> EtsFunctionLightIcon(modifier = modifier, sizeDp = sizeDp)
        "FT-2", "FT-6" -> EtsFunctionDimmerIcon(modifier = modifier, sizeDp = sizeDp)
        "FT-3", "FT-7" -> EtsFunctionBlindsIcon(modifier = modifier, sizeDp = sizeDp)
        "FT-4", "FT-5", "FT-8", "FT-9" -> EtsFunctionHeatingIcon(modifier = modifier, sizeDp = sizeDp)
        "SCENE" -> EtsFunctionSceneIcon(modifier = modifier, sizeDp = sizeDp)
        else -> EtsFunctionLightIcon(modifier = modifier, sizeDp = sizeDp)
    }
}

// ---------------------------------------------------------------------------
// 4. TOPOLOGY ICONS (Area, Line, DIN Device, Com Object)
// ---------------------------------------------------------------------------

/**
 * ETS Area Icon (area.png): Backbone bus loop with radiating nodes.
 */
@Composable
fun EtsAreaIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.FloorPlate,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Central backbone ring
        drawCircle(
            color = tint.copy(alpha = 0.22f),
            radius = w * 0.28f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Fill,
        )
        drawCircle(
            color = tint,
            radius = w * 0.28f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Stroke(width = 1.4f),
        )

        // Radiating line taps
        val branchColor = tint
        drawLine(branchColor, Offset(w * 0.50f, h * 0.06f), Offset(w * 0.50f, h * 0.22f), strokeWidth = 1.3f)
        drawLine(branchColor, Offset(w * 0.50f, h * 0.78f), Offset(w * 0.50f, h * 0.94f), strokeWidth = 1.3f)
        drawLine(branchColor, Offset(w * 0.06f, h * 0.50f), Offset(w * 0.22f, h * 0.50f), strokeWidth = 1.3f)
        drawLine(branchColor, Offset(w * 0.78f, h * 0.50f), Offset(w * 0.94f, h * 0.50f), strokeWidth = 1.3f)

        // Center hub dot
        drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.50f, h * 0.50f), style = Fill)
    }
}

/**
 * ETS Line Icon (line.png): Bus line with coupler branch nodes.
 */
@Composable
fun EtsLineIcon(
    modifier: Modifier = Modifier,
    tint: Color = EtsColors.RoomTeal,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Main bus trunk line
        drawLine(
            color = tint,
            start = Offset(w * 0.08f, h * 0.50f),
            end = Offset(w * 0.92f, h * 0.50f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round,
        )

        // Drop line taps
        drawLine(tint, Offset(w * 0.32f, h * 0.50f), Offset(w * 0.32f, h * 0.82f), strokeWidth = 1.2f)
        drawLine(tint, Offset(w * 0.68f, h * 0.50f), Offset(w * 0.68f, h * 0.18f), strokeWidth = 1.2f)

        // Coupler bus nodes
        drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.32f, h * 0.82f), style = Fill)
        drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.68f, h * 0.18f), style = Fill)
    }
}

/**
 * Authentic ETS DIN-Rail Device Icon (device.png / device_shield_active_16.png).
 * Displays screw terminal blocks on top and bottom, center status window, and
 * overlays the KNX Secure blue shield if [isSecure] is true!
 */
@Composable
fun EtsDeviceIcon(
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Main DIN module enclosure
        drawRoundRect(
            color = tint.copy(alpha = 0.20f),
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(2f),
            style = Fill,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(2f),
            style = Stroke(width = 1.2f),
        )

        // Screw terminals top (3 terminal dots)
        val termColor = tint
        drawRect(termColor, Offset(w * 0.24f, h * 0.04f), Size(w * 0.12f, h * 0.10f))
        drawRect(termColor, Offset(w * 0.44f, h * 0.04f), Size(w * 0.12f, h * 0.10f))
        drawRect(termColor, Offset(w * 0.64f, h * 0.04f), Size(w * 0.12f, h * 0.10f))

        // Screw terminals bottom (3 terminal dots)
        drawRect(termColor, Offset(w * 0.24f, h * 0.86f), Size(w * 0.12f, h * 0.10f))
        drawRect(termColor, Offset(w * 0.44f, h * 0.86f), Size(w * 0.12f, h * 0.10f))
        drawRect(termColor, Offset(w * 0.64f, h * 0.86f), Size(w * 0.12f, h * 0.10f))

        // Center programming LED & button
        drawCircle(
            color = if (isSecure) EtsColors.KnxBlueLight else tint,
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.42f),
            style = Fill,
        )

        // Label stripe
        drawRect(
            color = tint.copy(alpha = 0.6f),
            topLeft = Offset(w * 0.24f, h * 0.60f),
            size = Size(w * 0.52f, h * 0.12f),
        )

        // If KNX Secure: overlay blue shield badge on top-right (device_shield_active_16.png look)
        if (isSecure) {
            val shieldW = w * 0.54f
            val shieldH = h * 0.62f
            val ox = w * 0.46f
            val oy = -h * 0.04f

            val shieldPath = Path().apply {
                moveTo(ox + shieldW * 0.5f, oy)
                lineTo(ox + shieldW, oy + shieldH * 0.20f)
                lineTo(ox + shieldW * 0.95f, oy + shieldH * 0.65f)
                cubicTo(
                    ox + shieldW * 0.85f, oy + shieldH * 0.85f,
                    ox + shieldW * 0.55f, oy + shieldH * 0.98f,
                    ox + shieldW * 0.5f, oy + shieldH,
                )
                cubicTo(
                    ox + shieldW * 0.45f, oy + shieldH * 0.98f,
                    ox + shieldW * 0.15f, oy + shieldH * 0.85f,
                    ox + shieldW * 0.05f, oy + shieldH * 0.65f,
                )
                lineTo(ox, oy + shieldH * 0.20f)
                close()
            }
            drawPath(shieldPath, color = EtsColors.KnxBlue, style = Fill)
            drawPath(shieldPath, color = Color.White, style = Stroke(width = 0.7f))

            // White checkmark inside shield
            val check = Path().apply {
                moveTo(ox + shieldW * 0.26f, oy + shieldH * 0.50f)
                lineTo(ox + shieldW * 0.45f, oy + shieldH * 0.70f)
                lineTo(ox + shieldW * 0.76f, oy + shieldH * 0.32f)
            }
            drawPath(check, color = Color.White, style = Stroke(width = shieldW * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Authentic ETS Communication Object Icon (groupobject.png):
 * Yellow cylinder / barrel plug with connector notches.
 * Overlays KNX Secure blue shield if [isSecure] is true.
 */
@Composable
fun EtsComObjectIcon(
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    sizeDp: Dp = 15.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Yellow barrel body
        val barrel = Path().apply {
            moveTo(w * 0.18f, h * 0.30f)
            lineTo(w * 0.82f, h * 0.30f)
            lineTo(w * 0.82f, h * 0.74f)
            lineTo(w * 0.18f, h * 0.74f)
            close()
        }
        drawPath(barrel, color = EtsColors.ComObjectYellow, style = Fill)
        drawPath(barrel, color = Color(0xFFB45309), style = Stroke(width = 1.1f))

        // Cylinder top rim
        drawOval(
            color = Color(0xFFFDE047),
            topLeft = Offset(w * 0.18f, h * 0.20f),
            size = Size(w * 0.64f, h * 0.22f),
            style = Fill,
        )
        drawOval(
            color = Color(0xFFB45309),
            topLeft = Offset(w * 0.18f, h * 0.20f),
            size = Size(w * 0.64f, h * 0.22f),
            style = Stroke(width = 1.1f),
        )

        // Connector socket notches
        drawRect(Color(0xFF78350F), Offset(w * 0.32f, h * 0.46f), Size(w * 0.10f, h * 0.18f))
        drawRect(Color(0xFF78350F), Offset(w * 0.58f, h * 0.46f), Size(w * 0.10f, h * 0.18f))

        // If KNX Secure: overlay blue shield
        if (isSecure) {
            val shieldW = w * 0.54f
            val shieldH = h * 0.60f
            val ox = w * 0.46f
            val oy = -h * 0.04f
            val sPath = Path().apply {
                moveTo(ox + shieldW * 0.5f, oy)
                lineTo(ox + shieldW, oy + shieldH * 0.20f)
                lineTo(ox + shieldW * 0.95f, oy + shieldH * 0.65f)
                cubicTo(
                    ox + shieldW * 0.85f, oy + shieldH * 0.85f,
                    ox + shieldW * 0.55f, oy + shieldH * 0.98f,
                    ox + shieldW * 0.5f, oy + shieldH,
                )
                cubicTo(
                    ox + shieldW * 0.45f, oy + shieldH * 0.98f,
                    ox + shieldW * 0.15f, oy + shieldH * 0.85f,
                    ox + shieldW * 0.05f, oy + shieldH * 0.65f,
                )
                lineTo(ox, oy + shieldH * 0.20f)
                close()
            }
            drawPath(sPath, color = EtsColors.KnxBlue, style = Fill)
            drawPath(sPath, color = Color.White, style = Stroke(width = 0.7f))
        }
    }
}

// ---------------------------------------------------------------------------
// 5. GROUP ADDRESS HIERARCHY ICONS
// ---------------------------------------------------------------------------

/**
 * Authentic ETS Main Group Icon (maingroup.png): Green folder.
 */
@Composable
fun EtsMainGroupFolderIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Folder back tab
        val tab = Path().apply {
            moveTo(w * 0.08f, h * 0.30f)
            lineTo(w * 0.42f, h * 0.30f)
            lineTo(w * 0.50f, h * 0.42f)
            lineTo(w * 0.92f, h * 0.42f)
            lineTo(w * 0.92f, h * 0.84f)
            lineTo(w * 0.08f, h * 0.84f)
            close()
        }
        drawPath(tab, color = EtsColors.EtsGreen.copy(alpha = 0.5f), style = Fill)

        // Folder front leaf
        val front = Path().apply {
            moveTo(w * 0.08f, h * 0.44f)
            lineTo(w * 0.92f, h * 0.44f)
            lineTo(w * 0.86f, h * 0.88f)
            lineTo(w * 0.08f, h * 0.88f)
            close()
        }
        drawPath(front, color = EtsColors.EtsGreen, style = Fill)
        drawPath(front, color = Color.White.copy(alpha = 0.4f), style = Stroke(width = 0.9f))
    }
}

/**
 * Authentic ETS Middle Group Icon (middlegroup.png): Blue folder.
 */
@Composable
fun EtsMiddleGroupFolderIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Folder back tab
        val tab = Path().apply {
            moveTo(w * 0.08f, h * 0.30f)
            lineTo(w * 0.42f, h * 0.30f)
            lineTo(w * 0.50f, h * 0.42f)
            lineTo(w * 0.92f, h * 0.42f)
            lineTo(w * 0.92f, h * 0.84f)
            lineTo(w * 0.08f, h * 0.84f)
            close()
        }
        drawPath(tab, color = EtsColors.EtsFolderBlue.copy(alpha = 0.5f), style = Fill)

        // Folder front leaf
        val front = Path().apply {
            moveTo(w * 0.08f, h * 0.44f)
            lineTo(w * 0.92f, h * 0.44f)
            lineTo(w * 0.86f, h * 0.88f)
            lineTo(w * 0.08f, h * 0.88f)
            close()
        }
        drawPath(front, color = EtsColors.EtsFolderBlue, style = Fill)
        drawPath(front, color = Color.White.copy(alpha = 0.4f), style = Stroke(width = 0.9f))
    }
}

/**
 * Authentic ETS Sub Group (Group Address) Tag (subgroup.png).
 * Overlays KNX Secure blue shield if [isSecure] is true!
 */
@Composable
fun EtsGroupAddressTagIcon(
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    sizeDp: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        // Address tag body
        val tagPath = Path().apply {
            moveTo(w * 0.12f, h * 0.26f)
            lineTo(w * 0.60f, h * 0.26f)
            lineTo(w * 0.88f, h * 0.54f)
            lineTo(w * 0.60f, h * 0.82f)
            lineTo(w * 0.12f, h * 0.82f)
            close()
        }
        val tagColor = if (isSecure) EtsColors.KnxBlue else EtsColors.RoomTeal
        drawPath(tagPath, color = tagColor.copy(alpha = 0.25f), style = Fill)
        drawPath(tagPath, color = tagColor, style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Tag eyelet / hole
        drawCircle(
            color = tagColor,
            radius = w * 0.08f,
            center = Offset(w * 0.30f, h * 0.54f),
            style = Stroke(width = 1.1f),
        )

        // Overlay secure shield badge on top right if secure
        if (isSecure) {
            val shieldW = w * 0.54f
            val shieldH = h * 0.60f
            val ox = w * 0.46f
            val oy = -h * 0.04f
            val sPath = Path().apply {
                moveTo(ox + shieldW * 0.5f, oy)
                lineTo(ox + shieldW, oy + shieldH * 0.20f)
                lineTo(ox + shieldW * 0.95f, oy + shieldH * 0.65f)
                cubicTo(
                    ox + shieldW * 0.85f, oy + shieldH * 0.85f,
                    ox + shieldW * 0.55f, oy + shieldH * 0.98f,
                    ox + shieldW * 0.5f, oy + shieldH,
                )
                cubicTo(
                    ox + shieldW * 0.45f, oy + shieldH * 0.98f,
                    ox + shieldW * 0.15f, oy + shieldH * 0.85f,
                    ox + shieldW * 0.05f, oy + shieldH * 0.65f,
                )
                lineTo(ox, oy + shieldH * 0.20f)
                close()
            }
            drawPath(sPath, color = EtsColors.KnxBlue, style = Fill)
            drawPath(sPath, color = Color.White, style = Stroke(width = 0.7f))
        }
    }
}

// ---------------------------------------------------------------------------
// 6. COMMUNICATION OBJECT FLAGS STRIP (C R W T U)
// ---------------------------------------------------------------------------

/**
 * Authentic ETS Group Object Flags Strip: standard square flag badges [C] [R] [W] [T] [U].
 * In ETS, active flags show crisp dark letters on solid background; inactive flags show faint dashes.
 */
@Composable
fun EtsComFlagsBadgeStrip(
    flags: ComObjectFlags,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        "C" to flags.c,
        "R" to flags.r,
        "W" to flags.w,
        "T" to flags.t,
        "U" to flags.u,
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((letter, active) in items) {
            val bg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            val border = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            Box(
                modifier = Modifier
                    .background(bg, EtsShapes.Badge)
                    .border(0.7.dp, border, EtsShapes.Badge)
                    .padding(horizontal = 3.5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (active) letter else "-",
                    fontSize = 8.5.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = fg,
                )
            }
        }
    }
}
