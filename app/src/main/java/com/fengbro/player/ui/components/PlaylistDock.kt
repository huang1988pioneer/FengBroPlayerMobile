package com.fengbro.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.RecentPlayEntry
import com.fengbro.player.core.model.SideDockPane
import com.fengbro.player.ui.PlayerUiState
import com.fengbro.player.ui.theme.Accent
import com.fengbro.player.ui.theme.AccentGlow
import com.fengbro.player.ui.theme.BgHover
import com.fengbro.player.ui.theme.BgPanel
import com.fengbro.player.ui.theme.BgSelected
import com.fengbro.player.ui.theme.BorderSubtle
import com.fengbro.player.ui.theme.TextMuted
import com.fengbro.player.ui.theme.TextPrimary

@Composable
fun PlaylistDock(
    state: PlayerUiState,
    onShowPane: (SideDockPane) -> Unit,
    onSelect: (MediaItem) -> Unit,
    onPlayRecent: (RecentPlayEntry) -> Unit,
    onRemoveRecent: (RecentPlayEntry) -> Unit,
    onRemoveStream: (RecentPlayEntry) -> Unit,
    onClear: () -> Unit,
    onAutoPlay: (Boolean) -> Unit,
    sidePane: Boolean,
    modifier: Modifier = Modifier,
) {
    val chrome = modifier
        .background(BgPanel)
        .then(
            if (sidePane) {
                Modifier.border(width = 1.dp, color = BorderSubtle)
            } else {
                Modifier
            },
        )
        .then(
            if (sidePane) {
                Modifier.statusBarsPadding().navigationBarsPadding()
            } else {
                Modifier
            },
        )
    Column(modifier = chrome) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DockTab("清單", state.dockPane == SideDockPane.Playlist, Modifier.weight(1f)) {
                onShowPane(SideDockPane.Playlist)
            }
            DockTab("最近", state.dockPane == SideDockPane.Recent, Modifier.weight(1f)) {
                onShowPane(SideDockPane.Recent)
            }
            DockTab("串流", state.dockPane == SideDockPane.Streams, Modifier.weight(1f)) {
                onShowPane(SideDockPane.Streams)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.dockPane) {
                SideDockPane.Playlist -> {
                    Text("自動播放", color = TextMuted, fontSize = 11.sp)
                    Switch(
                        checked = state.autoPlay,
                        onCheckedChange = onAutoPlay,
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                    )
                    Text(state.playlistPositionText, color = TextMuted, fontSize = 11.sp)
                }
                SideDockPane.Recent -> Text("最多 50 筆，重開仍可讀取", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                SideDockPane.Streams -> Text("網路串流最多 30 筆", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            }
            Box(modifier = Modifier.weight(1f))
            Text(
                "清除",
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }

        when (state.dockPane) {
            SideDockPane.Playlist -> {
                if (state.playlist.isEmpty()) {
                    EmptyHint("播放清單是空的\n開啟檔案或網路串流後會出現在這裡")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        items(state.playlist, key = { it.id }) { item ->
                            PlaylistRow(item) { onSelect(item) }
                        }
                    }
                }
            }
            SideDockPane.Recent -> {
                if (!state.hasRecent) {
                    EmptyHint("尚無最近播放紀錄\n播放本機或網路媒體後會自動出現")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        items(state.recent, key = { it.key }) { entry ->
                            RecentRow(entry, onPlay = { onPlayRecent(entry) }, onRemove = { onRemoveRecent(entry) })
                        }
                    }
                }
            }
            SideDockPane.Streams -> {
                if (!state.hasStreams) {
                    EmptyHint("尚無網路串流紀錄\n開啟網路串流後會自動記錄")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        items(state.streams, key = { it.key }) { entry ->
                            StreamRow(entry, onPlay = { onPlayRecent(entry) }, onRemove = { onRemoveStream(entry) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) BgSelected else BgHover.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Accent else TextMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PlaylistRow(item: MediaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (item.isCurrent) BgSelected else ColorTransparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (item.isCurrent) "▶" else item.index.toString(),
            color = if (item.isCurrent) AccentGlow else TextMuted,
            fontSize = if (item.isCurrent) 11.sp else 12.sp,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(item.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(item.duration, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun RecentRow(entry: RecentPlayEntry, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPlay)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(entry.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${entry.playedAtText} · ${entry.duration}", color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "移除", tint = TextMuted)
        }
    }
}

@Composable
private fun StreamRow(entry: RecentPlayEntry, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPlay)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(entry.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.sourceUrl.orEmpty(), color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${entry.playedAtText} · ${entry.kindLabel}", color = TextMuted, fontSize = 10.sp)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "移除", tint = TextMuted)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
    }
}

private val ColorTransparent = androidx.compose.ui.graphics.Color.Transparent
