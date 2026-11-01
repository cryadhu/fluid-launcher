package com.example.launcher

import androidx.activity.compose.setContent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.launcher.data.*
import com.example.launcher.ui.ConnectivityPopup
import com.example.launcher.ui.theme.LauncherTheme
import org.junit.*
import org.junit.Assert.*

class ConnectivityPopupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun selectedWifiRequiresPasswordBeforeConnect() {
        val network = WifiNetwork("Test secured network", "[WPA2-PSK-CCMP][ESS]", -45)
        var selected: WifiNetwork? = null
        var password: String? = null
        compose.activity.setContentForTest {
            ConnectivityPopup(ConnectivityType.WIFI, DeviceState(wifi = true, wifiScanGranted = true, locationEnabled = true, wifiNetworks = listOf(network)), {}, {}, {}, {}, {}, {}, {}, {}, { item, text -> selected = item; password = text }, {}, {})
        }
        compose.onNodeWithText(network.ssid).performClick()
        compose.onNodeWithText("Connect").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("example-password")
        compose.onNodeWithText("Connect").performClick()
        compose.runOnIdle { assertEquals(network, selected); assertEquals("example-password", password) }
    }
    @Test fun nearbyBluetoothCanRequestPairing() {
        var paired: String? = null
        compose.activity.setContentForTest {
            ConnectivityPopup(ConnectivityType.BLUETOOTH, DeviceState(bluetooth = true, bluetoothGranted = true, nearbyBluetooth = listOf(PairedDevice("Test headphones", "00:11:22:33:44:55"))), {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, { paired = it })
        }
        compose.onNodeWithText("Test headphones").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("00:11:22:33:44:55", paired) }
    }
    @Test fun longWifiListStopsBelowStatusBar() { assertStatusBarClearance(ConnectivityType.WIFI) }
    @Test fun longBluetoothListStopsBelowStatusBar() { assertStatusBarClearance(ConnectivityType.BLUETOOTH) }

    private fun assertStatusBarClearance(type: ConnectivityType) {
        val device = DeviceState(wifi = true, wifiScanGranted = true, locationEnabled = true,
            wifiNetworks = (1..50).map { WifiNetwork("Network $it", "[ESS]", -40) },
            bluetooth = true, bluetoothGranted = true,
            nearbyBluetooth = (1..50).map { PairedDevice("Device $it", "00:11:22:33:44:55") })
        compose.activity.setContentForTest {
            ConnectivityPopup(type, device, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
        }
        val bounds = compose.onNodeWithTag("connectivity-sheet-content").fetchSemanticsNode().boundsInWindow
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), "sheet-${type.name}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        val inset = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
        val handleHeight = 21 * compose.activity.resources.displayMetrics.density
        assertTrue("Expanded sheet must stay below status bar: content top=${bounds.top}, inset=$inset", bounds.top - handleHeight >= inset - 2)
        compose.onNodeWithText("More settings").performScrollTo().assertIsDisplayed()
    }
}

private fun MainActivity.setContentForTest(content: @androidx.compose.runtime.Composable () -> Unit) {
    setContent { LauncherTheme(content = content) }
}
