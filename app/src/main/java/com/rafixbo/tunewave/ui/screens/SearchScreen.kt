package com.rafixbo.tunewave.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.rafixbo.tunewave.model.Song
import com.rafixbo.tunewave.playback.PlayerManager
import com.rafixbo.tunewave.ui.components.MoodChip
import com.rafixbo.tunewave.ui.components.SongRow

@Composable
fun SearchScreen(
    state: TuneWaveState,
    onOpenPlaylist: (Song) -> Unit,
) {
    val context = LocalContext.current
    val query by state.searchQuery.collectAsState()
    val results by state.searchResults.collectAsState()
    val loading by state.searchLoading.collectAsState()
    val searchError by state.searchError.collectAsState()
    val history by state.searchHistory.collectAsState()
    val currentSong by PlayerManager.currentSong.collectAsState()
    val liked by state.liked.collectAsState()
    val playlists by state.playlists.collectAsState()
    var filter by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) { state.refreshSearchHistory() }

    val filtered = when (filter) {
        "Songs" -> results.filter { it.kind == "SONG" }
        "Playlists" -> results.filter { it.isPlaylist }
        "Artists" -> results.filter { it.isArtist }
        else -> results
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { state.setSearchQuery(it) },
            placeholder = { Text("Search songs, artists, albums…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { state.setSearchQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) {
            if (history.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent searches", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Clear",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { state.clearSearchHistory() },
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { h ->
                        MoodChip(label = h, selected = false, onClick = { state.setSearchQuery(h) })
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Search YouTube music — no login needed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Songs", "Playlists", "Artists").forEach { f ->
                    MoodChip(label = f, selected = filter == f, onClick = { filter = f })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (!loading && searchError != null) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        searchError ?: "",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { state.retrySearch() }, shape = RoundedCornerShape(20.dp)) {
                        Text("Retry")
                    }
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(filtered) { song ->
                    SongRow(
                        song = song,
                        isPlaying = currentSong?.videoId == song.videoId,
                        isLiked = liked.any { it.videoId == song.videoId },
                        playlists = playlists,
                        onClick = { PlayerManager.play(context, song, filtered.filter { it.kind == "SONG" }.ifEmpty { listOf(song) }) },
                        onLike = { state.toggleLike(song) },
                        onDownload = { state.downloadSong(song) },
                        onAddToPlaylist = { id -> state.addToPlaylist(id, song) },
                        onOpenPlaylist = onOpenPlaylist,
                    )
                }
                if (!loading && filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
