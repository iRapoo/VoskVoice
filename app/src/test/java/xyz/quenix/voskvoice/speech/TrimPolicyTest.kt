package xyz.quenix.voskvoice.speech

import android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
import android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
import android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
import android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimPolicyTest {

    @Test
    fun closingTheScreenNeverFreesTheModel() {
        // Regression: UI_HIDDEN (20) > RUNNING_CRITICAL (15) numerically, and used to unload
        // the kept model right after every dictation.
        assertFalse(TrimPolicy.shouldFree(TRIM_MEMORY_UI_HIDDEN, keepLoaded = true))
        assertFalse(TrimPolicy.shouldFree(TRIM_MEMORY_UI_HIDDEN, keepLoaded = false))
    }

    @Test
    fun keptModelIsFreedOnlyBeforeBeingKilled() {
        assertFalse(TrimPolicy.shouldFree(TRIM_MEMORY_RUNNING_MODERATE, keepLoaded = true))
        assertFalse(TrimPolicy.shouldFree(TRIM_MEMORY_RUNNING_LOW, keepLoaded = true))
        // Loading the model itself triggers RUNNING_CRITICAL on a 1 GB watch.
        assertFalse(TrimPolicy.shouldFree(TRIM_MEMORY_RUNNING_CRITICAL, keepLoaded = true))
        assertFalse(TrimPolicy.shouldFree(TRIM_MEMORY_BACKGROUND, keepLoaded = true))
        assertTrue(TrimPolicy.shouldFree(TRIM_MEMORY_COMPLETE, keepLoaded = true))
    }

    @Test
    fun unkeptModelIsFreedEarly() {
        assertFalse(TrimPolicy.shouldFree(TRIM_MEMORY_RUNNING_MODERATE, keepLoaded = false))
        assertTrue(TrimPolicy.shouldFree(TRIM_MEMORY_RUNNING_LOW, keepLoaded = false))
        assertTrue(TrimPolicy.shouldFree(TRIM_MEMORY_BACKGROUND, keepLoaded = false))
        assertTrue(TrimPolicy.shouldFree(TRIM_MEMORY_MODERATE, keepLoaded = false))
    }
}
