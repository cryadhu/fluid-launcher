@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.launcher.*
import com.example.launcher.data.*
import com.example.launcher.ui.theme.*
import com.example.launcher.ui.theme.Tokens as T

@Composable fun HomeSettings(state: LauncherUiState, model: LauncherViewModel, modifier: Modifier) {
    val config = state.customization; val p = config.preferences
    var tab by remember { mutableStateOf("Appearance") }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) model.setPhotoWallpaper(uri) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Appearance", "Layout and behavior").forEach { title -> FilterChip(tab == title, { tab = title }, { Text(title, style = T.caption) }, colors = FilterChipDefaults.filterChipColors(containerColor = T.surface, selectedContainerColor = T.surfaceRaised, selectedLabelColor = T.amber, labelColor = T.muted)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("home-settings-scroll").padding(top = 10.dp, bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (tab == "Appearance") {
                CardBlock {
                    Label("WALLPAPER")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Tonal" to "Forest", "Slate" to "Midnight", "Paper" to "Paper", "Photo" to "Photo").forEach { (label, value) ->
                            val selected = config.wallpaper == value || value == "Photo" && config.wallpaper.startsWith("Photo:")
                            Column(Modifier.weight(1f).clickable {
                                if (value == "Photo") photo.launch("image/*") else {
                                    model.setAppearanceMode(if (value == "Paper") "Light" else "Dark")
                                    model.setWallpaper(value)
                                }
                            }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp)).background(if (value == "Photo") T.surfaceRaised else Color(wallpaperColor(value)))
                                    .border(if (selected) 2.dp else 1.dp, if (selected) T.amber else T.dotInactive, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                                    if (value == "Photo") Glyph("photos", T.muted)
                                }
                                Text(label, style = T.caption, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                CardBlock {
                    Label("THEME")
                    val dark = androidx.compose.foundation.isSystemInDarkTheme()
                    val mode = if (config.followSystemTheme) "Auto" else if (resolvedTheme(config,dark).dark) "Dark" else "Light"
                    Row(Modifier.fillMaxWidth().clip(T.searchPill).background(T.surfaceRaised).padding(4.dp)) {
                        listOf("Light", "Dark", "Auto").forEach { name -> Box(Modifier.weight(1f).height(42.dp).clip(T.searchPill).background(if (name == mode) T.amber else Color.Transparent).clickable { model.setAppearanceMode(name) }, contentAlignment = Alignment.Center) {
                            Text(name, style = T.caption, color = if (name == mode) T.onAccent else T.text, fontWeight = FontWeight.Bold)
                        } }
                    }
                    TextButton(onClick = { model.showHomePanel(HomePanel.THEMES) }) { Text("All themes and custom colors", color = T.amber) }
                }
                CardBlock {
                    Label("ACCENT COLOR")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(0xFFF2B84B,0xFF7FD6B8,0xFFF08A6B,0xFF8EC5F0,0xFFB4C3BE).forEach { raw ->
                            val color = raw.toInt(); val chosen = (p.accentColor ?: T.amber.toArgb()) == color
                            Box(Modifier.size(38.dp).border(if (chosen) 2.dp else 0.dp, if (chosen) T.text else Color.Transparent, CircleShape).padding(4.dp).clip(CircleShape).background(Color(color)).clickable { model.updateHomePreferences(p.copy(accentColor = color)) }, contentAlignment = Alignment.Center) {
                                if (chosen) Glyph("check", Color(0xFF15201D), Modifier.size(18.dp))
                            }
                        }
                    }
                }
                CardBlock {
                    Label("ICONS")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Squircle", "Circle", "Square").forEach { name -> Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, if (p.iconShape == name) T.amber else T.surfaceRaised, RoundedCornerShape(16.dp)).clickable { model.updateHomePreferences(p.copy(iconShape = name)) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val shape = when (name) { "Circle" -> CircleShape; "Square" -> RoundedCornerShape(4.dp); else -> RoundedCornerShape(12.dp) }
                            Box(Modifier.size(38.dp).background(T.surfaceRaised,shape), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).background(T.amber,CircleShape)) }
                            Text(name, style = T.caption.copy(fontSize = 10.sp), modifier = Modifier.padding(top = 6.dp))
                        } }
                    }
                    var size by remember(p.iconSize) { mutableFloatStateOf(p.iconSize.toFloat()) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Icon size", style = T.body); Text("${size.toInt()}", style = T.caption) }
                    Slider(size, { size = it }, valueRange = 44f..68f, onValueChangeFinished = { model.updateHomePreferences(p.copy(iconSize = size.toInt())) })
                    SettingsToggle("Show app labels", p.showLabels) { model.updateHomePreferences(p.copy(showLabels = it)) }
                }
            } else {
                CardBlock {
                    Label("LAYOUT")
                    SettingsChoice("Grid size", "4 × ${p.gridRows}", listOf("4 × 2","4 × 3","4 × 4")) { model.updateHomePreferences(p.copy(gridRows = it.last().digitToInt())) }
                    SettingsChoice("Dock apps", if (p.dockLimit == 0) "Off" else "Up to ${p.dockLimit}", listOf("Off","1","2","3","4","5")) { model.updateHomePreferences(p.copy(dockLimit = it.toIntOrNull() ?: 0)) }
                    SettingsChoice("Search bar", p.searchBar, listOf("Off","Top","Bottom")) { model.updateHomePreferences(p.copy(searchBar = it)) }
                    SettingsChoice("Clock style", p.clockStyle, listOf("Large","Small","Hidden")) { model.updateHomePreferences(p.copy(clockStyle = it)) }
                }
                CardBlock {
                    Label("PAGES")
                    listOf(0 to "Feed page", 2 to "Today page", 3 to "Tools page").forEach { (id,name) -> SettingsToggle(name, visiblePages(config).any { it.id == id }) { model.setPageVisible(id,it) } }
                }
                CardBlock {
                    Label("GESTURES AND BADGES")
                    SettingsToggle("Swipe up for app drawer", p.swipeUp) { model.updateHomePreferences(p.copy(swipeUp = it)) }
                    if (!p.swipeUp) Text("Use the Home search bar or the App drawer button below.", style = T.caption)
                    TextButton(onClick = { model.showHomePanel(null); model.drawer(true) }) { Text("Open app drawer", color = T.amber) }
                    SettingsToggle("Double-tap to lock screen", p.doubleTapLock) {
                        if (android.os.Build.VERSION.SDK_INT < 28) model.message("Double-tap lock requires Android 9 or later")
                        else { model.updateHomePreferences(p.copy(doubleTapLock = it)); if (it && !LauncherLockService.available()) model.openLockAccess() }
                    }
                    if (p.doubleTapLock && !LauncherLockService.available()) TextButton(onClick = model::openLockAccess) { Text("Enable Double-tap lock in Accessibility", color = T.amber) }
                    SettingsToggle("Notification dots", p.notificationDots) { model.updateHomePreferences(p.copy(notificationDots = it)); if (it && !state.device.mediaAccess) model.openNotificationAccess() }
                    if (p.notificationDots && !state.device.mediaAccess) TextButton(onClick = model::openNotificationAccess) { Text("Enable notification access", color = T.amber) }
                }
            }
            TextButton(onClick = { model.action { model.device.open(Intent(Settings.ACTION_SETTINGS)) } }, modifier = Modifier.fillMaxWidth()) { Text("Android settings", color = T.amber) }
        }
    }
}

@Composable private fun SettingsToggle(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.body.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        Switch(checked, change, modifier = Modifier.testTag("setting-$title"), colors = SwitchDefaults.colors(checkedTrackColor = T.amber, checkedThumbColor = T.onAccent, uncheckedThumbColor = T.muted, uncheckedTrackColor = T.surfaceRaised))
    }
}
@Composable private fun SettingsChoice(title: String, value: String, options: List<String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = true }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = T.body.copy(fontSize = 13.sp), modifier = Modifier.weight(1f)); Text(value, style = T.caption); Glyph("next", T.muted, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; change(choice) }) } }
    }
}
