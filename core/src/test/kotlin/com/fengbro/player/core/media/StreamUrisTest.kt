package com.fengbro.player.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamUrisTest {
    @Test
    fun `normalizes missing scheme to https`() {
        val uri = StreamUris.tryNormalize("example.com/a.mp4")
        assertNotNull(uri)
        assertEquals("https", uri!!.scheme)
        assertEquals("example.com", uri.host)
    }

    @Test
    fun `accepts protocol-relative urls`() {
        val uri = StreamUris.tryNormalize("//cdn.example.com/live.m3u8")
        assertNotNull(uri)
        assertEquals("https://cdn.example.com/live.m3u8", uri.toString())
    }

    @Test
    fun `rejects file scheme`() {
        assertNull(StreamUris.tryNormalize("file:///tmp/a.mp3"))
    }

    @Test
    fun `detects youtube extraction hosts`() {
        assertTrue(StreamUris.needsExtraction("https://www.youtube.com/watch?v=dQw4w9wg"))
        assertTrue(StreamUris.needsExtraction("https://youtu.be/dQw4w9wg"))
        assertTrue(StreamUris.needsExtraction("https://www.bilibili.com/video/BV1xx"))
        assertFalse(StreamUris.needsExtraction("https://cdn.example.com/a.mp4"))
    }
}
