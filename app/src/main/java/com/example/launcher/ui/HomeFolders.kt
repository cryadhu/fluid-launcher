@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.launcher.LauncherViewModel
import com.example.launcher.data.*
import com.example.launcher.ui.theme.Tokens as T

@Composable fun FolderPreview(entry: HomeEntry, apps: List<InstalledApp>, modifier: Modifier = Modifier) {
    val members = entry.components.mapNotNull { key -> apps.find { it.component.flattenToString() == key } }.take(4)
    BoxWithConstraints(modifier.clip(T.iconShape).background(T.surfaceRaised).padding(9.dp)) {
        val miniature = minOf(18.dp, (maxWidth - 4.dp) / 2)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            members.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { AppArtwork(it, Modifier.size(miniature)) }
            } }
        }
        if (T.preferences.notificationDots && entry.components.any { it.substringBefore('/') in T.badgePackages }) Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(T.notificationDot, androidx.compose.foundation.shape.CircleShape).testTag("folder-notification-dot:${entry.id}"))
    }
}

@Composable fun FolderCell(entry: HomeEntry, apps: List<InstalledApp>, drag: LauncherDragState, onOpen: () -> Unit, onUngroup: () -> Unit, modifier: Modifier, actions: List<CustomAccessibilityAction>) {
    val representative = apps.firstOrNull { it.component.flattenToString() in entry.components } ?: return
    val hitKey = "folder:${entry.id}"
    DisposableEffect(hitKey) { onDispose { drag.hits.remove(hitKey) } }
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val scale by animateFloatAsState(if (arrived) 1f else 0.8f, spring(stiffness = 500f), label = "Folder created")
    Column(modifier.fillMaxWidth().onGloballyPositioned { drag.hits[hitKey] = AppHit(representative, it.boundsInRoot(), false, homeEntry = entry) }
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (drag.item?.id == entry.id) 0.2f else 1f }
        .clickable(role = Role.Button, onClick = onOpen).padding(vertical = 4.dp).semantics(mergeDescendants = true) {
            contentDescription = "Open ${entry.title} folder"; stateDescription = "${entry.components.size} apps"
            customActions = actions + CustomAccessibilityAction("Ungroup folder") { onUngroup(); true }
        }, horizontalAlignment = Alignment.CenterHorizontally) {
        FolderPreview(entry, apps, Modifier.size(T.preferences.iconSize.dp))
        if (T.preferences.showLabels) {
            Spacer(Modifier.height(8.dp))
            Text(entry.title, style = T.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable fun FolderOverlay(folder: HomeEntry, apps: List<InstalledApp>, model: LauncherViewModel,
    onDragStart: (InstalledApp, Offset) -> Boolean = { _, _ -> false }, onDrag: (Offset) -> Unit = {}, onDragEnd: () -> Unit = {}, onDragCancel: () -> Unit = {}) {
    var dragging by remember { mutableStateOf(false) }
    val hits = remember { mutableMapOf<String, Rect>() }
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    var rename by remember(folder.id) { mutableStateOf(false) }
    var title by remember(folder.id, folder.title) { mutableStateOf(folder.title) }
    var picker by remember(folder.id) { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val members = folder.components.mapNotNull { key -> apps.find { it.component.flattenToString() == key } }
    Dialog(onDismissRequest = { model.openFolder(null) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogView = LocalView.current
        val slop = LocalViewConfiguration.current.touchSlop
        val window = (dialogView.parent as androidx.compose.ui.window.DialogWindowProvider).window
        SideEffect { window.setDimAmount(if (dragging) 0f else 0.32f) }
        Box(Modifier.fillMaxSize().testTag("folder-drag-layer").pointerInput(folder.id) {
            var held: InstalledApp? = null
            var origin = Offset.Zero
            var movement = Offset.Zero
            detectLauncherLongPress(onDragStart = { point ->
                hits.entries.lastOrNull { it.value.contains(point) }?.key?.let { key ->
                    apps.find { it.component.flattenToString() == key }?.let { app ->
                        val location = IntArray(2); dialogView.getLocationOnScreen(location)
                        held = app; origin = point + Offset(location[0].toFloat(), location[1].toFloat()); movement = Offset.Zero
                    }
                }
            }, onDrag = { _, amount ->
                if (dragging) move(amount) else held?.let { app ->
                    movement += amount
                    if (movement.getDistance() > slop) {
                        dragging = start(app, origin); held = null
                        if (dragging) { menuFor = null; move(movement) }
                    }
                }
            }, onDragEnd = {
                if (dragging) { dragging = false; end() } else held?.let { menuFor = it.component.flattenToString() }
                held = null
            }, onDragCancel = { held = null; if (dragging) { dragging = false; cancel() } })
        }.background(if (dragging) Color.Transparent else T.ground.copy(alpha = 0.48f)).clickable { if (!dragging) model.openFolder(null) }) {
            AnimatedVisibility(visible, enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f), modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = if (dragging) 0f else 1f }.statusBarsPadding().navigationBarsPadding().padding(24.dp).testTag("folder-viewport")) {
                val viewportHeight = maxHeight
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.fillMaxWidth().testTag("folder-expanded-content"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.title, style = T.pageTitle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("folder-title").clickable { rename = true }.semantics { heading() })
                        IconAction("edit", "Rename folder") { rename = true }
                    }
                    Text("${members.size} apps", style = T.caption)
                    Spacer(Modifier.height(28.dp))
                    Column(Modifier.fillMaxWidth().clip(T.widgetCard).background(T.surface).clickable {}.padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        (members.map { it to false } + listOf(null to true)).chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (app, add) ->
                                    Box(Modifier.weight(1f)) {
                                        if (add) Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { picker = true }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(Modifier.size(58.dp).border(1.dp, T.dotInactive, T.appTile), contentAlignment = Alignment.Center) { Glyph("plus", T.muted) }
                                            Spacer(Modifier.height(8.dp)); Text("Add apps", style = T.caption)
                                        } else if (app != null) {
                                            Column(Modifier.fillMaxWidth().onGloballyPositioned { hits[app.component.flattenToString()] = it.boundsInRoot() }.testTag("folder-app-${app.component.flattenToString()}").clickable(onClick = { model.openFolder(null); model.launch(app) }).padding(vertical = 4.dp).semantics {
                                                customActions = listOf(CustomAccessibilityAction("Remove from folder") { model.setFolderApps(folder.id, folder.components - app.component.flattenToString()); true })
                                            }, horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(Modifier.size(T.preferences.iconSize.dp).background(T.surfaceRaised, T.iconShape), contentAlignment = Alignment.Center) {
                                                    AppArtwork(app)
                                                    if (T.preferences.notificationDots && app.component.packageName in T.badgePackages) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).background(T.notificationDot, androidx.compose.foundation.shape.CircleShape).testTag("folder-app-notification-dot:${app.component.flattenToString()}"))
                                                }
                                                Spacer(Modifier.height(8.dp)); Text(app.label, style = T.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            DropdownMenu(menuFor == app.component.flattenToString(), { menuFor = null }) {
                                                DropdownMenuItem(text = { Text("Remove from folder") }, onClick = { model.setFolderApps(folder.id, folder.components - app.component.flattenToString()); menuFor = null })
                                            }
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { model.openFolder(null) }) { Text("Close folder", color = T.muted) }
                }
                }
                }
            }
        }
        if (rename) AlertDialog(onDismissRequest = { rename = false }, containerColor = T.surface,
            title = { Text("Rename folder", color = T.text) },
            text = { OutlinedTextField(title, { title = it.take(40) }, singleLine = true, label = { Text("Folder name") }) },
            confirmButton = { TextButton(onClick = { model.renameFolder(folder.id, title); rename = false }) { Text("Save", color = T.amber) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel", color = T.muted) } })
        if (picker) FolderAppPicker(folder, apps, { picker = false }, { model.setFolderApps(folder.id, it); picker = false })
    }
}

@Composable private fun FolderAppPicker(folder: HomeEntry, apps: List<InstalledApp>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var picked by remember { mutableStateOf(folder.components.toSet()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(T.ground).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Add apps", style = T.pageTitle, modifier = Modifier.weight(1f)); IconAction("close", "Close app picker", onClick = onDismiss)
            }
            Text("${picked.size} selected", style = T.caption, modifier = Modifier.padding(vertical = 12.dp))
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(apps, key = { it.component.flattenToString() }) { app ->
                    val key = app.component.flattenToString()
                    Column(Modifier.fillMaxWidth().clickable { picked = if (key in picked) picked - key else picked + key }.padding(8.dp).semantics(mergeDescendants = true) { contentDescription = "Select ${app.label}"; selected = key in picked }, horizontalAlignment = Alignment.CenterHorizontally) {
                        AppArtwork(app, Modifier.size(32.dp)); Spacer(Modifier.height(8.dp)); Text(app.label, style = T.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Checkbox(key in picked, onCheckedChange = null)
                    }
                }
            }
            ActionPill("Done", { onSave(apps.map { it.component.flattenToString() }.filter { it in picked }) }, Modifier.fillMaxWidth(), primary = true)
        }
    }
}
