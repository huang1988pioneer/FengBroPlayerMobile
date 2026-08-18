package com.fengbro.player

import android.app.Application
import com.fengbro.player.playback.PageStreamExtractor
import com.fengbro.player.playback.PlayerHolder

class FengBroApp : Application() {
    lateinit var playerHolder: PlayerHolder
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        PageStreamExtractor.initialize()
        playerHolder = PlayerHolder(this)
    }

    override fun onTerminate() {
        playerHolder.release()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: FengBroApp
            private set
    }
}
