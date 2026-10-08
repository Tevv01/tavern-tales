package dev.tevv.taverntales.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Moves [id] to [to] in a copy of [order]. */
internal fun moved(order: List<String>, id: String, to: Int): List<String> {
    val from = order.indexOf(id)
    if (from < 0 || from == to) return order
    return order.toMutableList().apply {
        removeAt(from)
        add(to.coerceIn(0, size), id)
    }
}

/**
 * The scenes of a collection in a grid where a tile can be held and dragged to another place; the
 * other tiles glide out of the way. Holding a tile and letting go without moving calls [onHold]
 * (the tile's menu). Tiles are placed by hand, [columns] across with the art's 5:7 shape.
 *
 * [tile] gets the modifier that must go on the tile's clickable surface, after its `clickable`, so a
 * drag or hold doesn't also count as a tap.
 */
@Composable
internal fun <T> ReorderableGrid(
    items: List<T>,
    id: (T) -> String,
    columns: Int,
    gap: Dp,
    onReorder: (List<String>) -> Unit,
    onHold: (T) -> Unit,
    tile: @Composable (item: T, gestures: Modifier, dragging: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val gapPx = with(density) { gap.toPx() }
        val cellW = (constraints.maxWidth - gapPx * (columns - 1)) / columns
        val cellH = cellW * 7f / 5f
        fun cellAt(index: Int) = Offset((index % columns) * (cellW + gapPx), (index / columns) * (cellH + gapPx))

        val ids = items.map(id)
        val currentIds by rememberUpdatedState(ids)
        val currentOnReorder by rememberUpdatedState(onReorder)
        val currentOnHold by rememberUpdatedState(onHold)
        val currentItems by rememberUpdatedState(items)
        // While dragging (and until the library has the new order) the grid shows this order.
        val order = remember { mutableStateListOf<String>() }
        var dragging by remember { mutableStateOf<String?>(null) }
        var awaitingSave by remember { mutableStateOf(false) }
        var dragTopLeft by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(ids) { if (awaitingSave && ids == order.toList()) awaitingSave = false }
        val shown = if (dragging != null || awaitingSave) order.toList() else ids

        val rows = (items.size + columns - 1) / columns
        val height = rows * cellH + (rows - 1).coerceAtLeast(0) * gapPx
        val byId = items.associateBy(id)
        Box(Modifier.fillMaxWidth().height(with(density) { height.toDp() })) {
            shown.forEachIndexed { index, itemId ->
                val item = byId[itemId] ?: return@forEachIndexed
                key(itemId) {
                    val target = cellAt(index)
                    val isDragged = dragging == itemId
                    val position = remember { Animatable(target, Offset.VectorConverter) }
                    LaunchedEffect(target, isDragged) {
                        if (!isDragged) position.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                    val lift by animateFloatAsState(if (isDragged) 1.06f else 1f, label = "lift")
                    var travelled by remember { mutableFloatStateOf(0f) }
                    val gestures = Modifier.pointerInput(itemId) {
                        val slop = viewConfiguration.touchSlop
                        fun finish() {
                            val moved = order.toList() != currentIds
                            if (moved) {
                                awaitingSave = true
                                currentOnReorder(order.toList())
                            } else if (travelled < slop) {
                                currentItems.find { id(it) == itemId }?.let(currentOnHold)
                            }
                            val dropped = dragTopLeft
                            scope.launch { position.snapTo(dropped) }
                            dragging = null
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                order.clear()
                                order.addAll(currentIds)
                                dragTopLeft = position.value
                                travelled = 0f
                                dragging = itemId
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragTopLeft += amount
                                travelled += amount.getDistance()
                                val centre = dragTopLeft + Offset(cellW / 2, cellH / 2)
                                val col = (centre.x / (cellW + gapPx)).toInt().coerceIn(0, columns - 1)
                                val row = (centre.y / (cellH + gapPx)).toInt().coerceAtLeast(0)
                                val to = (row * columns + col).coerceIn(0, order.size - 1)
                                if (order.indexOf(itemId) != to) {
                                    val next = moved(order.toList(), itemId, to)
                                    order.clear()
                                    order.addAll(next)
                                }
                            },
                            onDragEnd = ::finish,
                            onDragCancel = ::finish,
                        )
                    }
                    val at = if (isDragged) dragTopLeft else position.value
                    Box(
                        Modifier
                            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                            .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                            .zIndex(if (isDragged) 1f else 0f)
                            .scale(lift),
                    ) {
                        tile(item, gestures, isDragged)
                    }
                }
            }
        }
    }
}

/**
 * Dragging a collection by its header to a new place in the list. While one is dragged, every
 * collection shows only its header ([reordering]), so the list is short and easy to drop into. The
 * dragged header stays under the finger even as the list re-lays out or scrolls near its edges.
 */
internal class CollectionReorderState(private val listState: LazyListState) {
    var dragging by mutableStateOf<String?>(null)
        private set
    val reordering get() = dragging != null || awaitingSave

    /** The order shown while dragging, and until the library has saved it. */
    val order = mutableStateListOf<String>()
    private var awaitingSave by mutableStateOf(false)

    private var listTop = 0f
    private var fingerY by mutableFloatStateOf(0f) // in the list's viewport
    private var grab = 0f // finger's distance below the dragged item's top

    fun onListPositioned(rootY: Float) {
        listTop = rootY
    }

    fun shown(ids: List<String>): List<String> = if (reordering) order.toList() else ids

    /** Call when the library's order changes: once it matches the dropped order, the list follows the library again. */
    fun onLibraryChanged(ids: List<String>) {
        if (awaitingSave && ids == order.toList()) awaitingSave = false
    }

    fun start(key: String, ids: List<String>, fingerRootY: Float) {
        order.clear()
        order.addAll(ids)
        fingerY = fingerRootY - listTop
        val top = listState.layoutInfo.visibleItemsInfo.find { it.key == key }?.offset ?: 0
        grab = fingerY - top
        dragging = key
    }

    fun drag(dy: Float) {
        fingerY += dy
        swapUnderFinger()
    }

    private fun swapUnderFinger() {
        val key = dragging ?: return
        val over = listState.layoutInfo.visibleItemsInfo.find { it.key != key && fingerY >= it.offset && fingerY < it.offset + it.size }
        val to = over?.let { order.indexOf(it.key as String) } ?: return
        val next = moved(order.toList(), key, to)
        order.clear()
        order.addAll(next)
    }

    /** Ends the drag; returns the new order if it changed. */
    fun end(ids: List<String>): List<String>? {
        val result = order.toList().takeIf { it != ids }
        awaitingSave = result != null
        dragging = null
        return result
    }

    /** How far to shift the dragged item so its header stays under the finger. */
    fun translation(key: String): Float {
        if (dragging != key) return 0f
        val top = listState.layoutInfo.visibleItemsInfo.find { it.key == key }?.offset ?: return 0f
        return fingerY - grab - top
    }

    /** Scrolls while the finger is near the top or bottom edge, so a collection can travel far. */
    suspend fun autoScroll(edge: Float) {
        while (dragging != null) {
            val height = listState.layoutInfo.viewportSize.height
            val speed = when {
                fingerY < edge -> -(edge - fingerY) / edge * 30f
                fingerY > height - edge -> (fingerY - (height - edge)) / edge * 30f
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed)
                swapUnderFinger()
            }
            delay(16)
        }
    }
}

@Composable
internal fun rememberCollectionReorderState(listState: LazyListState): CollectionReorderState {
    val state = remember(listState) { CollectionReorderState(listState) }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(state.dragging != null) { if (state.dragging != null) state.autoScroll(edge) }
    return state
}

/** Lets the list tell [state] where it is on screen. */
internal fun Modifier.reorderableList(state: CollectionReorderState) =
    onGloballyPositioned { state.onListPositioned(it.positionInRoot().y) }

/**
 * Hold-and-drag on a collection's header. Letting go without moving calls [onHold]. Goes after
 * the header's `clickable` so a drag or hold doesn't also count as a tap.
 */
@Composable
internal fun Modifier.dragToReorder(
    state: CollectionReorderState,
    key: String,
    ids: List<String>,
    onReorder: (List<String>) -> Unit,
    onHold: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentIds by rememberUpdatedState(ids)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnHold by rememberUpdatedState(onHold)
    var headerTop by remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { headerTop = it.positionInRoot().y }
        .pointerInput(key) {
            val slop = viewConfiguration.touchSlop
            var travelled = 0f
            fun finish() {
                val newOrder = state.end(currentIds)
                if (newOrder != null) currentOnReorder(newOrder) else if (travelled < slop) currentOnHold()
            }
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    travelled = 0f
                    state.start(key, currentIds, headerTop + position.y)
                },
                onDrag = { change, amount ->
                    change.consume()
                    travelled += amount.getDistance()
                    state.drag(amount.y)
                },
                onDragEnd = ::finish,
                onDragCancel = ::finish,
            )
        }
}

/** Draws the dragged collection above the others, shifted to follow the finger. */
internal fun Modifier.draggedCollection(state: CollectionReorderState, key: String): Modifier =
    zIndex(if (state.dragging == key) 1f else 0f)
        .graphicsLayer {
            translationY = state.translation(key)
            if (state.dragging == key) {
                shadowElevation = 12f
                scaleX = 1.02f
                scaleY = 1.02f
            }
        }
