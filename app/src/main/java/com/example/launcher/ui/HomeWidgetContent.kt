package com.example.launcher.ui

import android.Manifest
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.launcher.*
import com.example.launcher.data.*
import com.example.launcher.ui.theme.Tokens as T
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun HomeWidgetContent(kind: HomeWidgetKind, state: LauncherUiState, model: LauncherViewModel, interactive: Boolean = true) {
    val context = LocalContext.current
    var edit by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshDevice() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) model.toggle("flashlight") }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.toggleFocus() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { draft = it; edit = "Quick note" }
    }
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> result.data?.data?.let { uri ->
        model.action { context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) model.favourite(it.getString(0) ?: "Contact", it.getString(1) ?: "")
        } }
    } }
    fun action(block: () -> Unit) { if (interactive) model.action(block) }
    fun input(title: String, initial: String = "") { if (interactive) { draft = initial; edit = title } }
    val time = Instant.ofEpochMilli(state.now).atZone(ZoneId.systemDefault())
    val contentSize = if ((kind == HomeWidgetKind.WEATHER || kind == HomeWidgetKind.MARKETS) && !interactive) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
    Column(contentSize.padding(12.dp), verticalArrangement = Arrangement.Center) {
        when (kind) {
            HomeWidgetKind.CLOCK -> Column(Modifier.fillMaxWidth().clickable(enabled = interactive) { action { model.device.open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) } }) {
                Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), style = T.clock.copy(fontSize = 48.sp, lineHeight = 52.sp))
                Text(time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), style = T.caption)
            }
            HomeWidgetKind.AGENDA -> {
                Label("UP NEXT")
                if (!state.device.calendarGranted) TextButton(onClick = { action { calendar.launch(Manifest.permission.READ_CALENDAR) } }, enabled = interactive) { Text("Connect calendar", color = T.amber) }
                else if (state.device.events.isEmpty()) Text("Nothing else today", style = T.caption, modifier = Modifier.padding(top = 12.dp))
                state.device.events.take(2).forEachIndexed { index, event ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = interactive) { action { model.device.open(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))) } }.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(3.dp).height(32.dp).background(if (index == 0) T.mint else T.coral, CircleShape))
                        Column { Text(event.title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(event.times, style = T.caption) }
                    }
                }
            }
            HomeWidgetKind.WEATHER -> {
                val weather = state.weather
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(enabled = interactive) { input("Weather city", state.customization.city) }) {
                        Text(weather?.let { "${it.temperature.toInt()}°" } ?: "—°", style = T.pageTitle)
                        Text(weather?.condition ?: if (state.customization.city.isBlank()) "Choose city" else state.weatherError ?: "Loading weather…", style = T.caption, maxLines = 2)
                        Text(weather?.city ?: state.customization.city, style = T.caption, maxLines = 1)
                    }
                    IconAction("weather", "Change weather city", enabled = interactive) { input("Weather city", state.customization.city) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { weather?.hours?.forEach { hour -> Column { Text(hour.time, style = T.caption); Text("${hour.temperature.toInt()}°", style = T.body) } } }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Open-Meteo", style = T.caption.copy(fontSize = 9.sp), modifier = Modifier.weight(1f).clickable(enabled = interactive) { action { model.device.open(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://open-meteo.com/"))) } })
                    Text("Refresh", style = T.caption.copy(fontSize = 9.sp, color = T.amber), modifier = Modifier.clickable(enabled = interactive) { model.refreshWidgetData() })
                }
            }
            HomeWidgetKind.MARKETS -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("MARKETS"); Text(state.marketQuotes.firstOrNull()?.let { "1M · " + Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm")) } ?: "1M · delayed", style = T.caption.copy(fontSize = 9.sp)) }
                if (state.marketQuotes.isEmpty()) TextButton(onClick = { model.refreshWidgetData() }, enabled = interactive && !state.widgetDataLoading) { Text(if (state.widgetDataLoading) "Loading prices…" else "Prices unavailable · Retry", color = T.amber) }
                state.marketQuotes.forEach { quote ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(quote.name, style = T.caption, modifier = Modifier.weight(1f))
                        Canvas(Modifier.width(48.dp).height(24.dp)) {
                            if (quote.points.size >= 2) {
                                val low = quote.points.min(); val range = (quote.points.max() - low).coerceAtLeast(1.0)
                                val path = Path(); quote.points.forEachIndexed { index, value ->
                                    val x = size.width * index / (quote.points.size - 1); val y = size.height * (1 - (value - low) / range).toFloat()
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }; drawPath(path, if (quote.change >= 0) T.mint else T.coral, style = androidx.compose.ui.graphics.drawscope.Stroke(2f, cap = StrokeCap.Round))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) { Text(String.format(Locale.getDefault(), "%,.2f", quote.price), style = T.caption); Text(String.format(Locale.getDefault(), "%+.2f%%", quote.change), style = T.caption.copy(color = if (quote.change >= 0) T.mint else T.coral)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Yahoo Finance · delayed", style = T.caption.copy(fontSize = 9.sp), modifier = Modifier.weight(1f).clickable(enabled = interactive) { action { model.device.open(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://finance.yahoo.com/"))) } })
                    Text("Refresh", style = T.caption.copy(fontSize = 9.sp, color = T.amber), modifier = Modifier.clickable(enabled = interactive) { model.refreshWidgetData() })
                }
            }
            HomeWidgetKind.MUSIC -> {
                if (!state.device.mediaAccess) {
                    Text("Music player", style = T.body)
                    if (interactive) MediaAccessButton(model) else Text("Connect your music", style = T.caption)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("music", T.mint, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text(state.device.mediaTitle ?: "Nothing playing", style = T.caption, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (state.device.playing) "Now playing" else "Paused", style = T.caption.copy(fontSize = 9.sp)) }
                    listOf("previous", if (state.device.playing) "pause" else "play", "next").forEach { command ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(if (command == "play" || command == "pause") T.amber else androidx.compose.ui.graphics.Color.Transparent).clickable(enabled = interactive && state.device.mediaTitle != null) { action { model.device.transport(if (command == "pause") "play" else command) } }.semantics { contentDescription = command }, contentAlignment = Alignment.Center) { Glyph(command, if (command == "play" || command == "pause") T.onAccent else T.text, Modifier.size(20.dp)) }
                    }
                }
            }
            HomeWidgetKind.QUICK_SETTINGS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconAction("wifi", "Wi-Fi", state.device.wifi, interactive) { model.showConnectivity(ConnectivityType.WIFI) }
                IconAction("bluetooth", "Bluetooth", state.device.bluetooth == true, interactive) { model.showConnectivity(ConnectivityType.BLUETOOTH) }
                IconAction("flashlight", "Flashlight", state.device.torch, interactive && state.device.hasTorch) { action { camera.launch(Manifest.permission.CAMERA) } }
                IconAction(if (state.device.soundMode == SoundMode.VIBRATE) "vibrate" else "silent", "Sound mode", state.device.soundMode != SoundMode.NORMAL, interactive) { model.toggle("silent") }
            }
            HomeWidgetKind.TASKS -> {
                Label("TASKS")
                state.tasks.take(3).forEach { task -> Row(Modifier.fillMaxWidth().clickable(enabled = interactive, role = Role.Checkbox) { model.toggleTask(task) }.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).border(1.dp, if (task.done) T.amber else T.muted, CircleShape).background(if (task.done) T.amber else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
                    Text(task.text, style = T.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } }
                TextButton(onClick = { input("New task") }, enabled = interactive, contentPadding = PaddingValues(0.dp)) { Text(if (state.tasks.isEmpty()) "Add a task" else "+ Task", style = T.caption, color = T.amber) }
            }
            HomeWidgetKind.FOCUS -> {
                val remaining = if (state.focusUntil > 0) (state.focusUntil - state.now).coerceAtLeast(0) else state.focusRemaining
                Box(Modifier.size(76.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { (remaining.toFloat() / (25 * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(), color = T.amber, trackColor = T.surfaceRaised, strokeWidth = 5.dp)
                    Text("%02d:%02d".format(remaining / 60_000, remaining / 1000 % 60), style = T.body)
                }
                TextButton(onClick = {
                    if (state.focusUntil == 0L && android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else model.toggleFocus()
                }, enabled = interactive, modifier = Modifier.align(Alignment.CenterHorizontally), contentPadding = PaddingValues(0.dp)) { Text(if (state.focusUntil > 0) "Pause focus" else "Start focus", style = T.caption, color = T.amber) }
                Text("Reset", style = T.caption, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = interactive) { model.resetFocus() })
            }
            HomeWidgetKind.NOTE -> Row(Modifier.fillMaxWidth().clickable(enabled = interactive) { input("Quick note") }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Label("QUICK NOTE"); Text(state.notes.firstOrNull()?.body ?: "Jot something down…", style = T.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                IconAction("mic", "Dictate note", enabled = interactive) { action { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) } }
                IconAction("plus", "Write quick note", accent = true, enabled = interactive) { input("Quick note") }
            }
            HomeWidgetKind.FAVOURITES -> {
                if (state.favourites.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Text("Favourites", style = T.body, modifier = Modifier.weight(1f)); IconAction("plus", "Add favourite", enabled = interactive) { action { contact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } } }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { state.favourites.take(4).forEachIndexed { index, person ->
                    val color = listOf(T.mint, T.coral, T.sky, T.amber)[index]
                    Column(Modifier.weight(1f).clickable(enabled = interactive) { action { model.device.dial(person.phone) } }.semantics { contentDescription = "Call ${person.name}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(32.dp).background(color, CircleShape), contentAlignment = Alignment.Center) { Text(person.name.take(1), style = T.body, color = T.onAccent) }
                        Text(person.name, style = T.caption.copy(fontSize = 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }; if (state.favourites.size < 4) IconAction("plus", "Add favourite", enabled = interactive) { action { contact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } } }
            }
        }
    }
    edit?.let { title -> AlertDialog(onDismissRequest = { edit = null }, containerColor = T.surface, title = { Text(title, color = T.text) },
        text = { OutlinedTextField(draft, { draft = it }, label = { Text(if (title == "Weather city") "City name" else title) }, singleLine = title != "Quick note") },
        confirmButton = { TextButton(onClick = { when (title) { "Weather city" -> model.setWeatherCity(draft); "New task" -> model.addHomeTask(draft); else -> model.saveQuickNote(draft) }; edit = null }, enabled = draft.isNotBlank()) { Text("Save", color = T.amber) } },
        dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } }) }
}
