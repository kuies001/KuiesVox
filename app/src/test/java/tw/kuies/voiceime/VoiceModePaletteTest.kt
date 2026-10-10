package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceModePaletteTest {
    @Test
    fun translationUsesTheMintAccentWithADarkIcon() {
        assertEquals(0xFF9CE8D5.toInt(), VoiceModePalette.TRANSLATE_MINT)
        assertEquals(0xFF183D39.toInt(), VoiceModePalette.TRANSLATE_ICON)
    }

    @Test
    fun onlyTheTranslationModeChangesTheCentralButtonColour() {
        assertNull(VoiceModePalette.centralButtonAccentOrNull(translationModeActive = false))
        assertEquals(
            VoiceModePalette.TRANSLATE_MINT,
            VoiceModePalette.centralButtonAccentOrNull(translationModeActive = true)!!
        )
    }
}
