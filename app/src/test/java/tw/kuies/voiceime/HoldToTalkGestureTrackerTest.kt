package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldToTalkGestureTrackerTest {
    @Test
    fun ordinaryTapStillProducesOneTapAction() {
        val tracker = HoldToTalkGestureTracker()

        assertTrue(tracker.onDown(1))
        assertEquals(HoldToTalkGestureEvent.TAP, tracker.onUp(1, isInside = true))
        assertEquals(HoldToTalkGestureEvent.NONE, tracker.onUp(1, isInside = true))
    }

    @Test
    fun secondTapCanStillStopAnExistingRecording() {
        val tracker = HoldToTalkGestureTracker()
        val stateMachine = VoiceImeStateMachine()

        assertEquals(HoldToTalkGestureEvent.TAP, tap(tracker))
        assertTrue(stateMachine.transitionTo(VoiceImeState.RECORDING))
        assertEquals(HoldToTalkGestureEvent.TAP, tap(tracker))
        assertTrue(stateMachine.transitionTo(VoiceImeState.TRANSCRIBING))
    }

    @Test
    fun longPressStartsRecordingAndReleaseRequestsTranscription() {
        val tracker = HoldToTalkGestureTracker()

        assertTrue(tracker.onDown(3))
        assertTrue(tracker.onLongPress(3))
        assertEquals(
            HoldToTalkGestureEvent.RELEASE_HOLD,
            tracker.onUp(3, isInside = true)
        )
    }

    @Test
    fun longPressReleaseNeverProducesATap() {
        val tracker = HoldToTalkGestureTracker()

        tracker.onDown(4)
        assertTrue(tracker.onLongPress(4))
        assertEquals(HoldToTalkGestureEvent.RELEASE_HOLD, tracker.onUp(4, true))
    }

    @Test
    fun cancelledLongPressStopsWithoutTranscribing() {
        val tracker = HoldToTalkGestureTracker()

        tracker.onDown(5)
        tracker.onLongPress(5)

        assertEquals(HoldToTalkGestureEvent.CANCEL_HOLD, tracker.onCancel(5))
        assertEquals(HoldToTalkGestureEvent.NONE, tracker.onUp(5, true))
    }

    @Test
    fun slidingOutsideAfterHoldCancelsAndDoesNotRestart() {
        val tracker = HoldToTalkGestureTracker()

        tracker.onDown(6)
        tracker.onLongPress(6)

        assertEquals(HoldToTalkGestureEvent.CANCEL_HOLD, tracker.onMove(6, isInside = false))
        assertEquals(HoldToTalkGestureEvent.NONE, tracker.onUp(6, isInside = true))
    }

    @Test
    fun recordingGestureCannotBeReenteredOrStartedTwice() {
        val tracker = HoldToTalkGestureTracker()

        assertTrue(tracker.onDown(7))
        assertTrue(tracker.onLongPress(7))
        assertFalse(tracker.onLongPress(7))
        assertFalse(tracker.onDown(8))
        assertTrue(tracker.isActive)
    }

    @Test
    fun cancellationBeforeLongPressDoesNotLeaveRecordingOrTap() {
        val tracker = HoldToTalkGestureTracker()

        tracker.onDown(9)
        assertEquals(HoldToTalkGestureEvent.NONE, tracker.onCancel(9))
        assertFalse(tracker.isActive)
        assertEquals(HoldToTalkGestureEvent.NONE, tracker.onUp(9, true))
    }

    private fun tap(tracker: HoldToTalkGestureTracker): HoldToTalkGestureEvent {
        assertTrue(tracker.onDown(1))
        return tracker.onUp(1, isInside = true)
    }
}
