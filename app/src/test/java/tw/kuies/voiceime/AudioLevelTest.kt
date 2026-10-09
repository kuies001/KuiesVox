package tw.kuies.voiceime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLevelTest {
    @Test
    fun silenceMapsToZeroAndLargerPcmAmplitudeMapsHigherWithinRange() {
        val silence = pcm16(ShortArray(320))
        val quiet = pcm16(ShortArray(320) { if (it % 2 == 0) 2_000 else -2_000 })
        val loud = pcm16(ShortArray(320) { if (it % 2 == 0) 20_000 else -20_000 })

        val silentLevel = Pcm16AudioLevel.fromPcm16Le(silence)
        val quietLevel = Pcm16AudioLevel.fromPcm16Le(quiet)
        val loudLevel = Pcm16AudioLevel.fromPcm16Le(loud)

        assertEquals(0f, silentLevel, 0f)
        assertTrue(quietLevel > silentLevel)
        assertTrue(loudLevel > quietLevel)
        listOf(silentLevel, quietLevel, loudLevel).forEach { level ->
            assertTrue(level in 0f..1f)
        }
    }

    @Test
    fun emptyOddAndOutOfRangeCountsAreSafe() {
        assertEquals(0f, Pcm16AudioLevel.fromPcm16Le(byteArrayOf()), 0f)
        assertEquals(0f, Pcm16AudioLevel.fromPcm16Le(byteArrayOf(0x7F), 1), 0f)
        assertEquals(0f, Pcm16AudioLevel.fromPcm16Le(byteArrayOf(0x7F, 0x7F), -1), 0f)
        assertEquals(
            Pcm16AudioLevel.fromPcm16Le(byteArrayOf(0x7F, 0x7F)),
            Pcm16AudioLevel.fromPcm16Le(byteArrayOf(0x7F, 0x7F), 99),
            0f
        )
    }

    @Test
    fun pcm16MinimumValueDoesNotOverflowAndInputBytesRemainUnchanged() {
        val pcm = pcm16(shortArrayOf(Short.MIN_VALUE, 0, Short.MIN_VALUE))
        val original = pcm.copyOf()

        val level = Pcm16AudioLevel.fromPcm16Le(pcm)

        assertEquals(1f, level, 0.0001f)
        assertArrayEquals(original, pcm)
    }

    @Test
    fun smootherRisesQuicklyAndFallsGradually() {
        val smoother = AudioLevelSmoother()

        val firstRise = smoother.update(0.8f)
        val secondRise = smoother.update(0.8f)
        val firstFall = smoother.update(0f)
        val secondFall = smoother.update(0f)

        assertTrue(firstRise > 0f)
        assertTrue(secondRise > firstRise)
        assertTrue(firstFall < secondRise)
        assertTrue(firstFall > 0f)
        assertTrue(secondFall < firstFall)
        assertTrue(secondFall > 0f)
    }

    @Test
    fun stopCancelAndRestartClearMeterAndRejectStaleRecordingUpdates() {
        val monitor = AudioLevelMonitor()
        val loud = pcm16(ShortArray(320) { Short.MAX_VALUE })

        monitor.start(1L)
        assertTrue(monitor.observe(1L, loud, loud.size))
        assertTrue(monitor.levelFor(1L) > 0f)
        monitor.stop()
        assertEquals(0f, monitor.levelFor(1L), 0f)
        assertFalse(monitor.observe(1L, loud, loud.size))

        monitor.start(2L)
        assertEquals(0f, monitor.levelFor(2L), 0f)
        assertFalse(monitor.observe(1L, loud, loud.size))
        assertTrue(monitor.observe(2L, loud, loud.size))
        assertTrue(monitor.levelFor(2L) > 0f)
        monitor.stop()
        assertEquals(0f, monitor.levelFor(2L), 0f)
    }

    private fun pcm16(samples: ShortArray): ByteArray = ByteArray(samples.size * 2).also { bytes ->
        samples.forEachIndexed { index, sample ->
            val value = sample.toInt()
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value shr 8).toByte()
        }
    }
}
