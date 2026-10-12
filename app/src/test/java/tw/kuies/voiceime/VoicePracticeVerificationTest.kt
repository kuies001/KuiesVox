package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePracticeVerificationTest {

    private val appPackage = "tw.kuies.voiceime"

    @Test
    fun realVoiceCommitMatchesSessionAndField() {
        val event = VoiceCommitSuccessEvent(
            sessionId = 12345L,
            packageName = appPackage,
            fieldId = 100,
            fieldName = OnboardingPracticeConfig.PRACTICE_FIELD_NAME,
            privateImeOptions = OnboardingPracticeConfig.PRACTICE_PRIVATE_IME_OPTIONS,
            committedTextLength = 5,
            timestamp = System.currentTimeMillis()
        )
        assertTrue(VoicePracticeVerificationPolicy.isPracticeSuccess(event, appPackage))
    }

    @Test
    fun commitWithMatchingPrivateImeOptionsSucceeds() {
        val event = VoiceCommitSuccessEvent(
            sessionId = 12345L,
            packageName = appPackage,
            fieldId = 100,
            fieldName = "other_name",
            privateImeOptions = OnboardingPracticeConfig.PRACTICE_PRIVATE_IME_OPTIONS,
            committedTextLength = 7,
            timestamp = System.currentTimeMillis()
        )
        assertTrue(VoicePracticeVerificationPolicy.isPracticeSuccess(event, appPackage))
    }

    @Test
    fun commitFromDifferentAppFails() {
        val event = VoiceCommitSuccessEvent(
            sessionId = 12345L,
            packageName = "com.other.app",
            fieldId = 100,
            fieldName = OnboardingPracticeConfig.PRACTICE_FIELD_NAME,
            privateImeOptions = OnboardingPracticeConfig.PRACTICE_PRIVATE_IME_OPTIONS,
            committedTextLength = 10,
            timestamp = System.currentTimeMillis()
        )
        assertFalse(VoicePracticeVerificationPolicy.isPracticeSuccess(event, appPackage))
    }

    @Test
    fun zeroLengthCommitDoesNotPass() {
        val event = VoiceCommitSuccessEvent(
            sessionId = 12345L,
            packageName = appPackage,
            fieldId = 100,
            fieldName = OnboardingPracticeConfig.PRACTICE_FIELD_NAME,
            privateImeOptions = OnboardingPracticeConfig.PRACTICE_PRIVATE_IME_OPTIONS,
            committedTextLength = 0,
            timestamp = System.currentTimeMillis()
        )
        assertFalse(VoicePracticeVerificationPolicy.isPracticeSuccess(event, appPackage))
    }

    @Test
    fun nonMatchingFieldDoesNotPass() {
        val event = VoiceCommitSuccessEvent(
            sessionId = 12345L,
            packageName = appPackage,
            fieldId = 100,
            fieldName = "normal_chat_box",
            privateImeOptions = null,
            committedTextLength = 12,
            timestamp = System.currentTimeMillis()
        )
        assertFalse(VoicePracticeVerificationPolicy.isPracticeSuccess(event, appPackage))
    }

    @Test
    fun eventBusDeliveryWorks() {
        var delivered = false
        val unregister = VoiceInputCommitEventBus.register { event ->
            if (event.sessionId == 999L) delivered = true
        }

        VoiceInputCommitEventBus.post(
            VoiceCommitSuccessEvent(
                sessionId = 999L,
                packageName = appPackage,
                fieldId = 1,
                fieldName = null,
                privateImeOptions = null,
                committedTextLength = 4,
                timestamp = System.currentTimeMillis()
            )
        )
        assertTrue(delivered)

        delivered = false
        unregister()
        VoiceInputCommitEventBus.post(
            VoiceCommitSuccessEvent(
                sessionId = 999L,
                packageName = appPackage,
                fieldId = 1,
                fieldName = null,
                privateImeOptions = null,
                committedTextLength = 4,
                timestamp = System.currentTimeMillis()
            )
        )
        assertFalse(delivered)
    }
}
