package com.rafixbo.tunewave.data

import com.rafixbo.tunewave.model.Song
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * YouTube Music search through the Innertube JSON API, parsed OUR way.
 *
 * Why this exists instead of NewPipe's YoutubeMusicSearchExtractor:
 * on Rafi's phone (v1.0.0+3, OnePlus CPH2719, Android 16) the extractor
 * threw `ParsingException: Unable to get contents.tabbedSearchResultsRenderer.tabs`
 * — the JSON YouTube returned to the phone had no
 * `contents.tabbedSearchResultsRenderer.tabs` path at all, so the
 * extractor found nothing even though the search itself succeeded.
 * The same endpoint, called from this VM with gl=US/GB/BD, DID return
 * that path — the shape YouTube serves varies by device/network, so
 * depending on one fixed container path is brittle.
 *
 * This parser therefore does NOT assume any container shape: it walks
 * the whole JSON tree and collects every `musicResponsiveListItemRenderer`
 * (and `videoRenderer` as a second shape) wherever it appears. A
 * different shelf layout, a missing tab, a renamed wrapper — none of
 * that matters, as long as the songs themselves are in the response.
 *
 * The request itself is the one verified live from the VM 2026-11-26
 * (a "saiyaraa" query returned real songs, HTTP 200): WEB_REMIX client,
 * Chrome UA, X-YouTube-Client-Name 67. The client version is NewPipe's
 * live-fetched one when available, otherwise the version that was
 * verified working.
 */
object YouTubeMusicJson {
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val FALLBACK_CLIENT_VERSION = "1.20240111.01.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val MUSIC_SONGS_PARAMS = "Eg-KAQwIARAAGAAgACgAMABqChAEEAUQAxAKEAk%3D"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().build()

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val version = runCatching { YoutubeParsingHelper.getYoutubeMusicClientVersion() }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_CLIENT_VERSION
        val body = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", version)
                    put("hl", "en-GB")
                    put("gl", "US")
                    put("platform", "DESKTOP")
                    put("utcOffsetMinutes", 0)
                })
                put("request", buildJsonObject { put("useSsl", true) })
                put("user", buildJsonObject { put("lockedSafetyMode", false) })
            })
            put("query", query)
            put("params", MUSIC_SONGS_PARAMS)
        }.toString()
        val request = Request.Builder()
            .url(SEARCH_URL)
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", version)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("YouTube Music search HTTP ${response.code}")
            }
            val text = response.body?.string()
                ?: throw IOException("Empty YouTube Music response")
            parseSongs(text)
        }
    }

    /** Tolerant parse: find song renderers anywhere in the JSON tree. */
    fun parseSongs(jsonText: String): List<Song> {
        val root = json.parseToJsonElement(jsonText)
        val found = LinkedHashMap<String, Song>()
        fun walk(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    (el["musicResponsiveListItemRenderer"] as? JsonObject)?.let { renderer ->
                        parseMusicItem(renderer)?.let { song ->
                            found.putIfAbsent(song.videoId, song)
                        }
                    }
                    (el["videoRenderer"] as? JsonObject)?.let { renderer ->
                        parseVideoItem(renderer)?.let { song ->
                            found.putIfAbsent(song.videoId, song)
                        }
                    }
                    el.values.forEach { walk(it) }
                }
                is JsonArray -> el.forEach { walk(it) }
                else -> {}
            }
        }
        walk(root)
        return found.values.take(30)
    }

    private fun parseMusicItem(r: JsonObject): Song? {
        val videoId = videoIdFrom(r) ?: return null
        val flex = r["flexColumns"] as? JsonArray ?: return null
        fun colText(i: Int): String? {
            val col = flex.getOrNull(i) as? JsonObject ?: return null
            val fcr = col["musicResponsiveListItemFlexColumnRenderer"] as? JsonObject
                ?: return null
            return runsText(fcr["text"] as? JsonObject)
        }
        val title = colText(0)?.takeIf { it.isNotBlank() } ?: return null
        val subtitle = colText(1) ?: ""
        // Subtitle is "Artist • Album • 3:45" — first segment is the artist.
        val parts = subtitle.split("•").map { it.trim() }
        val artist = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val durationSec = durationToSeconds(parts.lastOrNull())
            .takeIf { it > 0 }
            ?: durationToSeconds(
                (r["lengthText"] as? JsonObject)?.get("simpleText")
                    ?.jsonPrimitive?.contentOrNull,
            )
        val thumb = bestThumb(
            (r["thumbnail"] as? JsonObject)?.get("musicThumbnailRenderer") as? JsonObject,
        )
        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumb,
            durationSec = durationSec,
            kind = "SONG",
        )
    }

    private fun parseVideoItem(r: JsonObject): Song? {
        val videoId = (r["videoId"] as? JsonPrimitive)?.contentOrNull ?: return null
        val title = runsText(r["title"] as? JsonObject)?.takeIf { it.isNotBlank() }
            ?: return null
        val artist = runsText(r["ownerText"] as? JsonObject)
            ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val durationSec = durationToSeconds(
            (r["lengthText"] as? JsonObject)?.get("simpleText")
                ?.jsonPrimitive?.contentOrNull,
        )
        val thumb = bestThumb(r["thumbnail"] as? JsonObject)
        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumb,
            durationSec = durationSec,
            kind = "SONG",
        )
    }

    private fun videoIdFrom(obj: JsonObject): String? {
        (obj["playlistItemData"] as? JsonObject)?.get("videoId")
            ?.jsonPrimitive?.contentOrNull?.let { return it }
        val nav = obj["navigationEndpoint"] as? JsonObject
        (nav?.get("watchEndpoint") as? JsonObject)?.get("videoId")
            ?.jsonPrimitive?.contentOrNull?.let { return it }
        val overlay = (obj["overlay"] as? JsonObject)
            ?.get("musicItemThumbnailOverlayRenderer") as? JsonObject
        val content = overlay?.get("content") as? JsonObject
        val playButton = content?.get("musicPlayButtonRenderer") as? JsonObject
        val playNav = playButton?.get("playNavigationEndpoint") as? JsonObject
        return (playNav?.get("watchEndpoint") as? JsonObject)?.get("videoId")
            ?.jsonPrimitive?.contentOrNull
    }

    /** "Artist • Album" runs joined, or a plain simpleText. */
    private fun runsText(obj: JsonObject?): String? {
        if (obj == null) return null
        val runs = obj["runs"] as? JsonArray
        if (runs != null) {
            return runs.joinToString("") { run ->
                (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
            }.takeIf { it.isNotBlank() }
        }
        return obj["simpleText"]?.jsonPrimitive?.contentOrNull
    }

    /** Largest thumbnail in a `thumbnails` array. */
    private fun bestThumb(obj: JsonObject?): String? {
        val thumbs = obj?.get("thumbnails") as? JsonArray ?: return null
        return thumbs.mapNotNull { it as? JsonObject }
            .maxByOrNull { it["height"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?.get("url")?.jsonPrimitive?.contentOrNull
    }

    /** "3:45" -> 225, "1:02:30" -> 3750, anything else -> 0. */
    private fun durationToSeconds(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val parts = text.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }
}
