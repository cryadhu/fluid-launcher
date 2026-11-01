package com.example.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.launcher.ui.theme.Tokens as T

@Composable fun LauncherSearch(query: String, onQuery: (String) -> Unit, opened: Boolean, focusSearch: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val manager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(opened, focusSearch) {
        if (opened && focusSearch) { focus.requestFocus(); keyboard?.show() }
        else if (!opened) { manager.clearFocus(); keyboard?.hide() }
    }
    Box(modifier.height(52.dp).clip(T.searchPill).background(T.surface).clickable(enabled = !opened, role = Role.Button, onClick = onOpen), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = query, onValueChange = onQuery, enabled = opened, readOnly = !opened, singleLine = true,
            textStyle = T.body, cursorBrush = SolidColor(T.amber),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).focusProperties { canFocus = opened }.semantics { contentDescription = "Search apps, web, contacts" },
            decorationBox = { input ->
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = if (query.isEmpty()) 20.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("search", T.amber, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { if (query.isEmpty()) Text("Search apps", style = T.subtitle); input() }
                    if (query.isNotEmpty()) IconAction("close", "Clear search") { onQuery("") }
                }
            }
        )
    }
}
