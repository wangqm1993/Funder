package com.example.funder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    startIndex: Int = 0,
    itemCount: Int = Int.MAX_VALUE,
    onSwap: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        DragDropState(lazyListState, scope, onSwap, startIndex)
    }
    state.itemCount = itemCount
    return state
}

fun Modifier.dragContainer(
    dragDropState: DragDropState,
    onDragEnd: () -> Unit = {}
): Modifier {
    return pointerInput(dragDropState) {
        var overscrollJob: Job? = null
        detectDragGesturesAfterLongPress(
            onDrag = { change, offset ->
                change.consume()
                dragDropState.onDrag(offset)
                if (overscrollJob?.isActive == true) return@detectDragGesturesAfterLongPress
                dragDropState.checkForOverScroll()
                    .takeIf { it != 0f }
                    ?.let {
                        overscrollJob = dragDropState.scope.launch {
                            dragDropState.lazyListState.scrollBy(it)
                        }
                    } ?: run { overscrollJob?.cancel() }
            },
            onDragStart = { offset -> dragDropState.onDragStart(offset) },
            onDragEnd = {
                val wasDragging = dragDropState.currentIndexOfDraggedItem != null
                dragDropState.onDragInterrupted()
                overscrollJob?.cancel()
                if (wasDragging) onDragEnd()
            },
            onDragCancel = {
                val wasDragging = dragDropState.currentIndexOfDraggedItem != null
                dragDropState.onDragInterrupted()
                overscrollJob?.cancel()
                if (wasDragging) onDragEnd()
            }
        )
    }
}

class DragDropState internal constructor(
    val lazyListState: LazyListState,
    internal val scope: CoroutineScope,
    private val onSwap: (Int, Int) -> Unit,
    private val startIndex: Int
) {
    internal var itemCount: Int = Int.MAX_VALUE

    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)
        private set

    var settlingItemIndex by mutableStateOf<Int?>(null)
        private set
    var settlingItemOffset = Animatable(0f)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)

    private val initialOffsets: Pair<Int, Int>?
        get() = initiallyDraggedElement?.let { Pair(it.offset, it.offset + it.size) }

    private val currentElement: LazyListItemInfo?
        get() = currentIndexOfDraggedItem?.let { idx ->
            lazyListState.layoutInfo.visibleItemsInfo
                .getOrNull(idx - (lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0))
        }

    val elementDisplacement: Float?
        get() = currentIndexOfDraggedItem
            ?.let { idx ->
                lazyListState.layoutInfo.visibleItemsInfo
                    .getOrNull(idx - (lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0))
            }
            ?.let { item ->
                (initiallyDraggedElement?.offset ?: 0).toFloat() + draggedDistance - item.offset
            }

    private fun isValidIndex(index: Int) =
        index >= startIndex && index < startIndex + itemCount

    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offset.y.toInt() in item.offset..(item.offset + item.size) &&
                    isValidIndex(item.index)
            }
            ?.also {
                currentIndexOfDraggedItem = it.index
                initiallyDraggedElement = it
            }
    }

    fun onDragInterrupted() {
        if (currentIndexOfDraggedItem != null) {
            val offset = elementDisplacement ?: 0f
            settlingItemIndex = currentIndexOfDraggedItem
            scope.launch {
                settlingItemOffset.snapTo(offset)
                settlingItemOffset.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                settlingItemIndex = null
            }
        }
        draggedDistance = 0f
        currentIndexOfDraggedItem = null
        initiallyDraggedElement = null
    }

    fun onDrag(offset: Offset) {
        draggedDistance += offset.y
        initialOffsets?.let { (topOffset, bottomOffset) ->
            val startOffset = topOffset + draggedDistance
            val endOffset = bottomOffset + draggedDistance
            currentElement?.let { hovered ->
                lazyListState.layoutInfo.visibleItemsInfo
                    .filterNot { item ->
                        (item.offset + item.size) < startOffset ||
                            item.offset > endOffset ||
                            hovered.index == item.index ||
                            !isValidIndex(item.index)
                    }
                    .firstOrNull { item ->
                        val delta = startOffset - hovered.offset
                        when {
                            delta > 0 -> endOffset > item.offset + item.size
                            else -> startOffset < item.offset
                        }
                    }
                    ?.also { item ->
                        currentIndexOfDraggedItem?.let { current ->
                            onSwap(current - startIndex, item.index - startIndex)
                        }
                        currentIndexOfDraggedItem = item.index
                    }
            }
        }
    }

    fun checkForOverScroll(): Float {
        return initiallyDraggedElement?.let {
            val startOffset = it.offset + draggedDistance
            val endOffset = startOffset + it.size
            when {
                draggedDistance > 0 ->
                    (endOffset - lazyListState.layoutInfo.viewportEndOffset + 50f)
                        .takeIf { diff -> diff > 0 }
                draggedDistance < 0 ->
                    (startOffset - lazyListState.layoutInfo.viewportStartOffset - 50f)
                        .takeIf { diff -> diff < 0 }
                else -> null
            }
        } ?: 0f
    }
}
