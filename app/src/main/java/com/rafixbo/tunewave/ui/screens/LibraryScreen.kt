package com.rafixbo.tunewave.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rafixbo.tunewave.TuneWaveState
import com.rafixbo.tunewave.model.Playlist
import com.rafixbo.tunewave.model.Song
import com.rafixbo.tunewave.playback.PlayerManager
import com.rafixbo.tunewave.ui.components.SongRow

@Composable
fun LibraryScreen(state: TuneWaveState, onOpenPlaylist: (Song) -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Liked", "History", "Playlists", "Downloads")
    val liked by state.liked.collectAsState()
    val history by state.history.collectAsState()
    val playlists by state.playlists.collectAsState()
    val downloads by state.downloads.collectAsState()
    val currentSong by PlayerManager.currentSong.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var expandedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(Unit) { state.refreshLibrary() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> SongList(
                songs = liked,
                emptyText = "No liked songs yet — tap the heart on any song.",
                currentSong = currentSong,
                state = state,
                onOpenPlaylist = onOpenPlaylist,
            )
            1 -> Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { state.clearHistory() }) { Text("Clear history") }
                }
                SongList(
                    songs = history,
                    emptyText = "Nothing played yet.",
                    currentSong = currentSong,
                    state = state,
                    onOpenPlaylist = onOpenPlaylist,
                )
            }
            2 -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    Button(
                        onClick = { showCreate = true },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("New playlist")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(playlists) { pl ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable { expandedPlaylist = pl },
                        ) {
                            Text(pl.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text("${pl.songs.size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            if (pl.songs.isNotEmpty()) PlayerManager.play(context, pl.songs[0], pl.songs)
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist")
                        }
                        IconButton(onClick = { state.deletePlaylist(pl.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete playlist")
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No playlists yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                expandedPlaylist?.let { pl ->
                    items(pl.songs) { song ->
                        SongRow(
                            song = song,
                            isPlaying = currentSong?.videoId == song.videoId,
                            onClick = { PlayerManager.play(context, song, pl.songs) },
                            onLike = { state.toggleLike(song) },
                            onDownload = { state.downloadSong(song) },
                            onAddToPlaylist = { id -> state.addToPlaylist(id, song) },
                            onOpenPlaylist = onOpenPlaylist,
                        )
                    }
                }
            }
            3 -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(downloads) { song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SongRow(
                                song = song,
                                isPlaying = currentSong?.videoId == song.videoId,
                                isDownloaded = true,
                                onClick = { PlayerManager.play(context, song, downloads) },
                                onLike = { state.toggleLike(song) },
                                onDownload = {},
                                onAddToPlaylist = { id -> state.addToPlaylist(id, song) },
                                onOpenPlaylist = onOpenPlaylist,
                            )
                        }
                        IconButton(onClick = { state.deleteDownload(song) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                        }
                    }
                }
                if (downloads.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No downloads yet. Tap the download icon on any song to save it offline.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    state.createPlaylist(newName)
                    newName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    emptyText: String,
    currentSong: Song?,
    state: TuneWaveState,
    onOpenPlaylist: (Song) -> Unit,
) {
    val context = LocalContext.current
    val playlists by state.playlists.collectAsState()
    val liked by state.liked.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(songs) { song ->
            SongRow(
                song = song,
                isPlaying = currentSong?.videoId == song.videoId,
                isLiked = liked.any { it.videoId == song.videoId },
                playlists = playlists,
                onClick = { PlayerManager.play(context, song, songs) },
                onLike = { state.toggleLike(song) },
                onDownload = { state.downloadSong(song) },
                onAddToPlaylist = { id -> state.addToPlaylist(id, song) },
                onOpenPlaylist = onOpenPlaylist,
            )
        }
        if (songs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
