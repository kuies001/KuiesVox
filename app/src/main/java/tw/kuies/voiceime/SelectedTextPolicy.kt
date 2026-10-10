package tw.kuies.voiceime

/**
 * 一次「AI 編輯」工作所讀取的選取文字快照。
 * 只存在於當次工作的記憶體中，工作結束／取消／逾時／IME 關閉即丟棄。
 */
internal data class SelectedTextSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val editorTarget: VoiceEditorTargetKey
)

/** 取代前重新讀取的現況，用來與快照比對。 */
internal data class CurrentSelectionState(
    val operationId: Long,
    val editorTarget: VoiceEditorTargetKey?,
    val selectionStart: Int?,
    val selectionEnd: Int?,
    val selectedText: String?
)

internal object SelectedTextPolicy {
    /** 選取文字的長度上限，避免超大內容造成記憶體與 API 成本問題。 */
    const val MAX_SELECTED_CHARACTERS = 4_000

    /** 是否允許讀取目前選取範圍。 */
    fun canReadSelection(
        canInspectEditorText: Boolean,
        isSensitiveEditor: Boolean,
        selectedText: String?
    ): Boolean {
        if (!canInspectEditorText || isSensitiveEditor) return false
        val text = selectedText ?: return false
        if (text.isBlank()) return false
        return text.length <= MAX_SELECTED_CHARACTERS
    }

    /**
     * 選取是否已被取消：有些 App 會因為切換 IME 狀態而取消選取，此時範圍會塌成
     * 游標位置（start == end），或取不到範圍與內容。
     *
     * 這種情況**一律拒絕取代**：不得退回「在游標位置插入」，
     * 也不得改用「搜尋文件中相同文字再取代」的方式補救
     * （文件可能有多段相同內容，會改到錯的地方）。
     */
    fun isSelectionCancelled(current: CurrentSelectionState): Boolean {
        val start = current.selectionStart
        val end = current.selectionEnd
        if (start == null || end == null) return true
        if (start == end) return true
        return current.selectedText.isNullOrEmpty()
    }

    /**
     * 取代前的安全檢查（本階段最高優先）：
     * 必須是同一次作業、同一個輸入欄位、同一段選取範圍、同樣的選取內容。
     * 只比對 packageName 不足以判定安全，同一個 App 可以有多個輸入欄位。
     */
    fun canReplace(
        snapshot: SelectedTextSnapshot,
        expectedOperationId: Long,
        current: CurrentSelectionState
    ): Boolean {
        if (expectedOperationId == 0L) return false
        if (current.operationId != expectedOperationId) return false
        // 選取被取消／塌成游標時，沒有任何可以安全取代的範圍。
        if (isSelectionCancelled(current)) return false
        val target = current.editorTarget ?: return false
        if (!VoiceEditorTargetPolicy.stillTargetsSameEditor(snapshot.editorTarget, target)) return false
        if (current.selectionStart != snapshot.selectionStart) return false
        if (current.selectionEnd != snapshot.selectionEnd) return false
        return current.selectedText == snapshot.text
    }
}
