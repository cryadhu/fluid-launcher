package com.example.launcher.fluid

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FluidClockPanelTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var store: ClockStore
    private lateinit var original: ClockData

    @Before fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        store = ClockStore.get(context)
        original = store.state.value
        store.update { ClockData(alarms = starterAlarms()) }
    }

    @After fun restore() { store.update { original } }

    private fun open(start: String) {
        val model = FluidModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            FluidTheme(model.state.value.config) {
                FluidPanel("Clock", model.state.value, model, {}, {}, {}, {}, {}, {}, clockStart = start)
            }
        }
    }

    private fun tab(name: String) {
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(32)
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.mainClock.advanceTimeBy(32)
    }

    @Test fun alarmEditorSavesAndDeletesWithoutPanelChrome() {
        open("Alarm")
        compose.onNodeWithContentDescription("Close panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add alarm").performClick()
        compose.onNodeWithTag("alarm-label").performScrollTo().performTextInput("Test alarm")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Test alarm", store.state.value.alarms.last().label) }
        compose.onNodeWithTag("alarm-3").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Delete alarm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, store.state.value.alarms.size) }
    }

    @Test fun worldSearchAddsRemovesAndKeepsTabsFixed() {
        open("World clock")
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("fluid-panel-Clock").fetchSemanticsNode().boundsInRoot
        assertEquals(screen, panel)
        val tabs = compose.onNodeWithTag("clock-tabs").fetchSemanticsNode().boundsInRoot
        assertTrue(tabs.top > screen.height * .7f)
        compose.onNodeWithTag("world-local").assertExists()
        compose.onNodeWithContentDescription("Add a city").performClick()
        compose.onNodeWithTag("world-search").performTextInput("zzzzzzzz")
        compose.onNodeWithText("No matching cities").assertExists()
        compose.onNodeWithTag("world-search").performTextReplacement("Sydney")
        compose.onNode(hasText("Sydney") and hasClickAction() and !hasTestTag("world-search")).performScrollTo().performClick()
        compose.runOnIdle { assertTrue("Australia/Sydney" in store.state.value.zones) }
        compose.onNodeWithContentDescription("Remove Sydney").performScrollTo().performClick()
        compose.runOnIdle { assertTrue("Australia/Sydney" !in store.state.value.zones) }
        assertEquals(tabs, compose.onNodeWithTag("clock-tabs").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun stopwatchAndTimerKeepStateAcrossTabs() {
        open("Stopwatch")
        compose.mainClock.autoAdvance = false
        click("Start")
        click("Lap")
        compose.runOnIdle {
            assertTrue(store.state.value.stopwatch.running)
            assertEquals(1, store.state.value.stopwatch.laps.size)
        }
        tab("Timer")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("1", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("0", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Start").performScrollTo()
        compose.mainClock.autoAdvance = false
        click("Start")
        click("Pause")
        compose.runOnIdle { assertTrue(store.state.value.timer.left != null) }
        tab("Stopwatch")
        compose.onNodeWithTag("lap-1").assertExists()
        compose.runOnIdle { assertTrue(store.state.value.stopwatch.running) }
        tab("Timer")
        click("Resume")
        compose.runOnIdle { assertTrue(store.state.value.timer.endsAt != null) }
        click("Cancel")
        compose.runOnIdle { assertTrue(store.state.value.timer.idle) }
    }
}
