package com.jc.player.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jc.player.JcApp
import com.jc.player.data.AppSettings
import com.jc.player.data.DecoderMode
import com.jc.player.data.ThemeMode
import com.jc.player.ui.allFilesAccessIntent
import com.jc.player.ui.common.TextInputDialog
import com.jc.player.ui.hasAllFilesAccess
import com.jc.player.ui.theme.Accents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onPlayerButtons: () -> Unit = {}, onHistory: () -> Unit = {}, onCleaner: () -> Unit = {}) {
    val app = JcApp.instance
    val repo = app.settings
    val s by repo.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    fun set(f: (AppSettings) -> AppSettings) = repo.update(f)

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { Header("Appearance") }
            item { Choice("Theme", s.theme.label()) { dialog = "theme" } }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Accents.forEachIndexed { i, (_, color) ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(color, CircleShape)
                                    .border(2.dp, if (i == s.accent) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                    .clickable { set { it.copy(accent = i) } },
                                contentAlignment = Alignment.Center,
                            ) { if (i == s.accent) Icon(Icons.Default.Check, null, tint = Color.White) }
                        }
                    }
                }
            }

            item { Header("Playback") }
            item { Choice("Decoder", if (s.decoder == DecoderMode.HW) "HW: hardware (fast, saves battery)" else "SW: software (plays more files, uses more battery)") { dialog = "decoder" } }
            item { Toggle("Resume where you left off", s.resume) { v -> set { it.copy(resume = v) } } }
            item { Toggle("Auto-play next video", s.autoPlayNext) { v -> set { it.copy(autoPlayNext = v) } } }
            item { Toggle("Background play", s.backgroundPlay, "Keep playing audio when you leave the app") { v -> set { it.copy(backgroundPlay = v) } } }
            item { Toggle("Picture-in-picture on Home", s.autoPip, "Shrink the video to a floating window when you press Home") { v -> set { it.copy(autoPip = v) } } }
            item { Toggle("Rotate to match video", s.autoRotate, "Landscape videos open in landscape") { v -> set { it.copy(autoRotate = v) } } }
            item { Toggle("Volume boost up to 200%", s.volumeBoost) { v -> set { it.copy(volumeBoost = v) } } }
            item { Toggle("Remember brightness", s.keepBrightness) { v -> set { it.copy(keepBrightness = v) } } }
            item { SliderSetting("Seek step", s.seekStepSec.toFloat(), 5f..30f, 4, "${s.seekStepSec} s") { v -> set { it.copy(seekStepSec = v.toInt()) } } }
            item { SliderSetting("Default speed", s.defaultSpeed, 0.25f..4f, 14, "%.2fx".format(s.defaultSpeed)) { v ->
                set { it.copy(defaultSpeed = (Math.round(v * 4) / 4f)) } } }
            item { Choice("Preferred audio language", s.preferredAudioLang.ifBlank { "Video default" }) { dialog = "audioLang" } }
            item { Toggle("Night mode", s.nightMode, "Boosts quiet dialogue and softens loud scenes (Android 9+)") { v -> set { it.copy(nightMode = v) } } }
            item { Choice("Player buttons", "Choose and reorder the buttons under the seek bar") { onPlayerButtons() } }

            item { Header("Gestures") }
            item { Toggle("Swipe left side for brightness", s.gestureBrightness) { v -> set { it.copy(gestureBrightness = v) } } }
            item { Toggle("Swipe right side for volume", s.gestureVolume) { v -> set { it.copy(gestureVolume = v) } } }
            item { Toggle("Swipe sideways to seek", s.gestureSeek) { v -> set { it.copy(gestureSeek = v) } } }
            item { Toggle("Double-tap to skip", s.gestureDoubleTap) { v -> set { it.copy(gestureDoubleTap = v) } } }
            item { Toggle("Pinch to zoom", s.gesturePinch) { v -> set { it.copy(gesturePinch = v) } } }

            item { Header("Subtitles") }
            item { Toggle("Auto-load subtitle files", s.autoLoadSubtitles, "Loads Movie.srt when you play Movie.mp4") { v -> set { it.copy(autoLoadSubtitles = v) } } }
            item {
                val granted = hasAllFilesAccess(context)
                Choice("All files access", if (granted) "Allowed" else "Needed on Android 11+ to find subtitle files next to videos") {
                    context.startActivity(allFilesAccessIntent(context))
                }
            }
            item { Choice("Preferred subtitle language", s.preferredSubtitleLang.ifBlank { "Video default" }) { dialog = "subLang" } }
            item { Text("Subtitle size, colour and position can be changed from the player's subtitle menu.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

            item { Header("Library") }
            item { Toggle("Show folders starting with \".\"", s.showHiddenDotFolders) { v -> set { it.copy(showHiddenDotFolders = v) } } }
            item { Choice("Hidden folders", "${s.hiddenFolders.size} hidden") { dialog = "hidden" } }
            item { Choice("Private folder PIN", if (s.pinHash == null) "Not set" else "Change or remove") { dialog = "pin" } }
            item { Choice("Watch history", "See, resume or clear what you've watched") { onHistory() } }
            item { Choice("Clean up storage", "Find duplicates, watched and large videos") { onCleaner() } }

            item { Header("About") }
            item { Choice("JC Player 1.2", "No ads, no tracking. Built on Media3/ExoPlayer and FFmpeg.") {} }
        }
    }

    when (dialog) {
        "theme" -> Picker("Theme", ThemeMode.entries.map { it.label() }, ThemeMode.entries.indexOf(s.theme), { dialog = null }) { i ->
            set { it.copy(theme = ThemeMode.entries[i]) }
        }
        "decoder" -> Picker("Decoder", listOf("HW (hardware)", "SW (software)"), if (s.decoder == DecoderMode.HW) 0 else 1, { dialog = null }) { i ->
            set { it.copy(decoder = if (i == 0) DecoderMode.HW else DecoderMode.SW) }
        }
        "audioLang", "subLang" -> {
            val langs = listOf("" to "Video default", "en" to "English", "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu",
                "ml" to "Malayalam", "kn" to "Kannada", "bn" to "Bengali", "mr" to "Marathi", "ja" to "Japanese",
                "ko" to "Korean", "es" to "Spanish", "fr" to "French", "de" to "German")
            val current = if (dialog == "audioLang") s.preferredAudioLang else s.preferredSubtitleLang
            val isAudio = dialog == "audioLang"
            Picker("Preferred language", langs.map { it.second }, langs.indexOfFirst { it.first == current }.coerceAtLeast(0), { dialog = null }) { i ->
                val code = langs[i].first
                set { if (isAudio) it.copy(preferredAudioLang = code) else it.copy(preferredSubtitleLang = code) }
            }
        }
        "hidden" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Hidden folders") },
            text = {
                if (s.hiddenFolders.isEmpty()) Text("No hidden folders. Long-press a folder to hide it.")
                else LazyColumn {
                    s.hiddenFolders.sorted().forEach { path ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(path, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { set { it.copy(hiddenFolders = it.hiddenFolders - path) } }) { Text("Unhide") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
        )
        "pin" -> if (s.pinHash == null) {
            TextInputDialog("Set PIN", label = "4–8 digits", numeric = true, password = true, onDismiss = { dialog = null }) { pin ->
                if (pin.length in 4..8 && pin.all(Char::isDigit)) { repo.setPin(pin); dialog = null }
                else Toast.makeText(context, "Use 4–8 digits", Toast.LENGTH_SHORT).show()
            }
        } else {
            TextInputDialog("Enter current PIN", numeric = true, password = true, confirm = "Next", onDismiss = { dialog = null }) { pin ->
                if (repo.checkPin(pin)) dialog = "pinNew" else Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }
        "pinNew" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Private folder PIN") },
            text = { Text("Change the PIN, or remove it. Removing the PIN puts all private folders back in the main list.") },
            confirmButton = { TextButton(onClick = { repo.setPin(null); set { it.copy(privateFolders = emptySet()) }; dialog = null }) { Text("Remove PIN") } },
            dismissButton = { TextButton(onClick = { repo.setPin(null); dialog = "pin" }) { Text("Change PIN") } },
        )
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "Black (AMOLED)"
}

@Composable
private fun Header(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SliderSetting(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun Picker(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                options.forEachIndexed { i, label ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = i == selected, onClick = { onPick(i); onDismiss() })
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val buttonLabels = mapOf(
    "lock" to "Screen lock", "aspect" to "Aspect ratio", "rotate" to "Rotate", "screenshot" to "Screenshot",
    "speed" to "Playback speed", "background" to "Background play", "pip" to "Picture-in-picture",
    "repeat" to "Repeat", "shuffle" to "Shuffle", "night" to "Night mode", "equalizer" to "Equalizer", "sleep" to "Sleep timer", "abrepeat" to "A–B repeat",
    "quick" to "Quick settings",
)

/** Choose which buttons show under the player's seek bar, and their order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerButtonsScreen(onBack: () -> Unit) {
    val repo = JcApp.instance.settings
    val s by repo.state.collectAsState()
    val enabled = s.playerButtons
    val disabled = com.jc.player.data.ALL_PLAYER_BUTTONS.filter { it !in enabled }
    fun save(list: List<String>) = repo.update { it.copy(playerButtons = list) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player buttons") },
                navigationIcon = { androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { save(com.jc.player.data.DEFAULT_PLAYER_BUTTONS) }) { Text("Reset") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { Header("Shown (left to right)") }
            enabled.forEachIndexed { i, id ->
                item(key = "on_$id") {
                    Row(Modifier.animateItem().fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(buttonLabels[id] ?: id, Modifier.weight(1f))
                        androidx.compose.material3.IconButton(enabled = i > 0, onClick = {
                            save(enabled.toMutableList().apply { add(i - 1, removeAt(i)) })
                        }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        androidx.compose.material3.IconButton(enabled = i < enabled.lastIndex, onClick = {
                            save(enabled.toMutableList().apply { add(i + 1, removeAt(i)) })
                        }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        Switch(checked = true, onCheckedChange = { save(enabled - id) })
                    }
                }
            }
            if (disabled.isNotEmpty()) {
                item { Header("Hidden") }
                disabled.forEach { id ->
                    item(key = "off_$id") {
                        Row(Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(buttonLabels[id] ?: id, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = false, onCheckedChange = { save(enabled + id) })
                        }
                    }
                }
            }
            item {
                Text("Tip: swipe up from the bottom of the video, or use the Quick settings button, for every option at once.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
            }
        }
    }
}
