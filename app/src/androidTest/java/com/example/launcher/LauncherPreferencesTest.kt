package com.example.launcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.launcher.data.NewsCategory
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.media.AudioManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.content.IntentFilter
import android.provider.Settings

@RunWith(AndroidJUnit4::class)
class LauncherPreferencesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded }
    }
    @Test fun topicsCanBeSelectedAndSurviveRecreation() {
        compose.runOnIdle { model.selectNews(NewsCategory.all); model.page(0) }
        compose.onNodeWithContentDescription("News settings").performScrollTo().performClick()
        compose.onNodeWithText("Clear all").performClick()
        compose.onNodeWithText("Sports").performClick()
        compose.runOnIdle { assertEquals(setOf(NewsCategory.SPORTS), model.uiState.value.selectedNews) }
        compose.onNodeWithContentDescription("Back to news").performClick()
        compose.activityRule.scenario.recreate()
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded }
        compose.runOnIdle { assertEquals(setOf(NewsCategory.SPORTS), model.uiState.value.selectedNews); model.selectNews(NewsCategory.all) }
    }
    @Test fun connectivityTilesOpenLocalSheets() {
        compose.runOnIdle { model.page(2) }
        compose.onNodeWithText("Wi-Fi").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close Wi-Fi popup").assertIsDisplayed()
        compose.onNodeWithText("More settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Wi-Fi popup").performClick()
        compose.onNodeWithText("Bluetooth").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close Bluetooth popup").assertIsDisplayed()
        compose.onNodeWithText("More settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Bluetooth popup").performClick()
    }
    @Test fun quickActionsMatchReferenceAndMobileReplacesTorchTile() {
        compose.runOnIdle { model.page(2) }
        compose.onNodeWithText("Mobile data").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("QUICK ACTIONS").performScrollTo().assertIsDisplayed()
        listOf("Scan", "Translate", "Calculator", "Timer", "QR code", "Voice memo", "Airplane mode", "Flashlight").forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("QUICK ACTIONS").performScrollTo()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), "today-quick-actions.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun soundCyclesThroughAllThreeModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("cmd notification allow_dnd com.cry.fluid").close()
        val audio = compose.activity.getSystemService(AudioManager::class.java)
        compose.runOnIdle { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL; model.refreshDevice(); model.page(2) }
        try {
            compose.waitUntil(5000) { model.uiState.value.device.soundMode == com.example.launcher.data.SoundMode.NORMAL }
            compose.onNodeWithText("Normal").performScrollTo().performClick()
            compose.waitUntil(5000) { audio.ringerMode == AudioManager.RINGER_MODE_SILENT && model.uiState.value.device.soundMode == com.example.launcher.data.SoundMode.SILENT }
            compose.onNodeWithText("Silent").performScrollTo().performClick()
            compose.waitUntil(5000) { audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE && model.uiState.value.device.soundMode == com.example.launcher.data.SoundMode.VIBRATE }
            compose.onNodeWithText("Vibrate").performScrollTo().performClick()
            compose.waitUntil(5000) { audio.ringerMode == AudioManager.RINGER_MODE_NORMAL }
        } finally { compose.runOnIdle { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL } }
    }
    @Test fun wifiPowerDelegatesToAndroidAndMoreOpensFullSettings() {
        compose.runOnIdle { model.page(2) }
        compose.onNodeWithText("Wi-Fi").performScrollTo().performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val panel = instrumentation.addMonitor(IntentFilter(Settings.Panel.ACTION_WIFI), null, true)
        val full = instrumentation.addMonitor(IntentFilter(Settings.ACTION_WIFI_SETTINGS), null, true)
        try {
            compose.onNodeWithContentDescription("Wi-Fi power").performClick()
            compose.runOnIdle { assertEquals(1, panel.hits) }
            compose.onNodeWithText("More settings").performScrollTo().performClick()
            compose.waitUntil(5000) { full.hits == 1 }
        } finally { instrumentation.removeMonitor(panel); instrumentation.removeMonitor(full) }
    }
}
