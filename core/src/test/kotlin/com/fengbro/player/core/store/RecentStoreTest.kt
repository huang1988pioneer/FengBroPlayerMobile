package com.fengbro.player.core.store

import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.core.playlist.PlaylistManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RecentStoreTest {
    @Test
    fun `records newest first and de-duplicates`(@TempDir dir: Path) {
        val file = File(dir.toFile(), "recent.json")
        val store = RecentStore(file, maxEntries = 3)
        store.record(PlaylistManager.fromLocalPath("/a.mp3")!!)
        store.record(PlaylistManager.fromLocalPath("/b.mp4")!!)
        store.record(PlaylistManager.fromLocalPath("/a.mp3")!!)
        assertEquals(2, store.snapshot.size)
        assertEquals("/a.mp3", store.snapshot[0].filePath)
        assertEquals(MediaKind.Audio, store.snapshot[0].kind)
    }

    @Test
    fun `persists and reloads`(@TempDir dir: Path) {
        val file = File(dir.toFile(), "recent.json")
        val store = RecentStore(file, maxEntries = 50)
        store.recordUrl("https://example.com/live.m3u8", title = "直播")
        val reloaded = RecentStore(file, maxEntries = 50)
        reloaded.load()
        assertEquals(1, reloaded.snapshot.size)
        assertEquals("直播", reloaded.snapshot[0].title)
        assertTrue(reloaded.snapshot[0].isNetworkSource)
    }
}
