package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVoiceProfileTest {
    @Test
    fun missingProfileUsesTheCurrentGlobalSettings() {
        val global = SmartFormattingSettings(
            speechModel = "whisper-large-v3",
            speechLanguageMode = SpeechLanguageMode.ENGLISH,
            enabled = false,
            contextualCorrectionEnabled = false,
            terminalPeriodMode = TerminalPeriodMode.NEVER,
            formattingStyle = TextFormattingStyle.FORMAL
        )

        val resolved = AppVoiceProfilePolicy.resolve("com.example.notes", emptyList(), global)

        assertEquals(global, resolved.settings)
        assertEquals(TextFormattingStyle.FORMAL, resolved.formattingStyle)
        assertNull(resolved.appliedProfile)
    }

    @Test
    fun explicitOverridesApplyAndAllOtherFieldsInherit() {
        val global = SmartFormattingSettings(
            speechModel = "whisper-large-v3",
            speechLanguageMode = SpeechLanguageMode.AUTO,
            enabled = true,
            provider = TextFormattingProviderId.GROQ,
            contextualCorrectionEnabled = true,
            terminalPeriodMode = TerminalPeriodMode.ALWAYS,
            formattingStyle = TextFormattingStyle.DAILY
        )
        val profile = AppVoiceProfile(
            packageName = "com.example.notes",
            speechModelOverride = "whisper-large-v3-turbo",
            speechLanguageModeOverride = SpeechLanguageMode.CHINESE,
            smartFormattingEnabledOverride = false,
            contextualCorrectionEnabledOverride = false,
            formattingStyleOverride = TextFormattingStyle.TECHNICAL
        )

        val resolved = AppVoiceProfilePolicy.resolve(profile.packageName, listOf(profile), global)

        assertEquals("whisper-large-v3-turbo", resolved.settings.speechModel)
        assertEquals(SpeechLanguageMode.CHINESE, resolved.settings.speechLanguageMode)
        assertFalse(resolved.settings.enabled)
        assertEquals(TextFormattingProviderId.GROQ, resolved.settings.provider)
        assertFalse(resolved.settings.contextualCorrectionEnabled)
        assertEquals(TerminalPeriodMode.ALWAYS, resolved.settings.terminalPeriodMode)
        assertEquals(TextFormattingStyle.TECHNICAL, resolved.formattingStyle)
        assertSame(profile, resolved.appliedProfile)
    }

    @Test
    fun inheritedValuesFollowGlobalUpdatesInsteadOfBeingCopied() {
        val profile = AppVoiceProfile(packageName = "com.example.notes", speechModelOverride = "whisper-large-v3")
        val firstGlobal = SmartFormattingSettings(speechLanguageMode = SpeechLanguageMode.AUTO, threshold = 30)
        val changedGlobal = firstGlobal.copy(speechLanguageMode = SpeechLanguageMode.MIXED, threshold = 90)

        val first = AppVoiceProfilePolicy.resolve(profile.packageName, listOf(profile), firstGlobal)
        val second = AppVoiceProfilePolicy.resolve(profile.packageName, listOf(profile), changedGlobal)

        assertEquals(SpeechLanguageMode.AUTO, first.settings.speechLanguageMode)
        assertEquals(SpeechLanguageMode.MIXED, second.settings.speechLanguageMode)
        assertEquals(30, first.settings.threshold)
        assertEquals(90, second.settings.threshold)
        assertEquals("whisper-large-v3", second.settings.speechModel)
    }

    @Test
    fun disabledAndDeletedProfilesFallBackToGlobalSettings() {
        val global = SmartFormattingSettings(enabled = false)
        val profile = AppVoiceProfile(
            packageName = "com.example.notes",
            enabled = true,
            smartFormattingEnabledOverride = true
        )
        val disabled = AppVoiceProfileRules.setEnabled(listOf(profile), profile.packageName, false, 10L)
        val deleted = AppVoiceProfileRules.delete(listOf(profile), profile.packageName)

        assertTrue(AppVoiceProfilePolicy.resolve(profile.packageName, disabled, global).appliedProfile == null)
        assertEquals(global, AppVoiceProfilePolicy.resolve(profile.packageName, disabled, global).settings)
        assertTrue(deleted.isEmpty())
        assertEquals(global, AppVoiceProfilePolicy.resolve(profile.packageName, deleted, global).settings)
    }

    @Test
    fun upsertKeepsOneProfilePerPackageAndOriginalCreationTime() {
        val first = AppVoiceProfile(
            packageName = "com.example.notes",
            createdAt = 10L,
            updatedAt = 20L,
            speechModelOverride = "whisper-large-v3"
        )
        val edited = AppVoiceProfile(
            packageName = "com.example.notes",
            createdAt = 99L,
            updatedAt = 30L,
            speechModelOverride = "whisper-large-v3-turbo"
        )

        val saved = AppVoiceProfileRules.upsert(listOf(first), edited)

        assertEquals(1, saved.size)
        assertEquals(10L, saved.single().createdAt)
        assertEquals(30L, saved.single().updatedAt)
        assertEquals("whisper-large-v3-turbo", saved.single().speechModelOverride)
    }

    @Test
    fun blankOrInvalidPackageUsesGlobalSettings() {
        val global = SmartFormattingSettings(speechLanguageMode = SpeechLanguageMode.ENGLISH)
        val profile = AppVoiceProfile(packageName = "com.example.notes", speechLanguageModeOverride = SpeechLanguageMode.CHINESE)

        assertNull(AppPackageName.normalize("  "))
        assertNull(AppPackageName.normalize("not a package"))
        assertEquals(global, AppVoiceProfilePolicy.resolve(null, listOf(profile), global).settings)
        assertEquals(global, AppVoiceProfilePolicy.resolve("invalid", listOf(profile), global).settings)
    }

    @Test
    fun sensitiveEditorCannotUseProfileOverridesOrContextualCorrection() {
        val global = SmartFormattingSettings(enabled = true, contextualCorrectionEnabled = true)
        val profile = AppVoiceProfile(
            packageName = "com.example.passwords",
            smartFormattingEnabledOverride = false,
            contextualCorrectionEnabledOverride = true,
            formattingStyleOverride = TextFormattingStyle.TECHNICAL
        )

        val resolved = AppVoiceProfilePolicy.resolve(
            profile.packageName,
            listOf(profile),
            global,
            sensitiveEditor = true
        )

        assertTrue(resolved.settings.enabled)
        assertFalse(resolved.settings.contextualCorrectionEnabled)
        assertEquals(TextFormattingStyle.DAILY, resolved.formattingStyle)
        assertNull(resolved.appliedProfile)
    }

    @Test
    fun oldProfileJsonAndNewOverridesRemainReadable() {
        val oldJson = """[{"packageName":"com.example.old","enabled":true,"createdAt":7,"updatedAt":8}]"""
        val oldProfile = AppVoiceProfileRepository.decode(oldJson).single()
        assertNull(oldProfile.speechModelOverride)
        assertNull(oldProfile.speechLanguageModeOverride)
        assertNull(oldProfile.smartFormattingEnabledOverride)
        assertNull(oldProfile.providerOverride)
        assertNull(oldProfile.contextualCorrectionEnabledOverride)
        assertNull(oldProfile.taiwanWordingEnabledOverride)
        assertNull(oldProfile.smartPunctuationEnabledOverride)
        assertNull(oldProfile.terminalPeriodModeOverride)
        assertNull(oldProfile.formattingStyleOverride)

        val unknownOverrides = AppVoiceProfileRepository.decode(
            """[{"packageName":"com.example.unknown","terminalPeriodMode":"future","speechLanguageMode":"future","provider":"openai"}]"""
        ).single()
        assertNull(unknownOverrides.terminalPeriodModeOverride)
        assertNull(unknownOverrides.speechLanguageModeOverride)
        assertNull(unknownOverrides.providerOverride)

        val current = oldProfile.copy(
            speechModelOverride = "whisper-large-v3-turbo",
            speechLanguageModeOverride = SpeechLanguageMode.MIXED,
            smartFormattingEnabledOverride = false,
            providerOverride = TextFormattingProviderId.GROQ,
            contextualCorrectionEnabledOverride = true,
            taiwanWordingEnabledOverride = false,
            smartPunctuationEnabledOverride = true,
            terminalPeriodModeOverride = TerminalPeriodMode.AUTO,
            formattingStyleOverride = TextFormattingStyle.FORMAL
        )
        val encoded = AppVoiceProfileRepository.encode(listOf(current))
        val decoded = AppVoiceProfileRepository.decode(encoded).single()

        assertEquals(current, decoded)
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("Bearer "))
    }

    @Test
    fun unsupportedProvidersCannotBeStoredInProfiles() {
        assertEquals(listOf(TextFormattingProviderId.GROQ), TextFormattingProviderRegistry.supportedProfileProviders())
        val profile = AppVoiceProfile(
            packageName = "com.example.notes",
            providerOverride = TextFormattingProviderId.OPENAI
        )

        val normalized = AppVoiceProfileRules.upsert(emptyList(), profile).single()

        assertNull(normalized.providerOverride)
        assertEquals(TextFormattingProviderId.GROQ, AppVoiceProfilePolicy.resolve(
            profile.packageName,
            listOf(profile),
            SmartFormattingSettings()
        ).settings.provider)
    }

    @Test
    fun profileCanDisableFormattingWithoutTriggeringAFormattingProvider() {
        val global = SmartFormattingSettings(enabled = true, threshold = 0)
        val profile = AppVoiceProfile(packageName = "com.example.notes", smartFormattingEnabledOverride = false)
        val effective = AppVoiceProfilePolicy.resolve(profile.packageName, listOf(profile), global).settings
        var formattingCallCount = 0

        when (SmartFormattingPolicy.decide("long enough to format", effective)) {
            is SmartFormattingDecision.Format -> formattingCallCount++
            is SmartFormattingDecision.CommitOriginal -> Unit
        }

        assertEquals(0, formattingCallCount)
    }

    @Test
    fun taiwanWordingAndSmartPunctuationOverridesInheritOrApplyPerApp() {
        val global = SmartFormattingSettings(taiwanWordingEnabled = true, smartPunctuationEnabled = true)
        val inheriting = AppVoiceProfile(packageName = "com.example.notes")
        val technical = AppVoiceProfile(
            packageName = "com.example.terminal",
            taiwanWordingEnabledOverride = false,
            smartPunctuationEnabledOverride = false
        )

        val inherited = AppVoiceProfilePolicy.resolve(inheriting.packageName, listOf(inheriting), global).settings
        assertTrue(inherited.taiwanWordingEnabled)
        assertTrue(inherited.smartPunctuationEnabled)

        val overridden = AppVoiceProfilePolicy.resolve(technical.packageName, listOf(technical), global).settings
        assertFalse(overridden.taiwanWordingEnabled)
        assertFalse(overridden.smartPunctuationEnabled)
        assertTrue(overridden.enabled)

        assertEquals(technical, AppVoiceProfileRepository.decode(AppVoiceProfileRepository.encode(listOf(technical))).single())
    }

    @Test
    fun sensitiveEditorCannotUseTaiwanWordingOrSmartPunctuation() {
        val global = SmartFormattingSettings(taiwanWordingEnabled = true, smartPunctuationEnabled = true)

        val resolved = AppVoiceProfilePolicy.resolve(
            "com.example.passwords",
            emptyList(),
            global,
            sensitiveEditor = true
        )

        assertFalse(resolved.settings.taiwanWordingEnabled)
        assertFalse(resolved.settings.smartPunctuationEnabled)
    }

    @Test
    fun staleAsyncResultIsRejectedAfterAppFieldOrConnectionSwitch() {
        val connection = Any()
        val expected = VoiceEditorTargetKey(1L, "com.example.first", 2, "body", 1, 0, connection)
        val sameEditor = expected.copy()

        assertTrue(VoiceEditorTargetPolicy.stillTargetsSameEditor(expected, sameEditor))
        assertFalse(VoiceEditorTargetPolicy.stillTargetsSameEditor(
            expected,
            sameEditor.copy(sessionId = 2L, packageName = "com.example.second")
        ))
        assertFalse(VoiceEditorTargetPolicy.stillTargetsSameEditor(expected, sameEditor.copy(inputType = 129)))
        assertFalse(VoiceEditorTargetPolicy.stillTargetsSameEditor(expected, sameEditor.copy(connectionIdentity = Any())))
    }
}
