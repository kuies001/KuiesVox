package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Test

/** 測試只使用自造資料：檔案裡沒有任何真實姓名或私人詞彙。 */
class PronounPreferenceProcessorTest {

    private val preference = PronounPreference(
        subject = "小明",
        pronoun = "他",
        preferredPronoun = "她",
        maximumDistanceCodePoints = 12
    )

    @Test
    fun changesPronounOnlyNearTheConfiguredSubject() {
        assertEquals(
            "小明說她今天會到。",
            PronounPreferenceProcessor.apply("小明說他今天會到。", listOf(preference))
        )
    }

    @Test
    fun doesNotChangePronounsFarFromTheSubjectOrInAnotherSentence() {
        assertEquals(
            "小明和朋友討論了很久，接著又整理了所有資料，最後他才離開。",
            PronounPreferenceProcessor.apply(
                "小明和朋友討論了很久，接著又整理了所有資料，最後他才離開。",
                listOf(preference)
            )
        )
        assertEquals(
            "小明已經回家。他明天再來。",
            PronounPreferenceProcessor.apply("小明已經回家。他明天再來。", listOf(preference))
        )
    }

    @Test
    fun withoutAnyConfiguredPreferenceTheTextIsUntouched() {
        assertEquals(
            "小明說他今天會到。",
            PronounPreferenceProcessor.apply("小明說他今天會到。", emptyList())
        )
    }
}
