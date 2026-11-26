package com.rafixbo.tunewave

import com.rafixbo.tunewave.crash.CrashHandler
import com.rafixbo.tunewave.data.LocalStore
import com.rafixbo.tunewave.data.LyricsRepository
import com.rafixbo.tunewave.data.YouTubeRepository
import com.rafixbo.tunewave.download.TuneDownloader
import com.rafixbo.tunewave.model.AudioQuality
import com.rafixbo.tunewave.model.Playlist
import com.rafixbo.tunewave.model.Song
import com.rafixbo.tunewave.playback.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared UI state for all screens. Created once in TuneWaveApp. */
class TuneWaveState(
    val repository: YouTubeRepository,
    val localStore: LocalStore,
    val downloader: TuneDownloader,
    val lyricsRepository: LyricsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + CrashHandler.coroutineHandler)

    // Home
    private val _homeLoading = MutableStateFlow(true)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()
    private val _trending = MutableStateFlow<List<Song>>(emptyList())
    val trending: StateFlow<List<Song>> = _trending.asStateFlow()
    private val _newReleases = MutableStateFlow<List<Song>>(emptyList())
    val newReleases: StateFlow<List<Song>> = _newReleases.asStateFlow()
    private val _topCharts = MutableStateFlow<List<Song>>(emptyList())
    val topCharts: StateFlow<List<Song>> = _topCharts.asStateFlow()
    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()
    private var searchJob: Job? = null

    // Library
    private val _liked = MutableStateFlow<List<Song>>(emptyList())
    val liked: StateFlow<List<Song>> = _liked.asStateFlow()
    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    private val _downloads = MutableStateFlow<List<Song>>(emptyList())
    val downloads: StateFlow<List<Song>> = _downloads.asStateFlow()

    // Player extras
    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()
    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    // Settings
    val streamQuality = localStore.streamQuality
    val downloadQuality = localStore.downloadQuality
    val themeMode = localStore.themeMode

    fun loadHome() {
        scope.launch {
            _homeLoading.value = true
            _homeError.value = null
            try {
                val trendingList = repository.trending()
                _trending.value = trendingList
                // Charts / new releases via curated searches (YouTube Music
                // charts are search-driven in the extractor; no login).
                _topCharts.value = repository.charts("top hits 2026").take(20)
                _newReleases.value = repository.charts("new music releases 2026").take(20)
            } catch (e: Exception) {
                _homeError.value = "Couldn't load music. Check your connection and pull to retry."
            } finally {
                _homeLoading.value = false
            }
        }
    }

    fun refreshHome() = loadHome()

    fun searchForMood(mood: String) {
        setSearchQuery(mood)
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            _searchLoading.value = false
            _searchError.value = null
            return
        }
        searchJob = scope.launch {
            delay(400) // debounce
            _searchLoading.value = true
            _searchError.value = null
            try {
                val results = repository.search(q.trim())
                _searchResults.value = results
                if (results.isEmpty()) {
                    _searchError.value = "YouTube didn't return anything for this search. Check your connection and tap Retry."
                }
            } catch (e: Exception) {
                CrashHandler.logCaught("search", e)
                _searchResults.value = emptyList()
                _searchError.value = "Search failed. Check your connection and tap Retry."
            } finally {
                _searchLoading.value = false
            }
            // Saving history must never wipe the results if it fails.
            runCatching {
                localStore.addSearchHistory(q.trim())
                refreshSearchHistory()
            }.onFailure { CrashHandler.logCaught("search-history", it) }
        }
    }

    fun retrySearch() {
        setSearchQuery(_searchQuery.value)
    }

    fun refreshSearchHistory() {
        scope.launch { _searchHistory.value = localStore.searchHistory() }
    }

    fun clearSearchHistory() {
        scope.launch {
            localStore.clearSearchHistory()
            _searchHistory.value = emptyList()
        }
    }

    fun refreshLibrary() {
        scope.launch {
            _liked.value = localStore.likedSongs()
            _history.value = localStore.history()
            _playlists.value = localStore.playlists()
            _downloads.value = localStore.downloads()
        }
    }

    fun toggleLike(song: Song) {
        scope.launch {
            localStore.toggleLike(song)
            _liked.value = localStore.likedSongs()
        }
    }

    fun isLiked(song: Song): Boolean = _liked.value.any { it.videoId == song.videoId }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        scope.launch {
            localStore.createPlaylist(name)
            _playlists.value = localStore.playlists()
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch {
            localStore.deletePlaylist(id)
            _playlists.value = localStore.playlists()
        }
    }

    fun addToPlaylist(playlistId: String, song: Song) {
        scope.launch {
            localStore.addToPlaylist(playlistId, song)
            _playlists.value = localStore.playlists()
        }
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        scope.launch {
            localStore.removeFromPlaylist(playlistId, videoId)
            _playlists.value = localStore.playlists()
        }
    }

    fun downloadSong(song: Song) {
        scope.launch {
            if (downloader.download(song)) {
                _downloads.value = localStore.downloads()
            }
        }
    }

    fun downloadAll(songs: List<Song>) {
        scope.launch {
            downloader.downloadAll(songs)
            _downloads.value = localStore.downloads()
        }
    }

    fun deleteDownload(song: Song) {
        scope.launch {
            downloader.deleteDownload(song)
            _downloads.value = localStore.downloads()
        }
    }

    fun loadLyrics(song: Song) {
        scope.launch {
            _lyricsLoading.value = true
            _lyrics.value = null
            _lyrics.value = lyricsRepository.lyricsFor(song.title, song.artist)
            _lyricsLoading.value = false
        }
    }

    fun clearLyrics() {
        _lyrics.value = null
    }

    fun setStreamQuality(q: AudioQuality) {
        scope.launch { localStore.setStreamQuality(q) }
    }

    fun setDownloadQuality(q: AudioQuality) {
        scope.launch { localStore.setDownloadQuality(q) }
    }

    fun setThemeMode(mode: String) {
        scope.launch { localStore.setThemeMode(mode) }
    }

    fun clearHistory() {
        scope.launch {
            localStore.clearHistory()
            _history.value = emptyList()
        }
    }

    fun playPlaylist(playlistSong: Song, context: android.content.Context) {
        scope.launch {
            val tracks = runCatching { repository.loadPlaylist(playlistSong.videoId) }.getOrElse { emptyList() }
            if (tracks.isNotEmpty()) {
                PlayerManager.play(context, tracks[0], tracks)
            }
        }
    }

    fun setSleepDefault(min: Int) {
        scope.launch { localStore.setSleepDefaultMin(min) }
        PlayerManager.startSleepTimer(min)
    }
}
