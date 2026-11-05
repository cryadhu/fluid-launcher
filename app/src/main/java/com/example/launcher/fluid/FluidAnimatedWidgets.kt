@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.example.launcher.data.HomeWeather
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.isActive
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

/** A tap that ignores long presses, which belong to the card for rearranging. */
private fun Modifier.tap(role: Role = Role.Button, onClickLabel: String? = null, onClick: () -> Unit) =
    combinedClickable(role = role, onClickLabel = onClickLabel, onLongClick = {}, onClick = onClick)

private val Violet = Color(0xFF6B4EF0)
private val Teal = Color(0xFF2FB8A0)
private val Blue = Color(0xFF3E78FF)
private val Pink = Color(0xFFE0568F)
private val Amber = Color(0xFFE8A23E)

/** Widgets with a fixed card height; their content fills the card edge to edge. */
internal fun widgetFixedHeight(kind: FluidWidget): Dp? = when (kind) {
    FluidWidget.CLOCK, FluidWidget.WEATHER, FluidWidget.FOCUS, FluidWidget.BATTERY, FluidWidget.HYDRATION -> 156.dp
    FluidWidget.SUN_PATH, FluidWidget.NOW_PLAYING -> 150.dp
    FluidWidget.WIND -> 88.dp
    FluidWidget.BREATHE -> 170.dp
    FluidWidget.WORLD_CLOCKS -> 104.dp
    FluidWidget.ACTIVITY_RINGS -> 140.dp
    else -> null
}

@Composable private fun light(size: Int) = F.text(size).copy(fontWeight = FontWeight.Light)

@Composable internal fun WeatherCard(state: FluidState, large: Boolean, modifier: Modifier = Modifier) {
    val weather = state.weather
    val temp = weather?.temperature?.roundToInt()?.let { "$it°" } ?: "—°"
    val condition = weather?.condition ?: if (state.weatherLoading) "Looking outside…" else if (state.config.autoWeather) "Tap to locate" else "Choose city"
    Box(modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = "Weather $temp $condition" }) {
        if (large) Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                weather?.let { Text(it.city.substringBefore(','), style = F.text(11, muted = true), maxLines = 1) }
                Text(temp, style = light(44), color = F.ink)
                Text(listOfNotNull(condition, weather?.let { "UV ${uvLabel(it.uv)}" }).joinToString(" · "), style = F.text(12, muted = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            WeatherSky(weather, Modifier.weight(1f).fillMaxHeight())
        } else {
            WeatherSky(weather, Modifier.fillMaxWidth().fillMaxHeight(.66f).align(Alignment.TopCenter))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 12.dp, bottom = 14.dp)) {
                Text(temp, style = light(34), color = F.ink)
                Text(condition, style = F.text(12, muted = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun uvLabel(uv: Double) = when { uv < 3 -> "low"; uv < 6 -> "moderate"; uv < 8 -> "high"; else -> "very high" }

private fun minutesOf(time: String?): Int? = time?.split(':')?.takeIf { it.size >= 2 }?.let { (h, m) -> h.toIntOrNull()?.let { hh -> m.take(2).toIntOrNull()?.let { mm -> hh * 60 + mm } } }

@Composable internal fun SunPathWidget(state: FluidState, locate: () -> Unit) {
    val weather = state.weather
    // Sunrise and sunset are in the forecast location's time, so "now" must be too.
    val zone = weather?.utcOffset?.let { java.time.ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(state.now).atZone(zone)
    val now = time.hour * 60 + time.minute
    val rise = minutesOf(weather?.sunrise) ?: 6 * 60
    val set = minutesOf(weather?.sunset) ?: 18 * 60
    val daylight = now in rise..set
    val target = ((now - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, tween(1800, easing = FastOutSlowInEasing)) }
    val halo by rememberInfiniteTransition(label = "Sun halo").animateFloat(.18f, .36f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "Halo")
    val ink = F.muted
    Column(Modifier.fillMaxSize().tap(role = Role.Button) { if (weather == null) locate() }.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("SUN PATH", style = F.text(11, true).copy(letterSpacing = 1.4.sp), color = Violet)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp)) {
            val sx = size.width / 310f; val sy = size.height / 100f
            fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
            val p0 = p(10f, 92f); val c = p(155f, -30f); val p2 = p(300f, 92f)
            val arc = Path().apply { moveTo(p0.x, p0.y); quadraticTo(c.x, c.y, p2.x, p2.y) }
            drawLine(ink.copy(alpha = .35f), p(0f, 92f), p(310f, 92f), 1.dp.toPx())
            drawPath(arc, Violet.copy(alpha = .45f), style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))))
            val t = progress.value; val u = 1 - t
            val sun = Offset(u * u * p0.x + 2 * u * t * c.x + t * t * p2.x, u * u * p0.y + 2 * u * t * c.y + t * t * p2.y)
            val color = if (daylight) Color(0xFFF2A93B) else ink
            drawCircle(color.copy(alpha = if (daylight) halo else .12f), 14.dp.toPx(), sun)
            drawCircle(if (daylight) Color(0xFFFFC94D) else ink.copy(alpha = .5f), 7.dp.toPx(), sun)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Sunrise ${weather?.sunrise ?: "--:--"}", style = F.text(11, muted = true), modifier = Modifier.weight(1f))
            Text("Sunset ${weather?.sunset ?: "--:--"}", style = F.text(11, muted = true))
        }
    }
}

private fun compass(degrees: Int) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((degrees % 360 + 360) % 360 + 22) / 45 % 8]

@Composable internal fun WindWidget(weather: HomeWeather?, locate: () -> Unit) {
    val speed = weather?.windSpeed ?: 0.0
    // Faster wind makes the streams flow faster.
    val duration = (1600 * 12 / speed.coerceIn(4.0, 48.0)).toInt()
    val flow by rememberInfiniteTransition(label = "Wind").animateFloat(0f, 1f, infiniteRepeatable(tween(duration, easing = LinearEasing)), label = "Wind flow")
    Row(Modifier.fillMaxSize().tap(role = Role.Button) { if (weather == null) locate() }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val dash = 14.dp.toPx(); val gap = 16.dp.toPx()
            listOf(Violet, Blue, Teal).forEachIndexed { i, color ->
                val y = size.height * (.25f + i * .25f)
                val wave = Path().apply {
                    moveTo(0f, y)
                    cubicTo(size.width * .3f, y - 8.dp.toPx(), size.width * .6f, y + 8.dp.toPx(), size.width, y - 2.dp.toPx())
                }
                drawPath(wave, color.copy(alpha = .85f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), -(flow + i * .3f) * (dash + gap))))
            }
        }
        Column(Modifier.padding(start = 14.dp), horizontalAlignment = Alignment.End) {
            Text(weather?.let { "${it.windSpeed.roundToInt()} km/h" } ?: "-- km/h", style = F.text(20, true))
            Text(weather?.let { "Wind · ${compass(it.windDirection)}" } ?: "Wind", style = F.text(11, muted = true))
        }
    }
}

@Composable internal fun FocusWidget(state: FluidState, model: FluidModel) {
    val running = state.focusEnd != null
    val left = if (running) (state.focusEnd!! - state.now).coerceIn(0L, FOCUS_LENGTH) else state.focusLeft
    val fraction by animateFloatAsState(left.toFloat() / FOCUS_LENGTH, tween(900, easing = LinearEasing), label = "Focus ring")
    val pulse by rememberInfiniteTransition(label = "Focus pulse").animateFloat(.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "Dot")
    val track = F.purple.copy(alpha = .14f)
    val label = String.format(Locale.getDefault(), "%02d:%02d", left / 60_000, left / 1000 % 60)
    Box(Modifier.fillMaxSize().tap(role = Role.Button, onClickLabel = if (running) "Pause focus" else "Start focus", onClick = model::toggleFocus).semantics { stateDescription = "$label ${if (running) "running" else "paused"}" }) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2 + 2.dp.toPx()
                    val arc = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(Violet, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text(label, style = F.text(17, true))
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).graphicsLayer { alpha = if (running) pulse else .45f }.clip(CircleShape).background(Violet))
                Text(if (running) "Focus" else if (left < FOCUS_LENGTH) "Paused" else "Tap to focus", style = F.text(11, muted = true), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (!running && left < FOCUS_LENGTH) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp).clip(CircleShape).tap(role = Role.Button, onClick = model::resetFocus).semantics { contentDescription = "Reset focus" }, contentAlignment = Alignment.Center) {
            Glyph("refresh", F.muted, Modifier.size(16.dp))
        }
    }
}

@Composable internal fun BreatheWidget() {
    var paused by rememberSaveable { mutableStateOf(false) }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) {
            phase.animateTo(1f, tween(((1f - phase.value) * 8000).toInt().coerceAtLeast(1), easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    val dark = LocalGlass.current.dark
    val inhale = phase.value < .5f
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF4B3FB0).copy(alpha = .18f) else Color(0xFFDEDAFA).copy(alpha = .55f))
        .tap(role = Role.Button, onClickLabel = if (paused) "Resume" else "Pause") { paused = !paused }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .3f, size.height / 2f)
            val base = size.height * .34f
            repeat(3) { i ->
                val p = ((phase.value - i * .25f / 8f) % 1f + 1f) % 1f
                val scale = .8f + .45f * (.5f - .5f * cos(p * 2f * PI.toFloat()))
                drawCircle(Violet.copy(alpha = .14f + i * .1f), base * scale * (1f - i * .24f), center)
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 26.dp), horizontalAlignment = Alignment.End) {
            Crossfade(if (paused) "Paused" else if (inhale) "Breathe in" else "Breathe out", animationSpec = tween(700), label = "Breath label") {
                Text(it, style = F.text(19, true), color = if (dark) Color(0xFFD9D3FF) else Color(0xFF4B3FB0))
            }
            Text(if (paused) "Tap to resume" else "4 seconds each", style = F.text(11, muted = true), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun dayPart(hour: Int) = when (hour) { in 5..11 -> "Morning"; in 12..16 -> "Day"; in 17..20 -> "Evening"; else -> "Night" }

@Composable internal fun WorldClocksWidget(state: FluidState, model: FluidModel) {
    val local = ZoneId.systemDefault()
    val others = listOf("Europe/London", "America/New_York", "Asia/Tokyo").map(ZoneId::of).filter { it.rules != local.rules }.take(2)
    val blink = state.now / 1000 % 2 == 0L
    val formatter = DateTimeFormatter.ofPattern("HH mm", Locale.getDefault())
    Row(Modifier.fillMaxSize().tap(role = Role.Button) { model.open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        (listOf(local) + others).forEach { zone ->
            val time = Instant.ofEpochMilli(state.now).atZone(zone)
            val part = dayPart(time.hour)
            val (h, m) = time.format(formatter).split(' ')
            Column(Modifier.weight(1f)) {
                Text(zone.id.substringAfterLast('/').replace('_', ' '), style = F.text(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row {
                    Text(h, style = light(24), color = F.ink)
                    Text(":", style = light(24), color = F.ink.copy(alpha = if (blink) 1f else .2f))
                    Text(m, style = light(24), color = F.ink)
                }
                Text(part, style = F.text(11, true), color = if (part == "Night") Violet else Amber)
            }
        }
    }
}

@Composable internal fun NowPlayingWidget(state: FluidState, model: FluidModel) {
    val device = state.device
    val playing = device.playing
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) while (isActive) { rotation.animateTo(rotation.value + 360f, tween(12_000, easing = LinearEasing)) }
    }
    val bars = rememberInfiniteTransition(label = "Equaliser")
    val heights = (0 until 6).map { i -> bars.animateFloat(.25f, 1f, infiniteRepeatable(tween(900, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 140)), label = "Bar $i") }
    val elapsed = if (playing && device.mediaPositionAt > 0) ((SystemClock.elapsedRealtime() - device.mediaPositionAt) * device.mediaSpeed).toLong() else 0L
    val position = (device.mediaPosition + elapsed).coerceIn(0L, device.mediaDuration.coerceAtLeast(0L).takeIf { it > 0 } ?: Long.MAX_VALUE)
    val progress = if (device.mediaDuration > 0) position.toFloat() / device.mediaDuration else 0f
    val accent = F.purple
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).graphicsLayer { rotationZ = rotation.value }.clip(CircleShape).tap(role = Role.Button, onClickLabel = "Open music") { model.media("play") }, contentAlignment = Alignment.Center) {
                device.mediaArtwork?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    ?: Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Brush.sweepGradient(listOf(Color(0xFF6B4EF0), Color(0xFFB99BFF), Color(0xFF3E2C8F), Color(0xFF8C6CFF), Color(0xFF6B4EF0))))
                        repeat(3) { i -> drawCircle(Color.White.copy(alpha = .14f), size.minDimension * (.22f + i * .09f), style = Stroke(.8.dp.toPx())) }
                    }
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = .85f)))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(device.mediaTitle ?: "Nothing playing", style = F.text(17, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(device.mediaArtist ?: if (!device.mediaAccess) "Connect music access" else if (playing) "Playing" else "Tap play to start", style = F.text(12, muted = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 10.dp).height(20.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    heights.forEach { h -> Box(Modifier.width(4.dp).fillMaxHeight(if (playing) h.value else .2f).clip(RoundedCornerShape(2.dp)).background(Violet)) }
                }
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(F.lilac, accent))).tap(role = Role.Button) { if (device.mediaAccess) model.media("play") else model.accessNotifications() }
                .semantics { contentDescription = if (playing) "Pause music" else "Play music" }, contentAlignment = Alignment.Center) {
                Glyph(if (playing) "pause" else "play", Color.White, Modifier.size(20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp).clip(CircleShape).background(accent.copy(alpha = .15f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape).background(accent))
        }
    }
}

private data class Battery(val level: Int, val charging: Boolean, val full: Boolean)

@Composable internal fun BatteryWidget(model: FluidModel) {
    val context = LocalContext.current
    var battery by remember { mutableStateOf<Battery?>(null) }
    DisposableEffect(context) {
        fun read(intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0) battery = Battery(level * 100 / scale.coerceAtLeast(1), status == BatteryManager.BATTERY_STATUS_CHARGING, status == BatteryManager.BATTERY_STATUS_FULL)
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, intent: Intent) = read(intent) }
        read(ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val level = battery?.level ?: 0
    val charging = battery?.charging == true
    val fill by animateFloatAsState(level / 100f, tween(1200, easing = FastOutSlowInEasing), label = "Battery fill")
    val glow by rememberInfiniteTransition(label = "Charge glow").animateFloat(.1f, .4f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "Glow")
    val color = if (level <= 15 && !charging) Color(0xFFE05B67) else Teal
    Column(Modifier.fillMaxSize().tap(role = Role.Button) { model.open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx(); val inset = stroke / 2 + 4.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                if (charging) drawCircle(color.copy(alpha = glow), size.minDimension / 2f)
                drawArc(color.copy(alpha = .15f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                drawArc(color, -90f, 360f * fill, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (charging) Canvas(Modifier.size(width = 10.dp, height = 14.dp)) {
                    val w = size.width; val h = size.height
                    drawPath(Path().apply { moveTo(w * .62f, 0f); lineTo(0f, h * .58f); lineTo(w * .46f, h * .58f); lineTo(w * .36f, h); lineTo(w, h * .4f); lineTo(w * .54f, h * .4f); close() }, color)
                }
                Text(battery?.let { "$level%" } ?: "--%", style = F.text(17, true))
            }
        }
        Text(when { battery == null -> "Battery"; battery?.full == true -> "Full"; charging -> "Charging"; else -> "On battery" }, style = F.text(11, muted = true), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable internal fun HydrationWidget(state: FluidState, model: FluidModel) {
    val level by animateFloatAsState((state.water.toFloat() / WATER_GOAL).coerceIn(0f, 1f), spring(.8f, 120f), label = "Water level")
    val waves = rememberInfiniteTransition(label = "Water waves")
    val front by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "Front wave")
    val back by waves.animateFloat(1f, 0f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "Back wave")
    Box(Modifier.fillMaxSize().tap(role = Role.Button, onClickLabel = "Add a glass of water") { model.addWater(1) }) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(width = 92.dp, height = 88.dp).clip(RoundedCornerShape(24.dp)).background(Blue.copy(alpha = .08f)).border(1.dp, Blue.copy(alpha = .18f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    fun wave(shift: Float, amplitude: Float, color: Color) {
                        val top = size.height * (1f - level.coerceAtLeast(.06f))
                        val path = Path().apply {
                            moveTo(0f, size.height)
                            var x = 0f
                            while (x <= size.width) { lineTo(x, top + sin((x / size.width + shift) * 2f * PI.toFloat()) * amplitude); x += 3f }
                            lineTo(size.width, size.height); close()
                        }
                        drawPath(path, color)
                    }
                    wave(back, 4.dp.toPx(), Color(0xFF6B4EF0).copy(alpha = .35f))
                    wave(front, 3.dp.toPx(), Color(0xFF3E78FF).copy(alpha = .55f))
                }
                Text("${state.water}/$WATER_GOAL", style = F.text(16, true))
            }
            Text(if (state.water >= WATER_GOAL) "Water · goal met" else "Water", style = F.text(11, muted = true), modifier = Modifier.padding(top = 8.dp))
        }
        if (state.water > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp).clip(CircleShape).tap(role = Role.Button) { model.addWater(-1) }.semantics { contentDescription = "Remove a glass" }, contentAlignment = Alignment.Center) {
            Text("−", style = F.text(18, true), color = F.muted)
        }
    }
}

@Composable internal fun ActivityRingsWidget(state: FluidState, activityAccess: () -> Unit) {
    val steps = state.steps
    val move = (steps ?: 0) / 10_000f
    val exercise = (steps ?: 0) / 110 / 30f
    val stand = state.standHours / 12f
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(3000, easing = FastOutSlowInEasing)) }
    val rings = listOf(Triple("Move", Pink, move), Triple("Exercise", Teal, exercise), Triple("Stand", Blue, stand))
    Row(Modifier.fillMaxSize().tap(role = Role.Button) { if (steps == null) activityAccess() }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(106.dp)) {
            val stroke = 8.dp.toPx() * size.minDimension / 100.dp.toPx()
            rings.forEachIndexed { i, (_, color, value) ->
                val r = size.minDimension / 100f * (42f - i * 10f)
                val tl = Offset(size.width / 2 - r, size.height / 2 - r)
                drawArc(color.copy(alpha = .16f), 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(stroke))
                drawArc(color, -90f, 360f * value.coerceIn(0f, 1f) * reveal.value, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (steps == null) {
                Text("Activity rings", style = F.text(15, true))
                Text("Tap to enable activity tracking", style = F.text(11, muted = true))
            } else listOf(
                "Move" to "${String.format(Locale.getDefault(), "%,d", steps)} steps",
                "Exercise" to "${steps / 110} / 30 min",
                "Stand" to "${state.standHours} / 12 h"
            ).forEachIndexed { i, (name, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(rings[i].second))
                    Text(name, style = F.text(12, true), modifier = Modifier.padding(start = 8.dp).width(64.dp))
                    Text(value, style = F.text(11, muted = true), maxLines = 1)
                }
            }
        }
    }
}
