package com.example.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.launcher.LauncherViewModel
import com.example.launcher.data.appIconStyle
import com.example.launcher.ui.theme.Tokens as T

@Composable fun DockBar(items: List<DockItem>, drag: LauncherDragState, model: LauncherViewModel, pageId: Int = 1, active: Boolean = true) {
    val density = LocalDensity.current
    val count = if (drag.app != null) (drag.dockOriginal.size + if (drag.fromDockIndex < 0 && drag.dockOriginal.size < drag.dockCapacity) 1 else 0).coerceIn(1, drag.dockCapacity.coerceAtLeast(1)) else items.size.coerceAtLeast(1)
    SideEffect { if (active) drag.dockSlots.keys.removeAll { it >= count } }
    BoxWithConstraints(Modifier.padding(top = 16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(66.dp, (maxWidth - 24.dp) / count)
        Box(Modifier.width(width * count + 24.dp).clip(T.dock).background(T.surface).padding(12.dp).height(66.dp).testTag("home-dock")) {
            repeat(count) { index ->
                Box(Modifier.offset(x = width * index).width(width).fillMaxHeight().testTag("dock-slot-$index").onGloballyPositioned { if (active) drag.dockSlots[index] = it.boundsInRoot() }
                    .background(if (drag.app != null && drag.hoverDock == index) T.amber.copy(alpha = 0.18f) else Color.Transparent, T.appTile))
            }
            items.forEachIndexed { index, item -> key(item.key) {
                val position by animateIntOffsetAsState(IntOffset(with(density) { (width * index).roundToPx() }, 0), spring(stiffness = 550f), label = "Dock icon position")
                val cell = Modifier.offset { position }.width(width)
                val app = item.app
                fun remove() { model.saveDockOrder(items.map { it.key }.filterIndexed { i, _ -> i != index }) }
                fun launch() { model.action {
                    if (app != null && (item.key == "@phone" || item.intent == null)) model.launch(app)
                    else if (item.intent != null) model.device.open(item.intent)
                    else model.message("App unavailable. Drag another app into the dock.")
                } }
                if (app != null) DragAppCell(app, drag, false, ::launch, ::remove, cell,
                    extraActions = listOf(
                        CustomAccessibilityAction("Remove from dock") { remove(); true },
                        CustomAccessibilityAction("Move earlier in dock") { model.saveDockOrder(placeDockApp(items.map { it.key }, item.key, index, (index - 1).coerceAtLeast(0))); true },
                        CustomAccessibilityAction("Move later in dock") { model.saveDockOrder(placeDockApp(items.map { it.key }, item.key, index, (index + 1).coerceAtMost(items.lastIndex))); true }
                    ), dockIndex = index, labels = false, homePageId = pageId)
                else {
                    val placeholder = com.example.launcher.data.InstalledApp(android.content.ComponentName("com.example.launcher.dock", "Unavailable.${item.key.removePrefix("@")}"), item.label, appIconStyle(item.key, item.label))
                    DragAppCell(placeholder, drag, false, ::launch, ::remove, cell,
                        extraActions = listOf(CustomAccessibilityAction("Remove from dock") { remove(); true }), dockIndex = index, labels = false, homePageId = pageId)
                }
            } }
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Glyph("plus", T.muted) }
        }
    }
}
