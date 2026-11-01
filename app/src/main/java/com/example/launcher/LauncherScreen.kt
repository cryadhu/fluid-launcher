@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.launcher.data.*
import com.example.launcher.ui.*
import com.example.launcher.ui.theme.Tokens as T
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val names = listOf("Feed", "Home", "Today", "Tools")
private val accents get() = listOf(T.mint, T.coral, T.sky, T.amber)
private fun date(now: Long, pattern: String) = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

@Composable fun LauncherScreen(model: LauncherViewModel, chooseHome: () -> Unit, openHomeSettings: () -> Unit = chooseHome) {
    val state by model.uiState.collectAsStateWithLifecycle()
    if (!state.settingsLoaded) { Box(Modifier.fillMaxSize().background(T.ground)); return }
    // A HOME intent reveals the base page directly; it is not a swipe between launcher pages.
    val leagueHero = remember { LeagueHeroState() }
    val pages = visiblePages(state.customization)
    val names = pages.map { it.title }
    val currentPages by rememberUpdatedState(pages)
    val pager = key(state.homeReturnEpoch) { rememberPagerState(initialPage = pages.indexOfFirst { it.id == state.page }.coerceAtLeast(0), pageCount = { pages.size }) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val reduced = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val drag = remember { LauncherDragState() }
    val drawerMotion = remember(state.homeReturnEpoch) { DrawerMotion() }
    val density = LocalDensity.current
    val context = LocalContext.current
    var drawerMenu by remember { mutableStateOf<AppHit?>(null) }
    val dragSlop = LocalViewConfiguration.current.touchSlop
    LaunchedEffect(state.drawer) { if (!state.drawer) drawerMenu = null }
    var indicatorVisible by remember { mutableStateOf(true) }
    var contentScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(pager.isScrollInProgress, contentScrolling, state.page, state.drawer, state.newsSettingsOpen) {
        indicatorVisible = true
        if (!pager.isScrollInProgress && !contentScrolling) { delay(1000); indicatorVisible = false }
    }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = LocalFocusManager.current
    fun showDrawerMenu(hit: AppHit?) {
        if (hit != null) { model.dismissDrawerSearchFocus(); searchFocus.clearFocus(force = true); keyboard?.hide() }
        drawerMenu = hit
    }
    LaunchedEffect(drawerMenu) { if (drawerMenu != null) { searchFocus.clearFocus(force = true); keyboard?.hide() } }
    val activeHomeId = pages.find { it.id == state.page && it.isHome }?.id ?: 1
    val homeOrder = remember(state.apps, state.pins, state.homeLayout, state.customization.layouts, activeHomeId) {
        visibleHomeEntries(state, activeHomeId)
    }
    drag.blockedSlots = homeBlockedSlots(state.customization, activeHomeId)
    drag.dockCapacity = state.customization.preferences.dockLimit
    if (drag.dockCapacity == 0) drag.dockSlots.clear()
    val openedFolder = homeOrder.firstOrNull { it.isFolder && it.id == state.openFolderId }
    var folderDragSource by remember { mutableStateOf<String?>(null) }
    val homeBlur by animateDpAsState(if (openedFolder != null && folderDragSource == null) 18.dp else 0.dp, tween(if (reduced) 0 else 200), label = "Folder background blur")
    val currentOrder by rememberUpdatedState(homeOrder)
    val currentState by rememberUpdatedState(state)
    val dockOrder = remember(state.apps, state.dockComponents) { dockItems(context, state.apps, state.dockComponents) }
    val currentDock by rememberUpdatedState(dockOrder)
    val displayedDock = remember(state.apps, drag.app != null, drag.dockPreview, dockOrder) {
        if (drag.app != null) dockItems(context, state.apps, drag.dockPreview) else dockOrder
    }
    val drawerTravel = screenSize.height.toFloat().coerceAtLeast(1f)
    drawerMotion.height = drawerTravel
    fun openSettings() { model.action { model.device.open(Intent(Settings.ACTION_SETTINGS)) } }
    val drawerFraction by key(state.homeReturnEpoch) { animateFloatAsState(
        if (drawerMotion.dragging) drawerMotion.fraction else if (state.drawer) 1f else 0f,
        animationSpec = if (drawerMotion.dragging || reduced) snap() else tween(300, easing = FastOutSlowInEasing), label = "Drawer position"
    ) }
    drawerMotion.renderedFraction = drawerFraction
    val dropPosition = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val removalScale by animateFloatAsState(if (drag.settling && drag.overBin) 0f else 1f, tween(if (reduced) 0 else 180), label = "Remove shortcut")
    LaunchedEffect(drag.overBin) { if (drag.overBin) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    fun cancelAppDrag() { settleJob?.cancel(); if (drag.fromDrawer && drag.app != null) { model.drawer(true); model.query(drag.query) }; drag.reset(); folderDragSource = null }
    fun removeDraggedShortcut() {
        if (drag.fromDockIndex >= 0) model.saveDockOrder(drag.dockOriginal.filterIndexed { index, _ -> index != drag.fromDockIndex })
        else model.saveHomeLayout(drag.original.filterNot { it.id == drag.item?.id })
    }
    fun endAppDrag() {
        if (drag.app == null) return
        settleJob = scope.launch {
            drag.settling = true
            val accepted = drag.hover >= 0 || drag.hoverDock >= 0
            val destination = if (drag.overBin) drag.binRect?.center ?: drag.position else if (drag.hoverDock >= 0) drag.dockSlots[drag.hoverDock]?.center ?: drag.position else if (drag.hover >= 0) drag.slots[drag.hover]?.center ?: drag.position else drag.source
            dropPosition.snapTo(drag.position)
            if (!reduced) dropPosition.animateTo(destination, tween(220, easing = FastOutSlowInEasing))
            if (drag.overBin) removeDraggedShortcut()
            else if (drag.hoverDock >= 0) {
                if (drag.fromDockIndex >= 0 || drag.app?.component?.flattenToString() in drag.dockPreview) {
                    model.saveDockOrder(drag.dockPreview)
                    if (folderDragSource != null) model.saveHomeLayout(drag.original)
                    else if (drag.fromDockIndex < 0 && !drag.fromDrawer) model.saveHomeLayout(drag.original.filterNot { it.id == drag.item?.id })
                } else model.message("Dock can hold up to ${currentState.customization.preferences.dockLimit} apps")
            }
            else if (accepted && drag.app?.component?.packageName == "com.example.launcher.dock") model.message("App unavailable")
            else if (accepted) {
                val result = if (drag.folderTarget >= 0) mergeHomeEntry(drag.original, drag.item ?: return@launch, drag.folderTarget) else drag.preview
                if (result.any { it.id == drag.item?.id || drag.item?.id in it.components }) {
                    model.saveHomeLayout(result)
                    if (drag.fromDockIndex >= 0) model.saveDockOrder(drag.dockOriginal.filterIndexed { index, _ -> index != drag.fromDockIndex })
                }
                else model.message("Not enough Home space")
            }
            else if (drag.fromDrawer) { model.drawer(true); model.query(drag.query) }
            drag.reset()
            folderDragSource = null
        }
    }
    suspend fun move(page: Int) { val index = currentPages.indexOfFirst { it.id == page }.coerceAtLeast(0); if (reduced) pager.scrollToPage(index) else pager.animateScrollToPage(index, animationSpec = tween(350)) }
    LaunchedEffect(state.page) { if (pager.currentPage != state.page) move(state.page) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { index -> currentPages.getOrNull(index)?.let { model.page(it.id); view.announceForAccessibility("${it.title} page, ${index + 1} of ${currentPages.size}") } } }
    LaunchedEffect(state.page) {
        if (state.page == 0) while (isActive) { model.refreshFeed(); delay(15 * 60_000L) }
    }
    BackHandler { if (state.homePanel != null) model.showHomePanel(null) else if (state.selectedWidget != null) model.selectWidget(null) else if (drawerMenu != null) drawerMenu = null else if (state.openFolderId != null) model.openFolder(null) else if (state.newsSettingsOpen) model.showNewsSettings(false) else if (drag.app != null) cancelAppDrag() else if (state.drawer || drawerMotion.dragging) { drawerMotion.cancel(); model.drawer(false) } else { model.page(1); scope.launch { move(1) } } }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshDevice() }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) model.toggle("flashlight") else model.message("Camera permission is needed for the flashlight") }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshDevice() }
    val wifiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) model.scanWifi() else model.message("Precise location access is required to list networks")
        model.refreshDevice()
    }
    LaunchedEffect(state.connectivityPopup) { if (state.connectivityPopup == ConnectivityType.WIFI && state.device.wifiScanGranted && state.device.locationEnabled) model.scanWifi() }
    val bluetoothScanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) model.scanBluetooth() else model.message("Device access is needed to find Bluetooth devices")
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refreshDevice() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { model.draft(listOf(state.noteDraft, it).filter { it.isNotBlank() }.joinToString(" ")) }
    }
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri -> model.action {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) model.favourite(cursor.getString(0) ?: "Contact", cursor.getString(1) ?: "")
            }
        } }
    }
    Box(Modifier.fillMaxSize().blur(homeBlur).background(homeWallpaper(state.customization.wallpaper)).onGloballyPositioned { screenSize = it.size }.pointerInput(state.customization.preferences.doubleTapLock) {
        if (!currentState.customization.preferences.doubleTapLock) return@pointerInput
        var lastTap = 0L; var lastPoint = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var moved = false; var endedAt = down.uptimeMillis
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                moved = moved || (change.position - down.position).getDistance() > dragSlop
                endedAt = change.uptimeMillis
                val pressed = change.pressed
            } while (pressed)
            val home = currentState.customization.pages.any { it.id == currentState.page && it.isHome }
            val empty = drag.hits.values.none { !it.drawer && it.rect.contains(down.position) } && drag.widgetHits.values.none { it.contains(down.position) }
            if (home && empty && !currentState.drawer && !moved && endedAt - down.uptimeMillis < 250) {
                if (lastTap > 0 && endedAt - lastTap < 350 && (down.position - lastPoint).getDistance() < dragSlop * 2) { model.lockPhone(); lastTap = 0 }
                else { lastTap = endedAt; lastPoint = down.position }
            } else lastTap = 0
        }
    }.pointerInput(pager) {
        var pending: AppHit? = null
        var origin = Offset.Zero
        var movement = Offset.Zero
        fun beginDrag(hit: AppHit, pointer: Offset) {
            drawerMenu = null
            settleJob?.cancel()
            drag.start(hit, pointer, currentOrder, currentState.query, currentDock)
            keyboard?.hide()
            if (hit.drawer) { val id = currentState.customization.pages.find { it.id == currentState.page && it.isHome }?.id ?: 1; model.page(id); scope.launch { pager.scrollToPage(currentPages.indexOfFirst { it.id == id }) }; model.drawer(false) }
        }
        detectLauncherLongPress(
            onDragStart = { pointer ->
                val hit = drag.hits.values.lastOrNull { it.drawer == currentState.drawer && it.rect.contains(pointer) }
                val gridPage = currentState.customization.pages.any { it.id == currentState.page && it.isHome }
                val widget = drag.widgetHits.entries.lastOrNull { it.value.contains(pointer) }
                if (!currentState.drawer && gridPage && widget != null) { model.selectWidget(widget.key) }
                else if (hit != null && !drag.settling && (currentState.drawer || gridPage)) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    if (hit.drawer) {
                        pending = hit; origin = pointer; movement = Offset.Zero
                        showDrawerMenu(hit)
                    } else if (currentState.customization.locked) model.message("Home layout is locked") else beginDrag(hit, pointer)
                } else if (!currentState.drawer && gridPage) model.showHomePanel(HomePanel.CUSTOMIZE)
            },
            onDrag = { change, amount ->
                val held = pending
                if (held != null) {
                    movement += amount
                    if (movement.getDistance() > dragSlop) {
                        pending = null
                        if (currentState.customization.locked) { model.message("Home layout is locked") }
                        else { beginDrag(held, origin); drag.move(movement) }
                    }
                    change.consume()
                } else if (drag.app != null && !drag.settling) { change.consume(); drag.move(amount) }
            },
            onDragEnd = { pending = null; endAppDrag() }, onDragCancel = { pending = null; cancelAppDrag() }
        )
    }) {
        if (state.customization.wallpaper.startsWith("Photo:")) PhotoBackground(state.customization.wallpaper)
        if (!state.customization.wallpaper.startsWith("Photo:")) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 100.dp, y = (-80).dp).size(300.dp).background(T.decorA, CircleShape))
            Box(Modifier.offset(x = (-70).dp, y = 400.dp).size(200.dp).background(T.decorB, CircleShape))
        }
        HorizontalPager(pager, Modifier.fillMaxSize().then(if (state.drawer) Modifier.clearAndSetSemantics {} else Modifier.semantics {
            customActions = names.mapIndexed { index, name -> CustomAccessibilityAction("Show $name page") { model.page(pages[index].id); true } }
        }), userScrollEnabled = drag.app == null && !drawerMotion.dragging) { pageIndex ->
            val page = pages[pageIndex].id
            val pageScroll = rememberScrollState()
            LaunchedEffect(pageScroll, pager.currentPage) {
                if (pager.currentPage == pageIndex) snapshotFlow { pageScroll.isScrollInProgress }.collect { contentScrolling = it }
            }
            val padding = Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).padding(horizontal = if (page == 1) 24.dp else 20.dp).testTag("page-$page-content")
            when (page) {
                1 -> HomePage(state, model, chooseHome, openHomeSettings, ::openSettings, padding, drag, drawerMotion, if (drag.app != null && activeHomeId == page) drag.preview else visibleHomeEntries(state, page), displayedDock, pageScroll, page)
                0 -> FeedPage(state, model, padding, pageScroll, leagueHero)
                2 -> TodayPage(state, model, padding, pageScroll,
                    calendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                    torch = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) model.toggle("flashlight") else cameraPermission.launch(Manifest.permission.CAMERA) },
                    bluetooth = { model.showConnectivity(ConnectivityType.BLUETOOTH) })
                3 -> ToolsPage(state, model, padding, pageScroll,
                    pickContact = { model.action { contact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } },
                    voice = { model.action { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "Add to your quick note")) } })
                else -> HomePage(state, model, chooseHome, openHomeSettings, ::openSettings, padding, drag, drawerMotion, if (drag.app != null && activeHomeId == page) drag.preview else visibleHomeEntries(state, page), displayedDock, pageScroll, page)
            }
        }
        AnimatedVisibility(indicatorVisible && drag.app == null && !state.drawer && !state.newsSettingsOpen && openedFolder == null && state.homePanel == null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            enter = fadeIn(tween(if (reduced) 0 else 100)), exit = fadeOut(tween(if (reduced) 0 else 120))) {
            Row(Modifier.height(16.dp).testTag("page-indicator").semantics { contentDescription = "${names[pager.currentPage]} page, ${pager.currentPage + 1} of ${pages.size}" }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            names.forEachIndexed { index, _ ->
                val active = pager.currentPage == index
                val width by animateDpAsState(if (active) 12.dp else 4.dp, tween(if (reduced) 0 else 180), label = "Page dot")
                Box(Modifier.height(4.dp).width(width).background(if (active) T.amber else T.dotInactive, CircleShape))
            }
            }
        }
        if (state.drawer || drawerFraction > 0.001f) {
            Box(Modifier.fillMaxSize().offset { IntOffset(0, ((1f - drawerFraction) * drawerTravel).roundToInt()) }.testTag("drawer-surface")) { AppDrawer(state, model, drag, drawerMotion, drawerMenu != null) { app, namespace ->
                val key = "drawer${if (namespace.isEmpty()) "" else ":$namespace"}:${app.component.flattenToString()}"
                showDrawerMenu(drag.hits[key])
            } }
        }
        drawerMenu?.takeIf { state.drawer && drag.app == null }?.let { hit ->
            DrawerAppMenu(hit, { drawerMenu = null }, onHome = homeOrder.any { it.id == hit.app.component.flattenToString() },
                pin = { model.pin(hit.app); drawerMenu = null },
                favorite = hit.app.component.flattenToString() in state.favoriteApps,
                toggleFavorite = { model.toggleFavoriteApp(hit.app); drawerMenu = null },
                appInfo = { drawerMenu = null; model.action { model.device.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${hit.app.component.packageName}"))) } },
                uninstall = { drawerMenu = null; model.action { model.device.open(Intent(Intent.ACTION_DELETE, Uri.parse("package:${hit.app.component.packageName}"))) } })
        }
        AnimatedVisibility(drag.app != null && drag.canRemoveHome,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 116.dp),
            enter = slideInVertically(tween(if (reduced) 0 else 200)) { it } + fadeIn(),
            exit = slideOutVertically(tween(if (reduced) 0 else 160)) { it } + fadeOut()) {
            val color by animateColorAsState(if (drag.overBin) T.coral else T.surfaceRaised, tween(100), label = "Bin hover")
            val scale by animateFloatAsState(if (drag.overBin) 1.1f else 1f, spring(stiffness = 600f), label = "Bin highlight")
            Box(Modifier.size(64.dp).testTag("home-remove-bin").onGloballyPositioned {
                drag.binRect = it.boundsInRoot()
                if (drag.app != null && !drag.settling) drag.move(Offset.Zero)
            }.semantics { contentDescription = if (drag.fromDockIndex >= 0) "Remove from dock" else "Remove from Home"; stateDescription = if (drag.overBin) "Release to remove" else "Drop shortcut here" }
                .clickable(enabled = drag.app != null && drag.canRemoveHome && !drag.settling) { removeDraggedShortcut(); drag.reset() }, contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.background(color, T.appTile).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Glyph("bin", if (drag.overBin) T.onAccent else T.coral, Modifier.size(26.dp))
                    Text("Remove", style = T.caption, color = if (drag.overBin) T.onAccent else T.text)
                }
            }
        }
        drag.app?.let { app ->
            val pointer = if (drag.settling) dropPosition.value else drag.position
            val half = with(density) { 32.dp.toPx() }
            Box(Modifier.offset { IntOffset((pointer.x - half).roundToInt(), (pointer.y - half).roundToInt()) }.size(64.dp).graphicsLayer { scaleX = 1.12f * removalScale; scaleY = 1.12f * removalScale; alpha = removalScale; shadowElevation = with(density) { 12.dp.toPx() }; shape = T.appTile; clip = true }.background(T.surfaceRaised).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                if (drag.item?.isFolder == true) FolderPreview(drag.item!!, state.apps, Modifier.size(58.dp)) else AppArtwork(app, Modifier.size(30.dp))
            }
            Text(if (drag.overBin) { if (drag.fromDockIndex >= 0) "Release to remove from dock" else "Release to remove from Home" } else if (drag.folderTarget >= 0) "Drop to create or add to folder" else if (drag.fromDrawer) "Drop on Home or the dock" else "Drag to rearrange", style = T.caption, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp).background(T.surface, T.searchPill).padding(12.dp))
        }
        AnimatedVisibility(state.newsSettingsOpen, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            NewsSettingsScreen(state.selectedNews, model::selectNews, { model.showNewsSettings(false) })
        }
        state.message?.let { message ->
            Text(message, style = T.body.copy(color = T.onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp).clip(T.appTile).background(T.text).padding(horizontal = 18.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    openedFolder?.let { folder -> FolderOverlay(folder, state.apps, model,
        onDragStart = { app, screenPoint ->
            if (currentState.customization.locked) { model.message("Home layout is locked"); false } else {
                settleJob?.cancel()
                val location = IntArray(2); view.getLocationOnScreen(location)
                val point = screenPoint - Offset(location[0].toFloat(), location[1].toFloat())
                val half = with(density) { 29.dp.toPx() }
                val base = removeFolderMember(currentOrder, folder.id, app.component.flattenToString())
                drag.start(AppHit(app, Rect(point - Offset(half, half), point + Offset(half, half)), false), point, base, "", currentDock)
                folderDragSource = folder.id
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }, onDrag = { drag.move(it) }, onDragEnd = { model.openFolder(null); endAppDrag() }, onDragCancel = { cancelAppDrag() }) }
    LeagueTableHero(state, leagueHero, model::refreshLeague) {
        model.action { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://www.espn.com/soccer/standings/_/league/eng.1")) }
    }
    HomeCustomizationUi(state, model)
    state.connectivityPopup?.let { type ->
        ConnectivityPopup(type, state.device, { model.showConnectivity(null) },
            onMore = { model.action { model.device.open(Intent(if (type == ConnectivityType.WIFI) Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_BLUETOOTH_SETTINGS)) } },
            onWifiControls = { model.action { model.device.open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)) } },
            onBluetoothPermission = { if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) else model.refreshDevice() },
            onEnableBluetooth = { model.action { enableBluetooth.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) } },
            onScanBluetooth = {
                val required = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) model.scanBluetooth() else bluetoothScanPermission.launch(required)
            },
            onPairBluetooth = model::pairBluetooth,
            onWifiPermission = { wifiPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
            onScanWifi = model::scanWifi,
            onLocationSettings = { model.action { model.device.open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
            onConnectWifi = { network, password -> model.action {
                if (Build.VERSION.SDK_INT >= 30 && !network.capabilities.contains("EAP") && !network.capabilities.contains("WEP")) {
                    val builder = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(network.ssid)
                    if (network.capabilities.contains("PSK")) builder.setWpa2Passphrase(password)
                    else if (network.capabilities.contains("SAE")) builder.setWpa3Passphrase(password)
                    else if (network.capabilities.contains("OWE")) builder.setIsEnhancedOpen(true)
                    model.device.open(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(builder.build())))
                } else model.device.open(Intent(Settings.ACTION_WIFI_SETTINGS))
            } })
    }
}

@Composable private fun HomePage(state: LauncherUiState, model: LauncherViewModel, chooseHome: () -> Unit, openHomeSettings: () -> Unit, settings: () -> Unit, modifier: Modifier, drag: LauncherDragState, motion: DrawerMotion, homeApps: List<HomeEntry>, dock: List<DockItem>, scroll: ScrollState, pageId: Int) {
    val density = LocalDensity.current
    val preferences = state.customization.preferences
    val swipeEnabled by rememberUpdatedState(preferences.swipeUp)
    val rows = preferences.gridRows
    val threshold = with(density) { 48.dp.toPx() }
    val swipe = remember(drag, motion, model) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (swipeEnabled && drag.app == null && source == NestedScrollSource.UserInput && (motion.dragging || available.y < 0f)) {
                motion.drag(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!motion.dragging || !swipeEnabled) return Velocity.Zero
            motion.finish(available.y, threshold) { model.drawer(it) }
            return available
        }
    } }
    BoxWithConstraints(modifier.nestedScroll(swipe)) {
        val minHeight = maxHeight
        val compact = maxHeight < 760.dp
        Column(Modifier.verticalScroll(scroll, enabled = drag.app == null).heightIn(min = minHeight).padding(bottom = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            if (pageId == 1) Column {
                if (preferences.clockStyle != "Hidden") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(date(state.now, "HH:mm"), style = if (preferences.clockStyle == "Small") T.clock.copy(fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-1).sp) else if (compact) T.clock.copy(fontSize = 80.sp, lineHeight = 80.sp) else T.clock, maxLines = 1,
                        modifier = Modifier.weight(1f).testTag("home-clock").clickable(role = Role.Button) { model.action { model.device.open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) } }.semantics { contentDescription = "Open clock" })
                }
                if (preferences.clockStyle != "Hidden") Text(date(state.now, "EEEE, d MMMM"), style = T.homeDate, modifier = Modifier.padding(top = 12.dp))
                if (preferences.searchBar == "Top") HomeSearch(model)
                if (!state.device.defaultHome) {
                    Spacer(Modifier.height(12.dp))
                    ActionPill("Set as default launcher", chooseHome, Modifier.fillMaxWidth(), primary = true)
                    TextButton(onClick = openHomeSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Home app settings", color = T.amber) }
                }
            } else if (preferences.searchBar == "Top") HomeSearch(model) else Spacer(Modifier.height(48.dp))
            Column(Modifier.padding(top = if (compact) 12.dp else 20.dp)) {
                if (state.appsLoading) Text("Loading your apps…", style = T.subtitle)
                val cellHeight = 78.dp + 18.dp * density.fontScale.coerceAtLeast(1f)
                val gap = if (compact) 4.dp else 12.dp
                BoxWithConstraints(Modifier.fillMaxWidth().height(cellHeight * rows + gap * (rows - 1)).testTag("home-grid")) {
                    val cellWidth = maxWidth / HOME_COLUMNS
                    repeat(HOME_COLUMNS * rows) { index ->
                        Box(Modifier.offset(x = cellWidth * (index % HOME_COLUMNS), y = (cellHeight + gap) * (index / HOME_COLUMNS)).width(cellWidth).height(cellHeight).testTag("home-slot-$index")
                            .onGloballyPositioned { if (state.page == pageId) drag.slots[index] = it.boundsInRoot() }
                            .background(if (drag.app != null && drag.hover == index) T.amber.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent, T.appTile))
                    }
                    homeApps.forEach { entry -> key(entry.id) {
                        val index = entry.slot
                        val position by animateIntOffsetAsState(IntOffset(with(density) { (cellWidth * (index % HOME_COLUMNS)).roundToPx() }, with(density) { ((cellHeight + gap) * (index / HOME_COLUMNS)).roundToPx() }), spring(stiffness = 550f), label = "Home item position")
                        val cell = Modifier.offset { position }.width(cellWidth).background(if (drag.folderTarget == index) T.mint.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent, T.appTile)
                        val actions = listOf(
                            CustomAccessibilityAction("Move earlier") { model.saveHomeLayout(moveHomeEntry(homeApps, entry, (index - 1).coerceAtLeast(0))); true },
                            CustomAccessibilityAction("Move later") { model.saveHomeLayout(moveHomeEntry(homeApps, entry, (index + 1).coerceAtMost(HOME_CAPACITY - 1))); true }
                        )
                        if (entry.isFolder) FolderCell(entry, state.apps, drag, { model.openFolder(entry.id) }, { model.ungroupFolder(entry.id) }, cell, actions)
                        else state.apps.firstOrNull { it.component.flattenToString() == entry.id }?.let { app ->
                            DragAppCell(app, drag, false, { model.launch(app) }, { model.pin(app) }, cell, extraActions = actions, homePageId = pageId)
                        }
                    } }
                    state.customization.widgets.filter { it.pageId == pageId }.forEach { widget -> key(widget.id) {
                        DisposableEffect(widget.id) { onDispose { drag.widgetHits.remove(widget.id) } }
                        Box(Modifier.offset(x = cellWidth * (widget.slot % HOME_COLUMNS), y = (cellHeight + gap) * (widget.slot / HOME_COLUMNS))
                            .width(cellWidth * widget.kind.columns).height(cellHeight * widget.kind.rows + gap * (widget.kind.rows - 1)).padding(horizontal = 4.dp)
                            .onGloballyPositioned { if (state.page == pageId) drag.widgetHits[widget.id] = it.boundsInRoot() else drag.widgetHits.remove(widget.id) }
                            .testTag("home-widget-${widget.kind.name}").semantics { contentDescription = "${widget.kind.title} widget"; customActions = listOf(CustomAccessibilityAction("Widget options") { model.selectWidget(widget.id); true }) }
                            .clip(T.widgetCard).background(T.surface)) { HomeWidgetContent(widget.kind, state, model) }
                    } }

                }
                if (preferences.searchBar == "Bottom") HomeSearch(model)
                if (preferences.dockLimit > 0) DockBar(dock, drag, model, pageId, state.page == pageId)
            }
        }
    }
}

@Composable private fun HomeSearch(model: LauncherViewModel) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp).clip(T.searchPill).background(T.surface).clickable { model.drawer(true, focus = true) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Glyph("search", T.muted, Modifier.size(18.dp)); Text("Search apps", style = T.caption)
    }
}

@Composable private fun Modifier.drawerHandle(motion: DrawerMotion, model: LauncherViewModel): Modifier {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return draggable(rememberDraggableState { motion.drag(it) }, Orientation.Vertical, onDragStarted = { motion.begin() }, onDragStopped = { motion.finish(it, threshold) { open -> model.drawer(open) } })
}

@Composable private fun AppDrawer(state: LauncherUiState, model: LauncherViewModel, drag: LauncherDragState, motion: DrawerMotion, menuOpen: Boolean, appOptions: (InstalledApp, String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val grid = rememberLazyGridState()
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val nestedScroll = remember(motion, grid) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (drag.app == null && source == NestedScrollSource.UserInput && (motion.dragging || available.y > 0f && grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0)) {
                motion.drag(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!motion.dragging) return Velocity.Zero
            motion.finish(available.y, threshold) { model.drawer(it) }
            return available
        }
    } }
    val apps = remember(state.apps, state.query) { state.apps.filter { filterAppsLabel(it.label, state.query) } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var rotation by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (isActive) { delay(1000); now = System.currentTimeMillis() } }
    val candidates = remember(state.apps, now) { recentInstallCandidates(state.apps, now) }
    val paused by rememberUpdatedState(menuOpen || drag.app != null)
    LaunchedEffect(candidates.map { it.component }, state.query.isBlank()) {
        rotation = 0
        while (isActive && candidates.size > 4) { delay(5000); if (!paused) rotation = (rotation + 1) % candidates.size }
    }
    val recent = recentInstallWindow(candidates, rotation)
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    Column(Modifier.fillMaxSize().background(T.ground).nestedScroll(nestedScroll).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 8.dp)) {
        LauncherSearch(state.query, model::query, true, state.focusSearch, {}, Modifier.fillMaxWidth().testTag("launcher-search").drawerHandle(motion, model))
        if (state.query.isBlank()) RecentAppsRow(recent, drag, { model.launch(it) }, { appOptions(it, "recent") })
        if (state.query.isBlank()) FavoriteAppsRow(state.apps.filter { it.component.flattenToString() in state.favoriteApps }, drag, { model.launch(it) }, { appOptions(it, "favorite") })
        Text("ALL APPS · ${apps.size}", style = T.drawerLabel, modifier = Modifier.fillMaxWidth().drawerHandle(motion, model).padding(top = 28.dp, bottom = 12.dp))
        if (state.query.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Web", { model.action { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://www.google.com/search?q=" + Uri.encode(state.query))) } }, Modifier.weight(1f))
            ActionPill("Contacts", { model.action { model.device.open(Intent(Intent.ACTION_SEARCH).setType(ContactsContract.Contacts.CONTENT_TYPE).putExtra(android.app.SearchManager.QUERY, state.query)) } }, Modifier.weight(1f))
        }
        Text("Hold for options · drag to add to Home", style = T.caption, modifier = Modifier.padding(vertical = 12.dp))
        if (apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (state.appsLoading) "Loading apps…" else "No apps match your search", style = T.subtitle) }
        else LazyVerticalGrid(columns = GridCells.Fixed(4), state = grid, userScrollEnabled = drag.app == null, modifier = Modifier.weight(1f).testTag("all-apps-grid"), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(apps, key = { it.component.flattenToString() }) { app -> DragAppCell(app, drag, true, { model.launch(app) }, { appOptions(app, "") }) }
        }

    }
}

@Composable private fun FeedPage(state: LauncherUiState, model: LauncherViewModel, modifier: Modifier, scroll: ScrollState, leagueHero: LeagueHeroState) {
    val context = LocalContext.current
    Column(modifier.verticalScroll(scroll).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("For you", "A wider world, at your pace")
        PremierLeagueCard(state, leagueHero)
        ArsenalFixturesCard(state, model)
        CardBlock {
            Label("EXCHANGE RATES")
            if (state.feed.rates.isEmpty()) Text(if (state.feedLoading) "Loading daily rates…" else "Rates unavailable", style = T.subtitle)
            state.feed.rates.forEach { rate ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(rate.pair, style = T.body, fontWeight = FontWeight.Bold); Text("1 USD · ${rate.date}", style = T.caption) }
                    Text(String.format(Locale.getDefault(), "%.4f", rate.value), style = T.body, fontWeight = FontWeight.Bold, color = T.mint)
                }
            }
            Text("Daily reference rates · Frankfurter", style = T.caption)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label("TOP STORIES")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = model::requestFeedRefresh, enabled = !state.feedLoading) { Text(if (state.feedLoading) "Updating…" else "Refresh", color = T.amber) }
                IconAction("settings", "News settings", onClick = { model.showNewsSettings(true) })
            }
        }
        state.feed.error?.let { CardBlock { Text(it, style = T.subtitle); if (state.feed.stories.isNotEmpty() || state.feed.rates.isNotEmpty()) Text("Showing saved data where updates failed.", style = T.caption) } }
        val stories = selectedTopStories(state.feed.stories, state.selectedNews)
        if (state.selectedNews.isEmpty()) CardBlock { Text("Select topics in News settings to see stories.", style = T.body) }
        else if (stories.isEmpty()) CardBlock { Text(if (state.feedLoading) "Finding the latest stories…" else "No stories yet. Connect to the internet and tap Refresh.", style = T.body) }
        stories.forEach { story ->
            Row(Modifier.fillMaxWidth().clip(T.storyCard).background(T.surface).clickable(role = Role.Button) { model.action { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(story.url)) } }.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(T.appTile).background(T.surfaceRaised), contentAlignment = Alignment.Center) { Glyph(NewsCategory.fromKey(story.category)?.glyph ?: "news", if (story.category == "TECH") T.sky else T.coral, Modifier.size(28.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(NewsCategory.fromKey(story.category)?.label?.uppercase() ?: story.category, style = T.sectionLabel, color = if (story.category == "TECH") T.sky else T.coral)
                    Text(story.title, style = T.body, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("${story.source} · ${story.published}", style = T.caption)
                }
            }
        }
    }
}

@Composable private fun TodayPage(state: LauncherUiState, model: LauncherViewModel, modifier: Modifier, scroll: ScrollState, calendar: () -> Unit, torch: () -> Unit, bluetooth: () -> Unit) {
    val device = state.device
    Column(modifier.verticalScroll(scroll).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("Today", date(state.now, "EEEE, d MMMM"))
        CardBlock {
            Label("UP NEXT")
            if (!device.calendarGranted) { Text("See today’s events from your device calendar. cry needs calendar access to read your agenda.", style = T.subtitle); ActionPill("Connect calendar", calendar) }
            else if (device.calendarError) Text("Calendar unavailable. Try again after returning to Home.", style = T.subtitle)
            else if (device.events.isEmpty()) Text("Nothing else today", style = T.subtitle)
            device.events.forEachIndexed { index, event -> Row(Modifier.heightIn(min = 52.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(38.dp).background(accents[index % 3], CircleShape))
                Column { Text(event.title, style = T.body, fontWeight = FontWeight.Bold); Text(event.times, style = T.caption) }
            } }
        }
        listOf(listOf("wifi", "bluetooth"), listOf("mobile", "silent")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { name ->
                    val on = when (name) { "wifi" -> device.wifi; "bluetooth" -> device.bluetooth == true; "mobile" -> device.mobileData == true; else -> device.soundMode != SoundMode.NORMAL }
                    val title = when (name) { "wifi" -> "Wi-Fi"; "bluetooth" -> "Bluetooth"; "mobile" -> "Mobile data"; else -> device.soundMode.label }
                    val status = when { name == "bluetooth" && device.bluetooth == null -> "Check access"; name == "mobile" && !device.mobileAvailable -> "Unavailable"; name == "mobile" && device.mobileData == null -> "System controls"; name == "silent" -> "Tap to change"; on -> "On"; else -> "Off" }
                    Row(Modifier.weight(1f).heightIn(min = 72.dp).clip(T.toggle).background(if (on) T.amber else T.surfaceRaised).clickable(role = Role.Switch) { when (name) { "mobile" -> if (device.mobileAvailable) model.toggle("mobile") else model.message("Mobile data is unavailable on this device"); "bluetooth" -> bluetooth(); "wifi" -> model.showConnectivity(ConnectivityType.WIFI); else -> model.toggle(name) } }.semantics { stateDescription = status; toggleableState = if (on) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(if (name == "silent" && device.soundMode == SoundMode.VIBRATE) "vibrate" else name, if (on) T.onAccent else T.text, Modifier.size(22.dp))
                        Column { Text(title, style = T.body, color = if (on) T.onAccent else T.text); Text(status, style = T.caption, color = if (on) T.onAccent else T.muted) }
                    }
                }
            }
        }
        QuickActions(model, torch)
        CardBlock {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(T.appTile).background(T.surfaceRaised), contentAlignment = Alignment.Center) { Glyph("music", T.mint) }
                Column(Modifier.weight(1f)) { Text(device.mediaTitle ?: "Nothing playing", style = T.body, fontWeight = FontWeight.Bold); Text(if (!device.mediaAccess) "Connect your music" else if (device.playing) "Now playing" else "Paused", style = T.caption) }
            }
            if (!device.mediaAccess) MediaAccessButton(model)
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconAction("previous", "Previous track", enabled = device.mediaTitle != null) { model.action { model.device.transport("previous") } }
                IconAction(if (device.playing) "pause" else "play", if (device.playing) "Pause" else "Play", accent = true, enabled = device.mediaTitle != null) { model.action { model.device.transport("play") } }
                IconAction("next", "Next track", enabled = device.mediaTitle != null) { model.action { model.device.transport("next") } }
            }
        }
    }
}

@Composable private fun ToolsPage(state: LauncherUiState, model: LauncherViewModel, modifier: Modifier, scroll: ScrollState, pickContact: () -> Unit, voice: () -> Unit) {
    Column(modifier.verticalScroll(scroll).imePadding().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("Tools", "Tasks, people and quick notes")
        CardBlock {
            Label("TASKS")
            if (state.tasks.isEmpty()) Text("A little room for what matters.", style = T.subtitle)
            state.tasks.forEach { task ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Checkbox) { model.toggleTask(task) }.semantics { toggleableState = if (task.done) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(task.done, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = T.amber, checkmarkColor = T.onAccent, uncheckedColor = T.muted))
                        Text(task.text, style = T.body, color = if (task.done) T.muted else T.text, textDecoration = if (task.done) TextDecoration.LineThrough else null, modifier = Modifier.padding(start = 12.dp))
                    }
                    IconAction("close", "Delete task ${task.text}") { model.deleteTask(task) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { DraftField(state.taskDraft, "Add a task", model::taskDraft, Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); IconAction("plus", "Add task", accent = true) { model.addTask() } }
        }
        CardBlock {
            Label("FAVOURITES")
            if (state.favourites.isEmpty()) Text("Keep your people close. Pick a contact to add them here.", style = T.subtitle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                state.favourites.forEachIndexed { index, person ->
                    Column(Modifier.weight(1f).combinedClickable(onClick = { model.action { model.device.dial(person.phone) } }, onLongClick = { model.removeFavourite(person) }, onLongClickLabel = "Remove favourite").semantics { contentDescription = "Dial ${person.name}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp).background(accents[index % 4], CircleShape), contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase(), style = T.body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = T.onAccent) }
                        Text(person.name.substringBefore(" "), style = T.caption, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (state.favourites.size < 4) ActionPill("Add a contact", pickContact)
        }
        CardBlock {
            Label("QUICK NOTE")
            DraftField(state.noteDraft, "Jot something down…", model::draft, singleLine = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconAction("mic", "Dictate note", onClick = voice); Spacer(Modifier.width(8.dp)); IconAction("plus", "Save note", accent = true) { model.saveNote() } }
        }
        if (state.notes.isNotEmpty()) {
            Label("SAVED NOTES")
            state.notes.take(20).forEach { note -> CardBlock { Text(note.body, style = T.body); Text(date(note.createdAt, "d MMM, HH:mm"), style = T.caption) } }
        }
    }
}

@Composable private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 10.dp)) { Text(title, style = T.pageTitle, modifier = Modifier.semantics { heading() }); Text(subtitle, style = T.subtitle, modifier = Modifier.padding(top = 6.dp)) }
}



