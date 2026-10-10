package tw.kuies.voiceime

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val VOICE_IME_MAIN_INTERACTION_HEIGHT_DP = 130
internal const val VOICE_IME_PAGE_CONTENT_HEIGHT_DP = 178
internal const val VOICE_IME_MORE_ENTRY_ICON_DP = 20
internal const val VOICE_IME_MORE_ENTRY_ICON_GAP_DP = 12
private const val MORE_ENTRY_HORIZONTAL_PADDING_DP = 12
private const val IDLE_HINT_MAX_LINES = 2
private const val NORMAL_HINT_TEXT_SP = 11f
private const val FORMAT_MODE_HINT_TEXT_SP = 12f
private const val BACK_ICON_DP = 14
private const val BACK_ICON_GAP_DP = 4
private const val BACK_BUTTON_HEIGHT_DP = 26
private const val BACK_BUTTON_HORIZONTAL_PADDING_DP = 10

internal class VoiceImePanel(
    context: Context,
    onVoiceAction: () -> Unit,
    private val onCancel: () -> Unit,
    onSwitchInputMethod: () -> Unit,
    onEnter: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBackspacePressed: () -> Unit = {},
    onBackspaceReleased: () -> Boolean = { false },
    onOpenSettings: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearAll: () -> Unit = {},
    private val onOpenClipboardHistory: () -> Unit = {},
    private val onOpenVoiceHistory: () -> Unit = {},
    private val onHoldToTalkStart: () -> Boolean = { false },
    private val onHoldToTalkRelease: () -> Unit = {},
    private val onHoldToTalkCancel: () -> Unit = {},
    private val onInsertHistoryText: (String) -> Unit = {},
    private val onCopyHistoryText: (String) -> Unit = {},
    private val onPinClipboardItem: (String, Boolean) -> Unit = { _, _ -> },
    private val onDeleteClipboardItem: (String) -> Unit = {},
    private val onClearUnpinnedClipboard: () -> Unit = {},
    private val onDeleteVoiceHistoryItem: (Long) -> Unit = {},
    private val onClearVoiceHistory: () -> Unit = {},
    private val onOpenSavedSnippets: () -> Unit = {},
    private val onInsertSavedSnippet: (SavedSnippet) -> Boolean = { false },
    private val onSetSavedSnippetPinned: (String, Boolean) -> Unit = { _, _ -> },
    private val onDeleteSavedSnippets: (Set<String>) -> Unit = {},
    private val onClearSavedSnippets: () -> Unit = {},
    private val onManageSavedSnippets: (SavedSnippetManagerAction, String?) -> Unit = { _, _ -> },
    private val onIsSensitiveEditor: () -> Boolean = { true },
    private val onEnterFormatCommandMode: () -> Unit = {},
    private val onExitMode: () -> Unit = {},
    private val onEnterAiEditMode: () -> Unit = {},
    private val onAiEditConfirmReplace: () -> Unit = {}
) {
    internal val statusIndicator = FrameLayout(context)
    internal val statusDot = View(context)
    internal val topAreaRow = LinearLayout(context)
    internal val toolbarContainer = FrameLayout(context)
    internal val topToolbarRow = LinearLayout(context)
    internal val switchButton = ImageButton(context)
    internal val settingsButton = ImageButton(context)
    internal val clipboardButton = ImageButton(context)
    internal val backspaceButton = ImageButton(context)
    internal val enterButton = ImageButton(context)
    internal val clearConfirmationPanel = LinearLayout(context)
    internal val selectAllButton = textView(context, sizeSp = 13f, color = TEXT)
    internal val clearAllButton = textView(context, sizeSp = 13f, color = PINK)
    internal val cancelClearAllButton = textView(context, sizeSp = 13f, color = TEXT_MUTED)
    internal val confirmClearAllButton = textView(context, sizeSp = 13f, color = PINK)
    internal val moreButton = ImageButton(context)
    internal val mainPanel = LinearLayout(context)
    private val morePanel = LinearLayout(context)
    private val historyPanel = LinearLayout(context)
    internal val savedSnippetsPanel = LinearLayout(context)
    private val savedSnippetsPageContainer = FrameLayout(context)
    internal val snippetListScrollView = ScrollView(context)
    private val snippetRows = LinearLayout(context)
    private val snippetItemCheckBoxes = linkedMapOf<String, CheckBox>()
    private val snippetSortRow = LinearLayout(context)
    private val snippetBatchControlsPanel = LinearLayout(context)
    private val snippetSelectAllCheckBox = CheckBox(context)
    private val snippetAddButton = textView(context, sizeSp = 12f, color = LAVENDER_BRIGHT)
    private val snippetSearch = EditText(context)
    private val snippetStatus = textView(context, sizeSp = 11f, color = TEXT_MUTED)
    private val snippetTitle = textView(context, sizeSp = 14f, color = TEXT).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    private val snippetDeleteButton = textView(context, sizeSp = 11f, color = PINK)
    private val snippetAllSortButton = textView(context, sizeSp = 11f, color = TEXT_MUTED)
    private val snippetRecentSortButton = textView(context, sizeSp = 11f, color = TEXT_MUTED)
    private val snippetDeleteConfirmationOverlay = FrameLayout(context)
    private val snippetDeleteConfirmationTitle = textView(context, sizeSp = 14f, color = TEXT).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    private val snippetDeleteConfirmationMessage = textView(context, sizeSp = 12f, color = TEXT)
    private val snippetDeleteConfirmationState = SavedSnippetDeleteConfirmationState()
    private var savedSnippets: List<SavedSnippet> = emptyList()
    private var snippetHiddenForPrivacy = false
    private var snippetStatusMessage: String? = null
    private var snippetSort = SavedSnippetSort.ALL
    private var snippetSearchQuery = ""
    private val snippetSelection = SavedSnippetSelection()
    private var syncingSnippetSelection = false
    private val historyTitle = textView(context, sizeSp = 14f, color = TEXT).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    private val historyRows = LinearLayout(context)
    private val historyStatus = textView(context, sizeSp = 12f, color = TEXT_MUTED)
    private val historyConfirmation = LinearLayout(context)
    private val historyClearButton = textView(context, sizeSp = 12f, color = PINK)
    private val historyConfirmLabel = textView(context, sizeSp = 12f, color = TEXT)
    private val historyConfirmButton = textView(context, sizeSp = 12f, color = PINK)
    private val historyCancelButton = textView(context, sizeSp = 12f, color = TEXT_MUTED)
    private var page = PanelPage.MAIN
    private var clipboardItems: List<ClipboardHistoryEntity> = emptyList()
    private var voiceItems: List<VoiceHistoryEntity> = emptyList()
    private var clipboardHiddenForPrivacy = false
    private var voiceHiddenForPrivacy = false
    private var clipboardStatus: String? = null
    private var voiceStatus: String? = null
    private var clearHistoryConfirmation = false
    private var expandedVoiceItemId: Long? = null
    private val holdToTalkTracker = HoldToTalkGestureTracker()
    private var holdToTalkPointerId = MotionEvent.INVALID_POINTER_ID
    private var holdToTalkButton: ImageButton? = null
    private val holdToTalkTimeoutRunnable = Runnable {
        val button = holdToTalkButton ?: return@Runnable
        val pointerId = holdToTalkPointerId
        if (pointerId != MotionEvent.INVALID_POINTER_ID &&
            holdToTalkTracker.onLongPress(pointerId) &&
            onHoldToTalkStart()
        ) {
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    internal val mainInteractionContainer = FrameLayout(context)
    internal val audioLevelIndicator = AudioLevelIndicatorView(context)
    internal val idleActions = LinearLayout(context)
    internal val idleMicButton = ImageButton(context)
    internal val idleTitle = textView(context, sizeSp = 15f, color = TEXT).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    internal val idleHint = textView(context, sizeSp = NORMAL_HINT_TEXT_SP, color = TEXT_MUTED).apply {
        gravity = Gravity.CENTER
        setSingleLine(false)
        maxLines = IDLE_HINT_MAX_LINES
        ellipsize = null
    }
    internal val recordingActions = LinearLayout(context)
    internal val busyActions = LinearLayout(context)
    private val mainInteractionRow = LinearLayout(context)
    private val voiceActionsContainer = FrameLayout(context)
    private val sideActionColumn = LinearLayout(context)
    private val busyLabel = textView(context, sizeSp = 13f, color = TEXT)
    private val formatModeExitButton = LinearLayout(context)
    private val moreEntries = LinearLayout(context)

    // AI 編輯預覽：沿用既有的 178dp 頁面層，不改變 IME 整體高度。
    private val aiEditPageContainer = FrameLayout(context)
    internal val aiEditPanel = LinearLayout(context)
    private val aiEditTitle = textView(context, sizeSp = 14f, color = TEXT).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    private val aiEditOriginalText = textView(context, sizeSp = 12f, color = TEXT)
    private val aiEditResultText = textView(context, sizeSp = 12f, color = TEXT)
    private val aiEditScrollView = ScrollView(context)
    internal val aiEditStatus = textView(context, sizeSp = 11f, color = TEXT_MUTED)
    internal val aiEditConfirmButton = textView(context, sizeSp = 12f, color = PINK)

    val view: View

    init {
        val horizontalPadding = dp(context, 12)
        val topPadding = dp(context, 8)
        val bottomPadding = dp(context, 6)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = rounded(
                colors = intArrayOf(BACKGROUND, BACKGROUND),
                radius = dp(context, 24),
                strokeColor = OUTLINE,
                strokeWidth = dp(context, 1)
            )
        }
        mainPanel.orientation = LinearLayout.VERTICAL
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.systemGestures()
            )
            view.setPadding(
                maxOf(horizontalPadding, navigationInsets.left),
                topPadding,
                maxOf(horizontalPadding, navigationInsets.right),
                bottomPadding + safeInsets.bottom
            )
            insets
        }

        topAreaRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topToolbarRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusIndicator.apply {
            isClickable = false
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            addView(
                statusDot,
                FrameLayout.LayoutParams(dp(context, 14), dp(context, 14), Gravity.CENTER)
            )
        }
        topAreaRow.addView(
            statusIndicator,
            LinearLayout.LayoutParams(dp(context, 40), dp(context, 36)).apply {
                marginEnd = dp(context, 12)
            }
        )

        toolbarTextActionButton(context, selectAllButton, "全選", TEXT, "全選輸入文字") {
            clearConfirmationPanel.visibility = View.GONE
            topToolbarRow.visibility = View.VISIBLE
            onSelectAll()
        }
        clipboardButton.apply {
            setImageResource(R.drawable.ic_ime_clipboard)
            imageTintList = ColorStateList.valueOf(TEXT_MUTED)
            contentDescription = "剪貼簿歷史"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = toolbarRipple(dp(context, 10))
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            setOnClickListener { showClipboardHistoryPanel(onIsSensitiveEditor()) }
        }
        toolbarTextActionButton(context, clearAllButton, "清除", PINK, "清除全部文字") {
            topToolbarRow.visibility = View.GONE
            clearConfirmationPanel.visibility = View.VISIBLE
        }
        settingsButton.apply {
            setImageResource(R.drawable.ic_ime_settings)
            imageTintList = ColorStateList.valueOf(TEXT_MUTED)
            contentDescription = "設定"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = toolbarRipple(dp(context, 10))
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            setOnClickListener { onOpenSettings() }
        }
        var backspacePointerActive = false
        var backspaceClickAllowed = false
        backspaceButton.apply {
            setImageResource(R.drawable.ic_ime_backspace)
            imageTintList = ColorStateList.valueOf(blend(TEXT_MUTED, PINK, 0.45f))
            contentDescription = "刪除"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            setOnClickListener { onDelete() }
            setOnTouchListener { button, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        backspacePointerActive = true
                        backspaceClickAllowed = true
                        button.isPressed = true
                        onBackspacePressed()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (backspacePointerActive && backspaceClickAllowed &&
                            (event.x < 0 || event.y < 0 || event.x >= button.width ||
                                event.y >= button.height)
                        ) {
                            backspaceClickAllowed = false
                            button.isPressed = false
                            onBackspaceReleased()
                        }
                        backspacePointerActive
                    }
                    MotionEvent.ACTION_UP -> {
                        if (backspacePointerActive) {
                            backspacePointerActive = false
                            button.isPressed = false
                            val repeated = onBackspaceReleased()
                            if (backspaceClickAllowed && !repeated) button.performClick()
                            backspaceClickAllowed = false
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (backspacePointerActive) {
                            backspacePointerActive = false
                            backspaceClickAllowed = false
                            button.isPressed = false
                            onBackspaceReleased()
                        }
                        true
                    }
                    else -> backspacePointerActive
                }
            }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    if (backspacePointerActive) {
                        backspacePointerActive = false
                        backspaceClickAllowed = false
                        view.isPressed = false
                        onBackspaceReleased()
                    }
                }
            })
        }
        enterButton.apply {
            setImageResource(R.drawable.ic_ime_enter)
            imageTintList = ColorStateList.valueOf(blend(TEXT_MUTED, LAVENDER, 0.5f))
            contentDescription = "換行"
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7))
            setOnClickListener { onEnter() }
        }

        switchButton.apply {
            setImageResource(R.drawable.ic_ime_keyboard)
            imageTintList = ColorStateList.valueOf(TEXT_MUTED)
            contentDescription = "切換鍵盤"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = toolbarRipple(dp(context, 10))
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            setOnClickListener { onSwitchInputMethod() }
        }
        moreButton.apply {
            setImageResource(R.drawable.ic_ime_more)
            imageTintList = ColorStateList.valueOf(LAVENDER_BRIGHT)
            contentDescription = "更多功能"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = toolbarRipple(dp(context, 10))
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            setOnClickListener { showMorePanel() }
        }
        topToolbarRow.addView(
            clipboardButton,
            LinearLayout.LayoutParams(0, dp(context, 36), 1f)
        )
        topToolbarRow.addView(
            selectAllButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 36))
        )
        topToolbarRow.addView(
            clearAllButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 36))
        )
        topToolbarRow.addView(
            switchButton,
            LinearLayout.LayoutParams(0, dp(context, 36), 1f)
        )
        topToolbarRow.addView(
            settingsButton,
            LinearLayout.LayoutParams(0, dp(context, 36), 1f)
        )
        topToolbarRow.addView(moreButton, LinearLayout.LayoutParams(0, dp(context, 36), 1f))

        listOf(confirmClearAllButton, cancelClearAllButton).forEach { button ->
            button.textSize = 12f
            button.setSingleLine(true)
            button.ellipsize = null
            button.setPadding(dp(context, 8), 0, dp(context, 8), 0)
        }
        val confirmationActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(
                secondaryActionButton(context, confirmClearAllButton, "確定清除全部", PINK) {
                    clearConfirmationPanel.visibility = View.GONE
                    topToolbarRow.visibility = View.VISIBLE
                    onClearAll()
                },
                LinearLayout.LayoutParams(0, dp(context, 30), 2f).apply {
                    marginEnd = dp(context, 6)
                }
            )
            addView(
                secondaryActionButton(context, cancelClearAllButton, "取消", TEXT_MUTED) {
                    clearConfirmationPanel.visibility = View.GONE
                    topToolbarRow.visibility = View.VISIBLE
                },
                LinearLayout.LayoutParams(0, dp(context, 30), 1f)
            )
        }
        clearConfirmationPanel.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
            background = rounded(
                intArrayOf(SURFACE_VARIANT, SURFACE_VARIANT),
                dp(context, 18),
                blend(OUTLINE, PINK, 0.35f),
                dp(context, 1)
            )
            addView(
                confirmationActions,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
            visibility = View.GONE
        }
        toolbarContainer.apply {
            background = rounded(
                intArrayOf(SURFACE, SURFACE),
                dp(context, 18),
                blend(OUTLINE, BACKGROUND, 0.35f),
                dp(context, 1)
            )
            addView(
                topToolbarRow,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            addView(
                clearConfirmationPanel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(context, 36),
                    Gravity.CENTER
                )
            )
        }
        topAreaRow.addView(
            toolbarContainer,
            LinearLayout.LayoutParams(0, dp(context, 36), 1f)
        )
        mainPanel.addView(
            topAreaRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 36)
            )
        )

        idleActions.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            idleMicButton.apply {
                setImageResource(R.drawable.ic_ime_mic)
                imageTintList = ColorStateList.valueOf(BUTTON_TEXT)
                contentDescription = "開始語音輸入"
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = circleRipple(context)
                setPadding(dp(context, 23), dp(context, 23), dp(context, 23), dp(context, 23))
                isClickable = true
                isFocusable = true
                setOnClickListener { onVoiceAction() }
                installHoldToTalkGesture(this)
            }
            addView(
                idleMicButton,
                LinearLayout.LayoutParams(dp(context, 84), dp(context, 84))
            )
            idleTitle.apply {
                text = "開始語音輸入"
                gravity = Gravity.CENTER
            }
            addView(
                idleTitle,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(context, 6) }
            )
            idleHint.apply {
                text = "點一下開始說話"
                gravity = Gravity.CENTER
            }
            addView(
                idleHint,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(context, 3) }
            )
        }
        voiceActionsContainer.addView(
            idleActions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        // 只保留小型返回圖示與「返回」兩字，寬度大幅縮小，避免侵入主圓形按鈕的視覺區域。
        formatModeExitButton.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = "返回一般模式"
            setPadding(
                dp(context, BACK_BUTTON_HORIZONTAL_PADDING_DP),
                0,
                dp(context, BACK_BUTTON_HORIZONTAL_PADDING_DP),
                0
            )
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            background = RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                solid(SURFACE, dp(context, 14)),
                null
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_ime_back)
                    imageTintList = ColorStateList.valueOf(TEXT_MUTED)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                },
                LinearLayout.LayoutParams(dp(context, BACK_ICON_DP), dp(context, BACK_ICON_DP)).apply {
                    marginEnd = dp(context, BACK_ICON_GAP_DP)
                }
            )
            addView(
                textView(context, sizeSp = NORMAL_HINT_TEXT_SP, color = TEXT_MUTED).apply {
                    text = "返回"
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            setOnClickListener { onExitMode() }
        }
        voiceActionsContainer.addView(
            formatModeExitButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(context, BACK_BUTTON_HEIGHT_DP),
                Gravity.TOP or Gravity.END
            )
        )

        recordingActions.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                actionButton(
                    context,
                    R.drawable.ic_ime_stop,
                    "停止",
                    BUTTON_TEXT,
                    solid(CORAL, dp(context, 50)),
                    onVoiceAction
                ),
                LinearLayout.LayoutParams(0, dp(context, 48), 1f).apply {
                    marginEnd = dp(context, 8)
                }
            )
            addView(
                actionButton(
                    context,
                    R.drawable.ic_ime_close,
                    "取消",
                    TEXT,
                    solid(SURFACE_VARIANT, dp(context, 50)),
                    onCancel
                ),
                LinearLayout.LayoutParams(0, dp(context, 48), 1f)
            )
        }
        voiceActionsContainer.addView(
            recordingActions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        audioLevelIndicator.visibility = View.GONE
        voiceActionsContainer.addView(
            audioLevelIndicator,
            FrameLayout.LayoutParams(dp(context, 46), dp(context, 34), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(context, 3)
            }
        )

        busyActions.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val progress = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
                indeterminateTintList = ColorStateList.valueOf(LAVENDER)
            }
            addView(progress, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)))
            busyLabel.apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(
                busyLabel,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(context, 9)
                    marginEnd = dp(context, 8)
                }
            )
            addView(
                actionButton(
                    context,
                    R.drawable.ic_ime_close,
                    "取消",
                    TEXT_MUTED,
                    solid(SURFACE_VARIANT, dp(context, 50)),
                    onCancel
                ),
                LinearLayout.LayoutParams(dp(context, 84), dp(context, 42))
            )
        }
        voiceActionsContainer.addView(
            busyActions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        sideActionColumn.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                backspaceButton,
                LinearLayout.LayoutParams(dp(context, 40), dp(context, 40))
            )
            addView(View(context), LinearLayout.LayoutParams(1, dp(context, 18)))
            addView(
                enterButton,
                LinearLayout.LayoutParams(dp(context, 42), dp(context, 42))
            )
        }
        mainInteractionRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                voiceActionsContainer,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            addView(
                sideActionColumn,
                LinearLayout.LayoutParams(dp(context, 52), LinearLayout.LayoutParams.MATCH_PARENT)
            )
        }
        mainInteractionContainer.addView(
            mainInteractionRow,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        mainPanel.addView(
            mainInteractionContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VOICE_IME_MAIN_INTERACTION_HEIGHT_DP)
            ).apply { topMargin = dp(context, 6) }
        )

        root.addView(
            mainPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VOICE_IME_PAGE_CONTENT_HEIGHT_DP)
            )
        )
        buildHistoryPanels(context, root)
        buildSavedSnippetPanels(context, root)
        buildAiEditPanel(context, root)
        view = root
        render(VoiceImeState.IDLE)
    }

    fun setSwitchAvailable(available: Boolean) {
        switchButton.visibility = if (available) View.VISIBLE else View.GONE
    }

    fun showMainPanel() {
        clearSnippetDeleteConfirmation()
        snippetSelection.cancel()
        page = PanelPage.MAIN
        updatePageVisibility()
    }

    fun isShowingClipboardHistory(): Boolean = page == PanelPage.CLIPBOARD_HISTORY

    fun isShowingVoiceHistory(): Boolean = page == PanelPage.VOICE_HISTORY

    fun isShowingSavedSnippets(): Boolean =
        page == PanelPage.SAVED_SNIPPETS

    fun showClipboardHistoryPanel(isSensitiveEditor: Boolean) {
        clearSnippetDeleteConfirmation()
        page = PanelPage.CLIPBOARD_HISTORY
        clearHistoryConfirmation = false
        clipboardHiddenForPrivacy = isSensitiveEditor
        clipboardStatus = if (isSensitiveEditor) {
            "此欄位不顯示剪貼簿內容"
        } else {
            "正在載入剪貼簿…"
        }
        updatePageVisibility()
        renderHistoryRows()
        if (!isSensitiveEditor) onOpenClipboardHistory()
    }

    fun updateClipboardHistory(
        items: List<ClipboardHistoryEntity>,
        statusMessage: String? = null
    ) {
        clipboardItems = items
        clipboardStatus = statusMessage ?: if (items.isEmpty()) "目前沒有剪貼簿紀錄" else null
        if (page == PanelPage.CLIPBOARD_HISTORY) renderHistoryRows()
    }

    fun showVoiceHistoryPanel(isSensitiveEditor: Boolean) {
        clearSnippetDeleteConfirmation()
        page = PanelPage.VOICE_HISTORY
        clearHistoryConfirmation = false
        voiceHiddenForPrivacy = isSensitiveEditor
        voiceStatus = if (isSensitiveEditor) {
            "此欄位不顯示語音歷史"
        } else {
            "正在載入語音歷史…"
        }
        updatePageVisibility()
        renderHistoryRows()
        if (!isSensitiveEditor) onOpenVoiceHistory()
    }

    fun updateVoiceHistory(items: List<VoiceHistoryEntity>, statusMessage: String? = null) {
        voiceItems = items
        voiceStatus = statusMessage ?: if (items.isEmpty()) "目前沒有語音歷史" else null
        if (page == PanelPage.VOICE_HISTORY) renderHistoryRows()
    }

    fun showSavedSnippetsPanel(isSensitiveEditor: Boolean) {
        clearSnippetDeleteConfirmation()
        page = PanelPage.SAVED_SNIPPETS
        snippetSelection.cancel()
        snippetHiddenForPrivacy = isSensitiveEditor
        if (isSensitiveEditor) {
            savedSnippets = emptyList()
            snippetStatusMessage = "此欄位不顯示快捷短語"
        } else {
            snippetStatusMessage = "正在載入快捷短語…"
        }
        updatePageVisibility()
        renderSavedSnippets()
        if (!isSensitiveEditor) onOpenSavedSnippets()
    }

    fun updateSavedSnippets(library: SavedSnippetLibrary, statusMessage: String? = null) {
        if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
            savedSnippets = emptyList()
            snippetHiddenForPrivacy = true
            clearSnippetDeleteConfirmation()
            snippetStatusMessage = "此欄位不顯示快捷短語"
        } else {
            savedSnippets = library.snippets
            if (snippetDeleteConfirmationState.invalidateMissing(library.snippets.map(SavedSnippet::id))) {
                snippetDeleteConfirmationOverlay.visibility = View.GONE
            }
            snippetStatusMessage = statusMessage ?: if (library.snippets.isEmpty()) {
                "尚未建立快捷短語"
            } else {
                null
            }
        }
        if (isShowingSavedSnippets()) renderSavedSnippets()
    }

    fun hideSavedSnippetsForPrivacy() {
        if (!isShowingSavedSnippets()) return
        showSavedSnippetsPanel(isSensitiveEditor = true)
    }

    fun clearSnippetDeleteConfirmation() {
        snippetDeleteConfirmationState.dismiss()
        snippetDeleteConfirmationOverlay.visibility = View.GONE
    }

    fun resetSavedSnippetTransientState() {
        clearSnippetDeleteConfirmation()
        snippetSelection.cancel()
        if (page == PanelPage.SAVED_SNIPPETS) renderSavedSnippets()
    }

    private fun showMorePanel() {
        clearSnippetDeleteConfirmation()
        page = PanelPage.MORE
        updatePageVisibility()
    }

    fun isShowingAiEditPreview(): Boolean = page == PanelPage.AI_EDIT_PREVIEW

    /** 顯示 AI 改寫結果的預覽；原文與結果各自可在內部捲動，底部操作固定。 */
    fun showAiEditPreview(originalText: String, resultText: String) {
        clearSnippetDeleteConfirmation()
        aiEditOriginalText.text = originalText
        aiEditResultText.text = resultText
        showAiEditStatus(null)
        setAiEditConfirmEnabled(true)
        page = PanelPage.AI_EDIT_PREVIEW
        updatePageVisibility()
        aiEditScrollView.scrollTo(0, 0)
    }

    /**
     * 取代被拒絕時留在預覽，顯示原因並停用「確認取代」（不得退回游標插入或搜尋相同文字）。
     * 使用者仍可複製結果或取消。
     */
    fun showAiEditRefusal(message: String) {
        if (page != PanelPage.AI_EDIT_PREVIEW) return
        showAiEditStatus(message)
        setAiEditConfirmEnabled(false)
    }

    /** 結束預覽並回到主面板；原文不會被更動。 */
    fun hideAiEditPreview() {
        if (page == PanelPage.AI_EDIT_PREVIEW) showMainPanel()
    }

    private fun showAiEditStatus(message: String?) {
        aiEditStatus.text = message.orEmpty()
        aiEditStatus.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun setAiEditConfirmEnabled(enabled: Boolean) {
        aiEditConfirmButton.isEnabled = enabled
        aiEditConfirmButton.alpha = if (enabled) 1f else 0.5f
    }

    private fun updatePageVisibility() {
        mainPanel.visibility = if (page == PanelPage.MAIN) View.VISIBLE else View.GONE
        morePanel.visibility = if (page == PanelPage.MORE) View.VISIBLE else View.GONE
        historyPanel.visibility = if (
            page == PanelPage.CLIPBOARD_HISTORY || page == PanelPage.VOICE_HISTORY
        ) View.VISIBLE else View.GONE
        savedSnippetsPageContainer.visibility = if (page == PanelPage.SAVED_SNIPPETS) View.VISIBLE else View.GONE
        aiEditPageContainer.visibility = if (page == PanelPage.AI_EDIT_PREVIEW) View.VISIBLE else View.GONE
        savedSnippetsPanel.visibility = View.VISIBLE
        if (page != PanelPage.SAVED_SNIPPETS) clearSnippetDeleteConfirmation()
        renderHistoryRows()
        renderSavedSnippets()
    }

    private fun buildHistoryPanels(context: Context, root: LinearLayout) {
        morePanel.orientation = LinearLayout.VERTICAL
        morePanel.addView(
            subpanelHeader(context, "更多功能"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 42))
        )
        moreEntries.orientation = LinearLayout.VERTICAL
        moreEntries.addView(
            moreEntry(context, R.drawable.ic_ime_clipboard, "剪貼簿歷史") {
                showClipboardHistoryPanel(onIsSensitiveEditor())
            },
            moreEntryParams(context)
        )
        moreEntries.addView(
            moreEntry(context, R.drawable.ic_ime_history, "語音辨識歷史") {
                showVoiceHistoryPanel(onIsSensitiveEditor())
            },
            moreEntryParams(context)
        )
        moreEntries.addView(
            moreEntry(context, R.drawable.ic_ime_bookmark, "快捷短語") {
                showSavedSnippetsPanel(onIsSensitiveEditor())
            },
            moreEntryParams(context)
        )
        moreEntries.addView(
            moreEntry(context, R.drawable.ic_ime_format_command, "格式指令") {
                onEnterFormatCommandMode()
            },
            moreEntryParams(context)
        )
        moreEntries.addView(
            moreEntry(context, R.drawable.ic_ime_ai_edit, "AI 編輯") {
                onEnterAiEditMode()
            },
            moreEntryParams(context)
        )
        morePanel.addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(moreEntries)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        historyPanel.orientation = LinearLayout.VERTICAL
        val historyHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        historyHeader.addView(
            secondaryActionButton(context, textView(context, sizeSp = 12f, color = TEXT_MUTED), "返回", TEXT_MUTED) {
                showMainPanel()
            },
            LinearLayout.LayoutParams(dp(context, 62), dp(context, 34)).apply {
                marginEnd = dp(context, 8)
            }
        )
        historyHeader.addView(
            historyTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        historyClearButton.apply {
            text = "清除"
            gravity = Gravity.CENTER
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            setOnClickListener {
                clearHistoryConfirmation = true
                renderHistoryRows()
            }
        }
        historyHeader.addView(historyClearButton, LinearLayout.LayoutParams(dp(context, 54), dp(context, 34)))
        historyPanel.addView(
            historyHeader,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 38))
        )

        historyConfirmation.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 9), 0, dp(context, 5), 0)
            background = rounded(
                intArrayOf(SURFACE_VARIANT, SURFACE_VARIANT),
                dp(context, 16),
                blend(OUTLINE, PINK, 0.35f),
                dp(context, 1)
            )
            addView(historyConfirmLabel, LinearLayout.LayoutParams(0, dp(context, 34), 1f))
            addView(
                secondaryActionButton(context, historyCancelButton, "取消", TEXT_MUTED) {
                    clearHistoryConfirmation = false
                    renderHistoryRows()
                },
                LinearLayout.LayoutParams(dp(context, 52), dp(context, 30)).apply {
                    marginEnd = dp(context, 4)
                }
            )
            addView(
                secondaryActionButton(context, historyConfirmButton, "清除", PINK) {
                    clearHistoryConfirmation = false
                    if (page == PanelPage.CLIPBOARD_HISTORY) onClearUnpinnedClipboard()
                    if (page == PanelPage.VOICE_HISTORY) onClearVoiceHistory()
                    renderHistoryRows()
                },
                LinearLayout.LayoutParams(dp(context, 52), dp(context, 30))
            )
            visibility = View.GONE
        }
        historyPanel.addView(
            historyConfirmation,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 38)).apply {
                topMargin = dp(context, 6)
            }
        )
        historyStatus.apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }
        historyPanel.addView(
            historyStatus,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 32)).apply {
                topMargin = dp(context, 2)
            }
        )
        val scrollView = ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            addView(
                historyRows,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        historyRows.orientation = LinearLayout.VERTICAL
        historyPanel.addView(
            scrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(context, 2)
            }
        )

        root.addView(
            morePanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VOICE_IME_PAGE_CONTENT_HEIGHT_DP)
            )
        )
        root.addView(
            historyPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VOICE_IME_PAGE_CONTENT_HEIGHT_DP)
            )
        )
        morePanel.getChildAt(0).layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 34)
        )
        morePanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
    }

    private fun buildSavedSnippetPanels(context: Context, root: LinearLayout) {
        savedSnippetsPanel.orientation = LinearLayout.VERTICAL
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            secondaryActionButton(context, textView(context, sizeSp = 12f, color = TEXT_MUTED), "返回", TEXT_MUTED) {
                showMainPanel()
            },
            LinearLayout.LayoutParams(dp(context, 56), dp(context, 34)).apply {
                marginEnd = dp(context, 5)
            }
        )
        header.addView(
            snippetTitle.apply { text = "快捷短語" },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        snippetDeleteButton.apply {
            text = "刪除"
            gravity = Gravity.CENTER
            setOnClickListener {
                if (!snippetHiddenForPrivacy && !onIsSensitiveEditor() && savedSnippets.isNotEmpty()) {
                    snippetSelection.enter()
                    renderSavedSnippets()
                } else if (onIsSensitiveEditor()) {
                    hideSavedSnippetsForPrivacy()
                }
            }
        }
        header.addView(snippetDeleteButton, LinearLayout.LayoutParams(dp(context, 48), dp(context, 34)))
        header.addView(
            secondaryActionButton(context, snippetAddButton, "新增", LAVENDER_BRIGHT) {
                if (!snippetHiddenForPrivacy && !onIsSensitiveEditor()) {
                    onManageSavedSnippets(SavedSnippetManagerAction.NEW, null)
                }
                else hideSavedSnippetsForPrivacy()
            },
            LinearLayout.LayoutParams(dp(context, 50), dp(context, 34))
        )
        savedSnippetsPanel.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 36))
        )

        snippetBatchControlsPanel.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), 0, dp(context, 3), 0)
            background = rounded(intArrayOf(SURFACE_VARIANT, SURFACE_VARIANT), dp(context, 12), OUTLINE, dp(context, 1))
            addView(
                snippetSelectAllCheckBox.apply {
                    text = "全部"
                    textSize = 10f
                    setTextColor(TEXT)
                    buttonTintList = ColorStateList.valueOf(LAVENDER_BRIGHT)
                    contentDescription = "全選目前顯示的快捷短語"
                    setOnCheckedChangeListener { _, checked ->
                        if (!syncingSnippetSelection && snippetSelection.isActive) {
                            if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
                                hideSavedSnippetsForPrivacy()
                            } else {
                                snippetSelection.setAllSelected(checked, currentVisibleSnippetIds())
                                refreshSnippetSelectionControls()
                            }
                        }
                    }
                },
                LinearLayout.LayoutParams(0, dp(context, 30), 1f)
            )
            addView(
                secondaryActionButton(context, textView(context, sizeSp = 10f, color = TEXT_MUTED), "取消", TEXT_MUTED) {
                    snippetSelection.cancel()
                    renderSavedSnippets()
                },
                LinearLayout.LayoutParams(dp(context, 42), dp(context, 28)).apply { marginEnd = dp(context, 2) }
            )
            addView(
                secondaryActionButton(context, textView(context, sizeSp = 10f, color = PINK), "刪除（0）", PINK) {
                    showBatchSnippetDeleteConfirmation()
                }.apply {
                    isEnabled = false
                    alpha = 0.5f
                },
                LinearLayout.LayoutParams(dp(context, 76), dp(context, 28))
            )
            visibility = View.GONE
        }
        savedSnippetsPanel.addView(
            snippetBatchControlsPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 32)).apply {
                topMargin = dp(context, 2)
            }
        )

        snippetSortRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        configureSnippetSortButton(snippetAllSortButton, "全部", SavedSnippetSort.ALL, context)
        configureSnippetSortButton(snippetRecentSortButton, "最近使用", SavedSnippetSort.RECENT, context)
        snippetSortRow.addView(
            snippetAllSortButton,
            LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply { marginEnd = dp(context, 4) }
        )
        snippetSortRow.addView(
            snippetRecentSortButton,
            LinearLayout.LayoutParams(0, dp(context, 30), 1f)
        )
        savedSnippetsPanel.addView(
            snippetSortRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 34)).apply {
                topMargin = dp(context, 1)
            }
        )

        snippetSearch.apply {
            hint = "搜尋短語"
            textSize = 12f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(TEXT)
            setHintTextColor(TEXT_MUTED)
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            background = rounded(intArrayOf(SURFACE, SURFACE), dp(context, 12), OUTLINE, dp(context, 1))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    snippetSearchQuery = s?.toString().orEmpty()
                    renderSnippetRows()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        savedSnippetsPanel.addView(
            snippetSearch,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 32)).apply {
                topMargin = dp(context, 2)
            }
        )
        snippetStatus.apply {
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        savedSnippetsPanel.addView(
            snippetStatus,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 17))
        )
        snippetRows.orientation = LinearLayout.VERTICAL
        snippetListScrollView.apply {
            isFillViewport = false
            clipToPadding = false
            addView(
                snippetRows,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        savedSnippetsPanel.addView(
            snippetListScrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        buildSnippetDeleteConfirmationOverlay(context)
        savedSnippetsPageContainer.addView(
            savedSnippetsPanel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        savedSnippetsPageContainer.addView(
            snippetDeleteConfirmationOverlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        savedSnippetsPageContainer.visibility = View.GONE
        root.addView(
            savedSnippetsPageContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VOICE_IME_PAGE_CONTENT_HEIGHT_DP)
            )
        )
    }

    /**
     * AI 編輯預覽頁：沿用既有的 178dp 頁面層，原文與修改結果在內部捲動，
     * 底部「取消／複製結果／確認取代」固定，不改變 IME 整體高度。
     */
    private fun buildAiEditPanel(context: Context, root: LinearLayout) {
        aiEditPanel.orientation = LinearLayout.VERTICAL

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            secondaryActionButton(context, textView(context, sizeSp = 12f, color = TEXT_MUTED), "返回", TEXT_MUTED) {
                onCancel()
            },
            LinearLayout.LayoutParams(dp(context, 56), dp(context, 30)).apply { marginEnd = dp(context, 6) }
        )
        header.addView(
            aiEditTitle.apply { text = "AI 編輯預覽" },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        aiEditPanel.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 32))
        )

        aiEditStatus.apply {
            maxLines = 2
            visibility = View.GONE
        }
        aiEditPanel.addView(
            aiEditStatus,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 26))
        )

        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(aiEditSectionLabel(context, "原文"))
        aiEditOriginalText.setTextIsSelectable(true)
        content.addView(
            aiEditOriginalText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            aiEditSectionLabel(context, "修改結果"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 4) }
        )
        aiEditResultText.setTextIsSelectable(true)
        content.addView(
            aiEditResultText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        aiEditScrollView.apply {
            isFillViewport = false
            clipToPadding = false
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        aiEditPanel.addView(
            aiEditScrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(context, 2)
            }
        )

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            secondaryActionButton(context, textView(context, sizeSp = 12f, color = TEXT_MUTED), "取消", TEXT_MUTED) {
                onCancel()
            },
            LinearLayout.LayoutParams(0, dp(context, 32), 1f).apply { marginEnd = dp(context, 4) }
        )
        actions.addView(
            secondaryActionButton(
                context,
                textView(context, sizeSp = 12f, color = LAVENDER_BRIGHT),
                "複製結果",
                LAVENDER_BRIGHT
            ) {
                onCopyHistoryText(aiEditResultText.text.toString())
            },
            LinearLayout.LayoutParams(0, dp(context, 32), 1.2f).apply { marginEnd = dp(context, 4) }
        )
        actions.addView(
            secondaryActionButton(context, aiEditConfirmButton, "確認取代", PINK) {
                onAiEditConfirmReplace()
            },
            LinearLayout.LayoutParams(0, dp(context, 32), 1.4f)
        )
        aiEditPanel.addView(
            actions,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 34)).apply {
                topMargin = dp(context, 2)
            }
        )

        aiEditPageContainer.addView(
            aiEditPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        aiEditPageContainer.visibility = View.GONE
        root.addView(
            aiEditPageContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VOICE_IME_PAGE_CONTENT_HEIGHT_DP)
            )
        )
    }

    private fun aiEditSectionLabel(context: Context, text: String): View =
        textView(context, sizeSp = 10f, color = TEXT_MUTED).apply {
            this.text = text
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun renderSavedSnippets() {
        if (!isShowingSavedSnippets()) return
        val isSensitive = snippetHiddenForPrivacy || onIsSensitiveEditor()
        if (isSensitive) {
            snippetSelection.cancel()
            savedSnippets = emptyList()
            clearSnippetDeleteConfirmation()
            snippetHiddenForPrivacy = true
            snippetStatusMessage = "此欄位不顯示快捷短語"
        }
        renderSnippetSortButtons()
        snippetDeleteButton.visibility = if (
            snippetHiddenForPrivacy || savedSnippets.isEmpty() || snippetSelection.isActive
        ) View.GONE else View.VISIBLE
        snippetBatchControlsPanel.visibility = if (snippetSelection.isActive && !snippetHiddenForPrivacy) {
            View.VISIBLE
        } else {
            View.GONE
        }
        snippetAddButton.visibility = if (snippetHiddenForPrivacy || snippetSelection.isActive) View.GONE else View.VISIBLE
        snippetSortRow.visibility = if (snippetHiddenForPrivacy) View.GONE else View.VISIBLE
        snippetStatus.text = snippetStatusMessage.orEmpty()
        snippetStatus.visibility = if (snippetStatusMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        snippetSearch.visibility = if (snippetHiddenForPrivacy) View.GONE else View.VISIBLE
        renderSnippetRows()
    }

    private fun configureSnippetSortButton(
        button: TextView,
        label: String,
        sort: SavedSnippetSort,
        context: Context
    ) {
        button.apply {
            text = label
            gravity = Gravity.CENTER
            setPadding(dp(context, 6), 0, dp(context, 6), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
                    hideSavedSnippetsForPrivacy()
                } else {
                    snippetSort = sort
                    renderSavedSnippets()
                }
            }
        }
    }

    private fun renderSnippetSortButtons() {
        listOf(
            snippetAllSortButton to (snippetSort == SavedSnippetSort.ALL),
            snippetRecentSortButton to (snippetSort == SavedSnippetSort.RECENT)
        ).forEach { (button, selected) ->
            button.setTextColor(if (selected) TEXT else TEXT_MUTED)
            button.background = rounded(
                if (selected) intArrayOf(SURFACE_VARIANT, SURFACE_VARIANT) else intArrayOf(SURFACE, SURFACE),
                dp(view.context, 12),
                if (selected) LAVENDER_BRIGHT else OUTLINE,
                dp(view.context, 1)
            )
        }
    }

    private fun buildSnippetDeleteConfirmationOverlay(context: Context) {
        snippetDeleteConfirmationOverlay.apply {
            visibility = View.GONE
            background = rounded(
                intArrayOf(Color.argb(225, 11, 12, 20), Color.argb(225, 11, 12, 20)),
                dp(context, 16),
                OUTLINE,
                dp(context, 1)
            )
            isClickable = true
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 8))
            background = rounded(intArrayOf(SURFACE_VARIANT, SURFACE_VARIANT), dp(context, 14), OUTLINE, dp(context, 1))
            isClickable = true
            isFocusable = false
        }
        card.addView(
            snippetDeleteConfirmationTitle,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        snippetDeleteConfirmationMessage.maxLines = 4
        card.addView(
            snippetDeleteConfirmationMessage,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(context, 4)
            }
        )
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        actions.addView(
            secondaryActionButton(context, textView(context, sizeSp = 11f, color = TEXT_MUTED), "取消", TEXT_MUTED) {
                clearSnippetDeleteConfirmation()
            },
            LinearLayout.LayoutParams(dp(context, 54), dp(context, 30)).apply { marginEnd = dp(context, 5) }
        )
        actions.addView(
            secondaryActionButton(context, textView(context, sizeSp = 11f, color = PINK), "刪除", PINK) {
                confirmSnippetDeletion()
            },
            LinearLayout.LayoutParams(dp(context, 54), dp(context, 30))
        )
        card.addView(
            actions,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 30)).apply {
                topMargin = dp(context, 5)
            }
        )
        snippetDeleteConfirmationOverlay.addView(
            card,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            }
        )
    }

    private fun currentVisibleSnippetItems(): List<SavedSnippet> = SavedSnippetOrdering.filterAndSort(
        snippets = savedSnippets,
        search = snippetSearchQuery,
        sort = snippetSort
    )

    private fun currentVisibleSnippetIds(): List<String> =
        currentVisibleSnippetItems().map(SavedSnippet::id)

    private fun renderSnippetBatchControls(visibleIds: List<String>) {
        if (!snippetSelection.isActive) return
        val selectedIds = snippetSelection.selectedIds(visibleIds)
        val selectedCount = selectedIds.size
        syncingSnippetSelection = true
        val selectionState = snippetSelection.stateFor(visibleIds)
        snippetSelectAllCheckBox.isEnabled = visibleIds.isNotEmpty()
        snippetSelectAllCheckBox.text = if (selectionState == SavedSnippetSelectionState.PARTIAL) {
            "◩ 全部"
        } else {
            "全部"
        }
        snippetSelectAllCheckBox.isChecked = selectionState == SavedSnippetSelectionState.ALL
        snippetSelectAllCheckBox.contentDescription = when (selectionState) {
            SavedSnippetSelectionState.NONE -> "未選取；全選目前顯示的快捷短語"
            SavedSnippetSelectionState.PARTIAL -> "部分選取；全選目前顯示的快捷短語"
            SavedSnippetSelectionState.ALL -> "已全選目前顯示的快捷短語"
        }
        syncingSnippetSelection = false
        val deleteButton = snippetBatchControlsPanel.getChildAt(2)
        if (deleteButton is TextView) {
            deleteButton.text = "刪除（$selectedCount）"
            deleteButton.isEnabled = selectedCount > 0
            deleteButton.alpha = if (selectedCount > 0) 1f else 0.5f
        }
    }

    private fun refreshSnippetSelectionControls() {
        val visibleIds = currentVisibleSnippetIds()
        snippetSelection.reconcile(visibleIds)
        renderSnippetBatchControls(visibleIds)
        val selectedIds = snippetSelection.selectedIds(visibleIds)
        syncingSnippetSelection = true
        snippetItemCheckBoxes.forEach { (id, checkBox) -> checkBox.isChecked = id in selectedIds }
        syncingSnippetSelection = false
    }

    private fun showSingleSnippetDeleteConfirmation(snippet: SavedSnippet) {
        if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
            hideSavedSnippetsForPrivacy()
            return
        }
        showSnippetDeleteConfirmation(
            title = "刪除快捷短語",
            message = "確定要刪除「${snippet.title}」嗎？",
            ids = setOf(snippet.id),
            batchDelete = false
        )
    }

    private fun showBatchSnippetDeleteConfirmation() {
        if (!snippetSelection.isActive) return
        if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
            hideSavedSnippetsForPrivacy()
            return
        }
        val visibleIds = currentVisibleSnippetIds()
        val ids = snippetSelection.selectedIds(visibleIds)
        if (ids.isEmpty()) return
        val allLibraryItemsSelected = snippetSearchQuery.isBlank() && savedSnippets.isNotEmpty() &&
            ids.size == savedSnippets.size && savedSnippets.all { it.id in ids }
        val message = if (allLibraryItemsSelected) {
            "確定刪除全部快捷短語？"
        } else {
            "確定刪除選取的 ${ids.size} 則快捷短語？"
        }
        showSnippetDeleteConfirmation("刪除快捷短語", message, ids, batchDelete = true)
    }

    private fun showSnippetDeleteConfirmation(
        title: String,
        message: String,
        ids: Set<String>,
        batchDelete: Boolean
    ) {
        val safeIds = ids.filterTo(linkedSetOf()) { id -> savedSnippets.any { it.id == id } }
        if (!snippetDeleteConfirmationState.show(safeIds, batchDelete, message)) return
        snippetDeleteConfirmationTitle.text = title
        snippetDeleteConfirmationMessage.text = message
        snippetDeleteConfirmationOverlay.visibility = View.VISIBLE
        snippetDeleteConfirmationOverlay.bringToFront()
    }

    private fun confirmSnippetDeletion() {
        if (!snippetDeleteConfirmationState.isVisible) return
        if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
            hideSavedSnippetsForPrivacy()
            return
        }
        val request = snippetDeleteConfirmationState.consume(savedSnippets.map(SavedSnippet::id))
        snippetDeleteConfirmationOverlay.visibility = View.GONE
        val currentIds = request?.ids.orEmpty()
        clearSnippetDeleteConfirmation()
        if (request == null || request.batchDelete) {
            snippetSelection.cancel()
        } else if (snippetSelection.isActive) {
            currentIds.forEach { id -> snippetSelection.setSelected(id, false, currentVisibleSnippetIds()) }
        }
        if (currentIds.isNotEmpty()) onDeleteSavedSnippets(currentIds)
        renderSavedSnippets()
    }

    private fun renderSnippetRows() {
        snippetRows.removeAllViews()
        snippetItemCheckBoxes.clear()
        if (page != PanelPage.SAVED_SNIPPETS || snippetHiddenForPrivacy) return
        val visibleItems = currentVisibleSnippetItems()
        val visibleIds = visibleItems.map(SavedSnippet::id)
        snippetSelection.reconcile(visibleIds)
        renderSnippetBatchControls(visibleIds)
        if (!snippetStatusMessage.isNullOrBlank()) return
        if (visibleItems.isEmpty()) {
            snippetStatus.text = if (snippetSearchQuery.isBlank()) "尚未建立快捷短語" else "找不到符合的短語"
            snippetStatus.visibility = View.VISIBLE
            return
        }
        snippetStatus.text = ""
        snippetStatus.visibility = View.GONE
        visibleItems.forEach { snippet -> snippetRows.addView(savedSnippetCard(snippet)) }
    }

    private fun savedSnippetCard(snippet: SavedSnippet): View = LinearLayout(view.context).apply {
        val context = view.context
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 9), dp(context, 6), dp(context, 7), dp(context, 5))
        background = rounded(intArrayOf(SURFACE, SURFACE), dp(context, 13), OUTLINE, dp(context, 1))
        isLongClickable = true
        setOnLongClickListener {
            showSingleSnippetDeleteConfirmation(snippet)
            true
        }
        val contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
                    hideSavedSnippetsForPrivacy()
                } else if (snippetSelection.isActive) {
                    val visibleIds = currentVisibleSnippetIds()
                    val selected = snippet.id in snippetSelection.selectedIds(visibleIds)
                    snippetSelection.setSelected(snippet.id, !selected, visibleIds)
                    refreshSnippetSelectionControls()
                } else if (onInsertSavedSnippet(snippet)) {
                    showMainPanel()
                }
            }
            contentDescription = if (snippetSelection.isActive) {
                "選取快捷短語：${snippet.title}"
            } else {
                "插入快捷短語：${snippet.title}"
            }
            setOnLongClickListener {
                showSingleSnippetDeleteConfirmation(snippet)
                true
            }
        }
        contentColumn.addView(
            textView(context, sizeSp = 12f, color = TEXT).apply {
                text = snippet.title
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )
        contentColumn.addView(
            textView(context, sizeSp = 11f, color = TEXT_MUTED).apply {
                text = snippet.content
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(context, 2)
            }
        )
        val selectionCheckBox = CheckBox(context).apply {
            buttonTintList = ColorStateList.valueOf(LAVENDER_BRIGHT)
            contentDescription = "選取快捷短語：${snippet.title}"
            visibility = if (snippetSelection.isActive) View.VISIBLE else View.GONE
            isChecked = snippet.id in snippetSelection.selectedIds(currentVisibleSnippetIds())
            setOnCheckedChangeListener { _, checked ->
                if (!syncingSnippetSelection && snippetSelection.isActive) {
                    if (snippetHiddenForPrivacy || onIsSensitiveEditor()) {
                        hideSavedSnippetsForPrivacy()
                    } else {
                        snippetSelection.setSelected(snippet.id, checked, currentVisibleSnippetIds())
                        refreshSnippetSelectionControls()
                    }
                }
            }
            setOnLongClickListener {
                showSingleSnippetDeleteConfirmation(snippet)
                true
            }
        }
        if (snippetSelection.isActive) snippetItemCheckBoxes[snippet.id] = selectionCheckBox
        val selectionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                selectionCheckBox,
                LinearLayout.LayoutParams(dp(context, 34), LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            addView(
                contentColumn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        addView(selectionRow)
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        actions.addView(
            smallHistoryButton(context, if (snippet.pinned) "取消收藏" else "收藏", LAVENDER_BRIGHT) {
                onSetSavedSnippetPinned(snippet.id, !snippet.pinned)
            }
        )
        actions.addView(
            smallHistoryButton(context, "編輯", TEXT_MUTED) {
                if (!snippetHiddenForPrivacy && !onIsSensitiveEditor()) {
                    onManageSavedSnippets(SavedSnippetManagerAction.EDIT, snippet.id)
                }
                else hideSavedSnippetsForPrivacy()
            }
        )
        actions.addView(
            smallHistoryButton(context, "刪除", PINK) {
                showSingleSnippetDeleteConfirmation(snippet)
            }.apply {
                setOnLongClickListener {
                    showSingleSnippetDeleteConfirmation(snippet)
                    true
                }
            }
        )
        for (index in 0 until actions.childCount) {
            actions.getChildAt(index).setOnLongClickListener {
                showSingleSnippetDeleteConfirmation(snippet)
                true
            }
        }
        addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 23)))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, 4) }
    }

    private fun subpanelHeader(context: Context, title: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            secondaryActionButton(context, textView(context, sizeSp = 12f, color = TEXT_MUTED), "返回", TEXT_MUTED) {
                showMainPanel()
            },
            LinearLayout.LayoutParams(dp(context, 62), dp(context, 34)).apply {
                marginEnd = dp(context, 8)
            }
        )
        addView(
            textView(context, sizeSp = 14f, color = TEXT).apply {
                text = title
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    /** 「更多」頁面的四個項目共用同一個「左側 Icon + 右側文字」版型。 */
    private fun moreEntry(context: Context, iconRes: Int, label: String, onClick: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = label
            setPadding(dp(context, MORE_ENTRY_HORIZONTAL_PADDING_DP), 0, dp(context, MORE_ENTRY_HORIZONTAL_PADDING_DP), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                solid(SURFACE, dp(context, 14)),
                null
            )
            setOnClickListener { onClick() }
        }
        row.addView(
            ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(LAVENDER_BRIGHT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            },
            LinearLayout.LayoutParams(
                dp(context, VOICE_IME_MORE_ENTRY_ICON_DP),
                dp(context, VOICE_IME_MORE_ENTRY_ICON_DP)
            ).apply { marginEnd = dp(context, VOICE_IME_MORE_ENTRY_ICON_GAP_DP) }
        )
        row.addView(
            textView(context, sizeSp = 14f, color = TEXT).apply {
                text = label
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun moreEntryParams(context: Context): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 38)).apply {
            topMargin = dp(context, 5)
        }

    private fun renderHistoryRows() {
        if (!isShowingClipboardHistory() && !isShowingVoiceHistory()) return
        historyRows.removeAllViews()
        historyTitle.text = if (page == PanelPage.CLIPBOARD_HISTORY) "剪貼簿歷史" else "語音辨識歷史"
        historyClearButton.visibility = if (
            clipboardHiddenForPrivacy && page == PanelPage.CLIPBOARD_HISTORY ||
            voiceHiddenForPrivacy && page == PanelPage.VOICE_HISTORY
        ) View.GONE else View.VISIBLE
        historyClearButton.contentDescription = if (page == PanelPage.CLIPBOARD_HISTORY) {
            "清除未釘選項目"
        } else {
            "清除全部語音歷史"
        }
        historyConfirmLabel.text = if (page == PanelPage.CLIPBOARD_HISTORY) {
            "清除未釘選剪貼簿項目？"
        } else {
            "清除全部語音歷史？"
        }
        historyConfirmation.visibility = if (clearHistoryConfirmation) View.VISIBLE else View.GONE

        val status = when (page) {
            PanelPage.CLIPBOARD_HISTORY -> clipboardStatus
            PanelPage.VOICE_HISTORY -> voiceStatus
            else -> null
        }
        historyStatus.text = status.orEmpty()
        historyStatus.visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE

        if (page == PanelPage.CLIPBOARD_HISTORY && !clipboardHiddenForPrivacy) {
            clipboardItems.forEach { item -> historyRows.addView(clipboardHistoryCard(item)) }
        } else if (page == PanelPage.VOICE_HISTORY && !voiceHiddenForPrivacy) {
            voiceItems.forEach { item -> historyRows.addView(voiceHistoryCard(item)) }
        }
    }

    private fun clipboardHistoryCard(item: ClipboardHistoryEntity): View = LinearLayout(view.context).apply {
        val context = view.context
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 10), dp(context, 8), dp(context, 8), dp(context, 7))
        background = rounded(
            intArrayOf(SURFACE, SURFACE), dp(context, 16), OUTLINE, dp(context, 1)
        )
        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = textView(context, sizeSp = 13f, color = TEXT).apply {
            this.text = item.text
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = true
            setOnClickListener { onInsertHistoryText(item.text) }
        }
        contentRow.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        actions.addView(
            smallHistoryButton(context, if (item.pinned) "取消釘選" else "釘選", LAVENDER_BRIGHT) {
                onPinClipboardItem(item.id, !item.pinned)
            }
        )
        actions.addView(
            smallHistoryButton(context, "刪除", PINK) { onDeleteClipboardItem(item.id) }
        )
        contentRow.addView(actions, LinearLayout.LayoutParams(dp(context, 68), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(contentRow)
        addView(
            textView(context, sizeSp = 10f, color = TEXT_MUTED).apply {
                this.text = "${if (item.pinned) "已釘選 · " else ""}${formatHistoryTime(item.createdAt)}"
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(context, 4)
            }
        )
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, 6) }
    }

    private fun voiceHistoryCard(item: VoiceHistoryEntity): View = LinearLayout(view.context).apply {
        val context = view.context
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 10), dp(context, 8), dp(context, 8), dp(context, 7))
        background = rounded(
            intArrayOf(SURFACE, SURFACE), dp(context, 16), OUTLINE, dp(context, 1)
        )
        val finalTextView = textView(context, sizeSp = 13f, color = TEXT).apply {
            text = item.finalText
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = true
            setOnClickListener { onInsertHistoryText(item.finalText) }
        }
        addView(finalTextView)
        addView(
            textView(context, sizeSp = 10f, color = TEXT_MUTED).apply {
                text = formatHistoryTime(item.createdAt)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(context, 3)
            }
        )
        if (item.rawText != item.finalText && expandedVoiceItemId == item.id) {
            addView(
                textView(context, sizeSp = 12f, color = TEXT_MUTED).apply {
                    text = "原始辨識：${item.rawText}"
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(context, 5)
                }
            )
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (item.rawText != item.finalText) {
            actions.addView(
                smallHistoryButton(
                    context,
                    if (expandedVoiceItemId == item.id) "收合原文" else "查看原文",
                    TEXT_MUTED
                ) {
                    expandedVoiceItemId = if (expandedVoiceItemId == item.id) null else item.id
                    renderHistoryRows()
                },
                LinearLayout.LayoutParams(0, dp(context, 30), 1f)
            )
        } else {
            actions.addView(View(context), LinearLayout.LayoutParams(0, dp(context, 30), 1f))
        }
        actions.addView(
            smallHistoryButton(context, "複製", LAVENDER_BRIGHT) { onCopyHistoryText(item.finalText) },
            LinearLayout.LayoutParams(dp(context, 55), dp(context, 30))
        )
        actions.addView(
            smallHistoryButton(context, "刪除", PINK) { onDeleteVoiceHistoryItem(item.id) },
            LinearLayout.LayoutParams(dp(context, 55), dp(context, 30)).apply { marginStart = dp(context, 4) }
        )
        addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 30)).apply {
            topMargin = dp(context, 4)
        })
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, 6) }
    }

    private fun smallHistoryButton(
        context: Context,
        label: String,
        color: Int,
        onClick: () -> Unit
    ): View = secondaryActionButton(
        context,
        textView(context, sizeSp = 11f, color = color),
        label,
        color,
        onClick
    ).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 30)
        ).apply { bottomMargin = dp(context, 3) }
    }

    private fun formatHistoryTime(timestamp: Long): String =
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun render(
        state: VoiceImeState,
        statusLabelOverride: String? = null,
        holdToTalkRecording: Boolean = false,
        formatCommandMode: Boolean = false,
        aiEditMode: Boolean = false
    ) {
        val isTerminal = state == VoiceImeState.SUCCESS ||
            state == VoiceImeState.FORMATTING_FALLBACK ||
            state == VoiceImeState.CANCELLED || state == VoiceImeState.ERROR
        val accent = accentFor(state)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent)
        }
        val stateDescription = when (state) {
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
        statusIndicator.contentDescription = if (statusLabelOverride.isNullOrBlank()) {
            stateDescription
        } else {
            "$stateDescription，$statusLabelOverride"
        }

        settingsButton.isEnabled = state == VoiceImeState.IDLE
        settingsButton.alpha = if (settingsButton.isEnabled) 1f else 0.5f
        val bulkActionsEnabled = state == VoiceImeState.IDLE
        selectAllButton.isEnabled = bulkActionsEnabled
        clearAllButton.isEnabled = bulkActionsEnabled
        selectAllButton.alpha = if (bulkActionsEnabled) 1f else 0.5f
        clearAllButton.alpha = if (bulkActionsEnabled) 1f else 0.5f
        moreButton.isEnabled = bulkActionsEnabled
        moreButton.alpha = if (bulkActionsEnabled) 1f else 0.5f
        if (!bulkActionsEnabled) {
            clearConfirmationPanel.visibility = View.GONE
            topToolbarRow.visibility = View.VISIBLE
        } else if (clearConfirmationPanel.visibility != View.VISIBLE) {
            topToolbarRow.visibility = View.VISIBLE
        }
        formatModeExitButton.visibility = if (
            (formatCommandMode || aiEditMode) && (state == VoiceImeState.IDLE || isTerminal)
        ) View.VISIBLE else View.GONE
        // 格式指令模式的提示必須完整顯示。互動區固定為 130dp，若同時保留 15sp 標題與
        // 可能換行的提示就會被裁掉，所以格式模式把模式名稱與範例合成單一置中標籤，
        // 最多 2 行，並且不動麥克風、Delete、Enter 與音量回饋的配置。
        if (formatCommandMode) {
            idleTitle.visibility = View.GONE
            idleHint.textSize = FORMAT_MODE_HINT_TEXT_SP
            idleHint.setTextColor(TEXT)
            idleHint.text = "${FormatCommandPrompt.FORMAT_COMMAND_MODE_LABEL}　" +
                FormatCommandPrompt.FORMAT_COMMAND_HINT
        } else if (aiEditMode) {
            idleTitle.visibility = View.VISIBLE
            idleTitle.text = "AI 編輯模式"
            idleHint.textSize = NORMAL_HINT_TEXT_SP
            idleHint.setTextColor(TEXT_MUTED)
            idleHint.text = if (holdToTalkRecording) "放開即辨識" else "選取文字後，說出修改要求"
        } else {
            idleTitle.visibility = View.VISIBLE
            idleTitle.text = "開始語音輸入"
            idleHint.textSize = NORMAL_HINT_TEXT_SP
            idleHint.setTextColor(TEXT_MUTED)
            idleHint.text = if (holdToTalkRecording) "放開即辨識" else "點一下開始說話"
        }
        // 特殊模式改用專屬圖示，讓模式只靠主按鈕就能辨識；收音中仍由狀態燈、音量回饋與
        // 啟動配色（isActivated）表達，不會因此換回麥克風圖示。
        idleMicButton.setImageResource(
            when {
                formatCommandMode -> R.drawable.ic_ime_format_command
                aiEditMode -> R.drawable.ic_ime_ai_edit
                else -> R.drawable.ic_ime_mic
            }
        )
        idleMicButton.contentDescription = when {
            formatCommandMode -> "格式指令模式錄音"
            aiEditMode -> "AI 編輯模式錄音"
            else -> "開始語音輸入"
        }
        idleMicButton.isActivated = holdToTalkRecording
        idleMicButton.alpha = if (holdToTalkRecording) 0.9f else 1f
        idleMicButton.background = if (holdToTalkRecording) {
            solid(CORAL, dp(idleMicButton.context, 100))
        } else {
            circleRipple(idleMicButton.context)
        }
        idleActions.visibility = if (
            state == VoiceImeState.IDLE || isTerminal || holdToTalkRecording
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        recordingActions.visibility = if (
            state == VoiceImeState.RECORDING && !holdToTalkRecording
        ) View.VISIBLE else View.GONE
        val showAudioLevel = state == VoiceImeState.RECORDING
        audioLevelIndicator.visibility = if (showAudioLevel) View.VISIBLE else View.GONE
        if (showAudioLevel) {
            val params = audioLevelIndicator.layoutParams as FrameLayout.LayoutParams
            if (holdToTalkRecording) {
                params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                params.marginStart = dp(audioLevelIndicator.context, 8)
                params.topMargin = 0
            } else {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.marginStart = 0
                params.topMargin = dp(audioLevelIndicator.context, 3)
            }
            audioLevelIndicator.layoutParams = params
        } else {
            audioLevelIndicator.setLevel(0f)
        }
        val isBusy = state == VoiceImeState.TRANSCRIBING || state == VoiceImeState.FORMATTING
        busyActions.visibility = if (isBusy) View.VISIBLE else View.GONE
        busyLabel.text = when {
            formatCommandMode && isBusy -> "正在處理格式指令…"
            aiEditMode && isBusy -> "正在套用修改…"
            else -> state.label
        }
    }

    internal fun updateAudioLevel(level: Float) {
        if (audioLevelIndicator.visibility == View.VISIBLE) {
            audioLevelIndicator.setLevel(level)
        }
    }

    internal fun cancelHoldToTalkGesture() {
        holdToTalkButton?.removeCallbacks(holdToTalkTimeoutRunnable)
        val event = holdToTalkTracker.onCancel()
        holdToTalkPointerId = MotionEvent.INVALID_POINTER_ID
        holdToTalkButton?.isPressed = false
        holdToTalkButton?.parent?.requestDisallowInterceptTouchEvent(false)
        finishHoldToTalkGesture(event)
    }

    internal fun disposeHoldToTalkGesture() {
        holdToTalkButton?.removeCallbacks(holdToTalkTimeoutRunnable)
        holdToTalkTracker.reset()
        holdToTalkPointerId = MotionEvent.INVALID_POINTER_ID
        holdToTalkButton?.isPressed = false
        holdToTalkButton = null
    }

    private fun installHoldToTalkGesture(button: ImageButton) {
        holdToTalkButton = button
        button.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val pointerId = event.getPointerId(event.actionIndex)
                    if (!holdToTalkTracker.onDown(pointerId)) return@setOnTouchListener true
                    holdToTalkPointerId = pointerId
                    touched.isPressed = true
                    touched.parent?.requestDisallowInterceptTouchEvent(true)
                    touched.postDelayed(
                        holdToTalkTimeoutRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val pointerId = holdToTalkPointerId
                    val pointerIndex = event.findPointerIndex(pointerId)
                    if (pointerId == MotionEvent.INVALID_POINTER_ID || pointerIndex < 0) {
                        cancelHoldToTalkGesture()
                        return@setOnTouchListener true
                    }
                    val isInside = event.getX(pointerIndex) >= 0f &&
                        event.getY(pointerIndex) >= 0f &&
                        event.getX(pointerIndex) < touched.width &&
                        event.getY(pointerIndex) < touched.height
                    if (!isInside) {
                        touched.removeCallbacks(holdToTalkTimeoutRunnable)
                        touched.isPressed = false
                        touched.parent?.requestDisallowInterceptTouchEvent(false)
                        finishHoldToTalkGesture(holdToTalkTracker.onMove(pointerId, false))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pointerId = event.getPointerId(event.actionIndex)
                    if (pointerId != holdToTalkPointerId) {
                        cancelHoldToTalkGesture()
                        return@setOnTouchListener true
                    }
                    touched.removeCallbacks(holdToTalkTimeoutRunnable)
                    val isInside = event.x >= 0f && event.y >= 0f &&
                        event.x < touched.width && event.y < touched.height
                    val gesture = holdToTalkTracker.onUp(pointerId, isInside)
                    holdToTalkPointerId = MotionEvent.INVALID_POINTER_ID
                    touched.isPressed = false
                    touched.parent?.requestDisallowInterceptTouchEvent(false)
                    finishHoldToTalkGesture(gesture, touched)
                    true
                }
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_POINTER_UP -> {
                    cancelHoldToTalkGesture()
                    true
                }
                else -> true
            }
        }
        button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                holdToTalkButton = view as? ImageButton
            }

            override fun onViewDetachedFromWindow(view: View) {
                cancelHoldToTalkGesture()
                holdToTalkButton = null
            }
        })
    }

    private fun finishHoldToTalkGesture(
        event: HoldToTalkGestureEvent,
        button: View? = holdToTalkButton
    ) {
        when (event) {
            HoldToTalkGestureEvent.NONE -> Unit
            HoldToTalkGestureEvent.TAP -> button?.performClick()
            HoldToTalkGestureEvent.RELEASE_HOLD -> onHoldToTalkRelease()
            HoldToTalkGestureEvent.CANCEL_HOLD -> onHoldToTalkCancel()
        }
        if (event != HoldToTalkGestureEvent.NONE) {
            holdToTalkPointerId = MotionEvent.INVALID_POINTER_ID
        }
    }

    private fun actionButton(
        context: Context,
        icon: Int,
        label: String,
        foregroundColor: Int,
        background: android.graphics.drawable.Drawable,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = label
        this.background = RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            background,
            null
        )
        val iconView = ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(foregroundColor)
        }
        addView(iconView, LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)))
        val labelView = textView(context, sizeSp = 14f, color = foregroundColor).apply {
            text = label
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        addView(
            labelView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(context, 7) }
        )
        setOnClickListener { onClick() }
    }

    private fun secondaryActionButton(
        context: Context,
        button: TextView,
        label: String,
        color: Int,
        onClick: () -> Unit
    ): View = button.apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(color)
        setTypeface(typeface, Typeface.BOLD)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            solid(SURFACE, dp(context, 14)),
            null
        )
        setOnClickListener { onClick() }
    }

    private fun toolbarTextActionButton(
        context: Context,
        button: TextView,
        label: String,
        color: Int,
        description: String,
        onClick: () -> Unit
    ) = button.apply {
        text = label
        contentDescription = description
        textSize = 12f
        gravity = Gravity.CENTER
        setSingleLine(true)
        ellipsize = null
        setTextColor(color)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(context, 2), 0, dp(context, 2), 0)
        isClickable = true
        isFocusable = true
        background = toolbarRipple(dp(context, 10))
        setOnClickListener { onClick() }
    }

    private fun toolbarRipple(radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x33FFFFFF),
        null,
        solid(Color.WHITE, radius)
    )

    private fun accentFor(state: VoiceImeState): Int = when (state) {
        VoiceImeState.IDLE -> MINT
        VoiceImeState.RECORDING -> CORAL
        VoiceImeState.TRANSCRIBING -> AMBER
        VoiceImeState.FORMATTING -> AMBER
        VoiceImeState.AWAITING_CONFIRM -> LAVENDER
        VoiceImeState.SUCCESS -> MINT
        VoiceImeState.FORMATTING_FALLBACK -> AMBER
        VoiceImeState.CANCELLED -> TEXT_MUTED
        VoiceImeState.ERROR -> ERROR
    }

    private enum class PanelPage {
        MAIN,
        MORE,
        CLIPBOARD_HISTORY,
        VOICE_HISTORY,
        SAVED_SNIPPETS,
        AI_EDIT_PREVIEW
    }

    private fun textView(context: Context, sizeSp: Float, color: Int) = TextView(context).apply {
        textSize = sizeSp
        setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun rounded(colors: IntArray, radius: Int, strokeColor: Int, strokeWidth: Int) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, strokeColor)
        }

    private fun solid(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun ripple(context: Context, color: Int, stroke: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x33FFFFFF),
        solid(color, radius),
        rounded(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT), radius, stroke, dp(context, 1))
    )

    private fun circleRipple(context: Context) = RippleDrawable(
        ColorStateList.valueOf(0x44FFFFFF),
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(LAVENDER, LAVENDER_BRIGHT)
        ).apply { shape = GradientDrawable.OVAL },
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
    )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun blend(background: Int, foreground: Int, fraction: Float): Int {
        val inverse = 1f - fraction
        return Color.rgb(
            (Color.red(background) * inverse + Color.red(foreground) * fraction).toInt(),
            (Color.green(background) * inverse + Color.green(foreground) * fraction).toInt(),
            (Color.blue(background) * inverse + Color.blue(foreground) * fraction).toInt()
        )
    }

    private companion object {
        val BACKGROUND = Color.rgb(20, 27, 41)
        val SURFACE = Color.rgb(28, 37, 53)
        val SURFACE_VARIANT = Color.rgb(41, 52, 71)
        val OUTLINE = Color.rgb(59, 71, 91)
        val LAVENDER = Color.rgb(189, 186, 255)
        val LAVENDER_BRIGHT = Color.rgb(205, 201, 255)
        val MINT = Color.rgb(169, 228, 212)
        val AMBER = Color.rgb(255, 192, 118)
        val PINK = Color.rgb(240, 187, 212)
        val CORAL = Color.rgb(232, 153, 161)
        val ERROR = Color.rgb(255, 142, 142)
        val BUTTON_TEXT = Color.rgb(37, 35, 66)
        val TEXT = Color.rgb(230, 234, 243)
        val TEXT_MUTED = Color.rgb(180, 190, 206)
    }
}
