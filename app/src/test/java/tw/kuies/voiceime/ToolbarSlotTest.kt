package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarSlotTest {
    @Test
    fun theToolbarKeepsTheAgreedLeftToRightOrder() {
        assertEquals(
            listOf(
                "剪貼簿歷史",
                "全選輸入文字",
                "清除全部文字",
                "AI 語音翻譯",
                "切換鍵盤",
                "設定",
                "更多功能"
            ),
            ToolbarSlot.entries.map { it.label }
        )
    }

    @Test
    fun keyboardSettingsAndMoreAreAlwaysTheLastThree() {
        assertEquals(ToolbarSlot.FIXED_TRAILING, ToolbarSlot.entries.takeLast(3))
        assertEquals(
            listOf(ToolbarSlot.KEYBOARD, ToolbarSlot.SETTINGS, ToolbarSlot.MORE),
            ToolbarSlot.FIXED_TRAILING
        )
    }

    @Test
    fun translateSitsImmediatelyLeftOfTheKeyboard() {
        val slots = ToolbarSlot.entries

        assertEquals(ToolbarSlot.TRANSLATE, slots[slots.indexOf(ToolbarSlot.KEYBOARD) - 1])
    }
}
