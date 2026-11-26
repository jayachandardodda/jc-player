package com.jc.player.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val path: String?,
    val folderPath: String,
    val folderName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedSec: Long,
    val mimeType: String?,
)

data class VideoFolder(
    val path: String,
    val name: String,
    val videos: List<VideoItem>,
) {
    val count get() = videos.size
    val totalSize get() = videos.sumOf { it.sizeBytes }
    val latestAdded get() = videos.maxOfOrNull { it.dateAddedSec } ?: 0L
}

/** One entry of the play queue handed to the player screen. */
data class QueueItem(
    val uri: Uri,
    val title: String,
    val path: String? = null,
    /** Subtitle files that belong to this video (e.g. found next to it on a network share). */
    val subtitleUris: List<Uri> = emptyList(),
)

enum class SortBy { NAME, DATE, SIZE, DURATION }
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class DecoderMode { HW, SW }

data class SmbServer(
    val id: String,
    val name: String,
    val host: String,
    val share: String,
    val user: String,
    val password: String,
    val domain: String,
)

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i <= 1) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}
