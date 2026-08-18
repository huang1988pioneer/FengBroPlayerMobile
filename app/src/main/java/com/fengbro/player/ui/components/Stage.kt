package com.fengbro.player.ui.components

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import com.fengbro.player.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fengbro.player.core.gesture.TapZone
import com.fengbro.player.core.model.RecentPlayEntry
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
    onToggleChrome: () -> Unit,
    onDoubleTap: (TapZone) -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekDrag: (dx: Float, width: Float, finished: Boolean) -> Unit,
    onVerticalDragStart: () -> Unit,
    onVerticalDrag: (com.fengbro.player.core.gesture.DragKind, Float, Float, Boolean) -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenStream: () -> Unit,
    onPlayRecent: (RecentPlayEntry) -> Unit,
    gesturesEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgStage),
    ) {
        when {
            state.current == null -> {
                Landing(
                    recents = state.recent,
                    onOpenFile = onOpenFile,
                    onOpenFolder = onOpenFolder,
                    onOpenStream = onOpenStream,
                    onPlayRecent = onPlayRecent,
                )
            }
            state.isVideoStage -> {
                AndroidView(
                    factory = { context ->
                        (LayoutInflater.from(context).inflate(R.layout.player_view, null) as PlayerView).apply {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = playerHolder.player
                        }
                    },
                    update = { view ->
                        if (view.player !== playerHolder.player) {
                            view.player = playerHolder.player
                        }
                        view.resizeMode = if (state.videoFill) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                AudioStage(state = state, clock = clock)
            }
        }

        if (state.current != null && gesturesEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .playerGestures(
                        enabled = true,
                        onSingleTap = onToggleChrome,
                        onDoubleTap = onDoubleTap,
                        onLongPressStart = onLongPressStart,
                        onLongPressEnd = onLongPressEnd,
                        onSeekDrag = onSeekDrag,
                        onVerticalDrag = onVerticalDrag,
                        onVerticalDragStart = onVerticalDragStart,
                        onSeekDragStart = onSeekDragStart,
                    ),
            )
        }

        if (state.isMediaInfoVisible && state.current != null) {
            MediaInfoOverlay(state, clock)
        }
    }
}

@Composable
private fun Landing(
    recents: List<RecentPlayEntry>,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenStream: () -> Unit,
    onPlayRecent: (RecentPlayEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text("風哥播放器", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "點一下檔案就能播。手勢和 YouTube、MX、VLC 一樣。",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )
        Button(
            onClick = onOpenFile,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("開啟檔案", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onOpenFolder,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("資料夾")
            }
            OutlinedButton(
                onClick = onOpenStream,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("網路串流")
            }
        }

        if (recents.isNotEmpty()) {
            Text(
                "最近播放",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(recents.take(12), key = { it.key }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgCard)
                            .clickable { onPlayRecent(entry) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(entry.kindLabel, entry.playedAtText, entry.duration)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioStage(
    state: PlayerUiState,
    clock: PlaybackClock,
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverArt(state)
        if (state.hasLyrics && clock.currentLyric.isNotBlank()) {
            Text(
                text = clock.currentLyric,
                color = Accent,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(0.9f),
            )
        }
        Waveform(state.waveform, state.isPlaying, phase.value)
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
            .size(220.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.hsv(hue, 0.42f, 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("♪", color = Color.White, fontSize = 56.sp)
        }
    }
}

@Composable
private fun Waveform(bars: List<Float>, playing: Boolean, phase: Float) {
    val scale = if (playing) phase else 0.55f
    Canvas(
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth(0.7f)
            .height(36.dp),
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
private fun MediaInfoOverlay(state: PlayerUiState, clock: PlaybackClock) {
    val item = state.current ?: return
    val resolution = if (item.videoWidth > 0 && item.videoHeight > 0) {
        "${item.videoWidth}×${item.videoHeight} (${item.videoHeight}P)"
    } else {
        "—"
    }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .padding(18.dp)
            .clip(RoundedCornerShape(10.dp))
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
