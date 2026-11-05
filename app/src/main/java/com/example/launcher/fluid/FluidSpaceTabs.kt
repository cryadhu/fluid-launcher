@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.launcher.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.launcher.data.InstalledApp
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun fluidFilteredApps(apps: List<InstalledApp>, query: String, space: SpaceTab?): List<InstalledApp> = apps.filter { app ->
    app.label.contains(query.trim(), true) && (space == null || app.component.flattenToString() in space.apps)
}

@Composable internal fun SpaceAppsWidget(state: FluidState) {
    val host = LocalSpaceApps.current
    val spaces = state.config.spaces
    var chosen by rememberSaveable { mutableLongStateOf(spaces.firstOrNull()?.id ?: 0L) }
    val selected = spaces.find { it.id == chosen } ?: spaces.firstOrNull()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTabs(spaces.map { it.name }, spaces.indexOf(selected), Modifier.weight(1f)) { chosen = spaces[it].id }
            GlassIcon("edit", "Edit tabs", Modifier.size(42.dp)) { host.manage(selected?.id) }
        }
        when {
            selected == null -> Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("Add a tab to group your apps.", style = F.text(13, muted = true))
                GlassButton("Add tab", Modifier.padding(top = 12.dp), glyph = "plus") { host.manage(null) }
            }
            else -> {
                val apps = fluidFilteredApps(state.apps, "", selected)
                if (apps.isEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("Choose the apps that belong in ${selected.name}.", style = F.text(13, muted = true))
                    GlassButton("Choose apps", Modifier.padding(top = 12.dp), glyph = "plus") { host.manage(selected.id) }
                } else Column(Modifier.fillMaxWidth().testTag("space-apps-${selected.name}")) {
                    apps.chunked(4).forEach { group -> Row(Modifier.fillMaxWidth()) { group.forEach { app -> FluidAppTile(app, host.badges, state.config.labels, host.launch, {}, Modifier.weight(1f)) }; repeat(4 - group.size) { Spacer(Modifier.weight(1f)) } } }
                }
            }
        }
    }
}

/** Left-aligned tabs with one glass pill that slides and stretches between them. */
@Composable internal fun GlassTabs(titles: List<String>, selected: Int, modifier: Modifier = Modifier, select: (Int) -> Unit) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val bounds = remember { mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>() }
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds[selected]
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        launch { scroll.reveal(target, density.run { 24.dp.toPx() }) }
        if (!placed) { left.snapTo(target.start); right.snapTo(target.endInclusive); placed = true; return@LaunchedEffect }
        // The leading edge springs ahead and the trailing edge catches up, so the glass stretches as it moves.
        val forward = target.start > left.value
        val lead = spring<Float>(.74f, 620f)
        val trail = spring<Float>(.82f, 260f)
        launch { left.animateTo(target.start, if (forward) trail else lead) }
        launch { right.animateTo(target.endInclusive, if (forward) lead else trail) }
    }
    Box(modifier.horizontalScroll(scroll).testTag("space-tabs")) {
        if (placed && selected >= 0) Box(Modifier.offset { IntOffset(left.value.roundToInt(), 0) }
            .width(with(density) { (right.value - left.value).coerceAtLeast(0f).toDp() }).height(42.dp)
            .glass(21.dp, elevated = true).background(F.purple.copy(alpha = .13f)).testTag("space-tab-glass"))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            titles.forEachIndexed { index, title ->
                val active = index == selected
                Box(Modifier.height(42.dp).onPlaced { val x = it.positionInParent().x; val range = x..x + it.size.width; if (bounds[index] != range) bounds[index] = range }
                    .clip(CircleShape)
                    // Long presses belong to the card, which uses them to start rearranging.
                    .combinedClickable(role = Role.Tab, onLongClick = {}) { select(index) }
                    .semantics { this.selected = active }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(title, style = F.text(12, true), color = if (active) F.ink else F.muted, maxLines = 1)
                }
            }
        }
    }
}

private suspend fun ScrollState.reveal(range: ClosedFloatingPointRange<Float>, margin: Float) {
    val view = viewportSize.toFloat()
    if (view <= 0f) return
    when {
        range.start - margin < value -> animateScrollTo((range.start - margin).roundToInt().coerceAtLeast(0))
        range.endInclusive + margin > value + view -> animateScrollTo((range.endInclusive + margin - view).roundToInt().coerceAtMost(maxValue))
    }
}

@Composable private fun fieldColors() = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent, cursorColor = F.purple, focusedTextColor = F.ink, unfocusedTextColor = F.ink)

@Composable private fun GlassField(value: String, change: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, glyph: String? = null, done: () -> Unit = {}) {
    TextField(value, { change(it.take(if (glyph == "search") 40 else SPACE_NAME_LIMIT)) }, modifier.heightIn(min = 44.dp).glass(24.dp), singleLine = true,
        placeholder = { Text(placeholder, style = F.text(13, muted = true)) }, textStyle = F.text(),
        leadingIcon = glyph?.let { { Glyph(it, F.ink, Modifier.size(18.dp)) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { done() }), colors = fieldColors())
}

/** Opens focused with the current name selected, so typing replaces it. */
@Composable private fun RenameField(name: String, change: (String) -> Unit, modifier: Modifier, done: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(value, { value = it.copy(text = it.text.take(SPACE_NAME_LIMIT)); change(value.text) }, modifier.heightIn(min = 44.dp).glass(24.dp).focusRequester(focus), singleLine = true,
        placeholder = { Text("Tab name", style = F.text(13, muted = true)) }, textStyle = F.text(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { done() }), colors = fieldColors())
}

/** Add, rename, delete and reorder Space apps tabs, and choose the apps in each one. */
@Composable internal fun SpaceTabsEditor(state: FluidState, model: FluidModel, start: Long?) {
    val spaces = state.config.spaces
    var chosen by rememberSaveable { mutableLongStateOf(start ?: spaces.firstOrNull()?.id ?: 0L) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var fresh by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = spaces.find { it.id == chosen } ?: spaces.firstOrNull()
    fun rename() { renaming?.let { id -> if (validSpaceName(draft, spaces, id) != null) { model.renameSpace(id, draft); renaming = null } } }
    fun add() { model.addSpace(fresh)?.let { chosen = it; fresh = "" } }

    Text("Tabs", style = F.text(15, true))
    spaces.forEachIndexed { index, tab ->
        val active = tab.id == selected?.id
        Row(Modifier.fillMaxWidth().glass(24.dp).then(if (active) Modifier.border(1.5.dp, F.purple.copy(alpha = .55f), RoundedCornerShape(24.dp)) else Modifier)
            .clickable(role = Role.RadioButton) { chosen = tab.id }.semantics { this.selected = active }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).testTag("space-tab-row-${tab.id}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (renaming == tab.id) {
                RenameField(tab.name, { draft = it }, Modifier.weight(1f).testTag("space-tab-rename"), done = ::rename)
                GlassIcon("check", "Save name", Modifier.size(36.dp), primary = validSpaceName(draft, spaces, tab.id) != null, onClick = ::rename)
                GlassIcon("close", "Cancel rename", Modifier.size(36.dp)) { renaming = null }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(tab.name, style = F.text(14, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (tab.apps.size == 1) "1 app" else "${tab.apps.size} apps", style = F.text(11, muted = true))
                }
                if (index > 0) GlassIcon("up", "Move ${tab.name} left", Modifier.size(36.dp)) { model.moveSpace(tab.id, -1) }
                if (index < spaces.lastIndex) GlassIcon("up", "Move ${tab.name} right", Modifier.size(36.dp).rotate(180f)) { model.moveSpace(tab.id, 1) }
                GlassIcon("edit", "Rename ${tab.name}", Modifier.size(36.dp)) { renaming = tab.id; draft = tab.name; chosen = tab.id }
                GlassIcon("bin", "Delete ${tab.name}", Modifier.size(36.dp)) { model.deleteSpace(tab.id); if (renaming == tab.id) renaming = null }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassField(fresh, { fresh = it }, "New tab", Modifier.weight(1f).testTag("space-tab-new"), done = ::add)
        GlassButton("Add", primary = validSpaceName(fresh, spaces) != null, glyph = "plus", onClick = ::add)
    }
    if (fresh.isNotBlank() && validSpaceName(fresh, spaces) == null) Text("That name is already used.", style = F.text(11, muted = true))

    if (selected == null) return
    Text("Apps in ${selected.name}", style = F.text(15, true), modifier = Modifier.padding(top = 8.dp))
    GlassField(query, { query = it }, "Search apps", Modifier.fillMaxWidth(), glyph = "search")
    val apps = remember(state.apps, query) { fluidFilteredApps(state.apps, query, null) }
    if (apps.isEmpty()) Text(if (state.loading) "Finding your apps…" else "No matching apps", style = F.text(13, muted = true))
    apps.forEach { app ->
        val key = app.component.flattenToString()
        val included = key in selected.apps
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Checkbox) { model.toggleSpaceApp(selected.id, key) }
            .semantics { this.selected = included }.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            app.artwork?.let { Image(it.asImageBitmap(), null, Modifier.size(34.dp)) } ?: Box(Modifier.size(34.dp))
            Text(app.label, style = F.text(13), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(26.dp).clip(CircleShape).then(if (included) Modifier.background(F.purple) else Modifier.border(1.5.dp, F.muted.copy(alpha = .5f), CircleShape)), contentAlignment = Alignment.Center) {
                if (included) Glyph("check", Color.White, Modifier.size(15.dp))
            }
        }
    }
}
