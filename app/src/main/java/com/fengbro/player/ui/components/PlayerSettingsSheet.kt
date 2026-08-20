package com.fengbro.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fengbro.player.ui.PlayerUiState
import com.fengbro.player.ui.rememberPlayerWindowSpec
import com.fengbro.player.ui.theme.Accent
import com.fengbro.player.ui.theme.BgPanel
import com.fengbro.player.ui.theme.BorderSubtle
import com.fengbro.player.ui.theme.TextMuted
import com.fengbro.player.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerSettingsSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onRate: (Float) -> Unit,
    onToggleFill: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onClearSubtitle: () -> Unit,
    onOpenLyric: () -> Unit,
    onClearLyric: () -> Unit,
    onToggleInfo: () -> Unit,
    onEnterPip: () -> Unit,
    onStop: () -> Unit,
    onOpenFile: () -> Unit,
    onQueueFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenStream: () -> Unit,
) {
    val spec = rememberPlayerWindowSpec()
    if (spec.useDialogSettings) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                color = BgPanel,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .widthIn(min = 360.dp, max = 480.dp)
                    .heightIn(max = 640.dp),
            ) {
                SettingsBody(
                    state = state,
                    onDismiss = onDismiss,
                    onRate = onRate,
                    onToggleFill = onToggleFill,
                    onOpenSubtitle = onOpenSubtitle,
                    onClearSubtitle = onClearSubtitle,
                    onOpenLyric = onOpenLyric,
                    onClearLyric = onClearLyric,
                    onToggleInfo = onToggleInfo,
                    onEnterPip = onEnterPip,
                    onStop = onStop,
                    onOpenFile = onOpenFile,
                    onQueueFile = onQueueFile,
                    onOpenFolder = onOpenFolder,
                    onOpenStream = onOpenStream,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                )
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !spec.isCompactHeight),
            containerColor = BgPanel,
            contentColor = TextPrimary,
        ) {
            SettingsBody(
                state = state,
                onDismiss = onDismiss,
                onRate = onRate,
                onToggleFill = onToggleFill,
                onOpenSubtitle = onOpenSubtitle,
                onClearSubtitle = onClearSubtitle,
                onOpenLyric = onOpenLyric,
                onClearLyric = onClearLyric,
                onToggleInfo = onToggleInfo,
                onEnterPip = onEnterPip,
                onStop = onStop,
                onOpenFile = onOpenFile,
                onQueueFile = onQueueFile,
                onOpenFolder = onOpenFolder,
                onOpenStream = onOpenStream,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsBody(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onRate: (Float) -> Unit,
    onToggleFill: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onClearSubtitle: () -> Unit,
    onOpenLyric: () -> Unit,
    onClearLyric: () -> Unit,
    onToggleInfo: () -> Unit,
    onEnterPip: () -> Unit,
    onStop: () -> Unit,
    onOpenFile: () -> Unit,
    onQueueFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenStream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("播放速度", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                val selected = state.playbackRate == rate
                FilterChip(
                    selected = selected,
                    onClick = { onRate(rate); onDismiss() },
                    label = { Text(if (rate == 1f) "1×" else "${trimRate(rate)}×") },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent,
                        selectedLabelColor = TextPrimary,
                        containerColor = BorderSubtle,
                        labelColor = TextPrimary,
                    ),
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = BorderSubtle)
        SheetToggle("畫面填滿", state.videoFill, onToggleFill)
        if (state.isVideoStage) {
            SheetRow("載入字幕…", onOpenSubtitle)
            if (state.hasSubtitle) {
                SheetRow("關閉字幕", onClearSubtitle)
            }
        }
        if (state.current != null) {
            SheetRow("載入歌詞…", onOpenLyric)
            if (state.hasLyrics) {
                SheetRow("關閉歌詞", onClearLyric)
            }
        }
        SheetRow("影片資訊", onToggleInfo)
        if (state.isVideoStage && state.current != null) {
            SheetRow("子母畫面", onEnterPip)
        }
        SheetRow("停止播放", onStop)

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = BorderSubtle)
        Text("開啟", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        SheetRow("開啟檔案…", onOpenFile)
        SheetRow("加入檔案到清單…", onQueueFile)
        SheetRow("開啟資料夾…", onOpenFolder)
        SheetRow("開啟網路串流…", onOpenStream)
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = TextPrimary,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
private fun SheetToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
    ) {
        Text(label, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

private fun trimRate(rate: Float): String {
    return if (rate == rate.toLong().toFloat()) rate.toLong().toString()
    else "%.2f".format(rate).trimEnd('0').trimEnd('.')
}
