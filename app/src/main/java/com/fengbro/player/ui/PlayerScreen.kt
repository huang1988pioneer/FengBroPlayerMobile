package com.fengbro.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengbro.player.FengBroApp
import com.fengbro.player.core.model.ChromeMode
import com.fengbro.player.ui.components.PlayerStage
import com.fengbro.player.ui.components.PlaylistDock
import com.fengbro.player.ui.components.TransportBar
import com.fengbro.player.ui.theme.Accent
import com.fengbro.player.ui.theme.BgApp
import com.fengbro.player.ui.theme.BgPanel
import com.fengbro.player.ui.theme.TextMuted
import com.fengbro.player.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val clock by viewModel.clock.collectAsStateWithLifecycle()
    val playerHolder = FengBroApp.instance.playerHolder
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 700
    var menuOpen by remember { mutableStateOf(false) }

    val openMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importUris(uris, selectFirst = true) }

    val queueMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importUris(uris, selectFirst = false) }

    val openFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::importFolder) }

    val openSubtitle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::attachSubtitle) }

    fun launchOpenMedia() = openMedia.launch(arrayOf("audio/*", "video/*"))
    fun launchQueueMedia() = queueMedia.launch(arrayOf("audio/*", "video/*"))
    fun launchOpenFolder() = openFolder.launch(null)
    fun launchSubtitle() = openSubtitle.launch(arrayOf("application/*", "text/*", "*/*"))

    BackHandler(enabled = state.chrome == ChromeMode.Fullscreen) {
        viewModel.notifyExitedFullscreen()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp)
            .pointerInput(state.chrome) {
                detectTapGestures { viewModel.onUserActivity() }
            },
    ) {
        AnimatedVisibility(visible = state.chrome != ChromeMode.Fullscreen) {
            TopAppBar(
                title = {
                    Text(
                        state.windowTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary,
                    )
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "選單", tint = TextPrimary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("開啟檔案…") }, onClick = { menuOpen = false; launchOpenMedia() })
                        DropdownMenuItem(text = { Text("加入檔案到清單…") }, onClick = { menuOpen = false; launchQueueMedia() })
                        DropdownMenuItem(text = { Text("開啟資料夾…") }, onClick = { menuOpen = false; launchOpenFolder() })
                        DropdownMenuItem(text = { Text("開啟網路串流…") }, onClick = { menuOpen = false; viewModel.openNetworkDialog(true) })
                        DropdownMenuItem(text = { Text("開啟字幕…") }, onClick = { menuOpen = false; launchSubtitle() })
                        DropdownMenuItem(text = { Text("關閉字幕") }, onClick = { menuOpen = false; viewModel.clearSubtitle() })
                        DropdownMenuItem(text = { Text("影片資訊") }, onClick = { menuOpen = false; viewModel.toggleMediaInfo() })
                        DropdownMenuItem(text = { Text("速度 0.75×") }, onClick = { menuOpen = false; viewModel.setPlaybackRate(0.75f) })
                        DropdownMenuItem(text = { Text("速度 1.0×") }, onClick = { menuOpen = false; viewModel.setPlaybackRate(1f) })
                        DropdownMenuItem(text = { Text("速度 1.25×") }, onClick = { menuOpen = false; viewModel.setPlaybackRate(1.25f) })
                        DropdownMenuItem(text = { Text("速度 1.5×") }, onClick = { menuOpen = false; viewModel.setPlaybackRate(1.5f) })
                        DropdownMenuItem(text = { Text("速度 2.0×") }, onClick = { menuOpen = false; viewModel.setPlaybackRate(2f) })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPanel, titleContentColor = TextPrimary),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxSize()) {
                PlayerStage(
                    state = state,
                    clock = clock,
                    playerHolder = playerHolder,
                    onTogglePlay = viewModel::togglePlay,
                    onToggleFullscreen = viewModel::toggleFullscreen,
                    onOpenFile = { launchOpenMedia() },
                    onOpenFolder = { launchOpenFolder() },
                    onOpenStream = { viewModel.openNetworkDialog(true) },
                    onSeekBack = { viewModel.seekRelative(-10.0) },
                    onSeekForward = { viewModel.seekRelative(10.0) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (wide && state.isPlaylistVisible) {
                    PlaylistDock(
                        state = state,
                        onShowPane = viewModel::showDock,
                        onSelect = viewModel::selectMedia,
                        onPlayRecent = viewModel::playRecent,
                        onRemoveRecent = viewModel::removeRecent,
                        onRemoveStream = viewModel::removeStream,
                        onClear = viewModel::clearDock,
                        onAutoPlay = viewModel::setAutoPlay,
                    )
                }
            }

            if (!wide && state.isPlaylistVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                        .clickable { viewModel.togglePlaylist() },
                )
                PlaylistDock(
                    state = state,
                    onShowPane = viewModel::showDock,
                    onSelect = {
                        viewModel.selectMedia(it)
                    },
                    onPlayRecent = viewModel::playRecent,
                    onRemoveRecent = viewModel::removeRecent,
                    onRemoveStream = viewModel::removeStream,
                    onClear = viewModel::clearDock,
                    onAutoPlay = viewModel::setAutoPlay,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        AnimatedVisibility(visible = state.isControlBarVisible) {
            TransportBar(
                state = state,
                clock = clock,
                onStop = viewModel::stopMedia,
                onPrev = viewModel::playPrevious,
                onTogglePlay = viewModel::togglePlay,
                onNext = viewModel::playNext,
                onSeekBack = { viewModel.seekRelative(-10.0) },
                onSeekForward = { viewModel.seekRelative(10.0) },
                onBeginSeek = viewModel::beginSeek,
                onScrub = viewModel::scrubTo,
                onEndSeek = viewModel::endSeek,
                onToggleMute = viewModel::toggleMute,
                onVolume = viewModel::setVolume,
                onTogglePlaylist = viewModel::togglePlaylist,
                onToggleFullscreen = viewModel::toggleFullscreen,
                onOpen = { launchOpenMedia() },
            )
        }

        AnimatedVisibility(visible = state.chrome != ChromeMode.Fullscreen) {
            Text(
                text = listOf(state.statusMessage, state.statusDetail).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgPanel)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    if (state.showNetworkDialog) {
        NetworkUrlDialog(
            initial = state.networkUrlDraft,
            onDismiss = viewModel::dismissNetworkDialog,
            onConfirm = viewModel::confirmNetworkUrl,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkUrlDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("開啟網路串流") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https:// 或 YouTube / Bilibili 網址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Accent,
                    cursorColor = Accent,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("播放", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
