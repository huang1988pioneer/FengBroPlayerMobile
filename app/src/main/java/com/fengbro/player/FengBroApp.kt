package com.fengbro.player

import android.app.Application
import com.fengbro.player.data.FengBroDatabase
import com.fengbro.player.data.MediaLibraryRepository
import com.fengbro.player.data.PlayerPreferencesRepository
import com.fengbro.player.playback.PageStreamExtractor

class FengBroApp : Application() {
    lateinit var mediaLibrary: MediaLibraryRepository
        private set
    lateinit var playerPreferences: PlayerPreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        PageStreamExtractor.initialize()
        playerPreferences = PlayerPreferencesRepository(this)
        mediaLibrary = MediaLibraryRepository(FengBroDatabase.get(this).mediaLibraryDao())
    }

    companion object {
        lateinit var instance: FengBroApp
            private set
    }
}
