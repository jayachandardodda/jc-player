package com.jc.player.player

import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jc.player.JcApp
import com.jc.player.R

/** Hosts the media notification and lock-screen controls for background playback. */
class PlaybackService : MediaSessionService() {

    private val holder get() = (application as JcApp).playerHolder

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = holder.session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = holder.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            holder.release()
            stopSelf()
        }
    }
}
