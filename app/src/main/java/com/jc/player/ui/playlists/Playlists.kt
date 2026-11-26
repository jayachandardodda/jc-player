package com.jc.player.ui.playlists

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jc.player.JcApp
import com.jc.player.data.QueueItem
import com.jc.player.data.db.PlaylistWithCount
import com.jc.player.ui.common.EmptyState
import com.jc.player.ui.common.TextInputDialog
import com.jc.player.ui.common.VideoThumbnail
import com.jc.player.ui.common.playQueue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(onOpen: (Long) -> Unit) {
    val dao = JcApp.instance.database.playlists()
    val scope = rememberCoroutineScope()
    val playlists by dao.observePlaylists().collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var renaming by remember { mutableStateOf<PlaylistWithCount?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Playlists") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "New playlist") }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (playlists.isEmpty()) {
                EmptyState(
                    Icons.AutoMirrored.Filled.PlaylistPlay, "No playlists yet",
                    "Tap + to create one, or long-press any video and choose \"Add to playlist\".",
                )
            } else LazyColumn {
                items(playlists, key = { it.id }) { p ->
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { onOpen(p.id) }, onLongClick = { menuFor = p })
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary) }
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("${p.count} video${if (p.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(expanded = menuFor == p, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick = { renaming = p; menuFor = null })
                            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { scope.launch { dao.deletePlaylist(p.id) }; menuFor = null })
                        }
                    }
                }
            }
        }
    }

    if (creating) TextInputDialog("New playlist", label = "Name", confirm = "Create", onDismiss = { creating = false }) { name ->
        scope.launch { dao.insertPlaylist(com.jc.player.data.db.PlaylistEntity(name = name)) }
        creating = false
    }
    renaming?.let { p ->
        TextInputDialog("Rename playlist", initial = p.name, onDismiss = { renaming = null }) { name ->
            scope.launch { dao.rename(p.id, name) }
            renaming = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(id: Long, onBack: () -> Unit) {
    val app = JcApp.instance
    val dao = app.database.playlists()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by dao.observeItems(id).collectAsState(initial = emptyList())
    val playlists by dao.observePlaylists().collectAsState(initial = emptyList())
    val name = playlists.firstOrNull { it.id == id }?.name ?: "Playlist"
    val queue = remember(items) { items.map { QueueItem(Uri.parse(it.uri), it.title, app.media.pathFor(Uri.parse(it.uri))) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { context.playQueue(queue.shuffled(), 0) }) { Icon(Icons.Default.Shuffle, "Shuffle") }
                    IconButton(onClick = { context.playQueue(queue, 0) }) { Icon(Icons.Default.PlayArrow, "Play all") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (items.isEmpty()) EmptyState(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlist is empty",
                "Long-press any video and choose \"Add to playlist\".")
            else LazyColumn {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { context.playQueue(queue, index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoThumbnail(Uri.parse(item.uri), null, null, Modifier.width(112.dp).aspectRatio(16f / 10f))
                        Text(item.title, Modifier.padding(start = 12.dp).weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { scope.launch { dao.deleteItem(item.id) } }) {
                            Icon(Icons.Default.RemoveCircleOutline, "Remove")
                        }
                    }
                }
            }
        }
    }
}

/** Pick an existing playlist or make a new one, then add [entries] (uri, title) to it. */
@Composable
fun AddToPlaylistDialog(entries: List<Pair<String, String>>, onDone: () -> Unit) {
    val dao = JcApp.instance.database.playlists()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by dao.observePlaylists().collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }

    fun addTo(id: Long, name: String) = scope.launch {
        dao.addToPlaylist(id, entries)
        Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
        onDone()
    }

    if (creating) {
        TextInputDialog("New playlist", label = "Name", confirm = "Create", onDismiss = onDone) { name ->
            scope.launch {
                val id = dao.insertPlaylist(com.jc.player.data.db.PlaylistEntity(name = name))
                addTo(id, name)
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, null)
                        Text("New playlist", Modifier.padding(start = 16.dp))
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { addTo(p.id, p.name) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
                        Text(p.name, Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}
