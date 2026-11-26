package com.rafixbo.tunewave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import com.rafixbo.tunewave.TuneWaveState
import com.rafixbo.tunewave.model.Song
import com.rafixbo.tunewave.playback.PlayerManager
import com.rafixbo.tunewave.ui.components.MoodChip
import com.rafixbo.tunewave.ui.components.SongRow

private val MOODS = listOf("Lo-fi", "Bangla", "Hindi", "Pop", "Rock", "Hip-Hop", "Romantic", "Workout")

@Composable
fun HomeScreen(
    state: TuneWaveState,
    onMoodSearch: (String) -> Unit,
    onOpenPlaylist: (Song) -> Unit,
) {
    val context = LocalContext.current
    val trending by state.trending.collectAsState()
    val newReleases by state.newReleases.collectAsState()
    val topCharts by state.topCharts.collectAsState()
    val loading by state.homeLoading.collectAsState()
    val error by state.homeError.collectAsState()
    val currentSong by PlayerManager.currentSong.collectAsState()
    val liked by state.liked.collectAsState()
    val playlists by state.playlists.collectAsState()

    LaunchedEffect(Unit) {
        if (trending.isEmpty()) state.loadHome()
        state.refreshLibrary()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            // Shiny header — original TuneWave gradient, no borrowed branding.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF0891B2)),
                        ),
                    )
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            ) {
                Column {
                    Text(
                        text = "TuneWave",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 36.sp,
                    )
                    Text(
                        text = "No ads. No login. Just music.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MOODS) { mood ->
                    MoodChip(label = mood, selected = false, onClick = { onMoodSearch(mood) })
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (loading && trending.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (error != null && trending.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { state.refreshHome() }) { Text("Retry") }
                }
            }
        }

        if (newReleases.isNotEmpty()) {
            item {
                Text(
                    "New Releases",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(newReleases) { song ->
                        NewReleaseCard(
                            song = song,
                            onClick = { PlayerManager.play(context, song, newReleases) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (trending.isNotEmpty()) {
            item {
                Text(
                    "Trending Music",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(trending) { song ->
                SongRow(
                    song = song,
                    isPlaying = currentSong?.videoId == song.videoId,
                    isLiked = liked.any { it.videoId == song.videoId },
                    isDownloaded = false,
                    playlists = playlists,
                    onClick = { PlayerManager.play(context, song, trending) },
                    onLike = { state.toggleLike(song) },
                    onDownload = { state.downloadSong(song) },
                    onAddToPlaylist = { id -> state.addToPlaylist(id, song) },
                    onOpenPlaylist = onOpenPlaylist,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        if (topCharts.isNotEmpty()) {
            item {
                Text(
                    "Top Charts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(topCharts) { song ->
                SongRow(
                    song = song,
                    isPlaying = currentSong?.videoId == song.videoId,
                    isLiked = liked.any { it.videoId == song.videoId },
                    playlists = playlists,
                    onClick = { PlayerManager.play(context, song, topCharts) },
                    onLike = { state.toggleLike(song) },
                    onDownload = { state.downloadSong(song) },
                    onAddToPlaylist = { id -> state.addToPlaylist(id, song) },
                    onOpenPlaylist = onOpenPlaylist,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NewReleaseCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
            contentAlignment = Alignment.Center,
        ) {
            if (song.thumbnailUrl.isNotBlank()) {
                coil3.compose.AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// (NewReleaseCard is invoked inside LazyRow items; click handled via its onClick param.)
