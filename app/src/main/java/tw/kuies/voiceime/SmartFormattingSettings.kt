package tw.kuies.voiceime

internal data class SmartFormattingSettings(
    val enabled: Boolean = DEFAULT_ENABLED,
    val threshold: Int = DEFAULT_THRESHOLD,
    val model: String = DEFAULT_MODEL
) {
    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_THRESHOLD = 40
        const val DEFAULT_MODEL = "qwen/qwen3.8-27b"
        const val MIN_THRESHOLD = 0
        const val MAX_THRESHOLD = 500
    }
}

internal sealed interface SmartFormattingDecision {
    data class CommitOriginal(val text: String) : SmartFormattingDecision
    data class Format(val text: String, val model: String) : SmartFormattingDecision
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

        val model = settings.model.trim().ifBlank { SmartFormattingSettings.DEFAULT_MODEL }
        return SmartFormattingDecision.Format(text, model)
    }

    fun resolve(originalText: String, formattedText: String?): SmartFormattingResolution =
        if (formattedText.isNullOrBlank()) {
            SmartFormattingResolution(originalText, usedFallback = true)
        } else {
            SmartFormattingResolution(formattedText, usedFallback = false)
        }
}
