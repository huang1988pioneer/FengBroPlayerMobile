package com.fengbro.player.core.gesture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerGestureMathTest {
    @Test
    fun `tap zones split the stage into thirds`() {
        assertEquals(TapZone.Left, PlayerGestureMath.tapZone(10f, 300f))
        assertEquals(TapZone.Center, PlayerGestureMath.tapZone(150f, 300f))
        assertEquals(TapZone.Right, PlayerGestureMath.tapZone(290f, 300f))
        assertEquals(TapZone.Center, PlayerGestureMath.tapZone(0f, 0f))
    }

    @Test
    fun `horizontal drag is seek regardless of starting side`() {
        assertEquals(DragKind.Seek, PlayerGestureMath.classifyDrag(40f, 4f, 20f, 400f))
        assertEquals(DragKind.Seek, PlayerGestureMath.classifyDrag(-40f, 8f, 380f, 400f))
    }

    @Test
    fun `vertical drag is brightness on the left and volume on the right`() {
        assertEquals(DragKind.Brightness, PlayerGestureMath.classifyDrag(4f, -40f, 40f, 400f))
        assertEquals(DragKind.Volume, PlayerGestureMath.classifyDrag(4f, 40f, 360f, 400f))
    }

    @Test
    fun `full-width swipe seeks the whole duration`() {
        assertEquals(120.0, PlayerGestureMath.seekDeltaSeconds(200f, 200f, 120.0), 0.001)
        assertEquals(-60.0, PlayerGestureMath.seekDeltaSeconds(-100f, 200f, 120.0), 0.001)
        assertEquals(30.0, PlayerGestureMath.seekDeltaSeconds(100f, 200f, 0.0), 0.001)
    }

    @Test
    fun `upward swipe raises volume or brightness`() {
        val up = PlayerGestureMath.verticalAdjustment(-130f, 200f)
        val down = PlayerGestureMath.verticalAdjustment(130f, 200f)
        assertTrue(up > 0.9f)
        assertTrue(down < -0.9f)
    }

    @Test
    fun `signed seek labels stay compact`() {
        assertEquals("+0:10", PlayerGestureMath.formatSignedSeconds(10.2))
        assertEquals("-1:05", PlayerGestureMath.formatSignedSeconds(-65.0))
        assertEquals("+1:01:01", PlayerGestureMath.formatSignedSeconds(3661.0))
    }
}
