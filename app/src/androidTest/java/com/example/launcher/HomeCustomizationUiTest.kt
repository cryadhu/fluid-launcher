package com.example.launcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.launcher.data.*
import org.junit.*
import org.junit.Assert.*

class HomeCustomizationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LauncherViewModel
    @Before fun ready() {
        model = ViewModelProvider(compose.activity)[LauncherViewModel::class.java]
        compose.waitUntil(10000) { model.uiState.value.settingsLoaded && !model.uiState.value.appsLoading }
        compose.runOnIdle { model.resetHomeSettings() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant com.example.launcher android.permission.POST_NOTIFICATIONS").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    @After fun restoreInitialSetup() { compose.runOnIdle { model.resetHomeSettings() } }
    @Test fun homeSettingsApplyLabelsGridSearchAndPageVisibility() {
        compose.runOnIdle { model.showHomePanel(HomePanel.SETTINGS) }
        compose.onNodeWithTag("setting-Show app labels").performScrollTo().performClick()
        assertFalse(model.uiState.value.customization.preferences.showLabels)
        compose.onNodeWithText("Layout and behavior").performClick()
        compose.onNodeWithText("Grid size").performScrollTo().performClick()
        compose.onNodeWithText("4 × 2").performClick()
        assertEquals(2,model.uiState.value.customization.preferences.gridRows)
        compose.onNodeWithText("Search bar").performClick()
        compose.onNodeWithText("Bottom").performClick()
        compose.onNodeWithTag("setting-Today page").performScrollTo().performClick()
        assertTrue(2 in model.uiState.value.customization.preferences.hiddenPages)
        compose.onNodeWithTag("setting-Swipe up for app drawer").performScrollTo().performClick()
        assertFalse(model.uiState.value.customization.preferences.swipeUp)
        compose.runOnIdle { model.showHomePanel(null); model.page(1) }
        compose.onNodeWithTag("home-slot-15").assertDoesNotExist()
        compose.onNodeWithText("Search apps").assertIsDisplayed()
    }
    @Test fun themesReplaceTopShortcutAndApplyWhileWidgetShortcutsRemain() {
        compose.runOnIdle { model.showHomePanel(HomePanel.CUSTOMIZE) }
        compose.onNodeWithText("ADD WIDGETS").assertExists()
        compose.onNodeWithText("Themes").performClick()
        compose.onNodeWithTag("theme-Solar").performClick()
        compose.runOnIdle { assertEquals("Solar", model.uiState.value.customization.theme) }
        compose.onNodeWithTag("follow-system-theme").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(model.uiState.value.customization.followSystemTheme) }
        compose.onNodeWithTag("theme-Ember").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Ember", model.uiState.value.customization.theme)
            assertFalse(model.uiState.value.customization.followSystemTheme)
        }
    }
    @Test fun pageEditorDragReordersOptionalPagesAndKeepsHomeFixed() {
        compose.runOnIdle { repeat(2) { model.addHomePage() }; model.showHomePanel(HomePanel.PAGES) }
        val before = model.uiState.value.customization.pages.map { it.id }
        val sourceId = before[2]; val targetId = before[3]
        val grid = compose.onNodeWithTag("page-editor-scroll")
        val gridBounds = grid.fetchSemanticsNode().boundsInRoot
        val source = compose.onNodeWithTag("page-preview-$sourceId").fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val target = compose.onNodeWithTag("page-preview-$targetId").fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        grid.performTouchInput { down(source); advanceEventTime(800); moveTo(target, 500); up() }
        compose.waitUntil(5000) { model.uiState.value.customization.pages.map { it.id } != before }
        assertEquals(listOf(0,1,targetId,sourceId,2,3), model.uiState.value.customization.pages.map { it.id })
        compose.onNodeWithContentDescription("Remove Home page").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove For you page").assertDoesNotExist()
        compose.onNodeWithText("Required").assertDoesNotExist()
        val title = compose.onNodeWithText("Edit pages").fetchSemanticsNode().boundsInRoot
        val reset = compose.onNodeWithText("Reset").fetchSemanticsNode().boundsInRoot
        assertEquals(title.center.y, reset.center.y, 3f)
    }
    @Test fun catalogAndPageEditorRespondToFingerSwipes() {
        compose.runOnIdle { model.showHomePanel(HomePanel.WIDGETS) }
        val catalog = compose.onNodeWithTag("widget-catalog-scroll")
        val before = catalog.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        catalog.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(catalog.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value() > before)
        compose.runOnIdle { repeat(8) { model.addHomePage() }; model.showHomePanel(HomePanel.PAGES) }
        val pages = compose.onNodeWithTag("page-editor-scroll")
        pages.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(pages.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
    }
    @Test fun emptyHomeLongPressOpensReferenceSheetAndAddsClockWidget() {
        compose.onNodeWithContentDescription("Android settings").assertDoesNotExist()
        compose.onNodeWithTag("home-slot-15").performTouchInput { longClick() }
        compose.onNodeWithTag("customize-home-sheet").assertIsDisplayed()
        compose.onNode(hasText("Settings") and hasAnyAncestor(hasTestTag("customize-home-sheet"))).performClick()
        compose.onNodeWithText("Home settings").assertIsDisplayed()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.runOnIdle { model.showHomePanel(HomePanel.CUSTOMIZE) }
        compose.onNodeWithText("See all").performClick()
        compose.onNodeWithTag("add-widget-CLOCK").performScrollTo().performClick()
        compose.onNodeWithTag("home-widget-CLOCK").assertIsDisplayed()
        assertEquals(8, model.uiState.value.customization.widgets.single().slot)
        capture("home-clock-widget.png")
    }
    @Test fun pagesCanBePreviewedRemovedAndResetExceptForRequiredPages() {
        compose.runOnIdle { model.showHomePanel(HomePanel.CUSTOMIZE) }
        compose.onNodeWithText("Add page").performClick()
        val id = model.uiState.value.page
        assertEquals(listOf(0,1,id,2,3), model.uiState.value.customization.pages.map { it.id })
        compose.runOnIdle { model.pin(model.uiState.value.apps.first()); model.showHomePanel(HomePanel.PAGES) }
        assertEquals(1, model.uiState.value.customization.layouts[id]!!.size)
        compose.onNodeWithTag("page-preview-0").assertExists()
        compose.onNodeWithTag("page-preview-1").assertExists()
        compose.onNodeWithContentDescription("Remove Home page").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove For you page").assertDoesNotExist()
        capture("edit-pages.png")
        compose.onNodeWithContentDescription("Remove Today page").performScrollTo().performClick()
        compose.runOnIdle { model.removeHomePage(0); model.removeHomePage(1); model.removeHomePage(3) }
        assertEquals(listOf(0,1,id), model.uiState.value.customization.pages.map { it.id })
        compose.onNodeWithText("Reset").performClick()
        val resetButtons = compose.onAllNodesWithText("Reset")
        resetButtons[resetButtons.fetchSemanticsNodes().lastIndex].performClick()
        compose.waitUntil(5000) { model.uiState.value.customization.pages == initialPages }
        assertTrue(model.uiState.value.customization.widgets.isEmpty())
        assertTrue(model.uiState.value.customization.layouts.isEmpty())
        assertEquals(1, model.uiState.value.page)
    }
    @Test fun allTenWidgetsAddWithoutOverlapsAndPersistAcrossFreshLoad() {
        for (kind in HomeWidgetKind.entries) {
            compose.runOnIdle { model.showHomePanel(HomePanel.CUSTOMIZE); model.showHomePanel(HomePanel.WIDGETS) }
            compose.onNodeWithText(kind.group).performScrollTo().performClick()
            compose.onNodeWithTag("add-widget-${kind.name}").performScrollTo().performClick()
            compose.onNodeWithTag("home-widget-${kind.name}").assertIsDisplayed()
        }
        val config = model.uiState.value.customization
        assertEquals(HomeWidgetKind.entries.toSet(), config.widgets.map { it.kind }.toSet())
        for (page in config.pages.filter { it.isHome }) {
            val cells = config.widgets.filter { it.pageId == page.id }.flatMap { widgetCells(it.kind, it.slot) }
            assertEquals(cells.size, cells.distinct().size)
            assertTrue(model.homeEntries(page.id).none { it.slot in cells })
        }
        compose.waitUntil(5000) {
            java.io.File(compose.activity.filesDir, "datastore/launcher_settings.preferences_pb").let { it.exists() && it.readBytes().toString(Charsets.ISO_8859_1).contains(encodeCustomization(config)) }
        }
        val store = ViewModelStore()
        try {
            lateinit var fresh: LauncherViewModel
            compose.runOnIdle { fresh = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = LauncherViewModel(compose.activity.application, SavedStateHandle()) as T
            })[LauncherViewModel::class.java] }
            compose.waitUntil(10000) { fresh.uiState.value.settingsLoaded }
            assertEquals(config, fresh.uiState.value.customization)
        } finally { compose.runOnIdle { store.clear() } }
    }
    @Test fun layoutLockPreventsAppAndWidgetChanges() {
        compose.runOnIdle { model.showHomePanel(HomePanel.CUSTOMIZE); model.addHomeWidget(HomeWidgetKind.TASKS); model.setHomeLocked(true) }
        val before = model.uiState.value.customization
        val home = model.homeEntries()
        compose.runOnIdle {
            model.pin(model.uiState.value.apps.last()); model.addHomeWidget(HomeWidgetKind.FOCUS)
            model.removeHomeWidget(before.widgets.single().id); model.saveHomeLayout(emptyList())
        }
        assertEquals(before, model.uiState.value.customization)
        assertEquals(home, model.homeEntries())
        compose.runOnIdle { model.setHomeLocked(false); model.removeHomeWidget(before.widgets.single().id) }
        assertTrue(model.uiState.value.customization.widgets.isEmpty())
    }
    @Test fun addedPageCanBeRemovedAlongWithItsOwnLayout() {
        var id = -1
        compose.runOnIdle { id = model.addHomePage(); model.pin(model.uiState.value.apps.first()); model.showHomePanel(HomePanel.CUSTOMIZE); model.addHomeWidget(HomeWidgetKind.TASKS); model.showHomePanel(HomePanel.PAGES) }
        compose.onNodeWithContentDescription("Remove Page 1 page").performScrollTo().performClick()
        assertFalse(model.uiState.value.customization.pages.any { it.id == id })
        assertFalse(model.uiState.value.customization.layouts.containsKey(id))
        assertFalse(model.uiState.value.customization.widgets.any { it.pageId == id })
        assertEquals(1, model.uiState.value.page)
    }
    @Test fun drawerDragTargetsAddedPageWithoutChangingMainHome() {
        val primary = model.homeEntries(1)
        var pageId = -1
        compose.runOnIdle { pageId = model.addHomePage() }
        val target = compose.onNodeWithTag("home-slot-15").fetchSemanticsNode().boundsInRoot.center
        val app = model.uiState.value.apps.first()
        compose.runOnIdle { model.drawer(true); model.query(app.label) }
        val source = compose.onNode(hasText(app.label) and !hasSetTextAction()).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(source) }
        compose.mainClock.advanceTimeBy(750)
        compose.onRoot().performTouchInput { moveTo(source + androidx.compose.ui.geometry.Offset(2f, 0f), 16); moveTo(target, 400); up() }
        compose.waitUntil(5000) { model.uiState.value.customization.layouts[pageId]?.singleOrNull()?.slot == 15 }
        assertEquals(app.component.flattenToString(), model.uiState.value.customization.layouts[pageId]!!.single().id)
        assertEquals(primary, model.homeEntries(1))
        assertEquals(pageId, model.uiState.value.page)
        compose.onNodeWithText(app.label).assertIsDisplayed()
    }
    @Test fun widgetCanMoveToAnotherPageAndBeRemovedFromItsMenu() {
        compose.runOnIdle { model.showHomePanel(HomePanel.CUSTOMIZE); model.addHomeWidget(HomeWidgetKind.TASKS); model.addHomePage(); model.page(1) }
        compose.onNodeWithTag("home-widget-TASKS").performTouchInput { longClick() }
        compose.onNodeWithText("Move to page").performClick()
        compose.onNodeWithText("Page 1").performClick()
        assertTrue(model.uiState.value.page >= 4)
        compose.onNodeWithTag("home-widget-TASKS").assertIsDisplayed().performTouchInput { longClick() }
        compose.onNodeWithText("Remove widget").performClick()
        assertTrue(model.uiState.value.customization.widgets.isEmpty())
    }
    @Test fun taskNoteAndFocusWidgetsPerformRealActions() {
        for (kind in listOf(HomeWidgetKind.TASKS, HomeWidgetKind.NOTE, HomeWidgetKind.FOCUS)) {
            compose.runOnIdle { model.showHomePanel(HomePanel.CUSTOMIZE); model.addHomeWidget(kind) }
        }
        fun show(kind: HomeWidgetKind) { compose.runOnIdle { model.page(model.uiState.value.customization.widgets.first { it.kind == kind }.pageId) }; compose.waitForIdle() }
        show(HomeWidgetKind.FOCUS)
        compose.onNodeWithText("Start focus").performClick()
        assertTrue(model.uiState.value.focusUntil > System.currentTimeMillis())
        compose.onNodeWithText("Pause focus").performClick()
        assertEquals(0L, model.uiState.value.focusUntil)
        show(HomeWidgetKind.NOTE)
        compose.onNodeWithContentDescription("Write quick note").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Widget note test")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { model.uiState.value.notes.any { it.body == "Widget note test" } }
        show(HomeWidgetKind.TASKS)
        compose.onNodeWithText(if (model.uiState.value.tasks.isEmpty()) "Add a task" else "+ Task").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Widget task test")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { model.uiState.value.tasks.any { it.text == "Widget task test" } }
    }
    private fun capture(name: String) { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
        java.io.File(compose.activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    } }
}
