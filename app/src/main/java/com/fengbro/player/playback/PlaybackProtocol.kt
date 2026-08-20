package com.fengbro.player.playback

import android.os.Bundle
import androidx.core.os.bundleOf
import com.fengbro.player.core.model.ResolvedStream

internal object PlaybackProtocol {
    const val PLAY_URI = "com.fengbro.player.command.PLAY_URI"
    const val PLAY_RESOLVED = "com.fengbro.player.command.PLAY_RESOLVED"
    const val STOP_SERVICE = "com.fengbro.player.command.STOP_SERVICE"
    const val EVENT_NEXT = "com.fengbro.player.event.NEXT"
    const val EVENT_PREVIOUS = "com.fengbro.player.event.PREVIOUS"

    private const val URI = "uri"
    private const val MIME_TYPE = "mimeType"
    private const val SUBTITLE_URI = "subtitleUri"
    private const val TITLE = "title"
    private const val ARTIST = "artist"
    private const val ARTWORK = "artwork"
    private const val MEDIA_ID = "mediaId"
    private const val IS_VIDEO = "isVideo"
    private const val PAGE_URL = "pageUrl"
    private const val PRIMARY_URL = "primaryUrl"
    private const val AUDIO_URL = "audioUrl"
    private const val IS_AUDIO_ONLY = "isAudioOnly"
    private const val DURATION = "duration"
    private const val UPLOADER = "uploader"
    private const val REFERRER = "referrer"
    private const val PREFER_VIDEO = "preferVideo"

    fun uriArgs(
        uri: String,
        mimeType: String?,
        subtitleUri: String?,
        title: String?,
        artist: String?,
        artwork: ByteArray?,
        mediaId: String?,
        isVideo: Boolean,
    ): Bundle = bundleOf(
        URI to uri,
        MIME_TYPE to mimeType,
        SUBTITLE_URI to subtitleUri,
        TITLE to title,
        ARTIST to artist,
        ARTWORK to artwork?.takeIf { it.size <= MAX_BINDER_ARTWORK_BYTES },
        MEDIA_ID to mediaId,
        IS_VIDEO to isVideo,
    )

    fun playUri(engine: PlayerHolder, args: Bundle) {
        val uri = args.getString(URI) ?: return
        engine.playUri(
            uri = uri,
            mimeType = args.getString(MIME_TYPE),
            subtitleUri = args.getString(SUBTITLE_URI),
            title = args.getString(TITLE),
            artist = args.getString(ARTIST),
            artwork = args.getByteArray(ARTWORK),
            mediaId = args.getString(MEDIA_ID),
            isVideo = args.getBoolean(IS_VIDEO),
        )
    }

    fun resolvedArgs(
        stream: ResolvedStream,
        preferVideo: Boolean,
        title: String?,
        artist: String?,
    ): Bundle = bundleOf(
        PAGE_URL to stream.pageUrl,
        TITLE to (title ?: stream.title),
        PRIMARY_URL to stream.primaryUrl,
        AUDIO_URL to stream.audioUrl,
        IS_AUDIO_ONLY to stream.isAudioOnly,
        DURATION to stream.duration,
        UPLOADER to (artist ?: stream.uploader),
        REFERRER to stream.referrer,
        PREFER_VIDEO to preferVideo,
    )

    fun playResolved(engine: PlayerHolder, args: Bundle) {
        val primaryUrl = args.getString(PRIMARY_URL) ?: return
        val stream = ResolvedStream(
            pageUrl = args.getString(PAGE_URL).orEmpty(),
            title = args.getString(TITLE).orEmpty(),
            primaryUrl = primaryUrl,
            audioUrl = args.getString(AUDIO_URL),
            isAudioOnly = args.getBoolean(IS_AUDIO_ONLY),
            duration = args.getString(DURATION),
            uploader = args.getString(UPLOADER),
            referrer = args.getString(REFERRER),
        )
        engine.playResolved(
            stream = stream,
            preferVideo = args.getBoolean(PREFER_VIDEO),
            title = args.getString(TITLE),
            artist = args.getString(UPLOADER),
        )
    }

    private const val MAX_BINDER_ARTWORK_BYTES = 256_000
}
