package tw.kuies.voiceime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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

internal class VoiceImePanel(
    context: Context,
    onVoiceAction: () -> Unit,
    onCancel: () -> Unit,
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
    private val onIsSensitiveEditor: () -> Boolean = { true }
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
    internal val idleActions = LinearLayout(context)
    internal val idleMicButton = ImageButton(context)
    internal val idleTitle = textView(context, sizeSp = 15f, color = TEXT).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    internal val idleHint = textView(context, sizeSp = 11f, color = TEXT_MUTED)
    internal val recordingActions = LinearLayout(context)
    internal val busyActions = LinearLayout(context)
    private val mainInteractionRow = LinearLayout(context)
    private val voiceActionsContainer = FrameLayout(context)
    private val sideActionColumn = LinearLayout(context)
    private val busyLabel = textView(context, sizeSp = 13f, color = TEXT)

    val view: View

    init {
        val horizontalPadding = dp(context, 12)
        val topPadding = dp(context, 8)
        val bottomPadding = dp(context, 6)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
            background = rounded(
                colors = intArrayOf(BACKGROUND, BACKGROUND),
                radius = dp(context, 24),
                strokeColor = OUTLINE,
                strokeWidth = dp(context, 1)
            )
        }
        mainPanel.orientation = LinearLayout.VERTICAL
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.systemGestures()
            )
            view.setPadding(
                horizontalPadding + safeInsets.left,
                topPadding,
                horizontalPadding + safeInsets.right,
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
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
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
            LinearLayout.LayoutParams(dp(context, 48), dp(context, 36)).apply {
                marginEnd = dp(context, 16)
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
            LinearLayout.LayoutParams(dp(context, 28), dp(context, 36))
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
            LinearLayout.LayoutParams(dp(context, 28), dp(context, 36))
        )
        topToolbarRow.addView(
            settingsButton,
            LinearLayout.LayoutParams(dp(context, 28), dp(context, 36))
        )
        topToolbarRow.addView(moreButton, LinearLayout.LayoutParams(dp(context, 28), dp(context, 36)))

        val confirmationLabel = textView(context, sizeSp = 12f, color = TEXT).apply {
            text = "確定清除全部文字？"
            maxLines = 1
        }
        val confirmationActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                secondaryActionButton(context, cancelClearAllButton, "取消", TEXT_MUTED) {
                    clearConfirmationPanel.visibility = View.GONE
                    topToolbarRow.visibility = View.VISIBLE
                },
                LinearLayout.LayoutParams(dp(context, 54), dp(context, 30)).apply {
                    marginEnd = dp(context, 6)
                }
            )
            addView(
                secondaryActionButton(context, confirmClearAllButton, "清除", PINK) {
                    clearConfirmationPanel.visibility = View.GONE
                    topToolbarRow.visibility = View.VISIBLE
                    onClearAll()
                },
                LinearLayout.LayoutParams(dp(context, 60), dp(context, 30))
            )
        }
        clearConfirmationPanel.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 10), 0, dp(context, 6), 0)
            background = rounded(
                intArrayOf(SURFACE_VARIANT, SURFACE_VARIANT),
                dp(context, 18),
                blend(OUTLINE, PINK, 0.35f),
                dp(context, 1)
            )
            addView(
                confirmationLabel,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            addView(confirmationActions)
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
                LinearLayout.LayoutParams(0, dp(context, 48), 0.8f)
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
            setPadding(0, 0, dp(context, 4), 0)
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
                LinearLayout.LayoutParams(dp(context, 52), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(context, 2)
                }
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
        view = root
        render(VoiceImeState.IDLE)
    }

    fun setSwitchAvailable(available: Boolean) {
        switchButton.visibility = if (available) View.VISIBLE else View.GONE
    }

    fun showMainPanel() {
        page = PanelPage.MAIN
        updatePageVisibility()
    }

    fun isShowingClipboardHistory(): Boolean = page == PanelPage.CLIPBOARD_HISTORY

    fun isShowingVoiceHistory(): Boolean = page == PanelPage.VOICE_HISTORY

    fun showClipboardHistoryPanel(isSensitiveEditor: Boolean) {
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

    private fun showMorePanel() {
        page = PanelPage.MORE
        updatePageVisibility()
    }

    private fun updatePageVisibility() {
        mainPanel.visibility = if (page == PanelPage.MAIN) View.VISIBLE else View.GONE
        morePanel.visibility = if (page == PanelPage.MORE) View.VISIBLE else View.GONE
        historyPanel.visibility = if (
            page == PanelPage.CLIPBOARD_HISTORY || page == PanelPage.VOICE_HISTORY
        ) View.VISIBLE else View.GONE
        renderHistoryRows()
    }

    private fun buildHistoryPanels(context: Context, root: LinearLayout) {
        morePanel.orientation = LinearLayout.VERTICAL
        morePanel.addView(
            subpanelHeader(context, "更多功能"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 42))
        )
        morePanel.addView(
            historyNavigationButton(context, "剪貼簿歷史") {
                showClipboardHistoryPanel(onIsSensitiveEditor())
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44)).apply {
                topMargin = dp(context, 8)
            }
        )
        morePanel.addView(
            historyNavigationButton(context, "語音辨識歷史") {
                showVoiceHistoryPanel(onIsSensitiveEditor())
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44)).apply {
                topMargin = dp(context, 8)
            }
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

        morePanel.addView(
            View(context),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
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
        morePanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
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

    private fun historyNavigationButton(context: Context, label: String, onClick: () -> Unit): View =
        secondaryActionButton(context, textView(context, sizeSp = 14f, color = TEXT), label, TEXT) {
            onClick()
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
        holdToTalkRecording: Boolean = false
    ) {
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

        val isTerminal = state == VoiceImeState.SUCCESS ||
            state == VoiceImeState.FORMATTING_FALLBACK ||
            state == VoiceImeState.CANCELLED || state == VoiceImeState.ERROR
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
        }
        idleHint.text = if (holdToTalkRecording) "放開即辨識" else "點一下開始說話"
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
        val isBusy = state == VoiceImeState.TRANSCRIBING || state == VoiceImeState.FORMATTING
        busyActions.visibility = if (isBusy) View.VISIBLE else View.GONE
        busyLabel.text = state.label
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
        setPadding(0, 0, 0, 0)
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
        VoiceImeState.SUCCESS -> MINT
        VoiceImeState.FORMATTING_FALLBACK -> AMBER
        VoiceImeState.CANCELLED -> TEXT_MUTED
        VoiceImeState.ERROR -> ERROR
    }

    private enum class PanelPage {
        MAIN,
        MORE,
        CLIPBOARD_HISTORY,
        VOICE_HISTORY
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
