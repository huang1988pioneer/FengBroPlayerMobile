package com.fengbro.player.core.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerWindowSpecTest {
    @Test
    fun `phone portrait uses a bottom sheet and a single-column landing`() {
        val spec = PlayerWindowSpec.from(411, 891)
        assertEquals(WidthClass.Compact, spec.widthClass)
        assertFalse(spec.useSidePlaylist)
        assertFalse(spec.landingTwoPane)
        assertFalse(spec.keepPlaylistOpenOnSelect)
        assertFalse(spec.useDialogSettings)
        assertEquals(411, spec.landingMaxWidthDp)
        assertEquals(220, spec.coverSizeDp)
        assertEquals(76, spec.playButtonDp)
    }

    @Test
    fun `phone landscape keeps the sheet so video is not squeezed`() {
        val spec = PlayerWindowSpec.from(891, 411)
        assertEquals(WidthClass.Expanded, spec.widthClass)
        assertEquals(HeightClass.Compact, spec.heightClass)
        assertFalse(spec.useSidePlaylist)
        assertTrue(spec.landingTwoPane)
        assertTrue(spec.isCompactHeight)
        assertEquals(64, spec.playButtonDp)
        assertEquals(136, spec.coverSizeDp)
        assertTrue(spec.sheetMaxHeightDp <= 411 - 24)
    }

    @Test
    fun `tablet portrait uses a side playlist and a two-pane landing`() {
        val spec = PlayerWindowSpec.from(800, 1280)
        assertEquals(WidthClass.Medium, spec.widthClass)
        assertTrue(spec.useSidePlaylist)
        assertTrue(spec.landingTwoPane)
        assertTrue(spec.keepPlaylistOpenOnSelect)
        assertEquals(320, spec.sidePaneWidthDp)
        assertEquals(520, spec.landingMaxWidthDp)
        assertEquals(280, spec.coverSizeDp)
    }

    @Test
    fun `tablet landscape widens the side playlist`() {
        val spec = PlayerWindowSpec.from(1280, 800)
        assertEquals(WidthClass.Expanded, spec.widthClass)
        assertTrue(spec.useSidePlaylist)
        assertEquals(360, spec.sidePaneWidthDp)
        assertTrue(spec.useDialogSettings)
    }

    @Test
    fun `split-screen compact width stays phone-shaped`() {
        val spec = PlayerWindowSpec.from(400, 800)
        assertEquals(WidthClass.Compact, spec.widthClass)
        assertFalse(spec.useSidePlaylist)
        assertFalse(spec.landingTwoPane)
    }

    @Test
    fun `inner foldable size is a tablet workspace`() {
        val spec = PlayerWindowSpec.from(673, 841)
        assertEquals(WidthClass.Medium, spec.widthClass)
        assertEquals(HeightClass.Medium, spec.heightClass)
        assertTrue(spec.useSidePlaylist)
        assertTrue(spec.landingTwoPane)
    }
}
