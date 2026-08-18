package com.fengbro.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengbro.player.core.model.ChromeMode
import com.fengbro.player.ui.PlaybackClock
import com.fengbro.player.ui.PlayerUiState
import com.fengbro.player.ui.theme.Accent
import com.fengbro.player.ui.theme.AccentGlow
import com.fengbro.player.ui.theme.BgControlBar
import com.fengbro.player.ui.theme.TextMuted
import com.fengbro.player.ui.theme.TextPrimary

@Composable
fun TransportBar(
    state: PlayerUiState,
    clock: PlaybackClock,
    onStop: () -> Unit,
    onPrev: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onBeginSeek: () -> Unit,
    onScrub: (Float) -> Unit,
    onEndSeek: () -> Unit,
    onToggleMute: () -> Unit,
    onVolume: (Float) -> Unit,
    onTogglePlaylist: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgControlBar)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(clock.positionText, color = TextMuted, fontSize = 11.sp)
            Slider(
                value = clock.progress,
                onValueChange = {
                    onBeginSeek()
                    onScrub(it)
                },
                onValueChangeFinished = onEndSeek,
                modifier = Modifier.weight(1f).height(22.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = AccentGlow,
                    inactiveTrackColor = TextMuted.copy(alpha = 0.35f),
                ),
            )
            Text(clock.durationText, color = TextMuted, fontSize = 11.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ChromeIcon(Icons.Filled.Stop, "停止", onStop)
            ChromeIcon(Icons.Filled.SkipPrevious, "上一首", onPrev)
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暫停",
                    tint = Accent,
                )
            }
            ChromeIcon(Icons.Filled.SkipNext, "下一首", onNext)
            ChromeIcon(Icons.Filled.FastRewind, "倒退 10 秒", onSeekBack)
            ChromeIcon(Icons.Filled.FastForward, "快轉 10 秒", onSeekForward)

            ChromeIcon(
                if (state.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                "靜音",
                onToggleMute,
            )
            Slider(
                value = if (state.isMuted) 0f else state.volume,
                onValueChange = onVolume,
                modifier = Modifier.widthIn(max = 110.dp).height(22.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = TextMuted.copy(alpha = 0.35f),
                ),
            )
            ChromeIcon(Icons.Filled.PlaylistPlay, "播放清單", onTogglePlaylist)
            ChromeIcon(
                if (state.chrome == ChromeMode.Fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                "全螢幕",
                onToggleFullscreen,
            )
            ChromeIcon(Icons.Filled.FolderOpen, "開啟檔案", onOpen)
        }
    }
}

@Composable
private fun ChromeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}
