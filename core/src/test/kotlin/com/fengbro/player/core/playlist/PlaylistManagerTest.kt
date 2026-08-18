package com.fengbro.player.core.playlist

import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistManagerTest {
    @Test
    fun `import only auto-selects first new playable item`() {
        val manager = PlaylistManager()
        val first = PlaylistManager.fromLocalPath("/music/a.mp3")!!
        val second = PlaylistManager.fromLocalPath("/video/b.mp4")!!
        val result = manager.importPrepared(listOf(first, second), selectFirst = true)
        assertEquals(2, result.added)
        assertEquals(first.title, result.shouldSelect?.title)
        assertEquals(2, manager.size)
        assertEquals(1, manager.snapshot[0].index)
        assertEquals(2, manager.snapshot[1].index)
    }

    @Test
    fun `reopening existing path selects it instead of duplicating`() {
        val manager = PlaylistManager()
        val item = PlaylistManager.fromLocalPath("/music/a.mp3")!!
        manager.importPrepared(listOf(item), selectFirst = true)
        val again = PlaylistManager.fromLocalPath("/music/a.mp3")!!
        val result = manager.importPrepared(listOf(again), selectFirst = true)
        assertEquals(0, result.added)
        assertEquals(1, manager.size)
        assertEquals(item.id, result.shouldSelect?.id)
    }

    @Test
    fun `next and previous skip unplayable demo rows`() {
        val manager = PlaylistManager()
        val demo = MediaItem(index = 1, title = "demo", kind = MediaKind.Audio)
        val playable = PlaylistManager.fromLocalPath("/music/a.mp3")!!
        manager.add(demo)
        manager.add(playable)
        val next = manager.findPlayable(fromIndex = 0, direction = 1)
        assertEquals(playable.id, next?.id)
        val prev = manager.findPlayable(fromIndex = 1, direction = -1)
        assertEquals(playable.id, prev?.id)
    }

    @Test
    fun `network url is prepended when playing immediately`() {
        val manager = PlaylistManager()
        manager.add(PlaylistManager.fromLocalPath("/music/a.mp3")!!)
        val result = manager.addNetworkUrl("youtu.be/abc123", playImmediately = true)
        assertEquals("https://youtu.be/abc123", result.normalizedUrl)
        assertEquals(MediaKind.Video, result.item?.kind)
        assertEquals(result.item?.id, manager.snapshot.first().id)
        assertTrue(result.item?.title?.contains("YouTube") == true)
    }

    @Test
    fun `invalid network url is rejected`() {
        val manager = PlaylistManager()
        val result = manager.addNetworkUrl("not a url with spaces", playImmediately = true)
        assertNull(result.item)
        assertTrue(result.statusMessage.contains("有效"))
    }
}
