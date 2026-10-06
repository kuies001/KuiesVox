package tw.kuies.voiceime

import java.util.Locale
import java.util.UUID

internal data class PersonalGlossaryTerm(
    val id: String,
    val term: String,
    val enabled: Boolean
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

    fun delete(
        entries: List<PersonalGlossaryTerm>,
        id: String
    ): List<PersonalGlossaryTerm> = entries.filterNot { it.id == id }

    fun keyFor(term: String): String = term.trim().lowercase(Locale.ROOT)
}
