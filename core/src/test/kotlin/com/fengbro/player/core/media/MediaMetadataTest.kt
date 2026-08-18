package com.fengbro.player.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class MediaMetadataTest {
    @ParameterizedTest
    @ValueSource(strings = ["song.mp3", "A.FLAC", "x.m4a", "voice.opus"])
    fun `classifies audio extensions`(path: String) {
        assertTrue(MediaMetadata.isAudio(path))
        assertFalse(MediaMetadata.isVideo(path))
    }

    @ParameterizedTest
    @ValueSource(strings = ["clip.mp4", "film.MKV", "cam.webm", "show.ts"])
    fun `classifies video extensions`(path: String) {
        assertTrue(MediaMetadata.isVideo(path))
        assertFalse(MediaMetadata.isAudio(path))
    }

    @Test
    fun `formats duration with and without hours`() {
        assertEquals("00:00", MediaMetadata.formatDuration(0))
        assertEquals("01:05", MediaMetadata.formatDuration(65_000))
        assertEquals("1:01:01", MediaMetadata.formatDuration(3_661_000))
    }

    @Test
    fun `finds same-stem srt next to a video`(@TempDir dir: Path) {
        val video = dir.resolve("Movie.mp4")
        val srt = dir.resolve("Movie.srt")
        video.writeText("x")
        srt.writeText("1")
        assertEquals(srt.toFile().absolutePath, MediaMetadata.findSidecarSubtitle(video.toString()))
    }

    @Test
    fun `prefers srt over other sidecar subtitles`(@TempDir dir: Path) {
        val video = dir.resolve("clip.mkv")
        video.writeText("x")
        dir.resolve("clip.vtt").writeText("web")
        dir.resolve("clip.srt").writeText("sub")
        val found = MediaMetadata.findSidecarSubtitle(video.toString())
        assertTrue(found!!.endsWith("clip.srt"))
    }

    @Test
    fun `returns null when no sidecar exists`(@TempDir dir: Path) {
        val video = dir.resolve("alone.mp4")
        video.writeText("x")
        assertNull(MediaMetadata.findSidecarSubtitle(video.toString()))
    }

    @Test
    fun `hue is stable and in range`() {
        val a = MediaMetadata.hueFromPath("/music/a.mp3")
        val b = MediaMetadata.hueFromPath("/music/a.mp3")
        assertEquals(a, b)
        assertTrue(a in 0..359)
    }
}
