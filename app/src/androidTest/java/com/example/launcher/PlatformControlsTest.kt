package com.example.launcher

import android.Manifest
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class PlatformControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded }
        compose.runOnIdle { model.page(2) }
    }
    @Test fun airplaneReplacesScreenshotAndOpensSystemControl() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Settings.ACTION_AIRPLANE_MODE_SETTINGS), null, true)
        try {
            compose.onNodeWithText("Screenshot").assertDoesNotExist()
            compose.onNodeWithText("Airplane mode").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1, monitor.hits) }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun postingPermissionAndListenerHelpAreAvailable() {
        val context = compose.activity
        @Suppress("DEPRECATION") val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertNotNull(context.getSystemService(NotificationManager::class.java).getNotificationChannel("launcher"))
        compose.onNodeWithText("NOTIFICATIONS").assertDoesNotExist()
        compose.onNodeWithText("Allow notifications").assertDoesNotExist()
        compose.onNodeWithText("Enable media access").performScrollTo().performClick()
        compose.onNodeWithText("Allow restricted settings", substring = true).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { addDataScheme("package") }, null, true)
        try {
            compose.onNodeWithText("App info").performClick()
            compose.runOnIdle { assertEquals(1, monitor.hits) }
        } finally { instrumentation.removeMonitor(monitor) }
        compose.onNodeWithText("Done").performClick()
    }
    @Test fun favouritesUseTruecallerWithTheSelectedNumber() {
        val number = "+15550123456"
        var launched: Intent? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_DIAL) return null
                launched = intent
                return Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(monitor)
        compose.runOnIdle { model.favourite("Default dialer check", number); model.page(3) }
        compose.waitUntil(5000) { model.uiState.value.favourites.any { it.phone == number } }
        try {
            compose.onNodeWithContentDescription("Dial Default dialer check").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(number, launched?.data?.schemeSpecificPart)
                assertEquals("com.truecaller", launched?.`package`)
                assertTrue(launched!!.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            compose.runOnIdle { model.uiState.value.favourites.firstOrNull { it.phone == number }?.let(model::removeFavourite) }
        }
    }
    @Test fun phoneDockAndSavedTruecallerShortcutRequestCallingEntryPoint() {
        val context = compose.activity
        val dock = com.example.launcher.ui.dockItems(context, emptyList(), listOf("@phone")).single().intent!!
        assertEquals(Intent.ACTION_DIAL, dock.action)
        assertEquals("com.truecaller", dock.`package`)
        assertEquals("tel:", dock.data.toString())
        assertTrue(dock.hasCategory(Intent.CATEGORY_DEFAULT))
        var launched: Intent? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                launched = intent
                return Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.runOnIdle { model.device.launch(com.example.launcher.data.InstalledApp(
                android.content.ComponentName("com.truecaller", "com.truecaller.messaging.MessagingActivity"),
                "Truecaller", com.example.launcher.data.AppIconStyle("phone", com.example.launcher.data.AppAccent.MINT))) }
            compose.runOnIdle {
                assertEquals(Intent.ACTION_DIAL, launched?.action)
                assertEquals("com.truecaller", launched?.`package`)
                assertNull(launched?.component)
                assertEquals("tel:", launched?.data.toString())
                assertTrue(launched!!.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun pageContentUsesTheSpaceBehindFloatingIndicator() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("page-2-content").fetchSemanticsNode().boundsInRoot
        val navigation = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        assertEquals("No indicator strip should be reserved", root.bottom - navigation, content.bottom, 2f)
    }
}
