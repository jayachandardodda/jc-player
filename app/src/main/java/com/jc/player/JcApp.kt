package com.jc.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.jc.player.data.MediaRepository
import com.jc.player.data.QueueItem
import com.jc.player.data.SettingsRepository
import com.jc.player.data.db.AppDatabase
import com.jc.player.player.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class JcApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings by lazy { SettingsRepository(this) }
    val database by lazy { AppDatabase.create(this) }
    val media by lazy { MediaRepository(this, appScope) }
    val playerHolder by lazy { PlayerHolder(this) }

    /**
     * The queue the player screen should open next. Passed through memory rather than
     * intent extras, because a folder with thousands of videos would overflow an Intent.
     */
    var pendingQueue: Pair<List<QueueItem>, Int>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()

    companion object {
        lateinit var instance: JcApp
            private set
    }
}
