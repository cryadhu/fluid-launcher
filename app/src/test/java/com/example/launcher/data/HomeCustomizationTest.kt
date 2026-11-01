package com.example.launcher.data

import org.junit.Test
import org.junit.Assert.*

class HomeCustomizationTest {
    @Test fun pageReorderingKeepsRequiredPagesFixedAndPersistsOtherPages() {
        val pages = initialPages + LauncherPage(4, "Page 1")
        val moved = reorderHomePages(pages, 4, 2)
        assertEquals(listOf(0,1,4,2,3), moved.map { it.id })
        assertEquals(moved, decodeCustomization(encodeCustomization(HomeCustomization(pages = moved))).pages)
        assertEquals(listOf(0,1,2,3,4), reorderHomePages(moved, 4, 3).map { it.id })
        for (fixed in listOf(0,1)) {
            assertEquals(pages, reorderHomePages(pages, fixed, 3))
            assertEquals(pages, reorderHomePages(pages, 3, fixed))
        }
        assertEquals(pages, reorderHomePages(pages, 99, 2))
    }
    @Test fun initialSettingsShortcutIsRemovedWithoutMovingOtherAppsOrFolderMembers() {
        val entries = listOf(appEntry("com.android.settings/.Settings").copy(slot = 0), appEntry("other/.Main").copy(slot = 15), HomeEntry("folder:work", "Work", listOf("com.android.settings/.Settings"), 4))
        assertEquals(listOf(entries[1], entries[2]), withoutHomeSettingsShortcut(entries))
    }
    @Test fun widgetsFitContiguousGridCellsWithoutOverlappingIcons() {
        val icons = (0..7).toSet()
        assertEquals(8, findWidgetSlot(HomeWidgetKind.CLOCK, icons))
        assertEquals((8..15).toSet(), widgetCells(HomeWidgetKind.CLOCK, 8))
        assertEquals(-1, findWidgetSlot(HomeWidgetKind.CLOCK, icons + (8..15)))
        assertTrue(widgetCells(HomeWidgetKind.CLOCK, 1).isEmpty())
        assertEquals(10, findWidgetSlot(HomeWidgetKind.FOCUS, icons + widgetCells(HomeWidgetKind.TASKS, 8)))
    }
    @Test fun blockedWidgetCellsCannotReceiveOrDisplaceAppShortcuts() {
        val icons = normalizeHomeLayout(listOf(appEntry("a"), appEntry("b")))
        val blocked = widgetCells(HomeWidgetKind.CLOCK, 8)
        assertEquals(icons, moveHomeEntry(icons, icons[0], 15, blocked))
        val moved = moveHomeEntry(icons, appEntry("c"), 1, blocked)
        assertEquals(setOf(0,1,2), moved.map { it.slot }.toSet())
    }
    @Test fun pageLayoutsWidgetsAndPreferencesRoundTripWithRequiredPages() {
        val config = HomeCustomization(pages = listOf(initialPages[0], initialPages[1], LauncherPage(4, "Personal"), initialPages[3]),
            layouts = mapOf(4 to listOf(appEntry("a").copy(slot = 15))), widgets = listOf(HomeWidget("clock", HomeWidgetKind.CLOCK, 4, 0)), locked = true, wallpaper = "Midnight", city = "Bengaluru")
        assertEquals(config, decodeCustomization(encodeCustomization(config)))
        assertEquals(listOf(0,1), decodeCustomization("""{"pages":[],"widgets":[]}""").pages.map { it.id })
    }
    @Test fun invalidAndOverlappingWidgetsAreRejectedOnLoad() {
        val config = HomeCustomization(widgets = listOf(HomeWidget("a", HomeWidgetKind.CLOCK, 1, 8), HomeWidget("b", HomeWidgetKind.FOCUS, 1, 8), HomeWidget("c", HomeWidgetKind.CLOCK, 2, 0)))
        assertEquals(listOf("a"), decodeCustomization(encodeCustomization(config)).widgets.map { it.id })
    }
    @Test fun allReferenceWidgetsAreAvailableInTheirThreeGroups() {
        assertEquals(10, HomeWidgetKind.entries.size)
        assertEquals(3, HomeWidgetKind.entries.map { it.group }.distinct().size)
        HomeWidgetKind.entries.forEach { assertTrue(it.title, widgetCells(it, 0).isNotEmpty()) }
        assertTrue(initialPages.first { it.id == 0 }.protected)
        assertTrue(initialPages.first { it.id == 1 }.protected)
        assertFalse(initialPages.first { it.id == 2 }.protected)
    }
}
