package com.example.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.CancellationException

/** Consume release in Initial, before a child clickable can mistake a motionless hold for a tap. */
internal suspend fun PointerInputScope.detectLauncherLongPress(
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
    onDragStart(held.position)
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == held.id }
            if (change == null) { onDragCancel(); break }
            change.consume()
            if (!change.pressed) { onDragEnd(); break }
            onDrag(change, change.position - change.previousPosition)
        }
    } catch (cancelled: CancellationException) {
        onDragCancel()
        throw cancelled
    }
}
