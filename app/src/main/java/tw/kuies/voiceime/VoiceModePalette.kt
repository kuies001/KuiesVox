package tw.kuies.voiceime

/** 中央主按鈕的模式配色：底色、漸層亮端、圖示色。 */
internal data class VoiceModeAccent(val base: Int, val bright: Int, val icon: Int)

/**
 * 中央大型錄音按鈕的模式配色。模式色集中在這裡管理，不要在畫面各處寫死，
 * 未來新增專用模式時沿用同一套。
 *
 * 一般語音＝奶油杏（#F3D8C7）；語音翻譯＝鼠尾草綠（#D8E6C8）；
 * 格式指令＝柔霧紫（#E8D8F2）；AI 編輯＝蜜桃粉（#F3AAA4）。
 * 待命、錄音中與處理中都維持同一模式色。
 */
internal object VoiceModePalette {
    /** 一般語音模式主按鈕配色（奶油杏，#F3D8C7）。 */
    val NORMAL_CREAM = 0xFFF3D8C7.toInt()
    val NORMAL_CREAM_BRIGHT = 0xFFF9EBE0.toInt()
    val NORMAL_ICON = 0xFF35231C.toInt()

    /** 語音翻譯模式的主按鈕底色（鼠尾草綠，#D8E6C8）。 */
    val TRANSLATE_MINT = 0xFFD8E6C8.toInt()
    val TRANSLATE_MINT_BRIGHT = 0xFFE6F0DC.toInt()
    val TRANSLATE_ICON = 0xFF2C4230.toInt()

    /** 格式指令模式的主按鈕底色（柔霧紫，#E8D8F2）。 */
    val FORMAT_ROSE = 0xFFE8D8F2.toInt()
    val FORMAT_ROSE_BRIGHT = 0xFFF3EAF9.toInt()
    val FORMAT_ICON = 0xFF4A3554.toInt()

    /** AI 編輯模式的主按鈕底色（蜜桃粉，#F3AAA4）。 */
    val AI_EDIT_PEACH = 0xFFF3AAA4.toInt()
    val AI_EDIT_PEACH_BRIGHT = 0xFFF9D0CC.toInt()
    val AI_EDIT_ICON = 0xFF4E2725.toInt()

    /**
     * 依目前模式取得中央按鈕配色。一般語音回傳 null，沿用奶油杏漸層。
     */
    fun accentForMode(
        translationModeActive: Boolean,
        formatModeActive: Boolean,
        aiEditModeActive: Boolean = false
    ): VoiceModeAccent? = when {
        translationModeActive -> VoiceModeAccent(TRANSLATE_MINT, TRANSLATE_MINT_BRIGHT, TRANSLATE_ICON)
        formatModeActive -> VoiceModeAccent(FORMAT_ROSE, FORMAT_ROSE_BRIGHT, FORMAT_ICON)
        aiEditModeActive -> VoiceModeAccent(AI_EDIT_PEACH, AI_EDIT_PEACH_BRIGHT, AI_EDIT_ICON)
        else -> null
    }
}
