package tw.kuies.voiceime

import java.nio.charset.StandardCharsets

internal object GlossaryPromptBuilder {
    const val MAX_TERMS = 30
    const val MAX_UNICODE_CODE_POINTS = 400
    const val MAX_PROMPT_UTF8_BYTES = 180
    private const val PREFIX = "專有名詞拼寫參考："

    fun build(entries: List<PersonalGlossaryTerm>, mcpTerms: List<String> = emptyList()): String? {
        val terms = entries.asSequence()
            .filter { it.enabled }
            .map { it.term.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { PersonalGlossaryRules.keyFor(it) }
            .plus(
                mcpTerms.asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { PersonalGlossaryRules.keyFor(it) }
            )
            .distinctBy { PersonalGlossaryRules.keyFor(it) }

        var prompt = PREFIX
        var includedCount = 0
        for (term in terms) {
            if (includedCount >= MAX_TERMS) break
            val candidate = if (includedCount == 0) "$PREFIX$term" else "$prompt, $term"
            val codePointCount = candidate.codePointCount(0, candidate.length)
            val utf8ByteCount = candidate.toByteArray(StandardCharsets.UTF_8).size
            if (codePointCount > MAX_UNICODE_CODE_POINTS || utf8ByteCount > MAX_PROMPT_UTF8_BYTES) {
                continue
            }
            prompt = candidate
            includedCount += 1
        }

        return prompt.takeIf { includedCount > 0 }
    }
}
