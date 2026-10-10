package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarIconSpecTest {
    @Test
    fun everyToolbarIconUsesTheSharedButtonHeightAndOneBaseSize() {
        assertEquals(36, TOOLBAR_BUTTON_HEIGHT_DP)
        assertEquals(22, TOOLBAR_ICON_SIZE_DP)
        assertEquals(20, TOOLBAR_ICON_COMPACT_SIZE_DP)
        // 滿版圖示的補償只允許小幅差異。
        assertTrue(TOOLBAR_ICON_SIZE_DP - TOOLBAR_ICON_COMPACT_SIZE_DP <= 2)
    }

    @Test
    fun theIconPaddingCentresTheIconInTheSharedButtonHeight() {
        assertEquals(7, toolbarIconPaddingDp(TOOLBAR_ICON_SIZE_DP))
        assertEquals(8, toolbarIconPaddingDp(TOOLBAR_ICON_COMPACT_SIZE_DP))

        val renderedBase = TOOLBAR_BUTTON_HEIGHT_DP - 2 * toolbarIconPaddingDp(TOOLBAR_ICON_SIZE_DP)
        assertEquals(TOOLBAR_ICON_SIZE_DP, renderedBase)
    }
}
