@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.launcher.fluid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

internal val clockTabs = listOf("Alarm", "World clock", "Stopwatch", "Timer")

@Composable internal fun ClockPanel(state: FluidState, model: FluidModel, start: String?) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val data by store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(clockTabs.indexOf(start).coerceAtLeast(0)) }
    GlassTabs(clockTabs, tab, Modifier.fillMaxWidth()) { tab = it }
    when (tab) {
        0 -> AlarmTab(data, store, state.now, model)
        1 -> WorldTab(data, store, state.now)
        2 -> StopwatchTab(data, store)
        else -> TimerTab(data, store)
    }
}

/** Asks for notification access the first time something needs to ring. */
@Composable private fun rememberRingAccess(): () -> Unit {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Advances every frame while [active], so seconds and hundredths move smoothly. */
@Composable private fun rememberTicker(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        now = System.currentTimeMillis()
        if (active) while (isActive) withFrameMillis { now = System.currentTimeMillis() }
    }
    return now
}

private fun Context.openSettings(action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName) else intent.data = Uri.parse("package:$packageName")
    runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable private fun RingAccessHints() {
    val context = LocalContext.current
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumed++; onPauseOrDispose { } }
    val access = rememberRingAccess()
    val hints = remember(resumed) {
        listOfNotNull(
            ("Allow notifications so alarms can ring." to {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) access()
                else context.openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }).takeUnless { ClockAlarms.canNotify(context) },
            ("Allow exact alarms so they ring on time." to { context.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }).takeUnless { ClockAlarms.canExact(context) },
            ("Allow full-screen alarms so they show on the lock screen." to { context.openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) }).takeUnless { ClockAlarms.canFullScreen(context) },
        )
    }
    hints.forEach { (text, fix) ->
        Row(Modifier.fillMaxWidth().glass(22.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = F.text(12), modifier = Modifier.weight(1f))
            GlassButton("Allow", primary = true) { fix() }
        }
    }
}

@Composable private fun TimeText(hour: Int, minute: Int, size: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time = LocalTime.of(hour, minute)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(time.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")), style = F.text(size).copy(fontWeight = FontWeight.Light, fontSize = size.sp))
        if (!DateFormat.is24HourFormat(context)) Text(time.format(DateTimeFormatter.ofPattern("a")), style = F.text((size / 2.6f).toInt().coerceAtLeast(11), true), modifier = Modifier.padding(start = 4.dp, bottom = (size / 7).dp))
    }
}

@Composable private fun AlarmTab(data: ClockData, store: ClockStore, now: Long, model: FluidModel) {
    val context = LocalContext.current
    val access = rememberRingAccess()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    fun armed(alarm: Alarm) {
        access()
        val at = nextRing(alarm.copy(enabled = true, snoozedUntil = null), System.currentTimeMillis()) ?: return
        model.message("Alarm set for ${untilText(at - System.currentTimeMillis())} from now")
    }
    fun save(alarm: Alarm) { store.update { it.saveAlarm(alarm) }; armed(alarm); editing = null }
    val next = data.alarms.mapNotNull { nextRing(it, now) }.minOrNull()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (next == null) "No alarms on" else "Next alarm in ${untilText(next - now)}", style = F.text(15, true))
            if (next != null) Text(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE")) + ", " + clockTime(context, next), style = F.text(11, muted = true))
        }
        if (editing != NEW_ALARM) GlassIcon("plus", "Add alarm", primary = true) { editing = NEW_ALARM }
    }
    if (data.alarms.any { it.enabled }) RingAccessHints()
    SystemAlarmCard(data, store) { armed(it) }
    if (editing == NEW_ALARM) AlarmEditor(null, ::save, { editing = null }, null)
    if (data.alarms.isEmpty() && editing == null) Text("Add an alarm and it will ring here, even when the screen is locked.", style = F.text(13, muted = true))
    data.alarms.sortedBy { it.hour * 60 + it.minute }.forEach { alarm ->
        if (editing == alarm.id) AlarmEditor(alarm, ::save, { editing = null }) { store.update { it.deleteAlarm(alarm.id) }; editing = null }
        else AlarmCard(alarm, now, { on -> store.update { it.enableAlarm(alarm.id, on) }; if (on) armed(alarm) }) { editing = alarm.id }
    }
}

private const val NEW_ALARM = 0L

@Composable private fun SystemAlarmCard(data: ClockData, store: ClockStore, armed: (Alarm) -> Unit) {
    val context = LocalContext.current
    var at by remember { mutableStateOf<Long?>(null) }
    LifecycleResumeEffect(Unit) { at = store.systemAlarm(); onPauseOrDispose { } }
    val time = importableAlarm(data.alarms, at) ?: return
    Row(Modifier.fillMaxWidth().glass(22.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).testTag("system-alarm"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Next alarm in your Clock app", style = F.text(13, true))
            Text(time.format(DateTimeFormatter.ofPattern("EEE")) + " " + clockTime(context, time.toInstant().toEpochMilli()) + " · the original stays there", style = F.text(11, muted = true))
        }
        GlassButton("Import", primary = true) {
            val alarm = Alarm(NEW_ALARM, time.hour, time.minute)
            store.update { it.saveAlarm(alarm) }
            armed(alarm)
        }
    }
}

@Composable private fun AlarmCard(alarm: Alarm, now: Long, toggle: (Boolean) -> Unit, edit: () -> Unit) {
    val context = LocalContext.current
    val snoozed = alarm.snoozedUntil?.takeIf { it > now }
    Row(Modifier.fillMaxWidth().glass(26.dp).clickable(role = Role.Button, onClickLabel = "Edit alarm", onClick = edit)
        .padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 12.dp).testTag("alarm-${alarm.id}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else .45f)) {
            TimeText(alarm.hour, alarm.minute, 34)
            Text(listOfNotNull(alarm.label.ifBlank { null }, alarmDays(alarm.days), snoozed?.let { "Snoozed until ${clockTime(context, it)}" }).joinToString(" · "),
                style = F.text(12, muted = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(alarm.enabled, toggle, Modifier.semantics { contentDescription = "Alarm on" }.testTag("alarm-switch-${alarm.id}"),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = F.purple, uncheckedTrackColor = F.muted.copy(alpha = .2f), uncheckedBorderColor = Color.Transparent))
    }
}

@Composable private fun AlarmEditor(alarm: Alarm?, save: (Alarm) -> Unit, cancel: () -> Unit, delete: (() -> Unit)?) {
    val context = LocalContext.current
    val twentyFour = DateFormat.is24HourFormat(context)
    var hour by rememberSaveable { mutableIntStateOf(alarm?.hour ?: 7) }
    var minute by rememberSaveable { mutableIntStateOf(alarm?.minute ?: 0) }
    var days by rememberSaveable { mutableStateOf<List<Int>>(alarm?.days?.map { it.value } ?: emptyList()) }
    var label by rememberSaveable { mutableStateOf(alarm?.label ?: "") }
    val chosen = days.map(DayOfWeek::of).toSet()
    Column(Modifier.fillMaxWidth().glass(28.dp).border(1.5.dp, F.purple.copy(alpha = .55f), RoundedCornerShape(28.dp)).padding(18.dp).testTag("alarm-editor"),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (alarm == null) "New alarm" else "Edit alarm", style = F.text(15, true), modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(WHEEL_ROW).clip(RoundedCornerShape(18.dp)).background(F.purple.copy(alpha = .12f)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (twentyFour) Wheel((0..23).toList(), hour, "Hour", "%02d") { hour = it }
                else Wheel((1..12).toList(), (hour + 11) % 12 + 1, "Hour", "%d") { hour = it % 12 + if (hour >= 12) 12 else 0 }
                Text(":", style = F.text(30).copy(fontWeight = FontWeight.Light, fontSize = 30.sp))
                Wheel((0..59).toList(), minute, "Minute", "%02d") { minute = it }
                if (!twentyFour) Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AM" to false, "PM" to true).forEach { (name, pm) ->
                        val on = (hour >= 12) == pm
                        Box(Modifier.clip(CircleShape).then(if (on) Modifier.background(F.purple) else Modifier.border(1.2.dp, F.muted.copy(alpha = .4f), CircleShape))
                            .clickable(role = Role.RadioButton) { if (!on) hour = (hour + 12) % 24 }.semantics { selected = on }
                            .padding(horizontal = 12.dp, vertical = 6.dp)) { Text(name, style = F.text(12, true), color = if (on) Color.White else F.ink) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                val on = day in chosen
                Box(Modifier.size(36.dp).clip(CircleShape).then(if (on) Modifier.background(F.purple) else Modifier.border(1.2.dp, F.muted.copy(alpha = .4f), CircleShape))
                    .clickable(role = Role.Checkbox) { days = if (on) days - day.value else days + day.value }
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault()); selected = on }.testTag("alarm-day-${day.name}"), contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = F.text(12, true), color = if (on) Color.White else F.ink)
                }
            }
        }
        Text(if (chosen.isEmpty()) "Rings once" else "Repeats: ${alarmDays(chosen)}", style = F.text(11, muted = true), modifier = Modifier.fillMaxWidth())
        TextField(label, { label = it.take(ALARM_LABEL_LIMIT) }, Modifier.fillMaxWidth().heightIn(min = 44.dp).glass(24.dp).testTag("alarm-label"), singleLine = true,
            placeholder = { Text("Label", style = F.text(13, muted = true)) }, textStyle = F.text(), colors = fieldColors())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (delete != null) GlassIcon("bin", "Delete alarm", onClick = delete)
            Spacer(Modifier.weight(1f))
            GlassButton("Cancel", onClick = cancel)
            GlassButton("Save", primary = true, glyph = "check") { save(Alarm(alarm?.id ?: NEW_ALARM, hour, minute, chosen, label)) }
        }
    }
}

private val WHEEL_ROW = 48.dp

/** An endless, snapping column of numbers; the one under the centre band is the value. */
@Composable private fun Wheel(values: List<Int>, value: Int, name: String, format: String, change: (Int) -> Unit) {
    val n = values.size
    val list = rememberLazyListState(n * 500 + values.indexOf(value).coerceAtLeast(0) - 1)
    val scope = rememberCoroutineScope()
    val centre by remember { derivedStateOf {
        val info = list.layoutInfo
        val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: (list.firstVisibleItemIndex + 1)
    } }
    LaunchedEffect(centre) { values[centre % n].let { if (it != value) change(it) } }
    LazyColumn(Modifier.width(64.dp).height(WHEEL_ROW * 3).semantics { contentDescription = "$name ${format.format(value)}" }.testTag("wheel-$name"),
        state = list, flingBehavior = rememberSnapFlingBehavior(list), horizontalAlignment = Alignment.CenterHorizontally) {
        items(n * 1000) { index ->
            val item = values[index % n]
            val distance = kotlin.math.abs(index - centre)
            Box(Modifier.fillMaxWidth().height(WHEEL_ROW).clickable(role = Role.Button, onClickLabel = "Choose ${format.format(item)}") {
                scope.launch { list.animateScrollToItem(index - 1) }
            }, contentAlignment = Alignment.Center) {
                Text(format.format(item), style = F.text(if (distance == 0) 30 else 22).copy(fontWeight = FontWeight.Light, fontSize = (if (distance == 0) 30 else 22).sp),
                    color = if (distance == 0) F.ink else F.muted.copy(alpha = .55f))
            }
        }
    }
}

@Composable private fun WorldTab(data: ClockData, store: ClockStore, now: Long) {
    val context = LocalContext.current
    val local = ZoneId.systemDefault()
    var query by rememberSaveable { mutableStateOf("") }
    if (query.isBlank()) Column(Modifier.fillMaxWidth().glass(28.dp).padding(20.dp)) {
        Text(clockTime(context, now), style = F.text(44).copy(fontWeight = FontWeight.Light, fontSize = 44.sp))
        Text(Instant.ofEpochMilli(now).atZone(local).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) + " · " + zoneCity(local.id), style = F.text(12, muted = true))
    }
    TextField(query, { query = it.take(40) }, Modifier.fillMaxWidth().heightIn(min = 44.dp).glass(24.dp).testTag("world-search"), singleLine = true,
        placeholder = { Text("Add a city", style = F.text(13, muted = true)) }, textStyle = F.text(), leadingIcon = { Glyph("search", F.ink, Modifier.size(18.dp)) }, colors = fieldColors())
    val matches = remember(query, data.zones) { zoneMatches(query, data.zones) }
    if (query.isNotBlank() && matches.isEmpty()) Text("No matching cities", style = F.text(13, muted = true))
    matches.forEach { id ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { store.update { it.addZone(id) }; query = "" }.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(zoneCity(id), style = F.text(14, true)); Text(zoneRegion(id), style = F.text(11, muted = true)) }
            Text(clockTime(context, now, ZoneId.of(id)), style = F.text(13, muted = true))
            Glyph("plus", F.purple, Modifier.padding(start = 12.dp).size(18.dp))
        }
    }
    data.zones.forEach { id ->
        val zone = ZoneId.of(id)
        Row(Modifier.fillMaxWidth().glass(24.dp).padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).testTag("world-$id"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(zoneCity(id), style = F.text(15, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${zoneDay(zone, now)}, ${zoneDifference(zone, now)}", style = F.text(11, muted = true))
            }
            Text(clockTime(context, now, zone), style = F.text(24).copy(fontWeight = FontWeight.Light, fontSize = 24.sp))
            GlassIcon("bin", "Remove ${zoneCity(id)}", Modifier.size(36.dp)) { store.update { it.removeZone(id) } }
        }
    }
}

/** A glass dial with an arc for [progress] and a bright tip where it ends. */
@Composable private fun ClockDial(progress: Float, content: @Composable BoxScope.() -> Unit) {
    val accent = F.purple
    val track = F.ink.copy(alpha = .08f)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(250.dp).glass(125.dp, elevated = true), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val stroke = 6.dp.toPx()
            drawCircle(track, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val angle = Math.toRadians(360.0 * progress - 90.0)
            val r = size.minDimension / 2
            drawCircle(Color.White, stroke * .9f, Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat()))
            drawCircle(accent, stroke * .55f, Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat()))
        }
        content()
    } }
}

@Composable private fun StopwatchTab(data: ClockData, store: ClockStore) {
    val watch = data.stopwatch
    val now = rememberTicker(watch.running)
    val elapsed = watch.elapsed(now)
    ClockDial(elapsed % 60_000 / 60_000f) {
        Text(stopwatchText(elapsed), style = F.text(40).copy(fontWeight = FontWeight.Light, fontSize = 40.sp), modifier = Modifier.testTag("stopwatch-time"))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassButton(if (watch.running) "Lap" else "Reset", Modifier.weight(1f).alpha(if (elapsed > 0) 1f else .5f)) {
            val t = System.currentTimeMillis()
            if (watch.running) store.update { it.lapStopwatch(t) } else if (elapsed > 0) store.update { it.resetStopwatch() }
        }
        GlassButton(if (watch.running) "Pause" else if (elapsed > 0) "Resume" else "Start", Modifier.weight(1f), primary = true, glyph = if (watch.running) "pause" else "play") {
            val t = System.currentTimeMillis()
            store.update { if (watch.running) it.pauseStopwatch(t) else it.startStopwatch(t) }
        }
    }
    val laps = watch.laps.mapIndexed { i, total -> total - (watch.laps.getOrNull(i - 1) ?: 0L) }
    val fastest = laps.minOrNull().takeIf { laps.size > 2 }
    val slowest = laps.maxOrNull().takeIf { laps.size > 2 }
    if (laps.isNotEmpty()) Column(Modifier.fillMaxWidth().glass(24.dp).padding(horizontal = 18.dp, vertical = 8.dp)) {
        laps.indices.reversed().forEach { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("lap-${i + 1}"), verticalAlignment = Alignment.CenterVertically) {
                Text("Lap ${i + 1}", style = F.text(13, true), modifier = Modifier.weight(1f))
                Text(stopwatchText(laps[i]), style = F.text(13), color = when (laps[i]) { fastest -> F.purple; slowest -> Color(0xFFE0882B); else -> F.ink }, modifier = Modifier.padding(end = 18.dp))
                Text(stopwatchText(watch.laps[i]), style = F.text(13, muted = true))
            }
        }
    }
}


@Composable private fun TimerTab(data: ClockData, store: ClockStore) {
    val timer = data.timer
    val access = rememberRingAccess()
    if (timer.idle) {
        var digits by rememberSaveable { mutableStateOf("") }
        val value = timerDigitsToMillis(digits)
        val padded = digits.padStart(6, '0')
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("timer-entry"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            listOf("h", "m", "s").forEachIndexed { i, unit ->
                Text(padded.substring(i * 2, i * 2 + 2), style = F.text(48).copy(fontWeight = FontWeight.Light, fontSize = 48.sp), color = if (digits.length >= (2 - i) * 2 + 1) F.ink else F.muted.copy(alpha = .55f))
                Text(unit, style = F.text(16, true, muted = true), modifier = Modifier.padding(start = 2.dp, end = 10.dp, bottom = 10.dp))
            }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "del")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    Box(Modifier.weight(1f).height(56.dp).glass(28.dp).clickable(role = Role.Button) {
                        digits = when (key) { "del" -> digits.dropLast(1); else -> (digits + key).trimStart('0').take(6) }
                    }.semantics { contentDescription = if (key == "del") "Delete digit" else key }, contentAlignment = Alignment.Center) {
                        if (key == "del") Glyph("back", F.ink, Modifier.size(20.dp)) else Text(key, style = F.text(22))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10, 15, 30).forEach { minutes ->
                Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(F.purple.copy(alpha = .12f)).clickable(role = Role.Button) { digits = "${minutes}00" }, contentAlignment = Alignment.Center) {
                    Text("$minutes min", style = F.text(11, true), color = F.purple)
                }
            }
        }
        GlassButton("Start", Modifier.fillMaxWidth().alpha(if (value > 0) 1f else .5f), primary = true, glyph = "play") {
            if (value > 0) { store.update { it.startTimer(value, System.currentTimeMillis()) }; access(); digits = "" }
        }
        return
    }
    val running = timer.endsAt != null
    val now = rememberTicker(running)
    val left = timer.remaining(now)
    val done = running && left <= 0
    ClockDial((left.toFloat() / timer.duration).coerceIn(0f, 1f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (done) "Time's up" else timerText(left), style = F.text(if (done) 30 else 44).copy(fontWeight = FontWeight.Light, fontSize = (if (done) 30 else 44).sp), modifier = Modifier.testTag("timer-time"))
            Text(if (done) "−" + timerText(-left) else "of ${durationText(timer.duration)}", style = F.text(12, muted = true))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (done) {
            GlassButton("+1 min", Modifier.weight(1f)) { store.snooze(ClockStore.TIMER_KEY) }
            GlassButton("Stop", Modifier.weight(1f), primary = true) { store.dismiss(ClockStore.TIMER_KEY) }
        } else {
            GlassButton("Reset", Modifier.weight(1f)) { store.update { it.resetTimer() } }
            GlassButton("+1:00", Modifier.weight(1f)) { store.update { it.addTimerMinute(System.currentTimeMillis()) } }
            GlassButton(if (running) "Pause" else "Resume", Modifier.weight(1f), primary = true, glyph = if (running) "pause" else "play") {
                val t = System.currentTimeMillis()
                store.update { if (running) it.pauseTimer(t) else it.resumeTimer(t) }
            }
        }
    }
}
