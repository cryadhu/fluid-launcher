package com.example.launcher.fluid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import com.example.launcher.data.InstalledApp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A single card moves between source bounds and a safe, expanded viewport. */
@Composable internal fun FluidHeroOverlay(hero:FluidHero,state:FluidState,model:FluidModel,calendar:()->Unit,activity:()->Unit) {
    val kind=hero.kind ?: return
    if(kind==FluidWidget.PREMIER_LEAGUE) {
        LeagueWidgetHero(hero,state,model)
        return
    }
    val progress=remember(kind){Animatable(0f)}
    val scroll=rememberScrollState()
    LaunchedEffect(kind,hero.expanded){
        if(hero.expanded) progress.animateTo(1f,spring(1f,290f))
        else {
            if(scroll.value>0)scroll.animateScrollTo(0, tween(160))
            progress.animateTo(0f,spring(1f,380f));hero.kind=null
        }
    }
    BackHandler { hero.expanded=false }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("fluid-hero-overlay")) {
        val p=progress.value.coerceIn(0f,1f)
        val density=LocalDensity.current
        val safe=WindowInsets.safeDrawing.asPaddingValues()
        val target=with(density){Rect(18.dp.toPx(),(safe.calculateTopPadding()+18.dp).toPx(),(maxWidth-18.dp).toPx(),(maxHeight-safe.calculateBottomPadding()-18.dp).toPx())}
        val source=hero.origin
        val bounds=lerp(source,target,p)
        Box(Modifier.fillMaxSize().background(Color(0xFF493475).copy(alpha=.10f*p)).pointerInput(Unit){detectTapGestures{hero.expanded=false}})
        Column(Modifier.offset{IntOffset(bounds.left.roundToInt(),bounds.top.roundToInt())}.size(with(density){bounds.width.toDp()},with(density){bounds.height.toDp()})
            .glass((30f+6*p).dp,true).testTag("fluid-expanded-widget").verticalScroll(scroll,enabled=p>.99f)
            .padding(horizontal=if(kind==FluidWidget.MUSIC || kind==FluidWidget.MUSIC_OPEN)(20*p).dp else 20.dp).padding(top=if(kind==FluidWidget.MUSIC || kind==FluidWidget.MUSIC_OPEN)(20*p).dp else 20.dp,bottom=24.dp)) {
            Row(Modifier.fillMaxWidth().height((56*p).dp).graphicsLayer{alpha=p},verticalAlignment=Alignment.CenterVertically){
                androidx.compose.material3.Text(kind.title,style=F.text(22,true),modifier=Modifier.weight(1f));GlassIcon("close","Close widget"){hero.expanded=false}
            }
            WidgetBody(kind,state,model,detail=true,calendarAccess=calendar,activityAccess=activity)
        }
    }
}

/** The app's own icon blooms out from its touch bounds before Android takes the task. */
@Composable internal fun AppLaunchBloom(app:InstalledApp,origin:Rect,screen:IntSize) {
    val progress=remember(app.component){Animatable(0f)}
    LaunchedEffect(app.component){progress.animateTo(1f,tween(230,easing=CubicBezierEasing(.18f,0f,.2f,1f)))}
    val p=progress.value
    val density=LocalDensity.current
    val initial=if(origin.width>0)origin else Rect(screen.width/2f-30,screen.height/2f-30,screen.width/2f+30,screen.height/2f+30)
    val end=Rect(0f,0f,screen.width.toFloat(),screen.height.toFloat())
    val bounds=lerp(initial,end,p)
    Box(Modifier.fillMaxSize().pointerInput(Unit){detectTapGestures{} }.background(Color.White.copy(alpha=.08f*p))) {
        Box(Modifier.offset{IntOffset(bounds.left.roundToInt(),bounds.top.roundToInt())}.size(with(density){bounds.width.toDp()},with(density){bounds.height.toDp()})
            .graphicsLayer{alpha=1f-p*.2f}.glass((22*(1-p)).dp,true),contentAlignment=Alignment.Center){
            if(app.artwork!=null)Image(app.artwork.asImageBitmap(),null,Modifier.size((38+24*p).dp).graphicsLayer{alpha=1f-p*.5f})
        }
    }
}
