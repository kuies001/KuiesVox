package tw.kuies.voiceime

internal enum class HoldToTalkGestureEvent {
    NONE,
    TAP,
    RELEASE_HOLD,
    CANCEL_HOLD
}

internal class HoldToTalkGestureTracker {
    private enum class Phase {
        IDLE,
        PENDING,
        HOLDING,
        CONSUMED
    }

    private var phase = Phase.IDLE
    private var activePointerId: Int? = null

    fun onDown(pointerId: Int): Boolean {
        if (phase != Phase.IDLE) return false
        activePointerId = pointerId
        phase = Phase.PENDING
        return true
    }

    fun onLongPress(pointerId: Int): Boolean {
        if (pointerId != activePointerId || phase != Phase.PENDING) return false
        phase = Phase.HOLDING
        return true
    }

    fun onMove(pointerId: Int, isInside: Boolean): HoldToTalkGestureEvent {
        if (pointerId != activePointerId || isInside) return HoldToTalkGestureEvent.NONE
        val event = if (phase == Phase.HOLDING) {
            HoldToTalkGestureEvent.CANCEL_HOLD
        } else {
            HoldToTalkGestureEvent.NONE
        }
        phase = Phase.CONSUMED
        return event
    }

    fun onUp(pointerId: Int, isInside: Boolean): HoldToTalkGestureEvent {
        if (pointerId != activePointerId) return HoldToTalkGestureEvent.NONE
        val event = when {
            phase == Phase.PENDING && isInside -> HoldToTalkGestureEvent.TAP
            phase == Phase.HOLDING && isInside -> HoldToTalkGestureEvent.RELEASE_HOLD
            phase == Phase.HOLDING -> HoldToTalkGestureEvent.CANCEL_HOLD
            else -> HoldToTalkGestureEvent.NONE
        }
        reset()
        return event
    }

    fun onCancel(pointerId: Int? = activePointerId): HoldToTalkGestureEvent {
        if (pointerId != activePointerId) return HoldToTalkGestureEvent.NONE
        val event = if (phase == Phase.HOLDING) {
            HoldToTalkGestureEvent.CANCEL_HOLD
        } else {
            HoldToTalkGestureEvent.NONE
        }
        reset()
        return event
    }

    fun reset(): HoldToTalkGestureEvent {
        val event = if (phase == Phase.HOLDING) {
            HoldToTalkGestureEvent.CANCEL_HOLD
        } else {
            HoldToTalkGestureEvent.NONE
        }
        phase = Phase.IDLE
        activePointerId = null
        return event
    }

    val isActive: Boolean
        get() = phase == Phase.HOLDING
}
