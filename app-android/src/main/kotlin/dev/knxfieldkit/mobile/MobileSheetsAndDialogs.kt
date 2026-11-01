package dev.knxfieldkit.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.knxfieldkit.app.*
import dev.knxfieldkit.model.*
import java.io.File

@Composable
private fun MobileBottomSheetScrim(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val noOpInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clickable(interactionSource = noOpInteraction, indication = null, onClick = {}),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MobileColors.BgTopBar,
            border = BorderStroke(1.dp, MobileColors.BorderStrong),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MobileColors.BorderStrong),
                )
                content()
            }
        }
    }
}

@Composable
fun MobileProjectMenuSheet(
    state: AppUiState,
    onOpenFile: () -> Unit,
    onLoadEncryptedSample: () -> Unit,
    onLoadDemoVilla: () -> Unit,
    onOpenRecent: (File) -> Unit,
    onShareMarkdown: () -> Unit,
    onShareHtml: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "KNX Project & Reports",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MobileColors.TextPrimary,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MobileColors.TextSecondary)
            }
        }

        Button(
            onClick = {
                onDismiss()
                onOpenFile()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = Color(0xFF042F2E)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open .knxproj or .knxkeys File", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = {
                onDismiss()
                onLoadEncryptedSample()
            },
            border = BorderStroke(1.dp, MobileColors.AccentAmber),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MobileColors.AccentAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Test Encrypted ETS6 Sample (knx2026)", color = MobileColors.AccentAmber, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = {
                onDismiss()
                onLoadDemoVilla()
            },
            border = BorderStroke(1.dp, MobileColors.AccentSky),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.HomeWork, contentDescription = null, tint = MobileColors.AccentSky, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Load Built-In Demo (Villa Horizon)", color = MobileColors.AccentSky)
        }

        HorizontalDivider(color = MobileColors.BorderSubtle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onShareHtml()
                },
                border = BorderStroke(1.dp, MobileColors.BorderStrong),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MobileColors.AccentEmerald, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share HTML", fontSize = 12.sp, color = MobileColors.TextPrimary)
            }
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onShareMarkdown()
                },
                border = BorderStroke(1.dp, MobileColors.BorderStrong),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MobileColors.AccentSky, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share MD", fontSize = 12.sp, color = MobileColors.TextPrimary)
            }
        }

        if (state.recentFiles.isNotEmpty()) {
            Text(
                text = "RECENT FILES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MobileColors.TextSecondary,
            )
            state.recentFiles.take(4).forEach { recent ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MobileColors.BgCard,
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenRecent(recent)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MobileColors.AccentSky, modifier = Modifier.size(16.dp))
                        Text(
                            text = recent.name,
                            fontSize = 12.sp,
                            color = MobileColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MobilePasswordSheet(
    prompt: PasswordPromptState,
    onUnlock: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember(prompt.file) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    MobileBottomSheetScrim(onDismiss = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MobileColors.AccentAmber)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (prompt.isKeyring) "Unlock KNX Secure Keyring" else "Unlock Protected ETS Project",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MobileColors.TextPrimary,
                )
                Text(
                    text = prompt.file.name,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MobileColors.AccentSky,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MobileBadge(if (prompt.isKeyring) ".knxkeys" else "AES-256", MobileColors.AccentAmber)
        }

        Text(
            text = "Supports ETS6 PBKDF2-HMAC-SHA256 (UTF-16LE, 65536 rounds) + ETS4/ETS5 archives offline on device.",
            fontSize = 11.sp,
            color = MobileColors.TextSecondary,
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Archive Password") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password",
                        tint = MobileColors.TextSecondary,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MobileColors.BgInput,
                unfocusedContainerColor = MobileColors.BgInput,
                focusedBorderColor = MobileColors.AccentAmber,
                unfocusedBorderColor = MobileColors.BorderStrong,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MobileActionPill("Fill Sample: knx2026", MobileColors.AccentSky) {
                password = "knx2026"
            }
        }

        if (prompt.errorMessage != null) {
            Text(
                text = prompt.errorMessage ?: "",
                fontSize = 12.sp,
                color = MobileColors.AccentRed,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onUnlock(password) },
                enabled = password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = Color(0xFF042F2E)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Unlock", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MobileGatewayConnectSheet(
    state: AppUiState,
    controller: AppController,
    onDismiss: () -> Unit,
) {
    var host by remember(state.savedGatewayHost) { mutableStateOf(state.savedGatewayHost) }
    var portText by remember(state.savedGatewayPort) { mutableStateOf(state.savedGatewayPort.toString()) }
    var useNat by remember { mutableStateOf(true) }

    MobileBottomSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "KNX Bus Connection",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MobileColors.TextPrimary,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MobileColors.TextSecondary)
            }
        }

        val connectedState = state.connectionState as? ConnectionState.Connected
        if (connectedState != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MobileColors.AccentEmerald.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MobileColors.AccentEmerald.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CONNECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MobileColors.AccentEmerald)
                        Text(connectedState.description, fontSize = 12.sp, color = MobileColors.TextPrimary)
                    }
                    MobileActionPill("Disconnect", MobileColors.AccentRed) {
                        controller.disconnectBus()
                    }
                }
            }
        }

        // 1-Tap Virtual KNX Bus Simulator
        Button(
            onClick = { controller.connectSimulator() },
            colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = Color(0xFF042F2E)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Virtual KNX Bus (Offline Field Lab)", fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = MobileColors.BorderSubtle)

        // Wi-Fi Multicast Gateway Discovery
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "WI-FI KNXNET/IP DISCOVERY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MobileColors.TextSecondary,
            )
            MobileActionPill(
                label = if (state.isDiscoveringGateways) "Scanning..." else "Scan Wi-Fi",
                accent = MobileColors.AccentSky,
            ) {
                if (!state.isDiscoveringGateways) controller.discoverGateways()
            }
        }

        state.discoveredGateways.forEach { gw ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MobileColors.BgCard,
                border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(gw.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MobileColors.TextPrimary)
                        Text(
                            text = "${gw.host}:${gw.port} • ${gw.individualAddress ?: "-"}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MobileColors.AccentSky,
                        )
                    }
                    MobileActionPill("Connect", MobileColors.AccentEmerald) {
                        if (gw.host == "127.0.0.1" && gw.name.contains("Simulated")) {
                            controller.connectSimulator()
                        } else {
                            controller.connectTunneling(gw.host, gw.port, useNat = true)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MobileColors.BorderSubtle)

        Text(
            text = "DIRECT KNXNET/IP TUNNELING OR ROUTING",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MobileColors.TextSecondary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Gateway IP") },
                singleLine = true,
                modifier = Modifier.weight(1.8f),
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("NAT Traversal Mode (Recommended on Wi-Fi)", fontSize = 12.sp, color = MobileColors.TextPrimary)
                Text("Uses 0.0.0.0:0 HPAI across VLANs / mobile Wi-Fi", fontSize = 10.sp, color = MobileColors.TextSecondary)
            }
            Switch(checked = useNat, onCheckedChange = { useNat = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 3671
                    controller.connectTunneling(host, port, useNat)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentSky, contentColor = Color(0xFF082F49)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Connect Tunnel", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { controller.connectRouting() },
                border = BorderStroke(1.dp, MobileColors.AccentSky),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("IP Routing", color = MobileColors.AccentSky)
            }
        }
    }
}

@Composable
fun MobileGroupInspectorSheet(
    ga: GroupAddressInfo,
    project: KnxProject?,
    liveEntry: GroupValueEntry?,
    controller: AppController,
    onDismiss: () -> Unit,
) {
    val gaText = ga.address.toString()
    MobileBottomSheetScrim(onDismiss = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MobileBadge(gaText, MobileColors.AccentSky)
            Text(
                text = ga.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MobileColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MobileColors.TextSecondary)
            }
        }

        if (ga.description.isNotBlank()) {
            Text(ga.description, fontSize = 12.sp, color = MobileColors.TextSecondary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MobileBadge("DPT ${ga.dpt ?: "raw"}", MobileColors.AccentPurple)
            MobileBadge("Live: ${liveEntry?.valueText ?: "—"}", MobileColors.AccentEmerald)
            liveEntry?.latencyMs?.let { MobileBadge("${it}ms", MobileColors.AccentAmber) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MobileActionPill("GroupRead", MobileColors.AccentSky) {
                controller.readGroupAddress(gaText)
            }
            MobileActionPill("Open Smart Sender →", MobileColors.AccentEmerald) {
                controller.selectGroupAddressForSender(gaText)
                controller.selectTab(AppTab.SENDER)
                onDismiss()
            }
            MobileActionPill("Walk-Test Blink", MobileColors.AccentAmber) {
                controller.startWalkTestBlink(gaText)
                onDismiss()
            }
        }

        HorizontalDivider(color = MobileColors.BorderSubtle)

        Text(
            text = "LINKED DEVICE GROUP OBJECTS (${ga.linkedObjects.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MobileColors.TextSecondary,
        )

        if (ga.linkedObjects.isEmpty()) {
            Text("No ComObject links recorded in project XML.", fontSize = 12.sp, color = MobileColors.TextMuted)
        } else {
            ga.linkedObjects.forEach { link ->
                val devName = link.deviceAddress?.let { project?.findDevice(it)?.name } ?: "Device"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MobileColors.BgCard,
                    border = BorderStroke(1.dp, MobileColors.BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            link.deviceAddress?.let { addr ->
                                controller.openDeviceObjectsInExplorer(addr)
                                onDismiss()
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            link.deviceAddress?.let { MobileBadge(it.toString(), MobileColors.AccentEmerald) }
                            link.objectNumber?.let { MobileBadge("#$it", MobileColors.AccentPurple) }
                            Text(
                                text = devName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MobileColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${link.name} — ${link.functionText}".trim(' ', '—'),
                                fontSize = 11.sp,
                                color = MobileColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            MobileComFlagsStrip(link.flags)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileTelegramDetailSheet(
    item: EnrichedTelegram,
    onReplay: () -> Unit,
    onOpenSender: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = item.telegram
    val dstText = t.destinationGroup?.toString() ?: t.destinationIndividual?.toString() ?: "Broadcast"

    MobileBottomSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MobileBadge(t.apci.label, MobileColors.AccentSky)
                if (item.isUnmappedGroup) MobileBadge("GHOST GA", MobileColors.AccentAmber)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MobileColors.TextSecondary)
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MobileColors.BgCard,
            border = BorderStroke(1.dp, MobileColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Source: ${t.source} (${item.sourceDeviceName ?: "Unknown Device"})", fontSize = 12.sp, color = MobileColors.TextPrimary)
                Text("Destination: $dstText (${item.destinationName ?: "Unmapped"})", fontSize = 12.sp, color = MobileColors.TextPrimary)
                Text("Decoded Value: ${item.decodedValue.ifBlank { "—" }} (DPT ${item.dptId ?: "raw"})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MobileColors.AccentEmerald)
                Text("Hop Count: ${t.hopCount} • Priority: ${t.priority} • Repeated: ${t.repeated}", fontSize = 11.sp, color = MobileColors.TextSecondary)
                Text("Payload Hex: ${HexBytes.toHex(t.payload).ifBlank { "(empty)" }}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MobileColors.AccentSky)
                if (t.rawFrameHex.isNotBlank()) {
                    Text("CEMI Frame: ${t.rawFrameHex}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MobileColors.TextMuted)
                }
            }
        }

        if (t.destinationGroup != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onReplay()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MobileColors.AccentEmerald, contentColor = Color(0xFF042F2E)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Replay Frame", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        onOpenSender(t.destinationGroup.toString())
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, MobileColors.AccentSky),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Open Sender", color = MobileColors.AccentSky)
                }
            }
        }
    }
}
