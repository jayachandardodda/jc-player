package com.jc.player.ui.library

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jc.player.JcApp
import com.jc.player.data.QueueItem
import com.jc.player.data.VideoItem
import com.jc.player.data.formatDuration
import com.jc.player.data.formatSize
import com.jc.player.ui.common.EmptyState
import com.jc.player.ui.common.VideoThumbnail
import com.jc.player.ui.common.playQueue
import kotlinx.coroutines.launch

// ---------------- watch history ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val app = JcApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = rememberPlaybackStates()
    val videos by app.media.videos.collectAsState()
    val settings by app.settings.state.collectAsState()
    val byUri = remember(videos) { videos.associateBy { it.uri.toString() } }
    val history = remember(states) { states.sortedByDescending { it.lastPlayed } }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (history.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "Clear all") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (history.isEmpty()) EmptyState(Icons.Default.History, "No history yet", "Videos you watch will appear here.")
            else LazyColumn {
                items(history, key = { it.uri }) { s ->
                    val local = byUri[s.uri]
                    val uri = Uri.parse(s.uri)
                    val frac = if (s.durationMs > 0) s.positionMs.toFloat() / s.durationMs else 0f
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clickable {
                                if (local != null) { val (q, i) = folderQueueFor(local, videos, settings); context.playQueue(q, i) }
                                else context.playQueue(listOf(QueueItem(uri, s.title.ifBlank { app.media.displayName(uri) })), 0)
                            }
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoThumbnail(uri, if (s.durationMs > 0) formatDuration(s.durationMs) else null, frac,
                            Modifier.width(120.dp).aspectRatio(16f / 10f))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(local?.title ?: s.title.ifBlank { uri.lastPathSegment ?: s.uri }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (frac >= 0.95f) "Watched" else "${(frac * 100).toInt()}% watched") + " · " +
                                    DateUtils.getRelativeTimeSpanString(s.lastPlayed),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { scope.launch { app.database.playbackState().delete(s.uri) } }) {
                            Icon(Icons.Default.Close, "Remove from history")
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear watch history?") },
        text = { Text("Resume positions and progress bars will be reset. Your videos are not deleted.") },
        confirmButton = { TextButton(onClick = { scope.launch { app.database.playbackState().clear() }; confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

// ---------------- storage cleaner ----------------

private data class CleanGroup(val title: String, val items: List<VideoItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(onBack: () -> Unit) {
    val app = JcApp.instance
    val videos by app.media.videos.collectAsState()
    val states = rememberPlaybackStates()
    var tab by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf(false) }
    val deleter = rememberVideoDeleter { deleted -> if (deleted) checked = emptySet() }

    val duplicates = remember(videos) {
        videos.filter { it.sizeBytes > 0 }
            .groupBy { it.sizeBytes to it.durationMs / 1000 }
            .values.filter { it.size > 1 }
            .sortedByDescending { it.first().sizeBytes * (it.size - 1) }
            .map { g -> CleanGroup("${g.size} copies · ${formatSize(g.first().sizeBytes)} each", g.sortedBy { it.dateAddedSec }) }
    }
    val watched = remember(videos, states) {
        val done = states.filter { it.durationMs > 0 && it.positionMs >= it.durationMs * 0.9 }.map { it.uri }.toSet()
        videos.filter { it.uri.toString() in done && it.sizeBytes >= 50L * 1024 * 1024 }.sortedByDescending { it.sizeBytes }
    }
    val largest = remember(videos) { videos.sortedByDescending { it.sizeBytes }.take(25) }

    // Pre-tick every duplicate except the oldest copy in each group.
    LaunchedEffect(tab, duplicates) {
        checked = if (tab == 0) duplicates.flatMap { it.items.drop(1) }.map { it.id }.toSet() else emptySet()
    }
    val pool = when (tab) { 0 -> duplicates.flatMap { it.items }; 1 -> watched; else -> largest }
    val chosen = pool.filter { it.id in checked }.distinctBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean up storage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${chosen.size} selected", fontWeight = FontWeight.SemiBold)
                        Text("Frees ${formatSize(chosen.sumOf { it.sizeBytes })}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { confirm = true }) { Text("Delete") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Duplicates" to duplicates.size, "Watched" to watched.size, "Largest" to largest.size).forEachIndexed { i, (l, n) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text("$l ($n)") })
                }
            }
            val description = when (tab) {
                0 -> "Same size and length. The oldest copy is kept unticked."
                1 -> "Videos over 50 MB you've already watched to the end."
                else -> "Your biggest videos."
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 10.dp))
            if (pool.isEmpty()) EmptyState(Icons.Default.CleaningServices, "Nothing to clean here")
            else LazyColumn(Modifier.weight(1f)) {
                if (tab == 0) duplicates.forEach { g ->
                    item(key = "h" + g.items.first().id) {
                        Text(g.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
                    }
                    items(g.items, key = { "d" + it.id }) { v -> CleanRow(v, v.id in checked) { checked = if (v.id in checked) checked - v.id else checked + v.id } }
                } else items(pool, key = { it.id }) { v ->
                    CleanRow(v, v.id in checked) { checked = if (v.id in checked) checked - v.id else checked + v.id }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (confirm) ConfirmDeleteDialog(chosen, onDismiss = { confirm = false }) { deleter(chosen) }
}

@Composable
private fun CleanRow(v: VideoItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, { onToggle() })
        VideoThumbnail(v.uri, formatDuration(v.durationMs), null, Modifier.width(96.dp).aspectRatio(16f / 10f))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatSize(v.sizeBytes)} · ${v.folderName}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
