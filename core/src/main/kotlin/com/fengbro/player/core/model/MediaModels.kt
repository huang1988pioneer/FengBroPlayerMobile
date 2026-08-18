package com.fengbro.player.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class MediaKind {
    None,
    Audio,
    Video,
}

enum class ChromeMode {
    Normal,
    Fullscreen,
    Compact,
}

enum class SideDockPane {
    Playlist,
    Recent,
    Streams,
}

data class LrcLine(
    val timeMs: Long,
    val text: String = "",
)

data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    var index: Int,
    var title: String,
    var subtitle: String = "",
    var duration: String = "--:--",
    val kind: MediaKind,
    val filePath: String? = null,
    val sourceUrl: String? = null,
    val coverHue: Int = 200,
    var format: String = "",
    var bitrate: String = "",
    var videoWidth: Int = 0,
    var videoHeight: Int = 0,
    var videoCodec: String = "",
    var audioCodec: String = "",
    var isCurrent: Boolean = false,
    var coverArt: ByteArray? = null,
    var persistableUri: String? = filePath,
    var displayName: String? = null,
    var sidecarSubtitleUri: String? = null,
    var sidecarLrcUri: String? = null,
) {
    val isLocalFile: Boolean get() = !filePath.isNullOrBlank()
    val isNetworkSource: Boolean get() = !sourceUrl.isNullOrBlank()
    val isPlayable: Boolean get() = isLocalFile || isNetworkSource
    val hasCoverArt: Boolean get() = (coverArt?.size ?: 0) > 0

    val identityKey: String
        get() = when {
            !filePath.isNullOrBlank() -> "file:" + filePath.trim()
            !sourceUrl.isNullOrBlank() -> "url:" + sourceUrl.trim()
            else -> "id:$id"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaItem) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class RecentPlayEntry(
    var title: String = "",
    var subtitle: String = "",
    val filePath: String? = null,
    val sourceUrl: String? = null,
    val kind: MediaKind = MediaKind.None,
    var duration: String = "--:--",
    var format: String = "",
    val coverHue: Int = 200,
    var bitrate: String = "",
    var playedAtUtc: Instant = Instant.now(),
) {
    val isLocalFile: Boolean get() = !filePath.isNullOrBlank()
    val isNetworkSource: Boolean get() = !sourceUrl.isNullOrBlank()

    val key: String
        get() = when {
            !filePath.isNullOrBlank() -> "file:" + filePath.trim()
            !sourceUrl.isNullOrBlank() -> "url:" + sourceUrl.trim()
            else -> "title:$title"
        }

    val kindLabel: String
        get() = when (kind) {
            MediaKind.Video -> "影片"
            MediaKind.Audio -> "音樂"
            MediaKind.None -> ""
        }

    val playedAtText: String
        get() {
            val zone = ZoneId.systemDefault()
            val local = playedAtUtc.atZone(zone)
            val today = LocalDate.now(zone)
            val date = local.toLocalDate()
            return when {
                date == today -> local.format(HM)
                date == today.minusDays(1) -> "昨天 " + local.format(HM)
                date.year == today.year -> local.format(MD_HM)
                else -> local.format(YMD)
            }
        }

    fun toMediaItem(index: Int): MediaItem = MediaItem(
        index = index,
        title = title,
        subtitle = subtitle,
        duration = duration,
        kind = kind,
        filePath = filePath,
        sourceUrl = sourceUrl,
        coverHue = coverHue,
        format = format,
        bitrate = bitrate,
        persistableUri = filePath,
    )

    companion object {
        private val HM = DateTimeFormatter.ofPattern("HH:mm")
        private val MD_HM = DateTimeFormatter.ofPattern("M/d HH:mm")
        private val YMD = DateTimeFormatter.ofPattern("yyyy/M/d")

        fun fromMedia(item: MediaItem, utcNow: Instant = Instant.now()): RecentPlayEntry =
            RecentPlayEntry(
                title = item.title,
                subtitle = item.subtitle,
                filePath = item.filePath,
                sourceUrl = item.sourceUrl,
                kind = item.kind,
                duration = item.duration,
                format = item.format,
                coverHue = item.coverHue,
                bitrate = item.bitrate,
                playedAtUtc = utcNow,
            )
    }
}

data class AudioInfo(
    val title: String,
    val artist: String,
    val duration: String,
    val format: String,
    val bitrate: String,
    val lyrics: String? = null,
    val lengthMs: Long = 0,
    val coverArt: ByteArray? = null,
)

data class VideoInfo(
    val title: String,
    val channel: String,
    val duration: String,
    val format: String,
    val lengthMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
)

data class ResolvedStream(
    val pageUrl: String,
    val title: String,
    val primaryUrl: String,
    val audioUrl: String? = null,
    val isAudioOnly: Boolean = false,
    val duration: String? = null,
    val uploader: String? = null,
    val referrer: String? = null,
)
