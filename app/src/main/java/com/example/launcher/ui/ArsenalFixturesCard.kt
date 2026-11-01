package com.example.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.launcher.*
import com.example.launcher.data.*
import com.example.launcher.ui.theme.Tokens as T

@Composable fun ArsenalFixturesCard(state: LauncherUiState, model: LauncherViewModel) {
    val games = upcomingArsenal(state.arsenal.games, state.now)
    Column(Modifier.fillMaxWidth().clip(T.widgetCard).background(T.surface).padding(14.dp).testTag("arsenal-fixtures")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Label("ARSENAL"); Text("Next 4 games · all competitions", style = T.caption) }
            TextButton(onClick = { model.refreshArsenal(true) }, enabled = !state.arsenalLoading) { Text(if (state.arsenalLoading) "Updating…" else "Refresh", color = T.amber) }
        }
        if (games.isEmpty()) Text(when { state.arsenalLoading -> "Loading fixtures…"; state.arsenalError -> "Fixtures unavailable. Tap Refresh to retry."; else -> "No upcoming fixtures announced." }, style = T.body)
        games.forEach { game ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("arsenal-fixture-${game.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FixtureTeam(game.home, Modifier.weight(1f))
                    Text("vs", style = T.caption, modifier = Modifier.padding(horizontal = 8.dp))
                    FixtureTeam(game.away, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(fixtureDate(game), style = T.caption)
                    Text(fixtureTime(game), style = T.caption, color = T.amber, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (state.arsenalError && games.isNotEmpty()) Text("Offline · showing saved fixtures", style = T.caption, color = T.amber)
    }
}
@Composable private fun FixtureTeam(club: FixtureClub, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TeamLogo(club.id, club.name, club.logo, Modifier.size(22.dp))
        Text(club.name, style = T.caption, color = T.text, textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
