package xyz.quenix.voskvoice.speech

import android.content.ComponentCallbacks2

/** When to give a loaded model back to the system on [ComponentCallbacks2.onTrimMemory]. */
object TrimPolicy {

    /**
     * Trim levels are not one scale: RUNNING_* (5, 10, 15) are sent to foreground processes,
     * UI_HIDDEN (20) just means our screen was closed, and BACKGROUND..COMPLETE (40-80) are sent
     * to cached processes. So `level >= RUNNING_CRITICAL` would wrongly include UI_HIDDEN.
     *
     * A kept model ignores RUNNING_CRITICAL: on a 1 GB watch loading the model itself triggers it
     * (seen on a TicWatch Pro 3 right after every load, with ~280 MB still available thanks to
     * zram), so honoring it would make "keep in memory" useless. The user opted into the memory
     * cost; the model is only given up when the system says our process is about to be killed.
     */
    fun shouldFree(level: Int, keepLoaded: Boolean): Boolean = when {
        level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> false
        keepLoaded -> level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        else -> level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
    }
}
