package tw.kuies.voiceime

internal interface EnterInputConnection {
    fun commitText(text: CharSequence, newCursorPosition: Int): Boolean
    fun sendEnterKey(): Boolean
}

internal object EnterKeyInsertion {
    fun insertNewline(connection: EnterInputConnection?): Boolean {
        connection ?: return false

        val committed = try {
            connection.commitText("\n", 1)
        } catch (_: Exception) {
            false
        }
        if (committed) return true

        return try {
            connection.sendEnterKey()
        } catch (_: Exception) {
            false
        }
    }
}
