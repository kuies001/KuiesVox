package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaiwanWordingPreferenceTest {
    @Test
    fun enabledPromptAsksForTaiwanWordingAndProtectsProperNouns() {
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            taiwanWordingEnabled = true
        )

        assertTrue(prompt.contains("台灣繁體中文用字偏好"))
        assertTrue(prompt.contains("滑鼠"))
        assertTrue(prompt.contains("專有名詞"))
        assertTrue(prompt.contains("有歧義"))
        assertTrue(prompt.contains("個人詞庫"))
    }

    @Test
    fun disabledTaiwanWordingKeepsTheLegacyPromptUnchanged() {
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            taiwanWordingEnabled = false,
            smartPunctuationEnabled = false
        )

        assertEquals(TranscriptFormattingPrompt.SYSTEM_PROMPT, prompt)
        assertFalse(prompt.contains("台灣繁體中文用字偏好"))
    }

    @Test
    fun taiwanWordingIsIndependentFromTheOtherSwitches() {
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            taiwanWordingEnabled = true,
            smartPunctuationEnabled = false
        )

        assertTrue(prompt.contains("台灣繁體中文用字偏好"))
        assertFalse(prompt.contains("上下文智慧糾錯"))
        assertFalse(prompt.contains("智慧標點與分段"))
    }

    @Test
    fun personalGlossaryStaysAReferenceAndExplicitRulesWinInsideOneRequest() {
        val rules = listOf(TextCorrectionRule("rule-1", "鼠標", "滑鼠", true))
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = true,
            glossary = listOf(PersonalGlossaryTerm("glossary-1", "庫耶斯", true, listOf("庫耶斯輸入法"))),
            correctionRules = rules,
            taiwanWordingEnabled = true,
            smartPunctuationEnabled = true
        )
        val prepared = TextPostProcessor.processWithPlan("我買了一個新的鼠標", rules)
        val provider = FakeFormattingProvider("我買了一個新的滑鼠。")
        var result: TextFormattingResult? = null
        provider.format("test-key", "fake-model", prepared.text, prompt) { result = it }
        val formatted = (result as TextFormattingResult.Success).text
        val finalText = TextPostProcessor.enforceCorrectionPlan(formatted, rules, prepared.correctionPlan)

        assertEquals(1, provider.requestCount)
        assertEquals("我買了一個新的滑鼠。", finalText)
        assertTrue(prompt.contains("最高優先權"))
        assertTrue(prompt.contains("不得強迫替換"))
        assertTrue(prompt.contains("台灣繁體中文用字偏好"))
        assertTrue(prompt.contains("智慧標點與分段"))
        assertTrue(prompt.contains("庫耶斯"))
    }
}
