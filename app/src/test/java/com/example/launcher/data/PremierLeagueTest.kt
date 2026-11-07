package com.example.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PremierLeagueTest {
    private fun payload(): JSONObject {
        val entries = JSONArray((20 downTo 1).map { rank ->
            JSONObject().put("team", JSONObject().put("id", "$rank").put("displayName", "Club $rank").put("shortDisplayName", "C$rank").put("logos", JSONArray().put(JSONObject().put("href", "https://a.espncdn.com/i/teamlogos/soccer/500/$rank.png"))))
                .put("stats", JSONArray(listOf("rank" to rank, "gamesPlayed" to 8, "pointsAgainst" to 15, "pointsFor" to 9, "pointDifferential" to -6, "points" to 8, "wins" to 2, "ties" to 2, "losses" to 4).map { (name, value) -> JSONObject().put("name", name).put("value", value) }))
        })
        return JSONObject().put("season", JSONObject().put("displayName", "2026-27 English Premier League"))
            .put("children", JSONArray().put(JSONObject().put("standings", JSONObject().put("entries", entries))))
    }
    @Test fun standingsUseProviderRanksAndExactStats() {
        val table = parsePremierLeague(payload().toString(), 123L)
        assertEquals((1..20).toList(), table.clubs.map { it.position })
        assertEquals(6, table.clubs.take(6).size)
        assertEquals("2026-27 English Premier League", table.season)
        assertEquals(123L, table.fetchedAt)
        val club = table.clubs.first()
        assertEquals("Club 1", club.name)
        assertEquals("C1", club.shortName)
        assertEquals(listOf(2, 2, 4), listOf(club.won, club.drawn, club.lost))
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/1.png", club.logo)
        assertEquals(listOf(8, 15, 9, -6, 8), listOf(club.played, club.ga, club.gf, club.gd, club.points))
    }
    @Test(expected = IllegalArgumentException::class) fun incompleteTableIsRejectedInsteadOfReplacingSavedStandings() {
        val json = payload()
        json.getJSONArray("children").getJSONObject(0).getJSONObject("standings").getJSONArray("entries").remove(0)
        parsePremierLeague(json.toString(), 1L)
    }
}
