package tw.kuies.voiceime

internal enum class TerminalPeriodMode(val storageValue: String) {
    AUTO("auto"),
    ALWAYS("always"),
    NEVER("never");

    companion object {
        fun fromStoredValue(value: String?): TerminalPeriodMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: AUTO
    }
}

/** Applies the selected policy to only the final Chinese full stop before trailing whitespace. */
internal object TerminalPunctuationProcessor {
    private const val IME_MASK_ACTION = 0x000000ff
    private const val IME_ACTION_SEARCH = 3
    private const val IME_ACTION_SEND = 4

    private const val TYPE_MASK_CLASS = 0x0000000f
    private const val TYPE_MASK_VARIATION = 0x00000ff0
    private const val TYPE_CLASS_TEXT = 0x00000001
    private const val TYPE_CLASS_NUMBER = 0x00000002
    private const val TYPE_CLASS_PHONE = 0x00000003
    private const val TYPE_CLASS_DATETIME = 0x00000004
    private const val TYPE_TEXT_VARIATION_URI = 0x00000010
    private const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
    private const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val TYPE_TEXT_VARIATION_FILTER = 0x000000b0
    private const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0x000000d0
    private const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0

    fun process(
        text: String,
        mode: TerminalPeriodMode,
        imeOptions: Int = 0,
        inputType: Int = 0
    ): String {
        val normalized = collapseRedundantSentenceEnders(text)
        val shouldRemove = when (mode) {
            TerminalPeriodMode.AUTO -> shouldRemoveInAuto(imeOptions, inputType)
            TerminalPeriodMode.NEVER -> true
            TerminalPeriodMode.ALWAYS -> false
        }
        return if (shouldRemove) removeLastTerminalChinesePeriod(normalized) else normalized
    }

    /**
     * 文字整理可能產生重複或矛盾的結尾標點，例如「？」後又接「。」或連續句號。
     * 這裡只折疊緊接在句末標點之後的中文句號，不動其他標點與內容。
     */
    internal fun collapseRedundantSentenceEnders(text: String): String {
        if (!text.contains('。')) return text
        val builder = StringBuilder(text.length)
        text.forEach { char ->
            if (char == '。' && (builder.lastOrNull() == '。' || builder.lastOrNull().isSentenceEndingMark())) {
                return@forEach
            }
            builder.append(char)
        }
        return builder.toString()
    }

    private fun Char?.isSentenceEndingMark(): Boolean =
        this == '？' || this == '！' || this == '?' || this == '!'

    fun shouldRemoveInAuto(imeOptions: Int, inputType: Int): Boolean {
        when (imeOptions and IME_MASK_ACTION) {
            IME_ACTION_SEND, IME_ACTION_SEARCH -> return true
        }

        val inputClass = inputType and TYPE_MASK_CLASS
        if (inputClass == TYPE_CLASS_NUMBER || inputClass == TYPE_CLASS_PHONE ||
            inputClass == TYPE_CLASS_DATETIME
        ) return true

        if (inputClass == TYPE_CLASS_TEXT) {
            return when (inputType and TYPE_MASK_VARIATION) {
                TYPE_TEXT_VARIATION_URI,
                TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                TYPE_TEXT_VARIATION_PASSWORD,
                TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                TYPE_TEXT_VARIATION_FILTER,
                TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                TYPE_TEXT_VARIATION_WEB_PASSWORD -> true
                else -> false
            }
        }
        return false
    }

    private fun removeLastTerminalChinesePeriod(text: String): String {
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        val periodIndex = end - 1
        return if (periodIndex >= 0 && text[periodIndex] == '。') {
            text.removeRange(periodIndex, periodIndex + 1)
        } else {
            text
        }
    }
}
