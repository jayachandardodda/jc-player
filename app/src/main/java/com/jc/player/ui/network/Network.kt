package com.jc.player.ui.network

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.jc.player.JcApp
import com.jc.player.data.QueueItem
import com.jc.player.data.SmbServer
import com.jc.player.data.formatSize
import com.jc.player.player.SmbBrowser
import com.jc.player.player.SmbEntry
import com.jc.player.player.Subtitles
import com.jc.player.ui.common.EmptyState
import com.jc.player.ui.common.playQueue
import com.jc.player.ui.library.NaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private val VIDEO_EXT = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts", "mts", "mpg", "mpeg", "vob", "ogv", "rmvb", "divx",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBrowse: (String) -> Unit) {
    val app = JcApp.instance
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    var url by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<SmbServer?>(null) }

    fun play(u: String) {
        val clean = u.trim()
        if (clean.isEmpty()) return
        app.settings.addUrlToHistory(clean)
        context.playQueue(listOf(QueueItem(Uri.parse(clean), Uri.parse(clean).lastPathSegment ?: clean)), 0)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Network") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Text("Play a stream", style = MaterialTheme.typography.titleMedium)
                Text("http(s), HLS (.m3u8), DASH (.mpd), RTSP or smb:// links",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        placeholder = { Text("https://example.com/video.m3u8") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { play(url) }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { play(url) }) { Icon(Icons.Default.PlayArrow, "Play") }
                }
            }
            if (settings.urlHistory.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                        Text("Recent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { app.settings.update { it.copy(urlHistory = emptyList()) } }) { Text("Clear") }
                    }
                }
                items(settings.urlHistory) { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable { play(u) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(u, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        IconButton(onClick = { app.settings.update { s -> s.copy(urlHistory = s.urlHistory - u) } }) {
                            Icon(Icons.Default.Close, "Remove")
                        }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Network shares (SMB)", style = MaterialTheme.typography.titleMedium)
                        Text("Windows shared folders, NAS drives, routers with USB storage",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        editing = SmbServer(UUID.randomUUID().toString(), "", "", "", "", "", "")
                    }) { Icon(Icons.Default.Add, "Add share") }
                }
            }
            if (settings.smbServers.isEmpty()) item {
                Text("No shares added yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            items(settings.smbServers, key = { it.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onBrowse(s.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(s.name.ifBlank { s.host })
                        Text("\\\\${s.host}\\${s.share}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, "Edit") }
                }
            }
        }
    }

    editing?.let { server ->
        SmbServerDialog(
            initial = server,
            isNew = settings.smbServers.none { it.id == server.id },
            onDismiss = { editing = null },
            onDelete = {
                app.settings.update { s -> s.copy(smbServers = s.smbServers.filter { it.id != server.id }) }
                editing = null
            },
            onSave = { updated ->
                app.settings.update { s ->
                    s.copy(smbServers = s.smbServers.filter { it.id != updated.id } + updated)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun SmbServerDialog(
    initial: SmbServer, isNew: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (SmbServer) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var share by remember { mutableStateOf(initial.share) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.password) }
    var domain by remember { mutableStateOf(initial.domain) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add network share" else "Edit network share") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Server IP or name") }, placeholder = { Text("192.168.1.10") }, singleLine = true)
                OutlinedTextField(share, { share = it }, label = { Text("Share name") }, placeholder = { Text("Movies") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("Username (blank = guest)") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(domain, { domain = it }, label = { Text("Domain (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (host.isNotBlank() && share.isNotBlank()) onSave(
                    initial.copy(name = name.trim(), host = host.trim(), share = share.trim().trim('/', '\\'),
                        user = user.trim(), password = pass, domain = domain.trim())
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbBrowseScreen(serverId: String, onBack: () -> Unit) {
    val app = JcApp.instance
    val context = LocalContext.current
    val server = app.settings.current.smbServers.firstOrNull { it.id == serverId }
    var path by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(path, reload) {
        if (server == null) return@LaunchedEffect
        loading = true; error = null
        val result = withContext(Dispatchers.IO) { runCatching { SmbBrowser.list(server, path) } }
        result.onSuccess { entries = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }
    BackHandler(enabled = path.isNotEmpty()) { path = path.substringBeforeLast('/', "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(server?.name?.ifBlank { server.host } ?: "Share")
                        Text("/${server?.share ?: ""}/$path".trimEnd('/'), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (path.isEmpty()) onBack() else path = path.substringBeforeLast('/', "") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                server == null -> EmptyState(Icons.Default.Computer, "Share not found")
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> EmptyState(Icons.Default.Computer, "Couldn't connect", error) {
                    Button(onClick = { reload++ }) { Text("Retry") }
                }
                entries.isEmpty() -> EmptyState(Icons.Default.Folder, "Empty folder")
                else -> {
                    val videos = entries.filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in VIDEO_EXT }
                        .sortedWith(compareBy(NaturalOrder) { it.name })
                    LazyColumn {
                        items(entries, key = { it.name }) { e ->
                            val isVideo = e in videos
                            val isSub = Subtitles.isSubtitleName(e.name)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = e.isDirectory || isVideo) {
                                        if (e.isDirectory) path = if (path.isEmpty()) e.name else "$path/${e.name}"
                                        else {
                                            fun full(n: String) = if (path.isEmpty()) n else "$path/$n"
                                            val subs = entries.filter { Subtitles.isSubtitleName(it.name) }
                                            val queue = videos.map { v ->
                                                val base = v.name.substringBeforeLast('.').lowercase()
                                                QueueItem(
                                                    SmbBrowser.uriFor(server, full(v.name)), v.name,
                                                    subtitleUris = subs.filter { it.name.lowercase().startsWith(base) }
                                                        .map { SmbBrowser.uriFor(server, full(it.name)) },
                                                )
                                            }
                                            context.playQueue(queue, videos.indexOf(e))
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    when { e.isDirectory -> Icons.Default.Folder; isVideo -> Icons.Default.Movie; else -> Icons.AutoMirrored.Filled.InsertDriveFile },
                                    null,
                                    tint = if (e.isDirectory || isVideo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                    Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = if (e.isDirectory || isVideo || isSub) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!e.isDirectory) Text(formatSize(e.size), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
