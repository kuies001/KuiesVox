package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具列鍵盤按鈕的行為：按鈕本身一律可見，這裡驗證點擊後的切換路徑與回退順序，
 * 以及「除了兩個官方動作之外不做其他事」。
 */
class KeyboardSwitchTest {

    private class RecordingHost(
        private val switchResult: () -> Boolean = { true },
        private val pickerResult: () -> Boolean = { true }
    ) : KeyboardSwitchHost {
        val calls = mutableListOf<String>()

        override fun switchToNextInputMethod(): Boolean {
            calls += "switch"
            return switchResult()
        }

        override fun showSystemInputMethodPicker(): Boolean {
            calls += "picker"
            return pickerResult()
        }
    }

    @Test
    fun offeredDirectSwitchUsesTheNextInputMethodOnly() {
        val host = RecordingHost()

        val outcome = KeyboardSwitcher(host).perform(canSwitchToNext = true)

        assertEquals(KeyboardSwitchOutcome.SWITCHED_TO_NEXT, outcome)
        assertEquals(listOf("switch"), host.calls)
    }

    @Test
    fun unofferedDirectSwitchOpensTheSystemPickerWithoutAttemptingASwitch() {
        val host = RecordingHost()

        val outcome = KeyboardSwitcher(host).perform(canSwitchToNext = false)

        assertEquals(KeyboardSwitchOutcome.SYSTEM_PICKER_REQUESTED, outcome)
        assertEquals(listOf("picker"), host.calls)
    }

    @Test
    fun refusedDirectSwitchFallsBackToTheSystemPicker() {
        val host = RecordingHost(switchResult = { false })

        val outcome = KeyboardSwitcher(host).perform(canSwitchToNext = true)

        assertEquals(KeyboardSwitchOutcome.SYSTEM_PICKER_REQUESTED, outcome)
        assertEquals(listOf("switch", "picker"), host.calls)
    }

    @Test
    fun throwingDirectSwitchFallsBackToTheSystemPickerWithoutCrashing() {
        val host = RecordingHost(switchResult = { throw IllegalStateException("no next input method") })

        val outcome = KeyboardSwitcher(host).perform(canSwitchToNext = true)

        assertEquals(KeyboardSwitchOutcome.SYSTEM_PICKER_REQUESTED, outcome)
        assertEquals(listOf("switch", "picker"), host.calls)
    }

    @Test
    fun refusedPickerIsReportedAsUnavailableWithoutCrashing() {
        val host = RecordingHost(pickerResult = { false })

        val outcome = KeyboardSwitcher(host).perform(canSwitchToNext = false)

        assertEquals(KeyboardSwitchOutcome.UNAVAILABLE, outcome)
        assertEquals(listOf("picker"), host.calls)
    }

    @Test
    fun throwingPickerIsReportedAsUnavailableWithoutCrashing() {
        val host = RecordingHost(pickerResult = { throw IllegalStateException("no input method service") })

        val outcome = KeyboardSwitcher(host).perform(canSwitchToNext = false)

        assertEquals(KeyboardSwitchOutcome.UNAVAILABLE, outcome)
        assertEquals(listOf("picker"), host.calls)
    }

    @Test
    fun refusedSwitchWithAThrowingPickerIsStillReportedAsUnavailable() {
        val host = RecordingHost(
            switchResult = { false },
            pickerResult = { throw IllegalStateException("picker unavailable") }
        )

        val outcome = KeyboardSwitcher(host).perform(canSwitchToNext = true)

        assertEquals(KeyboardSwitchOutcome.UNAVAILABLE, outcome)
        assertEquals(listOf("switch", "picker"), host.calls)
    }

    @Test
    fun switchingOnlyEverTouchesTheTwoHostActions() {
        // 不論哪一種組合，都只會呼叫這兩個動作，且各最多一次，不會有其他副作用
        // （例如動到錄音狀態或已輸入的內容）。
        for (canSwitchToNext in listOf(true, false)) {
            for (switchResult in listOf(true, false)) {
                for (pickerResult in listOf(true, false)) {
                    val host = RecordingHost({ switchResult }, { pickerResult })

                    KeyboardSwitcher(host).perform(canSwitchToNext)

                    assertTrue(host.calls.all { it == "switch" || it == "picker" })
                    assertTrue(host.calls.count { it == "switch" } <= 1)
                    assertTrue(host.calls.count { it == "picker" } <= 1)
                    if (!canSwitchToNext) assertFalse(host.calls.contains("switch"))
                }
            }
        }
    }

    @Test
    fun theHostExposesOnlyTheTwoApisAvailableOnEverySupportedDevice() {
        // minSdk 26 之後 switchToNextInputMethod 與 showInputMethodPicker 都存在；
        // 這個測試讓未來新增平台呼叫時必須明確改動介面，不會偷偷用到較新的 API。
        val hostMethods = KeyboardSwitchHost::class.java.declaredMethods.map { it.name }.sorted()

        assertEquals(listOf("showSystemInputMethodPicker", "switchToNextInputMethod"), hostMethods)
    }

    @Test
    fun thePolicyRoutesOnlyOnTheOfferedFlag() {
        assertEquals(KeyboardSwitchRoute.SWITCH_TO_NEXT, KeyboardSwitchPolicy.routeFor(true))
        assertEquals(KeyboardSwitchRoute.SHOW_SYSTEM_PICKER, KeyboardSwitchPolicy.routeFor(false))
    }
}
