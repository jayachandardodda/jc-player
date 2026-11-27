package com.jc.player.data

import android.content.Context
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class AppSettings(
    // Look
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0,
    // Library
    val sortBy: SortBy = SortBy.NAME,
    val sortAsc: Boolean = true,
    val gridView: Boolean = false,
    val showHiddenDotFolders: Boolean = false,
    val hiddenFolders: Set<String> = emptySet(),
    val privateFolders: Set<String> = emptySet(),
    val pinHash: String? = null,
    // Playback
    val decoder: DecoderMode = DecoderMode.HW,
    val resume: Boolean = true,
    val autoPlayNext: Boolean = true,
    val seekStepSec: Int = 10,
    val defaultSpeed: Float = 1f,
    val backgroundPlay: Boolean = false,
    val autoPip: Boolean = true,
    val autoRotate: Boolean = true,
    val keepBrightness: Boolean = false,
    val lastBrightness: Float = -1f,
    val volumeBoost: Boolean = true,
    // Gestures
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureSeek: Boolean = true,
    val gestureDoubleTap: Boolean = true,
    val gesturePinch: Boolean = true,
    // Subtitles
    val autoLoadSubtitles: Boolean = true,
    val subtitleSizeSp: Int = 20,
    val subtitleColor: Int = Color.WHITE,
    val subtitleBackground: Int = 0, // 0 none, 1 semi, 2 solid
    val subtitleOutline: Boolean = true,
    val subtitleBold: Boolean = false,
    val subtitleBottomPercent: Int = 8,
    val preferredAudioLang: String = "",
    val preferredSubtitleLang: String = "",
    // Audio effects
    val eqEnabled: Boolean = false,
    val eqPreset: Int = -1,
    val eqBands: List<Int> = emptyList(),
    val bassBoost: Int = 0,
    // Night mode: compresses loud/quiet audio for late-night viewing
    val nightMode: Boolean = false,
    // 0 = off, 1 = repeat one, 2 = repeat all (same values as Media3's Player.REPEAT_MODE_*)
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    // Bottom-bar buttons in the player, in display order
    val playerButtons: List<String> = DEFAULT_PLAYER_BUTTONS,
    // Network
    val urlHistory: List<String> = emptyList(),
    val smbServers: List<SmbServer> = emptyList(),
)

val ALL_PLAYER_BUTTONS = listOf("lock", "aspect", "rotate", "screenshot", "speed", "background", "pip", "repeat", "shuffle", "night", "equalizer", "sleep", "abrepeat", "quick")
val DEFAULT_PLAYER_BUTTONS = listOf("lock", "aspect", "rotate", "screenshot", "speed", "background", "pip")

/**
 * Settings are kept in SharedPreferences: reads are synchronous, so the player
 * can apply them immediately on start without flashing defaults.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jc_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        save(next)
    }

    fun checkPin(pin: String): Boolean = current.pinHash == hash(pin)
    fun setPin(pin: String?) = update { it.copy(pinHash = pin?.let(::hash)) }

    fun addUrlToHistory(url: String) = update {
        it.copy(urlHistory = (listOf(url) + it.urlHistory.filter { u -> u != url }).take(30))
    }

    fun smbServerFor(host: String, share: String): SmbServer? =
        current.smbServers.firstOrNull { it.host.equals(host, true) && it.share.equals(share, true) }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("jc:$pin").toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun load(): AppSettings {
        val d = AppSettings()
        fun b(k: String, def: Boolean) = prefs.getBoolean(k, def)
        fun i(k: String, def: Int) = prefs.getInt(k, def)
        fun f(k: String, def: Float) = prefs.getFloat(k, def)
        fun s(k: String, def: String) = prefs.getString(k, def) ?: def
        return AppSettings(
            theme = enumOr(s("theme", d.theme.name), d.theme),
            accent = i("accent", d.accent),
            sortBy = enumOr(s("sortBy", d.sortBy.name), d.sortBy),
            sortAsc = b("sortAsc", d.sortAsc),
            gridView = b("gridView", d.gridView),
            showHiddenDotFolders = b("showHiddenDot", d.showHiddenDotFolders),
            hiddenFolders = prefs.getStringSet("hiddenFolders", emptySet())!!.toSet(),
            privateFolders = prefs.getStringSet("privateFolders", emptySet())!!.toSet(),
            pinHash = prefs.getString("pinHash", null),
            decoder = enumOr(s("decoder", d.decoder.name), d.decoder),
            resume = b("resume", d.resume),
            autoPlayNext = b("autoPlayNext", d.autoPlayNext),
            seekStepSec = i("seekStep", d.seekStepSec),
            defaultSpeed = f("defaultSpeed", d.defaultSpeed),
            backgroundPlay = b("backgroundPlay", d.backgroundPlay),
            autoPip = b("autoPip", d.autoPip),
            autoRotate = b("autoRotate", d.autoRotate),
            keepBrightness = b("keepBrightness", d.keepBrightness),
            lastBrightness = f("lastBrightness", d.lastBrightness),
            volumeBoost = b("volumeBoost", d.volumeBoost),
            gestureBrightness = b("gBright", d.gestureBrightness),
            gestureVolume = b("gVol", d.gestureVolume),
            gestureSeek = b("gSeek", d.gestureSeek),
            gestureDoubleTap = b("gDouble", d.gestureDoubleTap),
            gesturePinch = b("gPinch", d.gesturePinch),
            autoLoadSubtitles = b("autoSubs", d.autoLoadSubtitles),
            subtitleSizeSp = i("subSize", d.subtitleSizeSp),
            subtitleColor = i("subColor", d.subtitleColor),
            subtitleBackground = i("subBg", d.subtitleBackground),
            subtitleOutline = b("subOutline", d.subtitleOutline),
            subtitleBold = b("subBold", d.subtitleBold),
            subtitleBottomPercent = i("subBottom", d.subtitleBottomPercent),
            preferredAudioLang = s("prefAudio", ""),
            preferredSubtitleLang = s("prefSub", ""),
            eqEnabled = b("eqEnabled", d.eqEnabled),
            eqPreset = i("eqPreset", d.eqPreset),
            eqBands = s("eqBands", "").split(',').mapNotNull { it.toIntOrNull() },
            bassBoost = i("bassBoost", 0),
            nightMode = b("nightMode", false),
            repeatMode = i("repeatMode", 0).coerceIn(0, 2),
            shuffle = b("shuffle", false),
            playerButtons = s("playerButtons", DEFAULT_PLAYER_BUTTONS.joinToString(","))
                .split(',').filter { it in ALL_PLAYER_BUTTONS },
            urlHistory = s("urlHistory", "").split('\n').filter { it.isNotBlank() },
            smbServers = parseServers(s("smbServers", "[]")),
        )
    }

    private fun save(a: AppSettings) {
        prefs.edit()
            .putString("theme", a.theme.name)
            .putInt("accent", a.accent)
            .putString("sortBy", a.sortBy.name)
            .putBoolean("sortAsc", a.sortAsc)
            .putBoolean("gridView", a.gridView)
            .putBoolean("showHiddenDot", a.showHiddenDotFolders)
            .putStringSet("hiddenFolders", a.hiddenFolders)
            .putStringSet("privateFolders", a.privateFolders)
            .putString("pinHash", a.pinHash)
            .putString("decoder", a.decoder.name)
            .putBoolean("resume", a.resume)
            .putBoolean("autoPlayNext", a.autoPlayNext)
            .putInt("seekStep", a.seekStepSec)
            .putFloat("defaultSpeed", a.defaultSpeed)
            .putBoolean("backgroundPlay", a.backgroundPlay)
            .putBoolean("autoPip", a.autoPip)
            .putBoolean("autoRotate", a.autoRotate)
            .putBoolean("keepBrightness", a.keepBrightness)
            .putFloat("lastBrightness", a.lastBrightness)
            .putBoolean("volumeBoost", a.volumeBoost)
            .putBoolean("gBright", a.gestureBrightness)
            .putBoolean("gVol", a.gestureVolume)
            .putBoolean("gSeek", a.gestureSeek)
            .putBoolean("gDouble", a.gestureDoubleTap)
            .putBoolean("gPinch", a.gesturePinch)
            .putBoolean("autoSubs", a.autoLoadSubtitles)
            .putInt("subSize", a.subtitleSizeSp)
            .putInt("subColor", a.subtitleColor)
            .putInt("subBg", a.subtitleBackground)
            .putBoolean("subOutline", a.subtitleOutline)
            .putBoolean("subBold", a.subtitleBold)
            .putInt("subBottom", a.subtitleBottomPercent)
            .putString("prefAudio", a.preferredAudioLang)
            .putString("prefSub", a.preferredSubtitleLang)
            .putBoolean("eqEnabled", a.eqEnabled)
            .putInt("eqPreset", a.eqPreset)
            .putString("eqBands", a.eqBands.joinToString(","))
            .putInt("bassBoost", a.bassBoost)
            .putBoolean("nightMode", a.nightMode)
            .putInt("repeatMode", a.repeatMode)
            .putBoolean("shuffle", a.shuffle)
            .putString("playerButtons", a.playerButtons.joinToString(","))
            .putString("urlHistory", a.urlHistory.joinToString("\n"))
            .putString("smbServers", serializeServers(a.smbServers))
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, def: E): E =
        runCatching { enumValueOf<E>(name) }.getOrDefault(def)

    private fun parseServers(json: String): List<SmbServer> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { idx ->
            val o = arr.getJSONObject(idx)
            SmbServer(
                id = o.getString("id"), name = o.optString("name"), host = o.getString("host"),
                share = o.optString("share"), user = o.optString("user"),
                password = o.optString("password"), domain = o.optString("domain"),
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeServers(list: List<SmbServer>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("host", it.host); put("share", it.share)
                put("user", it.user); put("password", it.password); put("domain", it.domain)
            })
        }
    }.toString()
}
