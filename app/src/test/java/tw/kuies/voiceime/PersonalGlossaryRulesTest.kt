package tw.kuies.voiceime

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalGlossaryRulesTest {
    @Test
    fun addTrimsAndDeduplicatesTermsIgnoringCase() {
        val first = PersonalGlossaryRules.add(
            emptyList(),
            listOf(" DeepSeek ", "deepseek", "", "南岡山", "南岡山")
        )

        assertEquals(2, first.addedCount)
        assertEquals(listOf("DeepSeek", "南岡山"), first.entries.map { it.term })
        assertTrue(first.entries.all { it.id.isNotBlank() && it.enabled })

        val second = PersonalGlossaryRules.add(first.entries, listOf(" DeepSeek ", "OpenCode"))
        assertEquals(1, second.addedCount)
        assertEquals(3, second.entries.size)
    }

    @Test
    fun termsCanBeDisabledAndDeleted() {
        val entries = PersonalGlossaryRules.add(emptyList(), listOf("DeepSeek", "Hermes")).entries
        val disabled = PersonalGlossaryRules.setEnabled(entries, entries.first().id, false)

        assertFalse(disabled.first().enabled)
        assertTrue(disabled.last().enabled)
        assertEquals(listOf("Hermes"), PersonalGlossaryRules.delete(disabled, entries.first().id).map { it.term })
    }

    @Test
    fun promptIncludesOnlyEnabledTermsAndIsNullWhenNoneAreEnabled() {
        val entries = listOf(
            PersonalGlossaryTerm("1", "DeepSeek", true),
            PersonalGlossaryTerm("2", "OpenCode", false),
            PersonalGlossaryTerm("3", "南岡山", true)
        )

        val prompt = requireNotNull(GlossaryPromptBuilder.build(entries))
        assertTrue(prompt.contains("DeepSeek"))
        assertTrue(prompt.contains("南岡山"))
        assertFalse(prompt.contains("OpenCode"))
        assertNull(GlossaryPromptBuilder.build(entries.map { it.copy(enabled = false) }))
        assertNull(GlossaryPromptBuilder.build(emptyList()))
    }

    @Test
    fun promptIncludesAtMostThirtyTerms() {
        val entries = (1..60).map { index ->
            PersonalGlossaryTerm(index.toString(), "a${index.toString(36)}", true)
        }

        val prompt = requireNotNull(GlossaryPromptBuilder.build(entries))

        assertEquals(
            GlossaryPromptBuilder.MAX_TERMS,
            prompt.substringAfter("：").split(", ").size
        )
        assertEquals(60, entries.size)
    }

    @Test
    fun promptStaysWithinConservativeUnicodeAndUtf8Limits() {
        val entries = (1..60).map { index ->
            PersonalGlossaryTerm(index.toString(), "詞$index", true)
        }
        val prompt = requireNotNull(GlossaryPromptBuilder.build(entries))

        assertTrue(prompt.codePointCount(0, prompt.length) <= GlossaryPromptBuilder.MAX_UNICODE_CODE_POINTS)
        assertTrue(prompt.toByteArray(StandardCharsets.UTF_8).size <= GlossaryPromptBuilder.MAX_PROMPT_UTF8_BYTES)
        assertEquals(60, entries.size)
    }
}
