@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.launcher.*
import com.example.launcher.data.*
import com.example.launcher.ui.theme.Tokens as T
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun homeWallpaper(name: String): Color = if (name == "Theme") T.ground else Color(wallpaperColor(name))
fun visibleHomeEntries(state: LauncherUiState, pageId: Int): List<HomeEntry> = normalizeHomeLayout(if (pageId == 1)
    state.homeLayout ?: homeApps(state.apps, state.pins).map { appEntry(it.component.flattenToString()) }
    else state.customization.layouts[pageId].orEmpty()).filter { entry -> entry.components.any { key -> state.apps.any { it.component.flattenToString() == key } } }

@Composable fun HomeCustomizationUi(state: LauncherUiState, model: LauncherViewModel) {
    var reset by remember { mutableStateOf(false) }
    val back = { model.showHomePanel(HomePanel.CUSTOMIZE) }
    when (state.homePanel) {
        HomePanel.CUSTOMIZE -> ModalBottomSheet(onDismissRequest = { model.showHomePanel(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.surface, contentColor = T.text) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 20.dp).testTag("customize-home-sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Customize home", style = T.body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconAction("close", "Close customization") { model.showHomePanel(null) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SheetAction("app", "Themes", T.amber) { model.showHomePanel(HomePanel.THEMES) }
                    SheetAction("photos", "Wallpaper") { model.showHomePanel(HomePanel.WALLPAPER) }
                    SheetAction("settings", "Settings", T.sky) { model.showHomePanel(HomePanel.SETTINGS) }
                    SheetAction("plus", "Add page", T.coral) { model.addHomePage() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("ADD WIDGETS"); Text("See all", style = T.caption.copy(color = T.amber), modifier = Modifier.clickable { model.showHomePanel(HomePanel.WIDGETS) }.padding(4.dp)) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeWidgetKind.entries.forEach { kind -> Column(Modifier.width(76.dp).height(86.dp).clip(T.appTile).background(T.surfaceRaised).clickable(enabled = !state.customization.locked) { model.addHomeWidget(kind) }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) { Glyph(kind.glyph, T.mint, Modifier.size(28.dp)); Text(kind.title, style = T.caption.copy(fontSize = 10.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2) } }
                }
                Row(Modifier.fillMaxWidth().clickable { model.showHomePanel(HomePanel.PAGES) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Edit pages", style = T.body); Glyph("next", T.muted, Modifier.size(18.dp)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Lock home layout", style = T.body, modifier = Modifier.weight(1f)); Switch(state.customization.locked, model::setHomeLocked, modifier = Modifier.semantics { contentDescription = "Lock home layout" }, colors = SwitchDefaults.colors(checkedThumbColor = T.onAccent, checkedTrackColor = T.amber, uncheckedThumbColor = T.muted, uncheckedTrackColor = T.surfaceRaised, uncheckedBorderColor = T.dotInactive)) }
            }
        }
        HomePanel.WIDGETS -> CustomizationScreen("Widgets", back, scrollBehindNavigation = true) {
            var group by remember { mutableStateOf("Time and weather") }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Time and weather", "Money and media", "Tools and focus").forEach { name -> FilterChip(group == name, { group = name }, { Text(name, style = T.caption) }, colors = FilterChipDefaults.filterChipColors(containerColor = T.surface, labelColor = T.muted, selectedContainerColor = T.surfaceRaised, selectedLabelColor = T.amber)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().testTag("widget-catalog-scroll").verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val kinds = HomeWidgetKind.entries.filter { it.group == group }
                if (group == "Tools and focus") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        kinds.take(2).forEach { kind -> WidgetCatalogItem(kind, state, model, Modifier.weight(1f)) }
                    }
                    kinds.drop(2).forEach { kind -> WidgetCatalogItem(kind, state, model) }
                } else kinds.forEach { kind -> WidgetCatalogItem(kind, state, model) }
            }
        }
        HomePanel.PAGES -> CustomizationScreen("Edit pages", back, scrollBehindNavigation = true,
            toolbarAction = { TextButton(onClick = { reset = true }) { Text("Reset", color = T.amber) } }) {
            PageEditorGrid(state, model, Modifier.weight(1f))
        }
        HomePanel.SETTINGS -> CustomizationScreen("Home settings", back, scrollBehindNavigation = true) { HomeSettings(state, model, Modifier.weight(1f)) }
        HomePanel.THEMES -> CustomizationScreen("Themes", back, scrollBehindNavigation = true) { ThemePicker(state.customization, model, Modifier.weight(1f)) }
        HomePanel.WALLPAPER -> CustomizationScreen("Wallpaper", back) {
            Text("Home and lock screen wallpaper", style = T.subtitle)
            listOf("Forest", "Midnight", "Charcoal", "Plum").forEach { name ->
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).height(80.dp).clip(T.widgetCard).background(homeWallpaper(name)).border(1.dp, if (state.customization.wallpaper == name) T.amber else T.dotInactive, T.widgetCard).clickable { model.setWallpaper(name) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = T.body, modifier = Modifier.weight(1f)); if (state.customization.wallpaper == name) Glyph("check", T.amber)
                }
            }
        }
        null -> Unit
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, containerColor = T.surface, title = { Text("Reset initial setup?", color = T.text) },
        text = { Text("Restore all four original pages, default Home shortcuts, dock and wallpaper. Added pages and widgets will be removed. Your notes, tasks and favourites stay saved.", color = T.muted) },
        confirmButton = { TextButton(onClick = { reset = false; model.resetHomeSettings() }) { Text("Reset", color = T.amber) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } })
    state.customization.widgets.find { it.id == state.selectedWidget }?.let { widget ->
        var move by remember(widget.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { model.selectWidget(null) }, containerColor = T.surface, title = { Text(widget.kind.title, color = T.text) },
            text = { Column {
                if (!move) {
                    TextButton(onClick = { move = true }, enabled = !state.customization.locked) { Text("Move to page", color = T.amber) }
                    TextButton(onClick = { model.removeHomeWidget(widget.id) }, enabled = !state.customization.locked) { Text("Remove widget", color = T.coral) }
                    if (state.customization.locked) Text("Home layout is locked", color = T.muted)
                } else {
                    state.customization.pages.filter { it.isHome }.forEach { page -> TextButton(onClick = { model.moveWidgetToPage(widget.id, page.id) }) { Text(page.title, color = T.text) } }
                    TextButton(onClick = { val id = model.addHomePage(); model.moveWidgetToPage(widget.id, id) }) { Text("New page", color = T.amber) }
                }
            } }, confirmButton = { TextButton(onClick = { model.selectWidget(null) }) { Text("Done") } })
    }
    if (state.homePanel != null && state.message != null) Box(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Text(state.message, style = T.caption, color = T.onAccent, modifier = Modifier.clip(T.appTile).background(T.amber).padding(14.dp))
    }
}

@Composable private fun SheetAction(glyph: String, title: String, color: Color = T.mint, action: () -> Unit) {
    Column(Modifier.width(72.dp).clickable(onClick = action), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(T.appTile).background(T.surfaceRaised), contentAlignment = Alignment.Center) { Glyph(glyph, color) }
        Text(title, style = T.caption, modifier = Modifier.padding(top = 6.dp))
    }
}
@Composable private fun PageEditorGrid(state: LauncherUiState, model: LauncherViewModel, modifier: Modifier) {
    val grid = rememberLazyGridState()
    val pages by rememberUpdatedState(visiblePages(state.customization))
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var targetId by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var previewWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    fun hit(position: Offset): Int? = grid.layoutInfo.visibleItemsInfo.firstOrNull {
        position.x >= it.offset.x && position.x < it.offset.x + it.size.width && position.y >= it.offset.y && position.y < it.offset.y + it.size.height
    }?.key?.let { it as? Int }?.takeIf { id -> pages.any { it.id == id && !it.protected } }
    fun finish(cancel: Boolean) {
        val source = draggedId; val target = targetId
        draggedId = null; targetId = null
        if (!cancel && source != null && target != null) model.reorderPages(source, target)
    }
    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val bottom = grid.layoutInfo.viewportEndOffset
            val scroll = when { pointer.y < edge -> -18f; pointer.y > bottom - edge -> 18f; else -> 0f }
            if (scroll != 0f) { grid.scrollBy(scroll); targetId = hit(pointer) }
            delay(16)
        }
    }
    Box(modifier.fillMaxWidth()) {
        LazyVerticalGrid(GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize().testTag("page-editor-scroll").pointerInput(Unit) {
            detectLauncherLongPress(onDragStart = { position ->
                val id = hit(position)
                val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                if (id != null && item != null) {
                    draggedId = id; targetId = id; pointer = position
                    grab = position - Offset(item.offset.x.toFloat(), item.offset.y.toFloat()); previewWidth = item.size.width
                }
            }, onDrag = { change, amount -> if (draggedId != null) { change.consume(); pointer += amount; targetId = hit(pointer) } },
                onDragEnd = { finish(false) }, onDragCancel = { finish(true) })
        }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + 16.dp)) {
            items(pages, key = { it.id }) { page ->
                Box(Modifier.fillMaxWidth().graphicsLayer { alpha = if (draggedId == page.id) 0.25f else 1f }
                    .border(if (targetId == page.id && draggedId != page.id) 2.dp else 0.dp, if (targetId == page.id && draggedId != page.id) T.amber else Color.Transparent, T.widgetCard)) {
                    PageMiniature(page, state, Modifier.fillMaxWidth().aspectRatio(0.64f).testTag("page-preview-${page.id}")
                        .semantics { contentDescription = "${page.title} page preview" }
                        .clickable { model.showHomePanel(null); model.page(page.id) })
                    if (!page.protected) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).background(T.surface, T.appTile)) {
                        IconAction("bin", "Remove ${page.title} page") { model.removeHomePage(page.id) }
                    }
                }
            }
            item { ActionPill("Add page", { model.addHomePage() }, Modifier.fillMaxWidth()) }
        }
        pages.find { it.id == draggedId }?.let { page ->
            PageMiniature(page, state, Modifier.offset { IntOffset((pointer.x - grab.x).roundToInt(), (pointer.y - grab.y).roundToInt()) }
                .width(with(density) { previewWidth.toDp() }).aspectRatio(0.64f).graphicsLayer { alpha = 0.9f; scaleX = 1.03f; scaleY = 1.03f }.clearAndSetSemantics {})
        }
    }
}
@Composable private fun CustomizationScreen(title: String, back: () -> Unit, scrollBehindNavigation: Boolean = false, toolbarAction: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = back)
    // Render in the Activity so safeDrawing uses its real navigation-bar insets,
    // rather than the separate OEM dialog window's consumed/missing insets.
    Box(Modifier.fillMaxSize().background(T.ground).pointerInput(Unit) { detectTapGestures { } }) {
        val safeSides = if (scrollBehindNavigation) WindowInsetsSides.Top + WindowInsetsSides.Horizontal else WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(safeSides)).padding(horizontal = 20.dp).padding(bottom = if (scrollBehindNavigation) 0.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction("back", "Back", onClick = back); Text(title, style = T.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f)); toolbarAction()
            }
            content()
        }
    }
}
@Composable private fun WidgetCatalogItem(kind: HomeWidgetKind, state: LauncherUiState, model: LauncherViewModel, modifier: Modifier = Modifier) {
    val previewHeight = when (kind) {
        HomeWidgetKind.CLOCK -> 104.dp
        HomeWidgetKind.AGENDA, HomeWidgetKind.MARKETS -> 144.dp
        HomeWidgetKind.WEATHER -> 200.dp
        else -> if (kind.rows == 2) 184.dp else 96.dp
    }
    Column(modifier.fillMaxWidth()) {
        val previewSize = if (kind == HomeWidgetKind.WEATHER || kind == HomeWidgetKind.MARKETS) Modifier.wrapContentHeight() else Modifier.height(previewHeight)
        Box(Modifier.fillMaxWidth().then(previewSize).clip(T.widgetCard).background(T.surface)) { HomeWidgetContent(kind, state, model, interactive = false) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(kind.title, style = T.body, fontWeight = FontWeight.Bold); Text("${kind.columns} × ${kind.rows}", style = T.caption) }
            TextButton(onClick = { model.addHomeWidget(kind) }, enabled = !state.customization.locked, modifier = Modifier.testTag("add-widget-${kind.name}")) { Text("Add", color = T.amber) }
        }
    }
}
@Composable private fun PageMiniature(page: LauncherPage, state: LauncherUiState, modifier: Modifier) {
    Column(modifier.clip(T.widgetCard).background(homeWallpaper(state.customization.wallpaper)).border(1.dp, T.dotInactive, T.widgetCard).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (page.isHome) {
            if (page.id == 1) Text(Instant.ofEpochMilli(state.now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), style = T.pageTitle)
            Spacer(Modifier.weight(1f))
            BoxWithConstraints(Modifier.fillMaxWidth().height(84.dp)) {
                val width = maxWidth / 4
                visibleHomeEntries(state, page.id).forEach { entry ->
                    Box(Modifier.offset(x = width * (entry.slot % 4), y = 20.dp * (entry.slot / 4)).size(width, 18.dp), contentAlignment = Alignment.Center) {
                        if (entry.isFolder) Glyph("files", T.mint, Modifier.size(12.dp))
                        else state.apps.find { it.component.flattenToString() == entry.id }?.let { AppArtwork(it, Modifier.size(13.dp)) }
                    }
                }
                state.customization.widgets.filter { it.pageId == page.id }.forEach { widget ->
                    Box(Modifier.offset(x = width * (widget.slot % 4), y = 20.dp * (widget.slot / 4)).size(width * widget.kind.columns, 20.dp * widget.kind.rows - 2.dp).clip(T.appTile).background(T.surfaceRaised), contentAlignment = Alignment.Center) { Glyph(widget.kind.glyph, T.amber, Modifier.size(14.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().background(T.surface, T.dock).padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("phone", "messages", "camera", "browser").forEach { Glyph(it, T.mint, Modifier.size(12.dp)) } }
        } else {
            val lines = when (page.id) {
                0 -> listOf("PREMIER LEAGUE") + state.league.clubs.take(2).map { "${it.position}. ${it.name} · ${it.points} pts" } + listOf("EXCHANGE RATES") + state.feed.rates.take(2).map { "${it.pair} ${"%.2f".format(it.value)}" } + listOf("TOP STORIES") + state.feed.stories.take(2).map { it.title }
                2 -> listOf("UP NEXT") + state.device.events.take(2).map { it.title } + listOf("Wi-Fi · Bluetooth", "QUICK ACTIONS", state.device.mediaTitle ?: "Music player")
                else -> listOf("TASKS") + state.tasks.take(3).map { it.text } + listOf("QUICK NOTE", state.notes.firstOrNull()?.body ?: "Jot something down…", "FAVOURITES")
            }
            lines.forEach { Text(it, style = T.caption.copy(fontSize = 9.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clip(T.appTile).background(T.surface).padding(8.dp)) }
        }
    }
}
