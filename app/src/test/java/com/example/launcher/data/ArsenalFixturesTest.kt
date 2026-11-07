package com.example.launcher.data

import org.junit.Assert.*
import org.junit.Test
import org.json.*
import java.time.Instant

class ArsenalFixturesTest {
    private val home = FixtureClub("359", "Arsenal", "logo")
    private val away = FixtureClub("1", "Opponent", "logo2")
    @Test fun istUsesIndianDateAcrossMidnightAndIgnoresDeviceTimezone() {
        val game = ArsenalFixture("a", Instant.parse("2026-10-27T20:00:00Z").toEpochMilli(), true, home, away, "Cup")
        assertEquals("Wed, 28 Oct", fixtureDate(game))
        assertEquals("1:30 AM IST", fixtureTime(game))
        assertEquals("Time TBC", fixtureTime(game.copy(timeConfirmed = false)))
    }
    @Test fun nextFourAreFutureSortedAndUnique() {
        val games = listOf(6L, 2L, 1L, 5L, 4L, 3L, 2L).map { ArsenalFixture("$it", it, true, home, away, "") }
        assertEquals(listOf(2L,3L,4L,5L), upcomingArsenal(games, 1L).map { it.kickoff })
    }
    @Test fun parserExcludesCompletedAndPostponedAndPreservesHomeAwayAndBadges() {
        fun event(id: String, state: String, status: String): JSONObject {
            val teams = JSONArray(listOf("home" to "359", "away" to "1").map { (side, team) -> JSONObject().put("homeAway", side).put("team", JSONObject().put("id", team).put("displayName", if (team == "359") "Arsenal" else "Opponent").put("logos", JSONArray().put(JSONObject().put("href", "https://badge/$team")))) })
            return JSONObject().put("id", id).put("league", JSONObject().put("abbreviation", "Cup")).put("competitions", JSONArray().put(JSONObject().put("date", "2026-10-27T20:00Z").put("timeValid", true).put("competitors", teams).put("status", JSONObject().put("type", JSONObject().put("state", state).put("name", status)))))
        }
        val json = JSONObject().put("events", JSONArray().put(event("1", "pre", "STATUS_SCHEDULED")).put(event("2", "post", "STATUS_FINAL")).put(event("3", "pre", "STATUS_POSTPONED")))
        val games = parseArsenalFixtures(json.toString(), 42L).games
        assertEquals(1, games.size)
        assertEquals("359", games.single().home.id)
        assertEquals("1", games.single().away.id)
        assertEquals("https://badge/1", games.single().away.logo)
        assertTrue(games.single().timeConfirmed)
        assertEquals(1, parseArsenalFixtures(json.toString(), 42L, "1").games.size)
        assertTrue(parseArsenalFixtures(json.toString(), 42L, "2").games.isEmpty())
    }
}
