package com.fengbro.player.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.core.model.RecentPlayEntry
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recent_entries", primaryKeys = ["collection", "identityKey"])
data class RecentEntryEntity(
    val collection: String,
    val identityKey: String,
    val title: String,
    val subtitle: String,
    val filePath: String?,
    val sourceUrl: String?,
    val kind: String,
    val duration: String,
    val format: String,
    val coverHue: Int,
    val bitrate: String,
    val playedAtEpochMillis: Long,
)

@Entity(tableName = "playlist_entries")
data class PlaylistEntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val position: Int,
    val title: String,
    val subtitle: String,
    val duration: String,
    val kind: String,
    val filePath: String?,
    val sourceUrl: String?,
    val coverHue: Int,
    val format: String,
    val bitrate: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val videoCodec: String,
    val audioCodec: String,
    val coverArt: ByteArray?,
    val persistableUri: String?,
    val displayName: String?,
    val sidecarSubtitleUri: String?,
    val sidecarLrcUri: String?,
)

@Dao
interface MediaLibraryDao {
    @Query("SELECT * FROM recent_entries WHERE collection = :collection ORDER BY playedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(collection: String, limit: Int): Flow<List<RecentEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(entry: RecentEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(entries: List<RecentEntryEntity>)

    @Query("DELETE FROM recent_entries WHERE collection = :collection AND identityKey = :identityKey")
    suspend fun removeRecent(collection: String, identityKey: String)

    @Query("DELETE FROM recent_entries WHERE collection = :collection")
    suspend fun clearRecent(collection: String)

    @Query("DELETE FROM recent_entries WHERE collection = :collection AND identityKey NOT IN (SELECT identityKey FROM recent_entries WHERE collection = :collection ORDER BY playedAtEpochMillis DESC LIMIT :limit)")
    suspend fun trimRecent(collection: String, limit: Int)

    @Transaction
    suspend fun upsertAndTrimRecent(entry: RecentEntryEntity, limit: Int) {
        upsertRecent(entry)
        trimRecent(entry.collection, limit)
    }

    @Query("UPDATE recent_entries SET title = :title, subtitle = CASE WHEN :uploader IS NULL OR :uploader = '' THEN subtitle ELSE :uploader END, duration = CASE WHEN :duration IS NULL OR :duration = '' THEN duration ELSE :duration END WHERE sourceUrl = :sourceUrl")
    suspend fun updateStreamMetadata(sourceUrl: String, title: String, duration: String?, uploader: String?)

    @Query("SELECT * FROM playlist_entries ORDER BY position")
    suspend fun loadPlaylist(): List<PlaylistEntryEntity>

    @Query("DELETE FROM playlist_entries")
    suspend fun clearPlaylist()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(entries: List<PlaylistEntryEntity>)

    @Transaction
    suspend fun replacePlaylist(entries: List<PlaylistEntryEntity>) {
        clearPlaylist()
        if (entries.isNotEmpty()) insertPlaylist(entries)
    }
}

@Database(
    entities = [RecentEntryEntity::class, PlaylistEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FengBroDatabase : RoomDatabase() {
    abstract fun mediaLibraryDao(): MediaLibraryDao

    companion object {
        @Volatile private var instance: FengBroDatabase? = null

        fun get(context: Context): FengBroDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FengBroDatabase::class.java,
                "fengbro-player.db",
            ).build().also { instance = it }
        }
    }
}

internal fun RecentEntryEntity.toModel(): RecentPlayEntry = RecentPlayEntry(
    title = title,
    subtitle = subtitle,
    filePath = filePath,
    sourceUrl = sourceUrl,
    kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.None),
    duration = duration,
    format = format,
    coverHue = coverHue,
    bitrate = bitrate,
    playedAtUtc = Instant.ofEpochMilli(playedAtEpochMillis),
)

internal fun RecentPlayEntry.toEntity(collection: String): RecentEntryEntity = RecentEntryEntity(
    collection = collection,
    identityKey = key,
    title = title,
    subtitle = subtitle,
    filePath = filePath,
    sourceUrl = sourceUrl,
    kind = kind.name,
    duration = duration,
    format = format,
    coverHue = coverHue,
    bitrate = bitrate,
    playedAtEpochMillis = playedAtUtc.toEpochMilli(),
)

internal fun PlaylistEntryEntity.toModel(): MediaItem = MediaItem(
    id = id,
    index = position + 1,
    title = title,
    subtitle = subtitle,
    duration = duration,
    kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.None),
    filePath = filePath,
    sourceUrl = sourceUrl,
    coverHue = coverHue,
    format = format,
    bitrate = bitrate,
    videoWidth = videoWidth,
    videoHeight = videoHeight,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    coverArt = coverArt,
    persistableUri = persistableUri,
    displayName = displayName,
    sidecarSubtitleUri = sidecarSubtitleUri,
    sidecarLrcUri = sidecarLrcUri,
)

internal fun MediaItem.toEntity(position: Int): PlaylistEntryEntity = PlaylistEntryEntity(
    id = id,
    position = position,
    title = title,
    subtitle = subtitle,
    duration = duration,
    kind = kind.name,
    filePath = filePath,
    sourceUrl = sourceUrl,
    coverHue = coverHue,
    format = format,
    bitrate = bitrate,
    videoWidth = videoWidth,
    videoHeight = videoHeight,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    coverArt = coverArt,
    persistableUri = persistableUri,
    displayName = displayName,
    sidecarSubtitleUri = sidecarSubtitleUri,
    sidecarLrcUri = sidecarLrcUri,
)
