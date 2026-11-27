package com.jc.player.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jc.player.JcApp
import com.jc.player.data.AppSettings
import com.jc.player.data.QueueItem
import com.jc.player.data.SortBy
import com.jc.player.data.VideoFolder
import com.jc.player.data.VideoItem
import com.jc.player.data.db.FavoriteEntity
import com.jc.player.data.db.PlaybackStateEntity
import com.jc.player.data.formatSize
import kotlinx.coroutines.launch

fun isFolderVisible(path: String, s: AppSettings): Boolean {
    if (path in s.hiddenFolders || path in s.privateFolders) return false
    if (!s.showHiddenDotFolders && path.split('/').any { it.startsWith(".") }) return false
    return true
}

fun sortVideos(list: List<VideoItem>, s: AppSettings): List<VideoItem> {
    val sorted = when (s.sortBy) {
        SortBy.NAME -> list.sortedWith(compareBy(NaturalOrder) { it.title })
        SortBy.DATE -> list.sortedBy { it.dateAddedSec }
        SortBy.SIZE -> list.sortedBy { it.sizeBytes }
        SortBy.DURATION -> list.sortedBy { it.durationMs }
    }
    return if (s.sortAsc) sorted else sorted.reversed()
}

fun sortFolders(list: List<VideoFolder>, s: AppSettings): List<VideoFolder> {
    val sorted = when (s.sortBy) {
        SortBy.NAME -> list.sortedWith(compareBy(NaturalOrder) { it.name })
        SortBy.DATE -> list.sortedBy { it.latestAdded }
        SortBy.SIZE -> list.sortedBy { it.totalSize }
        SortBy.DURATION -> list.sortedBy { it.count }
    }
    return if (s.sortAsc) sorted else sorted.reversed()
}

/** "Episode 2" sorts before "Episode 10". */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("""\d+|\D+""")
    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val n = p.trimStart('0').length.compareTo(q.trimStart('0').length)
                if (n != 0) n else p.trimStart('0').compareTo(q.trimStart('0'))
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}

fun List<VideoItem>.toQueue() = map { QueueItem(it.uri, it.title, it.path) }

@Composable
fun rememberPlaybackStates(): List<PlaybackStateEntity> {
    val states by JcApp.instance.database.playbackState().observeAll().collectAsState(initial = emptyList())
    return states
}

@Composable
fun rememberProgressMap(): Map<String, Float> {
    val states = rememberPlaybackStates()
    return remember(states) {
        states.associate { it.uri to if (it.durationMs > 0) it.positionMs.toFloat() / it.durationMs else 0f }
    }
}

@Composable
fun rememberFavorites(): List<FavoriteEntity> {
    val favs by JcApp.instance.database.favorites().observeAll().collectAsState(initial = emptyList())
    return favs
}

/** How long a video counts as NEW after it lands on the phone (same idea as MX Player). */
const val NEW_WINDOW_SEC = 7L * 24 * 60 * 60

/** NEW = added to the phone within the last 7 days and never played. */
@Suppress("UNUSED_PARAMETER")
fun isNew(v: VideoItem, progress: Map<String, Float>, s: AppSettings): Boolean =
    v.dateAddedSec >= System.currentTimeMillis() / 1000 - NEW_WINDOW_SEC && progress[v.uri.toString()] == null

/** Plays [video] with the rest of its folder queued after it. */
fun folderQueueFor(video: VideoItem, all: List<VideoItem>, s: AppSettings): Pair<List<QueueItem>, Int> {
    val list = sortVideos(all.filter { it.folderPath == video.folderPath }, s)
    return list.toQueue() to list.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
}

/**
 * Returns a function that deletes videos, showing Android's confirmation dialog
 * on Android 11+ and deleting directly on older versions.
 */
@Composable
fun rememberVideoDeleter(onDone: (deleted: Boolean) -> Unit = {}): (List<VideoItem>) -> Unit {
    val app = JcApp.instance
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    suspend fun cleanup(items: List<VideoItem>) {
        items.forEach { app.database.playbackState().delete(it.uri.toString()); app.database.favorites().remove(it.uri.toString()) }
        app.media.refresh()
        onDone(true)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        val items = pending
        pending = emptyList()
        if (it.resultCode == Activity.RESULT_OK) scope.launch { cleanup(items) } else onDone(false)
    }
    return { items ->
        if (items.isNotEmpty()) {
            val req = app.media.deleteRequest(items.map { it.uri })
            if (req != null) {
                pending = items
                launcher.launch(IntentSenderRequest.Builder(req.intentSender).build())
            } else scope.launch { app.media.deleteDirect(items.map { it.uri }); cleanup(items) }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(items: List<VideoItem>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (items.size == 1) "Delete video?" else "Delete ${items.size} videos?") },
        text = {
            Text(
                if (items.size == 1) "\"${items[0].title}\" will be permanently deleted from your phone."
                else "${items.size} videos (${formatSize(items.sumOf { it.sizeBytes })}) will be permanently deleted from your phone."
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SortDialog(settings: AppSettings, onDismiss: () -> Unit) {
    val repo = JcApp.instance.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column {
                SortBy.entries.forEach { by ->
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = { repo.update { it.copy(sortBy = by) } }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.sortBy == by, onClick = { repo.update { it.copy(sortBy = by) } })
                        Text(by.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                listOf(true to "Ascending", false to "Descending").forEach { (asc, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.sortAsc == asc, onClick = { repo.update { it.copy(sortAsc = asc) } })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetItem(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurface)
        Text(label, Modifier.padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NewBadge() {
    Text(
        "NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

