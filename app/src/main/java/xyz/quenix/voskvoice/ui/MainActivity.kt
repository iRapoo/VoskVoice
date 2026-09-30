package xyz.quenix.voskvoice.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import xyz.quenix.voskvoice.R
import xyz.quenix.voskvoice.service.ModelKeeperService
import xyz.quenix.voskvoice.service.VoskRecognitionService
import xyz.quenix.voskvoice.settings.VoiceSettings
import xyz.quenix.voskvoice.speech.Language
import xyz.quenix.voskvoice.speech.ModelDownloader
import xyz.quenix.voskvoice.speech.ModelStore

/**
 * Setup screen: microphone permission, model downloads, default language, a test button and
 * whether the watch actually routes voice input to this app.
 */
class MainActivity : Activity() {

    private lateinit var settings: VoiceSettings

    private lateinit var scroll: ScrollView
    private lateinit var micButton: Button
    private lateinit var models: LinearLayout
    private lateinit var languageButton: Button
    private lateinit var keepSwitch: Switch
    private lateinit var testResult: TextView
    private lateinit var voiceInputStatus: TextView
    private lateinit var recognizerStatus: TextView
    private val modelButtons = mutableMapOf<Language, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = VoiceSettings(this)

        scroll = findViewById(R.id.scroll)
        micButton = findViewById(R.id.mic_permission)
        models = findViewById(R.id.models)
        languageButton = findViewById(R.id.default_language)
        keepSwitch = findViewById(R.id.keep_loaded)
        testResult = findViewById(R.id.test_result)
        voiceInputStatus = findViewById(R.id.voice_input_status)
        recognizerStatus = findViewById(R.id.recognizer_status)

        micButton.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC) }

        for (language in Language.entries) {
            val button = layoutInflater.inflate(R.layout.row_button, models, false) as Button
            button.setOnClickListener { onModelTapped(language) }
            button.setOnLongClickListener { onModelLongPressed(language) }
            models.addView(button)
            modelButtons[language] = button
        }

        languageButton.setOnClickListener {
            settings.language = settings.language.next()
            refresh()
        }

        keepSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked == settings.keepLoaded) return@setOnCheckedChangeListener
            settings.keepLoaded = checked
            ModelKeeperService.sync(this)
        }

        findViewById<Button>(R.id.test).setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .setClass(this, VoiceInputActivity::class.java)
            startActivityForResult(intent, REQUEST_TEST)
        }
    }

    override fun onResume() {
        super.onResume()
        ModelDownloader.listener = ::refresh
        ModelKeeperService.sync(this)
        refresh()
        // Lets the rotating crown / bezel scroll the screen.
        scroll.requestFocus()
    }

    override fun onPause() {
        ModelDownloader.listener = null
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    @Deprecated("Framework Activity API; AndroidX is intentionally not used")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_TEST) return
        val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        testResult.text = if (resultCode == RESULT_OK && text != null) text else getString(R.string.test_cancelled)
        testResult.visibility = View.VISIBLE
    }

    private fun onModelTapped(language: Language) {
        when {
            ModelDownloader.state(language) is ModelDownloader.State.Running -> Unit
            ModelStore.isInstalled(this, language) ->
                Toast.makeText(this, R.string.model_delete_hint, Toast.LENGTH_SHORT).show()
            else -> ModelDownloader.start(this, language)
        }
        refresh()
    }

    private fun onModelLongPressed(language: Language): Boolean {
        if (!ModelStore.isInstalled(this, language)) return false
        ModelStore.delete(this, language)
        ModelKeeperService.sync(this)
        Toast.makeText(this, getString(R.string.model_deleted, getString(languageName(language))), Toast.LENGTH_SHORT).show()
        refresh()
        return true
    }

    private fun refresh() {
        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micButton.setText(if (micGranted) R.string.mic_granted else R.string.mic_request)
        micButton.isEnabled = !micGranted

        for ((language, button) in modelButtons) {
            val name = getString(languageName(language))
            button.text = when (val state = ModelDownloader.state(language)) {
                is ModelDownloader.State.Running ->
                    if (state.percent == null) getString(R.string.model_connecting, name)
                    else getString(R.string.model_progress, name, state.percent)
                is ModelDownloader.State.Failed -> getString(R.string.model_failed, name, state.message)
                null ->
                    if (ModelStore.isInstalled(this, language)) getString(R.string.model_installed, name, ModelStore.sizeMb(this, language))
                    else getString(R.string.model_download, name, language.downloadMb)
            }
        }

        languageButton.text = getString(R.string.default_language, getString(languageName(settings.language)))
        keepSwitch.isChecked = settings.keepLoaded

        voiceInputStatus.text = getString(R.string.voice_input_status, getString(voiceInputHandler()))
        recognizerStatus.setText(if (isSystemRecognizer()) R.string.recognizer_ours else R.string.recognizer_not_set)
    }

    /** Who opens when an app asks for voice input: us, someone else, or "ask every time". */
    private fun voiceInputHandler(): Int {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return R.string.handler_none
        val handlers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        return when {
            resolved.packageName == packageName -> R.string.handler_ours
            // Resolved to something that is not a handler: the chooser, i.e. no default yet.
            // On Wear OS it lives in com.google.android.apps.wearable.settings, not in "android".
            ComponentName(resolved.packageName, resolved.name) !in handlers -> R.string.handler_ask
            else -> R.string.handler_other
        }
    }

    private fun isSystemRecognizer(): Boolean {
        val value = Settings.Secure.getString(contentResolver, "voice_recognition_service") ?: return false
        return ComponentName.unflattenFromString(value) == ComponentName(this, VoskRecognitionService::class.java)
    }

    private companion object {
        const val REQUEST_MIC = 1
        const val REQUEST_TEST = 2

        fun languageName(language: Language) = when (language) {
            Language.RU -> R.string.language_ru
            Language.EN -> R.string.language_en
        }
    }
}
