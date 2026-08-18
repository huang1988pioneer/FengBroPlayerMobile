package com.fengbro.player.ui

import com.fengbro.player.core.gesture.TapZone
import com.fengbro.player.core.model.ChromeMode
import com.fengbro.player.core.model.LrcLine
import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.core.model.RecentPlayEntry
import com.fengbro.player.core.model.SideDockPane

data class PlaybackClock(
    val progress: Float = 0f,
    val positionText: String = "00:00",
    val durationText: String = "00:00",
    val currentLyric: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

sealed class GestureHud {
    data object Hidden : GestureHud()
    data class SeekPreview(
        val positionMs: Long,
        val deltaSeconds: Double,
        val durationMs: Long,
    ) : GestureHud()
    data class Volume(val value: Float) : GestureHud()
    data class Brightness(val value: Float) : GestureHud()
    data class DoubleTapSeek(val zone: TapZone, val totalSeconds: Int) : GestureHud()
    data object SpeedBoost : GestureHud()
}

data class PlayerUiState(
    val playlist: List<MediaItem> = emptyList(),
    val recent: List<RecentPlayEntry> = emptyList(),
    val streams: List<RecentPlayEntry> = emptyList(),
    val current: MediaItem? = null,
    val lyrics: List<LrcLine> = emptyList(),
    val isPlaying: Boolean = false,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val autoPlay: Boolean = true,
    val playbackRate: Float = 1f,
    val isPlaylistVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val dockPane: SideDockPane = SideDockPane.Playlist,
    val chrome: ChromeMode = ChromeMode.Normal,
    val isChromeVisible: Boolean = true,
    val isControlBarVisible: Boolean = true,
    val isControlsLocked: Boolean = false,
    val isBoosting: Boolean = false,
    val isMediaInfoVisible: Boolean = false,
    val isInPictureInPicture: Boolean = false,
    val videoFill: Boolean = false,
    val hasSubtitle: Boolean = false,
    val subtitleName: String = "",
    val statusMessage: String = "就緒 — 可開啟本機音樂或影片檔案",
    val statusDetail: String = "",
    val flashMessage: String = "",
    val windowTitle: String = "鋒兄播放器",
    val activeKind: MediaKind = MediaKind.None,
    val coverBytes: ByteArray? = null,
    val waveform: List<Float> = seedWaveform(),
    val showNetworkDialog: Boolean = false,
    val networkPlayImmediately: Boolean = true,
    val networkUrlDraft: String = "",
    val playlistPositionText: String = "0 / 0",
    val hasLyrics: Boolean = false,
    val screenBrightness: Float = 0.55f,
    val gestureHud: GestureHud = GestureHud.Hidden,
) {
    val isVideoStage: Boolean get() = activeKind == MediaKind.Video
    val isAudioStage: Boolean get() = activeKind != MediaKind.Video
    val hasMedia: Boolean get() = current != null
    val hasRecent: Boolean get() = recent.isNotEmpty()
    val hasStreams: Boolean get() = streams.isNotEmpty()
}

fun seedWaveform(): List<Float> {
    val rnd = java.util.Random(42)
    return List(48) { i ->
        val envelope = kotlin.math.sin(i / 48.0 * Math.PI).toFloat()
        val noise = 0.25f + rnd.nextFloat() * 0.75f
        (envelope * noise).coerceIn(0.12f, 1f)
    }
}
