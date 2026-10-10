package tw.kuies.voiceime

/** 選取範圍（相對整個文字欄位）。 */
internal data class SelectionRange(val start: Int, val end: Int)

/**
 * 只依賴本 App 需要的最小 InputConnection 能力：讀取選取文字與選取範圍。
 * 真實的 IME 端以 InputConnection 實作（getSelectedText / getExtractedText），
 * 測試以 fake 實作，因此不需要 Android 裝置就能驗證讀取規則。
 */
internal interface SelectionReadConnection {
    fun selectedText(): CharSequence?
    fun selectionRange(): SelectionRange?
}

/**
 * 讀取目前真正選取的文字並建立唯讀快照。
 *
 * 任何無法可靠確認的情況都回傳 null，呼叫端必須拒絕進入改寫流程：
 * 沒有 InputConnection、沒有編輯器目標、沒有選取、選取空白、敏感欄位、
 * 編輯器不可檢視、取不到選取範圍、**範圍塌成游標（鍵盤／IME 狀態切換導致選取消失）**、
 * 或超過長度上限。
 *
 * 不會讀取整份文字、也不會讀取其他 App 的內容。
 */
internal object SelectedTextReader {
    fun read(
        connection: SelectionReadConnection?,
        canInspectEditorText: Boolean,
        isSensitiveEditor: Boolean,
        editorTarget: VoiceEditorTargetKey?
    ): SelectedTextSnapshot? {
        if (connection == null || editorTarget == null) return null
        val text = connection.selectedText()?.toString() ?: return null
        if (!SelectedTextPolicy.canReadSelection(canInspectEditorText, isSensitiveEditor, text)) {
            return null
        }
        val range = connection.selectionRange() ?: return null
        if (range.start >= range.end) return null
        return SelectedTextSnapshot(
            text = text,
            selectionStart = range.start,
            selectionEnd = range.end,
            editorTarget = editorTarget
        )
    }

    /**
     * 取代前重新讀取現況：把即時 InputConnection 轉成可與快照比對的 [CurrentSelectionState]。
     * 讀不到範圍或內容時對應欄位為 null，交由 [SelectedTextPolicy.canReplace] 拒絕取代。
     */
    fun readCurrent(
        connection: SelectionReadConnection?,
        operationId: Long,
        editorTarget: VoiceEditorTargetKey?
    ): CurrentSelectionState {
        val range = runCatching { connection?.selectionRange() }.getOrNull()
        val text = runCatching { connection?.selectedText()?.toString() }.getOrNull()
        return CurrentSelectionState(
            operationId = operationId,
            editorTarget = editorTarget,
            selectionStart = range?.start,
            selectionEnd = range?.end,
            selectedText = text
        )
    }
}
