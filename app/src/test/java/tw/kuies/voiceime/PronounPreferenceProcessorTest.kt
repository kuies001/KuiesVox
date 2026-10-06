package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Test

class PronounPreferenceProcessorTest {
    @Test
    fun changesMasculinePronounOnlyNearNamedPerson() {
        assertEquals(
            "王慧燕說她今天會到。",
            PronounPreferenceProcessor.apply("王慧燕說他今天會到。", DefaultPronounPreferences.rules)
        )
    }

    @Test
    fun doesNotChangePronounsFarFromNameOrInAnotherSentence() {
        assertEquals(
            "王慧燕和朋友討論了很久，接著又整理了所有資料，最後他才離開。",
            PronounPreferenceProcessor.apply(
                "王慧燕和朋友討論了很久，接著又整理了所有資料，最後他才離開。",
                DefaultPronounPreferences.rules
            )
        )
        assertEquals(
            "王慧燕已經回家。他明天再來。",
            PronounPreferenceProcessor.apply("王慧燕已經回家。他明天再來。", DefaultPronounPreferences.rules)
        )
    }
}
