package com.example.launcher.fluid

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class FluidSettingsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun settingsBackReturnsToCustomizeAndWallpaperHasBothTargets() {
        val model=FluidModel(ApplicationProvider.getApplicationContext<Application>())
        model.configure { it.copy(autoWeather=false,homeWallpaper="Default",lockWallpaper="Default") }
        compose.setContent { FluidTheme(model.state.value.config) { FluidLauncher(model,{}) } }
        compose.onNodeWithContentDescription("Customize home").performClick()
        compose.onNodeWithContentDescription("Home settings").performClick()
        compose.onNodeWithTag("fluid-panel-Settings").assertExists()
        pressBack()
        compose.onNodeWithTag("fluid-panel-Customize").assertExists()
        compose.onNodeWithContentDescription("Wallpaper",substring=false).performClick()
        compose.onNodeWithContentDescription("Home wallpaper: Default").assertExists()
        compose.onNodeWithContentDescription("Home wallpaper: Lavender").assertExists()
        compose.onNodeWithContentDescription("Home wallpaper: Dawn").assertExists().performClick()
        compose.waitUntil(10000) { model.state.value.config.homeWallpaper=="Dawn" && !model.state.value.wallpaperApplying }
        val manager=android.app.WallpaperManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(manager.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)>0)
        compose.onNodeWithText("Lock screen wallpaper").performScrollTo()
        compose.onNodeWithContentDescription("Lock wallpaper: Default").assertExists()
        compose.onNodeWithContentDescription("Lock wallpaper: Dawn").assertExists()
        compose.onNodeWithContentDescription("Lock wallpaper: Lavender").assertExists()
        compose.onNodeWithContentDescription("Lock wallpaper: Lavender").performScrollTo().performClick()
        compose.waitUntil(10000) { model.state.value.config.lockWallpaper=="Lavender" && !model.state.value.wallpaperApplying }
        assertTrue(manager.getWallpaperId(android.app.WallpaperManager.FLAG_LOCK)>0)
    }
}
