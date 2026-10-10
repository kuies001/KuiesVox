package tw.kuies.voiceime

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「前往輸入法設定」的跳轉順序與 fallback：先試官方輸入法設定頁，其次系統設定首頁，
 * 都開不起來時回報失敗讓 UI 提示手動前往（不得閃退）。
 */
class ImeSettingsLauncherTest {

    private class RecordingLauncher(private val openable: Set<String>) : SettingsLauncher {
        val attempts = mutableListOf<String>()

        override fun open(action: String): Boolean {
            attempts += action
            return action in openable
        }
    }

    @Test
    fun thePrimaryTargetIsTheSystemInputMethodSettings() {
        assertEquals(Settings.ACTION_INPUT_METHOD_SETTINGS, ImeSettingsLauncher.CANDIDATE_ACTIONS.first())
        assertTrue(ImeSettingsLauncher.CANDIDATE_ACTIONS.contains(Settings.ACTION_SETTINGS))
    }

    @Test
    fun theInputMethodSettingsPageIsUsedWhenItCanBeOpened() {
        val launcher = RecordingLauncher(
            setOf(Settings.ACTION_INPUT_METHOD_SETTINGS, Settings.ACTION_SETTINGS)
        )

        assertTrue(ImeSettingsLauncher.open(launcher))
        // 第一個就成功，不會再試下一個。
        assertEquals(listOf(Settings.ACTION_INPUT_METHOD_SETTINGS), launcher.attempts)
    }

    @Test
    fun itFallsBackToTheSystemSettingsHome() {
        val launcher = RecordingLauncher(setOf(Settings.ACTION_SETTINGS))

        assertTrue(ImeSettingsLauncher.open(launcher))
        assertEquals(
            listOf(Settings.ACTION_INPUT_METHOD_SETTINGS, Settings.ACTION_SETTINGS),
            launcher.attempts
        )
    }

    @Test
    fun whenNothingCanBeOpenedItReportsFailureInsteadOfCrashing() {
        val launcher = RecordingLauncher(emptySet())

        assertFalse(ImeSettingsLauncher.open(launcher))
        assertEquals(ImeSettingsLauncher.CANDIDATE_ACTIONS, launcher.attempts)
        // 失敗時要能給使用者手動前往的提示。
        assertTrue(ImeSettingsLauncher.MANUAL_HINT.isNotBlank())
    }
}
