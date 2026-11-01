package com.example.launcher.data

import org.json.JSONArray
import org.json.JSONObject

data class HomePreferences(val gridRows: Int = 4, val dockLimit: Int = 5, val searchBar: String = "Off", val clockStyle: String = "Large",
    val iconShape: String = "Squircle", val iconSize: Int = 58, val showLabels: Boolean = true, val swipeUp: Boolean = true,
    val doubleTapLock: Boolean = false, val notificationDots: Boolean = true, val accentColor: Int? = null, val hiddenPages: Set<Int> = emptySet())

internal fun preferencesJson(p: HomePreferences) = JSONObject().put("rows", p.gridRows).put("dock", p.dockLimit).put("search", p.searchBar)
    .put("clock", p.clockStyle).put("shape", p.iconShape).put("size", p.iconSize).put("labels", p.showLabels).put("swipe", p.swipeUp)
    .put("lock", p.doubleTapLock).put("dots", p.notificationDots).put("accent", p.accentColor ?: JSONObject.NULL).put("hidden", JSONArray(p.hiddenPages.toList()))
internal fun parsePreferences(json: JSONObject?): HomePreferences {
    if (json == null) return HomePreferences()
    val hidden = json.optJSONArray("hidden") ?: JSONArray()
    return HomePreferences(json.optInt("rows", 4).coerceIn(2,4), json.optInt("dock",5).coerceIn(0,5),
        json.optString("search", "Off").takeIf { it in listOf("Off","Top","Bottom") } ?: "Off",
        json.optString("clock", "Large").takeIf { it in listOf("Large","Small","Hidden") } ?: "Large",
        json.optString("shape", "Squircle").takeIf { it in listOf("Squircle","Circle","Square") } ?: "Squircle",
        json.optInt("size",58).coerceIn(44,68), json.optBoolean("labels",true), json.optBoolean("swipe",true), json.optBoolean("lock"), json.optBoolean("dots",true),
        if (json.has("accent") && !json.isNull("accent")) json.getInt("accent") or 0xFF000000.toInt() else null,
        (0 until hidden.length()).map { hidden.optInt(it,-1) }.filter { it in setOf(0,2,3) }.toSet())
}
fun visiblePages(config: HomeCustomization) = config.pages.filter { it.id == 1 || it.id !in config.preferences.hiddenPages }
fun homeBlockedSlots(config: HomeCustomization, page: Int) = widgetSlots(config,page) + (config.preferences.gridRows * HOME_COLUMNS until HOME_CAPACITY).toSet()
fun gridFits(config: HomeCustomization, rows: Int, main: List<HomeEntry>): Boolean = rows in 2..4 &&
    (listOf(main) + config.layouts.values).flatten().all { it.slot < rows * HOME_COLUMNS } &&
    config.widgets.all { widgetCells(it.kind,it.slot).all { slot -> slot < rows * HOME_COLUMNS } }
