package tw.kuies.voiceime

import java.nio.charset.StandardCharsets

internal object GlossaryPromptBuilder {
    const val MAX_TERMS = 30
    const val MAX_UNICODE_CODE_POINTS = 400
    const val MAX_PROMPT_UTF8_BYTES = 180
    private const val PREFIX = "專有名詞拼寫參考："
    private const val MIXED_LANGUAGE_INSTRUCTION =
        "保留中英口語切換；英文技術詞與品牌維持原拼法，不翻譯。"

    fun build(
        entries: List<PersonalGlossaryTerm>,
        mcpTerms: List<String> = emptyList(),
        languageMode: SpeechLanguageMode = SpeechLanguageMode.AUTO
    ): String? {
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

        val mixedLanguage = languageMode == SpeechLanguageMode.MIXED
        val prefix = if (mixedLanguage) "$MIXED_LANGUAGE_INSTRUCTION\n$PREFIX" else PREFIX
        var prompt = prefix
        var includedCount = 0
        for (term in terms) {
            if (includedCount >= MAX_TERMS) break
            val candidate = if (includedCount == 0) "$prefix$term" else "$prompt, $term"
            val codePointCount = candidate.codePointCount(0, candidate.length)
            val utf8ByteCount = candidate.toByteArray(StandardCharsets.UTF_8).size
            if (codePointCount > MAX_UNICODE_CODE_POINTS || utf8ByteCount > MAX_PROMPT_UTF8_BYTES) {
                continue
            }
            prompt = candidate
            includedCount += 1
        }

        return when {
            includedCount > 0 -> prompt
            mixedLanguage -> MIXED_LANGUAGE_INSTRUCTION
            else -> null
        }
    }
}
