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

internal fun fluidFilteredApps(apps:List<InstalledApp>,config:FluidConfig,query:String,tab:String):List<InstalledApp> = apps.filter { app ->
    val key=app.component.flattenToString()
    app.label.contains(query.trim(),true) && when(tab) {
        "Work" -> key in config.work
        "Personal" -> key in config.personal
        "Tools" -> app.icon.glyph in setOf("settings","calculator","clock","files","calendar","scan","translate","weather","notes","tasks")
        else -> true
    }
}

@Composable internal fun FluidDrawer(state:FluidState,badges:Set<String>,query:String,search:(String)->Unit,tab:String,selectTab:(String)->Unit,focusSearch:Boolean,ready:Boolean,inputReady:Boolean,
    launch:(InstalledApp,Rect)->Unit,options:(InstalledApp)->Unit,close:()->Unit,settings:()->Unit,pull:(Float)->Unit,finishPull:(Float)->Unit) {
    val requester=remember{FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    val grid=rememberLazyGridState()
    var pullDistance by remember{mutableFloatStateOf(0f)}
    LaunchedEffect(ready,focusSearch,inputReady){if(ready && focusSearch && inputReady){requester.requestFocus();keyboard?.show()}}
    LaunchedEffect(query,tab){grid.scrollToItem(0)}
    val filtered=remember(state.apps,state.config.work,state.config.personal,query,tab){fluidFilteredApps(state.apps,state.config,query,tab)}
    Column(Modifier.fillMaxSize().glass(36.dp,strength=1.12f).imePadding().testTag("fluid-app-drawer")) {
        Box(Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit){detectVerticalDragGestures(onDragStart={pullDistance=0f},onVerticalDrag={change,y->change.consume();pullDistance=(pullDistance+y).coerceAtLeast(0f);pull(pullDistance)},onDragEnd={finishPull(pullDistance)},onDragCancel={finishPull(0f)})}.semantics { contentDescription="Close app drawer"; onClick {close();true} },contentAlignment=Alignment.Center){Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(F.muted.copy(alpha=.35f)))}
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            TextField(query,search,Modifier.weight(1f).heightIn(min=54.dp).glass(28.dp).focusProperties{canFocus=inputReady}.focusRequester(requester).testTag("fluid-app-search"),enabled=inputReady,singleLine=true,
                placeholder={Text("Search apps",style=F.text(13,muted=true))},leadingIcon={Glyph("search",F.ink,Modifier.size(18.dp))},trailingIcon={if(query.isNotBlank())IconButton(onClick={search("")}){Glyph("close",F.muted,Modifier.size(17.dp))}},textStyle=F.text(),
                colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=F.purple,focusedTextColor=F.ink,unfocusedTextColor=F.ink))
            GlassIcon("settings","Drawer settings",onClick=settings)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("All","Work","Personal","Tools").forEach { name ->
                val selected=name==tab
                Box(Modifier.weight(1f).clip(CircleShape).then(if(selected)Modifier.background(Brush.linearGradient(listOf(F.lilac,F.purple)))else Modifier.glass(20.dp)).clickable(role=Role.Tab){selectTab(name)}.semantics{this.selected=selected}.padding(vertical=9.dp),contentAlignment=Alignment.Center){Text(name,style=F.text(11),color=if(selected)Color.White else F.ink)}
            }
        }
        LazyVerticalGrid(GridCells.Fixed(4),Modifier.weight(1f).fillMaxWidth().testTag("fluid-app-grid"),state=grid,contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+30.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            if(filtered.isEmpty())item(span={GridItemSpan(4)}) {
                Column(Modifier.fillMaxWidth().padding(top=48.dp),horizontalAlignment=Alignment.CenterHorizontally){Glyph("app",F.purple,Modifier.size(34.dp));Text(if(state.loading)"Finding your apps…"else if(query.isNotBlank())"No matching apps"else "Make this space yours",style=F.text(17,true),modifier=Modifier.padding(top=18.dp));Text(if(tab=="Work"||tab=="Personal")"Hold any app in All to assign a space."else "Your installed apps will appear here.",style=F.text(12,muted=true),modifier=Modifier.padding(top=8.dp))}
            }
            items(filtered,key={it.component.flattenToString()}){app-> FluidAppTile(app,badges,state.config.labels,launch,options,Modifier.animateItem().fillMaxWidth())}
        }
    }
}

@Composable internal fun FluidAppOptions(app:InstalledApp,state:FluidState,model:FluidModel,dismiss:()->Unit) {
    val key=app.component.flattenToString()
    Box(Modifier.fillMaxSize().background(Color(0xFF2E204B).copy(alpha=.1f)).clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=dismiss).statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=340.dp).fillMaxWidth().glass(32.dp,true).clickable(interactionSource=remember{MutableInteractionSource()},indication=null){}.padding(20.dp).testTag("fluid-app-options"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){if(app.artwork!=null)Image(app.artwork.asImageBitmap(),null,Modifier.size(42.dp));Text(app.label,style=F.text(18,true),modifier=Modifier.weight(1f).padding(horizontal=12.dp),maxLines=2);GlassIcon("close","Close app options",Modifier.size(32.dp),onClick=dismiss)}
            Spacer(Modifier.height(10.dp))
            OptionRow("files",if(key in state.config.work)"Remove from Work"else"Add to Work"){model.assign(app,if(key in state.config.work)"None"else"Work");dismiss()}
            OptionRow("contacts",if(key in state.config.personal)"Remove from Personal"else"Add to Personal"){model.assign(app,if(key in state.config.personal)"None"else"Personal");dismiss()}
            val dock=fluidDock(state.apps,state.config.dock).map{it.component.flattenToString()}
            OptionRow("plus",if(key in dock)"Remove from dock"else"Add to dock"){
                if(key in dock)model.configure{it.copy(dock=dock-key)}else if(dock.size<4)model.configure{it.copy(dock=dock+key)}else model.message("The dock holds four apps. Remove one first.");dismiss()
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

@Composable internal fun FluidPanel(name:String,state:FluidState,model:FluidModel,chooseHome:()->Unit,close:()->Unit,navigate:(String)->Unit,calendar:()->Unit,activity:()->Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF40315E).copy(alpha=.08f)).statusBarsPadding().padding(top=10.dp)) {
        Column(Modifier.fillMaxSize().glass(36.dp,strength=1.15f).testTag("fluid-panel-$name")) {
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){GlassIcon("back","Back",onClick={if(name=="Customize")close()else navigate("Customize")});Column(Modifier.weight(1f).padding(start=13.dp)){Text(name,style=F.text(23,true));Text(when(name){"Widgets"->"Little windows into your day";"Spaces"->"Apps that belong together";"Settings"->"Make it feel like you";else->"Your home, in your flow"},style=F.text(11,muted=true))};GlassIcon("close","Close panel",onClick=close)}
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(name) {
                    "Widgets" -> {
                        FluidWidget.entries.forEach { widget ->
                            Column(Modifier.fillMaxWidth().glass(28.dp).padding(18.dp)) {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(widget.title,style=F.text(17,true));Text(widget.subtitle,style=F.text(11,muted=true))};GlassButton(if(widget in state.config.widgets)"Remove"else"Add",primary=widget !in state.config.widgets){model.toggleWidget(widget)}}
                                Spacer(Modifier.height(18.dp));WidgetBody(widget,state,model,calendarAccess=calendar,activityAccess=activity)
                            }
                        }
                    }
                    "Spaces" -> {
                        Text("Hold an app in the drawer for more options, or assign it here.",style=F.text(13,muted=true))
                        state.apps.forEach{app->
                            val key=app.component.flattenToString()
                            Row(Modifier.fillMaxWidth().glass(23.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically){if(app.artwork!=null)Image(app.artwork.asImageBitmap(),null,Modifier.size(36.dp));Text(app.label,style=F.text(12,true),modifier=Modifier.weight(1f).padding(horizontal=10.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                                listOf("Work","Personal").forEach{space->val selected=key in if(space=="Work")state.config.work else state.config.personal
                                    TextButton(onClick={model.assign(app,if(selected)"None"else space)}){Text(if(selected)"✓ $space"else space,style=F.text(10,true),color=if(selected)F.purple else F.muted)}
                                }
                            }
                        }
                    }
                    "Settings" -> {
                        PanelCard("APPEARANCE"){
                            Text("Soft light. Quiet dark.",style=F.text(18,true));Text("Every surface follows your theme.",style=F.text(12,muted=true),modifier=Modifier.padding(top=6.dp))
                            Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                listOf("Light","Dark","Auto").forEach{mode->GlassButton(mode,Modifier.weight(1f),primary=state.config.mode==mode){model.configure{it.copy(mode=mode)}}}
                            }
                        }
                        PanelCard("LAYOUT"){
                            SettingSwitch("App labels","Keep names under your apps",state.config.labels){value->model.configure{it.copy(labels=value)}}
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
                        PanelCard("MAKE ROOM FOR YOU") {
                            Text("A softer kind of home.",style=F.text(22,true));Text("Add a widget, set the mood, find your space.",style=F.text(13,muted=true),modifier=Modifier.padding(top=8.dp))
                            Row(Modifier.fillMaxWidth().padding(top=22.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("app","Widget gallery",primary=true){navigate("Widgets")};Text("Widgets",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("weather","Appearance"){navigate("Settings")};Text("Theme",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("files","Organize spaces"){navigate("Spaces")};Text("Spaces",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                                Column(horizontalAlignment=Alignment.CenterHorizontally){GlassIcon("settings","Home settings"){navigate("Settings")};Text("Settings",style=F.text(11),modifier=Modifier.padding(top=8.dp))}
                            }
                        }
                        Text("ON YOUR HOME",style=F.text(11,true,muted=true),modifier=Modifier.padding(start=4.dp))
                        if(state.config.widgets.isEmpty())Text("A little breathing room. Add a widget whenever you're ready.",style=F.text(13,muted=true))
                        state.config.widgets.forEachIndexed { index,kind ->
                            Row(Modifier.fillMaxWidth().glass(24.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Glyph(kind.glyph,F.purple,Modifier.size(23.dp));Column(Modifier.weight(1f).padding(start=14.dp)){Text(kind.title,style=F.text(14,true));Text(kind.subtitle,style=F.text(10,muted=true))};if(index>0)GlassIcon("up","Move ${kind.title} up",Modifier.size(32.dp)){model.moveWidget(kind,-1)};if(index<state.config.widgets.lastIndex)GlassIcon("next","Move ${kind.title} down",Modifier.size(32.dp)){model.moveWidget(kind,1)};GlassIcon("close","Remove ${kind.title}",Modifier.size(32.dp)){model.toggleWidget(kind)}}
                        }
                        GlassButton("Explore widgets",Modifier.fillMaxWidth(),primary=true,glyph="plus"){navigate("Widgets")}
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
