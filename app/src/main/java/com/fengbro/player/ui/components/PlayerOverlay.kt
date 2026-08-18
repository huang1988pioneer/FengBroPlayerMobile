package com.fengbro.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengbro.player.core.gesture.PlayerGestureMath
import com.fengbro.player.core.gesture.TapZone
import com.fengbro.player.core.media.MediaMetadata
import com.fengbro.player.core.model.ChromeMode
import com.fengbro.player.ui.GestureHud
import com.fengbro.player.ui.PlaybackClock
import com.fengbro.player.ui.PlayerUiState
import com.fengbro.player.ui.theme.Accent
import com.fengbro.player.ui.theme.AccentGlow
import com.fengbro.player.ui.theme.TextPrimary

@Composable
fun PlayerOverlay(
    state: PlayerUiState,
    clock: PlaybackClock,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBeginSeek: () -> Unit,
    onScrub: (Float) -> Unit,
    onEndSeek: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.current == null) return

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.isChromeVisible && !state.isControlsLocked,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopChrome(state, onToggleLock, onOpenSettings, Modifier.align(Alignment.TopCenter))
                CenterPlay(state.isPlaying, onTogglePlay, Modifier.align(Alignment.Center))
                if (state.hasLyrics && clock.currentLyric.isNotBlank()) {
                    Text(
                        text = clock.currentLyric,
                        color = Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 20.dp, end = 20.dp, bottom = 108.dp),
                    )
                }
                BottomChrome(
                    state = state,
                    clock = clock,
                    onPrev = onPrev,
                    onNext = onNext,
                    onBeginSeek = onBeginSeek,
                    onScrub = onScrub,
                    onEndSeek = onEndSeek,
                    onTogglePlaylist = onTogglePlaylist,
                    onToggleFullscreen = onToggleFullscreen,
                    onEnterPip = onEnterPip,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (!state.isChromeVisible && !state.isControlsLocked) {
            ThinProgress(
                progress = clock.progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 2.dp),
            )
        }

        if (state.isControlsLocked) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "解鎖控制", tint = TextPrimary)
            }
        }

        GestureHudLayer(state)
        FlashBanner(state.flashMessage, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun TopChrome(
    state: PlayerUiState,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xCC000000), Color(0x00000000)),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
            ) {
                Text(
                    text = state.current?.title.orEmpty(),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = state.current?.subtitle?.ifBlank { state.statusDetail }.orEmpty()
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OverlayIcon(Icons.Filled.LockOpen, "鎖定控制", onToggleLock)
            OverlayIcon(Icons.Filled.MoreVert, "更多", onOpenSettings)
        }
    }
}

@Composable
private fun CenterPlay(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onTogglePlay,
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color(0x66000000)),
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = "播放/暫停",
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun BottomChrome(
    state: PlayerUiState,
    clock: PlaybackClock,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBeginSeek: () -> Unit,
    onScrub: (Float) -> Unit,
    onEndSeek: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x00000000), Color(0xCC000000)),
                ),
            )
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeLabel(clock.positionText)
            ScrubBar(
                progress = clock.progress,
                onBegin = onBeginSeek,
                onScrub = onScrub,
                onEnd = onEndSeek,
                modifier = Modifier.weight(1f),
            )
            TimeLabel(clock.durationText)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OverlayIcon(Icons.Filled.SkipPrevious, "上一首", onPrev)
            OverlayIcon(Icons.Filled.SkipNext, "下一首", onNext)
            Spacer(Modifier.weight(1f))
            if (state.playbackRate != 1f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = AccentGlow, modifier = Modifier.size(16.dp))
                    Text("${trimRate(state.playbackRate)}×", color = AccentGlow, fontSize = 12.sp)
                }
            }
            OverlayIcon(Icons.AutoMirrored.Filled.PlaylistPlay, "播放清單", onTogglePlaylist)
            if (state.isVideoStage) {
                OverlayIcon(Icons.Filled.PictureInPictureAlt, "子母畫面", onEnterPip)
            }
            OverlayIcon(
                if (state.chrome == ChromeMode.Fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                if (state.chrome == ChromeMode.Fullscreen) "離開全螢幕" else "全螢幕",
                onToggleFullscreen,
            )
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.widthIn(min = 40.dp),
    )
}

@Composable
private fun ScrubBar(
    progress: Float,
    onBegin: () -> Unit,
    onScrub: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    if (!dragging) local = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onBegin()
                    onScrub(ratio)
                    onEnd()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onBegin()
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        local = ratio
                        onScrub(ratio)
                    },
                    onDragEnd = {
                        dragging = false
                        onEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onEnd()
                    },
                    onHorizontalDrag = { change, _ ->
                        val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        local = ratio
                        onScrub(ratio)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val trackH = size.height
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                cornerRadius = CornerRadius(trackH, trackH),
                size = size,
            )
            val played = size.width * local.coerceIn(0f, 1f)
            if (played > 0f) {
                drawRoundRect(
                    color = Accent,
                    cornerRadius = CornerRadius(trackH, trackH),
                    size = Size(played, trackH),
                )
            }
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(played.coerceIn(0f, size.width), trackH / 2f),
            )
        }
    }
}

@Composable
private fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        drawRect(Color.White.copy(alpha = 0.22f))
        drawRect(
            color = Accent,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
        )
    }
}

@Composable
private fun GestureHudLayer(state: PlayerUiState) {
    when (val hud = state.gestureHud) {
        GestureHud.Hidden -> Unit
        is GestureHud.DoubleTapSeek -> DoubleTapBurst(hud)
        is GestureHud.SeekPreview -> SeekPreviewChip(hud, Modifier)
        is GestureHud.Volume -> LevelHud(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "${(hud.value * 100).toInt()}%",
            value = hud.value,
            modifier = Modifier,
        )
        is GestureHud.Brightness -> LevelHud(
            icon = Icons.Filled.BrightnessHigh,
            label = "${(hud.value * 100).toInt()}%",
            value = hud.value,
            modifier = Modifier,
        )
        GestureHud.SpeedBoost -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    "2.0× 加速",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DoubleTapBurst(hud: GestureHud.DoubleTapSeek) {
    val align = if (hud.zone == TapZone.Left) Alignment.CenterStart else Alignment.CenterEnd
    val icon = if (hud.zone == TapZone.Left) Icons.Filled.FastRewind else Icons.Filled.FastForward
    Box(Modifier.fillMaxSize(), contentAlignment = align) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .background(
                    Brush.horizontalGradient(
                        if (hud.zone == TapZone.Left) {
                            listOf(Color(0x66FFFFFF), Color.Transparent)
                        } else {
                            listOf(Color.Transparent, Color(0x66FFFFFF))
                        },
                    ),
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text("${hud.totalSeconds} 秒", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SeekPreviewChip(hud: GestureHud.SeekPreview, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xD9000000))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                PlayerGestureMath.formatSignedSeconds(hud.deltaSeconds),
                color = AccentGlow,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${MediaMetadata.formatDuration(hud.positionMs)} / ${MediaMetadata.formatDuration(hud.durationMs)}",
                color = Color.White,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun LevelHud(
    icon: ImageVector,
    label: String,
    value: Float,
    modifier: Modifier,
) {
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), label = "level")
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xD9000000))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Column {
                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Canvas(Modifier.width(92.dp).height(4.dp)) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.25f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        size = size,
                    )
                    drawRoundRect(
                        color = Accent,
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        size = Size(size.width * animated, size.height),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashBanner(message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message.isNotBlank(),
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 56.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xCC111111))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OverlayIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

private fun trimRate(rate: Float): String {
    return if (rate == rate.toLong().toFloat()) rate.toLong().toString()
    else "%.2f".format(rate).trimEnd('0').trimEnd('.')
}
