package com.example.launcher.fluid

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class FluidControlWidgetsTest {
    @get:Rule val compose=createComposeRule()
    private val model=FluidModel(ApplicationProvider.getApplicationContext<Application>())
    @After fun clearNotifications() { FluidNotifications.mutable.value=emptyList() }
    @Test fun tinyControlsAndSlidersAreAddableAndRemovableWithoutControlsPage() {
        model.configure { it.copy(autoWeather=false,widgets=listOf(FluidWidget.BRIGHTNESS,FluidWidget.VOLUME)+tinyControls) }
        compose.setContent { FluidTheme(model.state.value.config) { FluidLauncher(model,{}) } }
        compose.onNodeWithContentDescription("Controls").assertDoesNotExist()
        compose.onNodeWithContentDescription("Brightness",substring=false).assertExists()
        compose.onNodeWithContentDescription("Media volume").assertExists()
        compose.onNodeWithTag("fluid-widget-WIFI").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Remove widget").performClick()
        compose.onNodeWithTag("fluid-widget-WIFI").assertDoesNotExist()
        assertFalse(FluidWidget.WIFI in model.state.value.config.widgets)
        compose.onNodeWithTag("fluid-widget-BLUETOOTH").assertExists()
    }
    @Test fun notificationsTravelFromStackToListAndBack() {
        FluidNotifications.mutable.value=(0..2).map { FluidNotification("motion-$it","Mail","Message $it","Notification body",1000L,null,null,true) }
        model.configure { it.copy(autoWeather=false,widgets=listOf(FluidWidget.NOTIFICATIONS)) }
        compose.setContent { FluidTheme(model.state.value.config) { FluidLauncher(model,{}) } }
        val card=compose.onNodeWithTag("notification-card:motion-1",useUnmergedTree=true)
        val closed=card.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("See all").performClick()
        compose.mainClock.advanceTimeBy(160)
        val middle=card.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(1500)
        val open=card.fetchSemanticsNode().boundsInRoot.top
        assertTrue(middle>closed)
        assertTrue(open>middle)
        compose.onNodeWithText("Collapse").performClick()
        compose.mainClock.advanceTimeBy(160)
        assertTrue(card.fetchSemanticsNode().boundsInRoot.top<open)
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("notification-compact-stack",useUnmergedTree=true).assertExists()
        compose.mainClock.autoAdvance=true
    }
}
