package com.example.launcher.fluid

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.launcher.data.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FluidFootballWidgetsTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000_000L
    private val clubs = (1..20).map { rank ->
        LeagueClub(if(rank==1) "359" else "$rank", if(rank==1) "Arsenal" else "Club $rank", "", rank, 9, 5, 14, 9, 24-rank, 8, 0, 1)
    }
    private fun model() = FluidModel(ApplicationProvider.getApplicationContext<Application>())
    private fun state() = FluidState(now=now,league=LeagueTable("2026-27",clubs,now),config=FluidConfig(mode="Light"))

    @Test fun compactTableExpandsFromCardAndCollapsesBack() {
        val model=model()
        val hero=FluidHero()
        compose.setContent {
            FluidTheme(state().config) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
                        ExpandableWidget(FluidWidget.PREMIER_LEAGUE,state(),model,hero,{},{})
                    }
                    FluidHeroOverlay(hero,state(),model,{},{})
                }
            }
        }
        compose.onNodeWithTag("league-row-5",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("league-row-6",useUnmergedTree=true).assertDoesNotExist()
        val source=compose.onNodeWithTag("fluid-widget-PREMIER_LEAGUE").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("league-see-full-table").performClick()
        compose.mainClock.advanceTimeBy(80)
        val moving=compose.onNodeWithTag("fluid-expanded-widget").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(1800)
        val expanded=compose.onNodeWithTag("fluid-expanded-widget").fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.height>source.height)
        assertTrue(moving.height<expanded.height)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("league-row-20").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { hero.expanded=false }
        compose.waitForIdle()
        compose.onNodeWithTag("league-full-table").assertDoesNotExist()
        compose.onNodeWithTag("league-see-full-table").assertIsDisplayed()
    }

    @Test fun nextFourFixturesKeepOpponentAndHomeAwayAccurate() {
        val arsenal=FixtureClub("359","Arsenal","")
        val opponent=FixtureClub("382","Manchester City","")
        val games=(1..5).map { i -> ArsenalFixture("$i",now+i*86_400_000L,true,if(i%2==1)arsenal else opponent,if(i%2==1)opponent else arsenal,"Premier League") }
        val state=state().copy(fixtures=ArsenalSchedule(games+games.first().copy(id="cup",kickoff=now+1,competition="UEFA Champions League"),now))
        val model=model()
        compose.setContent { FluidTheme(state.config) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { NextMatchesWidget(state,model) } } }
        compose.onNodeWithTag("fixture-1").assertExists()
        compose.onNodeWithTag("fixture-4").assertExists()
        compose.onNodeWithTag("fixture-5").assertDoesNotExist()
        compose.onNodeWithTag("football-club-selector").performClick()
        compose.onNodeWithText("Club 2").assertExists()
    }

    @Test fun unavailableDataShowsNoInventedRows() {
        val state=FluidState(config=FluidConfig(mode="Dark"),leagueError=true,fixturesError=true)
        val model=model()
        compose.setContent { FluidTheme(state.config) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { PremierLeagueWidget(state,model);NextMatchesWidget(state,model) } } }
        compose.onNodeWithTag("league-row-1").assertDoesNotExist()
        compose.onNodeWithTag("fixture-1").assertDoesNotExist()
        compose.onNodeWithTag("football-club-selector").assertExists()
    }
}
