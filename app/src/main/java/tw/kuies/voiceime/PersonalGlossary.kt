package tw.kuies.voiceime

import java.util.Locale
import java.util.UUID

internal data class PersonalGlossaryTerm(
    val id: String,
    val term: String,
    val enabled: Boolean,
    val commonPhrases: List<String> = emptyList()
)

internal data class PersonalGlossaryAddResult(
    val entries: List<PersonalGlossaryTerm>,
    val addedCount: Int
)

internal object PersonalGlossaryRules {
    fun add(
        entries: List<PersonalGlossaryTerm>,
        rawTerms: List<String>
    ): PersonalGlossaryAddResult {
        val updatedEntries = entries.toMutableList()
        val knownTerms = entries.mapTo(mutableSetOf<String>()) { keyFor(it.term) }
        var addedCount = 0

        rawTerms.forEach { rawTerm ->
            val term = rawTerm.trim()
            if (term.isEmpty() || !knownTerms.add(keyFor(term))) return@forEach

            updatedEntries += PersonalGlossaryTerm(
                id = UUID.randomUUID().toString(),
                term = term,
                enabled = true
            )
            addedCount += 1
        }

        return PersonalGlossaryAddResult(updatedEntries, addedCount)
    }

    fun setEnabled(
        entries: List<PersonalGlossaryTerm>,
        id: String,
        enabled: Boolean
    ): List<PersonalGlossaryTerm> = entries.map { entry ->
        if (entry.id == id) entry.copy(enabled = enabled) else entry
    }

    fun setCommonPhrases(
        entries: List<PersonalGlossaryTerm>,
        id: String,
        rawPhrases: List<String>
    ): List<PersonalGlossaryTerm> {
        val phrases = rawPhrases.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(::keyFor)
            .take(MAX_COMMON_PHRASES)
            .map(::limitCodePoints)
            .toList()
        return entries.map { entry ->
            if (entry.id == id) entry.copy(commonPhrases = phrases) else entry
        }
    }

    fun delete(
        entries: List<PersonalGlossaryTerm>,
        id: String
    ): List<PersonalGlossaryTerm> = entries.filterNot { it.id == id }

    fun keyFor(term: String): String = term.trim().lowercase(Locale.ROOT)

    private fun limitCodePoints(value: String): String {
        val end = value.offsetByCodePoints(0, minOf(value.codePointCount(0, value.length), MAX_PHRASE_CODE_POINTS))
        return value.substring(0, end)
    }

    const val MAX_COMMON_PHRASES = 5
    const val MAX_PHRASE_CODE_POINTS = 80
}
