package com.example.launcher.fluid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.launcher.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FluidLauncherTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun homeDrawerKeyboardOptionsAndAllNewPagesWork() {
        compose.onNodeWithTag("fluid-home").assertExists()
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithTag("fluid-app-drawer").assertExists()
        compose.onNodeWithTag("fluid-app-search").performTextInput("Settings")
        compose.onNodeWithContentDescription("Settings").performTouchInput { longClick() }
        compose.onNodeWithTag("fluid-app-options").assertExists()
        compose.waitUntil(10000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true }
        compose.onNode(hasText("Add to Work") or hasText("Remove from Work")).performClick()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Space").performClick()
        compose.onNodeWithTag("fluid-space").assertExists()
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.onNodeWithTag("fluid-notifications").assertExists()
        compose.onNodeWithContentDescription("Controls").performClick()
        compose.onNodeWithTag("fluid-controls").assertExists()
    }
    @Test fun widgetExpandsFromItsBoundsAndGalleryScrollsToLastWidget() {
        val card=compose.onNodeWithTag("fluid-widget-AGENDA")
        val source=card.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance=false
        card.performClick()
        compose.mainClock.advanceTimeBy(160)
        val growing=compose.onNodeWithTag("fluid-expanded-widget").fetchSemanticsNode().boundsInRoot
        assertTrue(growing.height>source.height)
        compose.mainClock.advanceTimeBy(1000)
        val expanded=compose.onNodeWithTag("fluid-expanded-widget").fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.top<source.top)
        assertTrue(expanded.height>growing.height)
        compose.onNodeWithContentDescription("Close widget").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("fluid-hero-overlay").assertDoesNotExist()
        compose.mainClock.autoAdvance=true
        compose.onNodeWithContentDescription("Customize home").performClick()
        compose.onNodeWithContentDescription("Widget gallery").performClick()
        compose.onNodeWithText("A moment in time").performScrollTo().assertIsDisplayed()
    }
}
