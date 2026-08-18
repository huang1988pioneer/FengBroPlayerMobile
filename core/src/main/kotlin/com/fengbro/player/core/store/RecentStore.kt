package com.fengbro.player.core.store

import com.fengbro.player.core.media.StreamUris
import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.core.model.RecentPlayEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.Collections

class RecentStore(
    private val file: File,
    private val maxEntries: Int,
    private val requireSourceUrl: Boolean = false,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val items = mutableListOf<RecentPlayEntry>()
    private var loaded = false

    val snapshot: List<RecentPlayEntry>
        get() = Collections.unmodifiableList(items)

    fun load() {
        if (loaded) return
        loaded = true
        if (!file.isFile) return
        runCatching {
            val dto = json.decodeFromString<RecentFileDto>(file.readText())
            items.clear()
            dto.entries.orEmpty().take(maxEntries).forEach { entry ->
                if (entry.title.isNullOrBlank()) return@forEach
                if (entry.filePath.isNullOrBlank() && entry.sourceUrl.isNullOrBlank()) return@forEach
                if (requireSourceUrl && entry.sourceUrl.isNullOrBlank()) return@forEach
                if (requireSourceUrl && StreamUris.tryNormalize(entry.sourceUrl) == null) return@forEach
                items += entry.toModel()
            }
        }.onFailure {
            items.clear()
        }
    }

    fun record(item: MediaItem): Boolean {
        if (!item.isPlayable) return false
        if (item.filePath.isNullOrBlank() && item.sourceUrl.isNullOrBlank()) return false
        if (requireSourceUrl && item.sourceUrl.isNullOrBlank()) return false
        return recordEntry(RecentPlayEntry.fromMedia(item))
    }

    fun recordUrl(
        url: String,
        title: String? = null,
        kind: MediaKind = MediaKind.Video,
        duration: String = "--:--",
        format: String = "URL",
        coverHue: Int = 195,
    ): Boolean {
        val uri = StreamUris.tryNormalize(url) ?: return false
        val displayTitle = title?.takeIf { it.isNotBlank() } ?: StreamUris.titleFromUri(uri)
        return recordEntry(
            RecentPlayEntry(
                title = displayTitle,
                subtitle = uri.toString(),
                sourceUrl = uri.toString(),
                kind = if (kind == MediaKind.Audio) MediaKind.Audio else MediaKind.Video,
                duration = duration.ifBlank { "--:--" },
                format = format.ifBlank { "URL" },
                coverHue = coverHue,
                playedAtUtc = Instant.now(),
            ),
        )
    }

    fun recordEntry(entry: RecentPlayEntry): Boolean {
        load()
        if (items.isNotEmpty() && items[0].key.equals(entry.key, ignoreCase = true)) {
            items[0].playedAtUtc = Instant.now()
            items[0].title = entry.title
            if (entry.subtitle.isNotBlank()) items[0].subtitle = entry.subtitle
            if (entry.duration.isNotBlank()) items[0].duration = entry.duration
            save()
            return true
        }
        items.removeAll { it.key.equals(entry.key, ignoreCase = true) }
        items.add(0, entry.copy(playedAtUtc = Instant.now()))
        while (items.size > maxEntries) items.removeAt(items.lastIndex)
        save()
        return true
    }

    fun updateMetadata(sourceUrl: String, title: String, duration: String?, uploader: String?) {
        load()
        items.filter { it.sourceUrl.equals(sourceUrl, ignoreCase = true) }.forEach { entry ->
            entry.title = title
            if (!uploader.isNullOrBlank()) entry.subtitle = uploader
            if (!duration.isNullOrBlank()) entry.duration = duration
        }
        save()
    }

    fun remove(entry: RecentPlayEntry): Boolean {
        load()
        val removed = items.removeAll { it.key == entry.key }
        if (removed) save()
        return removed
    }

    fun clear() {
        load()
        if (items.isEmpty()) return
        items.clear()
        save()
    }

    fun save() {
        load()
        file.parentFile?.mkdirs()
        val dto = RecentFileDto(entries = items.map { it.toDto() })
        runCatching { file.writeText(json.encodeToString(dto)) }
    }

    @Serializable
    private data class RecentFileDto(
        val entries: List<RecentEntryDto>? = emptyList(),
    )

    @Serializable
    private data class RecentEntryDto(
        val title: String? = null,
        val subtitle: String? = null,
        val filePath: String? = null,
        val sourceUrl: String? = null,
        val kind: String? = null,
        val duration: String? = null,
        val format: String? = null,
        val coverHue: Int? = null,
        val bitrate: String? = null,
        val playedAtUtc: String? = null,
    )

    private fun RecentEntryDto.toModel(): RecentPlayEntry = RecentPlayEntry(
        title = title.orEmpty(),
        subtitle = subtitle.orEmpty(),
        filePath = filePath,
        sourceUrl = sourceUrl,
        kind = when (kind?.lowercase()) {
            "video" -> MediaKind.Video
            "audio" -> MediaKind.Audio
            else -> MediaKind.None
        },
        duration = if (duration.isNullOrBlank()) "--:--" else duration,
        format = format.orEmpty(),
        coverHue = coverHue ?: 200,
        bitrate = bitrate.orEmpty(),
        playedAtUtc = runCatching { Instant.parse(playedAtUtc) }.getOrDefault(Instant.now()),
    )

    private fun RecentPlayEntry.toDto(): RecentEntryDto = RecentEntryDto(
        title = title,
        subtitle = subtitle,
        filePath = filePath,
        sourceUrl = sourceUrl,
        kind = kind.name,
        duration = duration,
        format = format,
        coverHue = coverHue,
        bitrate = bitrate,
        playedAtUtc = playedAtUtc.toString(),
    )
}
