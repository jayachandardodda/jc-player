package com.jc.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jc.player.JcApp
import com.jc.player.ui.common.EmptyState
import com.jc.player.ui.library.CleanerScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jc.player.ui.library.FoldersScreen
import com.jc.player.ui.library.HistoryScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.jc.player.ui.library.SearchScreen
import com.jc.player.ui.library.VideosScreen
import com.jc.player.ui.network.NetworkScreen
import com.jc.player.ui.network.SmbBrowseScreen
import com.jc.player.ui.playlists.PlaylistDetailScreen
import com.jc.player.ui.playlists.PlaylistsScreen
import com.jc.player.ui.settings.SettingsScreen
import com.jc.player.ui.theme.JcTheme
import com.jc.player.ui.vault.VaultScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as JcApp
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            JcTheme(settings.theme, settings.accent) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PermissionGate { AppRoot() }
                }
            }
        }
    }
}

val videoPermission: String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAllFilesAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

fun allFilesAccessIntent(context: android.content.Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED ||
        hasAllFilesAccess(context)

    var ok by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = granted()
    }
    // Re-check when returning from system settings.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ok = granted() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val perms = buildList {
        add(videoPermission)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()
    LaunchedEffect(Unit) { if (!ok) launcher.launch(perms) }

    if (ok) {
        LaunchedEffect(Unit) {
            JcApp.instance.media.start()
        }
        content()
    } else {
        EmptyState(
            icon = Icons.Default.VideoLibrary,
            title = "Allow access to your videos",
            subtitle = "JC Player needs permission to find the videos on your phone. Nothing leaves your device.",
        ) {
            Button(onClick = { launcher.launch(perms) }) { Text("Allow access") }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("folders", "Videos", Icons.Default.Folder),
    Tab("playlists", "Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    Tab("network", "Network", Icons.Default.Lan),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val bottom = androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding())
        Box(Modifier.padding(bottom).consumeWindowInsets(bottom)) {
            NavHost(
                nav, startDestination = "folders",
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 6 } },
            ) {
                composable("folders") {
                    FoldersScreen(
                        onOpenFolder = { nav.navigate("folder?path=${Uri.encode(it)}") },
                        onSearch = { nav.navigate("search") },
                        onVault = { nav.navigate("vault") },
                        onHistory = { nav.navigate("history") },
                        onCleaner = { nav.navigate("cleaner") },
                    )
                }
                composable("history") { HistoryScreen(onBack = { nav.popBackStack() }) }
                composable("cleaner") { CleanerScreen(onBack = { nav.popBackStack() }) }
                composable("playerButtons") { com.jc.player.ui.settings.PlayerButtonsScreen(onBack = { nav.popBackStack() }) }
                composable(
                    "folder?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    VideosScreen(
                        folderPath = entry.arguments?.getString("path").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("search") { SearchScreen(onBack = { nav.popBackStack() }) }
                composable("vault") {
                    VaultScreen(
                        onBack = { nav.popBackStack() },
                        onOpenFolder = { nav.navigate("folder?path=${Uri.encode(it)}") },
                    )
                }
                composable("playlists") { PlaylistsScreen(onOpen = { nav.navigate("playlist/$it") }) }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    PlaylistDetailScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                }
                composable("network") { NetworkScreen(onBrowse = { nav.navigate("smb/$it") }) }
                composable(
                    "smb/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    SmbBrowseScreen(entry.arguments!!.getString("id")!!, onBack = { nav.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(
                        onPlayerButtons = { nav.navigate("playerButtons") },
                        onHistory = { nav.navigate("history") },
                        onCleaner = { nav.navigate("cleaner") },
                    )
                }
            }
        }
    }
}
