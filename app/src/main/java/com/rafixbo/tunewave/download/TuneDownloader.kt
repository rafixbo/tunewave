package com.rafixbo.tunewave.download

import com.rafixbo.tunewave.data.LocalStore
import com.rafixbo.tunewave.data.YouTubeRepository
import com.rafixbo.tunewave.model.AudioQuality
import com.rafixbo.tunewave.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** Downloads a song's audio at the chosen quality into app-private files
 *  for offline playback. No DRM, no ads — the raw audio stream only. */
class TuneDownloader(
    private val repository: YouTubeRepository,
    private val localStore: LocalStore,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    suspend fun download(song: Song): Boolean = withContext(Dispatchers.IO) {
        if (song.videoId.isEmpty() || song.isPlaylist || song.isArtist) return@withContext false
        if (localStore.downloads().any { it.videoId == song.videoId }) return@withContext true
        _downloading.value = _downloading.value + song.videoId
        try {
            val quality = localStore.downloadQuality.first()
            val resolved = repository.resolveStream(song, quality) ?: return@withContext false
            val ext = resolved.extension
            val file = File(localStore.downloadsDir(), "${song.videoId}.$ext")
            val req = Request.Builder().url(resolved.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                val total = body.contentLength()
                FileOutputStream(file).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    val stream = body.byteStream()
                    while (stream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            _progress.value = _progress.value + (song.videoId to done.toFloat() / total)
                        }
                    }
                }
            }
            if (file.length() < 10_000) {
                file.delete()
                return@withContext false
            }
            localStore.addDownload(
                song.copy(
                    title = resolved.song.title,
                    artist = resolved.song.artist,
                    thumbnailUrl = resolved.song.thumbnailUrl,
                    durationSec = resolved.song.durationSec,
                    localPath = file.absolutePath,
                ),
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            _downloading.value = _downloading.value - song.videoId
            _progress.value = _progress.value - song.videoId
        }
    }

    suspend fun deleteDownload(song: Song) {
        runCatching {
            if (song.localPath.isNotEmpty()) File(song.localPath).delete()
            File(localStore.downloadsDir(), "${song.videoId}.opus").delete()
            File(localStore.downloadsDir(), "${song.videoId}.m4a").delete()
        }
        localStore.removeDownload(song.videoId)
    }

    /** Download a whole playlist/album (each track, best effort). */
    suspend fun downloadAll(songs: List<Song>): Int {
        var ok = 0
        for (song in songs) {
            if (download(song)) ok++
        }
        return ok
    }

    fun downloadQualityLabel(q: AudioQuality): String = when (q) {
        AudioQuality.HIGH -> "Opus ~256 kbps (itag 251)"
        AudioQuality.MEDIUM -> "AAC 128 kbps (itag 140)"
        AudioQuality.LOW -> "Opus ~70 kbps (itag 249)"
    }
}
