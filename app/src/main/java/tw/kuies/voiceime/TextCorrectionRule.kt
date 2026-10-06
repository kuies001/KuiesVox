package tw.kuies.voiceime

import java.util.Locale
import java.util.UUID

internal data class TextCorrectionRule(
    val id: String,
    val sourceText: String,
    val replacementText: String,
    val enabled: Boolean
)

internal data class TextCorrectionRuleAddResult(
    val rules: List<TextCorrectionRule>,
    val addedCount: Int
)

internal object TextCorrectionRules {
    fun add(
        rules: List<TextCorrectionRule>,
        candidates: List<Pair<String, String>>
    ): TextCorrectionRuleAddResult {
        val updatedRules = rules.toMutableList()
        val knownSources = rules.mapTo(mutableSetOf()) { keyFor(it.sourceText) }
        var addedCount = 0

        candidates.forEach { (rawSource, rawReplacement) ->
            val source = rawSource.trim()
            val replacement = rawReplacement.trim()
            if (source.isEmpty() || !knownSources.add(keyFor(source))) return@forEach

            updatedRules += TextCorrectionRule(
                id = UUID.randomUUID().toString(),
                sourceText = source,
                replacementText = replacement,
                enabled = true
            )
            addedCount++
        }

        return TextCorrectionRuleAddResult(updatedRules, addedCount)
    }

    fun parseBatch(text: String): List<Pair<String, String>> = text.lineSequence()
        .mapNotNull { line ->
            val separatorIndex = line.indexOf("=>")
            if (separatorIndex < 0) return@mapNotNull null

            val source = line.substring(0, separatorIndex).trim()
            val replacement = line.substring(separatorIndex + 2).trim()
            if (source.isEmpty()) null else source to replacement
        }
        .toList()

    fun setEnabled(
        rules: List<TextCorrectionRule>,
        id: String,
        enabled: Boolean
    ): List<TextCorrectionRule> = rules.map { rule ->
        if (rule.id == id) rule.copy(enabled = enabled) else rule
    }

    fun delete(rules: List<TextCorrectionRule>, id: String): List<TextCorrectionRule> =
        rules.filterNot { it.id == id }

    fun keyFor(sourceText: String): String = sourceText.trim().lowercase(Locale.ROOT)
}
