package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferencesTest {

    private fun existingInstallSignals() = ThemeInstallSignals(
        onboardingCompleted = true,
        firstInstallTimeMillis = 1_000_000L,
        lastUpdateTimeMillis = 1_000_000L,
        nowMillis = 1_000_000L + 90L * 24 * 60 * 60 * 1000
    )

    private fun freshInstallSignals() = ThemeInstallSignals(
        onboardingCompleted = false,
        firstInstallTimeMillis = 1_000_000L,
        lastUpdateTimeMillis = 1_000_000L,
        nowMillis = 1_000_000L + 5_000L
    )

    /** 記憶體版本的儲存層，用來驗證持久化、快取、通知與資料隔離。 */
    private class FakeThemeStore(
        var storedThemeId: String? = null,
        var storedAppearance: String? = null,
        var signals: ThemeInstallSignals,
        var writeSucceeds: Boolean = true
    ) : ThemePreferenceStore {
        val writtenKeys = mutableListOf<String>()
        val writeCount: Int get() = writtenKeys.size / KEYS_PER_WRITE

        /** 模擬其他使用者資料（API Key、歷史、個人化…），主題系統不得碰觸。 */
        val unrelatedUserData = mutableMapOf(
            "groq_api_key" to "gsk_example",
            "voice_history" to "3 items",
            "app_profiles" to "LINE"
        )
        private val listeners = mutableListOf<() -> Unit>()

        override fun readThemeId(): String? = storedThemeId

        override fun readAppearanceMode(): String? = storedAppearance

        override fun writeSelection(selection: ThemeSelection): Boolean {
            if (!writeSucceeds) return false
            storedThemeId = selection.themeStableId
            storedAppearance = selection.appearanceStableId
            writtenKeys += SharedPreferencesThemeStore.THEME_ID_KEY
            writtenKeys += SharedPreferencesThemeStore.APPEARANCE_KEY
            notifyChange()
            return true
        }

        override fun readInstallSignals(): ThemeInstallSignals = signals

        override fun registerChangeListener(listener: () -> Unit): () -> Unit {
            listeners += listener
            return { listeners -= listener }
        }

        fun notifyChange() {
            listeners.toList().forEach { it() }
        }

        private companion object {
            const val KEYS_PER_WRITE = 2
        }
    }

    @Test
    fun storedSelectionIsUsedAsIsWithoutRewritingTheFile() {
        val store = FakeThemeStore(
            storedThemeId = AppThemeId.BRIGHT_SCIFI.stableId,
            storedAppearance = ThemeAppearance.DARK.stableId,
            signals = existingInstallSignals()
        )
        val preferences = ThemePreferences(store)

        val selection = preferences.load()

        assertEquals(AppThemeId.BRIGHT_SCIFI, selection.themeId)
        assertEquals(ThemeAppearance.DARK, selection.appearance)
        // 已有有效偏好時不得重複寫入（避免每次啟動都動檔案）。
        assertTrue("不應該寫入任何 key，實際寫入：${store.writtenKeys}", store.writtenKeys.isEmpty())
    }

    @Test
    fun existingInstallWithoutPreferenceKeepsLegacyWarmAndPersistsIt() {
        val store = FakeThemeStore(signals = existingInstallSignals())
        val preferences = ThemePreferences(store)

        val selection = preferences.load()

        assertEquals(AppThemeId.LEGACY_WARM, selection.themeId)
        assertEquals(ThemeAppearance.SYSTEM, selection.appearance)
        // 解析結果必須寫回，成為穩定的遷移標記。
        assertEquals(AppThemeId.LEGACY_WARM.stableId, store.storedThemeId)
        assertEquals(ThemeAppearance.SYSTEM.stableId, store.storedAppearance)
        assertEquals(1, store.writeCount)
    }

    @Test
    fun freshInstallWithoutPreferenceDefaultsToDailyLiving() {
        val store = FakeThemeStore(signals = freshInstallSignals())
        val preferences = ThemePreferences(store)

        val selection = preferences.load()

        assertEquals(AppThemeId.DAILY_LIVING, selection.themeId)
        assertEquals(AppThemeId.DAILY_LIVING.stableId, store.storedThemeId)
    }

    @Test
    fun corruptStoredValuesFallBackSafelyAndAreRepaired() {
        val store = FakeThemeStore(
            storedThemeId = "definitely-not-a-theme",
            storedAppearance = "night",
            signals = existingInstallSignals()
        )
        val preferences = ThemePreferences(store)

        val selection = preferences.load()

        assertEquals(AppThemeId.LEGACY_WARM, selection.themeId)
        assertEquals(ThemeAppearance.SYSTEM, selection.appearance)
        assertEquals(AppThemeId.LEGACY_WARM.stableId, store.storedThemeId)
        assertEquals(ThemeAppearance.SYSTEM.stableId, store.storedAppearance)
        // 修好之後就不需要再重複寫入。
        store.writtenKeys.clear()
        preferences.load()
        assertTrue(store.writtenKeys.isEmpty())
    }

    @Test
    fun savedSelectionSurvivesARestart() {
        val store = FakeThemeStore(signals = existingInstallSignals())
        val preferences = ThemePreferences(store)
        preferences.save(ThemeSelection(AppThemeId.CUTE_CREAM, ThemeAppearance.LIGHT))

        // 模擬重新啟動：新的物件、同一份儲存。
        val afterRestart = ThemePreferences(store).load()

        assertEquals(AppThemeId.CUTE_CREAM, afterRestart.themeId)
        assertEquals(ThemeAppearance.LIGHT, afterRestart.appearance)
    }

    @Test
    fun cachedValueAvoidsRepeatedStorageReadsButStillRefreshesOnDemand() {
        val store = FakeThemeStore(signals = existingInstallSignals())
        val preferences = ThemePreferences(store)
        assertEquals(AppThemeId.LEGACY_WARM, preferences.cached().themeId)

        // 模擬另一個元件直接改動儲存（不含通知）：快取不得重新讀檔。
        store.storedThemeId = AppThemeId.BRIGHT_SCIFI.stableId
        assertEquals(
            "cached() 不應該重新讀取儲存層",
            AppThemeId.LEGACY_WARM,
            preferences.cached().themeId
        )
        // 需要最新值時仍然讀得到。
        assertEquals(AppThemeId.BRIGHT_SCIFI, preferences.load().themeId)
        assertEquals(AppThemeId.BRIGHT_SCIFI, preferences.cached().themeId)
    }

    @Test
    fun changeNotificationDeliversCurrentAndUpdatedSelections() {
        val store = FakeThemeStore(signals = existingInstallSignals())
        val preferences = ThemePreferences(store)
        val received = mutableListOf<ThemeSelection>()

        val unsubscribe = preferences.observe { received += it }
        assertEquals(1, received.size)
        assertEquals(AppThemeId.LEGACY_WARM, received.last().themeId)

        preferences.setThemeId(AppThemeId.DAILY_LIVING)
        assertEquals(AppThemeId.DAILY_LIVING, received.last().themeId)

        preferences.setAppearance(ThemeAppearance.DARK)
        assertEquals(ThemeAppearance.DARK, received.last().appearance)
        // 切換外觀不得影響主題。
        assertEquals(AppThemeId.DAILY_LIVING, received.last().themeId)

        val countBeforeUnsubscribe = received.size
        unsubscribe()
        preferences.setThemeId(AppThemeId.CUTE_CREAM)
        assertEquals(countBeforeUnsubscribe, received.size)
    }

    @Test
    fun switchingThemeNeverTouchesOtherUserDataOrForeignKeys() {
        val store = FakeThemeStore(signals = existingInstallSignals())
        val preferences = ThemePreferences(store)
        val userDataBefore = store.unrelatedUserData.toMap()

        preferences.setThemeId(AppThemeId.INDUSTRIAL_LIGHT)
        preferences.setAppearance(ThemeAppearance.DARK)

        assertEquals(
            setOf(
                SharedPreferencesThemeStore.THEME_ID_KEY,
                SharedPreferencesThemeStore.APPEARANCE_KEY
            ),
            store.writtenKeys.toSet()
        )
        assertEquals(userDataBefore, store.unrelatedUserData)
    }

    @Test
    fun updatesAreReadModifyWriteSoThemeAndAppearanceDoNotClobberEachOther() {
        val store = FakeThemeStore(signals = existingInstallSignals())
        val preferences = ThemePreferences(store)

        preferences.setAppearance(ThemeAppearance.DARK)
        preferences.setThemeId(AppThemeId.BRIGHT_SCIFI)

        val selection = ThemePreferences(store).load()
        assertEquals(AppThemeId.BRIGHT_SCIFI, selection.themeId)
        assertEquals(ThemeAppearance.DARK, selection.appearance)
    }

    @Test
    fun storageFailureIsReportedAndLeavesTheStoredValueAlone() {
        val store = FakeThemeStore(signals = existingInstallSignals(), writeSucceeds = false)
        val preferences = ThemePreferences(store)

        val failure = runCatching { preferences.save(ThemeSelection(AppThemeId.CUTE_CREAM, ThemeAppearance.SYSTEM)) }
        assertTrue("儲存失敗必須回報錯誤", failure.isFailure)
        assertNotNull(failure.exceptionOrNull())
        assertNull(store.storedThemeId)

        val updateFailure = runCatching { preferences.setThemeId(AppThemeId.CUTE_CREAM) }
        assertTrue(updateFailure.isFailure)
        assertNull(store.storedThemeId)
    }

    @Test
    fun appearanceAndThemeStableIdsRoundTripThroughStorage() {
        val store = FakeThemeStore(signals = existingInstallSignals())
        val preferences = ThemePreferences(store)

        AppThemeId.entries.forEach { themeId ->
            preferences.setThemeId(themeId)
            assertEquals(themeId.stableId, store.storedThemeId)
            assertEquals(themeId, ThemePreferences(store).load().themeId)
        }
        ThemeAppearance.entries.forEach { appearance ->
            preferences.setAppearance(appearance)
            assertEquals(appearance.stableId, store.storedAppearance)
            assertEquals(appearance, ThemePreferences(store).load().appearance)
        }
    }
}
