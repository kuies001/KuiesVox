package tw.kuies.voiceime

/** 中央主按鈕的模式配色：底色、漸層亮端、圖示色。 */
internal data class VoiceModeAccent(val base: Int, val bright: Int, val icon: Int)

/**
 * 中央大型錄音按鈕的模式配色。模式色集中在這裡管理，不要在畫面各處寫死，
 * 未來新增專用模式時沿用同一套。
 *
 * 一般語音維持既有淡紫漸層（[accentForMode] 回傳 null）；語音翻譯＝薄荷青綠；
 * 格式指令＝柔和玫瑰紫。待命、錄音中與處理中都維持同一模式色。
 */
internal object VoiceModePalette {
    /** 語音翻譯模式的主按鈕底色（薄荷青綠，#9CE8D5）。 */
    val TRANSLATE_MINT = 0xFF9CE8D5.toInt()

    /** 語音翻譯模式主按鈕漸層的亮端。 */
    val TRANSLATE_MINT_BRIGHT = 0xFFBAF4E4.toInt()

    /** 語音翻譯模式主按鈕圖示的深色（#183D39），確保對比清楚。 */
    val TRANSLATE_ICON = 0xFF183D39.toInt()

    /** 格式指令模式的主按鈕底色（柔和玫瑰紫，#E7B7D8），與淡紫、薄荷綠都明顯區別。 */
    val FORMAT_ROSE = 0xFFE7B7D8.toInt()

    /** 格式指令模式主按鈕漸層的亮端。 */
    val FORMAT_ROSE_BRIGHT = 0xFFF3D3E8.toInt()

    /** 格式指令模式主按鈕圖示的深梅色，與玫瑰底對比清楚。 */
    val FORMAT_ICON = 0xFF3E1F33.toInt()

    /**
     * 依目前模式取得中央按鈕配色。一般語音回傳 null，沿用既有的淡紫漸層。
     */
    fun accentForMode(translationModeActive: Boolean, formatModeActive: Boolean): VoiceModeAccent? = when {
        translationModeActive -> VoiceModeAccent(TRANSLATE_MINT, TRANSLATE_MINT_BRIGHT, TRANSLATE_ICON)
        formatModeActive -> VoiceModeAccent(FORMAT_ROSE, FORMAT_ROSE_BRIGHT, FORMAT_ICON)
        else -> null
    }
}
