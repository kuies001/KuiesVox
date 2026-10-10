package tw.kuies.voiceime

internal object TextPostProcessor {
    fun process(text: String, rules: List<TextCorrectionRule>): String =
        processWithPlan(text, rules).text

    /**
     * [pronounPreferences] 沒有內建值：未明確傳入時不做任何代詞替換。
     */
    fun processWithPlan(
        text: String,
        rules: List<TextCorrectionRule>,
        pronounPreferences: List<PronounPreference> = emptyList()
    ): TextPostProcessingResult {
        val correction = TextCorrectionRules.apply(text, rules)
        val postProcessed = PronounPreferenceProcessor.apply(correction.text, pronounPreferences)
        return TextPostProcessingResult(postProcessed, correction.plan)
    }

    fun enforceCorrectionPlan(
        formattedText: String,
        rules: List<TextCorrectionRule>,
        plan: TextCorrectionPlan
    ): String = TextCorrectionRules.applyPlan(formattedText, rules, plan)
}

internal data class TextPostProcessingResult(
    val text: String,
    val correctionPlan: TextCorrectionPlan
)
