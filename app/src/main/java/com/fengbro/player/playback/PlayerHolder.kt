package com.fengbro.player.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.fengbro.player.core.model.ResolvedStream
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

internal class PlayerHolder(context: Context) {
    private val appContext = context.applicationContext

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val httpFactory = OkHttpDataSource.Factory(httpClient)
        .setUserAgent(USER_AGENT)

    private val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
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

    val sessionPlayer = PlaylistAwarePlayer(exoPlayer)
    val player: Player get() = sessionPlayer

    val isPlaying: Boolean get() = exoPlayer.isPlaying
    val hasMedia: Boolean get() = exoPlayer.mediaItemCount > 0 && exoPlayer.currentMediaItem != null
    val length: Long get() = exoPlayer.duration.coerceAtLeast(0)
    val time: Long get() = exoPlayer.currentPosition.coerceAtLeast(0)

    var lastError: String? = null
        private set

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                lastError = error.message ?: error.errorCodeName
            }
        })
    }

    fun playUri(
        uri: String,
        mimeType: String? = null,
        subtitleUri: String? = null,
        title: String? = null,
        artist: String? = null,
        artwork: ByteArray? = null,
        mediaId: String? = null,
        isVideo: Boolean = false,
    ) {
        lastError = null
        httpFactory.setDefaultRequestProperties(emptyMap())
        val builder = MediaItem.Builder().setUri(uri)
        if (!mediaId.isNullOrBlank()) builder.setMediaId(mediaId)
        if (!mimeType.isNullOrBlank()) builder.setMimeType(mimeType)
        builder.setMediaMetadata(buildMetadata(title, artist, artwork, isVideo))
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
        exoPlayer.setMediaItem(builder.build())
        if (!subtitleUri.isNullOrBlank()) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun playResolved(
        stream: ResolvedStream,
        preferVideo: Boolean,
        title: String? = null,
        artist: String? = null,
    ) {
        lastError = null
        val headers = mutableMapOf("User-Agent" to USER_AGENT)
        stream.referrer?.let { headers["Referer"] = it }
        httpFactory.setDefaultRequestProperties(headers)

        val displayTitle = title?.ifBlank { null } ?: stream.title
        val metadata = buildMetadata(displayTitle, artist ?: stream.uploader, artwork = null, isVideo = preferVideo && !stream.isAudioOnly)
        val videoUrl = stream.primaryUrl
        val audioUrl = stream.audioUrl
        if (!audioUrl.isNullOrBlank() && preferVideo && !stream.isAudioOnly) {
            val videoItem = MediaItem.Builder().setUri(videoUrl).setMediaMetadata(metadata).build()
            val audioItem = MediaItem.fromUri(audioUrl)
            val videoSource = mediaSourceFactory.createMediaSource(videoItem)
            val audioSource = mediaSourceFactory.createMediaSource(audioItem)
            exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else {
            val url = if (!preferVideo && !audioUrl.isNullOrBlank()) audioUrl else videoUrl
            exoPlayer.setMediaItem(
                MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build(),
            )
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        exoPlayer.play()
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun togglePause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun setRate(rate: Float) {
        exoPlayer.setPlaybackSpeed(rate.coerceIn(0.25f, 4f))
    }

    var volume: Float
        get() = exoPlayer.volume
        set(value) {
            exoPlayer.volume = value.coerceIn(0f, 1f)
        }

    fun seekRatio(ratio: Double) {
        val duration = exoPlayer.duration
        if (duration > 0) {
            exoPlayer.seekTo((duration * ratio.coerceIn(0.0, 1.0)).roundToLong())
        }
    }

    fun seekBySeconds(seconds: Double) {
        val duration = exoPlayer.duration
        val next = (exoPlayer.currentPosition + (seconds * 1000).toLong())
        if (duration > 0) {
            exoPlayer.seekTo(next.coerceIn(0, duration))
        } else {
            exoPlayer.seekTo(next.coerceAtLeast(0))
        }
    }

    fun progressRatio(): Float {
        val duration = exoPlayer.duration
        if (duration <= 0) return 0f
        return (exoPlayer.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun release() {
        sessionPlayer.onPlayNext = null
        sessionPlayer.onPlayPrevious = null
        exoPlayer.release()
    }

    private fun buildMetadata(
        title: String?,
        artist: String?,
        artwork: ByteArray?,
        isVideo: Boolean,
    ): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(title?.ifBlank { null } ?: appContext.getString(com.fengbro.player.R.string.app_name))
            .setArtist(artist?.ifBlank { null })
            .setAlbumTitle(appContext.getString(com.fengbro.player.R.string.app_name))
            .setIsPlayable(true)
            .setMediaType(
                if (isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC,
            )
        if (artwork != null && artwork.size > 64) {
            builder.setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return builder.build()
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
