package com.fengbro.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fengbro.player.FengBroApp
import com.fengbro.player.MainActivity
import com.fengbro.player.R

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = FengBroApp.instance.playerHolder.player
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.notification_channel)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .also { provider ->
                    provider.setSmallIcon(R.drawable.ic_stat_play)
                },
        )
        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "fengbro_playback"
        const val SESSION_ID = "fengbro"
        const val NOTIFICATION_ID = 0xFB01
        const val ACTION_PIP_TOGGLE = "com.fengbro.player.action.PIP_TOGGLE"
        const val ACTION_PIP_NEXT = "com.fengbro.player.action.PIP_NEXT"
        const val ACTION_PIP_PREV = "com.fengbro.player.action.PIP_PREV"
    }
}
