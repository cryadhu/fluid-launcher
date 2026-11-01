package com.example.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.launcher.data.*
import org.junit.*
import org.junit.Assert.*

class HomeGridTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        compose.runOnIdle { model.page(1); model.drawer(false) }
    }
    @Test fun addingBeyondEightFillsFourVisibleRowsAndSurvivesReload() {
        val apps = model.uiState.value.apps.take(HOME_CAPACITY + 1)
        assertTrue("Fixture needs at least 17 apps", apps.size > HOME_CAPACITY)
        compose.runOnIdle { model.saveHomeLayout(apps.take(8).map { appEntry(it.component.flattenToString()) }) }
        for (app in apps.drop(8).take(8)) compose.runOnIdle { model.pin(app) }
        compose.waitForIdle()
        assertEquals(16, model.uiState.value.homeLayout!!.size)
        for (index in 0 until HOME_CAPACITY) compose.onNodeWithTag("home-slot-$index").assertIsDisplayed()
        for (app in apps.take(16)) compose.onNodeWithText(app.label).assertIsDisplayed()
        compose.waitUntil(5000) {
            java.io.File(compose.activity.filesDir, "datastore/launcher_settings.preferences_pb").let {
                it.exists() && it.readBytes().toString(Charsets.ISO_8859_1).contains(encodeHomeLayout(model.uiState.value.homeLayout!!))
            }
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            lateinit var fresh: LauncherViewModel
            compose.runOnIdle {
                fresh = ViewModelProvider(store, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        LauncherViewModel(compose.activity.application, androidx.lifecycle.SavedStateHandle()) as T
                })[LauncherViewModel::class.java]
            }
            compose.waitUntil(10000) { fresh.uiState.value.settingsLoaded }
            assertEquals(model.uiState.value.homeLayout, fresh.uiState.value.homeLayout)
        } finally { compose.runOnIdle { store.clear() } }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), "home-4x4.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val top = compose.onNodeWithTag("home-slot-0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("home-slot-4").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("home-slot-15").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("dock-slot-0").fetchSemanticsNode().boundsInRoot
        assertEquals(top.height, last.height, 1f)
        assertTrue(second.top > top.top)
        assertTrue(last.bottom <= dock.top)
        compose.runOnIdle { model.pin(apps[16]) }
        assertEquals(16, model.uiState.value.homeLayout!!.size)
        assertFalse(model.uiState.value.homeLayout!!.any { it.id == apps[16].component.flattenToString() })
        compose.activityRule.scenario.recreate()
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && model.uiState.value.homeLayout?.size == 16 }
        assertEquals(apps.take(16).map { it.component.flattenToString() }, model.uiState.value.homeLayout!!.map { it.id })
        compose.onNodeWithText(apps[15].label).assertIsDisplayed()
    }
    @Test fun drawerAppCanBeDroppedIntoTheFourthRow() {
        val apps = model.uiState.value.apps.take(16)
        compose.runOnIdle { model.saveHomeLayout(apps.take(15).map { appEntry(it.component.flattenToString()) }) }
        val target = compose.onNodeWithTag("home-slot-15").fetchSemanticsNode().boundsInRoot.center
        compose.runOnIdle { model.drawer(true); model.query(apps[15].label) }
        val source = compose.onNode(hasText(apps[15].label) and !hasSetTextAction()).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(source) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(source + Offset(2f, 0f), 16); moveTo(target, 400); up() }
        compose.waitUntil(5000) { model.uiState.value.homeLayout?.size == 16 }
        assertEquals(apps[15].component.flattenToString(), model.uiState.value.homeLayout!!.last().id)
        assertFalse(model.uiState.value.drawer)
        compose.onNodeWithText(apps[15].label).assertIsDisplayed()
    }
    @Test fun iconCanStayAtBottomWithEmptyRowsAboveAfterFreshReload() {
        val apps = model.uiState.value.apps.take(2)
        compose.runOnIdle { model.saveHomeLayout(apps.map { appEntry(it.component.flattenToString()) }) }
        val source = compose.onNodeWithText(apps[0].label).fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag("home-slot-15").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(source) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(source + Offset(2f, 0f), 16); moveTo(target, 400); up() }
        compose.waitUntil(5000) { model.uiState.value.homeLayout!!.first { it.id == apps[0].component.flattenToString() }.slot == 15 }
        assertEquals(1, model.uiState.value.homeLayout!!.first { it.id == apps[1].component.flattenToString() }.slot)
        val bottom = compose.onNodeWithTag("home-slot-15").fetchSemanticsNode().boundsInRoot
        assertTrue(bottom.contains(compose.onNodeWithText(apps[0].label).fetchSemanticsNode().boundsInRoot.center))
        val expected = model.uiState.value.homeLayout!!
        compose.waitUntil(5000) {
            java.io.File(compose.activity.filesDir, "datastore/launcher_settings.preferences_pb").let {
                it.exists() && it.readBytes().toString(Charsets.ISO_8859_1).contains(encodeHomeLayout(expected))
            }
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            lateinit var fresh: LauncherViewModel
            compose.runOnIdle {
                fresh = ViewModelProvider(store, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        LauncherViewModel(compose.activity.application, androidx.lifecycle.SavedStateHandle()) as T
                })[LauncherViewModel::class.java]
            }
            compose.waitUntil(10000) { fresh.uiState.value.settingsLoaded }
            assertEquals(expected, fresh.uiState.value.homeLayout)
        } finally { compose.runOnIdle { store.clear() } }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), "home-sparse.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
