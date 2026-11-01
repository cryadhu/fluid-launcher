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
    AGENDA("Agenda", "Your day, at a glance", "calendar"), MUSIC("Music", "A little rhythm", "music"),
    WEATHER("Weather", "A window outside", "weather"), TASKS("Tasks", "Make room for focus", "tasks"),
    ACTIVITY("Activity", "Steps while the launcher is active", "health"), STORAGE("Storage", "Space for what matters", "files"), DATE("Date", "A moment in time", "calendar")
}
data class FluidTask(val id: Long, val text: String, val done: Boolean = false)
data class FluidConfig(val widgets: List<FluidWidget> = listOf(FluidWidget.AGENDA), val mode: String = "Light", val labels: Boolean = true,
    val city: String = "", val work: Set<String> = emptySet(), val personal: Set<String> = emptySet(), val dock: List<String> = listOf("@auto"))
fun encodeFluid(config: FluidConfig): String = JSONObject().apply {
    put("widgets", JSONArray(config.widgets.map { it.name })); put("mode", config.mode); put("labels", config.labels); put("city", config.city)
    put("work", JSONArray(config.work.toList())); put("personal", JSONArray(config.personal.toList())); put("dock", JSONArray(config.dock))
}.toString()
fun decodeFluid(raw: String): FluidConfig = runCatching {
    val j = JSONObject(raw)
    fun list(key: String) = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    FluidConfig(list("widgets").mapNotNull { name -> FluidWidget.entries.find { it.name == name } }.distinct(),
        j.optString("mode", "Light").takeIf { it in listOf("Light", "Dark", "Auto") } ?: "Light", j.optBoolean("labels", true),
        j.optString("city"), list("work").toSet(), list("personal").toSet(), if(j.has("dock")) list("dock").distinct().take(4) else listOf("@auto"))
}.getOrDefault(FluidConfig())

data class FluidState(val config: FluidConfig = FluidConfig(), val apps: List<InstalledApp> = emptyList(), val loading: Boolean = true,
    val device: DeviceState = DeviceState(), val now: Long = System.currentTimeMillis(), val homeEpoch: Int = 0,
    val tasks: List<FluidTask> = emptyList(), val weather: HomeWeather? = null, val weatherLoading: Boolean = false,
    val weatherError: String? = null, val storageUsed: Long = 0, val storageTotal: Long = 1, val message: String? = null,
    val steps: Int? = null)

class FluidModel(app: Application) : AndroidViewModel(app), android.hardware.SensorEventListener {
    private val prefs = app.getSharedPreferences("fluid_launcher_v1", 0)
    private val mutable = MutableStateFlow(FluidState(config = prefs.getString("config", null)?.let(::decodeFluid) ?: FluidConfig()))
    val state = mutable.asStateFlow()
    val device = DeviceRepository(app)
    private val sensors = app.getSystemService(android.hardware.SensorManager::class.java)
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
    fun tick() { mutable.update { it.copy(now = System.currentTimeMillis()) }; refreshDevice(); startSteps() }
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
    fun moveWidget(widget: FluidWidget, delta: Int) = configure { c ->
        val list = c.widgets.toMutableList(); val from = list.indexOf(widget); val to = (from + delta).coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (from >= 0) { list.removeAt(from); list.add(to, widget) }; c.copy(widgets = list)
    }
    fun assign(app: InstalledApp, space: String) = configure { c ->
        val key = app.component.flattenToString()
        c.copy(work = if (space == "Work") c.work + key else c.work - key, personal = if (space == "Personal") c.personal + key else c.personal - key)
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
    fun weatherCity(city: String) { if (city.isBlank()) return; configure { it.copy(city = city.trim()) }; refreshWeather() }
    fun refreshWeather() {
        val city = mutable.value.config.city; if (city.isBlank()) return
        weatherJob?.cancel(); weatherJob = viewModelScope.launch {
            mutable.update { it.copy(weatherLoading = true, weatherError = null) }
            try { val weather = WidgetDataRepository().weather(city); mutable.update { it.copy(weather = weather, weatherLoading = false) } }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(weatherLoading = false, weatherError = "Weather unavailable. Check your city and connection.") } }
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
