package com.example.launcher.fluid

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

/** Consecutive half widgets pair up in one row; every other widget takes a full row. */
internal fun widgetRows(widgets: List<FluidWidget>, half: (FluidWidget) -> Boolean): List<List<FluidWidget>> {
    val rows = mutableListOf<List<FluidWidget>>()
    var open: FluidWidget? = null
    widgets.forEach { w ->
        val pending = open
        when {
            half(w) && pending != null -> { rows += listOf(pending, w); open = null }
            half(w) -> open = w
            else -> { if (pending != null) rows += listOf(pending); open = null; rows += listOf(w) }
        }
    }
    open?.let { rows += listOf(it) }
    return rows
}

/** A 4-column page grid: 2×2 widgets sit side by side, wider widgets span the row. */
@Composable
internal fun WidgetGrid(widgets: List<FluidWidget>, config: FluidConfig, edit: PageEdit?, spacing: Dp, phase: State<Float>, card: @Composable (FluidWidget) -> Unit) {
    val rows = widgetRows(widgets) { isHalf(it, config) }
    Column(Modifier.fillMaxWidth().onSizeChanged { edit?.gridWidth = it.width.toFloat() }, verticalArrangement = Arrangement.spacedBy(spacing)) {
        rows.forEach { row ->
            val lifted = edit != null && row.any { it == edit.settling || it == edit.dragging }
            Row(Modifier.fillMaxWidth().zIndex(if (lifted) 2f else 0f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { kind -> key(kind) {
                    Box(Modifier.weight(1f).pageEditCard(kind, edit, phase, widgets.indexOf(kind))) {
                        card(kind)
                        if (edit != null && edit.active) Box(Modifier.matchParentSize().pageEditDrag(kind, edit))
                    }
                } }
                if (row.size == 1 && isHalf(row[0], config)) Spacer(Modifier.weight(1f))
            }
        }
    }
}
