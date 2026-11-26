package com.jc.player.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Grabs small frames from a local video for the seek-bar preview.
 * Uses the nearest keyframe (fast) and caches one frame per second.
 */
class FramePreviewer(private val context: Context) {
    private var retriever: MediaMetadataRetriever? = null
    private var uri: Uri? = null
    private val cache = LruCache<Long, Bitmap>(60)
    private val lock = Mutex()

    fun supports(u: Uri?): Boolean = u?.scheme == "content" || u?.scheme == "file"

    suspend fun frameAt(u: Uri, ms: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (!supports(u)) return@withContext null
        lock.withLock {
            if (u != uri) {
                releaseLocked()
                retriever = runCatching { MediaMetadataRetriever().apply { setDataSource(context, u) } }.getOrNull()
                uri = u
            }
            val second = (ms / 1000).coerceAtLeast(0)
            cache.get(second)?.let { return@withContext it }
            val r = retriever ?: return@withContext null
            val us = second * 1_000_000
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                    val h = (320f * full.height / full.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(full, 320, h, true)
                }
            }.getOrNull()
            bmp?.also { cache.put(second, it) }
        }
    }

    suspend fun release() = lock.withLock { releaseLocked() }

    private fun releaseLocked() {
        runCatching { retriever?.release() }
        retriever = null
        uri = null
        cache.evictAll()
    }
}
