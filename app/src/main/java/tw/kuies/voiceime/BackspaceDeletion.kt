package tw.kuies.voiceime

/** Minimal editor operations needed to delete text, exposed separately for JVM testing. */
internal interface BackspaceInputConnection {
    fun getSelectedText(): CharSequence?
    fun commitText(text: CharSequence, newCursorPosition: Int): Boolean
    fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean
    fun getTextBeforeCursor(maxChars: Int): CharSequence?
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
    fun sendDeleteKey(): Boolean
}

internal object BackspaceDeletion {
    /** Deletes the selection, or exactly one Unicode code point before the cursor. */
    fun deleteOne(connection: BackspaceInputConnection?): Boolean {
        if (connection == null) return false

        val selectedText = try {
            connection.getSelectedText()
        } catch (_: Exception) {
            null
        }
        if (!selectedText.isNullOrEmpty()) {
            return try {
                connection.commitText("", 1)
            } catch (_: Exception) {
                false
            }
        }

        val deletedByCodePoint = try {
            connection.deleteSurroundingTextInCodePoints(beforeLength = 1, afterLength = 0)
        } catch (_: Exception) {
            false
        }
        if (deletedByCodePoint) return true

        val textBeforeCursor = try {
            connection.getTextBeforeCursor(maxChars = 2)
        } catch (_: Exception) {
            null
        }
        if (textBeforeCursor != null) {
            val utf16Length = trailingCodePointUtf16Length(textBeforeCursor)
            if (utf16Length > 0) {
                val deletedByUtf16 = try {
                    connection.deleteSurroundingText(beforeLength = utf16Length, afterLength = 0)
                } catch (_: Exception) {
                    false
                }
                if (deletedByUtf16) return true
            }
        }

        // Some secure/custom editors do not expose surrounding text. Their DEL key handling
        // remains the safest fallback because the editor decides how to remove the character.
        return try {
            connection.sendDeleteKey()
        } catch (_: Exception) {
            false
        }
    }

    /** Returns 2 for a trailing valid surrogate pair, 1 for any other final UTF-16 unit. */
    fun trailingCodePointUtf16Length(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        val last = text[text.length - 1]
        return if (Character.isLowSurrogate(last) && text.length >= 2 &&
            Character.isHighSurrogate(text[text.length - 2])
        ) {
            2
        } else {
            1
        }
    }
}
