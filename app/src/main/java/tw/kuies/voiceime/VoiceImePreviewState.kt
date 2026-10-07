package tw.kuies.voiceime

internal class VoiceImePreviewState {
    var lastCommittedText: String? = null
        private set

    val visibleText: String?
        get() = lastCommittedText?.takeIf { it.isNotBlank() }

    fun recordCommitResult(text: String, committed: Boolean, terminalState: VoiceImeState) {
        if (!committed || text.isBlank()) return
        if (terminalState != VoiceImeState.SUCCESS &&
            terminalState != VoiceImeState.FORMATTING_FALLBACK
        ) return

        lastCommittedText = text
    }
}

internal object VoiceImePreviewLayout {
    const val MAX_LINES = 1
    const val ELLIPSIZE_AT_END = true
    const val DIVIDER_HEIGHT_DP = 1
    const val TEXT_SIZE_SP = 11f

    fun shouldShowPreview(text: String?): Boolean = !text.isNullOrBlank()
}
