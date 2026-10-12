package tw.kuies.voiceime

import android.content.Context
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IME 面板的主題整合驗收（需裝置／模擬器）。
 *
 * 覆蓋：
 *  - 每個主題 × 外觀模式下，面板高度與關鍵元件 bounds 完全不變（固定 Layout Geometry）。
 *  - 錄音進行中切換主題不影響錄音狀態、取消按鈕、音量縮放與觸控範圍。
 *  - 切換主題確實換色（狀態燈填色來自新主題的 Token）。
 */
class VoiceImeThemePanelTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun panel(): VoiceImePanel = VoiceImePanel(
        context = context,
        onVoiceAction = {},
        onCancel = {},
        onSwitchInputMethod = {}
    )

    private fun layoutPanel(panel: VoiceImePanel, widthDp: Int, heightDp: Int = 220) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        panel.view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        )
        panel.view.layout(0, 0, width, panel.view.measuredHeight)
    }

    /** 面板的固定幾何指紋：高度與各關鍵元件的 bounds 都必須一模一樣。 */
    private fun geometryOf(panel: VoiceImePanel): List<Int> = listOf(
        panel.view.measuredHeight,
        panel.mainInteractionContainer.measuredHeight,
        panel.idleActions.left,
        panel.idleActions.top,
        panel.idleActions.right,
        panel.idleActions.bottom,
        panel.idleMicButton.left,
        panel.idleMicButton.top,
        panel.idleMicButton.right,
        panel.idleMicButton.bottom,
        panel.recordingCancelButton.left,
        panel.recordingCancelButton.top,
        panel.recordingCancelButton.right,
        panel.recordingCancelButton.bottom,
        panel.topToolbarRow.left,
        panel.topToolbarRow.top,
        panel.topToolbarRow.right,
        panel.topToolbarRow.bottom,
        panel.backspaceButton.left,
        panel.backspaceButton.top,
        panel.backspaceButton.right,
        panel.backspaceButton.bottom,
        panel.enterButton.left,
        panel.enterButton.top,
        panel.enterButton.right,
        panel.enterButton.bottom
    )

    private fun allThemeSelections(): List<Pair<AppThemeId, ThemeAppearance>> =
        AppThemeId.entries.flatMap { themeId ->
            listOf(
                themeId to ThemeAppearance.LIGHT,
                themeId to ThemeAppearance.DARK,
                themeId to ThemeAppearance.SYSTEM
            )
        }

    private fun palette(themeId: AppThemeId, appearance: ThemeAppearance, systemDark: Boolean = false) =
        ThemePaletteRegistry.imePaletteFor(themeId, appearance, systemInDarkTheme = systemDark)

    @Test
    fun idleGeometryIsIdenticalAcrossEveryTheme() {
        val panel = panel()
        layoutPanel(panel, widthDp = 360)
        val baseline = geometryOf(panel)

        allThemeSelections().forEach { (themeId, appearance) ->
            panel.applyTheme(palette(themeId, appearance))
            layoutPanel(panel, widthDp = 360)
            assertEquals("$themeId/$appearance 不得改變待命幾何", baseline, geometryOf(panel))
            assertEquals(themeId, panel.currentPalette().themeId)
        }
    }

    @Test
    fun recordingGeometryAndVolumeScaleAreIdenticalAcrossEveryTheme() {
        val panel = panel()
        panel.render(VoiceImeState.RECORDING)
        layoutPanel(panel, widthDp = 360)
        panel.updateAudioLevel(1f)
        val baseline = geometryOf(panel)
        val baselineScale = panel.idleMicButton.scaleX
        val touchWidth = panel.idleMicButton.width

        allThemeSelections().forEach { (themeId, appearance) ->
            panel.applyTheme(palette(themeId, appearance))
            layoutPanel(panel, widthDp = 360)
            assertEquals("$themeId/$appearance 不得改變錄音幾何", baseline, geometryOf(panel))
            // 觸控範圍固定，不跟著視覺縮放。
            assertEquals("$themeId/$appearance 不得改變觸控範圍", touchWidth, panel.idleMicButton.width)
            assertEquals("$themeId/$appearance 不得重設音量縮放", baselineScale, panel.idleMicButton.scaleX, 0.0001f)
            assertEquals("$themeId/$appearance 必須仍顯示取消", View.VISIBLE, panel.recordingCancelButton.visibility)
            assertEquals("$themeId/$appearance 必須仍是停止", "停止錄音", panel.idleMicButton.contentDescription)
        }
    }

    @Test
    fun switchingThemeDuringRecordingKeepsTheRecordingStateUntouched() {
        val panel = panel()
        panel.render(VoiceImeState.RECORDING)
        layoutPanel(panel, widthDp = 360)
        panel.updateAudioLevel(1f)
        val scaleBefore = panel.idleMicButton.scaleX
        val boundsBefore = listOf(
            panel.idleMicButton.left,
            panel.idleMicButton.top,
            panel.idleMicButton.right,
            panel.idleMicButton.bottom
        )

        val cute = ThemePaletteRegistry.paletteFor(AppThemeId.CUTE_CREAM, ThemeAppearance.LIGHT, false)
        panel.applyTheme(cute)

        // 狀態機／錄音作業不在面板內，這裡驗證畫面狀態沒有被重設。
        assertEquals(View.VISIBLE, panel.idleActions.visibility)
        assertEquals(View.VISIBLE, panel.recordingCancelButton.visibility)
        assertEquals(View.INVISIBLE, panel.idleTitle.visibility)
        assertEquals(View.INVISIBLE, panel.idleHint.visibility)
        assertEquals("停止錄音", panel.idleMicButton.contentDescription)
        assertEquals(scaleBefore, panel.idleMicButton.scaleX, 0.0001f)
        assertEquals(
            boundsBefore,
            listOf(
                panel.idleMicButton.left,
                panel.idleMicButton.top,
                panel.idleMicButton.right,
                panel.idleMicButton.bottom
            )
        )
        // 顏色確實換成新主題的 Token。
        assertEquals(AppThemeId.CUTE_CREAM, panel.currentPalette().themeId)
        assertFalse(panel.currentPalette().isDark)
        assertEquals(cute.statusRecording, (panel.statusDot.background as ImeShape).resolvedFillColor())
        assertNotEquals(
            ThemePaletteRegistry.legacyImePalette().statusRecording,
            (panel.statusDot.background as ImeShape).resolvedFillColor()
        )
    }

    @Test
    fun darkAndLightAppearanceOfTheSameThemeRenderDifferentColours() {
        val panel = panel()
        layoutPanel(panel, widthDp = 360)

        panel.applyTheme(palette(AppThemeId.DAILY_LIVING, ThemeAppearance.LIGHT))
        val lightBackground = panel.currentPalette().background

        panel.applyTheme(palette(AppThemeId.DAILY_LIVING, ThemeAppearance.DARK))
        val darkBackground = panel.currentPalette().background

        assertNotEquals(lightBackground, darkBackground)
        assertTrue(panel.currentPalette().isDark)
    }

    @Test
    fun legacyThemeKeepsTheV0155ImeColoursOnEveryAppearance() {
        val panel = panel()
        layoutPanel(panel, widthDp = 360)
        val legacy = ThemePaletteRegistry.legacyImePalette()

        listOf(ThemeAppearance.SYSTEM, ThemeAppearance.LIGHT, ThemeAppearance.DARK).forEach { appearance ->
            panel.applyTheme(palette(AppThemeId.LEGACY_WARM, appearance, systemDark = true))
            assertEquals(legacy.background, panel.currentPalette().background)
            assertTrue(panel.currentPalette().isDark)
        }
    }
}
