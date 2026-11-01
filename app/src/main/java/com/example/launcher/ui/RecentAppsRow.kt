package com.example.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.launcher.data.InstalledApp
import com.example.launcher.ui.theme.Tokens as T

@Composable fun RecentAppsRow(apps: List<InstalledApp>, drag: LauncherDragState, launch: (InstalledApp) -> Unit, options: (InstalledApp) -> Unit) {
    if (apps.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("recent-apps-section")) {
        Label("NEW APPS")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            apps.take(4).forEach { app -> key(app.component) { DragAppCell(app, drag, true, { launch(app) }, { options(app) }, Modifier.weight(1f), hitNamespace = "recent") } }
            repeat((4 - apps.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp).testTag("recent-apps-divider"), color = T.dotInactive.copy(alpha = 0.4f))
    }
}

@Composable fun FavoriteAppsRow(apps: List<InstalledApp>, drag: LauncherDragState, launch: (InstalledApp) -> Unit, options: (InstalledApp) -> Unit) {
    if (apps.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("favorite-apps-section")) {
        val cellWidth = (maxWidth - 6.dp) / 4
        Column {
            Label("FAVORITES")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = drag.app == null).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                apps.forEach { app -> key(app.component) {
                    DragAppCell(app, drag, true, { launch(app) }, { options(app) }, Modifier.width(cellWidth), hitNamespace = "favorite")
                } }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp).testTag("favorite-apps-divider"), color = T.dotInactive.copy(alpha = 0.4f))
        }
    }
}
