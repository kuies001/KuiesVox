package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModePaletteTest {
    @Test
    fun eachModeMapsToItsOwnCentralButtonColour() {
        assertNull(VoiceModePalette.accentForMode(translationModeActive = false, formatModeActive = false))
        assertEquals(
            VoiceModePalette.TRANSLATE_MINT,
            VoiceModePalette.accentForMode(translationModeActive = true, formatModeActive = false)!!.base
        )
        assertEquals(
            VoiceModePalette.FORMAT_ROSE,
            VoiceModePalette.accentForMode(translationModeActive = false, formatModeActive = true)!!.base
        )
    }

    @Test
    fun formatModeHasADistinctColourFromNormalAndTranslation() {
        assertEquals(0xFFE7B7D8.toInt(), VoiceModePalette.FORMAT_ROSE)
        assertEquals(0xFF3E1F33.toInt(), VoiceModePalette.FORMAT_ICON)
        assertTrue(VoiceModePalette.FORMAT_ROSE != VoiceModePalette.TRANSLATE_MINT)
    }

    @Test
    fun translationUsesTheMintAccentWithADarkIcon() {
        assertEquals(0xFF9CE8D5.toInt(), VoiceModePalette.TRANSLATE_MINT)
        assertEquals(0xFF183D39.toInt(), VoiceModePalette.TRANSLATE_ICON)
    }
}
