package com.jc.player.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import com.jc.player.data.AppSettings

/** Equalizer, bass boost and the >100% volume boost, bound to the player's audio session. */
class AudioEffects {
    var equalizer: Equalizer? = null
        private set
    private var bass: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var night: android.media.audiofx.DynamicsProcessing? = null
    private var sessionId = C.AUDIO_SESSION_ID_UNSET

    val available get() = equalizer != null

    fun attach(id: Int, settings: AppSettings) {
        if (id == sessionId && equalizer != null) return
        release()
        sessionId = id
        if (id == C.AUDIO_SESSION_ID_UNSET || id == 0) return
        equalizer = runCatching { Equalizer(0, id) }.getOrNull()
        bass = runCatching { BassBoost(0, id) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(id) }.getOrNull()
        apply(settings)
        setNightMode(settings.nightMode)
    }

    fun apply(s: AppSettings) {
        equalizer?.let { eq ->
            runCatching {
                eq.enabled = s.eqEnabled
                if (s.eqPreset >= 0 && s.eqPreset < eq.numberOfPresets) {
                    eq.usePreset(s.eqPreset.toShort())
                } else if (s.eqBands.size == eq.numberOfBands.toInt()) {
                    s.eqBands.forEachIndexed { i, level -> eq.setBandLevel(i.toShort(), level.toShort()) }
                }
            }
        }
        bass?.let { b ->
            runCatching {
                b.enabled = s.eqEnabled && s.bassBoost > 0
                if (b.strengthSupported) b.setStrength(s.bassBoost.coerceIn(0, 1000).toShort())
            }
        }
    }

    /** [percent] above 100 amplifies the signal; 200% adds about +12 dB. */
    fun setBoost(percent: Int) {
        val l = loudness ?: return
        runCatching {
            val extra = (percent - 100).coerceIn(0, 100)
            l.setTargetGain(extra * 12)
            l.enabled = extra > 0
        }
    }

    /** Whether this phone supports night mode (Android 9+). */
    val nightSupported get() = android.os.Build.VERSION.SDK_INT >= 28

    /**
     * Night mode: a compressor that lifts quiet dialogue and tames loud scenes,
     * followed by a limiter so nothing clips.
     */
    fun setNightMode(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 28) return
        if (!on) { runCatching { night?.release() }; night = null; return }
        if (night != null || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        night = runCatching {
            val cfg = android.media.audiofx.DynamicsProcessing.Config.Builder(
                android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, false, 0, true, 1, false, 0, true,
            ).build()
            android.media.audiofx.DynamicsProcessing(0, sessionId, cfg).apply {
                setMbcBandAllChannelsTo(0, android.media.audiofx.DynamicsProcessing.MbcBand(
                    true, 20000f, 3f, 120f, 4f, -32f, 6f, -90f, 1f, 0f, 9f,
                ))
                setLimiterAllChannelsTo(android.media.audiofx.DynamicsProcessing.Limiter(
                    true, true, 0, 1f, 60f, 10f, -2f, 0f,
                ))
                enabled = true
            }
        }.getOrNull()
    }

    fun bandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 0
    fun bandRange(): Pair<Int, Int> = equalizer?.bandLevelRange?.let { it[0].toInt() to it[1].toInt() } ?: (-1500 to 1500)
    fun bandFreqHz(band: Int): Int = (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000
    fun bandLevel(band: Int): Int = equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0
    fun presets(): List<String> = equalizer?.let { eq ->
        (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
    } ?: emptyList()

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { loudness?.release() }
        runCatching { night?.release() }
        equalizer = null; bass = null; loudness = null; night = null
        sessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
