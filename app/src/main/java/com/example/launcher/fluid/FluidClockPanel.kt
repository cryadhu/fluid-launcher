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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

internal val clockTabs = listOf("Alarm", "World clock", "Timer", "Stopwatch")

private object ClockColors {
    val purple = Color(0xFF6B49F7)
    val ink @Composable get() = if (LocalGlass.current.dark) Color(0xFFF2EEFF) else Color(0xFF211D4D)
    val muted @Composable get() = if (LocalGlass.current.dark) Color(0xFFBCB8CD) else Color(0xFF656775)
    val fastest @Composable get() = if (LocalGlass.current.dark) Color(0xFF63D5A5) else Color(0xFF23A875)
    val slowest @Composable get() = if (LocalGlass.current.dark) Color(0xFFFF7B85) else Color(0xFFE63B4A)
}

@Composable private fun clockText(size: Int = 14, bold: Boolean = false, muted: Boolean = false) =
    F.text(size, bold).copy(color = if (muted) ClockColors.muted else ClockColors.ink)

/** Clock surfaces deliberately do not sample the launcher's wallpaper. */
@Composable private fun Modifier.clockSurface(radius: Dp = 30.dp, highlighted: Boolean = false): Modifier {
    val dark = LocalGlass.current.dark
    val shape = RoundedCornerShape(radius)
    val fill = if (highlighted) {
        if (dark) Color(0xFF514864).copy(alpha = .8f) else Color(0xFFD2D0E4).copy(alpha = .82f)
    } else if (dark) Color(0xFF363440).copy(alpha = .76f) else Color.White.copy(alpha = .58f)
    return shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .06f))
        .clip(shape).background(fill).border(1.dp, Color.White.copy(alpha = if (dark) .18f else .88f), shape)
}

@Composable private fun ClockButton(label: String, modifier: Modifier = Modifier, primary: Boolean = false,
    glyph: String? = null, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val base = modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else .45f)
    Row((if (primary) base.clip(CircleShape).background(if (danger) Color(0xFFCF3844) else ClockColors.purple) else base.clockSurface(50.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        glyph?.let { Glyph(it, if (primary) Color.White else ClockColors.purple, Modifier.size(18.dp)) }
        Text(label, style = clockText(14, true), color = if (primary) Color.White else ClockColors.ink, maxLines = 1)
    }
}

@Composable private fun ClockIcon(glyph: String, label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(modifier.size(if (primary) 48.dp else 44.dp).clockSurface(50.dp)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Glyph(glyph, if (primary) ClockColors.purple else ClockColors.muted, Modifier.size(if (primary) 25.dp else 18.dp))
    }
}

@Composable internal fun ClockPanel(state: FluidState, model: FluidModel, start: String?) {
    val context = LocalContext.current
    val store = remember(context) { ClockStore.get(context) }
    val data by store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable(start) { mutableIntStateOf(clockTabs.indexOf(start).coerceAtLeast(0)) }
    val dark = LocalGlass.current.dark
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }.testTag("fluid-panel-Clock")) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(if (dark) listOf(Color(0xFF272F34), Color(0xFF302F39), Color(0xFF453638))
                else listOf(Color(0xFFD8DFDC), Color(0xFFE8EBE6), Color(0xFFF4E2D2))))
            val sweep = Path().apply {
                moveTo(-size.width * .12f, size.height * .72f)
                cubicTo(size.width * .26f, size.height * .43f, size.width * .66f, size.height * .3f, size.width * 1.12f, size.height * .22f)
            }
            drawPath(sweep, Color.White.copy(alpha = if (dark) .22f else .82f), style = Stroke(2.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            key(tab) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (clockTabs[tab]) {
                        "Alarm" -> AlarmTab(data, store, state.now, model)
                        "World clock" -> WorldTab(data, store, state.now)
                        "Timer" -> TimerTab(data, store)
                        else -> StopwatchTab(data, store)
                    }
                }
            }
            ClockTabBar(tab) { tab = it }
        }
    }
}

@Composable private fun ClockHeader(title: String, subtitle: String, addLabel: String? = null, add: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = clockText(32).copy(fontWeight = FontWeight.Light), maxLines = 1)
            Text(subtitle, style = clockText(14, muted = true), modifier = Modifier.padding(top = 2.dp))
        }
        if (addLabel != null) ClockIcon("plus", addLabel, primary = true, onClick = add)
    }
}

@Composable private fun ClockTabBar(tab: Int, select: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().clockSurface(50.dp).padding(6.dp).testTag("clock-tabs"),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        clockTabs.forEachIndexed { index, label ->
            val on = tab == index
            val color = if (on) ClockColors.purple else ClockColors.muted
            Column(Modifier.weight(1f).clip(CircleShape)
                .background(if (on) ClockColors.purple.copy(alpha = if (LocalGlass.current.dark) .3f else .14f) else Color.Transparent)
                .clickable(role = Role.Tab) { select(index) }.semantics { selected = on }
                .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ClockTabIcon(label, color)
                Text(label, style = clockText(10, true), color = color, maxLines = 1)
            }
        }
    }
}

@Composable private fun ClockTabIcon(label: String, color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val unit = size.width / 24f
        val stroke = 1.7f * unit
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke, StrokeCap.Round)
        if (label == "World clock") {
            drawCircle(color, 9f * unit, style = Stroke(stroke))
            drawOval(color, Offset(8f * unit, 3f * unit), Size(8f * unit, 18f * unit), style = Stroke(stroke))
            line(3f, 12f, 21f, 12f)
        } else {
            drawCircle(color, 8f * unit, Offset(12f * unit, 13f * unit), style = Stroke(stroke))
            line(12f, 9f, 12f, 13f)
            if (label == "Alarm") {
                line(12f, 13f, 15f, 15f)
                line(3f, 5f, 6f, 2f); line(18f, 2f, 21f, 5f)
            } else {
                line(9f, 1f, 15f, 1f); line(12f, 1f, 12f, 5f)
                if (label == "Stopwatch") line(18f, 5f, 20f, 3f)
            }
        }
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
        Row(Modifier.fillMaxWidth().clockSurface(22.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = clockText(12), modifier = Modifier.weight(1f))
            ClockButton("Allow", primary = true) { fix() }
        }
    }
}

@Composable private fun TimeText(hour: Int, minute: Int, size: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time = LocalTime.of(hour, minute)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(time.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")), style = clockText(size).copy(fontWeight = FontWeight.Light, fontSize = size.sp))
        if (!DateFormat.is24HourFormat(context)) Text(time.format(DateTimeFormatter.ofPattern("a")), style = clockText((size / 2.6f).toInt().coerceAtLeast(11), true), modifier = Modifier.padding(start = 4.dp, bottom = (size / 7).dp))
    }
}

@Composable private fun AlarmTab(data: ClockData, store: ClockStore, now: Long, model: FluidModel) {
    val access = rememberRingAccess()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    fun armed(alarm: Alarm) {
        access()
        val at = nextRing(alarm.copy(enabled = true, snoozedUntil = null), System.currentTimeMillis()) ?: return
        model.message("Alarm set for ${untilText(at - System.currentTimeMillis())} from now")
    }
    fun save(alarm: Alarm) { store.update { it.saveAlarm(alarm) }; armed(alarm); editing = null }
    val next = data.alarms.mapNotNull { nextRing(it, now) }.minOrNull()
    val local = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(local)
    val nextTime = next?.let { Instant.ofEpochMilli(it).atZone(local) }
    val nextDay = when (nextTime?.toLocalDate()) {
        today.toLocalDate() -> "Today"
        today.toLocalDate().plusDays(1) -> "Tomorrow"
        else -> nextTime?.format(DateTimeFormatter.ofPattern("EEEE"))
    }
    ClockHeader("Alarms", today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), "Add alarm") { editing = NEW_ALARM }
    Row(Modifier.fillMaxWidth().clockSurface().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (next == null) "No alarms on" else "Next alarm · $nextDay", style = clockText(12, muted = true))
            if (nextTime != null) TimeText(nextTime.hour, nextTime.minute, 28, Modifier.padding(top = 3.dp))
        }
        if (next != null) Box(Modifier.clip(CircleShape).background(ClockColors.purple.copy(alpha = .13f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("in ${untilText(next - now)}", style = clockText(12, true), color = ClockColors.purple)
        }
    }
    if (data.alarms.any { it.enabled }) RingAccessHints()
    SystemAlarmCard(data, store) { armed(it) }
    if (editing == NEW_ALARM) key(NEW_ALARM) { AlarmEditor(null, ::save, { editing = null }, null) }
    if (data.alarms.isEmpty() && editing == null) Text("Add an alarm and it will ring here, even when the screen is locked.", style = clockText(13, muted = true))
    data.alarms.sortedBy { it.hour * 60 + it.minute }.forEach { alarm ->
        key(alarm.id) {
            if (editing == alarm.id) AlarmEditor(alarm, ::save, { editing = null }) { store.update { it.deleteAlarm(alarm.id) }; editing = null }
            else AlarmCard(alarm, now, { on -> store.update { it.enableAlarm(alarm.id, on) }; if (on) armed(alarm) }) { editing = alarm.id }
        }
    }
}

private const val NEW_ALARM = 0L

@Composable private fun SystemAlarmCard(data: ClockData, store: ClockStore, armed: (Alarm) -> Unit) {
    val context = LocalContext.current
    var at by remember { mutableStateOf<Long?>(null) }
    LifecycleResumeEffect(Unit) { at = store.systemAlarm(); onPauseOrDispose { } }
    val time = importableAlarm(data.alarms, at) ?: return
    Row(Modifier.fillMaxWidth().clockSurface(22.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).testTag("system-alarm"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Next alarm in your Clock app", style = clockText(13, true))
            Text(time.format(DateTimeFormatter.ofPattern("EEE")) + " " + clockTime(context, time.toInstant().toEpochMilli()) + " · the original stays there", style = clockText(11, muted = true))
        }
        ClockButton("Import", primary = true) {
            val alarm = Alarm(NEW_ALARM, time.hour, time.minute)
            store.update { it.saveAlarm(alarm) }
            armed(alarm)
        }
    }
}

@Composable private fun AlarmCard(alarm: Alarm, now: Long, toggle: (Boolean) -> Unit, edit: () -> Unit) {
    val context = LocalContext.current
    val snoozed = alarm.snoozedUntil?.takeIf { it > now }
    Row(Modifier.fillMaxWidth().clockSurface().clickable(role = Role.Button, onClickLabel = "Edit alarm", onClick = edit)
        .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp).testTag("alarm-${alarm.id}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else .5f)) {
            TimeText(alarm.hour, alarm.minute, 36)
            if (alarm.label.isNotBlank()) Text(alarm.label, style = clockText(14, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(alarmDays(alarm.days), style = clockText(12, muted = true))
            if (snoozed != null) Text("Snoozed until ${clockTime(context, snoozed)}", style = clockText(11, muted = true))
        }
        Switch(alarm.enabled, toggle, Modifier.semantics { contentDescription = "Alarm on" }.testTag("alarm-switch-${alarm.id}"),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ClockColors.purple, uncheckedTrackColor = ClockColors.muted.copy(alpha = .2f), uncheckedBorderColor = Color.Transparent))
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
    Column(Modifier.fillMaxWidth().clockSurface(28.dp).border(1.5.dp, ClockColors.purple.copy(alpha = .55f), RoundedCornerShape(28.dp)).padding(18.dp).testTag("alarm-editor"),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (alarm == null) "New alarm" else "Edit alarm", style = clockText(15, true), modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(WHEEL_ROW).clip(RoundedCornerShape(18.dp)).background(ClockColors.purple.copy(alpha = .12f)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (twentyFour) Wheel((0..23).toList(), hour, "Hour", "%02d") { hour = it }
                else Wheel((1..12).toList(), (hour + 11) % 12 + 1, "Hour", "%d") { hour = it % 12 + if (hour >= 12) 12 else 0 }
                Text(":", style = clockText(30).copy(fontWeight = FontWeight.Light, fontSize = 30.sp))
                Wheel((0..59).toList(), minute, "Minute", "%02d") { minute = it }
                if (!twentyFour) Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AM" to false, "PM" to true).forEach { (name, pm) ->
                        val on = (hour >= 12) == pm
                        Box(Modifier.clip(CircleShape).then(if (on) Modifier.background(ClockColors.purple) else Modifier.border(1.2.dp, ClockColors.muted.copy(alpha = .4f), CircleShape))
                            .clickable(role = Role.RadioButton) { if (!on) hour = (hour + 12) % 24 }.semantics { selected = on }
                            .padding(horizontal = 12.dp, vertical = 6.dp)) { Text(name, style = clockText(12, true), color = if (on) Color.White else ClockColors.ink) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                val on = day in chosen
                Box(Modifier.size(36.dp).clip(CircleShape).then(if (on) Modifier.background(ClockColors.purple) else Modifier.border(1.2.dp, ClockColors.muted.copy(alpha = .4f), CircleShape))
                    .clickable(role = Role.Checkbox) { days = if (on) days - day.value else days + day.value }
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault()); selected = on }.testTag("alarm-day-${day.name}"), contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = clockText(12, true), color = if (on) Color.White else ClockColors.ink)
                }
            }
        }
        Text(if (chosen.isEmpty()) "Rings once" else "Repeats: ${alarmDays(chosen)}", style = clockText(11, muted = true), modifier = Modifier.fillMaxWidth())
        TextField(label, { label = it.take(ALARM_LABEL_LIMIT) }, Modifier.fillMaxWidth().heightIn(min = 44.dp).clockSurface(24.dp).testTag("alarm-label"), singleLine = true,
            placeholder = { Text("Label", style = clockText(13, muted = true)) }, textStyle = clockText(), colors = clockFieldColors())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (delete != null) ClockIcon("bin", "Delete alarm", onClick = delete)
            Spacer(Modifier.weight(1f))
            ClockButton("Cancel", onClick = cancel)
            ClockButton("Save", primary = true, glyph = "check") { save(Alarm(alarm?.id ?: NEW_ALARM, hour, minute, chosen, label)) }
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
                Text(format.format(item), style = clockText(if (distance == 0) 30 else 22).copy(fontWeight = FontWeight.Light, fontSize = (if (distance == 0) 30 else 22).sp),
                    color = if (distance == 0) ClockColors.ink else ClockColors.muted.copy(alpha = .55f))
            }
        }
    }
}

@Composable private fun WorldTab(data: ClockData, store: ClockStore, now: Long) {
    val context = LocalContext.current
    val local = ZoneId.systemDefault()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    ClockHeader("World clock", "${data.zones.size + 1} cities", "Add a city") { searching = true }
    if (searching) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        TextField(query, { query = it.take(40) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).clockSurface(24.dp)
            .focusRequester(focus).testTag("world-search"), singleLine = true,
            placeholder = { Text("Add a city", style = clockText(13, muted = true)) }, textStyle = clockText(),
            leadingIcon = { Glyph("search", ClockColors.ink, Modifier.size(18.dp)) },
            trailingIcon = { IconButton(onClick = { searching = false; query = ""; keyboard?.hide() }) {
                Glyph("close", ClockColors.muted, Modifier.size(18.dp).semantics { contentDescription = "Close city search" })
            } }, colors = clockFieldColors())
        val matches = remember(query, data.zones) { zoneMatches(query, data.zones) }
        if (query.isNotBlank() && matches.isEmpty()) Text("No matching cities", style = clockText(13, muted = true))
        matches.forEach { id ->
            Row(Modifier.fillMaxWidth().clockSurface(22.dp).clickable(role = Role.Button, onClickLabel = "Add ${zoneCity(id)}") {
                store.update { it.addZone(id) }; query = ""; searching = false; keyboard?.hide()
            }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(zoneCity(id), style = clockText(14, true)); Text(zoneRegion(id), style = clockText(11, muted = true)) }
                Text(clockTime(context, now, ZoneId.of(id)), style = clockText(13, muted = true))
                Glyph("plus", ClockColors.purple, Modifier.padding(start = 12.dp).size(18.dp))
            }
        }
    }
    WorldCard(local, now, local = true)
    data.zones.forEach { id ->
        key(id) { WorldCard(ZoneId.of(id), now, remove = { store.update { it.removeZone(id) } }) }
    }
}

@Composable private fun clockFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = ClockColors.ink, unfocusedTextColor = ClockColors.ink, cursorColor = ClockColors.purple)

@Composable private fun WorldCard(zone: ZoneId, now: Long, local: Boolean = false, remove: (() -> Unit)? = null) {
    val time = Instant.ofEpochMilli(now).atZone(zone)
    val difference = zoneDifference(zone, now)
    val offset = when {
        difference.startsWith("+") -> "${difference.drop(1)} ahead"
        difference.startsWith("−") -> "${difference.drop(1)} behind"
        else -> difference
    }
    Row(Modifier.fillMaxWidth().clockSurface(highlighted = local).testTag(if (local) "world-local" else "world-${zone.id}")
        .padding(start = 14.dp, end = if (remove == null) 18.dp else 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WorldFace(time.hour, time.minute, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(zoneCity(zone.id), style = clockText(15, true), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (local) "Local time · ${time.format(DateTimeFormatter.ofPattern("z"))}" else offset, style = clockText(11, muted = true))
        }
        Column(horizontalAlignment = Alignment.End) {
            TimeText(time.hour, time.minute, 25)
            val day = zoneDay(zone, now)
            Text(day, style = clockText(11, true), color = if (day == "Today") ClockColors.muted else ClockColors.purple)
        }
        if (remove != null) IconButton(onClick = remove, modifier = Modifier.size(44.dp).semantics { contentDescription = "Remove ${zoneCity(zone.id)}" }) {
            Glyph("bin", ClockColors.muted, Modifier.size(16.dp))
        }
    }
}

@Composable private fun WorldFace(hour: Int, minute: Int, modifier: Modifier) {
    val night = hour < 6 || hour >= 18
    val face = if (night) Color(0xFF2C2948) else Color.White.copy(alpha = .82f)
    val hands = if (night) Color(0xFFF5F3FC) else Color(0xFF211D4D)
    Canvas(modifier) {
        val radius = size.minDimension / 2
        drawCircle(face, radius)
        fun hand(degrees: Double, length: Float, width: Float) {
            val angle = Math.toRadians(degrees - 90)
            drawLine(hands, center, Offset(center.x + cos(angle).toFloat() * radius * length,
                center.y + sin(angle).toFloat() * radius * length), width.dp.toPx(), StrokeCap.Round)
        }
        hand((hour % 12 + minute / 60.0) * 30, .5f, 2.5f)
        hand(minute * 6.0, .74f, 1.8f)
        drawCircle(hands, 2.dp.toPx())
    }
}

@Composable private fun ClockDial(progress: Float, stopwatch: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalGlass.current.dark
    val ink = ClockColors.ink
    val accent = ClockColors.purple
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 270.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val radius = size.minDimension / 2 - stroke
                val track = Color.White.copy(alpha = if (dark) .22f else .95f)
                drawCircle(Color.White.copy(alpha = if (dark) .06f else .42f))
                drawCircle(track, radius, style = Stroke(stroke))
                val angle = Math.toRadians(progress * 360.0 - 90.0)
                val tip = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                if (stopwatch) {
                    repeat(4) { tick ->
                        val a = Math.toRadians(tick * 90.0)
                        drawLine(ink, Offset(center.x + (radius - 5.dp.toPx()) * cos(a).toFloat(), center.y + (radius - 5.dp.toPx()) * sin(a).toFloat()),
                            Offset(center.x + (radius + 5.dp.toPx()) * cos(a).toFloat(), center.y + (radius + 5.dp.toPx()) * sin(a).toFloat()), 2.dp.toPx(), StrokeCap.Round)
                    }
                    drawLine(accent, center, tip, 3.dp.toPx(), StrokeCap.Round)
                    drawCircle(accent, 7.dp.toPx(), tip)
                    drawCircle(ink, 5.dp.toPx())
                } else if (progress > 0f) {
                    drawArc(accent, -90f, 360f * progress, false,
                        topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    drawCircle(accent, stroke / 2, tip)
                }
            }
            content()
        }
    }
}

@Composable private fun StopwatchTab(data: ClockData, store: ClockStore) {
    val watch = data.stopwatch
    val now = rememberTicker(watch.running)
    val elapsed = watch.elapsed(now)
    ClockHeader("Stopwatch", if (watch.running) "Running" else if (elapsed > 0) "Paused" else "Ready")
    ClockDial(elapsed % 60_000 / 60_000f, stopwatch = true) {
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stopwatchText(elapsed), style = clockText(if (elapsed >= 3_600_000) 27 else 34).copy(fontWeight = FontWeight.Light), modifier = Modifier.testTag("stopwatch-time"))
            if (elapsed > 0) Text("Lap ${watch.laps.size + 1} · ${stopwatchText(elapsed - (watch.laps.lastOrNull() ?: 0L))}", style = clockText(12, muted = true))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClockButton(if (watch.running) "Lap" else "Reset", Modifier.weight(1f).heightIn(min = 56.dp), enabled = elapsed > 0) {
            val t = System.currentTimeMillis()
            if (watch.running) store.update { it.lapStopwatch(t) } else store.update { it.resetStopwatch() }
        }
        ClockButton(if (watch.running) "Pause" else if (elapsed > 0) "Resume" else "Start", Modifier.weight(1f).heightIn(min = 56.dp),
            primary = true, danger = watch.running, glyph = if (watch.running) "pause" else "play") {
            val t = System.currentTimeMillis()
            store.update { if (watch.running) it.pauseStopwatch(t) else it.startStopwatch(t) }
        }
    }
    val laps = remember(watch.laps) { watch.laps.mapIndexed { i, total -> total - (watch.laps.getOrNull(i - 1) ?: 0L) } }
    val fastest = laps.minOrNull().takeIf { laps.size > 1 && laps.minOrNull() != laps.maxOrNull() }
    val slowest = laps.maxOrNull().takeIf { fastest != null }
    if (laps.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Laps", style = clockText(14, muted = true), modifier = Modifier.weight(1f))
            if (fastest != null) {
                Text("Fastest", style = clockText(11, true), color = ClockColors.fastest)
                Text("Slowest", style = clockText(11, true), color = ClockColors.slowest, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Column(Modifier.fillMaxWidth().clockSurface().padding(horizontal = 18.dp, vertical = 8.dp)) {
            laps.indices.reversed().forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).testTag("lap-${i + 1}"), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lap ${i + 1}", style = clockText(13, true), modifier = Modifier.weight(1f))
                    Text(stopwatchText(laps[i]), style = clockText(if (laps[i] >= 3_600_000) 14 else 20).copy(fontWeight = FontWeight.Light),
                        color = when (laps[i]) { fastest -> ClockColors.fastest; slowest -> ClockColors.slowest; else -> ClockColors.ink })
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total", style = clockText(10, muted = true))
                        Text(stopwatchText(watch.laps[i]), style = clockText(11, muted = true))
                    }
                }
                if (i > 0) HorizontalDivider(color = ClockColors.muted.copy(alpha = .14f))
            }
        }
    }
}


@Composable private fun TimerTab(data: ClockData, store: ClockStore) {
    val context = LocalContext.current
    val timer = data.timer
    val access = rememberRingAccess()
    var digits by rememberSaveable { mutableStateOf("") }
    var replaceMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    val running = timer.endsAt != null
    val now = rememberTicker(running)
    val left = timer.remaining(now)
    val done = running && left <= 0
    ClockHeader("Timer", when { timer.idle -> "Set a duration"; done -> "Time's up"; running -> "Running"; else -> "Paused" })
    if (timer.idle) {
        val value = timerDigitsToMillis(digits)
        val padded = digits.padStart(6, '0')
        ClockDial(0f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.testTag("timer-entry"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    listOf("h", "m", "s").forEachIndexed { i, unit ->
                        Text(padded.substring(i * 2, i * 2 + 2), style = clockText(34).copy(fontWeight = FontWeight.Light),
                            color = if (digits.length >= (2 - i) * 2 + 1) ClockColors.ink else ClockColors.muted.copy(alpha = .55f))
                        Text(unit, style = clockText(12, true, muted = true), modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = 7.dp))
                    }
                }
                Text("Hours · minutes · seconds", style = clockText(11, muted = true), modifier = Modifier.padding(top = 4.dp))
            }
        }
        QuickTimers { digits = "${it}00" }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "del")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    Box(Modifier.weight(1f).height(48.dp).clockSurface(28.dp).clickable(role = Role.Button) {
                        digits = when (key) { "del" -> digits.dropLast(1); else -> (digits + key).trimStart('0').take(6) }
                    }.semantics { contentDescription = if (key == "del") "Delete digit" else key }, contentAlignment = Alignment.Center) {
                        if (key == "del") Glyph("back", ClockColors.ink, Modifier.size(20.dp)) else Text(key, style = clockText(22))
                    }
                }
            }
        }
        ClockButton("Start", Modifier.fillMaxWidth().heightIn(min = 56.dp), primary = true, glyph = "play", enabled = value > 0) {
            store.update { it.startTimer(value, System.currentTimeMillis()) }; access(); digits = ""
        }
    } else {
        ClockDial((1f - left.toFloat() / timer.duration).coerceIn(0f, 1f)) {
            Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (done) "Time's up" else timerText(left),
                    style = clockText(if (done) 32 else if (left >= 3_600_000) 42 else 56).copy(fontWeight = FontWeight.Light),
                    modifier = Modifier.testTag("timer-time"), maxLines = 1)
                Text(if (done) "−${timerText(-left)}" else "of ${timerText(timer.duration)}", style = clockText(13, muted = true))
                if (!done && timer.endsAt != null) Text("ends ${clockTime(context, timer.endsAt)}", style = clockText(12, muted = true))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (done) {
                ClockButton("+1 min", Modifier.weight(1f).heightIn(min = 56.dp)) { store.snooze(ClockStore.TIMER_KEY) }
                ClockButton("Stop", Modifier.weight(1f).heightIn(min = 56.dp), primary = true, danger = true) { store.dismiss(ClockStore.TIMER_KEY) }
            } else {
                ClockButton("Cancel", Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = "Reset timer" }) { store.update { it.resetTimer() } }
                ClockButton(if (running) "Pause" else "Resume", Modifier.weight(1.2f).heightIn(min = 56.dp),
                    primary = true, glyph = if (running) "pause" else "play") {
                    val t = System.currentTimeMillis()
                    store.update { if (running) it.pauseTimer(t) else it.resumeTimer(t) }
                }
                Box(Modifier.size(56.dp).clockSurface(50.dp).clickable(role = Role.Button) {
                    store.update { it.addTimerMinute(System.currentTimeMillis()) }
                }.semantics { contentDescription = "Add one minute" }, contentAlignment = Alignment.Center) {
                    Text("+1:00", style = clockText(12, true))
                }
            }
        }
        QuickTimers { replaceMinutes = it }
        RingAccessHints()
    }
    // There is one real timer in the store. Ask before a preset replaces it.
    replaceMinutes?.let { minutes ->
        AlertDialog(onDismissRequest = { replaceMinutes = null },
            title = { Text("Replace current timer?", style = clockText(20, true)) },
            text = { Text("Start a new $minutes minute timer instead.", style = clockText()) },
            confirmButton = { TextButton(onClick = {
                store.update { it.startTimer(minutes * 60_000L, System.currentTimeMillis()) }
                access(); replaceMinutes = null
            }) { Text("Start", color = ClockColors.purple) } },
            dismissButton = { TextButton(onClick = { replaceMinutes = null }) { Text("Cancel", color = ClockColors.muted) } })
    }
}

@Composable private fun QuickTimers(select: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick start", style = clockText(14, muted = true), modifier = Modifier.padding(start = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10, 15, 30).forEach { minutes ->
                Box(Modifier.weight(1f).height(44.dp).clockSurface(50.dp).clickable(role = Role.Button) { select(minutes) }, contentAlignment = Alignment.Center) {
                    Text("$minutes min", style = clockText(11, true), maxLines = 1)
                }
            }
        }
    }
}
