package com.example.launcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@RunWith(AndroidJUnit4::class)
class LauncherTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun tasksAndNotesCommitToDisk() {
        val taskText = "Persistence task ${System.nanoTime()}"
        val noteText = "Persistence note ${System.nanoTime()}"
        compose.waitUntil(10000) { ViewModelProvider(compose.activity)[LauncherViewModel::class.java].uiState.value.settingsLoaded }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LauncherViewModel::class.java].page(3) }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(taskText)
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText(taskText).assertExists()
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextInput(noteText)
        compose.onNodeWithContentDescription("Save note").performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(5000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            SQLiteDatabase.openDatabase(context.getDatabasePath("cry.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM notes WHERE body = ?", arrayOf(noteText)).use { it.moveToFirst(); it.getInt(0) > 0 }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SQLiteDatabase.openDatabase(context.getDatabasePath("cry.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM tasks WHERE text = ?", arrayOf(taskText)).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        }
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { ViewModelProvider(compose.activity)[LauncherViewModel::class.java].page(3) }
        compose.onNodeWithText(taskText).assertExists()
    }

    @Test fun drawerFiltersAndBackReturnsHome() {
        compose.waitUntil(10000) { ViewModelProvider(compose.activity)[LauncherViewModel::class.java].uiState.value.settingsLoaded }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LauncherViewModel::class.java].page(1) }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LauncherViewModel::class.java].drawer(true) }
        compose.onNodeWithText("Search apps").performTextInput("no-app-has-this-name-93")
        compose.onNodeWithText("No apps match your search").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("launcher-search").assertDoesNotExist()
    }
    @Test fun drawerGridUsesFormerBottomHandleSpace() {
        val model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        compose.runOnIdle { model.page(1) }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LauncherViewModel::class.java].drawer(true) }
        compose.onNodeWithContentDescription("Close app drawer").assertDoesNotExist()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("all-apps-grid").fetchSemanticsNode().boundsInRoot
        val nav = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val padding = 8 * compose.activity.resources.displayMetrics.density
        org.junit.Assert.assertEquals(root.bottom - nav - padding, grid.bottom, 2f)
    }
}
