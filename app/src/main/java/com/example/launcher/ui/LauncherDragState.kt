package com.example.launcher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.launcher.data.InstalledApp
import com.example.launcher.data.PinnedAppEntity
import com.example.launcher.data.HomeEntry
import com.example.launcher.data.HOME_CAPACITY
import com.example.launcher.data.appEntry
import com.example.launcher.data.moveHomeEntry
import kotlin.math.abs
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri

fun homeApps(installed: List<InstalledApp>, pins: List<PinnedAppEntity>): List<InstalledApp> {
    val pinned = pins.sortedBy { it.position }.mapNotNull { pin -> installed.find { it.component.flattenToString() == pin.component } }
    val defaults = listOf("photos", "music", "maps", "mail", "calendar", "clock", "notes").mapNotNull { key -> installed.firstOrNull { it.label.contains(key, true) || it.component.packageName.contains(key, true) } }
    // Keep the existing eight default shortcuts; older explicit pins may use all sixteen slots.
    return (pinned + defaults + installed.filterNot { it.component.packageName == "com.android.settings" }).distinctBy { it.component }.take(maxOf(8, pinned.size).coerceAtMost(HOME_CAPACITY))
}

fun placeApp(order: List<InstalledApp>, app: InstalledApp, index: Int): List<InstalledApp> {
    val remaining = order.filterNot { it.component == app.component }.toMutableList()
    remaining.add(index.coerceIn(0, remaining.size), app)
    return remaining.take(HOME_CAPACITY)
}

data class DockItem(val key: String, val app: InstalledApp?, val label: String, val icon: String, val intent: Intent?)
fun dockItems(context: Context, apps: List<InstalledApp>, keys: List<String>): List<DockItem> = keys.map { key ->
    val icon = key.removePrefix("@")
    val intent = when (key) {
        "@phone" -> com.example.launcher.data.truecallerDialIntent()
        "@messages" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        "@camera" -> Intent("android.media.action.STILL_IMAGE_CAMERA")
        "@browser" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        else -> null
    }
    val resolved = if (key == "@phone") apps.firstOrNull { it.component.packageName == "com.truecaller" }
        else if (intent != null) context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let { info -> apps.find { it.component.packageName == info.packageName } }
        else apps.find { it.component.flattenToString() == key }
    // Truecaller can handle SMS too; Messages must not masquerade as Phone.
    val app = if (key == "@messages") resolved?.copy(label = "Messages", icon = com.example.launcher.data.AppIconStyle("messages", com.example.launcher.data.AppAccent.AMBER), artwork = null) else resolved
    DockItem(key, app, app?.label ?: when (key) { "@phone" -> "Truecaller"; "@messages" -> "Messages"; "@camera" -> "Camera"; "@browser" -> "Browser"; else -> "App unavailable" }, icon, intent)
}

fun placeDockApp(order: List<String>, component: String, source: Int, target: Int, capacity: Int = 5): List<String> {
    if (target !in 0..order.size || order.size > capacity) return order
    val from = source.takeIf { it in order.indices } ?: order.indexOf(component)
    if (from < 0 && order.size >= capacity) return order
    return order.toMutableList().apply {
        val item = if (from in indices) removeAt(from) else component
        add(target.coerceAtMost(size), item)
    }
}

@Stable class DrawerMotion {
    var homeSearchRect by mutableStateOf(Rect.Zero)
    var dragging by mutableStateOf(false)
    var fraction by mutableFloatStateOf(0f)
    var renderedFraction = 0f
    var height = 1f
    private var start = 0f
    private var distance = 0f
    fun begin() { if (!dragging) { start = renderedFraction; fraction = start; distance = 0f; dragging = true } }
    fun drag(delta: Float) { begin(); distance += delta; fraction = (fraction - delta / height.coerceAtLeast(1f)).coerceIn(0f, 1f) }
    fun finish(velocity: Float, threshold: Float, settle: (Boolean) -> Unit) {
        val open = if (abs(velocity) > 700f) velocity < 0 else if (abs(distance) > threshold) distance < 0 else start > 0.5f
        settle(open)
        dragging = false
    }
    fun cancel() { dragging = false }
}

data class AppHit(val app: InstalledApp, val rect: Rect, val drawer: Boolean, val dockIndex: Int = -1, val homeEntry: HomeEntry? = null)
@Stable class LauncherDragState {
    var dockCapacity = 5
    var blockedSlots: Set<Int> = emptySet()
    val widgetHits = mutableMapOf<String, Rect>()
    val hits = mutableMapOf<String, AppHit>()
    val slots = mutableMapOf<Int, Rect>()
    val dockSlots = mutableMapOf<Int, Rect>()
    var app by mutableStateOf<InstalledApp?>(null)
    var item by mutableStateOf<HomeEntry?>(null)
    var folderTarget by mutableIntStateOf(-1)
    var position by mutableStateOf(Offset.Zero)
    var preview by mutableStateOf<List<HomeEntry>>(emptyList())
    var dockPreview by mutableStateOf<List<String>>(emptyList())
    var dockOriginal: List<String> = emptyList()
    private var dockSource = -1
    var fromDockIndex = -1
    var fromDrawer = false
    var source = Offset.Zero
    var original: List<HomeEntry> = emptyList()
    var query = ""
    var hover by mutableIntStateOf(-1)
    var hoverDock by mutableIntStateOf(-1)
    var settling by mutableStateOf(false)
    var canRemoveHome by mutableStateOf(false)
    var overBin by mutableStateOf(false)
    var binRect: Rect? = null
    fun start(hit: AppHit, pointer: Offset, order: List<HomeEntry>, search: String, dock: List<DockItem>) {
        app = hit.app; item = hit.homeEntry ?: appEntry(hit.app.component.flattenToString()); folderTarget = -1; position = pointer; source = hit.rect.center; original = order; preview = order
        fromDrawer = hit.drawer; hover = -1; query = search; settling = false
        canRemoveHome = !hit.drawer
        overBin = false
        dockOriginal = dock.map { it.key }; dockPreview = dockOriginal; hoverDock = -1
        fromDockIndex = hit.dockIndex
        dockSource = hit.dockIndex
    }
    fun move(delta: Offset) {
        position += delta
        overBin = canRemoveHome && binRect?.contains(position) == true
        if (overBin) {
            hover = -1; hoverDock = -1; folderTarget = -1
            preview = original; dockPreview = dockOriginal
            return
        }
        val target = slots.entries.firstOrNull { it.key !in blockedSlots && it.value.contains(position) }?.key ?: -1
        hover = target
        hoverDock = if (item?.isFolder == true) -1 else dockSlots.entries.firstOrNull { it.value.contains(position) }?.key ?: -1
        val rect = slots[target]
        val entry = original.find { it.slot == target }
        folderTarget = if (item?.isFolder != true && entry != null && entry.id != item?.id && rect != null &&
            kotlin.math.abs(position.x - rect.center.x) < rect.width * 0.28f && kotlin.math.abs(position.y - (rect.top + rect.width * 0.4f)) < rect.width * 0.34f) target else -1
        preview = if (target >= 0 && folderTarget < 0) moveHomeEntry(original, item ?: return, target, blockedSlots) else original
        dockPreview = if (hoverDock >= 0) placeDockApp(dockOriginal, app?.component?.flattenToString() ?: return, dockSource, hoverDock, dockCapacity) else dockOriginal
    }
    fun reset() { fromDockIndex = -1; app = null; item = null; folderTarget = -1; settling = false; hover = -1; hoverDock = -1; preview = emptyList(); dockPreview = emptyList(); canRemoveHome = false; overBin = false; binRect = null }
}
