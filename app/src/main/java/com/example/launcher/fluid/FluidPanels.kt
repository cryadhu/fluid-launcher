@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.launcher.data.InstalledApp
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.delay

@Composable internal fun FluidDrawer(state:FluidState,badges:Set<String>,query:String,search:(String)->Unit,tab:String,selectTab:(String)->Unit,focusSearch:Boolean,ready:Boolean,inputReady:Boolean,
    launch:(InstalledApp,Rect)->Unit,options:(InstalledApp)->Unit,close:()->Unit,settings:()->Unit,pull:(Float)->Unit,finishPull:(Float)->Unit) {
    val requester=remember{FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    val grid=rememberLazyGridState()
    var pullDistance by remember{mutableFloatStateOf(0f)}
    LaunchedEffect(ready,focusSearch,inputReady){if(ready && focusSearch && inputReady){requester.requestFocus();keyboard?.show()}}
    LaunchedEffect(query,tab){grid.scrollToItem(0)}
    val space=state.config.spaces.find{it.name==tab}
    val filtered=remember(state.apps,space,query){fluidFilteredApps(state.apps,query,space)}
    val favorites=remember(state.apps,state.config.favoriteApps){state.apps.filter { it.component.flattenToString() in state.config.favoriteApps }.take(4)}
    Column(Modifier.fillMaxSize().imePadding().testTag("fluid-app-drawer")) {
        Box(Modifier.fillMaxWidth().height(8.dp).pointerInput(Unit){detectVerticalDragGestures(onDragStart={pullDistance=0f},onVerticalDrag={change,y->change.consume();pullDistance=(pullDistance+y).coerceAtLeast(0f);pull(pullDistance)},onDragEnd={finishPull(pullDistance)},onDragCancel={finishPull(0f)})})
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextField(query,search,Modifier.weight(1f).heightIn(min=44.dp).glass(28.dp).focusProperties{canFocus=inputReady}.focusRequester(requester).testTag("fluid-app-search"),enabled=inputReady,singleLine=true,
                placeholder={Text("Search apps",style=F.text(13,muted=true))},leadingIcon={Glyph("search",F.ink,Modifier.size(18.dp))},trailingIcon={if(query.isNotBlank())IconButton(onClick={search("")}){Glyph("close",F.muted,Modifier.size(17.dp))}},textStyle=F.text(),
                colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=F.purple,focusedTextColor=F.ink,unfocusedTextColor=F.ink))
            GlassIcon("settings","Drawer settings",Modifier.size(40.dp),onClick=settings)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (listOf("All")+state.config.spaces.map{it.name}).forEach { label ->
                val selected=label==(space?.name ?: "All")
                Box(Modifier.height(30.dp).widthIn(min=if(label=="All")34.dp else 46.dp).clip(CircleShape).then(if(selected)Modifier.background(Brush.linearGradient(listOf(F.lilac,F.purple)))else Modifier.glass(20.dp)).clickable(role=Role.Tab){selectTab(label)}.semantics{this.selected=selected}.padding(horizontal=10.dp),contentAlignment=Alignment.Center){Text(label,style=F.text(9,true),color=if(selected)Color.White else F.ink,maxLines=1)}
            }
        }
        LazyVerticalGrid(GridCells.Fixed(4),Modifier.weight(1f).fillMaxWidth().testTag("fluid-app-grid"),state=grid,contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+30.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            if(query.isBlank() && space==null && favorites.isNotEmpty()) {
                item(span={GridItemSpan(4)}) { Text("Favorites",style=F.text(11,muted=true),modifier=Modifier.padding(top=2.dp,bottom=4.dp)) }
                item(span={GridItemSpan(4)}) { Row(Modifier.fillMaxWidth().glass(28.dp).padding(horizontal=7.dp,vertical=5.dp)) { favorites.forEach { app -> FluidAppTile(app,badges,state.config.labels,launch,options,Modifier.weight(1f)) };repeat(4-favorites.size){Spacer(Modifier.weight(1f))} } }
                item(span={GridItemSpan(4)}) { Text("All apps",style=F.text(11,muted=true),modifier=Modifier.padding(top=10.dp,bottom=1.dp)) }
            }
            if(filtered.isEmpty())item(span={GridItemSpan(4)}) {
                Column(Modifier.fillMaxWidth().padding(top=48.dp),horizontalAlignment=Alignment.CenterHorizontally){Glyph("app",F.purple,Modifier.size(34.dp));Text(if(state.loading)"Finding your apps…"else if(query.isNotBlank())"No matching apps"else "Make this space yours",style=F.text(17,true),modifier=Modifier.padding(top=18.dp));Text(if(space!=null)"Hold any app in All to add it to ${space.name}."else "Your installed apps will appear here.",style=F.text(12,muted=true),modifier=Modifier.padding(top=8.dp))}
            }
            items(filtered,key={it.component.flattenToString()}){app-> FluidAppTile(app,badges,state.config.labels,launch,options,Modifier.animateItem().fillMaxWidth())}
        }
    }
}

@Composable internal fun FluidAppOptions(app:InstalledApp,state:FluidState,model:FluidModel,dismiss:()->Unit) {
    val key=app.component.flattenToString()
    Box(Modifier.fillMaxSize().background(Color(0xFF2E204B).copy(alpha=.10f)).statusBarsPadding().navigationBarsPadding().padding(top=8.dp)) {
        Column(Modifier.fillMaxSize().glass(36.dp,strength=1.15f).padding(20.dp).testTag("fluid-app-options"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){GlassIcon("back","Back",onClick=dismiss);if(app.artwork!=null)Image(app.artwork.asImageBitmap(),null,Modifier.size(42.dp).padding(start=12.dp));Text(app.label,style=F.text(22,true),modifier=Modifier.weight(1f).padding(horizontal=12.dp),maxLines=2);GlassIcon("close","Close app options",Modifier.size(36.dp),onClick=dismiss)}
            Spacer(Modifier.height(10.dp))
            OptionRow("app",if(key in state.config.favoriteApps)"Remove from favorites"else"Add to favorites"){model.toggleFavoriteApp(key);dismiss()}
            state.config.spaces.forEach { space ->
                val inside=key in space.apps
                OptionRow("files",if(inside)"Remove from ${space.name}" else "Add to ${space.name}"){model.toggleSpaceApp(space.id,key);dismiss()}
            }
            val dock=fluidDock(state.apps,state.config.dock).take(if(state.config.dock==listOf("@auto")) state.config.dockCount else 6).map{it.component.flattenToString()}
            OptionRow("plus",if(key in dock)"Remove from dock"else"Add to dock"){
                model.toggleDock(key);dismiss()
            }
            OptionRow("settings","App info"){dismiss();model.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${app.component.packageName}")))}
            val context=LocalContext.current
            val system=remember(app.component){runCatching{context.packageManager.getApplicationInfo(app.component.packageName,0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM!=0}.getOrDefault(true)}
            if(!system)OptionRow("bin","Uninstall"){dismiss();model.open(Intent(Intent.ACTION_DELETE,Uri.parse("package:${app.component.packageName}")))}
        }
    }
}

@Composable private fun OptionRow(glyph:String,title:String,click:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(role=Role.Button,onClick=click).padding(vertical=13.dp,horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {Glyph(glyph,F.purple,Modifier.size(21.dp));Text(title,style=F.text(13))}
}

@Composable internal fun FluidPanel(name:String,state:FluidState,model:FluidModel,chooseHome:()->Unit,close:()->Unit,navigate:(String)->Unit,calendar:()->Unit,activity:()->Unit,back:()->Unit,spaceStart:Long?=null) {
    val customizePage=name.removePrefix("Customize:").takeIf { name.startsWith("Customize:") } ?: "Home"
    Box(Modifier.fillMaxSize().background(Color(0xFF40315E).copy(alpha=.08f)).statusBarsPadding().padding(top=10.dp)) {
        Column(Modifier.fillMaxSize().glass(36.dp,strength=1.15f).testTag("fluid-panel-$name")) {
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){GlassIcon("back","Back",onClick=back);Column(Modifier.weight(1f).padding(start=13.dp)){Text(if(name.startsWith("Customize:"))"Customize $customizePage" else name,style=F.text(23,true));Text(when(name){"Widgets"->"Little windows into your day";"Space widgets"->"Widgets for your space";"Spaces"->"Tabs and the apps in them";"Settings"->"Make it feel like you";else->"Widgets and layout for this page"},style=F.text(11,muted=true))};GlassIcon("close","Close panel",onClick=close)}
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(name) {
                    "Widgets", "Space widgets" -> {
                        FluidWidget.entries.forEach { widget ->
                            Column(Modifier.fillMaxWidth().glass(28.dp).padding(18.dp)) {
                                val enabled=widget in if(name=="Space widgets")state.config.spaceWidgets else state.config.widgets
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(widget.title,style=F.text(17,true));WidgetSizeChip(widget,state.config)};Text(widget.subtitle,style=F.text(11,muted=true))};GlassButton(if(enabled)"Remove"else"Add",primary=!enabled){if(name=="Space widgets")model.toggleSpaceWidget(widget)else model.toggleWidget(widget)}}
                                Spacer(Modifier.height(18.dp));WidgetPreview(widget,state,model,calendar,activity)
                            }
                        }
                    }
                    "Spaces" -> SpaceTabsEditor(state,model,spaceStart)
                    "Wallpaper" -> { WallpaperSettings(state,model) }
                    "Dock" -> { DockSettings(state, model) }
                    "Market & news settings" -> { MarketNewsSettings(state, model) }
                    "Pages" -> {
                        Text("Markets & News and Home are fixed. You can manage the pages after Home.",style=F.text(12,muted=true))
                        state.config.pages.forEachIndexed { index,page ->
                            Row(Modifier.fillMaxWidth().glass(24.dp).padding(13.dp),verticalAlignment=Alignment.CenterVertically) { Text(page,style=F.text(14,true),modifier=Modifier.weight(1f));if(index>=2){GlassIcon("up","Move $page up",Modifier.size(32.dp)){model.movePage(page,-1)};GlassIcon("next","Move $page down",Modifier.size(32.dp)){model.movePage(page,1)};GlassIcon("close","Remove $page",Modifier.size(32.dp)){model.removePage(page)}} }
                        }
                        GlassButton("Add page",Modifier.fillMaxWidth(),primary=true,glyph="plus",onClick=model::addPage)
                    }
                    "Settings" -> {
                        GlassButton("Market & news settings", glyph="settings") { navigate("Market & news settings") }
                        PanelCard("APPEARANCE"){
                            Text("Soft light. Quiet dark.",style=F.text(18,true));Text("Every surface follows your theme.",style=F.text(12,muted=true),modifier=Modifier.padding(top=6.dp))
                            Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                listOf("Light","Dark","Auto").forEach{mode->GlassButton(mode,Modifier.weight(1f),primary=state.config.mode==mode){model.selectTheme(mode)}}
                            }
                            Text("Accent color",style=F.text(12,muted=true),modifier=Modifier.padding(top=18.dp))
                            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) { listOf("#8051FF","#2878E8","#0EAD92","#E35A8A","#E99827").forEach { color -> Box(Modifier.size(34.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(color))).border(if(state.config.accent==color)2.dp else 0.dp,Color.White,CircleShape).clickable{model.configure{it.copy(accent=color)}}) } }
                        }
                        PanelCard("DOCK") {
                            OptionRow("app","Dock") { navigate("Dock") }
                            Text("Icon count, size, names and favorite apps",style=F.text(12,muted=true))
                        }
                        PanelCard("LAYOUT"){
                            SettingSwitch("App labels","Keep names under your apps",state.config.labels){value->model.configure{it.copy(labels=value)}}
                            GlassButton("Pages",Modifier.padding(top=12.dp),glyph="app"){navigate("Pages")}
                            GlassButton("Arrange home widgets",Modifier.padding(top=12.dp),glyph="app"){navigate("Customize")}
                            GlassButton("Organize spaces",Modifier.padding(top=12.dp),glyph="files"){navigate("Spaces")}
                        }
                        PanelCard("CONNECTIONS"){
                            OptionRow("calendar","Calendar access",calendar)
                            OptionRow("silent","Notifications & music",model::accessNotifications)
                            OptionRow("app","Set as default launcher",chooseHome)
                            OptionRow("settings","Android settings"){model.open(Intent(Settings.ACTION_SETTINGS))}
                        }
                    }
                    else -> {
                        if(name.startsWith("Widgets:")) {
                            val page=name.removePrefix("Widgets:")
                            FluidWidget.entries.forEach { widget ->
                                val enabled=widget in model.widgetsForPage(page)
                                Column(Modifier.fillMaxWidth().glass(28.dp).padding(18.dp)) {
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(widget.title,style=F.text(17,true));WidgetSizeChip(widget,state.config)};Text(widget.subtitle,style=F.text(11,muted=true))};GlassButton(if(enabled)"Remove"else"Add",primary=!enabled){model.toggleWidgetForPage(page,widget)}}
                                    Spacer(Modifier.height(18.dp));WidgetPreview(widget,state,model,calendar,activity)
                                }
                            }
                        } else {
                        PanelCard("MAKE ROOM FOR YOU") {
                            Text("A softer kind of home.",style=F.text(22,true));Text("Add a widget, set the mood, find your space.",style=F.text(13,muted=true),modifier=Modifier.padding(top=8.dp))
                            Row(Modifier.fillMaxWidth().padding(top=22.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("app","Widget gallery",primary=true){navigate("Widgets:$customizePage")};Text("Widgets",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("weather","Appearance"){navigate("Settings")};Text("Theme",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("image","Wallpaper"){navigate("Wallpaper")};Text("Wallpaper",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("files","Organize spaces"){navigate("Spaces")};Text("Spaces",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("settings","Home settings"){navigate("Settings")};Text("Settings",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                            }
                        }
                        Text("ON YOUR HOME",style=F.text(11,true,muted=true),modifier=Modifier.padding(start=4.dp))
                        val currentWidgets=model.widgetsForPage(customizePage)
                        if(currentWidgets.isEmpty())Text("A little breathing room. Add a widget whenever you're ready.",style=F.text(13,muted=true))
                        currentWidgets.forEach { kind ->
                            Row(Modifier.fillMaxWidth().glass(24.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Glyph(kind.glyph,F.purple,Modifier.size(23.dp));Column(Modifier.weight(1f).padding(start=14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(kind.title,style=F.text(14,true));WidgetSizeChip(kind,state.config)};Text(kind.subtitle,style=F.text(10,muted=true))};GlassIcon("close","Remove ${kind.title}",Modifier.size(32.dp)){model.toggleWidgetForPage(customizePage,kind)}}
                        }
                        GlassButton("Explore widgets",Modifier.fillMaxWidth(),primary=true,glyph="plus"){navigate("Widgets:$customizePage")}
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun PanelCard(title:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().glass(28.dp).padding(20.dp)){Text(title,style=F.text(10,true,muted=true),modifier=Modifier.padding(bottom=14.dp));content()}
}
@Composable private fun SettingSwitch(title:String,subtitle:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=F.text(14,true));Text(subtitle,style=F.text(11,muted=true),modifier=Modifier.padding(top=4.dp))};Switch(value,change,colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=F.purple,uncheckedTrackColor=F.muted.copy(alpha=.2f),uncheckedBorderColor=Color.Transparent))}}

@Composable internal fun WidgetSizeChip(widget:FluidWidget,config:FluidConfig) {
    Text(widgetGrid(widget,widgetSize(widget,config)),style=F.text(10,true),color=F.purple,modifier=Modifier.padding(start=8.dp).clip(CircleShape).background(F.purple.copy(alpha=.12f)).padding(horizontal=8.dp,vertical=3.dp).testTag("widget-size-${widget.name}"))
}

@Composable private fun WidgetPreview(widget:FluidWidget,state:FluidState,model:FluidModel,calendar:()->Unit,activity:()->Unit) {
    val fixed=widgetFixedHeight(widget)
    if(fixed==null) WidgetBody(widget,state,model,calendarAccess=calendar,activityAccess=activity)
    else Box(Modifier.fillMaxWidth(if(isHalf(widget,state.config)).5f else 1f).height(fixed).clip(RoundedCornerShape(24.dp)).background(F.purple.copy(alpha=.05f))) {
        Column { WidgetBody(widget,state,model,calendarAccess=calendar,activityAccess=activity) }
    }
}
