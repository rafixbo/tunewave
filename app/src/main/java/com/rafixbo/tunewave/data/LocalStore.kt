package com.rafixbo.tunewave.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rafixbo.tunewave.model.AudioQuality
import com.rafixbo.tunewave.model.Playlist
import com.rafixbo.tunewave.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local persistence (v1): DataStore Preferences for settings + search
 * history, and JSON files for liked songs / history / playlists /
 * download metadata. No account, no server — everything stays on-device.
 * (Room was deferred to v2 to keep the v1 build green without a KSP
 * plugin; user-visible behaviour is identical — see README.)
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tunewave")

class LocalStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- Settings ----
    private val KEY_STREAM_QUALITY = stringPreferencesKey("stream_quality")
    private val KEY_DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
    private val KEY_THEME = stringPreferencesKey("theme") // SYSTEM | DARK
    private val KEY_SLEEP_DEFAULT = stringPreferencesKey("sleep_default_min")
    private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history_json")
    private val KEY_LIKED = stringPreferencesKey("liked_json")
    private val KEY_HISTORY = stringPreferencesKey("history_json")
    private val KEY_PLAYLISTS = stringPreferencesKey("playlists_json")
    private val KEY_DOWNLOADS = stringPreferencesKey("downloads_json")

    val streamQuality: Flow<AudioQuality> = context.dataStore.data.map {
        runCatching { AudioQuality.valueOf(it[KEY_STREAM_QUALITY] ?: "HIGH") }.getOrElse { AudioQuality.HIGH }
    }
    val downloadQuality: Flow<AudioQuality> = context.dataStore.data.map {
        runCatching { AudioQuality.valueOf(it[KEY_DOWNLOAD_QUALITY] ?: "HIGH") }.getOrElse { AudioQuality.HIGH }
    }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "DARK" }
    val sleepDefaultMin: Flow<Int> = context.dataStore.data.map {
        (it[KEY_SLEEP_DEFAULT] ?: "0").toIntOrNull() ?: 0
    }

    suspend fun setStreamQuality(q: AudioQuality) {
        context.dataStore.edit { it[KEY_STREAM_QUALITY] = q.name }
    }

    suspend fun setDownloadQuality(q: AudioQuality) {
        context.dataStore.edit { it[KEY_DOWNLOAD_QUALITY] = q.name }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun setSleepDefaultMin(min: Int) {
        context.dataStore.edit { it[KEY_SLEEP_DEFAULT] = min.toString() }
    }

    // ---- Generic JSON list helpers ----
    private suspend fun <T> readList(key: androidx.datastore.preferences.core.Preferences.Key<String>, decode: (String) -> List<T>): List<T> {
        val raw = context.dataStore.data.first()[key] ?: return emptyList()
        return runCatching { decode(raw) }.getOrElse { emptyList() }
    }

    private suspend fun <T> writeList(key: androidx.datastore.preferences.core.Preferences.Key<String>, list: List<T>, encode: (List<T>) -> String) {
        context.dataStore.edit { it[key] = encode(list) }
    }

    // ---- Search history ----
    suspend fun searchHistory(): List<String> =
        readList(KEY_SEARCH_HISTORY) { json.decodeFromString<List<String>>(it) }

    suspend fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val updated = (listOf(q) + searchHistory()).distinct().take(20)
        writeList(KEY_SEARCH_HISTORY, updated) { json.encodeToString(it) }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(KEY_SEARCH_HISTORY) }
    }

    // ---- Liked songs ----
    suspend fun likedSongs(): List<Song> =
        readList(KEY_LIKED) { json.decodeFromString<List<Song>>(it) }

    suspend fun toggleLike(song: Song): Boolean {
        val current = likedSongs().toMutableList()
        val exists = current.any { it.videoId == song.videoId }
        if (exists) current.removeAll { it.videoId == song.videoId } else current.add(0, song)
        writeList(KEY_LIKED, current) { json.encodeToString(it) }
        return !exists
    }

    suspend fun isLiked(videoId: String): Boolean =
        likedSongs().any { it.videoId == videoId }

    // ---- Play history (latest first, max 100) ----
    suspend fun history(): List<Song> =
        readList(KEY_HISTORY) { json.decodeFromString<List<Song>>(it) }

    suspend fun addHistory(song: Song) {
        val updated = (listOf(song) + history().filter { it.videoId != song.videoId }).take(100)
        writeList(KEY_HISTORY, updated) { json.encodeToString(it) }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(KEY_HISTORY) }
    }

    // ---- Playlists ----
    suspend fun playlists(): List<Playlist> =
        readList(KEY_PLAYLISTS) { json.decodeFromString<List<Playlist>>(it) }

    suspend fun createPlaylist(name: String): Playlist {
        val pl = Playlist(id = "pl_${System.currentTimeMillis()}", name = name.trim())
        val updated = playlists() + pl
        writeList(KEY_PLAYLISTS, updated) { json.encodeToString(it) }
        return pl
    }

    suspend fun deletePlaylist(id: String) {
        val updated = playlists().filter { it.id != id }
        writeList(KEY_PLAYLISTS, updated) { json.encodeToString(it) }
    }

    suspend fun addToPlaylist(playlistId: String, song: Song) {
        val updated = playlists().map { pl ->
            if (pl.id == playlistId && pl.songs.none { it.videoId == song.videoId }) {
                pl.copy(songs = pl.songs + song)
            } else {
                pl
            }
        }
        writeList(KEY_PLAYLISTS, updated) { json.encodeToString(it) }
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String) {
        val updated = playlists().map { pl ->
            if (pl.id == playlistId) pl.copy(songs = pl.songs.filter { it.videoId != videoId }) else pl
        }
        writeList(KEY_PLAYLISTS, updated) { json.encodeToString(it) }
    }

    // ---- Downloads metadata ----
    suspend fun downloads(): List<Song> =
        readList(KEY_DOWNLOADS) { json.decodeFromString<List<Song>>(it) }

    suspend fun addDownload(song: Song) {
        val updated = (listOf(song) + downloads().filter { it.videoId != song.videoId })
        writeList(KEY_DOWNLOADS, updated) { json.encodeToString(it) }
    }

    suspend fun removeDownload(videoId: String) {
        val updated = downloads().filter { it.videoId != videoId }
        writeList(KEY_DOWNLOADS, updated) { json.encodeToString(it) }
    }

    /** App-private downloads folder (no storage permission needed). */
    fun downloadsDir(): java.io.File {
        val dir = java.io.File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
