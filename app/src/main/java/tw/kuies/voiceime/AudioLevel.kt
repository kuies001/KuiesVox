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
    /** 環形刻度亮起的格數：無聲時仍保底亮起 [minLit] 格（低活動量），最大音量全亮。 */
    fun litSegments(level: Float, segmentCount: Int, minLit: Int = 0): Int {
        val segments = segmentCount.coerceAtLeast(0)
        if (segments == 0) return 0
        val baseline = minLit.coerceIn(0, segments)
        val lit = (boundedLevel(level) * segments).toInt().coerceIn(0, segments)
        return maxOf(lit, baseline)
    }

    /** 訊號波振幅（相對於可用半徑的比例）。 */
    fun waveAmplitudeFraction(level: Float): Float = 0.32f * boundedLevel(level)

    /**
     * 脈動圈半徑（相對於可用半徑的比例）：無聲時仍保留基礎圈，音量越大越外擴。
     * 上限刻意壓在 0.74–0.92，讓最外圈加上線寬後仍在安全半徑內（不會被圓形邊緣裁切）。
     */
    fun pulseRadiusFraction(step: Float, level: Float): Float {
        val boundedStep = if (step.isFinite()) step.coerceIn(0f, 1f) else 0f
        return boundedStep * (0.74f + 0.18f * boundedLevel(level))
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

/** 一個同心圓／脈動圈；半徑與線寬都是可用半徑的倍率。 */
internal data class VisualRing(val radiusFraction: Float, val strokeFraction: Float, val alpha: Int)

/** 一格環形刻度；角度以 12 點鐘方向為 0、順時針為正。 */
internal data class VisualTick(val angleRadians: Float, val innerFraction: Float, val outerFraction: Float)

/** 電波紋：以極座標點列表示，振幅已含入音量。 */
internal data class VisualWave(val points: List<Pair<Float, Float>>, val strokeFraction: Float, val alpha: Int)

/**
 * 一個錄音視覺化的完整繪製計畫。
 *
 * 所有座標都是「可用半徑的倍率」，因此與裝置密度、實際尺寸無關，
 * 可以在 JVM 測試中驗證：四種樣式互不相同、隨音量單調變化、且永遠不超出安全半徑。
 */
internal data class RecordingVisualPlan(
    val rings: List<VisualRing> = emptyList(),
    val ticks: List<VisualTick> = emptyList(),
    val wave: VisualWave? = null
) {
    val isEmpty: Boolean get() = rings.isEmpty() && ticks.isEmpty() && wave == null

    private val tickOuter: Float get() = ticks.maxOfOrNull { it.outerFraction + TICK_HALF_STROKE } ?: 0f
    private val ringOuter: Float get() = rings.maxOfOrNull { it.radiusFraction + it.strokeFraction / 2f } ?: 0f
    private val waveOuter: Float
        get() = wave?.let { shape -> shape.points.maxOfOrNull { it.second + shape.strokeFraction / 2f } ?: 0f } ?: 0f

    /** 圖形最遠到達的半徑倍率（含線寬）；必須 ≤ 1f（不得超出保留的視覺區域）。 */
    val maxRadiusFraction: Float
        get() = maxOf(tickOuter, ringOuter, waveOuter)

    private companion object {
        /** 刻度線的線寬（可用半徑的倍率），與繪製時使用的一致。 */
        const val TICK_HALF_STROKE = 0.0375f
    }
}

/**
 * 依主題樣式與音量產生繪製計畫。
 *
 * IME 的 `RecordingVisualizerDrawable` 與設定頁預覽都使用這一份計畫，
 * 因此預覽與實機的圖形完全一致，也可以在沒有裝置的情況下驗證幾何正確性。
 */
internal object RecordingVisualPlanner {
    /**
     * 主錄音鈕「視覺圓」的半徑比例。
     *
     * 中央按鈕的視覺圓比觸控區小，外圈（[BUTTON_SURFACE_RADIUS_FRACTION]–1.0）保留給
     * 主題的環形刻度與電波紋，因此刻度不會壓在主按鈕表面上。
     * 這個值同時被 IME 面板與設定頁預覽使用，避免兩邊硬編碼出不同外觀。
     * **觸控區不變**：改變的只有視覺圓，view 大小與 hit target 完全不動。
     */
    const val BUTTON_SURFACE_RADIUS_FRACTION = 0.81f

    /** 環形刻度帶：完全落在主按鈕視覺圓之外，且外緣含線寬仍在安全半徑內。 */
    const val TICK_INNER = 0.84f
    private const val TICK_OUTER = 0.95f

    /** 無聲時仍亮起的刻度數（Industrial 的低活動量）。 */
    const val METER_MIN_LIT = 2

    fun planFor(
        style: RecordingVisualStyle,
        level: Float,
        segmentCount: Int = 0,
        cycles: Int = 0
    ): RecordingVisualPlan {
        val volume = RecordingVisualScale.boundedLevel(level)
        return when (style) {
            RecordingVisualStyle.SOFT_PULSE -> {
                val count = cycles.coerceAtLeast(1)
                RecordingVisualPlan(
                    rings = (0 until count).map { index ->
                        val step = (index + 1f) / count
                        VisualRing(
                            radiusFraction = RecordingVisualScale.pulseRadiusFraction(step, volume),
                            strokeFraction = 0.06f,
                            alpha = RecordingVisualScale.pulseAlpha(step, volume)
                        )
                    }
                )
            }

            RecordingVisualStyle.SEGMENTED_METER_RING -> {
                val segments = segmentCount.coerceAtLeast(1)
                val lit = RecordingVisualScale.litSegments(volume, segments, METER_MIN_LIT)
                RecordingVisualPlan(
                    ticks = (0 until lit).map { index ->
                        VisualTick(
                            angleRadians = (-Math.PI / 2 + 2 * Math.PI * index / segments).toFloat(),
                            innerFraction = TICK_INNER,
                            outerFraction = TICK_OUTER
                        )
                    }
                )
            }

            RecordingVisualStyle.SIGNAL_WAVE -> {
                // 電波紋畫成一圈「被音量調變的環」，因此一定看得見、且不會超出保留區域。
                val points = (0 until WAVE_POINTS).map { index ->
                    val angle = 2.0 * Math.PI * index / WAVE_POINTS
                    val modulation = Math.sin(angle * WAVE_CYCLES)
                    val radius = WAVE_BASE_RADIUS +
                        WAVE_AMPLITUDE * RecordingVisualScale.waveAmplitudeFraction(volume) * modulation
                    angle.toFloat() to radius.toFloat()
                }
                RecordingVisualPlan(
                    wave = VisualWave(
                        points = points,
                        strokeFraction = 0.075f,
                        alpha = (140 + 115 * volume).toInt().coerceIn(0, 255)
                    )
                )
            }

            RecordingVisualStyle.SMOOTH_SCALE -> RecordingVisualPlan(
                // 低調的單一圈：只有輕微邊緣變化，不搶過主按鈕的縮放。
                rings = listOf(
                    VisualRing(
                        radiusFraction = 0.9f,
                        strokeFraction = 0.045f,
                        alpha = (40 + 90 * volume).toInt().coerceIn(0, 255)
                    )
                )
            )

            RecordingVisualStyle.LEGACY -> RecordingVisualPlan()
        }
    }

    private const val WAVE_POINTS = 48
    private const val WAVE_CYCLES = 4.0

    /** 電波紋同樣畫在主按鈕視覺圓之外的環形帶上。 */
    private const val WAVE_BASE_RADIUS = 0.9f
    private const val WAVE_AMPLITUDE = 0.09f
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
