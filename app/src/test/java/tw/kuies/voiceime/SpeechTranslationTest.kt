package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationTargetLanguageTest {
    @Test
    fun cyclingVisitsEveryLanguageOnceAndWrapsAround() {
        var language = TranslationTargetLanguage.DEFAULT
        val visited = mutableListOf(language)
        repeat(TranslationTargetLanguage.entries.size - 1) {
            language = language.next()
            visited += language
        }

        assertEquals(TranslationTargetLanguage.entries.size, visited.distinct().size)
        assertEquals(TranslationTargetLanguage.DEFAULT, language.next())
    }

    @Test
    fun aStoredChoiceIsRestoredAndUnknownValuesFallBackToTheDefault() {
        TranslationTargetLanguage.entries.forEach { language ->
            assertEquals(language, TranslationTargetLanguage.fromStoredValue(language.storageValue))
        }

        assertEquals(TranslationTargetLanguage.DEFAULT, TranslationTargetLanguage.fromStoredValue(null))
        assertEquals(TranslationTargetLanguage.DEFAULT, TranslationTargetLanguage.fromStoredValue("  "))
        assertEquals(TranslationTargetLanguage.DEFAULT, TranslationTargetLanguage.fromStoredValue("klingon"))
    }
}

class SpeechTranslationPolicyTest {
    private val source = "你明天有空嗎？"

    @Test
    fun aQuestionIsTranslatedAndItsAnswerIsNeverTheOutput() {
        val outcome = SpeechTranslationPolicy.resolve(
            sourceText = source,
            translatedText = "Are you free tomorrow?",
            targetStillValid = true,
            alreadyInserted = false
        )

        assertEquals(SpeechTranslationOutcome.Insert("Are you free tomorrow?"), outcome)
    }

    @Test
    fun aBlankResultIsRefusedAndNeverFallsBackToTheTranscript() {
        assertEquals(
            SpeechTranslationOutcome.Refuse(copyableText = null),
            SpeechTranslationPolicy.resolve(source, "   ", targetStillValid = true, alreadyInserted = false)
        )
        assertEquals(
            SpeechTranslationOutcome.Refuse(copyableText = null),
            SpeechTranslationPolicy.resolve(source, null, targetStillValid = true, alreadyInserted = false)
        )
    }

    @Test
    fun aStaleTargetOrAnAlreadyInsertedResultIsRefusedButStillCopyable() {
        val translated = "Are you free tomorrow?"

        assertEquals(
            SpeechTranslationOutcome.Refuse(translated),
            SpeechTranslationPolicy.resolve(source, translated, targetStillValid = false, alreadyInserted = false)
        )
        assertEquals(
            SpeechTranslationOutcome.Refuse(translated),
            SpeechTranslationPolicy.resolve(source, translated, targetStillValid = true, alreadyInserted = true)
        )
    }

    @Test
    fun theInsertedTextIsTrimmed() {
        assertEquals(
            SpeechTranslationOutcome.Insert("Are you free tomorrow?"),
            SpeechTranslationPolicy.resolve(source, "  Are you free tomorrow?  ", targetStillValid = true, alreadyInserted = false)
        )
    }
}

class SingleInsertionClaimTest {
    @Test
    fun theSameOperationCanOnlyBeClaimedOnce() {
        val claim = SingleInsertionClaim()

        assertFalse(claim.isClaimed(7L))
        assertTrue(claim.claim(7L))
        assertTrue(claim.isClaimed(7L))
        assertFalse(claim.claim(7L))
        assertTrue(claim.claim(8L))
    }

    @Test
    fun theZeroOperationIsNeverClaimed() {
        val claim = SingleInsertionClaim()

        assertFalse(claim.claim(0L))
        assertFalse(claim.isClaimed(0L))
    }
}
