package com.example.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.launcher.ui.homeApps
import com.example.launcher.ui.dockItems
import android.content.IntentFilter
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherGestureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    @Before fun showHome() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        compose.runOnIdle { model.page(1) }
        compose.waitForIdle()
    }
    @Test fun brandingIsAbsentAndDrawerTracksBothGestures() {
        compose.onNodeWithText("cry", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Android settings").assertDoesNotExist()
        compose.onRoot().performTouchInput { swipe(Offset(center.x, height * 0.55f), Offset(center.x, height * 0.2f), 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Search apps").assertIsDisplayed()
        compose.onNodeWithText("ALL APPS", substring = true).performTouchInput { swipe(center, center + Offset(0f, 500f), 500) }
        compose.waitForIdle()
        compose.onNodeWithTag("launcher-search").assertDoesNotExist()
        assertFalse(model.uiState.value.drawer)
    }
    @Test fun draggingHomeIconPersistsOrderAndOutsideDropCancels() {
        val before = homeApps(model.uiState.value.apps, model.uiState.value.pins)
        val source = compose.onNodeWithText(before[0].label).fetchSemanticsNode().boundsInRoot.center
        val destination = compose.onNodeWithText(before[2].label).fetchSemanticsNode().boundsInRoot.let { Offset(it.right - 3f, it.center.y) }
        drag(source, destination)
        compose.waitUntil(5000) { model.uiState.value.pins.getOrNull(2)?.component == before[0].component.flattenToString() }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        val after = homeApps(model.uiState.value.apps, model.uiState.value.pins)
        assertEquals(before[0].component, after[2].component)
        val start = compose.onNodeWithText(after[0].label).fetchSemanticsNode().boundsInRoot.center
        drag(start, Offset(40f, 400f))
        assertEquals(after.map { it.component }, homeApps(model.uiState.value.apps, model.uiState.value.pins).map { it.component })
    }
    @Test fun draggingFromDrawerAddsAppToChosenHomeSlot() {
        // Leave room for the new app instead of relying on a full grid discarding its last icon.
        val before = homeApps(model.uiState.value.apps, model.uiState.value.pins).take(8)
        compose.runOnIdle { model.saveHomeOrder(before) }
        val target = compose.onNodeWithText(before[1].label).fetchSemanticsNode().boundsInRoot.let { Offset(it.right - 3f, it.center.y) }
        val candidate = model.uiState.value.apps.first { app -> before.none { it.component == app.component } }
        compose.runOnIdle { model.drawer(true) }
        compose.waitForIdle()
        val source = compose.onNodeWithText(candidate.label).fetchSemanticsNode().boundsInRoot.center
        drag(source, target)
        compose.waitUntil(5000) { model.uiState.value.homeLayout?.any { it.slot == 1 && it.id == candidate.component.flattenToString() } == true }
        assertFalse(model.uiState.value.drawer)
        compose.onNodeWithText(candidate.label).assertIsDisplayed()
    }
    @Test fun swipingUpOnHomeOpensDrawerWithSearchOnlyInsideIt() {
        compose.onNodeWithContentDescription("Open app drawer").assertDoesNotExist()
        compose.onNodeWithTag("launcher-search").assertDoesNotExist()
        compose.onRoot().performTouchInput { swipe(Offset(center.x, height * 0.55f), Offset(center.x, height * 0.2f), 400) }
        compose.waitForIdle()
        assertTrue(model.uiState.value.drawer)
        compose.onAllNodesWithTag("launcher-search").assertCountEquals(1)
        compose.onNodeWithText("Search apps").performTextInput("no-matching-app-793")
        compose.onNodeWithText("No apps match your search").assertIsDisplayed()
    }
    @Test fun clockStaysAtTopWithoutSettingsButtonAndOpensClockApp() {
        val clock = compose.onNodeWithTag("home-clock").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("page-1-content").fetchSemanticsNode().boundsInRoot
        assertEquals(content.top, clock.top, 2f)
        compose.onNodeWithContentDescription("Android settings").assertDoesNotExist()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(android.provider.AlarmClock.ACTION_SHOW_ALARMS), null, true)
        try {
            compose.onNodeWithContentDescription("Open clock").performClick()
            compose.runOnIdle { assertEquals(1, monitor.hits) }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun dockReordersAndAcceptsFifthAppFromDrawer() {
        val old = model.uiState.value.dockComponents.toList()
        val sourceIndex = dockItems(compose.activity, model.uiState.value.apps, old).indexOfFirst { it.app != null }
        val targetIndex = (sourceIndex + 2) % 4
        val first = compose.onNodeWithTag("dock-slot-$sourceIndex").fetchSemanticsNode().boundsInRoot.center
        val third = compose.onNodeWithTag("dock-slot-$targetIndex").fetchSemanticsNode().boundsInRoot.center
        drag(first, third)
        compose.waitUntil(5000) { model.uiState.value.dockComponents[targetIndex] == old[sourceIndex] }
        val destination = compose.onNodeWithTag("dock-slot-1").fetchSemanticsNode().boundsInRoot.center
        val existing = dockItems(compose.activity, model.uiState.value.apps, model.uiState.value.dockComponents).mapNotNull { it.app?.component }
        val candidate = model.uiState.value.apps.first { it.component !in existing }
        compose.runOnIdle { model.drawer(true) }
        compose.waitForIdle()
        val source = compose.onNodeWithText(candidate.label).fetchSemanticsNode().boundsInRoot.center
        drag(source, destination)
        compose.waitUntil(5000) { model.uiState.value.dockComponents[1] == candidate.component.flattenToString() }
        assertEquals(5, model.uiState.value.dockComponents.size)
        val savedDock = model.uiState.value.dockComponents.toList()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        assertEquals(savedDock, model.uiState.value.dockComponents)
    }
    @Test fun dockShrinksAndDockBinRemovalLeavesHomeUnchanged() {
        val keys = model.uiState.value.apps.take(5).map { it.component.flattenToString() }
        compose.runOnIdle { model.saveDockOrder(keys) }
        val wide = compose.onNodeWithTag("home-dock").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { model.saveDockOrder(keys.take(2)) }
        val narrow = compose.onNodeWithTag("home-dock").fetchSemanticsNode().boundsInRoot.width
        assertTrue(narrow < wide)
        val home = model.homeEntries()
        val start = compose.onNodeWithTag("dock-slot-0").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(start + Offset(2f, 0f), 16) }
        val bin = compose.onNodeWithTag("home-remove-bin").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { moveTo(bin, 400); up() }
        compose.waitUntil(5000) { model.uiState.value.dockComponents == listOf(keys[1]) }
        assertEquals(home, model.homeEntries())
        compose.runOnIdle { model.saveDockOrder(emptyList()) }
        compose.onNodeWithTag("home-dock").assertIsDisplayed()
        assertTrue(model.uiState.value.dockComponents.isEmpty())
    }
    private fun drag(start: Offset, end: Offset) {
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(start + Offset(2f, 0f), 16); moveTo(end, 400); up() }
        compose.waitForIdle()
    }
}
