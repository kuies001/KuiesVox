package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 四頁導覽的流程規則：分頁指示、略過、上一步／下一步與完成。 */
class OnboardingFlowTest {

    @Test
    fun theGuideHasFourPages() {
        assertEquals(4, OnboardingFlow.pageCount())
        assertEquals(0, OnboardingFlow.MICROPHONE_PAGE)
        assertEquals(1, OnboardingFlow.GROQ_PAGE)
        assertEquals(2, OnboardingFlow.IME_PAGE)
        assertEquals(3, OnboardingFlow.PRACTICE_PAGE)
    }

    @Test
    fun thePrimaryButtonAdvancesAndFinishesOnTheLastPage() {
        assertEquals("下一步", OnboardingFlow.primaryLabel(OnboardingFlow.MICROPHONE_PAGE))
        assertEquals("下一步", OnboardingFlow.primaryLabel(OnboardingFlow.GROQ_PAGE))
        assertEquals("下一步：試用語音輸入", OnboardingFlow.primaryLabel(OnboardingFlow.IME_PAGE))
        assertEquals("完成體驗", OnboardingFlow.primaryLabel(OnboardingFlow.PRACTICE_PAGE))

        assertFalse(OnboardingFlow.isLastPage(OnboardingFlow.MICROPHONE_PAGE))
        assertFalse(OnboardingFlow.isLastPage(OnboardingFlow.GROQ_PAGE))
        assertFalse(OnboardingFlow.isLastPage(OnboardingFlow.IME_PAGE))
        assertTrue(OnboardingFlow.isLastPage(OnboardingFlow.PRACTICE_PAGE))
    }

    @Test
    fun skipIsOfferedForPagesExceptImePage() {
        assertTrue(OnboardingFlow.showsSkip(OnboardingFlow.MICROPHONE_PAGE))
        assertTrue(OnboardingFlow.showsSkip(OnboardingFlow.GROQ_PAGE))
        assertFalse(OnboardingFlow.showsSkip(OnboardingFlow.IME_PAGE))
        assertTrue(OnboardingFlow.showsSkip(OnboardingFlow.PRACTICE_PAGE))

        assertEquals("略過", OnboardingFlow.skipLabel(OnboardingFlow.MICROPHONE_PAGE))
        assertEquals("略過", OnboardingFlow.skipLabel(OnboardingFlow.GROQ_PAGE))
        assertEquals("稍後體驗", OnboardingFlow.skipLabel(OnboardingFlow.PRACTICE_PAGE))
    }

    @Test
    fun backIsOnlyAvailableAfterTheFirstPage() {
        assertFalse(OnboardingFlow.canGoBack(OnboardingFlow.MICROPHONE_PAGE))
        assertTrue(OnboardingFlow.canGoBack(OnboardingFlow.GROQ_PAGE))
        assertTrue(OnboardingFlow.canGoBack(OnboardingFlow.IME_PAGE))
        assertTrue(OnboardingFlow.canGoBack(OnboardingFlow.PRACTICE_PAGE))
    }

    @Test
    fun nextAndPreviousStayInsideTheGuide() {
        assertEquals(1, OnboardingFlow.nextPage(0))
        assertEquals(2, OnboardingFlow.nextPage(1))
        assertEquals(3, OnboardingFlow.nextPage(2))
        // 最後一頁按下一步不會超出範圍（由呼叫端改成完成）。
        assertEquals(3, OnboardingFlow.nextPage(3))
        // 第一頁沒有上一步。
        assertEquals(0, OnboardingFlow.previousPage(0))
        assertEquals(1, OnboardingFlow.previousPage(2))
        assertEquals(2, OnboardingFlow.previousPage(3))
    }

    @Test
    fun theGroqLinkPointsAtTheOfficialApiKeysPage() {
        assertEquals("https://console.groq.com/keys", OnboardingLinks.GROQ_API_KEYS)
    }
}
