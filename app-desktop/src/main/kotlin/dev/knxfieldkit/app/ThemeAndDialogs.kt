package dev.knxfieldkit.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KnxFieldKitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (isDark) WorkbenchDarkPalette else WorkbenchLightPalette
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accentEmerald,
            secondary = palette.accentSky,
            tertiary = palette.accentAmber,
            background = palette.bgCanvas,
            surface = palette.bgPanel,
            surfaceVariant = palette.bgHeader,
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
            surface = palette.bgPanel,
            surfaceVariant = palette.bgHeader,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.borderSubtle,
            outlineVariant = palette.borderStrong,
            error = palette.accentRed,
        )
    }

    CompositionLocalProvider(LocalWorkbenchColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(5.dp),
                medium = RoundedCornerShape(6.dp),
                large = RoundedCornerShape(8.dp),
            ),
            content = content,
        )
    }
}

@Composable
fun PasswordUnlockDialog(prompt: PasswordPromptState, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember(prompt.file) { mutableStateOf("") }
    var showPlain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WorkbenchColors.BgPanel,
        titleContentColor = WorkbenchColors.TextPrimary,
        textContentColor = WorkbenchColors.TextSecondary,
        shape = RoundedCornerShape(8.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Key, contentDescription = null, tint = WorkbenchColors.AccentAmber, modifier = Modifier.size(18.dp))
                Text(
                    text = if (prompt.isKeyring) "Unlock KNX Secure Keyring (.knxkeys)" else "Unlock Password-Protected .knxproj",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(prompt.file.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = WorkbenchColors.AccentSky)
                Text(
                    text = "Supports ETS6 (UTF-16LE PBKDF2-HMAC-SHA256 + AES-256) & ETS4/5 archive encryption.",
                    fontSize = 11.sp,
                    color = WorkbenchColors.TextSecondary,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Project / Keyring Password", fontSize = 11.sp) },
                    visualTransformation = if (showPlain) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        DesktopButton(
                            text = if (showPlain) "Hide" else "Show",
                            onClick = { showPlain = !showPlain },
                            compact = true,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                prompt.errorMessage?.let { err ->
                    Text(err, color = WorkbenchColors.AccentRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            DesktopPrimaryButton(
                text = "Unlock Archive",
                icon = Icons.Default.LockOpen,
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty(),
            )
        },
        dismissButton = {
            DesktopButton(text = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
fun GatewayConnectDialog(state: AppUiState, controller: AppController) {
    var host by remember(state.savedGatewayHost) { mutableStateOf(state.savedGatewayHost) }
    var portText by remember(state.savedGatewayPort) { mutableStateOf(state.savedGatewayPort.toString()) }
    var useNat by remember(state.savedGatewayUseNat) { mutableStateOf(state.savedGatewayUseNat) }
    var secureUserPwd by remember { mutableStateOf("") }
    var secureAuthCode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { controller.toggleConnectDialog(false) },
        containerColor = WorkbenchColors.BgPanel,
        shape = RoundedCornerShape(8.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Router, contentDescription = null, tint = WorkbenchColors.AccentSky, modifier = Modifier.size(18.dp))
                Text("KNXnet/IP Interface & Bus Connection", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WorkbenchColors.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(460.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DesktopPrimaryButton(
                        text = if (state.isDiscoveringGateways) "Scanning LAN..." else "Scan LAN Gateways",
                        icon = Icons.Default.Search,
                        onClick = { controller.discoverGateways() },
                        enabled = !state.isDiscoveringGateways,
                    )
                    DesktopButton(
                        text = "IP Routing (224.0.23.12)",
                        onClick = { controller.connectRouting() },
                    )
                }
                if (state.discoveredGateways.isNotEmpty()) {
                    Surface(
                        color = WorkbenchColors.BgCanvas,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, WorkbenchColors.BorderSubtle),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.discoveredGateways) { gw ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(WorkbenchColors.BgHeader)
                                        .border(1.dp, WorkbenchColors.BorderSubtle, RoundedCornerShape(4.dp))
                                        .clickable {
                                            host = gw.host
                                            portText = gw.port.toString()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(gw.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WorkbenchColors.TextPrimary)
                                    Text("${gw.host}:${gw.port} [${gw.individualAddress ?: "-"}]", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WorkbenchColors.AccentSky)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactInputField(value = host, onValueChange = { host = it }, prefixLabel = "Host", modifier = Modifier.weight(0.7f))
                    CompactInputField(value = portText, onValueChange = { portText = it }, prefixLabel = "Port", modifier = Modifier.weight(0.3f))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { useNat = !useNat },
                ) {
                    Checkbox(checked = useNat, onCheckedChange = { useNat = it }, modifier = Modifier.size(18.dp))
                    Text("Enable NAT / Route-Back Mode (VPN or Docker subnets)", fontSize = 11.sp, color = WorkbenchColors.TextSecondary)
                }
                CompactInputField(
                    value = secureUserPwd,
                    onValueChange = { secureUserPwd = it },
                    prefixLabel = "IP Secure Pwd",
                    placeholder = "Optional KNX IP Secure user password",
                    monospace = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                CompactInputField(
                    value = secureAuthCode,
                    onValueChange = { secureAuthCode = it },
                    prefixLabel = "Auth Code",
                    placeholder = "Optional device authentication code",
                    monospace = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            DesktopPrimaryButton(
                text = "Connect Tunneling",
                icon = Icons.Default.Router,
                color = WorkbenchColors.AccentSky,
                onClick = { controller.connectTunneling(host, portText.toIntOrNull() ?: 3671, useNat, secureUserPwd, secureAuthCode) },
            )
        },
        dismissButton = {
            DesktopButton(text = "Close", onClick = { controller.toggleConnectDialog(false) })
        },
    )
}
