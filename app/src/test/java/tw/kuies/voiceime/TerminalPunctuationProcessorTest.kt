package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalPunctuationProcessorTest {
    @Test
    fun neverRemovesOnlyTheLastTerminalChinesePeriod() {
        assertEquals("你好", apply("你好。", TerminalPeriodMode.NEVER))
        assertEquals("今天很好。等等去健身", apply("今天很好。等等去健身。", TerminalPeriodMode.NEVER))
        assertEquals("第一段。\n第二段", apply("第一段。\n第二段。", TerminalPeriodMode.NEVER))
    }

    @Test
    fun neverPreservesTrailingWhitespaceAndNewlines() {
        assertEquals("你好\n", apply("你好。\n", TerminalPeriodMode.NEVER))
        assertEquals("你好  \n\t", apply("你好。  \n\t", TerminalPeriodMode.NEVER))
    }

    @Test
    fun neverLeavesOtherTerminalPunctuationAndEmojiAlone() {
        listOf("你好？", "你好！", "你好?", "你好!", "你好……", "你好…", "你好😊")
            .forEach { text -> assertEquals(text, apply(text, TerminalPeriodMode.NEVER)) }
    }

    @Test
    fun autoRemovesPeriodForSendAndSearchActions() {
        assertEquals("送出", apply("送出。", TerminalPeriodMode.AUTO, imeOptions = IME_ACTION_SEND))
        assertEquals("查詢", apply("查詢。", TerminalPeriodMode.AUTO, imeOptions = IME_ACTION_SEARCH))
    }

    @Test
    fun autoRemovesPeriodForUrlEmailPhoneNumberAndPasswordFields() {
        val fields = listOf(
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD,
            TYPE_CLASS_PHONE,
            TYPE_CLASS_NUMBER,
            TYPE_CLASS_DATETIME
        )
        fields.forEach { inputType ->
            assertEquals("欄位", apply("欄位。", TerminalPeriodMode.AUTO, inputType = inputType))
        }
    }

    @Test
    fun autoKeepsPeriodForOrdinaryAndMultilineTextFields() {
        assertEquals("一般文字。", apply("一般文字。", TerminalPeriodMode.AUTO, inputType = TYPE_CLASS_TEXT))
        assertEquals(
            "第一行。\n第二行。",
            apply("第一行。\n第二行。", TerminalPeriodMode.AUTO,
                inputType = TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE)
        )
    }

    @Test
    fun alwaysPreservesExistingPeriodAndDoesNotAppendOne() {
        assertEquals("句子。", apply("句子。", TerminalPeriodMode.ALWAYS))
        assertEquals("沒有句號", apply("沒有句號", TerminalPeriodMode.ALWAYS))
    }

    @Test
    fun redundantSentenceEndersAreCollapsedBeforeTheModeIsApplied() {
        assertEquals("你今天有空。", apply("你今天有空。。", TerminalPeriodMode.ALWAYS))
        assertEquals("你好嗎？", apply("你好嗎？。", TerminalPeriodMode.ALWAYS))
        assertEquals("你要去哪裡？！", apply("你要去哪裡？！。", TerminalPeriodMode.ALWAYS))
        assertEquals("？", apply("？。", TerminalPeriodMode.ALWAYS))
        assertEquals("你好嗎？", apply("你好嗎？。", TerminalPeriodMode.NEVER))
        assertEquals("你今天有空", apply("你今天有空。。", TerminalPeriodMode.NEVER))
    }

    @Test
    fun collapsingLeavesOtherPunctuationParagraphsAndAsciiDotsUntouched() {
        assertEquals("i.e. this.", apply("i.e. this.", TerminalPeriodMode.ALWAYS))
        assertEquals("你好……", apply("你好……", TerminalPeriodMode.ALWAYS))
        assertEquals("第一段。\n第二段。", apply("第一段。\n第二段。", TerminalPeriodMode.ALWAYS))
        assertEquals(
            "第一段。\n\n第二段？",
            apply("第一段。\n\n第二段？。", TerminalPeriodMode.ALWAYS)
        )
    }

    @Test
    fun missingOrInvalidStoredModeDefaultsToAuto() {
        assertEquals(TerminalPeriodMode.AUTO, TerminalPeriodMode.fromStoredValue(null))
        assertEquals(TerminalPeriodMode.AUTO, TerminalPeriodMode.fromStoredValue("unknown"))
        assertEquals(TerminalPeriodMode.NEVER, TerminalPeriodMode.fromStoredValue("never"))
    }

    private fun apply(
        text: String,
        mode: TerminalPeriodMode,
        imeOptions: Int = 0,
        inputType: Int = 0
    ) = TerminalPunctuationProcessor.process(text, mode, imeOptions, inputType)

    private companion object {
        const val IME_ACTION_SEARCH = 3
        const val IME_ACTION_SEND = 4
        const val TYPE_CLASS_TEXT = 1
        const val TYPE_CLASS_NUMBER = 2
        const val TYPE_CLASS_PHONE = 3
        const val TYPE_CLASS_DATETIME = 4
        const val TYPE_TEXT_VARIATION_URI = 0x10
        const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x20
        const val TYPE_TEXT_VARIATION_PASSWORD = 0x80
        const val TYPE_TEXT_FLAG_MULTI_LINE = 0x20000
    }
}
