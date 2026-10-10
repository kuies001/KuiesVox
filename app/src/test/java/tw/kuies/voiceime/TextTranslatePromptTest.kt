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
        assertTrue(prompt.contains("輸出必須只有翻譯後的純文字"))
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
    fun thePromptRemovesSpeechFillersInTheSameTranslationPass() {
        val prompt = TextTranslatePrompt.SYSTEM_PROMPT

        assertTrue(prompt.contains("贅詞"))
        assertTrue(prompt.contains("嗯"))
        assertTrue(prompt.contains("呃"))
        assertTrue(prompt.contains("uh"))
        assertTrue(prompt.contains("um"))
        assertTrue(prompt.contains("同一次輸出完成"))
    }

    @Test
    fun thePromptProtectsMeaningfulFillersAndContentFromOverCleaning() {
        val prompt = TextTranslatePrompt.SYSTEM_PROMPT

        assertTrue(prompt.contains("具有語氣或語意作用"))
        assertTrue(prompt.contains("必須保留"))
        assertTrue(prompt.contains("不得摘要"))
        assertTrue(prompt.contains("不得刪除實質資訊"))
        assertTrue(prompt.contains("不得改變說話者意圖"))
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
