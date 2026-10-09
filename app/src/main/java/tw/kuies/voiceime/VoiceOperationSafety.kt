package tw.kuies.voiceime

/** Immutable identity for the editor that initiated an asynchronous voice operation. */
internal data class VoiceEditorTargetKey(
    val sessionId: Long,
    val packageName: String?,
    val fieldId: Int,
    val fieldName: String?,
    val inputType: Int,
    val imeOptions: Int,
    val connectionIdentity: Any?
)

internal object VoiceEditorTargetPolicy {
    fun stillTargetsSameEditor(expected: VoiceEditorTargetKey, current: VoiceEditorTargetKey): Boolean =
        expected.sessionId == current.sessionId &&
            expected.packageName == current.packageName &&
            expected.fieldId == current.fieldId &&
            expected.fieldName == current.fieldName &&
            expected.inputType == current.inputType &&
            expected.imeOptions == current.imeOptions &&
            expected.connectionIdentity != null &&
            expected.connectionIdentity === current.connectionIdentity
}
