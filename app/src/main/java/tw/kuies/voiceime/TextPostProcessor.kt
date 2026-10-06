package tw.kuies.voiceime

internal object TextPostProcessor {
    fun process(text: String, rules: List<TextCorrectionRule>): String {
        var correctedText = text
        rules.asSequence()
            .filter { it.enabled }
            .sortedByDescending { it.sourceText.trim().length }
            .forEach { rule ->
                try {
                    val source = rule.sourceText.trim()
                    if (source.isNotEmpty()) {
                        correctedText = correctedText.replace(
                            oldValue = source,
                            newValue = rule.replacementText,
                            ignoreCase = true
                        )
                    }
                } catch (_: Exception) {
                    Unit
                }
            }
        return PronounPreferenceProcessor.apply(correctedText, DefaultPronounPreferences.rules)
    }
}
