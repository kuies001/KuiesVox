package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeVisualSpecTest {

    private val newThemes = listOf(
        AppThemeId.CUTE_CREAM,
        AppThemeId.INDUSTRIAL_LIGHT,
        AppThemeId.BRIGHT_SCIFI,
        AppThemeId.DAILY_LIVING
    )

    @Test
    fun everyThemeHasItsOwnIconAndRecordingVisualStyle() {
        val pairs = ThemeVisualSpecRegistry.all().map { it.iconStyle to it.recordingVisualStyle }
        assertEquals("五個主題必須有五組不同的圖示／錄音樣式", 5, pairs.toSet().size)

        // 錄音視覺化方式逐主題指定，且四套新主題各不相同。
        assertEquals(
            RecordingVisualStyle.SOFT_PULSE,
            ThemeVisualSpecRegistry.forTheme(AppThemeId.CUTE_CREAM).recordingVisualStyle
        )
        assertEquals(
            RecordingVisualStyle.SEGMENTED_METER_RING,
            ThemeVisualSpecRegistry.forTheme(AppThemeId.INDUSTRIAL_LIGHT).recordingVisualStyle
        )
        assertEquals(
            RecordingVisualStyle.SIGNAL_WAVE,
            ThemeVisualSpecRegistry.forTheme(AppThemeId.BRIGHT_SCIFI).recordingVisualStyle
        )
        assertEquals(
            RecordingVisualStyle.SMOOTH_SCALE,
            ThemeVisualSpecRegistry.forTheme(AppThemeId.DAILY_LIVING).recordingVisualStyle
        )
        assertEquals(
            RecordingVisualStyle.LEGACY,
            ThemeVisualSpecRegistry.forTheme(AppThemeId.LEGACY_WARM).recordingVisualStyle
        )

        // 每套新主題的圖示風格都與 Legacy 不同。
        newThemes.forEach { themeId ->
            assertNotEquals(
                "$themeId 的圖示風格必須與 Legacy 不同",
                ThemeVisualSpecRegistry.forTheme(AppThemeId.LEGACY_WARM).iconStyle,
                ThemeVisualSpecRegistry.forTheme(themeId).iconStyle
            )
        }
    }

    @Test
    fun everyStyleIsActuallyUsedBySomeTheme() {
        val specs = ThemeVisualSpecRegistry.all()
        IconStyle.entries.forEach { style ->
            assertTrue("$style 沒有任何主題使用", specs.any { it.iconStyle == style })
        }
        ButtonStyle.entries.forEach { style ->
            assertTrue("$style 沒有任何主題使用", specs.any { it.buttonStyle == style })
        }
        BorderStyle.entries.forEach { style ->
            assertTrue(
                "$style 沒有任何主題使用",
                specs.any { it.borderStyle == style || it.containerBorderStyle == style }
            )
        }
        // 工具列容器的角標處理：HUD 風格的主題。
        val sciFi = ThemeVisualSpecRegistry.forTheme(AppThemeId.BRIGHT_SCIFI)
        assertEquals(ButtonStyle.HUD_OUTLINE, sciFi.buttonStyle)
        assertEquals(BorderStyle.HUD_CORNERS, sciFi.containerBorderStyle)
        assertEquals("面板本身仍是極細邊框", BorderStyle.HAIRLINE, sciFi.borderStyle)
        // 其餘主題的容器邊框與面板邊框一致。
        newThemes.filter { it != AppThemeId.BRIGHT_SCIFI }.forEach { themeId ->
            val spec = ThemeVisualSpecRegistry.forTheme(themeId)
            assertEquals(spec.borderStyle, spec.containerBorderStyle)
        }
        SurfaceStyle.entries.forEach { style ->
            assertTrue("$style 沒有任何主題使用", specs.any { it.surfaceStyle == style })
        }
        RecordingVisualStyle.entries.forEach { style ->
            assertTrue("$style 沒有任何主題使用", specs.any { it.recordingVisualStyle == style })
        }
    }

    @Test
    fun legacyVisualSpecIsNeutralSoTheV0155LookIsUnchanged() {
        val legacy = ThemeVisualSpecRegistry.forTheme(AppThemeId.LEGACY_WARM)

        assertEquals(IconStyle.NEUTRAL, legacy.iconStyle)
        assertEquals(ButtonStyle.NEUTRAL, legacy.buttonStyle)
        assertEquals(BorderStyle.NEUTRAL, legacy.borderStyle)
        assertEquals(SurfaceStyle.NEUTRAL, legacy.surfaceStyle)
        assertEquals(RecordingVisualStyle.LEGACY, legacy.recordingVisualStyle)
        // 沒有任何外框、外圈或刻度，因此不可能改變既有外觀。
        assertEquals(0f, legacy.borderWidthDp, 0f)
        assertEquals(0f, legacy.shadowAlpha, 0f)
        assertEquals(0, legacy.meterSegmentCount)
        assertFalse(legacy.hasRecordingOverlay)
        // 工具列按鈕完全沿用原本的背景（不套用任何主題底色）。
        assertEquals(null, legacy.toolbarButtonFill(ThemePaletteRegistry.legacyImePalette()))
        // 縮放上限與 v0.15.5 相同。
        assertEquals(RecordingCircleScale.MAX_SCALE, legacy.animationStyle.maxScale, 0f)
    }

    @Test
    fun nonLegacyThemesAllDefineAVisualTreatment() {
        newThemes.forEach { themeId ->
            val spec = ThemeVisualSpecRegistry.forTheme(themeId)
            assertEquals(themeId, spec.themeId)
            assertNotEquals("$themeId 必須有專屬的錄音視覺化", RecordingVisualStyle.LEGACY, spec.recordingVisualStyle)
            // 至少要有「按鈕底色」「容器邊框」或「外圈」其中一種造型處理，
            // 否則談不上不同風格（但都不得是逐顆按鈕的外框）。
            val palette = ThemePaletteRegistry.paletteFor(themeId, ThemeAppearance.DARK, false)
            val hasTreatment = spec.toolbarButtonFill(palette) != null ||
                spec.borderWidthDp > 0f ||
                spec.shadowAlpha > 0f ||
                spec.hasRecordingOverlay
            assertTrue("$themeId 沒有任何造型處理", hasTreatment)
            // 動畫上限必須大於基礎半徑且不超過安全上限。
            assertTrue("$themeId 的縮放上限不足", spec.animationStyle.maxScale > RecordingCircleScale.BASE_SCALE)
            assertTrue(
                "$themeId 的縮放上限超出安全範圍",
                spec.animationStyle.maxScale <= RecordingCircleScale.MAX_GROWTH
            )
            assertTrue("$themeId 的刻度數量不可為負", spec.meterSegmentCount >= 0)
            assertTrue("$themeId 的外圈透明度必須在 0..1", spec.shadowAlpha in 0f..1f)
            // 工業主題必須提供環形刻度數量，否則畫不出儀表。
            if (spec.recordingVisualStyle == RecordingVisualStyle.SEGMENTED_METER_RING) {
                assertTrue("環形刻度主題必須定義刻度數量", spec.meterSegmentCount > 0)
            }
        }
    }

    @Test
    fun recordingScaleRespectsTheThemeMaximumAndTheSafetyCap() {
        // 無聲：永遠回到基礎半徑。
        ThemeVisualSpecRegistry.all().forEach { spec ->
            assertEquals(
                RecordingCircleScale.BASE_SCALE,
                RecordingCircleScale.forLevel(0f, spec.animationStyle.maxScale),
                0f
            )
        }
        // 最大音量：等於該主題的上限。
        assertEquals(1.22f, RecordingCircleScale.forLevel(1f, 1.22f), 0.0001f)
        assertEquals(1.16f, RecordingCircleScale.forLevel(1f, 1.16f), 0.0001f)
        // 任何主題都不能超出安全上限（避免遮擋右側與下方控制項）。
        assertEquals(RecordingCircleScale.MAX_GROWTH, RecordingCircleScale.forLevel(1f, 9f), 0.0001f)
        assertEquals(RecordingCircleScale.BASE_SCALE, RecordingCircleScale.forLevel(1f, 0.2f), 0.0001f)
        // 非有限值一律視為無聲。
        assertEquals(RecordingCircleScale.BASE_SCALE, RecordingCircleScale.forLevel(1f, Float.NaN), 0f)
        assertEquals(RecordingCircleScale.BASE_SCALE, RecordingCircleScale.forLevel(Float.NaN, 1.2f), 0f)
        // 預設多載仍是 v0.15.5 的 1.2 倍。
        assertEquals(RecordingCircleScale.MAX_SCALE, RecordingCircleScale.forLevel(1f), 0f)
    }

    @Test
    fun onlyThemesWithAnOverlayDefineAMeterOrPulse() {
        ThemeVisualSpecRegistry.all().forEach { spec ->
            val label = spec.themeId.toString()
            // Legacy 沿用原動畫（沒有額外圖形），其餘主題都有專屬圖形。
            assertEquals(
                "$label 的 hasRecordingOverlay 與其樣式不一致",
                spec.recordingVisualStyle != RecordingVisualStyle.LEGACY,
                spec.hasRecordingOverlay
            )
            if (spec.recordingVisualStyle == RecordingVisualStyle.SOFT_PULSE) {
                assertTrue("脈動樣式必須定義圈數", spec.animationStyle.pulseCycles > 0)
            }
            if (spec.recordingVisualStyle == RecordingVisualStyle.SEGMENTED_METER_RING) {
                assertTrue("環形刻度樣式必須定義刻度數量", spec.meterSegmentCount > 0)
            }
        }
    }

    /** 測試 A6：預覽必須使用與實機相同的視覺規格。 */
    @Test
    fun previewUsesTheSameVisualSpecAsTheRealScreens() {
        AppThemeId.entries.forEach { themeId ->
            listOf(ThemeAppearance.LIGHT, ThemeAppearance.DARK).forEach { appearance ->
                val preview = ThemePreview.specFor(themeId, appearance, systemInDarkTheme = false)
                assertEquals(
                    "$themeId/$appearance 的預覽造型必須與實機相同",
                    ThemeVisualSpecRegistry.forTheme(themeId),
                    preview.visualSpec
                )
                assertTrue("${preview.simulatedLevel} 必須在 0..1", preview.simulatedLevel in 0f..1f)
            }
        }
        // 預覽卡之間必須看得出造型差異，而不是只有顏色不同。
        val previewVisuals = AppThemeId.entries
            .map { ThemePreview.specFor(it, ThemeAppearance.LIGHT, false).visualSpec }
        assertEquals(5, previewVisuals.toSet().size)
    }

    @Test
    fun previewSimulatedLevelIsOptionalAndDoesNotImplyAudio() {
        // 預覽預設帶一個模擬音量（讓預覽卡呈現錄音動態），但仍可指定任意值，且不需任何音訊來源。
        val default = ThemePreview.specFor(AppThemeId.DAILY_LIVING, ThemeAppearance.LIGHT, false)
        assertEquals(ThemePreview.DEFAULT_SIMULATED_LEVEL, default.simulatedLevel, 0f)

        val silent = ThemePreview.specFor(
            AppThemeId.DAILY_LIVING,
            ThemeAppearance.LIGHT,
            false,
            simulatedLevel = 0f
        )
        assertEquals(0f, silent.simulatedLevel, 0f)
        assertEquals(default.visualSpec, silent.visualSpec)
        assertNotEquals(default, silent)
    }

    @Test
    fun visualSpecsAreStablyDerivedFromTheThemeId() {
        AppThemeId.entries.forEach { themeId ->
            assertEquals(
                ThemeVisualSpecRegistry.forTheme(themeId),
                ThemeVisualSpecRegistry.forTheme(themeId)
            )
            assertEquals(5, ThemeVisualSpecRegistry.all().size)
        }
    }
}

/** 測試 A2／C2：四種錄音動畫都必須反映模擬音量變化，且輸入相同時結果相同。 */
class RecordingVisualScaleTest {

    @Test
    fun meterSegmentsReflectTheVolume() {
        assertEquals(0, RecordingVisualScale.litSegments(0f, 16))
        assertEquals(16, RecordingVisualScale.litSegments(1f, 16))
        assertEquals(8, RecordingVisualScale.litSegments(0.5f, 16))
        // 音量越大，亮起的刻度只增不減。
        val counts = (0..10).map { RecordingVisualScale.litSegments(it / 10f, 16) }
        counts.zipWithNext { lower, higher -> assertTrue("刻度必須單調增加：$counts", higher >= lower) }
        // 邊界與異常輸入。
        assertEquals(0, RecordingVisualScale.litSegments(0.9f, 0))
        assertEquals(0, RecordingVisualScale.litSegments(Float.NaN, 16))
        assertEquals(0, RecordingVisualScale.litSegments(-1f, 16))
        assertEquals(16, RecordingVisualScale.litSegments(9f, 16))
    }

    @Test
    fun signalWaveAmplitudeReflectsTheVolume() {
        assertEquals(0f, RecordingVisualScale.waveAmplitudeFraction(0f), 0f)
        assertEquals(0f, RecordingVisualScale.waveAmplitudeFraction(Float.NaN), 0f)
        val amplitudes = (0..10).map { RecordingVisualScale.waveAmplitudeFraction(it / 10f) }
        amplitudes.zipWithNext { lower, higher -> assertTrue("振幅必須單調增加", higher >= lower) }
        assertTrue(amplitudes.last() > amplitudes.first())
        assertEquals(0.32f, RecordingVisualScale.waveAmplitudeFraction(1f), 0.0001f)
    }

    @Test
    fun softPulseGrowsWithVolumeAndFadesTowardsTheOuterRing() {
        // 同一圈：音量越大，半徑越大。
        assertTrue(
            RecordingVisualScale.pulseRadiusFraction(0.5f, 1f) >
                RecordingVisualScale.pulseRadiusFraction(0.5f, 0f)
        )
        // 同一音量：外圈比內圈大、比內圈淡。
        assertTrue(
            RecordingVisualScale.pulseRadiusFraction(1f, 0.8f) >
                RecordingVisualScale.pulseRadiusFraction(0.5f, 0.8f)
        )
        assertTrue(
            RecordingVisualScale.pulseAlpha(1f, 0.8f) <
                RecordingVisualScale.pulseAlpha(0.4f, 0.8f)
        )
        // 音量越大，同一圈的透明度越高。
        assertTrue(
            RecordingVisualScale.pulseAlpha(0.5f, 1f) >
                RecordingVisualScale.pulseAlpha(0.5f, 0f)
        )
        assertEquals(0, RecordingVisualScale.pulseAlpha(1f, 1f))
        assertTrue(RecordingVisualScale.pulseAlpha(Float.NaN, Float.NaN) >= 0)
    }

    @Test
    fun everyRecordingVisualStyleReactsToVolume() {
        // 有圖形的三種樣式：以刻度／振幅／脈動反應音量。
        assertEquals(3, RecordingVisualScale.litSegments(1f, 3))
        assertTrue(RecordingVisualScale.waveAmplitudeFraction(1f) > 0f)
        assertTrue(RecordingVisualScale.pulseRadiusFraction(1f, 1f) > RecordingVisualScale.pulseRadiusFraction(1f, 0f))
        // 平滑縮放與 Legacy：以圓形縮放反應音量。
        assertTrue(RecordingCircleScale.forLevel(1f) > RecordingCircleScale.forLevel(0f))
        assertTrue(
            RecordingCircleScale.forLevel(1f, ThemeVisualSpecRegistry.forTheme(AppThemeId.DAILY_LIVING).animationStyle.maxScale) >
                RecordingCircleScale.forLevel(0f, 1.2f)
        )
    }

    @Test
    fun identicalInputProducesIdenticalOutputSoNoExtraRedrawIsNeeded() {
        // 相同音量必定得到相同結果，因此實作可以只在音量改變時才重畫（避免過量繪製）。
        assertEquals(
            RecordingVisualScale.litSegments(0.42f, 16),
            RecordingVisualScale.litSegments(0.42f, 16)
        )
        assertEquals(
            RecordingVisualScale.waveAmplitudeFraction(0.42f),
            RecordingVisualScale.waveAmplitudeFraction(0.42f),
            0f
        )
        assertEquals(
            RecordingVisualScale.pulseAlpha(0.5f, 0.42f),
            RecordingVisualScale.pulseAlpha(0.5f, 0.42f)
        )
        assertEquals(0.42f, RecordingVisualScale.boundedLevel(0.42f), 0f)
        assertEquals(1f, RecordingVisualScale.boundedLevel(4f), 0f)
        assertEquals(0f, RecordingVisualScale.boundedLevel(Float.POSITIVE_INFINITY), 0f)
    }
}

/**
 * TASK C 迴歸：工具列按鈕**不得**畫逐顆外框。
 *
 * 2026-10-12 實機回報七顆按鈕全部被明顯的白框包住；這裡以「低對比底色」把規則釘住：
 * 只要有人再把按鈕底色改成接近白色／圖示色，或讓底色對比拉高，測試就會失敗。
 */
class ToolbarButtonFrameTest {

    private fun palette(themeId: AppThemeId) =
        ThemePaletteRegistry.paletteFor(themeId, ThemeAppearance.DARK, false)

    @Test
    fun legacyKeepsItsOriginalButtonBackgroundWithNoFill() {
        val legacy = ThemeVisualSpecRegistry.forTheme(AppThemeId.LEGACY_WARM)
        assertEquals(null, legacy.toolbarButtonFill(palette(AppThemeId.LEGACY_WARM)))
    }

    @Test
    fun everyNewThemeUsesALowContrastFillInsteadOfAFrame() {
        val themes = listOf(
            AppThemeId.CUTE_CREAM,
            AppThemeId.INDUSTRIAL_LIGHT,
            AppThemeId.BRIGHT_SCIFI,
            AppThemeId.DAILY_LIVING
        )
        val fills = mutableSetOf<Int>()
        themes.forEach { themeId ->
            val p = palette(themeId)
            val spec = ThemeVisualSpecRegistry.forTheme(themeId)
            val fill = spec.toolbarButtonFill(p) ?: throw AssertionError("$themeId 必須提供按鈕底色")
            fills += fill
            val contrast = ThemeContrast.contrastRatio(fill, p.toolbarBackground)
            assertTrue(
                "$themeId 的按鈕底色對比過高（%.2f），會看起來像白框".format(contrast),
                contrast < 2.0
            )
            // 底色不得等於圖示色（那正是先前的白色外框效果）。
            assertTrue("$themeId 的按鈕底色不得等於圖示色", fill != p.toolbarIcon)
        }
        // 四套主題仍然彼此看得出差異。
        assertEquals(4, fills.size)
    }
}

/**
 * TASK D 迴歸：錄音視覺效果必須真的「畫得出來、畫得不同、畫在安全區域內」。
 *
 * 以與 IME drawable 共用的繪製計畫驗證；Legacy 維持完全沒有額外圖形。
 */
class RecordingVisualPlanTest {

    private val levelSteps = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    private fun planFor(themeId: AppThemeId, level: Float): RecordingVisualPlan {
        val spec = ThemeVisualSpecRegistry.forTheme(themeId)
        return RecordingVisualPlanner.planFor(
            style = spec.recordingVisualStyle,
            level = level,
            segmentCount = spec.meterSegmentCount,
            cycles = spec.animationStyle.pulseCycles
        )
    }

    @Test
    fun legacyDrawsNothingWhileEveryNewThemeActuallyDrawsSomething() {
        levelSteps.forEach { level ->
            assertTrue("Legacy 不得有額外圖形", planFor(AppThemeId.LEGACY_WARM, level).isEmpty)
        }
        listOf(
            AppThemeId.CUTE_CREAM,
            AppThemeId.INDUSTRIAL_LIGHT,
            AppThemeId.BRIGHT_SCIFI,
            AppThemeId.DAILY_LIVING
        ).forEach { themeId ->
            levelSteps.forEach { level ->
                val plan = planFor(themeId, level)
                assertTrue("$themeId @ $level 必須畫出圖形", !plan.isEmpty)
            }
        }
    }

    @Test
    fun everyPlanStaysInsideTheReservedSafeArea() {
        AppThemeId.entries.forEach { themeId ->
            levelSteps.forEach { level ->
                val plan = planFor(themeId, level)
                assertTrue(
                    "$themeId @ $level 超出安全半徑：${plan.maxRadiusFraction}",
                    plan.maxRadiusFraction <= 1f
                )
                assertTrue("$themeId @ $level 半徑不可為負", plan.maxRadiusFraction >= 0f)
                plan.rings.forEach { assertTrue("透明度必須在 0..255", it.alpha in 0..255) }
                plan.wave?.let { assertTrue("透明度必須在 0..255", it.alpha in 0..255) }
            }
        }
    }

    @Test
    fun theFourThemesProduceStructurallyDifferentVisuals() {
        val atVolume = 0.7f
        val plans = listOf(
            AppThemeId.CUTE_CREAM,
            AppThemeId.INDUSTRIAL_LIGHT,
            AppThemeId.BRIGHT_SCIFI,
            AppThemeId.DAILY_LIVING
        ).map { planFor(it, atVolume) }

        // 用「圖形種類」區分：脈動＝多圈、刻度＝格數、電波＝封閉波形、生活＝單圈。
        assertTrue("Cute 應為多圈脈動", plans[0].rings.size >= 2 && plans[0].wave == null && plans[0].ticks.isEmpty())
        assertTrue("Industrial 應為環形刻度", plans[1].ticks.isNotEmpty() && plans[1].rings.isEmpty())
        assertTrue("Sci-Fi 應為電波紋", plans[2].wave != null && plans[2].rings.isEmpty() && plans[2].ticks.isEmpty())
        assertTrue(
            "Living 應為單圈簡潔變化",
            plans[3].rings.size == 1 && plans[3].wave == null && plans[3].ticks.isEmpty()
        )
        assertEquals(4, plans.toSet().size)
    }

    @Test
    fun eachVisualReactsToEveryVolumeStep() {
        // 刻度：音量越大亮起越多，無聲仍有低活動量。
        val tickCounts = levelSteps.map { planFor(AppThemeId.INDUSTRIAL_LIGHT, it).ticks.size }
        assertEquals(RecordingVisualPlanner.METER_MIN_LIT, tickCounts.first())
        assertEquals(16, tickCounts.last())
        tickCounts.zipWithNext { lower, higher -> assertTrue("刻度必須單調增加：$tickCounts", higher >= lower) }

        // 脈動：音量越大外圈越大、越明顯。
        val pulseOuter = levelSteps.map { level ->
            planFor(AppThemeId.CUTE_CREAM, level).rings.maxOf { it.radiusFraction }
        }
        assertTrue("脈動必須隨音量擴大：$pulseOuter", pulseOuter.last() > pulseOuter.first())
        pulseOuter.zipWithNext { lower, higher -> assertTrue("脈動必須單調", higher >= lower) }

        // 電波：振幅隨音量增加，波形因此不同。
        val waves = levelSteps.map { level -> planFor(AppThemeId.BRIGHT_SCIFI, level).wave!! }
        val amplitudeSpread = waves.map { wave -> wave.points.maxOf { it.second } - wave.points.minOf { it.second } }
        assertTrue("電波振幅必須隨音量增加：$amplitudeSpread", amplitudeSpread.last() > amplitudeSpread.first())
        assertTrue("無聲時波形應接近平的", amplitudeSpread.first() < 0.01f)

        // 生活：單圈半徑固定但透明度隨音量變化。
        val livingAlphas = levelSteps.map { planFor(AppThemeId.DAILY_LIVING, it).rings.single().alpha }
        assertTrue("生活風格外圈透明度必須隨音量變化：$livingAlphas", livingAlphas.last() > livingAlphas.first())
    }

    @Test
    fun meterTicksAndSignalWaveStayOutsideTheButtonSurface() {
        // TASK C3/C4：環形刻度與電波紋必須畫在主按鈕視覺圓「之外」，
        // 不能壓在按鈕表面上，也不會覆蓋中央的停止圖示。
        levelSteps.forEach { level ->
            val meter = planFor(AppThemeId.INDUSTRIAL_LIGHT, level)
            meter.ticks.forEach { tick ->
                assertTrue(
                    "刻度必須在主按鈕視覺圓之外：inner=${tick.innerFraction}",
                    tick.innerFraction >= RecordingVisualPlanner.BUTTON_SURFACE_RADIUS_FRACTION
                )
            }
            val wave = planFor(AppThemeId.BRIGHT_SCIFI, level).wave!!
            val waveInner = wave.points.minOf { it.second } - wave.strokeFraction / 2f
            assertTrue(
                "電波紋必須在主按鈕視覺圓之外：inner=$waveInner",
                waveInner >= RecordingVisualPlanner.BUTTON_SURFACE_RADIUS_FRACTION
            )
        }
        // 視覺圓比觸控區小（1.0），因此外圈有空間容納刻度，觸控區本身不變。
        assertTrue(RecordingVisualPlanner.BUTTON_SURFACE_RADIUS_FRACTION < 1f)
        assertTrue(RecordingVisualPlanner.TICK_INNER >= RecordingVisualPlanner.BUTTON_SURFACE_RADIUS_FRACTION)
    }

    @Test
    fun theSameVolumeAlwaysYieldsTheSamePlan() {
        levelSteps.forEach { level ->
            AppThemeId.entries.forEach { themeId ->
                assertEquals(planFor(themeId, level), planFor(themeId, level))
            }
        }
        // 非有限音量一律視為無聲，不會放大任何圖形。
        assertEquals(
            planFor(AppThemeId.CUTE_CREAM, 0f),
            planFor(AppThemeId.CUTE_CREAM, Float.NaN)
        )
    }
}
