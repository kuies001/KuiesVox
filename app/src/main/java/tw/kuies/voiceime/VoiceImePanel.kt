package tw.kuies.voiceime

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val VOICE_IME_MAIN_INTERACTION_HEIGHT_DP = 130
internal const val VOICE_IME_PAGE_CONTENT_HEIGHT_DP = 178
internal const val VOICE_IME_MORE_ENTRY_ICON_DP = 20
internal const val VOICE_IME_MORE_ENTRY_ICON_GAP_DP = 12

/** 右側 Delete / Enter 操作區寬度；以覆蓋定位，不佔用中央內容的橫向寬度。 */
internal const val VOICE_IME_SIDE_ACTION_COLUMN_WIDTH_DP = 52

/** 工具列純圖示按鈕的統一規格：相同容器高度與基準圖示尺寸。 */
internal const val TOOLBAR_BUTTON_HEIGHT_DP = 36
internal const val TOOLBAR_ICON_SIZE_DP = 22

/** 滿版圖示（剪貼簿、翻譯）的視覺補償尺寸，避免看起來比鍵盤／設定／更多大。 */
internal const val TOOLBAR_ICON_COMPACT_SIZE_DP = 20

/**
 * 讓圖示以 [iconSizeDp] 的視覺尺寸置中：容器高度固定時，內距 =（容器高 − 圖示尺寸）÷ 2，
 * 搭配 CENTER_INSIDE 會把 24dp 的向量縮到該尺寸，因此所有圖示可用同一條公式統一大小。
 */
internal fun toolbarIconPaddingDp(iconSizeDp: Int): Int = (TOOLBAR_BUTTON_HEIGHT_DP - iconSizeDp) / 2
private const val MORE_ENTRY_HORIZONTAL_PADDING_DP = 12
private const val IDLE_HINT_MAX_LINES = 2
private const val NORMAL_HINT_TEXT_SP = 11f
private const val FORMAT_MODE_HINT_TEXT_SP = 11f
private const val IDLE_TITLE_TOP_MARGIN_DP = 6
private const val IDLE_HINT_TOP_MARGIN_DP = 3
private const val BACK_ICON_DP = 14
private const val BACK_ICON_GAP_DP = 4
private const val BACK_BUTTON_HEIGHT_DP = 26
private const val BACK_BUTTON_HORIZONTAL_PADDING_DP = 10

/** 中央圓形錄音按鈕的直徑；音量縮放以此為基準半徑。 */
private const val IDLE_MIC_BUTTON_SIZE_DP = 84

/**
 * 錄音中的「取消」按鈕高度。它疊在原本主標題／副標題所在的區塊內，
 * 刻意低於該區塊高度，因此不會把整個互動區撐高（IME 總高度不變）。
 */
private const val RECORDING_CANCEL_HEIGHT_DP = 34

/**
 * 標題／說明區塊的最小高度，必須大於 [RECORDING_CANCEL_HEIGHT_DP]。
 * 有這個下限，無論字型行高怎麼變，錄音中的「取消」都不會成為決定區塊高度的那一項，
 * 待命與錄音中的區塊高度因此必然相同（中央圓形不會位移）。
 */
private const val IDLE_SECONDARY_MIN_HEIGHT_DP = RECORDING_CANCEL_HEIGHT_DP + 2

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
    private val onAiEditConfirmReplace: () -> Unit = {},
    private val onCycleTranslationLanguage: () -> Unit = {},
    private val onTranslateShortcut: () -> Unit = {}
) {
    /**
     * 目前生效的 IME 主題配色。建構時先使用 Legacy 暖灰棕，因此升級後的外觀與 v0.15.5 相同；
     * 顏色一律在繪製當下解析（見 [PaletteShapeDrawable]），所以切換主題不需要重建任何 View。
     */
    @Volatile
    private var palette: ThemePalette = ThemePaletteRegistry.legacyImePalette()

    /**
     * 目前生效的視覺規格（圖示、按鈕、邊框、表面、錄音視覺化、動畫）。
     * 與 [palette] 一樣是「只影響繪製」的資料，預設為 Legacy，因此不改變 v0.15.5 外觀。
     */
    @Volatile
    private var visual: ThemeVisualSpec = ThemeVisualSpecRegistry.forTheme(AppThemeId.LEGACY_WARM)

    /** 需要在切換主題時重新套用的「非 Drawable」顏色（圖示 tint 與文字色）。 */
    private val colourRestylers = mutableListOf<() -> Unit>()

    /**
     * 只有在建構期間才記錄顏色套用動作：面板的固定元件各記錄一次，
     * 之後的重繪（狀態變更、清單重建）不會再往清單裡塞東西。
     */
    private var recordingStyles = true

    /** 目前狀態的畫面重繪動作；切換主題後用同一組參數再畫一次即可。 */
    private var refreshState: (() -> Unit)? = null

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
    /** 目前畫面是否為「錄音中的縮放圓形」；用來忽略非錄音期間遲到的音量回呼。 */
    private var recordingCircleActive = false

    /** 目前畫面上的錄音視覺化圖形；用來把音量餵給它，切換主題或狀態時會換成新的。 */
    private var recordingVisualizerRef: RecordingVisualizerDrawable? = null
    internal val mainInteractionContainer = FrameLayout(context)
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

    /**
     * 主標題／副標題與錄音中的「取消」共用同一個幾何區塊。
     * 兩者互斥顯示、區塊高度固定，所以待命與錄音中的中央圓形位置完全相同。
     */
    private val idleSecondarySlot = FrameLayout(context)
    private val idleSecondaryStack = LinearLayout(context)
    internal val recordingCancelButton = textView(context, sizeSp = 13f, color = TEXT).apply {
        text = "取消"
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(context, 22), 0, dp(context, 22), 0)
        isClickable = true
        isFocusable = true
        contentDescription = "取消"
        visibility = View.GONE
        background = RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            solid(SURFACE_VARIANT, dp(context, 17)),
            null
        )
        setOnClickListener { onCancel() }
    }
    internal val busyActions = LinearLayout(context)
    private val voiceActionsContainer = FrameLayout(context)
    private val sideActionColumn = LinearLayout(context)
    private val busyLabel = textView(context, sizeSp = 13f, color = TEXT)
    private val formatModeExitButton = LinearLayout(context)
    internal val translateLanguageButton = LinearLayout(context)
    internal val translateShortcutButton = ImageButton(context)
    internal val busyProgressIndicator = ProgressBar(context, null, android.R.attr.progressBarStyleSmall)
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
                BACKGROUND,
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
        toolbarIconButton(
            clipboardButton,
            R.drawable.ic_ime_clipboard,
            "剪貼簿歷史",
            TOOLBAR_ICON,
            TOOLBAR_ICON_COMPACT_SIZE_DP
        ) { showClipboardHistoryPanel(onIsSensitiveEditor()) }
        toolbarTextActionButton(context, clearAllButton, "清除", PINK, "清除全部文字") {
            topToolbarRow.visibility = View.GONE
            clearConfirmationPanel.visibility = View.VISIBLE
        }
        toolbarIconButton(
            settingsButton,
            R.drawable.ic_ime_settings,
            "設定",
            TOOLBAR_ICON,
            TOOLBAR_ICON_SIZE_DP
        ) { onOpenSettings() }
        var backspacePointerActive = false
        var backspaceClickAllowed = false
        backspaceButton.apply {
            setImageResource(R.drawable.ic_ime_backspace)
            imageTint(blend(TEXT_MUTED, PINK, 0.45f))
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
            imageTint(blend(TEXT_MUTED, LAVENDER, 0.5f))
            contentDescription = "換行"
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7))
            setOnClickListener { onEnter() }
        }

        toolbarIconButton(
            translateShortcutButton,
            R.drawable.ic_ime_translate,
            ToolbarSlot.TRANSLATE.label,
            TEXT_MUTED,
            TOOLBAR_ICON_COMPACT_SIZE_DP
        ) { onTranslateShortcut() }
        toolbarIconButton(
            switchButton,
            R.drawable.ic_ime_keyboard,
            ToolbarSlot.KEYBOARD.label,
            TOOLBAR_ICON,
            TOOLBAR_ICON_SIZE_DP
        ) { onSwitchInputMethod() }
        toolbarIconButton(
            moreButton,
            R.drawable.ic_ime_more,
            "更多功能",
            LAVENDER_BRIGHT,
            TOOLBAR_ICON_SIZE_DP
        ) { showMorePanel() }
        // 工具列順序由 ToolbarSlot 單一來源決定：鍵盤／設定／更多永遠是最後三個，
        // AI 翻譯固定緊鄰鍵盤左側，避免未來新增功能時插進固定位置。
        val toolbarViews = mapOf(
            ToolbarSlot.CLIPBOARD to clipboardButton,
            ToolbarSlot.SELECT_ALL to selectAllButton,
            ToolbarSlot.CLEAR to clearAllButton,
            ToolbarSlot.TRANSLATE to translateShortcutButton,
            ToolbarSlot.KEYBOARD to switchButton,
            ToolbarSlot.SETTINGS to settingsButton,
            ToolbarSlot.MORE to moreButton
        )
        ToolbarSlot.entries.forEach { slot ->
            // 全部項目等寬（weight=1）：圖示與文字項目共用同一節奏，中心間距一致；
            // 也讓 selected / unselected 不會改變任何項目的寬度或高度。
            topToolbarRow.addView(
                toolbarViews.getValue(slot),
                LinearLayout.LayoutParams(0, dp(context, TOOLBAR_BUTTON_HEIGHT_DP), 1f)
            )
        }

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
                SURFACE_VARIANT,
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
            // 外框只保留在「整個工具列容器」上（不是逐顆按鈕），四套主題各有不同處理。
            val borderWidth = containerBorderWidthPx(context)
            background = shape(
                fill = TOOLBAR_BACKGROUND,
                radius = dp(context, 18),
                stroke = if (borderWidth > 0) blend(OUTLINE, BACKGROUND, 0.35f) else null,
                strokeWidth = borderWidth,
                borderStyle = visual.containerBorderStyle,
                accent = TOOLBAR_ICON,
                shadowAlpha = visual.shadowAlpha
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
            // 左右對稱留白＝右側 Delete/Enter action rail 的寬度：文字被限制在中央內容
            // 安全區內（不會被 Enter 吃掉），但因為左右對稱，大圓仍維持在整個面板的中心線。
            setPadding(
                dp(context, VOICE_IME_SIDE_ACTION_COLUMN_WIDTH_DP),
                0,
                dp(context, VOICE_IME_SIDE_ACTION_COLUMN_WIDTH_DP),
                0
            )
            idleMicButton.apply {
                setImageResource(R.drawable.ic_ime_mic)
                imageTint(BUTTON_TEXT)
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
                LinearLayout.LayoutParams(
                    dp(context, IDLE_MIC_BUTTON_SIZE_DP),
                    dp(context, IDLE_MIC_BUTTON_SIZE_DP)
                )
            )
            idleTitle.apply {
                text = "開始語音輸入"
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            idleHint.apply {
                text = "點一下開始說話"
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            // 標題與說明照原間距疊放，再由外層 FrameLayout 疊上錄音中的「取消」按鈕：
            // 兩個狀態的區塊高度都等於標題＋說明的自然高度，因此中央圓形不會位移。
            idleSecondaryStack.apply {
                orientation = LinearLayout.VERTICAL
                // 區塊高度永遠由標題＋說明決定，取消按鈕只覆蓋、不撐高。
                minimumHeight = dp(context, IDLE_SECONDARY_MIN_HEIGHT_DP)
                addView(
                    idleTitle,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(context, IDLE_TITLE_TOP_MARGIN_DP) }
                )
                addView(
                    idleHint,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(context, IDLE_HINT_TOP_MARGIN_DP) }
                )
            }
            idleSecondarySlot.addView(
                idleSecondaryStack,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            idleSecondarySlot.addView(
                recordingCancelButton,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(context, RECORDING_CANCEL_HEIGHT_DP),
                    Gravity.CENTER
                )
            )
            addView(
                idleSecondarySlot,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
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
                    imageTint(TEXT_MUTED)
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
            ).apply { marginEnd = dp(context, VOICE_IME_SIDE_ACTION_COLUMN_WIDTH_DP) }
        )

        // 語音翻譯模式：左上角的小型語言切換晶片，沿用既有互動區，不改變 IME 高度。
        translateLanguageButton.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = "切換翻譯目標語言"
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
                    setImageResource(R.drawable.ic_ime_translate)
                    imageTint(LAVENDER_BRIGHT)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                },
                LinearLayout.LayoutParams(dp(context, BACK_ICON_DP), dp(context, BACK_ICON_DP)).apply {
                    marginEnd = dp(context, BACK_ICON_GAP_DP)
                }
            )
            addView(
                textView(context, sizeSp = NORMAL_HINT_TEXT_SP, color = LAVENDER_BRIGHT).apply {
                    text = "切換"
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            setOnClickListener { onCycleTranslationLanguage() }
        }
        voiceActionsContainer.addView(
            translateLanguageButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(context, BACK_BUTTON_HEIGHT_DP),
                Gravity.TOP or Gravity.START
            )
        )

        busyActions.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            busyProgressIndicator.progressTint(LAVENDER)
            addView(busyProgressIndicator, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)))
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
        // 處理中群組同理：左右對稱留白，中心維持在面板中心線且不侵入右側操作區。
        voiceActionsContainer.addView(
            busyActions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = dp(context, VOICE_IME_SIDE_ACTION_COLUMN_WIDTH_DP)
                marginEnd = dp(context, VOICE_IME_SIDE_ACTION_COLUMN_WIDTH_DP)
            }
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
        // 中央主內容（主按鈕／標題／副標題）以整個面板寬度為準置中；右側 Delete / Enter
        // 改為獨立覆蓋定位，不再佔用橫向寬度，因此不會把中央主內容推向左邊。
        // 需要避開側邊操作區的控制項（錄音列、忙碌列、返回）自行加右側留白，
        // 其餘寬度與位置與修正前一致。
        mainInteractionContainer.addView(
            voiceActionsContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        mainInteractionContainer.addView(
            sideActionColumn,
            FrameLayout.LayoutParams(
                dp(context, VOICE_IME_SIDE_ACTION_COLUMN_WIDTH_DP),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
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
        // 建構完成：之後的重繪不再累積顏色套用動作。
        recordingStyles = false
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
        showResultPreview("AI 編輯預覽", originalText, resultText, message = null, showConfirm = true)
    }

    /** 語音翻譯無法安全插入時顯示結果；只提供「複製結果／取消」，不提供取代。 */
    fun showTranslationResult(originalText: String, translatedText: String, message: String?) {
        showResultPreview("語音翻譯結果", originalText, translatedText, message, showConfirm = false)
    }

    private fun showResultPreview(
        title: String,
        originalText: String,
        resultText: String,
        message: String?,
        showConfirm: Boolean
    ) {
        clearSnippetDeleteConfirmation()
        aiEditTitle.text = title
        aiEditOriginalText.text = originalText
        aiEditResultText.text = resultText
        showAiEditStatus(message)
        aiEditConfirmButton.visibility = if (showConfirm) View.VISIBLE else View.GONE
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
        // 離開主互動頁時停止音量縮放回饋，避免回呼還在更新看不見的圓形。
        if (page != PanelPage.MAIN) {
            recordingCircleActive = false
            applyRecordingCircleScale(RecordingCircleScale.BASE_SCALE)
        }
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
        // 語音翻譯已改由頂部工具列的翻譯圖示作為唯一入口，不在「更多」頁重複提供。
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
                SURFACE_VARIANT,
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
            background = rounded(SURFACE_VARIANT, dp(context, 12), OUTLINE, dp(context, 1))
            addView(
                snippetSelectAllCheckBox.apply {
                    text = "全部"
                    textSize = 10f
                    textColour(TEXT)
                    buttonTint(LAVENDER_BRIGHT)
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
            textColour(TEXT)
            hintColour(TEXT_MUTED)
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            background = rounded(SURFACE, dp(context, 12), OUTLINE, dp(context, 1))
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
            button.textColour(if (selected) TEXT else TEXT_MUTED)
            button.background = rounded(
                if (selected) SURFACE_VARIANT else SURFACE,
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
                SCRIM_OVERLAY,
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
            background = rounded(SURFACE_VARIANT, dp(context, 14), OUTLINE, dp(context, 1))
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
        background = rounded(SURFACE, dp(context, 13), OUTLINE, dp(context, 1))
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
            buttonTint(LAVENDER_BRIGHT)
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
                imageTint(LAVENDER_BRIGHT)
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
            SURFACE, dp(context, 16), OUTLINE, dp(context, 1)
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
            SURFACE, dp(context, 16), OUTLINE, dp(context, 1)
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
        color: ImeColorSpec,
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
        aiEditMode: Boolean = false,
        translateMode: Boolean = false,
        translationTargetLabel: String? = null,
        micAvailable: Boolean = true
    ) {
        // 記下這一組狀態參數，切換主題時用同一組內容重畫，狀態機完全不動。
        refreshState = {
            render(
                state = state,
                statusLabelOverride = statusLabelOverride,
                holdToTalkRecording = holdToTalkRecording,
                formatCommandMode = formatCommandMode,
                aiEditMode = aiEditMode,
                translateMode = translateMode,
                translationTargetLabel = translationTargetLabel,
                micAvailable = micAvailable
            )
        }
        val isTerminal = VoiceImeActionAvailabilityPolicy.isTerminal(state)
        val accent = when {
            state == VoiceImeState.IDLE && !micAvailable -> TEXT_MUTED
            else -> accentFor(state)
        }
        statusDot.background = oval(accent)
        val stateDescription = when {
            state == VoiceImeState.IDLE && !micAvailable -> "麥克風未授權"
            state == VoiceImeState.IDLE -> "待命"
            state == VoiceImeState.RECORDING -> "錄音中"
            state == VoiceImeState.TRANSCRIBING -> "語音辨識中"
            state == VoiceImeState.FORMATTING -> "智慧整理中"
            state == VoiceImeState.AWAITING_CONFIRM -> "修改預覽中"
            state == VoiceImeState.SUCCESS -> "辨識完成"
            state == VoiceImeState.FORMATTING_FALLBACK -> "整理失敗，已保留辨識結果"
            state == VoiceImeState.CANCELLED -> "已取消"
            state == VoiceImeState.ERROR -> "發生錯誤"
            else -> "待命"
        }
        statusIndicator.contentDescription = if (statusLabelOverride.isNullOrBlank()) {
            stateDescription
        } else {
            "$stateDescription，$statusLabelOverride"
        }

        val settingsEnabled = VoiceImeActionAvailabilityPolicy.isSettingsAvailable(state)
        settingsButton.isEnabled = settingsEnabled
        settingsButton.alpha = if (settingsEnabled) 1f else 0.5f

        val bulkActionsEnabled = VoiceImeActionAvailabilityPolicy.isBulkTextActionAvailable(state)
        selectAllButton.isEnabled = bulkActionsEnabled
        clearAllButton.isEnabled = bulkActionsEnabled
        selectAllButton.alpha = if (bulkActionsEnabled) 1f else 0.5f
        clearAllButton.alpha = if (bulkActionsEnabled) 1f else 0.5f

        val moreEnabled = VoiceImeActionAvailabilityPolicy.isMoreMenuAvailable(state)
        moreButton.isEnabled = moreEnabled
        moreButton.alpha = if (moreEnabled) 1f else 0.5f

        val translateEnabled = VoiceImeActionAvailabilityPolicy.isTranslateShortcutAvailable(state)
        translateShortcutButton.isEnabled = translateEnabled
        translateShortcutButton.alpha = if (translateEnabled) 1f else 0.5f

        val clipboardEnabled = VoiceImeActionAvailabilityPolicy.isClipboardHistoryAvailable(state)
        clipboardButton.isEnabled = clipboardEnabled
        clipboardButton.alpha = if (clipboardEnabled) 1f else 0.5f

        val switchEnabled = VoiceImeActionAvailabilityPolicy.isKeyboardSwitchAvailable(state)
        switchButton.isEnabled = switchEnabled
        switchButton.alpha = if (switchEnabled) 1f else 0.5f

        // AI 翻譯快捷圖示：啟用時只把圖示改成薄荷綠（改變 tint），背景與其他工具列項目
        // 完全相同，因此 selected / unselected 的 Layout Bounds 一致，不會膨脹成大方框。
        translateShortcutButton.imageTint(if (translateMode) MODE_TRANSLATE else TEXT_MUTED)
        if (!bulkActionsEnabled) {
            clearConfirmationPanel.visibility = View.GONE
            topToolbarRow.visibility = View.VISIBLE
        } else if (clearConfirmationPanel.visibility != View.VISIBLE) {
            topToolbarRow.visibility = View.VISIBLE
        }
        formatModeExitButton.visibility = if (
            (formatCommandMode || aiEditMode || translateMode) &&
            (state == VoiceImeState.IDLE || isTerminal)
        ) View.VISIBLE else View.GONE
        translateLanguageButton.visibility = if (translateMode && (state == VoiceImeState.IDLE || isTerminal)) {
            View.VISIBLE
        } else {
            View.GONE
        }
        // 各模式共用同一個 Main Content Safe Area；只有中央內容不同。文字一律被 idleActions
        // 的對稱左右留白限制在中央內容安全區內，因此不會被右側 Delete / Enter action rail 遮住。
        if (formatCommandMode) {
            // 格式指令模式：模式名稱（主標題）與說明（次要字級）分成兩個區塊，兩行都置中。
            // 為了在固定 130dp 內同時放得下大圓＋標題＋可能換行的說明，格式模式把兩個
            // 區塊的上緣間距設為 0（純高度預算考量，不是視覺 offset）。
            idleTitle.visibility = View.VISIBLE
            idleTitle.text = FormatCommandPrompt.FORMAT_COMMAND_MODE_LABEL
            applyTopMargin(idleTitle, 0)
            idleHint.textSize = FORMAT_MODE_HINT_TEXT_SP
            idleHint.textColour(TEXT_MUTED)
            idleHint.text = FormatCommandPrompt.FORMAT_COMMAND_HINT
            applyTopMargin(idleHint, 0)
        } else if (aiEditMode) {
            idleTitle.visibility = View.VISIBLE
            idleTitle.text = "AI 編輯模式"
            applyTopMargin(idleTitle, dp(idleTitle.context, IDLE_TITLE_TOP_MARGIN_DP))
            idleHint.textSize = NORMAL_HINT_TEXT_SP
            idleHint.textColour(TEXT_MUTED)
            idleHint.text = if (holdToTalkRecording) "放開即辨識" else "選取文字後，說出修改要求"
            applyTopMargin(idleHint, dp(idleHint.context, IDLE_HINT_TOP_MARGIN_DP))
        } else if (translateMode) {
            idleTitle.visibility = View.VISIBLE
            idleTitle.text = "語音翻譯模式"
            applyTopMargin(idleTitle, dp(idleTitle.context, IDLE_TITLE_TOP_MARGIN_DP))
            idleHint.textSize = NORMAL_HINT_TEXT_SP
            idleHint.textColour(TEXT_MUTED)
            idleHint.text = if (holdToTalkRecording) {
                "放開即辨識"
            } else {
                "翻譯成：${translationTargetLabel.orEmpty()}"
            }
            applyTopMargin(idleHint, dp(idleHint.context, IDLE_HINT_TOP_MARGIN_DP))
        } else {
            idleTitle.visibility = View.VISIBLE
            idleTitle.text = "開始語音輸入"
            applyTopMargin(idleTitle, dp(idleTitle.context, IDLE_TITLE_TOP_MARGIN_DP))
            idleHint.textSize = NORMAL_HINT_TEXT_SP
            idleHint.textColour(TEXT_MUTED)
            idleHint.text = when {
                !micAvailable -> "麥克風未授權，點一下授權"
                holdToTalkRecording -> "放開即辨識"
                else -> "點一下開始說話"
            }
            applyTopMargin(idleHint, dp(idleHint.context, IDLE_HINT_TOP_MARGIN_DP))
        }
        // 特殊模式改用專屬圖示，讓模式只靠主按鈕就能辨識；長按錄音時維持模式圖示並以
        // 啟動配色（isActivated）表達收音中，不會因此換回麥克風圖示。
        // 錄音中（非長按）時，同一個中央圓形按鈕就地變成「停止」：圓形內換成停止圖示、
        // 沿用錄音珊瑚底色，半徑再依實際麥克風音量縮放（見 updateAudioLevel）。
        val recordingCircle = state == VoiceImeState.RECORDING && !holdToTalkRecording
        idleMicButton.setImageResource(
            when {
                recordingCircle -> R.drawable.ic_ime_stop
                formatCommandMode -> R.drawable.ic_ime_format_command
                aiEditMode -> R.drawable.ic_ime_ai_edit
                translateMode -> R.drawable.ic_ime_translate
                else -> R.drawable.ic_ime_mic
            }
        )
        idleMicButton.contentDescription = when {
            recordingCircle -> "停止錄音"
            formatCommandMode -> "格式指令模式錄音"
            aiEditMode -> "AI 編輯模式錄音"
            translateMode -> "語音翻譯模式錄音"
            else -> "開始語音輸入"
        }
        idleMicButton.isActivated = holdToTalkRecording
        idleMicButton.alpha = if (holdToTalkRecording) 0.9f else 1f
        // 模式色集中在 VoiceModePalette：翻譯＝鼠尾草綠、格式指令＝柔霧紫、AI 編輯＝蜜桃粉，
        // 待命與處理中都維持同一模式識別；一般語音使用奶油杏漸層。
        val modeAccent = modeColorSpecs(translateMode, formatCommandMode, aiEditMode)
        idleMicButton.imageTint(
            if (recordingCircle) BUTTON_TEXT else (modeAccent?.icon ?: BUTTON_TEXT)
        )
        idleMicButton.background = when {
            recordingCircle -> recordingCircleBackground()
            modeAccent != null -> circleRipple(idleMicButton.context, modeAccent.base, modeAccent.bright)
            holdToTalkRecording -> recordingCircleBackground()
            else -> circleRipple(idleMicButton.context)
        }
        idleActions.visibility = if (
            state == VoiceImeState.IDLE || isTerminal || holdToTalkRecording || recordingCircle
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        // 錄音中讓標題／說明留在原位（INVISIBLE 保留高度），由「取消」按鈕佔用同一區塊：
        // 中央圓形與待命時位置相同，IME 總高度不變。
        idleTitle.visibility = if (recordingCircle) View.INVISIBLE else View.VISIBLE
        idleHint.visibility = if (recordingCircle) View.INVISIBLE else View.VISIBLE
        recordingCancelButton.visibility = if (recordingCircle) View.VISIBLE else View.GONE
        recordingCircleActive = recordingCircle
        // 非錄音狀態一律回到基礎半徑，避免殘留的放大尺寸留在畫面上。
        if (!recordingCircle) applyRecordingCircleScale(RecordingCircleScale.BASE_SCALE)
        val isBusy = state == VoiceImeState.TRANSCRIBING || state == VoiceImeState.FORMATTING
        busyActions.visibility = if (isBusy) View.VISIBLE else View.GONE
        // 處理中也維持目前模式的識別色，不回到一般模式的淡紫。
        busyProgressIndicator.progressTint(modeAccent?.base ?: LAVENDER)
        busyLabel.text = when {
            formatCommandMode && isBusy -> "正在處理格式指令…"
            aiEditMode && isBusy -> "正在套用修改…"
            translateMode && isBusy -> "正在翻譯…"
            else -> state.label
        }
    }

    /**
     * 套用 IME 主題配色。
     *
     * 只重新套用視覺屬性（tint／文字色）並以同一組參數重畫目前狀態；背景是即時取色的
     * Drawable，重畫即生效。不重建任何 View、不重設 VoiceImeStateMachine、錄音作業、
     * 音量縮放比例或任何操作狀態。
     */
    internal fun applyTheme(
        palette: ThemePalette,
        visual: ThemeVisualSpec = ThemeVisualSpecRegistry.forTheme(palette.themeId)
    ) {
        if (palette == this.palette && visual == this.visual) return
        this.palette = palette
        this.visual = visual
        colourRestylers.toList().forEach { it() }
        refreshState?.invoke()
        // 子頁面（歷史、快捷短語…）的動態內容在同一次切換中一起換色。
        updatePageVisibility()
        view.invalidate()
    }

    /** 目前生效的面板配色；供 Service 與測試確認主題已套用。 */
    internal fun currentPalette(): ThemePalette = palette

    /** 目前生效的視覺規格；供測試確認主題造型已套用。 */
    internal fun currentVisualSpec(): ThemeVisualSpec = visual

    /**
     * 錄音中的音量回饋：音量越大，中央圓形越大。
     * 只調整 view 的 scale，不動 layout bounds，所以圓形位置、觸控區域與 IME 高度都不變；
     * 音量本身已在 Service 端平滑過（[AudioLevelSmoother]），這裡不再另外啟動動畫，
     * 以免每 40ms 重新啟動一次動畫造成抖動與額外負擔。
     */
    internal fun updateAudioLevel(level: Float) {
        if (!recordingCircleActive) return
        applyRecordingCircleScale(
            RecordingCircleScale.forLevel(level, visual.animationStyle.maxScale)
        )
        val visualizer = recordingVisualizerRef
        if (visualizer != null) {
            visualizer.level = level
        } else if (visual.hasRecordingOverlay && animationsEnabled()) {
            // 背景尚未建立（例如剛切換主題）：先重畫一次再餵音量。
            refreshState?.invoke()
            recordingVisualizerRef?.level = level
        }
    }

    private fun applyRecordingCircleScale(scale: Float) {
        if (idleMicButton.scaleX == scale && idleMicButton.scaleY == scale) return
        idleMicButton.scaleX = scale
        idleMicButton.scaleY = scale
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
        foregroundColor: ImeColorSpec,
        background: android.graphics.drawable.Drawable,
        onClick: () -> Unit,
        iconSizeDp: Int = 18,
        labelSizeSp: Float = 14f,
        labelGapDp: Int = 7
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = label
        this.background = RippleDrawable(
            ColorStateList.valueOf(RIPPLE_MASK(palette)),
            background,
            null
        )
        val iconView = ImageView(context).apply {
            setImageResource(icon)
            imageTint(foregroundColor)
        }
        addView(iconView, LinearLayout.LayoutParams(dp(context, iconSizeDp), dp(context, iconSizeDp)))
        val labelView = textView(context, sizeSp = labelSizeSp, color = foregroundColor).apply {
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
            ).apply { marginStart = dp(context, labelGapDp) }
        )
        setOnClickListener { onClick() }
    }

    private fun secondaryActionButton(
        context: Context,
        button: TextView,
        label: String,
        color: ImeColorSpec,
        onClick: () -> Unit
    ): View = button.apply {
        text = label
        gravity = Gravity.CENTER
        textColour(color)
        setTypeface(typeface, Typeface.BOLD)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(
            ColorStateList.valueOf(RIPPLE_MASK(palette)),
            solid(SURFACE, dp(context, 14)),
            null
        )
        setOnClickListener { onClick() }
    }

    /**
     * 統一的工具列純圖示按鈕建構方式：相同容器、相同垂直中心、相同基準圖示尺寸。
     * 只有滿版圖示可用 [TOOLBAR_ICON_COMPACT_SIZE_DP] 做小幅視覺補償，其餘一律 [TOOLBAR_ICON_SIZE_DP]。
     */
    private fun toolbarIconButton(
        button: ImageButton,
        @DrawableRes iconRes: Int,
        description: String,
        tint: ImeColorSpec,
        iconSizeDp: Int,
        onClick: () -> Unit
    ) = button.apply {
        val iconContext = context
        setImageResource(iconRes)
        imageTint(tint)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        // 依主題的按鈕／邊框風格重建背景；Legacy 與原本完全相同。
        styleOnce { background = themedButtonBackground(iconContext, dp(iconContext, 10)) }
        val iconPadding = dp(iconContext, toolbarIconPaddingDp(iconSizeDp))
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun toolbarTextActionButton(
        context: Context,
        button: TextView,
        label: String,
        color: ImeColorSpec,
        description: String,
        onClick: () -> Unit
    ) = button.apply {
        text = label
        contentDescription = description
        textSize = 12f
        gravity = Gravity.CENTER
        setSingleLine(true)
        ellipsize = null
        textColour(color)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(context, 2), 0, dp(context, 2), 0)
        isClickable = true
        isFocusable = true
        styleOnce { background = themedButtonBackground(context, dp(context, 10)) }
        setOnClickListener { onClick() }
    }

    private fun toolbarRipple(radius: Int) = RippleDrawable(
        ColorStateList.valueOf(RIPPLE_MASK(palette)),
        null,
        solid(MASK_WHITE, radius)
    )

    private fun accentFor(state: VoiceImeState): ImeColorSpec = when (state) {
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

    private fun textView(context: Context, sizeSp: Float, color: ImeColorSpec) = TextView(context).apply {
        textSize = sizeSp
        textColour(color)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
    }

    /**
     * 套用顏色並（只在建構期間）記下來供切換主題時重新套用。
     * 只用於「非 Drawable」的顏色（圖示 tint、文字色、進度條）；背景一律走
     * [PaletteShapeDrawable]，在繪製當下取色，不需要重新指定。
     */
    private fun styleOnce(action: () -> Unit) {
        action()
        if (recordingStyles) colourRestylers += action
    }

    private fun ImageView.imageTint(spec: ImeColorSpec) =
        styleOnce { imageTintList = ColorStateList.valueOf(spec(palette)) }

    private fun TextView.textColour(spec: ImeColorSpec) =
        styleOnce { setTextColor(spec(palette)) }

    private fun TextView.hintColour(spec: ImeColorSpec) =
        styleOnce { setHintTextColor(spec(palette)) }

    private fun ProgressBar.progressTint(spec: ImeColorSpec) =
        styleOnce { indeterminateTintList = ColorStateList.valueOf(spec(palette)) }

    private fun CompoundButton.buttonTint(spec: ImeColorSpec) =
        styleOnce { buttonTintList = ColorStateList.valueOf(spec(palette)) }

    /** 圓角矩形／圓形的即時取色 Drawable；顏色在 draw() 當下才向主題查詢。 */
    private fun shape(
        fill: ImeColorSpec,
        fillEnd: ImeColorSpec = fill,
        radius: Int = 0,
        stroke: ImeColorSpec? = null,
        strokeWidth: Int = 0,
        oval: Boolean = false,
        borderStyle: BorderStyle = BorderStyle.NEUTRAL,
        accent: ImeColorSpec? = null,
        shadowAlpha: Float = 0f
    ) = PaletteShapeDrawable(
        fill,
        fillEnd,
        radius,
        stroke,
        strokeWidth,
        oval,
        borderStyle,
        accent,
        shadowAlpha
    ) { palette }

    /**
     * 工具列／晶片按鈕的背景：**只有底色，沒有逐顆外框**。
     *
     * 2026-10-12 實機回報：先前每個按鈕都畫白色圓角外框，七顆排在一起非常擁擠。
     * 現在四套主題只靠底色（＋圖示色＋錄音視覺化）區分，外框只保留在「整個容器」上。
     * Legacy 直接沿用原本的 [toolbarRipple]，因此外觀與 v0.15.5 完全相同。
     */
    private fun themedButtonBackground(context: Context, radiusPx: Int): Drawable {
        val fill = visual.toolbarButtonFill(palette) ?: return toolbarRipple(radiusPx)
        val content = shape(fill = { fill }, radius = radiusPx)
        return RippleDrawable(ColorStateList.valueOf(RIPPLE_MASK(palette)), content, null)
    }

    /** 面板容器（工具列、卡片）的邊框寬度：Legacy 沿用原本的 1dp。 */
    private fun containerBorderWidthPx(context: Context): Int = when (visual.themeId) {
        AppThemeId.LEGACY_WARM -> dp(context, 1)
        AppThemeId.CUTE_CREAM -> 0
        else -> dp(context, visual.borderWidthDp.toInt().coerceIn(1, 3))
    }

    /**
     * 錄音視覺化圖形的前景色。
     *
     * 必須與錄音圓形底色（珊瑚色）有足夠對比，否則圖形等於看不見：
     * 先前的實作把刻度畫成與圓形相同的珊瑚色，才會出現「只有圓形縮放」的結果。
     */
    private fun visualizerAccent(): ImeColorSpec = when (visual.recordingVisualStyle) {
        RecordingVisualStyle.SOFT_PULSE -> LAVENDER_BRIGHT
        RecordingVisualStyle.SEGMENTED_METER_RING -> RECORDER_FOREGROUND
        RecordingVisualStyle.SIGNAL_WAVE -> SECONDARY_ACCENT
        RecordingVisualStyle.SMOOTH_SCALE -> RECORDER_FOREGROUND
        RecordingVisualStyle.LEGACY -> RECORDER_FOREGROUND
    }

    /** 主題的錄音視覺化圖形；只在該主題有定義且系統允許動畫時才會繪製。 */
    private fun recordingVisualizer(): RecordingVisualizerDrawable? {
        if (!visual.hasRecordingOverlay) return null
        if (!animationsEnabled()) return null
        return RecordingVisualizerDrawable(
            style = visual.recordingVisualStyle,
            accent = visualizerAccent(),
            segmentCount = visual.meterSegmentCount,
            cycles = visual.animationStyle.pulseCycles,
            provider = { palette }
        )
    }

    /** 系統是否允許動畫（開發人員選項／無障礙設定關閉動畫時為 false）。 */
    private fun animationsEnabled(): Boolean = runCatching {
        android.animation.ValueAnimator.areAnimatorsEnabled()
    }.getOrDefault(false)

    private fun solid(color: ImeColorSpec, radius: Int) = shape(fill = color, radius = radius)

    private fun rounded(color: ImeColorSpec, radius: Int, strokeColor: ImeColorSpec, strokeWidth: Int) =
        shape(fill = color, radius = radius, stroke = strokeColor, strokeWidth = strokeWidth)

    private fun oval(color: ImeColorSpec) = shape(fill = color, oval = true)

    private fun ripple(context: Context, color: ImeColorSpec, stroke: ImeColorSpec, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(RIPPLE_MASK(palette)),
        solid(color, radius),
        rounded(TRANSPARENT, radius, stroke, dp(context, 1))
    )

    /** 設定（或還原）標題／說明與上方內容的間距；只在需要調整高度預算時使用。 */
    private fun applyTopMargin(view: View, marginPx: Int) {
        val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.topMargin == marginPx) return
        params.topMargin = marginPx
        view.layoutParams = params
    }

    private fun circleRipple(
        context: Context,
        gradientStart: ImeColorSpec = RECORDER_BACKGROUND,
        gradientEnd: ImeColorSpec = LAVENDER_BRIGHT
    ) = RippleDrawable(
        ColorStateList.valueOf(0x44FFFFFF),
        shape(fill = gradientStart, fillEnd = gradientEnd, oval = true),
        oval(MASK_WHITE)
    )

    /**
     * 錄音中的圓形底色：主題有定義光暈（[ThemePalette.recordingHalo]）時，在圓形外緣加一圈
     * 低強度光暈；Legacy 的光暈全透明，因此外觀與 v0.15.5 完全相同。
     */
    private fun recordingCircleBackground(): Drawable {
        val halo = RECORDING_HALO(palette)
        val panelContext = view.context
        val visualRadiusPx = dp(panelContext, IDLE_MIC_BUTTON_SIZE_DP / 2)
        // 主按鈕視覺圓比觸控區小：外圈留給主題刻度／電波，且絕不覆蓋中央停止圖示。
        val surfaceInset = (visualRadiusPx * (1f - RecordingVisualPlanner.BUTTON_SURFACE_RADIUS_FRACTION))
            .toInt()
            .coerceAtLeast(0)
        val circle = InsetDrawable(
            solid(CORAL, dp(panelContext, 100)),
            surfaceInset,
            surfaceInset,
            surfaceInset,
            surfaceInset
        )
        val haloWidth = dp(panelContext, 4)
        val visualizer = recordingVisualizer()
        recordingVisualizerRef = visualizer
        // 光暈畫在視覺圓的邊緣（也就是視覺圓外緣），不會壓在按鈕表面上。
        val haloVisible = Color.alpha(halo) != 0 && haloWidth > 0
        if (visualizer == null && !haloVisible) return circle
        val layers = mutableListOf<Drawable>()
        layers += circle
        if (haloVisible) {
            layers += shape(
                fill = TRANSPARENT,
                stroke = RECORDING_HALO,
                strokeWidth = haloWidth,
                radius = dp(panelContext, 100),
                oval = true
            )
        }
        // 視覺化圖形畫在圓形底色之上、圖示之下（圖示由 ImageView 另外繪製），
        // 因此環形刻度／電波紋看得見，而圓形中央的停止圖示仍然清楚。
        visualizer?.let { layers += it }
        return LayerDrawable(layers.toTypedArray())
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun blend(background: ImeColorSpec, foreground: ImeColorSpec, fraction: Float): ImeColorSpec =
        { current -> ThemeMix.blend(background(current), foreground(current), fraction) }

    /** 中央錄音按鈕的模式配色（漸層兩端與圖示）；null 代表一般語音。 */
    private data class ModeColorSpecs(
        val base: ImeColorSpec,
        val bright: ImeColorSpec,
        val icon: ImeColorSpec
    )

    private fun modeColorSpecs(translate: Boolean, format: Boolean, aiEdit: Boolean): ModeColorSpecs? = when {
        translate -> ModeColorSpecs(
            base = { it.modeTranslate.base },
            bright = { it.modeTranslate.bright },
            icon = { it.modeTranslate.icon }
        )
        format -> ModeColorSpecs(
            base = { it.modeFormat.base },
            bright = { it.modeFormat.bright },
            icon = { it.modeFormat.icon }
        )
        aiEdit -> ModeColorSpecs(
            base = { it.modeEdit.base },
            bright = { it.modeEdit.bright },
            icon = { it.modeEdit.icon }
        )
        else -> null
    }

    private companion object {
        /**
         * IME 面板的語意顏色 Token。實際數值在繪製當下由目前主題的 [ThemePalette] 提供，
         * 因此同一個 View 可以跟著主題換色，不需要重建或重新指定背景。
         * 名稱沿用 v0.15.5 的舊常數，讓呼叫端保持不變；Legacy 主題的數值與舊常數完全相同。
         */
        val BACKGROUND: ImeColorSpec = { it.background }
        val SURFACE: ImeColorSpec = { it.surface }
        val SURFACE_VARIANT: ImeColorSpec = { it.surfaceVariant }
        val OUTLINE: ImeColorSpec = { it.outline }
        val LAVENDER: ImeColorSpec = { it.primary }
        val LAVENDER_BRIGHT: ImeColorSpec = { it.primaryBright }
        val MINT: ImeColorSpec = { it.statusIdle }
        val AMBER: ImeColorSpec = { it.statusProcessing }
        val PINK: ImeColorSpec = { it.secondary }
        val SECONDARY_ACCENT: ImeColorSpec = { it.secondary }
        val CORAL: ImeColorSpec = { it.recorderAccent }
        val ERROR: ImeColorSpec = { it.error }
        val BUTTON_TEXT: ImeColorSpec = { it.recorderForeground }
        val TEXT: ImeColorSpec = { it.onBackground }
        val TEXT_MUTED: ImeColorSpec = { it.onSurfaceVariant }
        val TOOLBAR_BACKGROUND: ImeColorSpec = { it.toolbarBackground }
        val TOOLBAR_ICON: ImeColorSpec = { it.toolbarIcon }
        val RECORDER_BACKGROUND: ImeColorSpec = { it.recorderBackground }
        val RECORDER_FOREGROUND: ImeColorSpec = { it.recorderForeground }
        val RECORDING_HALO: ImeColorSpec = { it.recordingHalo }
        val SCRIM_OVERLAY: ImeColorSpec = { it.overlayScrim }
        val MODE_TRANSLATE: ImeColorSpec = { it.modeTranslate.base }
        val TRANSPARENT: ImeColorSpec = { Color.TRANSPARENT }
        val MASK_WHITE: ImeColorSpec = { Color.WHITE }
        val RIPPLE_MASK: ImeColorSpec = { 0x33FFFFFF }
    }
}

/**
 * IME 面板的語意顏色運算式：給定目前主題配色，回傳實際 ARGB。
 * 用函式型別而不是列舉，讓 [VoiceImePanel] 的 blend 等組合可以自然疊加。
 */
private typealias ImeColorSpec = (ThemePalette) -> Int

/**
 * 讓測試可以描述面板形狀而不依賴特定 Drawable 實作（背景改為即時取色後不再是 GradientDrawable）。
 */
internal interface ImeShape {
    val ovalShape: Boolean
    val cornerRadiusPx: Int

    /** 目前主題下的實際填色；供測試確認顏色確實來自 Theme Tokens。 */
    fun resolvedFillColor(): Int

    fun resolvedStrokeColor(): Int
}

/**
 * 圓角矩形／圓形的即時取色 Drawable。
 *
 * 顏色在 draw() 當下才向主題查詢，因此切換主題後只要重畫（invalidate）就會套用新配色，
 * 完全不需要重建 View、重新指定背景或碰觸任何操作狀態。
 */
private class PaletteShapeDrawable(
    private val fill: ImeColorSpec,
    private val fillEnd: ImeColorSpec,
    private val radiusPx: Int,
    private val stroke: ImeColorSpec?,
    private val strokeWidthPx: Int,
    private val oval: Boolean,
    private val borderStyle: BorderStyle = BorderStyle.NEUTRAL,
    private val accent: ImeColorSpec? = null,
    private val shadowAlpha: Float = 0f,
    private val provider: () -> ThemePalette
) : Drawable(), ImeShape {
    override val ovalShape: Boolean get() = oval
    override val cornerRadiusPx: Int get() = radiusPx

    override fun resolvedFillColor(): Int = fill(provider())

    override fun resolvedStrokeColor(): Int = stroke?.invoke(provider()) ?: Color.TRANSPARENT

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var alphaScale = 1f

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val current = provider()
        val start = scaled(fill(current))
        val end = scaled(fillEnd(current))
        if (start != end) {
            // 每一幀重建 shader 只為了跟隨主題；圓形直徑固定，成本可忽略。
            fillPaint.color = Color.WHITE
            fillPaint.shader = LinearGradient(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                start,
                end,
                Shader.TileMode.CLAMP
            )
        } else {
            fillPaint.shader = null
            fillPaint.color = start
        }
        val radius = radiusPx.toFloat()
        val strokeColour = stroke?.let { scaled(it(current)) }
        if (oval) {
            canvas.drawOval(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                fillPaint
            )
            if (strokeColour != null && strokeWidthPx > 0) {
                val inset = strokeWidthPx / 2f
                strokePaint.color = strokeColour
                strokePaint.strokeWidth = strokeWidthPx.toFloat()
                canvas.drawOval(
                    bounds.left + inset,
                    bounds.top + inset,
                    bounds.right - inset,
                    bounds.bottom - inset,
                    strokePaint
                )
            }
            decoration(canvas, bounds, current)
            return
        }
        canvas.drawRoundRect(RectF(bounds), radius, radius, fillPaint)
        if (strokeColour != null && strokeWidthPx > 0) {
            val inset = strokeWidthPx / 2f
            strokePaint.color = strokeColour
            strokePaint.strokeWidth = strokeWidthPx.toFloat()
            canvas.drawRoundRect(
                RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset),
                radius,
                radius,
                strokePaint
            )
        }
        decoration(canvas, bounds, current)
    }

    /** 主題造型的額外裝飾：柔和外圈、機能內框、HUD 四角標記；全部畫在同一組 bounds 內。 */
    private fun decoration(canvas: Canvas, rect: Rect, current: ThemePalette) {
        val accentColor = accent?.invoke(current) ?: return
        if (shadowAlpha > 0f) {
            // 柔和外圈：只在邊緣一圈，不做大面積發光。
            accentPaint.color = withAlpha(accentColor, shadowAlpha * alphaScale)
            accentPaint.strokeWidth = maxOf(2f, rect.width() * 0.035f)
            val inset = accentPaint.strokeWidth / 2f
            canvas.drawRoundRect(
                RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                radiusPx.toFloat(),
                radiusPx.toFloat(),
                accentPaint
            )
        }
        when (borderStyle) {
            BorderStyle.HUD_CORNERS -> drawHudCorners(canvas, rect, withAlpha(accentColor, alphaScale))
            BorderStyle.FUNCTIONAL_FRAME -> {
                accentPaint.color = withAlpha(accentColor, 0.5f * alphaScale)
                accentPaint.strokeWidth = maxOf(1f, rect.width() * 0.02f)
                val inset = (strokeWidthPx + accentPaint.strokeWidth).coerceAtMost(rect.width() / 4f)
                val innerRadius = maxOf(2f, radiusPx - inset)
                canvas.drawRoundRect(
                    RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                    innerRadius,
                    innerRadius,
                    accentPaint
                )
            }
            else -> Unit
        }
    }

    private fun drawHudCorners(canvas: Canvas, rect: Rect, accentColor: Int) {
        accentPaint.color = accentColor
        accentPaint.strokeWidth = maxOf(2f, rect.width() * 0.05f)
        val length = rect.width() * 0.24f
        val inset = accentPaint.strokeWidth / 2f
        val left = rect.left + inset
        val top = rect.top + inset
        val right = rect.right - inset
        val bottom = rect.bottom - inset
        canvas.drawLine(left, top, left + length, top, accentPaint)
        canvas.drawLine(left, top, left, top + length, accentPaint)
        canvas.drawLine(right, top, right - length, top, accentPaint)
        canvas.drawLine(right, top, right, top + length, accentPaint)
        canvas.drawLine(left, bottom, left + length, bottom, accentPaint)
        canvas.drawLine(left, bottom, left, bottom - length, accentPaint)
        canvas.drawLine(right, bottom, right - length, bottom, accentPaint)
        canvas.drawLine(right, bottom, right, bottom - length, accentPaint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val clamped = if (alpha.isFinite()) alpha.coerceIn(0f, 1f) else 0f
        val combined = (((color ushr 24) and 0xFF) * clamped).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (combined shl 24)
    }

    override fun setAlpha(alpha: Int) {
        alphaScale = (alpha / 255f).coerceIn(0f, 1f)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun scaled(color: Int): Int {
        if (alphaScale >= 1f) return color
        val alpha = ((color ushr 24) and 0xFF) * alphaScale
        return (color and 0x00FFFFFF) or (alpha.toInt().coerceIn(0, 255) shl 24)
    }
}

/**
 * 主題專屬的錄音視覺化圖形：柔和脈動、環形音量刻度、訊號波。
 *
 * 只使用既有的音量資料（不建立第二個錄音程序），只畫在中央圓形自己的 bounds 內，
 * 因此不會遮擋取消、刪除、換行或工具列，也不影響任何量測結果。
 * 沒有內建計時器：只有音量更新時才重畫，靜止時完全不耗電。
 */
private class RecordingVisualizerDrawable(
    private val style: RecordingVisualStyle,
    private val accent: ImeColorSpec,
    private val segmentCount: Int,
    private val cycles: Int,
    private val provider: () -> ThemePalette
) : Drawable() {
    var level: Float = 0f
        set(value) {
            val bounded = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
            if (bounded == field) return
            field = bounded
            invalidateSelf()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val rect = bounds
        if (rect.isEmpty) return
        val color = accent(provider())
        val centreX = rect.exactCenterX()
        val centreY = rect.exactCenterY()
        val maxRadius = minOf(rect.width(), rect.height()) / 2f
        if (maxRadius <= 0f) return
        // 與設定頁預覽共用同一份繪製計畫，因此預覽與實機的圖形一致。
        val plan = RecordingVisualPlanner.planFor(style, level, segmentCount, cycles)
        paint.color = color
        plan.rings.forEach { ring ->
            paint.alpha = ring.alpha
            paint.strokeWidth = maxOf(2f, maxRadius * ring.strokeFraction)
            canvas.drawCircle(centreX, centreY, maxRadius * ring.radiusFraction, paint)
        }
        if (plan.ticks.isNotEmpty()) {
            val inner = maxRadius * plan.ticks.first().innerFraction
            val outer = maxRadius * plan.ticks.first().outerFraction
            paint.alpha = 255
            paint.strokeWidth = maxOf(2f, maxRadius * 0.075f)
            plan.ticks.forEach { tick ->
                val cos = kotlin.math.cos(tick.angleRadians)
                val sin = kotlin.math.sin(tick.angleRadians)
                canvas.drawLine(
                    centreX + cos * inner,
                    centreY + sin * inner,
                    centreX + cos * outer,
                    centreY + sin * outer,
                    paint
                )
            }
        }
        plan.wave?.let { wave ->
            paint.alpha = wave.alpha
            paint.strokeWidth = maxOf(2f, maxRadius * wave.strokeFraction)
            path.reset()
            wave.points.forEachIndexed { index, point ->
                val x = centreX + kotlin.math.cos(point.first) * maxRadius * point.second
                val y = centreY + kotlin.math.sin(point.first) * maxRadius * point.second
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
