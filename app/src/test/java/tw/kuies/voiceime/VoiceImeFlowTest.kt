package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceImeFlowTest {
    @Test
    fun idleTransitionsToRecording() {
        val machine = VoiceImeStateMachine()

        assertTrue(machine.transitionTo(VoiceImeState.RECORDING))
        assertEquals(VoiceImeState.RECORDING, machine.state)
    }

    @Test
    fun recordingTransitionsToTranscribing() {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)

        assertTrue(machine.transitionTo(VoiceImeState.TRANSCRIBING))
        assertEquals(VoiceImeState.TRANSCRIBING, machine.state)
    }

    @Test
    fun recordingCanBeCancelled() {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)

        assertTrue(machine.transitionTo(VoiceImeState.CANCELLED))
        assertEquals(VoiceImeState.CANCELLED, machine.state)
    }

    @Test
    fun transcribingCanBeCancelled() {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)
        machine.transitionTo(VoiceImeState.TRANSCRIBING)

        assertTrue(machine.transitionTo(VoiceImeState.CANCELLED))
        assertEquals(VoiceImeState.CANCELLED, machine.state)
    }

    @Test
    fun cancelledRequestCannotCommit() {
        val gate = VoiceImeRequestGate()
        val requestId = gate.begin()
        var commitCount = 0

        gate.invalidate()
        val committed = gate.runIfCurrent(requestId) { commitCount += 1 }

        assertFalse(committed)
        assertEquals(0, commitCount)
    }

    @Test
    fun successReturnsToIdle() {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)
        machine.transitionTo(VoiceImeState.TRANSCRIBING)
        assertTrue(machine.transitionTo(VoiceImeState.SUCCESS))

        assertTrue(machine.transitionTo(VoiceImeState.IDLE))
        assertEquals(VoiceImeState.IDLE, machine.state)
    }

    @Test
    fun formattingFallbackReturnsToIdle() {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)
        machine.transitionTo(VoiceImeState.TRANSCRIBING)
        machine.transitionTo(VoiceImeState.FORMATTING)

        assertTrue(machine.transitionTo(VoiceImeState.FORMATTING_FALLBACK))
        assertTrue(machine.transitionTo(VoiceImeState.IDLE))
        assertEquals(VoiceImeState.IDLE, machine.state)
    }

    @Test
    fun errorReturnsToIdle() {
        val machine = VoiceImeStateMachine()
        assertTrue(machine.transitionTo(VoiceImeState.ERROR))

        assertTrue(machine.transitionTo(VoiceImeState.IDLE))
        assertEquals(VoiceImeState.IDLE, machine.state)
    }
}
