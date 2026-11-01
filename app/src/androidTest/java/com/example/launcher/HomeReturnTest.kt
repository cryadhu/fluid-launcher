package com.example.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class HomeReturnTest {
    // Launch as the actual HOME task, not the ordinary app task used by generic Activity rules.
    @get:Rule val compose = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>(
        ActivityScenarioRule(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    ) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }
    private lateinit var model: LauncherViewModel
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        compose.runOnIdle { model.returnHome() }
    }

    @Test fun homeIntentFromEitherSideShowsHomeInFirstFrameWithoutPageSlide() {
        compose.mainClock.autoAdvance = false
        try {
            for (page in listOf(0, 3)) {
                compose.runOnIdle { model.page(page) }
                compose.mainClock.advanceTimeBy(600)
                val before = model.uiState.value.homeReturnEpoch
                compose.runOnUiThread {
                    compose.activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(ComponentName(compose.activity, MainActivity::class.java))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                compose.waitUntil(5000) { model.uiState.value.homeReturnEpoch > before }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("home-clock").assertIsDisplayed()
                val left = compose.onNodeWithTag("home-clock").fetchSemanticsNode().boundsInRoot.left
                assertEquals(24f * compose.activity.resources.displayMetrics.density, left, 2f)
                assertEquals(1, model.uiState.value.page)
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun appBackRevealsPreparedHomeWithDrawerClosed() {
        val activity = compose.activity
        // Remove Settings screens left by earlier manual/tests; one Back must exit its root.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am force-stop com.android.settings").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val settings = model.uiState.value.apps.first { it.component.packageName == "com.android.settings" }
        compose.runOnIdle { model.page(3); model.drawer(true); model.launch(settings) }
        compose.waitUntil(5000) { !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        compose.waitUntil(10000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.settings"
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = android.os.SystemClock.uptimeMillis()
        assertTrue(automation.injectInputEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_BACK, 0), true))
        assertTrue(automation.injectInputEvent(android.view.KeyEvent(now, now + 30, android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_BACK, 0), true))
        try {
            compose.waitUntil(10000) { automation.rootInActiveWindow?.packageName?.toString() == activity.packageName }
        } catch (error: Exception) {
            throw AssertionError("After Back foreground=${automation.rootInActiveWindow?.packageName}; Home=${activity.lifecycle.currentState}; finishing=${activity.isFinishing}", error)
        }
        fun containsClock(node: android.view.accessibility.AccessibilityNodeInfo): Boolean =
            node.contentDescription?.toString() == "Open clock" || (0 until node.childCount).any { index ->
                node.getChild(index)?.let(::containsClock) == true
            }
        assertTrue("Home clock must be exposed after app Back", containsClock(automation.rootInActiveWindow!!))
        assertEquals(1, model.uiState.value.page)
        assertFalse(model.uiState.value.drawer)
    }

    @Test fun homeWindowStaysStationaryAndIsExcludedFromAppRecents() {
        val animation = AnimationUtils.loadAnimation(compose.activity, R.anim.home_stationary)
        animation.startTime = 0
        for (time in listOf(0L, 90L, 179L)) {
            val transform = Transformation()
            animation.getTransformation(time, transform)
            assertTrue("Home must not translate or scale", transform.matrix.isIdentity)
            assertEquals(1f, transform.alpha, 0.001f)
        }
        val style = compose.activity.window.attributes.windowAnimations
        val attributes = compose.activity.obtainStyledAttributes(style, intArrayOf(
            android.R.attr.activityCloseEnterAnimation, android.R.attr.taskCloseEnterAnimation,
            android.R.attr.taskToFrontEnterAnimation))
        try { for (index in 0..2) assertEquals(R.anim.home_stationary, attributes.getResourceId(index, 0)) }
        finally { attributes.recycle() }
        val info = compose.activity.packageManager.getActivityInfo(ComponentName(compose.activity, MainActivity::class.java), 0)
        assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
    }
}
