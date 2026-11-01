package com.example.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FixtureClub(val id: String, val name: String, val logo: String)
data class ArsenalFixture(val id: String, val kickoff: Long, val timeConfirmed: Boolean, val home: FixtureClub, val away: FixtureClub, val competition: String)
data class ArsenalSchedule(val games: List<ArsenalFixture> = emptyList(), val fetchedAt: Long = 0)
internal fun fixtureDate(game: ArsenalFixture): String = Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
internal fun fixtureTime(game: ArsenalFixture): String = if (!game.timeConfirmed) "Time TBC" else Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("h:mm a 'IST'", Locale.ENGLISH))
internal fun upcomingArsenal(games: List<ArsenalFixture>, now: Long) = games.filter { it.kickoff > now }.distinctBy { it.id }.sortedBy { it.kickoff }.take(4)

internal fun parseArsenalFixtures(raw: String, fetchedAt: Long): ArsenalSchedule {
    val events = JSONObject(raw).getJSONArray("events")
    val games = (0 until events.length()).mapNotNull { index ->
        val event = events.getJSONObject(index)
        val match = event.getJSONArray("competitions").getJSONObject(0)
        val status = match.getJSONObject("status").getJSONObject("type")
        if (status.optString("state") != "pre" || status.optString("name") in setOf("STATUS_POSTPONED", "STATUS_CANCELED", "STATUS_CANCELLED")) return@mapNotNull null
        val teams = match.getJSONArray("competitors")
        fun club(side: String): FixtureClub {
            val team = (0 until teams.length()).map { teams.getJSONObject(it) }.first { it.getString("homeAway") == side }.getJSONObject("team")
            return FixtureClub(team.getString("id"), team.getString("displayName"), team.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty())
        }
        val home = club("home"); val away = club("away")
        if (home.id != "359" && away.id != "359") return@mapNotNull null
        ArsenalFixture(event.getString("id"), java.time.OffsetDateTime.parse(match.getString("date")).toInstant().toEpochMilli(), match.optBoolean("timeValid", event.optBoolean("timeValid", false)), home, away, event.optJSONObject("league")?.optString("abbreviation").orEmpty())
    }
    return ArsenalSchedule(games.sortedBy { it.kickoff }.distinctBy { it.id }, fetchedAt)
}
class ArsenalFixturesRepository(context: Context) {
    private val cache = File(context.filesDir, "arsenal_fixtures.json")
    suspend fun cached(): ArsenalSchedule = withContext(Dispatchers.IO) {
        runCatching { val json = JSONObject(cache.readText()); parseArsenalFixtures(json.getString("payload"), json.getLong("fetchedAt")) }.getOrDefault(ArsenalSchedule())
    }
    suspend fun refresh(): ArsenalSchedule = withContext(Dispatchers.IO) {
        val raw = getHttps("https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/359/schedule?fixture=true")
        val schedule = parseArsenalFixtures(raw, System.currentTimeMillis())
        val temp = File(cache.parentFile, "arsenal_fixtures.tmp")
        temp.writeText(JSONObject().put("payload", raw).put("fetchedAt", schedule.fetchedAt).toString())
        check(temp.renameTo(cache)); schedule
    }
}
