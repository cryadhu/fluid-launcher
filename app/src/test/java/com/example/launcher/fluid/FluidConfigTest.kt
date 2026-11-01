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
    @Test fun widgetOrderSpacesAndEmptyDockSurviveRestart() {
        val config=FluidConfig(widgets=listOf(FluidWidget.STORAGE,FluidWidget.WEATHER),mode="Dark",labels=false,city="Bengaluru",work=setOf("work/Main"),personal=setOf("personal/Main"),dock=emptyList())
        assertEquals(config,decodeFluid(encodeFluid(config)))
    }
    @Test fun corruptPreferencesRecoverAndUnknownWidgetsAreIgnored() {
        assertEquals(FluidConfig(),decodeFluid("broken json"))
        val decoded=decodeFluid("""{"widgets":["UNKNOWN","DATE","DATE"],"homeVersion":2,"mode":"invalid","dock":["a","a","b","c","d","e"]}""")
        assertEquals(listOf(FluidWidget.DATE),decoded.widgets)
        assertEquals("Light",decoded.mode)
        assertEquals(listOf("a","b","c","d"),decoded.dock)
    }
    @Test fun oldHomeGetsNewHeaderAndNotificationsWhileSizesPersist() {
        val migrated=decodeFluid("""{"widgets":["TASKS"],"mode":"Dark"}""")
        assertEquals(listOf(FluidWidget.CLOCK,FluidWidget.WEATHER,FluidWidget.NOTIFICATIONS,FluidWidget.TASKS),migrated.widgets)
        val edited=migrated.copy(widgets=emptyList(),sizes=mapOf(FluidWidget.CLOCK to "Large",FluidWidget.TASKS to "Compact"))
        assertEquals(edited,decodeFluid(encodeFluid(edited)))
        val bad=decodeFluid("""{"homeVersion":2,"widgets":[],"sizes":{"CLOCK":"HUGE","TASKS":"Compact"}}""")
        assertEquals(mapOf(FluidWidget.TASKS to "Compact"),bad.sizes)
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
