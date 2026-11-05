package com.example.launcher.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class FluidClockDataTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun oneTimeAlarmRingsTodayOrTomorrow() {
        val alarm = Alarm(1, 7, 30)
        assertEquals(at(5, 7, 30), nextRing(alarm, at(5, 6, 0), zone))
        assertEquals(at(6, 7, 30), nextRing(alarm, at(5, 7, 30), zone))
        assertNull(nextRing(alarm.copy(enabled = false), at(5, 6, 0), zone))
    }

    @Test fun repeatingAlarmSkipsToNextChosenDay() {
        // 5 November 2026 is a Thursday.
        val weekdays = Alarm(1, 8, 0, DayOfWeek.entries.take(5).toSet())
        assertEquals(at(6, 8, 0), nextRing(weekdays, at(5, 9, 0), zone))
        assertEquals(at(9, 8, 0), nextRing(weekdays, at(6, 9, 0), zone))
        assertEquals(at(12, 8, 0), nextRing(Alarm(2, 8, 0, setOf(DayOfWeek.THURSDAY)), at(5, 8, 0), zone))
    }

    @Test fun snoozeWinsUntilItPasses() {
        val now = at(5, 7, 30)
        val data = ClockData(alarms = listOf(Alarm(1, 7, 30))).alarmRang(1).snoozeAlarm(1, now)
        assertEquals(now + SNOOZE_LENGTH, nextRing(data.alarms.single(), now, zone))
        assertTrue(data.alarms.single().enabled)
    }

    @Test fun ringingTurnsOffOnlyOneTimeAlarms() {
        val data = ClockData(alarms = listOf(Alarm(1, 7, 0), Alarm(2, 7, 0, setOf(DayOfWeek.MONDAY)))).alarmRang(1).alarmRang(2)
        assertFalse(data.alarms[0].enabled)
        assertTrue(data.alarms[1].enabled)
    }

    @Test fun savingAddsEditsAndDeletes() {
        var data = ClockData().saveAlarm(Alarm(0, 6, 15, label = "  Gym  ")).saveAlarm(Alarm(0, 9, 0))
        assertEquals(listOf(1L, 2L), data.alarms.map { it.id })
        assertEquals("Gym", data.alarms[0].label)
        data = data.enableAlarm(1, false).saveAlarm(data.alarms[0].copy(hour = 6, minute = 45, enabled = false))
        assertEquals(6 to 45, data.alarms[0].hour to data.alarms[0].minute)
        assertTrue("Saving an alarm turns it on", data.alarms[0].enabled)
        assertEquals(listOf(2L), data.deleteAlarm(1).alarms.map { it.id })
    }

    @Test fun timerPausesResumesAndGainsMinutes() {
        var data = ClockData().startTimer(60_000, 0)
        assertEquals(60_000, data.timer.remaining(0))
        data = data.pauseTimer(20_000)
        assertEquals(40_000, data.timer.remaining(90_000))
        data = data.addTimerMinute(90_000).resumeTimer(100_000)
        assertEquals(200_000L, data.timer.endsAt)
        assertTrue(data.resetTimer().timer.idle)
        assertEquals(60_000, data.resetTimer().timer.duration)
    }

    @Test fun stopwatchBanksTimeAndLaps() {
        var data = ClockData().startStopwatch(1_000).lapStopwatch(4_000).pauseStopwatch(6_000)
        assertEquals(5_000, data.stopwatch.elapsed(60_000))
        assertEquals(listOf(3_000L), data.stopwatch.laps)
        data = data.startStopwatch(10_000)
        assertEquals(7_000, data.stopwatch.elapsed(12_000))
        assertEquals(Stopwatch(), data.resetStopwatch().stopwatch)
    }

    @Test fun clockDataSurvivesEncoding() {
        val data = ClockData(listOf(Alarm(3, 6, 5, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "Run", false, 123L)), listOf("Asia/Kolkata"),
            ClockTimer(90_000, left = 30_000), Stopwatch(500, 1_200, listOf(700)))
        assertEquals(data, decodeClock(encodeClock(data)))
        assertEquals(ClockData(), decodeClock(null))
        assertEquals(ClockData(), decodeClock("not json"))
    }

    @Test fun textHelpers() {
        assertEquals("Weekdays", alarmDays(DayOfWeek.entries.take(5).toSet()))
        assertEquals("Mon, Wed", alarmDays(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))
        assertEquals("Once", alarmDays(emptySet()))
        assertEquals("7 h 12 min", untilText((7 * 60 + 12) * 60_000L))
        assertEquals("1 min", untilText(5_000))
        assertEquals("8 s", durationText(8_000))
        assertEquals("1 h 5 min", durationText(65 * 60_000L))
        assertEquals("1 min 30 s", durationText(90_000))
        assertEquals(330_000, timerDigitsToMillis("530"))
        assertEquals(3_723_000, timerDigitsToMillis("10203"))
        assertEquals("01:02.34", stopwatchText(62_345))
        assertEquals("00:01", timerText(200))
        assertEquals("1:00:00", timerText(3_600_000))
    }

    @Test fun worldClockHelpers() {
        assertTrue(zoneMatches("tok", emptyList()).contains("Asia/Tokyo"))
        assertFalse(zoneMatches("tok", listOf("Asia/Tokyo")).contains("Asia/Tokyo"))
        assertEquals("New York", zoneCity("America/New_York"))
        val noon = ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("+5 h 30 min", zoneDifference(ZoneId.of("Asia/Kolkata"), noon, ZoneId.of("UTC")))
        assertEquals("Same time", zoneDifference(ZoneId.of("UTC"), noon, ZoneId.of("UTC")))
        assertEquals("Tomorrow", zoneDay(ZoneId.of("Pacific/Kiritimati"), noon, ZoneId.of("UTC")))
    }
}
