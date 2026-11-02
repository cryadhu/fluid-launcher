package com.example.launcher.fluid

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetSizeOptionsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun sizeLabelsStayOnOneLineOnNarrowLargeTextScreens() {
        compose.setContent { FluidTheme(FluidConfig()) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Box(Modifier.width(240.dp)){WidgetSizeOptions("Standard") {}}
            }
        } }
        val bounds=listOf("Compact","Standard","Large").map { label ->
            val layouts=mutableListOf<TextLayoutResult>()
            val node=compose.onNodeWithText(label,useUnmergedTree=true)
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertEquals(1,layouts.single().lineCount)
            node.fetchSemanticsNode().boundsInRoot
        }
        assertTrue(bounds[0].bottom<bounds[1].top)
        assertTrue(bounds[1].bottom<bounds[2].top)
    }
}
