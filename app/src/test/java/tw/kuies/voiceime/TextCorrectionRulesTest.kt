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
}
