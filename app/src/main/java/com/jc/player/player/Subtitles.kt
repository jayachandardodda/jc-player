package com.jc.player.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.io.File
import kotlin.math.max

data class ExternalSubtitle(val uri: Uri, val name: String)

object Subtitles {
    private val EXTENSIONS = listOf("srt", "ass", "ssa", "vtt", "ttml", "dfxp")
    const val EXTERNAL_ID_PREFIX = "ext:"

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    fun isSubtitleName(name: String) = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /**
     * Finds subtitle files that sit next to a video and share its name, e.g.
     * Movie.mp4 -> Movie.srt, Movie.en.srt. Needs "All files access" on Android 11+.
     */
    fun findSidecars(videoPath: String?): List<ExternalSubtitle> {
        if (videoPath == null) return emptyList()
        val video = File(videoPath)
        val dir = video.parentFile ?: return emptyList()
        val base = video.nameWithoutExtension.lowercase()
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        return files
            .filter { it.isFile && isSubtitleName(it.name) && it.name.lowercase().startsWith(base) && it.canRead() }
            .sortedBy { it.name.length }
            .map { ExternalSubtitle(Uri.fromFile(it), it.name) }
    }

    private val externalIdRegex = Regex("""ext:(\d+)""")

    /** Returns the index encoded in a side-loaded subtitle's format id, or null for embedded tracks. */
    fun externalIndex(formatId: String?): Int? =
        formatId?.let { externalIdRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun configs(subs: List<ExternalSubtitle>, selectIndex: Int): List<MediaItem.SubtitleConfiguration> =
        subs.mapIndexed { i, s ->
            MediaItem.SubtitleConfiguration.Builder(s.uri)
                .setId("$EXTERNAL_ID_PREFIX$i")
                .setMimeType(mimeFor(s.name))
                .setLabel(s.name)
                .setSelectionFlags(if (i == selectIndex) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

    /**
     * Writes a copy of [sub] with every timestamp moved by [offsetMs] and returns its uri.
     * Bytes are read as ISO-8859-1 so any text encoding survives untouched.
     */
    fun shifted(context: Context, sub: ExternalSubtitle, offsetMs: Long): ExternalSubtitle? = runCatching {
        val bytes = context.contentResolver.openInputStream(sub.uri)!!.use { it.readBytes() }
        val text = String(bytes, Charsets.ISO_8859_1)
        val ext = sub.name.substringAfterLast('.', "srt").lowercase()
        val out = when (ext) {
            "ass", "ssa" -> shiftAss(text, offsetMs)
            "ttml", "dfxp" -> return@runCatching null
            else -> shiftSrtVtt(text, offsetMs)
        }
        val dir = File(context.cacheDir, "subs").apply { mkdirs() }
        val file = File(dir, "shift_${sub.uri.toString().hashCode()}_${offsetMs}.$ext")
        file.writeBytes(out.toByteArray(Charsets.ISO_8859_1))
        ExternalSubtitle(Uri.fromFile(file), sub.name)
    }.getOrNull()

    private val srtTime = Regex("""(\d{1,2}):(\d{2}):(\d{2})([,.])(\d{3})""")
    private val vttShort = Regex("""(?<![\d:])(\d{2}):(\d{2})\.(\d{3})""")
    private val assTime = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{2})""")

    internal fun shiftSrtVtt(text: String, offset: Long): String = text.lineSequence().joinToString("\n") { line ->
        if (!line.contains("-->")) line
        else {
            val full = srtTime.replace(line) { m ->
                val (h, mi, s, sep, ms) = m.destructured
                fmtSrt(shift(h.toLong(), mi.toLong(), s.toLong(), ms.toLong(), offset), sep)
            }
            vttShort.replace(full) { m ->
                val (mi, s, ms) = m.destructured
                fmtSrt(shift(0, mi.toLong(), s.toLong(), ms.toLong(), offset), ".")
            }
        }
    }

    internal fun shiftAss(text: String, offset: Long): String = text.lineSequence().joinToString("\n") { line ->
        if (!line.startsWith("Dialogue:", ignoreCase = true)) line
        else assTime.replace(line) { m ->
            val (h, mi, s, cs) = m.destructured
            val t = shift(h.toLong(), mi.toLong(), s.toLong(), cs.toLong() * 10, offset)
            "%d:%02d:%02d.%02d".format(t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, (t % 1000) / 10)
        }
    }

    private fun shift(h: Long, m: Long, s: Long, ms: Long, offset: Long) =
        max(0L, h * 3_600_000 + m * 60_000 + s * 1000 + ms + offset)

    private fun fmtSrt(t: Long, sep: String) =
        "%02d:%02d:%02d%s%03d".format(t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, sep, t % 1000)
}
