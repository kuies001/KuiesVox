package tw.kuies.voiceime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPrivacyPolicyTest {
    @Test
    fun bothSwitchesDefaultToOnSoExistingUsersSeeNoChange() {
        val defaults = HistorySettings()

        assertTrue(defaults.voiceHistoryEnabled)
        assertTrue(defaults.clipboardHistoryEnabled)
        assertTrue(HistorySettings.DEFAULT_VOICE_HISTORY_ENABLED)
        assertTrue(HistorySettings.DEFAULT_CLIPBOARD_HISTORY_ENABLED)
    }

    @Test
    fun turningASwitchOffStopsOnlyThatHistory() {
        val voiceOff = HistorySettings(voiceHistoryEnabled = false, clipboardHistoryEnabled = true)
        assertFalse(HistoryCapturePolicy.shouldRecordVoice(voiceOff, isSensitiveEditor = false))
        assertTrue(HistoryCapturePolicy.shouldRecordClipboard(voiceOff, isSensitiveEditor = false))

        val clipboardOff = HistorySettings(voiceHistoryEnabled = true, clipboardHistoryEnabled = false)
        assertTrue(HistoryCapturePolicy.shouldRecordVoice(clipboardOff, isSensitiveEditor = false))
        assertFalse(HistoryCapturePolicy.shouldRecordClipboard(clipboardOff, isSensitiveEditor = false))
    }

    @Test
    fun sensitiveEditorsAreNeverRecordedEvenWhenBothSwitchesAreOn() {
        val bothOn = HistorySettings(voiceHistoryEnabled = true, clipboardHistoryEnabled = true)

        assertFalse(HistoryCapturePolicy.shouldRecordVoice(bothOn, isSensitiveEditor = true))
        assertFalse(HistoryCapturePolicy.shouldRecordClipboard(bothOn, isSensitiveEditor = true))
        assertFalse(
            HistoryCapturePolicy.shouldRecordVoice(
                HistorySettings(voiceHistoryEnabled = false),
                isSensitiveEditor = true
            )
        )
        assertFalse(
            HistoryCapturePolicy.shouldRecordClipboard(
                HistorySettings(clipboardHistoryEnabled = false),
                isSensitiveEditor = true
            )
        )
    }

    @Test
    fun theExistingClipboardPolicyStaysConservativeForNormalText() {
        // 只有「整段就是 4–8 位數字」才視為驗證碼；一般含數字的句子仍可保存。
        assertFalse(ClipboardHistoryPolicy.canStore("123456", isSensitiveEditor = false))
        assertFalse(ClipboardHistoryPolicy.canStore(" 1234 ", isSensitiveEditor = false))
        assertTrue(ClipboardHistoryPolicy.canStore("會議室 1234 號", isSensitiveEditor = false))
        assertTrue(ClipboardHistoryPolicy.canStore("訂單編號 A123456", isSensitiveEditor = false))
        assertTrue(ClipboardHistoryPolicy.canStore("2026-10-10 開會", isSensitiveEditor = false))
        assertTrue(ClipboardHistoryPolicy.canStore("驗證碼是 123456 請輸入", isSensitiveEditor = false))
        assertFalse(ClipboardHistoryPolicy.canStore("任何文字", isSensitiveEditor = true))
    }

    @Test
    fun voiceHistoryStillRequiresASuccessfulNonCancelledCommit() {
        assertTrue(
            VoiceHistoryPolicy.canStore(
                rawText = "原始辨識",
                finalText = "最終文字",
                successfulCommit = true,
                cancelled = false,
                isSensitiveEditor = false
            )
        )
        assertFalse(
            VoiceHistoryPolicy.canStore("原始", "最終", true, cancelled = true, isSensitiveEditor = false)
        )
        assertFalse(
            VoiceHistoryPolicy.canStore("原始", "最終", false, false, isSensitiveEditor = false)
        )
        assertFalse(
            VoiceHistoryPolicy.canStore("原始", "最終", true, false, isSensitiveEditor = true)
        )
    }
}
