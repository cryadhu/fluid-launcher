package com.example.launcher.fluid

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

internal const val SNOOZE_LENGTH = 10 * 60_000L
internal const val ALARM_LABEL_LIMIT = 24
internal val defaultWorldZones = listOf("Europe/London", "America/New_York", "Asia/Tokyo")

data class Alarm(val id: Long, val hour: Int, val minute: Int, val days: Set<DayOfWeek> = emptySet(), val label: String = "",
    val enabled: Boolean = true, val snoozedUntil: Long? = null)

/** Switched-off alarms offered on first open, like a stock Clock app. */
internal fun starterAlarms() = listOf(
    Alarm(1, 7, 0, DayOfWeek.entries.take(5).toSet(), enabled = false),
    Alarm(2, 9, 0, DayOfWeek.entries.drop(5).toSet(), enabled = false),
)

/** The phone's next alarm from another app, unless an alarm at the same time already exists. */
internal fun importableAlarm(alarms: List<Alarm>, at: Long?, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? {
    val time = at?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: return null
    return time.takeIf { alarms.none { a -> a.hour == time.hour && a.minute == time.minute } }
}

/** Idle when [endsAt] and [left] are both null; running when [endsAt] is set; paused when [left] is set. */
data class ClockTimer(val duration: Long = 5 * 60_000L, val endsAt: Long? = null, val left: Long? = null) {
    val idle get() = endsAt == null && left == null
    fun remaining(now: Long) = left ?: endsAt?.let { it - now } ?: duration
}

data class Stopwatch(val startedAt: Long? = null, val banked: Long = 0, val laps: List<Long> = emptyList()) {
    fun elapsed(now: Long) = banked + (startedAt?.let { now - it } ?: 0)
    val running get() = startedAt != null
}

data class ClockData(val alarms: List<Alarm> = emptyList(), val zones: List<String> = defaultWorldZones,
    val timer: ClockTimer = ClockTimer(), val stopwatch: Stopwatch = Stopwatch()) {

    fun saveAlarm(alarm: Alarm): ClockData {
        val clean = alarm.copy(label = alarm.label.trim().take(ALARM_LABEL_LIMIT), snoozedUntil = null, enabled = true)
        return if (alarms.any { it.id == alarm.id }) copy(alarms = alarms.map { if (it.id == alarm.id) clean else it })
        else copy(alarms = alarms + clean.copy(id = (alarms.maxOfOrNull { it.id } ?: 0) + 1))
    }
    fun deleteAlarm(id: Long) = copy(alarms = alarms.filterNot { it.id == id })
    fun enableAlarm(id: Long, on: Boolean) = alarmChange(id) { it.copy(enabled = on, snoozedUntil = null) }
    /** One-time alarms switch themselves off once they ring; repeating ones roll on to the next day. */
    fun alarmRang(id: Long) = alarmChange(id) { it.copy(snoozedUntil = null, enabled = it.enabled && it.days.isNotEmpty()) }
    fun snoozeAlarm(id: Long, now: Long) = alarmChange(id) { it.copy(enabled = true, snoozedUntil = now + SNOOZE_LENGTH) }
    private fun alarmChange(id: Long, change: (Alarm) -> Alarm) = copy(alarms = alarms.map { if (it.id == id) change(it) else it })

    fun addZone(zone: String) = if (zone in zones) this else copy(zones = zones + zone)
    fun removeZone(zone: String) = copy(zones = zones - zone)

    fun startTimer(duration: Long, now: Long) = copy(timer = ClockTimer(duration, endsAt = now + duration))
    fun pauseTimer(now: Long) = timer.endsAt?.let { copy(timer = timer.copy(endsAt = null, left = (it - now).coerceAtLeast(0))) } ?: this
    fun resumeTimer(now: Long) = timer.left?.let { copy(timer = timer.copy(endsAt = now + it, left = null)) } ?: this
    fun addTimerMinute(now: Long) = when {
        timer.left != null -> copy(timer = timer.copy(left = timer.left + 60_000))
        timer.endsAt != null -> copy(timer = timer.copy(endsAt = maxOf(timer.endsAt, now) + 60_000))
        else -> this
    }
    fun resetTimer() = copy(timer = ClockTimer(timer.duration))

    fun startStopwatch(now: Long) = if (stopwatch.running) this else copy(stopwatch = stopwatch.copy(startedAt = now))
    fun pauseStopwatch(now: Long) = if (!stopwatch.running) this else copy(stopwatch = stopwatch.copy(startedAt = null, banked = stopwatch.elapsed(now)))
    fun lapStopwatch(now: Long) = if (!stopwatch.running) this else copy(stopwatch = stopwatch.copy(laps = stopwatch.laps + stopwatch.elapsed(now)))
    fun resetStopwatch() = copy(stopwatch = Stopwatch())
}

/** The next moment [alarm] rings after [now], or null when it is off. */
internal fun nextRing(alarm: Alarm, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (!alarm.enabled) return null
    alarm.snoozedUntil?.takeIf { it > now }?.let { return it }
    val current = Instant.ofEpochMilli(now).atZone(zone)
    for (offset in 0L..7L) {
        val day = current.toLocalDate().plusDays(offset)
        if (alarm.days.isNotEmpty() && day.dayOfWeek !in alarm.days) continue
        val ring = ZonedDateTime.of(day, java.time.LocalTime.of(alarm.hour, alarm.minute), zone)
        if (ring.isAfter(current)) return ring.toInstant().toEpochMilli()
    }
    return null
}

internal fun alarmDays(days: Set<DayOfWeek>): String = when (days) {
    emptySet<DayOfWeek>() -> "Once"
    DayOfWeek.entries.toSet() -> "Every day"
    DayOfWeek.entries.take(5).toSet() -> "Weekdays"
    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
    else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.name.take(1) + it.name.substring(1, 3).lowercase() }
}

internal fun untilText(millis: Long): String {
    val minutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
    val days = minutes / 1440; val hours = minutes % 1440 / 60; val rest = minutes % 60
    return listOfNotNull(days.takeIf { it > 0 }?.let { "$it d" }, hours.takeIf { it > 0 }?.let { "$it h" }, rest.takeIf { it > 0 || days + hours == 0L }?.let { "$it min" }).joinToString(" ")
}

internal fun durationText(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600; val m = total % 3600 / 60; val s = total % 60
    return listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }, s.takeIf { it > 0 || total == 0L }?.let { "$it s" }).joinToString(" ")
}

/** Timer keypad digits, filled from the right as hh mm ss. */
internal fun timerDigitsToMillis(digits: String): Long {
    val padded = digits.takeLast(6).padStart(6, '0')
    return (padded.substring(0, 2).toLong() * 3600 + padded.substring(2, 4).toLong() * 60 + padded.substring(4, 6).toLong()) * 1000
}

internal fun stopwatchText(millis: Long): String {
    val ms = millis.coerceAtLeast(0)
    val h = ms / 3_600_000; val m = ms / 60_000 % 60; val s = ms / 1000 % 60; val cs = ms / 10 % 100
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d.%02d", h, m, s, cs) else String.format(Locale.ROOT, "%02d:%02d.%02d", m, s, cs)
}

/** Counts whole seconds up, so a timer never shows 00:00 while it is still running. */
internal fun timerText(millis: Long): String {
    val total = (millis.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600; val m = total / 60 % 60; val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

private val zoneRegions = setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")
private val worldZones by lazy { ZoneId.getAvailableZoneIds().filter { it.substringBefore('/') in zoneRegions && '/' in it }.sortedBy(::zoneCity) }
private val modernCities = mapOf("Calcutta" to "Kolkata", "Saigon" to "Ho Chi Minh", "Kiev" to "Kyiv", "Katmandu" to "Kathmandu", "Rangoon" to "Yangon")
internal fun zoneCity(id: String) = id.substringAfterLast('/').replace('_', ' ').let { modernCities[it] ?: it }
internal fun zoneRegion(id: String) = id.substringBeforeLast('/').replace('_', ' ').replace("/", " · ")

internal fun zoneMatches(query: String, existing: List<String>): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return worldZones.filter { it !in existing && (zoneCity(it).contains(q, true) || zoneRegion(it).startsWith(q, true)) }
        .sortedBy { !zoneCity(it).startsWith(q, true) }.distinctBy(::zoneCity).take(8)
}

/** "Same time", "+5 h 30 min" or "−3 h" relative to [local]. */
internal fun zoneDifference(zone: ZoneId, now: Long, local: ZoneId = ZoneId.systemDefault()): String {
    val instant = Instant.ofEpochMilli(now)
    val minutes = (zone.rules.getOffset(instant).totalSeconds - local.rules.getOffset(instant).totalSeconds) / 60
    if (minutes == 0) return "Same time"
    val a = kotlin.math.abs(minutes)
    return (if (minutes > 0) "+" else "−") + listOfNotNull((a / 60).takeIf { it > 0 }?.let { "$it h" }, (a % 60).takeIf { it > 0 }?.let { "$it min" }).joinToString(" ")
}

internal fun zoneDay(zone: ZoneId, now: Long, local: ZoneId = ZoneId.systemDefault()): String {
    val instant = Instant.ofEpochMilli(now)
    return when (instant.atZone(zone).toLocalDate().compareTo(instant.atZone(local).toLocalDate())) { 0 -> "Today"; in 1..Int.MAX_VALUE -> "Tomorrow"; else -> "Yesterday" }
}

internal fun encodeClock(data: ClockData): String = JSONObject()
    .put("alarms", JSONArray().apply { data.alarms.forEach { a -> put(JSONObject().put("id", a.id).put("hour", a.hour).put("minute", a.minute)
        .put("days", JSONArray(a.days.map { it.value })).put("label", a.label).put("enabled", a.enabled).put("snoozedUntil", a.snoozedUntil ?: 0L)) } })
    .put("zones", JSONArray(data.zones))
    .put("timer", JSONObject().put("duration", data.timer.duration).put("endsAt", data.timer.endsAt ?: 0L).put("left", data.timer.left ?: -1L))
    .put("stopwatch", JSONObject().put("startedAt", data.stopwatch.startedAt ?: 0L).put("banked", data.stopwatch.banked).put("laps", JSONArray(data.stopwatch.laps)))
    .toString()

internal fun decodeClock(text: String?): ClockData = runCatching {
    val j = JSONObject(text ?: return ClockData())
    val alarms = j.optJSONArray("alarms")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
        val days = o.optJSONArray("days")?.let { d -> (0 until d.length()).mapNotNull { k -> d.optInt(k).takeIf { it in 1..7 }?.let(DayOfWeek::of) } }.orEmpty().toSet()
        Alarm(o.getLong("id"), o.getInt("hour").coerceIn(0, 23), o.getInt("minute").coerceIn(0, 59), days, o.optString("label"), o.optBoolean("enabled", true), o.optLong("snoozedUntil").takeIf { it > 0 })
    } }.distinctBy { it.id } }.orEmpty()
    val zones = j.optJSONArray("zones")?.let { z -> (0 until z.length()).map(z::getString).filter { runCatching { ZoneId.of(it) }.isSuccess }.distinct() } ?: defaultWorldZones
    val t = j.optJSONObject("timer")
    val timer = if (t == null) ClockTimer() else ClockTimer(t.optLong("duration", 5 * 60_000L).coerceAtLeast(1000), t.optLong("endsAt").takeIf { it > 0 }, t.optLong("left", -1).takeIf { it >= 0 })
    val s = j.optJSONObject("stopwatch")
    val stopwatch = if (s == null) Stopwatch() else Stopwatch(s.optLong("startedAt").takeIf { it > 0 }, s.optLong("banked"), s.optJSONArray("laps")?.let { l -> (0 until l.length()).map(l::getLong) }.orEmpty())
    ClockData(alarms, zones, timer, stopwatch)
}.getOrDefault(ClockData())
