package tw.kuies.voiceime

internal object TextPostProcessor {
    fun process(text: String, rules: List<TextCorrectionRule>): String =
        processWithPlan(text, rules).text

    fun processWithPlan(text: String, rules: List<TextCorrectionRule>): TextPostProcessingResult {
        val correction = TextCorrectionRules.apply(text, rules)
        val postProcessed = PronounPreferenceProcessor.apply(
            correction.text,
            DefaultPronounPreferences.rules
        )
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
