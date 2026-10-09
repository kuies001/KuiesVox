package tw.kuies.voiceime

internal enum class TextFormattingProviderId(val displayName: String) {
    GROQ("Groq"),
    GEMINI("Google Gemini"),
    OPENAI("OpenAI");

    companion object {
        fun fromStoredValue(value: String?): TextFormattingProviderId =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GROQ
    }
}

internal enum class SpeechLanguageMode(
    val displayName: String,
    val description: String,
    val groqLanguageCode: String?,
    val storageValue: String
) {
    AUTO("自動偵測", "由 Whisper 自動判斷語音語言。", null, "auto"),
    CHINESE("中文", "以中文作為辨識語言。", "zh", "zh"),
    ENGLISH("英文", "以英文作為辨識語言。", "en", "en"),
    MIXED(
        "中英混合",
        "適合中文句子中包含英文品牌、技術詞或專有名詞。",
        null,
        "mixed"
    );

    companion object {
        const val LEGACY_PREFERENCE_KEY = "speech_language"

        fun fromStoredValue(value: String?): SpeechLanguageMode? =
            when (value?.trim()?.lowercase()) {
                "auto", "auto_detect", "auto-detect" -> AUTO
                "zh", "zh-cn", "chinese" -> CHINESE
                "en", "en-us", "english" -> ENGLISH
                "mixed", "zh-en", "zh_en", "bilingual" -> MIXED
                else -> null
            }

        fun fromStoredValues(currentValue: String?, legacyValue: String?): SpeechLanguageMode =
            fromStoredValue(currentValue) ?: fromStoredValue(legacyValue) ?: MIXED
    }
}

internal data class FormattingModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String? = null
)

internal object FormattingModels {
    const val CUSTOM_MODEL_ID = "__custom__"

    val speech = listOf(
        FormattingModelOption(
            "whisper-large-v3",
            "Whisper Large V3",
            "準確率較高，適合專有名詞、較複雜音訊或重視辨識品質的情境；速度稍慢、成本較高。"
        ),
        FormattingModelOption(
            "whisper-large-v3-turbo",
            "Whisper Large V3 Turbo",
            "速度更快、成本較低，適合日常語音輸入；準確率略低於 Large V3。"
        )
    )

    val groq = listOf(
        FormattingModelOption(
            "qwen/qwen3.8-27b",
            "Qwen 3.8 27B",
            "推薦。中文理解與指令遵循能力佳，速度快，適合逐字稿校對、段落整理與列點排版。",
            "推薦"
        ),
        FormattingModelOption(
            "qwen/qwen3.6-27b",
            "Qwen 3.6 27B",
            "舊版模型，部分 Groq 帳號可能已停止提供；僅保留相容選項。",
            "舊版"
        ),
        FormattingModelOption(
            "openai/gpt-oss-20b",
            "GPT-OSS 20B",
            "較輕量、速度快，適合基本文字校對與格式整理。"
        )
    )

    val gemini = listOf(
        FormattingModelOption(
            "gemini-3.5-flash-lite",
            "Gemini 3.5 Flash-Lite",
            "推薦。速度快、成本低，適合大量逐字稿校對與排版。",
            "推薦"
        ),
        FormattingModelOption(
            "gemini-3.6-flash",
            "Gemini 3.6 Flash",
            "品質與速度較平衡，適合較複雜的長文字整理。"
        ),
        FormattingModelOption(
            "gemini-3.8-flash",
            "Gemini 3.8 Flash",
            "能力較強，適合較複雜的語意整理，但通常不是最低成本選擇。"
        )
    )

    val openAi = listOf(
        FormattingModelOption(
            "gpt-5.6-luna",
            "GPT-5.6 Luna",
            "推薦。速度快、成本低，適合高頻文字校對與排版。",
            "推薦"
        ),
        FormattingModelOption(
            "gpt-5.6-terra",
            "GPT-5.6 Terra",
            "文字理解能力更強，適合較複雜的長篇逐字稿。"
        ),
        FormattingModelOption(
            "gpt-5.4-mini",
            "GPT-5.4 Mini",
            "較成熟的中小型模型，可作為相容或替代選項。"
        )
    )

    fun options(provider: TextFormattingProviderId): List<FormattingModelOption> = when (provider) {
        TextFormattingProviderId.GROQ -> groq
        TextFormattingProviderId.GEMINI -> gemini
        TextFormattingProviderId.OPENAI -> openAi
    }

    fun default(provider: TextFormattingProviderId): String = when (provider) {
        TextFormattingProviderId.GROQ -> "qwen/qwen3.8-27b"
        TextFormattingProviderId.GEMINI -> "gemini-3.5-flash-lite"
        TextFormattingProviderId.OPENAI -> "gpt-5.6-luna"
    }
}

internal data class SmartFormattingSettings(
    val enabled: Boolean = DEFAULT_ENABLED,
    val threshold: Int = DEFAULT_THRESHOLD,
    val provider: TextFormattingProviderId = TextFormattingProviderId.GROQ,
    val speechModel: String = DEFAULT_SPEECH_MODEL,
    val speechLanguageMode: SpeechLanguageMode = SpeechLanguageMode.MIXED,
    val groqFormattingModel: String = DEFAULT_GROQ_MODEL,
    val groqCustomModelId: String = "",
    val geminiFormattingModel: String = DEFAULT_GEMINI_MODEL,
    val geminiCustomModelId: String = "",
    val openAiFormattingModel: String = DEFAULT_OPENAI_MODEL,
    val openAiCustomModelId: String = "",
    val terminalPeriodMode: TerminalPeriodMode = TerminalPeriodMode.AUTO,
    val contextualCorrectionEnabled: Boolean = DEFAULT_CONTEXTUAL_CORRECTION_ENABLED,
    val formattingStyle: TextFormattingStyle = TextFormattingStyle.DAILY
) {
    val model: String
        get() = modelFor(provider)

    fun modelFor(selectedProvider: TextFormattingProviderId): String = when (selectedProvider) {
        TextFormattingProviderId.GROQ -> selectedModel(groqFormattingModel, groqCustomModelId, selectedProvider)
        TextFormattingProviderId.GEMINI -> selectedModel(geminiFormattingModel, geminiCustomModelId, selectedProvider)
        TextFormattingProviderId.OPENAI -> selectedModel(openAiFormattingModel, openAiCustomModelId, selectedProvider)
    }

    private fun selectedModel(model: String, customModel: String, selectedProvider: TextFormattingProviderId) =
        if (model == FormattingModels.CUSTOM_MODEL_ID) {
            customModel.trim()
        } else {
            model.trim().ifBlank { FormattingModels.default(selectedProvider) }
        }

    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_THRESHOLD = 40
        const val MIN_THRESHOLD = 0
        const val MAX_THRESHOLD = 500
        const val DEFAULT_CONTEXTUAL_CORRECTION_ENABLED = true
        const val DEFAULT_SPEECH_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_GROQ_MODEL = "qwen/qwen3.8-27b"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_OPENAI_MODEL = "gpt-5.6-luna"
    }
}

internal sealed interface SmartFormattingDecision {
    data class CommitOriginal(val text: String) : SmartFormattingDecision
    data class Format(
        val text: String,
        val model: String,
        val provider: TextFormattingProviderId = TextFormattingProviderId.GROQ
    ) : SmartFormattingDecision
}

internal data class SmartFormattingResolution(
    val text: String,
    val usedFallback: Boolean
)

internal object SmartFormattingPolicy {
    fun decide(text: String, settings: SmartFormattingSettings): SmartFormattingDecision {
        val trimmedText = text.trim()
        val codePointLength = trimmedText.codePointCount(0, trimmedText.length)
        val threshold = settings.threshold.coerceIn(
            SmartFormattingSettings.MIN_THRESHOLD,
            SmartFormattingSettings.MAX_THRESHOLD
        )
        if (!settings.enabled || trimmedText.isEmpty() || codePointLength <= threshold) {
            return SmartFormattingDecision.CommitOriginal(text)
        }

        return SmartFormattingDecision.Format(
            text = text,
            model = settings.modelFor(settings.provider),
            provider = settings.provider
        )
    }

    fun resolve(originalText: String, formattedText: String?): SmartFormattingResolution =
        if (formattedText.isNullOrBlank()) {
            SmartFormattingResolution(originalText, usedFallback = true)
        } else {
            SmartFormattingResolution(formattedText, usedFallback = false)
        }
}

internal object ContextualCorrectionPolicy {
    fun shouldUse(
        enabled: Boolean,
        smartFormattingEnabled: Boolean,
        providerAvailable: Boolean,
        apiKeyAvailable: Boolean,
        sensitiveEditor: Boolean
    ): Boolean = enabled && smartFormattingEnabled && providerAvailable && apiKeyAvailable && !sensitiveEditor
}
