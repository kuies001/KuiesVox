package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEnhancementPolicyTest {
    @Test
    fun featureRequiresSmartFormattingProviderKeyAndSafeEditor() {
        assertFalse(TextEnhancementPolicy.shouldUse(false, true, true, true, false))
        assertFalse(TextEnhancementPolicy.shouldUse(true, false, true, true, false))
        assertFalse(TextEnhancementPolicy.shouldUse(true, true, false, true, false))
        assertFalse(TextEnhancementPolicy.shouldUse(true, true, true, false, false))
        assertFalse(TextEnhancementPolicy.shouldUse(true, true, true, true, true))
        assertTrue(TextEnhancementPolicy.shouldUse(true, true, true, true, false))
    }

    @Test
    fun contextualCorrectionKeepsItsExistingGating() {
        assertFalse(ContextualCorrectionPolicy.shouldUse(true, false, true, true, false))
        assertFalse(ContextualCorrectionPolicy.shouldUse(true, true, false, true, false))
        assertFalse(ContextualCorrectionPolicy.shouldUse(true, true, true, false, false))
        assertFalse(ContextualCorrectionPolicy.shouldUse(true, true, true, true, true))
        assertFalse(ContextualCorrectionPolicy.shouldUse(false, true, true, true, false))
        assertTrue(ContextualCorrectionPolicy.shouldUse(true, true, true, true, false))
    }

    @Test
    fun taiwanWordingAndSmartPunctuationDefaultToEnabledForExistingUsers() {
        val settings = SmartFormattingSettings()

        assertTrue(settings.taiwanWordingEnabled)
        assertTrue(settings.smartPunctuationEnabled)
        assertEquals(settings, SmartFormattingSettingsRepository.normalize(settings))
    }
}
