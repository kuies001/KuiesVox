package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK B：短文字門檻預設值由 40 改為 8，且既有自訂值不得被覆寫。
 *
 * 政策語意：`codePointLength <= threshold` 時使用本機修正（不呼叫 AI）。
 */
class SmartFormattingThresholdTest {

    private fun decide(text: String, threshold: Int) = SmartFormattingPolicy.decide(
        text,
        SmartFormattingSettings(enabled = true, threshold = threshold)
    )

    @Test
    fun theDefaultThresholdIsNowEight() {
        assertEquals(8, SmartFormattingSettings.DEFAULT_THRESHOLD)
        assertEquals(8, SmartFormattingSettings().threshold)
        // 合法範圍維持不變，使用者仍可自行調整。
        assertEquals(0, SmartFormattingSettings.MIN_THRESHOLD)
        assertEquals(500, SmartFormattingSettings.MAX_THRESHOLD)
    }

    @Test
    fun boundaryLengthsFollowTheThresholdSemantics() {
        // 0／5／7／8 字：不超過門檻 → 本機修正，不呼叫 AI。
        listOf(0, 5, 7, 8).forEach { length ->
            val text = "字".repeat(length)
            assertEquals(
                "$length 字不應進入 AI 整理",
                SmartFormattingDecision.CommitOriginal(text),
                decide(text, SmartFormattingSettings.DEFAULT_THRESHOLD)
            )
        }
        // 9 字：超過新門檻 → 進入 AI 整理。
        listOf(9, 40).forEach { length ->
            val text = "字".repeat(length)
            assertEquals(
                "$length 字應進入 AI 整理",
                SmartFormattingDecision.Format(
                    text = text,
                    model = SmartFormattingSettings().modelFor(SmartFormattingSettings().provider),
                    provider = SmartFormattingSettings().provider
                ),
                decide(text, SmartFormattingSettings.DEFAULT_THRESHOLD)
            )
        }
    }

    @Test
    fun aUserChosenThresholdIsNeverRewrittenOrTreatedAsTheDefault() {
        // 使用者把門檻留／設成 40 時，40 字以內仍不呼叫 AI：40 被當成使用者偏好，不是舊預設。
        val fortyChars = "字".repeat(40)
        assertEquals(
            SmartFormattingDecision.CommitOriginal(fortyChars),
            decide(fortyChars, threshold = 40)
        )
        // normalize 也不得把 40 改回預設值。
        assertEquals(40, SmartFormattingSettingsRepository.normalize(
            SmartFormattingSettings(threshold = 40)
        ).threshold)
        // 其他自訂值同樣原樣保留（只做合法範圍夾限）。
        assertEquals(3, SmartFormattingSettingsRepository.normalize(
            SmartFormattingSettings(threshold = 3)
        ).threshold)
        assertEquals(500, SmartFormattingSettingsRepository.normalize(
            SmartFormattingSettings(threshold = 9_999)
        ).threshold)
        assertEquals(0, SmartFormattingSettingsRepository.normalize(
            SmartFormattingSettings(threshold = -5)
        ).threshold)
    }

    @Test
    fun countingUsesCodePointsTrimsWhitespaceAndCountsPunctuationAndNewlines() {
        // Unicode 補充平面字元算 1 個字，不會被拆成兩字而提前進入 AI。
        val emoji = "😀".repeat(8)
        assertEquals(SmartFormattingDecision.CommitOriginal(emoji), decide(emoji, threshold = 8))

        // 前後空白不計入字數。
        val padded = "    八個字八個字    "
        assertEquals(
            SmartFormattingDecision.CommitOriginal(padded),
            decide(padded, threshold = 8)
        )

        // 標點算一個字；「內部」換行也算一個字。
        val withPunctuation = "字".repeat(8) + "，"
        val withInnerNewline = "字".repeat(8) + "\n" + "字"
        assertTrue(
            "8 字剛好在門檻內，不應進入 AI",
            decide("字".repeat(8), 8) is SmartFormattingDecision.CommitOriginal
        )
        assertTrue(
            "多一個標點就超過門檻，應進入 AI",
            decide(withPunctuation, 8) is SmartFormattingDecision.Format
        )
        assertTrue(
            "內部換行算一個字，應進入 AI",
            decide(withInnerNewline, 8) is SmartFormattingDecision.Format
        )
        // 結尾換行屬於前後空白，不計入字數。
        assertTrue(
            "結尾換行應被 trim，不計入字數",
            decide("字".repeat(8) + "\n", 8) is SmartFormattingDecision.CommitOriginal
        )
    }

    @Test
    fun disablingSmartFormattingOrEmptyTextStillBypassesTheAiEntirely() {
        val long = "字".repeat(100)
        assertEquals(
            SmartFormattingDecision.CommitOriginal(long),
            SmartFormattingPolicy.decide(long, SmartFormattingSettings(enabled = false, threshold = 8))
        )
        assertEquals(
            SmartFormattingDecision.CommitOriginal("   "),
            decide("   ", threshold = 8)
        )
    }
}
