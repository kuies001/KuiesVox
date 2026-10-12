package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingNavigationTest {

    private fun firstRun() = OnboardingNavigation.start(OnboardingEntryPoint.FIRST_RUN)

    @Test
    fun firstRunStartsOnTheMicrophonePageAndShowsTheGuide() {
        val state = firstRun()
        assertTrue(state.isActive)
        assertTrue(state.showsOnboardingScreen)
        assertEquals(OnboardingStep.MICROPHONE, state.step)
        assertEquals(0, state.step.index)
        assertFalse(state.groqSettingsForOnboarding)
        assertFalse(state.awaitingApiKeySave)
    }

    @Test
    fun openingGroqFromTheGuideKeepsTheGuideAliveAndDoesNotMarkItCompleted() {
        val state = OnboardingNavigation.openGroqFromOnboarding(
            OnboardingNavigation.goToStep(firstRun(), OnboardingStep.GROQ)
        )

        // 這正是先前的缺陷：開啟 Groq 設定時整份導覽被提前結束並標記完成。
        assertTrue("導覽必須仍然存在", state.isActive)
        assertFalse("導覽尚未完成，不得標記", OnboardingNavigation.shouldMarkCompleted(state))
        assertEquals(OnboardingStep.GROQ, state.step)
        assertTrue(state.groqSettingsForOnboarding)
        // Groq 設定頁接管畫面，導覽畫面暫時不顯示。
        assertFalse(state.showsOnboardingScreen)
    }

    @Test
    fun savingTheKeyReturnsToTheGuideOnTheImePage() {
        var state = OnboardingNavigation.openGroqFromOnboarding(
            OnboardingNavigation.goToStep(firstRun(), OnboardingStep.GROQ)
        )
        state = OnboardingNavigation.onApiKeySaveStarted(state)
        assertTrue(state.awaitingApiKeySave)

        state = OnboardingNavigation.onApiKeySaveSucceeded(state)

        assertEquals(OnboardingStep.IME, state.step)
        assertEquals(2, state.step.index)
        assertTrue("成功後必須自動回到導覽", state.showsOnboardingScreen)
        assertTrue(state.isActive)
        assertFalse(state.groqSettingsForOnboarding)
        assertFalse(state.awaitingApiKeySave)
        assertFalse("儲存成功仍不算完成導覽", OnboardingNavigation.shouldMarkCompleted(state))
    }

    @Test
    fun aFailedSaveStaysOnGroqAndDoesNotAdvanceTheGuide() {
        val opened = OnboardingNavigation.openGroqFromOnboarding(
            OnboardingNavigation.goToStep(firstRun(), OnboardingStep.GROQ)
        )
        var state = OnboardingNavigation.onApiKeySaveStarted(opened)
        state = OnboardingNavigation.onApiKeySaveFailed(state)

        // 留在 Groq 設定頁、仍是導覽流程的一部分、步驟不前進。
        assertTrue(state.groqSettingsForOnboarding)
        assertFalse(state.showsOnboardingScreen)
        assertFalse(state.awaitingApiKeySave)
        assertEquals(OnboardingStep.GROQ, state.step)
        assertNotNull(OnboardingNavigation.backFromGroq(state))
    }

    @Test
    fun backingOutOfGroqReturnsToTheSecondGuidePage() {
        val state = OnboardingNavigation.openGroqFromOnboarding(
            OnboardingNavigation.goToStep(firstRun(), OnboardingStep.GROQ)
        )

        val back = OnboardingNavigation.backFromGroq(state)

        assertNotNull(back)
        assertTrue(back!!.showsOnboardingScreen)
        assertEquals("必須回到第二頁，而不是設定首頁", OnboardingStep.GROQ, back.step)
        assertFalse(back.groqSettingsForOnboarding)
        assertTrue(back.isActive)
    }

    @Test
    fun groqOpenedFromSettingsDoesNotTriggerTheGuide() {
        val idle = OnboardingNavigation.idle()
        val opened = OnboardingNavigation.openGroqFromOnboarding(idle)

        // 一般設定頁進入 Groq 時導覽本來就是關閉的，因此：
        assertFalse("一般設定頁進入 Groq 不得被標成導覽來源", opened.groqSettingsForOnboarding)
        assertFalse(opened.isActive)
        // 返回交由設定頁處理（回首頁），不觸發導覽。
        assertNull(OnboardingNavigation.backFromGroq(opened))
        // 儲存成功／失敗都不會啟動導覽，也不會把使用者丟回導覽任何一頁。
        assertFalse(OnboardingNavigation.onApiKeySaveSucceeded(opened).isActive)
        assertFalse(OnboardingNavigation.onApiKeySaveFailed(opened).isActive)
        assertFalse(OnboardingNavigation.onApiKeySaveSucceeded(opened).showsOnboardingScreen)
    }

    @Test
    fun onlyFinishingOrSkippingMarksTheGuideCompleted() {
        val idle = OnboardingNavigation.idle()
        assertTrue(OnboardingNavigation.shouldMarkCompleted(idle))

        var state = firstRun()
        assertFalse(OnboardingNavigation.shouldMarkCompleted(state))

        // 逐步前進、回首頁再回來，都不算完成。
        state = OnboardingNavigation.goToStep(state, OnboardingStep.GROQ)
        state = OnboardingNavigation.goToStep(state, OnboardingStep.IME)
        assertFalse(OnboardingNavigation.shouldMarkCompleted(state))

        // 最後一頁按「開始使用」或任何一頁按「略過」都走 finish()。
        state = OnboardingNavigation.finish(state)
        assertFalse(state.isActive)
        assertTrue(OnboardingNavigation.shouldMarkCompleted(state))
    }

    @Test
    fun reviewingFromSettingsKeepsTheGuideActiveUntilTheUserFinishesAgain() {
        val review = OnboardingNavigation.start(OnboardingEntryPoint.SETTINGS_REVIEW)

        // 重新查看只是再次顯示導覽，不會把完成狀態改回未完成。
        assertTrue(review.showsOnboardingScreen)
        assertFalse(OnboardingNavigation.shouldMarkCompleted(review))
        assertEquals(OnboardingEntryPoint.SETTINGS_REVIEW, review.entryPoint)
        // 再次結束時仍然只會寫入「已完成」。
        assertTrue(OnboardingNavigation.shouldMarkCompleted(OnboardingNavigation.finish(review)))
    }

    @Test
    fun everyGuidePageMapsToItsOwnNavigationStep() {
        assertEquals(3, OnboardingStep.entries.size)
        OnboardingStep.entries.forEach { step ->
            assertEquals(step, OnboardingStep.fromIndex(step.index))
        }
        assertEquals(OnboardingStep.MICROPHONE, OnboardingStep.fromIndex(-1))
        assertEquals(OnboardingStep.MICROPHONE, OnboardingStep.fromIndex(99))
    }

    @Test
    fun reOpeningGroqFromOnboardingClearsAStaleWaitingFlag() {
        var state = OnboardingNavigation.openGroqFromOnboarding(
            OnboardingNavigation.goToStep(firstRun(), OnboardingStep.GROQ)
        )
        state = OnboardingNavigation.onApiKeySaveStarted(state)
        assertTrue(state.awaitingApiKeySave)

        // 使用者返回後再次進入：不得殘留「等待儲存中」。
        state = OnboardingNavigation.backFromGroq(state)!!
        state = OnboardingNavigation.openGroqFromOnboarding(state)
        assertFalse(state.awaitingApiKeySave)
    }
}

class ImeEnablementPolicyTest {

    private val appPackage = "tw.kuies.voiceime"
    private val appClass = "tw.kuies.voiceime.VoiceImeService"

    private fun evaluate(enabled: List<String>, default: String?) =
        ImeEnablementPolicy.evaluate(enabled, default, appPackage, appClass)

    @Test
    fun reportsNotEnabledWhenKuiesVoxIsMissingFromTheEnabledList() {
        assertEquals(
            ImeEnablement.NOT_ENABLED,
            evaluate(listOf("com.samsung.android.honeyboard/.service.HoneyBoardService"), null)
        )
        assertEquals(ImeEnablement.NOT_ENABLED, evaluate(emptyList(), null))
        assertEquals(
            ImeEnablement.NOT_ENABLED,
            evaluate(listOf("com.samsung.android.honeyboard/.service.HoneyBoardService"), "tw.kuies.voiceime/.VoiceImeService")
        )
    }

    @Test
    fun reportsEnabledWhenPresentButNotTheDefault() {
        assertEquals(
            ImeEnablement.ENABLED,
            evaluate(
                listOf("com.samsung.android.honeyboard/.service.HoneyBoardService", "tw.kuies.voiceime/.VoiceImeService"),
                "com.samsung.android.honeyboard/.service.HoneyBoardService"
            )
        )
        assertEquals(
            ImeEnablement.ENABLED,
            evaluate(listOf("tw.kuies.voiceime/.VoiceImeService"), null)
        )
    }

    @Test
    fun reportsDefaultOnlyForOurOwnInputMethodBeingCurrent() {
        assertEquals(
            ImeEnablement.DEFAULT,
            evaluate(listOf("tw.kuies.voiceime/.VoiceImeService"), "tw.kuies.voiceime/.VoiceImeService")
        )
        assertEquals(
            ImeEnablement.DEFAULT,
            evaluate(
                listOf("com.samsung.android.honeyboard/.service.HoneyBoardService", "tw.kuies.voiceime/.VoiceImeService"),
                "tw.kuies.voiceime/tw.kuies.voiceime.VoiceImeService"
            )
        )
    }

    @Test
    fun componentIdFormsAreCanonicalisedBeforeComparing() {
        val expected = "tw.kuies.voiceime/tw.kuies.voiceime.VoiceImeService"
        assertEquals(expected, ImeEnablementPolicy.canonicalize("tw.kuies.voiceime/.VoiceImeService"))
        assertEquals(expected, ImeEnablementPolicy.canonicalize("tw.kuies.voiceime/tw.kuies.voiceime.VoiceImeService"))
        assertEquals(expected, ImeEnablementPolicy.canonicalize("  tw.kuies.voiceime/.VoiceImeService  "))
        assertEquals("", ImeEnablementPolicy.canonicalize(""))
        assertEquals("", ImeEnablementPolicy.canonicalize("   "))
        assertEquals("no-separator", ImeEnablementPolicy.canonicalize("no-separator"))
        // 相對類名（無前置點）也要展開成完整類名。
        assertEquals(expected, ImeEnablementPolicy.canonicalize("tw.kuies.voiceime/VoiceImeService"))
    }

    @Test
    fun anotherAppWithTheSameClassNameIsNotMistakenForOurs() {
        assertEquals(
            ImeEnablement.NOT_ENABLED,
            evaluate(listOf("com.example.clone/.VoiceImeService"), "com.example.clone/.VoiceImeService")
        )
        assertEquals(
            ImeEnablement.NOT_ENABLED,
            evaluate(listOf("tw.kuies.voiceime.other/.VoiceImeService"), null)
        )
    }
}
