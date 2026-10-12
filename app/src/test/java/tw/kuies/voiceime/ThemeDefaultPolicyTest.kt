package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDefaultPolicyTest {

    private val now = 1_800_000_000_000L

    private fun signals(
        onboardingCompleted: Boolean = true,
        firstInstallAgeMillis: Long = 90L * 24 * 60 * 60 * 1000,
        installToUpdateDeltaMillis: Long = 90L * 24 * 60 * 60 * 1000
    ) = ThemeInstallSignals(
        onboardingCompleted = onboardingCompleted,
        firstInstallTimeMillis = now - firstInstallAgeMillis,
        lastUpdateTimeMillis = now - firstInstallAgeMillis + installToUpdateDeltaMillis,
        nowMillis = now
    )

    @Test
    fun existingInstallKeepsLegacyWarmWhenNoPreferenceIsStored() {
        val resolved = ThemeDefaultPolicy.resolveThemeId(
            storedRawId = null,
            signals = signals()
        )
        assertEquals(AppThemeId.LEGACY_WARM, resolved)
    }

    @Test
    fun freshInstallDefaultsToDailyLiving() {
        val resolved = ThemeDefaultPolicy.resolveThemeId(
            storedRawId = null,
            signals = signals(onboardingCompleted = false, firstInstallAgeMillis = 30_000L, installToUpdateDeltaMillis = 0L)
        )
        assertEquals(AppThemeId.DAILY_LIVING, resolved)
        assertEquals(AppThemeId.DAILY_LIVING, AppThemeId.DEFAULT_FOR_NEW_INSTALL)
        assertEquals(AppThemeId.LEGACY_WARM, AppThemeId.DEFAULT_FOR_EXISTING_INSTALL)
    }

    @Test
    fun missingKeyAloneNeverCountsAsAFreshInstall() {
        // 升級安裝同樣沒有 theme_id key：完成過導覽且安裝已久 → 既有使用者。
        assertFalse(ThemeDefaultPolicy.isFreshInstall(signals()))
        // 沒有導覽紀錄但安裝已久（舊使用者從未完成導覽）→ 仍是既有使用者。
        assertFalse(
            ThemeDefaultPolicy.isFreshInstall(
                signals(onboardingCompleted = false, firstInstallAgeMillis = 90L * 24 * 60 * 60 * 1000)
            )
        )
        // 剛安裝但已經完成過導覽（例如還原備份）→ 保守視為既有使用者。
        assertFalse(
            ThemeDefaultPolicy.isFreshInstall(
                signals(onboardingCompleted = true, firstInstallAgeMillis = 1_000L, installToUpdateDeltaMillis = 0L)
            )
        )
        // 裝置時間異常（首次安裝時間在未來）→ 保守視為既有使用者。
        assertFalse(
            ThemeDefaultPolicy.isFreshInstall(
                signals(
                    onboardingCompleted = false,
                    firstInstallAgeMillis = -60_000L,
                    installToUpdateDeltaMillis = 0L
                )
            )
        )
    }

    @Test
    fun freshInstallWindowRequiresInstallAndUpdateToBeClose() {
        assertTrue(
            ThemeDefaultPolicy.isFreshInstall(
                signals(
                    onboardingCompleted = false,
                    firstInstallAgeMillis = ThemeDefaultPolicy.FRESH_INSTALL_WINDOW_MS - 1,
                    installToUpdateDeltaMillis = 0L
                )
            )
        )
        // 首次安裝在視窗之外 → 既有使用者。
        assertFalse(
            ThemeDefaultPolicy.isFreshInstall(
                signals(
                    onboardingCompleted = false,
                    firstInstallAgeMillis = ThemeDefaultPolicy.FRESH_INSTALL_WINDOW_MS + 1,
                    installToUpdateDeltaMillis = 0L
                )
            )
        )
        // 安裝與更新相距很遠（升級安裝）→ 既有使用者。
        assertFalse(
            ThemeDefaultPolicy.isFreshInstall(
                signals(
                    onboardingCompleted = false,
                    firstInstallAgeMillis = 1_000L,
                    installToUpdateDeltaMillis = ThemeDefaultPolicy.FRESH_INSTALL_WINDOW_MS + 1
                )
            )
        )
        // 缺少安裝時間（查詢失敗）→ 保守視為既有使用者。
        assertFalse(
            ThemeDefaultPolicy.isFreshInstall(
                ThemeInstallSignals(
                    onboardingCompleted = false,
                    firstInstallTimeMillis = 0L,
                    lastUpdateTimeMillis = 0L,
                    nowMillis = now
                )
            )
        )
    }

    @Test
    fun corruptOrUnknownStoredThemeIdFallsBackSafely() {
        listOf("", "   ", "cute", "CUTE_CREAM", "1", "legacy-warm").forEach { corrupt ->
            assertEquals(
                "損毀值 '$corrupt' 對既有使用者必須回到 Legacy Warm",
                AppThemeId.LEGACY_WARM,
                ThemeDefaultPolicy.resolveThemeId(corrupt, signals())
            )
            assertEquals(
                "損毀值 '$corrupt' 對新安裝必須回到 Daily Living",
                AppThemeId.DAILY_LIVING,
                ThemeDefaultPolicy.resolveThemeId(
                    corrupt,
                    signals(
                        onboardingCompleted = false,
                        firstInstallAgeMillis = 5_000L,
                        installToUpdateDeltaMillis = 0L
                    )
                )
            )
        }
    }

    @Test
    fun validStoredThemeWinsOverEveryDefault() {
        AppThemeId.entries.forEach { themeId ->
            assertEquals(
                themeId,
                ThemeDefaultPolicy.resolveThemeId(themeId.stableId, signals(onboardingCompleted = false))
            )
        }
        // 即使看起來像全新安裝，已儲存的偏好仍然優先。
        assertEquals(
            AppThemeId.BRIGHT_SCIFI,
            ThemeDefaultPolicy.resolveThemeId(
                AppThemeId.BRIGHT_SCIFI.stableId,
                signals(onboardingCompleted = false, firstInstallAgeMillis = 10L, installToUpdateDeltaMillis = 0L)
            )
        )
    }
}
