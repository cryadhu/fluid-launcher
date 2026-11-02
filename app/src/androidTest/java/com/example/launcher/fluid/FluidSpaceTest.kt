package com.example.launcher.fluid

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FluidSpaceTest {
    @get:Rule val compose=createComposeRule()
    private val model=FluidModel(ApplicationProvider.getApplicationContext<Application>())
    @Test fun homeReturnSelectsSecondPageAndSpaceWidgetsCanBeRemoved() {
        model.configure { it.copy(widgets=FluidConfig().widgets,spaceWidgets=listOf(FluidWidget.TASKS),autoWeather=false) }
        compose.setContent { FluidTheme(model.state.value.config) { FluidLauncher(model,{}) } }
        compose.onNodeWithTag("fluid-home").assertExists()
        compose.onNodeWithContentDescription("Markets").performClick()
        compose.onNodeWithTag("fluid-markets").assertExists()
        compose.runOnIdle { model.returnHome() }
        compose.onNodeWithTag("fluid-home").assertExists()
        compose.onNodeWithContentDescription("Space").performClick()
        compose.onNode(hasTestTag("fluid-widget-AGENDA") and hasAnyAncestor(hasTestTag("fluid-space"))).assertDoesNotExist()
        compose.waitUntil(10000){model.state.value.apps.size>=3}
        compose.runOnIdle {
            val apps=model.state.value.apps
            model.configure{it.copy(work=setOf(apps[0].component.flattenToString()),personal=setOf(apps[1].component.flattenToString()),tools=setOf(apps[2].component.flattenToString()))}
        }
        compose.onNodeWithTag("space-apps-Work").assertExists()
        compose.onNodeWithText("Personal",substring=false).performClick()
        compose.onNodeWithTag("space-apps-Personal").assertExists()
        compose.onNodeWithText("Tools",substring=false).performClick()
        compose.onNodeWithTag("space-apps-Tools").assertExists()
        val task=compose.onNodeWithTag("fluid-widget-TASKS")
        task.performScrollTo().performTouchInput{longClick()}
        compose.onNodeWithText("Remove widget").performClick()
        task.assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(FluidWidget.TASKS in model.state.value.config.spaceWidgets)
            assertTrue(FluidWidget.AGENDA in model.state.value.config.widgets)
        }
    }
}
