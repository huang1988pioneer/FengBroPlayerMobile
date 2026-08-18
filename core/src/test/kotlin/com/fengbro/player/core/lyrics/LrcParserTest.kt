package com.fengbro.player.core.lyrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
