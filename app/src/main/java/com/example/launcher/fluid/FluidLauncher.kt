@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.launcher.data.*
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.*
import kotlin.math.*

private val pageNames = listOf("Home", "Space", "Notifications", "Controls")

@Stable class FluidHero {
    var kind by mutableStateOf<FluidWidget?>(null)
    var origin by mutableStateOf(Rect.Zero)
    var expanded by mutableStateOf(false)
    val bounds = mutableMapOf<FluidWidget,Rect>()
    fun open(widget: FluidWidget, source: Rect? = null) {
        if (kind != null) return
        val rect = source ?: bounds[widget] ?: return
        origin = rect; kind = widget; expanded = true
    }
}

@Composable fun FluidLauncher(model: FluidModel, chooseHome: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val notices by FluidNotifications.items.collectAsStateWithLifecycle()
    val badges by NotificationBadges.packages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 4 })
    var widgetEdit by remember { mutableStateOf<FluidWidget?>(null) }
    var drawer by remember { mutableStateOf(false) }
    var drawerGesture by remember { mutableStateOf<Float?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var panel by remember { mutableStateOf<String?>(null) }
    var lastPanel by remember { mutableStateOf("Customize") }
    var appMenu by remember { mutableStateOf<InstalledApp?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("All") }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    val hero = remember { FluidHero() }
    var launching by remember { mutableStateOf<InstalledApp?>(null) }
    var launchRect by remember { mutableStateOf(Rect.Zero) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted) model.toggle("torch") else model.message("Allow camera access to use the flashlight") }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshDevice() }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.startSteps() }
    val calendarAccess = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
    val activityAccess = { if(Build.VERSION.SDK_INT >=29) activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) else model.startSteps() }
    val torch = { if(ContextCompat.checkSelfPermission(view.context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) model.toggle("torch") else cameraPermission.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(appMenu, panel) {
        if(appMenu!=null || panel!=null) {
            focus.clearFocus(force=true); keyboard?.hide()
            withFrameNanos { }
            focus.clearFocus(force=true); keyboard?.hide()
            (view.context as? android.app.Activity)?.let {
                androidx.core.view.WindowCompat.getInsetsController(it.window,view).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
        }
    }
    fun showPanel(name: String) { focus.clearFocus(); keyboard?.hide(); lastPanel=name; panel=name }
    fun openDrawer(focused: Boolean = false) { search=""; searchFocused=focused; drawer=true }
    fun launch(app: InstalledApp, rect: Rect) {
        if(launching!=null) return
        focus.clearFocus(); keyboard?.hide(); launchRect=rect; launching=app
        scope.launch { delay(170); model.launch(app); delay(260); launching=null; drawer=false; search=""; pager.scrollToPage(0) }
    }
    LaunchedEffect(state.homeEpoch) { if(state.homeEpoch>0) { drawerGesture=null; widgetEdit=null; drawer=false; appMenu=null; panel=null; hero.kind=null; hero.expanded=false; search=""; launching=null; focus.clearFocus(); keyboard?.hide(); pager.scrollToPage(0) } }
    BackHandler {
        when { widgetEdit!=null -> widgetEdit=null; appMenu!=null -> appMenu=null; hero.kind!=null -> hero.expanded=false; panel!=null -> panel=null; drawer -> { drawer=false; search=""; focus.clearFocus(); keyboard?.hide() }; else -> scope.launch { pager.animateScrollToPage(0,animationSpec=spring(.92f,320f)) } }
    }
    val drawerProgress by animateFloatAsState(drawerGesture ?: if(drawer) 1f else 0f,if(drawerGesture!=null) snap() else spring(.9f,360f),label="Drawer spring")
    val panelBlur by animateDpAsState(if(widgetEdit!=null || panel!=null || appMenu!=null || hero.kind!=null) 20.dp else 0.dp,spring(1f,280f),label="Panel backdrop")
    var swipe by remember { mutableFloatStateOf(0f) }
    CompositionLocalProvider(LocalWidgetEdit provides { widgetEdit=it },LocalNotificationsExpand provides { panel=null;scope.launch{pager.animateScrollToPage(2,animationSpec=spring(.9f,320f))} }) {
    Box(Modifier.fillMaxSize().testTag("fluid-launcher").onSizeChanged { sceneSize=it }) {
        FluidWallpaper()
        Box(Modifier.fillMaxSize().blur(panelBlur)) {
            HorizontalPager(pager,Modifier.fillMaxSize().graphicsLayer { scaleX=1f-drawerProgress*.055f; scaleY=scaleX; alpha=1f-drawerProgress*.48f }.blur((drawerProgress*18).dp)
                .then(if(drawerProgress>.01f) Modifier.clearAndSetSemantics {} else Modifier),userScrollEnabled=!drawer && panel==null && hero.kind==null) { index ->
                val distance = ((pager.currentPage-index)+pager.currentPageOffsetFraction).absoluteValue.coerceAtMost(1f)
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX=1f-distance*.045f; scaleY=scaleX; alpha=1f-distance*.28f; translationX=distance*8f }
                    .pointerInput(index) {
                        detectVerticalDragGestures(onDragStart={swipe=0f},onVerticalDrag={change,amount ->
                            swipe+=amount
                            if(swipe < -18.dp.toPx()) { change.consume(); drawerGesture=(-swipe/sceneSize.height.coerceAtLeast(1)).coerceIn(0f,1f) }
                        },onDragEnd={if(drawerGesture!=null){drawer=drawerGesture!!>.12f;drawerGesture=null;searchFocused=false;search=""};swipe=0f},onDragCancel={drawerGesture=null})
                    }) {
                    val padding = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=22.dp).padding(top=16.dp)
                    when(index) {
                        0 -> FluidHome(state,model,hero,badges,padding,calendarAccess,activityAccess,{showPanel("Customize")},{openDrawer(true)},{scope.launch { pager.animateScrollToPage(1,animationSpec=spring(.9f,320f)) }},{openDrawer(false)},::launch)
                        1 -> FluidSpace(state,model,hero,badges,padding,calendarAccess,activityAccess,{showPanel("Spaces")},::launch)
                        2 -> FluidInbox(state,model,notices,padding)
                        3 -> FluidControls(state,model,padding,torch,{showPanel("Settings")})
                    }
                }
            }
            if(drawerProgress<.99f) PageNavigation(pager.currentPage,1f-drawerProgress) { scope.launch { pager.animateScrollToPage(it,animationSpec=spring(.9f,320f)) } }
            if(drawerProgress>.001f) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationY=(1f-drawerProgress)*sceneSize.height; alpha=(drawerProgress*2).coerceAtMost(1f) }.statusBarsPadding().padding(top=8.dp).testTag("fluid-drawer-surface")) {
                    FluidDrawer(state,badges,search,{search=it},tab,{tab=it},searchFocused,drawerProgress>.98f,appMenu==null && panel==null,::launch,
                        options={ app -> focus.clearFocus(force=true); keyboard?.hide(); searchFocused=false; appMenu=app },close={drawer=false;focus.clearFocus();keyboard?.hide()},settings={showPanel("Settings")},pull={value -> drawerGesture=(1f-value/sceneSize.height.coerceAtLeast(1)).coerceIn(0f,1f)},finishPull={value -> drawer=value<sceneSize.height*.18f;drawerGesture=null})
                }
            }
        }
        FluidHeroOverlay(hero,state,model,calendarAccess,activityAccess)
        AnimatedVisibility(panel!=null,enter=fadeIn(spring(stiffness=350f))+slideInVertically(spring(.9f,350f)){it/6},exit=fadeOut(tween(180))+slideOutVertically(tween(240)){it/5}) {
            AnimatedContent(targetState=panel ?: lastPanel,transitionSpec={
                (fadeIn(tween(210))+slideInHorizontally(spring(.95f,350f)){it/10}) togetherWith (fadeOut(tween(130))+slideOutHorizontally(tween(190)){-it/16})
            },label="Glass panel navigation") { name ->
                FluidPanel(name,state,model,chooseHome,{panel=null},{lastPanel=it;panel=it},calendarAccess,activityAccess)
            }
        }
        AnimatedVisibility(appMenu!=null,enter=fadeIn(tween(110))+scaleIn(spring(.75f,450f),initialScale=.9f),exit=fadeOut(tween(100))+scaleOut(tween(130),targetScale=.96f)) {
            appMenu?.let { app -> FluidAppOptions(app,state,model,{appMenu=null}) }
        }
        widgetEdit?.let { WidgetEditSheet(it,state,model) { widgetEdit=null } }
        launching?.let { AppLaunchBloom(it,launchRect,sceneSize) }
        AnimatedVisibility(state.message!=null,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp,vertical=68.dp),enter=fadeIn()+slideInVertically{it/2},exit=fadeOut()) {
            Text(state.message.orEmpty(),style=F.text(13),modifier=Modifier.glass(24.dp,true).padding(horizontal=20.dp,vertical=14.dp))
        }
    }
}
}

@Composable private fun BoxScope.PageNavigation(page: Int, opacity: Float, select: (Int)->Unit) {
    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=3.dp).graphicsLayer { alpha=opacity }, horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        pageNames.forEachIndexed { index,name ->
            val width by animateDpAsState(if(page==index) 15.dp else 5.dp,spring(.82f,400f),label="Page dot")
            Box(Modifier.width(28.dp).height(26.dp).clickable(role=Role.Tab){select(index)}.semantics { contentDescription=name; selected=page==index },contentAlignment=Alignment.Center) {
                Box(Modifier.width(width).height(5.dp).clip(CircleShape).background(if(page==index) F.purple else F.muted.copy(alpha=.35f)))
            }
        }
    }
}

internal fun fluidDock(apps:List<InstalledApp>,keys:List<String>):List<InstalledApp> {
    if(keys != listOf("@auto")) return keys.mapNotNull { key -> apps.firstOrNull { it.component.flattenToString()==key } }.take(4)
    val groups = listOf(listOf("truecaller","dialer","contacts"),listOf("messaging","messages","mms"),listOf("chrome","browser"),listOf("camera"))
    val result=mutableListOf<InstalledApp>()
    groups.forEach { group -> group.firstNotNullOfOrNull { part -> apps.firstOrNull { it.component.packageName.contains(part,true) && it !in result } }?.let { result+=it } }
    return (result+apps.filterNot { it in result }).take(4)
}

@Composable internal fun FluidAppTile(app:InstalledApp,badges:Set<String>,labels:Boolean,launch:(InstalledApp,Rect)->Unit,options:(InstalledApp)->Unit,modifier:Modifier=Modifier) {
    var rect by remember(app.component) { mutableStateOf(Rect.Zero) }
    val interaction=remember { MutableInteractionSource() }
    Column(modifier.onGloballyPositioned { rect=Rect(it.positionInRoot(),it.size.toSize()) }.softPress(interaction).combinedClickable(interactionSource=interaction,indication=null,onClick={launch(app,rect)},onLongClick={options(app)},role=Role.Button)
        .semantics(mergeDescendants=true) { contentDescription=app.label; if(app.component.packageName in badges) stateDescription="New notifications" }.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.fillMaxSize().glass(18.dp),contentAlignment=Alignment.Center) {
                if(app.artwork!=null) Image(app.artwork.asImageBitmap(),null,Modifier.size(35.dp)) else Glyph(app.icon.glyph,F.purple,Modifier.size(28.dp))
            }
            if(app.component.packageName in badges) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(7.dp).background(F.purple,CircleShape).border(1.dp,Color.White,CircleShape))
        }
        if(labels) Text(app.label,style=F.text(10),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=7.dp))
    }
}

@Composable internal fun ExpandableWidget(kind:FluidWidget,state:FluidState,model:FluidModel,hero:FluidHero,calendar:()->Unit,activity:()->Unit,modifier:Modifier=Modifier) {
    val edit=LocalWidgetEdit.current
    val interaction=remember { MutableInteractionSource() }
    var source by remember { mutableStateOf(Rect.Zero) }
    Column(modifier.fillMaxWidth().onGloballyPositioned { source=Rect(it.positionInRoot(),it.size.toSize());hero.bounds[kind]=source }.graphicsLayer { alpha=if(hero.kind==kind) 0f else 1f }
        .softPress(interaction).glass(30.dp,true).combinedClickable(interactionSource=interaction,indication=null,onClick={hero.open(kind,source)},onLongClick={edit(kind)}).animateContentSize(spring(1f,350f)).heightIn(min=widgetMinimum(kind,state.config)).padding(if(state.config.sizes[kind]=="Compact")12.dp else 20.dp).testTag("fluid-widget-${kind.name}")) {
        WidgetBody(kind,state,model,calendarAccess=calendar,activityAccess=activity,compact=state.config.sizes[kind]=="Compact")
    }
}

@Composable private fun FluidSpace(state:FluidState,model:FluidModel,hero:FluidHero,badges:Set<String>,modifier:Modifier,calendar:()->Unit,activity:()->Unit,manage:()->Unit,launch:(InstalledApp,Rect)->Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom=100.dp).testTag("fluid-space"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FluidHeading("Your space","A little structure. More flow.") { GlassIcon("edit","Organize spaces",onClick=manage) }
        GlassButton("Work",glyph="files",onClick=manage)
        ExpandableWidget(FluidWidget.AGENDA,state,model,hero,calendar,activity)
        val apps=state.apps.filter { it.component.flattenToString() in state.config.work }
        if(apps.isEmpty()) Column(Modifier.fillMaxWidth().glass(28.dp).padding(20.dp)) {
            Text("Room for your work",style=F.text(17,true)); Text("Bring your favorite work apps together.",style=F.text(13,muted=true),modifier=Modifier.padding(top=6.dp)); GlassButton("Choose apps",Modifier.padding(top=14.dp),glyph="plus",onClick=manage)
        } else Column(Modifier.fillMaxWidth().glass(28.dp).padding(10.dp)) {
            apps.chunked(4).forEach { group -> Row(Modifier.fillMaxWidth()) { group.forEach { app -> FluidAppTile(app,badges,state.config.labels,launch,{manage()},Modifier.weight(1f)) }; repeat(4-group.size){Spacer(Modifier.weight(1f))} } }
        }
        GlassButton("Create a task",Modifier.fillMaxWidth(),glyph="plus") { hero.open(FluidWidget.TASKS) }
        ExpandableWidget(FluidWidget.TASKS,state,model,hero,calendar,activity)
    }
}

@Composable private fun FluidInbox(state:FluidState,model:FluidModel,notices:List<FluidNotification>,modifier:Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom=100.dp).testTag("fluid-notifications"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FluidHeading("Today",fluidDate(state.now,"EEE, MMM d")) { GlassIcon("silent","Notification access",onClick=model::accessNotifications) }
        NotificationStack(state,model,detail=true)
    }
}

@Composable private fun FluidControls(state:FluidState,model:FluidModel,modifier:Modifier,torch:()->Unit,settings:()->Unit) {
    val context=LocalContext.current
    var brightness by remember { mutableFloatStateOf(runCatching { Settings.System.getInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS)/255f }.getOrDefault(.5f)) }
    val activity=context as? android.app.Activity
    fun applyBrightness(value:Float) { brightness=value; activity?.window?.let { w -> w.attributes=w.attributes.apply { screenBrightness=value.coerceIn(.02f,1f) } } }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom=100.dp).testTag("fluid-controls"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FluidHeading(fluidDate(state.now,"HH:mm"),fluidDate(state.now,"EEE, MMM d")) { GlassIcon("settings","Launcher settings",onClick=settings) }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ControlTile("Wi-Fi",state.device.wifiName ?: if(state.device.wifi) "On" else "Off","wifi",state.device.wifi,Modifier.weight(1f)) { model.toggle("wifi") }
            ControlTile("Bluetooth",when(state.device.bluetooth){true->"On";false->"Off";null->"Manage"},"bluetooth",state.device.bluetooth==true,Modifier.weight(1f)) { model.toggle("bluetooth") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ControlTile("Mobile data",when(state.device.mobileData){true->"On";false->"Off";null->"Manage"},"mobile",state.device.mobileData==true,Modifier.weight(1f)) { model.toggle("mobile") }
            ControlTile("Sound",state.device.soundMode.label,if(state.device.soundMode==SoundMode.VIBRATE) "vibrate" else "silent",state.device.soundMode!=SoundMode.NORMAL,Modifier.weight(1f)) { model.toggle("silent") }
        }
        Row(Modifier.fillMaxWidth().glass(25.dp).padding(horizontal=18.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Glyph("weather",F.ink,Modifier.size(23.dp)); Slider(brightness,::applyBrightness,Modifier.weight(1f).padding(start=12.dp).semantics { contentDescription="Launcher brightness" },colors=SliderDefaults.colors(thumbColor=F.purple,activeTrackColor=F.purple,inactiveTrackColor=F.lilac.copy(alpha=.25f)))
        }
        Text("Brightness while using this launcher",style=F.text(10,muted=true),modifier=Modifier.padding(horizontal=10.dp))
        Row(Modifier.fillMaxWidth().glass(28.dp).padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).glass(15.dp),contentAlignment=Alignment.Center){Glyph("music",F.purple)}
            Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(state.device.mediaTitle ?: "A little rhythm",style=F.text(13,true),maxLines=2,overflow=TextOverflow.Ellipsis);Text(if(state.device.playing)"Now playing" else "Music controls",style=F.text(11,muted=true))}
            GlassIcon(if(state.device.playing)"pause" else "play","Play or pause",Modifier.size(36.dp)){model.media("play")};GlassIcon("next","Next track",Modifier.size(36.dp)){model.media("next")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            GlassIcon("flashlight","Flashlight",primary=state.device.torch,onClick=torch)
            GlassIcon("airplane","Airplane mode",primary=state.device.airplaneMode){model.toggle("airplane")}
            GlassIcon("clock","Alarms"){model.open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))}
            GlassIcon("video","Cast screen"){model.toggle("cast")}
        }
        GlassButton("Phone settings",Modifier.align(Alignment.CenterHorizontally).padding(top=10.dp),glyph="settings"){model.open(Intent(Settings.ACTION_SETTINGS))}
    }
}

@Composable private fun ControlTile(title:String,subtitle:String,glyph:String,on:Boolean,modifier:Modifier,click:()->Unit) {
    val interaction=remember{MutableInteractionSource()}
    val active by animateFloatAsState(if(on)1f else 0f,spring(.85f,350f),label="Radio tile")
    Column(modifier.softPress(interaction).glass(28.dp).background(Brush.linearGradient(listOf(F.lilac.copy(alpha=active*.88f),F.purple.copy(alpha=active*.94f))))
        .clickable(interaction,indication=null,role=Role.Button,onClick=click).padding(17.dp).heightIn(min=101.dp)) {
        Box(Modifier.size(33.dp).clip(CircleShape).background(Color.White.copy(alpha=.28f)),contentAlignment=Alignment.Center){Glyph(glyph,if(on)Color.White else F.purple,Modifier.size(19.dp))}
        Spacer(Modifier.height(14.dp));Text(title,style=F.text(12,true),color=if(on)Color.White else F.ink);Text(subtitle,style=F.text(10),color=if(on)Color.White.copy(alpha=.85f) else F.muted,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
