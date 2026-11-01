package com.example.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class LeagueClub(val id: String, val name: String, val logo: String, val position: Int, val played: Int, val ga: Int, val gf: Int, val gd: Int, val points: Int)
data class LeagueTable(val season: String = "", val clubs: List<LeagueClub> = emptyList(), val fetchedAt: Long = 0)

internal fun parsePremierLeague(raw: String, fetchedAt: Long): LeagueTable {
    val json = JSONObject(raw)
    val entries = json.getJSONArray("children").getJSONObject(0).getJSONObject("standings").getJSONArray("entries")
    val clubs = (0 until entries.length()).map { index ->
        val entry = entries.getJSONObject(index)
        val team = entry.getJSONObject("team")
        val stats = entry.getJSONArray("stats")
        fun stat(name: String): Int = (0 until stats.length()).map { stats.getJSONObject(it) }.first { it.getString("name") == name }.getInt("value")
        LeagueClub(team.getString("id"), team.getString("displayName"), team.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty(), stat("rank"), stat("gamesPlayed"), stat("pointsAgainst"), stat("pointsFor"), stat("pointDifferential"), stat("points"))
    }.sortedBy { it.position }
    require(clubs.size == 20 && clubs.map { it.position } == (1..20).toList() && clubs.map { it.id }.distinct().size == 20)
    return LeagueTable(json.getJSONObject("season").getString("displayName"), clubs, fetchedAt)
}

class PremierLeagueRepository(context: Context) {
    private val cache = File(context.filesDir, "premier_league.json")
    suspend fun cached(): LeagueTable = withContext(Dispatchers.IO) {
        runCatching { val json = JSONObject(cache.readText()); parsePremierLeague(json.getString("payload"), json.getLong("fetchedAt")) }.getOrDefault(LeagueTable())
    }
    suspend fun refresh(): LeagueTable = withContext(Dispatchers.IO) {
        val raw = getHttps("https://site.web.api.espn.com/apis/v2/sports/soccer/eng.1/standings")
        val table = parsePremierLeague(raw, System.currentTimeMillis())
        val temp = File(cache.parentFile, "premier_league.tmp")
        temp.writeText(JSONObject().put("payload", raw).put("fetchedAt", table.fetchedAt).toString())
        check(temp.renameTo(cache))
        table
    }
}
