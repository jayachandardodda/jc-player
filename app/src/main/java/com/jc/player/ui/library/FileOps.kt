package com.jc.player.ui.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jc.player.JcApp
import com.jc.player.data.VideoItem
import com.jc.player.ui.allFilesAccessIntent
import com.jc.player.ui.common.TextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Moving, copying and renaming files. Needs "All files access" on Android 11+. */
object FileOps {

    fun canWrite(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    data class Destination(val label: String, val path: String, val removable: Boolean = false)

    /** Existing video folders + common folders + the SD card, if there is one. */
    fun destinations(context: Context, videos: List<VideoItem>): List<Destination> {
        val out = linkedMapOf<String, Destination>()
        val primary = Environment.getExternalStorageDirectory().absolutePath
        listOf(Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DCIM).forEach {
            val p = "$primary/$it"
            out[p] = Destination(it, p)
        }
        videos.map { it.folderPath }.distinct().sorted().forEach { p ->
            if (p !in out) out[p] = Destination(File(p).name, p)
        }
        sdCardRoots(context).forEach { root ->
            val p = "$root/Movies"
            out[p] = Destination("SD card › Movies", p, removable = true)
        }
        return out.values.toList()
    }

    fun sdCardRoots(context: Context): List<String> {
        val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return sm.storageVolumes.filter { it.isRemovable }.mapNotNull { vol ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory?.absolutePath
            else context.getExternalFilesDirs(null).mapNotNull { it?.absolutePath }
                .firstOrNull { !it.startsWith(Environment.getExternalStorageDirectory().absolutePath) }
                ?.substringBefore("/Android/data")
        }
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        var n = 1
        while (f.exists()) { f = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"); n++ }
        return f
    }

    /** Moves or copies [items] into [destDir]. Returns the number of files that succeeded. */
    suspend fun transfer(
        context: Context, items: List<VideoItem>, destDir: File, move: Boolean, onProgress: (Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val changed = mutableListOf<String>()
        var ok = 0
        items.forEachIndexed { i, v ->
            val src = v.path?.let(::File)
            if (src != null && src.exists() && src.parentFile?.absolutePath != destDir.absolutePath) {
                val dst = uniqueTarget(destDir, src.name)
                val done = runCatching {
                    if (move && src.renameTo(dst)) true
                    else {
                        src.inputStream().use { input -> dst.outputStream().use { input.copyTo(it, 1 shl 20) } }
                        if (move) src.delete()
                        true
                    }
                }.getOrElse { dst.delete(); false }
                if (done) { ok++; changed += dst.absolutePath; if (move) changed += src.absolutePath }
            }
            withContext(Dispatchers.Main) { onProgress(i + 1) }
        }
        scan(context, changed)
        ok
    }

    suspend fun renameFolder(context: Context, path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(path)
        val dst = File(src.parentFile, newName)
        if (dst.exists()) return@withContext false
        val oldFiles = src.listFiles()?.map { it.absolutePath }.orEmpty()
        if (!src.renameTo(dst)) return@withContext false
        val newFiles = dst.listFiles()?.map { it.absolutePath }.orEmpty()
        scan(context, oldFiles + newFiles)
        true
    }

    /** Tells MediaStore about added files and forgets removed ones. */
    private suspend fun scan(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            var remaining = paths.size
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                synchronized(this) { remaining--; if (remaining == 0 && cont.isActive) cont.resumeWith(Result.success(Unit)) }
            }
        }
        JcApp.instance.media.refresh()
    }
}

/** Shown when file access is missing; sends the user to the system setting. */
@Composable
fun NeedFileAccessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow file access") },
        text = { Text("To move, copy or rename files, JC Player needs \"All files access\". Turn it on, then come back and try again.") },
        confirmButton = { TextButton(onClick = { context.startActivity(allFilesAccessIntent(context)); onDismiss() }) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick a folder, then move or copy [items] there with a progress indicator. */
@Composable
fun MoveCopyDialog(items: List<VideoItem>, move: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videos by JcApp.instance.media.videos.collectAsState()
    if (!FileOps.canWrite(context)) { NeedFileAccessDialog(onDone); return }

    val destinations = remember(videos) { FileOps.destinations(context, videos) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var newFolder by remember { mutableStateOf(false) }

    fun start(dir: File) {
        running = true
        scope.launch {
            val ok = FileOps.transfer(context, items, dir, move) { progress = it }
            val verb = if (move) "Moved" else "Copied"
            Toast.makeText(context, if (ok == items.size) "$verb $ok video${if (ok == 1) "" else "s"}" else "$verb $ok of ${items.size}", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    if (newFolder) {
        TextInputDialog("New folder in Movies", label = "Folder name", confirm = if (move) "Move here" else "Copy here",
            onDismiss = { newFolder = false }) { name ->
            newFolder = false
            start(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), name))
        }
        return
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDone() },
        title = { Text(if (running) (if (move) "Moving…" else "Copying…") else "${if (move) "Move" else "Copy"} ${items.size} video${if (items.size == 1) "" else "s"} to") },
        text = {
            if (running) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("  $progress of ${items.size}")
            } else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { newFolder = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
                        Text("New folder…", Modifier.padding(start = 16.dp))
                    }
                }
                items(destinations, key = { it.path }) { d ->
                    Row(Modifier.fillMaxWidth().clickable { start(File(d.path)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (d.removable) Icons.Default.SdCard else Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(d.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(d.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { if (!running) TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@Composable
fun RenameFolderDialog(path: String, currentName: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!FileOps.canWrite(context)) { NeedFileAccessDialog(onDone); return }
    TextInputDialog("Rename folder", initial = currentName, onDismiss = onDone) { name ->
        scope.launch {
            val ok = FileOps.renameFolder(context, path, name)
            Toast.makeText(context, if (ok) "Folder renamed" else "Couldn't rename (name taken or no access)", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
}

