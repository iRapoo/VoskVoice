package xyz.quenix.voskvoice.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatTest {

    @Test
    fun capitalizesFirstLetter() {
        assertEquals("Привет как дела", TextFormat.sentence("привет как дела", Language.RU))
    }

    @Test
    fun collapsesSpacesAndTrims() {
        assertEquals("Hello world", TextFormat.sentence("  hello   world ", Language.EN))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", TextFormat.sentence("   ", Language.RU))
    }

    @Test
    fun englishPronounI() {
        // Every standalone "i" becomes "I"; words that merely contain "i" are untouched.
        assertEquals("I think I'm late and I see it", TextFormat.sentence("i think i'm late and i see it", Language.EN))
        assertEquals("Maybe I will", TextFormat.sentence("maybe i will", Language.EN))
        assertEquals("It is fine", TextFormat.sentence("it is fine", Language.EN))
    }

    @Test
    fun russianLetterIsNotTouched() {
        assertEquals("Я и ты", TextFormat.sentence("я и ты", Language.RU))
    }

    @Test
    fun languageFromTag() {
        assertEquals(Language.RU, Language.fromTag("ru-RU"))
        assertEquals(Language.EN, Language.fromTag("en_US"))
        assertEquals(Language.EN, Language.fromTag("EN"))
        assertEquals(null, Language.fromTag("de-DE"))
        assertEquals(null, Language.fromTag(null))
    }
}
