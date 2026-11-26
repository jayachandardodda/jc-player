package com.jc.player.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context, private val scope: CoroutineScope) {

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var observerRegistered = false
    private var pendingRefresh: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // MediaStore fires many events in a burst; debounce them.
            pendingRefresh?.cancel()
            pendingRefresh = scope.launch { delay(800); refresh() }
        }
    }

    fun start() {
        if (!observerRegistered) {
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
            )
            observerRegistered = true
        }
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        _loading.value = true
        val list = withContext(Dispatchers.IO) { runCatching { query() }.getOrDefault(emptyList()) }
        _videos.value = list
        _loading.value = false
    }

    @Suppress("DEPRECATION")
    private fun query(): List<VideoItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val out = ArrayList<VideoItem>()
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeC = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val wC = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hC = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dateC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeC = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val bucketC = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val path = c.getString(dataC)
                val folderPath = path?.let { File(it).parent } ?: "/"
                val name = c.getString(nameC) ?: path?.substringAfterLast('/') ?: "video"
                out += VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                    title = name,
                    path = path,
                    folderPath = folderPath,
                    folderName = c.getString(bucketC) ?: File(folderPath).name.ifEmpty { "Storage" },
                    durationMs = c.getLong(durC),
                    sizeBytes = c.getLong(sizeC),
                    width = c.getInt(wC),
                    height = c.getInt(hC),
                    dateAddedSec = c.getLong(dateC),
                    mimeType = c.getString(mimeC),
                )
            }
        }
        return out
    }

    /** Android 11+: returns a system dialog asking the user to confirm deletion. */
    fun deleteRequest(uris: List<Uri>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            MediaStore.createDeleteRequest(context.contentResolver, uris) else null

    /** Android 8–10: delete directly (needs WRITE_EXTERNAL_STORAGE on 8/9). */
    suspend fun deleteDirect(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        runCatching { uris.forEach { context.contentResolver.delete(it, null, null) } }.isSuccess
    }

    fun writeRequest(uri: Uri): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)) else null

    suspend fun rename(item: VideoItem, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        val ext = item.title.substringAfterLast('.', "")
        val finalName = if (ext.isNotEmpty() && !newTitle.endsWith(".$ext", true)) "$newTitle.$ext" else newTitle
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, finalName) }
                context.contentResolver.update(item.uri, values, null, null) > 0
            } else {
                val path = item.path ?: return@runCatching false
                val src = File(path)
                val dst = File(src.parentFile, finalName)
                src.renameTo(dst).also { ok ->
                    if (ok) {
                        @Suppress("DEPRECATION")
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DATA, dst.absolutePath)
                            put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
                        }
                        context.contentResolver.update(item.uri, values, null, null)
                    }
                }
            }
        }.getOrDefault(false).also { if (it) refresh() }
    }

    /** Best-effort display name for any uri (content://, file://, http...). */
    fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: uri.toString()
    }

    /** Best-effort filesystem path for a content uri (used to find subtitles next to a video). */
    @Suppress("DEPRECATION")
    fun pathFor(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        _videos.value.firstOrNull { it.uri == uri }?.path?.let { return it }
        if (uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }
}
