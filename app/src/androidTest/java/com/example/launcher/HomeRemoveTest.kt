package com.example.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.launcher.data.*
import com.example.launcher.ui.homeApps
import org.junit.*
import org.junit.Assert.*

class HomeRemoveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    private lateinit var initial: List<HomeEntry>
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        initial = normalizeHomeLayout(homeApps(model.uiState.value.apps, model.uiState.value.pins).map { appEntry(it.component.flattenToString()) })
        compose.runOnIdle { model.page(1); model.saveHomeLayout(initial) }
    }
    @After fun restore() { compose.runOnIdle { model.saveHomeLayout(initial) } }
    private fun beginDrag(start: Offset) {
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(start + Offset(2f, 0f), 16) }
        compose.waitForIdle()
    }
    private fun hoverBin() {
        val target = compose.onNodeWithTag("home-remove-bin").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { moveTo(target, 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Release to remove from Home").assertIsDisplayed()
    }
    @Test fun binRemovesOnlyHomeShortcutAndPersists() {
        val app = model.uiState.value.apps.first { it.component.flattenToString() == initial[0].id }
        compose.onNodeWithTag("home-remove-bin").assertDoesNotExist()
        beginDrag(compose.onNodeWithText(app.label).fetchSemanticsNode().boundsInRoot.center)
        compose.onNodeWithTag("home-remove-bin").assertIsDisplayed()
        hoverBin()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), "home-bin-hover.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onRoot().performTouchInput { up() }
        compose.waitUntil(5000) { model.uiState.value.homeLayout!!.none { it.id == initial[0].id } }
        compose.onNodeWithText(app.label).assertDoesNotExist()
        assertTrue(model.uiState.value.apps.any { it.component == app.component })
        compose.activityRule.scenario.recreate()
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.onNodeWithText(app.label).assertDoesNotExist()
        compose.onNodeWithTag("home-remove-bin").assertDoesNotExist()
        compose.runOnIdle { model.drawer(true) }
        compose.onNodeWithText(app.label).assertExists()
        compose.runOnIdle { model.drawer(false) }
    }
    @Test fun leavingBinBeforeReleaseKeepsTheShortcut() {
        val app = model.uiState.value.apps.first { it.component.flattenToString() == initial[0].id }
        beginDrag(compose.onNodeWithText(app.label).fetchSemanticsNode().boundsInRoot.center)
        hoverBin()
        compose.onRoot().performTouchInput { moveTo(Offset(20f, 200f), 300); up() }
        compose.waitForIdle()
        assertEquals(initial, model.uiState.value.homeLayout)
        compose.onNodeWithText(app.label).assertIsDisplayed()
    }
    @Test fun binRemovesFolderWithoutUninstallingItsMembers() {
        val layout = mergeHomeEntry(initial, initial[0], 1)
        val folder = layout.first { it.isFolder }
        compose.runOnIdle { model.saveHomeLayout(layout) }
        beginDrag(compose.onNodeWithContentDescription("Open Folder folder").fetchSemanticsNode().boundsInRoot.center)
        hoverBin()
        compose.onRoot().performTouchInput { up() }
        compose.waitUntil(5000) { model.uiState.value.homeLayout!!.none { it.id == folder.id } }
        assertEquals(layout.filterNot { it.id == folder.id }, model.uiState.value.homeLayout)
        folder.components.forEach { key ->
            val app = model.uiState.value.apps.first { it.component.flattenToString() == key }
            @Suppress("DEPRECATION") val installed = compose.activity.packageManager.getApplicationInfo(app.component.packageName, 0)
            assertNotNull(installed)
        }
        compose.onNodeWithContentDescription("Open Folder folder").assertDoesNotExist()
    }
}
