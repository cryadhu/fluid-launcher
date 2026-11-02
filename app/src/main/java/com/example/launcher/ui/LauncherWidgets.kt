@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.launcher.data.InstalledApp
import com.example.launcher.data.AppIconStyle
import com.example.launcher.data.AppAccent
import com.example.launcher.ui.theme.Tokens as T

internal val iconPaths = mapOf(
    "bin" to "M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7",
    "edit" to "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4zM14.5 5.5l3 3",
    "airplane" to "M22 2L9 9l-6-2-1 2 6 4 4 6 2-1-2-6zM9 15l-3 3-3-1-1 1 4 4 1-1-1-3 3-3",
    "power" to "M12 3v9M6 5a9 9 0 1 0 12 0",
    "mobile" to "M7 3v18M3 7l4-4 4 4M17 21V3M13 17l4 4 4-4",
    "vibrate" to "M8 4h8v16H8zM4 7v10M1 9v6M20 7v10M23 9v6",
    "scan" to "M4 9V5a1 1 0 0 1 1-1h4M15 4h4a1 1 0 0 1 1 1v4M20 15v4a1 1 0 0 1-1 1h-4M9 20H5a1 1 0 0 1-1-1v-4M4 12h16",
    "translate" to "M3 5h11M8 3v2M5 5c0 5 3 8 7 10M12 5c-1 5-4 8-9 10M13 21l4-10 4 10M15 17h4",
    "qr" to "M3 3h6v6H3zM15 3h6v6h-6zM3 15h6v6H3zM15 15h2v2h-2zM20 14v3M14 20h3M20 20h1v1h-1z",
    "screenshot" to "M8 3H4v5M16 3h4v5M20 16v4h-4M8 20H4v-4M9 8h6v8H9z",
    "phone" to "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 14l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z",
    "messages" to "M4 5h16v11H9l-5 4z",
    "camera" to "M3 8a2 2 0 0 1 2-2h2l1.5-2h7L17 6h2a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM12 17a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z",
    "browser" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM15.5 8.5l-2 5-5 2 2-5z",
    "music" to "M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
    "search" to "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4",
    "back" to "M19 12H5M12 5l-7 7 7 7",
    "wifi" to "M2 9a15 15 0 0 1 20 0M5.5 12.5a10 10 0 0 1 13 0M9 16a5 5 0 0 1 6 0M12 19.5h.01",
    "bluetooth" to "M7 8l10 8-5 4V4l5 4-10 8",
    "flashlight" to "M8 3h8v5l-2 3v10h-4V11L8 8zM8 6h8",
    "silent" to "M6 17v-6a6 6 0 0 1 12 0v6l2 2H4zM10 21h4",
    "mic" to "M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zM6 11a6 6 0 0 0 12 0M12 17v4",
    "plus" to "M12 5v14M5 12h14",
    "up" to "M6 15l6-6 6 6",
    "previous" to "M19 5v14L9 12zM6 5v14",
    "next" to "M5 5v14l10-7zM18 5v14",
    "play" to "M8 5v14l11-7z",
    "pause" to "M8 5v14M16 5v14",
    "close" to "M6 6l12 12M6 18L18 6",
    "check" to "M4 12l5 5L20 6",
    "home" to "M3 10l9-7 9 7M5 9v12h5v-7h4v7h5V9",
    "settings" to "M4 7h9M17 7h3M4 17h3M11 17h9M15 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM9 15a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
    "news" to "M4 4h16v16H4zM8 8h8M8 12h8M8 16h5",
    "photos" to "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM3 17l5-5 4 4 3-3 6 6",
    "maps" to "M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
    "mail" to "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 7l9 6 9-6",
    "calendar" to "M6 4h12a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM4 10h16M9 2v4M15 2v4",
    "clock" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2",
    "notes" to "M6 3h9l4 4v14H6zM15 3v4h4M9 12h7M9 16h5",
    "files" to "M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    "wallet" to "M3 7a2 2 0 0 1 2-2h13v4M3 7v11a2 2 0 0 0 2 2h14V9H5a2 2 0 0 1-2-2zM16 14.5h.01",
    "contacts" to "M15.5 8a3.5 3.5 0 1 0-7 0 3.5 3.5 0 0 0 7 0zM5 21v-2a7 7 0 0 1 14 0v2",
    "cloud" to "M6 18a4 4 0 0 1-1-7.9A7 7 0 0 1 18.5 8a5 5 0 0 1-.5 10z",
    "video" to "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM10 8l6 4-6 4z",
    "tasks" to "M9 6h11M9 12h11M9 18h11M3 5l1 1 2-2M3 11l1 1 2-2M3 17l1 1 2-2",
    "calculator" to "M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM8 7h8M8 12h1M15 12h1M8 16h1M15 16h1",
    "weather" to "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5",
    "health" to "M20.5 5.5a5 5 0 0 0-7 0L12 7l-1.5-1.5a5 5 0 0 0-7 7L12 21l8.5-8.5a5 5 0 0 0 0-7zM3 12h5l2-4 3 8 2-4h6",
    "book" to "M12 5C9 3 5 3 3 4v16c2-1 6-1 9 1 3-2 7-2 9-1V4c-2-1-6-1-9 1zM12 5v16",
    "social" to "M15 5a3 3 0 1 0 6 0 3 3 0 0 0-6 0zM3 12a3 3 0 1 0 6 0 3 3 0 0 0-6 0zM15 19a3 3 0 1 0 6 0 3 3 0 0 0-6 0zM9 10.5l6-4M9 13.5l6 4",
    "shopping" to "M5 7h14l2 14H3zM8 8V6a4 4 0 0 1 8 0v2",
    "transport" to "M5 11l2-7h10l2 7M4 11h16v8H4zM6 19v2M18 19v2M7 15h1M16 15h1",
    "travel" to "M21 3l-6 18-4-8-8-4zM11 13L21 3",
    "food" to "M5 3v6a3 3 0 0 0 6 0V3M8 3v18M19 3c-3 3-3 8 0 9v9M19 3v9",
    "game" to "M7 7h10a4 4 0 0 1 4 3l1 7a3 3 0 0 1-5 2l-2-2H9l-2 2a3 3 0 0 1-5-2l1-7a4 4 0 0 1 4-3zM6 12h4M8 10v4M16 11h.01M18 14h.01",
    "shield" to "M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6zM8 12l3 3 5-6",
    "translate" to "M3 5h12M9 3v2M5 5c0 5 4 8 8 10M13 5c0 5-4 8-9 10M13 21l4-10 4 10M14.5 18h5",
    "document" to "M6 3h9l4 4v14H6zM15 3v4h4M9 11h7M9 15h7M9 18h4",
    "code" to "M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16",
    "app" to "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"
)
@Composable fun Glyph(name: String, color: Color = T.muted, modifier: Modifier = Modifier.size(24.dp)) {
    val vector = remember(name) { ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(iconPaths[name] ?: iconPaths.getValue("app")).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build() }
    Icon(vector, contentDescription = null, tint = color, modifier = modifier)
}
@Composable fun ThemedAppIcon(style: AppIconStyle, modifier: Modifier = Modifier.size(26.dp)) {
    val color = when (style.accent) { AppAccent.AMBER -> T.amber; AppAccent.MINT -> T.mint; AppAccent.CORAL -> T.coral; AppAccent.SKY -> T.sky }
    Glyph(style.glyph, color, modifier)
}
@Composable fun AppArtwork(app: InstalledApp, modifier: Modifier = Modifier) {
    val artwork = app.artwork
    val scale = T.preferences.iconSize / 58f
    if (artwork != null) {
        Image(artwork.asImageBitmap(), contentDescription = null,
            modifier = modifier.size(36.dp * scale).testTag("installed-icon:${app.component.flattenToString()}"))
    } else ThemedAppIcon(app.icon, modifier.size(26.dp * scale))
}
@Composable fun IconAction(name: String, label: String, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(if (accent) T.amber else Color.Transparent).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Glyph(name, if (!enabled) T.dotInactive else if (accent) T.onAccent else T.text)
    }
}
@Composable fun Label(text: String) { Text(text, style = T.sectionLabel) }
@Composable fun CardBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(T.widgetCard).background(T.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
@Composable fun ActionPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Box(modifier.heightIn(min = 48.dp).clip(T.searchPill).background(if (primary) T.amber else T.surfaceRaised).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.body.copy(fontWeight = FontWeight.Bold, color = if (primary) T.onAccent else T.text))
    }
}
@Composable fun AppCell(app: InstalledApp, onClick: () -> Unit, onLongClick: () -> Unit, labels: Boolean = true, modifier: Modifier = Modifier, extraActions: List<CustomAccessibilityAction> = emptyList(), longPressLabel: String = "Pin or remove from Home") {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick, role = Role.Button).padding(vertical = 4.dp).semantics(mergeDescendants = true) {
        contentDescription = app.label
        if (T.preferences.notificationDots && app.component.packageName in T.badgePackages) stateDescription = "New notifications"
        onLongClick(label = longPressLabel) { onLongClick(); true }
        customActions = listOf(CustomAccessibilityAction(longPressLabel) { onLongClick(); true }) + extraActions
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(T.preferences.iconSize.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(T.iconShape).background(if (labels) T.surface else T.surfaceRaised), contentAlignment = Alignment.Center) { AppArtwork(app) }
            if (T.preferences.notificationDots && app.component.packageName in T.badgePackages) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).background(T.notificationDot, CircleShape).testTag("notification-dot:${app.component.flattenToString()}"))
        }
        if (labels && T.preferences.showLabels) { Spacer(Modifier.height(8.dp)); Text(app.label, style = T.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
@Composable fun DraftField(value: String, placeholder: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    TextField(value = value, onValueChange = onValue, modifier = modifier.fillMaxWidth(), placeholder = { Text(placeholder, style = T.subtitle) }, textStyle = T.body, singleLine = singleLine,
        colors = TextFieldDefaults.colors(focusedContainerColor = T.surfaceRaised, unfocusedContainerColor = T.surfaceRaised, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = T.amber), shape = T.searchPill)
}

@Composable fun DragAppCell(app: InstalledApp, drag: LauncherDragState, drawer: Boolean, onClick: () -> Unit, onPin: () -> Unit, modifier: Modifier = Modifier, extraActions: List<CustomAccessibilityAction> = emptyList(), dockIndex: Int = -1, labels: Boolean = true, homePageId: Int = 1, hitNamespace: String = "") {
    val key = "${if (drawer) "drawer" + if (hitNamespace.isEmpty()) "" else ":$hitNamespace" else if (dockIndex >= 0) "dock:$homePageId:$dockIndex" else "home:$homePageId"}:${app.component.flattenToString()}"
    DisposableEffect(key) { onDispose { drag.hits.remove(key) } }
    AppCell(app, onClick, onPin, labels = labels, modifier = modifier.onGloballyPositioned { drag.hits[key] = AppHit(app, it.boundsInRoot(), drawer, dockIndex) }.graphicsLayer {
        val moving = if (dockIndex >= 0) drag.app != null && drag.fromDockIndex == dockIndex else drag.app?.component == app.component
        alpha = if (moving && !drawer) 0.18f else 1f
    }, extraActions = extraActions, longPressLabel = if (drawer) "App options" else if (dockIndex >= 0) "Remove from dock" else "Pin or remove from Home")
}
