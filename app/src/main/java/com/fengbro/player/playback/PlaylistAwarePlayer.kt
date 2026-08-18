package com.fengbro.player.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlaylistAwarePlayer(
    private val exoPlayer: ExoPlayer,
) : ForwardingPlayer(exoPlayer) {
    var onPlayNext: (() -> Unit)? = null
    var onPlayPrevious: (() -> Unit)? = null

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_STOP)
            .build()
    }

    override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return availableCommands.contains(command)
    }

    override fun seekToNext() {
        onPlayNext?.invoke() ?: super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        seekToNext()
    }

    override fun seekToPrevious() {
        onPlayPrevious?.invoke() ?: super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        seekToPrevious()
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true
}
