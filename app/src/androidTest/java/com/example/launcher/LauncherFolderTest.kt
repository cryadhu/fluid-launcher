package com.example.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.launcher.data.*
import com.example.launcher.ui.homeApps
import org.junit.*
import org.junit.Assert.*

class LauncherFolderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    private lateinit var initial: List<HomeEntry>
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        initial = normalizeHomeLayout(homeApps(model.uiState.value.apps, model.uiState.value.pins).map { appEntry(it.component.flattenToString()) })
        compose.runOnIdle { model.openFolder(null); model.page(1); model.saveHomeLayout(initial) }
    }
    @After fun restore() { compose.runOnIdle { model.openFolder(null); model.saveHomeLayout(initial) } }
    private fun createFolder(): HomeEntry {
        val apps = model.uiState.value.apps
        val first = apps.first { it.component.flattenToString() == initial[0].id }
        val second = apps.first { it.component.flattenToString() == initial[1].id }
        val start = compose.onNodeWithText(first.label).fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithText(second.label).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(start + Offset(2f, 0f), 16); moveTo(end, 400); up() }
        compose.waitUntil(5000) { model.uiState.value.homeLayout.orEmpty().any { it.isFolder } }
        return model.uiState.value.homeLayout!!.first { it.isFolder }
    }
    @Test fun dropCreatesFolderAndRenamePersistsWithBlurredOpenView() {
        val folder = createFolder()
        assertEquals(setOf(initial[0].id, initial[1].id), folder.components.toSet())
        compose.onNodeWithContentDescription("Open Folder folder").performClick()
        compose.onNodeWithText("2 apps").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("folder-viewport", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val expanded = compose.onNodeWithTag("folder-expanded-content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Folder should be vertically centered", viewport.center.y, expanded.center.y, 2f)
        assertEquals("Folder should be horizontally centered", viewport.center.x, expanded.center.x, 2f)
        compose.onNodeWithContentDescription("Rename folder").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Work")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithTag("folder-title").assertTextEquals("Work")
        compose.waitUntil(5000) {
            java.io.File(compose.activity.filesDir, "datastore/launcher_settings.preferences_pb").let { file -> file.exists() && file.readBytes().toString(Charsets.ISO_8859_1).let { it.contains(folder.id) && it.contains("Work") } }
        }
        val freshStore = ViewModelStore()
        lateinit var fresh: LauncherViewModel
        try {
            compose.runOnIdle {
                fresh = ViewModelProvider(freshStore, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = LauncherViewModel(compose.activity.application, SavedStateHandle()) as T
                })[LauncherViewModel::class.java]
            }
            compose.waitUntil(10000) { fresh.uiState.value.settingsLoaded }
            assertEquals(folder.copy(title = "Work"), fresh.uiState.value.homeLayout!!.first { it.id == folder.id })
        } finally { compose.runOnIdle { freshStore.clear() } }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), "folder-open.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Close folder").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.onNodeWithContentDescription("Open Work folder").assertIsDisplayed().performClick()
        compose.onNodeWithTag("folder-title").assertTextEquals("Work")
    }
    @Test fun folderCanAddAppsAndRemoveMembers() {
        val folder = createFolder()
        val addition = model.uiState.value.apps.first { it.component.flattenToString() !in folder.components }
        compose.onNodeWithContentDescription("Open Folder folder").performClick()
        compose.onNodeWithText("Add apps").performClick()
        compose.onNodeWithContentDescription("Select ${addition.label}").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(5000) { model.uiState.value.homeLayout!!.first { it.id == folder.id }.components.size == 3 }
        compose.onNodeWithText("3 apps").assertIsDisplayed()
        val member = model.uiState.value.apps.first { it.component.flattenToString() == folder.components.first() }
        compose.onNodeWithText(member.label).performTouchInput { longClick() }
        compose.onNodeWithText("Remove from folder").performClick()
        compose.waitUntil(5000) { model.uiState.value.homeLayout!!.first { it.id == folder.id }.components.size == 2 }
    }
    @Test fun notificationDotsAppearOnFolderAndItsMember() {
        val folder = createFolder()
        val component = folder.components.first()
        compose.runOnIdle { NotificationBadges.mutable.value = setOf(component.substringBefore('/')) }
        try {
            compose.onNodeWithTag("folder-notification-dot:${folder.id}",useUnmergedTree=true).assertExists()
            compose.onNodeWithContentDescription("Open Folder folder").performClick()
            compose.onNodeWithTag("folder-app-notification-dot:$component",useUnmergedTree=true).assertExists()
        } finally { compose.runOnIdle { NotificationBadges.mutable.value = emptySet() } }
    }
    @Test fun folderMemberCanBeDraggedBackToEmptyHomeCell() {
        val folder = createFolder()
        val member = folder.components.first()
        val target = compose.onNodeWithTag("home-slot-15").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithContentDescription("Open Folder folder").performClick()
        val layer = compose.onNodeWithTag("folder-drag-layer")
        val origin = layer.fetchSemanticsNode().boundsInRoot.topLeft
        val start = compose.onNodeWithTag("folder-app-$member").fetchSemanticsNode().boundsInRoot.center
        layer.performTouchInput { down(start - origin); advanceEventTime(800); moveTo(target - origin, 600); up() }
        compose.waitUntil(5000) { model.uiState.value.homeLayout.orEmpty().any { it.id == member && it.slot == 15 } }
        assertFalse(model.uiState.value.homeLayout!!.first { it.id == folder.id }.components.contains(member))
        assertEquals(folder.components.toSet() + initial.drop(2).map { it.id }, model.uiState.value.homeLayout!!.flatMap { it.components }.toSet())
    }
    @Test fun folderTileCanBeRearrangedWithoutLosingItsContents() {
        val folder = createFolder()
        val app = model.uiState.value.apps.first { it.component.flattenToString() == initial[3].id }
        val start = compose.onNodeWithContentDescription("Open Folder folder").fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithText(app.label).fetchSemanticsNode().boundsInRoot.let { Offset(it.right - 3f, it.center.y) }
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(start + Offset(2f, 0f), 16); moveTo(end, 400); up() }
        compose.waitUntil(5000) { model.uiState.value.homeLayout!!.first { it.id == folder.id }.slot == 3 }
        assertEquals(folder.components, model.uiState.value.homeLayout!!.first { it.id == folder.id }.components)
    }
}
