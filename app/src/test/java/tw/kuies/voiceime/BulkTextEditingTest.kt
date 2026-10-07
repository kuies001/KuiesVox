package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkTextEditingTest {
    @Test
    fun selectAllPrefersEditorContextAction() {
        val editor = FakeEditor("all text")

        assertTrue(BulkTextEditing.selectAll(editor, allowFullTextFallback = true))

        assertEquals(1, editor.contextSelectAllCalls)
        assertEquals(0, editor.readTextLengthCalls)
        assertEquals("all text", editor.text)
        assertEquals(0 to 8, editor.selection)
    }

    @Test
    fun selectAllFallsBackToFullExtractedRange() {
        val editor = FakeEditor("whole field").apply { contextActionSupported = false }

        assertTrue(BulkTextEditing.selectAll(editor, allowFullTextFallback = true))

        assertEquals(1, editor.readTextLengthCalls)
        assertEquals(0 to 11, editor.selection)
        assertEquals(0, editor.selectAllShortcutCalls)
    }

    @Test
    fun sensitiveEditorDoesNotReadTextAndUsesShortcutFallback() {
        val editor = FakeEditor("private").apply {
            contextActionSupported = false
            selectionSupported = false
        }

        assertTrue(BulkTextEditing.selectAll(editor, allowFullTextFallback = false))

        assertEquals(0, editor.readTextLengthCalls)
        assertEquals(1, editor.selectAllShortcutCalls)
        assertEquals("private", editor.text)
    }

    @Test
    fun clearAllSelectsThenCommitsEmptyTextWithoutBackspaceLoop() {
        val editor = FakeEditor("clear this")

        assertTrue(BulkTextEditing.clearAll(editor, allowFullTextFallback = true))

        assertEquals("", editor.text)
        assertEquals(1, editor.commitTextCalls)
        assertEquals(0, editor.deleteKeyCalls)
        assertEquals(0 to 0, editor.selection)
    }

    @Test
    fun clearAllUsesDeleteKeyOnlyWhenCommitIsUnsupported() {
        val editor = FakeEditor("clear this").apply { commitSupported = false }

        assertTrue(BulkTextEditing.clearAll(editor, allowFullTextFallback = true))

        assertEquals("", editor.text)
        assertEquals(1, editor.deleteKeyCalls)
    }

    @Test
    fun emptyAndUnavailableEditorsAreSafe() {
        val emptyEditor = FakeEditor("")

        assertTrue(BulkTextEditing.clearAll(emptyEditor, allowFullTextFallback = true))
        assertEquals("", emptyEditor.text)
        assertFalse(BulkTextEditing.selectAll(null, allowFullTextFallback = true))
        assertFalse(BulkTextEditing.clearAll(null, allowFullTextFallback = true))

        val unsupportedEditor = FakeEditor("untouched").apply {
            contextActionSupported = false
            selectionSupported = false
            shortcutSupported = false
        }
        assertFalse(BulkTextEditing.clearAll(unsupportedEditor, allowFullTextFallback = false))
        assertEquals("untouched", unsupportedEditor.text)
        assertEquals(0, unsupportedEditor.commitTextCalls)
    }

    private class FakeEditor(initialText: String) : BulkEditInputConnection {
        var text = initialText
        private var selectionStart = 0
        private var selectionEnd = 0
        var contextActionSupported = true
        var selectionSupported = true
        var shortcutSupported = true
        var commitSupported = true
        var contextSelectAllCalls = 0
        var readTextLengthCalls = 0
        var selectAllShortcutCalls = 0
        var commitTextCalls = 0
        var deleteKeyCalls = 0
        val selection: Pair<Int, Int>
            get() = selectionStart to selectionEnd

        override fun performSelectAll(): Boolean {
            contextSelectAllCalls += 1
            if (!contextActionSupported) return false
            selectionStart = 0
            selectionEnd = text.length
            return true
        }

        override fun getFullTextLength(): Int {
            readTextLengthCalls += 1
            return text.length
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            if (!selectionSupported) return false
            selectionStart = start
            selectionEnd = end
            return true
        }

        override fun sendSelectAllShortcut(): Boolean {
            selectAllShortcutCalls += 1
            if (!shortcutSupported) return false
            selectionStart = 0
            selectionEnd = text.length
            return true
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            commitTextCalls += 1
            if (!commitSupported) return false
            val start = minOf(selectionStart, selectionEnd)
            val end = maxOf(selectionStart, selectionEnd)
            this.text = this.text.replaceRange(start, end, text.toString())
            selectionStart = start + text.length
            selectionEnd = selectionStart
            return true
        }

        override fun sendDeleteKey(): Boolean {
            deleteKeyCalls += 1
            val start = minOf(selectionStart, selectionEnd)
            val end = maxOf(selectionStart, selectionEnd)
            text = text.removeRange(start, end)
            selectionStart = start
            selectionEnd = start
            return true
        }
    }
}
