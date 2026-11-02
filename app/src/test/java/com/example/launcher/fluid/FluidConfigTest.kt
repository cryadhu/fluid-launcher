package com.example.launcher.fluid

import org.junit.Assert.*
import org.junit.Test

class FluidConfigTest {
    @Test fun newDesignDefaultsAreIndependentOfLegacyPages() {
        val config = FluidConfig()
        assertEquals(listOf(FluidWidget.CLOCK,FluidWidget.WEATHER,FluidWidget.NOTIFICATIONS,FluidWidget.AGENDA),config.widgets)
        assertEquals("Light",config.mode)
        assertEquals(listOf("@auto"),config.dock)
        assertTrue(config.work.isEmpty())
    }
    @Test fun spaceWidgetsAndAllAppSectionsPersistIndependentlyOfHome() {
        val defaults=FluidConfig()
        assertFalse(FluidWidget.AGENDA in defaults.spaceWidgets)
        val config=defaults.copy(tools=setOf("tools/Main"),work=setOf("work/Main"),personal=setOf("personal/Main"),spaceWidgets=emptyList())
        val restored=decodeFluid(encodeFluid(config))
        assertEquals(config,restored)
        assertTrue(restored.spaceWidgets.isEmpty())
        assertTrue(FluidWidget.AGENDA in restored.widgets)
        assertEquals(listOf("Markets","Home","Space","Controls"),pageNames)
        assertEquals(1,HOME_PAGE)
    }
    @Test fun widgetOrderSpacesAndEmptyDockSurviveRestart() {
        val config=FluidConfig(widgets=listOf(FluidWidget.STORAGE,FluidWidget.WEATHER),mode="Dark",labels=false,city="Bengaluru",work=setOf("work/Main"),personal=setOf("personal/Main"),dock=emptyList())
        assertEquals(config,decodeFluid(encodeFluid(config)))
    }
    @Test fun corruptPreferencesRecoverAndUnknownWidgetsAreIgnored() {
        assertEquals(FluidConfig(),decodeFluid("broken json"))
        val decoded=decodeFluid("""{"widgets":["UNKNOWN","DATE","DATE"],"homeVersion":2,"mode":"invalid","dock":["a","a","b","c","d","e"]}""")
        assertEquals(listOf(FluidWidget.DATE),decoded.widgets)
        assertEquals("Light",decoded.mode)
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
        assertEquals(3,decodeFluid("{}").dockCount)
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
