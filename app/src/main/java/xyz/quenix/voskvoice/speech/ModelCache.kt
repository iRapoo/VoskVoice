package xyz.quenix.voskvoice.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import java.io.IOException

/**
 * Keeps at most one Vosk model in memory.
 *
 * Loading a small model takes a few seconds on a watch and ~150-250 MB of RAM, so:
 * - only one language is loaded at a time (two would not fit next to the system);
 * - after the last user releases it, the model stays loaded for [IDLE_UNLOAD_MS], so replying to
 *   several messages in a row does not pay the loading time again.
 */
object ModelCache {

    class BusyException : IOException("Another language is being recognized right now")

    private const val TAG = "ModelCache"
    private const val IDLE_UNLOAD_MS = 5 * 60_000L

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val unloadIfIdle = Runnable { synchronized(lock) { if (users == 0 && !keepLoaded) closeLocked() } }

    private var language: Language? = null
    private var model: Model? = null
    private var users = 0

    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    fun isLoaded(language: Language): Boolean = synchronized(lock) { this.language == language && model != null }

    /**
     * Returns the model for [language], loading it if needed. Blocks for seconds on the first
     * call, so never call it on the main thread. Every successful call must be paired with
     * [release].
     */
    @Throws(IOException::class)
    fun acquire(context: Context, language: Language): Model = synchronized(lock) {
        main.removeCallbacks(unloadIfIdle)
        model?.let { current ->
            if (this.language == language) {
                users++
                return current
            }
            if (users > 0) throw BusyException()
            closeLocked()
        }
        if (!ModelStore.isInstalled(context, language)) throw IOException("Model ${language.code} is not installed")

        val started = SystemClock.elapsedRealtime()
        val loaded = Model(ModelStore.dir(context, language).absolutePath)
        Log.i(TAG, "Loaded ${language.code} in ${SystemClock.elapsedRealtime() - started} ms")
        this.language = language
        model = loaded
        users = 1
        loaded
    }

    fun release() = synchronized(lock) {
        if (users > 0) users--
        scheduleUnloadLocked()
    }

    /**
     * When true, an unused model is never unloaded by the idle timer (see ModelKeeperService).
     * Switching to another language still replaces it.
     */
    var keepLoaded: Boolean = false
        set(value) = synchronized(lock) {
            field = value
            scheduleUnloadLocked()
        }

    /** Loads [language] ahead of time so the next dictation starts instantly. Blocking. */
    fun preload(context: Context, language: Language) {
        try {
            acquire(context, language)
            release()
        } catch (e: IOException) {
            Log.w(TAG, "Preload of ${language.code} skipped: ${e.message}")
        }
    }

    /** Frees the model now if it is [language] and nobody uses it (e.g. before deleting it). */
    fun evict(language: Language) = synchronized(lock) {
        if (this.language == language && users == 0) closeLocked()
    }

    /**
     * Reaction to [android.content.ComponentCallbacks2.onTrimMemory]. A kept model is given up
     * only when the system is critically low on memory; otherwise it is freed as soon as the
     * system asks.
     */
    fun onTrimMemory(level: Int) = synchronized(lock) {
        val free = users == 0 && model != null && TrimPolicy.shouldFree(level, keepLoaded)
        if (model != null) Log.i(TAG, "onTrimMemory($level), keepLoaded=$keepLoaded, freeing=$free")
        if (free) closeLocked()
    }

    private fun scheduleUnloadLocked() {
        main.removeCallbacks(unloadIfIdle)
        if (users == 0 && !keepLoaded) main.postDelayed(unloadIfIdle, IDLE_UNLOAD_MS)
    }

    private fun closeLocked() {
        model?.close()
        if (model != null) Log.i(TAG, "Unloaded ${language?.code}")
        model = null
        language = null
    }
}
