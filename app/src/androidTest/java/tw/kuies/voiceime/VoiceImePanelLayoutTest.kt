package tw.kuies.voiceime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceImePanelLayoutTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun toolbarHasOneRowInTheRequiredOrderAndNoStatusText() {
        val panel = panel()
        panel.setSwitchAvailable(true)
        val row = panel.topToolbarRow

        assertEquals(7, row.childCount)
        assertEquals(panel.statusIndicator, row.getChildAt(0))
        assertEquals(panel.selectAllButton, row.getChildAt(1))
        assertEquals(panel.clipboardButton, row.getChildAt(2))
        assertEquals(panel.clearAllButton, row.getChildAt(3))
        assertEquals(panel.switchButton, row.getChildAt(4))
        assertEquals(panel.settingsButton, row.getChildAt(5))
        assertEquals(panel.moreButton, row.getChildAt(6))
        assertEquals(2, panel.toolbarContainer.childCount)
        assertEquals(2, panel.mainPanel.childCount)
        assertTrue(allTextViews(row).none { it.text in setOf("待命", "錄音中") })

        layoutPanel(panel, widthDp = 320)
        assertTrue(panel.moreButton.right <= row.width)
        assertTrue(panel.moreButton.right > panel.settingsButton.right)

        val edgeGestureInset = (24 * context.resources.displayMetrics.density).toInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.systemGestures(),
                Insets.of(edgeGestureInset, 0, edgeGestureInset, 0)
            )
            .build()
        ViewCompat.dispatchApplyWindowInsets(panel.view, insets)
        layoutPanel(panel, widthDp = 320)

        assertTrue(panel.moreButton.right <= row.width)
        for (index in 0 until row.childCount - 1) {
            assertTrue(row.getChildAt(index).right <= row.getChildAt(index + 1).left)
        }
    }

    @Test
    fun statusIndicatorIsAnAccessibleRoundDotWithStateColors() {
        val panel = panel()
        val colors = listOf(
            VoiceImeState.IDLE to Color.rgb(169, 228, 212),
            VoiceImeState.RECORDING to Color.rgb(232, 153, 161),
            VoiceImeState.TRANSCRIBING to Color.rgb(255, 192, 118),
            VoiceImeState.FORMATTING to Color.rgb(255, 192, 118),
            VoiceImeState.SUCCESS to Color.rgb(169, 228, 212),
            VoiceImeState.FORMATTING_FALLBACK to Color.rgb(255, 192, 118),
            VoiceImeState.CANCELLED to Color.rgb(180, 190, 206),
            VoiceImeState.ERROR to Color.rgb(255, 142, 142)
        )

        colors.forEach { (state, expectedColor) ->
            panel.render(state)
            val dot = panel.statusDot.background as GradientDrawable
            assertEquals(GradientDrawable.OVAL, dot.shape)
            assertEquals(expectedColor, dot.color?.defaultColor)
            assertEquals(state.accessibilityLabel(), panel.statusIndicator.contentDescription)
        }
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, panel.statusIndicator.importantForAccessibility)
        assertEquals(
            (14 * context.resources.displayMetrics.density).toInt(),
            panel.statusDot.layoutParams.width
        )
        assertEquals(null, panel.statusIndicator.background)
    }

    @Test
    fun clipboardButtonOpensClipboardHistoryDirectly() {
        var historyRequests = 0
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onOpenClipboardHistory = { historyRequests += 1 },
            onIsSensitiveEditor = { false }
        )

        assertEquals("剪貼簿歷史", panel.clipboardButton.contentDescription)
        assertTrue(panel.clipboardButton.performClick())
        assertTrue(panel.isShowingClipboardHistory())
        assertEquals(1, historyRequests)
    }

    @Test
    fun allPanelPagesKeepTheSameHeight() {
        val panel = panel()
        val heights = mutableListOf(measuredHeight(panel))

        assertEquals(
            (VOICE_IME_PAGE_CONTENT_HEIGHT_DP * context.resources.displayMetrics.density).toInt(),
            panel.mainPanel.layoutParams.height
        )
        assertTrue(panel.moreButton.performClick())
        heights += measuredHeight(panel)
        panel.showClipboardHistoryPanel(isSensitiveEditor = true)
        heights += measuredHeight(panel)
        panel.showVoiceHistoryPanel(isSensitiveEditor = true)
        heights += measuredHeight(panel)

        assertTrue(heights.all { it == heights.first() })
    }

    @Test
    fun keyboardAndSettingsRemainClickable() {
        var switched = false
        var settingsOpened = false
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = { switched = true },
            onOpenSettings = { settingsOpened = true }
        )
        panel.setSwitchAvailable(true)

        assertTrue(panel.switchButton.performClick())
        assertTrue(panel.settingsButton.performClick())
        assertTrue(switched)
        assertTrue(settingsOpened)
        assertEquals("切換鍵盤", panel.switchButton.contentDescription)
        assertEquals("設定", panel.settingsButton.contentDescription)
    }

    @Test
    fun idleStateUsesCompactRoundMicrophoneButtonWithTwoLabels() {
        var started = false
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = { started = true },
            onCancel = {},
            onSwitchInputMethod = {}
        )

        assertEquals(
            (84 * context.resources.displayMetrics.density).toInt(),
            panel.idleMicButton.layoutParams.width
        )
        assertEquals(panel.idleMicButton.layoutParams.width, panel.idleMicButton.layoutParams.height)
        val circle = (panel.idleMicButton.background as RippleDrawable).getDrawable(0) as GradientDrawable
        assertEquals(GradientDrawable.OVAL, circle.shape)
        assertEquals("開始語音輸入", panel.idleTitle.text)
        assertEquals("點一下開始說話", panel.idleHint.text)
        assertTrue(panel.idleMicButton.performClick())
        assertTrue(started)
    }

    @Test
    fun mainVoiceActionsStayLeftOfDeleteAndEnterOnTheRight() {
        val panel = panel()
        layoutPanel(panel, widthDp = 360)
        val row = panel.mainInteractionContainer.getChildAt(0) as LinearLayout
        val voiceArea = row.getChildAt(0)
        val sideActions = row.getChildAt(1) as LinearLayout

        assertTrue(voiceArea.left < sideActions.left)
        assertTrue(sideActions.left >= voiceArea.right)
        assertEquals(sideActions, panel.backspaceButton.parent)
        assertEquals(sideActions, panel.enterButton.parent)
        assertEquals(0, sideActions.indexOfChild(panel.backspaceButton))
        assertEquals(2, sideActions.indexOfChild(panel.enterButton))
        assertEquals("刪除", panel.backspaceButton.contentDescription)
        assertEquals("換行", panel.enterButton.contentDescription)
        assertNotEquals(R.drawable.ic_ime_backspace, R.drawable.ic_ime_enter)
    }

    @Test
    fun deleteAndEnterRemainClickableDuringVoiceWork() {
        var deletions = 0
        var enters = 0
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onDelete = { deletions += 1 },
            onEnter = { enters += 1 }
        )

        listOf(VoiceImeState.RECORDING, VoiceImeState.TRANSCRIBING, VoiceImeState.FORMATTING)
            .forEach { state ->
                panel.render(state)
                assertEquals(View.VISIBLE, panel.backspaceButton.visibility)
                assertEquals(View.VISIBLE, panel.enterButton.visibility)
            }
        assertTrue(panel.backspaceButton.performClick())
        assertTrue(panel.enterButton.performClick())
        assertEquals(1, deletions)
        assertEquals(1, enters)
    }

    @Test
    fun clearActionKeepsItsConfirmationInTheSingleToolbarRow() {
        var selections = 0
        var clears = 0
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onSelectAll = { selections += 1 },
            onClearAll = { clears += 1 }
        )

        assertTrue(panel.selectAllButton.performClick())
        assertEquals(1, selections)
        panel.clearAllButton.performClick()
        assertEquals(View.GONE, panel.topToolbarRow.visibility)
        assertEquals(View.VISIBLE, panel.clearConfirmationPanel.visibility)
        assertEquals(36, panel.clearConfirmationPanel.layoutParams.height)
        assertEquals(0, clears)

        panel.cancelClearAllButton.performClick()
        assertEquals(View.GONE, panel.clearConfirmationPanel.visibility)
        assertEquals(View.VISIBLE, panel.topToolbarRow.visibility)
        panel.clearAllButton.performClick()
        panel.confirmClearAllButton.performClick()
        assertEquals(1, clears)
        assertEquals(View.GONE, panel.clearConfirmationPanel.visibility)

        panel.render(VoiceImeState.FORMATTING)
        assertFalse(panel.clearAllButton.isEnabled)
        assertEquals(View.VISIBLE, panel.topToolbarRow.visibility)
        assertEquals(View.GONE, panel.clearConfirmationPanel.visibility)
    }

    @Test
    fun mainVoiceScreenDoesNotShowARecentTranscriptPreview() {
        val panel = panel()

        assertTrue(allTextViews(panel.idleActions).none { it.text.contains("最近") })
        panel.render(VoiceImeState.SUCCESS)
        assertTrue(allTextViews(panel.mainInteractionContainer).none { it.text.contains("最近") })
    }

    @Test
    fun fixedInteractionHeightAndVoiceActionsDoNotOverlapAcrossStates() {
        val panel = panel()
        val expectedInteractionHeight =
            (VOICE_IME_MAIN_INTERACTION_HEIGHT_DP * context.resources.displayMetrics.density).toInt()

        listOf(VoiceImeState.IDLE, VoiceImeState.RECORDING, VoiceImeState.TRANSCRIBING,
            VoiceImeState.FORMATTING, VoiceImeState.SUCCESS).forEach { state ->
            panel.render(state)
            measuredHeight(panel)
            assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.layoutParams.height)
            assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.measuredHeight)
        }
        assertEquals(View.VISIBLE, panel.idleActions.visibility)
        panel.render(VoiceImeState.RECORDING)
        assertEquals(View.VISIBLE, panel.recordingActions.visibility)
        assertEquals(View.GONE, panel.busyActions.visibility)
        panel.render(VoiceImeState.TRANSCRIBING)
        assertEquals(View.VISIBLE, panel.busyActions.visibility)
        assertEquals(View.GONE, panel.recordingActions.visibility)
    }

    @Test
    fun panelFitsCompactHeightAndReservesNavigationInsetsAtTheBottom() {
        val panel = panel()
        val maxHeight = (205 * context.resources.displayMetrics.density).toInt()
        val originalHeight = measuredHeight(panel)

        assertTrue("Panel height was ${originalHeight}px, max is $maxHeight px", originalHeight <= maxHeight)

        val root = panel.view
        val originalBottomPadding = root.paddingBottom
        val originalLeftPadding = root.paddingLeft
        val originalRightPadding = root.paddingRight
        val navigationBarInset = (32 * context.resources.displayMetrics.density).toInt()
        val systemGestureInset = (40 * context.resources.displayMetrics.density).toInt()
        val leftGestureInset = (8 * context.resources.displayMetrics.density).toInt()
        val rightGestureInset = (6 * context.resources.displayMetrics.density).toInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                Insets.of(0, 0, 0, navigationBarInset)
            )
            .setInsets(
                WindowInsetsCompat.Type.systemGestures(),
                Insets.of(leftGestureInset, 0, rightGestureInset, systemGestureInset)
            )
            .build()

        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(originalBottomPadding + systemGestureInset, root.paddingBottom)
        assertEquals(originalLeftPadding + leftGestureInset, root.paddingLeft)
        assertEquals(originalRightPadding + rightGestureInset, root.paddingRight)
    }

    private fun panel() = VoiceImePanel(
        context = context,
        onVoiceAction = {},
        onCancel = {},
        onSwitchInputMethod = {}
    )

    private fun measuredHeight(panel: VoiceImePanel): Int = layoutPanel(panel, widthDp = 360)

    private fun layoutPanel(panel: VoiceImePanel, widthDp: Int): Int {
        val width = (widthDp * context.resources.displayMetrics.density).toInt()
        panel.view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        panel.view.layout(0, 0, panel.view.measuredWidth, panel.view.measuredHeight)
        return panel.view.measuredHeight
    }

    private fun allTextViews(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(allTextViews(view.getChildAt(index)))
        }
    }

    private fun VoiceImeState.accessibilityLabel(): String = when (this) {
        VoiceImeState.IDLE -> "待命"
        VoiceImeState.RECORDING -> "錄音中"
        VoiceImeState.TRANSCRIBING -> "語音辨識中"
        VoiceImeState.FORMATTING -> "智慧整理中"
        VoiceImeState.SUCCESS -> "辨識完成"
        VoiceImeState.FORMATTING_FALLBACK -> "整理失敗，已保留辨識結果"
        VoiceImeState.CANCELLED -> "已取消"
        VoiceImeState.ERROR -> "發生錯誤"
    }
}
