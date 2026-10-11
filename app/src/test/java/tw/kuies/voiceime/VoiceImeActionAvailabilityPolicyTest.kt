package tw.kuies.voiceime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceImeActionAvailabilityPolicyTest {

    @Test
    fun micPermissionDoesNotBlockSettingsOrToolbarWhenNotRecording() {
        val nonRecordingStates = listOf(
            VoiceImeState.IDLE,
            VoiceImeState.SUCCESS,
            VoiceImeState.ERROR,
            VoiceImeState.CANCELLED,
            VoiceImeState.FORMATTING_FALLBACK
        )

        for (state in nonRecordingStates) {
            assertTrue("Settings must be available in state $state", VoiceImeActionAvailabilityPolicy.isSettingsAvailable(state))
            assertTrue("More menu must be available in state $state", VoiceImeActionAvailabilityPolicy.isMoreMenuAvailable(state))
            assertTrue("Clipboard must be available in state $state", VoiceImeActionAvailabilityPolicy.isClipboardHistoryAvailable(state))
            assertTrue("Bulk actions must be available in state $state", VoiceImeActionAvailabilityPolicy.isBulkTextActionAvailable(state))
            assertTrue("Translate shortcut must be available in state $state", VoiceImeActionAvailabilityPolicy.isTranslateShortcutAvailable(state))
            assertTrue("Keyboard switch must be available in state $state", VoiceImeActionAvailabilityPolicy.isKeyboardSwitchAvailable(state))
        }
    }

    @Test
    fun voiceOperationsInFlightBlockDistractingActions() {
        val inFlightStates = listOf(
            VoiceImeState.RECORDING,
            VoiceImeState.TRANSCRIBING,
            VoiceImeState.FORMATTING
        )

        for (state in inFlightStates) {
            assertTrue("State $state must be recognized as in-flight", VoiceImeActionAvailabilityPolicy.isVoiceOperationInFlight(state))
            assertFalse("More menu must be disabled in state $state", VoiceImeActionAvailabilityPolicy.isMoreMenuAvailable(state))
            assertFalse("Clipboard must be disabled in state $state", VoiceImeActionAvailabilityPolicy.isClipboardHistoryAvailable(state))
            assertFalse("Bulk actions must be disabled in state $state", VoiceImeActionAvailabilityPolicy.isBulkTextActionAvailable(state))
            assertFalse("Translate shortcut must be disabled in state $state", VoiceImeActionAvailabilityPolicy.isTranslateShortcutAvailable(state))
            assertFalse("Cannot start new recording when operation is in-flight", VoiceImeActionAvailabilityPolicy.canStartRecording(state, hasMicPermission = true, activeOperationId = 1L))
        }
    }

    @Test
    fun awaitingConfirmBlocksMenuAndBulkActions() {
        val state = VoiceImeState.AWAITING_CONFIRM
        assertTrue(VoiceImeActionAvailabilityPolicy.isAwaitingConfirm(state))
        assertFalse(VoiceImeActionAvailabilityPolicy.isMoreMenuAvailable(state))
        assertFalse(VoiceImeActionAvailabilityPolicy.isClipboardHistoryAvailable(state))
        assertFalse(VoiceImeActionAvailabilityPolicy.isBulkTextActionAvailable(state))
        assertFalse(VoiceImeActionAvailabilityPolicy.isTranslateShortcutAvailable(state))
        assertFalse(VoiceImeActionAvailabilityPolicy.canStartRecording(state, hasMicPermission = true, activeOperationId = 0L))
    }

    @Test
    fun recordingRequiresMicPermissionAndNoActiveOperation() {
        assertTrue(
            "Can start recording when idle, mic granted and no operation",
            VoiceImeActionAvailabilityPolicy.canStartRecording(VoiceImeState.IDLE, hasMicPermission = true, activeOperationId = 0L)
        )
        assertTrue(
            "Can start recording after terminal state, mic granted and no operation",
            VoiceImeActionAvailabilityPolicy.canStartRecording(VoiceImeState.SUCCESS, hasMicPermission = true, activeOperationId = 0L)
        )
        assertFalse(
            "Cannot start recording without mic permission",
            VoiceImeActionAvailabilityPolicy.canStartRecording(VoiceImeState.IDLE, hasMicPermission = false, activeOperationId = 0L)
        )
        assertFalse(
            "Cannot start recording when activeOperationId is present",
            VoiceImeActionAvailabilityPolicy.canStartRecording(VoiceImeState.IDLE, hasMicPermission = true, activeOperationId = 42L)
        )
    }

    @Test
    fun terminalStatesDoNotBlockToolbarActions() {
        val terminalStates = listOf(
            VoiceImeState.SUCCESS,
            VoiceImeState.ERROR,
            VoiceImeState.CANCELLED,
            VoiceImeState.FORMATTING_FALLBACK
        )

        for (state in terminalStates) {
            assertTrue("State $state must be recognized as terminal", VoiceImeActionAvailabilityPolicy.isTerminal(state))
            assertTrue("Settings available in terminal $state", VoiceImeActionAvailabilityPolicy.isSettingsAvailable(state))
            assertTrue("More menu available in terminal $state", VoiceImeActionAvailabilityPolicy.isMoreMenuAvailable(state))
            assertTrue("Clipboard available in terminal $state", VoiceImeActionAvailabilityPolicy.isClipboardHistoryAvailable(state))
            assertTrue("Bulk actions available in terminal $state", VoiceImeActionAvailabilityPolicy.isBulkTextActionAvailable(state))
            assertTrue("Translate shortcut available in terminal $state", VoiceImeActionAvailabilityPolicy.isTranslateShortcutAvailable(state))
        }
    }
}
