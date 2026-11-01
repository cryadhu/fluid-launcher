package com.example.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.launcher.data.*
import com.example.launcher.ui.*
import com.example.launcher.ui.theme.LauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LeagueHeroTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cardExpandsFromItsBoundsAndCollapsesBackWithoutAnotherWindow() {
        val hero = LeagueHeroState()
        val state = LauncherUiState(page=0,league=LeagueTable("2026-27",(1..20).map { LeagueClub("$it","Club $it","",it,8,5,12,7,20-it) },1L))
        compose.setContent { LauncherTheme { Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal=24.dp,vertical=60.dp)) { PremierLeagueCard(state,hero) }
            LeagueTableHero(state,hero,{},{})
        } } }
        val source = compose.onNodeWithTag("premier-league-card").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("premier-league-card").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(150)
        val middle = compose.onNodeWithTag("premier-league-full-table").fetchSemanticsNode().boundsInRoot
        assertTrue(middle.width > source.width)
        assertTrue(middle.top < source.top)
        compose.mainClock.advanceTimeBy(600)
        val expanded = compose.onNodeWithTag("premier-league-full-table").fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.height > source.height)
        assertTrue(expanded.width >= middle.width)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("premier-league-all-clubs").performScrollToNode(hasTestTag("league-club-20"))
        compose.onNodeWithTag("league-club-20").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close league table").performClick()
        compose.waitUntil(5000) { !hero.active }
        compose.onNodeWithTag("premier-league-hero-overlay").assertDoesNotExist()
        val returned = compose.onNodeWithTag("premier-league-card").fetchSemanticsNode().boundsInRoot
        assertEquals(source.top,returned.top,1f)
        assertEquals(source.height,returned.height,1f)
        compose.mainClock.autoAdvance = true
    }
}
