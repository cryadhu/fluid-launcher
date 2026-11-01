package com.example.launcher.ui

import android.content.pm.ApplicationInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.example.launcher.ui.theme.Tokens as T
import kotlin.math.roundToInt

/** In-window menu keeps the original long-press pointer stream alive for hold-and-drag. */
@Composable fun DrawerAppMenu(hit: AppHit, dismiss: () -> Unit, onHome: Boolean, pin: () -> Unit, appInfo: () -> Unit, uninstall: () -> Unit, favorite: Boolean, toggleFavorite: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val removable = remember(hit.app.component) {
        runCatching { context.packageManager.getApplicationInfo(hit.app.component.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0 }.getOrDefault(false)
    }
    var appeared by remember(hit.app.component) { mutableStateOf(false) }
    LaunchedEffect(hit.app.component) { appeared = true }
    val progress by animateFloatAsState(if (appeared) 1f else 0f, label = "App options appear")
    BoxWithConstraints(Modifier.fillMaxSize().testTag("drawer-app-menu-overlay")
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss)) {
        val width = 224.dp
        val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
        val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
        val x = with(density) { (hit.rect.center.x.toDp() - width / 2).coerceIn(8.dp, (maxWidth - width - 8.dp).coerceAtLeast(8.dp)) }
        val y = with(density) { (hit.rect.bottom.toDp() + 6.dp).coerceIn(safeTop, (maxHeight - safeBottom - 280.dp).coerceAtLeast(safeTop)) }
        Column(Modifier.offset { with(density) { IntOffset(x.toPx().roundToInt(), y.toPx().roundToInt()) } }
            .width(width).graphicsLayer { alpha = progress; scaleX = 0.94f + 0.06f * progress; scaleY = scaleX }
            .clip(T.widgetCard).background(T.surfaceRaised).testTag("drawer-app-menu")
            .semantics { paneTitle = "App options"; isTraversalGroup = true }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(vertical = 8.dp)) {
            Text(hit.app.label, style = T.body, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), maxLines = 1)
            MenuAction("plus", if (onHome) "Already on Home" else "Add to Home", !onHome, pin)
            MenuAction("plus", if (favorite) "Remove from favorites" else "Add to favorite", true, toggleFavorite)
            MenuAction("settings", "App info", true, appInfo)
            MenuAction("bin", "Uninstall", removable, uninstall)
        }
    }
}

@Composable private fun MenuAction(glyph: String, label: String, enabled: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = action)
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Glyph(glyph, if (enabled) T.mint else T.dotInactive, Modifier.size(20.dp))
        Text(label, style = T.body, color = if (enabled) T.text else T.dotInactive)
    }
}
