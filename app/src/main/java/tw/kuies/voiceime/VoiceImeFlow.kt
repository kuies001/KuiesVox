package tw.kuies.voiceime

import okhttp3.Call

internal enum class VoiceImeState(val label: String) {
    IDLE("待命"),
    RECORDING("錄音中…"),
    TRANSCRIBING("正在辨識…"),
    FORMATTING("正在整理文字…"),
    AWAITING_CONFIRM("修改預覽"),
    SUCCESS("完成"),
    FORMATTING_FALLBACK("整理失敗，已使用原文"),
    CANCELLED("已取消"),
    ERROR("發生錯誤")
}

internal class VoiceImeStateMachine {
    @Volatile
    var state: VoiceImeState = VoiceImeState.IDLE
        private set

    @Synchronized
    fun transitionTo(next: VoiceImeState): Boolean {
        val allowed = when (state) {
            VoiceImeState.IDLE -> next == VoiceImeState.RECORDING || next == VoiceImeState.ERROR
            VoiceImeState.RECORDING -> next == VoiceImeState.TRANSCRIBING ||
                next == VoiceImeState.CANCELLED || next == VoiceImeState.ERROR
            VoiceImeState.TRANSCRIBING -> next == VoiceImeState.FORMATTING ||
                next == VoiceImeState.SUCCESS || next == VoiceImeState.FORMATTING_FALLBACK ||
                next == VoiceImeState.CANCELLED || next == VoiceImeState.ERROR
            VoiceImeState.FORMATTING -> next == VoiceImeState.SUCCESS ||
                next == VoiceImeState.FORMATTING_FALLBACK || next == VoiceImeState.AWAITING_CONFIRM ||
                next == VoiceImeState.CANCELLED || next == VoiceImeState.ERROR
            // 修改預覽不自動逾時：等待使用者按下「確認取代」或「取消」。
            VoiceImeState.AWAITING_CONFIRM -> next == VoiceImeState.SUCCESS ||
                next == VoiceImeState.CANCELLED || next == VoiceImeState.ERROR ||
                next == VoiceImeState.IDLE
            VoiceImeState.SUCCESS, VoiceImeState.FORMATTING_FALLBACK,
            VoiceImeState.CANCELLED, VoiceImeState.ERROR ->
                next == VoiceImeState.IDLE
        }
        if (allowed) state = next
        return allowed
    }
}

internal class VoiceImeRequestGate {
    @Volatile
    private var generation = 0L

    @Synchronized
    fun begin(): Long {
        generation += 1
        return generation
    }

    @Synchronized
    fun invalidate(): Long {
        generation += 1
        return generation
    }

    fun isCurrent(requestId: Long): Boolean = requestId != 0L && generation == requestId

    @Synchronized
    fun runIfCurrent(requestId: Long, action: () -> Unit): Boolean {
        if (!isCurrent(requestId)) return false
        action()
        return true
    }
}

internal class ActiveRequestCall {
    private val lock = Any()
    private var activeCall: Call? = null

    fun attach(call: Call) {
        synchronized(lock) { activeCall = call }
    }

    fun clear() {
        synchronized(lock) { activeCall = null }
    }

    fun cancel() {
        val call = synchronized(lock) {
            activeCall.also { activeCall = null }
        }
        call?.cancel()
    }
}
