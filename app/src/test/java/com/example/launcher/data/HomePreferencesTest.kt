package com.example.launcher.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomePreferencesTest {
    @Test fun allHomeSettingsPersistAndLegacySettingsKeepExistingBehavior() {
        val prefs = HomePreferences(2,3,"Bottom","Small","Circle",64,false,false,false,0xFF7FD6B8.toInt(),setOf(0,2))
        val config = HomeCustomization(preferences = prefs)
        assertEquals(config, decodeCustomization(encodeCustomization(config)))
        assertEquals(HomePreferences(), decodeCustomization("{}").preferences)
    }
    @Test fun removedLockSettingIsIgnoredWithoutChangingNotificationDots() {
        val prefs = parsePreferences(JSONObject("""{"lock":true,"dots":false}"""))
        assertFalse(prefs.notificationDots)
        assertFalse(preferencesJson(prefs).has("lock"))
        assertEquals(prefs, parsePreferences(preferencesJson(prefs)))
    }
    @Test fun malformedPreferencesAreBoundedAndCannotHideHome() {
        val p = parsePreferences(JSONObject("""{"rows":20,"dock":9,"size":-5,"search":"other","clock":"other","shape":"other","hidden":[0,1,2,3,99]}"""))
        assertEquals(4,p.gridRows); assertEquals(5,p.dockLimit); assertEquals(44,p.iconSize)
        assertEquals("Off",p.searchBar); assertEquals("Large",p.clockStyle); assertEquals("Squircle",p.iconShape)
        assertEquals(setOf(0,2,3),p.hiddenPages)
        assertEquals(listOf(1),visiblePages(HomeCustomization(preferences = p)).map { it.id })
    }
    @Test fun shrinkingGridRejectsOccupiedLowerRowsIncludingWidgets() {
        val config = HomeCustomization()
        assertTrue(gridFits(config,2,listOf(appEntry("a").copy(slot=7))))
        assertFalse(gridFits(config,2,listOf(appEntry("a").copy(slot=8))))
        val withWidget = config.copy(widgets = listOf(HomeWidget(kind=HomeWidgetKind.WEATHER,pageId=1,slot=8)))
        assertFalse(gridFits(withWidget,2,emptyList()))
        assertTrue(gridFits(withWidget,4,emptyList()))
        assertEquals((8..15).toSet(),homeBlockedSlots(config.copy(preferences=HomePreferences(gridRows=2)),1))
    }
    @Test fun hiddenPagesPreserveSavedPageContentAndOrdering() {
        val config = HomeCustomization(pages=initialPages + LauncherPage(4,"Extra"),layouts=mapOf(4 to listOf(appEntry("a").copy(slot=3))),preferences=HomePreferences(hiddenPages=setOf(0,3)))
        assertEquals(listOf(1,2,4),visiblePages(config).map { it.id })
        assertEquals(config,decodeCustomization(encodeCustomization(config)))
    }
    @Test fun accentOverridesPresetAndResolvesReadableOnAccentColor() {
        val config = HomeCustomization(theme="Ember",preferences=HomePreferences(accentColor=0xFF7FD6B8.toInt()))
        val palette = com.example.launcher.ui.theme.resolvedTheme(config,true)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF7FD6B8),palette.amber)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF161616),palette.onAccent)
    }
}
