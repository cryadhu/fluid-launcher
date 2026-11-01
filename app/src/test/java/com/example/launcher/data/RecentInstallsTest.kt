package com.example.launcher.data

import org.junit.Assert.*
import org.junit.Test

class RecentInstallsTest {
    @Test fun onlyFirstInstallsInsideTwentyMinuteWindowQualify() {
        val now = 3_000_000L
        val stamps = listOf(RecentInstallStamp("fresh/Main","fresh",now-1), RecentInstallStamp("nineteen/Main","nineteen",now-1_140_000), RecentInstallStamp("edge/Main","edge",now-1_200_000), RecentInstallStamp("future/Main","future",now+1), RecentInstallStamp("unknown/Main","unknown",0), RecentInstallStamp("oldUpdated/Main","oldUpdated",now-1_800_000))
        assertEquals(listOf("fresh", "nineteen"),eligibleRecentInstalls(stamps,now).map { it.packageName })
        assertTrue(eligibleRecentInstalls(listOf(stamps.first()),now+1_200_000).isEmpty())
    }
    @Test fun aliasesAppearOnlyOnceAndNewestAppsComeFirst() {
        val stamps = listOf(RecentInstallStamp("a/Alias2","a",100), RecentInstallStamp("b/Main","b",200), RecentInstallStamp("a/Alias1","a",100))
        assertEquals(listOf("b/Main","a/Alias1"),eligibleRecentInstalls(stamps,300).map { it.component })
    }
    @Test fun roundRobinNeverExceedsFourAndEventuallyShowsEveryApp() {
        val apps = (0..8).toList()
        val windows = apps.indices.map { recentInstallWindow(apps,it) }
        assertTrue(windows.all { it.size == 4 && it.distinct().size == 4 })
        assertEquals(apps.toSet(),windows.flatten().toSet())
        assertEquals(listOf(0,1,2,3),windows.first())
        assertEquals(listOf(4,5,6,7),windows[1])
        assertEquals(listOf(8,0,1,2),windows[2])
        assertEquals(listOf(1,2),recentInstallWindow(listOf(1,2),50))
        assertTrue(recentInstallWindow(emptyList<Int>(),0).isEmpty())
    }
}
