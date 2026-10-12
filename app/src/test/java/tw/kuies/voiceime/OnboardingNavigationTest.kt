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
        state = OnboardingNavigation.goToStep(state, OnboardingStep.PRACTICE)
        assertFalse(OnboardingNavigation.shouldMarkCompleted(state))

        // 第四頁完成或稍後體驗進入獨立完成頁，此時導覽仍處於進行中，尚未標記完成
        state = OnboardingNavigation.showCompletion(state)
        assertTrue(state.isActive)
        assertTrue(state.isCompletionScreen)
        assertFalse(OnboardingNavigation.shouldMarkCompleted(state))

        // 完成頁按下「回到輸入法設定」或「我知道了」走 finish()
        state = OnboardingNavigation.finish(state)
        assertFalse(state.isActive)
        assertFalse(state.isCompletionScreen)
        assertTrue(OnboardingNavigation.shouldMarkCompleted(state))
    }

    @Test
    fun completionScreenCanBeEnteredFromPracticePage() {
        var state = OnboardingNavigation.start(OnboardingEntryPoint.FIRST_RUN)
        state = OnboardingNavigation.goToStep(state, OnboardingStep.PRACTICE)
        assertFalse(state.isCompletionScreen)

        // 使用者點選「完成體驗」或「稍後體驗」
        state = OnboardingNavigation.showCompletion(state)
        assertTrue(state.isCompletionScreen)
        assertTrue(state.showsOnboardingScreen)
        assertTrue(state.isActive)
        assertFalse(OnboardingNavigation.shouldMarkCompleted(state))

        // 切換步驟會重設 isCompletionScreen
        state = OnboardingNavigation.goToStep(state, OnboardingStep.IME)
        assertFalse(state.isCompletionScreen)
    }

    @Test
    fun completionScreenFinishingIsIdempotentAndCleansState() {
        var state = OnboardingNavigation.start(OnboardingEntryPoint.SETTINGS_REVIEW)
        state = OnboardingNavigation.goToStep(state, OnboardingStep.PRACTICE)
        state = OnboardingNavigation.showCompletion(state)
        assertTrue(state.isCompletionScreen)

        // 使用者點選「回到輸入法設定」或「我知道了」
        val finished = OnboardingNavigation.finish(state)
        assertFalse(finished.isActive)
        assertFalse(finished.isCompletionScreen)
        assertFalse(finished.showsOnboardingScreen)
        assertTrue(OnboardingNavigation.shouldMarkCompleted(finished))

        // 重複快速點擊 finish 依然安全處於 idle
        val finishedAgain = OnboardingNavigation.finish(finished)
        assertEquals(finished, finishedAgain)
    }

    @Test
    fun completionScreenDoesNotAllowBackToGroqSettingsDirectly() {
        // 從 Groq 設定頁進入導覽
        val guide = OnboardingNavigation.start(OnboardingEntryPoint.SETTINGS_REVIEW)
        val onPractice = OnboardingNavigation.goToStep(guide, OnboardingStep.PRACTICE)
        val completion = OnboardingNavigation.showCompletion(onPractice)

        // 在完成頁時 backFromGroq 必須回傳 null（不誤觸發回 Groq 第二頁）
        assertNull(OnboardingNavigation.backFromGroq(completion))
        assertTrue(completion.isCompletionScreen)
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
        assertEquals(4, OnboardingStep.entries.size)
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
