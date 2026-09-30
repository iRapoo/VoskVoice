package xyz.quenix.voskvoice.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import xyz.quenix.voskvoice.settings.VoiceSettings
import xyz.quenix.voskvoice.speech.Dictation
import xyz.quenix.voskvoice.speech.ModelCache
import xyz.quenix.voskvoice.speech.ModelStore

/**
 * System speech recognizer backed by Vosk, for apps that use [SpeechRecognizer] directly
 * instead of starting the "speak now" screen.
 *
 * The watch has no recognizer by default; this one is made the system default with
 * `adb shell settings put secure voice_recognition_service xyz.quenix.voskvoice/.service.VoskRecognitionService`
 * (see README).
 */
class VoskRecognitionService : RecognitionService() {

    private var dictation: Dictation? = null

    override fun onStartListening(intent: Intent, callback: Callback) {
        dictation?.cancel()
        dictation = null

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback.safely { error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
            return
        }
        val language = VoiceSettings(this).languageFor(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        if (!ModelStore.isInstalled(this, language)) {
            callback.safely { error(ERROR_LANGUAGE_UNAVAILABLE) }
            return
        }
        val partials = intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

        dictation = Dictation(this, language, object : Dictation.Listener {
            override fun onReady() = callback.safely { readyForSpeech(Bundle()) }

            override fun onSpeechBegin() = callback.safely { beginningOfSpeech() }

            // SpeechRecognizer clients expect roughly -2..10 dB.
            override fun onLevel(level: Float) = callback.safely { rmsChanged(level * 12f - 2f) }

            override fun onPartial(text: String) {
                if (partials && text.isNotEmpty()) callback.safely { partialResults(results(text)) }
            }

            override fun onEndOfSpeech() = callback.safely { endOfSpeech() }

            override fun onResult(text: String) {
                dictation = null
                callback.safely { results(results(text)) }
            }

            override fun onError(error: Dictation.Error) {
                dictation = null
                val code = when (error) {
                    Dictation.Error.NO_SPEECH -> SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    Dictation.Error.NO_MODEL -> ERROR_LANGUAGE_UNAVAILABLE
                    Dictation.Error.BUSY -> SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    Dictation.Error.AUDIO -> SpeechRecognizer.ERROR_AUDIO
                }
                callback.safely { error(code) }
            }
        }).also { it.start() }
    }

    override fun onStopListening(callback: Callback) {
        dictation?.stop()
    }

    override fun onCancel(callback: Callback) {
        dictation?.cancel()
        dictation = null
    }

    override fun onDestroy() {
        dictation?.cancel()
        dictation = null
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ModelCache.onTrimMemory(level)
    }

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
    }

    /** The client may have gone away; that is not our problem to crash on. */
    private inline fun Callback.safely(block: Callback.() -> Unit) {
        try {
            block()
        } catch (e: RemoteException) {
            Log.w(TAG, "Client disconnected", e)
            dictation?.cancel()
            dictation = null
        }
    }

    private companion object {
        const val TAG = "VoskRecognition"

        /**
         * [SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE] exists only since API 31; older clients
         * treat unknown codes as a generic error, which is fine.
         */
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
