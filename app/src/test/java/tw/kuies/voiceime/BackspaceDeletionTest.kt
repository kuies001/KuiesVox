package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackspaceDeletionTest {
    @Test
    fun deletesOneChineseCharacter() {
        val editor = FakeEditor("測試", cursor = 2)

        assertTrue(BackspaceDeletion.deleteOne(editor))

        assertEquals("測", editor.text)
        assertEquals(1, editor.codePointDeleteCalls)
    }

    @Test
    fun deletesOneEnglishLetter() {
        val editor = FakeEditor("voice", cursor = 5)

        assertTrue(BackspaceDeletion.deleteOne(editor))

        assertEquals("voic", editor.text)
    }

    @Test
    fun deletesEmojiAsOneCodePointInsteadOfHalfASurrogatePair() {
        val editor = FakeEditor("A😀", cursor = 3)

        assertTrue(BackspaceDeletion.deleteOne(editor))

        assertEquals("A", editor.text)
        assertEquals(0, editor.utf16DeleteCalls)
    }

    @Test
    fun deletesSelectedTextBeforeTryingBackspace() {
        val editor = FakeEditor("中ABC文", cursor = 4, selectionStart = 1, selectionEnd = 4)

        assertTrue(BackspaceDeletion.deleteOne(editor))

        assertEquals("中文", editor.text)
        assertEquals(1, editor.commitTextCalls)
        assertEquals(0, editor.codePointDeleteCalls)
    }

    @Test
    fun fallbackCountsBothUtf16UnitsForEmoji() {
        val editor = FakeEditor("A😀", cursor = 3, codePointDeletionSupported = false)

        assertTrue(BackspaceDeletion.deleteOne(editor))

        assertEquals("A", editor.text)
        assertEquals(2, editor.lastUtf16DeleteLength)
    }

    @Test
    fun cursorAtBeginningAndMissingConnectionAreSafe() {
        val editor = FakeEditor("文字", cursor = 0)

        BackspaceDeletion.deleteOne(editor)
        assertEquals("文字", editor.text)
        assertFalse(BackspaceDeletion.deleteOne(null))

        val unsupportedEditor = FakeEditor(
            "文字",
            cursor = 0,
            codePointDeletionSupported = false
        )
        BackspaceDeletion.deleteOne(unsupportedEditor)
        assertEquals("文字", unsupportedEditor.text)
        assertEquals(1, unsupportedEditor.deleteKeyCalls)
    }

    @Test
    fun unavailableSurroundingTextFallsBackToEditorDeleteKey() {
        val editor = FakeEditor("text", cursor = 4).apply {
            codePointDeletionSupported = false
            surroundingTextAvailable = false
        }

        assertTrue(BackspaceDeletion.deleteOne(editor))

        assertEquals("tex", editor.text)
        assertEquals(1, editor.deleteKeyCalls)
    }

    private class FakeEditor(
        var text: String,
        cursor: Int,
        selectionStart: Int = cursor,
        selectionEnd: Int = cursor,
        var codePointDeletionSupported: Boolean = true
    ) : BackspaceInputConnection {
        var cursor = cursor
            private set
        var selectionStart = selectionStart
            private set
        var selectionEnd = selectionEnd
            private set
        var surroundingTextAvailable = true
        var codePointDeleteCalls = 0
        var utf16DeleteCalls = 0
        var lastUtf16DeleteLength = 0
        var commitTextCalls = 0
        var deleteKeyCalls = 0

        override fun getSelectedText(): CharSequence =
            text.substring(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd))

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            commitTextCalls += 1
            val start = minOf(selectionStart, selectionEnd)
            val end = maxOf(selectionStart, selectionEnd)
            this.text = this.text.replaceRange(start, end, text.toString())
            cursor = start + text.length
            selectionStart = cursor
            selectionEnd = cursor
            return true
        }

        override fun deleteSurroundingTextInCodePoints(
            beforeLength: Int,
            afterLength: Int
        ): Boolean {
            codePointDeleteCalls += 1
            if (!codePointDeletionSupported) return false
            if (cursor == 0) return true
            val start = Character.offsetByCodePoints(text, cursor, -beforeLength)
            val end = Character.offsetByCodePoints(text, cursor, afterLength)
            text = text.removeRange(start, end)
            cursor = start
            selectionStart = cursor
            selectionEnd = cursor
            return true
        }

        override fun getTextBeforeCursor(maxChars: Int): CharSequence? =
            if (surroundingTextAvailable) text.substring(maxOf(0, cursor - maxChars), cursor) else null

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            utf16DeleteCalls += 1
            lastUtf16DeleteLength = beforeLength
            if (beforeLength > cursor) return false
            val start = cursor - beforeLength
            val end = minOf(text.length, cursor + afterLength)
            text = text.removeRange(start, end)
            cursor = start
            selectionStart = cursor
            selectionEnd = cursor
            return true
        }

        override fun sendDeleteKey(): Boolean {
            deleteKeyCalls += 1
            if (cursor == 0) return false
            val start = Character.offsetByCodePoints(text, cursor, -1)
            text = text.removeRange(start, cursor)
            cursor = start
            selectionStart = cursor
            selectionEnd = cursor
            return true
        }
    }
}
