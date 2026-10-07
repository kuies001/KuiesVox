package tw.kuies.voiceime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal const val VOICE_IME_MAIN_INTERACTION_HEIGHT_DP = 130

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
    onClearAll: () -> Unit = {}
) {
    private val statusChip = LinearLayout(context)
    private val statusDot = View(context)
    internal val statusLabel = textView(context, sizeSp = 12f, color = TEXT).apply {
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    internal val switchButton = ImageButton(context)
    internal val settingsButton = ImageButton(context)
    internal val backspaceButton = ImageButton(context)
    internal val enterButton = ImageButton(context)
    internal val bulkActionsContainer = FrameLayout(context)
    internal val bulkActionsRow = LinearLayout(context)
    internal val clearConfirmationPanel = LinearLayout(context)
    internal val selectAllButton = textView(context, sizeSp = 13f, color = TEXT)
    internal val clearAllButton = textView(context, sizeSp = 13f, color = PINK)
    internal val cancelClearAllButton = textView(context, sizeSp = 13f, color = TEXT_MUTED)
    internal val confirmClearAllButton = textView(context, sizeSp = 13f, color = PINK)
    internal val mainInteractionContainer = FrameLayout(context)
    internal val idleActions = LinearLayout(context)
    internal val idleMicButton = ImageButton(context)
    internal val idleTitle = textView(context, sizeSp = 15f, color = TEXT).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    internal val idleHint = textView(context, sizeSp = 11f, color = TEXT_MUTED)
    internal val recordingActions = LinearLayout(context)
    internal val busyActions = LinearLayout(context)
    internal val previewText = textView(
        context,
        sizeSp = VoiceImePreviewLayout.TEXT_SIZE_SP,
        color = PREVIEW_TEXT
    ).apply {
        maxLines = VoiceImePreviewLayout.MAX_LINES
        ellipsize = if (VoiceImePreviewLayout.ELLIPSIZE_AT_END) {
            android.text.TextUtils.TruncateAt.END
        } else {
            null
        }
    }
    internal val previewDivider = View(context).apply {
        setBackgroundColor(Color.rgb(43, 54, 71))
    }
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

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusChip.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 11), dp(context, 8), dp(context, 12), dp(context, 8))
            addView(statusDot, LinearLayout.LayoutParams(dp(context, 8), dp(context, 8)))
            addView(
                statusLabel,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(context, 7)
                }
            )
        }
        topRow.addView(statusChip, LinearLayout.LayoutParams(0, dp(context, 36), 1f))

        settingsButton.apply {
            setImageResource(R.drawable.ic_ime_settings)
            imageTintList = ColorStateList.valueOf(TEXT_MUTED)
            contentDescription = "Open KuiesVox settings"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
            setOnClickListener { onOpenSettings() }
        }
        topRow.addView(
            settingsButton,
            LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)).apply {
                marginEnd = dp(context, 6)
            }
        )

        var backspacePointerActive = false
        var backspaceClickAllowed = false
        backspaceButton.apply {
            setImageResource(R.drawable.ic_ime_backspace)
            imageTintList = ColorStateList.valueOf(blend(TEXT_MUTED, PINK, 0.45f))
            contentDescription = "退格 / 刪除"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
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
        topRow.addView(
            backspaceButton,
            LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)).apply {
                marginEnd = dp(context, 8)
            }
        )

        enterButton.apply {
            setImageResource(R.drawable.ic_ime_enter)
            imageTintList = ColorStateList.valueOf(blend(TEXT_MUTED, LAVENDER, 0.5f))
            contentDescription = "Enter / 換行"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
            setOnClickListener { onEnter() }
        }
        topRow.addView(
            enterButton,
            LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)).apply {
                marginEnd = dp(context, 8)
            }
        )

        switchButton.apply {
            setImageResource(R.drawable.ic_ime_keyboard)
            imageTintList = ColorStateList.valueOf(TEXT_MUTED)
            contentDescription = "切換輸入法"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
            setOnClickListener { onSwitchInputMethod() }
        }
        topRow.addView(
            switchButton,
            LinearLayout.LayoutParams(dp(context, 36), dp(context, 36))
        )
        root.addView(topRow)

        bulkActionsRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                secondaryActionButton(context, selectAllButton, "全選", TEXT) {
                    clearConfirmationPanel.visibility = View.GONE
                    bulkActionsRow.visibility = View.VISIBLE
                    onSelectAll()
                },
                LinearLayout.LayoutParams(0, dp(context, 34), 1f).apply {
                    marginEnd = dp(context, 6)
                }
            )
            addView(
                secondaryActionButton(context, clearAllButton, "清除全部", PINK) {
                    bulkActionsRow.visibility = View.GONE
                    clearConfirmationPanel.visibility = View.VISIBLE
                },
                LinearLayout.LayoutParams(
                    0,
                    dp(context, 34),
                    1f
                )
            )
        }
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
                    bulkActionsRow.visibility = View.VISIBLE
                },
                LinearLayout.LayoutParams(dp(context, 54), dp(context, 30)).apply {
                    marginEnd = dp(context, 6)
                }
            )
            addView(
                secondaryActionButton(context, confirmClearAllButton, "清除", PINK) {
                    clearConfirmationPanel.visibility = View.GONE
                    bulkActionsRow.visibility = View.VISIBLE
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
        bulkActionsContainer.apply {
            addView(
                bulkActionsRow,
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
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        root.addView(
            bulkActionsContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 36)
            ).apply { topMargin = dp(context, 2) }
        )

        root.addView(
            previewText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 4) }
        )
        root.addView(
            previewDivider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VoiceImePreviewLayout.DIVIDER_HEIGHT_DP)
            ).apply { topMargin = dp(context, 5) }
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
        mainInteractionContainer.addView(
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
        mainInteractionContainer.addView(
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
        mainInteractionContainer.addView(
            busyActions,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        root.addView(
            mainInteractionContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, VOICE_IME_MAIN_INTERACTION_HEIGHT_DP)
            ).apply { topMargin = dp(context, 6) }
        )

        view = root
        render(VoiceImeState.IDLE, null)
    }

    fun setSwitchAvailable(available: Boolean) {
        switchButton.visibility = if (available) View.VISIBLE else View.GONE
    }

    fun render(state: VoiceImeState, latestResult: String?, statusLabelOverride: String? = null) {
        val accent = accentFor(state)
        statusLabel.text = statusLabelOverride ?: state.label
        statusLabel.setTextColor(accent)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent)
        }
        statusChip.background = rounded(
            colors = intArrayOf(blend(SURFACE_VARIANT, accent, 0.18f), SURFACE_VARIANT),
            radius = dp(statusChip.context, 50),
            strokeColor = blend(OUTLINE, accent, 0.35f),
            strokeWidth = dp(statusChip.context, 1)
        )

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
        if (!bulkActionsEnabled) {
            clearConfirmationPanel.visibility = View.GONE
            bulkActionsRow.visibility = View.VISIBLE
        }
        idleActions.visibility = if (state == VoiceImeState.IDLE || isTerminal) {
            View.VISIBLE
        } else {
            View.GONE
        }
        recordingActions.visibility = if (state == VoiceImeState.RECORDING) View.VISIBLE else View.GONE
        val isBusy = state == VoiceImeState.TRANSCRIBING || state == VoiceImeState.FORMATTING
        busyActions.visibility = if (isBusy) View.VISIBLE else View.GONE
        busyLabel.text = state.label

        val hasPreview = VoiceImePreviewLayout.shouldShowPreview(latestResult)
        previewText.visibility = if (hasPreview) View.VISIBLE else View.GONE
        previewDivider.visibility = if (hasPreview) View.VISIBLE else View.GONE
        previewText.text = latestResult?.let { "最近：$it" }.orEmpty()
        val isActive = state == VoiceImeState.RECORDING || isBusy
        previewText.setTextColor(if (isActive) blend(PREVIEW_TEXT, BACKGROUND, 0.12f) else PREVIEW_TEXT)
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

    private fun accentFor(state: VoiceImeState): Int = when (state) {
        VoiceImeState.IDLE -> TEXT_MUTED
        VoiceImeState.RECORDING -> CORAL
        VoiceImeState.TRANSCRIBING -> BLUE
        VoiceImeState.FORMATTING -> LAVENDER_BRIGHT
        VoiceImeState.SUCCESS -> MINT
        VoiceImeState.FORMATTING_FALLBACK -> PINK
        VoiceImeState.CANCELLED -> TEXT_MUTED
        VoiceImeState.ERROR -> ERROR
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
        val PINK = Color.rgb(240, 187, 212)
        val CORAL = Color.rgb(232, 153, 161)
        val BLUE = Color.rgb(158, 187, 255)
        val ERROR = Color.rgb(255, 142, 142)
        val BUTTON_TEXT = Color.rgb(37, 35, 66)
        val TEXT = Color.rgb(230, 234, 243)
        val TEXT_MUTED = Color.rgb(180, 190, 206)
        val PREVIEW_TEXT = Color.rgb(137, 151, 173)
    }
}
