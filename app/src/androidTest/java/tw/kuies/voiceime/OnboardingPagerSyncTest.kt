package tw.kuies.voiceime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.kuies.voiceime.ui.theme.VoiceIMETheme

/**
 * 新手導覽的 Pager 同步迴歸測試（TASK A）。
 *
 * 缺陷：OnboardingScreen 同時使用外部 `step` 與內部 `pagerState`，而新建的 Pager
 * 預設 settledPage = 0，第一次 snapshotFlow 就把外部狀態反向覆寫成第一頁，
 * 造成「Groq 儲存成功後回到第一頁」。
 *
 * 這些測試直接驗證：以第三頁開啟時看得見第三頁內容、且不會把步驟回報成第一頁。
 */
@RunWith(AndroidJUnit4::class)
class OnboardingPagerSyncTest {

    @get:Rule
    val rule = createComposeRule()

    private fun showScreen(
        step: OnboardingStep,
        imeEnablement: ImeEnablement = ImeEnablement.NOT_ENABLED,
        onStepChange: (OnboardingStep) -> Unit = {}
    ) {
        rule.setContent {
            VoiceIMETheme {
                OnboardingScreen(
                    step = step,
                    hasMicrophonePermission = true,
                    imeEnablement = imeEnablement,
                    imeSettingsHint = "",
                    onStepChange = onStepChange,
                    onRequestMicrophonePermission = {},
                    onOpenGroq = {},
                    onOpenApiKeySettings = {},
                    onOpenImeSettings = {},
                    onSwitchToKuiesVox = {},
                    onFinish = {}
                )
            }
        }
    }

    /** 測試 1＋2：以第三頁初始化時，必須直接顯示第三頁的兩張步驟卡。 */
    @Test
    fun openingOnTheImeStepShowsTheThirdPageAndNotTheEarlierPages() {
        showScreen(OnboardingStep.IME)
        rule.waitForIdle()

        rule.onNodeWithText("步驟 1：開啟 KuiesVox").assertIsDisplayed()
        rule.onNodeWithText("步驟 2：選擇 KuiesVox").assertIsDisplayed()
        // 其他頁面的內容不得出現。
        rule.onNodeWithText("申請 Groq API").assertDoesNotExist()
    }

    /** 測試 3：初始化、靜置後都不得把第三頁回報成第一頁。 */
    @Test
    fun theThirdStepIsNeverOverwrittenByTheInitialPagerPosition() {
        var reported: OnboardingStep? = null
        showScreen(OnboardingStep.IME) { reported = it }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)

        assertNull("不應該回報任何步驟變更（尤其不得回報第一頁）", reported)
    }

    /** 測試 4：外部切頁後仍停在目標頁，不會被舊的 settledPage 覆寫。 */
    @Test
    fun changingTheExternalStepMovesThePagerAndKeepsItThere() {
        var reported: OnboardingStep? = null
        var step by mutableStateOf(OnboardingStep.MICROPHONE)
        rule.setContent {
            VoiceIMETheme {
                OnboardingScreen(
                    step = step,
                    hasMicrophonePermission = true,
                    imeEnablement = ImeEnablement.NOT_ENABLED,
                    imeSettingsHint = "",
                    onStepChange = { reported = it },
                    onRequestMicrophonePermission = {},
                    onOpenGroq = {},
                    onOpenApiKeySettings = {},
                    onOpenImeSettings = {},
                    onSwitchToKuiesVox = {},
                    onFinish = {}
                )
            }
        }
        rule.waitForIdle()

        // 模擬「Groq 儲存成功 → 第三頁」。
        step = OnboardingStep.IME
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)

        rule.onNodeWithText("步驟 1：開啟 KuiesVox").assertIsDisplayed()
        assertNull("程式主動切頁不得被回報成使用者操作", reported)
    }

    /** 測試 5：第三頁三種輸入法狀態的兩個步驟卡內容。 */
    @Test
    fun pageThreeReflectsEveryImeEnablementState() {
        showScreen(OnboardingStep.IME, imeEnablement = ImeEnablement.NOT_ENABLED)
        rule.waitForIdle()
        rule.onNodeWithText("尚未啟用").assertIsDisplayed()
        rule.onNodeWithText("請先完成步驟 1").assertIsDisplayed()

        rule.runOnIdle { }
        showScreen(OnboardingStep.IME, imeEnablement = ImeEnablement.ENABLED)
        rule.waitForIdle()
        rule.onNodeWithText("尚未切換").assertIsDisplayed()
        rule.onNodeWithText("選擇輸入法").assertIsDisplayed()

        rule.runOnIdle { }
        showScreen(OnboardingStep.IME, imeEnablement = ImeEnablement.DEFAULT)
        rule.waitForIdle()
        // 兩步都完成：不再顯示任何動作按鈕，只顯示完成狀態。
        rule.onNodeWithText("前往鍵盤設定").assertDoesNotExist()
        rule.onNodeWithText("選擇輸入法").assertDoesNotExist()
        rule.onNodeWithText("步驟 1：開啟 KuiesVox").assertIsDisplayed()
        rule.onNodeWithText("步驟 2：選擇 KuiesVox").assertIsDisplayed()
    }

    @Test
    fun theGuideStillOffersSkipOnEveryPage() {
        listOf(OnboardingStep.MICROPHONE, OnboardingStep.GROQ, OnboardingStep.IME).forEach { step ->
            showScreen(step)
            rule.waitForIdle()
            if (step != OnboardingStep.IME) {
                rule.onNodeWithText("略過").assertIsDisplayed()
            } else {
                // 最後一頁提供「開始使用」。
                rule.onNodeWithText("開始使用").assertIsDisplayed()
            }
        }
        assertEquals(OnboardingStep.entries.size, OnboardingFlow.pageCount())
    }
}
