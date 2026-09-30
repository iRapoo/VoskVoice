package xyz.quenix.voskvoice.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One phrase of dictation: listens to the microphone and stops by itself after a pause in
 * speech, like Google voice input does.
 *
 * Loading a model takes ~5-8 s on a watch, so the microphone is opened first and audio is
 * buffered while the model loads: the user can start talking immediately and nothing is lost,
 * the text just appears once the model is ready.
 *
 * Two background threads: one reads the microphone into [chunks], the other loads the model and
 * feeds the chunks to Vosk. All [Listener] calls arrive on the main thread, none after [cancel].
 */
class Dictation(
    private val context: Context,
    val language: Language,
    private val listener: Listener,
) {

    interface Listener {
        /** The microphone is open, the user can speak. */
        fun onReady() {}

        /** The model is not in memory yet; audio is buffered while it loads. */
        fun onLoading() {}

        /** The model is loaded (only called after [onLoading]). */
        fun onLoaded() {}

        fun onSpeechBegin() {}

        /** Loudness, 0 (silence) to 1 (loud), about 10 times a second. */
        fun onLevel(level: Float) {}

        /** Text recognized so far; it can still change. */
        fun onPartial(text: String) {}

        fun onEndOfSpeech() {}

        fun onResult(text: String)

        fun onError(error: Error)
    }

    enum class Error { NO_MODEL, BUSY, AUDIO, NO_SPEECH }

    @Volatile private var stopRequested = false
    @Volatile private var cancelled = false

    /** Set by the recognizer thread when it no longer needs audio. */
    @Volatile private var micDone = false
    @Volatile private var micFailed = false
    private val chunks = LinkedBlockingQueue<ShortArray>()
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        Thread(::run, "dictation-${language.code}").start()
    }

    /** Stops listening and delivers what was heard so far. */
    fun stop() {
        stopRequested = true
    }

    /** Stops listening; no more listener calls. */
    fun cancel() {
        cancelled = true
    }

    private fun post(block: Listener.() -> Unit) {
        main.post { if (!cancelled) listener.block() }
    }

    private fun run() {
        val record = openMicrophone() ?: return post { onError(Error.AUDIO) }
        val reader = Thread({ readMicrophone(record) }, "dictation-mic")
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                // Another app holds the microphone.
                post { onError(Error.AUDIO) }
                return
            }
            post { onReady() }
            reader.start()
            recognize()
        } finally {
            micDone = true
            if (reader.isAlive) reader.join()
            record.stop()
            record.release()
        }
    }

    private fun readMicrophone(record: AudioRecord) {
        while (!micDone && !cancelled && !stopRequested) {
            val buffer = ShortArray(CHUNK_SAMPLES)
            val read = record.read(buffer, 0, buffer.size)
            if (read < 0) {
                Log.w(TAG, "AudioRecord.read returned $read")
                micFailed = true
                return
            }
            if (read == 0) continue
            val level = level(buffer, read)
            post { onLevel(level) }
            chunks.put(if (read == buffer.size) buffer else buffer.copyOf(read))
        }
    }

    private fun recognize() {
        val loading = !ModelCache.isLoaded(language)
        if (loading) post { onLoading() }
        val model = try {
            ModelCache.acquire(context, language)
        } catch (e: ModelCache.BusyException) {
            return post { onError(Error.BUSY) }
        } catch (e: IOException) {
            Log.w(TAG, "Cannot load model ${language.code}", e)
            return post { onError(Error.NO_MODEL) }
        }
        try {
            if (loading) post { onLoaded() }
            if (!cancelled) Recognizer(model, Dictation.SAMPLE_RATE.toFloat()).use(::decode)
        } finally {
            ModelCache.release()
        }
    }

    private fun decode(recognizer: Recognizer) {
        // Kaldi's endpointer ends the phrase after this much trailing silence, and gives up if
        // nothing was said at all. Unlike our own timers it knows speech from background noise,
        // and it counts audio time, so buffered audio is judged correctly.
        recognizer.setEndpointerDelays(NO_SPEECH_TIMEOUT_S, END_SILENCE_S, MAX_PHRASE_S)

        var speaking = false
        var lastPartial = ""
        var text = ""
        var endpoint = false
        var samples = 0L
        val started = SystemClock.elapsedRealtime()

        while (!cancelled) {
            val chunk = chunks.poll(POLL_MS, TimeUnit.MILLISECONDS)
            if (chunk == null) {
                // Tapped "stop" and everything recorded so far is decoded; or the mic died.
                if (stopRequested || micFailed) break
                continue
            }
            samples += chunk.size
            if (recognizer.acceptWaveForm(chunk, chunk.size)) {
                text = field(recognizer.result, "text")
                endpoint = true
                break
            }
            val partial = field(recognizer.partialResult, "partial")
            if (partial.isNotEmpty() && !speaking) {
                speaking = true
                post { onSpeechBegin() }
            }
            if (partial != lastPartial) {
                lastPartial = partial
                post { onPartial(TextFormat.sentence(partial, language)) }
            }
            // Safety net in case the endpointer never fires (e.g. constant loud noise).
            if (samples > (MAX_PHRASE_S + 2) * SAMPLE_RATE) break
        }
        micDone = true
        if (cancelled) return
        if (micFailed && !speaking) return post { onError(Error.AUDIO) }

        if (!endpoint) text = field(recognizer.finalResult, "text")
        val audioMs = samples * 1000 / SAMPLE_RATE
        Log.i(TAG, "Decoded $audioMs ms of audio in ${SystemClock.elapsedRealtime() - started} ms")

        post { onEndOfSpeech() }
        val result = TextFormat.sentence(text, language)
        if (result.isEmpty()) post { onError(Error.NO_SPEECH) } else post { onResult(result) }
    }

    private fun openMicrophone(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = max(minBuffer, CHUNK_SAMPLES * 2 * 4)
        // VOICE_RECOGNITION is the source tuned for speech recognition (no AGC or noise
        // suppression that distort the signal); some devices do not support it, so fall back to MIC.
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val record = try {
                AudioRecord(
                    source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes,
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "No RECORD_AUDIO permission", e)
                return null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Audio source $source rejected", e)
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    companion object {
        private const val TAG = "Dictation"

        /** All Vosk small models are trained on 16 kHz audio. */
        const val SAMPLE_RATE = 16_000

        /** 100 ms of audio per read. */
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10
        private const val POLL_MS = 200L

        private const val NO_SPEECH_TIMEOUT_S = 6f
        private const val END_SILENCE_S = 1.2f
        private const val MAX_PHRASE_S = 30f

        private fun field(json: String, key: String): String =
            runCatching { JSONObject(json).optString(key) }.getOrDefault("").trim()

        /** Maps RMS loudness from about -50 dBFS (quiet room) .. -10 dBFS (loud voice) to 0..1. */
        private fun level(buffer: ShortArray, count: Int): Float {
            var sum = 0.0
            for (i in 0 until count) sum += buffer[i].toDouble() * buffer[i]
            val rms = sqrt(sum / count) / Short.MAX_VALUE
            val dbfs = 20 * log10(max(rms, 1e-6))
            return ((dbfs + 50) / 40).toFloat().coerceIn(0f, 1f)
        }
    }
}
