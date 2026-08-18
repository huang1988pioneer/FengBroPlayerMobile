package com.fengbro.player.core.lyrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class LrcParserTest {
    @Test
    fun `parses multiple timestamps on one line`() {
        val lines = LrcParser.parse(
            """
            [offset:+100]
            [00:12.00][00:24.50]同一個副歌
            [01:00.00]最後一句
            """.trimIndent(),
        )
        assertEquals(3, lines.size)
        assertEquals(12100L, lines[0].timeMs)
        assertEquals("同一個副歌", lines[0].text)
        assertEquals(24600L, lines[1].timeMs)
        assertEquals(60100L, lines[2].timeMs)
    }

    @Test
    fun `finds current lyric by binary search`() {
        val lines = LrcParser.parse(
            """
            [00:00.00]A
            [00:10.00]B
            [00:20.00]C
            """.trimIndent(),
        )
        assertEquals("A", LrcParser.currentLine(lines, 0)?.text)
        assertEquals("B", LrcParser.currentLine(lines, 10_000)?.text)
        assertEquals("B", LrcParser.currentLine(lines, 19_999)?.text)
        assertEquals("C", LrcParser.currentLine(lines, 25_000)?.text)
    }

    @Test
    fun `finds same-stem lrc next to media`(@TempDir dir: Path) {
        val song = dir.resolve("track.mp3")
        val lrc = dir.resolve("track.lrc")
        song.writeText("x")
        lrc.writeText("[00:01.00]你好")
        val found = LrcParser.findSidecar(song.toString())
        assertEquals(lrc.toFile().absolutePath, found)
        val lines = LrcParser.load(found!!)
        assertEquals(1, lines.size)
        assertEquals("你好", lines[0].text)
        assertTrue(lines[0].timeMs >= 1000L)
    }
}
