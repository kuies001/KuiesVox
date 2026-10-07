package tw.kuies.voiceime

/** The small, privacy-conscious subset of InputConnection used by bulk text actions. */
internal interface BulkEditInputConnection {
    fun performSelectAll(): Boolean
    fun getFullTextLength(): Int?
    fun setSelection(start: Int, end: Int): Boolean
    fun sendSelectAllShortcut(): Boolean
    fun commitText(text: CharSequence, newCursorPosition: Int): Boolean
    fun sendDeleteKey(): Boolean
}

internal object BulkTextEditing {
    /** Selects all text without logging or retaining the editor contents. */
    fun selectAll(
        connection: BulkEditInputConnection?,
        allowFullTextFallback: Boolean
    ): Boolean {
        connection ?: return false

        if (attempt { connection.performSelectAll() }) return true

        if (allowFullTextFallback) {
            val fullTextLength = try {
                connection.getFullTextLength()
            } catch (_: Exception) {
                null
            }
            if (fullTextLength != null && fullTextLength >= 0 &&
                attempt { connection.setSelection(0, fullTextLength) }
            ) {
                return true
            }
        }

        return attempt { connection.sendSelectAllShortcut() }
    }

    /** Selects all first, then replaces the selection with empty text. */
    fun clearAll(
        connection: BulkEditInputConnection?,
        allowFullTextFallback: Boolean
    ): Boolean {
        connection ?: return false
        if (!selectAll(connection, allowFullTextFallback)) return false

        if (attempt { connection.commitText("", 1) }) return true

        // The editor still owns the selected range and can handle this standard key safely.
        return attempt { connection.sendDeleteKey() }
    }

    private inline fun attempt(action: () -> Boolean): Boolean = try {
        action()
    } catch (_: Exception) {
        false
    }
}
