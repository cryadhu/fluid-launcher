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
import kotlin.math.*

/** Scrolled-off cards cycle behind the remaining deck, keeping every item composed. */
internal fun notificationDeckOrder(count: Int, first: Int): List<Int> =
    if(count<=0) emptyList() else List(count) { (first.coerceIn(0,count-1)+it)%count }

@Composable internal fun NotificationStack(state: FluidState, model: FluidModel, detail: Boolean = false) {
    val notifications by FluidNotifications.items.collectAsState()
    val edit=LocalWidgetEdit.current
    var expanded by rememberSaveable { mutableStateOf(detail) }
    var position by remember { mutableFloatStateOf(0f) }
    val size=state.config.sizes[FluidWidget.NOTIFICATIONS] ?: "Standard"
    val compactHeight=when(size) { "Compact"->76.dp;"Large"->104.dp;else->88.dp }
    val rowHeight=when(size) { "Compact"->94.dp;"Large"->120.dp;else->108.dp }
    val density=LocalDensity.current
    val stride=with(density) { rowHeight.toPx() }
    val last=(notifications.size-1).coerceAtLeast(0).toFloat()
    val currentPosition=position.coerceIn(0f,last)
    val head=currentPosition.roundToInt()
    val scroll=rememberScrollableState { delta ->
        val previous=position.coerceIn(0f,last)
        position=(previous-delta/stride).coerceIn(0f,last)
        (previous-position)*stride
    }
    LaunchedEffect(notifications.size) {
        position=position.coerceIn(0f,last)
        if(notifications.isEmpty()) expanded=false
    }
    LaunchedEffect(scroll.isScrollInProgress,expanded) {
        if(!scroll.isScrollInProgress) {
            if(!expanded) position=0f
            else animate(position,position.roundToInt().toFloat(),animationSpec=spring(1f,400f)) { value,_ -> position=value.coerceIn(0f,last) }
        }
    }
    Column(Modifier.fillMaxWidth().animateContentSize(spring(1f,350f)).testTag("fluid-widget-NOTIFICATIONS")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Notification(${notifications.size})",style=F.text(11,muted=true),modifier=Modifier.weight(1f)
                .combinedClickable(onClick={},onLongClick={edit(FluidWidget.NOTIFICATIONS)}))
            if(notifications.size>1 || expanded) TextButton(onClick={expanded=!expanded}) {
                Text(if(expanded) "Collapse" else "See all",style=F.text(11,true),color=F.purple)
            }
            if(notifications.any { it.clearable }) TextButton(onClick={FluidNotifications.clear()}) { Text("Clear all",style=F.text(11,true),color=F.purple) }
        }
        if(notifications.isEmpty()) {
            Column(Modifier.fillMaxWidth().glass(28.dp).combinedClickable(onClick={if(!state.device.mediaAccess) model.accessNotifications()},onLongClick={edit(FluidWidget.NOTIFICATIONS)}).padding(20.dp)) {
                Text(if(state.device.mediaAccess) "All caught up" else "A clearer view of your day",style=F.text(15,true))
                Text(if(state.device.mediaAccess) "New notifications will stack here." else "Connect notifications to show your live updates.",style=F.text(11,muted=true),modifier=Modifier.padding(top=7.dp))
                if(!state.device.mediaAccess) GlassButton("Connect notifications",Modifier.padding(top=12.dp),onClick=model::accessNotifications)
            }
        } else {
            val tail=if(expanded) (notifications.size-4).coerceIn(0,3)*6.dp else (notifications.size-1).coerceIn(0,3)*7.dp
            val height=if(expanded) min(notifications.size,4)*rowHeight+tail else compactHeight+tail
            Box(Modifier.fillMaxWidth().height(height).clipToBounds()
                .testTag(if(expanded) "notification-inline-expanded" else "notification-compact-stack")
                .semantics {
                    stateDescription=if(expanded) "First notification ${head+1} of ${notifications.size}" else "Compact notification stack"
                    if(expanded) {
                        verticalScrollAxisRange=ScrollAxisRange({currentPosition},{last})
                        scrollBy { _,y -> position=(position+y/stride).coerceIn(0f,last);true }
                    }
                }.scrollable(scroll,Orientation.Vertical,enabled=expanded && notifications.size>1)) {
                notificationDeckOrder(notifications.size,if(expanded) head else 0).withIndex().toList().asReversed().forEach { (relative,index) ->
                    val notice=notifications[index]
                    key(notice.key) {
                        val behind=relative>=if(expanded) 4 else 1
                        val depth=if(expanded) (relative-3).coerceIn(0,3) else relative.coerceAtMost(3)
                        val targetY=if(expanded) relative.coerceAtMost(3)*rowHeight+depth*6.dp else depth*7.dp
                        val y by animateDpAsState(targetY,spring(1f,400f),label="Notification deck position")
                        val inset by animateDpAsState(if(expanded) depth*5.dp else depth*12.dp,spring(1f,400f),label="Notification deck inset")
                        val cardHeight by animateDpAsState(if(expanded) rowHeight-4.dp else compactHeight,spring(1f,400f),label="Notification card height")
                        DismissibleNotification(notice,model,{edit(FluidWidget.NOTIFICATIONS)},expanded,!behind,
                            Modifier.padding(horizontal=inset).offset(y=y).height(cardHeight))
                    }
                }
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
        .then(drag).glass(28.dp,true)
        .combinedClickable(enabled=interactive,onClick={model.openNotification(notice)},onLongClick={latestHold()})
        .then(if(interactive) Modifier.testTag("notification-card:${notice.key}").semantics { stateDescription="Visible notification" }
            else Modifier.clearAndSetSemantics { testTag="notification-card:${notice.key}";stateDescription="Behind stack";invisibleToUser() })
        .padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(notice.app,style=F.text(11,muted=true),maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(notice.title,style=F.text(14,true),maxLines=if(expanded) 2 else 1,overflow=TextOverflow.Ellipsis)
            if(expanded && notice.text.isNotBlank()) Text(notice.text,style=F.text(11,muted=true),maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        Box(Modifier.size(if(expanded) 46.dp else 48.dp).clip(RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
            if(notice.icon!=null) Image(notice.icon.asImageBitmap(),null,Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().glass(14.dp),contentAlignment=Alignment.Center) { Glyph("app",F.purple,Modifier.size(25.dp)) }
        }
    }
}
