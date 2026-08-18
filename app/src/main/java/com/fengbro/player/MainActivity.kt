package com.fengbro.player

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fengbro.player.core.model.ChromeMode
import com.fengbro.player.playback.PlaybackService
import com.fengbro.player.ui.PlayerScreen
import com.fengbro.player.ui.PlayerViewModel
import com.fengbro.player.ui.theme.FengBroTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PlaybackService.ACTION_PIP_TOGGLE -> viewModel.togglePlay()
                PlaybackService.ACTION_PIP_NEXT -> viewModel.playNext()
                PlaybackService.ACTION_PIP_PREV -> viewModel.playPrevious()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestNotificationPermission()
        handleIncoming(intent)
        viewModel.requestEnterPip = { enterPip() }
        setContent {
            val state by viewModel.ui.collectAsStateWithLifecycle()
            LaunchedEffect(state.chrome, state.isInPictureInPicture) {
                if (!state.isInPictureInPicture) {
                    applyChrome(state.chrome == ChromeMode.Fullscreen)
                }
            }
            LaunchedEffect(state.screenBrightness) {
                val attrs = window.attributes
                attrs.screenBrightness = state.screenBrightness
                window.attributes = attrs
            }
            LaunchedEffect(
                state.isVideoStage,
                state.current?.id,
                state.isPlaying,
                state.current?.videoWidth,
                state.current?.videoHeight,
            ) {
                updatePipParams()
            }
            DisposableEffect(Unit) {
                registerPipReceiver()
                onDispose { runCatching { unregisterReceiver(pipReceiver) } }
            }
            FengBroTheme {
                PlayerScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && viewModel.canEnterPip() && viewModel.ui.value.isPlaying) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setInPictureInPicture(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            applyChrome(viewModel.ui.value.chrome == ChromeMode.Fullscreen)
        }
    }

    override fun onDestroy() {
        viewModel.requestEnterPip = null
        val attrs = window.attributes
        attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
        super.onDestroy()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || !viewModel.canEnterPip()) return
        runCatching {
            enterPictureInPictureMode(buildPipParams())
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val (width, height) = viewModel.pipAspect()
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(width, height))
            .setActions(pipActions())
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(viewModel.canEnterPip() && viewModel.ui.value.isPlaying)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            builder.setTitle(viewModel.ui.value.current?.title ?: getString(R.string.app_name))
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun pipActions(): List<RemoteAction> {
        val playing = viewModel.ui.value.isPlaying
        return listOf(
            remoteAction(
                PlaybackService.ACTION_PIP_PREV,
                R.drawable.ic_pip_prev,
                getString(R.string.previous),
                11,
            ),
            remoteAction(
                PlaybackService.ACTION_PIP_TOGGLE,
                if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                getString(R.string.play_pause),
                12,
            ),
            remoteAction(
                PlaybackService.ACTION_PIP_NEXT,
                R.drawable.ic_pip_next,
                getString(R.string.next),
                13,
            ),
        )
    }

    private fun remoteAction(action: String, icon: Int, title: String, requestCode: Int): RemoteAction {
        val intent = Intent(action).setPackage(packageName)
        val pending = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pending)
    }

    private fun registerPipReceiver() {
        val filter = IntentFilter().apply {
            addAction(PlaybackService.ACTION_PIP_TOGGLE)
            addAction(PlaybackService.ACTION_PIP_NEXT)
            addAction(PlaybackService.ACTION_PIP_PREV)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, filter)
        }
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = intent.data ?: intent.clipData?.getItemAt(0)?.uri
        val extra = intent.getStringExtra(Intent.EXTRA_TEXT)
        viewModel.handleIncoming(uri, extra)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun applyChrome(fullscreen: Boolean) {
        if (isInPictureInPictureMode) return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
