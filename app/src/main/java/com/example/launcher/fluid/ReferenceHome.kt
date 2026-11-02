@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.launcher.data.InstalledApp
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.time.*
import kotlin.math.*

val LocalNotificationsExpand = staticCompositionLocalOf<()->Unit> { {} }
val LocalWidgetEdit = staticCompositionLocalOf<(FluidWidget)->Unit> { {} }
internal fun widgetMinimum(kind:FluidWidget,config:FluidConfig):Dp = when(config.sizes[kind]) { "Compact" -> 96.dp; "Large" -> if(kind==FluidWidget.NOTIFICATIONS) 248.dp else 230.dp; else -> 138.dp }

@Composable internal fun FluidHome(state:FluidState,model:FluidModel,hero:FluidHero,badges:Set<String>,modifier:Modifier,calendar:()->Unit,activity:()->Unit,customize:()->Unit,ask:()->Unit,space:()->Unit,launch:(InstalledApp,Rect)->Unit) {
    val edit=LocalWidgetEdit.current
    Column(modifier.navigationBarsPadding().padding(bottom=32.dp).testTag("fluid-home")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).combinedClickable(onClick={},onLongClick=customize).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(fluidDate(state.now,"EEEE, MMM d"),style=F.text(12,muted=true))
                GlassIcon("edit","Customize home",Modifier.size(34.dp),onClick=customize)
            }
            val top=state.config.widgets.filter{it==FluidWidget.CLOCK || it==FluidWidget.WEATHER}
            if(top.isNotEmpty()) Row(Modifier.fillMaxWidth().animateContentSize(spring(1f,350f)),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                top.forEach{kind ->
                    val height=when(state.config.sizes[kind]){"Compact"->112.dp;"Large"->210.dp;else->156.dp}
                    Box(Modifier.weight(if(state.config.sizes[kind]=="Large")1.4f else 1f).height(height).animateContentSize(spring(1f,350f)).testTag("fluid-widget-${kind.name}")) {
                        if(kind==FluidWidget.CLOCK) AnalogClock(state.now,Modifier.fillMaxSize()){model.open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))}
                        else WeatherScene(state,Modifier.fillMaxSize(),{hero.open(kind)}, {edit(kind)})
                        // The unframed weather still participates in the same shared card transition.
                        Box(Modifier.fillMaxSize().onGloballyPositioned{hero.bounds[kind]=Rect(it.positionInRoot(),it.size.toSize())})
                    }
                }
            }
            state.config.widgets.filterNot{it==FluidWidget.CLOCK || it==FluidWidget.WEATHER}.forEach{kind -> key(kind){
                if(kind==FluidWidget.NOTIFICATIONS) Box(Modifier.onGloballyPositioned { hero.bounds[kind]=it.boundsInRoot() }) { NotificationStack(state,model) }
                else {
                    Text(if(kind==FluidWidget.AGENDA)"Today" else kind.title,style=F.text(11,muted=true),modifier=Modifier.padding(start=8.dp,top=2.dp))
                    ExpandableWidget(kind,state,model,hero,calendar,activity)
                }
            } }
        }
        FluidDock(state, model, badges, ask, launch)

    }
}

@Composable internal fun AnalogClock(now:Long,modifier:Modifier=Modifier,click:()->Unit={}) {
    val edit=LocalWidgetEdit.current
    val time=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    // Unwrapped angles keep the hands moving forward across minute/hour boundaries.
    val start=remember{now}
    val initial=remember{time.hour%12*3600+time.minute*60+time.second}
    val secondTarget=(initial+(now-start)/1000f)*6f
    val seconds by animateFloatAsState(secondTarget,tween(950,easing=LinearEasing),label="Clock seconds")
    val ink=F.ink
    val face=if(LocalGlass.current.dark)Color(0xFF35403D)else Color.White
    BoxWithConstraints(modifier.combinedClickable(onClick=click,onLongClick={edit(FluidWidget.CLOCK)}).semantics{contentDescription="Clock ${fluidDate(now,"HH:mm")}"},contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val radius=size.minDimension*.46f
            val center=Offset(size.width/2,size.height/2)
            drawCircle(Brush.radialGradient(listOf(face.copy(alpha=.95f),face.copy(alpha=.65f)),center,radius),radius,center)
            repeat(60){i -> val a=Math.toRadians(i*6.0-90);val outer=radius*.88f;val inner=radius*if(i%5==0).79f else .86f
                drawLine(ink.copy(alpha=if(i%5==0).45f else .14f),center+Offset(cos(a).toFloat()*inner,sin(a).toFloat()*inner),center+Offset(cos(a).toFloat()*outer,sin(a).toFloat()*outer),if(i%5==0)1.3.dp.toPx()else .7.dp.toPx(),StrokeCap.Round)
            }
            fun hand(angle:Float,length:Float,width:Float,color:Color){val a=Math.toRadians(angle.toDouble()-90);drawLine(color,center,center+Offset(cos(a).toFloat()*radius*length,sin(a).toFloat()*radius*length),width.dp.toPx(),StrokeCap.Round)}
            hand(seconds/720f,.46f,3f,ink)
            hand(seconds/60f,.67f,2f,ink)
            hand(seconds,.75f,.8f,Color(0xFFDF9279))
            drawCircle(ink,2.4.dp.toPx(),center)
        }
        Text("12",style=F.text(8,muted=true),modifier=Modifier.align(Alignment.TopCenter).padding(top=maxHeight*.22f))
    }
}

@Composable internal fun WeatherScene(state:FluidState,modifier:Modifier=Modifier,click:()->Unit={},hold:()->Unit={}) {
    val transition=rememberInfiniteTransition(label="Weather atmosphere")
    val drift by transition.animateFloat(-3f,3f,infiniteRepeatable(tween(3200,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="Cloud drift")
    val rain by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1400,easing=LinearEasing)),label="Rain fall")
    val condition=state.weather?.condition.orEmpty()
    val wet=condition.contains("rain",true)||condition.contains("shower",true)||condition.contains("storm",true)
    val cloudy=wet||condition.contains("cloud",true)||condition.contains("fog",true)
    val temp=state.weather?.temperature?.roundToInt()?.let{"$it\u00b0"} ?: "--\u00b0"
    Box(modifier.combinedClickable(onClick=click,onLongClick=hold).semantics{contentDescription="Weather $temp ${state.weather?.condition ?: "Choose city"}"}) {
        Canvas(Modifier.fillMaxSize().graphicsLayer{translationX=drift.dp.toPx()}) {
            val x=size.width*.32f;val y=size.height*.47f;val unit=size.minDimension
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A5),Color(0xFFFFE3A5).copy(alpha=.05f)),Offset(x,y),unit*.3f),unit*.3f,Offset(x,y))
            drawCircle(Color(0xFFFFD38B),unit*.16f,Offset(x,y))
            if(cloudy || state.weather==null){
                val color=if(wet)Color(0xFF8E9CA6)else Color(0xFFE7ECEC)
                listOf(Triple(-.15f,.04f,.18f),Triple(.02f,-.04f,.23f),Triple(.22f,.06f,.17f)).forEach{(dx,dy,r)->drawCircle(Brush.radialGradient(listOf(Color.White,color),Offset(x+dx*unit,y+dy*unit),r*unit),r*unit,Offset(x+dx*unit,y+dy*unit))}
            }
            if(wet)repeat(7){i->val px=x-unit*.2f+i*unit*.065f;val py=y+unit*.18f+((rain+i*.17f)%1f)*unit*.29f;drawLine(Color(0xFF84BACC).copy(alpha=.6f),Offset(px,py),Offset(px-2.dp.toPx(),py+5.dp.toPx()),1.dp.toPx(),StrokeCap.Round)}
        }
        Column(Modifier.align(Alignment.CenterEnd).widthIn(min=94.dp).glass(27.dp).padding(horizontal=17.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            AnimatedContent(temp,transitionSpec={(fadeIn(tween(350))+slideInVertically{it/4}) togetherWith fadeOut(tween(200))},label="Weather temperature"){value->Text(value,style=F.text(35))}
            Text(state.weather?.condition ?: "Choose city",style=F.text(9,muted=true),maxLines=2,modifier=Modifier.widthIn(max=86.dp))
            state.weather?.let{Text(it.city.substringBefore(','),style=F.text(8,muted=true),maxLines=1,modifier=Modifier.padding(top=5.dp))}
        }
    }
}

@Composable internal fun WidgetEditSheet(kind:FluidWidget,state:FluidState,model:FluidModel,close:()->Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.09f)).clickable(onClick=close).statusBarsPadding().navigationBarsPadding().padding(22.dp),contentAlignment=Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().glass(32.dp,true).clickable{}.padding(22.dp).testTag("widget-edit"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(kind.title,style=F.text(22,true),modifier=Modifier.weight(1f));GlassIcon("close","Close widget options",onClick=close)}
            Text("Widget size",style=F.text(12,muted=true))
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {listOf("Compact","Standard","Large").forEach{size->GlassButton(size,Modifier.weight(1f),primary=(state.config.sizes[kind] ?: "Standard")==size){model.resizeWidget(kind,size)}}}
            GlassButton("Remove widget",Modifier.fillMaxWidth(),glyph="bin"){if(kind in state.config.widgets)model.toggleWidget(kind);close()}
        }
    }
}

@Composable internal fun NotificationStack(state:FluidState,model:FluidModel,detail:Boolean=false) {
    val notifications by FluidNotifications.items.collectAsState()
    val edit=LocalWidgetEdit.current
    val expand=LocalNotificationsExpand.current
    val size=state.config.sizes[FluidWidget.NOTIFICATIONS] ?: "Standard"
    Column(Modifier.fillMaxWidth().animateContentSize(spring(1f,350f)).testTag("fluid-widget-NOTIFICATIONS")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Notifications",style=F.text(11,muted=true),modifier=Modifier.weight(1f).combinedClickable(onClick={},onLongClick={edit(FluidWidget.NOTIFICATIONS)}))
            if(notifications.size>1)TextButton(onClick=expand){Text("See all",style=F.text(11,true),color=F.purple)}
            if(notifications.any{it.clearable}) TextButton(onClick={FluidNotifications.clear()}){Text("Clear all",style=F.text(11,true),color=F.purple)}
        }
        if(notifications.isEmpty()) Column(Modifier.fillMaxWidth().glass(28.dp).combinedClickable(onClick={if(!state.device.mediaAccess)model.accessNotifications()},onLongClick={edit(FluidWidget.NOTIFICATIONS)}).padding(20.dp)) {
            Text(if(state.device.mediaAccess)"All caught up"else"A clearer view of your day",style=F.text(15,true))
            Text(if(state.device.mediaAccess)"New notifications will stack here."else"Connect notifications to show your live updates.",style=F.text(11,muted=true),modifier=Modifier.padding(top=7.dp))
            if(!state.device.mediaAccess)GlassButton("Connect notifications",Modifier.padding(top=12.dp),onClick=model::accessNotifications)
        } else if(detail) notifications.forEach { notice -> key(notice.key) { DismissibleNotification(notice,model,{edit(FluidWidget.NOTIFICATIONS)},size) } }
        else {
            val height=when(size){"Compact"->126.dp;"Large"->220.dp;else->164.dp}
            Box(Modifier.fillMaxWidth().height(height+(notifications.size-1)*14.dp)) {
                notifications.asReversed().forEach { notice -> key(notice.key) {
                    val index=notifications.indexOf(notice)
                    DismissibleNotification(notice,model,{edit(FluidWidget.NOTIFICATIONS)},size,
                        Modifier.padding(horizontal=(index.coerceAtMost(3)*4).dp).offset(y=(index*14).dp).height(height))
                } }
            }
            if(notifications.size>1)Text("${notifications.size} notifications",style=F.text(10,muted=true),modifier=Modifier.padding(start=8.dp))
        }

    }
}

@Composable private fun DismissibleNotification(notice:FluidNotification,model:FluidModel,hold:()->Unit,size:String,modifier:Modifier=Modifier) {
    val scope=rememberCoroutineScope()
    val offset=remember(notice.key){Animatable(0f)}
    var width by remember{mutableFloatStateOf(1f)}
    var dragging by remember{mutableStateOf(false)}
    var total by remember{mutableFloatStateOf(0f)}
    val latestHold by rememberUpdatedState(hold)
    Column(modifier.fillMaxWidth().testTag("notification-card:${notice.key}").padding(bottom=if(modifier==Modifier)10.dp else 0.dp).onSizeChanged{width=it.width.toFloat()}.graphicsLayer{translationX=offset.value;rotationZ=offset.value/width*4f;alpha=1f-(abs(offset.value)/width).coerceIn(0f,.8f)}
        .pointerInput(notice.key){detectHorizontalDragGestures(onDragStart={total=offset.value;dragging=true},onHorizontalDrag={change,delta->change.consume();total+=delta;scope.launch{offset.snapTo(total)}},onDragEnd={dragging=false;scope.launch{
            if(abs(total)>width*.28f && notice.clearable){offset.animateTo(sign(total)*width*1.15f,tween(200));FluidNotifications.dismiss(notice.key)
                val removed=withTimeoutOrNull(1500){FluidNotifications.items.first{list->list.none{it.key==notice.key}}}
                if(removed==null)offset.animateTo(0f,spring(1f,350f))
            }
            else {offset.animateTo(0f,spring(.85f,420f));if(!notice.clearable && abs(total)>width*.28f)model.message("This ongoing notification cannot be cleared")}
        }},onDragCancel={dragging=false;scope.launch{offset.animateTo(0f)}})}
        .glass(28.dp,true).combinedClickable(onClick={model.openNotification(notice)},onLongClick={latestHold()}).padding(if(size=="Compact")16.dp else 20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            if(notice.icon!=null)Image(notice.icon.asImageBitmap(),null,Modifier.size(29.dp).clip(RoundedCornerShape(9.dp)))
            Text(notice.app,style=F.text(11,true),modifier=Modifier.weight(1f).padding(start=9.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(fluidDate(notice.time,"HH:mm"),style=F.text(10,muted=true))
        }
        Spacer(Modifier.height(12.dp))
        Text(notice.title,style=F.text(if(size=="Compact")13 else 16,true),maxLines=2,overflow=TextOverflow.Ellipsis)
        if(notice.text.isNotBlank())Text(notice.text,style=F.text(12,muted=true),maxLines=if(size=="Large")5 else if(size=="Compact")1 else 3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=7.dp))
    }
}
