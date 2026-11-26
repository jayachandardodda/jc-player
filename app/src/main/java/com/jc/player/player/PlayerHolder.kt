package com.jc.player.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.jc.player.JcApp
import com.jc.player.data.DecoderMode
import com.jc.player.ui.player.PlayerActivity

/**
 * Owns the single ExoPlayer instance and its MediaSession. Living outside the
 * activity lets audio keep playing in the background and in picture-in-picture.
 */
class PlayerHolder(private val app: JcApp) {

    var player: ExoPlayer? = null
        private set
    var session: MediaSession? = null
        private set
    var decoderMode: DecoderMode? = null
        private set

    val effects = AudioEffects()

    /** Name of the video decoder in use, shown in the info dialog. */
    var videoDecoderName: String? = null
    var audioDecoderName: String? = null

    fun obtain(mode: DecoderMode): ExoPlayer {
        player?.let { if (decoderMode == mode) return it }
        release()

        val renderers = DefaultRenderersFactory(app)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (mode == DecoderMode.SW) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
            .setMediaCodecSelector(if (mode == DecoderMode.SW) softwareFirst else MediaCodecSelector.DEFAULT)

        val s = app.settings.current
        val p = ExoPlayer.Builder(app, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(RoutingDataSource.Factory(app)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(s.seekStepSec * 1000L)
            .setSeekForwardIncrementMs(s.seekStepSec * 1000L)
            .build()

        p.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effects.attach(audioSessionId, app.settings.current)
            }
        })
        p.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long,
            ) { videoDecoderName = decoderName }

            override fun onAudioDecoderInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long,
            ) { audioDecoderName = decoderName }
        })
        effects.attach(p.audioSessionId, s)

        val openPlayer = PendingIntent.getActivity(
            app, 0,
            Intent(app, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_REATTACH, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(app, p)
            .setId("jc-${System.nanoTime()}")
            .setSessionActivity(openPlayer)
            .build()
        player = p
        decoderMode = mode
        return p
    }

    fun release() {
        session?.release()
        session = null
        player?.release()
        player = null
        decoderMode = null
        effects.release()
        videoDecoderName = null
        audioDecoderName = null
    }

    private companion object {
        /** Lists software (CPU) decoders before hardware ones for the "SW" decoder mode. */
        val softwareFirst = MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                .sortedBy { if (it.softwareOnly) 0 else 1 }
        }
    }
}
