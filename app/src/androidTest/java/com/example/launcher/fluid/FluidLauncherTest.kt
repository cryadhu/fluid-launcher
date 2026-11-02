package com.example.launcher.fluid

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
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
    @get:Rule(order=0) val homeRole=object : org.junit.rules.ExternalResource() {
        override fun before() {
            val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd role add-role-holder --user 0 android.app.role.HOME com.cry.fluid")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Before fun resetWidgetLayout() {
        compose.runOnIdle { androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java].configure { it.copy(widgets=FluidConfig().widgets,sizes=emptyMap(),dock=listOf("@auto"),dockCount=3,dockLabels=false,dockIconSize=48,spaceWidgets=listOf(FluidWidget.TASKS)) } }
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
        compose.onNodeWithContentDescription("Close app drawer").performClick()
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
    @Test fun upwardSwipeDoesNotOpenDrawerAndLongPressCanResizeAndRemoveClock() {
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
        compose.onNodeWithTag("fluid-app-drawer").assertDoesNotExist()
        compose.onNodeWithContentDescription("All apps").performClick()
        compose.onNodeWithTag("fluid-app-drawer").assertExists()
    }
    @Test fun dockLongPressShowsBinAndDropRemovesOnlyShortcut() {
        lateinit var app: com.example.launcher.data.InstalledApp
        compose.waitUntil(10000) { androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java].state.value.apps.isNotEmpty() }
        compose.runOnIdle {
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java]
            app=model.state.value.apps.first()
            model.configure { it.copy(dock=listOf(app.component.flattenToString()),dockCount=3) }
        }
        val card=compose.onNodeWithContentDescription("Dock ${app.label}")
        val source=card.fetchSemanticsNode().boundsInRoot
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val bin=compose.onNodeWithTag("dock-remove-target").assertExists().fetchSemanticsNode().boundsInRoot
        card.performTouchInput { moveTo(bin.center-source.topLeft,200) }
        compose.waitForIdle()
        compose.onNodeWithTag("dock-remove-target").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Ready to remove"))
        card.performTouchInput { up() }
        compose.waitForIdle()
        card.assertDoesNotExist()
        compose.runOnIdle {
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java]
            assertFalse(model.state.value.config.dock.contains(app.component.flattenToString()))
            assertTrue(model.state.value.apps.any { it.component==app.component })
            model.configure { it.copy(dock=listOf("@auto")) }
        }
    }
    @Test fun dockDragReordersApps() {
        var apps=emptyList<com.example.launcher.data.InstalledApp>()
        compose.waitUntil(10000) { androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java].state.value.apps.size>=3 }
        compose.runOnIdle {
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java]
            apps=model.state.value.apps.take(3)
            model.configure { it.copy(dock=apps.map { app -> app.component.flattenToString() },dockCount=3) }
        }
        val first=compose.onNodeWithContentDescription("Dock ${apps.first().label}")
        val source=first.fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithContentDescription("Dock ${apps.last().label}").fetchSemanticsNode().boundsInRoot
        first.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        first.performTouchInput { moveTo(target.center-source.topLeft,200) }
        compose.waitForIdle()
        first.performTouchInput { up() }
        compose.runOnIdle {
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java]
            assertEquals(listOf(apps[1],apps[2],apps[0]).map { it.component.flattenToString() },model.state.value.config.dock)
        }
    }
    @Test fun dockSettingsControlCountAndSize() {
        compose.onNodeWithContentDescription("Customize home").performClick()
        compose.onNodeWithContentDescription("Home settings").performClick()
        compose.onNodeWithText("Dock",substring=false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Dock icon count").performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        compose.onNodeWithContentDescription("Dock icon size").performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        compose.runOnIdle {
            val config=androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java].state.value.config
            assertEquals(6,config.dockCount)
            assertEquals(40,config.dockIconSize)
        }
    }
    @Test fun notificationTapDeliversOriginalDestination() {
        val destination=java.util.concurrent.atomic.AtomicReference<String>()
        val receiver=object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) { destination.set(intent.getStringExtra("destination")) }
        }
        val action="com.example.launcher.TEST_NOTIFICATION"
        androidx.core.content.ContextCompat.registerReceiver(compose.activity,receiver,android.content.IntentFilter(action),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val intent=android.content.Intent(action).setPackage(compose.activity.packageName).putExtra("destination","conversation-42")
        val pending=android.app.PendingIntent.getBroadcast(compose.activity,4242,intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        try {
            compose.runOnIdle {
                FluidNotifications.mutable.value=listOf(FluidNotification("tap-test","Test app","Open conversation","Message",System.currentTimeMillis(),null,pending,true))
            }
            compose.onNodeWithTag("notification-card:tap-test").performClick()
            compose.waitUntil(5000) { destination.get()=="conversation-42" }
        } finally { pending.cancel();compose.activity.unregisterReceiver(receiver) }

    }
    @Test fun notificationsExpandAllAndScrollWithHome() {
        val time=java.time.Instant.parse("2022-01-03T10:00:00Z").toEpochMilli()
        compose.runOnIdle {
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)[FluidModel::class.java]
            model.configure { it.copy(widgets=listOf(FluidWidget.NOTIFICATIONS,FluidWidget.AGENDA)) }
            FluidNotifications.mutable.value=(1..7).map { FluidNotification("inline-$it","App $it","Notification $it","Preview message $it",time,null,null,true) }
            FluidNotifications.cancel={key -> FluidNotifications.mutable.value=FluidNotifications.mutable.value.filterNot { it.key==key } }
        }
        val cards=SemanticsMatcher("Notification cards") { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("notification-card:")==true }
        compose.onNodeWithText("Notification(7)").assertExists()
        compose.onNodeWithText("7 notifications").assertDoesNotExist()
        compose.onNodeWithTag("notification-compact-stack",useUnmergedTree=true).assertExists()
        compose.onAllNodes(cards).assertCountEquals(3)
        compose.onNodeWithTag("notification-icon:inline-1",useUnmergedTree=true).assertWidthIsEqualTo(32.dp)
        compose.onNodeWithText(notificationTimestamp(time)).assertExists()
        fun captureHome(name: String) {
            val bitmap=compose.onNodeWithTag("fluid-home",useUnmergedTree=true).captureToImage().asAndroidBitmap()
            val temporary=java.io.File(compose.activity.filesDir,"$name.tmp")
            temporary.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            check(temporary.renameTo(java.io.File(compose.activity.filesDir,"$name.png")))
        }
        captureHome("notification-compact")
        compose.onNodeWithText("See all").performClick()
        compose.onNodeWithTag("fluid-hero-overlay").assertDoesNotExist()
        compose.onNodeWithTag("notification-inline-expanded",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("Collapse").assertExists()
        compose.onAllNodes(cards).assertCountEquals(7)
        compose.onAllNodesWithText(notificationTimestamp(time)).assertCountEquals(7)
        compose.onNodeWithTag("fluid-home-date",useUnmergedTree=true).assertIsDisplayed()
        captureHome("notification-expanded")
        compose.onNodeWithTag("notification-card:inline-1").performTouchInput { swipeUp() }
        compose.onNodeWithTag("fluid-home-date",useUnmergedTree=true).assertIsNotDisplayed()
        compose.onNodeWithTag("notification-card:inline-7").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("fluid-widget-AGENDA",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        captureHome("notification-scrolled")
        compose.onNodeWithText("Collapse").performScrollTo().performClick()
        compose.onNodeWithTag("notification-compact-stack",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("See all").assertExists()
        compose.onAllNodes(cards).assertCountEquals(3)
    }
    @After fun clearFixtureNotifications() {
        FluidNotifications.mutable.value=emptyList();FluidNotifications.cancel=null
    }
    @Test fun notificationStackSwipesBothDirectionsAndClearsRemainingCards() {
        compose.runOnIdle {
            FluidNotifications.mutable.value=(1..3).map{FluidNotification("test-$it","Test app","Notice $it","A real notification shape",System.currentTimeMillis(),null,null,true)}
            FluidNotifications.cancel={key->FluidNotifications.mutable.value=FluidNotifications.mutable.value.filterNot{it.key==key}}
        }
        (1..3).forEach { compose.onNodeWithTag("notification-card:test-$it").assertExists() }
        compose.onNodeWithTag("notification-card:test-1").performTouchInput{swipeLeft()}
        compose.waitUntil(5000){FluidNotifications.items.value.size==2}
        compose.onNodeWithTag("notification-card:test-2").performTouchInput{swipeRight()}
        compose.waitUntil(5000){FluidNotifications.items.value.size==1}
        compose.onNodeWithText("Clear all").performClick()
        compose.waitUntil(5000){FluidNotifications.items.value.isEmpty()}
        compose.onNodeWithTag("notification-card:test-3").assertDoesNotExist()
    }
}
