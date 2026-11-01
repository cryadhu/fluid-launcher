package com.example.launcher.fluid

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.example.launcher.ui.Glyph
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun fluidDate(now: Long, format: String) = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(format,Locale.getDefault()))

@Composable fun WeekStrip(now: Long) {
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        (0..4).forEach { delta -> val day = today.plusDays(delta.toLong())
            Column(Modifier.width(50.dp).then(if (delta == 0) Modifier.glass(22.dp) else Modifier).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.format(DateTimeFormatter.ofPattern("EEE")),style = F.text(11,muted=delta!=0))
                Text(day.dayOfMonth.toString(),style=F.text(if(delta==0) 26 else 20,delta==0),modifier=Modifier.padding(top=3.dp))
                if (delta==0) Text(day.format(DateTimeFormatter.ofPattern("MMM")),style=F.text(10,muted=true))
            }
        }
    }
}

@Composable fun MusicOrb(state: FluidState, play: () -> Unit, expand: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val rotation = if (state.device.playing) {
        val spin = rememberInfiniteTransition(label = "Record rotation")
        val angle by spin.animateFloat(0f,360f,infiniteRepeatable(tween(18000,easing=LinearEasing)),label="Record angle")
        angle
    } else 0f
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(if(compact)104.dp else 132.dp).glass(70.dp).clickable(onClick=expand),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                // Leave clear gaps at twelve and six o'clock for the label and play control.
                val track = Stroke(1.dp.toPx(), cap=StrokeCap.Round)
                val accent = Stroke(2.dp.toPx(), cap=StrokeCap.Round)
                listOf(-60f,120f).forEach { start ->
                    drawArc(Color.White.copy(alpha=.55f),start,120f,false,style=track)
                    if(state.device.playing) drawArc(F.purple,start,120f,false,style=accent)
                }
            }
            Canvas(Modifier.size(if(compact)60.dp else 78.dp).clip(CircleShape).rotate(rotation)) {
                drawCircle(Brush.sweepGradient(listOf(Color(0xFFDDD3FF),F.purple,Color(0xFF46327D),F.lilac,Color(0xFFDDD3FF))))
                repeat(5) { i -> drawCircle(Color.White.copy(alpha=.12f),size.minDimension*(.14f+i*.07f),style=Stroke(1.dp.toPx())) }
                drawCircle(Color(0xFFEBE2FF),5.dp.toPx())
            }
            Text(if(state.device.playing) "PLAYING" else "MUSIC",style=F.text(8,true),modifier=Modifier.align(Alignment.TopCenter).padding(top=9.dp))
            Box(Modifier.align(Alignment.BottomCenter).size(40.dp).testTag("music-play").clip(CircleShape).clickable(role=Role.Button,onClick=play).semantics { contentDescription=if(state.device.playing) "Pause music" else "Play music" },contentAlignment=Alignment.Center) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(F.purple),contentAlignment=Alignment.Center) { Glyph(if(state.device.playing) "pause" else "play",Color.White,Modifier.size(12.dp)) }
            }
        }
        Text(state.device.mediaTitle ?: "Music",style=F.text(11),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=7.dp).widthIn(max=140.dp))
    }
}

@Composable fun WidgetBody(kind: FluidWidget, state: FluidState, model: FluidModel, detail: Boolean = false,
    calendarAccess: () -> Unit = {}, activityAccess: () -> Unit = {}, compact: Boolean = false) {
    when(kind) {
        FluidWidget.CLOCK -> AnalogClock(state.now, Modifier.fillMaxWidth().height(if(detail) 270.dp else if(compact) 110.dp else 160.dp)) { model.open(android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
        FluidWidget.NOTIFICATIONS -> NotificationStack(state,model,detail=detail)

        FluidWidget.AGENDA -> {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(fluidDate(state.now,"EEE, MMM d"),style=F.text(if(compact)16 else 21,true)); Text(if(state.device.calendarGranted) "${state.device.events.size} upcoming events" else "Your day, together",style=F.text(12,muted=true),modifier=Modifier.padding(top=3.dp)) }
                Box(Modifier.size(34.dp).glass(50.dp),contentAlignment=Alignment.Center) { Glyph("calendar",F.purple,Modifier.size(17.dp)) }
            }
            Spacer(Modifier.height(if(compact)7.dp else 14.dp))
            if (!state.device.calendarGranted) {
                if(!compact)Text("Bring your calendar into view.",style=F.text(13,muted=true))
                GlassButton("Connect calendar",Modifier.padding(top=10.dp),glyph="plus",onClick=calendarAccess)
            } else if (state.device.events.isEmpty()) {
                Text(if(state.device.calendarError) "Calendar unavailable" else "A little breathing room.",style=F.text(14,true))
                Text("Tap to open your calendar",style=F.text(12,muted=true),modifier=Modifier.padding(top=6.dp))
            } else Column(Modifier.glass(21.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                state.device.events.take(if(detail) 12 else if(compact) 1 else 2).forEachIndexed { index,event ->
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(4.dp).height(28.dp).clip(CircleShape).background(if(index%2==0) F.purple else F.lilac))
                        Column { Text(event.title,style=F.text(13,true),maxLines=if(detail) 3 else 1,overflow=TextOverflow.Ellipsis); Text(event.times,style=F.text(11,muted=true)) }
                    }
                }
            }
            if(detail) GlassButton("Open calendar",Modifier.padding(top=16.dp),glyph="calendar") { model.open(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_APP_CALENDAR)) }
        }
        FluidWidget.WEATHER -> {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.weather?.city ?: "Weather",style=F.text(12,muted=true))
                    Text(state.weather?.let { "${it.temperature.roundToInt()}°" } ?: "—°",style=F.text(42))
                    Text(state.weather?.condition ?: if(state.weatherLoading) "Looking outside…" else "Choose your city",style=F.text(12,muted=true))
                }
                Glyph("weather",F.purple,Modifier.size(46.dp))
            }
            if(detail) {
                var city by remember(state.config.city) { mutableStateOf(state.config.city) }
                Spacer(Modifier.height(18.dp)); FluidField(city,"City",{city=it})
                GlassButton(if(state.weatherLoading) "Refreshing…" else "Update weather",Modifier.padding(top=12.dp),primary=true) { model.weatherCity(city) }
                state.weatherError?.let { Text(it,style=F.text(12,muted=true),modifier=Modifier.padding(top=12.dp)) }
                state.weather?.hours?.forEach { hour -> Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text(hour.time,style=F.text()); Text("${hour.temperature.roundToInt()}°",style=F.text(14,true)) } }
                Text("Open-Meteo · live forecast",style=F.text(10,muted=true),modifier=Modifier.padding(top=20.dp))
            }
        }
        FluidWidget.MUSIC -> {
            MusicOrb(state,{model.media("play")},{},Modifier.fillMaxWidth(),compact=compact)
            if(detail) {
                Text(state.device.mediaTitle ?: "Nothing playing yet",style=F.text(20,true),modifier=Modifier.padding(top=24.dp))
                Text(if(state.device.mediaAccess) "Start music in your favorite app." else "Connect notification access to control your music.",style=F.text(13,muted=true),modifier=Modifier.padding(top=8.dp))
                Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                    GlassIcon("previous","Previous track") { model.media("previous") }; GlassIcon(if(state.device.playing) "pause" else "play","Play or pause",primary=true) { model.media("play") }; GlassIcon("next","Next track") { model.media("next") }
                }
                if(!state.device.mediaAccess) GlassButton("Connect music",Modifier.padding(top=20.dp),onClick=model::accessNotifications)
            }
        }
        FluidWidget.TASKS -> {
            Text("Today at Work",style=F.text(16,true)); Spacer(Modifier.height(12.dp))
            if(state.tasks.isEmpty()) Text("A clear mind starts here.\nAdd your first task.",style=F.text(13,muted=true))
            state.tasks.take(if(detail) 100 else if(compact) 1 else 3).forEach { task ->
                Row(Modifier.fillMaxWidth().clickable { model.toggleTask(task) }.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(if(task.done) F.purple else Color.White.copy(alpha=.25f)).border(1.dp,if(task.done) F.purple else F.muted,androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),contentAlignment=Alignment.Center) { if(task.done) Glyph("check",Color.White,Modifier.size(12.dp)) }
                    Text(task.text,style=F.text(13,muted=task.done),modifier=Modifier.weight(1f).padding(start=10.dp))
                    if(detail) TextButton(onClick={model.removeTask(task)}) { Text("×",color=F.muted) }
                }
            }
            if(detail) {
                var draft by remember { mutableStateOf("") }
                Spacer(Modifier.height(16.dp)); FluidField(draft,"A new task…",{draft=it})
                GlassButton("Add task",Modifier.padding(top=12.dp),primary=true,glyph="plus") { model.addTask(draft); draft="" }
            }
        }
        FluidWidget.STORAGE -> {
            Text("Storage",style=F.text(15,true)); Spacer(Modifier.height(12.dp))
            val used = state.storageUsed.toFloat()/state.storageTotal
            Text("${(used*100).roundToInt()}%",style=F.text(32,true)); Text("used",style=F.text(11,muted=true))
            LinearProgressIndicator(progress={used},modifier=Modifier.fillMaxWidth().padding(top=14.dp).height(7.dp).clip(CircleShape),color=F.purple,trackColor=F.lilac.copy(alpha=.28f))
            if(detail) {
                Text(String.format(Locale.getDefault(),"%.1f GB of %.1f GB",state.storageUsed/1e9,state.storageTotal/1e9),style=F.text(14,muted=true),modifier=Modifier.padding(top=24.dp))
                GlassButton("Manage storage",Modifier.padding(top=20.dp),glyph="files") { model.open(android.content.Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            }
        }
        FluidWidget.ACTIVITY -> {
            val color=F.purple
            Box(Modifier.fillMaxWidth().height(if(detail) 220.dp else if(compact)92.dp else 132.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(if(detail) 200.dp else if(compact)86.dp else 126.dp).padding(6.dp)) { drawArc(color.copy(alpha=.12f),-90f,360f,false,style=Stroke(9.dp.toPx())); drawArc(color,-90f,((state.steps?:0)/10000f).coerceIn(0f,1f)*360f,false,style=Stroke(9.dp.toPx(),cap=StrokeCap.Round)) }
                Column(horizontalAlignment=Alignment.CenterHorizontally) { Glyph("health",F.purple,Modifier.size(20.dp)); Text(state.steps?.toString()?:"—",style=F.text(24,true)); Text("Steps",style=F.text(11,muted=true)) }
            }
            if(detail) {
                Text("Steps counted from your first launcher session today. The sensor updates while this launcher is active.",style=F.text(13,muted=true),modifier=Modifier.padding(top=16.dp))
                if(state.steps==null) GlassButton("Enable activity",Modifier.padding(top=16.dp),onClick=activityAccess)
            }
        }
        FluidWidget.DATE -> {
            Column(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(fluidDate(state.now,"EEE"),style=F.text(13)); Text(fluidDate(state.now,"d"),style=F.text(if(detail) 96 else if(compact)32 else 44,true)); Text(fluidDate(state.now,"MMMM yyyy"),style=F.text(12,muted=true))
            }
        }
    }
}

@Composable fun FluidField(value: String, placeholder: String, change: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(value, change, modifier.fillMaxWidth().glass(24.dp), placeholder={Text(placeholder,style=F.text(14,muted=true))},textStyle=F.text(),singleLine=true,
        colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=F.purple,focusedTextColor=F.ink,unfocusedTextColor=F.ink))
}
