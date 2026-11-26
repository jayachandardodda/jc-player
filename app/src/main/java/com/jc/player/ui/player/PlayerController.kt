package com.jc.player.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.SubtitleView
import com.google.android.gms.cast.framework.CastContext
import com.jc.player.JcApp
import com.jc.player.cast.LocalMediaServer
import com.jc.player.data.AppSettings
import com.jc.player.data.QueueItem
import com.jc.player.data.db.PlaybackStateEntity
import com.jc.player.player.ExternalSubtitle
import com.jc.player.player.Subtitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

enum class AspectMode(val label: String, val ratio: Float?) {
    FIT("Fit", null), STRETCH("Stretch", null), CROP("Crop", null),
    R16_9("16:9", 16f / 9f), R4_3("4:3", 4f / 3f), R21_9("21:9", 21f / 9f),
}

enum class Panel { NONE, AUDIO, SUBTITLES, SUB_STYLE, SPEED, MORE, EQUALIZER, SLEEP, INFO, QUICK }

/** What the gesture overlay is showing. */
enum class GestureKind { NONE, BRIGHTNESS, VOLUME, CENTER }

data class TrackOption(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean, val formatId: String?)

/** All playback logic and the UI state the player screen draws from. */
class PlayerController(private val activity: PlayerActivity) : Player.Listener {

    private val app = JcApp.instance
    val settings: AppSettings get() = app.settings.current
    private val handler = Handler(Looper.getMainLooper())

    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    private var queue: List<QueueItem> = emptyList()

    /** Original (unshifted) external subtitles per media id. */
    private val externalSubs = mutableMapOf<String, MutableList<ExternalSubtitle>>()

    // ---- UI state ----
    var title by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var isBuffering by mutableStateOf(false)
    var hasNext by mutableStateOf(false)
    var hasPrev by mutableStateOf(false)
    var controlsVisible by mutableStateOf(true)
    var interaction by mutableIntStateOf(0)
    var locked by mutableStateOf(false)
    var kidsLock by mutableStateOf(false)
    var panel by mutableStateOf(Panel.NONE)
    var aspect by mutableStateOf(AspectMode.FIT)
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var gestureLabel by mutableStateOf<String?>(null)
    var gestureLevel by mutableStateOf<Float?>(null)
    var brightness by mutableFloatStateOf(-1f)
    var volumePercent by mutableIntStateOf(0)
    var speed by mutableFloatStateOf(1f)
    var abA by mutableStateOf<Long?>(null)
    var abB by mutableStateOf<Long?>(null)
    var sleepAt by mutableStateOf<Long?>(null)
    var sleepEndOfVideo by mutableStateOf(false)
    var inPip by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resumedFrom by mutableStateOf<Long?>(null)
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var subtitleDelayMs by mutableLongStateOf(0L)
    var backgroundPlay by mutableStateOf(false)
    var showRemaining by mutableStateOf(false)
    var casting by mutableStateOf(false)
    var castDeviceName by mutableStateOf<String?>(null)

    var subtitleView: SubtitleView? = null
    var playerView: androidx.media3.ui.PlayerView? = null

    // Gesture feedback
    var gestureKind by mutableStateOf(GestureKind.NONE)
    var rippleSide by mutableIntStateOf(0)       // -1 left, 1 right
    var rippleText by mutableStateOf("")
    var rippleKey by mutableIntStateOf(0)
    var fastForwarding by mutableStateOf(false)
    private var speedBeforeFastForward = 1f
    var flashKey by mutableIntStateOf(0)
    var nightMode by mutableStateOf(false)

    // Seek preview
    val previewer = com.jc.player.player.FramePreviewer(activity.applicationContext)
    var previewMs by mutableStateOf<Long?>(null)
    var previewBitmap by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var lastSaveAt = 0L
    private var currentMediaId: String? = null

    /** The player the transport controls drive: the Chromecast while casting, else the phone. */
    val active: Player? get() = if (casting) castPlayer else player

    init {
        backgroundPlay = settings.backgroundPlay
        nightMode = settings.nightMode
        volumePercent = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxVolume
    }

    // ---------------- loading ----------------

    fun attach(p: ExoPlayer) {
        if (player !== p) {
            player?.removeListener(this)
            player = p
            p.addListener(this)
        }
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(settings.preferredAudioLang.ifBlank { null })
            .setPreferredTextLanguage(settings.preferredSubtitleLang.ifBlank { null })
            .build()
        p.pauseAtEndOfMediaItems = !settings.autoPlayNext
        syncFromPlayer()
    }

    fun load(items: List<QueueItem>, startIndex: Int) {
        val p = player ?: return
        saveCurrent()
        queue = items
        externalSubs.clear()
        error = null
        abA = null; abB = null; subtitleDelayMs = 0
        activity.lifecycleScope.launch {
            val dao = app.database.playbackState()
            val mediaItems = withContext(Dispatchers.IO) {
                items.map { q ->
                    val saved = runCatching { dao.get(q.uri.toString()) }.getOrNull()
                    val subs = mutableListOf<ExternalSubtitle>()
                    q.subtitleUris.forEach { subs += ExternalSubtitle(it, it.lastPathSegment ?: "subtitle.srt") }
                    if (settings.autoLoadSubtitles) {
                        subs += Subtitles.findSidecars(q.path ?: app.media.pathFor(q.uri))
                    }
                    saved?.externalSubtitles?.split('\n')?.filter { it.isNotBlank() }?.forEach { s ->
                        val u = Uri.parse(s)
                        if (subs.none { it.uri == u }) subs += ExternalSubtitle(u, app.media.displayName(u))
                    }
                    externalSubs[q.uri.toString()] = subs
                    buildItem(q, subs, selectIndex = if (subs.isEmpty()) -1 else 0)
                }
            }
            val start = startIndex.coerceIn(0, mediaItems.lastIndex)
            val saved = withContext(Dispatchers.IO) { runCatching { dao.get(items[start].uri.toString()) }.getOrNull() }
            val resumeAt = resumePositionFor(saved)
            p.setMediaItems(mediaItems, start, resumeAt)
            p.setPlaybackSpeed(speed.takeIf { it != 1f } ?: settings.defaultSpeed)
            p.prepare()
            p.playWhenReady = true
            resumedFrom = resumeAt.takeIf { it > 0 }
            syncFromPlayer()
        }
    }

    private fun resumePositionFor(saved: PlaybackStateEntity?): Long {
        if (!settings.resume || saved == null) return 0
        val nearEnd = saved.durationMs > 0 && saved.positionMs > saved.durationMs - 5_000
        return if (saved.positionMs > 3_000 && !nearEnd) saved.positionMs else 0
    }

    private fun buildItem(q: QueueItem, subs: List<ExternalSubtitle>, selectIndex: Int): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(q.uri)
            .setMediaId(q.uri.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(q.title).setDisplayTitle(q.title).build())
            .setSubtitleConfigurations(Subtitles.configs(subs, selectIndex))
        val s = q.uri.toString().lowercase()
        when {
            s.contains(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            s.contains(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        return builder.build()
    }

    // ---------------- persistence ----------------

    fun saveCurrent() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        save(item, p.currentPosition, p.duration)
    }

    private fun save(item: MediaItem, pos: Long, dur: Long) {
        val id = item.mediaId
        if (id.isBlank() || id.startsWith("http") && dur == C.TIME_UNSET) return
        val subs = externalSubs[id].orEmpty()
            .filter { it.uri.scheme == "content" }
            .joinToString("\n") { it.uri.toString() }
        val entity = PlaybackStateEntity(
            uri = id,
            positionMs = pos.coerceAtLeast(0),
            durationMs = if (dur == C.TIME_UNSET) 0 else dur,
            lastPlayed = System.currentTimeMillis(),
            title = item.mediaMetadata.title?.toString() ?: "",
            speed = speed,
            externalSubtitles = subs.ifBlank { null },
        )
        app.appScope.launch(Dispatchers.IO) { runCatching { app.database.playbackState().upsert(entity) } }
    }

    // ---------------- periodic tick (called ~4x a second) ----------------

    fun tick() {
        val p = active ?: return
        positionMs = p.currentPosition.coerceAtLeast(0)
        durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0
        bufferedMs = p.bufferedPosition
        val a = abA; val b = abB
        if (a != null && b != null && positionMs >= b) p.seekTo(a)
        sleepAt?.let { if (System.currentTimeMillis() >= it) { sleepAt = null; p.pause(); toast("Sleep timer: paused") } }
        val now = System.currentTimeMillis()
        if (!casting && now - lastSaveAt > 5_000 && isPlaying) { lastSaveAt = now; saveCurrent() }
    }

    private fun syncFromPlayer() {
        val p = active ?: return
        isPlaying = p.isPlaying
        title = p.mediaMetadata.displayTitle?.toString() ?: p.mediaMetadata.title?.toString() ?: title
        hasNext = p.hasNextMediaItem()
        hasPrev = p.hasPreviousMediaItem()
        speed = p.playbackParameters.speed
        if (!casting) { tracks = p.currentTracks; videoSize = p.videoSize }
        tick()
    }

    // ---------------- Player.Listener ----------------

    override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        if (!isPlaying) saveCurrent()
        activity.updatePipParams()
    }

    override fun onPlaybackStateChanged(state: Int) {
        isBuffering = state == Player.STATE_BUFFERING
        if (state == Player.STATE_ENDED && !casting) {
            val p = player ?: return
            p.currentMediaItem?.let { save(it, p.duration, p.duration) }
            if (!p.hasNextMediaItem() || sleepEndOfVideo) activity.finish()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val prevId = currentMediaId
        if (prevId != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            // The previous video finished: mark it watched.
            app.appScope.launch(Dispatchers.IO) {
                runCatching {
                    val dao = app.database.playbackState()
                    dao.get(prevId)?.let { dao.upsert(it.copy(positionMs = it.durationMs, lastPlayed = System.currentTimeMillis())) }
                }
            }
            if (sleepEndOfVideo) { sleepEndOfVideo = false; player?.pause(); toast("Sleep timer: paused") }
        }
        // replaceMediaItem (loading/shifting subtitles) also fires a transition for the same video.
        if (mediaItem?.mediaId == currentMediaId) return
        currentMediaId = mediaItem?.mediaId
        abA = null; abB = null
        subtitleDelayMs = 0
        appliedDelayMs = 0
        error = null
        subtitleView?.setCues(emptyList())
        handler.removeCallbacksAndMessages(null)
        activity.onNewVideo()
        // Resume the newly selected item (manual next/prev) from where it was left.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            val id = mediaItem?.mediaId ?: return
            activity.lifecycleScope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching { app.database.playbackState().get(id) }.getOrNull() }
                val pos = resumePositionFor(saved)
                val p = player ?: return@launch
                if (pos > 0 && p.currentMediaItem?.mediaId == id && p.currentPosition < 2_000) {
                    p.seekTo(pos); resumedFrom = pos
                }
            }
        }
    }

    override fun onVideoSizeChanged(size: VideoSize) {
        videoSize = size
        activity.onVideoSize(size)
    }

    override fun onTracksChanged(tracks: Tracks) { this.tracks = tracks }

    override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onCues(cueGroup: CueGroup) {
        val view = subtitleView ?: return
        val delay = embeddedCueDelay()
        if (delay <= 0) view.setCues(cueGroup.cues)
        else handler.postDelayed({ view.setCues(cueGroup.cues) }, (delay / speed.coerceAtLeast(0.25f)).toLong())
    }

    override fun onPlayerError(e: PlaybackException) {
        error = when (e.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                "This phone can't decode this video with the current decoder. Try switching HW/SW."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network error. Check your connection."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "File not found."
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "No permission to open this file."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "This file format isn't supported or the file is damaged."
            else -> "Can't play this video (${e.errorCodeName})."
        }
    }

    // ---------------- transport ----------------

    fun togglePlay() {
        val p = active ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        if (p.isPlaying) p.pause() else p.play()
        poke()
    }

    fun seekBy(ms: Long) {
        val p = active ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + ms).coerceIn(0, dur))
        positionMs = p.currentPosition
    }

    fun seekTo(ms: Long) { active?.seekTo(ms.coerceAtLeast(0)); positionMs = ms }
    fun next() { active?.seekToNextMediaItem(); poke() }
    fun previous() {
        val p = active ?: return
        if (p.currentPosition > 5_000 || !p.hasPreviousMediaItem()) p.seekTo(0) else p.seekToPreviousMediaItem()
        poke()
    }

    fun setScrubbing(on: Boolean) {
        player?.setSeekParameters(if (on) SeekParameters.CLOSEST_SYNC else SeekParameters.DEFAULT)
    }

    fun changeSpeed(s: Float) {
        val v = (s * 100).roundToInt() / 100f
        active?.setPlaybackSpeed(v.coerceIn(0.25f, 4f))
        speed = v
    }

    fun poke() { controlsVisible = true; interaction++ }

    fun retryAfterError() {
        error = null
        player?.prepare()
        player?.play()
    }

    // ---------------- volume & brightness ----------------

    fun maxVolumePercent() = if (settings.volumeBoost) 200 else 100

    fun setVolume(percent: Int) {
        val p = percent.coerceIn(0, maxVolumePercent())
        volumePercent = p
        if (p <= 100) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (p * maxVolume / 100f).roundToInt(), 0)
            app.playerHolder.effects.setBoost(100)
        } else {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            app.playerHolder.effects.setBoost(p)
        }
    }

    fun refreshSystemVolume() {
        val sys = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxVolume
        if (volumePercent <= 100 || sys < 100) volumePercent = sys
    }

    fun showVolume() {
        gestureKind = GestureKind.VOLUME
        gestureLabel = "$volumePercent%"; gestureLevel = volumePercent / maxVolumePercent().toFloat()
    }

    fun showCenter(text: String) {
        gestureKind = GestureKind.CENTER; gestureLabel = text; gestureLevel = null
    }

    // ---------------- seek preview ----------------

    /** True when the current video is a local file, so we can show frame previews while seeking. */
    fun previewSupported(): Boolean = previewer.supports(player?.currentMediaItem?.localConfiguration?.uri) && !casting

    suspend fun loadPreview(ms: Long) {
        val uri = player?.currentMediaItem?.localConfiguration?.uri ?: return
        val bmp = previewer.frameAt(uri, ms)
        if (previewMs != null) previewBitmap = bmp?.asImageBitmap()
    }

    fun endPreview() { previewMs = null; previewBitmap = null }

    // ---------------- double-tap ripple ----------------

    fun showRipple(side: Int, text: String) { rippleSide = side; rippleText = text; rippleKey++ }

    // ---------------- hold for 2x ----------------

    fun startFastForward() {
        val p = active ?: return
        if (fastForwarding || !p.isPlaying) return
        speedBeforeFastForward = p.playbackParameters.speed
        p.setPlaybackSpeed(2f)
        fastForwarding = true
    }

    fun stopFastForward() {
        if (!fastForwarding) return
        active?.setPlaybackSpeed(speedBeforeFastForward)
        fastForwarding = false
    }

    // ---------------- night mode ----------------

    fun toggleNightMode() {
        if (!app.playerHolder.effects.nightSupported) { toast("Night mode needs Android 9 or newer"); return }
        nightMode = !nightMode
        app.settings.update { it.copy(nightMode = nightMode) }
        app.playerHolder.effects.setNightMode(nightMode)
        toast(if (nightMode) "Night mode on: quiet sounds louder, loud sounds softer" else "Night mode off")
    }

    // ---------------- screenshot ----------------

    fun takeScreenshot() {
        val sv = playerView?.videoSurfaceView as? android.view.SurfaceView
        val vs = videoSize
        if (sv == null || sv.width == 0 || casting) { toast("Can't take a screenshot right now"); return }
        val w = if (vs.width > 0) vs.width else sv.width
        val h = if (vs.height > 0) vs.height else sv.height
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        runCatching {
            android.view.PixelCopy.request(sv, bmp, { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    flashKey++
                    activity.lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { saveScreenshot(bmp) }
                        toast(if (ok) "Screenshot saved to Pictures/JC Player" else "Couldn't save screenshot")
                    }
                } else toast("Screenshot failed")
            }, handler)
        }.onFailure { toast("Screenshot failed") }
    }

    private fun saveScreenshot(bmp: android.graphics.Bitmap): Boolean = runCatching {
        val name = "JC_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date()) + ".jpg"
        val resolver = activity.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JC Player")
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "JC Player")
            dir.mkdirs()
            val f = java.io.File(dir, name)
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            android.media.MediaScannerConnection.scanFile(activity, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
        }
        true
    }.getOrDefault(false)

    fun stepVolume(up: Boolean) {
        refreshSystemVolume()
        val step = (100f / maxVolume).roundToInt().coerceAtLeast(1)
        setVolume(volumePercent + if (up) step else -step)
        showVolume()
        handler.removeCallbacks(clearGesture)
        handler.postDelayed(clearGesture, 900)
    }

    private val clearGesture = Runnable { gestureLabel = null; gestureLevel = null; gestureKind = GestureKind.NONE }
    fun clearGestureSoon() { handler.removeCallbacks(clearGesture); handler.postDelayed(clearGesture, 500) }

    fun changeBrightness(value: Float) {
        brightness = value.coerceIn(0.01f, 1f)
        activity.applyBrightness(brightness)
    }

    // ---------------- tracks ----------------

    fun trackOptions(type: Int): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        var n = 0
        tracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i, /* allowExceedsCapabilities = */ true)) continue
                n++
                val f = g.getTrackFormat(i)
                val lang = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage }
                val parts = mutableListOf<String>()
                parts += f.label ?: lang ?: "Track $n"
                if (f.label != null && lang != null && !f.label!!.contains(lang, true)) parts += lang
                if (type == C.TRACK_TYPE_AUDIO) {
                    codecName(f.sampleMimeType)?.let { parts += it }
                    if (f.channelCount > 0) parts += when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount}ch" }
                }
                if (type == C.TRACK_TYPE_TEXT && Subtitles.externalIndex(f.id) != null) parts += "file"
                out += TrackOption(g, i, parts.joinToString(" · "), g.isTrackSelected(i), f.id)
            }
        }
        return out
    }

    fun subtitlesOff(): Boolean {
        val p = player ?: return true
        return p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
            trackOptions(C.TRACK_TYPE_TEXT).none { it.selected }
    }

    fun selectTrack(type: Int, option: TrackOption?) {
        val p = player ?: return
        val b = p.trackSelectionParameters.buildUpon()
        if (option == null) b.setTrackTypeDisabled(type, true)
        else b.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        p.trackSelectionParameters = b.build()
        if (type == C.TRACK_TYPE_TEXT) { subtitleDelayMs = 0; appliedDelayMs = 0; subtitleView?.setCues(emptyList()) }
    }

    private fun codecName(mime: String?): String? = when (mime) {
        MimeTypes.AUDIO_AC3 -> "AC3"; MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "EAC3"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"; MimeTypes.AUDIO_AAC -> "AAC"; MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_OPUS -> "Opus"; MimeTypes.AUDIO_VORBIS -> "Vorbis"; MimeTypes.AUDIO_FLAC -> "FLAC"
        else -> mime?.substringAfter('/')?.uppercase()
    }

    // ---------------- external subtitles & delay ----------------

    fun addExternalSubtitle(uri: Uri, name: String) {
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        val list = externalSubs.getOrPut(id) { mutableListOf() }
        list.removeAll { it.uri == uri }
        list.add(0, ExternalSubtitle(uri, name))
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        subtitleDelayMs = 0
        appliedDelayMs = 0
        replaceCurrent(list, selectIndex = 0)
        saveCurrent()
        toast("Subtitle loaded")
    }

    /** Index into the current item's external list if a side-loaded file is the active subtitle. */
    private fun activeExternalIndex(): Int? {
        val sel = trackOptions(C.TRACK_TYPE_TEXT).firstOrNull { it.selected } ?: return null
        return Subtitles.externalIndex(sel.formatId)
    }

    /** Embedded tracks can only be delayed (shown later) by holding cues back. */
    private fun embeddedCueDelay(): Long = if (activeExternalIndex() != null) 0 else subtitleDelayMs.coerceAtLeast(0)

    fun canShiftEarlier(): Boolean = activeExternalIndex() != null

    /** Applies [subtitleDelayMs]. External files are rewritten with shifted timestamps. */
    private var appliedDelayMs = 0L

    fun applySubtitleDelay() {
        if (subtitleDelayMs == appliedDelayMs) return
        val ext = activeExternalIndex() ?: return
        appliedDelayMs = subtitleDelayMs
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        val originals = externalSubs[id] ?: return
        val delay = subtitleDelayMs
        activity.lifecycleScope.launch {
            val shifted = if (delay == 0L) originals[ext] else withContext(Dispatchers.IO) {
                Subtitles.shifted(activity, originals[ext], delay)
            } ?: run { toast("This subtitle format can't be shifted"); return@launch }
            val list = originals.toMutableList().also { it[ext] = shifted }
            replaceCurrent(list, selectIndex = ext, keepOriginals = true)
        }
    }

    private fun replaceCurrent(subs: List<ExternalSubtitle>, selectIndex: Int, keepOriginals: Boolean = false) {
        val p = player ?: return
        val idx = p.currentMediaItemIndex
        val cur = p.currentMediaItem ?: return
        val pos = p.currentPosition
        val q = queue.getOrNull(idx)?.takeIf { it.uri.toString() == cur.mediaId }
            ?: QueueItem(Uri.parse(cur.mediaId), cur.mediaMetadata.title?.toString() ?: "")
        if (!keepOriginals) externalSubs[cur.mediaId] = subs.toMutableList()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
        p.replaceMediaItem(idx, buildItem(q, subs, selectIndex))
        p.seekTo(idx, pos)
    }

    // ---------------- A-B repeat & sleep ----------------

    fun abRepeatTap() {
        val pos = active?.currentPosition ?: return
        when {
            abA == null -> { abA = pos; toast("Point A set. Tap again for point B.") }
            abB == null -> if (pos > abA!! + 500) { abB = pos; toast("Repeating A–B") } else toast("B must be after A")
            else -> { abA = null; abB = null; toast("A–B repeat off") }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepEndOfVideo = false
        sleepAt = null
        when {
            minutes == null -> toast("Sleep timer off")
            minutes < 0 -> { sleepEndOfVideo = true; toast("Will pause at the end of this video") }
            else -> { sleepAt = System.currentTimeMillis() + minutes * 60_000L; toast("Pausing in $minutes min") }
        }
    }

    // ---------------- zoom ----------------

    fun resetZoom() { zoom = 1f; panX = 0f; panY = 0f }

    fun cycleAspect() {
        aspect = AspectMode.entries[(aspect.ordinal + 1) % AspectMode.entries.size]
        resetZoom()
        showCenter(aspect.label)
        handler.removeCallbacks(clearGesture)
        handler.postDelayed(clearGesture, 900)
    }

    // ---------------- Chromecast ----------------

    private var castPlayer: CastPlayer? = null
    private var mediaServer: LocalMediaServer? = null

    fun initCast() {
        val ctx = runCatching { CastContext.getSharedInstance(activity) }.getOrNull() ?: return
        val cp = runCatching { CastPlayer(ctx) }.getOrNull() ?: return
        castPlayer = cp
        cp.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { if (casting) syncFromPlayer() }
        })
        cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() = startCasting(ctx)
            override fun onCastSessionUnavailable() = stopCasting()
        })
        if (cp.isCastSessionAvailable) startCasting(ctx)
    }

    private fun startCasting(ctx: CastContext) {
        val local = player ?: return
        val cp = castPlayer ?: return
        val items = try {
            (0 until local.mediaItemCount).map { i -> castItem(local.getMediaItemAt(i)) }
        } catch (e: Exception) {
            toast(e.message ?: "Can't cast this video"); return
        }
        val index = local.currentMediaItemIndex
        val pos = local.currentPosition
        local.pause()
        cp.setMediaItems(items, index, pos)
        cp.playWhenReady = true
        cp.prepare()
        castDeviceName = ctx.sessionManager.currentCastSession?.castDevice?.friendlyName
        casting = true
        syncFromPlayer()
    }

    private fun stopCasting() {
        if (!casting) return
        val cp = castPlayer
        val index = cp?.currentMediaItemIndex ?: 0
        val pos = cp?.currentPosition ?: 0
        casting = false
        castDeviceName = null
        player?.let { if (index < it.mediaItemCount) it.seekTo(index, pos) }
        syncFromPlayer()
    }

    private fun castItem(item: MediaItem): MediaItem {
        val uri = item.localConfiguration?.uri ?: Uri.parse(item.mediaId)
        val name = item.mediaMetadata.title?.toString() ?: uri.lastPathSegment ?: "video"
        val mime = item.localConfiguration?.mimeType ?: LocalMediaServer.guessMime(uri.lastPathSegment ?: name)
        val url = when (uri.scheme) {
            "http", "https" -> uri.toString()
            "content", "file" -> {
                val server = mediaServer ?: LocalMediaServer(activity).also { it.start(); mediaServer = it }
                server.register(uri, mime)
            }
            else -> throw IllegalStateException("Network-share videos can't be cast yet")
        }
        return MediaItem.Builder()
            .setUri(url)
            .setMimeType(mime)
            .setMediaId(item.mediaId)
            .setMediaMetadata(item.mediaMetadata)
            .build()
    }

    // ---------------- teardown ----------------

    fun release() {
        saveCurrent()
        handler.removeCallbacksAndMessages(null)
        player?.removeListener(this)
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        runCatching { mediaServer?.stop() }
        mediaServer = null
        app.appScope.launch { previewer.release() }
    }

    fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
}
