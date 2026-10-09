package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatCommandInterpreterTest {
    @Test
    fun plainNewlineCommandIsHandledLocally() {
        listOf("換行", "換行。", "換行！", "「換行」", " 換行 ", "，換行、").forEach { transcript ->
            assertEquals(transcript, FormatCommandInterpretation.Newline, interpret(transcript))
        }
    }

    @Test
    fun plainBlankLineCommandIsHandledLocally() {
        listOf("空一行", "空一行。", "「空一行」", "空一行！").forEach { transcript ->
            assertEquals(transcript, FormatCommandInterpretation.BlankLine, interpret(transcript))
        }
    }

    @Test
    fun sentencesThatMerelyContainACommandWordAreSentToTheAiLayer() {
        listOf(
            "我覺得換行很好用",
            "換行符號要放在哪裡",
            "請幫我空一行看看",
            "這個按鈕是換行嗎",
            "我今天想學怎麼空一行"
        ).forEach { transcript ->
            assertEquals(transcript, FormatCommandInterpretation.UseAi, interpret(transcript))
        }
    }

    @Test
    fun compositeAndUnrelatedCommandsGoToTheAiLayer() {
        listOf(
            "換行換行",
            "列成三點，第一點更新系統，第二點測試 API，第三點確認備份",
            "用項目符號列出牛奶、雞蛋、麵包",
            "標題是今日工作紀錄，內容是完成系統更新並測試 API",
            "先寫標題本週待辦，然後換行，第一點確認備份，第二點更新伺服器",
            "今天天氣很好"
        ).forEach { transcript ->
            assertEquals(transcript, FormatCommandInterpretation.UseAi, interpret(transcript))
        }
    }

    @Test
    fun destructiveRequestsAreRefusedInsteadOfBeingSentAnywhere() {
        listOf(
            "刪除全部文字",
            "刪除全部文字。",
            "刪除全部",
            "清空全部",
            "清除全部文字",
            "刪除所有內容"
        ).forEach { transcript ->
            assertEquals(transcript, FormatCommandInterpretation.Unsupported, interpret(transcript))
        }
    }

    @Test
    fun emptyTranscriptIsNotTreatedAsACommand() {
        assertEquals(FormatCommandInterpretation.UseAi, interpret(""))
        assertEquals(FormatCommandInterpretation.UseAi, interpret("   "))
    }

    @Test
    fun onlyCommandsThatProduceRealTextAreRecordedInVoiceHistory() {
        assertFalse(FormatCommandInterpretation.Newline.recordsVoiceHistory())
        assertFalse(FormatCommandInterpretation.BlankLine.recordsVoiceHistory())
        assertFalse(FormatCommandInterpretation.Unsupported.recordsVoiceHistory())
        assertTrue(FormatCommandInterpretation.UseAi.recordsVoiceHistory())
    }

    private fun interpret(transcript: String): FormatCommandInterpretation =
        FormatCommandInterpreter.interpret(transcript)
}
