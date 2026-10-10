package tw.kuies.voiceime

/**
 * 中央大型錄音按鈕的模式配色（純常數與決策，方便以單元測試鎖定）。
 *
 * 一般語音／格式指令／AI 編輯維持原本的淡紫色；只有語音翻譯模式改用薄荷青綠色。
 */
internal object VoiceModePalette {
    /** 語音翻譯模式的主按鈕底色（薄荷青綠，#9CE8D5）。 */
    val TRANSLATE_MINT = 0xFF9CE8D5.toInt()

    /** 語音翻譯模式主按鈕漸層的亮端。 */
    val TRANSLATE_MINT_BRIGHT = 0xFFBAF4E4.toInt()

    /** 語音翻譯模式主按鈕圖示的深色（#183D39），確保對比清楚。 */
    val TRANSLATE_ICON = 0xFF183D39.toInt()

    /**
     * 翻譯模式的中央按鈕底色：待命、錄音中、辨識／翻譯處理中都回傳薄荷綠，
     * 不會因為錄音或處理就回到一般模式的淡紫色。非翻譯模式回傳 null，沿用既有配色。
     */
    fun centralButtonAccentOrNull(translationModeActive: Boolean): Int? =
        if (translationModeActive) TRANSLATE_MINT else null
}
