package tw.kuies.voiceime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTextPolicyTest {
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

    private fun snapshot() = SelectedTextSnapshot(
        text = selectedText,
        selectionStart = 3,
        selectionEnd = 17,
        editorTarget = target
    )

    private fun current(
        operationId: Long = 99L,
        editorTarget: VoiceEditorTargetKey? = target,
        start: Int? = 3,
        end: Int? = 17,
        text: String? = selectedText
    ) = CurrentSelectionState(operationId, editorTarget, start, end, text)

    @Test
    fun theSelectionIsOnlyReadWhenItIsInspectableNotSensitiveAndBounded() {
        assertTrue(SelectedTextPolicy.canReadSelection(true, false, "文字"))
        assertFalse(SelectedTextPolicy.canReadSelection(false, false, "文字"))
        assertFalse(SelectedTextPolicy.canReadSelection(true, true, "文字"))
        assertFalse(SelectedTextPolicy.canReadSelection(true, false, null))
        assertFalse(SelectedTextPolicy.canReadSelection(true, false, "   "))
        assertFalse(
            SelectedTextPolicy.canReadSelection(
                true,
                false,
                "x".repeat(SelectedTextPolicy.MAX_SELECTED_CHARACTERS + 1)
            )
        )
        assertTrue(
            SelectedTextPolicy.canReadSelection(
                true,
                false,
                "x".repeat(SelectedTextPolicy.MAX_SELECTED_CHARACTERS)
            )
        )
    }

    @Test
    fun replacementIsAllowedOnlyForTheSameOperationFieldRangeAndText() {
        assertTrue(SelectedTextPolicy.canReplace(snapshot(), expectedOperationId = 99L, current()))
    }

    @Test
    fun replacementIsRefusedWhenTheOperationIsNotTheCurrentOne() {
        val expected = snapshot()

        assertFalse(SelectedTextPolicy.canReplace(expected, 99L, current(operationId = 100L)))
        assertFalse(SelectedTextPolicy.canReplace(expected, 0L, current()))
    }

    @Test
    fun replacementIsRefusedWhenTheEditorTargetChanged() {
        val expected = snapshot()

        assertFalse(SelectedTextPolicy.canReplace(expected, 99L, current(editorTarget = null)))
        // 同一個 App 的另一個輸入欄位：只比 packageName 不足以判定安全
        assertFalse(
            SelectedTextPolicy.canReplace(expected, 99L, current(editorTarget = target.copy(fieldId = 43)))
        )
        // 另一個 App
        assertFalse(
            SelectedTextPolicy.canReplace(
                expected,
                99L,
                current(editorTarget = target.copy(packageName = "com.example.mail"))
            )
        )
    }

    @Test
    fun replacementIsRefusedWhenTheSelectionMovedOrChanged() {
        val expected = snapshot()

        assertFalse(SelectedTextPolicy.canReplace(expected, 99L, current(start = 1)))
        assertFalse(SelectedTextPolicy.canReplace(expected, 99L, current(end = 18)))
        assertFalse(SelectedTextPolicy.canReplace(expected, 99L, current(start = null, end = null)))
        assertFalse(SelectedTextPolicy.canReplace(expected, 99L, current(text = "已經改過的文字")))
        assertFalse(SelectedTextPolicy.canReplace(expected, 99L, current(text = null)))
    }
}
