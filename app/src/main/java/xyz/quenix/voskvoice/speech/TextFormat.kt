package xyz.quenix.voskvoice.speech

/**
 * Small models output lowercase words without punctuation ("привет как дела"). This makes the
 * text look like something a person would type in a message.
 */
object TextFormat {

    private val englishI = Regex("""\bi\b""")
    private val spaces = Regex("""\s+""")

    fun sentence(raw: String, language: Language): String {
        var text = raw.trim().replace(spaces, " ")
        if (text.isEmpty()) return text
        if (language == Language.EN) text = text.replace(englishI, "I")
        return text.replaceFirstChar { it.titlecase() }
    }
}
