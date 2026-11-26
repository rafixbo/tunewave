package com.rafixbo.tunewave

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.rafixbo.tunewave.playback.PlayerManager
import com.rafixbo.tunewave.ui.screens.DeveloperScreen
import com.rafixbo.tunewave.ui.screens.HomeScreen
import com.rafixbo.tunewave.ui.screens.LibraryScreen
import com.rafixbo.tunewave.ui.screens.PlayerScreen
import com.rafixbo.tunewave.ui.screens.SearchScreen
import com.rafixbo.tunewave.ui.screens.SettingsScreen
import com.rafixbo.tunewave.ui.theme.TuneWaveTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — playback works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PlayerManager.connect(this)
        val app = application as TuneWaveApp
        val state = app.state
        setContent {
            TuneWaveTheme(darkTheme = true) {
                TuneWaveNav(state)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT release the controller on destroy — the service keeps
        // playing in the background; the controller is released on
        // process death with the service.
    }
}

private sealed class Tab(val route: String, val label: String) {
    data object Home : Tab("home", "Home")
    data object Search : Tab("search", "Search")
    data object Library : Tab("library", "Library")
    data object Settings : Tab("settings", "Settings")
}

@Composable
private fun TuneWaveNav(state: TuneWaveState) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val tabs = listOf(Tab.Home, Tab.Search, Tab.Library, Tab.Settings)
    val isMainTab = tabs.any { it.route == currentDestination?.route }
    val currentSong by PlayerManager.currentSong.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                // Mini player above the nav bar (hidden on player screen).
                if (currentSong != null && currentDestination?.route != "player") {
                    MiniPlayer(onClick = { navController.navigate("player") })
                }
                if (isMainTab) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        when (tab) {
                                            Tab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                                            Tab.Search -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                                            Tab.Library -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
                                            Tab.Settings -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
                                        },
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = "home",
                enterTransition = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.97f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() + scaleOut(targetScale = 0.97f) },
            ) {
                composable("home") {
                    HomeScreen(
                        state = state,
                        onMoodSearch = { mood ->
                            state.setSearchQuery(mood)
                            navController.navigate("search")
                        },
                        onOpenPlaylist = { playlistSong ->
                            state.playPlaylist(playlistSong, navController.context)
                        },
                    )
                }
                composable("search") {
                    SearchScreen(
                        state = state,
                        onOpenPlaylist = { playlistSong ->
                            state.playPlaylist(playlistSong, navController.context)
                        },
                    )
                }
                composable("library") {
                    LibraryScreen(state = state, onOpenPlaylist = { playlistSong ->
                        state.playPlaylist(playlistSong, navController.context)
                    })
                }
                composable("settings") {
                    SettingsScreen(state = state, onOpenDeveloper = { navController.navigate("developer") })
                }
                composable("player") {
                    PlayerScreen(state = state, onBack = { navController.popBackStack() })
                }
                composable("developer") {
                    DeveloperScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(onClick: () -> Unit) {
    val song by PlayerManager.currentSong.collectAsState()
    val isPlaying by PlayerManager.isPlaying.collectAsState()
    val current = song ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF8B5CF6)),
            contentAlignment = Alignment.Center,
        ) {
            if (current.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = current.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                current.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                current.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.IconButton(onClick = { PlayerManager.togglePlayPause() }) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
            )
        }
    }
}
