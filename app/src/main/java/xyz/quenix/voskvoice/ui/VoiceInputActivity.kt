package xyz.quenix.voskvoice.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import xyz.quenix.voskvoice.R
import xyz.quenix.voskvoice.settings.VoiceSettings
import xyz.quenix.voskvoice.speech.Dictation
import xyz.quenix.voskvoice.speech.Language
import xyz.quenix.voskvoice.speech.ModelStore

/**
 * "Speak now" screen. Handles [RecognizerIntent.ACTION_RECOGNIZE_SPEECH], which is what apps
 * (and the watch's reply screen) start when you tap the microphone, and returns the text in
 * [RecognizerIntent.EXTRA_RESULTS].
 *
 * Listening starts right away and stops by itself after a pause; tapping the microphone stops
 * earlier, swiping the screen away cancels.
 */
class VoiceInputActivity : Activity(), Dictation.Listener {

    private enum class Phase { LOADING, LISTENING, RETRY, NO_MODEL }

    private lateinit var settings: VoiceSettings
    private lateinit var language: Language
    private var dictation: Dictation? = null
    private var phase = Phase.LOADING
    private var waitingForPermission = false

    private lateinit var languageButton: Button
    private lateinit var ring: View
    private lateinit var mic: View
    private lateinit var status: TextView
    private lateinit var text: TextView
    private var prompt: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settings = VoiceSettings(this)
        language = settings.languageFor(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        languageButton = findViewById(R.id.language)
        ring = findViewById(R.id.level_ring)
        mic = findViewById(R.id.mic)
        status = findViewById(R.id.status)
        text = findViewById(R.id.text)

        prompt = intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT)
        text.hint = prompt
        mic.setOnClickListener { onMicTapped() }
        languageButton.setOnClickListener { switchLanguage() }
    }

    override fun onStart() {
        super.onStart()
        // Coming back from the permission dialog: onRequestPermissionsResult takes it from here.
        if (waitingForPermission) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen()
        } else {
            waitingForPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQUEST_MIC) return
        waitingForPermission = false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            listen()
        } else {
            Toast.makeText(this, R.string.voice_no_permission, Toast.LENGTH_LONG).show()
            cancelAndFinish()
        }
    }

    override fun onStop() {
        // Screen off, swiped away or another app on top: never keep the microphone open in the
        // background. The permission dialog also stops us, so that case is let through.
        if (!waitingForPermission && !isChangingConfigurations) cancelAndFinish()
        super.onStop()
    }

    private fun listen() {
        dictation?.cancel()
        languageButton.text = language.label
        text.text = ""
        if (!ModelStore.isInstalled(this, language)) {
            dictation = null
            show(Phase.NO_MODEL, getString(R.string.voice_no_model, language.label))
            return
        }
        show(Phase.LOADING, getString(R.string.voice_starting))
        dictation = Dictation(this, language, this).also { it.start() }
    }

    private fun onMicTapped() {
        when (phase) {
            Phase.LISTENING -> {
                status.setText(R.string.voice_processing)
                dictation?.stop()
            }
            Phase.RETRY -> listen()
            Phase.NO_MODEL -> startActivity(Intent(this, MainActivity::class.java))
            Phase.LOADING -> Unit
        }
    }

    /** Cycles through the installed languages; the choice becomes the new default. */
    private fun switchLanguage() {
        val installed = ModelStore.installed(this)
        var next = language.next()
        while (next != language && next !in installed) next = next.next()
        if (next == language) {
            Toast.makeText(this, getString(R.string.voice_only_one_model, language.next().label), Toast.LENGTH_SHORT).show()
            return
        }
        language = next
        settings.language = next
        listen()
    }

    private fun show(phase: Phase, message: String) {
        this.phase = phase
        status.text = message
        val color = getColor(if (phase == Phase.LISTENING) R.color.accent else R.color.idle)
        mic.backgroundTintList = ColorStateList.valueOf(color)
        ring.visibility = if (phase == Phase.LISTENING) View.VISIBLE else View.INVISIBLE
        if (phase != Phase.LISTENING) setLevel(0f)
    }

    private fun setLevel(level: Float) {
        val scale = 1f + level * 0.3f
        ring.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
    }

    // Dictation.Listener

    override fun onReady() {
        show(Phase.LISTENING, getString(R.string.voice_speak))
        // A buzz on the wrist is the clearest "go ahead" signal, the watch may not be in view.
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    // The microphone is already recording, so the user can talk while the model loads; the text
    // simply appears later. Say so in small print instead of making it look like a wait.
    override fun onLoading() {
        text.hint = getString(R.string.voice_loading)
    }

    override fun onLoaded() {
        text.hint = prompt
    }

    override fun onSpeechBegin() {
        status.setText(R.string.voice_listening)
    }

    override fun onLevel(level: Float) = setLevel(level)

    override fun onPartial(text: String) {
        this.text.text = text
    }

    override fun onResult(text: String) {
        dictation = null
        val data = Intent()
            .putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text))
            .putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1f))
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onError(error: Dictation.Error) {
        dictation = null
        when (error) {
            Dictation.Error.NO_SPEECH -> show(Phase.RETRY, getString(R.string.voice_no_speech))
            Dictation.Error.NO_MODEL -> show(Phase.NO_MODEL, getString(R.string.voice_no_model, language.label))
            Dictation.Error.BUSY -> show(Phase.RETRY, getString(R.string.voice_busy))
            Dictation.Error.AUDIO -> show(Phase.RETRY, getString(R.string.voice_audio_error))
        }
    }

    private fun cancelAndFinish() {
        dictation?.cancel()
        dictation = null
        if (!isFinishing) {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private companion object {
        const val REQUEST_MIC = 1
    }
}
