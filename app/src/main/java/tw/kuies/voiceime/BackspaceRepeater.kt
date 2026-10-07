package tw.kuies.voiceime

/** Schedules hold-repeat work without tying it to a coroutine or service-owned scope. */
internal class BackspaceRepeater(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val onDelete: () -> Unit,
    private val canRepeat: () -> Boolean
) {
    companion object {
        const val INITIAL_DELAY_MS = 450L
        const val REPEAT_INTERVAL_MS = 80L
    }

    private var isPressed = false
    private var hasRepeated = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!isPressed || !canRepeat()) {
                stop()
                return
            }
            hasRepeated = true
            onDelete()
            if (isPressed && canRepeat()) postDelayed(this, REPEAT_INTERVAL_MS)
            else stop()
        }
    }

    fun start() {
        if (isPressed || !canRepeat()) return
        isPressed = true
        hasRepeated = false
        postDelayed(repeatRunnable, INITIAL_DELAY_MS)
    }

    /** Stops pending work and reports whether a repeat deletion already happened. */
    fun stop(): Boolean {
        isPressed = false
        removeCallbacks(repeatRunnable)
        return hasRepeated
    }
}
