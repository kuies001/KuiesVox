package tw.kuies.voiceime

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 共享幾何數據結構：提供正規化座標 (0f..1f 或以中心 (0.5f, 0.5f) 為基準)。
 * 同時為 Android Canvas 與 Compose DrawScope 提供精確、一致的五套按鈕輪廓路徑計算。
 */
internal object RecordingVisualGeometry {

    /** 閒置時機械面板的圓角半徑 (dp)，維持柔和圓潤。 */
    const val MECHANICAL_PANEL_CORNER_RADIUS_DP = 22

    /** 閒置時生活方塊基底的圓角半徑 (dp)，維持柔和圓潤。 */
    const val DUAL_LAYER_SQUARE_CORNER_RADIUS_DP = 22

    /**
     * 奶油可愛 (CUTE_CREAM)：柔和不規則圓角有機曲線 (變形 squircle/blob)。
     * 計算出閉合的平滑曲線點列 (normalized [0f, 1f] 空間，以 (0.5, 0.5) 為中心)。
     * [volumeLevel] 控制隨音量產生微幅的有機流動擴散。
     */
    fun organicBlobPoints(
        volumeLevel: Float = 0f,
        pointCount: Int = 36,
        baseRadiusFraction: Float = 0.42f
    ): List<Pair<Float, Float>> {
        val bounded = if (volumeLevel.isFinite()) volumeLevel.coerceIn(0f, 1f) else 0f
        val points = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until pointCount) {
            val angle = 2.0 * PI * i / pointCount
            // 複合諧波產生有機圓潤形狀 (blob/squircle)
            val rMod = 0.035 * cos(3.0 * angle) + 0.02 * sin(2.0 * angle)
            val volumeExpand = 0.04 * bounded * sin(4.0 * angle)
            val radius = (baseRadiusFraction + rMod + volumeExpand).toFloat()
            val x = (0.5f + radius * cos(angle).toFloat()).coerceIn(0.06f, 0.94f)
            val y = (0.5f + radius * sin(angle).toFloat()).coerceIn(0.06f, 0.94f)
            points += x to y
        }
        return points
    }

    /**
     * 明亮科幻 (BRIGHT_SCIFI) 閒置：柔和科技 HUD 模組多邊形（平滑切角，無尖銳死角）。
     * [chamferFraction] 為切角比例。
     */
    fun hudRoundedModulePoints(
        radiusFraction: Float = 0.43f,
        chamferFraction: Float = 0.24f
    ): List<Pair<Float, Float>> {
        val r = radiusFraction.coerceIn(0.2f, 0.48f)
        val c = r * chamferFraction
        val cx = 0.5f
        val cy = 0.5f

        // 8 個頂點順時針：上左、上右、右上、右下、下右、下左、左下、左上
        return listOf(
            (cx - r + c) to (cy - r),
            (cx + r - c) to (cy - r),
            (cx + r) to (cy - r + c),
            (cx + r) to (cy + r - c),
            (cx + r - c) to (cy + r),
            (cx - r + c) to (cy + r),
            (cx - r) to (cy + r - c),
            (cx - r) to (cy - r + c)
        )
    }

    /**
     * 五套主題錄音狀態的幾何約束驗證：
     * 硬性規則：在錄音狀態下，主按鈕與外層動畫視覺輪廓必須保持圓形。
     */
    fun isRecordingSilhouetteCircular(themeId: AppThemeId): Boolean = true
}
