package com.example.launcher

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.*
import com.example.launcher.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val android.content.Context.settingsStore by preferencesDataStore("launcher_settings")
data class LauncherUiState(
    val badgePackages: Set<String> = emptySet(),
    val favoriteApps: Set<String> = emptySet(),
    val arsenal: ArsenalSchedule = ArsenalSchedule(), val arsenalLoading: Boolean = false, val arsenalError: Boolean = false,
    val league: LeagueTable = LeagueTable(), val leagueLoading: Boolean = false, val leagueError: Boolean = false,
    val customization: HomeCustomization = HomeCustomization(), val homePanel: HomePanel? = null, val customizePage: Int = 1,
    val selectedWidget: String? = null, val weather: HomeWeather? = null, val weatherError: String? = null,
    val marketQuotes: List<MarketQuote> = emptyList(), val widgetDataLoading: Boolean = false,
    val focusUntil: Long = 0, val focusRemaining: Long = 25 * 60_000L,
    val homeReturnEpoch: Int = 0,
    val settingsLoaded: Boolean = false,
    val homeLayout: List<HomeEntry>? = null, val openFolderId: String? = null,
    val dockComponents: List<String> = listOf("@phone", "@messages", "@camera", "@browser"),
    val page: Int = 1, val drawer: Boolean = false, val focusSearch: Boolean = false, val query: String = "",
    val apps: List<InstalledApp> = emptyList(), val appsLoading: Boolean = true,
    val now: Long = System.currentTimeMillis(), val device: DeviceState = DeviceState(),
    val feed: FeedData = FeedData(), val feedLoading: Boolean = false,
    val selectedNews: Set<NewsCategory> = NewsCategory.all, val newsSettingsOpen: Boolean = false,
    val connectivityPopup: ConnectivityType? = null,
    val tasks: List<TaskEntity> = emptyList(), val notes: List<NoteEntity> = emptyList(),
    val pins: List<PinnedAppEntity> = emptyList(), val favourites: List<FavouriteEntity> = emptyList(),
    val noteDraft: String = "", val taskDraft: String = "", val message: String? = null
)
class LauncherViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(LauncherUiState(page = saved.get<Int>("page") ?: 1, noteDraft = saved["noteDraft"] ?: "", taskDraft = saved["taskDraft"] ?: ""))
    val uiState = mutable.asStateFlow()
    val device = DeviceRepository(app)
    private val db = LauncherDatabase.getInstance(app)
    private val dao = db.dao()
    private val feeds = FeedRepository(app)
    private val wallpaperMutex = Mutex()
    private val leagueRepository = PremierLeagueRepository(app)
    private val arsenalRepository = ArsenalFixturesRepository(app)
    private var arsenalJob: Job? = null
    private var lastArsenalAttempt = 0L
    private var leagueJob: Job? = null
    private var lastLeagueAttempt = 0L
    private var refreshJob: Job? = null
    private var deviceJob: Job? = null
    private var appsJob: Job? = null
    private var toastJob: Job? = null
    private val lastNewsAttempt = mutableMapOf<NewsCategory, Long>()
    private var lastRatesAttempt = 0L
    private var pendingFeedRefresh = false
    private val pageKey = intPreferencesKey("selected_page")
    private val favoriteAppsKey = stringSetPreferencesKey("drawer_favorite_apps")
    private val dockKey = stringPreferencesKey("dock_components")
    private val homeLayoutKey = stringPreferencesKey("home_layout_v1")
    private val newsKey = stringSetPreferencesKey("news_categories_v1")
    private val customizationKey = stringPreferencesKey("home_customization_v1")
    private val focusUntilKey = longPreferencesKey("focus_until")
    private val focusRemainingKey = longPreferencesKey("focus_remaining")
    private val widgetData = WidgetDataRepository()
    private var widgetJob: Job? = null
    private var widgetRefreshPending = false
    private var lastWidgetRefresh = 0L
    init {
        device.onChange = { refreshDevice() }
        viewModelScope.launch { NotificationBadges.packages.collect { badges -> mutable.update { it.copy(badgePackages = badges) } } }
        refreshApps(); refreshDevice()
        viewModelScope.launch { dao.tasks().collect { tasks -> mutable.update { it.copy(tasks = tasks) } } }
        viewModelScope.launch { dao.notes().collect { notes -> mutable.update { it.copy(notes = notes) } } }
        viewModelScope.launch { dao.pins().collect { pins -> mutable.update { it.copy(pins = pins) } } }
        viewModelScope.launch { dao.favourites().collect { favourites -> mutable.update { it.copy(favourites = favourites) } } }
        viewModelScope.launch {
            try {
                val preferences = app.settingsStore.data.first()
                preferences[customizationKey]?.let { raw -> mutable.update { it.copy(customization = decodeCustomization(raw)) } }
                mutable.update { it.copy(focusUntil = preferences[focusUntilKey] ?: 0, focusRemaining = preferences[focusRemainingKey] ?: 25 * 60_000L) }
                mutable.update { it.copy(favoriteApps = preferences[favoriteAppsKey] ?: emptySet()) }
                val firstCustomizationSetup = preferences[customizationKey] == null
                preferences[homeLayoutKey]?.let { raw -> mutable.update { it.copy(homeLayout = decodeHomeLayout(raw).let { layout -> if (firstCustomizationSetup) withoutHomeSettingsShortcut(layout) else layout }) } }
                preferences[newsKey]?.let { keys -> mutable.update { it.copy(selectedNews = keys.mapNotNull(NewsCategory::fromKey).toSet()) } }
                preferences[dockKey]?.let { raw ->
                    val dock = runCatching { org.json.JSONArray(raw).let { json -> (0 until json.length()).map { json.getString(it) } } }.getOrNull()
                    if (dock != null && dock.size <= 5) mutable.update { it.copy(dockComponents = dock) }
                }
                if (!saved.contains("page")) {
                    val page = preferences[pageKey] ?: 1
                    if (!saved.contains("page")) mutable.update { state -> state.copy(page = page.takeIf { id -> state.customization.pages.any { it.id == id } } ?: 1) }
                }
                if (firstCustomizationSetup) {
                    dao.pins().first().filter { it.component.substringBefore('/') == "com.android.settings" }.forEach { dao.unpin(it.component) }
                    app.settingsStore.edit { values ->
                        values[customizationKey] = encodeCustomization(mutable.value.customization)
                        mutable.value.homeLayout?.let { values[homeLayoutKey] = encodeHomeLayout(it) }
                    }
                }
            } catch (e: Exception) { if (e is CancellationException) throw e }
            finally { mutable.update { it.copy(settingsLoaded = true) } }
            val cached = feeds.cached(); mutable.update { it.copy(feed = cached) }
            val league = leagueRepository.cached(); mutable.update { it.copy(league = league) }
            val arsenal = arsenalRepository.cached(); mutable.update { it.copy(arsenal = arsenal) }
            if (mutable.value.customization.widgets.isNotEmpty()) refreshWidgetData()
            if (mutable.value.page == 0) refreshFeed()
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (mutable.value.customization.widgets.any { it.kind == HomeWidgetKind.WEATHER || it.kind == HomeWidgetKind.MARKETS } && System.currentTimeMillis() - lastWidgetRefresh > 15 * 60_000L) refreshWidgetData()
                val until = mutable.value.focusUntil
                if (until > 0) {
                    val remaining = (until - System.currentTimeMillis()).coerceAtLeast(0)
                    mutable.update { it.copy(now = System.currentTimeMillis(), focusRemaining = remaining, focusUntil = if (remaining == 0L) 0 else until) }
                    if (remaining == 0L) {
                        persistFocus(); message("Focus session complete")
                        val app = getApplication<Application>()
                        if (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val pending = android.app.PendingIntent.getActivity(app, 25, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
                            runCatching { androidx.core.app.NotificationManagerCompat.from(app).notify(25, androidx.core.app.NotificationCompat.Builder(app, "launcher").setSmallIcon(com.example.launcher.R.drawable.ic_cry).setContentTitle("Focus session complete").setContentText("Time for a break.").setContentIntent(pending).setAutoCancel(true).build()) }
                        }
                    }
                }
            }
        }
    }
    fun page(page: Int) {
        val p = page.takeIf { id -> visiblePages(mutable.value.customization).any { it.id == id } } ?: 1; saved["page"] = p
        if (p != 2) device.stopBluetoothScan()
        mutable.update { it.copy(page = p, openFolderId = if (p == it.page && it.customization.pages.any { page -> page.id == p && page.isHome }) it.openFolderId else null, newsSettingsOpen = it.newsSettingsOpen && p == 0, connectivityPopup = if (p == 2) it.connectivityPopup else null) }
        viewModelScope.launch { getApplication<Application>().settingsStore.edit { it[pageKey] = p } }
        if (p == 0) refreshFeed()
    }
    fun drawer(open: Boolean, focus: Boolean = false) { mutable.update { it.copy(drawer = open, openFolderId = null, focusSearch = focus, query = "") } }
    fun returnHome() = revealHomePage(1)
    private fun revealHomePage(pageId: Int) {
        saved["page"] = pageId
        device.stopBluetoothScan()
        mutable.update { it.copy(page = pageId, drawer = false, openFolderId = null, newsSettingsOpen = false,
            connectivityPopup = null, focusSearch = false, query = "", homePanel = null, selectedWidget = null, homeReturnEpoch = it.homeReturnEpoch + 1) }
        persist { getApplication<Application>().settingsStore.edit { it[pageKey] = pageId } }
    }
    fun query(query: String) { mutable.update { it.copy(query = query) } }
    fun toggleFavoriteApp(app: InstalledApp) {
        val component = app.component.flattenToString()
        persist {
            getApplication<Application>().settingsStore.edit { values ->
                val current = values[favoriteAppsKey] ?: emptySet()
                val updated = if (component in current) current - component else current + component
                values[favoriteAppsKey] = updated
                mutable.update { it.copy(favoriteApps = updated) }
            }
        }
    }
    fun dismissDrawerSearchFocus() { mutable.update { it.copy(focusSearch = false) } }
    fun tick() { mutable.update { it.copy(now = System.currentTimeMillis()) }; refreshDevice() }
    fun refreshApps() {
        appsJob?.cancel(); appsJob = viewModelScope.launch {
            try { val apps = device.apps(); mutable.update { it.copy(apps = apps, appsLoading = false) } }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(appsLoading = false) }; message("Couldn’t load installed apps") }
        }
    }
    fun refreshDevice() {
        deviceJob?.cancel(); deviceJob = viewModelScope.launch {
            try { val state = device.state(); mutable.update { it.copy(device = state) } }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    fun refreshFeed() {
        refreshArsenal()
        refreshLeague()
        if (refreshJob?.isActive == true) { pendingFeedRefresh = true; return }
        val now = System.currentTimeMillis()
        val required = mutable.value.selectedNews.filter { now - (lastNewsAttempt[it] ?: 0L) >= 15 * 60_000 }.toSet()
        val refreshRates = now - lastRatesAttempt >= 15 * 60_000
        if (required.isEmpty() && !refreshRates) return
        required.forEach { lastNewsAttempt[it] = now }
        if (refreshRates) lastRatesAttempt = now
        refreshJob = viewModelScope.launch {
            mutable.update { it.copy(feedLoading = true) }
            try { val feed = feeds.refresh(mutable.value.feed, required, refreshRates); mutable.update { it.copy(feed = feed) } }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(feed = it.feed.copy(error = "Feed unavailable. Check your connection.")) } }
            finally {
                mutable.update { it.copy(feedLoading = false) }
                if (pendingFeedRefresh) {
                    pendingFeedRefresh = false
                    refreshJob = null
                    if (mutable.value.page == 0) refreshFeed()
                }
            }
        }
    }
    fun refreshLeague(force: Boolean = false) {
        if (leagueJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (!force && (now - mutable.value.league.fetchedAt < 15 * 60_000 || now - lastLeagueAttempt < 60_000)) return
        lastLeagueAttempt = now
        leagueJob = viewModelScope.launch {
            mutable.update { it.copy(leagueLoading = true) }
            try { val table = leagueRepository.refresh(); mutable.update { it.copy(league = table, leagueError = false) } }
            catch (error: Exception) { if (error is CancellationException) throw error; mutable.update { it.copy(leagueError = true) } }
            finally { mutable.update { it.copy(leagueLoading = false) } }
        }
    }
    fun refreshArsenal(force: Boolean = false) {
        if (arsenalJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (!force && (now - mutable.value.arsenal.fetchedAt < 15 * 60_000 || now - lastArsenalAttempt < 60_000)) return
        lastArsenalAttempt = now
        arsenalJob = viewModelScope.launch {
            mutable.update { it.copy(arsenalLoading = true) }
            try { val schedule = arsenalRepository.refresh(); mutable.update { it.copy(arsenal = schedule, arsenalError = false) } }
            catch (error: Exception) { if (error is CancellationException) throw error; mutable.update { it.copy(arsenalError = true) } }
            finally { mutable.update { it.copy(arsenalLoading = false) } }
        }
    }
    fun requestFeedRefresh() {
        if (refreshJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (mutable.value.selectedNews.any { now - (lastNewsAttempt[it] ?: 0L) >= 15 * 60_000 } || now - lastRatesAttempt >= 15 * 60_000) refreshFeed()
        else message("Checked recently. Feeds update every 15 minutes.")
    }
    fun showNewsSettings(open: Boolean) { mutable.update { it.copy(newsSettingsOpen = open) } }
    fun selectNews(categories: Set<NewsCategory>) {
        mutable.update { it.copy(selectedNews = categories.toSet()) }
        persist { getApplication<Application>().settingsStore.edit { it[newsKey] = categories.map { category -> category.name }.toSet() } }
        if (mutable.value.page == 0) refreshFeed()
    }
    fun showConnectivity(type: ConnectivityType?) { if (type != ConnectivityType.BLUETOOTH) device.stopBluetoothScan(); mutable.update { it.copy(connectivityPopup = type) }; refreshDevice() }
    fun launch(app: InstalledApp) { val origin = activeHomePage(); action { device.launch(app); revealHomePage(origin) } }
    fun action(block: () -> Unit) { try { block() } catch (_: Exception) { message("This action is unavailable on this device") } }
    fun toggle(name: String) = action {
        when (name) {
            "wifi" -> device.open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS))
            "bluetooth" -> device.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "flashlight" -> { device.torch(); message(if (mutable.value.device.torch) "Flashlight off" else "Flashlight on") }
            "silent" -> device.silent()
            "mobile" -> device.mobileData()
        }
        refreshDevice()
    }
    fun scanWifi() = action { if (!device.scanWifi()) message("Showing recent networks; Android may limit scan frequency"); refreshDevice() }
    fun scanBluetooth() = action { if (!device.scanBluetooth()) message("Turn on Bluetooth to find nearby devices"); refreshDevice() }
    fun pairBluetooth(address: String) = action { if (!device.pairBluetooth(address)) message("Couldn’t start pairing. Try More settings."); refreshDevice() }
    fun draft(value: String) { saved["noteDraft"] = value; mutable.update { it.copy(noteDraft = value) } }
    fun taskDraft(value: String) { saved["taskDraft"] = value; mutable.update { it.copy(taskDraft = value) } }
    fun addTask() { val text = mutable.value.taskDraft.trim(); if (text.isNotEmpty()) persist { dao.addTask(TaskEntity(text = text)); taskDraft("") } }
    fun toggleTask(task: TaskEntity) = persist { dao.toggleTask(task.id, !task.done) }
    fun deleteTask(task: TaskEntity) = persist { dao.deleteTask(task.id) }
    fun saveNote() {
        val body = mutable.value.noteDraft.trim(); if (body.isEmpty()) { message("Write a note first"); return }
        persist { dao.addNote(NoteEntity(body = body)); draft(""); message("Note saved") }
    }
    fun pin(app: InstalledApp) {
        val layout = homeEntries()
        val blocked = homeBlockedSlots(mutable.value.customization, activeHomePage())
        val key = app.component.flattenToString()
        if (layout.any { !it.isFolder && it.id == key }) saveHomeLayout(layout.filterNot { !it.isFolder && it.id == key })
        else if (firstFreeHomeSlot(layout, blocked) < 0) message("This page is full. Add a page or create a folder.")
        else saveHomeLayout(moveHomeEntry(layout, appEntry(key), firstFreeHomeSlot(layout, blocked), blocked))
    }
    fun favourite(name: String, phone: String) = persist { dao.favourite(FavouriteEntity(phone, name)) }
    fun saveHomeOrder(apps: List<InstalledApp>) {
        saveHomeLayout(apps.distinctBy { it.component }.take(HOME_CAPACITY).map { appEntry(it.component.flattenToString()) })
    }
    fun saveHomeLayout(entries: List<HomeEntry>, pageId: Int = activeHomePage()) {
        if (mutable.value.customization.locked) { message("Home layout is locked"); return }
        val layout = normalizeHomeLayout(entries)
        if (layout.any { it.slot in homeBlockedSlots(mutable.value.customization, pageId) }) { message("That space contains a widget"); return }
        if (pageId != 1) {
            changeCustomization(mutable.value.customization.copy(layouts = mutable.value.customization.layouts + (pageId to layout)))
            return
        }
        val pins = layout.mapNotNull { entry -> if (!entry.isFolder) PinnedAppEntity(entry.id, entry.slot) else null }
        mutable.update { it.copy(homeLayout = layout, pins = pins, openFolderId = it.openFolderId?.takeIf { id -> layout.any { entry -> entry.id == id } }) }
        persist {
            getApplication<Application>().settingsStore.edit { it[homeLayoutKey] = encodeHomeLayout(layout) }
            dao.replacePins(pins)
        }
    }
    fun openFolder(id: String?) { mutable.update { it.copy(openFolderId = id, drawer = false) } }
    fun renameFolder(id: String, name: String) {
        val title = name.trim().take(40).ifEmpty { "Folder" }
        saveHomeLayout(homeEntries().map { if (it.id == id) it.copy(title = title) else it })
    }
    fun setFolderApps(id: String, components: List<String>) {
        saveHomeLayout(homeEntries().mapNotNull { if (it.id == id) components.distinct().takeIf { apps -> apps.isNotEmpty() }?.let { apps -> it.copy(components = apps) } else it })
    }
    fun ungroupFolder(id: String) {
        val layout = homeEntries()
        val folder = layout.find { it.id == id } ?: return
        val otherIds = layout.filterNot { it.id == id }.map { it.id }.toSet()
        val blocked = homeBlockedSlots(mutable.value.customization, activeHomePage())
        if (layout.size - 1 + folder.components.count { it !in otherIds } > HOME_CAPACITY - blocked.size) { message("Not enough Home space to ungroup. Remove apps from the folder first."); return }
        var ungrouped = layout.filterNot { it.id == id }
        folder.components.filterNot { it in otherIds }.forEachIndexed { index, key ->
            ungrouped = moveHomeEntry(ungrouped, appEntry(key), if (index == 0) folder.slot else firstFreeHomeSlot(ungrouped, blocked), blocked)
        }
        saveHomeLayout(ungrouped)
    }
    fun saveDockOrder(components: List<String>) {
        if (mutable.value.customization.locked) { message("Home layout is locked"); return }
        if (components.size > mutable.value.customization.preferences.dockLimit) return
        mutable.update { it.copy(dockComponents = components) }
        persist { getApplication<Application>().settingsStore.edit { it[dockKey] = org.json.JSONArray(components).toString() } }
    }
    fun removeFavourite(contact: FavouriteEntity) = persist { dao.removeFavourite(contact.phone) }
    fun activeHomePage(): Int = mutable.value.page.takeIf { id -> mutable.value.customization.pages.any { it.id == id && it.isHome } } ?: 1
    fun homeEntries(pageId: Int = activeHomePage()): List<HomeEntry> = normalizeHomeLayout(if (pageId == 1)
        mutable.value.homeLayout ?: com.example.launcher.ui.homeApps(mutable.value.apps, mutable.value.pins).map { appEntry(it.component.flattenToString()) }
        else mutable.value.customization.layouts[pageId].orEmpty())
    private fun changeCustomization(config: HomeCustomization) {
        mutable.update { it.copy(customization = config) }
        persist { getApplication<Application>().settingsStore.edit { it[customizationKey] = encodeCustomization(config) } }
    }
    fun showHomePanel(panel: HomePanel?) {
        mutable.update { it.copy(homePanel = panel, customizePage = if (panel == HomePanel.CUSTOMIZE) activeHomePage() else it.customizePage) }
        if (panel == HomePanel.WIDGETS) refreshWidgetData()
    }
    fun addHomePage(): Int {
        val config = mutable.value.customization
        val id = maxOf(3, config.pages.maxOf { it.id }) + 1
        val page = LauncherPage(id, "Page ${id - 3}")
        val pages = config.pages.toMutableList().apply { add(indexOfFirst { it.id == 1 } + 1, page) }
        changeCustomization(config.copy(pages = pages))
        mutable.update { it.copy(page = id, homePanel = null, homeReturnEpoch = it.homeReturnEpoch + 1) }
        return id
    }
    fun removeHomePage(id: Int) {
        if (id == 0 || id == 1) return
        val config = mutable.value.customization
        changeCustomization(config.copy(pages = config.pages.filterNot { it.id == id }, layouts = config.layouts - id, widgets = config.widgets.filterNot { it.pageId == id }))
        mutable.update { it.copy(page = if (it.page == id) 1 else it.page, homeReturnEpoch = it.homeReturnEpoch + 1) }
    }
    fun reorderPages(sourceId: Int, targetId: Int) {
        val config = mutable.value.customization
        val pages = reorderHomePages(config.pages, sourceId, targetId)
        if (pages == config.pages) return
        changeCustomization(config.copy(pages = pages))
        mutable.update { it.copy(homeReturnEpoch = it.homeReturnEpoch + 1) }
    }
    fun setHomeLocked(locked: Boolean) = changeCustomization(mutable.value.customization.copy(locked = locked))
    fun updateHomePreferences(value: HomePreferences) {
        val config = mutable.value.customization
        if (!gridFits(config, value.gridRows, homeEntries(1))) { message("Move icons or widgets out of the lower rows first"); return }
        if (mutable.value.dockComponents.size > value.dockLimit) { message("Remove dock icons before reducing the limit"); return }
        changeCustomization(config.copy(preferences = value))
        if (mutable.value.page in value.hiddenPages) returnHome()
    }
    fun setPageVisible(id: Int, visible: Boolean) {
        if (id !in setOf(0,2,3)) return
        val config = mutable.value.customization
        val pages = if (visible && config.pages.none { it.id == id }) config.pages + initialPages.first { it.id == id } else config.pages
        changeCustomization(config.copy(pages = pages, preferences = config.preferences.copy(hiddenPages = if (visible) config.preferences.hiddenPages - id else config.preferences.hiddenPages + id)))
        mutable.update { it.copy(homeReturnEpoch = it.homeReturnEpoch + 1) }
        if (!visible && mutable.value.page == id) returnHome()
    }
    fun setAppearanceMode(mode: String) {
        val config = mutable.value.customization
        changeCustomization(config.copy(theme = if (mode == "Light") "Sand" else "Tonal", followSystemTheme = mode == "Auto", wallpaper = "Theme"))
    }
    fun openNotificationAccess() = action { device.open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    fun openLockAccess() = action { device.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    fun lockPhone() { if (!LauncherLockService.lock()) message("Enable Double-tap lock in Android Accessibility settings") }
    fun selectTheme(name: String) {
        if (name !in listOf("Tonal", "Solar", "Ember", "Sand", "Custom")) return
        changeCustomization(mutable.value.customization.copy(theme = name, followSystemTheme = false, wallpaper = "Theme", preferences = mutable.value.customization.preferences.copy(accentColor = null)))
    }
    fun followSystemTheme(enabled: Boolean) = changeCustomization(mutable.value.customization.copy(followSystemTheme = enabled, wallpaper = "Theme"))
    fun saveCustomTheme(background: Int, accent: Int) = changeCustomization(mutable.value.customization.copy(theme = "Custom", customBackground = background or 0xFF000000.toInt(), customAccent = accent or 0xFF000000.toInt(), followSystemTheme = false, wallpaper = "Theme", preferences = mutable.value.customization.preferences.copy(accentColor = null)))
    fun setWallpaper(name: String) {
        if (name !in listOf("Forest", "Midnight", "Charcoal", "Plum", "Paper")) return
        viewModelScope.launch {
            wallpaperMutex.withLock {
                try {
                    applyPhoneWallpaper(getApplication(), name)
                    changeCustomization(mutable.value.customization.copy(wallpaper = name))
                    message("Home and lock screen wallpaper updated")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message("Phone could not change home and lock screen wallpaper")
                }
            }
        }
    }
    fun setPhotoWallpaper(uri: android.net.Uri) {
        viewModelScope.launch { wallpaperMutex.withLock {
            try {
                applyPhotoWallpaper(getApplication(), uri)
                changeCustomization(mutable.value.customization.copy(wallpaper = "Photo:${System.currentTimeMillis()}"))
                message("Photo applied to home and lock screen")
            } catch (error: Exception) { if (error is CancellationException) throw error; message("Phone could not apply this photo") }
        } }
    }
    fun addHomeWidget(kind: HomeWidgetKind) {
        if (mutable.value.customization.locked) { message("Home layout is locked"); return }
        var pageId = mutable.value.customizePage.takeIf { id -> mutable.value.customization.pages.any { it.id == id && it.isHome } } ?: 1
        var slot = findWidgetSlot(kind, homeEntries(pageId).map { it.slot }.toSet() + homeBlockedSlots(mutable.value.customization, pageId))
        if (slot < 0) { pageId = addHomePage(); slot = 0; message("Added ${kind.title} to a new page") }
        changeCustomization(mutable.value.customization.copy(widgets = mutable.value.customization.widgets + HomeWidget(kind = kind, pageId = pageId, slot = slot)))
        mutable.update { it.copy(page = pageId, homePanel = null, homeReturnEpoch = it.homeReturnEpoch + 1) }
        if (kind == HomeWidgetKind.WEATHER || kind == HomeWidgetKind.MARKETS) refreshWidgetData()
    }
    fun selectWidget(id: String?) { mutable.update { it.copy(selectedWidget = id) } }
    fun removeHomeWidget(id: String) {
        if (mutable.value.customization.locked) { message("Home layout is locked"); return }
        changeCustomization(mutable.value.customization.copy(widgets = mutable.value.customization.widgets.filterNot { it.id == id }))
        selectWidget(null)
    }
    fun moveWidgetToPage(id: String, pageId: Int) {
        if (mutable.value.customization.locked) { message("Home layout is locked"); return }
        val widget = mutable.value.customization.widgets.find { it.id == id } ?: return
        val config = mutable.value.customization.copy(widgets = mutable.value.customization.widgets.filterNot { it.id == id })
        val slot = findWidgetSlot(widget.kind, homeEntries(pageId).map { it.slot }.toSet() + homeBlockedSlots(config, pageId))
        if (slot < 0) { message("No room on that page"); return }
        changeCustomization(config.copy(widgets = config.widgets + widget.copy(pageId = pageId, slot = slot)))
        mutable.update { it.copy(selectedWidget = null, page = pageId, homeReturnEpoch = it.homeReturnEpoch + 1) }
    }
    fun resetHomeSettings() {
        changeCustomization(HomeCustomization())
        mutable.update { it.copy(homeLayout = null, pins = emptyList(), dockComponents = listOf("@phone", "@messages", "@camera", "@browser"), selectedNews = NewsCategory.all, weather = null, weatherError = null) }
        persist {
            dao.replacePins(emptyList())
            getApplication<Application>().settingsStore.edit { it.remove(homeLayoutKey); it.remove(dockKey); it.remove(newsKey) }
        }
        returnHome(); resetFocus()
    }
    fun setWeatherCity(city: String) {
        if (city.trim().isEmpty()) return
        changeCustomization(mutable.value.customization.copy(city = city.trim()))
        mutable.update { it.copy(weather = null) }; refreshWidgetData()
    }
    fun refreshWidgetData() {
        val catalog = mutable.value.homePanel == HomePanel.WIDGETS
        val wantWeather = mutable.value.customization.city.isNotBlank() && (catalog || mutable.value.customization.widgets.any { it.kind == HomeWidgetKind.WEATHER })
        val wantMarkets = catalog || mutable.value.customization.widgets.any { it.kind == HomeWidgetKind.MARKETS }
        if (!wantWeather && !wantMarkets) return
        if (widgetJob?.isActive == true) { widgetRefreshPending = true; return }
        lastWidgetRefresh = System.currentTimeMillis()
        widgetJob = viewModelScope.launch {
            mutable.update { it.copy(widgetDataLoading = true, weatherError = null) }
            supervisorScope {
                val weather = launch {
                    val city = mutable.value.customization.city
                    if (wantWeather) try { val data = widgetData.weather(city); mutable.update { if (it.customization.city == city) it.copy(weather = data) else it } }
                    catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(weatherError = "Weather unavailable. Check the city or retry.") } }
                }
                val markets = launch { if (wantMarkets) { val data = runCatching { widgetData.markets() }.getOrDefault(emptyList()); mutable.update { it.copy(marketQuotes = data.ifEmpty { it.marketQuotes }) } } }
                weather.join(); markets.join()
            }
            mutable.update { it.copy(widgetDataLoading = false) }
            widgetJob = null
            if (widgetRefreshPending) { widgetRefreshPending = false; refreshWidgetData() }
        }
    }
    fun saveQuickNote(text: String) { if (text.isNotBlank()) persist { dao.addNote(NoteEntity(body = text.trim())); message("Note saved") } }
    fun addHomeTask(text: String) { if (text.isNotBlank()) persist { dao.addTask(TaskEntity(text = text.trim())) } }
    private fun persistFocus() = persist { getApplication<Application>().settingsStore.edit { it[focusUntilKey] = mutable.value.focusUntil; it[focusRemainingKey] = mutable.value.focusRemaining } }
    fun toggleFocus() {
        val state = mutable.value
        val duration = state.focusRemaining.takeIf { it > 0 } ?: 25 * 60_000L
        mutable.update { if (state.focusUntil > 0) it.copy(focusUntil = 0, focusRemaining = (state.focusUntil - System.currentTimeMillis()).coerceAtLeast(0))
            else it.copy(focusUntil = System.currentTimeMillis() + duration, focusRemaining = duration, now = System.currentTimeMillis()) }
        persistFocus()
    }
    fun resetFocus() { mutable.update { it.copy(focusUntil = 0, focusRemaining = 25 * 60_000L) }; persistFocus() }
    private fun persist(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: Exception) { if (e is CancellationException) throw e; message("Couldn’t save changes. Please retry.") } } }
    fun message(message: String) { toastJob?.cancel(); mutable.update { it.copy(message = message) }; toastJob = viewModelScope.launch { delay(1800); mutable.update { it.copy(message = null) } } }
    override fun onCleared() { device.close() }
}
