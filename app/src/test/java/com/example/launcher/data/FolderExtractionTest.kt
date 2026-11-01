package com.example.launcher.data

import org.junit.Assert.*
import org.junit.Test

class FolderExtractionTest {
    @Test fun extractionLeavesSourceUnchangedUntilValidDropAndPreservesOtherMembers() {
        val original = listOf(HomeEntry("folder:work", "Work", listOf("a", "b"), 3), appEntry("c").copy(slot = 6))
        val base = removeFolderMember(original, "folder:work", "a")
        assertEquals(listOf("a", "b"), original.first().components)
        val result = moveHomeEntry(base, appEntry("a"), 15)
        assertEquals(listOf("b"), result.first { it.id == "folder:work" }.components)
        assertEquals(3, result.first { it.id == "folder:work" }.slot)
        assertEquals(15, result.first { it.id == "a" }.slot)
        assertEquals(setOf("a", "b", "c"), result.flatMap { it.components }.toSet())
    }
    @Test fun lastMemberFreesFolderCellAndFullOrBlockedDropDoesNotLoseApps() {
        val folder = HomeEntry("folder:x", "X", listOf("a"), 0)
        val base = removeFolderMember(listOf(folder), folder.id, "a")
        assertTrue(base.isEmpty())
        assertEquals(0, moveHomeEntry(base, appEntry("a"), 0).single().slot)
        val full = (0..15).map { appEntry("app$it").copy(slot = it) }
        assertEquals(full, moveHomeEntry(full, appEntry("a"), 4))
        assertEquals(base, moveHomeEntry(base, appEntry("a"), 0, setOf(0)))
    }
    @Test fun callingActivitySelectionRejectsSmsAndPrefersDialer() {
        assertEquals(-1, callingActivityScore("com.truecaller.messaging.NewConversationActivity"))
        assertEquals(-1, callingActivityScore("com.truecaller.sms.GroupActivity"))
        assertTrue(callingActivityScore("com.truecaller.dialer.DialerActivity") > callingActivityScore("com.truecaller.ui.TruecallerInit"))
    }
}
