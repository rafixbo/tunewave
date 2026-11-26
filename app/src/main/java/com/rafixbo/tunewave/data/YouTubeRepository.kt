package com.rafixbo.tunewave.data

import com.rafixbo.tunewave.crash.CrashHandler
import com.rafixbo.tunewave.model.AudioQuality
import com.rafixbo.tunewave.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTube backend — online-based, NO login, NO Google account, NO API key.
 * Uses NewPipe Extractor (unauthenticated page fetches). Audio streams from
 * the extractor carry no ads: TuneWave never requests video ads and never
 * loads an ad URL.
 *
 * Honest quality: YouTube's best audio is Opus itag 251 (~160-256 kbps).
 * HIGH picks 251, MEDIUM picks 140 (AAC 128 kbps), LOW picks 249/171.
 */
class YouTubeRepository {

    private val service = ServiceList.YouTube

    init {
        // NewPipe.init is idempotent-safe to call once per process; the
        // Application calls ensureInit() on start, this is a backstop.
        ensureInit()
    }

    companion object {
        @Volatile
        private var initialised = false

        fun ensureInit() {
            if (initialised) return
            synchronized(this) {
                if (initialised) return
                NewPipe.init(OkHttpDownloader())
                initialised = true
            }
        }
    }

    private fun bestThumb(item: InfoItem): String {
        val thumbs = try {
            item.thumbnails
        } catch (_: Exception) {
            null
        } ?: return ""
        return thumbs.maxByOrNull { it.height }?.url
            ?: thumbs.lastOrNull()?.url
            ?: ""
    }

    private fun InfoItem.toSong(): Song? = when (this) {
        is StreamInfoItem -> Song(
            videoId = url.substringAfter("v=", url).substringBefore("&")
                .ifEmpty { url.substringAfterLast("/") },
            title = name,
            artist = uploaderName ?: "Unknown Artist",
            thumbnailUrl = bestThumb(this),
            durationSec = if (duration > 0) duration else 0,
            kind = "SONG",
        )
        is PlaylistInfoItem -> Song(
            videoId = url,
            title = name,
            artist = uploaderName ?: "Playlist",
            thumbnailUrl = bestThumb(this),
            kind = "PLAYLIST",
            streamCount = try {
                streamCount.toInt()
            } catch (_: Exception) {
                0
            },
        )
        is ChannelInfoItem -> Song(
            videoId = url,
            title = name,
            artist = "Artist",
            thumbnailUrl = bestThumb(this),
            kind = "ARTIST",
        )
        else -> null
    }

    /**
     * Search — our own YouTube Music JSON parser FIRST, web HTML second.
     * NewPipe's music extractor is NOT used: on Rafi's phone it threw
     * `ParsingException: Unable to get contents.tabbedSearchResultsRenderer.tabs`
     * (v1.0.0+3 log) — the JSON YouTube served the phone had no such
     * path. Our parser walks the whole JSON tree and finds the songs
     * wherever they are, so no single container path can break search.
     * Parser logic verified against a real response (20 songs, titles,
     * artists, durations, thumbnails all correct).
     */
    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        // 1) YouTube Music JSON (Innertube) — our own tolerant parser.
        val musicJson = runCatching { YouTubeMusicJson.search(query) }
        if (musicJson.isSuccess && !musicJson.getOrNull().isNullOrEmpty()) {
            return@withContext musicJson.getOrNull()!!
        }
        musicJson.exceptionOrNull()?.let { CrashHandler.logCaught("search/music-json", it) }
        // 2) Plain YouTube web search (HTML results page) — fallback.
        val web = runCatching {
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()
            extractor.initialPage.items.mapNotNull { it.toSong() }
        }
        if (web.isSuccess && !web.getOrNull().isNullOrEmpty()) {
            return@withContext web.getOrNull()!!
        }
        web.exceptionOrNull()?.let { CrashHandler.logCaught("search/web", it) }
        // 3) Last try: plain search with "music" appended.
        runCatching {
            val extractor = service.getSearchExtractor("$query music")
            extractor.fetchPage()
            extractor.initialPage.items.mapNotNull { it.toSong() }
        }.onFailure { CrashHandler.logCaught("search/fallback", it) }
            .getOrElse { emptyList() }
    }

    /** Trending Music from YouTube's default kiosk (Trending), with a
     *  plain search fallback if the kiosk ever changes shape. */
    suspend fun trending(): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        try {
            val kiosk = service.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            kiosk.initialPage.items.mapNotNull { it.toSong() }
                .filter { it.kind == "SONG" }
                .take(30)
        } catch (_: Exception) {
            search("trending music 2026").take(30)
        }
    }

    suspend fun charts(query: String): List<Song> = withContext(Dispatchers.IO) {
        runCatching { search(query) }.getOrElse { emptyList() }
    }

    /** Full track list of a YouTube playlist (by URL or ID). */
    suspend fun loadPlaylist(playlistUrl: String): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        val extractor = service.getPlaylistExtractor(playlistUrl)
        extractor.fetchPage()
        extractor.initialPage.items.mapNotNull { it.toSong() }
    }

    /** Resolve the direct audio URL for a song at the requested quality. */
    suspend fun resolveStream(song: Song, quality: AudioQuality): ResolvedStream? =
        withContext(Dispatchers.IO) {
            ensureInit()
            if (song.isPlaylist || song.isArtist) return@withContext null
            val url = "https://www.youtube.com/watch?v=${song.videoId}"
            val extractor = service.getStreamExtractor(url)
            extractor.fetchPage()
            val title = runCatching { extractor.name }.getOrElse { song.title }
            val artist = runCatching { extractor.uploaderName }.getOrElse { song.artist }
            val thumb = runCatching {
                extractor.thumbnails.maxByOrNull { it.height }?.url
                    ?: extractor.thumbnails.lastOrNull()?.url
            }.getOrNull() ?: song.thumbnailUrl
            val duration = runCatching { extractor.length }.getOrElse { song.durationSec }
            val streams: List<AudioStream> = runCatching { extractor.audioStreams }.getOrElse { emptyList() }
            val picked = pickStream(streams, quality) ?: return@withContext null
            ResolvedStream(
                song = song.copy(title = title ?: song.title, artist = artist ?: song.artist, thumbnailUrl = thumb, durationSec = duration),
                url = picked.url ?: return@withContext null,
                itag = picked.itag,
                bitrate = picked.averageBitrate,
                extension = if (picked.itag == 251 || picked.itag == 249 || picked.itag == 250) "opus" else "m4a",
            )
        }

    private fun pickStream(streams: List<AudioStream>, quality: AudioQuality): AudioStream? {
        if (streams.isEmpty()) return null
        val byItag = { itag: Int -> streams.firstOrNull { it.itag == itag } }
        return when (quality) {
            AudioQuality.HIGH -> byItag(251) ?: byItag(140)
                ?: streams.maxByOrNull { it.averageBitrate }
            AudioQuality.MEDIUM -> byItag(140) ?: byItag(251)
                ?: streams.maxByOrNull { it.averageBitrate }
            AudioQuality.LOW -> byItag(249) ?: byItag(171) ?: byItag(140)
                ?: streams.minByOrNull { it.averageBitrate }
        }
    }

    data class ResolvedStream(
        val song: Song,
        val url: String,
        val itag: Int,
        val bitrate: Int,
        val extension: String,
    )
}
