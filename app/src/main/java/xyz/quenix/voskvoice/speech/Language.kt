package xyz.quenix.voskvoice.speech

import java.util.Locale

/**
 * Supported recognition languages and the Vosk model used for each.
 *
 * Only the "small" models are used: a big model needs 2+ GB of RAM, while a watch has about
 * 300 MB free. A small model takes ~40-50 MB on disk and ~150-250 MB of RAM once loaded.
 */
enum class Language(
    /** Short code, also the model folder name on the watch: `files/models/<code>`. */
    val code: String,
    /** Label on the language button. */
    val label: String,
    val modelUrl: String,
    /** Download size, only for the UI. */
    val downloadMb: Int,
) {
    RU("ru", "RU", "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", 45),
    EN("en", "EN", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 40);

    fun next(): Language = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }

        /**
         * Maps a BCP 47 tag from [android.speech.RecognizerIntent.EXTRA_LANGUAGE] ("ru-RU",
         * "en_US", "en") to a language, or null if the caller asked for something unsupported.
         */
        fun fromTag(tag: String?): Language? {
            if (tag.isNullOrBlank()) return null
            val lang = tag.replace('_', '-').substringBefore('-').lowercase(Locale.ROOT)
            return fromCode(lang)
        }
    }
}
