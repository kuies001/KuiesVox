package tw.kuies.voiceime

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 外觀 → 主題 設定畫面。
 *
 * 點選主題或外觀模式即時套用並儲存（沒有額外的儲存按鈕）。所有預覽色都取自
 * [ThemePreviewSpec]，也就是與實際畫面相同的 Theme Tokens，因此預覽與套用結果一致。
 */
@Composable
internal fun ThemeAppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    // 預覽的模擬錄音動態只在系統允許動畫時播放；關閉動畫時顯示靜態示意圖。
    val previewAnimationsEnabled = remember {
        runCatching { android.animation.ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
    }
    var selection by remember { mutableStateOf(ThemePreferencesRepository.cachedSync(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val unregister = ThemePreferencesRepository.registerListener(context) { updated ->
            selection = updated
        }
        onDispose { unregister() }
    }

    fun applyTheme(themeId: AppThemeId) {
        val previous = selection
        selection = previous.copy(themeId = themeId)
        errorMessage = null
        scope.launch {
            setThemeId(context, themeId)
                .onFailure {
                    errorMessage = "主題儲存失敗，已回復原有設定。"
                    selection = previous
                }
        }
    }

    fun applyAppearance(appearance: ThemeAppearance) {
        val previous = selection
        selection = previous.copy(appearance = appearance)
        errorMessage = null
        scope.launch {
            setAppearance(context, appearance)
                .onFailure {
                    errorMessage = "外觀模式儲存失敗，已回復原有設定。"
                    selection = previous
                }
        }
    }

    val newThemes = listOf(
        AppThemeId.CUTE_CREAM,
        AppThemeId.INDUSTRIAL_LIGHT,
        AppThemeId.BRIGHT_SCIFI,
        AppThemeId.DAILY_LIVING
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("外觀", style = MaterialTheme.typography.headlineSmall)
        Text(
            "主題會同時套用到 App 設定畫面與輸入法面板；選擇後立即生效並記住。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("外觀模式", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeAppearance.entries.forEach { appearance ->
                FilterChip(
                    selected = selection.appearance == appearance,
                    onClick = { applyAppearance(appearance) },
                    label = { Text(appearance.displayName) }
                )
            }
        }
        Text(
            when (selection.appearance) {
                ThemeAppearance.SYSTEM -> "目前跟隨 Android 系統的深淺色設定。"
                ThemeAppearance.LIGHT -> "目前固定使用淺色外觀。"
                ThemeAppearance.DARK -> "目前固定使用深色外觀。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("主題", style = MaterialTheme.typography.titleMedium)
        newThemes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { themeId ->
                    ThemePreviewCard(
                        themeId = themeId,
                        appearance = selection.appearance,
                        systemDark = systemDark,
                        selected = selection.themeId == themeId,
                        animate = previewAnimationsEnabled,
                        onClick = { applyTheme(themeId) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 奇數時補一個等寬空白，維持雙欄對齊。
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }

        Text("相容選項", style = MaterialTheme.typography.titleMedium)
        ThemePreviewCard(
            themeId = AppThemeId.LEGACY_WARM,
            appearance = selection.appearance,
            systemDark = systemDark,
            selected = selection.themeId == AppThemeId.LEGACY_WARM,
            animate = previewAnimationsEnabled,
            onClick = { applyTheme(AppThemeId.LEGACY_WARM) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Warm & Cozy 保留原本的奶茶暖色外觀；輸入法面板在相容模式下固定維持原樣。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        errorMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ThemePreviewCard(
    themeId: AppThemeId,
    appearance: ThemeAppearance,
    systemDark: Boolean,
    selected: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = ThemePreview.specFor(themeId, appearance, systemDark)
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .border(border, MaterialTheme.shapes.medium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    themeId.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selected) {
                    Text(
                        "使用中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            ImePanelPreview(spec, animate = animate)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                spec.swatches.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
            }
            Text(
                themeId.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 縮小版輸入法面板示意。
 *
 * 底色、工具列、中央錄音按鈕的顏色都來自 Theme Tokens；按鈕造型、邊框與錄音視覺化則
 * 來自同一份 Theme Visual Spec，因此預覽與實際套用後的樣子一致。
 * 這裡完全不會啟動麥克風（音量是模擬值），且示意圖不可操作。
 */
@Composable
private fun ImePanelPreview(spec: ThemePreviewSpec, animate: Boolean) {
    val visual = spec.visualSpec
    val level = simulatedRecordingLevel(animate)
    val accent = Color(spec.recorderAccent)
    val frameColor = Color(spec.toolbarIcon)
    val shape = previewShape(visual.buttonStyle)
    val centreScale = 1f + (visual.animationStyle.maxScale - 1f) * level

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(spec.imeBackground))
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(shape)
                    .background(Color(spec.toolbarBackground))
                    .previewFrame(visual, frameColor, shape)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(previewShape(visual.buttonStyle))
                            .background(previewChipFill(spec))
                    )
                }
            }
            Box(contentAlignment = Alignment.Center) {
                // 錄音視覺化：與 IME 使用同一份繪製計畫與同一組幾何常數。
                Canvas(modifier = Modifier.size(42.dp).scale(centreScale)) {
                    previewRecordingVisual(spec, level, accent)
                }
                Box(
                    modifier = Modifier
                        // 主按鈕視覺圓半徑與 IME 相同（共用常數），外圈留給刻度／電波。
                        .size((42 * RecordingVisualPlanner.BUTTON_SURFACE_RADIUS_FRACTION).dp)
                        .scale(centreScale)
                        .clip(CircleShape)
                        .background(Color(spec.recorderBackground))
                        .previewFrame(visual, accent, CircleShape)
                        .then(
                            if (Color(spec.recordingHalo).alpha > 0f) {
                                Modifier.border(3.dp, Color(spec.recordingHalo), CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ime_mic),
                        contentDescription = null,
                        tint = Color(spec.recorderForeground),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/** 預覽用的模擬音量；系統關閉動畫時給固定值，避免任何多餘更新。 */
@Composable
private fun simulatedRecordingLevel(animate: Boolean): Float {
    if (!animate) return 0.85f
    val transition = rememberInfiniteTransition(label = "previewRecordingLevel")
    val level by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "previewLevel"
    )
    return level
}

/** 依 ButtonStyle 決定預覽的圓角（只影響預覽卡，不影響實機）。 */
private fun previewShape(buttonStyle: ButtonStyle) = when (buttonStyle) {
    ButtonStyle.SOFT_PILL -> RoundedCornerShape(50)
    ButtonStyle.HARD_GEOMETRIC -> RoundedCornerShape(4.dp)
    ButtonStyle.HUD_OUTLINE -> RoundedCornerShape(9.dp)
    ButtonStyle.SOFT_SUBTLE -> RoundedCornerShape(11.dp)
    ButtonStyle.NEUTRAL -> RoundedCornerShape(10.dp)
}

/** 依 BorderStyle 決定預覽的邊框；NEUTRAL（Legacy）不加任何外框。 */
@Composable
private fun Modifier.previewFrame(
    visual: ThemeVisualSpec,
    color: Color,
    shape: Shape
): Modifier {
    if (visual.containerBorderStyle == BorderStyle.NEUTRAL || visual.borderWidthDp <= 0f) return this
    val width = when (visual.containerBorderStyle) {
        BorderStyle.HAIRLINE, BorderStyle.SUBTLE_LINE -> 0.8.dp
        BorderStyle.HUD_CORNERS -> 0.9.dp
        BorderStyle.FUNCTIONAL_FRAME -> 1.6.dp
        else -> 1.dp
    }
    return border(width, color.copy(alpha = 0.55f), shape)
}

/** 依 ButtonStyle 決定工具列晶片的底色；與 IME 共用同一個配方，且**不含外框**。 */
private fun previewChipFill(spec: ThemePreviewSpec): Color =
    spec.toolbarChipFill?.let { Color(it) } ?: Color(spec.toolbarIcon).copy(alpha = 0.18f)

/** 預覽的錄音視覺化圖形；由與 IME 相同的繪製計畫產生，因此兩邊圖形一致。 */
private fun DrawScope.previewRecordingVisual(spec: ThemePreviewSpec, level: Float, accent: Color) {
    val visual = spec.visualSpec
    val plan = RecordingVisualPlanner.planFor(
        style = visual.recordingVisualStyle,
        level = level,
        segmentCount = visual.meterSegmentCount,
        cycles = visual.animationStyle.pulseCycles
    )
    val centre = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val maxRadius = size.minDimension / 2f
    plan.rings.forEach { ring ->
        drawCircle(
            color = accent.copy(alpha = ring.alpha / 255f),
            radius = maxRadius * ring.radiusFraction,
            center = centre,
            style = Stroke(width = maxRadius * ring.strokeFraction)
        )
    }
    if (plan.ticks.isNotEmpty()) {
        val inner = maxRadius * plan.ticks.first().innerFraction
        val outer = maxRadius * plan.ticks.first().outerFraction
        plan.ticks.forEach { tick ->
            val cos = kotlin.math.cos(tick.angleRadians)
            val sin = kotlin.math.sin(tick.angleRadians)
            drawLine(
                color = accent,
                start = androidx.compose.ui.geometry.Offset(centre.x + cos * inner, centre.y + sin * inner),
                end = androidx.compose.ui.geometry.Offset(centre.x + cos * outer, centre.y + sin * outer),
                strokeWidth = maxRadius * 0.075f,
                cap = StrokeCap.Round
            )
        }
    }
    plan.wave?.let { wave ->
        val path = Path()
        wave.points.forEachIndexed { index, point ->
            val x = centre.x + kotlin.math.cos(point.first) * maxRadius * point.second
            val y = centre.y + kotlin.math.sin(point.first) * maxRadius * point.second
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path,
            color = accent.copy(alpha = wave.alpha / 255f),
            style = Stroke(width = maxRadius * wave.strokeFraction)
        )
    }
}

private suspend fun setThemeId(context: Context, themeId: AppThemeId): Result<Unit> =
    suspendCancellableCoroutine { continuation ->
        ThemePreferencesRepository.setThemeId(context, themeId) { result ->
            continuation.resume(result.map { })
        }
    }

private suspend fun setAppearance(context: Context, appearance: ThemeAppearance): Result<Unit> =
    suspendCancellableCoroutine { continuation ->
        ThemePreferencesRepository.setAppearance(context, appearance) { result ->
            continuation.resume(result.map { })
        }
    }
