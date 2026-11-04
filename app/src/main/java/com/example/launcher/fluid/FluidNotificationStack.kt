@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.fluid

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlin.math.*

internal fun notificationTimestamp(time: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    java.time.Instant.ofEpochMilli(time).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("MMM, dd yyyy hh:mma",java.util.Locale.ENGLISH))

@Composable internal fun NotificationStack(state: FluidState, model: FluidModel, detail: Boolean = false) {
    val notifications by FluidNotifications.items.collectAsState()
    val edit=LocalWidgetEdit.current
    val pageEditor=LocalPageEdit.current
    var expanded by rememberSaveable { mutableStateOf(detail) }
    val size=state.config.sizes[FluidWidget.NOTIFICATIONS] ?: "Standard"
    val compactHeight=when(size) { "Compact"->88.dp;"Large"->112.dp;else->96.dp }
    // Some OEM listener implementations miss a removal callback. Reconcile against
    // Android's active list while this widget is visible so stale cards disappear.
    LaunchedEffect(Unit) { while(true) { FluidNotifications.reconcile(); delay(4_000) } }
    LaunchedEffect(notifications.isEmpty()) { if(notifications.isEmpty()) expanded=false }
    Column(Modifier.fillMaxWidth().padding(bottom=20.dp).testTag("fluid-widget-NOTIFICATIONS")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Notification(${notifications.size})",style=F.text(11,muted=true),modifier=Modifier.weight(1f)
                .combinedClickable(onClick={},onLongClick={if(pageEditor==null)edit(FluidWidget.NOTIFICATIONS)}))
            if(notifications.size>1 || expanded) TextButton(onClick={expanded=!expanded}) {
                Text(if(expanded) "Collapse" else "See all",style=F.text(11,true),color=F.purple)
            }
            if(notifications.any { it.clearable }) TextButton(onClick={FluidNotifications.clear()}) { Text("Clear all",style=F.text(11,true),color=F.purple) }
        }
        if(notifications.isEmpty()) {
            Column(Modifier.fillMaxWidth().glass(28.dp,elevated=true).combinedClickable(onClick={if(!state.device.mediaAccess) model.accessNotifications()},onLongClick={if(pageEditor==null)edit(FluidWidget.NOTIFICATIONS)}).padding(20.dp)) {
                Text(if(state.device.mediaAccess) "All caught up" else "A clearer view of your day",style=F.text(15,true))
                Text(if(state.device.mediaAccess) "New notifications will stack here." else "Connect notifications to show your live updates.",style=F.text(11,muted=true),modifier=Modifier.padding(top=7.dp))
                if(!state.device.mediaAccess) GlassButton("Connect notifications",Modifier.padding(top=12.dp),onClick=model::accessNotifications)
            }
        } else {
            val progress by animateFloatAsState(if(expanded)1f else 0f,spring(.9f,320f),label="Notification unstack")
            val cardHeight=132.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val previews=notifications.take(3)
            val closedHeight=compactHeight+(previews.size-1)*7.dp
            val openHeight=cardHeight*notifications.size+8.dp*(notifications.size-1)
            val totalHeight=closedHeight+(openHeight-closedHeight)*progress
            Box(Modifier.fillMaxWidth().height(totalHeight).testTag(if(expanded)"notification-inline-expanded"else"notification-compact-stack")) {
                val visible=if(expanded || progress>.001f)notifications else previews
                visible.withIndex().toList().asReversed().forEach { (index,notice) -> key(notice.key) {
                    val closedY=index.coerceAtMost(2)*7.dp
                    val openY=(cardHeight+8.dp)*index
                    val inset=index.coerceAtMost(2)*12.dp*(1f-progress)
                    val height=compactHeight+(cardHeight-compactHeight)*progress
                    DismissibleNotification(notice,model,{edit(FluidWidget.NOTIFICATIONS)},progress>.5f,expanded || index==0,
                        Modifier.offset(y=closedY+(openY-closedY)*progress).padding(horizontal=inset).height(height)
                            .graphicsLayer { alpha=if(index<3)1f else progress; scaleX=1f-(1f-progress)*index.coerceAtMost(2)*.012f })
                } }
            }
        }
    }
}

@Composable private fun DismissibleNotification(notice: FluidNotification, model: FluidModel, hold: () -> Unit, expanded: Boolean, interactive: Boolean, modifier: Modifier) {
    val scope=rememberCoroutineScope()
    val offset=remember(notice.key) { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var total by remember { mutableFloatStateOf(0f) }
    val latestHold by rememberUpdatedState(hold)
    val drag=if(interactive) Modifier.pointerInput(notice.key) {
        detectHorizontalDragGestures(onDragStart={total=offset.value},onHorizontalDrag={change,delta -> change.consume();total+=delta;scope.launch { offset.snapTo(total) }},onDragEnd={scope.launch {
            if(abs(total)>width*.28f && notice.clearable) {
                offset.animateTo(sign(total)*width*1.15f,tween(200));FluidNotifications.dismiss(notice.key)
                val removed=withTimeoutOrNull(1500) { FluidNotifications.items.first { list -> list.none { it.key==notice.key } } }
                if(removed==null) offset.animateTo(0f,spring(1f,350f))
            } else {
                offset.animateTo(0f,spring(.85f,420f))
                if(!notice.clearable && abs(total)>width*.28f) model.message("This ongoing notification cannot be cleared")
            }
        }},onDragCancel={scope.launch { offset.animateTo(0f) }})
    } else Modifier
    Row(modifier.fillMaxWidth().onSizeChanged { width=it.width.toFloat() }
        .graphicsLayer { translationX=offset.value;rotationZ=offset.value/width*3f;alpha=1f-(abs(offset.value)/width).coerceIn(0f,.8f) }
        .then(drag).glass(28.dp,elevated=true,shadowed=interactive)
        .combinedClickable(enabled=interactive,onClick={model.openNotification(notice)},onLongClick={latestHold()})
        .then(if(interactive) Modifier.testTag("notification-card:${notice.key}").semantics { stateDescription="Visible notification" }
            else Modifier.clearAndSetSemantics { testTag="notification-card:${notice.key}";stateDescription="Behind stack";invisibleToUser() })
        .padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(notice.app,style=F.text(11,muted=true),maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(notice.title,style=F.text(14,true),maxLines=if(expanded) 2 else 1,overflow=TextOverflow.Ellipsis)
            if(expanded && notice.text.isNotBlank()) Text(notice.text,style=F.text(11,muted=true),maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(notificationTimestamp(notice.time),style=F.text(10,muted=true),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).testTag("notification-icon:${notice.key}"),contentAlignment=Alignment.Center) {
            if(notice.icon!=null) Image(notice.icon.asImageBitmap(),null,Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().glass(10.dp,shadowed=expanded),contentAlignment=Alignment.Center) { Glyph("app",F.purple,Modifier.size(19.dp)) }
        }
    }
}
