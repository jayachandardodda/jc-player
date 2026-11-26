package com.jc.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.media3.common.C
import com.jc.player.JcApp
import com.jc.player.data.AppSettings
import com.jc.player.data.DecoderMode
import com.jc.player.data.formatDuration
import com.jc.player.ui.library.InfoLine

@Composable
fun Panels(c: PlayerController, activity: PlayerActivity, s: AppSettings) {
    val close = { c.panel = Panel.NONE }
    when (c.panel) {
        Panel.AUDIO -> SidePanel("Audio track", close) {
            val options = c.trackOptions(C.TRACK_TYPE_AUDIO)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (options.isEmpty()) Text("No audio tracks", color = Color.Gray)
                options.forEach { o -> PanelRow(o.label, o.selected) { c.selectTrack(C.TRACK_TYPE_AUDIO, o); close() } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x33FFFFFF))
                PanelRow("Equalizer") { c.panel = Panel.EQUALIZER }
                PanelRow("Disable audio", options.isNotEmpty() && options.none { it.selected }) {
                    c.selectTrack(C.TRACK_TYPE_AUDIO, null); close()
                }
            }
        }

        Panel.SUBTITLES -> SidePanel("Subtitles", close) {
            val options = c.trackOptions(C.TRACK_TYPE_TEXT)
            val off = c.subtitlesOff()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PanelRow("Off", off) { c.selectTrack(C.TRACK_TYPE_TEXT, null); close() }
                options.forEach { o -> PanelRow(o.label, o.selected && !off) { c.selectTrack(C.TRACK_TYPE_TEXT, o); close() } }
                PanelRow("Load subtitle file…", sub = "SRT, ASS/SSA, VTT, TTML") { close(); activity.pickSubtitleFile() }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x33FFFFFF))
                Text("Sync (delay)", color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    val canEarlier = c.canShiftEarlier()
                    StepButton("-0.5s", enabled = canEarlier || c.subtitleDelayMs > 0) { c.subtitleDelayMs -= 500 }
                    StepButton("-0.1s", enabled = canEarlier || c.subtitleDelayMs > 0) { c.subtitleDelayMs -= 100 }
                    Text("%+.1fs".format(c.subtitleDelayMs / 1000f), color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp).clickable { c.subtitleDelayMs = 0 })
                    StepButton("+0.1s") { c.subtitleDelayMs += 100 }
                    StepButton("+0.5s") { c.subtitleDelayMs += 500 }
                }
                if (!c.canShiftEarlier()) Text("Built-in subtitles can only be delayed. Subtitle files can be moved either way.",
                    color = Color(0x99FFFFFF), fontSize = 12.sp)
                PanelRow("Style: size, colour, position") { c.panel = Panel.SUB_STYLE }
            }
        }

        Panel.SUB_STYLE -> SidePanel("Subtitle style", close) { SubtitleStyle(s) }

        Panel.SPEED -> SidePanel("Playback speed", close) {
            Column {
                Text("%.2fx".format(c.speed), color = Color.White, fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Slider(value = c.speed, onValueChange = { c.changeSpeed((Math.round(it * 20) / 20f)) }, valueRange = 0.25f..4f)
                Chips(listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f), c.speed,
                    { "${it}x".replace(".0x", "x") }) { c.changeSpeed(it) }
                Text("Pitch is kept natural at every speed.", color = Color(0x99FFFFFF), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }

        Panel.MORE -> SidePanel("More", close) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PanelRow("Decoder: ${s.decoder.name}", sub = "Tap to switch to ${if (s.decoder == DecoderMode.HW) "SW (software)" else "HW (hardware)"}") {
                    close(); activity.switchDecoder(if (s.decoder == DecoderMode.HW) DecoderMode.SW else DecoderMode.HW)
                }
                PanelRow("Playback speed", sub = "%.2fx".format(c.speed)) { c.panel = Panel.SPEED }
                PanelRow("Equalizer") { c.panel = Panel.EQUALIZER }
                PanelRow("Sleep timer", sub = sleepLabel(c)) { c.panel = Panel.SLEEP }
                PanelRow(
                    when { c.abA == null -> "A–B repeat: set point A"; c.abB == null -> "A–B repeat: set point B"; else -> "A–B repeat: turn off" },
                    selected = c.abB != null,
                    sub = c.abA?.let { a -> "A ${formatDuration(a)}" + (c.abB?.let { " → B ${formatDuration(it)}" } ?: "") },
                ) { c.abRepeatTap() }
                PanelRow("Aspect ratio: ${c.aspect.label}") { c.cycleAspect() }
                if (c.zoom != 1f) PanelRow("Reset zoom (${(c.zoom * 100).toInt()}%)") { c.resetZoom() }
                PanelRow("Night mode", c.nightMode, sub = "Louder dialogue, softer explosions") { c.toggleNightMode() }
                PanelRow("Screenshot", sub = "Saved to Pictures/JC Player") { close(); c.takeScreenshot() }
                PanelRow("Background play", c.backgroundPlay, sub = "Keep audio playing when you leave the app") {
                    c.backgroundPlay = !c.backgroundPlay
                }
                PanelRow("Kids lock", sub = "Locks the screen and pins the app") { close(); activity.setKidsLock(true) }
                PanelRow("Video info") { c.panel = Panel.INFO }
            }
        }

        Panel.EQUALIZER -> SidePanel("Equalizer", close) { EqualizerPanel(s) }

        Panel.SLEEP -> SidePanel("Sleep timer", close) {
            Column {
                Text(sleepLabel(c), color = Color(0xAAFFFFFF), modifier = Modifier.padding(bottom = 8.dp))
                PanelRow("Off", c.sleepAt == null && !c.sleepEndOfVideo) { c.setSleepTimer(null); close() }
                listOf(15, 30, 45, 60, 90, 120).forEach { m -> PanelRow("$m minutes") { c.setSleepTimer(m); close() } }
                PanelRow("End of this video", c.sleepEndOfVideo) { c.setSleepTimer(-1); close() }
            }
        }

        Panel.INFO -> SidePanel("Video info", close) {
            val p = c.player
            val holder = JcApp.instance.playerHolder
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val v = p?.videoFormat
                val a = p?.audioFormat
                ColoredInfo("Title", c.title)
                p?.currentMediaItem?.localConfiguration?.uri?.let { ColoredInfo("Source", it.toString()) }
                ColoredInfo("Duration", formatDuration(c.durationMs))
                if (v != null) {
                    ColoredInfo("Video", listOfNotNull(
                        "${v.width}×${v.height}",
                        v.frameRate.takeIf { it > 0 }?.let { "%.2f fps".format(it) },
                        v.codecs ?: v.sampleMimeType,
                        v.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
                    ).joinToString(" · "))
                }
                holder.videoDecoderName?.let { ColoredInfo("Video decoder", it) }
                if (a != null) {
                    ColoredInfo("Audio", listOfNotNull(
                        a.sampleMimeType, a.channelCount.takeIf { it > 0 }?.let { "$it ch" },
                        a.sampleRate.takeIf { it > 0 }?.let { "$it Hz" },
                        a.language,
                    ).joinToString(" · "))
                }
                holder.audioDecoderName?.let { ColoredInfo("Audio decoder", it) }
                ColoredInfo("Decoder mode", s.decoder.name)
            }
        }

        Panel.NONE, Panel.QUICK -> {}
    }
}

private fun sleepLabel(c: PlayerController): String = when {
    c.sleepEndOfVideo -> "Pausing at end of video"
    c.sleepAt != null -> "Pausing in ${((c.sleepAt!! - System.currentTimeMillis()) / 60_000 + 1)} min"
    else -> "Off"
}

@Composable
private fun ColoredInfo(label: String, value: String) {
    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = Color.White)) {
        InfoLine(label, value)
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) Color.White else Color(0x55FFFFFF),
        fontSize = 13.sp,
        modifier = Modifier
            .padding(2.dp)
            .background(Color(0x22FFFFFF), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(values: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            val sel = v == selected
            Text(
                label(v),
                color = if (sel) Color.White else Color(0xDDFFFFFF),
                modifier = Modifier
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                    .clickable { onPick(v) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SubtitleStyle(s: AppSettings) {
    val repo = JcApp.instance.settings
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Size: ${s.subtitleSizeSp}sp", color = Color.White)
        Slider(value = s.subtitleSizeSp.toFloat(), onValueChange = { v -> repo.update { it.copy(subtitleSizeSp = v.toInt()) } }, valueRange = 12f..44f)
        Text("Colour", color = Color.White)
        val colors = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFF8BE9FD, 0xFF69F0AE, 0xFFFF80AB, 0xFFFFAB40).map { it.toInt() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            colors.forEach { col ->
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(col), CircleShape)
                        .border(2.dp, if (s.subtitleColor == col) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                        .clickable { repo.update { it.copy(subtitleColor = col) } }
                )
            }
        }
        Text("Background", color = Color.White)
        Chips(listOf(0, 1, 2), s.subtitleBackground, { listOf("None", "Shaded", "Black")[it] }) { v ->
            repo.update { it.copy(subtitleBackground = v) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Outline", color = Color.White, modifier = Modifier.weight(1f))
            Switch(s.subtitleOutline, { v -> repo.update { it.copy(subtitleOutline = v) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bold", color = Color.White, modifier = Modifier.weight(1f))
            Switch(s.subtitleBold, { v -> repo.update { it.copy(subtitleBold = v) } })
        }
        Text("Distance from bottom: ${s.subtitleBottomPercent}%", color = Color.White)
        Slider(value = s.subtitleBottomPercent.toFloat(), onValueChange = { v -> repo.update { it.copy(subtitleBottomPercent = v.toInt()) } },
            valueRange = 0f..40f)
    }
}

@Composable
private fun EqualizerPanel(s: AppSettings) {
    val repo = JcApp.instance.settings
    val fx = JcApp.instance.playerHolder.effects
    var refresh by remember { mutableIntStateOf(0) }
    fun update(f: (AppSettings) -> AppSettings) { repo.update(f); fx.apply(repo.current); refresh++ }

    if (!fx.available) {
        Text("This phone doesn't allow apps to use the equalizer.", color = Color.White)
        return
    }
    val (min, max) = fx.bandRange()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", color = Color.White, modifier = Modifier.weight(1f))
            Switch(s.eqEnabled, { v -> update { it.copy(eqEnabled = v) } })
        }
        val presets = fx.presets()
        if (presets.isNotEmpty()) {
            Text("Presets", color = Color.White, modifier = Modifier.padding(vertical = 6.dp))
            Chips(presets.indices.toList() + -1, s.eqPreset, { if (it < 0) "Custom" else presets[it] }) { i ->
                update {
                    if (i < 0) it.copy(eqPreset = -1)
                    else it.copy(eqPreset = i, eqEnabled = true)
                }
                if (i >= 0) {
                    // Record the preset's band levels so "Custom" starts from them.
                    repo.update { it.copy(eqBands = (0 until fx.bandCount()).map { b -> fx.bandLevel(b) }) }
                }
            }
        }
        Text("Bands", color = Color.White, modifier = Modifier.padding(top = 12.dp))
        key(refresh) {
            (0 until fx.bandCount()).forEach { b ->
                val level = s.eqBands.getOrNull(b) ?: fx.bandLevel(b)
                val hz = fx.bandFreqHz(b)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", color = Color.White, fontSize = 12.sp, modifier = Modifier.size(40.dp, 20.dp))
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { v ->
                            val bands = (0 until fx.bandCount()).map { i -> s.eqBands.getOrNull(i) ?: fx.bandLevel(i) }.toMutableList()
                            bands[b] = v.toInt()
                            update { it.copy(eqBands = bands, eqPreset = -1, eqEnabled = true) }
                        },
                        valueRange = min.toFloat()..max.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text("%+d".format(level / 100), color = Color.White, fontSize = 12.sp, modifier = Modifier.size(32.dp, 20.dp))
                }
            }
        }
        Text("Bass boost", color = Color.White, modifier = Modifier.padding(top = 8.dp))
        Slider(value = s.bassBoost.toFloat(), onValueChange = { v -> update { it.copy(bassBoost = v.toInt(), eqEnabled = true) } }, valueRange = 0f..1000f)
        OutlinedButton(onClick = {
            update { it.copy(eqPreset = -1, eqBands = List(fx.bandCount()) { 0 }, bassBoost = 0) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
    }
}

@Composable
private fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

/** Swipe-up drawer with every common option as a tile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickDrawer(c: PlayerController, activity: PlayerActivity) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offset = remember { androidx.compose.animation.core.Animatable(with(density) { 320.dp.toPx() }) }
    androidx.compose.runtime.LaunchedEffect(Unit) { offset.animateTo(0f, androidx.compose.animation.core.tween(240)) }
    val close = { c.panel = Panel.NONE }
    Box(
        Modifier.fillMaxSize().background(Color(0x55000000))
            .clickable(onClick = close, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayerTranslationY(offset.value)
                .fillMaxWidth()
                .background(Color(0xF215171C), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) {}
                .androidxSafeDrawing()
                .padding(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).background(Color(0x66FFFFFF), RoundedCornerShape(2.dp)))
            Text("Quick settings", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val I = androidx.compose.material.icons.Icons.Default
                Tile(I.Speed, "Speed", "%.2fx".format(c.speed)) { c.panel = Panel.SPEED }
                Tile(I.Audiotrack, "Audio") { c.panel = Panel.AUDIO }
                Tile(I.Subtitles, "Subtitles") { c.panel = Panel.SUBTITLES }
                Tile(I.Equalizer, "Equalizer") { c.panel = Panel.EQUALIZER }
                Tile(I.NightsStay, "Night mode", if (c.nightMode) "On" else "Off", on = c.nightMode) { c.toggleNightMode() }
                Tile(I.Bedtime, "Sleep timer", sleepLabelShort(c), on = c.sleepAt != null || c.sleepEndOfVideo) { c.panel = Panel.SLEEP }
                Tile(I.Repeat, "A–B repeat", when { c.abA == null -> "Off"; c.abB == null -> "Set B"; else -> "On" }, on = c.abA != null) { c.abRepeatTap() }
                Tile(I.PhotoCamera, "Screenshot") { close(); c.takeScreenshot() }
                Tile(I.Headphones, "Background", if (c.backgroundPlay) "On" else "Off", on = c.backgroundPlay) { c.backgroundPlay = !c.backgroundPlay }
                Tile(I.AspectRatio, "Aspect", c.aspect.label) { c.cycleAspect() }
                Tile(I.ScreenRotation, "Rotate") { activity.toggleRotation() }
                Tile(I.PictureInPictureAlt, "Pop-up") { close(); activity.enterPip() }
                Tile(I.Memory, "Decoder", com.jc.player.JcApp.instance.settings.current.decoder.name) {
                    close(); activity.switchDecoder(if (com.jc.player.JcApp.instance.settings.current.decoder == DecoderMode.HW) DecoderMode.SW else DecoderMode.HW)
                }
                Tile(I.ChildCare, "Kids lock") { close(); activity.setKidsLock(true) }
                Tile(I.Info, "Video info") { c.panel = Panel.INFO }
            }
        }
    }
}

private fun sleepLabelShort(c: PlayerController): String = when {
    c.sleepEndOfVideo -> "End"
    c.sleepAt != null -> "${((c.sleepAt!! - System.currentTimeMillis()) / 60_000 + 1)}m"
    else -> "Off"
}

@Composable
private fun Tile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String? = null, on: Boolean = false, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .size(width = 92.dp, height = 84.dp)
            .background(if (on) accent.copy(alpha = 0.28f) else Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(icon, label, tint = if (on) accent else Color.White)
        Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        if (value != null) Text(value, color = Color(0x99FFFFFF), fontSize = 11.sp, maxLines = 1)
    }
}

private fun Modifier.graphicsLayerTranslationY(y: Float) = this.then(androidx.compose.ui.Modifier.graphicsLayer { translationY = y })
private fun Modifier.androidxSafeDrawing() = this.then(androidx.compose.ui.Modifier.safeDrawingPadding())
