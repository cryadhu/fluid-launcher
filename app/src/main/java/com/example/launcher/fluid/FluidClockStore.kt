package com.example.launcher.fluid

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.launcher.MainActivity
import com.example.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Process-wide clock state shared by the launcher, the ringing screen and the alarm receivers. */
class ClockStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("fluid_clock_v1", 0)
    private val mutable = MutableStateFlow(decodeClock(prefs.getString("data", null)))
    val state: StateFlow<ClockData> = mutable.asStateFlow()
    private val ringingNow = MutableStateFlow(emptySet<String>())
    val ringing: StateFlow<Set<String>> = ringingNow.asStateFlow()

    fun update(change: (ClockData) -> ClockData) {
        val (old, new) = synchronized(this) { val old = mutable.value; old to change(old).also { mutable.value = it } }
        if (old == new) return
        prefs.edit().putString("data", encodeClock(new)).apply()
        old.alarms.filter { a -> new.alarms.none { it.id == a.id } }.forEach { ClockAlarms.cancelAlarm(context, it.id) }
        ClockAlarms.schedule(context, new)
    }

    fun reschedule() = ClockAlarms.schedule(context, mutable.value)

    internal fun ring(key: String) { ringingNow.value = ringingNow.value + key }
    /** Silences the alarm or timer behind [key], whichever surface handled it. */
    fun silence(key: String) {
        ringingNow.value = ringingNow.value - key
        NotificationManagerCompat.from(context).cancel(notificationId(key))
    }

    fun dismiss(key: String) {
        silence(key)
        if (key == TIMER_KEY) update { it.resetTimer() }
    }

    fun snooze(key: String) {
        silence(key)
        val now = System.currentTimeMillis()
        if (key == TIMER_KEY) update { it.copy(timer = ClockTimer(it.timer.duration, endsAt = now + 60_000)) }
        else alarmId(key)?.let { id -> update { it.snoozeAlarm(id, now) } }
    }

    companion object {
        @Volatile private var instance: ClockStore? = null
        fun get(context: Context): ClockStore = instance ?: synchronized(this) { instance ?: ClockStore(context.applicationContext).also { instance = it } }
        const val TIMER_KEY = "timer"
        fun alarmKey(id: Long) = "alarm:$id"
        fun alarmId(key: String) = key.removePrefix("alarm:").takeIf { key.startsWith("alarm:") }?.toLongOrNull()
        internal fun notificationId(key: String) = alarmId(key)?.let { 4000 + it.toInt() } ?: 3999
    }
}

internal object ClockAlarms {
    const val FIRE = "com.cry.fluid.clock.FIRE"
    const val DISMISS = "com.cry.fluid.clock.DISMISS"
    const val SNOOZE = "com.cry.fluid.clock.SNOOZE"
    const val EXTRA_KEY = "clock_key"
    private const val CHANNEL = "fluid_alarms_v1"

    private fun broadcast(context: Context, action: String, key: String) = PendingIntent.getBroadcast(context, ClockStore.notificationId(key),
        Intent(context, ClockReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun showClock(context: Context) = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PANEL, "Clock:Alarm")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancelAlarm(context: Context, id: Long) = context.getSystemService(AlarmManager::class.java).cancel(broadcast(context, FIRE, ClockStore.alarmKey(id)))

    fun schedule(context: Context, data: ClockData) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        data.alarms.forEach { alarm ->
            val fire = broadcast(context, FIRE, ClockStore.alarmKey(alarm.id))
            val at = nextRing(alarm, now)
            if (at == null) manager.cancel(fire) else exact(manager, at, fire) { manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, showClock(context)), fire) }
        }
        val timer = broadcast(context, FIRE, ClockStore.TIMER_KEY)
        val ends = data.timer.endsAt
        if (ends == null || ends <= now) manager.cancel(timer) else exact(manager, ends, timer) { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ends, timer) }
    }

    /** Falls back to an inexact wake-up when the system has withdrawn exact alarm access. */
    private fun exact(manager: AlarmManager, at: Long, fire: PendingIntent, set: () -> Unit) {
        if ((Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) && runCatching(set).isSuccess) return
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings for your alarms and finished timers"
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 400, 600, 400)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        })
    }

    fun canNotify(context: Context) = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun canFullScreen(context: Context) = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun canExact(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @android.annotation.SuppressLint("MissingPermission") // canNotify checks POST_NOTIFICATIONS first.
    fun fire(context: Context, key: String) {
        val store = ClockStore.get(context)
        val data = store.state.value
        val (title, text) = if (key == ClockStore.TIMER_KEY) {
            if (data.timer.endsAt == null) return
            "Time's up" to "Timer · ${durationText(data.timer.duration)}"
        } else {
            val alarm = data.alarms.find { it.id == ClockStore.alarmId(key) }?.takeIf { it.enabled } ?: return
            store.update { it.alarmRang(alarm.id) }
            alarm.label.ifBlank { "Alarm" } to alarmTime(context, alarm.hour, alarm.minute)
        }
        store.ring(key)
        ensureChannel(context)
        val screen = PendingIntent.getActivity(context, ClockStore.notificationId(key), Intent(context, AlarmActivity::class.java).putExtra(EXTRA_KEY, key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_alarm).setContentTitle(title).setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(false).setContentIntent(screen).setFullScreenIntent(screen, true).setTimeoutAfter(10 * 60_000L)
            .addAction(0, if (key == ClockStore.TIMER_KEY) "+1 min" else "Snooze", broadcast(context, SNOOZE, key))
            .addAction(0, "Stop", broadcast(context, DISMISS, key))
            .build().apply { flags = flags or android.app.Notification.FLAG_INSISTENT }
        if (canNotify(context)) runCatching { NotificationManagerCompat.from(context).notify(ClockStore.notificationId(key), notification) }
        else context.startActivity(Intent(context, AlarmActivity::class.java).putExtra(EXTRA_KEY, key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun alarmTime(context: Context, hour: Int, minute: Int): String =
    java.time.LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))

internal fun clockTime(context: Context, millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))

class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ClockAlarms.EXTRA_KEY) ?: return
        val store = ClockStore.get(context)
        when (intent.action) {
            ClockAlarms.FIRE -> ClockAlarms.fire(context, key)
            ClockAlarms.DISMISS -> store.dismiss(key)
            ClockAlarms.SNOOZE -> store.snooze(key)
        }
    }
}

/** Alarms are lost on reboot and drift with time zone changes, so put them back. */
class ClockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ClockStore.get(context).reschedule() }
}
