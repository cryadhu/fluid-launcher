@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
val LocalClockPanel = staticCompositionLocalOf<(String)->Unit> { {} }
internal val LocalLeagueExpand = staticCompositionLocalOf<()->Unit> { {} }
val LocalWidgetEdit = staticCompositionLocalOf<(FluidWidget)->Unit> { {} }
internal val LocalPageEdit = staticCompositionLocalOf<PageEdit?> { null }
/** Shared with the page surface so content-level press anchors still light the full page. */
internal val LocalPagePress = staticCompositionLocalOf<MutableInteractionSource?> { null }
internal fun widgetMinimum(kind:FluidWidget,config:FluidConfig):Dp = if(kind==FluidWidget.BRIGHTNESS || kind==FluidWidget.VOLUME) when(config.sizes[kind]) { "Compact"->64.dp;"Large"->112.dp;else->80.dp } else when(config.sizes[kind]) { "Compact" -> 96.dp; "Large" -> if(kind==FluidWidget.NOTIFICATIONS) 248.dp else 230.dp; else -> 138.dp }

@Composable internal fun FluidHome(state:FluidState,model:FluidModel,hero:FluidHero,badges:Set<String>,modifier:Modifier,calendar:()->Unit,activity:()->Unit,customize:()->Unit,ask:()->Unit,space:()->Unit,launch:(InstalledApp,Rect)->Unit,edit:PageEdit?=null) {
    val editable=state.config.widgets.filterNot{it in tinyControls}
    val scroll=rememberScrollState()
    val pressSource=LocalPagePress.current
    val ownPress=remember{MutableInteractionSource()}
    val density=LocalDensity.current
    val phase=rememberInfiniteTransition(label="Widget wobble").animateFloat(0f,6.2831855f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="Wobble")
    SideEffect { edit?.bind(editable,with(density){14.dp.toPx()},scroll) }
    Box(modifier.navigationBarsPadding().padding(bottom=32.dp).onGloballyPositioned{edit?.viewport=it.boundsInRoot()}.testTag("fluid-home")) {
        Column(Modifier.fillMaxSize()) {
            // Kept as a gesture/semantics anchor for the scroll container; its own ripple is
            // suppressed because it is inset by the page padding. The page-level clickable in
            // FluidLauncher draws the full-bleed highlight instead.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).combinedClickable(interactionSource=pressSource ?: ownPress,indication=null,onClick={},onLongClick={if(edit?.active!=true)customize()}).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(fluidDate(state.now,"EEEE, MMM d"),style=F.text(12,muted=true),modifier=Modifier.testTag("fluid-home-date"))
                    if(edit?.active!=true) GlassIcon("edit","Customize home",Modifier.size(34.dp),onClick=customize) else Spacer(Modifier.size(34.dp))
                }
                WidgetGrid(editable,state.config,edit,14.dp,phase) { kind ->
                    if(kind==FluidWidget.NOTIFICATIONS) Box(Modifier.onGloballyPositioned { hero.bounds[kind]=it.boundsInRoot() }) { NotificationStack(state,model) }
                    else ExpandableWidget(kind,state,model,hero,calendar,activity)
                }
                ControlWidgetRows(state.config.widgets.filter{it in tinyControls},state,model)
            }
            val dockBlur by animateDpAsState(if(edit?.active==true) 10.dp else 0.dp,spring(1f,300f),label="Dock edit blur")
            Box(Modifier.blur(dockBlur,BlurredEdgeTreatment.Unbounded)) { FluidDock(state, model, badges, ask, launch) }
        }
    }
}

@Composable internal fun AnalogClock(now:Long,modifier:Modifier=Modifier,click:(()->Unit)?=null) {
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
    // Seconds are an angle, so fold them back into the real hour/minute positions
    // instead of feeding the angle straight into every hand.
    val minuteAngle=time.minute*6f+seconds/60f
    val hourAngle=(time.hour%12)*30f+time.minute*.5f+seconds/720f
    val ink=F.ink
    val face=if(LocalGlass.current.dark)Color(0xFF35403D)else Color.White
    BoxWithConstraints(modifier.then(if(click!=null) Modifier.combinedClickable(onClick=click,onLongClick={edit(FluidWidget.CLOCK)}) else Modifier).semantics{contentDescription="Clock ${fluidDate(now,"HH:mm")}"},contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val radius=size.minDimension*.46f
            val center=Offset(size.width/2,size.height/2)
            drawCircle(Brush.radialGradient(listOf(face.copy(alpha=.95f),face.copy(alpha=.65f)),center,radius),radius,center)
            repeat(60){i -> val a=Math.toRadians(i*6.0-90);val outer=radius*.88f;val inner=radius*if(i%5==0).79f else .86f
                drawLine(ink.copy(alpha=if(i%5==0).45f else .14f),center+Offset(cos(a).toFloat()*inner,sin(a).toFloat()*inner),center+Offset(cos(a).toFloat()*outer,sin(a).toFloat()*outer),if(i%5==0)1.3.dp.toPx()else .7.dp.toPx(),StrokeCap.Round)
            }
            fun hand(angle:Float,length:Float,width:Float,color:Color){val a=Math.toRadians(angle.toDouble()-90);drawLine(color,center,center+Offset(cos(a).toFloat()*radius*length,sin(a).toFloat()*radius*length),width.dp.toPx(),StrokeCap.Round)}
            hand(hourAngle,.46f,3f,ink)
            hand(minuteAngle,.67f,2f,ink)
            hand(seconds,.75f,.8f,Color(0xFFDF9279))
            drawCircle(ink,2.4.dp.toPx(),center)
        }
        Text("12",style=F.text(8,muted=true),modifier=Modifier.align(Alignment.TopCenter).padding(top=maxHeight*.22f))
    }
}

/** The animated sky behind the weather widget, driven by the live condition code. */
@Composable internal fun WeatherSky(weather:HomeWeather?,modifier:Modifier=Modifier) {
    val transition=rememberInfiniteTransition(label="Weather atmosphere")
    val drift by transition.animateFloat(-6f,6f,infiniteRepeatable(tween(9000,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="Cloud drift")
    val driftBack by transition.animateFloat(6f,-6f,infiniteRepeatable(tween(12000,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="Back cloud drift")
    val pulse by transition.animateFloat(1f,1.08f,infiniteRepeatable(tween(1500,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="Sun pulse")
    val rain by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1100,easing=LinearEasing)),label="Rain fall")
    val cycle by transition.animateFloat(0f,1f,infiniteRepeatable(tween(4200,easing=LinearEasing)),label="Weather cycle")
    val spin by transition.animateFloat(0f,360f,infiniteRepeatable(tween(30000,easing=LinearEasing)),label="Sun rays")
    val code=weather?.code ?: -1
    val thunder=code in 95..99
    val wet=code in 51..67 || code in 80..82 || thunder
    val snow=code in 71..77 || code in 85..86
    val fog=code==45 || code==48
    val night=weather?.isDay==false
    val windy=(weather?.windSpeed ?: 0.0)>=20
    val cloudy=code in 2..3 || wet || snow || fog
    val sunny=weather!=null && !night && !fog && !wet && !snow && code!=3
    val temp=weather?.temperature?.roundToInt()?.let{"$it\u00b0"} ?: "--\u00b0"
    Box(modifier.semantics{contentDescription="Weather $temp ${weather?.condition ?: "Choose city"}"}) {
        Canvas(Modifier.fillMaxSize()) {
            val x=size.width*.5f;val y=size.height*.46f;val u=min(size.width,size.height*1.15f)
            val sway=drift.dp.toPx()
            val withCloud=cloudy || weather==null
            if(sunny) {
                val c=if(withCloud) Offset(x+u*.11f,y-u*.11f) else Offset(x,y)
                val r=u*(if(withCloud) .11f else .16f)*pulse
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A5).copy(alpha=.75f),Color(0xFFFFE3A5).copy(alpha=0f)),c,r*2.4f),r*2.4f,c)
                rotate(spin,c) {
                    repeat(12){i->
                        val a=Math.toRadians(i*30.0).toFloat();val spread=.075f
                        val ray=Path().apply {
                            moveTo(c.x+cos(a-spread)*r*1.28f,c.y+sin(a-spread)*r*1.28f)
                            lineTo(c.x+cos(a)*r*(if(i%2==0)1.75f else 1.55f),c.y+sin(a)*r*(if(i%2==0)1.75f else 1.55f))
                            lineTo(c.x+cos(a+spread)*r*1.28f,c.y+sin(a+spread)*r*1.28f);close()
                        }
                        drawPath(ray,Color(0xFFFFD27A).copy(alpha=.85f))
                    }
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4CF),Color(0xFFFFC566)),c-Offset(r*.3f,r*.3f),r*1.3f),r,c)
            }
            if(night) {
                val c=if(withCloud) Offset(x+u*.10f,y-u*.12f) else Offset(x,y)
                val r=u*.15f
                drawCircle(Brush.radialGradient(listOf(Color(0xFFD6E5EF).copy(alpha=.35f),Color.Transparent),c,r*2.2f),r*2.2f,c)
                val moon=Path().apply{addOval(Rect(c,r))}
                val cut=Path().apply{addOval(Rect(Offset(c.x+r*.55f,c.y-r*.35f),r*.9f))}
                drawPath(Path.combine(PathOperation.Difference,moon,cut),Brush.linearGradient(listOf(Color(0xFFF4F8FC),Color(0xFFC5D6E4)),c-Offset(r,r),c+Offset(r,r)))
                listOf(Offset(-.34f,-.26f),Offset(-.12f,-.36f),Offset(.36f,-.32f),Offset(.30f,.02f),Offset(-.40f,.04f)).forEachIndexed{i,o->
                    val twinkle=.35f+.65f*((sin((cycle+i*.23f)*2f*PI.toFloat())+1f)/2f)
                    star(Offset(x+o.x*u,y+o.y*u),(if(i%2==0)3.6f else 2.6f).dp.toPx()*(.7f+.3f*twinkle),Color.White.copy(alpha=twinkle))
                }
            }
            if(withCloud) {
                val (top,bottom)=when {
                    thunder->Color(0xFF9AA6B6) to Color(0xFF56657A)
                    wet->Color(0xFFC6D0DB) to Color(0xFF7D90A3)
                    night->Color(0xFFD0DAE5) to Color(0xFF8E9FB2)
                    snow->Color(0xFFF7F9FC) to Color(0xFFC2CFDC)
                    else->Color.White to Color(0xFFD3DEE9)
                }
                if(wet || snow || fog || code==3) cloud(Offset(x+u*.14f+driftBack.dp.toPx(),y-u*.09f),u*.42f,lerp(top,bottom,.35f),bottom,.92f)
                val flash=if(thunder) thunderFlash(cycle) else 0f
                cloud(Offset(x+sway,y),u*.62f,lerp(top,Color.White,flash*.5f),bottom,1f)
            }
            if(wet) {
                val count=if(thunder) 6 else 8
                repeat(count){i->
                    val p=(rain*(if(i%2==0)1f else .85f)+i*.37f)%1f
                    val px=x-u*.2f+i*u*(.4f/count)+sway
                    val py=y+u*.13f+p*u*.32f
                    raindrop(Offset(px-p*u*.04f,py),u,sin(p*PI.toFloat()))
                }
            }
            if(snow) repeat(7){i->
                val p=(cycle*(if(i%2==0)1f else .8f)+i*.29f)%1f
                val px=x-u*.19f+i*u*.062f+sin((p*2f+i*.4f)*PI.toFloat())*4.dp.toPx()+sway
                val py=y+u*.13f+p*u*.31f
                snowflake(Offset(px,py),(if(i%3==0)5.5f else if(i%3==1)4.2f else 3.4f).dp.toPx(),p*180f+i*40f,sin(p*PI.toFloat()).coerceIn(0f,1f))
            }
            if(thunder) {
                val flash=thunderFlash(cycle)
                val o=Offset(x+u*.02f+sway,y+u*.10f)
                val bolt=Path().apply {
                    moveTo(o.x+u*.05f,o.y);lineTo(o.x-u*.07f,o.y+u*.17f);lineTo(o.x-u*.005f,o.y+u*.17f)
                    lineTo(o.x-u*.06f,o.y+u*.33f);lineTo(o.x+u*.10f,o.y+u*.12f);lineTo(o.x+u*.03f,o.y+u*.12f)
                    lineTo(o.x+u*.10f,o.y);close()
                }
                if(flash>0f) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A6).copy(alpha=.55f*flash),Color.Transparent),Offset(o.x,o.y+u*.15f),u*.32f),u*.32f,Offset(o.x,o.y+u*.15f))
                drawPath(bolt,Brush.verticalGradient(listOf(Color(0xFFFFF6C2),Color(0xFFFFC23D)),o.y,o.y+u*.33f),alpha=.45f+.55f*flash)
            }
            if(fog) repeat(4){i->
                val off=sin((cycle+i*.25f)*2f*PI.toFloat())*u*.05f*(if(i%2==0)1f else -1f)
                val w=u*(.62f-i%2*.12f);val h=u*.05f
                val left=x-w/2f+off+u*(if(i%2==0)-.02f else .04f)
                val yy=y+u*(.02f+i*.085f)
                drawRoundRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha=0f),Color.White.copy(alpha=.8f),Color.White.copy(alpha=0f)),left,left+w),Offset(left,yy),Size(w,h),CornerRadius(h/2f))
            }
            if(windy) repeat(3){i->
                val p=(cycle+i*.33f)%1f
                val length=u*listOf(.44f,.34f,.40f)[i]
                val start=Offset(x-u*.40f+p*u*.18f,y+u*(-.04f+i*.12f))
                gust(start,length,u*.045f,(if(night) Color(0xFFDDE7F0) else Color.White).copy(alpha=.9f*sin(p*PI.toFloat())),2.2.dp.toPx())
            }
        }
    }
}

/** Two quick strikes early in each cycle, then a calm stretch. */
private fun thunderFlash(cycle:Float):Float = when {
    cycle<.05f -> 1f-cycle/.05f
    cycle>=.09f && cycle<.15f -> 1f-(cycle-.09f)/.06f
    else -> 0f
}

private fun DrawScope.cloud(center:Offset,s:Float,top:Color,bottom:Color,alpha:Float) {
    val shape=Path().apply {
        addRoundRect(RoundRect(Rect(center.x-s*.42f,center.y-s*.02f,center.x+s*.42f,center.y+s*.20f),CornerRadius(s*.11f)))
        addOval(Rect(Offset(center.x-s*.21f,center.y+s*.01f),s*.17f))
        addOval(Rect(Offset(center.x+s*.03f,center.y-s*.08f),s*.24f))
        addOval(Rect(Offset(center.x+s*.26f,center.y+s*.04f),s*.15f))
    }
    // A radial falloff squashed into an ellipse gives a soft contact shadow on every API level.
    val shade=Offset(center.x+s*.03f,center.y+s*.24f)
    scale(1f,.26f,shade){drawCircle(Brush.radialGradient(listOf(Color(0xFF29384A).copy(alpha=.20f*alpha),Color(0xFF29384A).copy(alpha=.07f*alpha),Color.Transparent),shade,s*.5f),s*.5f,shade)}
    drawPath(shape,Brush.verticalGradient(listOf(top,bottom),center.y-s*.32f,center.y+s*.22f),alpha=alpha)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=.55f*alpha),Color.White.copy(alpha=0f)),Offset(center.x-s*.02f,center.y-s*.18f),s*.2f),s*.2f,Offset(center.x-s*.02f,center.y-s*.18f))
}

private fun DrawScope.raindrop(at:Offset,u:Float,alpha:Float) {
    val r=u*.017f;val length=u*.06f
    val drop=Path().apply {
        moveTo(0f,-length)
        cubicTo(r*.5f,-length*.45f,r,-r*.4f,r,0f)
        arcTo(Rect(-r,-r,r,r),0f,180f,false)
        cubicTo(-r,-r*.4f,-r*.5f,-length*.45f,0f,-length)
        close()
    }
    withTransform({translate(at.x,at.y);rotate(14f,Offset.Zero)}) {
        drawPath(drop,Brush.verticalGradient(listOf(Color(0xFFCBE9F6),Color(0xFF5FA8D3)),-length,r),alpha=alpha.coerceIn(0f,1f))
    }
}

private fun DrawScope.snowflake(at:Offset,radius:Float,angle:Float,alpha:Float) {
    val stroke=(radius*.26f).coerceAtLeast(1f)
    rotate(angle,at) {
        listOf(Color(0xFF7F97AF).copy(alpha=.45f*alpha) to stroke*1.9f,Color.White.copy(alpha=alpha) to stroke).forEach{(color,width)->
            repeat(6){i->
                val a=i*PI.toFloat()/3f
                val tip=Offset(at.x+cos(a)*radius,at.y+sin(a)*radius)
                drawLine(color,at,tip,width,StrokeCap.Round)
                val joint=Offset(at.x+cos(a)*radius*.58f,at.y+sin(a)*radius*.58f)
                listOf(-.65f,.65f).forEach{b->drawLine(color,joint,Offset(joint.x+cos(a+b)*radius*.36f,joint.y+sin(a+b)*radius*.36f),width*.85f,StrokeCap.Round)}
            }
        }
    }
}

private fun DrawScope.star(at:Offset,radius:Float,color:Color) {
    val inner=radius*.28f
    val sparkle=Path().apply {
        moveTo(at.x,at.y-radius)
        quadraticTo(at.x+inner*.3f,at.y-inner*.3f,at.x+radius,at.y)
        quadraticTo(at.x+inner*.3f,at.y+inner*.3f,at.x,at.y+radius)
        quadraticTo(at.x-inner*.3f,at.y+inner*.3f,at.x-radius,at.y)
        quadraticTo(at.x-inner*.3f,at.y-inner*.3f,at.x,at.y-radius)
        close()
    }
    drawPath(sparkle,color)
}

private fun DrawScope.gust(start:Offset,length:Float,curl:Float,color:Color,width:Float) {
    val end=Offset(start.x+length,start.y)
    val path=Path().apply {
        moveTo(start.x,start.y)
        cubicTo(start.x+length*.35f,start.y-curl*.7f,start.x+length*.65f,start.y+curl*.7f,end.x,end.y)
        arcTo(Rect(Offset(end.x,end.y-curl),curl),90f,-270f,false)
    }
    drawPath(path,Color(0xFF7F97AF).copy(alpha=color.alpha*.4f),style=Stroke(width*2f,cap=StrokeCap.Round,join=StrokeJoin.Round))
    drawPath(path,color,style=Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round))
}

@Composable internal fun WidgetEditSheet(kind:FluidWidget,page:String,state:FluidState,model:FluidModel,arrange:()->Unit,resize:()->Unit,close:()->Unit) {
    val canArrange=kind !in tinyControls
    val options=sizeOptions(kind)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.09f)).clickable(onClick=close).statusBarsPadding().navigationBarsPadding().padding(22.dp),contentAlignment=Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().glass(32.dp,true).clickable{}.padding(22.dp).testTag("widget-edit"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(kind.title,style=F.text(22,true),modifier=Modifier.weight(1f));GlassIcon("close","Close widget options",onClick=close)}
            Text("Size",style=F.text(13,true))
            WidgetSizeOptions(kind,widgetSize(kind,state.config)){model.resizeWidget(kind,it)}
            if(canArrange && options.isEmpty()) {
                GlassButton("Arrange",Modifier.fillMaxWidth(),glyph="app") { arrange(); close() }
                Text("Drag widgets directly on the page and they settle into place.",style=F.text(11,muted=true))
            } else if(options.isEmpty()) {
            } else if(canArrange) {
                Text("Edit with gestures",style=F.text(12,muted=true))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    GlassButton("Arrange",Modifier.weight(1f),glyph="app") { arrange(); close() }
                    GlassButton("Resize",Modifier.weight(1f),glyph="settings") { resize(); close() }
                }
                Text("Arrange: drag widgets directly on the page and they settle into place. Resize: drag a handle to change size.",style=F.text(11,muted=true))
            } else {
                GlassButton("Resize",Modifier.fillMaxWidth(),glyph="settings") { resize(); close() }
                Text("Drag a handle to change this widget's size.",style=F.text(11,muted=true))
            }
            GlassButton("Remove widget",Modifier.fillMaxWidth(),glyph="bin"){model.removeWidgetForPage(page,kind);close()}
        }
    }
}

/** Full-screen, grid-based resize surface.  The corner/edge handles and preview deliberately mirror
 * the home layout so changing size never feels like a disconnected setting. */
@Composable internal fun WidgetResizeEditor(kind:FluidWidget,state:FluidState,model:FluidModel,page:String,done:()->Unit) {
    val size=widgetSize(kind,state.config)
    val choices=sizeOptions(kind)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.13f)).statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        Column(Modifier.fillMaxSize().glass(30.dp,true).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text("Resize widget",style=F.text(23,true));Text("Drag a handle · snaps to the grid",style=F.text(12,muted=true))};GlassButton("Done",glyph="check",primary=true,onClick=done) }
            val previewHeight=when(size){"Compact"->150.dp;"Large"->280.dp;else->215.dp}
            Box(Modifier.fillMaxWidth().height(previewHeight).padding(8.dp).border(2.dp,F.purple,RoundedCornerShape(26.dp)).pointerInput(size){
                var distance=0f; detectDragGestures(onDragStart={distance=0f},onDrag={change,drag->change.consume();distance+=drag.x+drag.y;if(abs(distance)>70f && choices.isNotEmpty()){val at=choices.indexOf(size).coerceAtLeast(0);model.resizeWidget(kind,choices[(at+if(distance>0)1 else -1).coerceIn(0,choices.lastIndex)]);distance=0f}})
            },contentAlignment=Alignment.Center) {
                Box(Modifier.align(Alignment.TopCenter).offset(y=(-6).dp).width(28.dp).height(6.dp).clip(CircleShape).background(F.purple))
                Box(Modifier.align(Alignment.CenterStart).offset(x=(-6).dp).width(6.dp).height(28.dp).clip(CircleShape).background(F.purple))
                Box(Modifier.align(Alignment.CenterEnd).offset(x=6.dp).width(6.dp).height(28.dp).clip(CircleShape).background(F.purple))
                listOf(Alignment.TopStart,Alignment.TopEnd,Alignment.BottomStart,Alignment.BottomEnd).forEach { alignment -> Box(Modifier.align(alignment).offset(if(alignment==Alignment.TopStart||alignment==Alignment.BottomStart)(-7).dp else 7.dp,if(alignment==Alignment.TopStart||alignment==Alignment.TopEnd)(-7).dp else 7.dp).size(14.dp).clip(CircleShape).background(Color.White).border(2.dp,F.purple,CircleShape)) }
                Column(horizontalAlignment=Alignment.CenterHorizontally){ Glyph(kind.glyph,F.purple,Modifier.size(30.dp));Text(kind.title,style=F.text(18,true));Text("$size · ${widgetGrid(kind,size)}",style=F.text(12,muted=true)) }
            }
            Text("Widget size",style=F.text(14,true));WidgetSizeOptions(kind,size){model.resizeWidget(kind,it)}
            Text("Use the handles to stretch the widget. The launcher snaps it to the nearest available grid size.",style=F.text(11,muted=true))
            GlassButton("Remove widget",Modifier.fillMaxWidth(),glyph="bin"){ model.removeWidgetForPage(page,kind);done() }
        }
    }
}

@Composable internal fun WidgetSizeOptions(kind:FluidWidget,selected:String,select:(String)->Unit) {
    val options=sizeOptions(kind)
    if(options.isEmpty()) { Text("Fixed size · ${widgetGrid(kind)}",style=F.text(12,muted=true)); return }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked=maxWidth<330.dp || LocalDensity.current.fontScale>1.15f
        val content:@Composable (String,Modifier)->Unit={label,modifier->
            Box(modifier.heightIn(min=48.dp).glass(20.dp).background(if(selected==label)F.purple.copy(alpha=.16f)else Color.Transparent)
                .clickable(role=Role.RadioButton){select(label)}.semantics{this.selected=label==selected}.padding(horizontal=8.dp,vertical=9.dp),contentAlignment=Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(label,style=F.text(12,true),maxLines=1,softWrap=false,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    Text(widgetGrid(kind,label),style=F.text(10,muted=true),maxLines=1)
                }
            }
        }
        if(stacked)Column(verticalArrangement=Arrangement.spacedBy(8.dp)){options.forEach{content(it,Modifier.fillMaxWidth())}}
        else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){options.forEach{content(it,Modifier.weight(1f))}}
    }
}
