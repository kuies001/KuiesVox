package tw.kuies.voiceime

data class ClipboardHistoryEntity(
    val id: String,
    val text: String,
    val createdAt: Long,
    val pinned: Boolean
)

data class VoiceHistoryEntity(
    val id: Long = 0,
    val createdAt: Long,
    val rawText: String,
    val finalText: String
)

internal object EditorPrivacyPolicy {
    private const val INPUT_CLASS_MASK = 0x0000000f
    private const val INPUT_VARIATION_MASK = 0x00000ff0
    private const val INPUT_CLASS_TEXT = 0x00000001
    private const val INPUT_CLASS_NUMBER = 0x00000002
    private const val TEXT_VARIATION_PASSWORD = 0x00000080
    private const val TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    private const val NUMBER_VARIATION_PASSWORD = 0x00000010
    private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000

    fun isSensitive(inputType: Int, imeOptions: Int): Boolean {
        if (imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true

        val inputClass = inputType and INPUT_CLASS_MASK
        val variation = inputType and INPUT_VARIATION_MASK
        return when (inputClass) {
            INPUT_CLASS_TEXT -> variation == TEXT_VARIATION_PASSWORD ||
                variation == TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == TEXT_VARIATION_WEB_PASSWORD
            INPUT_CLASS_NUMBER -> variation == NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}

internal object ClipboardHistoryPolicy {
    const val MAX_UNPINNED_ITEMS = 20

    fun canStore(text: String, isSensitiveEditor: Boolean): Boolean {
        if (isSensitiveEditor || text.isBlank()) return false
        // Short numeric codes are commonly one-time verification codes.
        if (text.trim().matches(Regex("[0-9]{4,8}"))) return false
        return true
    }
}

internal object SystemClipboardCapturePolicy {
    fun shouldRegisterListener(serviceDestroyed: Boolean, isSensitiveEditor: Boolean): Boolean =
        !serviceDestroyed && !isSensitiveEditor

    fun shouldReadClipboard(
        serviceDestroyed: Boolean,
        listenerRegistered: Boolean,
        isSensitiveEditor: Boolean,
        allowWithoutListener: Boolean = false
    ): Boolean = !serviceDestroyed &&
        !isSensitiveEditor &&
        (listenerRegistered || allowWithoutListener)

    fun canCaptureClip(
        itemCount: Int,
        hasPlainText: Boolean,
        hasHtmlText: Boolean,
        isSensitiveClip: Boolean,
        isSensitiveEditor: Boolean
    ): Boolean = itemCount > 0 &&
        (hasPlainText || hasHtmlText) &&
        !isSensitiveClip &&
        !isSensitiveEditor
}

internal class ClipboardListenerLifecycle(
    private val registerListener: () -> Boolean,
    private val unregisterListener: () -> Unit
) {
    var isRegistered: Boolean = false
        private set

    fun registerIfAllowed(allowed: Boolean): Boolean {
        if (!allowed || isRegistered || !registerListener()) return false
        isRegistered = true
        return true
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            unregisterListener()
        } finally {
            isRegistered = false
        }
    }
}

internal object VoiceHistoryPolicy {
    const val MAX_ITEMS = 50

    fun canStore(
        rawText: String,
        finalText: String,
        successfulCommit: Boolean,
        cancelled: Boolean,
        isSensitiveEditor: Boolean
    ): Boolean = successfulCommit && !cancelled && !isSensitiveEditor &&
        rawText.isNotBlank() && finalText.isNotBlank()
}
