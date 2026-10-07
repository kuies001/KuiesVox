package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechLanguageModeTest {
    @Test
    fun modesMapOnlySupportedLanguageCodesToGroq() {
        assertNull(SpeechLanguageMode.AUTO.groqLanguageCode)
        assertEquals("zh", SpeechLanguageMode.CHINESE.groqLanguageCode)
        assertEquals("en", SpeechLanguageMode.ENGLISH.groqLanguageCode)
        assertNull(SpeechLanguageMode.MIXED.groqLanguageCode)
    }

    @Test
    fun mixedIsDefaultWhenNoLanguagePreferenceExists() {
        assertEquals(SpeechLanguageMode.MIXED, SpeechLanguageMode.fromStoredValues(null, null))
        assertEquals(SpeechLanguageMode.MIXED, SmartFormattingSettings().speechLanguageMode)
    }

    @Test
    fun currentLanguagePreferenceTakesPrecedenceOverLegacyValue() {
        assertEquals(
            SpeechLanguageMode.AUTO,
            SpeechLanguageMode.fromStoredValues("auto", "zh")
        )
    }

    @Test
    fun existingLegacyLanguagePreferenceIsPreserved() {
        assertEquals(
            SpeechLanguageMode.CHINESE,
            SpeechLanguageMode.fromStoredValues(null, "zh")
        )
        assertEquals(
            SpeechLanguageMode.ENGLISH,
            SpeechLanguageMode.fromStoredValues(null, "english")
        )
    }

    @Test
    fun invalidPreferenceFallsBackToMixed() {
        assertEquals(
            SpeechLanguageMode.MIXED,
            SpeechLanguageMode.fromStoredValues("unknown", null)
        )
    }
}
