package com.fengbro.player.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.playback.PlayerHolder
import com.fengbro.player.ui.PlaybackClock
import com.fengbro.player.ui.PlayerUiState
import com.fengbro.player.ui.theme.Accent
import com.fengbro.player.ui.theme.AccentGlow
import com.fengbro.player.ui.theme.AccentSoft
import com.fengbro.player.ui.theme.BgCard
import com.fengbro.player.ui.theme.BgStage
import com.fengbro.player.ui.theme.TextMuted
import com.fengbro.player.ui.theme.TextPrimary
import com.fengbro.player.ui.theme.TextSecondary

@OptIn(UnstableApi::class)
@Composable
fun PlayerStage(
    state: PlayerUiState,
    clock: PlaybackClock,
    playerHolder: PlayerHolder,
    onTogglePlay: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenStream: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgStage),
    ) {
        if (state.isVideoStage) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setKeepContentOnPlayerReset(true)
                        player = playerHolder.player
                    }
                },
                update = { view ->
                    if (view.player !== playerHolder.player) {
                        view.player = playerHolder.player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AudioStage(
                state = state,
                clock = clock,
                onOpenFile = onOpenFile,
                onOpenFolder = onOpenFolder,
                onOpenStream = onOpenStream,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.activeKind) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = size.width.toFloat()
                            when {
                                offset.x < w * 0.28f -> onSeekBack()
                                offset.x > w * 0.72f -> onSeekForward()
                                else -> onTogglePlay()
                            }
                        },
                        onDoubleTap = { onToggleFullscreen() },
                    )
                },
        )

        if (state.isMediaInfoVisible && state.current != null) {
            MediaInfoOverlay(state, clock)
        }
    }
}

@Composable
private fun AudioStage(
    state: PlayerUiState,
    clock: PlaybackClock,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenStream: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "wave")
    val phase = pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.current != null) {
            CoverArt(state)
            Text(
                text = state.current.title,
                color = TextPrimary,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(0.9f),
            )
            Text(
                text = state.current.subtitle.ifBlank { "本機音樂" },
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (state.hasLyrics && clock.currentLyric.isNotBlank()) {
                Text(
                    text = clock.currentLyric,
                    color = Accent,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth(0.9f),
                )
            }
            Waveform(state.waveform, state.isPlaying, phase.value)
        } else {
            Text("風哥播放器", color = TextPrimary, fontSize = 26.sp)
            Text(
                "開啟檔案、資料夾，或貼上網路串流網址",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton("開啟檔案", onOpenFile)
                ChipButton("開啟資料夾", onOpenFolder)
                ChipButton("開啟網路串流", onOpenStream)
            }
        }
    }
}

@Composable
private fun CoverArt(state: PlayerUiState) {
    val bytes = state.coverBytes
    val bitmap = remember(bytes) {
        if (bytes != null && bytes.size > 64) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } else {
            null
        }
    }
    val hue = (state.current?.coverHue ?: 200).toFloat()
    Box(
        modifier = Modifier
            .size(200.dp)
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.hsv(hue, 0.42f, 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♪", color = Color.White, fontSize = 48.sp)
                Text("無封面", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Waveform(bars: List<Float>, playing: Boolean, phase: Float) {
    val scale = if (playing) phase else 0.55f
    Canvas(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth(0.7f)
            .height(36.dp)
            .graphicsLayer { },
    ) {
        if (bars.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2f)
        bars.forEachIndexed { index, value ->
            val h = size.height * value * scale
            val x = index * (barWidth + gap)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(AccentSoft, AccentGlow)),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(2f, 2f),
                alpha = 0.85f,
            )
        }
    }
}

@Composable
private fun ChipButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = BgCard, contentColor = TextPrimary),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun MediaInfoOverlay(state: PlayerUiState, clock: PlaybackClock) {
    val item = state.current ?: return
    val resolution = if (item.videoWidth > 0 && item.videoHeight > 0) {
        "${item.videoWidth}×${item.videoHeight} (${item.videoHeight}P)"
    } else {
        "—"
    }
    Column(
        modifier = Modifier
            .padding(18.dp)
            .width(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE6171717))
            .padding(14.dp),
    ) {
        Text("影片資訊", color = Accent, fontSize = 12.sp)
        Text(item.title, color = TextPrimary, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotBlank()) Text(item.subtitle, color = TextSecondary, fontSize = 12.sp)
        Text(state.statusDetail.ifBlank { resolution }, color = TextMuted, fontSize = 12.sp)
        Text("${clock.positionText} / ${clock.durationText}", color = TextMuted, fontSize = 12.sp)
        if (state.hasSubtitle) Text("字幕：${state.subtitleName}", color = TextMuted, fontSize = 12.sp)
    }
}
