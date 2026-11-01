package com.example.launcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.*
import org.junit.Assert.*

class PageIndicatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun compactIndicatorHidesAfterIdleAndReturnsOnVerticalScroll() {
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { model.page(2) }
            compose.mainClock.advanceTimeBy(450)
            compose.onNodeWithTag("page-indicator").assertIsDisplayed()
            val bounds = compose.onNodeWithTag("page-indicator").getUnclippedBoundsInRoot()
            assertTrue("Indicator should be less than 40dp wide", (bounds.right.value - bounds.left.value) <= 40f)
            assertTrue("Indicator should use only 16dp of vertical space", (bounds.bottom.value - bounds.top.value) <= 16f)
            compose.mainClock.advanceTimeBy(700)
            compose.onNodeWithTag("page-indicator").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(550)
            compose.onNodeWithTag("page-indicator").assertDoesNotExist()
            compose.onRoot().performTouchInput { down(center.copy(y = height * 0.65f)) }
            compose.onRoot().performTouchInput { moveTo(center.copy(y = height * 0.3f), 300) }
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithTag("page-indicator").assertIsDisplayed()
            compose.onRoot().performTouchInput { advanceEventTime(1000); up() }
            compose.mainClock.advanceTimeBy(1800)
            compose.onNodeWithTag("page-indicator").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance = true }
    }
}

