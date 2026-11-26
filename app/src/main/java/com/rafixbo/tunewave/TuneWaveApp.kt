package com.rafixbo.tunewave

import android.app.Application
import com.rafixbo.tunewave.crash.CrashHandler
import com.rafixbo.tunewave.data.LocalStore
import com.rafixbo.tunewave.data.LyricsRepository
import com.rafixbo.tunewave.data.YouTubeRepository
import com.rafixbo.tunewave.download.TuneDownloader
import com.rafixbo.tunewave.playback.PlayerManager

class TuneWaveApp : Application() {
    lateinit var state: TuneWaveState
        private set

    override fun onCreate() {
        super.onCreate()
        // Crash catcher FIRST — before anything else can throw on open.
        CrashHandler.install(this)
        // No login, no account, no API key — the YouTube extractor is
        // initialised with plain unauthenticated requests. If init fails
        // (no network, YouTube-side change), the app must still open:
        // Home shows an error state and later calls retry the init.
        try {
            YouTubeRepository.ensureInit()
        } catch (e: Exception) {
            CrashHandler.logCaught("app-init/newpipe", e)
        }
        val repository = YouTubeRepository()
        val localStore = LocalStore(this)
        val downloader = TuneDownloader(repository, localStore)
        PlayerManager.repository = repository
        PlayerManager.localStore = localStore
        state = TuneWaveState(repository, localStore, downloader, LyricsRepository())
    }
}
