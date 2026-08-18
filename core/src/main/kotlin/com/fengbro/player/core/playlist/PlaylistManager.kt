package com.fengbro.player.core.playlist

import com.fengbro.player.core.media.MediaMetadata
import com.fengbro.player.core.media.StreamUris
import com.fengbro.player.core.model.AudioInfo
import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.core.model.VideoInfo

data class ImportResult(
    val added: Int,
    val firstNewPlayable: MediaItem?,
    val firstExisting: MediaItem?,
    val statusMessage: String,
    val shouldSelect: MediaItem?,
)

data class NetworkAddResult(
    val item: MediaItem?,
    val alreadyExisted: Boolean,
    val statusMessage: String,
    val shouldSelect: MediaItem?,
    val normalizedUrl: String?,
)

class PlaylistManager {
    private val items = mutableListOf<MediaItem>()

    val snapshot: List<MediaItem> get() = items.toList()
    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    fun clear() {
        items.clear()
    }

    fun reindex() {
        items.forEachIndexed { index, item -> item.index = index + 1 }
    }

    fun indexOf(item: MediaItem?): Int {
        if (item == null) return -1
        return items.indexOfFirst { it.id == item.id }
    }

    fun findByIdentity(filePath: String? = null, sourceUrl: String? = null): MediaItem? {
        if (!filePath.isNullOrBlank()) {
            items.firstOrNull { it.filePath.equals(filePath, ignoreCase = true) }?.let { return it }
        }
        if (!sourceUrl.isNullOrBlank()) {
            items.firstOrNull { it.sourceUrl.equals(sourceUrl, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    fun findPlayable(fromIndex: Int, direction: Int): MediaItem? {
        if (items.isEmpty()) return null
        val n = items.size
        for (step in 1..n) {
            val idx = ((fromIndex + direction * step) % n + n) % n
            val item = items[idx]
            if (item.isPlayable) return item
        }
        return null
    }

    fun firstPlayable(): MediaItem? = items.firstOrNull { it.isPlayable }

    fun insert(index: Int, item: MediaItem) {
        items.add(index.coerceIn(0, items.size), item)
        reindex()
    }

    fun add(item: MediaItem) {
        items.add(item)
        reindex()
    }

    fun remove(item: MediaItem): Boolean {
        val removed = items.removeAll { it.id == item.id }
        if (removed) reindex()
        return removed
    }

    fun importPrepared(newItems: List<MediaItem>, selectFirst: Boolean): ImportResult {
        var added = 0
        var firstNewPlayable: MediaItem? = null
        var firstExisting: MediaItem? = null
        for (item in newItems) {
            val existing = findByIdentity(item.filePath, item.sourceUrl)
            if (existing != null) {
                if (firstExisting == null) firstExisting = existing
                continue
            }
            items.add(item)
            added++
            if (firstNewPlayable == null && item.isPlayable) firstNewPlayable = item
        }
        reindex()
        val shouldSelect = when {
            firstNewPlayable != null && selectFirst -> firstNewPlayable
            !selectFirst && added > 0 -> null
            added == 0 && firstExisting != null && selectFirst -> firstExisting
            else -> null
        }
        val status = when {
            firstNewPlayable != null && selectFirst -> "已加入 $added 個媒體檔案"
            !selectFirst && added > 0 -> "已加入 $added 個項目，等待播放"
            added == 0 && firstExisting != null && selectFirst -> "切換播放：${firstExisting.title}"
            added == 0 && newItems.isNotEmpty() -> "未辨識到可支援的媒體格式"
            else -> "未加入新檔案（可能重複）"
        }
        return ImportResult(added, firstNewPlayable, firstExisting, status, shouldSelect)
    }

    fun addNetworkUrl(
        rawUrl: String,
        playImmediately: Boolean,
    ): NetworkAddResult {
        val uri = StreamUris.tryNormalize(rawUrl)
            ?: return NetworkAddResult(
                item = null,
                alreadyExisted = false,
                statusMessage = "請輸入有效的串流網址（http/https/rtsp，可省略 https://）",
                shouldSelect = null,
                normalizedUrl = null,
            )

        val absolute = uri.toString()
        val path = uri.path.orEmpty()
        val isAudio = MediaMetadata.isAudio(path)
        val isVideo = MediaMetadata.isVideo(path) || MediaMetadata.looksLikeStreamPlaylist(path)
        val kind = if (isAudio && !isVideo) MediaKind.Audio else MediaKind.Video
        val title = when {
            StreamUris.needsExtraction(uri) && uri.host.orEmpty().contains("youtu", ignoreCase = true) -> "YouTube 影片"
            StreamUris.needsExtraction(uri) -> uri.host.orEmpty()
            else -> StreamUris.titleFromUri(uri)
        }
        val ext = path.substringAfterLast('.', "")
        val format = if (ext.isBlank() || ext.contains('/')) "URL" else ext.uppercase()

        val existing = findByIdentity(sourceUrl = absolute)
        if (existing != null) {
            return NetworkAddResult(
                item = existing,
                alreadyExisted = true,
                statusMessage = if (playImmediately) "切換播放：${existing.title}" else "串流已在播放清單中",
                shouldSelect = if (playImmediately) existing else null,
                normalizedUrl = absolute,
            )
        }

        val item = MediaItem(
            index = items.size + 1,
            title = title,
            subtitle = absolute,
            duration = "--:--",
            kind = kind,
            sourceUrl = absolute,
            coverHue = if (kind == MediaKind.Audio) 210 else 195,
            format = format,
        )
        if (playImmediately) items.add(0, item) else items.add(item)
        reindex()
        return NetworkAddResult(
            item = item,
            alreadyExisted = false,
            statusMessage = if (playImmediately) "已加入網路串流" else "串流已加入播放清單，等待播放",
            shouldSelect = if (playImmediately) item else null,
            normalizedUrl = absolute,
        )
    }

    companion object {
        fun fromLocalPath(
            path: String,
            audioInfo: AudioInfo? = null,
            videoInfo: VideoInfo? = null,
        ): MediaItem? {
            return when {
                MediaMetadata.isVideo(path) -> {
                    val info = videoInfo ?: MediaMetadata.fallbackVideo(path)
                    MediaItem(
                        index = 0,
                        title = info.title,
                        subtitle = "本機影片 · ${info.format}",
                        duration = info.duration,
                        kind = MediaKind.Video,
                        filePath = path,
                        coverHue = MediaMetadata.hueFromPath(path),
                        format = info.format,
                        videoWidth = info.width,
                        videoHeight = info.height,
                        videoCodec = info.videoCodec,
                        audioCodec = info.audioCodec,
                        persistableUri = path,
                        displayName = info.title,
                    )
                }
                MediaMetadata.isAudio(path) -> {
                    val info = audioInfo ?: MediaMetadata.fallbackAudio(path)
                    MediaItem(
                        index = 0,
                        title = info.title,
                        subtitle = info.artist,
                        duration = info.duration,
                        kind = MediaKind.Audio,
                        filePath = path,
                        coverHue = MediaMetadata.hueFromPath(path),
                        format = info.format,
                        bitrate = info.bitrate,
                        coverArt = info.coverArt,
                        persistableUri = path,
                        displayName = info.title,
                    )
                }
                else -> {
                    val name = MediaMetadata.displayStem(path)
                    val asVideo = path.contains("video", ignoreCase = true)
                    MediaItem(
                        index = 0,
                        title = name,
                        subtitle = if (asVideo) "本機影片" else "本機音樂",
                        duration = "—:—",
                        kind = if (asVideo) MediaKind.Video else MediaKind.Audio,
                        filePath = path,
                        coverHue = MediaMetadata.hueFromPath(path),
                        format = MediaMetadata.extensionLabel(path),
                        persistableUri = path,
                        displayName = name,
                    )
                }
            }
        }
    }
}
