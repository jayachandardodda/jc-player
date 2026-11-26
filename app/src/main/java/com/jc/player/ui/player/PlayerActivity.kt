package com.jc.player.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jc.player.JcApp
import com.jc.player.R
import com.jc.player.data.DecoderMode
import com.jc.player.data.QueueItem
import com.jc.player.data.ThemeMode
import com.jc.player.player.PlaybackService
import com.jc.player.ui.theme.JcTheme

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REATTACH = "reattach"
        const val EXTRA_FROM_APP = "from_app"
        private const val ACTION_PIP = "com.jc.player.PIP_CONTROL"
        private const val EXTRA_PIP_CMD = "cmd"
        private const val CMD_PLAY_PAUSE = 1
        private const val CMD_REWIND = 2
        private const val CMD_FORWARD = 3
    }

    private val app get() = application as JcApp
    lateinit var controller: PlayerController
        private set
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var userRotated = false
    private var orientationDecided = false

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = app.media.displayName(uri)
            if (!com.jc.player.player.Subtitles.isSubtitleName(name)) controller.toast("Not a subtitle file: $name")
            else controller.addExternalSubtitle(uri, name)
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_PIP_CMD, 0)) {
                CMD_PLAY_PAUSE -> controller.togglePlay()
                CMD_REWIND -> controller.seekBy(-controller.settings.seekStepSec * 1000L)
                CMD_FORWARD -> controller.seekBy(controller.settings.seekStepSec * 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        controller = PlayerController(this)
        val s = app.settings.current
        if (s.keepBrightness && s.lastBrightness > 0) controller.changeBrightness(s.lastBrightness)

        ContextCompat.registerReceiver(this, pipReceiver, android.content.IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    controller.kidsLock -> controller.toast("Kids lock is on. Long-press the lock icon to unlock.")
                    controller.locked -> { controller.poke() }
                    controller.panel != Panel.NONE -> controller.panel = Panel.NONE
                    else -> finish()
                }
            }
        })

        setContent {
            JcTheme(ThemeMode.DARK, app.settings.current.accent) { PlayerScreen(controller) }
        }

        if (!handleIntent(intent)) { finish(); return }
        controller.initCast()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Returns false if there is nothing to play. */
    private fun handleIntent(intent: Intent): Boolean {
        val holder = app.playerHolder
        if (intent.getBooleanExtra(EXTRA_REATTACH, false)) {
            val existing = holder.player
            if (existing != null && existing.mediaItemCount > 0) {
                controller.attach(existing)
                return true
            }
        }
        val queue = resolveQueue(intent) ?: return controller.player != null
        userRotated = false
        orientationDecided = false
        val p = holder.obtain(app.settings.current.decoder)
        controller.attach(p)
        controller.load(queue.first, queue.second)
        return true
    }

    private fun resolveQueue(intent: Intent): Pair<List<QueueItem>, Int>? {
        if (intent.getBooleanExtra(EXTRA_FROM_APP, false)) {
            app.pendingQueue?.let { app.pendingQueue = null; return it }
        }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> intent.data
        }
        uri ?: return null
        val title = intent.getStringExtra("title") ?: app.media.displayName(uri)
        return listOf(QueueItem(uri, title, app.media.pathFor(uri))) to 0
    }

    /** Re-creates the player with a different decoder, keeping queue and position. */
    fun switchDecoder(mode: DecoderMode) {
        val old = controller.player ?: return
        val items = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        val index = old.currentMediaItemIndex
        val pos = old.currentPosition
        controller.saveCurrent()
        app.settings.update { it.copy(decoder = mode) }
        releaseMediaController()
        val p = app.playerHolder.obtain(mode)
        controller.attach(p)
        p.setMediaItems(items, index, pos)
        p.setPlaybackSpeed(controller.speed)
        p.prepare()
        p.play()
        controller.error = null
        connectMediaController()
        controller.toast(if (mode == DecoderMode.HW) "HW decoder" else "SW decoder")
    }

    fun pickSubtitleFile() {
        subtitlePicker.launch(arrayOf("application/x-subrip", "text/*", "application/octet-stream", "*/*"))
    }

    // ---------------- lifecycle ----------------

    override fun onStart() {
        super.onStart()
        connectMediaController()
        controller.refreshSystemVolume()
    }

    override fun onStop() {
        super.onStop()
        controller.saveCurrent()
        val p = controller.player ?: return
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inPip && !(controller.backgroundPlay && p.isPlaying) && !controller.casting) p.pause()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) {
            val s = app.settings.current
            if (s.keepBrightness && controller.brightness > 0) app.settings.update { it.copy(lastBrightness = controller.brightness) }
            controller.release()
        }
        runCatching { unregisterReceiver(pipReceiver) }
        releaseMediaController()
        if (isFinishing) {
            app.playerHolder.release()
            stopService(Intent(this, PlaybackService::class.java))
        }
        super.onDestroy()
    }

    private fun connectMediaController() {
        if (mediaControllerFuture != null || app.playerHolder.session == null) return
        // Binding a controller starts PlaybackService, which shows the media notification.
        mediaControllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
    }

    private fun releaseMediaController() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---------------- hardware keys ----------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { controller.stepVolume(true); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { controller.stepVolume(false); return true }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { controller.togglePlay(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                controller.seekBy(controller.settings.seekStepSec * 1000L); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                controller.seekBy(-controller.settings.seekStepSec * 1000L); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------- brightness & rotation ----------------

    fun applyBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    fun currentBrightness(): Float {
        val w = window.attributes.screenBrightness
        if (w >= 0) return w
        return runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
            .getOrDefault(0.5f).coerceIn(0.01f, 1f)
    }

    fun onNewVideo() {
        orientationDecided = false
    }

    fun onVideoSize(size: VideoSize) {
        updatePipParams()
        if (size.width == 0 || size.height == 0) return
        if (orientationDecided || userRotated || !app.settings.current.autoRotate) return
        orientationDecided = true
        val w = size.width * size.pixelWidthHeightRatio
        requestedOrientation = if (w >= size.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    fun toggleRotation() {
        userRotated = true
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // ---------------- kids lock ----------------

    fun setKidsLock(on: Boolean) {
        controller.kidsLock = on
        controller.locked = on
        if (on) {
            runCatching { startLockTask() }
            controller.toast("Kids lock on. Long-press the lock icon to unlock.")
        } else {
            runCatching { stopLockTask() }
        }
    }

    // ---------------- picture-in-picture ----------------

    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            controller.toast("Picture-in-picture isn't supported on this phone"); return
        }
        controller.panel = Panel.NONE
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val size = controller.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            val r = (size.width * size.pixelWidthHeightRatio) / size.height
            when {
                r > 2.39f -> Rational(239, 100)
                r < 1 / 2.39f -> Rational(100, 239)
                else -> Rational((size.width * size.pixelWidthHeightRatio).toInt(), size.height)
            }
        } else Rational(16, 9)

        fun action(cmd: Int, icon: Int, title: String) = RemoteAction(
            Icon.createWithResource(this, icon), title, title,
            PendingIntent.getBroadcast(
                this, cmd,
                Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_PIP_CMD, cmd),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val playing = controller.isPlaying
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(
                listOf(
                    action(CMD_REWIND, R.drawable.ic_pip_rewind, "Rewind"),
                    if (playing) action(CMD_PLAY_PAUSE, R.drawable.ic_pip_pause, "Pause")
                    else action(CMD_PLAY_PAUSE, R.drawable.ic_pip_play, "Play"),
                    action(CMD_FORWARD, R.drawable.ic_pip_forward, "Forward"),
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(app.settings.current.autoPip && playing && !controller.locked)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            app.settings.current.autoPip && controller.isPlaying && !controller.locked
        ) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        controller.inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) controller.controlsVisible = false
    }
}
