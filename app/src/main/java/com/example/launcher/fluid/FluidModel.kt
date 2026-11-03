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
    ACTIVITY("Activity", "Steps while the launcher is active", "health"), STORAGE("Storage", "Space for what matters", "files"), DATE("Date", "A moment in time", "calendar")
}
data class FluidTask(val id: Long, val text: String, val done: Boolean = false)
data class FluidConfig(val widgets: List<FluidWidget> = listOf(FluidWidget.CLOCK, FluidWidget.WEATHER, FluidWidget.NOTIFICATIONS, FluidWidget.AGENDA), val mode: String = "Auto", val labels: Boolean = true,
    val city: String = "", val work: Set<String> = emptySet(), val personal: Set<String> = emptySet(), val dock: List<String> = listOf("@auto"), val sizes: Map<FluidWidget,String> = emptyMap(), val stockSymbols: String = "RELIANCE.NS,TCS.NS,HDFCBANK.NS", val market: String = "India", val newsTopics: Set<NewsCategory> = setOf(NewsCategory.WORLD, NewsCategory.BUSINESS), val dockCount: Int = 4, val dockLabels: Boolean = false, val dockIconSize: Int = 48, val autoWeather: Boolean = true, val tools: Set<String> = emptySet(), val spaceWidgets: List<FluidWidget> = listOf(FluidWidget.TASKS), val homeWallpaper:String="Dawn", val lockWallpaper:String="Dawn", val homeWallpaperBlur:Boolean=false, val lockWallpaperBlur:Boolean=false, val favoriteApps:Set<String> = emptySet(), val pages:List<String> = listOf("Markets","Home","Space","Plan"), val accent:String="#8051FF", val planWidgets:List<FluidWidget> = listOf(FluidWidget.AGENDA,FluidWidget.TASKS,FluidWidget.QUICK_ACTIONS,FluidWidget.FAVORITES))
fun encodeFluid(config: FluidConfig): String = JSONObject().apply {
    put("tools",JSONArray(config.tools.toList()));put("spaceWidgets",JSONArray(config.spaceWidgets.map{it.name}))
    put("themeVersion",1);put("wallpaperVersion",3);put("dockDefaultsVersion",2)
    put("homeWallpaper",config.homeWallpaper);put("lockWallpaper",config.lockWallpaper);put("homeWallpaperBlur",config.homeWallpaperBlur);put("lockWallpaperBlur",config.lockWallpaperBlur)
    put("autoWeather",config.autoWeather)
    put("dockCount", config.dockCount); put("dockLabels", config.dockLabels); put("dockIconSize", config.dockIconSize); put("market", config.market); put("stockSymbols", config.stockSymbols); put("newsTopics", JSONArray(config.newsTopics.map { it.name })); put("homeVersion", 2); put("sizes", JSONObject(config.sizes.mapKeys { it.key.name }))
    put("widgets", JSONArray(config.widgets.map { it.name })); put("mode", config.mode); put("labels", config.labels); put("city", config.city)
    put("work", JSONArray(config.work.toList())); put("personal", JSONArray(config.personal.toList())); put("dock", JSONArray(config.dock));put("favoriteApps",JSONArray(config.favoriteApps.toList()));put("pages",JSONArray(config.pages));put("accent",config.accent);put("planWidgets",JSONArray(config.planWidgets.map{it.name}))
}.toString()
fun decodeFluid(raw: String): FluidConfig = runCatching {
    val j = JSONObject(raw)
    fun list(key: String) = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val stored = list("widgets").mapNotNull { name -> FluidWidget.entries.find { it.name == name } }.distinct()
    val widgets = if(j.optInt("homeVersion",1)<2) (listOf(FluidWidget.CLOCK,FluidWidget.WEATHER,FluidWidget.NOTIFICATIONS)+stored).distinct() else stored
    val sizes = FluidWidget.entries.mapNotNull { kind -> j.optJSONObject("sizes")?.optString(kind.name)?.takeIf { it in listOf("Compact","Standard","Large") }?.let { kind to it } }.toMap()
    FluidConfig(widgets,
        (if(j.has("themeVersion"))j.optString("mode", "Auto")else "Auto").takeIf { it in listOf("Light", "Dark", "Auto") } ?: "Auto", j.optBoolean("labels", true),
        j.optString("city"), list("work").toSet(), list("personal").toSet(), if(j.has("dock")) list("dock").distinct().take(6) else listOf("@auto"), sizes, j.optString("stockSymbols", "RELIANCE.NS,TCS.NS,HDFCBANK.NS"), j.optString("market", "India"), if(j.has("newsTopics")) list("newsTopics").mapNotNull(NewsCategory::fromKey).toSet() else setOf(NewsCategory.WORLD, NewsCategory.BUSINESS), j.optInt("dockCount",4).let { if(!j.has("dockDefaultsVersion") && it==3 && list("dock")==listOf("@auto"))4 else it }.coerceIn(1,6), j.optBoolean("dockLabels",false), j.optInt("dockIconSize",48).coerceIn(36,56),j.optBoolean("autoWeather",true),list("tools").toSet(),if(j.has("spaceWidgets"))list("spaceWidgets").mapNotNull{name->FluidWidget.entries.find{it.name==name}}.distinct()else listOf(FluidWidget.TASKS)).copy(homeWallpaper=j.optString("homeWallpaper","Dawn").let{if(it=="Original")"Default"else it}.takeIf{validWallpaper(it)} ?: "Dawn",lockWallpaper=j.optString("lockWallpaper","Dawn").let{if(it=="Original")"Default"else it}.takeIf{validWallpaper(it)} ?: "Dawn",homeWallpaperBlur=j.optBoolean("homeWallpaperBlur",false),lockWallpaperBlur=j.optBoolean("lockWallpaperBlur",false),favoriteApps=list("favoriteApps").toSet(),pages=(if(j.has("pages"))list("pages")else listOf("Markets","Home","Space","Plan")).let{listOf("Markets","Home")+it.filter{page->page !in listOf("Markets","Home")}.distinct()},accent=j.optString("accent","#8051FF").takeIf{runCatching{android.graphics.Color.parseColor(it)}.isSuccess} ?: "#8051FF",planWidgets=(if(j.has("planWidgets"))list("planWidgets").mapNotNull{name->FluidWidget.entries.find{it.name==name}}else listOf(FluidWidget.AGENDA,FluidWidget.TASKS,FluidWidget.QUICK_ACTIONS,FluidWidget.FAVORITES)).distinct())
}.getOrDefault(FluidConfig())

data class FluidState(val config: FluidConfig = FluidConfig(), val apps: List<InstalledApp> = emptyList(), val loading: Boolean = true,
    val device: DeviceState = DeviceState(), val now: Long = System.currentTimeMillis(), val homeEpoch: Int = 0, val wallpaperApplying:Boolean=false,
    val tasks: List<FluidTask> = emptyList(), val weather: HomeWeather? = null, val weatherLoading: Boolean = false,
    val weatherError: String? = null, val storageUsed: Long = 0, val storageTotal: Long = 1, val message: String? = null,
    val steps: Int? = null, val weatherPreview: Int = 0)

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
        mutable.update { it.copy(tasks = tasks) }
        device.onChange = ::refreshDevice
        refreshApps(); refreshDevice(); refreshWeather()
        viewModelScope.launch { while (isActive) { delay(1000); mutable.update { it.copy(now = System.currentTimeMillis()) } } }
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
        if(size in listOf("Compact","Standard","Large")) configure { it.copy(sizes = it.sizes + (widget to size)) }
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
    fun assign(app: InstalledApp, space: String) = configure { c ->
        val key = app.component.flattenToString()
        c.copy(work = if (space == "Work") c.work + key else c.work - key, personal = if (space == "Personal") c.personal + key else c.personal - key, tools=if(space=="Tools")c.tools+key else c.tools-key)
    }
    fun toggleFavoriteApp(key:String) = configure { c -> c.copy(favoriteApps=if(key in c.favoriteApps)c.favoriteApps-key else c.favoriteApps+key) }
    fun addPage()=configure { c -> c.copy(pages=c.pages+"Page ${(c.pages.count{it.startsWith("Page ")}+1)}") }
    fun removePage(page:String)=configure { c -> if(page in listOf("Markets","Home"))c else c.copy(pages=c.pages-page) }
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
    fun cycleWeatherPreview() { mutable.update { it.copy(weatherPreview = (it.weatherPreview + 1) % 7) } }
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
        mutable.update { it.copy(steps = (total - baseline).coerceAtLeast(0)) }
    }
    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
    override fun onCleared() { device.close(); pauseSensors() }
}
