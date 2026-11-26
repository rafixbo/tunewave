package com.rafixbo.tunewave.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.rafixbo.tunewave.crash.CrashHandler
import com.rafixbo.tunewave.data.LocalStore
import com.rafixbo.tunewave.data.YouTubeRepository
import com.rafixbo.tunewave.model.AudioQuality
import com.rafixbo.tunewave.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Background playback service. Media3 shows the system notification +
 * lockscreen controls automatically from this session — playback keeps
 * going with the screen off or the app minimised.
 */
class PlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        // A failed player/service must never kill the app on open: the
        // controller future simply fails and the UI stays usable.
        try {
            val exo = ExoPlayer.Builder(this).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
            }
            player = exo
            mediaSession = MediaSession.Builder(this, exo).build()
        } catch (e: Exception) {
            CrashHandler.logCaught("player-service", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}

/** App-wide player state + controls. The UI talks only to this object. */
object PlayerManager {
    private val scope = CoroutineScope(Dispatchers.Main + CrashHandler.coroutineHandler)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionJob: Job? = null
    private var sleepJob: Job? = null

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sleepRemainingMin = MutableStateFlow(0)
    val sleepRemainingMin: StateFlow<Int> = _sleepRemainingMin.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    var repository: YouTubeRepository? = null
    var localStore: LocalStore? = null

    fun connect(context: Context) {
        if (controllerFuture != null) return
        try {
            val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                _controller.value = controller
                controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val idx = controller.currentMediaItemIndex
                    _queueIndex.value = idx
                    _queue.value.getOrNull(idx)?.let { _currentSong.value = it }
                    _durationMs.value = controller.duration.coerceAtLeast(0)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _isLoading.value = state == Player.STATE_BUFFERING
                    _durationMs.value = controller.duration.coerceAtLeast(0)
                }
            })
            startPositionPolling()
        }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            CrashHandler.logCaught("player-connect", e)
        }
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                val c = _controller.value
                if (c != null) {
                    _positionMs.value = c.currentPosition.coerceAtLeast(0)
                    val d = c.duration
                    if (d > 0) _durationMs.value = d
                }
                delay(500)
            }
        }
    }

    fun play(context: Context, song: Song, queue: List<Song> = listOf(song)) {
        val store = localStore
        val repo = repository ?: return
        scope.launch {
            _isLoading.value = true
            try {
                val quality = runCatching {
                    // Read the latest stream-quality setting synchronously-ish.
                    store?.streamQuality?.first() ?: AudioQuality.HIGH
                }.getOrElse { AudioQuality.HIGH }
                val startIndex = queue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
                val items = queue.map { s ->
                    val resolved = if (s.isDownloaded && s.localPath.isNotEmpty()) {
                        null // local file — handled below via file URI
                    } else {
                        runCatching { repo.resolveStream(s, quality) }.getOrNull()
                    }
                    val uri = if (s.isDownloaded && s.localPath.isNotEmpty()) {
                        android.net.Uri.fromFile(java.io.File(s.localPath))
                    } else {
                        android.net.Uri.parse(resolved?.url ?: return@map null)
                    }
                    val meta = MediaMetadata.Builder()
                        .setTitle(resolved?.song?.title ?: s.title)
                        .setArtist(resolved?.song?.artist ?: s.artist)
                        .setArtworkUri(android.net.Uri.parse(resolved?.song?.thumbnailUrl ?: s.thumbnailUrl))
                        .build()
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(s.videoId)
                        .setMediaMetadata(meta)
                        .build()
                }.filterNotNull()
                if (items.isEmpty()) return@launch
                // Keep the resolved (full) song data for the now-playing card.
                val resolvedQueue = queue.mapIndexed { i, s ->
                    if (i < items.size) s else s
                }
                _queue.value = resolvedQueue
                _queueIndex.value = startIndex
                _currentSong.value = queue[startIndex]
                val controller = _controller.value
                if (controller == null) {
                    connect(context)
                    // Controller connects async; retry shortly.
                    delay(600)
                }
                val c = _controller.value ?: return@launch
                c.setMediaItems(items, startIndex, 0L)
                c.prepare()
                c.play()
                runCatching { store?.addHistory(queue[startIndex]) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePlayPause() {
        val c = _controller.value ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        _controller.value?.seekToNextMediaItem()
    }

    fun previous() {
        _controller.value?.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        _controller.value?.seekTo(ms)
    }

    fun toggleShuffle() {
        val next = !_shuffle.value
        _shuffle.value = next
        _controller.value?.shuffleModeEnabled = next
    }

    fun cycleRepeat() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        _controller.value?.repeatMode = next
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed
        _controller.value?.playbackParameters = PlaybackParameters(speed)
    }

    /** Sleep timer: stops playback after [minutes]. 0 cancels. */
    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepRemainingMin.value = 0
            return
        }
        _sleepRemainingMin.value = minutes
        sleepJob = scope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000)
                remaining--
                _sleepRemainingMin.value = remaining
            }
            _controller.value?.pause()
            _sleepRemainingMin.value = 0
        }
    }

    fun release() {
        positionJob?.cancel()
        sleepJob?.cancel()
        controllerFuture?.let { runCatching { it.get() }.getOrNull()?.release() }
        controllerFuture = null
        _controller.value = null
    }
}
