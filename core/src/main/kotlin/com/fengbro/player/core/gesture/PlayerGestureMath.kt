package com.fengbro.player.core.gesture

import kotlin.math.abs
import kotlin.math.roundToInt

enum class TapZone {
    Left,
    Center,
    Right,
}

enum class DragKind {
    Seek,
    Brightness,
    Volume,
}

object PlayerGestureMath {
    const val LEFT_ZONE = 0.33f
    const val RIGHT_ZONE = 0.67f
    const val DOUBLE_TAP_STEP_SECONDS = 10
    const val VERTICAL_TRAVEL = 0.65f

    fun tapZone(x: Float, width: Float): TapZone {
        if (width <= 0f) return TapZone.Center
        val ratio = (x / width).coerceIn(0f, 1f)
        return when {
            ratio < LEFT_ZONE -> TapZone.Left
            ratio > RIGHT_ZONE -> TapZone.Right
            else -> TapZone.Center
        }
    }

    fun classifyDrag(dx: Float, dy: Float, startX: Float, width: Float): DragKind {
        if (abs(dx) >= abs(dy)) return DragKind.Seek
        val leftHalf = width <= 0f || startX < width * 0.5f
        return if (leftHalf) DragKind.Brightness else DragKind.Volume
    }

    fun seekDeltaSeconds(dx: Float, width: Float, durationSeconds: Double): Double {
        if (width <= 0f) return 0.0
        val span = if (durationSeconds > 0.0) durationSeconds else 60.0
        return (dx / width) * span
    }

    fun verticalAdjustment(dy: Float, height: Float): Float {
        if (height <= 0f) return 0f
        return (-dy / (height * VERTICAL_TRAVEL)).coerceIn(-1f, 1f)
    }

    fun formatSignedSeconds(seconds: Double): String {
        val rounded = seconds.roundToInt()
        val sign = if (rounded < 0) "-" else "+"
        val abs = abs(rounded)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        val secs = abs % 60
        return if (hours > 0) {
            "%s%d:%02d:%02d".format(sign, hours, minutes, secs)
        } else {
            "%s%d:%02d".format(sign, minutes, secs)
        }
    }
}
