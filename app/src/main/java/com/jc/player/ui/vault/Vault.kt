package com.jc.player.ui.vault

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jc.player.JcApp
import com.jc.player.data.formatSize
import com.jc.player.ui.common.EmptyState
import java.io.File

/** Folders moved here are hidden from the main list and only shown after entering the PIN. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit) {
    val app = JcApp.instance
    val settings by app.settings.state.collectAsState()
    val videos by app.media.videos.collectAsState()
    var unlocked by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private folder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                settings.pinHash == null -> PinSetup { pin -> app.settings.setPin(pin); unlocked = true }
                !unlocked -> PinEntry { pin -> app.settings.checkPin(pin).also { if (it) unlocked = true } }
                settings.privateFolders.isEmpty() -> EmptyState(
                    Icons.Default.Lock, "No private folders",
                    "Long-press a folder in Videos and choose \"Move to Private folder\".",
                )
                else -> LazyColumn {
                    items(settings.privateFolders.sorted()) { path ->
                        val inFolder = videos.filter { it.folderPath == path }
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenFolder(path) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) }
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(inFolder.firstOrNull()?.folderName ?: File(path).name, style = MaterialTheme.typography.titleMedium)
                                Text("${inFolder.size} videos · ${formatSize(inFolder.sumOf { it.sizeBytes })}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                app.settings.update { it.copy(privateFolders = it.privateFolders - path) }
                            }) { Icon(Icons.Default.LockOpen, "Remove from private") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinSetup(onSet: (String) -> Unit) {
    val context = LocalContext.current
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    PinLayout("Create a PIN", "Use 4 to 8 digits. You'll need it to open private folders.") {
        PinField(first, "PIN") { first = it }
        PinField(second, "Confirm PIN") { second = it }
        Button(onClick = {
            when {
                first.length < 4 -> Toast.makeText(context, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                first != second -> Toast.makeText(context, "PINs don't match", Toast.LENGTH_SHORT).show()
                else -> onSet(first)
            }
        }) { Text("Set PIN") }
    }
}

@Composable
private fun PinEntry(onCheck: (String) -> Boolean) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    PinLayout("Enter PIN", null) {
        PinField(pin, "PIN") {
            pin = it
            if (it.length >= 4 && onCheck(it)) pin = ""
        }
        Button(onClick = {
            if (!onCheck(pin)) Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
        }) { Text("Unlock") }
    }
}

@Composable
private fun PinLayout(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 8 && v.all(Char::isDigit)) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}
