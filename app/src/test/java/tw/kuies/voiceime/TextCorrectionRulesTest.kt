package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCorrectionRulesTest {
    @Test
    fun batchParserReadsSourceAndReplacementFromEachLine() {
        assertEquals(
            listOf("deep seek" to "DeepSeek", "gpt 5.6" to "GPT-5.6"),
            TextCorrectionRules.parseBatch("deep seek => DeepSeek\ngpt 5.6 => GPT-5.6\ninvalid line")
        )
    }

    @Test
    fun addTrimsSourcesAndSkipsDuplicateSourcesIgnoringCase() {
        val result = TextCorrectionRules.add(
            emptyList(),
            listOf(" deep seek " to " DeepSeek ", "DEEP SEEK" to "Other")
        )

        assertEquals(1, result.addedCount)
        assertEquals("deep seek", result.rules.single().sourceText)
        assertEquals("DeepSeek", result.rules.single().replacementText)
        assertTrue(result.rules.single().enabled)
        assertTrue(result.rules.single().id.isNotBlank())
    }

    @Test
    fun rulesCanBeDisabledAndDeleted() {
        val added = TextCorrectionRules.add(emptyList(), listOf("deep seek" to "DeepSeek")).rules.single()
        val disabled = TextCorrectionRules.setEnabled(listOf(added), added.id, false)

        assertFalse(disabled.single().enabled)
        assertTrue(TextCorrectionRules.delete(disabled, added.id).isEmpty())
    }

    @Test
    fun deleteAllRemovesOnlyTheSelectedRulesAndKeepsEverythingElse() {
        val rules = listOf(
            TextCorrectionRule("keep-1", "保留來源", "保留替換", enabled = false),
            TextCorrectionRule("drop-1", "刪除來源", "刪除替換", enabled = true),
            TextCorrectionRule("keep-2", "另一保留", "", enabled = true)
        )

        val remaining = TextCorrectionRules.deleteAll(rules, setOf("drop-1"))

        assertEquals(listOf("keep-1", "keep-2"), remaining.map { it.id })
        assertFalse(remaining.first().enabled)
        assertEquals("保留替換", remaining.first().replacementText)
    }

    @Test
    fun deleteAllWithNoIdsOrUnknownIdsChangesNothing() {
        val rules = TextCorrectionRules.add(
            emptyList(),
            listOf("甲" to "A", "乙" to "B")
        ).rules

        assertEquals(rules, TextCorrectionRules.deleteAll(rules, emptySet()))
        assertEquals(rules, TextCorrectionRules.deleteAll(rules, setOf("missing-id")))
    }

    @Test
    fun deletingTheSameIdsTwiceIsIdempotentSoRepeatedTapsCannotDeleteMore() {
        val rules = TextCorrectionRules.add(
            emptyList(),
            listOf("甲" to "A", "乙" to "B", "丙" to "C")
        ).rules
        val ids = rules.take(2).map { it.id }.toSet()

        val once = TextCorrectionRules.deleteAll(rules, ids)
        val twice = TextCorrectionRules.deleteAll(once, ids)

        assertEquals(listOf("丙"), once.map { it.sourceText })
        assertEquals(once, twice)
    }
}
