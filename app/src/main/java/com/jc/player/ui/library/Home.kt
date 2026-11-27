package com.jc.player.ui.library

import android.widget.Toast
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jc.player.JcApp
import com.jc.player.data.VideoFolder
import com.jc.player.data.VideoItem
import com.jc.player.data.db.PlaybackStateEntity
import com.jc.player.data.formatDuration
import com.jc.player.data.formatSize
import com.jc.player.ui.common.EmptyState
import com.jc.player.ui.common.VideoThumbnail
import com.jc.player.ui.common.playQueue
import kotlinx.coroutines.launch

private val Gold = Color(0xFFFFC107)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    onOpenFolder: (String) -> Unit,
    onSearch: () -> Unit,
    onVault: () -> Unit,
    onHistory: () -> Unit,
    onCleaner: () -> Unit,
) {
    val app = JcApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videos by app.media.videos.collectAsState()
    val loading by app.media.loading.collectAsState()
    val settings by app.settings.state.collectAsState()
    val states = rememberPlaybackStates()
    val favorites = rememberFavorites()
    val progress = remember(states) { states.associate { it.uri to if (it.durationMs > 0) it.positionMs.toFloat() / it.durationMs else 0f } }
    var showSort by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<VideoFolder?>(null) }
    var renaming by remember { mutableStateOf<VideoFolder?>(null) }

    val visible = remember(videos, settings) { videos.filter { isFolderVisible(it.folderPath, settings) } }
    val byUri = remember(visible) { visible.associateBy { it.uri.toString() } }
    val favFolderKeys = remember(favorites) { favorites.filter { it.type == "folder" }.map { it.key }.toSet() }
    val folders = remember(visible, settings, favFolderKeys) {
        val grouped = visible.groupBy { it.folderPath }.map { (path, list) -> VideoFolder(path, list.first().folderName, list) }
        val sorted = sortFolders(grouped, settings)
        sorted.filter { it.path in favFolderKeys } + sorted.filter { it.path !in favFolderKeys }
    }
    // Local history: everything played, most recent first (only files that still exist and are visible).
    val history = remember(states, byUri) {
        states.sortedByDescending { it.lastPlayed }
            .mapNotNull { s -> byUri[s.uri]?.let { it to s } }
            .take(20)
    }
    // The folder holding the most recently played video gets its name in the accent colour.
    val lastPlayed = history.firstOrNull()?.first
    val lastPlayedFolder = lastPlayed?.folderPath
    val favoriteVideos = remember(favorites, byUri) { favorites.filter { it.type == "video" }.mapNotNull { byUri[it.key] } }

    fun play(v: VideoItem) { val (q, i) = folderQueueFor(v, videos, settings); context.playQueue(q, i) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(30.dp).background(
                                Brush.linearGradient(listOf(Color(0xFF3A6FF7), Color(0xFF8A4FFF))), RoundedCornerShape(9.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Text("JC Player", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Watch history") }, leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = { menu = false; onHistory() })
                        DropdownMenuItem(text = { Text("Clean up storage") }, leadingIcon = { Icon(Icons.Default.CleaningServices, null) },
                            onClick = { menu = false; onCleaner() })
                        DropdownMenuItem(text = { Text("Private folder") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = { menu = false; onVault() })
                        DropdownMenuItem(text = { Text("Refresh") }, leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menu = false; scope.launch { app.media.refresh() } })
                    }
                },
            )
        },
        floatingActionButton = {
            // Resume button: picks up the last played video where it was left (like MX Player).
            if (lastPlayed != null) FloatingActionButton(
                onClick = { play(lastPlayed) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.size(64.dp),
            ) { Icon(Icons.Default.PlayArrow, "Resume ${lastPlayed.title}", Modifier.size(36.dp)) }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { scope.launch { app.media.refresh() } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (folders.isEmpty()) {
                if (!loading) EmptyState(Icons.Default.VideoLibrary, "No videos found", "Videos you save or download will show up here.")
            } else LazyColumn(Modifier.fillMaxSize()) {
                if (history.isNotEmpty()) {
                    item(key = "history") {
                        SectionHeader("Local history", "See all", onHistory)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(history, key = { it.second.uri }) { (v, s) ->
                                HistoryCard(
                                    v, s,
                                    modifier = Modifier.animateItem(),
                                    onClick = { play(v) },
                                    onRemove = { scope.launch { app.database.playbackState().delete(s.uri) } },
                                )
                            }
                        }
                    }
                }
                if (favoriteVideos.isNotEmpty()) {
                    item(key = "fav") {
                        SectionHeader("Favourites")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(favoriteVideos, key = { it.id }) { v -> SmallCard(v, progress[v.uri.toString()], false) { play(v) } }
                        }
                    }
                }
                item(key = "foldersHeader") { SectionHeader("Folders · ${folders.size}") }
                items(folders, key = { it.path }) { folder ->
                    val newCount = folder.videos.count { isNew(it, progress, settings) }
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onOpenFolder(folder.path) }, onLongClick = { folderMenu = folder })
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            com.jc.player.ui.common.FolderIcon(Modifier.size(width = 76.dp, height = 56.dp))
                            if (newCount > 0) CountBadge(newCount, Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp))
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (folder.path == lastPlayedFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false))
                                if (folder.path in favFolderKeys) Icon(Icons.Default.Star, null, Modifier.padding(start = 6.dp).size(16.dp), tint = Gold)
                            }
                            Text("${folder.count} video${if (folder.count == 1) "" else "s"} · ${formatSize(folder.totalSize)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Leave room so the resume button never covers the last folder.
                item { Spacer(Modifier.height(if (lastPlayed != null) 96.dp else 24.dp)) }
            }
        }
    }

    if (showSort) SortDialog(settings) { showSort = false }
    renaming?.let { f -> RenameFolderDialog(f.path, java.io.File(f.path).name) { renaming = null } }
    folderMenu?.let { folder ->
        val isFav = folder.path in favFolderKeys
        ModalBottomSheet(onDismissRequest = { folderMenu = null }) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Text(folder.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp))
            SheetItem(Icons.Default.PlayArrow, "Play all") {
                folderMenu = null; context.playQueue(sortVideos(folder.videos, settings).toQueue(), 0)
            }
            SheetItem(if (isFav) Icons.Default.Star else Icons.Default.StarBorder, if (isFav) "Unpin from top" else "Pin to top (favourite)",
                tint = if (isFav) Gold else null) {
                folderMenu = null; scope.launch { app.database.favorites().toggle(folder.path, "folder", folder.name) }
            }
            SheetItem(Icons.Default.DriveFileRenameOutline, "Rename folder") { folderMenu = null; renaming = folder }
            SheetItem(Icons.Default.VisibilityOff, "Hide folder") {
                folderMenu = null
                app.settings.update { it.copy(hiddenFolders = it.hiddenFolders + folder.path) }
                Toast.makeText(context, "Folder hidden. Unhide it in Settings.", Toast.LENGTH_SHORT).show()
            }
            SheetItem(Icons.Default.Lock, "Move to Private folder") {
                folderMenu = null
                if (settings.pinHash == null) {
                    Toast.makeText(context, "Set a PIN in the Private folder first", Toast.LENGTH_SHORT).show(); onVault()
                } else {
                    app.settings.update { it.copy(privateFolders = it.privateFolders + folder.path) }
                    Toast.makeText(context, "Moved to Private folder", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A Local-history tile: thumbnail with how far you got, plus a menu to remove just this entry. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(v: VideoItem, s: PlaybackStateEntity, modifier: Modifier = Modifier, onClick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val duration = if (s.durationMs > 0) s.durationMs else v.durationMs
    val progress = if (duration > 0) s.positionMs.toFloat() / duration else 0f
    Column(modifier.width(172.dp).combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
        Box {
            VideoThumbnail(v.uri, formatDuration(duration), progress, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(0x73000000)).combinedClickable(onClick = { menu = true }),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.MoreVert, "Options", tint = Color.White, modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = { menu = false; onClick() })
                    DropdownMenuItem(text = { Text("Remove from history") }, leadingIcon = { Icon(Icons.Default.Close, null) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
        Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp))
    }
}

/** Red unwatched-count badge that sits on the folder icon's corner (capped at 99+). */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Box(
        modifier
            .shadow(3.dp, pill)
            .background(MaterialTheme.colorScheme.background, pill) // ring that separates it from the thumbnail
            .padding(2.dp)
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFFFF5470), Color(0xFFE0243F))), pill)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SmallCard(v: VideoItem, progress: Float?, isNew: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(160.dp).combinedClickableCompat(onClick)) {
        Box {
            VideoThumbnail(v.uri, formatDuration(v.durationMs), progress, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
            if (isNew) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { NewBadge() }
        }
        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)
