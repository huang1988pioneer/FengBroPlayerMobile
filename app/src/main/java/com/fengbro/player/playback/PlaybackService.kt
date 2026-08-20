package com.fengbro.player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.fengbro.player.MainActivity
import com.fengbro.player.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var engine: PlayerHolder

    override fun onCreate() {
        super.onCreate()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        createNotificationChannel()
        engine = PlayerHolder(this)
        engine.sessionPlayer.onPlayNext = { notifyAppController(PlaybackProtocol.EVENT_NEXT) }
        engine.sessionPlayer.onPlayPrevious = { notifyAppController(PlaybackProtocol.EVENT_PREVIOUS) }
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
        mediaSession = MediaSession.Builder(this, engine.player)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutDown()
        super.onTaskRemoved(rootIntent)
    }

    private fun shutDown() {
        stopPlaybackAndForeground()
        mediaSession?.release()
        mediaSession = null
    }

    private fun stopPlaybackAndForeground() {
        engine.stop()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engine.sessionPlayer.onPlayNext = null
        engine.sessionPlayer.onPlayPrevious = null
        mediaSession?.release()
        mediaSession = null
        engine.release()
        super.onDestroy()
    }

    private fun notifyAppController(action: String) {
        val session = mediaSession ?: return
        val command = SessionCommand(action, Bundle.EMPTY)
        session.connectedControllers
            .filter { it.packageName == packageName }
            .forEach { session.sendCustomCommand(it, command, Bundle.EMPTY) }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            if (controller.packageName == packageName) {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(PlaybackProtocol.PLAY_URI, Bundle.EMPTY))
                    .add(SessionCommand(PlaybackProtocol.PLAY_RESOLVED, Bundle.EMPTY))
                    .add(SessionCommand(PlaybackProtocol.STOP_SERVICE, Bundle.EMPTY))
                    .build()
                builder.setAvailableSessionCommands(commands)
            }
            return builder.build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackProtocol.PLAY_URI -> PlaybackProtocol.playUri(engine, args)
                PlaybackProtocol.PLAY_RESOLVED -> PlaybackProtocol.playResolved(engine, args)
                PlaybackProtocol.STOP_SERVICE -> stopPlaybackAndForeground()
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
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
