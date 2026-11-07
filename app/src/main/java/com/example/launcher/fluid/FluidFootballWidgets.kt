package com.example.launcher.fluid

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.launcher.data.ArsenalFixture
import com.example.launcher.data.FixtureClub
import com.example.launcher.data.LeagueClub
import com.example.launcher.ui.Glyph
import com.example.launcher.ui.TeamLogo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val RelegationRed = Color(0xFFD9384B)
private val ArsenalFallback = FixtureClub("359", "Arsenal", "https://a.espncdn.com/i/teamlogos/soccer/500/359.png")

@Composable
internal fun PremierLeagueWidget(
    state: FluidState,
    model: FluidModel,
    expanded: Boolean = false,
    open: () -> Unit = {}
) {
    LaunchedEffect(model) { model.refreshLeague() }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        if (expanded) LeagueDetailHeader(state, Modifier.padding(bottom = 12.dp))
        LeagueCardContents(state, model, if (expanded) 1f else 0f, scroll, open = open)
    }
}

@Composable
internal fun NextMatchesWidget(state: FluidState, model: FluidModel) {
    val selectedId = state.config.footballClub
    LaunchedEffect(model, selectedId) { model.refreshFixtures() }
    LaunchedEffect(model) { model.refreshLeague() }
    val clubs = remember(state.league.clubs) {
        state.league.clubs.sortedBy { it.position }.distinctBy { it.id }
            .map { FixtureClub(it.id, it.name, it.logo) }.ifEmpty { listOf(ArsenalFallback) }
    }
    val selected = clubs.find { it.id == selectedId }
        ?: state.fixtures.games.asSequence().flatMap { sequenceOf(it.home, it.away) }.find { it.id == selectedId }
        ?: ArsenalFallback.takeIf { selectedId == it.id }
        ?: FixtureClub(selectedId, "Select club", "")
    val games = remember(state.fixtures.games, selectedId, state.now) {
        state.fixtures.games.filter {
            it.kickoff > state.now && it.competition == "Premier League" && (it.home.id == selectedId || it.away.id == selectedId)
        }.distinctBy { it.id }.sortedBy { it.kickoff }.take(4)
    }
    val zone = ZoneId.systemDefault()
    val offline = footballOffline(state.now)
    var choosing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(selected.id, selected.name, selected.logo, Modifier.size(26.dp))
            Text(
                "NEXT 4 MATCHES", style = F.text(12, true).copy(letterSpacing = 1.sp), color = F.purple,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Box {
                Row(
                    Modifier.widthIn(max = 110.dp).heightIn(min = 44.dp)
                        .testTag("football-club-selector")
                        .clickable(role = Role.Button, onClickLabel = "Choose football club") { choosing = true }
                        .semantics { contentDescription = "Football club: ${selected.name}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(selected.name, style = F.text(12, true, muted = true), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Glyph("up", F.muted, Modifier.size(14.dp).rotate(180f))
                }
                DropdownMenu(
                    expanded = choosing, onDismissRequest = { choosing = false },
                    modifier = Modifier.heightIn(max = 320.dp).widthIn(min = 220.dp, max = 300.dp)
                ) {
                    clubs.forEach { club ->
                        DropdownMenuItem(
                            text = { Text(club.name, style = F.text(13, club.id == selectedId), maxLines = 1,
                                overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { TeamLogo(club.id, club.name, club.logo, Modifier.size(24.dp)) },
                            trailingIcon = {
                                if (club.id == selectedId) Glyph("check", F.purple, Modifier.size(16.dp))
                            },
                            onClick = { choosing = false; model.selectFootballClub(club.id) }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = F.muted.copy(alpha = .14f))
        FootballDataStatus(
            games.isNotEmpty(), state.fixturesLoading, state.fixturesError, offline,
            "No upcoming fixtures published.", "fixtures"
        ) { model.refreshFixtures(force = true) }
        games.forEach { game ->
            FixtureRow(game, selectedId, state.now, zone)
            HorizontalDivider(color = F.muted.copy(alpha = .14f))
        }
        Text("Premier League · Times in ${zone.id}", style = F.text(10, muted = true), modifier = Modifier.padding(top = 12.dp))
        FootballAttribution(state.fixtures.fetchedAt)
    }
}

@Composable
internal fun LeagueWidgetHero(hero: FluidHero, state: FluidState, model: FluidModel) {
    val kind = hero.kind ?: return
    if (kind != FluidWidget.PREMIER_LEAGUE) return
    val progress = remember(hero, kind) { Animatable(0f) }
    val scroll = rememberScrollState()
    val measuredOrigin = remember(hero, kind) { hero.origin }
    LaunchedEffect(kind, hero.expanded) {
        if (hero.expanded) progress.animateTo(1f, spring(1f, 290f))
        else {
            if (scroll.value > 0) scroll.animateScrollTo(0, tween(160))
            progress.animateTo(0f, spring(1f, 380f))
            hero.kind = null
        }
    }
    BackHandler { hero.expanded = false }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("fluid-hero-overlay")) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val target = with(density) {
            Rect(
                (safe.calculateLeftPadding(direction) + 16.dp).toPx(),
                (safe.calculateTopPadding() + 12.dp).toPx(),
                (maxWidth - safe.calculateRightPadding(direction) - 16.dp).toPx(),
                (maxHeight - safe.calculateBottomPadding() - 16.dp).toPx()
            )
        }
        val source = measuredOrigin.takeIf { it.width > 0f && it.height > 0f }
            ?: hero.bounds[kind]?.takeIf { it.width > 0f && it.height > 0f } ?: target
        val p = progress.value.coerceIn(0f, 1f)
        val header = (p / .65f).coerceIn(0f, 1f)
        val bounds = lerp(source, target, p)
        val sourcePadding = if (state.config.sizes[kind] == "Compact") 12f else 20f
        Box(
            Modifier.fillMaxSize().background(Color(0xFF493475).copy(alpha = .10f * p))
                .pointerInput(hero) { detectTapGestures { hero.expanded = false } }
        )
        Column(
            Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .pointerInput(hero) { detectTapGestures {} }
        ) {
            if (header > 0f) {
                Box(Modifier.fillMaxWidth().height((72f * header).dp).clipToBounds()
                    .graphicsLayer { alpha = header }
                    .then(if (header < .95f) Modifier.clearAndSetSemantics {} else Modifier)) {
                    LeagueDetailHeader(
                        state, Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true),
                        back = { hero.expanded = false }
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f).glass((30f + 4f * p).dp, true)
                    .testTag("fluid-expanded-widget")
                    .padding((sourcePadding + (20f - sourcePadding) * p).dp)
            ) {
                LeagueCardContents(
                    state, model, p, scroll, Modifier.fillMaxSize(), fillRows = true,
                    interactive = hero.expanded && p > .99f
                )
            }
        }
    }
}

@Composable
private fun LeagueDetailHeader(state: FluidState, modifier: Modifier = Modifier, back: (() -> Unit)? = null) {
    Row(modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            Box(
                Modifier.size(44.dp).clickable(role = Role.Button, onClick = back)
                    .semantics { contentDescription = "Back to football widgets" },
                contentAlignment = Alignment.Center
            ) { Glyph("up", F.ink, Modifier.size(24.dp).rotate(270f)) }
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Premier League", style = F.text(22, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (state.league.fetchedAt > 0) "Table · updated ${footballDate(state.league.fetchedAt, "MMM d")}"
                else "Table · ESPN", style = F.text(12, muted = true), modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun LeagueCardContents(
    state: FluidState,
    model: FluidModel,
    progress: Float,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    fillRows: Boolean = false,
    interactive: Boolean = true,
    open: () -> Unit = {}
) {
    val clubs = remember(state.league.clubs) { state.league.clubs.sortedBy { it.position } }
    val header = (progress / .65f).coerceIn(0f, 1f)
    val extra = ((progress - .22f) / .73f).coerceIn(0f, 1f)
    val rowHeight = (40f - 6f * header).dp
    val offline = footballOffline(state.now)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val statWidth = (maxWidth * .07f).coerceIn(20.dp, 30.dp)
        val pointsWidth = (maxWidth * .10f).coerceIn(28.dp, 38.dp)
        Column(Modifier.fillMaxWidth().then(if (fillRows) Modifier.fillMaxHeight() else Modifier)) {
            if (header < 1f) {
                Box(Modifier.fillMaxWidth().height((42f * (1f - header)).dp).clipToBounds()
                    .graphicsLayer { alpha = 1f - header }
                    .then(if (header > 0f) Modifier.clearAndSetSemantics {} else Modifier)) {
                    Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
                        LeagueCompactHeader()
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            if (header > 0f) {
                Box(Modifier.fillMaxWidth().height((30f * header).dp).clipToBounds()
                    .graphicsLayer { alpha = header }
                    .then(if (header < .99f) Modifier.clearAndSetSemantics {} else Modifier)) {
                    LeagueLegend(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true))
                }
            }
            LeagueTableHeader(statWidth, pointsWidth)
            val rowsModifier = when {
                fillRows -> Modifier.weight(1f).verticalScroll(scroll, enabled = interactive)
                progress > 0f -> Modifier.heightIn(max = 600.dp).verticalScroll(scroll, enabled = interactive)
                else -> Modifier
            }.fillMaxWidth().then(if (progress >= .99f) Modifier.testTag("league-full-table") else Modifier)
            Column(rowsModifier) {
                FootballDataStatus(
                    clubs.isNotEmpty(), state.leagueLoading, state.leagueError, offline,
                    "No standings available.", "standings", interactive
                ) { model.refreshLeague(force = true) }
                clubs.take(5).forEach { club ->
                    LeagueTableRow(club, state.config.footballClub, statWidth, pointsWidth, rowHeight, header)
                }
                if (extra > 0f && clubs.size > 5) {
                    Box(Modifier.fillMaxWidth().height(rowHeight * (clubs.size - 5) * extra)
                        .clipToBounds().graphicsLayer { alpha = extra }
                        .then(if (extra < .99f) Modifier.clearAndSetSemantics {} else Modifier)) {
                        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
                            clubs.drop(5).forEach { club ->
                                LeagueTableRow(club, state.config.footballClub, statWidth, pointsWidth, rowHeight, header)
                            }
                        }
                    }
                }
            }
            if (header < 1f) {
                Box(Modifier.fillMaxWidth().height((48f * (1f - header)).dp).clipToBounds()
                    .graphicsLayer { alpha = 1f - header }
                    .then(if (header > 0f) Modifier.clearAndSetSemantics {} else Modifier)) {
                    LeagueOpenAction(clubs.isNotEmpty(), open, interactive)
                }
            }
            FootballAttribution(state.league.fetchedAt)
        }
    }
}

@Composable
private fun LeagueCompactHeader() {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "PREMIER LEAGUE", style = F.text(12, true).copy(letterSpacing = 1.sp), color = F.purple,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Text("Table", style = F.text(12, muted = true), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LeagueLegend(modifier: Modifier) {
    Row(modifier.heightIn(min = 30.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        listOf("Top 4" to F.purple, "Bottom 3" to RelegationRed, "My team" to F.lilac.copy(alpha = .5f))
            .forEach { (label, color) ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
                    Text(label, style = F.text(10, muted = true))
                }
            }
    }
}

@Composable
private fun LeagueTableHeader(statWidth: Dp, pointsWidth: Dp) {
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = F.text(11, true, muted = true), textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        Text("Club", style = F.text(11, true, muted = true), modifier = Modifier.weight(1f).padding(start = 34.dp))
        listOf("P", "W", "D", "L", "Pts").forEachIndexed { index, label ->
            Text(label, style = F.text(11, true, muted = true), textAlign = if (index == 4) TextAlign.End else TextAlign.Center,
                modifier = Modifier.width(if (index == 4) pointsWidth else statWidth).padding(end = if (index == 4) 4.dp else 0.dp))
        }
    }
}

@Composable
private fun LeagueTableRow(
    club: LeagueClub,
    selectedId: String,
    statWidth: Dp,
    pointsWidth: Dp,
    rowHeight: Dp,
    expanded: Float
) {
    val selected = club.id == selectedId
    val mark = when (club.position) { in 1..4 -> F.purple; in 18..20 -> RelegationRed; else -> Color.Transparent }
    Box(Modifier.fillMaxWidth().height(rowHeight).testTag("league-row-${club.position}")
        .clip(RoundedCornerShape(14.dp))
        .background(if (selected) F.lilac.copy(alpha = if (LocalGlass.current.dark) .25f else .28f) else Color.Transparent)
        .semantics(mergeDescendants = true) {
            contentDescription = "${club.position}. ${club.name}, played ${club.played}, won ${club.won}, drawn ${club.drawn}, lost ${club.lost}, ${club.points} points${if (selected) ", my team" else ""}"
        }) {
        if (mark != Color.Transparent && expanded > 0f) {
            Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(rowHeight * .62f)
                .background(mark.copy(alpha = expanded), RoundedCornerShape(2.dp)))
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(club.position.toString(), style = F.text(13, true), textAlign = TextAlign.Center,
                color = if (club.position in 1..4) androidx.compose.ui.graphics.lerp(F.purple, F.muted, expanded) else F.muted,
                modifier = Modifier.width(24.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamLogo(club.id, club.name, club.logo, Modifier.size((26f - 4f * expanded).dp))
                Text(club.shortName, style = F.text(13, selected), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            listOf(club.played, club.won, club.drawn, club.lost, club.points).forEachIndexed { index, value ->
                Text(value.toString(), style = F.text(12, index == 4, muted = index != 4),
                    textAlign = if (index == 4) TextAlign.End else TextAlign.Center,
                    modifier = Modifier.width(if (index == 4) pointsWidth else statWidth).padding(end = if (index == 4) 4.dp else 0.dp))
            }
        }
    }
}

@Composable
private fun LeagueOpenAction(enabled: Boolean, open: () -> Unit, interactive: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(48.dp).testTag("league-see-full-table")
        .clickable(enabled = enabled && interactive, role = Role.Button, onClick = open),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        Text("See full table", style = F.text(13, true), color = if (enabled) F.purple else F.muted)
        Glyph("up", if (enabled) F.purple else F.muted, Modifier.size(16.dp).rotate(90f))
    }
}

@Composable
private fun FixtureRow(game: ArsenalFixture, selectedId: String, now: Long, zone: ZoneId) {
    val home = game.home.id == selectedId
    val opponent = if (home) game.away else game.home
    val kickoff = Instant.ofEpochMilli(game.kickoff).atZone(zone)
    val today = kickoff.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("fixture-${game.id}")) {
        val dateWidth = (maxWidth * .28f).coerceIn(78.dp, 100.dp)
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(dateWidth)) {
                Text(if (today) "Today" else kickoff.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                    style = F.text(12, true), color = if (today) F.purple else F.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (game.timeConfirmed) kickoff.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())) else "Time TBC",
                    style = F.text(12, muted = true), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
            TeamLogo(opponent.id, opponent.name, opponent.logo, Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("${if (home) "vs" else "at"} ${opponent.name}", style = F.text(13, true), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(listOf(if (home) "Home" else "Away", game.competition).filter { it.isNotBlank() }.joinToString(" · "),
                    style = F.text(11, muted = true), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(28.dp).background(if (home) F.purple else F.muted.copy(alpha = .12f), RoundedCornerShape(9.dp))
                .semantics { contentDescription = if (home) "Home match" else "Away match" }, contentAlignment = Alignment.Center) {
                Text(if (home) "H" else "A", style = F.text(13, true), color = if (home) Color.White else F.ink)
            }
        }
    }
}

@Composable
private fun FootballDataStatus(
    hasData: Boolean,
    loading: Boolean,
    error: Boolean,
    offline: Boolean,
    empty: String,
    subject: String,
    interactive: Boolean = true,
    retry: () -> Unit
) {
    if (hasData && !loading && !error && !offline) return
    val message = when {
        offline -> if (hasData) "Offline · showing saved $subject" else "Offline · $subject unavailable"
        loading -> if (hasData) "Updating $subject…" else "Loading $subject…"
        error -> if (hasData) "Couldn't update · showing saved $subject" else "Couldn't load $subject."
        else -> empty
    }
    Column(Modifier.fillMaxWidth().heightIn(min = if (hasData) 36.dp else 100.dp).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = if (hasData) Alignment.Start else Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = F.purple, strokeWidth = 1.5.dp)
            Text(message, style = F.text(11, muted = true))
        }
        if (!loading) {
            TextButton(onClick = retry, enabled = interactive, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Retry", style = F.text(11, true), color = F.purple)
            }
        }
    }
}

@Composable
private fun FootballAttribution(fetchedAt: Long) {
    Text(if (fetchedAt > 0) "ESPN · updated ${footballDate(fetchedAt, "MMM d, yyyy · h:mm a")}" else "ESPN",
        style = F.text(10, muted = true), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
}

private fun footballDate(time: Long, pattern: String): String = Instant.ofEpochMilli(time)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

@Composable
private fun footballOffline(now: Long): Boolean {
    val context = LocalContext.current
    return remember(context, now / 5_000L) {
        runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
            val network = connectivity.activeNetwork ?: return@runCatching true
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@runCatching false
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
    }
}
