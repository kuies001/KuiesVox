package tw.kuies.voiceime

import org.junit.Assert.assertTrue
import org.junit.Test

class TextTranslatePromptTest {
    @Test
    fun theSystemPromptDeclaresATranslatorThatNeverAnswersTheSource() {
        val prompt = TextTranslatePrompt.SYSTEM_PROMPT

        assertTrue(prompt.contains("翻譯處理器"))
        assertTrue(prompt.contains("不是聊天助理"))
        assertTrue(prompt.contains("絕對不可以回答"))
        assertTrue(prompt.contains("只包含翻譯結果"))
    }

    @Test
    fun thePromptKeepsTechnicalNamesAndTaiwanWordingRules() {
        val prompt = TextTranslatePrompt.SYSTEM_PROMPT

        assertTrue(prompt.contains("GitHub"))
        assertTrue(prompt.contains("README"))
        assertTrue(prompt.contains("API"))
        assertTrue(prompt.contains("台灣"))
    }

    @Test
    fun theUserMessageSeparatesTheTargetLanguageFromTheSourceData() {
        val message = TextTranslatePrompt.buildUserMessage(
            sourceContent = "你明天有空嗎？",
            targetLanguage = "English"
        )

        assertTrue(message.contains("TARGET LANGUAGE:"))
        assertTrue(message.contains("English"))
        assertTrue(message.contains("SOURCE CONTENT:"))
        assertTrue(message.contains("<<<SOURCE_CONTENT"))
        assertTrue(message.contains("你明天有空嗎？"))
        assertTrue(message.contains("SOURCE_CONTENT>>>"))
    }

    @Test
    fun aQuestionAndItsAnswerArePresentedAsDataNotInstructions() {
        // 原文是問句，但整段被夾在 SOURCE CONTENT delimiter 內，且系統提示禁止回答它。
        val message = TextTranslatePrompt.buildUserMessage(
            sourceContent = "Are you free tomorrow?",
            targetLanguage = "Japanese"
        )

        val sourceIndex = message.indexOf("<<<SOURCE_CONTENT")
        val contentIndex = message.indexOf("Are you free tomorrow?")
        val closeIndex = message.indexOf("SOURCE_CONTENT>>>")
        assertTrue(sourceIndex in 0 until contentIndex)
        assertTrue(contentIndex < closeIndex)
        assertTrue(message.contains("Japanese"))
    }
}
