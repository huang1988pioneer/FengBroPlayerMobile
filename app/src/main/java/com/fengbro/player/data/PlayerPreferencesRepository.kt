package com.fengbro.player.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerPreferencesDataStore by preferencesDataStore(name = "player_preferences")

data class PlayerPreferences(
    val autoPlay: Boolean = true,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val playbackRate: Float = 1f,
    val videoFill: Boolean = false,
    val screenBrightness: Float = 0.55f,
)

class PlayerPreferencesRepository(context: Context) {
    private val store = context.applicationContext.playerPreferencesDataStore

    val preferences: Flow<PlayerPreferences> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toModel)

    suspend fun save(value: PlayerPreferences) {
        store.edit { preferences ->
            preferences[AUTO_PLAY] = value.autoPlay
            preferences[VOLUME] = value.volume.coerceIn(0f, 1f)
            preferences[MUTED] = value.muted
            preferences[PLAYBACK_RATE] = value.playbackRate.coerceIn(0.25f, 4f)
            preferences[VIDEO_FILL] = value.videoFill
            preferences[SCREEN_BRIGHTNESS] = value.screenBrightness.coerceIn(0.01f, 1f)
        }
    }

    suspend fun isMigrationComplete(name: String): Boolean =
        store.data.first()[stringPreferencesKey("migration_$name")] == "done"

    suspend fun markMigrationComplete(name: String) {
        store.edit { it[stringPreferencesKey("migration_$name")] = "done" }
    }

    private fun toModel(preferences: Preferences): PlayerPreferences = PlayerPreferences(
        autoPlay = preferences[AUTO_PLAY] ?: true,
        volume = (preferences[VOLUME] ?: 1f).coerceIn(0f, 1f),
        muted = preferences[MUTED] ?: false,
        playbackRate = (preferences[PLAYBACK_RATE] ?: 1f).coerceIn(0.25f, 4f),
        videoFill = preferences[VIDEO_FILL] ?: false,
        screenBrightness = (preferences[SCREEN_BRIGHTNESS] ?: 0.55f).coerceIn(0.01f, 1f),
    )

    private companion object {
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val VOLUME = floatPreferencesKey("volume")
        val MUTED = booleanPreferencesKey("muted")
        val PLAYBACK_RATE = floatPreferencesKey("playback_rate")
        val VIDEO_FILL = booleanPreferencesKey("video_fill")
        val SCREEN_BRIGHTNESS = floatPreferencesKey("screen_brightness")
    }
}
