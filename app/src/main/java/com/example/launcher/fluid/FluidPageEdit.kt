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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun pageSupportsWidgets(page: String) = page != "Markets"

/** The lifted widget is drawn by a root overlay from this layer, so it keeps floating under the
 * finger while pages scroll beneath it. */
@Stable
internal class DragRoute {
    var lifted by mutableStateOf<FluidWidget?>(null)
    var at by mutableStateOf(Offset.Zero)
    var layer: GraphicsLayer? = null
}

/** In-page rearrangement state. Widget cards stay in the real page layout, the dragged card
 * follows the finger, and every displaced card is FLIP-animated into its new slot. */
@Stable
internal class PageEdit(
    val page: String,
    private val model: FluidModel,
    private val scope: kotlinx.coroutines.CoroutineScope,
    val route: DragRoute = DragRoute(),
    private val onActivated: () -> Unit = {},
    private val onPageDropped: (String) -> Unit = {},
    private val onHandoff: (kind: FluidWidget, target: String, at: Offset, grab: Offset, height: Float) -> Unit = { _, _, _, _, _ -> }
) {
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
    var newPageArmed by mutableStateOf(false)
        private set
    /** A card handed over from another page whose slot is not yet measured at rest. */
    var incoming by mutableStateOf<FluidWidget?>(null)
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
    private var grab = Offset.Zero
    private var pointer = Offset.Zero
        set(value) { field = value; route.at = value - grab }
    private var autoScroll: Job? = null
    private var autoDirection = 0
    private var edgeJob: Job? = null
    private var edgeDirection = 0

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
        incoming = null
        overRemove = false
        val rect = cards[kind] ?: Rect.Zero
        grab = local
        pointer = rect.topLeft + carrying + local
        route.lifted = kind
    }

    /** Takes over a drag from another page. The card goes straight into the slot under the
     * finger, the cards below it slide down, and it keeps floating while this page scrolls in. */
    fun receive(kind: FluidWidget, from: String, at: Offset, grabbed: Offset, height: Float) {
        if (!active) { active = true; onActivated() }
        stopAutoScroll()
        cancelEdge()
        settleJob?.cancel(); settleJob = null; settling = null; settle = null
        val old = visible - kind
        val before = old.firstOrNull { w -> (cards[w]?.center?.y ?: Float.MAX_VALUE) > at.y }
        val new = old.toMutableList().apply { add(before?.let { indexOf(it) } ?: size, kind) }
        forget(kind)
        heights[kind] = height
        shift(old, new, kind)
        visible = new
        model.moveWidgetToPage(from, page, kind, before)
        grab = grabbed
        pointer = at
        drag = Offset.Zero
        overRemove = false
        incoming = kind
        dragging = kind
        route.lifted = kind
    }

    /** Once this page has settled, anchor the floating card to its measured slot. */
    fun land(kind: FluidWidget) {
        if (incoming != kind || dragging != kind) return
        incoming = null
        val rect = cards[kind] ?: return
        drag = pointer - grab - rect.topLeft
        maybeSwap(kind)
    }

    fun forget(kind: FluidWidget) {
        flipJobs.remove(kind)?.cancel()
        heights.remove(kind)
        cards.remove(kind)
        flips.remove(kind)
    }

    fun dragBy(delta: Offset) {
        val kind = dragging ?: return
        pointer += delta
        if (incoming == kind) return
        drag += delta
        overRemove = removeBounds.width > 0f && removeBounds.contains(pointer)
        maybeSwap(kind)
        autoScrollIfNeeded()
        updateEdge()
    }

    fun end() {
        stopAutoScroll()
        val kind = dragging ?: return
        val createPage = newPageArmed
        cancelEdge()
        dragging = null
        val start = if (incoming == kind) cards[kind]?.let { pointer - grab - it.topLeft } ?: Offset.Zero else drag
        incoming = null
        if (overRemove) {
            remove(kind)
            return
        }
        route.lifted = null
        if (createPage) {
            val target = model.addPage()
            model.moveWidgetToPage(page, target, kind)
            forget(kind)
            drag = Offset.Zero
            overRemove = false
            onPageDropped(target)
            return
        }
        val anim = Animatable(start, Offset.VectorConverter)
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
        if (dragging != null && route.lifted == dragging) route.lifted = null
        active = false
        dragging = null
        incoming = null
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
            if (route.lifted == kind) route.lifted = null
            removing = null
            removeProgress.snapTo(1f)
        }
    }

    private fun tops(list: List<FluidWidget>): Map<FluidWidget, Float> {
        var y = 0f
        return buildMap { list.forEach { w -> put(w, y); y += (heights[w] ?: 0f) + spacingPx } }
    }

    private fun maybeSwap(kind: FluidWidget) {
        repeat(visible.size.coerceAtLeast(1)) {
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

    /** Holding a card against a side edge scrolls to that page, like a home-screen launcher.
     * Only a deliberate sideways drag counts, so vertical reorders near an edge stay local. */
    private fun updateEdge() {
        val direction = if (overRemove) 0 else edgeDirectionNow()
        if (direction == edgeDirection) return
        edgeDirection = direction
        edgeJob?.cancel()
        edgeJob = null
        newPageArmed = false
        if (direction == 0) return
        val kind = dragging ?: return
        val pages = model.state.value.config.pages
        val neighbour = pages.getOrNull(pages.indexOf(page) + direction)
        edgeJob = scope.launch {
            if (neighbour == null) {
                // A page holding only the dragged card would be left empty, so it cannot spawn another.
                if (direction < 0 || visible.none { it != kind }) return@launch
                delay(220)
                newPageArmed = true
                delay(750)
                if (dragging == kind) handoff(kind, model.addPage())
            } else if (pageSupportsWidgets(neighbour)) {
                delay(450)
                if (dragging == kind) handoff(kind, neighbour)
            }
        }
    }

    private fun edgeDirectionNow(): Int {
        val vp = viewport
        if (vp.width <= 0f || incoming != null) return 0
        val zone = vp.width * .12f
        val travel = vp.width * .065f
        return when {
            drag.x < -travel && pointer.x <= vp.left + zone -> -1
            drag.x > travel && pointer.x >= vp.right - zone -> 1
            else -> 0
        }
    }

    private fun handoff(kind: FluidWidget, target: String) {
        val grabbed = grab
        val at = pointer
        val height = heights[kind] ?: 0f
        stopAutoScroll()
        edgeJob = null
        edgeDirection = 0
        newPageArmed = false
        dragging = null
        drag = Offset.Zero
        overRemove = false
        val old = visible
        val new = old - kind
        shift(old, new, kind)
        visible = new
        forget(kind)
        onHandoff(kind, target, at, grabbed, height)
    }

    private fun cancelEdge() {
        edgeJob?.cancel()
        edgeJob = null
        edgeDirection = 0
        newPageArmed = false
    }
}

/** Lift, FLIP offsets and per-card measurement all live here so the page keeps its layout. */
@Composable
internal fun Modifier.pageEditCard(kind: FluidWidget, edit: PageEdit?, phase: State<Float>, index: Int): Modifier {
    if (edit == null) return this
    val measured = this.onGloballyPositioned {
        val height = it.size.height.toFloat()
        if (edit.heights[kind] != height) edit.heights[kind] = height
        if (edit.dragging != kind || edit.incoming == kind) {
            // Unclipped, so a card on a page that is still off screen reports its real slot.
            val rect = Rect(it.positionInRoot(), it.size.toSize())
            if (edit.cards[kind] != rect) edit.cards[kind] = rect
        }
    }
    // This detector only opens the editor. The move stream is routed from the launcher root
    // in the Initial pass, so nested widget controls cannot cancel it and the drag survives
    // the card moving to another page.
    val grip = measured.pointerInput(kind) {
        detectDragGesturesAfterLongPress(
            onDragStart = { if (!edit.active) edit.activate(kind, it) },
            onDragCancel = {},
            onDragEnd = {},
            onDrag = { _, _ -> }
        )
    }
    val flip = edit.flips[kind]
    val settle = if (edit.settling == kind) edit.settle else null
    return grip
        .zIndex(if (edit.settling == kind) 2f else 0f)
        .graphicsLayer {
            val live = if (edit.dragging != kind && settle != null) settle.value else Offset.Zero
            translationX = live.x
            translationY = live.y + (flip?.value ?: 0f)
        }
        .drawWithContent {
            val layer = edit.route.layer
            if (edit.route.lifted == kind && layer != null) layer.record { this@drawWithContent.drawContent() } else drawContent()
        }
}

/** Draws the lifted card above every page, the editor chrome and the bin. */
@Composable
internal fun DragGhost(route: DragRoute, edits: Map<String, PageEdit>) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(layer) {
        route.layer = layer
        onDispose { if (route.layer === layer) route.layer = null }
    }
    Spacer(Modifier.fillMaxSize().drawBehind {
        val kind = route.lifted ?: return@drawBehind
        val removing = edits.values.firstOrNull { it.removing == kind }
        val progress = removing?.removeProgress?.value ?: 1f
        val scale = if (removing != null) progress else 1.035f
        layer.alpha = progress
        val pivot = Offset(layer.size.width / 2f, layer.size.height / 2f)
        translate(route.at.x, route.at.y) { scale(scale, pivot) { drawLayer(layer) } }
    })
}

/** Hit testing only happens on touch down, so a card that changes page mid-gesture can never
 * receive the pointer itself. The launcher root observes it instead and drives the live drag. */
internal fun Modifier.routePageDrags(edits: Map<String, PageEdit>): Modifier = pointerInput(edits) {
    awaitPointerEventScope {
        var id: PointerId? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (id == null) id = event.changes.firstOrNull { it.pressed && !it.previousPressed }?.id
            val change = event.changes.firstOrNull { it.id == id } ?: continue
            val edit = edits.values.firstOrNull { it.dragging != null }
            if (!change.pressed) {
                id = null
                edit?.end()
                continue
            }
            val delta = change.position - change.previousPosition
            if (edit != null && delta != Offset.Zero) { change.consume(); edit.dragBy(delta) }
        }
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
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp),
        enter = slideInVertically(spring(dampingRatio = .8f, stiffness = 360f)) { it } + fadeIn(tween(140)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120))
    ) {
        val hot = edit.overRemove
        val lift by animateFloatAsState(if (hot) 1f else 0f, spring(dampingRatio = .6f, stiffness = 520f), label = "Bin hover")
        val red = Color(0xFFE05B67)
        Box(
            Modifier.size(width = 64.dp, height = 46.dp)
                // The drop zone is a little larger than the button so it is easy to hit.
                .onGloballyPositioned { edit.removeBounds = it.boundsInRoot().let { r -> r.inflate(r.height * .45f) } }
                .graphicsLayer {
                    val scale = 1f + lift * .16f
                    scaleX = scale
                    scaleY = scale
                }
                .glass(16.dp, true)
                .background(red.copy(alpha = .14f + lift * .86f), RoundedCornerShape(16.dp))
                .border(1.5.dp, red.copy(alpha = .7f + lift * .3f), RoundedCornerShape(16.dp))
                .testTag("widget-remove-target")
                .semantics { stateDescription = if (hot) "Ready to remove" else "Drag here to remove" },
            contentAlignment = Alignment.Center
        ) {
            Glyph("bin", lerp(red, Color.White, lift), Modifier.size(22.dp))
        }
    }
    AnimatedVisibility(
        visible = edit.active && edit.newPageArmed,
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
}
