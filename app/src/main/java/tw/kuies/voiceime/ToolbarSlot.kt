package tw.kuies.voiceime

/**
 * 頂部工具列由左至右的固定順序（單一來源）。
 *
 * 鍵盤／設定／更多永遠是最後三個，AI 翻譯永遠緊鄰鍵盤左側；
 * 新增功能時只能插入在 AI 翻譯左邊，不得擠進最後三個位置。
 */
internal enum class ToolbarSlot(val label: String) {
    CLIPBOARD("剪貼簿歷史"),
    SELECT_ALL("全選輸入文字"),
    CLEAR("清除全部文字"),
    TRANSLATE("AI 語音翻譯"),
    KEYBOARD("切換鍵盤"),
    SETTINGS("設定"),
    MORE("更多功能");

    companion object {
        /** 永遠固定的最右側三個位置。 */
        val FIXED_TRAILING: List<ToolbarSlot> = listOf(KEYBOARD, SETTINGS, MORE)
    }
}
