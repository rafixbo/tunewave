package com.rafixbo.tunewave.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/** Lyrics from LRCLIB (free, no key, no login): https://lrclib.net */
class LyricsRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns synced lyrics (LRC) if available, else plain lyrics, else null. */
    suspend fun lyricsFor(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://lrclib.net/api/search?track_name=" +
                URLEncoder.encode(title, "UTF-8") +
                "&artist_name=" + URLEncoder.encode(artist, "UTF-8")
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val arr = json.parseToJsonElement(body).jsonArray
                val first = arr.firstOrNull()?.jsonObject ?: return@withContext null
                val synced = first["syncedLyrics"]?.jsonPrimitive?.content
                val plain = first["plainLyrics"]?.jsonPrimitive?.content
                val lrc = if (!synced.isNullOrBlank()) synced else plain
                lrc?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.joinToString("\n") { line ->
                        // Strip LRC timestamps "[00:12.34] words" -> "words"
                        line.replace(Regex("^\\[\\d{2}:\\d{2}\\.\\d{2}]\\s*"), "")
                    }?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}
