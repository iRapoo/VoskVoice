package xyz.quenix.voskvoice.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import xyz.quenix.voskvoice.service.ModelKeeperService
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads a model zip and unpacks it on the fly (the zip itself is never stored, so a 45 MB
 * model needs 45 MB of free space, not 90).
 *
 * The archive is unpacked into a temporary folder that is renamed only after everything was
 * written, so an interrupted download never looks like an installed model.
 *
 * State is kept here rather than in the activity so that progress survives leaving and
 * reopening the screen. All listener calls happen on the main thread.
 */
object ModelDownloader {

    sealed interface State {
        /** [percent] is null until the server reports the size. */
        data class Running(val percent: Int?) : State
        data class Failed(val message: String) : State
    }

    private const val TAG = "ModelDownloader"
    private val main = Handler(Looper.getMainLooper())
    private val states = mutableMapOf<Language, State>()

    /** The screen currently showing progress, if any. */
    var listener: (() -> Unit)? = null

    fun state(language: Language): State? = states[language]

    fun start(context: Context, language: Language) {
        if (states[language] is State.Running) return
        val app = context.applicationContext
        update(language, State.Running(null))
        Thread({
            try {
                download(app, language)
                update(language, null)
                // The first installed model can now be kept in memory.
                main.post { ModelKeeperService.sync(app) }
            } catch (e: Exception) {
                Log.w(TAG, "Download of ${language.code} failed", e)
                update(language, State.Failed(e.message ?: e.javaClass.simpleName))
            }
        }, "model-download-${language.code}").start()
    }

    private fun update(language: Language, state: State?) = main.post {
        if (state == null) states.remove(language) else states[language] = state
        listener?.invoke()
    }

    private fun download(context: Context, language: Language) {
        val root = ModelStore.root(context).apply { mkdirs() }
        val tmp = File(root, "${language.code}.part").apply { deleteRecursively(); mkdirs() }

        val connection = URL(language.modelUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var lastPercent = -1
            val counting = CountingStream(connection.inputStream) { read ->
                if (total > 0) {
                    val percent = (read * 100 / total).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        update(language, State.Running(percent))
                    }
                }
            }
            ZipInputStream(counting.buffered()).use { zip -> unpack(zip, tmp) }
        } finally {
            connection.disconnect()
        }

        val target = ModelStore.dir(context, language)
        ModelCache.evict(language)
        target.deleteRecursively()
        if (!tmp.renameTo(target)) throw IOException("Cannot move model to $target")
        if (!ModelStore.isInstalled(context, language)) {
            target.deleteRecursively()
            throw IOException("Archive does not look like a Vosk model")
        }
    }

    /**
     * Model archives have a single top-level folder ("vosk-model-small-ru-0.22/am/...").
     * It is stripped so the model ends up directly in [target].
     */
    private fun unpack(zip: ZipInputStream, target: File) {
        val targetPath = target.canonicalPath + File.separator
        while (true) {
            val entry = zip.nextEntry ?: break
            val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
            if (relative.isEmpty()) continue
            val out = File(target, relative)
            // Zip Slip guard: never write outside the target folder.
            if (!out.canonicalPath.startsWith(targetPath)) throw IOException("Bad zip entry ${entry.name}")
            if (entry.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
        }
    }

    private class CountingStream(
        input: InputStream,
        private val onRead: (Long) -> Unit,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) onRead(++count) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) { count += it; onRead(count) } }
    }
}
