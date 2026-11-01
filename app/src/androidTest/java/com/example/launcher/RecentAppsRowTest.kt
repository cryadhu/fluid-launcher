package com.example.launcher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.launcher.data.*
import com.example.launcher.ui.*
import com.example.launcher.ui.theme.LauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecentAppsRowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recentRowShowsAtMostFourAndDisappearsWithDividerWhenEmpty() {
        var apps by mutableStateOf((0..4).map { InstalledApp(ComponentName("test.app$it","Main"),"App $it",AppIconStyle("app",AppAccent.MINT)) })
        var opened = ""
        val drag = LauncherDragState()
        compose.setContent { LauncherTheme(badgePackages = setOf("test.app0")) { RecentAppsRow(apps,drag,{ opened = it.label },{}) } }
        compose.onNodeWithTag("recent-apps-section").assertExists()
        compose.onNodeWithTag("recent-apps-divider").assertExists()
        compose.onNodeWithText("App 4").assertDoesNotExist()
        compose.onNodeWithText("App 0").performClick()
        assertEquals("App 0",opened)
        assertTrue(drag.hits.keys.any { it.startsWith("drawer:recent:") })
        compose.onNodeWithTag("notification-dot:test.app0/Main",useUnmergedTree=true).assertExists()
        compose.runOnIdle { apps = emptyList() }
        compose.onNodeWithTag("recent-apps-section").assertDoesNotExist()
        compose.onNodeWithTag("recent-apps-divider").assertDoesNotExist()
    }
    @Test fun favoritesLaunchAndDisappearTogetherWithDivider() {
        var apps by mutableStateOf(listOf(InstalledApp(ComponentName("test.favorite", "Main"), "Favorite app", AppIconStyle("app", AppAccent.MINT))))
        var opened = false
        val drag = LauncherDragState()
        compose.setContent { LauncherTheme { FavoriteAppsRow(apps, drag, { opened = true }, {}) } }
        compose.onNodeWithTag("favorite-apps-divider").assertExists()
        compose.onNodeWithText("Favorite app").performClick()
        assertTrue(opened)
        assertTrue(drag.hits.keys.any { it.startsWith("drawer:favorite:") })
        compose.runOnIdle { apps = emptyList() }
        compose.onNodeWithTag("favorite-apps-section").assertDoesNotExist()
        compose.onNodeWithTag("favorite-apps-divider").assertDoesNotExist()
    }
}
