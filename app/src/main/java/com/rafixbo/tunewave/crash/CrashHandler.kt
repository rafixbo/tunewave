package com.rafixbo.tunewave.crash

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.rafixbo.tunewave.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * TuneWave crash catcher — installed FIRST in TuneWaveApp.onCreate, before
 * anything else can throw.
 *
 * - Catches every UNCAUGHT crash (any thread, any screen, any feature) and
 *   writes a full log file with app version, Android version, device model
 *   and the complete stack trace, then lets the system show its normal
 *   crash dialog (the previous default handler still runs).
 * - `logCaught(tag, throwable)` is the catcher for CAUGHT errors: every
 *   guarded call site in the app reports here instead of failing silently.
 * - `coroutineHandler` is attached to every app CoroutineScope, so one
 *   failing background job (library refresh, download, playback poll)
 *   is logged instead of killing the whole app.
 * - Logs live in app-private files/crash-logs/ (last 10 kept). Settings →
 *   Crash logs shows the latest one and shares it as text — that is how a
 *   crash report reaches the developer after a crash on a real phone.
 */
object CrashHandler {
    private const val TAG = "TuneWaveCrash"
    private const val DIR = "crash-logs"
    private const val MAX_FILES = 10

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var installed = false

    /** The handler for every app CoroutineScope. Never throws. */
    val coroutineHandler = CoroutineExceptionHandler { _, throwable ->
        logCaught("coroutine", throwable)
    }

    fun install(app: Application) {
        if (installed) return
        installed = true
        appContext = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeLog("UNCAUGHT [thread=${thread.name}]", throwable)
            } catch (_: Exception) {
                // Never let the catcher itself crash the crash path.
            }
            // Hand over to the system default: crash dialog + process death,
            // exactly as Android would do without us.
            previous?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "Crash catcher installed (v${BuildConfig.VERSION_NAME})")
    }

    /** Report a caught error from anywhere in the app. Never throws. */
    fun logCaught(tag: String, throwable: Throwable) {
        try {
            Log.e(TAG, "Caught in $tag: ${throwable.message}", throwable)
            writeLog("CAUGHT [$tag]", throwable)
        } catch (_: Exception) {
        }
    }

    /** All saved crash logs, newest first. Never throws. */
    fun listLogs(): List<File> = try {
        val dir = logDir() ?: return emptyList()
        dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Full text of the newest crash log, or null when there is none. */
    fun latestLogText(): String? = try {
        listLogs().firstOrNull()?.readText()
    } catch (_: Exception) {
        null
    }

    /** Delete all saved crash logs. Never throws. */
    fun clearLogs() {
        try {
            listLogs().forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun logDir(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, DIR).apply { if (!exists()) mkdirs() }
    }

    private fun writeLog(kind: String, throwable: Throwable) {
        val dir = logDir() ?: return
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        file.writeText(
            "TuneWave crash log\n" +
                "time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n" +
                "kind: $kind\n" +
                "app: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})\n" +
                "android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                "device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "---\n" +
                sw.toString(),
        )
        // Keep only the newest logs.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
        Log.e(TAG, "Crash log written: ${file.name}")
    }
}
