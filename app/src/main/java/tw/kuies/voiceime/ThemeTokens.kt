package tw.kuies.voiceime

/**
 * KuiesVox 主題系統的共用資料層。
 *
 * 這個檔案刻意只使用 Int（ARGB）與純 Kotlin 計算，不碰 android.graphics 或 Compose，
 * 因此同一套 Tokens 可以同時餵給：
 *  - Compose App 設定畫面（ui.theme.VoiceIMETheme 轉成 ColorScheme）
 *  - IME 原生 View 面板（VoiceImePanel 轉成 Drawable / tint）
 * 並且可以在 JVM 單元測試中完整驗證配色與解析規則。
 *
 * 顏色一律使用固定且穩定的 String ID 儲存（見 [AppThemeId.stableId]），
 * 不使用 enum ordinal，避免日後插入新主題時讓既有使用者的設定跳掉。
 */

/** 可選主題。順序即設定畫面的顯示順序。 */
internal enum class AppThemeId(
    val stableId: String,
    val displayName: String,
    val summary: String
) {
    LEGACY_WARM(
        stableId = "legacy_warm",
        displayName = "Warm & Cozy",
        summary = "經典奶茶暖色（v0.15.5 原貌）"
    ),
    CUTE_CREAM(
        stableId = "cute_cream",
        displayName = "奶油可愛",
        summary = "柔和圓角、奶油底色、蜜桃粉點綴"
    ),
    INDUSTRIAL_LIGHT(
        stableId = "industrial_light",
        displayName = "明亮工業",
        summary = "幾何邊框、機能分區、消光橘重點"
    ),
    BRIGHT_SCIFI(
        stableId = "bright_scifi",
        displayName = "明亮科幻",
        summary = "淺冰藍底、極細邊框、青色與薰衣草點綴"
    ),
    DAILY_LIVING(
        stableId = "daily_living",
        displayName = "明亮生活",
        summary = "米白底、柔沙主色、乾淨柔和"
    );

    companion object {
        /** 全新安裝的預設主題。 */
        val DEFAULT_FOR_NEW_INSTALL: AppThemeId = DAILY_LIVING

        /** 既有安裝（升級）在沒有偏好時維持原貌。 */
        val DEFAULT_FOR_EXISTING_INSTALL: AppThemeId = LEGACY_WARM

        /** 未知、空白或損毀的 ID 一律回傳 null，由呼叫端決定安全 fallback。 */
        fun fromStableId(raw: String?): AppThemeId? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return entries.firstOrNull { it.stableId == trimmed }
        }
    }
}

/** 外觀模式：跟隨系統、強制淺色、強制深色。 */
internal enum class ThemeAppearance(val stableId: String, val displayName: String) {
    SYSTEM("system", "跟隨系統"),
    LIGHT("light", "淺色"),
    DARK("dark", "深色");

    companion object {
        val DEFAULT: ThemeAppearance = SYSTEM

        fun fromStableId(raw: String?): ThemeAppearance? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return entries.firstOrNull { it.stableId == trimmed }
        }
    }
}

/** 中央錄音按鈕的模式識別三色組（底色、漸層亮端、圖示）。 */
internal data class ModeAccent(
    val base: Int,
    val bright: Int,
    val icon: Int
)

/**
 * 一個主題在某一種外觀下的完整 Token 組。
 *
 * 進階視覺 Token（[pillRadiusDp]、[cardRadiusDp]、[strokeWidthDp]）只影響繪製外觀，
 * 圓角與邊框寬度都不參與測量，因此不會改變任何元件的尺寸或位置。
 */
internal data class ThemePalette(
    val themeId: AppThemeId,
    val appearance: ThemeAppearance,
    val isDark: Boolean,

    // 基礎色階
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val primary: Int,
    val primaryBright: Int,
    val secondary: Int,
    val tertiary: Int,
    val onBackground: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val error: Int,

    // IME 工具列與中央錄音按鈕
    val toolbarBackground: Int,
    val toolbarIcon: Int,
    val recorderBackground: Int,
    val recorderForeground: Int,
    val recorderAccent: Int,
    val recordingHalo: Int,

    // 狀態燈
    val statusIdle: Int,
    val statusRecording: Int,
    val statusProcessing: Int,
    val statusPreview: Int,

    // 四種輸入模式
    val modeNormal: ModeAccent,
    val modeTranslate: ModeAccent,
    val modeFormat: ModeAccent,
    val modeEdit: ModeAccent,

    // 進階視覺 Token（不影響版面）
    val pillRadiusDp: Int,
    val cardRadiusDp: Int,
    val strokeWidthDp: Int,

    /** 面板內對話框（批次刪除確認等）的遮罩底色。 */
    val overlayScrim: Int
) {
    /** 依模式取得中央按鈕配色；一般語音沿用 [recorderBackground] 的奶油/主色漸層。 */
    fun accentForMode(
        translateModeActive: Boolean,
        formatModeActive: Boolean,
        aiEditModeActive: Boolean = false
    ): ModeAccent = when {
        translateModeActive -> modeTranslate
        formatModeActive -> modeFormat
        aiEditModeActive -> modeEdit
        else -> ModeAccent(recorderBackground, primaryBright, recorderForeground)
    }
}

/**
 * 全部主題 × 外觀的配色表。
 *
 * LEGACY_WARM 的數值刻意與 v0.15.5 的硬編碼常數完全相同，
 * 因此舊使用者升級後 IME 面板與 App 外觀都不會改變。
 */
internal object ThemePaletteRegistry {

    private const val TRANSPARENT = 0x00000000

    private fun palette(
        themeId: AppThemeId,
        appearance: ThemeAppearance,
        isDark: Boolean,
        background: Long,
        surface: Long,
        surfaceVariant: Long,
        primary: Long,
        primaryBright: Long,
        secondary: Long,
        tertiary: Long,
        onBackground: Long,
        onSurfaceVariant: Long,
        outline: Long,
        error: Long,
        toolbarBackground: Long,
        toolbarIcon: Long,
        recorderBackground: Long,
        recorderForeground: Long,
        recorderAccent: Long,
        recordingHalo: Long = 0x00000000,
        statusIdle: Long,
        statusRecording: Long,
        statusProcessing: Long,
        statusPreview: Long,
        modeNormal: Triple<Long, Long, Long>,
        modeTranslate: Triple<Long, Long, Long>,
        modeFormat: Triple<Long, Long, Long>,
        modeEdit: Triple<Long, Long, Long>,
        pillRadiusDp: Int,
        cardRadiusDp: Int,
        strokeWidthDp: Int = 1,
        onSurface: Long = onBackground,
        overlayScrim: Long? = null
    ) = ThemePalette(
        themeId = themeId,
        appearance = appearance,
        isDark = isDark,
        background = background.toInt(),
        surface = surface.toInt(),
        surfaceVariant = surfaceVariant.toInt(),
        primary = primary.toInt(),
        primaryBright = primaryBright.toInt(),
        secondary = secondary.toInt(),
        tertiary = tertiary.toInt(),
        onBackground = onBackground.toInt(),
        onSurface = onSurface.toInt(),
        onSurfaceVariant = onSurfaceVariant.toInt(),
        outline = outline.toInt(),
        error = error.toInt(),
        toolbarBackground = toolbarBackground.toInt(),
        toolbarIcon = toolbarIcon.toInt(),
        recorderBackground = recorderBackground.toInt(),
        recorderForeground = recorderForeground.toInt(),
        recorderAccent = recorderAccent.toInt(),
        recordingHalo = recordingHalo.toInt(),
        statusIdle = statusIdle.toInt(),
        statusRecording = statusRecording.toInt(),
        statusProcessing = statusProcessing.toInt(),
        statusPreview = statusPreview.toInt(),
        modeNormal = modeNormal.toAccent(),
        modeTranslate = modeTranslate.toAccent(),
        modeFormat = modeFormat.toAccent(),
        modeEdit = modeEdit.toAccent(),
        pillRadiusDp = pillRadiusDp,
        cardRadiusDp = cardRadiusDp,
        strokeWidthDp = strokeWidthDp,
        overlayScrim = (overlayScrim ?: defaultOverlayScrim(background)).toInt()
    )

    /** 未指定遮罩時，由背景色推導一個只留 12% 底色的對話框遮罩。 */
    private fun defaultOverlayScrim(background: Long): Long =
        (0xE1L shl 24) or ThemeMix.blend(background.toInt(), 0xFF000000.toInt(), 0.88f).toLong() and 0xFFFFFFL

    private fun Triple<Long, Long, Long>.toAccent() = ModeAccent(
        base = first.toInt(),
        bright = second.toInt(),
        icon = third.toInt()
    )

    // ------------------------------------------------------------------
    // Legacy Warm：與 v0.15.5 完全相同的數值（IME 面板長期使用深色暖灰棕）
    // ------------------------------------------------------------------
    private val LEGACY_WARM_DARK = palette(
        themeId = AppThemeId.LEGACY_WARM,
        appearance = ThemeAppearance.DARK,
        isDark = true,
        background = 0xFF2F2A28,
        surface = 0xFF3A312E,
        surfaceVariant = 0xFF433936,
        primary = 0xFFF3D8C7,
        primaryBright = 0xFFF9EBE0,
        secondary = 0xFFF3AAA4,
        tertiary = 0xFFBFD8C1,
        onBackground = 0xFFFFF9F4,
        onSurfaceVariant = 0xFFD1BFB5,
        outline = 0xFF63544E,
        error = 0xFFF2A7A7,
        toolbarBackground = 0xFF433936,
        toolbarIcon = 0xFFD1BFB5,
        recorderBackground = 0xFFF3D8C7,
        recorderForeground = 0xFF35231C,
        recorderAccent = 0xFFE89088,
        recordingHalo = 0x00000000,
        statusIdle = 0xFFBFD8C1,
        statusRecording = 0xFFE89088,
        statusProcessing = 0xFFE8BFA3,
        statusPreview = 0xFFF3D8C7,
        modeNormal = Triple(0xFFF3D8C7, 0xFFF9EBE0, 0xFF35231C),
        modeTranslate = Triple(0xFFD8E6C8, 0xFFE6F0DC, 0xFF2C4230),
        modeFormat = Triple(0xFFE8D8F2, 0xFFF3EAF9, 0xFF4A3554),
        modeEdit = Triple(0xFFF3AAA4, 0xFFF9D0CC, 0xFF4E2725),
        pillRadiusDp = 50,
        cardRadiusDp = 24,
        overlayScrim = 0xE10B0C14
    )

    /** Legacy Warm 的淺色組：等同 v0.15.5 的 Compose 淺色設定畫面。 */
    private val LEGACY_WARM_LIGHT = palette(
        themeId = AppThemeId.LEGACY_WARM,
        appearance = ThemeAppearance.LIGHT,
        isDark = false,
        background = 0xFFFFF8F2,
        surface = 0xFFFFFDF9,
        surfaceVariant = 0xFFF6EBDD,
        primary = 0xFFD39B7A,
        primaryBright = 0xFFF3D8C7,
        secondary = 0xFFF3AAA4,
        tertiary = 0xFFBFD8C1,
        onBackground = 0xFF5A4A42,
        onSurfaceVariant = 0xFF8A766A,
        outline = 0xFFE5D6CA,
        error = 0xFFB84F50,
        toolbarBackground = 0xFFF6EBDD,
        toolbarIcon = 0xFF8A766A,
        recorderBackground = 0xFFF3D8C7,
        recorderForeground = 0xFF35231C,
        recorderAccent = 0xFFF3AAA4,
        statusIdle = 0xFFBFD8C1,
        statusRecording = 0xFFF3AAA4,
        statusProcessing = 0xFFF3D8C7,
        statusPreview = 0xFFF3D8C7,
        modeNormal = Triple(0xFFF3D8C7, 0xFFF9EBE0, 0xFF35231C),
        modeTranslate = Triple(0xFFD8E6C8, 0xFFE6F0DC, 0xFF2C4230),
        modeFormat = Triple(0xFFE8D8F2, 0xFFF3EAF9, 0xFF4A3554),
        modeEdit = Triple(0xFFF3AAA4, 0xFFF9D0CC, 0xFF4E2725),
        pillRadiusDp = 50,
        cardRadiusDp = 24
    )

    // ------------------------------------------------------------------
    // Cute Cream
    // ------------------------------------------------------------------
    private val CUTE_CREAM_LIGHT = palette(
        themeId = AppThemeId.CUTE_CREAM,
        appearance = ThemeAppearance.LIGHT,
        isDark = false,
        background = 0xFFFFFDF8,
        surface = 0xFFFFF8F0,
        surfaceVariant = 0xFFFCEEDF,
        primary = 0xFFE3A47A,
        primaryBright = 0xFFF3C9A8,
        secondary = 0xFFF5B7AE,
        tertiary = 0xFFCFE2C8,
        onBackground = 0xFF5B4B42,
        onSurfaceVariant = 0xFF8A7666,
        outline = 0xFFF0DFCC,
        error = 0xFFC4574F,
        toolbarBackground = 0xFFFCEEDF,
        toolbarIcon = 0xFF8A7666,
        recorderBackground = 0xFFE3A47A,
        recorderForeground = 0xFF4A3225,
        recorderAccent = 0xFFF5B7AE,
        recordingHalo = 0x40F5B7AE,
        statusIdle = 0xFFCFE2C8,
        statusRecording = 0xFFF5B7AE,
        statusProcessing = 0xFFF0C79A,
        statusPreview = 0xFFE3A47A,
        modeNormal = Triple(0xFFE3A47A, 0xFFF3C9A8, 0xFF4A3225),
        modeTranslate = Triple(0xFFCFE2C8, 0xFFE4EFDE, 0xFF33482F),
        modeFormat = Triple(0xFFEBD9EE, 0xFFF5EAF7, 0xFF4A3554),
        modeEdit = Triple(0xFFF5B7AE, 0xFFFBDCD7, 0xFF5A2F2A),
        pillRadiusDp = 100,
        cardRadiusDp = 20
    )

    private val CUTE_CREAM_DARK = palette(
        themeId = AppThemeId.CUTE_CREAM,
        appearance = ThemeAppearance.DARK,
        isDark = true,
        background = 0xFF2A2320,
        surface = 0xFF332A26,
        surfaceVariant = 0xFF3E332E,
        primary = 0xFFF0BE9A,
        primaryBright = 0xFFF8D8BC,
        secondary = 0xFFF6C4BC,
        tertiary = 0xFFC9DCC2,
        onBackground = 0xFFFFF6EE,
        onSurfaceVariant = 0xFFD8C4B8,
        outline = 0xFF574840,
        error = 0xFFF0A6A0,
        toolbarBackground = 0xFF3E332E,
        toolbarIcon = 0xFFD8C4B8,
        recorderBackground = 0xFFF0BE9A,
        recorderForeground = 0xFF3A2418,
        recorderAccent = 0xFFF6C4BC,
        recordingHalo = 0x40F6C4BC,
        statusIdle = 0xFFC9DCC2,
        statusRecording = 0xFFF6C4BC,
        statusProcessing = 0xFFE8C39B,
        statusPreview = 0xFFF0BE9A,
        modeNormal = Triple(0xFFF0BE9A, 0xFFF8D8BC, 0xFF3A2418),
        modeTranslate = Triple(0xFFC9DCC2, 0xFFDFEAD9, 0xFF23351F),
        modeFormat = Triple(0xFFD9C6DE, 0xFFEBE1EE, 0xFF3A2A41),
        modeEdit = Triple(0xFFF6C4BC, 0xFFFBDCD7, 0xFF4A231E),
        pillRadiusDp = 100,
        cardRadiusDp = 20
    )

    // ------------------------------------------------------------------
    // Industrial Light
    // ------------------------------------------------------------------
    private val INDUSTRIAL_LIGHT_LIGHT = palette(
        themeId = AppThemeId.INDUSTRIAL_LIGHT,
        appearance = ThemeAppearance.LIGHT,
        isDark = false,
        background = 0xFFF6F4EF,
        surface = 0xFFFBFAF7,
        surfaceVariant = 0xFFE8E2D8,
        primary = 0xFFC98A53,
        primaryBright = 0xFFE0B58C,
        secondary = 0xFFD96B3B,
        tertiary = 0xFFA49A8F,
        onBackground = 0xFF3F3A35,
        onSurfaceVariant = 0xFF6E665D,
        outline = 0xFFCFC7B9,
        error = 0xFFB4472F,
        toolbarBackground = 0xFFE8E2D8,
        toolbarIcon = 0xFF3F3A35,
        recorderBackground = 0xFFC98A53,
        recorderForeground = 0xFF2A1D12,
        recorderAccent = 0xFFD96B3B,
        recordingHalo = 0x00000000,
        statusIdle = 0xFFA49A8F,
        statusRecording = 0xFFD96B3B,
        statusProcessing = 0xFFC98A53,
        statusPreview = 0xFFC98A53,
        modeNormal = Triple(0xFFC98A53, 0xFFE0B58C, 0xFF3A2A18),
        modeTranslate = Triple(0xFFA8BFA8, 0xFFCBDCCB, 0xFF2E3D2E),
        modeFormat = Triple(0xFFB9AECB, 0xFFD6CEE2, 0xFF322B41),
        modeEdit = Triple(0xFFD96B3B, 0xFFE9A282, 0xFF3D1F12),
        pillRadiusDp = 6,
        cardRadiusDp = 8,
        strokeWidthDp = 2
    )

    private val INDUSTRIAL_LIGHT_DARK = palette(
        themeId = AppThemeId.INDUSTRIAL_LIGHT,
        appearance = ThemeAppearance.DARK,
        isDark = true,
        background = 0xFF221F1C,
        surface = 0xFF2B2724,
        surfaceVariant = 0xFF363029,
        primary = 0xFFE0A46A,
        primaryBright = 0xFFF0C79A,
        secondary = 0xFFEE8256,
        tertiary = 0xFFB8AEA2,
        onBackground = 0xFFF6F2EC,
        onSurfaceVariant = 0xFFC4B9AC,
        outline = 0xFF4A4239,
        error = 0xFFE58C74,
        toolbarBackground = 0xFF363029,
        toolbarIcon = 0xFFF6F2EC,
        recorderBackground = 0xFFE0A46A,
        recorderForeground = 0xFF2A1D12,
        recorderAccent = 0xFFEE8256,
        recordingHalo = 0x00000000,
        statusIdle = 0xFFB8AEA2,
        statusRecording = 0xFFEE8256,
        statusProcessing = 0xFFE0A46A,
        statusPreview = 0xFFE0A46A,
        modeNormal = Triple(0xFFE0A46A, 0xFFF0C79A, 0xFF2A1D12),
        modeTranslate = Triple(0xFFB7CBB7, 0xFFD5E2D5, 0xFF1F2C1F),
        modeFormat = Triple(0xFFC6BCD6, 0xFFDFD8E8, 0xFF262033),
        modeEdit = Triple(0xFFEE8256, 0xFFF3AE8F, 0xFF31180C),
        pillRadiusDp = 6,
        cardRadiusDp = 8,
        strokeWidthDp = 2
    )

    // ------------------------------------------------------------------
    // Bright Sci-Fi
    // ------------------------------------------------------------------
    private val BRIGHT_SCIFI_LIGHT = palette(
        themeId = AppThemeId.BRIGHT_SCIFI,
        appearance = ThemeAppearance.LIGHT,
        isDark = false,
        background = 0xFFF7FBFF,
        surface = 0xFFFFFFFF,
        surfaceVariant = 0xFFEAF4FA,
        primary = 0xFF72B7E8,
        primaryBright = 0xFFB3DAF3,
        secondary = 0xFF8EDDD0,
        tertiary = 0xFFC7B8F5,
        onBackground = 0xFF32424F,
        onSurfaceVariant = 0xFF5F7280,
        outline = 0xFFD6E9F5,
        error = 0xFFD9536A,
        toolbarBackground = 0xFFEAF4FA,
        toolbarIcon = 0xFF4A6070,
        recorderBackground = 0xFF72B7E8,
        recorderForeground = 0xFF102B3D,
        recorderAccent = 0xFF8EDDD0,
        recordingHalo = 0x5572B7E8,
        statusIdle = 0xFF8EDDD0,
        statusRecording = 0xFF72B7E8,
        statusProcessing = 0xFFC7B8F5,
        statusPreview = 0xFF72B7E8,
        modeNormal = Triple(0xFF72B7E8, 0xFFB3DAF3, 0xFF102B3D),
        modeTranslate = Triple(0xFF8EDDD0, 0xFFC6EEE6, 0xFF163F38),
        modeFormat = Triple(0xFFC7B8F5, 0xFFE2DBFA, 0xFF352C55),
        modeEdit = Triple(0xFFF2A6C0, 0xFFF9D2DF, 0xFF4C1A2B),
        pillRadiusDp = 12,
        cardRadiusDp = 14
    )

    private val BRIGHT_SCIFI_DARK = palette(
        themeId = AppThemeId.BRIGHT_SCIFI,
        appearance = ThemeAppearance.DARK,
        isDark = true,
        background = 0xFF10161C,
        surface = 0xFF171F27,
        surfaceVariant = 0xFF1F2A34,
        primary = 0xFF8FC7EE,
        primaryBright = 0xFFBEE0F6,
        secondary = 0xFF9FE4D8,
        tertiary = 0xFFCFC2F8,
        onBackground = 0xFFE6F2FA,
        onSurfaceVariant = 0xFFA9BECC,
        outline = 0xFF2C3A46,
        error = 0xFFF09AAB,
        toolbarBackground = 0xFF1F2A34,
        toolbarIcon = 0xFFCFE3F0,
        recorderBackground = 0xFF8FC7EE,
        recorderForeground = 0xFF0B2130,
        recorderAccent = 0xFF9FE4D8,
        recordingHalo = 0x559FE4D8,
        statusIdle = 0xFF9FE4D8,
        statusRecording = 0xFF8FC7EE,
        statusProcessing = 0xFFCFC2F8,
        statusPreview = 0xFF8FC7EE,
        modeNormal = Triple(0xFF8FC7EE, 0xFFBEE0F6, 0xFF0B2130),
        modeTranslate = Triple(0xFF9FE4D8, 0xFFCDF1EB, 0xFF0E2F2A),
        modeFormat = Triple(0xFFCFC2F8, 0xFFE6DFFC, 0xFF2A2247),
        modeEdit = Triple(0xFFF4B3CA, 0xFFFADAE4, 0xFF3F1524),
        pillRadiusDp = 12,
        cardRadiusDp = 14
    )

    // ------------------------------------------------------------------
    // Daily Living
    // ------------------------------------------------------------------
    private val DAILY_LIVING_LIGHT = palette(
        themeId = AppThemeId.DAILY_LIVING,
        appearance = ThemeAppearance.LIGHT,
        isDark = false,
        background = 0xFFFCFAF6,
        surface = 0xFFFFFFFF,
        surfaceVariant = 0xFFF2ECE4,
        primary = 0xFFD59A74,
        primaryBright = 0xFFE8C4A9,
        secondary = 0xFFC8D8C4,
        tertiary = 0xFFC8D5DE,
        onBackground = 0xFF4F4741,
        onSurfaceVariant = 0xFF7A716A,
        outline = 0xFFE4DCD2,
        error = 0xFFBF5250,
        toolbarBackground = 0xFFF2ECE4,
        toolbarIcon = 0xFF7A716A,
        recorderBackground = 0xFFD59A74,
        recorderForeground = 0xFF43301F,
        recorderAccent = 0xFFCE7A62,
        recordingHalo = 0x00000000,
        statusIdle = 0xFFC8D8C4,
        statusRecording = 0xFFCE7A62,
        statusProcessing = 0xFFC8D5DE,
        statusPreview = 0xFFD59A74,
        modeNormal = Triple(0xFFD59A74, 0xFFE8C4A9, 0xFF43301F),
        modeTranslate = Triple(0xFFC8D8C4, 0xFFE1EADF, 0xFF2F3D2E),
        modeFormat = Triple(0xFFC8D5DE, 0xFFE1E9EE, 0xFF2E3B44),
        modeEdit = Triple(0xFFE2B0A6, 0xFFF0D3CD, 0xFF4A2A26),
        pillRadiusDp = 18,
        cardRadiusDp = 16
    )

    private val DAILY_LIVING_DARK = palette(
        themeId = AppThemeId.DAILY_LIVING,
        appearance = ThemeAppearance.DARK,
        isDark = true,
        background = 0xFF201D1A,
        surface = 0xFF292521,
        surfaceVariant = 0xFF342F29,
        primary = 0xFFE3B18C,
        primaryBright = 0xFFF2D0B4,
        secondary = 0xFFCCDCC8,
        tertiary = 0xFFCBD8E0,
        onBackground = 0xFFF7F3EE,
        onSurfaceVariant = 0xFFBBB2A9,
        outline = 0xFF473F37,
        error = 0xFFE5A09B,
        toolbarBackground = 0xFF342F29,
        toolbarIcon = 0xFFBBB2A9,
        recorderBackground = 0xFFE3B18C,
        recorderForeground = 0xFF2E2015,
        recorderAccent = 0xFFDE9480,
        recordingHalo = 0x00000000,
        statusIdle = 0xFFCCDCC8,
        statusRecording = 0xFFDE9480,
        statusProcessing = 0xFFCBD8E0,
        statusPreview = 0xFFE3B18C,
        modeNormal = Triple(0xFFE3B18C, 0xFFF2D0B4, 0xFF2E2015),
        modeTranslate = Triple(0xFFCCDCC8, 0xFFE2EBE0, 0xFF243122),
        modeFormat = Triple(0xFFCBD8E0, 0xFFE3EAF0, 0xFF232E36),
        modeEdit = Triple(0xFFDE9480, 0xFFEFC3B8, 0xFF3B211C),
        pillRadiusDp = 18,
        cardRadiusDp = 16
    )

    private val ALL: Map<AppThemeId, Pair<ThemePalette, ThemePalette>> = mapOf(
        AppThemeId.LEGACY_WARM to (LEGACY_WARM_LIGHT to LEGACY_WARM_DARK),
        AppThemeId.CUTE_CREAM to (CUTE_CREAM_LIGHT to CUTE_CREAM_DARK),
        AppThemeId.INDUSTRIAL_LIGHT to (INDUSTRIAL_LIGHT_LIGHT to INDUSTRIAL_LIGHT_DARK),
        AppThemeId.BRIGHT_SCIFI to (BRIGHT_SCIFI_LIGHT to BRIGHT_SCIFI_DARK),
        AppThemeId.DAILY_LIVING to (DAILY_LIVING_LIGHT to DAILY_LIVING_DARK)
    )

    /** 淺色組（Compose 設定畫面與啟動預覽用）。 */
    val light: Map<AppThemeId, ThemePalette> = ALL.mapValues { it.value.first }
    private val dark: Map<AppThemeId, ThemePalette> = ALL.mapValues { it.value.second }

    /** 依主題與外觀取得配色；SYSTEM 由 [systemInDarkTheme] 決定。 */
    fun paletteFor(
        themeId: AppThemeId,
        appearance: ThemeAppearance,
        systemInDarkTheme: Boolean
    ): ThemePalette {
        val useDark = when (appearance) {
            ThemeAppearance.SYSTEM -> systemInDarkTheme
            ThemeAppearance.LIGHT -> false
            ThemeAppearance.DARK -> true
        }
        val source = if (useDark) dark else light
        return source[themeId] ?: LEGACY_WARM_DARK
    }

    /**
     * IME 面板專用：Legacy Warm 在任何外觀模式下都維持 v0.15.5 的暖灰棕外觀，
     * 避免既有使用者升級後輸入法面板忽然變成淺色。其餘主題則跟隨外觀設定。
     */
    fun imePaletteFor(
        themeId: AppThemeId,
        appearance: ThemeAppearance,
        systemInDarkTheme: Boolean
    ): ThemePalette = when (themeId) {
        AppThemeId.LEGACY_WARM -> LEGACY_WARM_DARK
        else -> paletteFor(themeId, appearance, systemInDarkTheme)
    }

    /** 供 IME 面板建構時使用的預設配色；與 v0.15.5 的暖灰棕完全相同。 */
    fun legacyImePalette(): ThemePalette = LEGACY_WARM_DARK

    /** 供測試與設定畫面預覽列舉全部主題。 */
    fun allPalettes(): List<ThemePalette> =
        AppThemeId.entries.flatMap { themeId ->
            listOf(
                paletteFor(themeId, ThemeAppearance.LIGHT, systemInDarkTheme = false),
                paletteFor(themeId, ThemeAppearance.DARK, systemInDarkTheme = false)
            )
        }
}

/**
 * 設定畫面主題預覽卡要畫的內容。
 *
 * 預覽卡的每一個顏色都只能來自這裡，而這裡只能由 [ThemePalette] 產生，
 * 因此預覽不可能與實際畫面使用不同的配色（見 ThemePreviewTest）。
 */
internal data class ThemePreviewSpec(
    val imeBackground: Int,
    val toolbarBackground: Int,
    val toolbarIcon: Int,
    val recorderBackground: Int,
    val recorderForeground: Int,
    val recorderAccent: Int,
    val recordingHalo: Int,
    val swatches: List<Int>,
    val modeAccents: List<Int>,
    /** 該主題的造型規格（圖示、按鈕、邊框、錄音視覺化）——預覽與實機共用同一份。 */
    val visualSpec: ThemeVisualSpec,
    /** 工具列按鈕的底色；null 代表 Legacy 沿用原本（無底色）的外觀。 */
    val toolbarChipFill: Int?,
    /** 預覽要模擬的錄音音量（0f–1f）；預覽不會啟動麥克風。 */
    val simulatedLevel: Float = ThemePreview.DEFAULT_SIMULATED_LEVEL
) {
    companion object {
        fun of(
            palette: ThemePalette,
            visualSpec: ThemeVisualSpec = ThemeVisualSpecRegistry.forTheme(palette.themeId),
            simulatedLevel: Float = ThemePreview.DEFAULT_SIMULATED_LEVEL
        ) = ThemePreviewSpec(
            imeBackground = palette.background,
            toolbarBackground = palette.toolbarBackground,
            toolbarIcon = palette.toolbarIcon,
            recorderBackground = palette.recorderBackground,
            recorderForeground = palette.recorderForeground,
            recorderAccent = palette.recorderAccent,
            recordingHalo = palette.recordingHalo,
            swatches = listOf(
                palette.primary,
                palette.secondary,
                palette.tertiary,
                palette.surfaceVariant,
                palette.outline
            ),
            modeAccents = listOf(
                palette.modeNormal.base,
                palette.modeTranslate.base,
                palette.modeFormat.base,
                palette.modeEdit.base
            ),
            visualSpec = visualSpec,
            toolbarChipFill = visualSpec.toolbarButtonFill(palette),
            simulatedLevel = simulatedLevel
        )
    }
}

/** 設定畫面預覽與實際畫面共用同一個查表，避免預覽與套用後不一致。 */
internal object ThemePreview {
    /** 預覽卡上示意的錄音音量；純粹是畫面示意，不讀取任何麥克風資料。 */
    const val DEFAULT_SIMULATED_LEVEL = 0.62f

    fun specFor(
        themeId: AppThemeId,
        appearance: ThemeAppearance,
        systemInDarkTheme: Boolean,
        simulatedLevel: Float = DEFAULT_SIMULATED_LEVEL
    ): ThemePreviewSpec = ThemePreviewSpec.of(
        palette = ThemePaletteRegistry.paletteFor(themeId, appearance, systemInDarkTheme),
        visualSpec = ThemeVisualSpecRegistry.forTheme(themeId),
        simulatedLevel = simulatedLevel
    )
}

/**
 * 對比計算（WCAG 相對亮度），只用位元運算，因此可在 JVM 單元測試中直接驗證配色可讀性。
 */
internal object ThemeContrast {
    /** 一般文字的最低對比（WCAG AA）。 */
    const val NORMAL_TEXT_MIN = 4.5
    /** 大型文字與 UI 元件的最低對比。 */
    const val LARGE_TEXT_MIN = 3.0

    private const val ON_DARK = 0xFF1F1B18.toInt()
    private const val ON_LIGHT = 0xFFFFFFFF.toInt()

    fun relativeLuminance(color: Int): Double {
        val red = channel(color, 16)
        val green = channel(color, 8)
        val blue = channel(color, 0)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    fun contrastRatio(foreground: Int, background: Int): Double {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 在深色與白色之間挑出對 [background] 對比較高的前景色。 */
    fun bestOnColor(background: Int, dark: Int = ON_DARK, light: Int = ON_LIGHT): Int =
        if (contrastRatio(dark, background) >= contrastRatio(light, background)) dark else light

    private fun channel(color: Int, shift: Int): Double {
        val raw = (color shr shift) and 0xFF
        val normalized = raw / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
    }
}

/** 顏色混合（不做 gamma 校正，與既有 IME 的 blend 行為一致），純 Kotlin 因此可測試。 */
internal object ThemeMix {
    fun blend(background: Int, foreground: Int, fraction: Float): Int {
        val ratio = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        val inverse = 1f - ratio
        return argb(
            mix(channel(background, 16), channel(foreground, 16), inverse, ratio),
            mix(channel(background, 8), channel(foreground, 8), inverse, ratio),
            mix(channel(background, 0), channel(foreground, 0), inverse, ratio)
        )
    }

    private fun mix(background: Int, foreground: Int, inverse: Float, ratio: Float): Int =
        (background * inverse + foreground * ratio).toInt().coerceIn(0, 255)

    private fun channel(color: Int, shift: Int): Int = (color shr shift) and 0xFF

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

