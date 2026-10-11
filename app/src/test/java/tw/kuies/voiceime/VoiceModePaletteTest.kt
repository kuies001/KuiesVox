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
        assertEquals(
            VoiceModePalette.AI_EDIT_PEACH,
            VoiceModePalette.accentForMode(translationModeActive = false, formatModeActive = false, aiEditModeActive = true)!!.base
        )
    }

    @Test
    fun formatModeHasADistinctColourFromNormalAndTranslation() {
        assertEquals(0xFFE8D8F2.toInt(), VoiceModePalette.FORMAT_ROSE)
        assertEquals(0xFF4A3554.toInt(), VoiceModePalette.FORMAT_ICON)
        assertTrue(VoiceModePalette.FORMAT_ROSE != VoiceModePalette.TRANSLATE_MINT)
    }

    @Test
    fun translationUsesTheMintAccentWithADarkIcon() {
        assertEquals(0xFFD8E6C8.toInt(), VoiceModePalette.TRANSLATE_MINT)
        assertEquals(0xFF2C4230.toInt(), VoiceModePalette.TRANSLATE_ICON)
    }

    @Test
    fun aiEditUsesThePeachAccentWithADarkIcon() {
        assertEquals(0xFFF3AAA4.toInt(), VoiceModePalette.AI_EDIT_PEACH)
        assertEquals(0xFF4E2725.toInt(), VoiceModePalette.AI_EDIT_ICON)
    }

    @Test
    fun normalVoiceUsesTheCreamAccentWithADeepBrownIcon() {
        assertEquals(0xFFF3D8C7.toInt(), VoiceModePalette.NORMAL_CREAM)
        assertEquals(0xFF35231C.toInt(), VoiceModePalette.NORMAL_ICON)
    }
}
