package com.example.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LauncherPage(val id: Int, val title: String) { val protected get() = id == 0 || id == 1; val isHome get() = id == 1 || id >= 4 }
val initialPages = listOf(LauncherPage(0, "For you"), LauncherPage(1, "Home"), LauncherPage(2, "Today"), LauncherPage(3, "Tools"))
fun reorderHomePages(pages: List<LauncherPage>, sourceId: Int, targetId: Int): List<LauncherPage> {
    val source = pages.indexOfFirst { it.id == sourceId }
    val target = pages.indexOfFirst { it.id == targetId }
    if (source < 2 || target < 2 || source == target || pages[source].protected || pages[target].protected) return pages
    return pages.toMutableList().apply { add(target, removeAt(source)) }
}
enum class HomePanel { CUSTOMIZE, WIDGETS, PAGES, WALLPAPER, THEMES, SETTINGS }
enum class HomeWidgetKind(val title: String, val group: String, val columns: Int, val rows: Int, val glyph: String) {
    CLOCK("Clock", "Time and weather", 4, 2, "clock"), AGENDA("Agenda", "Time and weather", 4, 2, "calendar"), WEATHER("Weather", "Time and weather", 4, 2, "weather"),
    MARKETS("Markets", "Money and media", 4, 2, "wallet"), MUSIC("Music player", "Money and media", 4, 1, "music"), QUICK_SETTINGS("Quick settings", "Money and media", 4, 1, "settings"),
    TASKS("Tasks", "Tools and focus", 2, 2, "tasks"), FOCUS("Focus timer", "Tools and focus", 2, 2, "clock"), NOTE("Quick note", "Tools and focus", 4, 1, "notes"), FAVOURITES("Favourites", "Tools and focus", 4, 1, "contacts")
}
data class HomeWidget(val id: String = UUID.randomUUID().toString(), val kind: HomeWidgetKind, val pageId: Int, val slot: Int)
data class HomeCustomization(val pages: List<LauncherPage> = initialPages, val layouts: Map<Int, List<HomeEntry>> = emptyMap(),
    val widgets: List<HomeWidget> = emptyList(), val locked: Boolean = false, val wallpaper: String = "Forest", val city: String = "", val theme: String = "Tonal", val followSystemTheme: Boolean = false,
    val customBackground: Int = 0xFF0E1A19.toInt(), val customAccent: Int = 0xFFF2B84B.toInt(), val preferences: HomePreferences = HomePreferences())

fun withoutHomeSettingsShortcut(entries: List<HomeEntry>) = entries.filterNot { !it.isFolder && it.id.substringBefore('/') == "com.android.settings" }

fun widgetCells(kind: HomeWidgetKind, slot: Int): Set<Int> {
    if (slot < 0 || slot % HOME_COLUMNS + kind.columns > HOME_COLUMNS || slot / HOME_COLUMNS + kind.rows > HOME_ROWS) return emptySet()
    return (0 until kind.rows).flatMap { row -> (0 until kind.columns).map { column -> slot + row * HOME_COLUMNS + column } }.toSet()
}
fun widgetSlots(config: HomeCustomization, pageId: Int) = config.widgets.filter { it.pageId == pageId }.flatMap { widgetCells(it.kind, it.slot) }.toSet()
fun findWidgetSlot(kind: HomeWidgetKind, occupied: Set<Int>): Int = (0 until HOME_CAPACITY).firstOrNull {
    val cells = widgetCells(kind, it); cells.isNotEmpty() && cells.none(occupied::contains)
} ?: -1
fun encodeCustomization(config: HomeCustomization): String = JSONObject().apply {
    put("preferences", preferencesJson(config.preferences))
    put("pages", JSONArray(config.pages.map { JSONObject().put("id", it.id).put("title", it.title) }))
    put("layouts", JSONObject().apply { config.layouts.forEach { (id, entries) -> put(id.toString(), JSONArray(encodeHomeLayout(entries))) } })
    put("widgets", JSONArray(config.widgets.map { JSONObject().put("id", it.id).put("kind", it.kind.name).put("page", it.pageId).put("slot", it.slot) }))
    put("locked", config.locked); put("wallpaper", config.wallpaper); put("city", config.city); put("theme", config.theme); put("followSystemTheme", config.followSystemTheme); put("customBackground", config.customBackground); put("customAccent", config.customAccent)
}.toString()
fun decodeCustomization(raw: String): HomeCustomization {
    val json = JSONObject(raw)
    val list = json.optJSONArray("pages") ?: JSONArray()
    val parsed = (0 until list.length()).map { list.getJSONObject(it).let { item -> LauncherPage(item.getInt("id"), item.getString("title")) } }.distinctBy { it.id }
    // The required pages keep their original positions, even if a saved configuration is damaged.
    val pages = initialPages.take(2) + parsed.filter { it.id >= 2 }
    val layouts = json.optJSONObject("layouts") ?: JSONObject()
    val decodedLayouts = layouts.keys().asSequence().mapNotNull { key -> key.toIntOrNull()?.takeIf { id -> pages.any { it.id == id && it.isHome } }?.let { it to decodeHomeLayout(layouts.getJSONArray(key).toString()) } }.toMap()
    val widgets = json.optJSONArray("widgets") ?: JSONArray()
    val occupied = mutableMapOf<Int, MutableSet<Int>>()
    val decodedWidgets = (0 until widgets.length()).mapNotNull { index -> runCatching {
        val item = widgets.getJSONObject(index)
        HomeWidget(item.getString("id"), HomeWidgetKind.valueOf(item.getString("kind")), item.getInt("page"), item.getInt("slot"))
    }.getOrNull() }.filter { widget ->
        val cells = widgetCells(widget.kind, widget.slot)
        val used = occupied.getOrPut(widget.pageId) { mutableSetOf() }
        val valid = pages.any { it.id == widget.pageId && it.isHome } && cells.isNotEmpty() && cells.none(used::contains)
        if (valid) used.addAll(cells)
        valid
    }.distinctBy { it.id }
    return HomeCustomization(pages, decodedLayouts, decodedWidgets, json.optBoolean("locked"), json.optString("wallpaper", "Forest"), json.optString("city"),
        json.optString("theme", "Tonal").takeIf { it in listOf("Tonal", "Solar", "Ember", "Sand", "Custom") } ?: "Tonal", json.optBoolean("followSystemTheme"),
        json.optInt("customBackground", 0xFF0E1A19.toInt()) or 0xFF000000.toInt(), json.optInt("customAccent", 0xFFF2B84B.toInt()) or 0xFF000000.toInt(), parsePreferences(json.optJSONObject("preferences")))
}
