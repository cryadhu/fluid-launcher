package com.example.launcher.fluid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.launcher.MainActivity
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FluidLauncherTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun resetWidgetLayout() {
        compose.runOnIdle { androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java].configure { it.copy(widgets=FluidConfig().widgets,sizes=emptyMap()) } }
    }
    @Test fun homeDrawerKeyboardOptionsAndAllNewPagesWork() {
        compose.onNodeWithTag("fluid-home").assertExists()
        compose.onNodeWithContentDescription("All apps").performClick()
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
        compose.onNodeWithContentDescription("Markets").performClick()
        compose.onNodeWithTag("fluid-markets").assertExists()
        compose.onNodeWithContentDescription("Controls").performClick()
        compose.onNodeWithTag("fluid-controls").assertExists()
    }
    @Test fun widgetExpandsFromItsBoundsAndGalleryScrollsToLastWidget() {
        val card=compose.onNodeWithTag("fluid-widget-AGENDA")
        card.performScrollTo()
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
    @Test fun upwardSwipeOpensDrawerAndLongPressCanResizeAndRemoveClock() {
        val clock=compose.onNodeWithTag("fluid-widget-CLOCK",useUnmergedTree=true)
        val original=clock.fetchSemanticsNode().boundsInRoot.height
        clock.performTouchInput { longClick() }
        compose.onNodeWithTag("widget-edit").assertExists()
        compose.onNodeWithText("Large").performClick()
        compose.onNodeWithContentDescription("Close widget options").performClick()
        assertTrue(clock.fetchSemanticsNode().boundsInRoot.height>original)
        clock.performTouchInput { longClick() }
        compose.onNodeWithText("Remove widget").performClick()
        compose.onNodeWithTag("fluid-widget-CLOCK",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("fluid-home").performTouchInput { swipeUp() }
        compose.onNodeWithTag("fluid-app-drawer").assertExists()
    }
    @After fun clearFixtureNotifications() {
        FluidNotifications.mutable.value=emptyList();FluidNotifications.cancel=null
    }
    @Test fun notificationStackSwipesBothDirectionsAndClearsRemainingCards() {
        compose.runOnIdle {
            FluidNotifications.mutable.value=(1..3).map{FluidNotification("test-$it","Test app","Notice $it","A real notification shape",System.currentTimeMillis(),null,null,true)}
            FluidNotifications.cancel={key->FluidNotifications.mutable.value=FluidNotifications.mutable.value.filterNot{it.key==key}}
        }
        compose.onNodeWithTag("notification-card:test-1").performTouchInput{swipeLeft()}
        compose.waitUntil(5000){FluidNotifications.items.value.size==2}
        compose.onNodeWithTag("notification-card:test-2").performTouchInput{swipeRight()}
        compose.waitUntil(5000){FluidNotifications.items.value.size==1}
        compose.onNodeWithText("Clear all").performClick()
        compose.waitUntil(5000){FluidNotifications.items.value.isEmpty()}
        compose.onNodeWithTag("notification-card:test-3").assertDoesNotExist()
    }
}
