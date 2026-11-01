@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.launcher.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.launcher.LauncherUiState
import com.example.launcher.LauncherViewModel
import com.example.launcher.data.*
import com.example.launcher.ui.theme.Tokens as T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Stable class LeagueHeroState {
    var source by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    var origin by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    var active by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    fun open() { if (!active && source.width > 0 && source.height > 0) { origin = source; active = true; expanded = true } }
}

@Composable fun PremierLeagueCard(state: LauncherUiState, hero: LeagueHeroState) {
    Column(Modifier.fillMaxWidth().onGloballyPositioned {
        hero.source = androidx.compose.ui.geometry.Rect(it.positionInRoot(), it.size.toSize())
    }.graphicsLayer { alpha = if (hero.active) 0f else 1f }
        .clip(T.widgetCard).background(T.surface).clickable(enabled = !hero.active, role = Role.Button, onClickLabel = "Open full Premier League table") { hero.open() }
        .testTag("premier-league-card").then(if (hero.active) Modifier.clearAndSetSemantics {} else Modifier).padding(14.dp)) {
        LeagueHeroHeading(state,0f,{}, {})
        LeagueSeason(state)
        if (state.league.clubs.isNotEmpty()) {
            LeagueHeader()
            state.league.clubs.take(6).forEach { LeagueRow(it) }
        } else LeagueUnavailable(state)
        LeagueHeroFooter(state,0f,{})
    }
}

/** One visible table surface moves from the card's real bounds to the safe full-screen layout. */
@Composable fun LeagueTableHero(state: LauncherUiState, hero: LeagueHeroState, refresh: (Boolean) -> Unit, openSource: () -> Unit) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val refreshLatest by rememberUpdatedState(refresh)
    LaunchedEffect(state.page,state.homeReturnEpoch) {
        if (state.page != 0) { hero.active = false; hero.expanded = false; progress.snapTo(0f) }
    }
    LaunchedEffect(hero.active,hero.expanded) {
        if (!hero.active) { progress.snapTo(0f); return@LaunchedEffect }
        if (hero.expanded) {
            list.scrollToItem(0)
            progress.animateTo(1f, androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f,0f,0f,1f)))
            refreshLatest(false)
        } else {
            if (list.firstVisibleItemIndex != 0 || list.firstVisibleItemScrollOffset != 0) list.animateScrollToItem(0)
            progress.animateTo(0f, androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f,0f,0.2f,1f)))
            hero.active = false
        }
    }
    if (!hero.active) return
    androidx.activity.compose.BackHandler { if (hero.expanded) hero.expanded = false }
    val p = progress.value
    val density = androidx.compose.ui.platform.LocalDensity.current
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val bottom = maxOf(insets.calculateBottomPadding(), WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding())
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(T.ground.copy(alpha = p)).testTag("premier-league-hero-overlay")
        .pointerInput(Unit) { detectTapGestures { } }) {
        val width = with(density) { maxWidth.toPx() }; val height = with(density) { maxHeight.toPx() }
        val source = if (hero.expanded) hero.origin else hero.source
        val bounds = androidx.compose.ui.geometry.Rect(source.left * (1-p), source.top * (1-p), source.right + (width-source.right)*p, source.bottom + (height-source.bottom)*p)
        val corner = androidx.compose.foundation.shape.RoundedCornerShape(28.dp * (1-p))
        val padding = 14.dp + 2.dp * p
        Column(Modifier.offset { IntOffset(bounds.left.roundToInt(),bounds.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .graphicsLayer { shape = corner; clip = true; shadowElevation = with(density) { 12.dp.toPx() } * 4*p*(1-p) }
            .background(T.surface).testTag("premier-league-full-table").semantics { paneTitle = "Premier League full table"; isTraversalGroup = true }
            .padding(start = padding + insets.calculateStartPadding(direction)*p, end = padding + insets.calculateEndPadding(direction)*p,
                top = padding + insets.calculateTopPadding()*p, bottom = padding + bottom*p)) {
            LeagueHeroHeading(state,p,{ hero.expanded = false },{ refreshLatest(true) })
            LeagueSeason(state)
            Box(Modifier.fillMaxWidth().height(20.dp*p).clipToBounds().graphicsLayer { alpha = p }) {
                if (state.league.fetchedAt > 0) Text("Updated ${Instant.ofEpochMilli(state.league.fetchedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}", style = T.caption, maxLines = 1)
            }
            if (state.league.clubs.isNotEmpty()) LeagueHeader()
            LazyColumn(Modifier.weight(1f).testTag("premier-league-all-clubs"), state = list, userScrollEnabled = hero.expanded && p >= 0.999f) {
                if (state.league.clubs.isEmpty()) item { LeagueUnavailable(state) }
                items(state.league.clubs, key = { it.id }) { LeagueRow(it) }
            }
            LeagueHeroFooter(state,p,openSource)
        }
    }
}
@Composable private fun LeagueHeroHeading(state: LauncherUiState, p: Float, close: () -> Unit, refresh: () -> Unit) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height((24.dp + 24.dp*p)*fontScale), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp*p).fillMaxHeight().clipToBounds().graphicsLayer { alpha = p }) {
            IconAction("back", "Close league table", enabled = p >= 0.999f, onClick = close)
        }
        Text("PREMIER LEAGUE", style = T.sectionLabel.copy(fontSize = (11+7*p).sp, letterSpacing = (1.5f*(1-p)).sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.width(84.dp*p).fillMaxHeight().clipToBounds().graphicsLayer { alpha = p }) {
            TextButton(onClick = refresh, enabled = p >= 0.999f && !state.leagueLoading, modifier = Modifier.requiredWidth(84.dp)) {
                Text(if (state.leagueLoading) "Updating…" else "Refresh", style = T.caption, color = T.amber, maxLines = 1)
            }
        }
    }
}
@Composable private fun LeagueSeason(state: LauncherUiState) {
    Text(state.league.season.ifBlank { "English Premier League" }, style = T.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
}
@Composable private fun LeagueUnavailable(state: LauncherUiState) {
    Text(if (state.leagueLoading) "Loading standings…" else "Standings unavailable · open table to retry", style = T.body)
}
@Composable private fun LeagueHeroFooter(state: LauncherUiState, p: Float, openSource: () -> Unit) {
    if (state.leagueError) Text(if (state.league.clubs.isEmpty()) "Couldn’t load standings. Check your connection." else "Offline · showing saved table", style = T.caption, color = T.amber)
    Box(Modifier.fillMaxWidth().height(28.dp + 20.dp*p), contentAlignment = Alignment.CenterStart) {
        Text("View full table ›", style = T.caption, color = T.amber, modifier = Modifier.graphicsLayer { alpha = 1-p })
        TextButton(onClick = openSource, enabled = p >= 0.999f, modifier = Modifier.graphicsLayer { alpha = p }) { Text("Standings & club logos · ESPN", style = T.caption, color = T.amber, maxLines = 1) }
    }
}

@Composable private fun LeagueHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = T.caption, modifier = Modifier.width(22.dp))
        Text("Club", style = T.caption, modifier = Modifier.weight(1f))
        listOf("Pld", "GA", "GF", "GD", "Pts").forEach { Text(it, style = T.caption, textAlign = TextAlign.End, modifier = Modifier.width(27.dp)) }
    }
}
@Composable private fun LeagueRow(club: LeagueClub) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("league-club-${club.position}"), verticalAlignment = Alignment.CenterVertically) {
        Text(club.position.toString(), style = T.caption, color = if (club.position <= 6) T.mint else T.muted, modifier = Modifier.width(22.dp))
        TeamLogo(club.id, club.name, club.logo)
        Text(club.name, style = T.body.copy(fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
        listOf(club.played, club.ga, club.gf, club.gd, club.points).forEachIndexed { index, value -> Text(if (index == 3 && value > 0) "+$value" else value.toString(), style = T.caption.copy(fontSize = 12.sp), color = if (index == 4) T.amber else T.text, fontWeight = if (index == 4) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, modifier = Modifier.width(27.dp)) }
    }
}
private object TeamBadgeCache { val images = android.util.LruCache<String, android.graphics.Bitmap>(48) }
@Composable internal fun TeamLogo(id: String, name: String, url: String, modifier: Modifier = Modifier.size(22.dp)) {
    val context = LocalContext.current
    val cacheKey = "$id:$url"
    var logo by remember(cacheKey) { mutableStateOf(TeamBadgeCache.images.get(cacheKey)) }
    LaunchedEffect(url) {
        if (logo != null) return@LaunchedEffect
        logo = withContext(Dispatchers.IO) { runCatching {
            require(id.matches(Regex("[0-9]+")))
            require(url.startsWith("https://a.espncdn.com/i/teamlogos/"))
            val file = File(context.cacheDir, "club-${id}.png")
            if (!file.exists()) {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10000; connection.readTimeout = 10000
                    check(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 }) ?: error("Invalid logo")
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } finally { connection.disconnect() }
            }
            BitmapFactory.decodeFile(file.path)?.also { TeamBadgeCache.images.put(cacheKey,it) }
        }.getOrNull() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (logo != null) Image(logo!!.asImageBitmap(), contentDescription = "${name} logo", modifier = Modifier.fillMaxSize())
        else Glyph("app", T.mint, Modifier.size(18.dp))
    }
}
