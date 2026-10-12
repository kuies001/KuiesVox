package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingVisualGeometryTest {

    @Test
    fun blobPointsAreContainedWithinUnitBounds() {
        val points = RecordingVisualGeometry.organicBlobPoints(0f)
        assertEquals(36, points.size)
        points.forEach { (x, y) ->
            assertTrue("x $x should be between 0 and 1", x in 0f..1f)
            assertTrue("y $y should be between 0 and 1", y in 0f..1f)
        }
    }

    @Test
    fun blobPointsModulateWithVolume() {
        val quiet = RecordingVisualGeometry.organicBlobPoints(0f)
        val loud = RecordingVisualGeometry.organicBlobPoints(1f)
        assertEquals(quiet.size, loud.size)
        // 有機流動隨音量擴散，數值必然產生變化
        val diffCount = quiet.zip(loud).count { (p1, p2) ->
            p1.first != p2.first || p1.second != p2.second
        }
        assertTrue("Loud volume should modulate blob points", diffCount > 0)
    }

    @Test
    fun hudRoundedPointsAreContainedWithinBounds() {
        val points = RecordingVisualGeometry.hudRoundedModulePoints()
        assertEquals(8, points.size)
        points.forEach { (x, y) ->
            assertTrue("x $x should be between 0 and 1", x in 0f..1f)
            assertTrue("y $y should be between 0 and 1", y in 0f..1f)
        }
    }

    @Test
    fun allFiveThemesEnforceCircularRecordingSilhouettes() {
        AppThemeId.entries.forEach { themeId ->
            assertTrue(
                "Theme $themeId must have circular recording silhouette",
                RecordingVisualGeometry.isRecordingSilhouetteCircular(themeId)
            )
        }
    }

    @Test
    fun idleSilhouettesMaintainThemeDistinctiveness() {
        val legacy = ThemeVisualSpecRegistry.forTheme(AppThemeId.LEGACY_WARM)
        val cute = ThemeVisualSpecRegistry.forTheme(AppThemeId.CUTE_CREAM)
        val industrial = ThemeVisualSpecRegistry.forTheme(AppThemeId.INDUSTRIAL_LIGHT)
        val scifi = ThemeVisualSpecRegistry.forTheme(AppThemeId.BRIGHT_SCIFI)
        val daily = ThemeVisualSpecRegistry.forTheme(AppThemeId.DAILY_LIVING)

        assertEquals(ButtonSilhouette.CIRCULAR, legacy.buttonSilhouette)
        assertEquals(ButtonSilhouette.ORGANIC_CURVE, cute.buttonSilhouette)
        assertEquals(ButtonSilhouette.MECHANICAL_PANEL, industrial.buttonSilhouette)
        assertEquals(ButtonSilhouette.HUD_MODULE, scifi.buttonSilhouette)
        assertEquals(ButtonSilhouette.DUAL_LAYER_SQUARE, daily.buttonSilhouette)

        // 閒置時圓角均有足夠的柔和度
        assertTrue(RecordingVisualGeometry.MECHANICAL_PANEL_CORNER_RADIUS_DP >= 20)
        assertTrue(RecordingVisualGeometry.DUAL_LAYER_SQUARE_CORNER_RADIUS_DP >= 20)
    }

    @Test
    fun recordingVisualPlanStaysWithinSafeRadius() {
        listOf(
            RecordingVisualStyle.SOFT_PULSE,
            RecordingVisualStyle.SEGMENTED_METER_RING,
            RecordingVisualStyle.SIGNAL_WAVE,
            RecordingVisualStyle.SMOOTH_SCALE,
            RecordingVisualStyle.LEGACY
        ).forEach { style ->
            val quietPlan = RecordingVisualPlanner.planFor(style, 0f, 16, 2)
            val loudPlan = RecordingVisualPlanner.planFor(style, 1f, 16, 2)
            assertTrue(quietPlan.maxRadiusFraction <= 1.0f)
            assertTrue(loudPlan.maxRadiusFraction <= 1.0f)
        }
    }
}
