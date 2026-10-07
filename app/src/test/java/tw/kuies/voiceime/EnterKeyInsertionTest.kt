package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterKeyInsertionTest {
    @Test
    fun commitsNewlineBeforeConsideringKeyEventFallback() {
        val connection = FakeConnection(commitResult = true)

        assertTrue(EnterKeyInsertion.insertNewline(connection))

        assertEquals(listOf("\n" to 1), connection.commits)
        assertEquals(0, connection.enterKeyEvents)
    }

    @Test
    fun sendsEnterKeyWhenCommitIsRejected() {
        val connection = FakeConnection(commitResult = false, enterKeyResult = true)

        assertTrue(EnterKeyInsertion.insertNewline(connection))

        assertEquals(listOf("\n" to 1), connection.commits)
        assertEquals(1, connection.enterKeyEvents)
    }

    @Test
    fun sendsEnterKeyWhenCommitThrows() {
        val connection = FakeConnection(
            enterKeyResult = true,
            commitException = IllegalStateException("unavailable")
        )

        assertTrue(EnterKeyInsertion.insertNewline(connection))
        assertEquals(1, connection.enterKeyEvents)
    }

    @Test
    fun nullConnectionDoesNotCrashOrSendEvents() {
        assertFalse(EnterKeyInsertion.insertNewline(null))
    }

    private class FakeConnection(
        private val commitResult: Boolean = false,
        private val enterKeyResult: Boolean = false,
        private val commitException: Exception? = null
    ) : EnterInputConnection {
        val commits = mutableListOf<Pair<CharSequence, Int>>()
        var enterKeyEvents = 0

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            commits += text to newCursorPosition
            commitException?.let { throw it }
            return commitResult
        }

        override fun sendEnterKey(): Boolean {
            enterKeyEvents += 1
            return enterKeyResult
        }
    }
}
