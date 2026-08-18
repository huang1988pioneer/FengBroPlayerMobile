package com.fengbro.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengbro.player.FengBroApp
import com.fengbro.player.core.model.ChromeMode
import com.fengbro.player.ui.components.PlayerOverlay
import com.fengbro.player.ui.components.PlayerSettingsSheet
import com.fengbro.player.ui.components.PlayerStage
import com.fengbro.player.ui.components.PlaylistDock
import com.fengbro.player.ui.theme.Accent
import com.fengbro.player.ui.theme.BgApp
import com.fengbro.player.ui.theme.BgPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val clock by viewModel.clock.collectAsStateWithLifecycle()
    val playerHolder = FengBroApp.instance.playerHolder
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 700

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

    BackHandler(enabled = shouldHandleBack(state)) {
        viewModel.consumeBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp),
    ) {
        PlayerStage(
            state = state,
            clock = clock,
            playerHolder = playerHolder,
            onToggleChrome = viewModel::toggleChrome,
            onDoubleTap = viewModel::onDoubleTap,
            onLongPressStart = viewModel::startSpeedBoost,
            onLongPressEnd = viewModel::endSpeedBoost,
            onSeekDragStart = viewModel::beginSeekGesture,
            onSeekDrag = viewModel::applySeekGesture,
            onVerticalDragStart = viewModel::beginVerticalGesture,
            onVerticalDrag = viewModel::applyVerticalGesture,
            onOpenFile = { launchOpenMedia() },
            onOpenFolder = { launchOpenFolder() },
            onOpenStream = { viewModel.openNetworkDialog(true) },
            onPlayRecent = viewModel::playRecent,
            gesturesEnabled = !state.isInPictureInPicture && !state.isControlsLocked,
            modifier = Modifier.fillMaxSize(),
        )

        if (!state.isInPictureInPicture) PlayerOverlay(
            state = state,
            clock = clock,
            onTogglePlay = viewModel::togglePlay,
            onPrev = viewModel::playPrevious,
            onNext = viewModel::playNext,
            onBeginSeek = {
                viewModel.beginSeek()
                viewModel.onUserActivity()
            },
            onScrub = viewModel::scrubTo,
            onEndSeek = {
                viewModel.endSeek()
                viewModel.onUserActivity()
            },
            onTogglePlaylist = viewModel::togglePlaylist,
            onToggleFullscreen = viewModel::toggleFullscreen,
            onEnterPip = viewModel::enterPictureInPicture,
            onToggleLock = viewModel::toggleLock,
            onOpenSettings = viewModel::openSettings,
        )

        if (wide && state.isPlaylistVisible && !state.isInPictureInPicture) {
            PlaylistDock(
                state = state,
                onShowPane = viewModel::showDock,
                onSelect = {
                    viewModel.selectMedia(it)
                    viewModel.closePlaylist()
                },
                onPlayRecent = {
                    viewModel.playRecent(it)
                    viewModel.closePlaylist()
                },
                onRemoveRecent = viewModel::removeRecent,
                onRemoveStream = viewModel::removeStream,
                onClear = viewModel::clearDock,
                onAutoPlay = viewModel::setAutoPlay,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(320.dp)
                    .fillMaxHeight(),
            )
        }
    }

    if (!wide && state.isPlaylistVisible && !state.isInPictureInPicture) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closePlaylist,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgPanel,
        ) {
            PlaylistDock(
                state = state,
                onShowPane = viewModel::showDock,
                onSelect = {
                    viewModel.selectMedia(it)
                    viewModel.closePlaylist()
                },
                onPlayRecent = {
                    viewModel.playRecent(it)
                    viewModel.closePlaylist()
                },
                onRemoveRecent = viewModel::removeRecent,
                onRemoveStream = viewModel::removeStream,
                onClear = viewModel::clearDock,
                onAutoPlay = viewModel::setAutoPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 520.dp),
            )
        }
    }

    if (state.isSettingsVisible && !state.isInPictureInPicture) {
        PlayerSettingsSheet(
            state = state,
            onDismiss = viewModel::closeSettings,
            onRate = viewModel::setPlaybackRate,
            onToggleFill = viewModel::toggleVideoFill,
            onOpenSubtitle = {
                viewModel.closeSettings()
                launchSubtitle()
            },
            onClearSubtitle = {
                viewModel.clearSubtitle()
                viewModel.closeSettings()
            },
            onToggleInfo = {
                viewModel.toggleMediaInfo()
                viewModel.closeSettings()
            },
            onEnterPip = {
                viewModel.closeSettings()
                viewModel.enterPictureInPicture()
            },
            onStop = {
                viewModel.stopMedia()
                viewModel.closeSettings()
            },
            onOpenFile = {
                viewModel.closeSettings()
                launchOpenMedia()
            },
            onQueueFile = {
                viewModel.closeSettings()
                launchQueueMedia()
            },
            onOpenFolder = {
                viewModel.closeSettings()
                launchOpenFolder()
            },
            onOpenStream = {
                viewModel.closeSettings()
                viewModel.openNetworkDialog(true)
            },
        )
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

private fun shouldHandleBack(state: PlayerUiState): Boolean {
    if (state.isInPictureInPicture) return false
    return state.isControlsLocked ||
        state.isSettingsVisible ||
        state.isPlaylistVisible ||
        state.chrome == ChromeMode.Fullscreen
}
