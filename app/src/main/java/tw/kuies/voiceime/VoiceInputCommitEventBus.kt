package tw.kuies.voiceime

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 內部安全的語音辨識文字提交成功事件。
 * 不傳遞辨識文字原文，也不暴露 API Key 等敏感資料，僅攜帶目標編輯器 session、欄位標籤與文字長度。
 */
internal data class VoiceCommitSuccessEvent(
    val sessionId: Long,
    val packageName: String?,
    val fieldId: Int,
    val fieldName: String?,
    val privateImeOptions: String?,
    val committedTextLength: Int,
    val timestamp: Long
)

internal object VoiceInputCommitEventBus {
    private val listeners = CopyOnWriteArrayList<(VoiceCommitSuccessEvent) -> Unit>()

    fun post(event: VoiceCommitSuccessEvent) {
        listeners.forEach { it(event) }
    }

    fun register(listener: (VoiceCommitSuccessEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    fun clearListenersForTesting() {
        listeners.clear()
    }
}
