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
    fun editFor(page:String)=pageEdits.getOrPut(page) { PageEdit(page,model,scope){pageEdit=page} }
    val edit=pageEdit?.let { pageEdits[it] }
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
        val current=pageEdit
        if(current!=null) {
            edit?.close()
            scope.launch { delay(230); if(pageEdit==current) pageEdit=null }
        }
    }
    var handledHomeEpoch by rememberSaveable { mutableIntStateOf(state.homeEpoch) }
    LaunchedEffect(state.homeEpoch) { if(state.homeEpoch>0 && state.homeEpoch!=handledHomeEpoch) { handledHomeEpoch=state.homeEpoch; drawerGesture=null; widgetEdit=null; edit?.close(); pageEdit=null; drawer=false; appMenu=null; panel=null; hero.kind=null; hero.expanded=false; search=""; launching=null; focus.clearFocus(); keyboard?.hide(); pager.animateScrollToPage(HOME_PAGE,animationSpec=spring(.9f,320f)) } }
    BackHandler {
        when { pageEdit!=null -> finishPageEdit(); resizeEditor!=null -> resizeEditor=null; widgetEdit!=null -> widgetEdit=null; appMenu!=null -> appMenu=null; hero.kind!=null -> hero.expanded=false; panel!=null -> panelBack(); drawer -> { drawer=false; search=""; focus.clearFocus(); keyboard?.hide() }; else -> scope.launch { pager.animateScrollToPage(HOME_PAGE,animationSpec=spring(.92f,320f)) } }
    }
    val drawerProgress by animateFloatAsState(drawerGesture ?: if(drawer) 1f else 0f,if(drawerGesture!=null) snap() else spring(.9f,360f),label="Drawer spring")
    val panelBlur by animateDpAsState(if(widgetEdit!=null || resizeEditor!=null || panel!=null || appMenu!=null || hero.kind!=null) 20.dp else 0.dp,spring(1f,280f),label="Panel backdrop")
    CompositionLocalProvider(LocalSpaceWidgetGallery provides {showPanel("Space widgets")},LocalWeatherLocate provides locateWeather,LocalContactsAccess provides contactsAccess,LocalDockDragging provides { dockDragging=it },LocalWidgetEdit provides { kind -> val page=state.config.pages.getOrNull(pager.currentPage) ?: "Home"; widgetEditPage=page; editFor(page).activate(kind,Offset.Zero) }) {
    Box(Modifier.fillMaxSize().testTag("fluid-launcher").onSizeChanged { sceneSize=it }) {
        FluidWallpaper()
        Box(Modifier.fillMaxSize().blur(panelBlur)) {
            HorizontalPager(pager,Modifier.fillMaxSize().graphicsLayer { scaleX=1f-drawerProgress*.055f; scaleY=scaleX; alpha=1f-drawerProgress*.48f }.blur((drawerProgress*18).dp)
                .then(if(drawerProgress>.01f) Modifier.clearAndSetSemantics {} else Modifier),userScrollEnabled=!dockDragging && !drawer && panel==null && hero.kind==null && pageEdit==null) { index ->
                val distance = ((pager.currentPage-index)+pager.currentPageOffsetFraction).absoluteValue.coerceAtMost(1f)
                val currentPage=state.config.pages[index]
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX=1f-distance*.045f; scaleY=scaleX; alpha=1f-distance*.28f; translationX=distance*8f }.combinedClickable(onClick={},onLongClick={if(pageEdits.values.none{it.active})showPanel("Customize:$currentPage")})) {
                    val padding = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=22.dp).padding(top=16.dp)
                    val pageState=editFor(currentPage)
                    CompositionLocalProvider(LocalPageEdit provides pageState) {
                        when(state.config.pages[index]) {
                            "Home" -> FluidHome(state,model,hero,badges,padding,calendarAccess,activityAccess,{showPanel("Customize:Home")},{openDrawer(true)},{scope.launch { pager.animateScrollToPage(state.config.pages.indexOf("Space").coerceAtLeast(HOME_PAGE),animationSpec=spring(.9f,320f)) }},::launch,pageState,finishPageEdit)
                            "Space" -> FluidSpace(state,model,hero,badges,padding,calendarAccess,activityAccess,{showPanel("Spaces")},::launch,pageState,finishPageEdit)
                            "Plan" -> FluidPlan(state,model,padding,calendarAccess,pageState,finishPageEdit)
                            "Markets" -> FluidMarkets(state,model,padding) { showPanel("Market & news settings") }
                            else -> FluidCustomPage(currentPage,state,model,hero,padding,calendarAccess,activityAccess,pageState,finishPageEdit)
                        }
                    }
                }
            }
            if(drawerProgress<.99f) PageNavigation(pager.currentPage,1f-drawerProgress,state.config.pages) { scope.launch { pager.animateScrollToPage(it,animationSpec=spring(.9f,320f)) } }
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
                FluidPanel(name,state,model,chooseHome,{panel=null},::navigatePanel,calendarAccess,activityAccess,::panelBack)
            }
        }
        AnimatedVisibility(appMenu!=null,enter=fadeIn(tween(110))+scaleIn(spring(.75f,450f),initialScale=.9f),exit=fadeOut(tween(100))+scaleOut(tween(130),targetScale=.96f)) {
            appMenu?.let { app -> FluidAppOptions(app,state,model,{appMenu=null}) }
        }
        widgetEdit?.let { selected -> WidgetEditSheet(selected,page=widgetEditPage,state,model,arrange={pageEdit=widgetEditPage},resize={resizeEditor=selected}) { widgetEdit=null } }
        resizeEditor?.let { selected -> WidgetResizeEditor(selected,state,model,page=widgetEditPage){resizeEditor=null} }
        launching?.let { AppLaunchBloom(it,launchRect,sceneSize) }
        AnimatedVisibility(state.message!=null,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp,vertical=68.dp),enter=fadeIn()+slideInVertically{it/2},exit=fadeOut()) {
            Text(state.message.orEmpty(),style=F.text(13),modifier=Modifier.glass(24.dp,true).padding(horizontal=20.dp,vertical=14.dp))
        }
    }
}
}

@Composable private fun BoxScope.PageNavigation(page: Int, opacity: Float, names:List<String>, select: (Int)->Unit) {
    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=3.dp).graphicsLayer { alpha=opacity }, horizontalArrangement=Arrangement.spacedBy(2.dp)) {
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
    val opensDetail=kind!=FluidWidget.TASKS && kind!=FluidWidget.AGENDA
    val surface=if(kind==FluidWidget.MUSIC_OPEN) Modifier else Modifier.glass(30.dp,true)
    Column(modifier.fillMaxWidth().onGloballyPositioned { source=Rect(it.positionInRoot(),it.size.toSize());hero.bounds[kind]=source }.graphicsLayer { alpha=if(hero.kind==kind) 0f else 1f }
        .softPress(interaction).then(surface).combinedClickable(interactionSource=interaction,indication=null,onClick={if(kind==FluidWidget.AGENDA)model.open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))else if(opensDetail)hero.open(kind,source)},onLongClick={if(pageEditor==null)edit(kind)}).animateContentSize(spring(1f,350f)).heightIn(min=widgetMinimum(kind,state.config)).padding(if(state.config.sizes[kind]=="Compact")12.dp else 20.dp).testTag("fluid-widget-${kind.name}")) {
        WidgetBody(kind,state,model,calendarAccess=calendar,activityAccess=activity,compact=state.config.sizes[kind]=="Compact")
    }
}

@Composable private fun FluidSpace(state:FluidState,model:FluidModel,hero:FluidHero,badges:Set<String>,modifier:Modifier,calendar:()->Unit,activity:()->Unit,manage:()->Unit,launch:(InstalledApp,Rect)->Unit,edit:PageEdit?=null,finishEdit:()->Unit={}) {
    var selected by rememberSaveable { mutableStateOf("Work") }
    val tabs=listOf("Work","Personal","Tools")
    val apps=fluidFilteredApps(state.apps,state.config,"",selected)
    val widgets=state.config.spaceWidgets.filterNot{it in tinyControls}
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val phase=rememberInfiniteTransition(label="Widget wobble").animateFloat(0f,6.2831855f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="Wobble")
    SideEffect { edit?.bind(widgets,with(density){16.dp.toPx()},scroll) }
    Box(modifier.navigationBarsPadding().onGloballyPositioned{edit?.viewport=it.boundsInRoot()}.testTag("fluid-space")) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FluidHeading("Your space","Apps and widgets, your way.") { if(edit?.active!=true) GlassIcon("edit","Organize spaces",onClick=manage) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                tabs.forEach { title ->
                    Box(Modifier.weight(1f).heightIn(min=46.dp).glass(24.dp).background(if(selected==title)F.purple.copy(alpha=.16f)else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role=Role.Tab){selected=title}.semantics{this.selected=title==selected}.padding(vertical=12.dp),contentAlignment=Alignment.Center) { Text(title,style=F.text(12,true),maxLines=1) }
                }
            }
            if(apps.isEmpty()) Column(Modifier.fillMaxWidth().glass(28.dp).padding(20.dp)) {
                Text("Your ${selected.lowercase()} apps",style=F.text(17,true));Text("Choose the apps that belong here.",style=F.text(13,muted=true),modifier=Modifier.padding(top=6.dp));GlassButton("Choose apps",Modifier.padding(top=14.dp),glyph="plus",onClick=manage)
            } else Column(Modifier.fillMaxWidth().glass(28.dp).padding(10.dp).testTag("space-apps-$selected")) {
                apps.chunked(4).forEach { group -> Row(Modifier.fillMaxWidth()) { group.forEach { app -> FluidAppTile(app,badges,state.config.labels,launch,{manage()},Modifier.weight(1f)) };repeat(4-group.size){Spacer(Modifier.weight(1f))} } }
            }
            widgets.forEachIndexed { index,kind -> key(kind) {
                Box(Modifier.pageEditCard(kind,edit,phase,index)) {
                    ExpandableWidget(kind,state,model,hero,calendar,activity)
                    if(edit!=null && edit.active) Box(Modifier.matchParentSize().pageEditDrag(kind,edit))
                }
            } }
            ControlWidgetRows(state.config.spaceWidgets.filter{it in tinyControls},state,model)
            val openGallery=LocalSpaceWidgetGallery.current
            if(edit?.active!=true) GlassButton("Add widgets",Modifier.fillMaxWidth(),glyph="plus",onClick=openGallery)
        }
        edit?.let { PageEditOverlay(it,finishEdit) }
    }
}

@Composable internal fun FluidCustomPage(page:String,state:FluidState,model:FluidModel,hero:FluidHero,modifier:Modifier,calendar:()->Unit,activity:()->Unit,edit:PageEdit?=null,finishEdit:()->Unit={}) {
    val widgets=model.widgetsForPage(page)
    val scroll=rememberScrollState()
    val density=LocalDensity.current
    val phase=rememberInfiniteTransition(label="Widget wobble").animateFloat(0f,6.2831855f,infiniteRepeatable(tween(900,easing=LinearEasing)),label="Wobble")
    SideEffect { edit?.bind(widgets,with(density){14.dp.toPx()},scroll) }
    Box(modifier.navigationBarsPadding().onGloballyPositioned{edit?.viewport=it.boundsInRoot()}) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom=100.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            FluidHeading(page,"A fresh page to make your own.")
            if(widgets.isEmpty()) Text("Long press empty space to add widgets.",style=F.text(13,muted=true))
            widgets.forEachIndexed { index,kind -> key(kind) {
                Box(Modifier.pageEditCard(kind,edit,phase,index)) {
                    ExpandableWidget(kind,state,model,hero,calendar,activity)
                    if(edit!=null && edit.active) Box(Modifier.matchParentSize().pageEditDrag(kind,edit))
                }
            } }
        }
        edit?.let { PageEditOverlay(it,finishEdit) }
    }
}
