package com.example.launcher.data

import com.example.launcher.ui.placeDockApp
import org.junit.Assert.*
import org.junit.Test

class DockLayoutTest {
    @Test fun dockAcceptsUpToFiveWithoutReplacingExistingItems() {
        val four = listOf("phone", "messages", "camera", "browser")
        val five = placeDockApp(four, "music", -1, 2)
        assertEquals(listOf("phone", "messages", "music", "camera", "browser"), five)
        assertEquals(five, placeDockApp(five, "sixth", -1, 3))
        assertEquals(listOf("music"), placeDockApp(emptyList(), "music", -1, 0))
    }
    @Test fun dockReordersExistingEntriesAndDoesNotDuplicateApps() {
        assertEquals(listOf("b", "c", "a"), placeDockApp(listOf("a", "b", "c"), "a", 0, 2))
        assertEquals(listOf("c", "a", "b"), placeDockApp(listOf("a", "b", "c"), "c", -1, 0))
        assertEquals(listOf("a"), placeDockApp(listOf("a"), "a", 0, 1))
    }
}
