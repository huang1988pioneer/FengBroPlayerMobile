package com.fengbro.player.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAny
import com.fengbro.player.core.gesture.DragKind
import com.fengbro.player.core.gesture.PlayerGestureMath
import com.fengbro.player.core.gesture.TapZone
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

fun Modifier.playerGestures(
    enabled: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: (TapZone) -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onSeekDrag: (dx: Float, width: Float, finished: Boolean) -> Unit,
    onVerticalDrag: (kind: DragKind, dy: Float, height: Float, finished: Boolean) -> Unit,
    onVerticalDragStart: () -> Unit,
    onSeekDragStart: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        val start = down.position
        val width = size.width.toFloat()
        val height = size.height.toFloat()

        val firstPhase = withTimeoutOrNull(longPressTimeout) {
            awaitPastSlopOrUp(down.id, start, slop)
        }

        when {
            firstPhase == null -> {
                onLongPressStart()
                waitForPointerUp()
                onLongPressEnd()
            }
            firstPhase is GestureProbe.Up -> {
                val second = withTimeoutOrNull(doubleTapTimeout) {
                    awaitFirstDown(requireUnconsumed = false)
                }
                if (second != null) {
                    onDoubleTap(PlayerGestureMath.tapZone(second.position.x, width))
                    waitForPointerUp()
                } else {
                    onSingleTap()
                }
            }
            firstPhase is GestureProbe.Drag -> {
                val kind = PlayerGestureMath.classifyDrag(
                    dx = firstPhase.position.x - start.x,
                    dy = firstPhase.position.y - start.y,
                    startX = start.x,
                    width = width,
                )
                when (kind) {
                    DragKind.Seek -> onSeekDragStart()
                    DragKind.Brightness, DragKind.Volume -> onVerticalDragStart()
                }
                emitDrag(kind, firstPhase.position, start, width, height, finished = false, onSeekDrag, onVerticalDrag)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) {
                        emitDrag(kind, change.position, start, width, height, finished = true, onSeekDrag, onVerticalDrag)
                        break
                    }
                    emitDrag(kind, change.position, start, width, height, finished = false, onSeekDrag, onVerticalDrag)
                }
            }
        }
    }
}

private sealed class GestureProbe {
    data object Up : GestureProbe()
    data class Drag(val position: Offset) : GestureProbe()
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPastSlopOrUp(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    start: Offset,
    slop: Float,
): GestureProbe {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return GestureProbe.Up
        if (!change.pressed) {
            change.consume()
            return GestureProbe.Up
        }
        if (change.positionChanged()) {
            val distance = hypot(change.position.x - start.x, change.position.y - start.y)
            if (distance > slop) {
                change.consume()
                return GestureProbe.Drag(change.position)
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForPointerUp() {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (!event.changes.fastAny { it.pressed }) {
            event.changes.forEach { it.consume() }
            return
        }
    }
}

private fun emitDrag(
    kind: DragKind,
    current: Offset,
    start: Offset,
    width: Float,
    height: Float,
    finished: Boolean,
    onSeekDrag: (Float, Float, Boolean) -> Unit,
    onVerticalDrag: (DragKind, Float, Float, Boolean) -> Unit,
) {
    when (kind) {
        DragKind.Seek -> onSeekDrag(current.x - start.x, width, finished)
        DragKind.Brightness, DragKind.Volume ->
            onVerticalDrag(kind, current.y - start.y, height, finished)
    }
}
