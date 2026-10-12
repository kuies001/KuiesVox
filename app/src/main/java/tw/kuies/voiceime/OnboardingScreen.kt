package tw.kuies.voiceime

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** 導覽中會用到的外部連結。 */
internal object OnboardingLinks {
    const val GROQ_API_KEYS = "https://console.groq.com/keys"
}

/** 三頁導覽的流程規則（純邏輯，可在 JVM 測試中驗證）。 */
internal object OnboardingFlow {
    const val PAGE_COUNT = 3
    const val MICROPHONE_PAGE = 0
    const val GROQ_PAGE = 1
    const val IME_PAGE = 2

    fun pageCount(): Int = PAGE_COUNT

    fun isLastPage(page: Int): Boolean = page >= PAGE_COUNT - 1

    fun canGoBack(page: Int): Boolean = page > 0

    /** 第 1、2 頁可以略過；最後一頁改由「開始使用」完成，不再顯示略過。 */
    fun showsSkip(page: Int): Boolean = !isLastPage(page)

    fun primaryLabel(page: Int): String = if (isLastPage(page)) "開始使用" else "下一步"

    fun nextPage(page: Int): Int = (page + 1).coerceAtMost(PAGE_COUNT - 1)

    fun previousPage(page: Int): Int = (page - 1).coerceAtLeast(0)
}

/** 開啟外部設定的抽象；讓跳轉順序與 fallback 可在 JVM 測試中驗證。 */
internal interface SettingsLauncher {
    /** 嘗試開啟指定 action：成功回傳 true，無法開啟回傳 false，且不得拋出例外。 */
    fun open(action: String): Boolean
}

/**
 * 開啟輸入法設定：先試 Android 官方的輸入法設定頁，其次退回系統設定首頁。
 *
 * 全部候選都開不起來時回傳 false，由呼叫端提示使用者手動前往（不會閃退）。
 */
internal object ImeSettingsLauncher {
    val CANDIDATE_ACTIONS = listOf(
        Settings.ACTION_INPUT_METHOD_SETTINGS,
        Settings.ACTION_SETTINGS
    )

    const val MANUAL_HINT = "無法自動開啟系統設定，請手動前往「設定 → 一般管理 → 鍵盤」。"

    fun open(launcher: SettingsLauncher): Boolean =
        CANDIDATE_ACTIONS.any { action -> launcher.open(action) }
}

/**
 * 首次安裝的三頁導覽。
 *
 * 只是 MainActivity 的前置畫面：不碰 IME 面板，也不要求使用者當場輸入 API Key；
 * 任何一頁都可以直接略過或下一步，不會強制完成三個步驟。
 */
@Composable
internal fun OnboardingScreen(
    modifier: Modifier = Modifier,
    step: OnboardingStep,
    hasMicrophonePermission: Boolean,
    imeEnablement: ImeEnablement,
    imeSettingsHint: String,
    onStepChange: (OnboardingStep) -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onOpenGroq: () -> Unit,
    onOpenApiKeySettings: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onSwitchToKuiesVox: () -> Unit,
    onFinish: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val pagerState = rememberPagerState { OnboardingFlow.pageCount() }
    val page = step.index

    // 導覽狀態驅動頁面（例如從 Groq 設定儲存成功返回時要直接停在第三頁）。
    LaunchedEffect(page) { pagerState.animateScrollToPage(page) }
    // 使用者滑動時把結果回報給導覽狀態，兩邊保持同一個來源。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            val target = OnboardingStep.fromIndex(settled)
            if (target != step) onStepChange(target)
        }
    }

    fun goTo(target: Int) {
        onStepChange(OnboardingStep.fromIndex(target))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Text(
            "新手導覽",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { index ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (index) {
                    OnboardingFlow.MICROPHONE_PAGE -> MicrophoneOnboardingPage(
                        granted = hasMicrophonePermission,
                        onRequestPermission = onRequestMicrophonePermission
                    )

                    OnboardingFlow.GROQ_PAGE -> GroqOnboardingPage(
                        onOpenGroq = onOpenGroq,
                        onOpenApiKeySettings = onOpenApiKeySettings,
                        onContinue = { goTo(OnboardingFlow.nextPage(index)) }
                    )

                    else -> ImeOnboardingPage(
                        imeEnablement = imeEnablement,
                        hint = imeSettingsHint,
                        onOpenImeSettings = onOpenImeSettings,
                        onSwitchToKuiesVox = onSwitchToKuiesVox
                    )
                }
            }
        }
        OnboardingPageIndicator(pageCount = OnboardingFlow.pageCount(), currentPage = page)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (OnboardingFlow.showsSkip(page)) {
                TextButton(onClick = onFinish) { Text("略過") }
            }
            Spacer(Modifier.weight(1f))
            if (OnboardingFlow.canGoBack(page)) {
                OutlinedButton(
                    onClick = { goTo(OnboardingFlow.previousPage(page)) },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("上一步") }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = {
                    if (OnboardingFlow.isLastPage(page)) {
                        onFinish()
                    } else {
                        goTo(OnboardingFlow.nextPage(page))
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text(OnboardingFlow.primaryLabel(page)) }
        }
    }
}

@Composable
private fun MicrophoneOnboardingPage(
    granted: Boolean,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_home_voice),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
    OnboardingPageTexts(
        title = "開啟麥克風權限",
        body = "讓 KuiesVox 聽見你的聲音，幫你轉成文字。"
    )
    Button(
        onClick = onRequestPermission,
        enabled = !granted,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (granted) "已授權" else "授權麥克風") }
    OnboardingHint(
        if (granted) {
            "已取得麥克風權限，可以開始使用語音輸入。"
        } else {
            "沒有授權也能繼續；之後可到系統設定的應用程式權限中開啟麥克風。"
        }
    )
}

@Composable
private fun GroqOnboardingPage(
    onOpenGroq: () -> Unit,
    onOpenApiKeySettings: () -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_home_groq),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
    OnboardingPageTexts(
        title = "申請 Groq API",
        body = "KuiesVox 使用 Groq Whisper 進行語音辨識。你可以先申請 API Key，之後再回來設定。"
    )
    GroqStepsCard()
    Button(
        onClick = onOpenGroq,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Text("前往 Groq") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onOpenApiKeySettings,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f)
        ) { Text("前往 API 設定", maxLines = 1) }
        OutlinedButton(
            onClick = onContinue,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f)
        ) { Text("稍後設定", maxLines = 1) }
    }
    OnboardingHint("申請完成後回到 KuiesVox，在「Groq」頁面貼上 API Key 即可。")
}

@Composable
private fun ImeOnboardingPage(
    imeEnablement: ImeEnablement,
    hint: String,
    onOpenImeSettings: () -> Unit,
    onSwitchToKuiesVox: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // 以官方 API 查到的狀態決定文案與動作，不以「設定頁是否開啟成功」推測。
    val statusText = when (imeEnablement) {
        ImeEnablement.NOT_ENABLED -> "尚未啟用 KuiesVox"
        ImeEnablement.ENABLED -> "KuiesVox 已啟用"
        ImeEnablement.DEFAULT -> "KuiesVox 已啟用並設為預設輸入法"
    }
    val statusColor = if (imeEnablement == ImeEnablement.NOT_ENABLED) colors.onSurfaceVariant else colors.primary
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ime_keyboard),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
    OnboardingPageTexts(
        title = "開啟 KuiesVox 輸入法",
        body = "到手機設定中開啟 KuiesVox，就可以開始使用。"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor
        )
        if (imeEnablement != ImeEnablement.NOT_ENABLED) {
            Text("✔", style = MaterialTheme.typography.titleMedium, color = statusColor)
        }
    }
    when (imeEnablement) {
        ImeEnablement.NOT_ENABLED -> Button(
            onClick = onOpenImeSettings,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("前往輸入法設定") }

        ImeEnablement.ENABLED -> Button(
            onClick = onSwitchToKuiesVox,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("切換至 KuiesVox") }

        // 已設為預設：顯示完成狀態即可，不需要再按任何按鈕。
        ImeEnablement.DEFAULT -> Text(
            "已完成，隨時可以在輸入欄位使用語音輸入。",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
    ImeStepsCard(hint)
    OnboardingHint("啟用後在任一輸入欄位切換到 KuiesVox，就能用語音輸入。")
}

/** 第 2 頁的步驟圖卡：用 Compose 畫出四個編號步驟，文具小卡片風格。 */
@Composable
private fun GroqStepsCard() {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                "1" to "前往 Groq",
                "2" to "註冊或登入",
                "3" to "建立 API Key",
                "4" to "回到 KuiesVox 設定"
            ).forEach { (number, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            number,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            }
        }
    }
}

/** 第 3 頁的手機設定說明小卡片。 */
@Composable
private fun ImeStepsCard(hint: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "三星裝置路徑：",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary
            )
            Text(
                "設定 → 一般管理 → 鍵盤清單與預設值",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageTexts(title: String, body: String) {
    val colors = MaterialTheme.colorScheme
    Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
}

@Composable
private fun OnboardingHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OnboardingPageIndicator(pageCount: Int, currentPage: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (selected) 20.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.outlineVariant)
            )
        }
    }
}
