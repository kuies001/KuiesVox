package tw.kuies.voiceime

import android.content.Context
import java.io.IOException

internal object SmartFormattingSettingsRepository {
    private const val PREFERENCES_NAME = "smart_formatting_settings"
    private const val ENABLED_KEY = "smart_formatting_enabled"
    private const val THRESHOLD_KEY = "smart_formatting_threshold"
    private const val LEGACY_MODEL_KEY = "smart_formatting_model"
    private const val SPEECH_MODEL_KEY = "speech_model"
    private const val SPEECH_LANGUAGE_MODE_KEY = "speech_language_mode"
    private const val PROVIDER_KEY = "text_formatting_provider"
    private const val GROQ_MODEL_KEY = "groq_formatting_model"
    private const val GROQ_CUSTOM_MODEL_KEY = "groq_custom_model_id"
    private const val GEMINI_MODEL_KEY = "gemini_formatting_model"
    private const val GEMINI_CUSTOM_MODEL_KEY = "gemini_custom_model_id"
    private const val OPENAI_MODEL_KEY = "openai_formatting_model"
    private const val OPENAI_CUSTOM_MODEL_KEY = "openai_custom_model_id"
    private const val TERMINAL_PERIOD_MODE_KEY = "terminal_period_mode"
    private const val CONTEXTUAL_CORRECTION_ENABLED_KEY = "contextual_correction_enabled"

    fun loadAsync(context: Context, callback: (Result<SmartFormattingSettings>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ loadSync(appContext) }, callback)
    }

    fun saveAsync(
        context: Context,
        settings: SmartFormattingSettings,
        callback: (Result<Unit>) -> Unit
    ) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            saveSync(appContext, settings)
            Unit
        }, callback)
    }

    internal fun loadSync(context: Context): SmartFormattingSettings {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return normalize(
            SmartFormattingSettings(
                enabled = preferences.getBoolean(ENABLED_KEY, SmartFormattingSettings.DEFAULT_ENABLED),
                threshold = preferences.getInt(THRESHOLD_KEY, SmartFormattingSettings.DEFAULT_THRESHOLD),
                provider = TextFormattingProviderId.fromStoredValue(preferences.getString(PROVIDER_KEY, null)),
                speechModel = preferences.getString(
                    SPEECH_MODEL_KEY,
                    SmartFormattingSettings.DEFAULT_SPEECH_MODEL
                ).orEmpty(),
                speechLanguageMode = SpeechLanguageMode.fromStoredValues(
                    preferences.getString(SPEECH_LANGUAGE_MODE_KEY, null),
                    preferences.getString(SpeechLanguageMode.LEGACY_PREFERENCE_KEY, null)
                ),
                groqFormattingModel = preferences.getString(
                    GROQ_MODEL_KEY,
                    preferences.getString(LEGACY_MODEL_KEY, SmartFormattingSettings.DEFAULT_GROQ_MODEL)
                ).orEmpty(),
                groqCustomModelId = preferences.getString(GROQ_CUSTOM_MODEL_KEY, "").orEmpty(),
                geminiFormattingModel = preferences.getString(
                    GEMINI_MODEL_KEY,
                    SmartFormattingSettings.DEFAULT_GEMINI_MODEL
                ).orEmpty(),
                geminiCustomModelId = preferences.getString(GEMINI_CUSTOM_MODEL_KEY, "").orEmpty(),
                openAiFormattingModel = preferences.getString(
                    OPENAI_MODEL_KEY,
                    SmartFormattingSettings.DEFAULT_OPENAI_MODEL
                ).orEmpty(),
                openAiCustomModelId = preferences.getString(OPENAI_CUSTOM_MODEL_KEY, "").orEmpty(),
                terminalPeriodMode = TerminalPeriodMode.fromStoredValue(
                    preferences.getString(TERMINAL_PERIOD_MODE_KEY, null)
                ),
                contextualCorrectionEnabled = preferences.getBoolean(
                    CONTEXTUAL_CORRECTION_ENABLED_KEY,
                    SmartFormattingSettings.DEFAULT_CONTEXTUAL_CORRECTION_ENABLED
                )
            )
        )
    }

    internal fun saveSync(context: Context, settings: SmartFormattingSettings) {
        val normalized = normalize(settings)
        val saved = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, normalized.enabled)
            .putInt(THRESHOLD_KEY, normalized.threshold)
            .putString(SPEECH_MODEL_KEY, normalized.speechModel)
            .putString(SPEECH_LANGUAGE_MODE_KEY, normalized.speechLanguageMode.storageValue)
            .putString(PROVIDER_KEY, normalized.provider.name.lowercase())
            .putString(GROQ_MODEL_KEY, normalized.groqFormattingModel)
            .putString(LEGACY_MODEL_KEY, normalized.groqFormattingModel)
            .putString(GROQ_CUSTOM_MODEL_KEY, normalized.groqCustomModelId)
            .putString(GEMINI_MODEL_KEY, normalized.geminiFormattingModel)
            .putString(GEMINI_CUSTOM_MODEL_KEY, normalized.geminiCustomModelId)
            .putString(OPENAI_MODEL_KEY, normalized.openAiFormattingModel)
            .putString(OPENAI_CUSTOM_MODEL_KEY, normalized.openAiCustomModelId)
            .putString(TERMINAL_PERIOD_MODE_KEY, normalized.terminalPeriodMode.storageValue)
            .putBoolean(CONTEXTUAL_CORRECTION_ENABLED_KEY, normalized.contextualCorrectionEnabled)
            .commit()
        if (!saved) throw IOException("Smart formatting settings could not be persisted")
    }

    internal fun normalize(settings: SmartFormattingSettings): SmartFormattingSettings {
        val (groqModel, groqCustomModel) = normalizeModel(
            settings.groqFormattingModel,
            settings.groqCustomModelId,
            TextFormattingProviderId.GROQ
        )
        val (geminiModel, geminiCustomModel) = normalizeModel(
            settings.geminiFormattingModel,
            settings.geminiCustomModelId,
            TextFormattingProviderId.GEMINI
        )
        val (openAiModel, openAiCustomModel) = normalizeModel(
            settings.openAiFormattingModel,
            settings.openAiCustomModelId,
            TextFormattingProviderId.OPENAI
        )
        return settings.copy(
            threshold = settings.threshold.coerceIn(
                SmartFormattingSettings.MIN_THRESHOLD,
                SmartFormattingSettings.MAX_THRESHOLD
            ),
            speechModel = settings.speechModel.takeIf { model ->
                FormattingModels.speech.any { option -> option.id == model }
            } ?: SmartFormattingSettings.DEFAULT_SPEECH_MODEL,
            groqFormattingModel = groqModel,
            groqCustomModelId = groqCustomModel,
            geminiFormattingModel = geminiModel,
            geminiCustomModelId = geminiCustomModel,
            openAiFormattingModel = openAiModel,
            openAiCustomModelId = openAiCustomModel,
            terminalPeriodMode = settings.terminalPeriodMode
        )
    }

    private fun normalizeModel(
        value: String,
        customModelId: String,
        provider: TextFormattingProviderId
    ): Pair<String, String> {
        val model = value.trim()
        val custom = customModelId.trim()
        if (model == FormattingModels.CUSTOM_MODEL_ID) {
            return model to custom
        }
        if (model.isBlank()) {
            return FormattingModels.default(provider) to custom
        }
        if (FormattingModels.options(provider).any { it.id == model }) {
            return model to custom
        }
        // The previous settings UI stored custom IDs directly in the model field.
        return FormattingModels.CUSTOM_MODEL_ID to model
    }
}
