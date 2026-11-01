@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.launcher.data.*
import com.example.launcher.ui.theme.Tokens as T
import kotlinx.coroutines.launch

@Composable fun ConnectivityPopup(type: ConnectivityType, device: DeviceState, onDismiss: () -> Unit, onMore: () -> Unit, onWifiControls: () -> Unit, onBluetoothPermission: () -> Unit, onEnableBluetooth: () -> Unit,
    onWifiPermission: () -> Unit, onScanWifi: () -> Unit, onLocationSettings: () -> Unit, onConnectWifi: (WifiNetwork, String) -> Unit,
    onScanBluetooth: () -> Unit, onPairBluetooth: (String) -> Unit) {
    val density = LocalDensity.current
    val fallbackHeight = LocalConfiguration.current.screenHeightDp.dp
    val rootHeight = LocalView.current.rootView.height
    val windowHeight = if (rootHeight > 0) with(density) { rootHeight.toDp() } else fallbackHeight
    val topInset = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
    val maximumHeight = (windowHeight - topInset).coerceAtLeast(0.dp)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit = {}) { scope.launch { sheet.hide(); onDismiss(); after() } }
    val wifi = type == ConnectivityType.WIFI
    val enabled = if (wifi) device.wifi else device.bluetooth == true
    val title = if (wifi) "Wi-Fi" else "Bluetooth"
    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }
    var password by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }, sheetState = sheet, containerColor = T.surface, contentColor = T.text, shape = T.widgetCard,
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(40.dp).height(5.dp).background(T.handle, CircleShape)) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = (maximumHeight - 21.dp).coerceAtLeast(0.dp)).testTag("connectivity-sheet-content").navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Glyph(if (wifi) "wifi" else "bluetooth", T.amber)
                Text(title, style = T.pageTitle, modifier = Modifier.weight(1f).semantics { heading() })
                IconToggleButton(checked = enabled, onCheckedChange = { if (wifi) onWifiControls() else if (!device.bluetoothGranted) onBluetoothPermission() else if (!enabled) onEnableBluetooth() else onMore() },
                    enabled = wifi || device.bluetoothAvailable, modifier = Modifier.semantics { contentDescription = "$title power"; stateDescription = if (enabled) "On" else "Off" }) {
                    Glyph("power", if (enabled) T.amber else T.muted)
                }
                IconAction("close", "Close $title popup") { close() }
            }
            Column(Modifier.fillMaxWidth().background(T.surfaceRaised, T.appTile).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when { !wifi && !device.bluetoothAvailable -> "Unavailable"; !wifi && !device.bluetoothGranted -> "Access needed"; enabled -> "On"; else -> "Off" }, style = T.body, color = if (enabled) T.mint else T.muted)
                if (wifi) Text(if (device.wifiConnected) device.wifiName ?: "Connected to Wi-Fi" else if (device.wifi) "Not connected to a network" else "Wi-Fi is turned off", style = T.subtitle)
                else Text(if (enabled) "Available for your paired devices" else "Connect headphones, speakers and other devices", style = T.subtitle)
            }
            if (wifi) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Label("AVAILABLE NETWORKS")
                    TextButton(onClick = onScanWifi, enabled = device.wifiScanGranted && device.locationEnabled && enabled) { Text("Refresh", color = T.amber) }
                }
                when {
                    !enabled -> Text("Turn on Wi-Fi to find nearby networks.", style = T.subtitle)
                    !device.wifiScanGranted -> { Text("Android requires precise location access to list nearby Wi-Fi networks. Your location is not stored.", style = T.subtitle); ActionPill("Allow network scanning", onWifiPermission) }
                    !device.locationEnabled -> { Text("Turn on device location to scan Wi-Fi networks.", style = T.subtitle); ActionPill("Location settings", onLocationSettings) }
                    device.wifiNetworks.isEmpty() -> Text("No networks found yet. Tap Refresh or use Wi-Fi controls.", style = T.subtitle)
                    else -> device.wifiNetworks.forEach { network ->
                        Row(Modifier.fillMaxWidth().clickable { password = ""; selectedNetwork = network }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph("wifi", T.mint)
                            Column(Modifier.weight(1f)) { Text(network.ssid, style = T.body); Text(if (network.ssid == device.wifiName) "Connected" else if (network.capabilities.contains("PSK") || network.capabilities.contains("SAE") || network.capabilities.contains("EAP") || network.capabilities.contains("WEP")) "Secured" else "Open network", style = T.caption) }
                        }
                    }
                }
                ActionPill("Wi-Fi controls", onWifiControls, Modifier.fillMaxWidth())
                Text("Android confirms network changes and controls Wi-Fi power.", style = T.caption)
            } else if (device.bluetoothAvailable) {
                if (!device.bluetoothGranted) {
                    Text("Allow nearby-device access to show your paired devices.", style = T.subtitle)
                    ActionPill("Allow device access", onBluetoothPermission, Modifier.fillMaxWidth(), primary = true)
                } else {
                    if (!enabled) ActionPill("Turn on Bluetooth", onEnableBluetooth, Modifier.fillMaxWidth(), primary = true)
                    Label("PAIRED DEVICES")
                    if (device.pairedDevices.isEmpty()) Text("No paired devices", style = T.subtitle)
                    device.pairedDevices.take(6).forEach { paired ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { close(onMore) }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph("bluetooth", T.sky)
                            Column { Text(paired.name, style = T.body); Text("Paired", style = T.caption) }
                        }
                    }
                    if (enabled) {
                        Label("NEARBY DEVICES")
                        device.nearbyBluetooth.forEach { nearby ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPairBluetooth(nearby.address) }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Glyph("bluetooth", T.mint)
                                Column { Text(nearby.name, style = T.body); Text("Tap to pair", style = T.caption) }
                            }
                        }
                        if (device.nearbyBluetooth.isEmpty()) Text(if (device.bluetoothScanning) "Searching for devices…" else "Make your device discoverable, then tap Find devices.", style = T.subtitle)
                        ActionPill(if (device.bluetoothScanning) "Searching…" else "Find devices", onScanBluetooth, Modifier.fillMaxWidth())
                    }
                    Text("Android manages audio connections and Bluetooth power. Tap a device to manage its connection.", style = T.caption)
                }
            }
            HorizontalDivider(color = T.surfaceRaised)
            ActionPill("More settings", { close(onMore) }, Modifier.fillMaxWidth())
        }
    }
    selectedNetwork?.let { network ->
        val secured = network.capabilities.contains("PSK") || network.capabilities.contains("SAE")
        AlertDialog(onDismissRequest = { selectedNetwork = null; password = "" }, containerColor = T.surface, title = { Text(network.ssid, color = T.text) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (secured) "Enter this network’s password. Android will ask you to confirm saving and connecting." else "Android will confirm this network. Saved, enterprise and WEP networks can be managed in Wi-Fi controls.", style = T.subtitle)
                if (secured) OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            } },
            confirmButton = { TextButton(onClick = { onConnectWifi(network, password); selectedNetwork = null; password = "" }, enabled = !secured || password.length >= 8) { Text("Connect", color = T.amber) } },
            dismissButton = { TextButton(onClick = { selectedNetwork = null; password = "" }) { Text("Cancel", color = T.muted) } })
    }
}
