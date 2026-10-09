package tw.kuies.voiceime

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextFormattingSettingsRepositoryTest {
    @Test
    fun speechModelAndProviderModelsPersistAcrossReloads() {
        val context = isolatedPreferencesContext()
        val preferences = context.getSharedPreferences("smart_formatting_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            SmartFormattingSettingsRepository.saveSync(
                context,
                SmartFormattingSettings(
                    speechModel = "whisper-large-v3",
                    provider = TextFormattingProviderId.GEMINI,
                    groqFormattingModel = FormattingModels.CUSTOM_MODEL_ID,
                    groqCustomModelId = "groq/custom",
                    terminalPeriodMode = TerminalPeriodMode.NEVER,
                    geminiFormattingModel = FormattingModels.CUSTOM_MODEL_ID,
                    geminiCustomModelId = "gemini/custom",
                    openAiFormattingModel = FormattingModels.CUSTOM_MODEL_ID,
                    openAiCustomModelId = "openai/custom"
                )
            )

            val reloaded = SmartFormattingSettingsRepository.loadSync(context)

            assertEquals("whisper-large-v3", reloaded.speechModel)
            assertEquals(TextFormattingProviderId.GEMINI, reloaded.provider)
            assertEquals("groq/custom", reloaded.modelFor(TextFormattingProviderId.GROQ))
            assertEquals("gemini/custom", reloaded.modelFor(TextFormattingProviderId.GEMINI))
            assertEquals("openai/custom", reloaded.modelFor(TextFormattingProviderId.OPENAI))
            assertEquals(TerminalPeriodMode.NEVER, reloaded.terminalPeriodMode)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun defaultProviderModelsArePersistedIndependently() {
        val context = isolatedPreferencesContext()
        val preferences = context.getSharedPreferences("smart_formatting_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            SmartFormattingSettingsRepository.saveSync(context, SmartFormattingSettings())
            val reloaded = SmartFormattingSettingsRepository.loadSync(context)

            assertEquals("qwen/qwen3.8-27b", reloaded.groqFormattingModel)
            assertEquals("gemini-3.5-flash-lite", reloaded.geminiFormattingModel)
            assertEquals("gpt-5.6-luna", reloaded.openAiFormattingModel)
            assertEquals(TerminalPeriodMode.AUTO, reloaded.terminalPeriodMode)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun newContextSettingDefaultsOnAndDoesNotOverwriteExistingFormattingToggle() {
        val context = isolatedPreferencesContext()
        val preferences = context.getSharedPreferences("smart_formatting_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().putBoolean("smart_formatting_enabled", false).commit()
        try {
            val legacy = SmartFormattingSettingsRepository.loadSync(context)
            assertFalse(legacy.enabled)
            assertTrue(legacy.contextualCorrectionEnabled)

            SmartFormattingSettingsRepository.saveSync(
                context,
                legacy.copy(contextualCorrectionEnabled = false)
            )
            val reloaded = SmartFormattingSettingsRepository.loadSync(context)

            assertFalse(reloaded.enabled)
            assertFalse(reloaded.contextualCorrectionEnabled)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun providerApiKeysAreIndependentAndClearingOneKeepsTheOther() {
        val context = isolatedPreferencesContext()
        val preferences = context.getSharedPreferences("text_formatting_api_keys", Context.MODE_PRIVATE)
        val secureStorage = SecureCredentialStore(context)
        preferences.edit().clear().commit()
        secureStorage.removeSecret(SecureCredentialStore.GEMINI_API_KEY)
        secureStorage.removeSecret(SecureCredentialStore.OPENAI_API_KEY)
        try {
            TextFormattingApiKeyStore.save(context, ExternalFormattingProvider.GEMINI, "gemini-test-value")
            TextFormattingApiKeyStore.save(context, ExternalFormattingProvider.OPENAI, "openai-test-value")
            assertEquals("gemini-test-value", TextFormattingApiKeyStore.read(context, ExternalFormattingProvider.GEMINI))
            assertEquals("openai-test-value", TextFormattingApiKeyStore.read(context, ExternalFormattingProvider.OPENAI))

            TextFormattingApiKeyStore.save(context, ExternalFormattingProvider.GEMINI, "")

            assertNull(TextFormattingApiKeyStore.read(context, ExternalFormattingProvider.GEMINI))
            assertEquals("openai-test-value", TextFormattingApiKeyStore.read(context, ExternalFormattingProvider.OPENAI))
            assertFalse(preferences.contains("openai_api_key"))
            assertEquals("openai-test-value", secureStorage.readSecret(SecureCredentialStore.OPENAI_API_KEY))
            assertNull(secureStorage.readSecret(SecureCredentialStore.GEMINI_API_KEY))
        } finally {
            preferences.edit().clear().commit()
            secureStorage.removeSecret(SecureCredentialStore.GEMINI_API_KEY)
            secureStorage.removeSecret(SecureCredentialStore.OPENAI_API_KEY)
        }
    }

    private fun isolatedPreferencesContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
}
