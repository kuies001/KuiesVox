package tw.kuies.voiceime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

internal class VoiceImePanel(
    context: Context,
    onVoiceAction: () -> Unit,
    onCancel: () -> Unit,
    onSwitchInputMethod: () -> Unit
) {
    private val statusChip = LinearLayout(context)
    private val statusDot = View(context)
    private val statusLabel = textView(context, sizeSp = 12f, color = TEXT).apply {
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val switchButton = ImageButton(context)
    private val idleActions = LinearLayout(context)
    private val recordingActions = LinearLayout(context)
    private val busyActions = LinearLayout(context)
    private val terminalMessage = textView(context, sizeSp = 13f, color = TEXT_MUTED)
    private val previewSection = LinearLayout(context)
    private val previewText = textView(context, sizeSp = 13f, color = TEXT)
    private val busyLabel = textView(context, sizeSp = 13f, color = TEXT)

    val view: View

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            background = rounded(
                colors = intArrayOf(BACKGROUND, BACKGROUND),
                radius = dp(context, 24),
                strokeColor = OUTLINE,
                strokeWidth = dp(context, 1)
            )
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

        switchButton.apply {
            setImageResource(R.drawable.ic_ime_keyboard)
            imageTintList = ColorStateList.valueOf(TEXT_MUTED)
            contentDescription = "切換輸入法"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ripple(context, SURFACE_VARIANT, OUTLINE, dp(context, 50))
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
            setOnClickListener { onSwitchInputMethod() }
        }
        topRow.addView(
            switchButton,
            LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).apply {
                marginStart = dp(context, 8)
            }
        )
        root.addView(topRow)

        val actionCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            background = rounded(
                colors = intArrayOf(SURFACE, SURFACE_VARIANT),
                radius = dp(context, 22),
                strokeColor = OUTLINE,
                strokeWidth = dp(context, 1)
            )
        }
        root.addView(
            actionCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 9) }
        )

        idleActions.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(
                actionButton(
                    context = context,
                    icon = R.drawable.ic_ime_mic,
                    label = "開始語音輸入",
                    foregroundColor = BUTTON_TEXT,
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(LAVENDER, LAVENDER_BRIGHT)
                    ).apply { cornerRadius = dp(context, 50).toFloat() },
                    onClick = onVoiceAction
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(context, 48)
                )
            )
            val hint = textView(context, sizeSp = 12f, color = TEXT_MUTED).apply {
                text = "點一下開始說話"
                gravity = Gravity.CENTER
            }
            addView(
                hint,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(context, 7) }
            )
        }
        actionCard.addView(idleActions)

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
        actionCard.addView(recordingActions)

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
        actionCard.addView(busyActions)

        terminalMessage.apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            minHeight = dp(context, 38)
        }
        actionCard.addView(terminalMessage)

        previewSection.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 11), dp(context, 8), dp(context, 11), dp(context, 8))
            background = solid(SURFACE_VARIANT, dp(context, 15))
            val caption = textView(context, sizeSp = 10f, color = TEXT_MUTED).apply {
                text = "最近輸入"
                maxLines = 1
            }
            addView(caption)
            previewText.apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(
                previewText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(context, 2) }
            )
        }
        root.addView(
            previewSection,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }
        )

        view = root
        render(VoiceImeState.IDLE, null)
    }

    fun setSwitchAvailable(available: Boolean) {
        switchButton.visibility = if (available) View.VISIBLE else View.GONE
    }

    fun render(state: VoiceImeState, latestResult: String?) {
        val accent = accentFor(state)
        statusLabel.text = state.label
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

        idleActions.visibility = if (state == VoiceImeState.IDLE) View.VISIBLE else View.GONE
        recordingActions.visibility = if (state == VoiceImeState.RECORDING) View.VISIBLE else View.GONE
        val isBusy = state == VoiceImeState.TRANSCRIBING || state == VoiceImeState.FORMATTING
        busyActions.visibility = if (isBusy) View.VISIBLE else View.GONE
        busyLabel.text = state.label

        val isTerminal = state == VoiceImeState.SUCCESS || state == VoiceImeState.FORMATTING_FALLBACK ||
            state == VoiceImeState.CANCELLED || state == VoiceImeState.ERROR
        terminalMessage.visibility = if (isTerminal) View.VISIBLE else View.GONE
        terminalMessage.text = when (state) {
            VoiceImeState.SUCCESS -> "文字已輸入"
            VoiceImeState.FORMATTING_FALLBACK -> "已輸入未整理的文字"
            VoiceImeState.CANCELLED -> "本次操作已取消"
            VoiceImeState.ERROR -> "語音輸入未完成，請稍後再試"
            else -> ""
        }
        terminalMessage.setTextColor(accent)

        previewSection.visibility = if (latestResult.isNullOrBlank()) View.GONE else View.VISIBLE
        previewText.text = latestResult.orEmpty()
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
    }
}
