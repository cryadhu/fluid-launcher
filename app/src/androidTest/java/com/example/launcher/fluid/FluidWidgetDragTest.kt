package com.example.launcher.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import com.example.launcher.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FluidWidgetDragTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun openPlan() {
        compose.waitForIdle()
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[FluidModel::class.java]
                .configure { it.copy(autoWeather = false, planWidgets = listOf(FluidWidget.TASKS, FluidWidget.QUICK_ACTIONS)) }
        }
        compose.onNodeWithContentDescription("Plan").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fluid-plan", useUnmergedTree = true).assertExists()
        compose.onNode(
            hasTestTag("fluid-plan") and hasAnyDescendant(hasTestTag("fluid-widget-TASKS")),
            useUnmergedTree = true
        ).assertExists()
    }

    private fun planWidget(kind: FluidWidget) = compose.onNode(
        hasTestTag("fluid-widget-${kind.name}") and hasAnyAncestor(hasTestTag("fluid-plan")),
        useUnmergedTree = true
    )

    @Test fun longPressDragWithoutLiftingReordersAndKeepsTheNewSlots() {
        val tasks = planWidget(FluidWidget.TASKS)
        val quick = planWidget(FluidWidget.QUICK_ACTIONS)
        val startTasks = tasks.fetchSemanticsNode().boundsInRoot
        val startQuick = quick.fetchSemanticsNode().boundsInRoot
        assertTrue("Tasks should start above Quick actions", startTasks.top < startQuick.top)

        val travel = startQuick.center.y - startTasks.center.y + 120f
        // One continuous press: long press, then drag, then release. Before the fix the
        // detector node was torn down when the editor activated, so this stream was lost.
        tasks.performTouchInput {
            down(Offset(60f, 30f))
            advanceEventTime(800)
            // The long-press timeout swallows the move that unblocks it, so keep moving.
            repeat(10) { moveBy(Offset(0f, travel / 8f), delayMillis = 40) }
            up()
        }
        compose.onNodeWithText("Rearrange").assertExists()
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()

        val settledTasks = tasks.fetchSemanticsNode().boundsInRoot
        val settledQuick = quick.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The dragged widget should land in the slot it was dropped on (tasks=$settledTasks quick=$settledQuick)",
            settledTasks.top > settledQuick.top
        )
        assertTrue(
            "The other widget should take the freed slot",
            settledQuick.top < startQuick.top
        )

        // A second, separate gesture must start from the settled slots instead of the
        // stale lifted offset, and must swap the widgets back.
        val secondTravel = settledTasks.center.y - settledQuick.center.y + 120f
        tasks.performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(10) { moveBy(Offset(0f, -secondTravel / 8f), delayMillis = 40) }
            up()
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()

        val finalTasks = tasks.fetchSemanticsNode().boundsInRoot
        val finalQuick = quick.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Dragging back up should restore the original order (tasks=$finalTasks quick=$finalQuick)",
            finalTasks.top < finalQuick.top
        )
    }
}
