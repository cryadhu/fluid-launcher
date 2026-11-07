package com.example.launcher.fluid

import org.junit.Assert.*
import org.junit.Test

class FluidConfigTest {
    @Test fun footballWidgetsAndSelectedClubPersistAcrossPages() {
        val widgets=listOf(FluidWidget.PREMIER_LEAGUE,FluidWidget.NEXT_MATCHES)
        val config=FluidConfig(widgets=widgets,spaceWidgets=widgets,planWidgets=widgets,footballClub="382",pageWidgets=mapOf("Football" to widgets))
        assertEquals(config,decodeFluid(encodeFluid(config)))
        assertEquals("359",decodeFluid("{}").footballClub)
        assertEquals("359",decodeFluid("""{"footballClub":"../secret"}""").footballClub)
        widgets.forEach { assertEquals("4×3",widgetGrid(it)) }
    }
    @Test fun customWallpapersAndAutomaticThemePersistSafely() {
        val custom="Custom:12345678-abcd-1234-abcd-123456789abc.jpg"
        val config=FluidConfig(homeWallpaper=custom,lockWallpaper="Lavender",mode="Auto")
        assertEquals(config,decodeFluid(encodeFluid(config)))
        assertEquals("Auto",FluidConfig().mode)
        assertEquals("Auto",decodeFluid("""{"mode":"Light"}""").mode)
        assertFalse(validWallpaper("Custom:../../secret.jpg"))
        assertEquals("Dawn",decodeFluid("""{"homeWallpaper":"Custom:../../secret.jpg"}""").homeWallpaper)
    }
    @Test fun dawnAndFourDockAppsAreDefaultsAndExplicitSelectionsPersist() {
        assertEquals(listOf("Pastel Glass","Default","Dawn","Lavender"),wallpaperStyles)
        assertEquals("Dawn",FluidConfig().homeWallpaper)
        assertEquals(4,FluidConfig().dockCount)
        val migrated=decodeFluid("""{"homeWallpaper":"Original","dock":["@auto"],"dockCount":3}""")
        assertEquals("Default",migrated.homeWallpaper)
        assertEquals(4,migrated.dockCount)
        val chosen=FluidConfig(homeWallpaper="Default",dockCount=3)
        assertEquals(chosen,decodeFluid(encodeFluid(chosen)))
    }
    @Test fun controlWidgetSelectionsPersistAndControlsPageIsGone() {
        val controls=listOf(FluidWidget.BRIGHTNESS,FluidWidget.VOLUME)+tinyControls
        val config=FluidConfig(widgets=controls,spaceWidgets=controls.reversed())
        assertEquals(config,decodeFluid(encodeFluid(config)))
        assertEquals(listOf("Markets","Home","Space","Plan"),pageNames)
    }
    @Test fun homeAndLockWallpapersPersistIndependently() {
        val config=FluidConfig(homeWallpaper="Lavender",lockWallpaper="Default",homeWallpaperBlur=true,lockWallpaperBlur=true)
        assertEquals(config,decodeFluid(encodeFluid(config)))
        assertEquals("Dawn",decodeFluid("{}").homeWallpaper)
        assertEquals("Dawn",decodeFluid("""{"homeWallpaper":"unknown","lockWallpaper":"bad"}""").lockWallpaper)
    }
    @Test fun newDesignDefaultsAreIndependentOfLegacyPages() {
        val config = FluidConfig()
        assertEquals(listOf(FluidWidget.CLOCK,FluidWidget.WEATHER,FluidWidget.NOTIFICATIONS,FluidWidget.AGENDA),config.widgets)
        assertEquals("Auto",config.mode)
        assertEquals(listOf("@auto"),config.dock)
        assertEquals(listOf("Work","Personal","Tools"),config.spaces.map{it.name})
        assertTrue(config.spaces.all{it.apps.isEmpty()})
    }
    @Test fun spaceTabsPersistAndOldWorkPersonalToolsAssignmentsMigrate() {
        val config=FluidConfig(spaces=listOf(SpaceTab(4,"Games",setOf("a/Main","b/Main")),SpaceTab(9,"Read")))
        assertEquals(config,decodeFluid(encodeFluid(config)))
        val old=decodeFluid("""{"work":["w/Main"],"personal":["p/Main"],"tools":["t/Main","w/Main"]}""")
        assertEquals(listOf(SpaceTab(1,"Work",setOf("w/Main")),SpaceTab(2,"Personal",setOf("p/Main")),SpaceTab(3,"Tools",setOf("t/Main","w/Main"))),old.spaces)
        assertTrue(decodeFluid(encodeFluid(FluidConfig(spaces=emptyList()))).spaces.isEmpty())
        val duplicated=decodeFluid("""{"spaces":[{"id":1,"name":"Work"},{"id":1,"name":"work"},{"id":1,"name":"Fun"},{"id":2,"name":" "}]}""")
        assertEquals(listOf(SpaceTab(1,"Work"),SpaceTab(2,"Fun")),duplicated.spaces)
    }
    @Test fun spaceNamesMustBeUniqueAndNotAll() {
        val spaces=listOf(SpaceTab(1,"Work"),SpaceTab(2,"Play"))
        assertNull(validSpaceName("  work ",spaces))
        assertNull(validSpaceName("all",spaces))
        assertNull(validSpaceName("   ",spaces))
        assertEquals("Work",validSpaceName("Work",spaces,except=1))
        assertEquals("Reading",validSpaceName(" Reading ",spaces))
        assertEquals(SPACE_NAME_LIMIT,validSpaceName("x".repeat(40),spaces)!!.length)
    }
    @Test fun spaceWidgetsAndAllAppSectionsPersistIndependentlyOfHome() {
        val defaults=FluidConfig()
        assertFalse(FluidWidget.AGENDA in defaults.spaceWidgets)
        val config=defaults.copy(spaces=listOf(SpaceTab(1,"Work",setOf("work/Main")),SpaceTab(2,"Tools",setOf("tools/Main"))),spaceWidgets=emptyList())
        val restored=decodeFluid(encodeFluid(config))
        assertEquals(config,restored)
        assertTrue(restored.spaceWidgets.isEmpty())
        assertTrue(FluidWidget.AGENDA in restored.widgets)
        assertEquals(listOf("Markets","Home","Space","Plan"),pageNames)
        assertEquals(1,HOME_PAGE)
    }
    @Test fun widgetOrderSpacesAndEmptyDockSurviveRestart() {
        val config=FluidConfig(widgets=listOf(FluidWidget.STORAGE,FluidWidget.WEATHER),mode="Dark",labels=false,city="Bengaluru",spaces=listOf(SpaceTab(1,"Work",setOf("work/Main"))),dock=emptyList())
        assertEquals(config,decodeFluid(encodeFluid(config)))
    }
    @Test fun corruptPreferencesRecoverAndUnknownWidgetsAreIgnored() {
        assertEquals(FluidConfig(),decodeFluid("broken json"))
        val decoded=decodeFluid("""{"widgets":["UNKNOWN","DATE","DATE"],"homeVersion":2,"mode":"invalid","dock":["a","a","b","c","d","e"]}""")
        assertEquals(listOf(FluidWidget.DATE),decoded.widgets)
        assertEquals("Auto",decoded.mode)
        assertEquals(listOf("a","b","c","d","e"),decoded.dock)
    }
    @Test fun oldHomeGetsNewHeaderAndNotificationsWhileSizesPersist() {
        val migrated=decodeFluid("""{"widgets":["TASKS"],"mode":"Dark"}""")
        assertEquals(listOf(FluidWidget.CLOCK,FluidWidget.WEATHER,FluidWidget.NOTIFICATIONS,FluidWidget.TASKS),migrated.widgets)
        val edited=migrated.copy(widgets=emptyList(),sizes=mapOf(FluidWidget.CLOCK to "Large",FluidWidget.TASKS to "Compact"))
        assertEquals(edited,decodeFluid(encodeFluid(edited)))
        val bad=decodeFluid("""{"homeVersion":2,"widgets":[],"sizes":{"CLOCK":"HUGE","TASKS":"Compact"}}""")
        assertEquals(mapOf(FluidWidget.TASKS to "Compact"),bad.sizes)
    }
    @Test fun marketAndNewsPreferencesSurviveRestart() {
        assertEquals("India", FluidConfig().market)
        assertTrue(FluidConfig().stockSymbols.contains(".NS"))
        val config = FluidConfig(market="Custom",stockSymbols="SAP.DE,0700.HK",newsTopics=setOf(com.example.launcher.data.NewsCategory.MUSIC,com.example.launcher.data.NewsCategory.MOVIES))
        assertEquals(config,decodeFluid(encodeFluid(config)))
        assertEquals(emptySet<com.example.launcher.data.NewsCategory>(),decodeFluid(encodeFluid(config.copy(newsTopics=emptySet()))).newsTopics)
    }
    @Test fun stockParserSkipsMissingChartPrices() {
        val quote = parseStock("""{"chart":{"result":[{"meta":{"regularMarketPrice":110,"chartPreviousClose":100,"currency":"INR","regularMarketTime":1000},"indicators":{"quote":[{"close":[100,null,110]}]}}]}}""", "TCS.NS")
        assertEquals(10.0,quote.change,0.001)
        assertEquals(listOf(100f,110f),quote.points)
        assertEquals(1000000L,quote.time)
    }
    @Test fun dockSettingsPersistAndClampCorruptValues() {
        val custom=FluidConfig(dockCount=6,dockIconSize=40,dockLabels=true,dock=listOf("a","b","c","d","e","f"))
        assertEquals(custom,decodeFluid(encodeFluid(custom)))
        val bad=decodeFluid("""{"dockCount":100,"dockIconSize":-10}""")
        assertEquals(6,bad.dockCount)
        assertEquals(36,bad.dockIconSize)
        assertEquals(4,decodeFluid("{}").dockCount)
        assertEquals(emptyList<String>(),decodeFluid(encodeFluid(custom.copy(dock=emptyList()))).dock)
    }
    @Test fun notificationTimestampUsesRequestedDateAndTwelveHourTime() {
        val time=java.time.Instant.parse("2022-01-03T10:00:00Z").toEpochMilli()
        assertEquals("Jan, 03 2022 10:00AM",notificationTimestamp(time,java.time.ZoneId.of("UTC")))
        assertEquals("Jan, 03 2022 03:30PM",notificationTimestamp(time,java.time.ZoneId.of("Asia/Kolkata")))
        assertEquals("Jan, 03 2022 12:00AM",notificationTimestamp(time-10*60*60*1000,java.time.ZoneId.of("UTC")))
    }
    @Test fun frostPreservesSolidColorsAndSoftensEdges() {
        val solid=IntArray(16){0xFF8866CC.toInt()}
        blurPass(solid,4,4,2)
        assertTrue(solid.all{it==0xFF8866CC.toInt()})
        val edge=intArrayOf(0xFF000000.toInt(),0xFF000000.toInt(),0xFFFFFFFF.toInt(),0xFFFFFFFF.toInt())
        blurPass(edge,4,1,1)
        assertEquals(0,edge[0] and 255)
        assertTrue((edge[1] and 255) in 1..254)
        assertTrue((edge[2] and 255)>(edge[1] and 255))
        assertEquals(255,edge[3] and 255)
    }
}
