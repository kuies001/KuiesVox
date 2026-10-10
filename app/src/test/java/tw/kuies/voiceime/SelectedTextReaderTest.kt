package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTextReaderTest {
    private val target = VoiceEditorTargetKey(
        sessionId = 7L,
        packageName = "com.example.chat",
        fieldId = 42,
        fieldName = "message",
        inputType = 1,
        imeOptions = 0,
        connectionIdentity = Any()
    )

    private val selectedText = "你們到底什麼時候才要處理？"
    private val validRange = SelectionRange(3, 17)

    private class FakeConnection(
        private val text: CharSequence?,
        private val range: SelectionRange?
    ) : SelectionReadConnection {
        override fun selectedText(): CharSequence? = text
        override fun selectionRange(): SelectionRange? = range
    }

    @Test
    fun aRealSelectionBecomesAReadOnlySnapshot() {
        val snapshot = SelectedTextReader.read(
            connection = FakeConnection(selectedText, validRange),
            canInspectEditorText = true,
            isSensitiveEditor = false,
            editorTarget = target
        )

        assertEquals(selectedText, snapshot?.text)
        assertEquals(3, snapshot?.selectionStart)
        assertEquals(17, snapshot?.selectionEnd)
        assertEquals(target, snapshot?.editorTarget)
    }

    @Test
    fun everyUnreliableCaseRefusesToStartAnEdit() {
        // 沒有 InputConnection（欄位已失效）
        assertNull(SelectedTextReader.read(null, true, false, target))
        // 沒有編輯器目標
        assertNull(SelectedTextReader.read(FakeConnection(selectedText, validRange), true, false, null))
        // 沒有選取文字
        assertNull(SelectedTextReader.read(FakeConnection(null, validRange), true, false, target))
        // 空白選取
        assertNull(SelectedTextReader.read(FakeConnection("   ", validRange), true, false, target))
        // 敏感欄位（密碼／PIN）
        assertNull(SelectedTextReader.read(FakeConnection(selectedText, validRange), true, true, target))
        // 編輯器不可檢視
        assertNull(SelectedTextReader.read(FakeConnection(selectedText, validRange), false, false, target))
        // 取不到選取範圍
        assertNull(SelectedTextReader.read(FakeConnection(selectedText, null), true, false, target))
        // 範圍塌成游標：鍵盤／IME 狀態切換導致選取消失，不得當成插入點
        assertNull(
            SelectedTextReader.read(FakeConnection(selectedText, SelectionRange(9, 9)), true, false, target)
        )
        // 反向或無效範圍
        assertNull(
            SelectedTextReader.read(FakeConnection(selectedText, SelectionRange(20, 3)), true, false, target)
        )
        // 超過長度上限
        assertNull(
            SelectedTextReader.read(
                FakeConnection(
                    "x".repeat(SelectedTextPolicy.MAX_SELECTED_CHARACTERS + 1),
                    SelectionRange(0, 10)
                ),
                true,
                false,
                target
            )
        )
    }

    @Test
    fun theBoundaryLengthIsStillAccepted() {
        val boundary = "x".repeat(SelectedTextPolicy.MAX_SELECTED_CHARACTERS)

        val snapshot = SelectedTextReader.read(
            connection = FakeConnection(boundary, SelectionRange(0, boundary.length)),
            canInspectEditorText = true,
            isSensitiveEditor = false,
            editorTarget = target
        )

        assertEquals(boundary, snapshot?.text)
    }

    @Test
    fun theCurrentStateMirrorsTheLiveConnectionForReplacementReview() {
        val current = SelectedTextReader.readCurrent(
            connection = FakeConnection(selectedText, validRange),
            operationId = 99L,
            editorTarget = target
        )

        assertEquals(99L, current.operationId)
        assertEquals(target, current.editorTarget)
        assertEquals(3, current.selectionStart)
        assertEquals(17, current.selectionEnd)
        assertEquals(selectedText, current.selectedText)
    }

    @Test
    fun anUnreadableCurrentSelectionBecomesAFailClosedState() {
        val current = SelectedTextReader.readCurrent(null, 99L, target)

        assertNull(current.selectionStart)
        assertNull(current.selectionEnd)
        assertNull(current.selectedText)
    }

    @Test
    fun theReplacementReviewAcceptsOnlyTheUnchangedSelection() {
        val snapshot = SelectedTextReader.read(
            connection = FakeConnection(selectedText, validRange),
            canInspectEditorText = true,
            isSensitiveEditor = false,
            editorTarget = target
        )

        val unchanged = SelectedTextReader.readCurrent(
            FakeConnection(selectedText, validRange),
            operationId = 99L,
            editorTarget = target
        )
        assertTrue(SelectedTextPolicy.canReplace(snapshot!!, 99L, unchanged))

        // 選取範圍移動：即使文字相同，也不得取代。
        val moved = SelectedTextReader.readCurrent(
            FakeConnection(selectedText, SelectionRange(40, 54)),
            operationId = 99L,
            editorTarget = target
        )
        assertFalse(SelectedTextPolicy.canReplace(snapshot, 99L, moved))

        // 選取被取消：範圍塌成游標，取不到選取文字。
        val cancelled = SelectedTextReader.readCurrent(
            FakeConnection(null, SelectionRange(9, 9)),
            operationId = 99L,
            editorTarget = target
        )
        assertTrue(SelectedTextPolicy.isSelectionCancelled(cancelled))
        assertFalse(SelectedTextPolicy.canReplace(snapshot, 99L, cancelled))
    }
}
