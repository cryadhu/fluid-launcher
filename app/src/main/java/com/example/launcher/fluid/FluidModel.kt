package com.example.launcher.fluid

import android.app.Application
import android.content.Intent
import android.os.StatFs
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.launcher.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class FluidWidget(val title: String, val subtitle: String, val glyph: String) {
    BRIGHTNESS("Brightness", "Display brightness", "weather"), VOLUME("Volume", "Media volume", "music"),
    WIFI("Wi-Fi", "Internet controls", "wifi"), BLUETOOTH("Bluetooth", "Bluetooth controls", "bluetooth"), MOBILE_DATA("Mobile data", "Mobile connection", "mobile"), SOUND("Sound", "Normal, silent and vibrate", "silent"), FLASHLIGHT("Flashlight", "Quick light", "flashlight"), AIRPLANE("Airplane mode", "Flight mode controls", "airplane"),
    CLOCK("Clock", "Time in motion", "clock"), NOTIFICATIONS("Notifications", "Your day, stacked in glass", "silent"),
    AGENDA("Agenda", "Your day, at a glance", "calendar"), QUICK_ACTIONS("Quick actions", "Useful tools at a touch", "app"), FAVORITES("Favorites", "Your starred contacts", "contacts"), MUSIC("Music", "Orb with glass background", "music"), MUSIC_OPEN("Music (open)", "Orb without background", "music"),
    WEATHER("Weather", "A window outside", "weather"), TASKS("Tasks", "Make room for focus", "tasks"),
    ACTIVITY("Activity", "Steps while the launcher is active", "health"), STORAGE("Storage", "Space for what matters", "files"), DATE("Date", "A moment in time", "calendar"),
    SPACE_APPS("Space apps", "Your apps, grouped in tabs", "app"),
    SUN_PATH("Sun path", "Sunrise to sunset", "weather"), WIND("Wind", "Speed and direction", "weather"),
    FOCUS("Focus", "A 25 minute focus ring", "clock"), BREATHE("Breathe", "A calm breathing guide", "health"), WORLD_CLOCKS("World clocks", "Time around the world", "clock"),
    NOW_PLAYING("Now playing", "Your music, spinning", "music"), BATTERY("Battery", "Charge at a glance", "health"), HYDRATION("Water", "Glasses through the day", "health"), ACTIVITY_RINGS("Activity rings", "Move, exercise and stand", "health")
}
internal val halfWidgets=setOf(FluidWidget.CLOCK,FluidWidget.WEATHER,FluidWidget.FOCUS,FluidWidget.BATTERY,FluidWidget.HYDRATION)
private val fixedWidgets=mapOf(FluidWidget.SUN_PATH to "4×2",FluidWidget.WIND to "4×1",FluidWidget.BREATHE to "4×2",FluidWidget.WORLD_CLOCKS to "4×1",FluidWidget.NOW_PLAYING to "4×2",FluidWidget.ACTIVITY_RINGS to "4×2")
private val controlKinds=setOf(FluidWidget.WIFI,FluidWidget.BLUETOOTH,FluidWidget.MOBILE_DATA,FluidWidget.SOUND,FluidWidget.FLASHLIGHT,FluidWidget.AIRPLANE)
/** Size names a widget can take; an empty list means the widget has one fixed size. */
internal fun sizeOptions(kind:FluidWidget):List<String> = when(kind) {
    in controlKinds, in fixedWidgets -> emptyList()
    in halfWidgets -> listOf("Standard","Large")
    else -> listOf("Compact","Standard","Large")
}
internal fun widgetSize(kind:FluidWidget,config:FluidConfig):String = config.sizes[kind]?.takeIf{it in sizeOptions(kind)} ?: "Standard"
/** Half widgets share a row with the next half widget, like a 2×2 tile on a 4-column grid. */
internal fun isHalf(kind:FluidWidget,config:FluidConfig) = kind in halfWidgets && widgetSize(kind,config)=="Standard"
internal fun widgetGrid(kind:FluidWidget,size:String="Standard"):String = fixedWidgets[kind] ?: when {
    kind in controlKinds -> "1×1"
    kind in halfWidgets -> if(size=="Large") "4×2" else "2×2"
    kind==FluidWidget.BRIGHTNESS || kind==FluidWidget.VOLUME -> if(size=="Large") "4×2" else "4×1"
    else -> when(size) { "Compact"->"4×1"; "Large"->if(kind==FluidWidget.NOTIFICATIONS) "4×4" else "4×3"; else->"4×2" }
}
data class FluidTask(val id: Long, val text: String, val done: Boolean = false)
/** A named tab in the Space apps widget; an app can belong to any number of tabs. */
data class SpaceTab(val id: Long, val name: String, val apps: Set<String> = emptySet())
internal val defaultSpaces = listOf(SpaceTab(1, "Work"), SpaceTab(2, "Personal"), SpaceTab(3, "Tools"))
internal const val SPACE_NAME_LIMIT = 16
/** Returns the trimmed name when it can be used for a new or renamed tab. */
internal fun validSpaceName(name: String, spaces: List<SpaceTab>, except: Long? = null): String? = name.trim().take(SPACE_NAME_LIMIT).takeIf { trimmed ->
    trimmed.isNotEmpty() && !trimmed.equals("All", true) && spaces.none { it.id != except && it.name.equals(trimmed, true) }
}
data class FluidConfig(val widgets: List<FluidWidget> = listOf(FluidWidget.CLOCK, FluidWidget.WEATHER, FluidWidget.NOTIFICATIONS, FluidWidget.AGENDA), val mode: String = "Auto", val labels: Boolean = true,
    val city: String = "", val dock: List<String> = listOf("@auto"), val sizes: Map<FluidWidget,String> = emptyMap(), val stockSymbols: String = "RELIANCE.NS,TCS.NS,HDFCBANK.NS", val market: String = "India", val newsTopics: Set<NewsCategory> = setOf(NewsCategory.WORLD, NewsCategory.BUSINESS), val dockCount: Int = 4, val dockLabels: Boolean = false, val dockIconSize: Int = 48, val autoWeather: Boolean = true, val spaceWidgets: List<FluidWidget> = listOf(FluidWidget.SPACE_APPS, FluidWidget.TASKS), val homeWallpaper:String="Dawn", val lockWallpaper:String="Dawn", val homeWallpaperBlur:Boolean=false, val lockWallpaperBlur:Boolean=false, val favoriteApps:Set<String> = emptySet(), val pages:List<String> = listOf("Markets","Home","Space","Plan"), val accent:String="#8051FF", val planWidgets:List<FluidWidget> = listOf(FluidWidget.AGENDA,FluidWidget.TASKS,FluidWidget.QUICK_ACTIONS,FluidWidget.FAVORITES), val pageWidgets:Map<String,List<FluidWidget>> = emptyMap(), val spaces:List<SpaceTab> = defaultSpaces)
fun encodeFluid(config: FluidConfig): String = JSONObject().apply {
    put("spaces",JSONArray(config.spaces.map{ JSONObject().put("id",it.id).put("name",it.name).put("apps",JSONArray(it.apps.toList())) }));put("spaceWidgets",JSONArray(config.spaceWidgets.map{it.name}));put("spaceAppsVersion",1)
    put("themeVersion",1);put("wallpaperVersion",3);put("dockDefaultsVersion",2)
    put("homeWallpaper",config.homeWallpaper);put("lockWallpaper",config.lockWallpaper);put("homeWallpaperBlur",config.homeWallpaperBlur);put("lockWallpaperBlur",config.lockWallpaperBlur)
    put("autoWeather",config.autoWeather)
    put("dockCount", config.dockCount); put("dockLabels", config.dockLabels); put("dockIconSize", config.dockIconSize); put("market", config.market); put("stockSymbols", config.stockSymbols); put("newsTopics", JSONArray(config.newsTopics.map { it.name })); put("homeVersion", 2); put("sizes", JSONObject(config.sizes.mapKeys { it.key.name }))
    put("widgets", JSONArray(config.widgets.map { it.name })); put("mode", config.mode); put("labels", config.labels); put("city", config.city)
    put("dock", JSONArray(config.dock));put("favoriteApps",JSONArray(config.favoriteApps.toList()));put("pages",JSONArray(config.pages));put("accent",config.accent);put("planWidgets",JSONArray(config.planWidgets.map{it.name}));put("pageWidgets",JSONObject(config.pageWidgets.mapValues{JSONArray(it.value.map{widget->widget.name})}))
}.toString()
fun decodeFluid(raw: String): FluidConfig = runCatching {
    val j = JSONObject(raw)
    fun list(key: String) = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val stored = list("widgets").mapNotNull { name -> FluidWidget.entries.find { it.name == name } }.distinct()
    val widgets = if(j.optInt("homeVersion",1)<2) (listOf(FluidWidget.CLOCK,FluidWidget.WEATHER,FluidWidget.NOTIFICATIONS)+stored).distinct() else stored
    val sizes = FluidWidget.entries.mapNotNull { kind -> j.optJSONObject("sizes")?.optString(kind.name)?.takeIf { it in listOf("Compact","Standard","Large") }?.let { kind to it } }.toMap()
    val pageWidgets=j.optJSONObject("pageWidgets")?.let { objectValue -> objectValue.keys().asSequence().associateWith { page -> (objectValue.optJSONArray(page)?.let { array -> (0 until array.length()).mapNotNull { index -> FluidWidget.entries.find { it.name==array.optString(index) } } } ?: emptyList()).distinct() } } ?: emptyMap()
    FluidConfig(widgets,
        (if(j.has("themeVersion"))j.optString("mode", "Auto")else "Auto").takeIf { it in listOf("Light", "Dark", "Auto") } ?: "Auto", j.optBoolean("labels", true),
        j.optString("city"), if(j.has("dock")) list("dock").distinct().take(6) else listOf("@auto"), sizes, j.optString("stockSymbols", "RELIANCE.NS,TCS.NS,HDFCBANK.NS"), j.optString("market", "India"), if(j.has("newsTopics")) list("newsTopics").mapNotNull(NewsCategory::fromKey).toSet() else setOf(NewsCategory.WORLD, NewsCategory.BUSINESS), j.optInt("dockCount",4).let { if(!j.has("dockDefaultsVersion") && it==3 && list("dock")==listOf("@auto"))4 else it }.coerceIn(1,6), j.optBoolean("dockLabels",false), j.optInt("dockIconSize",48).coerceIn(36,56),j.optBoolean("autoWeather",true),(if(j.has("spaceWidgets"))list("spaceWidgets").mapNotNull{name->FluidWidget.entries.find{it.name==name}}.distinct()else listOf(FluidWidget.TASKS)).let{ if(j.has("spaceAppsVersion") || FluidWidget.SPACE_APPS in it) it else listOf(FluidWidget.SPACE_APPS)+it }).copy(homeWallpaper=j.optString("homeWallpaper","Dawn").let{if(it=="Original")"Default"else it}.takeIf{validWallpaper(it)} ?: "Dawn",lockWallpaper=j.optString("lockWallpaper","Dawn").let{if(it=="Original")"Default"else it}.takeIf{validWallpaper(it)} ?: "Dawn",homeWallpaperBlur=j.optBoolean("homeWallpaperBlur",false),lockWallpaperBlur=j.optBoolean("lockWallpaperBlur",false),favoriteApps=list("favoriteApps").toSet(),pages=(if(j.has("pages"))list("pages")else listOf("Markets","Home","Space","Plan")).let{listOf("Markets","Home")+it.filter{page->page !in listOf("Markets","Home")}.distinct()},accent=j.optString("accent","#8051FF").takeIf{runCatching{android.graphics.Color.parseColor(it)}.isSuccess} ?: "#8051FF",planWidgets=(if(j.has("planWidgets"))list("planWidgets").mapNotNull{name->FluidWidget.entries.find{it.name==name}}else listOf(FluidWidget.AGENDA,FluidWidget.TASKS,FluidWidget.QUICK_ACTIONS,FluidWidget.FAVORITES)).distinct(),pageWidgets=pageWidgets,spaces=decodeSpaces(j))
}.getOrDefault(FluidConfig())

private fun decodeSpaces(j: JSONObject): List<SpaceTab> {
    fun strings(array: JSONArray?) = array?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toSet()
    val stored = j.optJSONArray("spaces") ?: return listOf("work", "personal", "tools").zip(defaultSpaces) { key, tab -> tab.copy(apps = strings(j.optJSONArray(key))) }
    val tabs = mutableListOf<SpaceTab>()
    for (index in 0 until stored.length()) {
        val item = stored.optJSONObject(index) ?: continue
        val name = validSpaceName(item.optString("name"), tabs) ?: continue
        val id = item.optLong("id", index + 1L).takeIf { id -> tabs.none { it.id == id } } ?: ((tabs.maxOfOrNull { it.id } ?: 0) + 1)
        tabs += SpaceTab(id, name, strings(item.optJSONArray("apps")))
    }
    return tabs
}

data class FluidState(val config: FluidConfig = FluidConfig(), val apps: List<InstalledApp> = emptyList(), val loading: Boolean = true,
    val device: DeviceState = DeviceState(), val now: Long = System.currentTimeMillis(), val homeEpoch: Int = 0, val wallpaperApplying:Boolean=false,
    val tasks: List<FluidTask> = emptyList(), val weather: HomeWeather? = null, val weatherLoading: Boolean = false,
    val weatherError: String? = null, val storageUsed: Long = 0, val storageTotal: Long = 1, val message: String? = null,
    val steps: Int? = null, val standHours: Int = 0, val water: Int = 0, val focusEnd: Long? = null, val focusLeft: Long = FOCUS_LENGTH)
internal const val FOCUS_LENGTH = 25 * 60_000L
internal const val WATER_GOAL = 8

class FluidModel(app: Application) : AndroidViewModel(app), android.hardware.SensorEventListener {
    private val prefs = app.getSharedPreferences("fluid_launcher_v1", 0)
    private val mutable = MutableStateFlow(FluidState(config = prefs.getString("config", null)?.let(::decodeFluid) ?: FluidConfig()))
    val state = mutable.asStateFlow()
    val device = DeviceRepository(app)
    private val sensors = app.getSystemService(android.hardware.SensorManager::class.java)
    private var lastWeatherAttempt=0L
    val weatherLocationAsked get() = prefs.getBoolean("weatherLocationAsked",false)
    fun markWeatherLocationAsked() { prefs.edit().putBoolean("weatherLocationAsked",true).apply() }
    private var weatherJob: Job? = null
    private var appsJob: Job? = null
    private var deviceJob: Job? = null
    private var toastJob: Job? = null
    init {
        val tasks = runCatching { JSONArray(prefs.getString("tasks", "[]")).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { FluidTask(it.getLong("id"), it.getString("text"), it.getBoolean("done")) } } } }.getOrDefault(emptyList())
        val today = LocalDate.now().toString()
        mutable.update { it.copy(tasks = tasks, water = if (prefs.getString("waterDay", "") == today) prefs.getInt("water", 0) else 0,
            standHours = if (prefs.getString("standDay", "") == today) prefs.getStringSet("standHours", emptySet()).orEmpty().size else 0,
            focusEnd = prefs.getLong("focusEnd", 0L).takeIf { it > 0L }, focusLeft = prefs.getLong("focusLeft", FOCUS_LENGTH)) }
        device.onChange = ::refreshDevice
        refreshApps(); refreshDevice(); refreshWeather()
        viewModelScope.launch { while (isActive) { delay(1000); mutable.update { it.copy(now = System.currentTimeMillis()) }; finishFocusIfDue() } }
    }
    fun returnHome() { mutable.update { it.copy(homeEpoch = it.homeEpoch + 1) } }
    fun tick() { mutable.update { it.copy(now = System.currentTimeMillis()) }; refreshDevice(); startSteps(); if(System.currentTimeMillis()-lastWeatherAttempt>15*60_000L)refreshWeather() }
    fun refreshApps() { appsJob?.cancel(); appsJob = viewModelScope.launch {
        try { val apps = device.apps(nativeIcons = true); mutable.update { it.copy(apps = apps, loading = false) } }
        catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(loading = false) }; message("Couldn't load apps. Try again.") }
    } }
    fun refreshDevice() { deviceJob?.cancel(); deviceJob = viewModelScope.launch {
        try { val d = device.state(); val disk = StatFs(getApplication<Application>().filesDir.path)
            mutable.update { it.copy(device = d, storageUsed = disk.totalBytes - disk.availableBytes, storageTotal = disk.totalBytes.coerceAtLeast(1)) }
        } catch (e: Exception) { if (e is CancellationException) throw e }
    } }
    fun configure(change: (FluidConfig) -> FluidConfig) {
        val config = change(mutable.value.config)
        mutable.update { it.copy(config = config) }; prefs.edit().putString("config", encodeFluid(config)).apply()
    }
    fun toggleWidget(widget: FluidWidget) = configure { it.copy(widgets = if (widget in it.widgets) it.widgets - widget else it.widgets + widget) }
    fun selectTheme(mode:String) {
        if(mode !in listOf("Light","Dark","Auto"))return
        configure { it.copy(mode=mode) }
    }
    fun importWallpaper(uri:android.net.Uri,lock:Boolean) {
        if(mutable.value.wallpaperApplying)return
        mutable.update { it.copy(wallpaperApplying=true) }
        viewModelScope.launch {
            var style:String?=null
            try {
                val importedStyle=withContext(Dispatchers.IO){saveCustomWallpaper(getApplication(),uri)}
                style=importedStyle
                applySystemWallpaper(getApplication(),importedStyle,lock,if(lock)mutable.value.config.lockWallpaperBlur else mutable.value.config.homeWallpaperBlur)
                configure { if(lock)it.copy(lockWallpaper=importedStyle)else it.copy(homeWallpaper=importedStyle) }
                message(if(lock)"Lock screen wallpaper updated"else"Home wallpaper updated")
            } catch(e:Exception) {
                style?.let { java.io.File(getApplication<Application>().filesDir,it.removePrefix("Custom:")).delete() }
                if(e is CancellationException)throw e
                message("Could not import this image. Please choose another photo.")
            } finally { mutable.update { it.copy(wallpaperApplying=false) } }
        }
    }
    fun applyWallpaper(style:String,lock:Boolean,dark:Boolean) {
        if(mutable.value.wallpaperApplying || !validWallpaper(style)) return
        mutable.update { it.copy(wallpaperApplying=true) }
        viewModelScope.launch {
            try {
                applySystemWallpaper(getApplication(),style,lock,if(lock)mutable.value.config.lockWallpaperBlur else mutable.value.config.homeWallpaperBlur)
                configure { if(lock)it.copy(lockWallpaper=style)else it.copy(homeWallpaper=style) }
                message(if(lock)"Lock screen wallpaper updated"else"Home wallpaper updated")
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                message(e.message ?: "Could not apply wallpaper")
            } finally { mutable.update { it.copy(wallpaperApplying=false) } }
        }
    }
    fun setWallpaperBlur(lock:Boolean,blur:Boolean) {
        if(mutable.value.wallpaperApplying)return
        val config=mutable.value.config; val style=if(lock)config.lockWallpaper else config.homeWallpaper
        mutable.update { it.copy(wallpaperApplying=true) }
        viewModelScope.launch {
            try {
                applySystemWallpaper(getApplication(),style,lock,blur)
                configure { if(lock)it.copy(lockWallpaperBlur=blur)else it.copy(homeWallpaperBlur=blur) }
            } catch(e:Exception) { if(e is CancellationException)throw e;message(e.message ?: "Could not update wallpaper blur") }
            finally { mutable.update { it.copy(wallpaperApplying=false) } }
        }
    }
    fun resizeWidget(widget: FluidWidget, size: String) {
        if(size in sizeOptions(widget)) configure { it.copy(sizes = it.sizes + (widget to size)) }
    }
    fun toggleSpaceWidget(widget:FluidWidget)=configure { it.copy(spaceWidgets=if(widget in it.spaceWidgets)it.spaceWidgets-widget else it.spaceWidgets+widget) }
    fun removeSpaceWidget(widget:FluidWidget)=configure { it.copy(spaceWidgets=it.spaceWidgets-widget) }
    fun moveWidget(widget: FluidWidget, delta: Int) = configure { c ->
        val list = c.widgets.toMutableList(); val from = list.indexOf(widget); val to = (from + delta).coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (from >= 0) { list.removeAt(from); list.add(to, widget) }; c.copy(widgets = list)
    }
    fun moveSpaceWidget(widget: FluidWidget, delta: Int) = configure { c ->
        val list = c.spaceWidgets.toMutableList(); val from = list.indexOf(widget); val to = (from + delta).coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (from >= 0) { list.removeAt(from); list.add(to, widget) }; c.copy(spaceWidgets = list)
    }
    fun widgetsForPage(page:String):List<FluidWidget> = when(page) { "Home"->mutable.value.config.widgets; "Space"->mutable.value.config.spaceWidgets; "Plan"->mutable.value.config.planWidgets; else->mutable.value.config.pageWidgets[page].orEmpty() }
    fun toggleWidgetForPage(page:String,widget:FluidWidget) = when(page) { "Home"->toggleWidget(widget);"Space"->toggleSpaceWidget(widget);"Plan"->togglePlanWidget(widget);else->configure { c -> val current=c.pageWidgets[page].orEmpty();c.copy(pageWidgets=c.pageWidgets+(page to if(widget in current)current-widget else current+widget)) } }
    fun removeWidgetForPage(page:String,widget:FluidWidget) = when(page) { "Home"->toggleWidget(widget);"Space"->removeSpaceWidget(widget);"Plan"->togglePlanWidget(widget);else->configure { c -> c.copy(pageWidgets=c.pageWidgets+(page to c.pageWidgets[page].orEmpty().filterNot{it==widget})) } }
    fun moveWidgetForPage(page:String,widget:FluidWidget,delta:Int) = when(page) { "Home"->moveWidget(widget,delta);"Space"->moveSpaceWidget(widget,delta);"Plan"->movePlanWidget(widget,delta);else->configure { c -> val list=c.pageWidgets[page].orEmpty().toMutableList();val from=list.indexOf(widget);val to=(from+delta).coerceIn(0,(list.size-1).coerceAtLeast(0));if(from>=0){list.removeAt(from);list.add(to,widget)};c.copy(pageWidgets=c.pageWidgets+(page to list)) } }
    /** Rewrites only the slots occupied by [visible] so hidden groups (top-row clock/weather,
     * tiny controls) keep their storage positions while the on-page order changes. */
    fun reorderVisibleWidgetsForPage(page:String,visible:List<FluidWidget>,newVisible:List<FluidWidget>) {
        fun reorder(list:List<FluidWidget>):List<FluidWidget> {
            val slots=list.indices.filter{list[it] in visible}
            if(slots.size!=newVisible.size)return list
            val result=list.toMutableList()
            slots.forEachIndexed{i,slot->result[slot]=newVisible[i]}
            return result
        }
        configure { c -> when(page) {
            "Home"->c.copy(widgets=reorder(c.widgets));"Space"->c.copy(spaceWidgets=reorder(c.spaceWidgets));"Plan"->c.copy(planWidgets=reorder(c.planWidgets))
            else->c.copy(pageWidgets=c.pageWidgets+(page to reorder(c.pageWidgets[page].orEmpty())))
        } }
    }
    fun toggleSpaceApp(space: Long, key: String) = configure { c ->
        c.copy(spaces = c.spaces.map { if (it.id == space) it.copy(apps = if (key in it.apps) it.apps - key else it.apps + key) else it })
    }
    /** Returns the new tab's id, or null when the name is empty or already taken. */
    fun addSpace(name: String): Long? {
        var id: Long? = null
        configure { c -> val valid = validSpaceName(name, c.spaces) ?: return@configure c
            val next = (c.spaces.maxOfOrNull { it.id } ?: 0) + 1; id = next; c.copy(spaces = c.spaces + SpaceTab(next, valid)) }
        return id
    }
    fun renameSpace(space: Long, name: String) = configure { c ->
        val valid = validSpaceName(name, c.spaces, except = space) ?: return@configure c
        c.copy(spaces = c.spaces.map { if (it.id == space) it.copy(name = valid) else it })
    }
    fun deleteSpace(space: Long) = configure { c -> c.copy(spaces = c.spaces.filterNot { it.id == space }) }
    fun moveSpace(space: Long, by: Int) = configure { c ->
        val from = c.spaces.indexOfFirst { it.id == space }; val to = from + by
        if (from < 0 || to !in c.spaces.indices) c else c.copy(spaces = c.spaces.toMutableList().apply { add(to, removeAt(from)) })
    }
    fun toggleFavoriteApp(key:String) = configure { c -> c.copy(favoriteApps=if(key in c.favoriteApps)c.favoriteApps-key else c.favoriteApps+key) }
    fun addPage(): String {
        var name = ""
        configure { c -> name = "Page ${(c.pages.count{it.startsWith("Page ")}+1)}"; c.copy(pages=c.pages+name) }
        return name
    }
    fun removePage(page:String)=configure { c -> if(page in listOf("Markets","Home"))c else c.copy(pages=c.pages-page) }
    fun moveWidgetToPage(from:String,to:String,widget:FluidWidget,before:FluidWidget?=null) {
        if(from==to)return
        fun FluidConfig.withPage(page:String,change:(List<FluidWidget>)->List<FluidWidget>):FluidConfig = when(page) {
            "Home"->copy(widgets=change(widgets));"Space"->copy(spaceWidgets=change(spaceWidgets));"Plan"->copy(planWidgets=change(planWidgets))
            else->copy(pageWidgets=pageWidgets+(page to change(pageWidgets[page].orEmpty())))
        }
        configure { c ->
            val removed=c.withPage(from){ it.filterNot{w->w==widget} }
            removed.withPage(to){ list -> val rest=list-widget; val at=before?.let{rest.indexOf(it)}?.takeIf{it>=0} ?: rest.size; rest.toMutableList().apply{add(at,widget)} }
        }
    }
    fun movePage(page:String,delta:Int)=configure { c -> val list=c.pages.toMutableList();val from=list.indexOf(page);val to=(from+delta).coerceIn(2,list.lastIndex);if(from>=2&&from!=to){list.removeAt(from);list.add(to,page)};c.copy(pages=list) }
    fun togglePlanWidget(widget:FluidWidget)=configure { c -> c.copy(planWidgets=if(widget in c.planWidgets)c.planWidgets-widget else c.planWidgets+widget) }
    fun movePlanWidget(widget:FluidWidget,delta:Int)=configure { c -> val list=c.planWidgets.toMutableList();val from=list.indexOf(widget);val to=(from+delta).coerceIn(0,(list.size-1).coerceAtLeast(0));if(from>=0){list.removeAt(from);list.add(to,widget)};c.copy(planWidgets=list) }
    fun removeDock(key: String) = configure { c -> c.copy(dock=fluidDock(mutable.value.apps,c.dock).take(if(c.dock==listOf("@auto")) c.dockCount else 6).map { it.component.flattenToString() } - key) }
    fun reorderDock(key: String, target: Int) = configure { c ->
        val keys=fluidDock(mutable.value.apps,c.dock).take(if(c.dock==listOf("@auto")) c.dockCount else 6).map { it.component.flattenToString() }.toMutableList()
        if(keys.remove(key)) keys.add(target.coerceIn(0,keys.size),key)
        c.copy(dock=keys)
    }
    fun toggleDock(key: String) {
        val c=mutable.value.config
        val keys=fluidDock(mutable.value.apps,c.dock).take(if(c.dock==listOf("@auto")) c.dockCount else 6).map { it.component.flattenToString() }
        if(key in keys) configure { it.copy(dock=keys-key) }
        else if(keys.size < c.dockCount) configure { it.copy(dock=keys+key) }
        else message("Dock is full. Increase icon count in Dock settings or remove an app.")
    }
    fun openNotification(notice: FluidNotification) {
        val pending=notice.intent ?: run { message("This notification has no page to open"); return }
        try {
            val options=android.app.ActivityOptions.makeBasic()
            if(android.os.Build.VERSION.SDK_INT>=34) options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pending.send(getApplication<Application>(),0,null,null,null,null,options.toBundle())
        } catch (_: android.app.PendingIntent.CanceledException) { message("This notification has expired") }
        catch (_: Exception) { message("Couldn't open this notification") }
    }
    fun launch(app: InstalledApp) = action { device.launch(app) }
    fun action(block: () -> Unit) { try { block() } catch (_: Exception) { message("This action isn't available on this device") } }
    fun open(intent: Intent) = action { device.open(intent) }
    fun message(text: String) { toastJob?.cancel(); mutable.update { it.copy(message = text) }; toastJob = viewModelScope.launch { delay(3500); mutable.update { it.copy(message = null) } } }
    fun media(command: String) {
        action {
            if (!device.transport(command)) {
                if (command == "play") device.startMusicPlayer()
                else message("Start a track in your music player first")
            }
        }
        refreshDevice()
    }
    fun accessNotifications() = open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun dial(phone:String) = open(Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:$phone")))
    fun toggle(name: String) {
        action { when (name) {
            "wifi", "mobile" -> device.open(Intent(if (android.os.Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS))
            "bluetooth" -> device.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "silent" -> device.silent()
            "torch" -> device.torch()
            "airplane" -> device.open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            "cast" -> device.open(Intent(Settings.ACTION_CAST_SETTINGS))
        } }; refreshDevice()
    }
    fun addTask(text: String) { if (text.isBlank()) return; saveTasks(mutable.value.tasks + FluidTask(System.nanoTime(), text.trim())) }
    fun toggleTask(task: FluidTask) = saveTasks(mutable.value.tasks.map { if (it.id == task.id) it.copy(done = !it.done) else it })
    fun removeTask(task: FluidTask) = saveTasks(mutable.value.tasks.filterNot { it.id == task.id })
    private fun saveTasks(tasks: List<FluidTask>) {
        mutable.update { it.copy(tasks = tasks) }
        prefs.edit().putString("tasks", JSONArray(tasks.map { JSONObject().put("id", it.id).put("text", it.text).put("done", it.done) }).toString()).apply()
    }
    fun weatherCity(city: String) { if (city.isBlank()) return; configure { it.copy(city = city.trim(),autoWeather=false) }; refreshWeather(true) }
    fun useWeatherLocation() { configure { it.copy(autoWeather=true) };refreshWeather(true) }
    fun refreshWeather(force: Boolean=false) {
        if(weatherJob?.isActive==true && !force)return
        weatherJob?.cancel();lastWeatherAttempt=System.currentTimeMillis()
        weatherJob=viewModelScope.launch {
            mutable.update{it.copy(weatherLoading=true,weatherError=null)}
            try {
                val config=mutable.value.config
                val weather=if(config.autoWeather){
                    val point=currentWeatherLocation(getApplication())
                    WidgetDataRepository().weather(point.latitude,point.longitude,point.name)
                } else WidgetDataRepository().weather(config.city)
                mutable.update{it.copy(weather=weather,weatherLoading=false)}
            }catch(e:Exception){if(e is CancellationException)throw e;mutable.update{it.copy(weatherLoading=false,weatherError=e.message ?: "Weather unavailable. Try again.")}}
        }
    }
    fun toggleFocus() {
        val now = System.currentTimeMillis()
        val s = mutable.value
        if (s.focusEnd != null) saveFocus(null, (s.focusEnd - now).coerceIn(0L, FOCUS_LENGTH))
        else saveFocus(now + s.focusLeft.takeIf { it > 0L }.let { it ?: FOCUS_LENGTH }, s.focusLeft)
    }
    fun resetFocus() = saveFocus(null, FOCUS_LENGTH)
    private fun finishFocusIfDue() {
        val end = mutable.value.focusEnd ?: return
        if (System.currentTimeMillis() < end) return
        saveFocus(null, FOCUS_LENGTH)
        message("Focus session complete. Take a short break.")
    }
    private fun saveFocus(end: Long?, left: Long) {
        mutable.update { it.copy(focusEnd = end, focusLeft = left) }
        prefs.edit().putLong("focusEnd", end ?: 0L).putLong("focusLeft", left).apply()
    }
    fun addWater(delta: Int) {
        val today = LocalDate.now().toString()
        val current = if (prefs.getString("waterDay", "") == today) mutable.value.water else 0
        val next = (current + delta).coerceIn(0, 20)
        mutable.update { it.copy(water = next) }
        prefs.edit().putString("waterDay", today).putInt("water", next).apply()
        if (delta > 0 && next == WATER_GOAL) message("Daily water goal reached")
    }
    fun startSteps() {
        if (android.os.Build.VERSION.SDK_INT >= 29 && androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        sensors.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)?.let { sensors.registerListener(this, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) }
    }
    fun pauseSensors() { sensors.unregisterListener(this) }
    override fun onSensorChanged(event: android.hardware.SensorEvent) {
        val total = event.values.firstOrNull()?.toInt() ?: return
        val day = LocalDate.now().toString()
        var baseline = prefs.getInt("stepBaseline", total)
        if (prefs.getString("stepDay", "") != day || total < baseline) { baseline = total; prefs.edit().putString("stepDay", day).putInt("stepBaseline", baseline).apply() }
        val steps = (total - baseline).coerceAtLeast(0)
        val previous = mutable.value.steps
        // An hour counts as a stand hour once steps are recorded during it.
        val hours = if (prefs.getString("standDay", "") == day) prefs.getStringSet("standHours", emptySet()).orEmpty() else emptySet()
        val stood = if (previous != null && steps > previous) hours + java.time.LocalTime.now().hour.toString() else hours
        if (stood != hours || prefs.getString("standDay", "") != day) prefs.edit().putString("standDay", day).putStringSet("standHours", stood).apply()
        mutable.update { it.copy(steps = steps, standHours = stood.size) }
    }
    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
    override fun onCleared() { device.close(); pauseSensors() }
}
