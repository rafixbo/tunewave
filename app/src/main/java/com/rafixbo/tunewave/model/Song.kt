package com.rafixbo.tunewave.model

import kotlinx.serialization.Serializable

/** A single track. Source is always YouTube (videoId) unless it is a local file. */
@Serializable
data class Song(
    val videoId: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val thumbnailUrl: String = "",
    val durationSec: Long = 0,
    /** SONG, PLAYLIST or ARTIST — search can return all three. */
    val kind: String = "SONG",
    /** Local file path when downloaded, empty otherwise. */
    val localPath: String = "",
    val streamCount: Int = 0,
) {
    val isDownloaded: Boolean get() = localPath.isNotEmpty()
    val isPlaylist: Boolean get() = kind == "PLAYLIST"
    val isArtist: Boolean get() = kind == "ARTIST"
    val displayDuration: String
        get() {
            if (durationSec <= 0) return ""
            val m = durationSec / 60
            val s = durationSec % 60
            return "%d:%02d".format(m, s)
        }
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

/** Download quality — YouTube's best audio is Opus itag 251 (~160-256 kbps).
 *  There is no FLAC / lossless on YouTube; TuneWave never claims otherwise. */
enum class AudioQuality(val label: String) {
    HIGH("High · Opus ~256 kbps"),
    MEDIUM("Medium · AAC 128 kbps"),
    LOW("Low · Opus ~70 kbps"),
}
