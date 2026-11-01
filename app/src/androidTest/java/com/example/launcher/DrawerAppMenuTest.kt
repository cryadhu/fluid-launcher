package com.example.launcher

import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class DrawerAppMenuTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        compose.runOnIdle { model.page(1); model.drawer(true) }
    }
    private fun hold(label: String) {
        val point = compose.onNode(hasText(label) and !hasSetTextAction()).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(point) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }
    @Test fun stationaryLongPressShowsMenuWithoutClosingDrawerOrEditingHome() {
        val before = model.uiState.value.homeLayout
        val pins = model.uiState.value.pins
        hold(model.uiState.value.apps.first().label)
        assertTrue(model.uiState.value.drawer)
        assertEquals(before, model.uiState.value.homeLayout)
        assertEquals(pins, model.uiState.value.pins)
        compose.onNodeWithTag("drawer-app-menu").assertIsDisplayed()
        compose.onNodeWithText("App info").assertIsDisplayed()
        compose.onNodeWithText("Uninstall").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("drawer-app-menu").assertDoesNotExist()
        assertTrue(model.uiState.value.drawer)
    }
    @Test fun menuClearsSearchFocusAndHidesKeyboardWithoutClearingQuery() {
        val app = model.uiState.value.apps.first()
        compose.runOnIdle { model.drawer(true, focus = true); model.query(app.label) }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        hold(app.label)
        compose.onNodeWithTag("drawer-app-menu").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        assertFalse(model.uiState.value.focusSearch)
        assertEquals(app.label, model.uiState.value.query)
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true
        }
    }
    @Test fun appInfoOpensAndroidDetailsForHeldPackage() {
        val app = model.uiState.value.apps.first()
        hold(app.label)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var opened: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                opened = intent
                return Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithText("App info").performClick()
            compose.runOnIdle {
                assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened?.action)
                assertEquals("package:${app.component.packageName}", opened?.dataString)
            }
            compose.onNodeWithTag("drawer-app-menu").assertDoesNotExist()
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun uninstallUsesSystemConfirmationAndDoesNotImmediatelyDeleteApp() {
        val app = model.uiState.value.apps.firstOrNull {
            compose.activity.packageManager.getApplicationInfo(it.component.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
        } ?: model.uiState.value.apps.first()
        compose.runOnIdle { model.query(app.label) }
        hold(app.label)
        val removable = compose.activity.packageManager.getApplicationInfo(app.component.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
        if (!removable) { compose.onNodeWithText("Uninstall").assertIsNotEnabled(); return }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_DELETE).apply { addDataScheme("package") }, null, true)
        try {
            compose.onNodeWithText("Uninstall").performClick()
            compose.runOnIdle { assertEquals(1, monitor.hits) }
            assertNotNull(compose.activity.packageManager.getApplicationInfo(app.component.packageName, 0))
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
