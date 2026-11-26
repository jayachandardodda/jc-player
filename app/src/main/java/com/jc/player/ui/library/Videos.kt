package com.jc.player.ui.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jc.player.JcApp
import com.jc.player.data.VideoItem
import com.jc.player.data.formatDuration
import com.jc.player.data.formatSize
import com.jc.player.ui.common.EmptyState
import com.jc.player.ui.common.TextInputDialog
import com.jc.player.ui.common.VideoThumbnail
import com.jc.player.ui.common.playQueue
import com.jc.player.ui.common.shareVideos
import com.jc.player.ui.playlists.AddToPlaylistDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Which videos are ticked in multi-select mode. Empty = not selecting. */
class Selection {
    var ids by mutableStateOf(setOf<Long>())
    val active get() = ids.isNotEmpty()
    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
    fun clear() { ids = emptySet() }
}

// ---------------- videos in a folder ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(folderPath: String, onBack: () -> Unit) {
    val app = JcApp.instance
    val context = LocalContext.current
    val videos by app.media.videos.collectAsState()
    val settings by app.settings.state.collectAsState()
    var showSort by remember { mutableStateOf(false) }
    val selection = remember { Selection() }
    val list = remember(videos, settings, folderPath) { sortVideos(videos.filter { it.folderPath == folderPath }, settings) }
    val title = list.firstOrNull()?.folderName ?: File(folderPath).name
    BackHandler(selection.active) { selection.clear() }

    Scaffold(
        topBar = {
            if (selection.active) SelectionTopBar(selection, list)
            else TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${list.size} videos · ${formatSize(list.sumOf { it.sizeBytes })}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { context.playQueue(list.toQueue(), 0) }) { Icon(Icons.Default.PlayArrow, "Play all") }
                    IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    IconButton(onClick = { app.settings.update { it.copy(gridView = !it.gridView) } }) {
                        Icon(if (settings.gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, "Layout")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (list.isEmpty()) EmptyState(Icons.Default.VideoLibrary, "This folder is empty")
            else VideoList(list, settings.gridView, selection) { index -> context.playQueue(list.toQueue(), index) }
        }
    }
    if (showSort) SortDialog(settings) { showSort = false }
}

/** Contextual top bar shown while videos are selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(selection: Selection, all: List<VideoItem>) {
    val app = JcApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chosen = all.filter { it.id in selection.ids }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val deleter = rememberVideoDeleter { deleted -> if (deleted) selection.clear() }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        navigationIcon = { IconButton(onClick = { selection.clear() }) { Icon(Icons.Default.Close, "Cancel") } },
        title = { Text("${chosen.size} selected") },
        actions = {
            IconButton(onClick = { selection.ids = if (chosen.size == all.size) emptySet() else all.map { it.id }.toSet() }) {
                Icon(Icons.Default.SelectAll, "Select all")
            }
            IconButton(onClick = { context.shareVideos(chosen.map { it.uri }) }) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.Delete, "Delete") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Play selected") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                    onClick = { menu = false; context.playQueue(chosen.toQueue(), 0); selection.clear() })
                DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = { menu = false; dialog = "playlist" })
                DropdownMenuItem(text = { Text("Add to favourites") }, leadingIcon = { Icon(Icons.Default.Star, null) },
                    onClick = {
                        menu = false
                        scope.launch {
                            chosen.forEach { app.database.favorites().add(com.jc.player.data.db.FavoriteEntity(it.uri.toString(), "video", it.title)) }
                            Toast.makeText(context, "Added to favourites", Toast.LENGTH_SHORT).show()
                            selection.clear()
                        }
                    })
                DropdownMenuItem(text = { Text("Move to…") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                    onClick = { menu = false; dialog = "move" })
                DropdownMenuItem(text = { Text("Copy to…") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = { menu = false; dialog = "copy" })
            }
        },
    )
    when (dialog) {
        "delete" -> ConfirmDeleteDialog(chosen, onDismiss = { dialog = null }) { deleter(chosen) }
        "playlist" -> AddToPlaylistDialog(chosen.map { it.uri.toString() to it.title }) { dialog = null; selection.clear() }
        "move", "copy" -> MoveCopyDialog(chosen, move = dialog == "move") { dialog = null; selection.clear() }
    }
}

// ---------------- search ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val app = JcApp.instance
    val context = LocalContext.current
    val videos by app.media.videos.collectAsState()
    val settings by app.settings.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val selection = remember { Selection() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val results = remember(videos, settings, query) {
        if (query.isBlank()) emptyList()
        else sortVideos(videos.filter { isFolderVisible(it.folderPath, settings) && it.title.contains(query.trim(), true) }, settings)
    }
    BackHandler(selection.active) { selection.clear() }
    Scaffold(
        topBar = {
            if (selection.active) SelectionTopBar(selection, results)
            else TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search videos") }, singleLine = true,
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                query.isBlank() -> EmptyState(Icons.Default.Search, "Search all your videos", "Type part of a file name")
                results.isEmpty() -> EmptyState(Icons.Default.Search, "No matches")
                else -> VideoList(results, false, selection) { i ->
                    val (q, idx) = folderQueueFor(results[i], videos, settings)
                    context.playQueue(q, idx)
                }
            }
        }
    }
}

// ---------------- the list itself ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoList(
    list: List<VideoItem>,
    grid: Boolean,
    selection: Selection,
    header: (@Composable () -> Unit)? = null,
    onPlay: (Int) -> Unit,
) {
    val settings by JcApp.instance.settings.state.collectAsState()
    val progress = rememberProgressMap()
    val favorites = rememberFavorites()
    val favKeys = remember(favorites) { favorites.map { it.key }.toSet() }
    var menuFor by remember { mutableStateOf<VideoItem?>(null) }

    fun click(index: Int, v: VideoItem) { if (selection.active) selection.toggle(v.id) else onPlay(index) }

    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(list, key = { _, it -> it.id }) { index, v ->
                val selected = v.id in selection.ids
                Column(
                    Modifier
                        .animateItem()
                        .combinedClickable(onClick = { click(index, v) }, onLongClick = { selection.toggle(v.id) })
                ) {
                    Box {
                        VideoThumbnail(v.uri, formatDuration(v.durationMs), progress[v.uri.toString()],
                            Modifier.fillMaxWidth().aspectRatio(16f / 10f))
                        if (selection.active) SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        else if (isNew(v, progress, settings)) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { NewBadge() }
                    }
                    Row(Modifier.padding(top = 6.dp)) {
                        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        if (v.uri.toString() in favKeys) Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color(0xFFFFC107))
                    }
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            if (header != null) item { header() }
            itemsIndexed(list, key = { _, it -> it.id }) { index, v ->
                val selected = v.id in selection.ids
                val p = progress[v.uri.toString()]
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .combinedClickable(onClick = { click(index, v) }, onLongClick = { selection.toggle(v.id) })
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        VideoThumbnail(v.uri, formatDuration(v.durationMs), p, Modifier.width(128.dp).aspectRatio(16f / 10f))
                        if (selection.active) SelectMark(selected, Modifier.align(Alignment.TopStart).padding(4.dp))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            if (isNew(v, progress, settings)) { NewBadge(); Spacer(Modifier.width(6.dp)) }
                            if (v.uri.toString() in favKeys) {
                                Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFFFC107)); Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                buildString {
                                    append(formatSize(v.sizeBytes))
                                    if (v.height > 0) append(" · ${minOf(v.width, v.height)}p")
                                    if (p != null && p >= 0.95f) append(" · Watched")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!selection.active) IconButton(onClick = { menuFor = v }) { Icon(Icons.Default.MoreVert, "More") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    menuFor?.let { v ->
        VideoActionsSheet(v, v.uri.toString() in favKeys, onDismiss = { menuFor = null },
            onPlay = { menuFor = null; onPlay(list.indexOf(v).coerceAtLeast(0)) })
    }
}

@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier) {
    Icon(
        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        modifier = modifier.size(24.dp).background(Color(0x66000000), CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionsSheet(video: VideoItem, isFavorite: Boolean, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val app = JcApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingRename by remember { mutableStateOf<String?>(null) }
    val deleter = rememberVideoDeleter { onDismiss() }
    val writeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) {
        val name = pendingRename
        if (it.resultCode == android.app.Activity.RESULT_OK && name != null) scope.launch {
            val ok = app.media.rename(video, name)
            Toast.makeText(context, if (ok) "Renamed" else "Couldn't rename", Toast.LENGTH_SHORT).show()
        }
        onDismiss()
    }

    if (dialog == null) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                VideoThumbnail(video.uri, formatDuration(video.durationMs), null, Modifier.width(96.dp).aspectRatio(16f / 10f))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${formatSize(video.sizeBytes)} · ${formatDuration(video.durationMs)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SheetItem(Icons.Default.PlayArrow, "Play", onClick = onPlay)
            SheetItem(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                if (isFavorite) "Remove from favourites" else "Add to favourites",
                tint = if (isFavorite) Color(0xFFFFC107) else null) {
                scope.launch { app.database.favorites().toggle(video.uri.toString(), "video", video.title) }
                onDismiss()
            }
            SheetItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { dialog = "playlist" }
            SheetItem(Icons.Default.Share, "Share") { context.shareVideos(listOf(video.uri)); onDismiss() }
            SheetItem(Icons.Default.DriveFileRenameOutline, "Rename") { dialog = "rename" }
            SheetItem(Icons.AutoMirrored.Filled.DriveFileMove, "Move to…") { dialog = "move" }
            SheetItem(Icons.Default.ContentCopy, "Copy to…") { dialog = "copy" }
            SheetItem(Icons.Default.Delete, "Delete") { dialog = "delete" }
            SheetItem(Icons.Default.Info, "Properties") { dialog = "info" }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "playlist" -> AddToPlaylistDialog(listOf(video.uri.toString() to video.title), onDone = onDismiss)
        "move", "copy" -> MoveCopyDialog(listOf(video), move = dialog == "move", onDone = onDismiss)
        "rename" -> TextInputDialog(
            title = "Rename", initial = video.title.substringBeforeLast('.'), onDismiss = onDismiss,
            onConfirm = { name ->
                val req = app.media.writeRequest(video.uri)
                if (req != null) {
                    pendingRename = name
                    writeLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(req.intentSender).build())
                } else scope.launch {
                    val ok = app.media.rename(video, name)
                    Toast.makeText(context, if (ok) "Renamed" else "Couldn't rename", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            },
        )
        // Stay composed while Android's confirmation is open, so its result reaches the deleter.
        "delete" -> ConfirmDeleteDialog(listOf(video), onDismiss = { if (dialog == "delete") onDismiss() }) {
            dialog = "deleting"; deleter(listOf(video))
        }
        "info" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("Name", video.title)
                    InfoLine("Location", video.folderPath)
                    InfoLine("Size", formatSize(video.sizeBytes))
                    InfoLine("Duration", formatDuration(video.durationMs))
                    if (video.width > 0) InfoLine("Resolution", "${video.width} × ${video.height}")
                    video.mimeType?.let { InfoLine("Format", it) }
                    InfoLine("Added", DateFormat.getDateTimeInstance().format(Date(video.dateAddedSec * 1000)))
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    }
}

