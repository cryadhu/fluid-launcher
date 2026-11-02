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

val LocalWeatherLocate = staticCompositionLocalOf<()->Unit> { {} }
val LocalWidgetEdit = staticCompositionLocalOf<(FluidWidget)->Unit> { {} }
internal fun widgetMinimum(kind:FluidWidget,config:FluidConfig):Dp = when(config.sizes[kind]) { "Compact" -> 96.dp; "Large" -> if(kind==FluidWidget.NOTIFICATIONS) 248.dp else 230.dp; else -> 138.dp }

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
    val weather=state.weather
    val code=weather?.code ?: -1
    val wet=code in 51..67 || code in 80..82 || code in 95..99
    val snow=code in 71..77 || code in 85..86
    val fog=code==45 || code==48
    val night=weather?.isDay==false
    val windy=(weather?.windSpeed ?: 0.0)>=20
    val cloudy=code in 1..3 || wet || snow || fog
    val temp=state.weather?.temperature?.roundToInt()?.let{"$it\u00b0"} ?: "--\u00b0"
    Box(modifier.combinedClickable(onClick=click,onLongClick=hold).semantics{contentDescription="Weather $temp ${state.weather?.condition ?: "Choose city"}"}) {
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
                val color=if(wet)Color(0xFF8E9CA6)else Color(0xFFE7ECEC)
                listOf(Triple(-.15f,.04f,.18f),Triple(.02f,-.04f,.23f),Triple(.22f,.06f,.17f)).forEach{(dx,dy,r)->drawCircle(Brush.radialGradient(listOf(Color.White,color),Offset(x+dx*unit,y+dy*unit),r*unit),r*unit,Offset(x+dx*unit,y+dy*unit))}
            }
            if(wet)repeat(7){i->val px=x-unit*.2f+i*unit*.065f;val py=y+unit*.18f+((rain+i*.17f)%1f)*unit*.29f;drawLine(Color(0xFF84BACC).copy(alpha=.6f),Offset(px,py),Offset(px-2.dp.toPx(),py+5.dp.toPx()),1.dp.toPx(),StrokeCap.Round)}
            if(snow)repeat(7){i->val py=y+unit*.18f+((rain+i*.17f)%1f)*unit*.29f;drawCircle(Color.White,2.dp.toPx(),Offset(x-unit*.2f+i*unit*.065f,py))}
            if(windy || fog)repeat(3){i->val shift=if(windy)(rain*unit*.3f)else drift.dp.toPx();val yy=y+unit*(.12f+i*.1f);drawLine(Color(0xFFA4B9BD).copy(alpha=.65f),Offset(x-unit*.28f+shift,yy),Offset(x+unit*.27f+shift,yy),2.dp.toPx(),StrokeCap.Round)}
            if(code in 95..99){val flash=Path().apply{moveTo(x+unit*.08f,y+unit*.15f);lineTo(x-unit*.04f,y+unit*.35f);lineTo(x+unit*.07f,y+unit*.31f);lineTo(x-unit*.02f,y+unit*.49f)};drawPath(flash,Color(0xFFFFD980).copy(alpha=if(rain>.8f).95f else .25f),style=Stroke(2.dp.toPx()))}

        }
        Column(Modifier.align(Alignment.CenterEnd).widthIn(min=94.dp).glass(27.dp).padding(horizontal=17.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            AnimatedContent(temp,transitionSpec={(fadeIn(tween(350))+slideInVertically{it/4}) togetherWith fadeOut(tween(200))},label="Weather temperature"){value->Text(value,style=F.text(35))}
            Text(state.weather?.condition ?: if(state.weatherLoading) "Locating..." else if(state.config.autoWeather) "Location needed" else "Choose city",style=F.text(9,muted=true),maxLines=2,modifier=Modifier.widthIn(max=86.dp))
            state.weather?.let{Text(it.city.substringBefore(','),style=F.text(8,muted=true),maxLines=1,modifier=Modifier.padding(top=5.dp))}
        }
    }
}

@Composable internal fun WidgetEditSheet(kind:FluidWidget,state:FluidState,model:FluidModel,close:()->Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.09f)).clickable(onClick=close).statusBarsPadding().navigationBarsPadding().padding(22.dp),contentAlignment=Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().glass(32.dp,true).clickable{}.padding(22.dp).testTag("widget-edit"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(kind.title,style=F.text(22,true),modifier=Modifier.weight(1f));GlassIcon("close","Close widget options",onClick=close)}
            Text("Widget size",style=F.text(12,muted=true))
            WidgetSizeOptions(state.config.sizes[kind] ?: "Standard") { model.resizeWidget(kind,it) }

            GlassButton("Remove widget",Modifier.fillMaxWidth(),glyph="bin"){if(kind in state.config.widgets)model.toggleWidget(kind);close()}
        }
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
