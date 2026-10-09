package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorTest {
    @Test
    fun appliesSingleRule() {
        assertEquals(
            "DeepSeek is useful",
            TextPostProcessor.process("deep seek is useful", listOf(rule("deep seek", "DeepSeek")))
        )
    }

    @Test
    fun appliesMultipleRules() {
        val rules = listOf(
            rule("deep seek", "DeepSeek"),
            rule("gpt 5.6", "GPT-5.6")
        )

        assertEquals("DeepSeek and GPT-5.6", TextPostProcessor.process("deep seek and gpt 5.6", rules))
    }

    @Test
    fun matchesWithoutCaseSensitivity() {
        assertEquals(
            "DeepSeek",
            TextPostProcessor.process("DEEP SEEK", listOf(rule("deep seek", "DeepSeek")))
        )
    }

    @Test
    fun ignoresDisabledRules() {
        assertEquals(
            "deep seek",
            TextPostProcessor.process("deep seek", listOf(rule("deep seek", "DeepSeek", enabled = false)))
        )
    }

    @Test
    fun prioritizesLongerSources() {
        val rules = listOf(
            rule("deep seek", "DeepSeek"),
            rule("deep seek v4.1", "DeepSeek V4.1")
        )

        assertEquals("DeepSeek V4.1", TextPostProcessor.process("deep seek v4.1", rules))
    }

    @Test
    fun leavesTextUnchangedWhenThereAreNoRules() {
        assertEquals("original result", TextPostProcessor.process("original result", emptyList()))
    }

    @Test
    fun matchesLiteralTextRatherThanRegexPatterns() {
        val rules = listOf(rule("gpt 5.6", "GPT-5.6"))

        assertEquals("GPT-5.6 and gpt 5x6", TextPostProcessor.process("gpt 5.6 and gpt 5x6", rules))
    }

    @Test
    fun conflictingRulesAreAppliedInOnePassWithoutCycles() {
        val rules = listOf(rule("A", "B"), rule("B", "A"))

        assertEquals("B", TextPostProcessor.process("A", rules))
    }

    @Test
    fun finalRuleProtectionOnlyRestoresCorrectionsMatchedInOriginalAsr() {
        val rules = listOf(rule("A", "B"), rule("B", "C"))
        val processed = TextPostProcessor.processWithPlan("A", rules)

        assertEquals("B", processed.text)
        assertEquals("B B", TextPostProcessor.enforceCorrectionPlan("A B", rules, processed.correctionPlan))
    }

    @Test
    fun aiCannotUndoAnExplicitRuleAndRepeatedTextIsNotOverCorrected() {
        val rule = rule("助手", "住手")
        val processed = TextPostProcessor.processWithPlan("你給我助手", listOf(rule))

        assertEquals(
            "你給我住手 助手",
            TextPostProcessor.enforceCorrectionPlan("你給我助手 助手", listOf(rule), processed.correctionPlan)
        )
    }

    private fun rule(source: String, replacement: String, enabled: Boolean = true) =
        TextCorrectionRule("id-$source", source, replacement, enabled)
}
