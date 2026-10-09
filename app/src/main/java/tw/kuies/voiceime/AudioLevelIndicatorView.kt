package tw.kuies.voiceime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

internal class AudioLevelIndicatorView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val barWidth = 5f * density
    private val barGap = 4f * density
    private val minBarHeight = 4f * density
    private val cornerRadius = barWidth / 2f
    private val barFactors = floatArrayOf(0.65f, 0.85f, 1f, 0.85f, 0.65f)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCDC9FF.toInt()
        style = Paint.Style.FILL
    }

    var level: Float = 0f
        private set

    init {
        contentDescription = "麥克風音量指示"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = false
        isClickable = false
    }

    fun setLevel(level: Float) {
        val boundedLevel = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        if (this.level == boundedLevel) return
        this.level = boundedLevel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val totalWidth = barFactors.size * barWidth + (barFactors.size - 1) * barGap
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val maxBarHeight = max(minBarHeight, height - 4f * density)
        val availableHeight = max(0f, maxBarHeight - minBarHeight)

        barFactors.forEachIndexed { index, factor ->
            val barHeight = minBarHeight + availableHeight * level * factor
            val left = startX + index * (barWidth + barGap)
            rect.set(left, centerY - barHeight / 2f, left + barWidth, centerY + barHeight / 2f)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}
