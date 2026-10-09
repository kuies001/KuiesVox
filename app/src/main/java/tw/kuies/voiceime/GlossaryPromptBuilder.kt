package tw.kuies.voiceime

import java.nio.charset.StandardCharsets

internal object GlossaryPromptBuilder {
    const val MAX_TERMS = 30
    const val MAX_UNICODE_CODE_POINTS = 160
    // Groq documents a 224-token Whisper prompt limit. The tokenizer is not bundled,
    // so this byte ceiling leaves room for tokenizer overhead and is not an exact token count.
    const val MAX_PROMPT_UTF8_BYTES = 160
    private const val MAX_TERM_CODE_POINTS = 38
    private const val MAX_TERM_UTF8_BYTES = 48
    private const val MAX_PHRASE_CODE_POINTS = 42
    private const val MAX_PHRASE_UTF8_BYTES = 24
    private const val MAX_CONTEXT_PHRASES_PER_TERM = 2
    private const val PREFIX = "專有名詞與語境參考："
    private const val MIXED_LANGUAGE_INSTRUCTION =
        "保留中英口語切換；英文技術詞與品牌維持原拼法，不翻譯。"
    private const val ENGLISH_PREFIX = "Recognition and spelling references: "

    fun build(
        entries: List<PersonalGlossaryTerm>,
        mcpTerms: List<String> = emptyList(),
        languageMode: SpeechLanguageMode = SpeechLanguageMode.AUTO
    ): String? {
        val mixedLanguage = languageMode == SpeechLanguageMode.MIXED
        val prefix = when (languageMode) {
            SpeechLanguageMode.ENGLISH -> ENGLISH_PREFIX
            SpeechLanguageMode.MIXED -> "$MIXED_LANGUAGE_INSTRUCTION\n$PREFIX"
            else -> PREFIX
        }
        val items = mutableListOf<String>()
        var includedTerms = 0

        val localTerms = entries.asSequence()
            .filter { it.enabled }
            .map { it.copy(term = it.term.trim()) }
            .filter { it.term.isNotEmpty() }
            .distinctBy { PersonalGlossaryRules.keyFor(it.term) }
            .sortedByDescending { it.commonPhrases.isNotEmpty() }
            .toList()

        for (entry in localTerms) {
            if (includedTerms >= MAX_TERMS) break
            val term = truncate(entry.term, MAX_TERM_CODE_POINTS, MAX_TERM_UTF8_BYTES)
            items += term
            if (!fits(render(prefix, items))) {
                items.removeAt(items.lastIndex)
                continue
            }
            includedTerms++

            val phrases = entry.commonPhrases.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(PersonalGlossaryRules::keyFor)
                .take(MAX_CONTEXT_PHRASES_PER_TERM)
            var contextSuffix = ""
            for (phrase in phrases) {
                val boundedPhrase = truncate(phrase, MAX_PHRASE_CODE_POINTS, MAX_PHRASE_UTF8_BYTES)
                val candidateItem = if (contextSuffix.isEmpty()) {
                    "$term（語境：$boundedPhrase）"
                } else {
                    "${items.last().dropLast(1)}；$boundedPhrase）"
                }
                val previousItem = items[items.lastIndex]
                items[items.lastIndex] = candidateItem
                if (fits(render(prefix, items))) {
                    contextSuffix = candidateItem.removePrefix(term)
                } else {
                    items[items.lastIndex] = previousItem
                }
            }
        }

        val seen = localTerms.mapTo(mutableSetOf()) { PersonalGlossaryRules.keyFor(it.term) }
        for (rawTerm in mcpTerms) {
            if (includedTerms >= MAX_TERMS) break
            val term = rawTerm.trim()
            if (term.isEmpty() || !seen.add(PersonalGlossaryRules.keyFor(term))) continue
            items += truncate(term, MAX_TERM_CODE_POINTS, MAX_TERM_UTF8_BYTES)
            if (!fits(render(prefix, items))) {
                items.removeAt(items.lastIndex)
                continue
            }
            includedTerms++
        }

        return when {
            includedTerms > 0 -> render(prefix, items)
            mixedLanguage -> MIXED_LANGUAGE_INSTRUCTION
            languageMode == SpeechLanguageMode.ENGLISH -> null
            else -> null
        }
    }

    private fun render(prefix: String, items: List<String>): String =
        if (items.isEmpty()) prefix else "$prefix${items.joinToString(", ")}"

    private fun fits(prompt: String): Boolean =
        prompt.codePointCount(0, prompt.length) <= MAX_UNICODE_CODE_POINTS &&
            prompt.toByteArray(StandardCharsets.UTF_8).size <= MAX_PROMPT_UTF8_BYTES

    private fun truncate(value: String, maximumCodePoints: Int, maximumUtf8Bytes: Int): String {
        val result = StringBuilder()
        var index = 0
        var codePoints = 0
        var utf8Bytes = 0
        while (index < value.length && codePoints < maximumCodePoints) {
            val codePoint = value.codePointAt(index)
            val chars = String(Character.toChars(codePoint))
            val bytes = chars.toByteArray(StandardCharsets.UTF_8).size
            val nextIndex = index + Character.charCount(codePoint)
            val needsEllipsis = nextIndex < value.length
            val ellipsisBytes = if (needsEllipsis) "…".toByteArray(StandardCharsets.UTF_8).size else 0
            if (utf8Bytes + bytes + ellipsisBytes > maximumUtf8Bytes) break
            result.append(chars)
            utf8Bytes += bytes
            index = nextIndex
            codePoints++
        }
        val truncated = index < value.length
        if (truncated && utf8Bytes + "…".toByteArray(StandardCharsets.UTF_8).size <= maximumUtf8Bytes) {
            result.append('…')
        }
        return result.toString()
    }
}
