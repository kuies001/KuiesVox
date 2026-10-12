package tw.kuies.voiceime

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
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
            ImePanelPreview(spec)
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

/** 縮小版輸入法面板示意：底色、工具列與中央錄音按鈕全部取自 Theme Tokens。 */
@Composable
private fun ImePanelPreview(spec: ThemePreviewSpec) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .background(Color(spec.imeBackground))
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color(spec.toolbarBackground))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(spec.toolbarIcon))
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(spec.recorderBackground))
                    .then(
                        // 只在主題有定義光暈時畫一圈外圈；Legacy 為全透明，因此不會出現。
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
                    modifier = Modifier.size(16.dp)
                )
            }
        }
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
