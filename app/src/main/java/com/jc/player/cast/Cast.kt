package com.jc.player.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

/** Tells the Cast SDK to use Google's default media receiver (no registration needed). */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setStopReceiverApplicationWhenEndingSession(true)
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/**
 * Tiny HTTP server so a Chromecast on the same Wi-Fi can fetch videos stored on the phone.
 * Supports Range requests, which the Cast receiver needs for seeking.
 */
class LocalMediaServer(private val context: Context) : NanoHTTPD(0) {

    private val items = ConcurrentHashMap<String, Pair<Uri, String>>()

    fun register(uri: Uri, mime: String): String {
        val id = Integer.toHexString(uri.toString().hashCode()) + "_" + items.size
        items[id] = uri to mime
        val ip = wifiAddress(context) ?: throw IllegalStateException("Connect to Wi-Fi to cast videos from this phone")
        return "http://$ip:$listeningPort/media/$id"
    }

    override fun serve(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/media/")
        val (uri, mime) = items[id] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        return try {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
            val total = if (afd.length >= 0) afd.length else afd.parcelFileDescriptor.statSize
            val range = session.headers["range"]?.removePrefix("bytes=")?.split("-")
            val start = range?.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = range?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            val stream: InputStream = FileInputStream(afd.fileDescriptor).also {
                it.channel.position(afd.startOffset + start)
            }
            val length = end - start + 1
            val resp = if (range != null) {
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, stream, length).apply {
                    addHeader("Content-Range", "bytes $start-$end/$total")
                }
            } else newFixedLengthResponse(Response.Status.OK, mime, stream, total)
            resp.addHeader("Accept-Ranges", "bytes")
            resp.addHeader("Access-Control-Allow-Origin", "*")
            resp
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
    }

    companion object {
        fun wifiAddress(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }

        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            "webm" -> MimeTypes.VIDEO_WEBM
            "m3u8" -> MimeTypes.APPLICATION_M3U8
            "mpd" -> MimeTypes.APPLICATION_MPD
            "ts", "m2ts" -> MimeTypes.VIDEO_MP2T
            else -> MimeTypes.VIDEO_MP4
        }
    }
}
