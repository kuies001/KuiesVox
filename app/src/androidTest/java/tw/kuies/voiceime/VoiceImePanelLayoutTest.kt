package tw.kuies.voiceime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceImePanelLayoutTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun previewAddsOnlyOneCompactLineAndDivider() {
        val emptyPanel = panel()
        val previewPanel = panel().apply {
            render(VoiceImeState.IDLE, "A long recognized sentence to preview")
        }

        val heightIncrease = measuredHeight(previewPanel) - measuredHeight(emptyPanel)
        val maxPreviewHeight = (32 * context.resources.displayMetrics.density).toInt()

        assertTrue(
            "Preview added $heightIncrease px; expected at most $maxPreviewHeight px",
            heightIncrease in 0..maxPreviewHeight
        )
        assertEquals(1, previewPanel.previewText.maxLines)
        assertEquals(android.text.TextUtils.TruncateAt.END, previewPanel.previewText.ellipsize)
    }

    @Test
    fun emptyPreviewHidesTextAndDividerWithoutAddingHeight() {
        val emptyPanel = panel()
        val blankPanel = panel().apply { render(VoiceImeState.IDLE, "   ") }

        assertEquals(View.GONE, emptyPanel.previewText.visibility)
        assertEquals(View.GONE, emptyPanel.previewDivider.visibility)
        assertEquals(measuredHeight(emptyPanel), measuredHeight(blankPanel))
    }

    @Test
    fun switchInputMethodButtonRemainsClickable() {
        var switched = false
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = { switched = true }
        )
        panel.setSwitchAvailable(true)

        assertTrue(panel.switchButton.performClick())
        assertTrue(switched)
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
    fun backspaceButtonIsSmallClickableAndAvailableWhileVoiceIsBusy() {
        var deletions = 0
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onDelete = { deletions += 1 }
        )

        panel.render(VoiceImeState.TRANSCRIBING, null)

        assertEquals(View.VISIBLE, panel.backspaceButton.visibility)
        assertEquals(
            (40 * context.resources.displayMetrics.density).toInt(),
            panel.backspaceButton.layoutParams.width
        )
        assertTrue(panel.backspaceButton.performClick())
        assertEquals(1, deletions)

        panel.render(VoiceImeState.RECORDING, null)
        assertEquals(View.VISIBLE, panel.backspaceButton.visibility)
    }

    @Test
    fun toolbarShowsEqualDeleteEnterAndKeyboardButtonsDuringVoiceStates() {
        var deletions = 0
        var enters = 0
        var switches = 0
        var settingsOpens = 0
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = { switches += 1 },
            onEnter = { enters += 1 },
            onDelete = { deletions += 1 },
            onOpenSettings = { settingsOpens += 1 }
        )
        panel.setSwitchAvailable(true)

        listOf(VoiceImeState.IDLE, VoiceImeState.RECORDING, VoiceImeState.TRANSCRIBING,
            VoiceImeState.FORMATTING).forEach { state ->
            panel.render(state, null)
            assertEquals(View.VISIBLE, panel.settingsButton.visibility)
            assertEquals(state == VoiceImeState.IDLE, panel.settingsButton.isEnabled)
            assertEquals(View.VISIBLE, panel.backspaceButton.visibility)
            assertEquals(View.VISIBLE, panel.enterButton.visibility)
            assertEquals(View.VISIBLE, panel.switchButton.visibility)
        }

        val widths = listOf(
            panel.settingsButton.layoutParams.width,
            panel.backspaceButton.layoutParams.width,
            panel.enterButton.layoutParams.width,
            panel.switchButton.layoutParams.width
        )
        assertTrue(widths.all { it == widths.first() })
        val settingsGap = (panel.settingsButton.layoutParams as LinearLayout.LayoutParams).marginEnd
        val deleteGap = (panel.backspaceButton.layoutParams as LinearLayout.LayoutParams).marginEnd
        val enterGap = (panel.enterButton.layoutParams as LinearLayout.LayoutParams).marginEnd
        assertTrue(deleteGap > settingsGap)
        assertTrue(enterGap > settingsGap)
        assertTrue(panel.backspaceButton.performClick())
        assertTrue(panel.enterButton.performClick())
        assertTrue(panel.switchButton.performClick())
        panel.render(VoiceImeState.IDLE, null)
        assertTrue(panel.settingsButton.performClick())
        assertEquals(1, deletions)
        assertEquals(1, enters)
        assertEquals(1, switches)
        assertEquals(1, settingsOpens)
    }

    @Test
    fun bulkActionsAreVisibleDirectlyAndClearRequiresConfirmation() {
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

        assertEquals(View.VISIBLE, panel.bulkActionsRow.visibility)
        assertTrue(panel.selectAllButton.isEnabled)
        assertTrue(panel.selectAllButton.performClick())
        assertEquals(1, selections)

        panel.clearAllButton.performClick()
        assertEquals(View.VISIBLE, panel.clearConfirmationPanel.visibility)
        assertEquals(0, clears)

        panel.cancelClearAllButton.performClick()
        assertEquals(View.GONE, panel.clearConfirmationPanel.visibility)
        panel.clearAllButton.performClick()
        panel.confirmClearAllButton.performClick()
        assertEquals(1, clears)
        assertEquals(View.GONE, panel.clearConfirmationPanel.visibility)

        panel.render(VoiceImeState.FORMATTING, null)
        assertFalse(panel.clearAllButton.isEnabled)
        assertEquals(View.VISIBLE, panel.bulkActionsRow.visibility)
        assertEquals(View.GONE, panel.clearConfirmationPanel.visibility)
    }

    @Test
    fun idlePanelFitsCompactHeightWithSmallerMicrophone() {
        val panel = panel()
        val maxHeight = (225 * context.resources.displayMetrics.density).toInt()
        val panelHeight = measuredHeight(panel)

        assertTrue("Panel height was ${panelHeight}px, max is $maxHeight px", panelHeight <= maxHeight)
    }

    @Test
    fun idleAndRecordingStatesShareFixedInteractionAndPanelHeights() {
        val panel = panel()
        val preview = "最近輸入的預覽文字"
        val expectedInteractionHeight =
            (VOICE_IME_MAIN_INTERACTION_HEIGHT_DP * context.resources.displayMetrics.density).toInt()

        panel.render(VoiceImeState.IDLE, preview)
        val idlePanelHeight = measuredHeight(panel)

        assertTrue(panel.mainInteractionContainer.layoutParams is LinearLayout.LayoutParams)
        assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.layoutParams.height)
        assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.measuredHeight)
        assertEquals(View.VISIBLE, panel.idleActions.visibility)

        panel.render(VoiceImeState.RECORDING, preview)
        val recordingPanelHeight = measuredHeight(panel)

        assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.layoutParams.height)
        assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.measuredHeight)
        assertEquals(View.GONE, panel.idleActions.visibility)
        assertEquals(View.VISIBLE, panel.recordingActions.visibility)
        assertEquals(idlePanelHeight, recordingPanelHeight)
    }

    @Test
    fun navigationBarInsetsAreReservedAtTheBottom() {
        val panel = panel()
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

    @Test
    fun actionRowsSwitchWithoutOverlappingAcrossActiveStates() {
        val panel = panel()

        panel.render(VoiceImeState.RECORDING, "last result")
        assertEquals(View.VISIBLE, panel.recordingActions.visibility)
        assertEquals(View.GONE, panel.busyActions.visibility)
        assertEquals(View.GONE, panel.idleActions.visibility)

        panel.render(VoiceImeState.TRANSCRIBING, "last result")
        assertEquals(View.GONE, panel.recordingActions.visibility)
        assertEquals(View.VISIBLE, panel.busyActions.visibility)
        assertEquals(View.GONE, panel.idleActions.visibility)

        panel.render(VoiceImeState.FORMATTING, "last result")
        assertEquals(View.GONE, panel.recordingActions.visibility)
        assertEquals(View.VISIBLE, panel.busyActions.visibility)
        assertEquals(View.GONE, panel.idleActions.visibility)

        panel.render(VoiceImeState.IDLE, "last result")
        assertEquals(View.VISIBLE, panel.idleActions.visibility)
        assertEquals(View.GONE, panel.busyActions.visibility)
        assertEquals(View.GONE, panel.recordingActions.visibility)
    }

    private fun panel() = VoiceImePanel(
        context = context,
        onVoiceAction = {},
        onCancel = {},
        onSwitchInputMethod = {}
    )

    private fun measuredHeight(panel: VoiceImePanel): Int {
        val width = (360 * context.resources.displayMetrics.density).toInt()
        panel.view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return panel.view.measuredHeight
    }
}
