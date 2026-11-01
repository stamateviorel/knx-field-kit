package dev.knxfieldkit.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.knxfieldkit.model.*

@Composable
fun KnxFieldKitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2ECC71),
            secondary = Color(0xFF38BDF8),
            tertiary = Color(0xFFF59E0B),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            surfaceVariant = Color(0xFF334155),
            error = Color(0xFFEF4444)
        ),
        content = content
    )
}

@Composable
fun PasswordUnlockDialog(prompt: PasswordPromptState, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember(prompt.file) { mutableStateOf("") }
    var showPlain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prompt.isKeyring) "Unlock KNX Secure Keyring (.knxkeys)" else "Unlock Password-Protected .knxproj") },
        text = {
            Column {
                Text(prompt.file.name)
                Text("Supports ETS6 (UTF-16LE PBKDF2-SHA256 AES-256) & ETS4/5 encryption")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (showPlain) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPlain = !showPlain }) {
                            Text(if (showPlain) "Hide" else "Show")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (prompt.errorMessage != null) {
                    Text(prompt.errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GatewayConnectDialog(state: AppUiState, controller: AppController) {
    var host by remember { mutableStateOf("192.168.1.10") }
    var portText by remember { mutableStateOf("3671") }
    var useNat by remember { mutableStateOf(false) }
    var secureUserPwd by remember { mutableStateOf("") }
    var secureAuthCode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { controller.toggleConnectDialog(false) },
        title = { Text("Connect to KNX Gateway") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.connectSimulator() }) {
                        Text("Virtual KNX Simulator")
                    }
                    OutlinedButton(onClick = { controller.discoverGateways() }, enabled = !state.isDiscoveringGateways) {
                        Text(if (state.isDiscoveringGateways) "Scanning..." else "Scan LAN Gateways")
                    }
                    OutlinedButton(onClick = { controller.connectRouting() }) {
                        Text("IP Routing")
                    }
                }
                if (state.discoveredGateways.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp)) {
                        items(state.discoveredGateways) { gw ->
                            OutlinedButton(
                                onClick = {
                                    host = gw.host
                                    portText = gw.port.toString()
                                    if (gw.host == "127.0.0.1") controller.connectSimulator()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${gw.name} (${gw.host}:${gw.port}) [${gw.individualAddress ?: "-"}]")
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Gateway Host / IP") }, singleLine = true, modifier = Modifier.weight(0.7f))
                    OutlinedTextField(value = portText, onValueChange = { portText = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(0.3f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useNat, onCheckedChange = { useNat = it })
                    Text("Enable NAT Mode (Route-Back)")
                }
                OutlinedTextField(value = secureUserPwd, onValueChange = { secureUserPwd = it }, label = { Text("KNX IP Secure User Password (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = secureAuthCode, onValueChange = { secureAuthCode = it }, label = { Text("Device Auth Code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { controller.connectTunneling(host, portText.toIntOrNull() ?: 3671, useNat, secureUserPwd, secureAuthCode) }) {
                Text("Connect Tunneling")
            }
        },
        dismissButton = {
            TextButton(onClick = { controller.toggleConnectDialog(false) }) {
                Text("Close")
            }
        }
    )
}
