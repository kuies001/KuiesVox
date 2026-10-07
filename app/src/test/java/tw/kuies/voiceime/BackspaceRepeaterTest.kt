package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackspaceRepeaterTest {
    @Test
    fun startsAfterHoldDelayAndRepeatsAtConfiguredInterval() {
        val scheduler = FakeScheduler()
        var deletions = 0
        val repeater = repeater(scheduler) { deletions += 1 }

        repeater.start()
        scheduler.advanceBy(BackspaceRepeater.INITIAL_DELAY_MS - 1)
        assertEquals(0, deletions)

        scheduler.advanceBy(1)
        assertEquals(1, deletions)
        scheduler.advanceBy(BackspaceRepeater.REPEAT_INTERVAL_MS - 1)
        assertEquals(1, deletions)
        scheduler.advanceBy(1)
        assertEquals(2, deletions)
    }

    @Test
    fun releasingStopsAllPendingRepeats() {
        val scheduler = FakeScheduler()
        var deletions = 0
        val repeater = repeater(scheduler) { deletions += 1 }

        repeater.start()
        scheduler.advanceBy(100)
        assertFalse(repeater.stop())
        scheduler.advanceBy(1_000)

        assertEquals(0, deletions)
    }

    @Test
    fun releaseAfterRepeatReportsItAndStopsImmediately() {
        val scheduler = FakeScheduler()
        var deletions = 0
        val repeater = repeater(scheduler) { deletions += 1 }

        repeater.start()
        scheduler.advanceBy(BackspaceRepeater.INITIAL_DELAY_MS)
        assertTrue(repeater.stop())
        scheduler.advanceBy(1_000)

        assertEquals(1, deletions)
    }

    @Test
    fun serviceBecomingInactivePreventsFurtherRepeats() {
        val scheduler = FakeScheduler()
        var active = true
        var deletions = 0
        val repeater = BackspaceRepeater(
            postDelayed = scheduler::post,
            removeCallbacks = scheduler::remove,
            onDelete = { deletions += 1 },
            canRepeat = { active }
        )

        repeater.start()
        active = false
        scheduler.advanceBy(BackspaceRepeater.INITIAL_DELAY_MS + 1_000)

        assertEquals(0, deletions)
        assertFalse(repeater.stop())
    }

    @Test
    fun serviceDestroyAfterRepeatCancelsTheNextScheduledDeletion() {
        val scheduler = FakeScheduler()
        var active = true
        var deletions = 0
        val repeater = BackspaceRepeater(
            postDelayed = scheduler::post,
            removeCallbacks = scheduler::remove,
            onDelete = { deletions += 1 },
            canRepeat = { active }
        )

        repeater.start()
        scheduler.advanceBy(BackspaceRepeater.INITIAL_DELAY_MS)
        active = false
        repeater.stop()
        scheduler.advanceBy(1_000)

        assertEquals(1, deletions)
    }

    private fun repeater(scheduler: FakeScheduler, onDelete: () -> Unit) = BackspaceRepeater(
        postDelayed = scheduler::post,
        removeCallbacks = scheduler::remove,
        onDelete = onDelete,
        canRepeat = { true }
    )

    private class FakeScheduler {
        private data class Scheduled(val runnable: Runnable, val at: Long)

        private val scheduled = mutableListOf<Scheduled>()
        private var now = 0L

        fun post(runnable: Runnable, delayMs: Long) {
            remove(runnable)
            scheduled += Scheduled(runnable, now + delayMs)
        }

        fun remove(runnable: Runnable) {
            scheduled.removeAll { it.runnable === runnable }
        }

        fun advanceBy(durationMs: Long) {
            val target = now + durationMs
            while (true) {
                val next = scheduled.minByOrNull { it.at } ?: break
                if (next.at > target) break
                scheduled.remove(next)
                now = next.at
                next.runnable.run()
            }
            now = target
        }
    }
}
