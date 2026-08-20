package com.fengbro.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.fengbro.player.core.model.ResolvedStream
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToLong

/** UI-facing playback module. Service connection and custom commands stay behind this interface. */
class PlaybackConnection(context: Context) {
    private val appContext = context.applicationContext
    private val applicationExecutor = ContextCompat.getMainExecutor(appContext)
    private val listeners = linkedSetOf<Player.Listener>()
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    var onPlayNext: (() -> Unit)? = null
    var onPlayPrevious: (() -> Unit)? = null

    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    )
        .setListener(
            object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    listeners.forEach(controller::removeListener)
                    _player.value = null
                }

                override fun onCustomCommand(
                    controller: MediaController,
                    command: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (command.customAction) {
                        PlaybackProtocol.EVENT_NEXT -> onPlayNext?.invoke()
                        PlaybackProtocol.EVENT_PREVIOUS -> onPlayPrevious?.invoke()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            },
        )
        .buildAsync()

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { controller ->
                    _player.value = controller
                    listeners.forEach(controller::addListener)
                    val queued = synchronized(pendingActions) {
                        pendingActions.toList().also { pendingActions.clear() }
                    }
                    queued.forEach { it(controller) }
                }
            },
            applicationExecutor,
        )
    }

    val isPlaying: Boolean get() = _player.value?.isPlaying == true
    val hasMedia: Boolean get() = (_player.value?.mediaItemCount ?: 0) > 0
    val length: Long get() = _player.value?.duration?.coerceAtLeast(0) ?: 0
    val time: Long get() = _player.value?.currentPosition?.coerceAtLeast(0) ?: 0

    var volume: Float
        get() = _player.value?.volume ?: 1f
        set(value) = withController { it.volume = value.coerceIn(0f, 1f) }

    fun addListener(listener: Player.Listener) {
        listeners += listener
        _player.value?.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        listeners -= listener
        _player.value?.removeListener(listener)
    }

    fun playUri(
        uri: String,
        mimeType: String? = null,
        subtitleUri: String? = null,
        title: String? = null,
        artist: String? = null,
        artwork: ByteArray? = null,
        mediaId: String? = null,
        isVideo: Boolean = false,
    ) = send(
        PlaybackProtocol.PLAY_URI,
        PlaybackProtocol.uriArgs(uri, mimeType, subtitleUri, title, artist, artwork, mediaId, isVideo),
    )

    fun playResolved(stream: ResolvedStream, preferVideo: Boolean, title: String?, artist: String?) =
        send(PlaybackProtocol.PLAY_RESOLVED, PlaybackProtocol.resolvedArgs(stream, preferVideo, title, artist))

    fun pause() = withController(Player::pause)
    fun resume() = withController(Player::play)
    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    fun stopService() = send(PlaybackProtocol.STOP_SERVICE, Bundle.EMPTY)

    fun setRate(rate: Float) = withController { it.setPlaybackSpeed(rate.coerceIn(0.25f, 4f)) }

    fun seekRatio(ratio: Double) = withController { controller ->
        val duration = controller.duration
        if (duration > 0) controller.seekTo((duration * ratio.coerceIn(0.0, 1.0)).roundToLong())
    }

    fun seekBySeconds(seconds: Double) = withController { controller ->
        val duration = controller.duration
        val next = controller.currentPosition + (seconds * 1000).toLong()
        controller.seekTo(if (duration > 0) next.coerceIn(0, duration) else next.coerceAtLeast(0))
    }

    fun progressRatio(): Float {
        val controller = _player.value ?: return 0f
        if (controller.duration <= 0) return 0f
        return (controller.currentPosition.toFloat() / controller.duration).coerceIn(0f, 1f)
    }

    fun release() {
        onPlayNext = null
        onPlayPrevious = null
        synchronized(pendingActions) { pendingActions.clear() }
        onApplicationThread {
            listeners.clear()
            _player.value = null
            MediaController.releaseFuture(controllerFuture)
        }
    }

    private fun send(action: String, args: Bundle) = withController { controller ->
        controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    private fun withController(action: (MediaController) -> Unit) {
        onApplicationThread {
            val controller = _player.value as? MediaController
            if (controller != null) {
                action(controller)
            } else {
                synchronized(pendingActions) { pendingActions += action }
            }
        }
    }

    private fun onApplicationThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else applicationExecutor.execute(action)
    }
}
