package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatCommandPromptTest {
    @Test
    fun promptCoversEverySupportedLayoutInstruction() {
        val prompt = FormatCommandPrompt.SYSTEM_PROMPT

        assertTrue(prompt.contains("換行"))
        assertTrue(prompt.contains("空一行"))
        assertTrue(prompt.contains("1. 2. 3."))
        assertTrue(prompt.contains("•"))
        assertTrue(prompt.contains("標題"))
        assertTrue(prompt.contains("混合指令"))
    }

    @Test
    fun promptKeepsContentOrderAndTechnicalTerms() {
        val prompt = FormatCommandPrompt.SYSTEM_PROMPT

        assertTrue(prompt.contains("不增加、不刪除、不改寫"))
        assertTrue(prompt.contains("保留技術名詞"))
        assertTrue(prompt.contains("不使用 Markdown 語法"))
    }

    @Test
    fun promptRefusesDestructiveRequestsAndUnsupportedInstructions() {
        val prompt = FormatCommandPrompt.SYSTEM_PROMPT

        assertTrue(prompt.contains("刪除"))
        assertTrue(prompt.contains("清空"))
        assertTrue(prompt.contains("不是排版指令"))
        assertTrue(prompt.contains("輸出空字串"))
    }

    @Test
    fun formatPromptIsIndependentFromTheNormalFormattingPrompt() {
        val prompt = FormatCommandPrompt.SYSTEM_PROMPT
        val normalPrompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = true,
            taiwanWordingEnabled = true,
            smartPunctuationEnabled = true
        )

        assertFalse(prompt == normalPrompt)
        assertFalse(prompt.contains("上下文智慧糾錯"))
        assertFalse(prompt.contains("台灣繁體中文用字偏好"))
        assertFalse(prompt.contains("智慧標點與分段"))
        assertFalse(normalPrompt.contains("格式指令模式"))
    }

    @Test
    fun eachFormatCommandNeedsExactlyOneControlledRequest() {
        val cases = listOf(
            "列成三點，第一點更新系統，第二點測試 API，第三點確認備份" to
                "1. 更新系統\n2. 測試 API\n3. 確認備份",
            "用項目符號列出牛奶、雞蛋、麵包" to "• 牛奶\n• 雞蛋\n• 麵包",
            "標題是今日工作紀錄，內容是完成系統更新並測試 API" to
                "今日工作紀錄\n\n完成系統更新並測試 API。",
            "先寫標題本週待辦，然後換行，第一點確認備份，第二點更新伺服器" to
                "本週待辦\n\n1. 確認備份\n2. 更新伺服器",
            "幫我 commit 到 GitHub" to "幫我 commit 到 GitHub"
        )

        cases.forEach { (input, expected) ->
            val provider = FakeFormattingProvider(expected)
            var result: TextFormattingResult? = null

            provider.format("test-key", "fake-model", input, FormatCommandPrompt.SYSTEM_PROMPT) {
                result = it
            }

            assertEquals(expected, (result as TextFormattingResult.Success).text)
            assertEquals(1, provider.requestCount)
            assertEquals(FormatCommandPrompt.SYSTEM_PROMPT, provider.lastSystemPrompt)
        }
    }

    @Test
    fun destructiveInstructionProducesNoInsertableText() {
        val provider = FakeFormattingProvider("")
        var result: TextFormattingResult? = null

        provider.format(
            "test-key",
            "fake-model",
            "刪除全部文字",
            FormatCommandPrompt.SYSTEM_PROMPT
        ) { result = it }

        assertEquals("", (result as TextFormattingResult.Success).text.trim())
        assertEquals(1, provider.requestCount)
    }

    @Test
    fun hintTextIsTheDocumentedModePrompt() {
        assertEquals("可說：換行、空一行、列成三點、加入標題", FormatCommandPrompt.FORMAT_COMMAND_HINT)
    }
}
