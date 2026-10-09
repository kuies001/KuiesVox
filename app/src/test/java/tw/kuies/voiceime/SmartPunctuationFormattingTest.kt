package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPunctuationFormattingTest {
    @Test
    fun enabledPromptExplainsSentenceTypeAndParagraphRules() {
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            smartPunctuationEnabled = true
        )

        assertTrue(prompt.contains("智慧標點與分段"))
        assertTrue(prompt.contains("嗎、呢、為什麼、怎麼、何時、哪裡、是否、有沒有"))
        assertTrue(prompt.contains("間接問句"))
        assertTrue(prompt.contains("反問句"))
        assertTrue(prompt.contains("多個句子"))
        assertTrue(prompt.contains("分段"))
        assertFalse(prompt.contains("台灣繁體中文用字偏好"))
    }

    @Test
    fun disabledSmartPunctuationKeepsTheLegacyPromptUnchanged() {
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            smartPunctuationEnabled = false
        )

        assertEquals(TranscriptFormattingPrompt.SYSTEM_PROMPT, prompt)
    }

    @Test
    fun controlledFormatterKeepsQuestionMarksAndIndirectQuestionPeriods() {
        val cases = listOf(
            "你今天有去健身房嗎" to "你今天有去健身房嗎？",
            "你為什麼沒有告訴我" to "你為什麼沒有告訴我？",
            "你到底在做什麼" to "你到底在做什麼？",
            "我想知道你明天有沒有去健身房" to "我想知道你明天有沒有去健身房。",
            "我不知道他為什麼沒來" to "我不知道他為什麼沒來。",
            "請幫我確認他是否已經到公司" to "請幫我確認他是否已經到公司。",
            "這樣做不是更方便嗎" to "這樣做不是更方便嗎？",
            "難道這不是很奇怪" to "難道這不是很奇怪？",
            "如果明天下雨我們就改期" to "如果明天下雨，我們就改期。"
        )
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            smartPunctuationEnabled = true
        )

        cases.forEach { (input, response) ->
            val provider = FakeFormattingProvider(response)
            var result: TextFormattingResult? = null
            provider.format("test-key", "fake-model", input, prompt) { result = it }
            val formatted = (result as TextFormattingResult.Success).text

            assertEquals(response, SmartFormattingPolicy.resolve(input, formatted).text)
            assertEquals(1, provider.requestCount)
            assertEquals(response, TerminalPunctuationProcessor.process(formatted, TerminalPeriodMode.ALWAYS))
        }
    }

    @Test
    fun oneRequestCanSplitSeveralSentencesInsideASingleUtterance() {
        val input = "我今天去公司開會你知道主管說了什麼嗎他說明天要改成遠端會議"
        val response = "我今天去公司開會。你知道主管說了什麼嗎？他說明天要改成遠端會議。"
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            smartPunctuationEnabled = true
        )
        val provider = FakeFormattingProvider(response)
        var result: TextFormattingResult? = null

        provider.format("test-key", "fake-model", input, prompt) { result = it }

        assertEquals(response, (result as TextFormattingResult.Success).text)
        assertEquals(1, provider.requestCount)
    }

    @Test
    fun paragraphGuidanceDoesNotForceShortMessagesIntoArticles() {
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            smartPunctuationEnabled = true
        )

        assertTrue(prompt.contains("短句與簡短聊天訊息不要分段"))
        assertTrue(prompt.contains("不改變敘述順序"))
    }
}
