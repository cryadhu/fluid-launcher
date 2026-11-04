package com.example.launcher.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/** A page that can receive a dragged widget. [page] is null when the drag targets a new page. */
internal data class PageDrop(val page: String?, val direction: Int, val create: Boolean)

internal fun pageSupportsWidgets(page: String) = page != "Markets"

/** In-page rearrangement state. Widget cards stay in the real page layout, the dragged card
 * follows the finger, and every displaced card is FLIP-animated into its new slot. */
@Stable
internal class PageEdit(val page: String, private val model: FluidModel, private val scope: kotlinx.coroutines.CoroutineScope, private val onActivated: () -> Unit = {}, private val onPageDropped: (String) -> Unit = {}) {
    var active by mutableStateOf(false)
        private set
    var dragging by mutableStateOf<FluidWidget?>(null)
        private set
    var drag by mutableStateOf(Offset.Zero)
        private set
    var settling by mutableStateOf<FluidWidget?>(null)
        private set
    var settle by mutableStateOf<Animatable<Offset, AnimationVector2D>?>(null)
        private set
    var removing by mutableStateOf<FluidWidget?>(null)
        private set
    val removeProgress = Animatable(1f)
    var overRemove by mutableStateOf(false)
        private set
    var dropTarget by mutableStateOf<PageDrop?>(null)
        private set
    var removeBounds by mutableStateOf(Rect.Zero)
    var viewport by mutableStateOf(Rect.Zero)
    val heights = mutableStateMapOf<FluidWidget, Float>()
    val cards = mutableStateMapOf<FluidWidget, Rect>()
    val flips = mutableStateMapOf<FluidWidget, Animatable<Float, AnimationVector1D>>()

    private val flipJobs = mutableMapOf<FluidWidget, Job>()
    private var settleJob: Job? = null
    private var visible: List<FluidWidget> = emptyList()
    private var spacingPx = 0f
    private var scroll: ScrollState? = null
    private var pointer = Offset.Zero
    private var autoScroll: Job? = null
    private var autoDirection = 0
    private var edgeJob: Job? = null
    private var edgeCandidate: PageDrop? = null

    fun bind(widgets: List<FluidWidget>, spacing: Float, scrollState: ScrollState) {
        visible = widgets
        spacingPx = spacing
        scroll = scrollState
    }

    fun enter() {
        if (active) return
        active = true
        onActivated()
    }

    fun activate(kind: FluidWidget, local: Offset) {
        if (active && dragging != null) return
        if (!active) { active = true; onActivated() }
        begin(kind, local)
    }

    fun begin(kind: FluidWidget, local: Offset) {
        if (!active || removing != null) return
        stopAutoScroll()
        cancelEdge()
        // Re-grabbing a card while it is still settling starts from the position the
        // user can see, so the card never jumps back to its slot under the finger.
        val carrying = if (settling == kind) settle?.value ?: Offset.Zero else Offset.Zero
        if (settling == kind) {
            settleJob?.cancel()
            settleJob = null
            settling = null
            settle = null
        }
        dragging = kind
        drag = carrying
        overRemove = false
        val rect = cards[kind] ?: Rect.Zero
        pointer = rect.topLeft + carrying + local
    }

    fun dragBy(delta: Offset) {
        val kind = dragging ?: return
        drag += delta
        pointer += delta
        overRemove = removeBounds.width > 0f && removeBounds.contains(pointer)
        maybeSwap(kind)
        autoScrollIfNeeded()
        updatePageDrop()
    }

    fun end() {
        stopAutoScroll()
        val kind = dragging ?: return
        val drop = dropTarget
        cancelEdge()
        dragging = null
        if (overRemove) {
            remove(kind)
            return
        }
        if (drop != null) {
            val target = drop.page ?: model.addPage()
            model.moveWidgetToPage(page, target, kind)
            flipJobs.remove(kind)?.cancel()
            heights.remove(kind)
            cards.remove(kind)
            flips.remove(kind)
            drag = Offset.Zero
            overRemove = false
            onPageDropped(target)
            return
        }
        val anim = Animatable(drag, Offset.VectorConverter)
        settleJob?.cancel()
        settle = anim
        settling = kind
        drag = Offset.Zero
        overRemove = false
        settleJob = scope.launch {
            anim.animateTo(Offset.Zero, spring(dampingRatio = .82f, stiffness = 760f))
            if (settle === anim) { settle = null; settling = null; settleJob = null }
        }
    }

    fun close() {
        if (!active) return
        stopAutoScroll()
        cancelEdge()
        settleJob?.cancel()
        settleJob = null
        active = false
        dragging = null
        settling = null
        settle = null
        drag = Offset.Zero
        overRemove = false
    }

    private fun remove(kind: FluidWidget) {
        dragging = null
        drag = Offset.Zero
        settling = null
        settle = null
        overRemove = false
        removing = kind
        scope.launch {
            removeProgress.snapTo(1f)
            removeProgress.animateTo(0f, tween(170))
            val old = visible
            val new = old - kind
            shift(old, new, kind)
            visible = new
            model.removeWidgetForPage(page, kind)
            heights.remove(kind)
            cards.remove(kind)
            flips.remove(kind)
            removing = null
            removeProgress.snapTo(1f)
        }
    }

    private fun tops(list: List<FluidWidget>): Map<FluidWidget, Float> {
        var y = 0f
        return buildMap { list.forEach { w -> put(w, y); y += (heights[w] ?: 0f) + spacingPx } }
    }

    private fun maybeSwap(kind: FluidWidget) {
        repeat(4) {
            val list = visible
            val from = list.indexOf(kind)
            if (from < 0) return
            val current = tops(list)
            val center = (current[kind] ?: 0f) + (heights[kind] ?: 0f) / 2f + drag.y
            val next = list.getOrNull(from + 1)
            val prev = list.getOrNull(from - 1)
            val nextCenter = next?.let { (current[it] ?: 0f) + (heights[it] ?: 0f) / 2f }
            val prevCenter = prev?.let { (current[it] ?: 0f) + (heights[it] ?: 0f) / 2f }
            val pair = when {
                next != null && nextCenter != null && center > nextCenter -> from to from + 1
                prev != null && prevCenter != null && center < prevCenter -> from to from - 1
                else -> return
            }
            val newList = list.toMutableList().apply {
                val moved = this[pair.first]; this[pair.first] = this[pair.second]; this[pair.second] = moved
            }
            val newTops = tops(newList)
            shift(list, newList, kind)
            drag += Offset(0f, (current[kind] ?: 0f) - (newTops[kind] ?: 0f))
            visible = newList
            model.reorderVisibleWidgetsForPage(page, list, newList)
        }
    }

    private fun shift(old: List<FluidWidget>, new: List<FluidWidget>, exclude: FluidWidget?) {
        val oldTops = tops(old)
        val newTops = tops(new)
        new.forEach { w ->
            if (w == exclude) return@forEach
            val delta = (oldTops[w] ?: 0f) - (newTops[w] ?: 0f)
            if (delta == 0f) return@forEach
            val previous = flips[w]?.value ?: 0f
            val anim = Animatable(previous + delta)
            flips[w] = anim
            flipJobs.remove(w)?.cancel()
            flipJobs[w] = scope.launch { anim.animateTo(0f, spring(dampingRatio = .86f, stiffness = 900f)) }
        }
        flips.keys.toList().filterNot { it in new }.forEach { w ->
            flipJobs.remove(w)?.cancel()
            flips.remove(w)
        }
    }

    private fun autoScrollIfNeeded() {
        if (overRemove) { stopAutoScroll(); return }
        if (viewport.height <= 0f) return
        val edge = 140f
        val direction = when {
            pointer.y < viewport.top + edge -> -1
            pointer.y > viewport.bottom - edge -> 1
            else -> 0
        }
        if (direction == autoDirection) return
        autoDirection = direction
        autoScroll?.cancel()
        autoScroll = null
        if (direction == 0) return
        val state = scroll ?: return
        autoScroll = scope.launch {
            while (isActive) {
                val before = state.value
                state.scrollBy(direction * 18f)
                val moved = state.value - before
                if (moved == 0) break
                drag += Offset(0f, moved.toFloat())
                dragging?.let { maybeSwap(it) }
                delay(16)
            }
        }
    }

    private fun stopAutoScroll() {
        autoDirection = 0
        autoScroll?.cancel()
        autoScroll = null
    }

    /** Only a deliberate sideways drag can arm a page drop, so grabbing a card near an edge
     * never steals a normal vertical reorder. The dwell keeps the hint from flashing by. */
    private fun updatePageDrop() {
        val next = if (overRemove) null else computePageDrop()
        if (next == edgeCandidate) return
        edgeCandidate = next
        edgeJob?.cancel()
        edgeJob = null
        if (next == null) { dropTarget = null; return }
        edgeJob = scope.launch {
            delay(300)
            if (edgeCandidate == next) dropTarget = next
        }
    }

    private fun computePageDrop(): PageDrop? {
        val vp = viewport
        if (vp.width <= 0f) return null
        if (abs(drag.x) < vp.width * .065f) return null
        val zone = vp.width * .16f
        val pages = model.state.value.config.pages
        val index = pages.indexOf(page)
        if (index < 0) return null
        if (drag.x < 0f && pointer.x <= vp.left + zone) {
            val previous = pages.getOrNull(index - 1)?.takeIf(::pageSupportsWidgets) ?: return null
            return PageDrop(previous, -1, false)
        }
        if (drag.x > 0f && pointer.x >= vp.right - zone) {
            val next = pages.getOrNull(index + 1)
            return when {
                next == null -> PageDrop(null, 1, true)
                pageSupportsWidgets(next) -> PageDrop(next, 1, false)
                else -> null
            }
        }
        return null
    }

    private fun cancelEdge() {
        edgeJob?.cancel()
        edgeJob = null
        edgeCandidate = null
        dropTarget = null
    }
}

/** Lift, FLIP offsets and per-card measurement all live here so the page keeps its layout. */
@Composable
internal fun Modifier.pageEditCard(kind: FluidWidget, edit: PageEdit?, phase: State<Float>, index: Int): Modifier {
    if (edit == null) return this
    val measured = this.onGloballyPositioned {
        val height = it.size.height.toFloat()
        if (edit.heights[kind] != height) edit.heights[kind] = height
        if (edit.dragging != kind) {
            val rect = it.boundsInRoot()
            if (edit.cards[kind] != rect) edit.cards[kind] = rect
        }
    }
    // This detector only opens the editor. The move stream is claimed below, in the
    // Initial pass, because nested widget controls (text fields, rows, buttons) consume
    // drags and would cancel the gesture that activated the editor.
    val grip = measured.pointerInput(kind) {
        detectDragGesturesAfterLongPress(
            onDragStart = { if (!edit.active) edit.activate(kind, it) },
            onDragCancel = {},
            onDragEnd = {},
            onDrag = { _, _ -> }
        )
    }
    val claim = grip.pointerInput(kind) {
        var pointer: PointerId? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (pointer == null) pointer = event.changes.firstOrNull { !it.previousPressed && it.pressed }?.id
                if (edit.dragging != kind) {
                    if (event.changes.none { it.pressed }) pointer = null
                    continue
                }
                val change = event.changes.firstOrNull { it.id == pointer } ?: continue
                if (!change.pressed) {
                    pointer = null
                    edit.end()
                } else {
                    val delta = change.position - change.previousPosition
                    if (delta != Offset.Zero) {
                        change.consume()
                        edit.dragBy(delta)
                    }
                }
            }
        }
    }
    val flip = edit.flips[kind]
    val settle = if (edit.settling == kind) edit.settle else null
    return claim
        .zIndex(if (edit.dragging == kind || edit.settling == kind || edit.removing == kind) 2f else 0f)
        .graphicsLayer {
            val isDragging = edit.dragging == kind
            val removingThis = edit.removing == kind
            val live = when {
                isDragging -> edit.drag
                settle != null -> settle.value
                else -> Offset.Zero
            }
            translationX = live.x
            translationY = live.y + (flip?.value ?: 0f)
            val scale = when {
                removingThis -> edit.removeProgress.value
                isDragging -> 1.035f
                else -> 1f
            }
            scaleX = scale
            scaleY = scale
            rotationZ = 0f
            alpha = if (removingThis) edit.removeProgress.value else 1f
        }
}

/** Transparent touch layer above a card so nested widget controls cannot eat the drag. */
@Composable
internal fun Modifier.pageEditDrag(kind: FluidWidget, edit: PageEdit): Modifier {
    if (!edit.active) return this
    return this.pointerInput(kind, edit.active, edit.removing) {
        detectDragGestures(
            onDragStart = { edit.begin(kind, it) },
            onDragEnd = {},
            onDragCancel = {},
            onDrag = { change, amount -> change.consume(); edit.dragBy(amount) }
        )
    }
}

@Composable
internal fun BoxScope.PageEditOverlay(edit: PageEdit, onDone: () -> Unit) {
    AnimatedVisibility(
        visible = edit.active,
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        enter = slideInVertically(spring(dampingRatio = .8f, stiffness = 360f)) { -it / 2 } + fadeIn(tween(140)),
        exit = slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(120))
    ) {
        Row(Modifier.fillMaxWidth().glass(25.dp, true).padding(start = 16.dp, top = 9.dp, end = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Rearrange", style = F.text(18, true)); Text("Hold and drag · drop in a slot", style = F.text(10, muted = true)) }
            GlassButton("Done", primary = true, glyph = "check", onClick = onDone)
        }
    }
    AnimatedVisibility(
        visible = edit.active,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 42.dp),
        enter = slideInVertically(spring(dampingRatio = .8f, stiffness = 360f)) { it } + fadeIn(tween(140)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120))
    ) {
        val hot = edit.overRemove
        Box(
            Modifier.fillMaxWidth().height(62.dp)
                .onGloballyPositioned { edit.removeBounds = it.boundsInRoot() }
                .graphicsLayer {
                    val scale = if (hot) 1.015f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .glass(24.dp, true)
                .background(Color(0xFFE05B67).copy(alpha = if (hot) .24f else .11f), RoundedCornerShape(24.dp))
                .border(if (hot) 2.5.dp else 1.5.dp, Color(0xFFE05B67).copy(alpha = if (hot) 1f else .65f), RoundedCornerShape(24.dp))
                .testTag("widget-remove-target")
                .semantics { stateDescription = if (hot) "Ready to remove" else "Drag here to remove" },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("bin", Color(0xFFE05B67), Modifier.size(22.dp))
                Text(if (hot) "Release to remove" else "Drag here to remove", style = F.text(14, true), color = Color(0xFFE05B67), modifier = Modifier.padding(start = 9.dp))
            }
        }
    }
    val newPageDrop = edit.dropTarget?.takeIf { it.create }
    AnimatedVisibility(
        visible = edit.active && newPageDrop != null,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(112.dp),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120))
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, F.purple.copy(alpha = .55f))))
                .testTag("widget-new-page-target"),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(Modifier.padding(end = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(F.purple), contentAlignment = Alignment.Center) {
                    Glyph("plus", Color.White, Modifier.size(18.dp))
                }
                Text("New page", style = F.text(11, true), color = Color.White, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    val pageDrop = edit.dropTarget?.takeIf { !it.create }
    AnimatedVisibility(
        visible = edit.active && pageDrop != null,
        modifier = Modifier.align(if (pageDrop?.direction == -1) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 12.dp),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120))
    ) {
        Row(Modifier.glass(24.dp, true).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pageDrop?.direction == -1) Glyph("previous", F.purple, Modifier.size(16.dp))
            Text(pageDrop?.page.orEmpty(), style = F.text(12, true), modifier = Modifier.padding(horizontal = 8.dp))
            if (pageDrop?.direction == 1) Glyph("next", F.purple, Modifier.size(16.dp))
        }
    }
}
