package tw.kuies.voiceime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
    fun statusAndSingleRowToolbarAreVisuallySeparatedInTheRequiredOrder() {
        val panel = panel()
        panel.setSwitchAvailable(true)
        val row = panel.topToolbarRow

        assertEquals(2, panel.topAreaRow.childCount)
        assertEquals(panel.statusIndicator, panel.topAreaRow.getChildAt(0))
        assertEquals(panel.toolbarContainer, panel.topAreaRow.getChildAt(1))
        assertEquals(6, row.childCount)
        assertEquals(panel.clipboardButton, row.getChildAt(0))
        assertEquals(panel.selectAllButton, row.getChildAt(1))
        assertEquals(panel.clearAllButton, row.getChildAt(2))
        assertEquals(panel.switchButton, row.getChildAt(3))
        assertEquals(panel.settingsButton, row.getChildAt(4))
        assertEquals(panel.moreButton, row.getChildAt(5))
        assertEquals(2, panel.toolbarContainer.childCount)
        assertEquals(2, panel.mainPanel.childCount)
        assertEquals(2, allTextViews(row).size)
        assertTrue(panel.toolbarContainer.background is GradientDrawable)
        assertEquals(null, panel.statusIndicator.background)
        assertFalse(panel.statusIndicator.isClickable)
        val statusParams = panel.statusIndicator.layoutParams as LinearLayout.LayoutParams
        assertEquals(
            (40 * context.resources.displayMetrics.density).toInt(),
            statusParams.width
        )
        assertEquals(
            (12 * context.resources.displayMetrics.density).toInt(),
            statusParams.marginEnd
        )
        listOf(0, 3, 4, 5).forEach { index ->
            val params = row.getChildAt(index).layoutParams as LinearLayout.LayoutParams
            assertEquals(0, params.width)
            assertEquals(1f, params.weight)
        }

        layoutPanel(panel, widthDp = 320)
        assertTrue(panel.statusIndicator.left < panel.toolbarContainer.left)
        assertTrue(
            panel.toolbarContainer.left -
                (panel.statusIndicator.left + panel.statusDot.right) >=
                (20 * context.resources.displayMetrics.density).toInt()
        )
        assertEquals(panel.topAreaRow.width, panel.statusIndicator.width + statusParams.marginEnd + panel.toolbarContainer.width)
        assertEquals(panel.toolbarContainer.width, row.width)
        assertTrue(panel.clipboardButton.width > (28 * context.resources.displayMetrics.density).toInt())
        assertTrue(panel.moreButton.right == row.width)
        assertTrue(panel.moreButton.right > panel.settingsButton.right)
        assertCompleteToolbarLabel(panel.selectAllButton, "全選")
        assertCompleteToolbarLabel(panel.clearAllButton, "清除")

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
        for (index in 0 until row.childCount) {
            assertTrue(row.getChildAt(index).left >= 0)
            assertTrue(row.getChildAt(index).right <= row.width)
        }
        assertTrue(
            panel.toolbarContainer.left -
                (panel.statusIndicator.left + panel.statusDot.right) >=
                (20 * context.resources.displayMetrics.density).toInt()
        )
        assertCompleteToolbarLabel(panel.selectAllButton, "全選")
        assertCompleteToolbarLabel(panel.clearAllButton, "清除")
    }

    @Test
    fun statusIndicatorIsAnAccessibleRoundDotWithStateColors() {
        val panel = panel()
        val colors = listOf(
            VoiceImeState.IDLE to Color.rgb(169, 228, 212),
            VoiceImeState.RECORDING to Color.rgb(232, 153, 161),
            VoiceImeState.TRANSCRIBING to Color.rgb(255, 192, 118),
            VoiceImeState.FORMATTING to Color.rgb(255, 192, 118),
            VoiceImeState.AWAITING_CONFIRM to Color.rgb(189, 186, 255),
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
    fun imePanelUsesTheFullDisplayViewportWidthAcrossItsParents() {
        val panel = panel()
        panel.setSwitchAvailable(true)
        val root = panel.view
        val viewportWidth = context.resources.displayMetrics.widthPixels
        val density = context.resources.displayMetrics.density
        val host = FrameLayout(context)
        host.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        host.measure(
            View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)

        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, root.layoutParams.width)
        assertEquals(viewportWidth, host.measuredWidth)
        assertEquals(viewportWidth, root.measuredWidth)
        assertTrue(root.paddingLeft <= (16 * density).toInt())
        assertTrue(root.paddingRight <= (16 * density).toInt())
        assertTrue(root.width - root.paddingLeft - root.paddingRight >= viewportWidth - (32 * density).toInt())
        assertEquals(root.width - root.paddingLeft - root.paddingRight, panel.mainPanel.width)
        assertEquals(panel.mainPanel.width, panel.topAreaRow.width)

        val statusParams = panel.statusIndicator.layoutParams as LinearLayout.LayoutParams
        assertTrue(panel.statusIndicator.width <= (40 * density).toInt())
        assertEquals(
            panel.topAreaRow.width - panel.statusIndicator.width - statusParams.marginEnd,
            panel.toolbarContainer.width
        )
        assertTrue(panel.toolbarContainer.width > (panel.topAreaRow.width * 0.7f))
        assertEquals(panel.toolbarContainer.width, panel.topToolbarRow.width)
        assertEquals(panel.topToolbarRow.width, panel.moreButton.right)
        assertCompleteToolbarLabel(panel.selectAllButton, "全選")
        assertCompleteToolbarLabel(panel.clearAllButton, "清除")

        val interactionRow = panel.mainInteractionContainer.getChildAt(0) as LinearLayout
        val voiceArea = interactionRow.getChildAt(0)
        val sideActions = interactionRow.getChildAt(1) as LinearLayout
        assertEquals(panel.mainPanel.width, panel.mainInteractionContainer.width)
        assertEquals(panel.mainInteractionContainer.width, interactionRow.width)
        assertEquals(interactionRow.width - sideActions.width, voiceArea.width)
        val sideButtonRightInRoot = panel.mainPanel.left + panel.mainInteractionContainer.left +
            interactionRow.left + sideActions.left + panel.backspaceButton.right
        val sideButtonRightGap = root.width - sideButtonRightInRoot
        assertTrue(
            "Right action is ${sideButtonRightGap / density}dp from the viewport edge",
            sideButtonRightGap <= (20 * density).toInt()
        )
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
        panel.showAiEditPreview(
            originalText = "你們到底什麼時候才要處理？",
            resultText = "想請問目前的處理進度如何？"
        )
        heights += measuredHeight(panel)
        panel.showTranslationResult(
            originalText = "你明天有空嗎？",
            translatedText = "Are you free tomorrow?",
            message = "目前輸入目標無法安全插入，原文未更動；可複製結果後自行貼上。"
        )
        heights += measuredHeight(panel)

        val safePanel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onIsSensitiveEditor = { false }
        )
        val safeHeights = mutableListOf(measuredHeight(safePanel))
        safePanel.showSavedSnippetsPanel(isSensitiveEditor = false)
        safePanel.updateSavedSnippets(
            SavedSnippetLibrary(
                categories = SavedSnippetCategoryDefaults.entries,
                snippets = listOf(
                    SavedSnippet("one", "第一則", "這是較長的快捷短語預覽內容。\n仍會完整插入。", "general", true, 1, 1, null),
                    SavedSnippet("two", "第二則", "第二筆", "work", false, 1, 1, null)
                )
            )
        )
        safeHeights += measuredHeight(safePanel)
        assertTrue(safePanel.snippetListScrollView.measuredHeight > 0)
        val firstSnippetTitle = allTextViews(safePanel.savedSnippetsPanel)
            .first { it.text.toString() == "第一則" }
        assertTrue((firstSnippetTitle.parent as View).performClick())
        safeHeights += measuredHeight(safePanel)

        assertTrue(heights.all { it == heights.first() })
        assertTrue(safeHeights.all { it == safeHeights.first() })
    }

    @Test
    fun savedSnippetManagementRoutesOpenFromTheImePanel() {
        val managementRequests = mutableListOf<Pair<SavedSnippetManagerAction, String?>>()
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onManageSavedSnippets = { action, id -> managementRequests += action to id },
            onIsSensitiveEditor = { false }
        )
        panel.showSavedSnippetsPanel(isSensitiveEditor = false)
        panel.updateSavedSnippets(
            SavedSnippetLibrary(
                SavedSnippetCategoryDefaults.entries,
                listOf(SavedSnippet("snippet-1", "標題", "完整內容", "general", false, 1, 1, null))
            )
        )

        val snippetAddButton = allTextViews(panel.savedSnippetsPanel)
            .first { it.text.toString() == "新增" }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(snippetAddButton.performClick())
        }

        val editButton = allTextViews(panel.savedSnippetsPanel)
            .first { it.text.toString() == "編輯" }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(editButton.performClick())
        }
        assertEquals(
            listOf(
                SavedSnippetManagerAction.NEW to null,
                SavedSnippetManagerAction.EDIT to "snippet-1"
            ),
            managementRequests
        )
    }

    @Test
    fun savedSnippetTapOnlyInsertsWithoutOtherSideEffects() {
        val snippet = SavedSnippet("snippet-1", "範例", "多行文字\n保留完整內容", "general", false, 1, 1, null)
        val inserted = mutableListOf<SavedSnippet>()
        var voiceActions = 0
        var clipboardHistoryReads = 0
        var voiceHistoryReads = 0
        var historyInserts = 0
        var clipboardCopies = 0
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = { voiceActions++ },
            onCancel = {},
            onSwitchInputMethod = {},
            onOpenClipboardHistory = { clipboardHistoryReads++ },
            onOpenVoiceHistory = { voiceHistoryReads++ },
            onInsertHistoryText = { historyInserts++ },
            onCopyHistoryText = { clipboardCopies++ },
            onInsertSavedSnippet = {
                inserted += it
                true
            },
            onIsSensitiveEditor = { false }
        )
        panel.showSavedSnippetsPanel(isSensitiveEditor = false)
        panel.updateSavedSnippets(
            SavedSnippetLibrary(SavedSnippetCategoryDefaults.entries, listOf(snippet))
        )

        val title = allTextViews(panel.savedSnippetsPanel).first { it.text.toString() == snippet.title }
        val contentColumn = title.parent as View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(contentColumn.performClick())
        }

        assertEquals(listOf(snippet), inserted)
        assertEquals(0, voiceActions)
        assertEquals(0, clipboardHistoryReads)
        assertEquals(0, voiceHistoryReads)
        assertEquals(0, historyInserts)
        assertEquals(0, clipboardCopies)
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
        assertTrue(voiceArea.width > (240 * context.resources.displayMetrics.density).toInt())
        val sideActionParams = sideActions.layoutParams as LinearLayout.LayoutParams
        assertEquals((52 * context.resources.displayMetrics.density).toInt(), sideActionParams.width)
        assertEquals(0, sideActionParams.marginEnd)
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
        assertEquals(
            (36 * context.resources.displayMetrics.density).toInt(),
            panel.clearConfirmationPanel.layoutParams.height
        )
        assertEquals(0, clears)

        val confirmationActions = panel.clearConfirmationPanel.getChildAt(0) as LinearLayout
        assertEquals(2, confirmationActions.childCount)
        assertEquals(panel.confirmClearAllButton, confirmationActions.getChildAt(0))
        assertEquals(panel.cancelClearAllButton, confirmationActions.getChildAt(1))
        assertEquals("確定清除全部", panel.confirmClearAllButton.text.toString())
        assertEquals("取消", panel.cancelClearAllButton.text.toString())
        assertEquals(Gravity.CENTER, panel.confirmClearAllButton.gravity)
        assertEquals(Gravity.CENTER, panel.cancelClearAllButton.gravity)
        assertEquals(1, panel.confirmClearAllButton.maxLines)
        assertEquals(1, panel.cancelClearAllButton.maxLines)
        assertEquals(null, panel.confirmClearAllButton.ellipsize)
        assertEquals(null, panel.cancelClearAllButton.ellipsize)
        assertEquals(panel.confirmClearAllButton.textSize, panel.cancelClearAllButton.textSize)
        assertEquals(panel.confirmClearAllButton.paddingLeft, panel.cancelClearAllButton.paddingLeft)
        assertEquals(panel.confirmClearAllButton.paddingRight, panel.cancelClearAllButton.paddingRight)
        val confirmParams = panel.confirmClearAllButton.layoutParams as LinearLayout.LayoutParams
        val cancelParams = panel.cancelClearAllButton.layoutParams as LinearLayout.LayoutParams
        assertEquals(2f, confirmParams.weight)
        assertEquals(1f, cancelParams.weight)
        assertEquals(confirmParams.height, cancelParams.height)

        layoutPanel(panel, widthDp = 320)
        assertTrue(panel.confirmClearAllButton.width > panel.cancelClearAllButton.width)
        assertEquals(panel.confirmClearAllButton.top, panel.cancelClearAllButton.top)
        assertEquals(panel.confirmClearAllButton.bottom, panel.cancelClearAllButton.bottom)
        assertCompleteToolbarLabel(panel.confirmClearAllButton, "確定清除全部")
        assertCompleteToolbarLabel(panel.cancelClearAllButton, "取消")
        val confirmBackground = (panel.confirmClearAllButton.background as RippleDrawable)
            .getDrawable(0) as GradientDrawable
        val cancelBackground = (panel.cancelClearAllButton.background as RippleDrawable)
            .getDrawable(0) as GradientDrawable
        assertEquals(confirmBackground.cornerRadius, cancelBackground.cornerRadius)

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
    fun recordingStopAndCancelButtonsUseBalancedSharedStyle() {
        val panel = panel()
        panel.render(VoiceImeState.RECORDING)
        layoutPanel(panel, widthDp = 360)

        val stopButton = panel.recordingActions.getChildAt(0) as LinearLayout
        val cancelButton = panel.recordingActions.getChildAt(1) as LinearLayout
        val stopParams = stopButton.layoutParams as LinearLayout.LayoutParams
        val cancelParams = cancelButton.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, stopParams.weight)
        assertEquals(stopParams.weight, cancelParams.weight)
        assertEquals(stopParams.height, cancelParams.height)
        assertTrue(kotlin.math.abs(stopButton.width - cancelButton.width) <= 1)
        assertEquals(Gravity.CENTER, stopButton.gravity)
        assertEquals(Gravity.CENTER, cancelButton.gravity)
        assertEquals(0, stopButton.paddingLeft)
        assertEquals(0, cancelButton.paddingLeft)

        val stopIcon = stopButton.getChildAt(0) as ImageView
        val cancelIcon = cancelButton.getChildAt(0) as ImageView
        assertEquals(stopIcon.layoutParams.width, cancelIcon.layoutParams.width)
        assertEquals(stopIcon.layoutParams.height, cancelIcon.layoutParams.height)
        assertEquals(
            (18 * context.resources.displayMetrics.density).toInt(),
            stopIcon.layoutParams.width
        )
        val stopLabel = stopButton.getChildAt(1) as TextView
        val cancelLabel = cancelButton.getChildAt(1) as TextView
        assertEquals("停止", stopLabel.text.toString())
        assertEquals("取消", cancelLabel.text.toString())
        assertEquals(stopLabel.textSize, cancelLabel.textSize)
        assertEquals(
            (7 * context.resources.displayMetrics.density).toInt(),
            (stopLabel.layoutParams as LinearLayout.LayoutParams).marginStart
        )
        assertEquals(
            (stopLabel.layoutParams as LinearLayout.LayoutParams).marginStart,
            (cancelLabel.layoutParams as LinearLayout.LayoutParams).marginStart
        )
        val stopBackground = (stopButton.background as RippleDrawable)
            .getDrawable(0) as GradientDrawable
        val cancelBackground = (cancelButton.background as RippleDrawable)
            .getDrawable(0) as GradientDrawable
        assertEquals(stopBackground.cornerRadius, cancelBackground.cornerRadius)
        assertEquals(
            (48 * context.resources.displayMetrics.density).toInt(),
            stopButton.height
        )
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
    fun audioLevelIndicatorTracksRecordingWithoutChangingImeHeight() {
        val panel = panel()
        layoutPanel(panel, widthDp = 360)
        val originalPanelHeight = measuredHeight(panel)
        val expectedInteractionHeight =
            (VOICE_IME_MAIN_INTERACTION_HEIGHT_DP * context.resources.displayMetrics.density).toInt()

        assertEquals(View.GONE, panel.audioLevelIndicator.visibility)
        panel.render(VoiceImeState.RECORDING)
        layoutPanel(panel, widthDp = 360)
        assertEquals(View.VISIBLE, panel.audioLevelIndicator.visibility)
        assertEquals("麥克風音量指示", panel.audioLevelIndicator.contentDescription)
        assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.measuredHeight)
        assertTrue(panel.audioLevelIndicator.bottom <= panel.recordingActions.top)
        panel.updateAudioLevel(0.7f)
        assertEquals(0.7f, panel.audioLevelIndicator.level, 0f)
        assertEquals(originalPanelHeight, measuredHeight(panel))

        panel.render(VoiceImeState.TRANSCRIBING)
        assertEquals(View.GONE, panel.audioLevelIndicator.visibility)
        assertEquals(0f, panel.audioLevelIndicator.level, 0f)
        panel.render(VoiceImeState.CANCELLED)
        assertEquals(View.GONE, panel.audioLevelIndicator.visibility)

        panel.render(VoiceImeState.RECORDING, holdToTalkRecording = true)
        layoutPanel(panel, widthDp = 360)
        assertEquals(View.VISIBLE, panel.audioLevelIndicator.visibility)
        assertEquals(View.VISIBLE, panel.idleActions.visibility)
        assertEquals(View.GONE, panel.recordingActions.visibility)
        assertEquals(expectedInteractionHeight, panel.mainInteractionContainer.measuredHeight)
        assertEquals(originalPanelHeight, measuredHeight(panel))
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
        val navigationLeftInset = (20 * context.resources.displayMetrics.density).toInt()
        val navigationRightInset = (18 * context.resources.displayMetrics.density).toInt()
        val systemGestureInset = (40 * context.resources.displayMetrics.density).toInt()
        val leftGestureInset = (40 * context.resources.displayMetrics.density).toInt()
        val rightGestureInset = (36 * context.resources.displayMetrics.density).toInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                Insets.of(navigationLeftInset, 0, navigationRightInset, navigationBarInset)
            )
            .setInsets(
                WindowInsetsCompat.Type.systemGestures(),
                Insets.of(leftGestureInset, 0, rightGestureInset, systemGestureInset)
            )
            .build()

        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(originalBottomPadding + systemGestureInset, root.paddingBottom)
        assertEquals(maxOf(originalLeftPadding, navigationLeftInset), root.paddingLeft)
        assertEquals(maxOf(originalRightPadding, navigationRightInset), root.paddingRight)
    }

    @Test
    fun formatCommandEntryIsOfferedInTheMorePageAndReturnsToTheMainArea() {
        var entered = 0
        val holder = arrayOfNulls<VoiceImePanel>(1)
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onEnterFormatCommandMode = {
                entered += 1
                holder[0]?.showMainPanel()
            }
        )
        holder[0] = panel
        val originalHeight = measuredHeight(panel)

        assertTrue(panel.moreButton.performClick())
        val entry = findViewByDescription(panel.view, "格式指令")
        assertTrue("the More page must offer the format command entry", entry != null)
        assertTrue(entry!!.performClick())

        assertEquals(1, entered)
        assertEquals(View.VISIBLE, panel.mainPanel.visibility)
        assertEquals(originalHeight, measuredHeight(panel))
    }

    @Test
    fun morePageEntriesShareOneIconAndLabelLayout() {
        val panel = panel()
        assertTrue(panel.moreButton.performClick())

        val density = context.resources.displayMetrics.density
        val expectedIcon = (VOICE_IME_MORE_ENTRY_ICON_DP * density).toInt()
        val expectedGap = (VOICE_IME_MORE_ENTRY_ICON_GAP_DP * density).toInt()
        val labels = listOf("剪貼簿歷史", "語音辨識歷史", "快捷短語", "格式指令", "AI 編輯", "語音翻譯")

        val rows = labels.map { entryLabel ->
            val row = findMoreEntryRow(panel.view, entryLabel)
            assertTrue("missing More entry: $entryLabel", row != null)
            val entry = row as LinearLayout
            assertEquals(entryLabel, entry.contentDescription?.toString())
            assertEquals(2, entry.childCount)

            val icon = entry.getChildAt(0) as ImageView
            val iconParams = icon.layoutParams as LinearLayout.LayoutParams
            assertEquals("$entryLabel icon size", expectedIcon, iconParams.width)
            assertEquals("$entryLabel icon size", expectedIcon, iconParams.height)
            assertEquals("$entryLabel icon gap", expectedGap, iconParams.marginEnd)
            assertTrue("$entryLabel must draw an icon", icon.drawable != null)

            val text = entry.getChildAt(1) as TextView
            assertEquals(entryLabel, text.text.toString())
            entry
        }

        assertEquals(6, rows.size)
        assertEquals("row heights must match", 1, rows.map { it.layoutParams.height }.distinct().size)
        assertEquals("row padding must match", 1, rows.map { it.paddingLeft }.distinct().size)
        assertEquals("row padding must match", 1, rows.map { it.paddingRight }.distinct().size)
    }

    @Test
    fun aiEditEntryAndPreviewReuseThe178dpPageLayer() {
        var entered = 0
        var confirms = 0
        val holder = arrayOfNulls<VoiceImePanel>(1)
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onEnterAiEditMode = {
                entered += 1
                holder[0]?.showMainPanel()
            },
            onAiEditConfirmReplace = { confirms += 1 }
        )
        holder[0] = panel
        val originalHeight = measuredHeight(panel)

        assertTrue(panel.moreButton.performClick())
        val entry = findViewByDescription(panel.view, "AI 編輯")
        assertTrue("the More page must offer the AI edit entry", entry != null)
        assertTrue(entry!!.performClick())
        assertEquals(1, entered)

        panel.showAiEditPreview("原文內容", "修改後的內容")
        layoutPanel(panel, widthDp = 360)
        assertTrue(panel.isShowingAiEditPreview())
        assertEquals(
            "the preview must reuse the 178dp page layer",
            (VOICE_IME_PAGE_CONTENT_HEIGHT_DP * context.resources.displayMetrics.density).toInt(),
            panel.mainPanel.layoutParams.height
        )
        assertEquals(originalHeight, measuredHeight(panel))

        val labels = allTextViews(panel.aiEditPanel).map { it.text.toString() }
        assertTrue(labels.contains("原文內容"))
        assertTrue(labels.contains("修改後的內容"))
        assertTrue(labels.contains("取消"))
        assertTrue(labels.contains("複製結果"))
        assertTrue(labels.contains("確認取代"))

        assertTrue(panel.aiEditConfirmButton.performClick())
        assertEquals(1, confirms)

        panel.showAiEditRefusal("原始選取範圍已變更，為避免覆蓋其他文字，請重新選取")
        assertFalse("a refused replacement must not be retryable", panel.aiEditConfirmButton.isEnabled)
        assertTrue(allTextViews(panel.aiEditPanel).any { it.text.contains("請重新選取") })
    }

    @Test
    fun translationEntryShowsTheModeAndALanguageChipWithoutChangingHeight() {
        var entered = 0
        var cycles = 0
        val holder = arrayOfNulls<VoiceImePanel>(1)
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onEnterTranslateMode = {
                entered += 1
                holder[0]?.showMainPanel()
            },
            onCycleTranslationLanguage = { cycles += 1 }
        )
        holder[0] = panel
        val originalHeight = measuredHeight(panel)

        assertTrue(panel.moreButton.performClick())
        val entry = findViewByDescription(panel.view, "語音翻譯")
        assertTrue("the More page must offer the voice translation entry", entry != null)
        assertTrue(entry!!.performClick())
        assertEquals(1, entered)

        panel.render(VoiceImeState.IDLE, translateMode = true, translationTargetLabel = "英文")
        layoutPanel(panel, widthDp = 320)
        assertEquals(View.VISIBLE, panel.idleTitle.visibility)
        assertEquals("語音翻譯模式", panel.idleTitle.text.toString())
        assertEquals("翻譯成：英文", panel.idleHint.text.toString())
        assertEquals("語音翻譯模式錄音", panel.idleMicButton.contentDescription)
        assertEquals(View.VISIBLE, panel.translateLanguageButton.visibility)
        assertEquals(View.VISIBLE, findViewByDescription(panel.view, "返回一般模式")?.visibility)

        assertTrue(panel.translateLanguageButton.performClick())
        assertEquals(1, cycles)

        // 離開翻譯模式後語言晶片必須收起，且高度不變。
        panel.render(VoiceImeState.IDLE)
        assertEquals(View.GONE, panel.translateLanguageButton.visibility)
        assertEquals(originalHeight, measuredHeight(panel))
    }

    @Test
    fun formatCommandModeShowsAModeHintAndAnExplicitExitControlWithoutChangingHeight() {
        var exited = 0
        val panel = VoiceImePanel(
            context = context,
            onVoiceAction = {},
            onCancel = {},
            onSwitchInputMethod = {},
            onExitMode = { exited += 1 }
        )
        val normalHeight = measuredHeight(panel)
        assertEquals(View.GONE, findViewByDescription(panel.view, "返回一般模式")?.visibility)

        panel.render(VoiceImeState.IDLE, formatCommandMode = true)
        assertEquals(View.GONE, panel.idleTitle.visibility)
        assertEquals(
            "${FormatCommandPrompt.FORMAT_COMMAND_MODE_LABEL}　${FormatCommandPrompt.FORMAT_COMMAND_HINT}",
            panel.idleHint.text.toString()
        )
        assertEquals(2, panel.idleHint.maxLines)
        assertEquals(null, panel.idleHint.ellipsize)
        assertEquals(Gravity.CENTER, panel.idleHint.gravity)
        assertEquals(View.VISIBLE, findViewByDescription(panel.view, "返回一般模式")?.visibility)
        assertEquals(normalHeight, measuredHeight(panel))

        val formatIconState = panel.idleMicButton.drawable.constantState
        assertEquals("格式指令模式錄音", panel.idleMicButton.contentDescription)
        val exit = findViewByDescription(panel.view, "返回一般模式")
        assertTrue("exit control must be reachable", exit != null)
        assertTrue(allTextViews(exit!!).any { it.text == "返回" })
        assertTrue(
            "exit must not intrude on the main round button: exit.left=${exit.left} " +
                "mic.right=${panel.idleMicButton.right}",
            exit.left >= panel.idleMicButton.right
        )
        assertTrue("exit must stay narrower than the main button", exit.width < panel.idleMicButton.width)
        assertTrue(
            "exit needs a usable touch area",
            exit.height >= (22 * context.resources.displayMetrics.density).toInt()
        )

        panel.render(VoiceImeState.IDLE)
        assertEquals(View.VISIBLE, panel.idleTitle.visibility)
        assertEquals("開始語音輸入", panel.idleTitle.text)
        assertEquals("點一下開始說話", panel.idleHint.text.toString())
        assertEquals(2, panel.idleHint.maxLines)
        assertEquals("開始語音輸入", panel.idleMicButton.contentDescription)
        assertNotEquals(
            "format mode must use a different main icon",
            formatIconState,
            panel.idleMicButton.drawable.constantState
        )
        assertEquals(View.GONE, findViewByDescription(panel.view, "返回一般模式")?.visibility)

        panel.render(VoiceImeState.IDLE, formatCommandMode = true)
        assertTrue(findViewByDescription(panel.view, "返回一般模式")?.performClick() == true)
        assertEquals(1, exited)
    }

    @Test
    fun formatCommandModeKeepsTheExitControlOutOfRecordingAndProcessing() {
        val panel = panel()

        panel.render(VoiceImeState.RECORDING, formatCommandMode = true)
        assertEquals(View.GONE, findViewByDescription(panel.view, "返回一般模式")?.visibility)
        assertEquals(View.VISIBLE, panel.recordingActions.visibility)
        assertEquals(View.VISIBLE, panel.audioLevelIndicator.visibility)

        panel.render(VoiceImeState.TRANSCRIBING, formatCommandMode = true)
        assertEquals(View.GONE, findViewByDescription(panel.view, "返回一般模式")?.visibility)
        assertEquals(View.VISIBLE, panel.busyActions.visibility)
        assertTrue(
            allTextViews(panel.busyActions).any { it.text == "正在處理格式指令…" }
        )

        panel.render(VoiceImeState.SUCCESS, formatCommandMode = true)
        assertEquals(View.VISIBLE, findViewByDescription(panel.view, "返回一般模式")?.visibility)

        panel.render(VoiceImeState.ERROR, formatCommandMode = false)
        assertEquals(View.GONE, findViewByDescription(panel.view, "返回一般模式")?.visibility)
    }

    @Test
    fun morePageStillKeepsTheSharedPanelHeightWithTheNewEntry() {
        val panel = panel()
        layoutPanel(panel, widthDp = 360)

        assertTrue(panel.moreButton.performClick())
        layoutPanel(panel, widthDp = 360)

        val maxHeight = (205 * context.resources.displayMetrics.density).toInt()
        assertTrue("Panel height was ${panel.view.measuredHeight}px, max is $maxHeight px", panel.view.measuredHeight <= maxHeight)
        assertEquals(
            (VOICE_IME_PAGE_CONTENT_HEIGHT_DP * context.resources.displayMetrics.density).toInt(),
            panel.mainPanel.layoutParams.height
        )
    }

    @Test
    fun formatCommandHintIsNotTruncatedByTheFixedInteractionHeight() {
        val panel = panel()
        val interactionHeight =
            (VOICE_IME_MAIN_INTERACTION_HEIGHT_DP * context.resources.displayMetrics.density).toInt()

        listOf(320, 360).forEach { widthDp ->
            layoutPanel(panel, widthDp = widthDp)
            panel.render(VoiceImeState.IDLE)
            val normalIconState = panel.idleMicButton.drawable.constantState
            panel.render(VoiceImeState.IDLE, formatCommandMode = true)
            layoutPanel(panel, widthDp = widthDp)

            assertEquals(
                "interaction height must stay fixed at ${widthDp}dp wide",
                interactionHeight,
                panel.mainInteractionContainer.measuredHeight
            )
            assertTrue(
                "idle column must fit the fixed interaction area at ${widthDp}dp: " +
                    "column=${panel.idleActions.measuredHeight}px limit=${interactionHeight}px",
                panel.idleActions.measuredHeight <= interactionHeight
            )
            assertTrue(
                "hint must not exceed two lines at ${widthDp}dp wide",
                panel.idleHint.layout.lineCount in 1..2
            )
            assertEquals(null, panel.idleHint.ellipsize)
            assertNotEquals(
                "format mode must keep its own icon at ${widthDp}dp wide",
                normalIconState,
                panel.idleMicButton.drawable.constantState
            )

            val exit = findViewByDescription(panel.view, "返回一般模式")
            assertTrue("missing exit control at ${widthDp}dp wide", exit != null)
            assertEquals(View.VISIBLE, exit!!.visibility)
            assertTrue(
                "exit must not intrude on the main button at ${widthDp}dp wide: " +
                    "exit.left=${exit.left} mic.right=${panel.idleMicButton.right}",
                exit.left >= panel.idleMicButton.right
            )
        }
    }

    /** 更多頁項目是 LinearLayout 列；工具列的剪貼簿 ImageButton 用的是同一個 contentDescription。 */
    private fun findMoreEntryRow(view: View, label: String): View? {
        if (view is LinearLayout && view.contentDescription?.toString() == label) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findMoreEntryRow(view.getChildAt(index), label)?.let { return it }
            }
        }
        return null
    }

    private fun findViewByDescription(view: View, description: String): View? {
        if (view.contentDescription?.toString() == description) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findViewByDescription(view.getChildAt(index), description)?.let { return it }
            }
        }
        return null
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

    private fun assertCompleteToolbarLabel(button: TextView, expected: String) {
        assertEquals(expected, button.text.toString())
        assertEquals(1, button.maxLines)
        assertEquals(null, button.ellipsize)
        assertEquals(1, button.layout.lineCount)
        assertEquals(0, button.layout.getEllipsisCount(0))
        assertTrue(button.layout.getLineWidth(0) <= button.measuredWidth)
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
        VoiceImeState.AWAITING_CONFIRM -> "修改預覽中"
        VoiceImeState.SUCCESS -> "辨識完成"
        VoiceImeState.FORMATTING_FALLBACK -> "整理失敗，已保留辨識結果"
        VoiceImeState.CANCELLED -> "已取消"
        VoiceImeState.ERROR -> "發生錯誤"
    }
}
