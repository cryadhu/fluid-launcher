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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import kotlinx.coroutines.flow.first
import kotlin.math.*

internal const val HOME_PAGE=1
internal const val SPACE_PAGE=2
internal const val PLAN_PAGE=3
internal val pageNames = listOf("Markets", "Home", "Space", "Plan")

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
    val pager = rememberPagerState(initialPage=HOME_PAGE,pageCount = { state.config.pages.size })
    var widgetEdit by remember { mutableStateOf<FluidWidget?>(null) }
    var widgetEditPage by rememberSaveable { mutableStateOf("Home") }
    var pageEdit by remember { mutableStateOf<String?>(null) }
    val pageEdits=remember { mutableStateMapOf<String,PageEdit>() }
    val dragRoute=remember { DragRoute() }
    fun editFor(page:String):PageEdit = pageEdits.getOrPut(page) {
        lateinit var created:PageEdit
        created = PageEdit(page,model,scope,dragRoute,
            onActivated = {
                pageEdits.values.filter { it !== created }.forEach { it.close() }
                pageEdit = page
            },
            onPageDropped = { target ->
                scope.launch {
                    val index = model.state.value.config.pages.indexOf(target)
                    if (index >= 0) {
                        snapshotFlow { pager.pageCount }.first { it > index }
                        pager.animateScrollToPage(index, animationSpec = spring(.9f, 320f))
                    }
                    pageEdits.values.forEach { it.close() }
                    pageEdit = null
                }
            },
            onFinished = {
                pageEdits.values.forEach { it.close() }
                scope.launch { delay(230); if (pageEdits.values.none { it.active }) pageEdit = null }
            },
            onHandoff = { kind, target, at, grab, height ->
                val dest = editFor(target)
                dest.receive(kind, page, at, grab, height)
                scope.launch {
                    val index = model.state.value.config.pages.indexOf(target)
                    if (index >= 0) {
                        snapshotFlow { pager.pageCount }.first { it > index }
                        pager.animateScrollToPage(index, animationSpec = spring(.92f, 420f))
                    }
                    withTimeoutOrNull(700) { snapshotFlow { dest.cards[kind] }.first { it != null } }
                    withFrameNanos { }
                    dest.land(kind)
                }
            })
        created
    }
    val activeEdit = pageEdits.values.firstOrNull { it.active }
    val editing = activeEdit != null
    val editBlur by animateDpAsState(if (editing) 16.dp else 0.dp, spring(1f, 300f), label = "Edit backdrop")
    LaunchedEffect(pager.settledPage) {
        val current = pageEdits.values.firstOrNull { it.active } ?: return@LaunchedEffect
        val settled = model.state.value.config.pages.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        if (settled == current.page) return@LaunchedEffect
        current.close()
        if (pageSupportsWidgets(settled)) { editFor(settled).enter(); pageEdit = settled } else pageEdit = null
    }
    var resizeEditor by remember { mutableStateOf<FluidWidget?>(null) }
    var dockDragging by remember { mutableStateOf(false) }
    var drawer by remember { mutableStateOf(false) }
    var drawerGesture by remember { mutableStateOf<Float?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var panelHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var lastPanel by rememberSaveable { mutableStateOf("Customize") }
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
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshDevice() }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshDevice() }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.startSteps() }
    val locationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshWeather(true) }
    val locateWeather:()->Unit={
        model.markWeatherLocationAsked();model.configure{it.copy(autoWeather=true)}
        if(ContextCompat.checkSelfPermission(view.context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)model.refreshWeather(true)
        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    LaunchedEffect(state.config.autoWeather,FluidWidget.WEATHER in state.config.widgets,state.device.defaultHome) {
        if(state.device.defaultHome && state.config.autoWeather && FluidWidget.WEATHER in state.config.widgets && !model.weatherLocationAsked){
            model.markWeatherLocationAsked()
            if(ContextCompat.checkSelfPermission(view.context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            else model.refreshWeather(true)
        }
    }
    val calendarAccess = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
    val contactsAccess = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }
    val activityAccess = { if(Build.VERSION.SDK_INT >=29) activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) else model.startSteps() }
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
    fun showPanel(name: String) { focus.clearFocus(); keyboard?.hide(); panelHistory=emptyList(); lastPanel=name; panel=name }
    fun panelBack() { if(panelHistory.isNotEmpty()) { val previous=panelHistory.last(); panelHistory=panelHistory.dropLast(1); lastPanel=previous; panel=previous } else panel=null }
    fun navigatePanel(name:String) { if(name!=panel) { panel?.let { panelHistory=panelHistory+it }; lastPanel=name; panel=name } }
    fun openDrawer(focused: Boolean = false) { search=""; searchFocused=focused; drawer=true }
    fun launch(app: InstalledApp, rect: Rect) {
        if(launching!=null) return
        focus.clearFocus(); keyboard?.hide(); launchRect=rect; launching=app
        scope.launch { delay(170); model.launch(app); delay(260); launching=null; drawer=false; search=""; pager.scrollToPage(HOME_PAGE) }
    }
    val finishPageEdit:()->Unit = {
        val open = pageEdits.values.filter { it.active }
        if (open.isNotEmpty()) {
            open.forEach { it.close() }
            scope.launch { delay(230); pageEdit=null }
        }
    }
    var handledHomeEpoch by rememberSaveable { mutableIntStateOf(state.homeEpoch) }
    LaunchedEffect(state.homeEpoch) { if(state.homeEpoch>0 && state.homeEpoch!=handledHomeEpoch) { handledHomeEpoch=state.homeEpoch; drawerGesture=null; widgetEdit=null; pageEdits.values.forEach{it.close()}; pageEdit=null; drawer=false; appMenu=null; panel=null; hero.kind=null; hero.expanded=false; search=""; launching=null; focus.clearFocus(); keyboard?.hide(); pager.animateScrollToPage(HOME_PAGE,animationSpec=spring(.9f,320f)) } }
    LaunchedEffect(state.panelRequest) { state.panelRequest?.let { showPanel(it); model.requestPanel(null) } }
    BackHandler {
        when { pageEdit!=null -> finishPageEdit(); resizeEditor!=null -> resizeEditor=null; widgetEdit!=null -> widgetEdit=null; appMenu!=null -> appMenu=null; hero.kind!=null -> hero.expanded=false; panel!=null -> panelBack(); drawer -> { drawer=false; search=""; focus.clearFocus(); keyboard?.hide() }; else -> scope.launch { pager.animateScrollToPage(HOME_PAGE,animationSpec=spring(.92f,320f)) } }
    }
    val drawerProgress by animateFloatAsState(drawerGesture ?: if(drawer) 1f else 0f,if(drawerGesture!=null) snap() else spring(.9f,360f),label="Drawer spring")
    val panelBlur by animateDpAsState(if(widgetEdit!=null || resizeEditor!=null || panel!=null || appMenu!=null || hero.kind!=null) 20.dp else 0.dp,spring(1f,280f),label="Panel backdrop")
    CompositionLocalProvider(LocalSpaceApps provides SpaceAppsHost(badges,::launch,{ id -> showPanel(if(id==null)"Spaces" else "Spaces:$id") }),LocalSpaceWidgetGallery provides {showPanel("Space widgets")},LocalClockPanel provides { tab -> showPanel("Clock:$tab") },LocalWeatherLocate provides locateWeather,LocalContactsAccess provides contactsAccess,LocalDockDragging provides { dockDragging=it },LocalWidgetEdit provides { kind -> val page=state.config.pages.getOrNull(pager.currentPage) ?: "Home"; widgetEditPage=page; editFor(page).activate(kind,Offset.Zero) }) {
    Box(Modifier.fillMaxSize().testTag("fluid-launcher").onSizeChanged { sceneSize=it }.routePageDrags(pageEdits)) {
        FluidWallpaper(Modifier.blur(editBlur))
        if(editBlur>0.dp) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=editBlur.value/16f*.10f)))
        Box(Modifier.fillMaxSize().blur(panelBlur)) {
            HorizontalPager(pager,beyondViewportPageCount=if(editing) 1 else 0,modifier=Modifier.fillMaxSize().graphicsLayer { scaleX=1f-drawerProgress*.055f; scaleY=scaleX; alpha=1f-drawerProgress*.48f }.blur((drawerProgress*18).dp)
                .then(if(drawerProgress>.01f) Modifier.clearAndSetSemantics {} else Modifier),userScrollEnabled=!dockDragging && !drawer && panel==null && hero.kind==null && pageEdits.values.none{it.dragging!=null}) { index ->
                val distance = ((pager.currentPage-index)+pager.currentPageOffsetFraction).absoluteValue.coerceAtMost(1f)
                val currentPage=state.config.pages[index]
                val pagePress=remember { MutableInteractionSource() }
                val pressed by pagePress.collectIsPressedAsState()
                val highlight by animateFloatAsState(if(pressed)1f else 0f,spring(1f,500f),label="Page press")
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX=1f-distance*.045f; scaleY=scaleX; alpha=1f-distance*.28f; translationX=distance*8f }.combinedClickable(interactionSource=pagePress,indication=null,onClick={},onLongClick={if(pageEdits.values.none{it.active})showPanel("Customize:$currentPage")})) {
                    // The page-level press highlight is deliberately edge-to-edge; the inner
                    // content clickables suppress their own inset ripples.
                    if(highlight>.001f && !editing) Box(Modifier.matchParentSize().graphicsLayer { alpha=highlight }.background(F.ink.copy(alpha=.12f)))
                    val padding = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=22.dp).padding(top=16.dp)
                    val pageState=editFor(currentPage)
                    CompositionLocalProvider(LocalPageEdit provides pageState,LocalPagePress provides pagePress) {
                        when(state.config.pages[index]) {
                            "Home" -> FluidHome(state,model,hero,badges,padding,calendarAccess,activityAccess,{showPanel("Customize:Home")},{openDrawer(true)},{scope.launch { pager.animateScrollToPage(state.config.pages.indexOf("Space").coerceAtLeast(HOME_PAGE),animationSpec=spring(.9f,320f)) }},::launch,pageState)
                            "Space" -> FluidSpace(state,model,hero,padding,calendarAccess,activityAccess,{showPanel("Spaces")},pageState)
                            "Plan" -> FluidPlan(state,model,hero,padding,calendarAccess,activityAccess,pageState)
                            "Markets" -> FluidMarkets(state,model,padding) { showPanel("Market & news settings") }
                            else -> FluidCustomPage(currentPage,state,model,hero,padding,calendarAccess,activityAccess,pageState)
                        }
                    }
                }
            }
            if(drawerProgress<.99f) PageNavigation(pager.currentPage,1f-drawerProgress,state.config.pages,Modifier.blur(editBlur*.6f,BlurredEdgeTreatment.Unbounded)) { scope.launch { pager.animateScrollToPage(it,animationSpec=spring(.9f,320f)) } }
            if(drawerProgress>.001f) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationY=(1f-drawerProgress)*sceneSize.height; alpha=(drawerProgress*2).coerceAtMost(1f) }.statusBarsPadding().padding(top=8.dp).testTag("fluid-drawer-surface")) {
                    FluidDrawer(state,badges,search,{search=it},tab,{tab=it},searchFocused,drawerProgress>.98f,appMenu==null && panel==null,::launch,
                        options={ app -> focus.clearFocus(force=true); keyboard?.hide(); searchFocused=false; appMenu=app },close={drawer=false;focus.clearFocus();keyboard?.hide()},settings={showPanel("Settings")},pull={value -> drawerGesture=(1f-value/sceneSize.height.coerceAtLeast(1)).coerceIn(0f,1f)},finishPull={value -> drawer=value<sceneSize.height*.18f;drawerGesture=null})
                }
            }
        }
        FluidHeroOverlay(hero,state,model,calendarAccess,activityAccess)
        activeEdit?.let { PageEditOverlay(it) }
        DragGhost(dragRoute,pageEdits)
        activeEdit?.let { RemoveTarget(it) }
        AnimatedVisibility(panel!=null,enter=fadeIn(spring(stiffness=350f))+slideInVertically(spring(.9f,350f)){it/6},exit=fadeOut(tween(180))+slideOutVertically(tween(240)){it/5}) {
            AnimatedContent(targetState=panel ?: lastPanel,transitionSpec={
                (fadeIn(tween(210))+slideInHorizontally(spring(.95f,350f)){it/10}) togetherWith (fadeOut(tween(130))+slideOutHorizontally(tween(190)){-it/16})
            },label="Glass panel navigation") { name ->
                val spaceStart=if(name.startsWith("Spaces:")) name.removePrefix("Spaces:").toLongOrNull() else null
                val clockStart=if(name.startsWith("Clock:")) name.removePrefix("Clock:") else null
                FluidPanel(if(name.startsWith("Spaces:"))"Spaces" else if(clockStart!=null)"Clock" else name,state,model,chooseHome,{panel=null},::navigatePanel,calendarAccess,activityAccess,::panelBack,spaceStart,clockStart)
            }
        }
        AnimatedVisibility(appMenu!=null,enter=fadeIn(tween(110))+scaleIn(spring(.75f,450f),initialScale=.9f),exit=fadeOut(tween(100))+scaleOut(tween(130),targetScale=.96f)) {
            appMenu?.let { app -> FluidAppOptions(app,state,model,{appMenu=null}) }
        }
        widgetEdit?.let { selected -> WidgetEditSheet(selected,page=widgetEditPage,state,model,arrange={ editFor(widgetEditPage).enter(); pageEdit=widgetEditPage },resize={resizeEditor=selected}) { widgetEdit=null } }
        resizeEditor?.let { selected -> WidgetResizeEditor(selected,state,model,page=widgetEditPage){resizeEditor=null} }
        launching?.let { AppLaunchBloom(it,launchRect,sceneSize) }
        AnimatedVisibility(state.message!=null,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp,vertical=68.dp),enter=fadeIn()+slideInVertically{it/2},exit=fadeOut()) {
            Text(state.message.orEmpty(),style=F.text(13),modifier=Modifier.glass(24.dp,true).padding(horizontal=20.dp,vertical=14.dp))
        }
    }
}
}

@Composable private fun BoxScope.PageNavigation(page: Int, opacity: Float, names:List<String>, modifier: Modifier = Modifier, select: (Int)->Unit) {
    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=3.dp).then(modifier).graphicsLayer { alpha=opacity }, horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        names.forEachIndexed { index,name ->
            val width by animateDpAsState(if(page==index) 15.dp else 5.dp,spring(.82f,400f),label="Page dot")
            Box(Modifier.width(28.dp).height(26.dp).clickable(role=Role.Tab){select(index)}.semantics { contentDescription=name; selected=page==index },contentAlignment=Alignment.Center) {
                if(name=="Home") Glyph("home",if(page==index) F.purple else F.muted.copy(alpha=.35f),Modifier.size(12.dp))
                else Box(Modifier.width(width).height(5.dp).clip(CircleShape).background(if(page==index) F.purple else F.muted.copy(alpha=.35f)))
            }
        }
    }
}

internal fun fluidDock(apps:List<InstalledApp>,keys:List<String>):List<InstalledApp> {
    if(keys != listOf("@auto")) return keys.mapNotNull { key -> apps.firstOrNull { it.component.flattenToString()==key } }.take(6)
    val groups = listOf(listOf("truecaller","dialer","contacts"),listOf("messaging","messages","mms"),listOf("chrome","browser"),listOf("camera"))
    val result=mutableListOf<InstalledApp>()
    groups.forEach { group -> group.firstNotNullOfOrNull { part -> apps.firstOrNull { it.component.packageName.contains(part,true) && it !in result } }?.let { result+=it } }
    return (result+apps.filterNot { it in result }).take(6)
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
    val pageEditor=LocalPageEdit.current
    val interaction=remember { MutableInteractionSource() }
    var source by remember { mutableStateOf(Rect.Zero) }
    val fixed=widgetFixedHeight(kind)?.let { if(kind==FluidWidget.CLOCK && widgetSize(kind,state.config)=="Large") 240.dp else it }
    val opensDetail=kind==FluidWidget.WEATHER || fixed==null && kind!=FluidWidget.TASKS && kind!=FluidWidget.AGENDA && kind!=FluidWidget.SPACE_APPS
    val surface=if(kind==FluidWidget.MUSIC_OPEN || kind==FluidWidget.CLOCK) Modifier else Modifier.glass(30.dp,true)
    val openClock=LocalClockPanel.current
    val click={ when {
        kind==FluidWidget.AGENDA -> model.open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
        kind==FluidWidget.CLOCK -> openClock("Alarm")
        opensDetail -> hero.open(kind,source)
    } }
    val size=if(fixed!=null) Modifier.height(fixed).clip(RoundedCornerShape(30.dp)) else Modifier.animateContentSize(spring(1f,350f)).heightIn(min=widgetMinimum(kind,state.config)).padding(if(state.config.sizes[kind]=="Compact")12.dp else 20.dp)
    Column(modifier.fillMaxWidth().onGloballyPositioned { source=Rect(it.positionInRoot(),it.size.toSize());hero.bounds[kind]=source }.graphicsLayer { alpha=if(hero.kind==kind) 0f else 1f }
        .softPress(interaction).then(surface).combinedClickable(interactionSource=interaction,indication=null,onClick=click,onLongClick={if(pageEditor==null)edit(kind)}).then(size).testTag("fluid-widget-${kind.name}")) {
        WidgetBody(kind,state,model,calendarAccess=calendar,activityAccess=activity,compact=state.config.sizes[kind]=="Compact")
    }
}

@Composable private fun FluidSpace(state:FluidState,model:FluidModel,hero:FluidHero,modifier:Modifier,calendar:()->Unit,activity:()->Unit,manage:()->Unit,edit:PageEdit?=null) {
    val widgets=state.config.spaceWidgets.filterNot{it in tinyControls}
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val phase=rememberInfiniteTransition(label="Widget wobble").animateFloat(0f,6.2831855f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="Wobble")
    SideEffect { edit?.bind(widgets,with(density){16.dp.toPx()},scroll) }
    Box(modifier.navigationBarsPadding().onGloballyPositioned{edit?.viewport=it.boundsInRoot()}.testTag("fluid-space")) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FluidHeading("Your space","Apps and widgets, your way.") { if(edit?.active!=true) GlassIcon("edit","Organize spaces",onClick=manage) }
            WidgetGrid(widgets,state.config,edit,16.dp,phase) { kind -> ExpandableWidget(kind,state,model,hero,calendar,activity) }
            ControlWidgetRows(state.config.spaceWidgets.filter{it in tinyControls},state,model)
            val openGallery=LocalSpaceWidgetGallery.current
            if(edit?.active!=true) GlassButton("Add widgets",Modifier.fillMaxWidth(),glyph="plus",onClick=openGallery)
        }
    }
}

@Immutable internal class SpaceAppsHost(val badges:Set<String>,val launch:(InstalledApp,Rect)->Unit,val manage:(Long?)->Unit)
internal val LocalSpaceApps = compositionLocalOf { SpaceAppsHost(emptySet(),{_,_->},{}) }

@Composable internal fun FluidCustomPage(page:String,state:FluidState,model:FluidModel,hero:FluidHero,modifier:Modifier,calendar:()->Unit,activity:()->Unit,edit:PageEdit?=null) {
    val widgets=model.widgetsForPage(page)
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val phase=rememberInfiniteTransition(label="Widget wobble").animateFloat(0f,6.2831855f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="Wobble")
    SideEffect { edit?.bind(widgets,with(density){14.dp.toPx()},scroll) }
    Box(modifier.navigationBarsPadding().onGloballyPositioned{edit?.viewport=it.boundsInRoot()}) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            FluidHeading(page,"A fresh page to make your own.")
            if(widgets.isEmpty()) Text("Long press empty space to add widgets.",style=F.text(13,muted=true))
            WidgetGrid(widgets,state.config,edit,14.dp,phase) { kind -> ExpandableWidget(kind,state,model,hero,calendar,activity) }
        }
    }
}
