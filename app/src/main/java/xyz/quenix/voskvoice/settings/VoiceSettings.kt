package xyz.quenix.voskvoice.settings

import android.content.Context
import xyz.quenix.voskvoice.speech.Language
import xyz.quenix.voskvoice.speech.ModelStore

class VoiceSettings(private val context: Context) {

    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)

    /** Language used when the calling app does not ask for a specific one. */
    var language: Language
        get() = Language.fromCode(prefs.getString(KEY_LANGUAGE, null)) ?: Language.RU
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.code).apply()

    /**
     * Keep the last used model in memory all the time (ModelKeeperService), so voice input starts
     * instantly instead of after 5-8 s of loading. Costs ~170 MB of RAM.
     */
    var keepLoaded: Boolean
        get() = prefs.getBoolean(KEY_KEEP_LOADED, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_LOADED, value).apply()

    /**
     * Picks the language for a request: the one the app asked for if its model is installed,
     * otherwise the default, otherwise any installed one.
     */
    fun languageFor(requestedTag: String?): Language {
        val installed = ModelStore.installed(context)
        return listOfNotNull(Language.fromTag(requestedTag), language).firstOrNull { it in installed }
            ?: installed.firstOrNull()
            ?: language
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_KEEP_LOADED = "keep_loaded"
    }
}
