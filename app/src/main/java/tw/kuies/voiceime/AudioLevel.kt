package tw.kuies.voiceime

import kotlin.math.log10
import kotlin.math.sqrt

/** Meter for the PCM16 little-endian samples already read by AudioRecord. */
internal object Pcm16AudioLevel {
    private const val DB_FLOOR = -50.0
    private const val DB_CEILING = -8.0
    private const val FULL_SCALE = 32768.0

    fun fromPcm16Le(bytes: ByteArray, byteCount: Int = bytes.size): Float {
        val boundedCount = byteCount.coerceIn(0, bytes.size)
        val usableCount = boundedCount and -2
        if (usableCount == 0) return 0f

        var sumSquares = 0L
        var offset = 0
        while (offset < usableCount) {
            val low = bytes[offset].toInt() and 0xFF
            val high = bytes[offset + 1].toInt() and 0xFF
            val unsignedSample = low or (high shl 8)
            val sample = if (unsignedSample >= 0x8000) unsignedSample - 0x10000 else unsignedSample
            val value = sample.toLong()
            sumSquares += value * value
            offset += 2
        }

        val sampleCount = usableCount / 2
        val rms = sqrt(sumSquares.toDouble() / sampleCount)
        if (rms == 0.0) return 0f

        val amplitude = rms / FULL_SCALE
        val decibels = 20.0 * log10(amplitude)
        return ((decibels - DB_FLOOR) / (DB_CEILING - DB_FLOOR))
            .toFloat()
            .coerceIn(0f, 1f)
    }
}

internal class AudioLevelSmoother(
    private val attack: Float = 0.55f,
    private val release: Float = 0.18f
) {
    var value: Float = 0f
        private set

    fun update(target: Float): Float {
        val boundedTarget = if (target.isFinite()) target.coerceIn(0f, 1f) else 0f
        val coefficient = if (boundedTarget >= value) attack else release
        value += (boundedTarget - value) * coefficient
        if (kotlin.math.abs(value) < 0.0001f) value = 0f
        return value
    }

    fun reset() {
        value = 0f
    }
}

/** Keeps meter state scoped to the current recording operation. */
internal class AudioLevelMonitor {
    private val smoother = AudioLevelSmoother()

    @Volatile
    private var activeOperationId: Long = 0L

    @Volatile
    private var latestLevel: Float = 0f

    @Synchronized
    fun start(operationId: Long) {
        activeOperationId = operationId
        latestLevel = 0f
        smoother.reset()
    }

    @Synchronized
    fun observe(operationId: Long, pcmBytes: ByteArray, byteCount: Int): Boolean {
        if (operationId == 0L || activeOperationId != operationId) return false
        latestLevel = smoother.update(Pcm16AudioLevel.fromPcm16Le(pcmBytes, byteCount))
        return true
    }

    @Synchronized
    fun levelFor(operationId: Long): Float =
        if (operationId != 0L && activeOperationId == operationId) latestLevel else 0f

    @Synchronized
    fun isActive(operationId: Long): Boolean =
        operationId != 0L && activeOperationId == operationId

    @Synchronized
    fun stop() {
        activeOperationId = 0L
        latestLevel = 0f
        smoother.reset()
    }
}
