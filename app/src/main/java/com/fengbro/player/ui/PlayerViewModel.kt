package com.fengbro.player.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.fengbro.player.FengBroApp
import com.fengbro.player.core.gesture.DragKind
import com.fengbro.player.core.gesture.PlayerGestureMath
import com.fengbro.player.core.gesture.TapZone
import com.fengbro.player.core.lyrics.LrcParser
import com.fengbro.player.core.media.MediaMetadata
import com.fengbro.player.core.media.StreamUris
import com.fengbro.player.core.model.ChromeMode
import com.fengbro.player.core.model.LrcLine
import com.fengbro.player.core.model.MediaItem
import com.fengbro.player.core.model.MediaKind
import com.fengbro.player.core.model.RecentPlayEntry
import com.fengbro.player.core.model.SideDockPane
import com.fengbro.player.core.playlist.PlaylistManager
import com.fengbro.player.core.store.RecentStore
import com.fengbro.player.playback.LocalMetadataReader
import com.fengbro.player.playback.PageStreamExtractor
import com.fengbro.player.playback.PlaybackService
import com.fengbro.player.playback.PlayerHolder
import com.fengbro.player.playback.SidecarFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val playlist = PlaylistManager()
    private val recentStore = RecentStore(
        File(application.filesDir, "recent.json"),
        maxEntries = 50,
    )
    private val streamStore = RecentStore(
        File(application.filesDir, "recent-streams.json"),
        maxEntries = 30,
        requireSourceUrl = true,
    )
    private val engine: PlayerHolder = FengBroApp.instance.playerHolder

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val _clock = MutableStateFlow(PlaybackClock())
    val clock: StateFlow<PlaybackClock> = _clock.asStateFlow()

    private var selectGeneration = 0
    private var isSeeking = false
    private var seekTarget = 0f
    private var volumeBeforeMute = 1f
    private var playlistVisibleBeforeFs = true
    private var suppressChrome = false
    private var hideBarJob: Job? = null
    private var extractJob: Job? = null
    private var subtitleUri: String? = null
    private var subtitleOwner: String? = null
    private var subtitleSuppressedFor: String? = null
    private var flashJob: Job? = null
    private var hudClearJob: Job? = null
    private var stackResetJob: Job? = null
    private var stackedSeekSeconds = 0
    private var lastDoubleTapZone: TapZone? = null
    private var savedRateBeforeBoost = 1f
    private var gestureVolumeStart = 1f
    private var gestureBrightnessStart = 0.55f
    private var gestureSeekStartRatio = 0f
    var requestEnterPip: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                scheduleHideChrome()
            } else if (!_ui.value.isControlsLocked) {
                showChrome(autoHide = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) onEndReached()
            if (playbackState == Player.STATE_READY) {
                val duration = engine.length
                if (duration > 0) {
                    _clock.update {
                        it.copy(durationText = MediaMetadata.formatDuration(duration))
                    }
                    _ui.update { state ->
                        val current = state.current ?: return@update state
                        if (current.duration == "--:--" || current.duration == "—:—") {
                            current.duration = MediaMetadata.formatDuration(duration)
                        }
                        publishLists(state)
                    }
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val message = "無法播放此媒體：${error.message ?: error.errorCodeName}"
            _ui.update {
                it.copy(
                    isPlaying = false,
                    statusMessage = message,
                    flashMessage = message,
                )
            }
        }
    }

    init {
        recentStore.load()
        streamStore.load()
        engine.sessionPlayer.onPlayNext = { playNext() }
        engine.sessionPlayer.onPlayPrevious = { playPrevious() }
        engine.player.addListener(playerListener)
        refreshStores()
        viewModelScope.launch {
            while (isActive) {
                tickClock()
                delay(200)
            }
        }
    }

    override fun onCleared() {
        engine.player.removeListener(playerListener)
        engine.sessionPlayer.onPlayNext = null
        engine.sessionPlayer.onPlayPrevious = null
        requestEnterPip = null
        super.onCleared()
    }

    fun importUris(uris: List<Uri>, selectFirst: Boolean = true) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                val named = uris.map { uri ->
                    uri to LocalMetadataReader.displayName(
                        getApplication<Application>().contentResolver,
                        uri,
                    )
                }
                val subtitleByStem = SidecarFiles.pairSubtitles(named)
                val lyricByStem = SidecarFiles.pairLyrics(named)
                named.mapNotNull { (uri, name) ->
                    if (MediaMetadata.isSubtitle(name) || MediaMetadata.isLyric(name)) {
                        persistRead(uri)
                        null
                    } else {
                        val stem = MediaMetadata.displayStem(name).lowercase()
                        buildLocalItem(uri, name, subtitleByStem[stem], lyricByStem[stem])
                    }
                }
            }
            val result = playlist.importPrepared(prepared, selectFirst)
            publishLists(_ui.value.copy(statusMessage = result.statusMessage))
            result.shouldSelect?.let { selectMedia(it) }
        }
    }

    fun importFolder(treeUri: Uri) {
        persistRead(treeUri)
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(getApplication(), treeUri)
                val (media, extras) = collectFolderEntries(root)
                val namedExtras = extras.map { it.uri to it.name.orEmpty() }
                val subtitleByStem = SidecarFiles.pairSubtitles(namedExtras)
                val lyricByStem = SidecarFiles.pairLyrics(namedExtras)
                (subtitleByStem.values + lyricByStem.values).forEach(::persistRead)
                media.mapNotNull { file ->
                    val name = file.name.orEmpty()
                    val stem = MediaMetadata.displayStem(name).lowercase()
                    buildLocalItem(file.uri, name, subtitleByStem[stem], lyricByStem[stem])
                }
            }
            if (files.isEmpty()) {
                _ui.update { it.copy(statusMessage = "此資料夾及子資料夾沒有支援的媒體檔") }
                return@launch
            }
            val result = playlist.importPrepared(files, selectFirst = true)
            publishLists(_ui.value.copy(statusMessage = result.statusMessage))
            result.shouldSelect?.let { selectMedia(it) }
        }
    }

    fun openNetworkDialog(playImmediately: Boolean = true) {
        _ui.update {
            it.copy(
                showNetworkDialog = true,
                networkPlayImmediately = playImmediately,
            )
        }
    }

    fun dismissNetworkDialog() {
        _ui.update { it.copy(showNetworkDialog = false, statusMessage = "已取消開啟網路串流") }
    }

    fun confirmNetworkUrl(raw: String) {
        _ui.update { it.copy(showNetworkDialog = false, networkUrlDraft = raw) }
        val playImmediately = _ui.value.networkPlayImmediately
        val result = playlist.addNetworkUrl(raw, playImmediately)
        if (result.normalizedUrl != null) {
            streamStore.recordUrl(
                result.normalizedUrl!!,
                result.item?.title,
                result.item?.kind ?: MediaKind.Video,
                format = result.item?.format ?: "URL",
            )
        }
        publishLists(_ui.value.copy(statusMessage = result.statusMessage))
        result.shouldSelect?.let { selectMedia(it) }
    }

    fun selectMedia(item: MediaItem) {
        extractJob?.cancel()
        val generation = ++selectGeneration
        markCurrent(item)
        _ui.update {
            it.copy(
                activeKind = item.kind,
                isPlaying = item.isPlayable,
            )
        }
        if (!item.isPlayable) {
            engine.stop()
            _ui.update { it.copy(statusMessage = "示範媒體（無檔案）：${item.title} — 請開啟本機檔案") }
            return
        }
        engine.volume = if (_ui.value.isMuted) 0f else _ui.value.volume
        engine.setRate(_ui.value.playbackRate)
        when {
            item.isLocalFile && item.filePath != null -> playLocal(item, generation)
            item.isNetworkSource && item.sourceUrl != null -> playNetwork(item, generation)
        }
        recordRecent(item)
    }

    fun togglePlay() {
        val current = _ui.value.current
        if (current == null) {
            playlist.firstPlayable()?.let { selectMedia(it) }
            return
        }
        if (!current.isPlayable) {
            _ui.update { it.copy(isPlaying = !it.isPlaying) }
            return
        }
        if (engine.isPlaying) {
            engine.pause()
            showChrome(autoHide = false)
            return
        }
        if (engine.hasMedia) {
            engine.resume()
            engine.setRate(_ui.value.playbackRate)
            showChrome(autoHide = true)
        } else {
            selectMedia(current)
        }
    }

    fun stopMedia() {
        engine.stop()
        _clock.update { PlaybackClock(durationText = _ui.value.current?.duration ?: "00:00") }
        _ui.update {
            it.copy(
                isPlaying = false,
                statusMessage = it.current?.let { media -> "已停止：${media.title}" } ?: "已停止",
            )
        }
    }

    fun playPrevious() {
        if (playlist.isEmpty) return
        val from = playlist.indexOf(_ui.value.current).let { if (it < 0) 0 else it }
        val prev = playlist.findPlayable(from, -1)
        if (prev == null) {
            stopMedia()
            _ui.update { it.copy(statusMessage = "清單中沒有可播放的媒體") }
        } else {
            selectMedia(prev)
        }
    }

    fun playNext() {
        if (playlist.isEmpty) return
        val from = playlist.indexOf(_ui.value.current)
        val next = playlist.findPlayable(from, 1)
        if (next == null) {
            stopMedia()
            _ui.update { it.copy(statusMessage = "清單中沒有可播放的媒體") }
        } else {
            selectMedia(next)
        }
    }

    fun playRecent(entry: RecentPlayEntry) {
        val existing = playlist.findByIdentity(entry.filePath, entry.sourceUrl)
        if (existing != null) {
            selectMedia(existing)
            return
        }
        val item = entry.toMediaItem(playlist.size + 1)
        playlist.insert(0, item)
        publishLists(_ui.value)
        selectMedia(item)
    }

    fun removeRecent(entry: RecentPlayEntry) {
        recentStore.remove(entry)
        _ui.update { it.copy(statusMessage = "已自最近播放移除：${entry.title}") }
        refreshStores()
    }

    fun removeStream(entry: RecentPlayEntry) {
        streamStore.remove(entry)
        _ui.update { it.copy(statusMessage = "已自最近串流移除：${entry.title}") }
        refreshStores()
    }

    fun showDock(pane: SideDockPane) {
        _ui.update {
            it.copy(
                dockPane = pane,
                isPlaylistVisible = true,
                statusMessage = when (pane) {
                    SideDockPane.Playlist -> it.statusMessage
                    SideDockPane.Recent ->
                        if (it.recent.isEmpty()) "尚無最近播放紀錄" else "最近播放：${it.recent.size} 筆"
                    SideDockPane.Streams ->
                        if (it.streams.isEmpty()) "尚無網路串流播放紀錄" else "最近網路串流：${it.streams.size} 筆"
                },
            )
        }
    }

    fun togglePlaylist() {
        val next = !_ui.value.isPlaylistVisible
        _ui.update { it.copy(isPlaylistVisible = next, isSettingsVisible = if (next) false else it.isSettingsVisible) }
    }

    fun closePlaylist() {
        _ui.update { it.copy(isPlaylistVisible = false) }
    }

    fun openSettings() {
        _ui.update { it.copy(isSettingsVisible = true, isPlaylistVisible = false) }
        onUserActivity()
    }

    fun closeSettings() {
        _ui.update { it.copy(isSettingsVisible = false) }
    }

    fun clearDock() {
        when (_ui.value.dockPane) {
            SideDockPane.Recent -> {
                recentStore.clear()
                _ui.update { it.copy(statusMessage = "已清除最近播放紀錄") }
                refreshStores()
            }
            SideDockPane.Streams -> {
                streamStore.clear()
                _ui.update { it.copy(statusMessage = "已清除最近網路串流紀錄") }
                refreshStores()
            }
            SideDockPane.Playlist -> {
                engine.stop()
                playlist.clear()
                _clock.value = PlaybackClock()
                _ui.update {
                    it.copy(
                        current = null,
                        activeKind = MediaKind.None,
                        isPlaying = false,
                        coverBytes = null,
                        lyrics = emptyList(),
                        hasLyrics = false,
                        statusDetail = "",
                        windowTitle = appName(),
                        statusMessage = "已清除播放清單",
                        playlistPositionText = "0 / 0",
                    )
                }
                publishLists(_ui.value)
            }
        }
    }

    fun setAutoPlay(value: Boolean) {
        _ui.update { it.copy(autoPlay = value) }
    }

    fun setVolume(value: Float) {
        val vol = value.coerceIn(0f, 1f)
        engine.volume = if (_ui.value.isMuted) 0f else vol
        _ui.update {
            it.copy(
                volume = vol,
                isMuted = if (vol > 0f && it.isMuted) false else it.isMuted,
            )
        }
        if (vol > 0f && _ui.value.isMuted) {
            _ui.update { it.copy(isMuted = false) }
        }
    }

    fun nudgeVolume(delta: Float) {
        setVolume(_ui.value.volume + delta)
    }

    fun toggleMute() {
        if (_ui.value.isMuted) {
            _ui.update { it.copy(isMuted = false) }
            engine.volume = volumeBeforeMute.coerceIn(0.05f, 1f)
            _ui.update { it.copy(volume = volumeBeforeMute.coerceIn(0.05f, 1f), statusMessage = "已取消靜音") }
        } else {
            volumeBeforeMute = _ui.value.volume.takeIf { it > 0f } ?: 1f
            engine.volume = 0f
            _ui.update { it.copy(isMuted = true, statusMessage = "已靜音") }
        }
    }

    fun setPlaybackRate(rate: Float) {
        val clamped = rate.coerceIn(0.25f, 4f)
        engine.setRate(clamped)
        _ui.update { it.copy(playbackRate = clamped, statusMessage = "播放速度 ${"%.2f".format(clamped)}×") }
        flash("速度 ${trimRate(clamped)}×")
    }

    fun toggleVideoFill() {
        val next = !_ui.value.videoFill
        _ui.update { it.copy(videoFill = next) }
        flash(if (next) "畫面填滿" else "原始比例")
    }

    fun seekRelative(seconds: Double) {
        if (_ui.value.current?.isPlayable != true) return
        engine.seekBySeconds(seconds)
        publishClockFromEngine()
    }

    fun beginSeek() {
        isSeeking = true
    }

    fun scrubTo(progress: Float) {
        val ratio = progress.coerceIn(0f, 1f)
        seekTarget = ratio
        val duration = engine.length.coerceAtLeast(_clock.value.durationMs)
        val positionMs = if (duration > 0) (duration * ratio).toLong() else 0L
        _clock.update {
            it.copy(
                progress = ratio,
                positionMs = positionMs,
                durationMs = if (duration > 0) duration else it.durationMs,
                positionText = MediaMetadata.formatDuration(positionMs),
            )
        }
    }

    fun endSeek() {
        if (isSeeking && _ui.value.current?.isPlayable == true) {
            engine.seekRatio(seekTarget.toDouble())
            _clock.update { it.copy(progress = seekTarget) }
        }
        isSeeking = false
    }

    fun toggleFullscreen() {
        if (suppressChrome) return
        val next = if (_ui.value.chrome == ChromeMode.Fullscreen) ChromeMode.Normal else ChromeMode.Fullscreen
        applyChrome(next)
    }

    fun notifyExitedFullscreen() {
        if (_ui.value.chrome != ChromeMode.Fullscreen) return
        suppressChrome = true
        applyChrome(ChromeMode.Normal)
        suppressChrome = false
    }

    fun onUserActivity() {
        if (_ui.value.current == null || _ui.value.isControlsLocked) return
        showChrome(autoHide = _ui.value.isPlaying)
    }

    fun toggleChrome() {
        if (_ui.value.current == null) return
        if (_ui.value.isControlsLocked) {
            flash("控制已鎖定，點解鎖即可")
            return
        }
        if (_ui.value.isChromeVisible) hideChrome() else showChrome(autoHide = _ui.value.isPlaying)
    }

    fun showChrome(autoHide: Boolean = true) {
        if (_ui.value.isControlsLocked) return
        hideBarJob?.cancel()
        _ui.update { it.copy(isChromeVisible = true, isControlBarVisible = true) }
        if (autoHide) scheduleHideChrome()
    }

    fun hideChrome() {
        hideBarJob?.cancel()
        _ui.update { it.copy(isChromeVisible = false, isControlBarVisible = false) }
    }

    fun toggleLock() {
        val locked = !_ui.value.isControlsLocked
        hideBarJob?.cancel()
        _ui.update {
            it.copy(
                isControlsLocked = locked,
                isChromeVisible = !locked,
                isControlBarVisible = !locked,
                isPlaylistVisible = if (locked) false else it.isPlaylistVisible,
                isSettingsVisible = if (locked) false else it.isSettingsVisible,
            )
        }
        flash(if (locked) "控制已鎖定" else "控制已解鎖")
    }

    fun onDoubleTap(zone: TapZone) {
        if (_ui.value.current == null || _ui.value.isControlsLocked) return
        if (zone == TapZone.Center) {
            togglePlay()
            return
        }
        if (lastDoubleTapZone != zone) stackedSeekSeconds = 0
        lastDoubleTapZone = zone
        stackedSeekSeconds += PlayerGestureMath.DOUBLE_TAP_STEP_SECONDS
        seekRelative(if (zone == TapZone.Left) -10.0 else 10.0)
        _ui.update { it.copy(gestureHud = GestureHud.DoubleTapSeek(zone, stackedSeekSeconds)) }
        stackResetJob?.cancel()
        stackResetJob = viewModelScope.launch {
            delay(800)
            stackedSeekSeconds = 0
            lastDoubleTapZone = null
            clearHudIf { it is GestureHud.DoubleTapSeek }
        }
    }

    fun startSpeedBoost() {
        val current = _ui.value.current
        if (current?.isPlayable != true || _ui.value.isControlsLocked || _ui.value.isBoosting) return
        savedRateBeforeBoost = _ui.value.playbackRate
        engine.setRate(2f)
        hideChrome()
        _ui.update { it.copy(isBoosting = true, gestureHud = GestureHud.SpeedBoost) }
    }

    fun endSpeedBoost() {
        if (!_ui.value.isBoosting) return
        engine.setRate(savedRateBeforeBoost)
        _ui.update { it.copy(isBoosting = false, gestureHud = GestureHud.Hidden) }
        scheduleHideChrome()
    }

    fun beginVerticalGesture() {
        hideBarJob?.cancel()
        gestureVolumeStart = if (_ui.value.isMuted) 0f else _ui.value.volume
        gestureBrightnessStart = _ui.value.screenBrightness
    }

    fun applyVerticalGesture(kind: DragKind, dy: Float, height: Float, finished: Boolean) {
        if (_ui.value.current == null || _ui.value.isControlsLocked) return
        val adj = PlayerGestureMath.verticalAdjustment(dy, height)
        when (kind) {
            DragKind.Volume -> {
                val vol = (gestureVolumeStart + adj).coerceIn(0f, 1f)
                setVolume(vol)
                _ui.update { it.copy(gestureHud = GestureHud.Volume(vol)) }
            }
            DragKind.Brightness -> {
                val brightness = (gestureBrightnessStart + adj).coerceIn(0.01f, 1f)
                _ui.update { it.copy(screenBrightness = brightness, gestureHud = GestureHud.Brightness(brightness)) }
            }
            DragKind.Seek -> Unit
        }
        if (finished) {
            scheduleClearHud(400) { it is GestureHud.Volume || it is GestureHud.Brightness }
            scheduleHideChrome()
        }
    }

    fun beginSeekGesture() {
        if (_ui.value.current?.isPlayable != true || _ui.value.isControlsLocked) return
        hideBarJob?.cancel()
        beginSeek()
        gestureSeekStartRatio = _clock.value.progress
    }

    fun applySeekGesture(dx: Float, width: Float, finished: Boolean) {
        if (!isSeeking || _ui.value.current?.isPlayable != true) {
            if (finished) isSeeking = false
            return
        }
        val duration = engine.length.coerceAtLeast(_clock.value.durationMs)
        val delta = PlayerGestureMath.seekDeltaSeconds(dx, width, duration / 1000.0)
        val startMs = gestureSeekStartRatio * duration
        val targetMs = (startMs + delta * 1000.0).coerceIn(0.0, duration.toDouble().coerceAtLeast(0.0))
        val ratio = if (duration > 0) (targetMs / duration).toFloat() else 0f
        scrubTo(ratio)
        _ui.update {
            it.copy(gestureHud = GestureHud.SeekPreview(targetMs.toLong(), delta, duration))
        }
        if (finished) {
            endSeek()
            scheduleClearHud(400) { hud -> hud is GestureHud.SeekPreview }
            scheduleHideChrome()
        }
    }

    fun consumeBack(): Boolean {
        val state = _ui.value
        return when {
            state.isControlsLocked -> {
                toggleLock()
                true
            }
            state.isSettingsVisible -> {
                closeSettings()
                true
            }
            state.isPlaylistVisible -> {
                closePlaylist()
                true
            }
            state.chrome == ChromeMode.Fullscreen -> {
                notifyExitedFullscreen()
                true
            }
            else -> false
        }
    }

    fun toggleMediaInfo() {
        if (!_ui.value.isVideoStage) return
        _ui.update { it.copy(isMediaInfoVisible = !it.isMediaInfoVisible) }
    }

    fun attachSubtitle(uri: Uri) {
        if (!_ui.value.isVideoStage) {
            _ui.update { it.copy(statusMessage = "字幕僅適用於影片播放模式") }
            return
        }
        persistRead(uri)
        subtitleUri = uri.toString()
        subtitleOwner = _ui.value.current?.identityKey
        subtitleSuppressedFor = null
        _ui.value.current?.sidecarSubtitleUri = subtitleUri
        val name = LocalMetadataReader.displayName(getApplication<Application>().contentResolver, uri)
        _ui.update { it.copy(hasSubtitle = true, subtitleName = name, statusMessage = "已載入字幕：$name") }
        flash("已載入字幕")
        _ui.value.current?.let { selectMedia(it) }
    }

    fun clearSubtitle() {
        subtitleUri = null
        subtitleOwner = null
        subtitleSuppressedFor = _ui.value.current?.identityKey
        _ui.update { it.copy(hasSubtitle = false, subtitleName = "", statusMessage = "已關閉字幕") }
        flash("已關閉字幕")
        _ui.value.current?.let { if (it.kind == MediaKind.Video) selectMedia(it) }
    }

    fun removeFromPlaylist(item: MediaItem) {
        val wasCurrent = _ui.value.current?.id == item.id
        playlist.remove(item)
        if (wasCurrent) {
            val next = playlist.firstPlayable()
            if (next != null) selectMedia(next) else stopMedia().also {
                _ui.update { it.copy(current = null, activeKind = MediaKind.None, windowTitle = appName()) }
            }
        }
        publishLists(_ui.value.copy(statusMessage = "已自清單移除：${item.title}"))
    }

    fun handleIncoming(uri: Uri?, extraText: String?) {
        when {
            uri != null && (uri.scheme == "content" || uri.scheme == "file") ->
                importUris(listOf(uri), selectFirst = true)
            uri != null && (uri.scheme == "http" || uri.scheme == "https") ->
                confirmNetworkUrl(uri.toString())
            !extraText.isNullOrBlank() && StreamUris.tryNormalize(extraText) != null ->
                confirmNetworkUrl(extraText)
        }
    }

    private fun applyChrome(mode: ChromeMode) {
        if (mode == ChromeMode.Fullscreen) {
            playlistVisibleBeforeFs = _ui.value.isPlaylistVisible
            _ui.update {
                it.copy(
                    chrome = mode,
                    isPlaylistVisible = false,
                    isSettingsVisible = false,
                    isChromeVisible = true,
                    isControlBarVisible = true,
                )
            }
            onUserActivity()
        } else {
            hideBarJob?.cancel()
            _ui.update {
                it.copy(
                    chrome = mode,
                    isPlaylistVisible = playlistVisibleBeforeFs,
                    isChromeVisible = true,
                    isControlBarVisible = true,
                )
            }
            scheduleHideChrome()
        }
    }

    private fun scheduleHideChrome() {
        hideBarJob?.cancel()
        if (!_ui.value.isPlaying || _ui.value.isControlsLocked || isSeeking || _ui.value.isBoosting) return
        if (_ui.value.current == null) return
        hideBarJob = viewModelScope.launch {
            delay(3_000)
            if (!isSeeking && _ui.value.isPlaying && !_ui.value.isBoosting && !_ui.value.isControlsLocked) {
                _ui.update { it.copy(isChromeVisible = false, isControlBarVisible = false) }
            }
        }
    }

    private fun flash(message: String) {
        _ui.update { it.copy(flashMessage = message) }
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(2_400)
            _ui.update { state ->
                if (state.flashMessage == message) state.copy(flashMessage = "") else state
            }
        }
    }

    private fun scheduleClearHud(delayMs: Long, predicate: (GestureHud) -> Boolean) {
        hudClearJob?.cancel()
        hudClearJob = viewModelScope.launch {
            delay(delayMs)
            clearHudIf(predicate)
        }
    }

    private fun clearHudIf(predicate: (GestureHud) -> Boolean) {
        _ui.update { state ->
            if (predicate(state.gestureHud)) state.copy(gestureHud = GestureHud.Hidden) else state
        }
    }

    private fun trimRate(rate: Float): String {
        return if (rate == rate.toLong().toFloat()) {
            rate.toLong().toString()
        } else {
            "%.2f".format(rate).trimEnd('0').trimEnd('.')
        }
    }

    private fun playLocal(item: MediaItem, generation: Int) {
        val resolvedSubtitle = resolveSubtitle(item)
        engine.playUri(
            uri = item.filePath!!,
            subtitleUri = resolvedSubtitle,
            title = item.title,
            artist = item.subtitle,
            artwork = item.coverArt,
            mediaId = item.id,
            isVideo = item.kind == MediaKind.Video,
        )
        startPlaybackService()
        _ui.update {
            it.copy(
                isPlaying = true,
                statusMessage = if (!resolvedSubtitle.isNullOrBlank()) {
                    "正在播放：${item.title} · 同名字幕"
                } else {
                    "正在播放：${item.title}"
                },
            )
        }
        if (generation != selectGeneration) return
    }

    private fun playNetwork(item: MediaItem, generation: Int) {
        val url = item.sourceUrl ?: return
        if (StreamUris.needsExtraction(url)) {
            _ui.update { it.copy(statusMessage = "正在解析網路串流（${PageStreamExtractor.serviceLabel(url)}）…") }
            extractJob = viewModelScope.launch {
                val resolved = PageStreamExtractor.resolve(url)
                if (generation != selectGeneration) return@launch
                if (resolved == null) {
                    _ui.update {
                        it.copy(
                            isPlaying = false,
                            statusMessage = "無法解析此網頁影片。請改貼可直接播放的媒體網址，或稍後再試。",
                        )
                    }
                    return@launch
                }
                applyNetworkMetadata(url, resolved.title, resolved.duration, resolved.uploader)
                engine.playResolved(
                    resolved,
                    preferVideo = item.kind == MediaKind.Video,
                    title = resolved.title,
                    artist = resolved.uploader,
                )
                startPlaybackService()
                _ui.update { it.copy(isPlaying = true, statusMessage = "正在播放：${resolved.title}") }
            }
            return
        }
        engine.playUri(
            uri = url,
            title = item.title,
            artist = item.subtitle,
            mediaId = item.id,
            isVideo = item.kind == MediaKind.Video,
        )
        startPlaybackService()
        _ui.update { it.copy(isPlaying = true, statusMessage = "正在播放網路媒體：${item.title}") }
    }

    private fun applyNetworkMetadata(sourceUrl: String, title: String, duration: String?, uploader: String?) {
        playlist.snapshot.filter { it.sourceUrl.equals(sourceUrl, ignoreCase = true) }.forEach { item ->
            item.title = title
            if (!uploader.isNullOrBlank()) item.subtitle = uploader
            if (!duration.isNullOrBlank()) item.duration = duration
        }
        recentStore.updateMetadata(sourceUrl, title, duration, uploader)
        streamStore.updateMetadata(sourceUrl, title, duration, uploader)
        _ui.update { state ->
            val current = state.current
            val nextTitle = if (current?.sourceUrl.equals(sourceUrl, ignoreCase = true)) {
                "$title — ${appName()}"
            } else {
                state.windowTitle
            }
            publishLists(
                state.copy(
                    windowTitle = nextTitle,
                    statusDetail = listOf(current?.format, uploader, if (current?.kind == MediaKind.Video) "影片" else "音樂")
                        .filter { !it.isNullOrBlank() }
                        .joinToString(" · "),
                ),
            )
        }
    }

    private fun markCurrent(item: MediaItem) {
        playlist.snapshot.forEach { it.isCurrent = it.id == item.id }
        val lyrics = loadLyrics(item)
        _ui.update {
            it.copy(
                current = item,
                windowTitle = "${item.title} — ${appName()}",
                statusDetail = listOf(item.format, item.bitrate, if (item.kind == MediaKind.Video) "影片" else "音樂")
                    .filter { part -> part.isNotBlank() }
                    .joinToString(" · "),
                coverBytes = item.coverArt,
                lyrics = lyrics,
                hasLyrics = lyrics.isNotEmpty(),
                activeKind = item.kind,
            )
        }
        _clock.update {
            PlaybackClock(durationText = item.duration.ifBlank { "00:00" })
        }
        showChrome(autoHide = true)
        publishLists(_ui.value)
    }

    private fun recordRecent(item: MediaItem) {
        recentStore.record(item)
        if (item.isNetworkSource) streamStore.record(item)
        refreshStores()
    }

    private fun onEndReached() {
        _ui.update { it.copy(isPlaying = false) }
        if (_ui.value.autoPlay) playNext()
    }

    private fun tickClock() {
        if (isSeeking || !_ui.value.isPlaying) return
        publishClockFromEngine()
    }

    private fun publishClockFromEngine() {
        val duration = engine.length
        val time = engine.time
        val lyric = LrcParser.currentLine(_ui.value.lyrics, time)?.text.orEmpty()
        _clock.update {
            it.copy(
                progress = engine.progressRatio(),
                positionText = MediaMetadata.formatDuration(time),
                durationText = if (duration > 0) MediaMetadata.formatDuration(duration) else it.durationText,
                currentLyric = lyric,
                positionMs = time,
                durationMs = if (duration > 0) duration else it.durationMs,
            )
        }
    }

    private fun publishLists(base: PlayerUiState): PlayerUiState {
        val current = base.current
        val index = playlist.indexOf(current)
        val next = base.copy(
            playlist = playlist.snapshot,
            recent = recentStore.snapshot,
            streams = streamStore.snapshot,
            playlistPositionText = if (playlist.isEmpty) {
                "0 / 0"
            } else {
                "${if (index >= 0) index + 1 else 0} / ${playlist.size}"
            },
        )
        _ui.value = next
        return next
    }

    private fun refreshStores() {
        _ui.update {
            it.copy(
                recent = recentStore.snapshot,
                streams = streamStore.snapshot,
                playlist = playlist.snapshot,
            )
        }
    }

    fun enterPictureInPicture() {
        if (!canEnterPip()) {
            flash("子母畫面僅適用於影片")
            return
        }
        requestEnterPip?.invoke()
    }

    fun canEnterPip(): Boolean = _ui.value.isVideoStage && _ui.value.current != null

    fun setInPictureInPicture(value: Boolean) {
        _ui.update {
            it.copy(
                isInPictureInPicture = value,
                isChromeVisible = if (value) false else it.isChromeVisible,
                isControlBarVisible = if (value) false else it.isControlBarVisible,
                isPlaylistVisible = if (value) false else it.isPlaylistVisible,
                isSettingsVisible = if (value) false else it.isSettingsVisible,
            )
        }
        if (!value && !_ui.value.isControlsLocked) {
            showChrome(autoHide = _ui.value.isPlaying)
        }
    }

    fun pipAspect(): Pair<Int, Int> {
        val item = _ui.value.current
        val width = item?.videoWidth ?: 0
        val height = item?.videoHeight ?: 0
        if (width <= 0 || height <= 0) return 16 to 9
        val max = 2.39
        var w = width.toDouble()
        var h = height.toDouble()
        if (w / h > max) w = h * max
        if (h / w > max) h = w * max
        return w.toInt().coerceAtLeast(1) to h.toInt().coerceAtLeast(1)
    }

    private fun appName(): String = getApplication<Application>().getString(com.fengbro.player.R.string.app_name)

    private fun startPlaybackService() {
        val context = getApplication<Application>()
        val intent = Intent(context, PlaybackService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun resolveSubtitle(item: MediaItem): String? {
        if (item.kind != MediaKind.Video) return null
        if (subtitleSuppressedFor == item.identityKey) {
            subtitleUri = null
            subtitleOwner = null
            _ui.update { it.copy(hasSubtitle = false, subtitleName = "") }
            return null
        }
        if (subtitleOwner == item.identityKey && !subtitleUri.isNullOrBlank()) {
            return subtitleUri
        }
        val found = item.sidecarSubtitleUri
            ?: item.filePath?.let { path ->
                val uri = Uri.parse(path)
                SidecarFiles.findSubtitle(
                    getApplication(),
                    uri,
                    item.displayName,
                )?.also(::persistRead)?.toString()
            }
        if (found.isNullOrBlank()) {
            subtitleUri = null
            subtitleOwner = null
            _ui.update { it.copy(hasSubtitle = false, subtitleName = "") }
            return null
        }
        item.sidecarSubtitleUri = found
        subtitleUri = found
        subtitleOwner = item.identityKey
        val label = LocalMetadataReader.displayName(
            getApplication<Application>().contentResolver,
            Uri.parse(found),
        )
        _ui.update {
            it.copy(
                hasSubtitle = true,
                subtitleName = label,
                statusMessage = "已載入同名字幕：$label",
            )
        }
        flash("已載入同名字幕")
        return found
    }

    private fun loadLyrics(item: MediaItem): List<LrcLine> {
        val found = item.sidecarLrcUri
            ?: item.filePath?.let { path ->
                SidecarFiles.findLyric(
                    getApplication(),
                    Uri.parse(path),
                    item.displayName,
                )?.also(::persistRead)?.toString()
            }
        if (found.isNullOrBlank()) return emptyList()
        item.sidecarLrcUri = found
        val lines = readLrc(Uri.parse(found))
        if (lines.isNotEmpty()) {
            flash("已載入同名歌詞")
        }
        return lines
    }

    private fun readLrc(uri: Uri): List<LrcLine> {
        val bytes = runCatching {
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).takeIf { file -> file.isFile }?.readBytes() }
                else -> getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }.getOrNull()
        return if (bytes == null || bytes.isEmpty()) emptyList() else LrcParser.parseBytes(bytes)
    }

    private fun buildLocalItem(
        uri: Uri,
        nameHint: String? = null,
        pairedSubtitle: Uri? = null,
        pairedLyric: Uri? = null,
    ): MediaItem? {
        val resolver = getApplication<Application>().contentResolver
        val name = nameHint ?: LocalMetadataReader.displayName(resolver, uri)
        val path = uri.toString()
        persistRead(uri)
        val sidecar = pairedSubtitle?.also(::persistRead)?.toString()
            ?: SidecarFiles.findSubtitle(getApplication(), uri, name)?.also(::persistRead)?.toString()
        val lyric = pairedLyric?.also(::persistRead)?.toString()
            ?: SidecarFiles.findLyric(getApplication(), uri, name)?.also(::persistRead)?.toString()
        return when {
            MediaMetadata.isVideo(name) || resolver.getType(uri)?.startsWith("video/") == true -> {
                val info = LocalMetadataReader.readVideo(getApplication(), uri, name)
                MediaItem(
                    index = 0,
                    title = info.title,
                    subtitle = "本機影片 · ${info.format}",
                    duration = info.duration,
                    kind = MediaKind.Video,
                    filePath = path,
                    coverHue = MediaMetadata.hueFromPath(name),
                    format = info.format,
                    videoWidth = info.width,
                    videoHeight = info.height,
                    videoCodec = info.videoCodec,
                    persistableUri = path,
                    displayName = name,
                    sidecarSubtitleUri = sidecar,
                    sidecarLrcUri = lyric,
                )
            }
            MediaMetadata.isAudio(name) || resolver.getType(uri)?.startsWith("audio/") == true -> {
                val info = LocalMetadataReader.readAudio(getApplication(), uri, name)
                MediaItem(
                    index = 0,
                    title = info.title,
                    subtitle = info.artist,
                    duration = info.duration,
                    kind = MediaKind.Audio,
                    filePath = path,
                    coverHue = MediaMetadata.hueFromPath(name),
                    format = info.format,
                    bitrate = info.bitrate,
                    coverArt = info.coverArt,
                    persistableUri = path,
                    displayName = name,
                    sidecarLrcUri = lyric,
                )
            }
            else -> MediaItem(
                index = 0,
                title = MediaMetadata.displayStem(name),
                subtitle = "本機媒體",
                duration = "—:—",
                kind = MediaKind.Audio,
                filePath = path,
                coverHue = MediaMetadata.hueFromPath(name),
                format = MediaMetadata.extensionLabel(name),
                persistableUri = path,
                displayName = name,
                sidecarLrcUri = lyric,
            )
        }
    }

    private fun persistRead(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun collectFolderEntries(root: DocumentFile?): Pair<List<DocumentFile>, List<DocumentFile>> {
        if (root == null) return emptyList<DocumentFile>() to emptyList()
        val media = mutableListOf<DocumentFile>()
        val extras = mutableListOf<DocumentFile>()
        fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { child ->
                val name = child.name.orEmpty()
                when {
                    child.isDirectory -> walk(child)
                    child.isFile && MediaMetadata.isSupportedMedia(name) -> media += child
                    child.isFile && (MediaMetadata.isSubtitle(name) || MediaMetadata.isLyric(name)) -> extras += child
                }
            }
        }
        if (root.isDirectory) walk(root) else if (root.isFile) {
            val name = root.name.orEmpty()
            if (MediaMetadata.isSupportedMedia(name)) media += root
            if (MediaMetadata.isSubtitle(name) || MediaMetadata.isLyric(name)) extras += root
        }
        return media.sortedBy { it.name.orEmpty().lowercase() } to extras
    }
}
