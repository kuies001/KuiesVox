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

internal data class TextCorrectionPlan(val matchedOccurrences: Map<String, Int>)

internal data class TextCorrectionApplication(
    val text: String,
    val plan: TextCorrectionPlan
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

    fun apply(text: String, rules: List<TextCorrectionRule>): TextCorrectionApplication {
        val activeRules = orderedRules(rules)
        if (activeRules.isEmpty()) return TextCorrectionApplication(text, TextCorrectionPlan(emptyMap()))

        val matchedOccurrences = mutableMapOf<String, Int>()
        val result = replaceInOnePass(text, activeRules) { rule, original ->
            matchedOccurrences[rule.id] = (matchedOccurrences[rule.id] ?: 0) + 1
            rule.replacementText
        }
        return TextCorrectionApplication(result, TextCorrectionPlan(matchedOccurrences))
    }

    fun applyPlan(text: String, rules: List<TextCorrectionRule>, plan: TextCorrectionPlan): String {
        val remaining = plan.matchedOccurrences.filterValues { it > 0 }.toMutableMap()
        val activeRules = orderedRules(rules).filter { it.id in remaining }
        if (activeRules.isEmpty()) return text
        return replaceInOnePass(text, activeRules) { rule, original ->
            val count = remaining[rule.id] ?: 0
            if (count <= 0) original else {
                remaining[rule.id] = count - 1
                rule.replacementText
            }
        }
    }

    private fun orderedRules(rules: List<TextCorrectionRule>): List<TextCorrectionRule> =
        rules.withIndex()
            .filter { (_, rule) -> rule.enabled && rule.sourceText.trim().isNotEmpty() }
            .sortedWith(compareByDescending<IndexedValue<TextCorrectionRule>> { it.value.sourceText.trim().length }
                .thenBy { it.index })
            .map { it.value.copy(sourceText = it.value.sourceText.trim()) }

    private fun replaceInOnePass(
        text: String,
        rules: List<TextCorrectionRule>,
        replacement: (TextCorrectionRule, String) -> String
    ): String {
        val pattern = Regex(rules.joinToString("|") { Regex.escape(it.sourceText) }, RegexOption.IGNORE_CASE)
        return pattern.replace(text) { match ->
            val rule = rules.firstOrNull { it.sourceText.equals(match.value, ignoreCase = true) }
                ?: return@replace match.value
            replacement(rule, match.value)
        }
    }
}
