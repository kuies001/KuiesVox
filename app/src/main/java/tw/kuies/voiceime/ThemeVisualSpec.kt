package tw.kuies.voiceime

/**
 * KuiesVox 的主題「視覺規格」。
 *
 * Color Tokens（[ThemePalette]）只描述顏色；這裡描述造型語言：圖示線條風格、按鈕與邊框
 * 處理、表面層次、錄音視覺化方式與動畫參數。集中在一個註冊表，避免每個 View 各自判斷 themeId。
 *
 * 重要：這些規格只影響「繪製」，不得改變任何量測結果（尺寸、位置、觸控範圍）。
 * 幾何仍完全由 [ThemePalette] 的圓角 Token 與既有的固定 Layout Geometry 決定。
 */

/** 圖示線條與外框風格。 */
internal enum class IconStyle {
    /** 柔和、較粗的圓角線條（奶油可愛）。 */
    SOFT_ROUNDED,

    /** 幾何機能風格（明亮工業）。 */
    GEOMETRIC_FUNCTIONAL,

    /** 細線、科技介面風格（明亮科幻）。 */
    THIN_TECHNICAL,

    /** 簡潔平衡（明亮生活）。 */
    CLEAN_BALANCED,

    /** v0.15.5 原樣，不做任何外框處理。 */
    NEUTRAL
}

/** 按鈕造型風格。 */
internal enum class ButtonStyle {
    /** 柔軟的圓潤外觀。 */
    SOFT_PILL,

    /** 較硬朗的幾何邊框與分層。 */
    HARD_GEOMETRIC,

    /** 簡潔的 HUD 邊框。 */
    HUD_OUTLINE,

    /** 低調柔和的圓角。 */
    SOFT_SUBTLE,

    /** v0.15.5 原樣。 */
    NEUTRAL
}

/**
 * 中央錄音主按鈕的幾何外輪廓造型。
 *
 * 五套主題擁有各自獨立的實體鍵帽／模組輪廓，不再全部看起來像圓形：
 * - [ORGANIC_CURVE]: 柔和不規則圓角有機曲線（變形微圓弧），具蓬鬆感（奶油可愛）。
 * - [MECHANICAL_PANEL]: 圓角矩形機械面板基底，中央機能按鍵（明亮工業）。
 * - [HUD_MODULE]: 科技 HUD 模組（八角形／切角多邊形幾何）（明亮科幻）。
 * - [DUAL_LAYER_SQUARE]: 圓角方塊基底搭配同心圓（明亮生活）。
 * - [CIRCULAR]: 經典標準正圓形（經典溫暖 Legacy）。
 */
internal enum class ButtonSilhouette {
    ORGANIC_CURVE,
    MECHANICAL_PANEL,
    HUD_MODULE,
    DUAL_LAYER_SQUARE,
    CIRCULAR
}

/** 邊框處理風格。 */
internal enum class BorderStyle {
    NONE,
    HAIRLINE,
    FUNCTIONAL_FRAME,
    HUD_CORNERS,
    SUBTLE_LINE,
    NEUTRAL
}

/** 表面層次風格。 */
internal enum class SurfaceStyle {
    /** 輕柔陰影與外圈變化（奶油可愛）。 */
    SOFT_ELEVATED,

    /** 機能分區與機械按鍵分層（明亮工業）。 */
    LAYERED_PANELS,

    /** 極淡的玻璃感分層（明亮科幻）。 */
    GLASS_TINT,

    /** 自然留白、不加層次（明亮生活）。 */
    CLEAN,

    /** v0.15.5 原樣。 */
    NEUTRAL
}

/** 錄音時的視覺化方式；所有樣式共用同一份音量資料。 */
internal enum class RecordingVisualStyle {
    /** 柔和擴散的光暈脈動。 */
    SOFT_PULSE,

    /** 環形音量刻度，音量越大亮起越多。 */
    SEGMENTED_METER_RING,

    /** 電波紋／訊號波，振幅依音量改變。 */
    SIGNAL_WAVE,

    /** 平滑的圓形縮放（低調，無額外圖形）。 */
    SMOOTH_SCALE,

    /** v0.15.5 的行為：只有原本的縮放回饋。 */
    LEGACY
}

/**
 * 動畫參數。
 *
 * [maxScale]／[smoothing] 只影響縮放回饋與平滑度；[respectReducedMotion] 表示在系統關閉動畫時
 * 必須跳過視覺化繪製（只保留靜態外觀）。
 */
internal data class AnimationStyle(
    val maxScale: Float,
    val smoothing: Float,
    val pulseCycles: Int,
    val respectReducedMotion: Boolean = true
)

/** 一個主題的完整視覺規格。 */
internal data class ThemeVisualSpec(
    val themeId: AppThemeId,
    val iconStyle: IconStyle,
    val buttonStyle: ButtonStyle,
    val buttonSilhouette: ButtonSilhouette,
    val borderStyle: BorderStyle,
    val surfaceStyle: SurfaceStyle,
    val recordingVisualStyle: RecordingVisualStyle,
    val animationStyle: AnimationStyle,

    // 以下皆為「只影響繪製」的參數，不參與量測。
    /**
     * 面板／卡片的邊框寬度（dp）。**不得用於工具列逐顆按鈕**：
     * 每顆按鈕都畫外框會造成視覺擁擠（2026-10-12 實機回報的白框問題）。
     */
    val borderWidthDp: Float,
    /** 環形音量的刻度數量（僅 SEGMENTED_METER_RING 使用）。 */
    val meterSegmentCount: Int,
    /** 面板容器的柔和外圈透明度（0 = 無）。 */
    val shadowAlpha: Float
) {
    /** 是否需要繪製額外的錄音視覺化圖形（Legacy 維持原樣，因此不需要）。 */
    val hasRecordingOverlay: Boolean
        get() = recordingVisualStyle != RecordingVisualStyle.LEGACY

    /**
     * 面板容器實際採用的邊框處理：HUD 按鈕風格的容器帶角標，其餘沿用主題的邊框風格。
     * 只用在「一個容器」上（工具列外框、卡片），不逐顆按鈕套用。
     */
    val containerBorderStyle: BorderStyle
        get() = if (buttonStyle == ButtonStyle.HUD_OUTLINE) BorderStyle.HUD_CORNERS else borderStyle

    /**
     * 工具列按鈕的底色；null 代表沿用 v0.15.5 原本的按鈕背景。
     *
     * 刻意「只有底色、沒有外框」：四套主題靠底色、圖示色與錄音視覺化區分，
     * 不再逐顆畫白色圓角框。
     */
    fun toolbarButtonFill(palette: ThemePalette): Int? = when (buttonStyle) {
        ButtonStyle.SOFT_PILL -> ThemeMix.blend(palette.surfaceVariant, palette.primary, 0.22f)
        ButtonStyle.HARD_GEOMETRIC -> palette.surfaceVariant
        ButtonStyle.HUD_OUTLINE -> ThemeMix.blend(palette.surfaceVariant, palette.secondary, 0.18f)
        ButtonStyle.SOFT_SUBTLE -> ThemeMix.blend(palette.surfaceVariant, palette.surface, 0.55f)
        ButtonStyle.NEUTRAL -> null
    }
}

/**
 * 五個主題的視覺規格。
 *
 * Legacy Warm 的每一項都是中性值（NEUTRAL／LEGACY／0），因此不會因為新增 Style Tokens
 * 而改變既有外觀。
 */
internal object ThemeVisualSpecRegistry {

    private val LEGACY = ThemeVisualSpec(
        themeId = AppThemeId.LEGACY_WARM,
        iconStyle = IconStyle.NEUTRAL,
        buttonStyle = ButtonStyle.NEUTRAL,
        buttonSilhouette = ButtonSilhouette.CIRCULAR,
        borderStyle = BorderStyle.NEUTRAL,
        surfaceStyle = SurfaceStyle.NEUTRAL,
        recordingVisualStyle = RecordingVisualStyle.LEGACY,
        animationStyle = AnimationStyle(
            maxScale = RecordingCircleScale.MAX_SCALE,
            smoothing = 1f,
            pulseCycles = 0
        ),
        borderWidthDp = 0f,
        meterSegmentCount = 0,
        shadowAlpha = 0f
    )

    private val CUTE_CREAM = ThemeVisualSpec(
        themeId = AppThemeId.CUTE_CREAM,
        iconStyle = IconStyle.SOFT_ROUNDED,
        buttonStyle = ButtonStyle.SOFT_PILL,
        buttonSilhouette = ButtonSilhouette.ORGANIC_CURVE,
        borderStyle = BorderStyle.NONE,
        surfaceStyle = SurfaceStyle.SOFT_ELEVATED,
        recordingVisualStyle = RecordingVisualStyle.SOFT_PULSE,
        animationStyle = AnimationStyle(maxScale = 1.22f, smoothing = 0.85f, pulseCycles = 2),
        borderWidthDp = 0f,
        meterSegmentCount = 0,
        shadowAlpha = 0.22f
    )

    private val INDUSTRIAL_LIGHT = ThemeVisualSpec(
        themeId = AppThemeId.INDUSTRIAL_LIGHT,
        iconStyle = IconStyle.GEOMETRIC_FUNCTIONAL,
        buttonStyle = ButtonStyle.HARD_GEOMETRIC,
        buttonSilhouette = ButtonSilhouette.MECHANICAL_PANEL,
        borderStyle = BorderStyle.FUNCTIONAL_FRAME,
        surfaceStyle = SurfaceStyle.LAYERED_PANELS,
        recordingVisualStyle = RecordingVisualStyle.SEGMENTED_METER_RING,
        animationStyle = AnimationStyle(maxScale = 1.16f, smoothing = 0.6f, pulseCycles = 0),
        borderWidthDp = 2f,
        meterSegmentCount = 16,
        shadowAlpha = 0f
    )

    private val BRIGHT_SCIFI = ThemeVisualSpec(
        themeId = AppThemeId.BRIGHT_SCIFI,
        iconStyle = IconStyle.THIN_TECHNICAL,
        buttonStyle = ButtonStyle.HUD_OUTLINE,
        buttonSilhouette = ButtonSilhouette.HUD_MODULE,
        borderStyle = BorderStyle.HAIRLINE,
        surfaceStyle = SurfaceStyle.GLASS_TINT,
        recordingVisualStyle = RecordingVisualStyle.SIGNAL_WAVE,
        animationStyle = AnimationStyle(maxScale = 1.18f, smoothing = 0.7f, pulseCycles = 0),
        borderWidthDp = 1f,
        meterSegmentCount = 0,
        shadowAlpha = 0.14f
    )

    private val DAILY_LIVING = ThemeVisualSpec(
        themeId = AppThemeId.DAILY_LIVING,
        iconStyle = IconStyle.CLEAN_BALANCED,
        buttonStyle = ButtonStyle.SOFT_SUBTLE,
        buttonSilhouette = ButtonSilhouette.DUAL_LAYER_SQUARE,
        borderStyle = BorderStyle.SUBTLE_LINE,
        surfaceStyle = SurfaceStyle.CLEAN,
        recordingVisualStyle = RecordingVisualStyle.SMOOTH_SCALE,
        animationStyle = AnimationStyle(maxScale = 1.2f, smoothing = 0.75f, pulseCycles = 0),
        borderWidthDp = 1f,
        meterSegmentCount = 0,
        shadowAlpha = 0f
    )

    private val ALL: Map<AppThemeId, ThemeVisualSpec> = mapOf(
        AppThemeId.LEGACY_WARM to LEGACY,
        AppThemeId.CUTE_CREAM to CUTE_CREAM,
        AppThemeId.INDUSTRIAL_LIGHT to INDUSTRIAL_LIGHT,
        AppThemeId.BRIGHT_SCIFI to BRIGHT_SCIFI,
        AppThemeId.DAILY_LIVING to DAILY_LIVING
    )

    fun forTheme(themeId: AppThemeId): ThemeVisualSpec = ALL[themeId] ?: LEGACY

    fun all(): List<ThemeVisualSpec> = AppThemeId.entries.map(::forTheme)
}
