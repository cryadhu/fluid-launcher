@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.launcher.LauncherViewModel
import com.example.launcher.data.HomeCustomization
import com.example.launcher.ui.theme.*
import com.example.launcher.ui.theme.Tokens as T

@Composable fun ThemePicker(config: HomeCustomization, model: LauncherViewModel, modifier: Modifier) {
    var custom by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pick a color theme", style = T.caption)
        listOf(listOf("Tonal", "Solar"), listOf("Ember", "Sand")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { name ->
                val palette = themePalette(name)
                val selected = !config.followSystemTheme && config.theme == name
                Column(Modifier.weight(1f).testTag("theme-$name").clickable { model.selectTheme(name) }) {
                    Column(Modifier.fillMaxWidth().aspectRatio(0.9f).clip(T.appTile).background(palette.ground).border(if (selected) 2.dp else 1.dp, if (selected) T.amber else palette.raised, T.appTile).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("9:41", style = T.pageTitle.copy(color = palette.text, fontSize = 28.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(palette.amber, palette.mint, palette.coral, palette.sky).forEach { color -> Box(Modifier.size(11.dp).background(color, CircleShape)) } }
                        Box(Modifier.fillMaxWidth().height(18.dp).clip(T.searchPill).background(palette.surface))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, style = T.body, fontWeight = FontWeight.Bold); if (selected) Glyph("check", T.amber, Modifier.size(18.dp)) }
                    Text(if (palette.dark) "Dark" else "Light", style = T.caption)
                }
            } }
        }
        Row(Modifier.fillMaxWidth().clip(T.appTile).border(1.dp, T.dotInactive, T.appTile).clickable { custom = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph("plus", T.amber)
            Column { Text(if (config.theme == "Custom") "Edit custom theme ✓" else "Create custom theme", style = T.body); Text("Choose your background and accent", style = T.caption) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Follow system light/dark", style = T.body, modifier = Modifier.weight(1f))
            Switch(config.followSystemTheme, model::followSystemTheme, modifier = Modifier.testTag("follow-system-theme"), colors = SwitchDefaults.colors(checkedTrackColor = T.amber, checkedThumbColor = T.onAccent, uncheckedTrackColor = T.surfaceRaised, uncheckedThumbColor = T.muted))
        }
    }
    if (custom) {
        var background by remember { mutableStateOf("#%06X".format(config.customBackground and 0xFFFFFF)) }
        var accent by remember { mutableStateOf("#%06X".format(config.customAccent and 0xFFFFFF)) }
        var error by remember { mutableStateOf(false) }
        val bg = parseThemeColor(background); val fg = parseThemeColor(accent)
        AlertDialog(onDismissRequest = { custom = false }, containerColor = T.surface,
            title = { Text("Create custom theme", color = T.text) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(background, { background = it; error = false }, label = { Text("Background · #RRGGBB") }, singleLine = true)
                OutlinedTextField(accent, { accent = it; error = false }, label = { Text("Accent · #RRGGBB") }, singleLine = true)
                if (bg != null && fg != null) Row(Modifier.fillMaxWidth().height(56.dp).clip(T.appTile).background(Color(bg)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(24.dp).background(Color(fg), CircleShape)) }
                if (error) Text("Enter colors as #RRGGBB", color = T.coral, style = T.caption)
            } },
            confirmButton = { TextButton(onClick = { if (bg != null && fg != null) { model.saveCustomTheme(bg, fg); custom = false } else error = true }) { Text("Apply", color = T.amber) } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel", color = T.muted) } })
    }
}
internal fun parseThemeColor(value: String): Int? = value.trim().removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLong(16)?.toInt()?.or(0xFF000000.toInt())
