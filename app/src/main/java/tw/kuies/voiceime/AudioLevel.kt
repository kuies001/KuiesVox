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

/**
 * 把正規化的音量（0f–1f）映射成中央錄音圓形按鈕的縮放倍率。
 *
 * 無聲時維持基礎半徑（[BASE_SCALE]），音量最大時放大到 [MAX_SCALE]；
 * 只調整 view 的 scale，因此 layout bounds、觸控區域與 IME 高度都不變。
 */
internal object RecordingCircleScale {
    const val BASE_SCALE = 1f
    const val MAX_SCALE = 1.2f

    /** 以預設上限（v0.15.5 的 1.2 倍）映射。 */
    fun forLevel(level: Float): Float = forLevel(level, MAX_SCALE)

    /**
     * 以主題指定的縮放上限映射；主題的 AnimationStyle 可以要求較大或較小的成長幅度，
     * 但永遠不會小於基礎半徑，也不會無限放大。
     */
    fun forLevel(level: Float, maxScale: Float): Float {
        val bounded = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        val upper = if (maxScale.isFinite()) maxScale.coerceIn(BASE_SCALE, MAX_GROWTH) else BASE_SCALE
        return BASE_SCALE + (upper - BASE_SCALE) * bounded
    }

    /** 任何主題都不得超過的成長上限；維持既有安全區域（不遮擋右側與下方控制項）。 */
    const val MAX_GROWTH = 1.25f
}

/**
 * 錄音視覺化的共用數學。
 *
 * IME 的 [RecordingVisualizerDrawable] 與設定頁預覽都使用這裡的計算，因此預覽與實機
 * 的動態反應一致，而且可以在 JVM 測試中直接驗證「音量變化確實反映在畫面上」。
 */
internal object RecordingVisualScale {
    /** 環形刻度亮起的格數：無聲 0 格、最大音量全亮，且隨音量單調增加。 */
    fun litSegments(level: Float, segmentCount: Int): Int {
        val segments = segmentCount.coerceAtLeast(0)
        if (segments == 0) return 0
        return (boundedLevel(level) * segments).toInt().coerceIn(0, segments)
    }

    /** 訊號波振幅（相對於可用半徑的比例）。 */
    fun waveAmplitudeFraction(level: Float): Float = 0.32f * boundedLevel(level)

    /** 脈動圈半徑（相對於可用半徑的比例）：無聲時仍保留基礎圈，音量越大越外擴。 */
    fun pulseRadiusFraction(step: Float, level: Float): Float {
        val boundedStep = if (step.isFinite()) step.coerceIn(0f, 1f) else 0f
        return boundedStep * (0.82f + 0.18f * boundedLevel(level))
    }

    /** 脈動圈透明度：外圈較淡，且只在有音量時明顯。 */
    fun pulseAlpha(step: Float, level: Float, base: Int = 120): Int {
        val boundedStep = if (step.isFinite()) step.coerceIn(0f, 1f) else 0f
        val scale = (1f - boundedStep) * (0.45f + 0.55f * boundedLevel(level))
        return (base * scale).toInt().coerceIn(0, 255)
    }

    /** 非有限值一律視為無聲，避免任何異常輸入放大動畫。 */
    fun boundedLevel(level: Float): Float =
        if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
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
