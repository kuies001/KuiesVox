package tw.kuies.voiceime

import java.lang.reflect.Proxy
import okhttp3.Call
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
    fun activeHttpRequestIsCancelledAndTheCallTimeoutRemainsBounded() {
        var cancelCount = 0
        val call = Proxy.newProxyInstance(
            Call::class.java.classLoader,
            arrayOf(Call::class.java)
        ) { _, method, _ ->
            if (method.name == "cancel") cancelCount++
            null
        } as Call
        val slot = ActiveRequestCall()

        slot.attach(call)
        slot.cancel()
        slot.cancel()

        assertEquals(1, cancelCount)
        assertEquals(90L, GroqHttpClient.CALL_TIMEOUT_SECONDS)
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

    private fun machineAwaitingConfirmation(): VoiceImeStateMachine {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)
        machine.transitionTo(VoiceImeState.TRANSCRIBING)
        machine.transitionTo(VoiceImeState.FORMATTING)
        assertTrue(machine.transitionTo(VoiceImeState.AWAITING_CONFIRM))
        return machine
    }

    @Test
    fun theAiEditPreviewOnlyAppearsAfterFormatting() {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)
        machine.transitionTo(VoiceImeState.TRANSCRIBING)

        // 尚未改寫就不得跳到預覽。
        assertFalse(machine.transitionTo(VoiceImeState.AWAITING_CONFIRM))
        assertTrue(machine.transitionTo(VoiceImeState.FORMATTING))
        assertTrue(machine.transitionTo(VoiceImeState.AWAITING_CONFIRM))
        assertEquals(VoiceImeState.AWAITING_CONFIRM, machine.state)
    }

    @Test
    fun theAiEditPreviewWaitsForTheUserAndThenReturnsToIdle() {
        val machine = machineAwaitingConfirmation()

        // 確認取代成功後才離開預覽。
        assertTrue(machine.transitionTo(VoiceImeState.SUCCESS))
        assertTrue(machine.transitionTo(VoiceImeState.IDLE))
        assertEquals(VoiceImeState.IDLE, machine.state)
    }

    @Test
    fun theAiEditPreviewCanBeCancelledWithoutReplacingTheText() {
        val machine = machineAwaitingConfirmation()

        assertTrue(machine.transitionTo(VoiceImeState.CANCELLED))
        assertTrue(machine.transitionTo(VoiceImeState.IDLE))
        assertEquals(VoiceImeState.IDLE, machine.state)
    }

    @Test
    fun theAiEditPreviewCannotBeEnteredFromATerminalState() {
        val machine = VoiceImeStateMachine()
        machine.transitionTo(VoiceImeState.RECORDING)
        machine.transitionTo(VoiceImeState.TRANSCRIBING)
        machine.transitionTo(VoiceImeState.SUCCESS)

        assertFalse(machine.transitionTo(VoiceImeState.AWAITING_CONFIRM))
    }
}
