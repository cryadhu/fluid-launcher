package com.example.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.launcher.data.NewsCategory
import com.example.launcher.ui.theme.Tokens as T

@Composable fun NewsSettingsScreen(selected: Set<NewsCategory>, onSelect: (Set<NewsCategory>) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(T.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction("back", "Back to news", onClick = onBack)
            Text("News preferences", style = T.pageTitle, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Text("Choose the topics you want in Top stories. Your choices save automatically.", style = T.subtitle)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelect(NewsCategory.all) }) { Text("Select all", color = T.amber) }
            TextButton(onClick = { onSelect(emptySet()) }) { Text("Clear all", color = T.amber) }
        }
        NewsCategory.entries.forEachIndexed { index, category ->
            val checked = category in selected
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(T.surface, T.appTile)
                .toggleable(checked, role = Role.Checkbox, onValueChange = { onSelect(if (it) selected + category else selected - category) })
                .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Glyph(category.glyph, listOf(T.mint, T.coral, T.sky, T.amber)[index % 4])
                Text(category.label, style = T.body, modifier = Modifier.weight(1f))
                Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = T.amber, checkmarkColor = T.onAccent, uncheckedColor = T.muted))
            }
        }
        if (selected.isEmpty()) Text("No topics selected. Top stories will stay empty until you select a topic.", style = T.caption)
    }
}
