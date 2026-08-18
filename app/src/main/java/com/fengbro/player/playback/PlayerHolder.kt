package com.fengbro.player.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import com.fengbro.player.core.model.ResolvedStream
import kotlin.math.roundToLong

class PlayerHolder(context: Context) {
    private val appContext = context.applicationContext

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    private val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            playWhenReady = true
            volume = 1f
        }

    val isPlaying: Boolean get() = player.isPlaying
    val hasMedia: Boolean get() = player.mediaItemCount > 0 && player.currentMediaItem != null
    val length: Long get() = player.duration.coerceAtLeast(0)
    val time: Long get() = player.currentPosition.coerceAtLeast(0)

    var lastError: String? = null
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                lastError = error.message ?: error.errorCodeName
            }
        })
    }

    fun playUri(uri: String, mimeType: String? = null, subtitleUri: String? = null) {
        lastError = null
        val builder = MediaItem.Builder().setUri(uri)
        if (!mimeType.isNullOrBlank()) builder.setMimeType(mimeType)
        if (!subtitleUri.isNullOrBlank()) {
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUri))
                        .setMimeType(guessSubtitleMime(subtitleUri))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        player.setMediaItem(builder.build())
        player.prepare()
        player.play()
    }

    fun playResolved(stream: ResolvedStream, preferVideo: Boolean) {
        lastError = null
        val headers = mutableMapOf("User-Agent" to USER_AGENT)
        stream.referrer?.let { headers["Referer"] = it }
        httpFactory.setDefaultRequestProperties(headers)

        val videoUrl = stream.primaryUrl
        val audioUrl = stream.audioUrl
        if (!audioUrl.isNullOrBlank() && preferVideo && !stream.isAudioOnly) {
            val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUrl))
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
            player.setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else {
            val url = if (!preferVideo && !audioUrl.isNullOrBlank()) audioUrl else videoUrl
            player.setMediaItem(MediaItem.fromUri(url))
        }
        player.prepare()
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun togglePause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun setRate(rate: Float) {
        player.setPlaybackSpeed(rate.coerceIn(0.25f, 4f))
    }

    var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value.coerceIn(0f, 1f)
        }

    fun seekRatio(ratio: Double) {
        val duration = player.duration
        if (duration > 0) {
            player.seekTo((duration * ratio.coerceIn(0.0, 1.0)).roundToLong())
        }
    }

    fun seekBySeconds(seconds: Double) {
        val duration = player.duration
        val next = (player.currentPosition + (seconds * 1000).toLong())
        if (duration > 0) {
            player.seekTo(next.coerceIn(0, duration))
        } else {
            player.seekTo(next.coerceAtLeast(0))
        }
    }

    fun progressRatio(): Float {
        val duration = player.duration
        if (duration <= 0) return 0f
        return (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun release() {
        player.release()
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun guessSubtitleMime(path: String): String = when {
            path.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
            path.endsWith(".ass", true) || path.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}
