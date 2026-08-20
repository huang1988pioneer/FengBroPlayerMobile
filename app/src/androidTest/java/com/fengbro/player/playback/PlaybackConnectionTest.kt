package com.fengbro.player.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackConnectionTest {
    @Test
    fun controllerSendsPlaybackIntoServiceOwnedPlayer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connection = PlaybackConnection(context)
        try {
            val player = withTimeout(5_000) { connection.player.filterNotNull().first() }

            withContext(Dispatchers.Main) {
                connection.playUri(
                    uri = SILENT_WAV_DATA_URI,
                    title = "Connection test",
                    mediaId = "connection-test",
                )
            }

            withTimeout(5_000) {
                while (withContext(Dispatchers.Main) { player.mediaItemCount == 0 }) delay(20)
            }
            withContext(Dispatchers.Main) {
                assertEquals("connection-test", player.currentMediaItem?.mediaId)
                assertEquals("Connection test", player.mediaMetadata.title?.toString())
                connection.stopService()
            }
            withContext(Dispatchers.Main) {
                connection.playUri(
                    uri = SILENT_WAV_DATA_URI,
                    title = "Playback resumed",
                    mediaId = "resumed-after-stop",
                )
            }
            withTimeout(5_000) {
                while (withContext(Dispatchers.Main) {
                        player.currentMediaItem?.mediaId != "resumed-after-stop"
                    }
                ) delay(20)
            }
            withContext(Dispatchers.Main) {
                assertEquals("Playback resumed", player.mediaMetadata.title?.toString())
                connection.stopService()
            }
        } finally {
            withContext(Dispatchers.Main) { connection.release() }
        }
    }

    private companion object {
        const val SILENT_WAV_DATA_URI =
            "data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA="
    }
}
