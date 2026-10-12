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
                specs.any { it.borderStyle == style || it.buttonBorderStyle == style }
            )
        }
        // HUD 按鈕一定要有角標處理。
        val sciFi = ThemeVisualSpecRegistry.forTheme(AppThemeId.BRIGHT_SCIFI)
        assertEquals(ButtonStyle.HUD_OUTLINE, sciFi.buttonStyle)
        assertEquals(BorderStyle.HUD_CORNERS, sciFi.buttonBorderStyle)
        assertEquals("面板本身仍是極細邊框", BorderStyle.HAIRLINE, sciFi.borderStyle)
        // 其餘主題的按鈕邊框與面板邊框一致。
        newThemes.filter { it != AppThemeId.BRIGHT_SCIFI }.forEach { themeId ->
            val spec = ThemeVisualSpecRegistry.forTheme(themeId)
            assertEquals(spec.borderStyle, spec.buttonBorderStyle)
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
        assertEquals(0f, legacy.iconFrameWidthDp, 0f)
        assertEquals(0f, legacy.iconFrameInsetDp, 0f)
        assertEquals(0f, legacy.borderWidthDp, 0f)
        assertEquals(0f, legacy.shadowAlpha, 0f)
        assertEquals(0, legacy.meterSegmentCount)
        assertFalse(legacy.hasRecordingOverlay)
        // 縮放上限與 v0.15.5 相同。
        assertEquals(RecordingCircleScale.MAX_SCALE, legacy.animationStyle.maxScale, 0f)
    }

    @Test
    fun nonLegacyThemesAllDefineAVisualTreatment() {
        newThemes.forEach { themeId ->
            val spec = ThemeVisualSpecRegistry.forTheme(themeId)
            assertEquals(themeId, spec.themeId)
            assertNotEquals("$themeId 必須有專屬的錄音視覺化", RecordingVisualStyle.LEGACY, spec.recordingVisualStyle)
            // 至少要有「外框」「邊框」或「外圈」其中一種造型處理，否則談不上不同風格。
            val hasTreatment = spec.iconFrameWidthDp > 0f ||
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
            val overlayStyles = listOf(
                RecordingVisualStyle.SOFT_PULSE,
                RecordingVisualStyle.SEGMENTED_METER_RING,
                RecordingVisualStyle.SIGNAL_WAVE
            )
            assertEquals(
                "${spec.themeId} 的 hasRecordingOverlay 與其樣式不一致",
                spec.recordingVisualStyle in overlayStyles,
                spec.hasRecordingOverlay
            )
            if (spec.recordingVisualStyle == RecordingVisualStyle.SOFT_PULSE) {
                assertTrue("脈動樣式必須定義圈數", spec.animationStyle.pulseCycles > 0)
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
