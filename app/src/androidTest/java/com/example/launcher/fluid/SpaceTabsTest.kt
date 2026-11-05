package com.example.launcher.fluid

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpaceTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassSlidesBetweenTabsInsteadOfJumping() {
        compose.setContent { FluidTheme(FluidConfig()) {
            var selected by remember { mutableIntStateOf(0) }
            Box(Modifier.width(360.dp)) { GlassTabs(listOf("Work", "Personal", "Tools"), selected) { selected = it } }
        } }
        val glass = compose.onNodeWithTag("space-tab-glass")
        val start = glass.getBoundsInRoot().left
        val end = compose.onNodeWithText("Tools").getBoundsInRoot().left
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Tools").performClick()
        compose.mainClock.advanceTimeBy(96)
        val moving = glass.getBoundsInRoot().left
        assertTrue("glass should be between tabs mid-way: $start < $moving < $end", moving > start && moving < end)
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(end.value, glass.getBoundsInRoot().left.value, 1f)
        compose.onNodeWithText("Tools").assertIsSelected()
    }

    @Test fun editorAddsRenamesDeletesTabsAndChoosesApps() {
        val model = FluidModel(ApplicationProvider.getApplicationContext<Application>())
        model.configure { it.copy(spaces = defaultSpaces) }
        compose.setContent { FluidTheme(FluidConfig()) {
            val state by model.state.collectAsState()
            Column(Modifier.verticalScroll(rememberScrollState())) { SpaceTabsEditor(state, model, null) }
        } }
        compose.waitUntil(10000) { model.state.value.apps.isNotEmpty() }
        compose.onNodeWithTag("space-tab-new").performTextInput("Games")
        compose.onNodeWithText("Add").performClick()
        compose.runOnIdle { assertEquals(listOf("Work", "Personal", "Tools", "Games"), model.state.value.config.spaces.map { it.name }) }
        compose.onNodeWithText("Apps in Games").assertExists()
        val app = model.state.value.apps.first()
        compose.onNodeWithText(app.label).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(app.component.flattenToString() in model.state.value.config.spaces.last().apps) }
        compose.onNodeWithContentDescription("Rename Personal").performClick()
        compose.onNodeWithTag("space-tab-rename").performTextInput("Home")
        compose.onNodeWithContentDescription("Save name").performClick()
        compose.onNodeWithContentDescription("Delete Work").performClick()
        compose.runOnIdle { assertEquals(listOf("Home", "Tools", "Games"), model.state.value.config.spaces.map { it.name }) }
        model.configure { it.copy(spaces = defaultSpaces) }
    }
}
