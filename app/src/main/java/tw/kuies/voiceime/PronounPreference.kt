package tw.kuies.voiceime

/**
 * 代詞偏好：同一句的主語出現後，把指定代詞換成偏好的寫法。
 *
 * 這裡刻意只有機制、沒有任何內建規則：呼叫端必須明確傳入要套用的偏好，因此不會有
 * 開發者私人稱呼被打包進 APK，也不會自動套用到其他使用者的文字。
 */
internal data class PronounPreference(
    val subject: String,
    val pronoun: String,
    val preferredPronoun: String,
    val maximumDistanceCodePoints: Int
)

internal object PronounPreferenceProcessor {
    private val sentenceBoundaries = setOf('\n', '\r', '。', '！', '？', '!', '?', '.', '；', ';')

    fun apply(text: String, preferences: List<PronounPreference>): String {
        if (text.isEmpty() || preferences.isEmpty()) return text
        val replacements = mutableMapOf<Int, Pair<Int, String>>()

        preferences.forEach { preference ->
            if (preference.subject.isEmpty() || preference.pronoun.isEmpty()) return@forEach
            var searchFrom = 0
            while (searchFrom < text.length) {
                val subjectStart = text.indexOf(preference.subject, searchFrom)
                if (subjectStart < 0) break
                searchFrom = subjectStart + preference.subject.length
                val subjectEnd = subjectStart + preference.subject.length
                var clauseStart = subjectStart
                while (clauseStart > 0 && text[clauseStart - 1] !in sentenceBoundaries) clauseStart -= 1
                var clauseEnd = subjectEnd
                while (clauseEnd < text.length && text[clauseEnd] !in sentenceBoundaries) clauseEnd += 1

                var pronounStart = text.indexOf(preference.pronoun, clauseStart)
                while (pronounStart >= 0 && pronounStart + preference.pronoun.length <= clauseEnd) {
                    val pronounEnd = pronounStart + preference.pronoun.length
                    val gapStart = minOf(subjectEnd, pronounEnd)
                    val gapEnd = maxOf(subjectStart, pronounStart)
                    val distance = text.codePointCount(gapStart, gapEnd)
                    if (distance <= preference.maximumDistanceCodePoints) {
                        replacements.putIfAbsent(
                            pronounStart,
                            preference.pronoun.length to preference.preferredPronoun
                        )
                    }
                    pronounStart = text.indexOf(preference.pronoun, pronounEnd)
                }
            }
        }

        if (replacements.isEmpty()) return text
        val result = StringBuilder(text.length)
        var cursor = 0
        replacements.toSortedMap().forEach { (start, replacement) ->
            if (start < cursor) return@forEach
            result.append(text, cursor, start)
            result.append(replacement.second)
            cursor = start + replacement.first
        }
        result.append(text, cursor, text.length)
        return result.toString()
    }
}
