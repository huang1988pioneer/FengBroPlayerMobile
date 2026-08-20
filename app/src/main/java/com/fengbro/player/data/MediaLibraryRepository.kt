package com.fengbro.player.data

import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.core.model.RecentPlayEntry
import com.fengbro.player.core.store.RecentStore
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistent media library module. Room ordering, limits and legacy migration are internal details. */
class MediaLibraryRepository(private val dao: MediaLibraryDao) {
    private val writeMutex = Mutex()

    val recents: Flow<List<RecentPlayEntry>> = dao.observeRecent(RECENT, RECENT_LIMIT)
        .map { entries -> entries.map(RecentEntryEntity::toModel) }
    val streams: Flow<List<RecentPlayEntry>> = dao.observeRecent(STREAM, STREAM_LIMIT)
        .map { entries -> entries.map(RecentEntryEntity::toModel) }

    suspend fun record(item: MediaItem) {
        if (!item.isPlayable) return
        recordEntry(RECENT, RecentPlayEntry.fromMedia(item), RECENT_LIMIT)
        if (item.isNetworkSource) recordEntry(STREAM, RecentPlayEntry.fromMedia(item), STREAM_LIMIT)
    }

    suspend fun recordUrl(
        url: String,
        title: String,
        kind: MediaKind,
        format: String,
    ) {
        recordEntry(
            STREAM,
            RecentPlayEntry(
                title = title,
                subtitle = url,
                sourceUrl = url,
                kind = kind,
                format = format,
                playedAtUtc = Instant.now(),
            ),
            STREAM_LIMIT,
        )
    }

    suspend fun removeRecent(entry: RecentPlayEntry) = dao.removeRecent(RECENT, entry.key)
    suspend fun removeStream(entry: RecentPlayEntry) = dao.removeRecent(STREAM, entry.key)
    suspend fun clearRecents() = dao.clearRecent(RECENT)
    suspend fun clearStreams() = dao.clearRecent(STREAM)

    suspend fun updateMetadata(sourceUrl: String, title: String, duration: String?, uploader: String?) =
        dao.updateStreamMetadata(sourceUrl, title, duration, uploader)

    suspend fun loadPlaylist(): List<MediaItem> = dao.loadPlaylist().map(PlaylistEntryEntity::toModel)

    suspend fun replacePlaylist(items: List<MediaItem>) = writeMutex.withLock {
        dao.replacePlaylist(items.mapIndexed { index, item -> item.toEntity(index) })
    }

    suspend fun migrateLegacy(
        recentFile: File,
        streamFile: File,
        preferences: PlayerPreferencesRepository,
    ) {
        if (preferences.isMigrationComplete(LEGACY_MIGRATION)) return
        writeMutex.withLock {
            val recentStore = RecentStore(recentFile, RECENT_LIMIT).apply { load() }
            val streamStore = RecentStore(streamFile, STREAM_LIMIT, requireSourceUrl = true).apply { load() }
            if (recentStore.snapshot.isNotEmpty()) {
                dao.upsertRecent(recentStore.snapshot.map { it.toEntity(RECENT) })
            }
            if (streamStore.snapshot.isNotEmpty()) {
                dao.upsertRecent(streamStore.snapshot.map { it.toEntity(STREAM) })
            }
            preferences.markMigrationComplete(LEGACY_MIGRATION)
        }
    }

    private suspend fun recordEntry(collection: String, entry: RecentPlayEntry, limit: Int) =
        writeMutex.withLock {
            dao.upsertAndTrimRecent(entry.copy(playedAtUtc = Instant.now()).toEntity(collection), limit)
        }

    private companion object {
        const val RECENT = "recent"
        const val STREAM = "stream"
        const val RECENT_LIMIT = 50
        const val STREAM_LIMIT = 30
        const val LEGACY_MIGRATION = "recent_json_to_room_v1"
    }
}
