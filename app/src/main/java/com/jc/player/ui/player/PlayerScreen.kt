package com.jc.player.ui.player

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.jc.player.JcApp
import com.jc.player.data.AppSettings
import com.jc.player.data.DecoderMode
import com.jc.player.data.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(c: PlayerController) {
    val activity = LocalContext.current as PlayerActivity
    val settings by JcApp.instance.settings.state.collectAsState()

    LaunchedEffect(Unit) { while (true) { c.tick(); delay(250) } }
    LaunchedEffect(c.controlsVisible, c.interaction, c.isPlaying, c.panel) {
        if (c.controlsVisible && c.isPlaying && c.panel == Panel.NONE) { delay(3500); c.controlsVisible = false }
    }
    LaunchedEffect(c.resumedFrom) { if (c.resumedFrom != null) { delay(5000); c.resumedFrom = null } }
    LaunchedEffect(c.subtitleDelayMs) { delay(600); c.applySubtitleDelay() }
    // Fetch preview frames while scrubbing (debounced).
    LaunchedEffect(c.previewMs) { val ms = c.previewMs ?: return@LaunchedEffect; delay(40); c.loadPreview(ms) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(c)
        SubtitleLayer(c, settings)

        if (!c.inPip) {
            GestureLayer(c, activity, settings)
            if (c.casting) CastingBanner(c)
            if (c.isBuffering && !c.casting) CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White)
            SeekRipple(c)
            EdgeBars(c)
            CenterHud(c)
            FastForwardPill(c)
            if (c.locked) LockedOverlay(c, activity)
            else AnimatedVisibility(c.controlsVisible, enter = fadeIn(tween(180)), exit = fadeOut(tween(250))) { Controls(c, activity, settings) }
            ResumeChip(c)
            ScreenshotFlash(c)
            c.error?.let { ErrorOverlay(c, activity, it) }
            if (c.panel == Panel.QUICK) QuickDrawer(c, activity)
            else if (c.panel != Panel.NONE) Panels(c, activity, settings)
        }
    }
}

// ---------------- video & subtitles ----------------

@Composable
private fun VideoSurface(c: PlayerController) {
    val ratio = c.aspect.ratio
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val mod = if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize()
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    subtitleView?.visibility = View.GONE // subtitles are drawn by SubtitleLayer (supports delay)
                    setKeepContentOnPlayerReset(true)
                    c.playerView = this
                }
            },
            update = { view ->
                if (view.player !== c.player) view.player = c.player
                view.resizeMode = when (c.aspect) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = mod.graphicsLayer {
                scaleX = c.zoom; scaleY = c.zoom
                translationX = c.panX; translationY = c.panY
            },
        )
    }
}

@Composable
private fun SubtitleLayer(c: PlayerController, s: AppSettings) {
    AndroidView(
        factory = { ctx -> SubtitleView(ctx).also { c.subtitleView = it } },
        update = { v ->
            val bg = when (s.subtitleBackground) { 1 -> 0x99000000.toInt(); 2 -> 0xFF000000.toInt(); else -> 0 }
            v.setStyle(
                CaptionStyleCompat(
                    s.subtitleColor, bg, android.graphics.Color.TRANSPARENT,
                    if (s.subtitleOutline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
                    android.graphics.Color.BLACK,
                    if (s.subtitleBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
                )
            )
            v.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, s.subtitleSizeSp.toFloat())
            v.setBottomPaddingFraction(s.subtitleBottomPercent / 100f)
            v.setApplyEmbeddedStyles(true)
            v.setApplyEmbeddedFontSizes(false)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ---------------- gestures ----------------

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME, PINCH, QUICK }

@Composable
private fun GestureLayer(c: PlayerController, activity: PlayerActivity, s: AppSettings) {
    val slop = LocalViewConfiguration.current.touchSlop
    var tapSide by remember { mutableStateOf(0) }
    var tapTotal by remember { mutableLongStateOf(0L) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(s.gestureDoubleTap, s.seekStepSec, c.locked) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        c.stopFastForward()
                    },
                    onLongPress = { if (!c.locked && c.panel == Panel.NONE) c.startFastForward() },
                    onTap = {
                        if (c.panel != Panel.NONE) c.panel = Panel.NONE
                        else if (c.locked) c.poke()
                        else { c.controlsVisible = !c.controlsVisible; c.interaction++ }
                    },
                    onDoubleTap = if (!s.gestureDoubleTap || c.locked) null else { offset ->
                        val third = size.width / 3f
                        if (offset.x < third || offset.x > 2 * third) {
                            val side = if (offset.x < third) -1 else 1
                            val now = System.currentTimeMillis()
                            tapTotal = if (side == tapSide && now - lastTapAt < 1200) tapTotal + s.seekStepSec else s.seekStepSec.toLong()
                            tapSide = side; lastTapAt = now
                            c.seekBy(side * s.seekStepSec * 1000L)
                            c.showRipple(side, "${tapTotal}s")
                        } else c.togglePlay()
                    },
                )
            }
            .pointerInput(s, c.locked) {
                if (c.locked) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var mode = DragMode.NONE
                    var decided = false
                    var total = Offset.Zero
                    val startX = down.position.x
                    val startPos = c.active?.currentPosition ?: 0L
                    var target = startPos
                    val preview = c.previewSupported()
                    var bright = activity.currentBrightness()
                    c.refreshSystemVolume()
                    var vol = c.volumePercent.toFloat()
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2 && s.gesturePinch && (mode == DragMode.NONE || mode == DragMode.PINCH)) {
                            mode = DragMode.PINCH; decided = true
                            c.zoom = (c.zoom * event.calculateZoom()).coerceIn(0.25f, 6f)
                            val pan = event.calculatePan()
                            c.panX += pan.x; c.panY += pan.y
                            c.showCenter("Zoom ${(c.zoom * 100).toInt()}%")
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && mode != DragMode.PINCH) {
                            val ch = pressed[0]
                            val delta = ch.positionChange()
                            total += delta
                            if (!decided && total.getDistance() > slop) {
                                decided = true
                                val edge = 24.dp.toPx()
                                val nearEdge = down.position.y < edge || down.position.y > size.height - edge
                                val vertical = abs(total.y) > abs(total.x)
                                mode = when {
                                    nearEdge -> DragMode.NONE
                                    c.fastForwarding -> DragMode.NONE
                                    vertical && total.y < 0 && down.position.y > size.height * 0.78f -> DragMode.QUICK
                                    !vertical -> if (s.gestureSeek && c.durationMs > 0) DragMode.SEEK else DragMode.NONE
                                    startX < size.width / 2f -> if (s.gestureBrightness) DragMode.BRIGHTNESS else DragMode.NONE
                                    else -> if (s.gestureVolume) DragMode.VOLUME else DragMode.NONE
                                }
                                when (mode) {
                                    DragMode.SEEK -> if (!preview) c.setScrubbing(true)
                                    DragMode.QUICK -> { c.panel = Panel.QUICK; c.controlsVisible = false }
                                    else -> {}
                                }
                            }
                            when (mode) {
                                DragMode.SEEK -> {
                                    val span = 120_000f * total.x / size.width
                                    target = (startPos + span.toLong()).coerceIn(0, c.durationMs)
                                    if (preview) c.previewMs = target else c.seekTo(target)
                                    val diff = (target - startPos) / 1000
                                    c.showCenter("${if (diff >= 0) "+" else "-"}${formatDuration(abs(diff) * 1000)}\n${formatDuration(target)} / ${formatDuration(c.durationMs)}")
                                    ch.consume()
                                }
                                DragMode.BRIGHTNESS -> {
                                    bright = (bright - delta.y / (size.height * 0.8f)).coerceIn(0.01f, 1f)
                                    c.changeBrightness(bright)
                                    c.gestureKind = GestureKind.BRIGHTNESS
                                    c.gestureLabel = "${(bright * 100).toInt()}%"; c.gestureLevel = bright
                                    ch.consume()
                                }
                                DragMode.VOLUME -> {
                                    vol = (vol - delta.y / (size.height * 0.8f) * 100f).coerceIn(0f, c.maxVolumePercent().toFloat())
                                    c.setVolume(vol.toInt())
                                    c.showVolume()
                                    ch.consume()
                                }
                                DragMode.QUICK -> ch.consume()
                                else -> {}
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (mode == DragMode.SEEK) {
                        if (preview) { c.seekTo(target); c.endPreview() } else c.setScrubbing(false)
                    }
                    if (mode != DragMode.NONE && mode != DragMode.QUICK) c.clearGestureSoon()
                }
            }
    )
}

// ---------------- gesture feedback ----------------

@Composable
private fun BoxScope.SeekRipple(c: PlayerController) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(c.rippleKey) {
        if (c.rippleKey == 0) return@LaunchedEffect
        visible = true; delay(650); visible = false
    }
    val left = c.rippleSide < 0
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(120)), exit = fadeOut(tween(300)),
        modifier = Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.38f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(if (left) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (left) Icons.Default.FastRewind else Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(36.dp))
                Text(c.rippleText, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/** MX-style vertical bars: brightness on the left edge, volume on the right. */
@Composable
private fun BoxScope.EdgeBars(c: PlayerController) {
    val kind = c.gestureKind
    if (kind != GestureKind.BRIGHTNESS && kind != GestureKind.VOLUME) return
    val level = (c.gestureLevel ?: 0f).coerceIn(0f, 1f)
    val boosted = kind == GestureKind.VOLUME && c.volumePercent > 100
    val accent = if (boosted) Color(0xFFFF8A3D) else MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .align(if (kind == GestureKind.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd)
            .safeDrawingPadding()
            .padding(horizontal = 40.dp)
            .background(Color(0x99000000), RoundedCornerShape(24.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (kind == GestureKind.BRIGHTNESS) Icons.Default.Brightness6 else Icons.AutoMirrored.Filled.VolumeUp, null,
            tint = Color.White, modifier = Modifier.size(22.dp))
        Box(Modifier.padding(vertical = 10.dp).width(8.dp).height(150.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x44FFFFFF))) {
            val fill by animateFloatAsState(level, tween(80), label = "level")
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fill).background(accent))
        }
        Text(c.gestureLabel ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BoxScope.CenterHud(c: PlayerController) {
    if (c.gestureKind != GestureKind.CENTER) return
    val label = c.gestureLabel ?: return
    Column(
        Modifier
            .align(Alignment.Center)
            .background(Color(0xAA000000), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        c.previewBitmap?.let { bmp ->
            Image(bmp, null, contentScale = ContentScale.Fit,
                modifier = Modifier.padding(bottom = 8.dp).width(220.dp).aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(8.dp)))
        }
        label.split('\n').forEachIndexed { i, line ->
            Text(line, color = Color.White, fontSize = if (i == 0) 22.sp else 15.sp,
                fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}

@Composable
private fun BoxScope.FastForwardPill(c: PlayerController) {
    AnimatedVisibility(
        c.fastForwarding,
        enter = fadeIn() + slideInVertically { -it }, exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 16.dp),
    ) {
        Row(
            Modifier.background(Color(0xAA000000), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("2x", color = Color.White, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(20.dp))
        }
    }
}

@Composable
private fun ScreenshotFlash(c: PlayerController) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(c.flashKey) {
        if (c.flashKey == 0) return@LaunchedEffect
        alpha.snapTo(0.8f); alpha.animateTo(0f, tween(350))
    }
    if (alpha.value > 0f) Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color.White))
}

// ---------------- controls ----------------

@Composable
private fun Controls(c: PlayerController, activity: PlayerActivity, s: AppSettings) {
    Box(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xD0000000), Color.Transparent)))
                .safeDrawingPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { activity.finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(c.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val info = listOfNotNull(
                    c.videoSize.takeIf { it.height > 0 }?.let { "${minOf(it.width, it.height)}p" },
                    if (c.nightMode) "Night mode" else null,
                    c.sleepAt?.let { "Sleep ${((it - System.currentTimeMillis()) / 60_000 + 1)}m" } ?: if (c.sleepEndOfVideo) "Sleep at end" else null,
                    if (c.abB != null) "A–B" else null,
                ).joinToString(" · ")
                if (info.isNotEmpty()) Text(info, color = Color(0xB3FFFFFF), fontSize = 12.sp)
            }
            CastButton()
            IconButton(onClick = { c.panel = Panel.AUDIO }) { Icon(Icons.Default.Audiotrack, "Audio track", tint = Color.White) }
            IconButton(onClick = { c.panel = Panel.SUBTITLES }) { Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White) }
            IconButton(onClick = { c.panel = Panel.QUICK }) { Icon(Icons.Default.Tune, "Quick settings", tint = Color.White) }
            IconButton(onClick = { c.panel = Panel.MORE }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
        }

        // Centre transport
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Default.SkipPrevious, "Previous", 30.dp) { c.previous() }
            RoundButton(Icons.Default.Replay10, "Rewind", 30.dp) { c.seekBy(-s.seekStepSec * 1000L); c.poke() }
            PlayPauseButton(c)
            RoundButton(Icons.Default.Forward10, "Forward", 30.dp) { c.seekBy(s.seekStepSec * 1000L); c.poke() }
            RoundButton(Icons.Default.SkipNext, "Next", 30.dp, enabled = c.hasNext) { c.next() }
        }

        // Bottom: seek bar + configurable buttons
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD0000000))))
                .safeDrawingPadding()
                .padding(horizontal = 12.dp),
        ) {
            SeekBar(c)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                s.playerButtons.forEach { id -> BottomButton(id, c, activity) }
            }
        }
    }
}

@Composable
private fun BottomButton(id: String, c: PlayerController, activity: PlayerActivity) {
    val accent = MaterialTheme.colorScheme.primary
    @Composable
    fun btn(icon: ImageVector, label: String, on: Boolean = false, action: () -> Unit) =
        IconButton(onClick = { action(); c.poke() }) { Icon(icon, label, tint = if (on) accent else Color.White) }
    when (id) {
        "lock" -> btn(Icons.Default.Lock, "Lock") { c.locked = true; c.panel = Panel.NONE }
        "aspect" -> btn(Icons.Default.AspectRatio, "Aspect ratio") { c.cycleAspect() }
        "rotate" -> btn(Icons.Default.ScreenRotation, "Rotate") { activity.toggleRotation() }
        "screenshot" -> btn(Icons.Default.PhotoCamera, "Screenshot") { c.takeScreenshot() }
        "speed" -> TextButton(onClick = { c.panel = Panel.SPEED }) {
            Icon(Icons.Default.Speed, null, tint = Color.White)
            Text(" ${"%.2f".format(c.speed).trimEnd('0').trimEnd('.')}x", color = Color.White)
        }
        "background" -> btn(Icons.Default.Headphones, "Background play", c.backgroundPlay) {
            c.backgroundPlay = !c.backgroundPlay
            c.toast(if (c.backgroundPlay) "Background play on" else "Background play off")
        }
        "pip" -> btn(Icons.Default.PictureInPictureAlt, "Picture-in-picture") { activity.enterPip() }
        "repeat" -> btn(if (c.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            "Repeat", c.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) { c.cycleRepeat() }
        "shuffle" -> btn(Icons.Default.Shuffle, "Shuffle", c.shuffle) { c.toggleShuffle() }
        "night" -> btn(Icons.Default.NightsStay, "Night mode", c.nightMode) { c.toggleNightMode() }
        "equalizer" -> btn(Icons.Default.Equalizer, "Equalizer") { c.panel = Panel.EQUALIZER }
        "sleep" -> btn(Icons.Default.Bedtime, "Sleep timer", c.sleepAt != null || c.sleepEndOfVideo) { c.panel = Panel.SLEEP }
        "abrepeat" -> btn(Icons.Default.Repeat, "A–B repeat", c.abA != null) { c.abRepeatTap() }
        "quick" -> btn(Icons.Default.Tune, "Quick settings") { c.panel = Panel.QUICK }
    }
}

@Composable
private fun PlayPauseButton(c: PlayerController) {
    Box(
        Modifier.size(76.dp).background(Color(0x55000000), CircleShape).border(1.5.dp, Color(0x55FFFFFF), CircleShape)
            .clickable { c.togglePlay() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            c.isPlaying,
            transitionSpec = { (scaleIn(tween(160), 0.6f) + fadeIn(tween(160))) togetherWith (scaleOut(tween(120), 0.6f) + fadeOut(tween(120))) },
            label = "playPause",
        ) { playing ->
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(46.dp))
        }
    }
}

/** Seek bar that thickens while dragging and shows a frame preview above the thumb. */
@Composable
private fun SeekBar(c: PlayerController) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val dur = c.durationMs.coerceAtLeast(1)
    val frac = if (dragging) dragFrac else (c.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val shown = (frac * dur).toLong()
    val height by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "barHeight")
    val thumb by animateDpAsState(if (dragging) 20.dp else 14.dp, label = "thumb")
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val preview = c.previewSupported()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(shown), color = Color.White, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        BoxWithConstraints(Modifier.weight(1f).height(36.dp)) {
            val widthPx = constraints.maxWidth.toFloat()
            fun fracAt(x: Float) = (x / widthPx).coerceIn(0f, 1f)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(c.durationMs, preview) {
                        if (c.durationMs <= 0) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            dragging = true
                            if (!preview) c.setScrubbing(true)
                            dragFrac = fracAt(down.position.x)
                            if (preview) c.previewMs = (dragFrac * dur).toLong() else c.seekTo((dragFrac * dur).toLong())
                            c.poke()
                            do {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.first()
                                dragFrac = fracAt(ch.position.x)
                                if (preview) c.previewMs = (dragFrac * dur).toLong() else c.seekTo((dragFrac * dur).toLong())
                                c.poke()
                                ch.consume()
                            } while (ev.changes.any { it.pressed })
                            c.seekTo((dragFrac * dur).toLong())
                            if (preview) c.endPreview() else c.setScrubbing(false)
                            dragging = false
                        }
                    }
            ) {
                val h = height.toPx(); val y = size.height / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(Color(0x40FFFFFF), Offset(0f, y - h / 2), Size(size.width, h), r)
                val buf = (c.bufferedMs.toFloat() / dur).coerceIn(0f, 1f)
                drawRoundRect(Color(0x66FFFFFF), Offset(0f, y - h / 2), Size(size.width * buf, h), r)
                // A–B markers
                c.abA?.let { a ->
                    val x1 = size.width * a / dur
                    val x2 = c.abB?.let { size.width * it / dur } ?: (x1 + 3f)
                    drawRect(Color(0x99FFC107), Offset(x1, y - h / 2), Size((x2 - x1).coerceAtLeast(3f), h))
                }
                drawRoundRect(accent, Offset(0f, y - h / 2), Size(size.width * frac, h), r)
                drawCircle(accent, thumb.toPx() / 2, Offset(size.width * frac, y))
            }
            // Preview bubble above the thumb
            val bmp = c.previewBitmap
            if (dragging && preview) {
                val bubbleW = 168.dp
                val bubbleWpx = with(density) { bubbleW.toPx() }
                val x = (widthPx * frac - bubbleWpx / 2).coerceIn(0f, (widthPx - bubbleWpx).coerceAtLeast(0f))
                Column(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), -with(density) { 118.dp.roundToPx() }) }
                        .width(bubbleW)
                        .background(Color(0xE6000000), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(7.dp)).background(Color(0xFF222222))) {
                        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(formatDuration(shown), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Text(
            if (c.showRemaining) "-" + formatDuration(c.durationMs - shown) else formatDuration(c.durationMs),
            color = Color.White, fontSize = 13.sp,
            modifier = Modifier.width(60.dp).padding(start = 8.dp).clickable { c.showRemaining = !c.showRemaining },
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, iconSize: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).background(Color(0x33000000), CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun CastButton() {
    AndroidView(
        factory = { ctx ->
            MediaRouteButton(ctx).also { btn -> runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, btn) } }
        },
        modifier = Modifier.size(48.dp),
    )
}

@Composable
private fun BoxScope.CastingBanner(c: PlayerController) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Cast, null, tint = Color.White, modifier = Modifier.size(56.dp))
        Text("Playing on ${c.castDeviceName ?: "Chromecast"}", color = Color.White, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun LockedOverlay(c: PlayerController, activity: PlayerActivity) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(c.interaction) { visible = true; delay(2500); visible = false }
    if (!visible) return
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(56.dp)
                .background(Color(0x88000000), CircleShape)
                .combinedClickable(
                    onClick = { if (c.kidsLock) c.toast("Long-press to turn off kids lock") else { c.locked = false; c.poke() } },
                    onLongClick = { if (c.kidsLock) { activity.setKidsLock(false); c.poke() } },
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(if (c.kidsLock) Icons.Default.Lock else Icons.Default.LockOpen, "Unlock", tint = Color.White) }
    }
}

@Composable
private fun BoxScope.ResumeChip(c: PlayerController) {
    val pos = c.resumedFrom ?: return
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .safeDrawingPadding()
            .padding(start = 16.dp, bottom = 110.dp)
            .background(Color(0xDD202020), RoundedCornerShape(20.dp))
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Resumed at ${formatDuration(pos)}", color = Color.White, fontSize = 14.sp)
        TextButton(onClick = { c.seekTo(0); c.resumedFrom = null }) { Text("Start over") }
    }
}

@Composable
private fun ErrorOverlay(c: PlayerController, activity: PlayerActivity, message: String) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
                val other = if (c.settings.decoder == DecoderMode.HW) DecoderMode.SW else DecoderMode.HW
                Button(onClick = { activity.switchDecoder(other) }) { Text("Try ${other.name} decoder") }
                OutlinedButton(onClick = { c.retryAfterError() }) { Text("Retry") }
                if (c.hasNext) OutlinedButton(onClick = { c.error = null; c.next() }) { Text("Skip") }
                OutlinedButton(onClick = { activity.finish() }) { Text("Close") }
            }
        }
    }
}

// ---------------- panels ----------------

/** Translucent panel that slides in from the right edge. */
@Composable
fun SidePanel(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val offset = remember { Animatable(with(density) { 340.dp.toPx() }) }
    LaunchedEffect(Unit) { offset.animateTo(0f, tween(220)) }
    Box(Modifier.fillMaxSize().clickable(onClick = onClose, indication = null, interactionSource = remember { MutableInteractionSource() })) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { translationX = offset.value }
                .fillMaxHeight()
                .width(340.dp)
                .background(Color(0xF015171C))
                .clickable(enabled = false) {}
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Box(Modifier.weight(1f, fill = false)) { content() }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun PanelRow(text: String, selected: Boolean = false, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (sub != null) Text(sub, color = Color(0xAAFFFFFF), fontSize = 12.sp)
        }
        if (selected) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}
