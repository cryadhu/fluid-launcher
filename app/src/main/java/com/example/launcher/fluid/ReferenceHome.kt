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
import com.example.launcher.data.HomeWeather
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.time.*
import kotlin.math.*

val LocalWeatherLocate = staticCompositionLocalOf<()->Unit> { {} }
val LocalContactsAccess = staticCompositionLocalOf<()->Unit> { {} }
val LocalSpaceWidgetGallery = staticCompositionLocalOf<()->Unit> { {} }
val LocalWidgetEdit = staticCompositionLocalOf<(FluidWidget)->Unit> { {} }
data class WidgetGestureMode(val widget:FluidWidget?=null,val resize:Boolean=false,val space:Boolean=false,val plan:Boolean=false,val settled:()->Unit={})
private data class WeatherPreview(val code:Int,val label:String,val day:Boolean,val wind:Double)
val LocalWidgetGestureMode = staticCompositionLocalOf { WidgetGestureMode() }
internal fun weatherForDisplay(state:FluidState):HomeWeather? {
    if(state.weatherPreview==0)return state.weather
    val preview=listOf(
        WeatherPreview(0,"Sunny",true,5.0), WeatherPreview(3,"Cloudy",true,9.0), WeatherPreview(63,"Rainy",true,14.0),
        WeatherPreview(71,"Snow",true,7.0), WeatherPreview(45,"Misty",true,5.0), WeatherPreview(2,"Windy",true,32.0), WeatherPreview(1,"Clear night",false,4.0)
    )[state.weatherPreview]
    val base=state.weather ?: HomeWeather("Preview",22.0,preview.label,emptyList(),preview.code,preview.day,preview.wind)
    return base.copy(code=preview.code,condition=preview.label,isDay=preview.day,windSpeed=preview.wind)
}
internal fun widgetMinimum(kind:FluidWidget,config:FluidConfig):Dp = if(kind==FluidWidget.BRIGHTNESS || kind==FluidWidget.VOLUME) when(config.sizes[kind]) { "Compact"->64.dp;"Large"->112.dp;else->80.dp } else when(config.sizes[kind]) { "Compact" -> 96.dp; "Large" -> if(kind==FluidWidget.NOTIFICATIONS) 248.dp else 230.dp; else -> 138.dp }

@Composable internal fun FluidHome(state:FluidState,model:FluidModel,hero:FluidHero,badges:Set<String>,modifier:Modifier,calendar:()->Unit,activity:()->Unit,customize:()->Unit,ask:()->Unit,space:()->Unit,launch:(InstalledApp,Rect)->Unit) {
    val edit=LocalWidgetEdit.current
    Column(modifier.navigationBarsPadding().padding(bottom=32.dp).testTag("fluid-home")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).combinedClickable(onClick={},onLongClick=customize).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(fluidDate(state.now,"EEEE, MMM d"),style=F.text(12,muted=true),modifier=Modifier.testTag("fluid-home-date"))
                GlassIcon("edit","Customize home",Modifier.size(34.dp),onClick=customize)
            }
            val top=state.config.widgets.filter{it==FluidWidget.CLOCK || it==FluidWidget.WEATHER}
            if(top.isNotEmpty()) Row(Modifier.fillMaxWidth().animateContentSize(spring(1f,350f)),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                top.forEach{kind ->
                    val height=when(state.config.sizes[kind]){"Compact"->112.dp;"Large"->210.dp;else->156.dp}
                    Box(Modifier.weight(if(state.config.sizes[kind]=="Large")1.4f else 1f).height(height).animateContentSize(spring(1f,350f)).widgetGesture(kind,state,model).testTag("fluid-widget-${kind.name}")) {
                        if(kind==FluidWidget.CLOCK) AnalogClock(state.now,Modifier.fillMaxSize()){model.open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))}
                        else WeatherScene(state,Modifier.fillMaxSize(),{hero.open(kind)}, {edit(kind)})
                        // The unframed weather still participates in the same shared card transition.
                        Box(Modifier.fillMaxSize().onGloballyPositioned{hero.bounds[kind]=Rect(it.positionInRoot(),it.size.toSize())})
                    }
                }
            }
            state.config.widgets.filterNot{it==FluidWidget.CLOCK || it==FluidWidget.WEATHER || it in tinyControls}.forEach{kind -> key(kind){
                if(kind==FluidWidget.NOTIFICATIONS) Box(Modifier.widgetGesture(kind,state,model).onGloballyPositioned { hero.bounds[kind]=it.boundsInRoot() }) { NotificationStack(state,model) }
                else {
                    Text(if(kind==FluidWidget.AGENDA)"Today" else kind.title,style=F.text(11,muted=true),modifier=Modifier.padding(start=8.dp,top=2.dp))
                    ExpandableWidget(kind,state,model,hero,calendar,activity)
                }
            } }
            ControlWidgetRows(state.config.widgets.filter{it in tinyControls},state,model)
        }
        FluidDock(state, model, badges, ask, launch)

    }
}

@Composable internal fun AnalogClock(now:Long,modifier:Modifier=Modifier,click:()->Unit={}) {
    val edit=LocalWidgetEdit.current
    val time=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    // Keep an unwrapped angle so the hand crosses 59 -> 00 naturally. The first
    // frame is snapped to real time instead of animating all the way from 12.
    val secondTarget=(time.second+time.nano/1_000_000_000f)*6f
    val secondHand=remember { Animatable(secondTarget) }
    LaunchedEffect(time.minute,time.second) {
        val delta=((secondTarget-secondHand.value+540f)%360f)-180f
        secondHand.animateTo(secondHand.value+delta,tween(850,easing=LinearEasing))
    }
    val seconds=secondHand.value
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
    val weather=weatherForDisplay(state)
    val code=weather?.code ?: -1
    val wet=code in 51..67 || code in 80..82 || code in 95..99
    val snow=code in 71..77 || code in 85..86
    val fog=code==45 || code==48
    val night=weather?.isDay==false
    val windy=(weather?.windSpeed ?: 0.0)>=20
    val cloudy=code in 1..3 || wet || snow || fog
    val temp=weather?.temperature?.roundToInt()?.let{"$it\u00b0"} ?: "--\u00b0"
    Box(modifier.combinedClickable(onClick=click,onLongClick=hold).semantics{contentDescription="Weather $temp ${weather?.condition ?: "Choose city"}"}) {
        Canvas(Modifier.fillMaxSize().graphicsLayer{translationX=drift.dp.toPx()}) {
            val x=size.width*.32f;val y=size.height*.47f;val unit=size.minDimension
            if(weather!=null && !night && !fog && !wet && !snow && code!=3){
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A5),Color(0xFFFFE3A5).copy(alpha=.05f)),Offset(x,y),unit*.3f),unit*.3f,Offset(x,y))
                drawCircle(Color(0xFFFFD38B),unit*.16f,Offset(x,y))
            }
            if(night){
                val moon=Path().apply{addOval(Rect(x-unit*.17f,y-unit*.17f,x+unit*.17f,y+unit*.17f))}
                val cut=Path().apply{addOval(Rect(x-unit*.05f,y-unit*.22f,x+unit*.25f,y+unit*.1f))}
                drawPath(Path.combine(PathOperation.Difference,moon,cut),Color(0xFFD6E5EF))
                repeat(4){i->drawCircle(Color.White.copy(alpha=.6f),1.3.dp.toPx(),Offset(x+unit*(i*.12f-.2f),y-unit*(.25f+i%2*.08f)))}
            }
            if(cloudy || weather==null){
                val cloud=Path().apply {
                    moveTo(x-unit*.28f,y+unit*.16f)
                    cubicTo(x-unit*.43f,y+unit*.15f,x-unit*.43f,y-unit*.07f,x-unit*.27f,y-unit*.09f)
                    cubicTo(x-unit*.24f,y-unit*.28f,x+unit*.02f,y-unit*.31f,x+unit*.10f,y-unit*.12f)
                    cubicTo(x+unit*.25f,y-unit*.18f,x+unit*.35f,y-unit*.06f,x+unit*.32f,y+unit*.03f)
                    cubicTo(x+unit*.47f,y+unit*.05f,x+unit*.43f,y+unit*.17f,x+unit*.30f,y+unit*.17f)
                    lineTo(x-unit*.28f,y+unit*.16f);close()
                }
                val top=if(wet)Color(0xFFC1CCD7)else if(night)Color(0xFFD3DFE9)else Color(0xFFFAFCFF)
                val bottom=if(wet)Color(0xFF7C8F9F)else Color(0xFFC4D2DE)
                scale(.76f,.70f,pivot=Offset(x,y)) {
                    drawPath(cloud,Brush.verticalGradient(listOf(top,bottom),y-unit*.27f,y+unit*.18f))
                    drawPath(cloud,Color.White.copy(alpha=.30f),style=Stroke(.6.dp.toPx()))
                }
            }
            if(wet)repeat(7){i->val px=x-unit*.2f+i*unit*.065f;val py=y+unit*.18f+((rain+i*.17f)%1f)*unit*.29f;drawLine(Color(0xFF84BACC).copy(alpha=.6f),Offset(px,py),Offset(px-2.dp.toPx(),py+5.dp.toPx()),1.dp.toPx(),StrokeCap.Round)}
            if(snow)repeat(7){i->val py=y+unit*.18f+((rain+i*.17f)%1f)*unit*.29f;drawCircle(Color.White,2.dp.toPx(),Offset(x-unit*.2f+i*unit*.065f,py))}
            if(windy || fog)repeat(3){i->val shift=if(windy)(rain*unit*.3f)else drift.dp.toPx();val yy=y+unit*(.12f+i*.1f);drawLine(Color(0xFFA4B9BD).copy(alpha=.65f),Offset(x-unit*.28f+shift,yy),Offset(x+unit*.27f+shift,yy),2.dp.toPx(),StrokeCap.Round)}
            if(code in 95..99){val flash=Path().apply{moveTo(x+unit*.08f,y+unit*.15f);lineTo(x-unit*.04f,y+unit*.35f);lineTo(x+unit*.07f,y+unit*.31f);lineTo(x-unit*.02f,y+unit*.49f)};drawPath(flash,Color(0xFFFFD980).copy(alpha=if(rain>.8f).95f else .25f),style=Stroke(2.dp.toPx()))}

        }
        Column(Modifier.align(Alignment.CenterEnd).widthIn(min=94.dp).glass(27.dp).padding(horizontal=17.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            AnimatedContent(temp,transitionSpec={(fadeIn(tween(350))+slideInVertically{it/4}) togetherWith fadeOut(tween(200))},label="Weather temperature"){value->Text(value,style=F.text(35))}
            Text(weather?.condition ?: if(state.weatherLoading) "Locating..." else if(state.config.autoWeather) "Location needed" else "Choose city",style=F.text(9,muted=true),maxLines=2,modifier=Modifier.widthIn(max=86.dp))
            weather?.let{Text(it.city.substringBefore(','),style=F.text(8,muted=true),maxLines=1,modifier=Modifier.padding(top=5.dp))}
        }
    }
}

@Composable internal fun WidgetEditSheet(kind:FluidWidget,state:FluidState,model:FluidModel,space:Boolean=false,plan:Boolean=false,arrange:()->Unit,resize:()->Unit,close:()->Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.09f)).clickable(onClick=close).statusBarsPadding().navigationBarsPadding().padding(22.dp),contentAlignment=Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().glass(32.dp,true).clickable{}.padding(22.dp).testTag("widget-edit"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(kind.title,style=F.text(22,true),modifier=Modifier.weight(1f));GlassIcon("close","Close widget options",onClick=close)}
            Text("Edit with gestures",style=F.text(12,muted=true))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                GlassButton("Arrange",Modifier.weight(1f),glyph="app") { arrange(); close() }
                GlassButton("Resize",Modifier.weight(1f),glyph="settings") { resize(); close() }
            }
            Text("Arrange: drag vertically to swap widgets. Resize: drag horizontally or vertically to change size.",style=F.text(11,muted=true))
            GlassButton("Remove widget",Modifier.fillMaxWidth(),glyph="bin"){if(plan)model.togglePlanWidget(kind)else if(space)model.removeSpaceWidget(kind)else if(kind in state.config.widgets)model.toggleWidget(kind);close()}
        }
    }
}

/** Full-screen, grid-based resize surface.  The corner/edge handles and preview deliberately mirror
 * the home layout so changing size never feels like a disconnected setting. */
@Composable internal fun WidgetResizeEditor(kind:FluidWidget,state:FluidState,model:FluidModel,space:Boolean=false,plan:Boolean=false,done:()->Unit) {
    val size=state.config.sizes[kind] ?: "Standard"
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.13f)).statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        Column(Modifier.fillMaxSize().glass(30.dp,true).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text("Resize widget",style=F.text(23,true));Text("Drag a handle · snaps to the grid",style=F.text(12,muted=true))};GlassButton("Done",glyph="check",primary=true,onClick=done) }
            val previewHeight=when(size){"Compact"->150.dp;"Large"->280.dp;else->215.dp}
            Box(Modifier.fillMaxWidth().height(previewHeight).padding(8.dp).border(2.dp,F.purple,RoundedCornerShape(26.dp)).pointerInput(size){
                var distance=0f; detectDragGestures(onDragStart={distance=0f},onDrag={change,drag->change.consume();distance+=drag.x+drag.y;if(abs(distance)>70f){val choices=listOf("Compact","Standard","Large");val at=choices.indexOf(size);model.resizeWidget(kind,choices[(at+if(distance>0)1 else -1).coerceIn(0,choices.lastIndex)]);distance=0f}})
            },contentAlignment=Alignment.Center) {
                Box(Modifier.align(Alignment.TopCenter).offset(y=(-6).dp).width(28.dp).height(6.dp).clip(CircleShape).background(F.purple))
                Box(Modifier.align(Alignment.CenterStart).offset(x=(-6).dp).width(6.dp).height(28.dp).clip(CircleShape).background(F.purple))
                Box(Modifier.align(Alignment.CenterEnd).offset(x=6.dp).width(6.dp).height(28.dp).clip(CircleShape).background(F.purple))
                listOf(Alignment.TopStart,Alignment.TopEnd,Alignment.BottomStart,Alignment.BottomEnd).forEach { alignment -> Box(Modifier.align(alignment).offset(if(alignment==Alignment.TopStart||alignment==Alignment.BottomStart)(-7).dp else 7.dp,if(alignment==Alignment.TopStart||alignment==Alignment.TopEnd)(-7).dp else 7.dp).size(14.dp).clip(CircleShape).background(Color.White).border(2.dp,F.purple,CircleShape)) }
                Column(horizontalAlignment=Alignment.CenterHorizontally){ Glyph(kind.glyph,F.purple,Modifier.size(30.dp));Text(kind.title,style=F.text(18,true));Text(size,style=F.text(12,muted=true)) }
            }
            Text("Widget size",style=F.text(14,true));WidgetSizeOptions(size){model.resizeWidget(kind,it)}
            Text("Use the handles to stretch the widget. The launcher snaps it to the nearest available grid size.",style=F.text(11,muted=true))
            GlassButton("Remove widget",Modifier.fillMaxWidth(),glyph="bin"){ if(plan)model.togglePlanWidget(kind) else if(space)model.removeSpaceWidget(kind) else model.toggleWidget(kind);done() }
        }
    }
}

@Composable internal fun Modifier.widgetGesture(kind:FluidWidget,state:FluidState,model:FluidModel):Modifier {
    val mode=LocalWidgetGestureMode.current
    if(mode.widget!=kind)return this
    var dragging by remember(kind) { mutableStateOf(false) }; var x by remember(kind){mutableFloatStateOf(0f)};var y by remember(kind){mutableFloatStateOf(0f)}
    val phase by rememberInfiniteTransition(label="Arrange wobble").animateFloat(0f,6.283f,infiniteRepeatable(tween(460,easing=LinearEasing)),label="Wobble phase")
    return graphicsLayer { translationX=x;translationY=y;rotationZ=if(dragging)0f else sin(phase)*1.45f;scaleX=if(dragging)1.035f else 1f;scaleY=if(dragging)1.035f else 1f;shadowElevation=if(dragging)16.dp.toPx() else 0f }
        .pointerInput(kind,mode.space,mode.plan) {
            var swapped=0f
            detectDragGestures(onDragStart={dragging=true;x=0f;y=0f;swapped=0f},onDragCancel={dragging=false;x=0f;y=0f},onDragEnd={dragging=false;x=0f;y=0f},onDrag={change,drag ->
                change.consume();x+=drag.x;y+=drag.y;swapped+=drag.y
                if(abs(swapped)>104f) { val direction=if(swapped>0)1 else -1;if(mode.plan)model.movePlanWidget(kind,direction)else if(mode.space)model.moveSpaceWidget(kind,direction)else model.moveWidget(kind,direction);mode.settled();swapped=0f;y=0f }
            })
        }
}

@Composable internal fun WidgetSizeOptions(selected:String,select:(String)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked=maxWidth<330.dp || LocalDensity.current.fontScale>1.15f
        val content:@Composable (String,Modifier)->Unit={label,modifier->
            Box(modifier.heightIn(min=48.dp).glass(20.dp).background(if(selected==label)F.purple.copy(alpha=.16f)else Color.Transparent)
                .clickable(role=Role.RadioButton){select(label)}.semantics{this.selected=label==selected}.padding(horizontal=8.dp,vertical=13.dp),contentAlignment=Alignment.Center){
                Text(label,style=F.text(12,true),maxLines=1,softWrap=false,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if(stacked)Column(verticalArrangement=Arrangement.spacedBy(8.dp)){listOf("Compact","Standard","Large").forEach{content(it,Modifier.fillMaxWidth())}}
        else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Compact","Standard","Large").forEach{content(it,Modifier.weight(1f))}}
    }
}
