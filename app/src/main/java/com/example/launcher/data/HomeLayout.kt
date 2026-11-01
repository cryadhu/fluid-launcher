package com.example.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val HOME_COLUMNS = 4
const val HOME_ROWS = 4
const val HOME_CAPACITY = HOME_COLUMNS * HOME_ROWS

data class HomeEntry(val id: String, val title: String, val components: List<String>, val slot: Int = -1) {
    val isFolder get() = id.startsWith("folder:")
}

fun appEntry(component: String) = HomeEntry(component, "", listOf(component))
fun removeFolderMember(layout: List<HomeEntry>, folderId: String, component: String): List<HomeEntry> = layout.mapNotNull { entry ->
    if (entry.id != folderId) entry else entry.components.filterNot { it == component }.takeIf { it.isNotEmpty() }?.let { entry.copy(components = it) }
}

/** Migrate old packed layouts once, while preserving explicit empty cells in newer layouts. */
fun normalizeHomeLayout(entries: List<HomeEntry>): List<HomeEntry> {
    val items = entries.filter { it.components.isNotEmpty() }.distinctBy { it.id }.take(HOME_CAPACITY)
    val claimed = mutableSetOf<Int>()
    val fixed = items.map { entry -> entry.takeIf { it.slot in 0 until HOME_CAPACITY && claimed.add(it.slot) } }
    return items.mapIndexed { index, entry ->
        fixed[index] ?: entry.copy(slot = (0 until HOME_CAPACITY).first { it !in claimed }.also { claimed.add(it) })
    }.sortedBy { it.slot }
}

fun firstFreeHomeSlot(entries: List<HomeEntry>, blocked: Set<Int> = emptySet()): Int =
    (0 until HOME_CAPACITY).firstOrNull { slot -> slot !in blocked && normalizeHomeLayout(entries).none { it.slot == slot } } ?: -1

fun moveHomeEntry(order: List<HomeEntry>, item: HomeEntry, index: Int, blocked: Set<Int> = emptySet()): List<HomeEntry> {
    if (index !in 0 until HOME_CAPACITY || index in blocked) return normalizeHomeLayout(order)
    val layout = normalizeHomeLayout(order)
    val source = layout.find { it.id == item.id }
    val target = layout.find { it.slot == index && it.id != item.id }
    val displacedSlot = source?.slot ?: firstFreeHomeSlot(layout, blocked)
    if (source == null && displacedSlot < 0) return layout
    return (layout.filterNot { it.id == item.id || it.id == target?.id } +
        listOfNotNull(target?.copy(slot = displacedSlot), item.copy(slot = index))).sortedBy { it.slot }
}

fun mergeHomeEntry(order: List<HomeEntry>, item: HomeEntry, index: Int): List<HomeEntry> {
    val layout = normalizeHomeLayout(order)
    val target = layout.find { it.slot == index } ?: return layout
    if (item.isFolder || target.id == item.id) return layout
    val folder = if (target.isFolder) target.copy(components = (target.components + item.components).distinct())
        else HomeEntry("folder:${UUID.randomUUID()}", "Folder", (target.components + item.components).distinct(), target.slot)
    return layout.filterNot { it.id == item.id }.map { if (it.id == target.id) folder else it }
}

fun encodeHomeLayout(entries: List<HomeEntry>): String = JSONArray(normalizeHomeLayout(entries).map {
    JSONObject().put("id", it.id).put("title", it.title).put("apps", JSONArray(it.components)).put("slot", it.slot)
}).toString()

fun decodeHomeLayout(raw: String): List<HomeEntry> {
    val json = JSONArray(raw)
    return normalizeHomeLayout((0 until json.length()).map { index ->
        val item = json.getJSONObject(index)
        val apps = item.getJSONArray("apps")
        HomeEntry(item.getString("id"), item.optString("title", "Folder"), (0 until apps.length()).map { apps.getString(it) }.distinct(), item.optInt("slot", index))
    })
}
