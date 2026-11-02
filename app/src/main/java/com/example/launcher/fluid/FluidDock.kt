@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.launcher.data.InstalledApp
import com.example.launcher.ui.Glyph
import kotlin.math.roundToInt

internal val LocalDockDragging=staticCompositionLocalOf<(Boolean)->Unit> { {} }

@Composable internal fun FluidDock(state: FluidState, model: FluidModel, badges: Set<String>, allApps: () -> Unit, launch: (InstalledApp, Rect) -> Unit) {
    val draggingChanged=LocalDockDragging.current
    val apps=fluidDock(state.apps,state.config.dock).take(state.config.dockCount)
    var dragged by remember { mutableStateOf<InstalledApp?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bin by remember { mutableStateOf(Rect.Zero) }
    val bounds=remember { mutableStateMapOf<String,Rect>() }
    val overBin=dragged!=null && bin.contains(pointer)
    val binScale by animateFloatAsState(if(overBin) 1.18f else 1f,spring(.6f,400f),label="Dock bin hover")
    fun finish(drop: Boolean) {
        val app=dragged
        if(drop && app!=null) {
            val key=app.component.flattenToString()
            if(bin.contains(pointer)) model.removeDock(key)
            else {
                val target=apps.indexOfFirst { bounds[it.component.flattenToString()]?.contains(pointer)==true }
                if(target>=0) model.reorderDock(key,target)
            }
        }
        dragged=null;draggingChanged(false)
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin=it.positionInRoot() }.testTag("fluid-dock")) {
        Row(Modifier.fillMaxWidth().glass(28.dp).padding(horizontal=4.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            apps.forEach { app -> key(app.component) {
                val key=app.component.flattenToString()
                Column(Modifier.weight(1f).onGloballyPositioned { bounds[key]=it.boundsInRoot() }
                    .graphicsLayer { alpha=if(dragged?.component==app.component) 0f else 1f }
                    .pointerInput(key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart={local -> draggingChanged(true);dragged=app;pointer=(bounds[key]?.topLeft ?: Offset.Zero)+local},
                            onDrag={change,delta -> change.consume();pointer=(bounds[key]?.topLeft ?: Offset.Zero)+change.position},
                            onDragEnd={finish(true)},onDragCancel={finish(false)})
                    }.clickable(onClick={launch(app,bounds[key] ?: Rect.Zero)})
                    .semantics(mergeDescendants=true) {
                        contentDescription="Dock ${app.label}"
                        customActions=listOf(CustomAccessibilityAction("Remove from dock") { model.removeDock(key);true })
                    }.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    DockArtwork(app,state.config.dockIconSize.dp,badges)
                    if(state.config.dockLabels) Text(app.label,style=F.text(9),maxLines=1,modifier=Modifier.padding(top=5.dp))
                }
            } }
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { GlassIcon("app","All apps",Modifier.size(40.dp),onClick=allApps) }
        }
        AnimatedVisibility(dragged!=null,Modifier.align(Alignment.TopCenter).offset(y=(-94).dp),enter=fadeIn()+scaleIn(spring(.6f,380f)),exit=fadeOut()+scaleOut()) {
            Column(Modifier.onGloballyPositioned { bin=it.boundsInRoot() }.graphicsLayer { scaleX=binScale;scaleY=binScale }.glass(24.dp,true)
                .background(if(overBin) Color(0x55E85773) else Color.Transparent).padding(horizontal=24.dp,vertical=15.dp).testTag("dock-remove-target")
                .semantics { contentDescription="Remove from dock";stateDescription=if(overBin) "Ready to remove" else "Drag here" },horizontalAlignment=Alignment.CenterHorizontally) {
                Glyph("bin",if(overBin) Color(0xFFBA2446) else F.ink,Modifier.size(28.dp))
                Text("Remove",style=F.text(11,true))
            }
        }
        dragged?.let { app ->
            Box(Modifier.offset { IntOffset((pointer.x-origin.x-28.dp.toPx()).roundToInt(),(pointer.y-origin.y-28.dp.toPx()).roundToInt()) }
                .size(56.dp).graphicsLayer { scaleX=1.1f;scaleY=1.1f }.testTag("dock-drag-preview")) { DockArtwork(app,56.dp,badges) }
        }
    }
}
@Composable private fun DockArtwork(app: InstalledApp, size: Dp, badges: Set<String>) {
    Box(Modifier.size(size).glass(16.dp),contentAlignment=Alignment.Center) {
        if(app.artwork!=null) Image(app.artwork.asImageBitmap(),null,Modifier.fillMaxSize(.7f)) else Glyph(app.icon.glyph,F.purple,Modifier.fillMaxSize(.6f))
        if(app.component.packageName in badges) Box(Modifier.align(Alignment.TopEnd).size(6.dp).background(F.purple,androidx.compose.foundation.shape.CircleShape))
    }
}
@Composable internal fun DockSettings(state: FluidState, model: FluidModel) {
    Text("Dock",style=F.text(22,true))
    Text("Hold and drag icons to reorder. Drop on the bin to remove.",style=F.text(12,muted=true))
    Text("App icons: ${state.config.dockCount}",style=F.text(14,true))
    Slider(state.config.dockCount.toFloat(),{ value -> model.configure { it.copy(dockCount=value.roundToInt()) } },valueRange=1f..6f,steps=4,modifier=Modifier.semantics { contentDescription="Dock icon count" })
    Text("Icon size: ${state.config.dockIconSize} dp",style=F.text(14,true))
    Slider(state.config.dockIconSize.toFloat(),{ value -> model.configure { it.copy(dockIconSize=value.roundToInt()) } },valueRange=36f..56f,modifier=Modifier.semantics { contentDescription="Dock icon size" })
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Show app names",style=F.text(14),modifier=Modifier.weight(1f))
        Switch(state.config.dockLabels,{value -> model.configure { it.copy(dockLabels=value) } })
    }
    GlassButton("Restore suggested apps") { model.configure { it.copy(dock=listOf("@auto")) } }
    Text("Choose dock apps",style=F.text(17,true))
    val keys=fluidDock(state.apps,state.config.dock).take(state.config.dockCount).map { it.component.flattenToString() }
    state.apps.forEach { app ->
        val key=app.component.flattenToString()
        Row(Modifier.fillMaxWidth().glass(20.dp).clickable { model.toggleDock(key) }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(app.label,style=F.text(13),modifier=Modifier.weight(1f))
            Checkbox(key in keys,{model.toggleDock(key)})
        }
    }
}
