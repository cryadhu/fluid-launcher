package com.example.launcher.data

import org.junit.Test
import org.junit.Assert.*

class HomeLayoutTest {
    @Test fun allSixteenSlotsSurvivePersistenceAndReordering() {
        val entries = (0 until HOME_CAPACITY).map { appEntry("app:$it").copy(slot = it) }
        assertEquals(16, HOME_CAPACITY)
        assertEquals(entries, decodeHomeLayout(encodeHomeLayout(entries)))
        val moved = moveHomeEntry(entries, entries.first(), 15)
        assertEquals(16, moved.size)
        assertEquals(entries.first().copy(slot = 15), moved.last())
        assertEquals(entries.map { it.id }.toSet(), moved.map { it.id }.toSet())
        assertEquals(entries, moveHomeEntry(entries, appEntry("extra"), 15))
    }
    @Test fun centerDropCreatesFolderWithoutLosingOtherItems() {
        val order = listOf(appEntry("a"), appEntry("b"), appEntry("c"))
        val result = mergeHomeEntry(order, order[0], 1)
        assertEquals(2, result.size)
        assertTrue(result[0].isFolder)
        assertEquals(listOf("b", "a"), result[0].components)
        assertEquals(appEntry("c").copy(slot = 2), result[1])
        assertEquals(1, result[0].slot)
    }
    @Test fun drawerDropAddsToExistingFolderAndDeduplicates() {
        val folder = HomeEntry("folder:work", "Work", listOf("a", "b"))
        val result = mergeHomeEntry(listOf(folder, appEntry("c")), appEntry("c"), 0)
        assertEquals(listOf(folder.copy(components = listOf("a", "b", "c"), slot = 0)), result)
        assertEquals(result, mergeHomeEntry(result, appEntry("c"), 0))
    }
    @Test fun edgeDropReordersFolderAsOneItem() {
        val folder = HomeEntry("folder:work", "Work", listOf("a", "b"))
        assertEquals(listOf(appEntry("c").copy(slot = 0), folder.copy(slot = 1)), moveHomeEntry(listOf(folder, appEntry("c")), folder, 1))
    }
    @Test fun namesAndMembersRoundTripIncludingAnEmptyLayout() {
        val entries = listOf(HomeEntry("folder:work", "Work & music", listOf("a", "b")), appEntry("c"))
        assertEquals(normalizeHomeLayout(entries), decodeHomeLayout(encodeHomeLayout(entries)))
        assertTrue(decodeHomeLayout(encodeHomeLayout(emptyList())).isEmpty())
    }
    @Test fun droppingOntoSelfOrDroppingAFolderDoesNotNestFolders() {
        val folder = HomeEntry("folder:x", "Folder", listOf("a", "b"))
        val order = listOf(folder, appEntry("c"))
        assertEquals(normalizeHomeLayout(order), mergeHomeEntry(order, folder, 1))
        assertEquals(normalizeHomeLayout(order), mergeHomeEntry(order, order[1], 1))
    }
    @Test fun movingToBottomKeepsTheEmptyCellsAndOtherIconsInPlace() {
        val entries = listOf(appEntry("a").copy(slot = 0), appEntry("b").copy(slot = 1))
        val moved = moveHomeEntry(entries, entries[0], 15)
        assertEquals(listOf(entries[1], entries[0].copy(slot = 15)), moved)
        assertEquals(moved, decodeHomeLayout(encodeHomeLayout(moved)))
        assertEquals(0, firstFreeHomeSlot(moved))
        val swapped = moveHomeEntry(moved, entries[1], 15)
        assertEquals(listOf(entries[0].copy(slot = 1), entries[1].copy(slot = 15)), swapped)
    }
    @Test fun oldLayoutsMigrateAndNewDrawerDropsDoNotLoseOccupiedIcons() {
        val legacy = """[{"id":"a","title":"","apps":["a"]},{"id":"b","title":"","apps":["b"]}]"""
        val old = decodeHomeLayout(legacy)
        assertEquals(listOf(0, 1), old.map { it.slot })
        val added = moveHomeEntry(old, appEntry("c"), 15)
        assertEquals(listOf(0, 1, 15), added.map { it.slot })
        val occupied = moveHomeEntry(added, appEntry("d"), 1)
        assertEquals(setOf("a", "b", "c", "d"), occupied.map { it.id }.toSet())
        assertEquals(2, occupied.first { it.id == "b" }.slot)
    }
}
