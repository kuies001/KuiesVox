package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 導覽顯示判斷：首次安裝顯示、完成或略過後不再自動顯示、重新查看不會改回未完成。
 */
class OnboardingGateTest {

    private class FakeFlagStore(private var completed: Boolean = false) : OnboardingFlagStore {
        var writes = 0
            private set

        override fun isCompleted(): Boolean = completed

        override fun markCompleted() {
            completed = true
            writes++
        }
    }

    @Test
    fun aFreshInstallShowsTheGuide() {
        assertTrue(OnboardingGate.shouldShowOnStartup(FakeFlagStore()))
    }

    @Test
    fun finishingTheGuideStopsItFromShowingAgain() {
        val store = FakeFlagStore()

        OnboardingGate.complete(store)

        assertFalse(OnboardingGate.shouldShowOnStartup(store))
        assertEquals(1, store.writes)
    }

    @Test
    fun skippingUsesTheSameCompletionFlagAsFinishing() {
        val store = FakeFlagStore()

        // 「略過」與「開始使用」走同一條完成路徑。
        OnboardingGate.complete(store)

        assertTrue(store.isCompleted())
        assertFalse(OnboardingGate.shouldShowOnStartup(store))
    }

    @Test
    fun reopeningTheGuideFromSettingsDoesNotChangeTheFlag() {
        val store = FakeFlagStore(completed = true)

        // 重新查看只是顯示導覽，不會呼叫 complete()，因此沒有任何寫入。
        assertFalse(OnboardingGate.shouldShowOnStartup(store))
        assertEquals(0, store.writes)
        assertTrue(store.isCompleted())
    }

    @Test
    fun theDecisionOnlyDependsOnCompletionAndNeverOnAVersion() {
        // 判斷只吃「是否已完成」：升級版本不會讓已完成的安裝重新顯示導覽。
        val completed = FakeFlagStore(completed = true)
        val fresh = FakeFlagStore(completed = false)

        assertFalse(OnboardingGate.shouldShowOnStartup(completed))
        assertTrue(OnboardingGate.shouldShowOnStartup(fresh))
    }
}
