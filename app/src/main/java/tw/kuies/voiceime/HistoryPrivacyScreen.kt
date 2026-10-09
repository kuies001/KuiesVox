package tw.kuies.voiceime

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
internal fun HistoryPrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(HistorySettings()) }
    var loaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadHistorySettings(context).onSuccess { settings = it }
        loaded = true
    }

    fun applyToggle(
        optimistic: HistorySettings,
        persist: suspend () -> Result<HistorySettings>
    ) {
        settings = optimistic
        errorMessage = null
        scope.launch {
            persist()
                .onSuccess { settings = it }
                .onFailure {
                    // 儲存失敗時回復成實際持久化的狀態，不讓 UI 顯示未生效的設定。
                    errorMessage = "設定儲存失敗，已回復原有設定。"
                    loadHistorySettings(context).onSuccess { settings = it }
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("隱私與歷史紀錄", style = MaterialTheme.typography.headlineSmall)
        Text(
            "關閉後停止新增歷史紀錄，既有紀錄不會自動刪除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HistoryToggleRow(
            title = "儲存語音辨識歷史",
            description = "語音輸入完成後，將辨識結果加入語音辨識歷史。",
            checked = settings.voiceHistoryEnabled,
            enabled = loaded,
            onCheckedChange = { enabled ->
                applyToggle(settings.withVoiceHistoryEnabled(enabled)) {
                    setVoiceHistoryEnabled(context, enabled)
                }
            }
        )
        HistoryToggleRow(
            title = "儲存剪貼簿歷史",
            description = "複製文字時，將內容加入剪貼簿歷史。",
            checked = settings.clipboardHistoryEnabled,
            enabled = loaded,
            onCheckedChange = { enabled ->
                applyToggle(settings.withClipboardHistoryEnabled(enabled)) {
                    setClipboardHistoryEnabled(context, enabled)
                }
            }
        )
        errorMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            "密碼、驗證碼等敏感欄位永遠不會被記錄。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 儲存作業在 AppStorageExecutor 上執行，這裡把結果帶回 Compose 的 Main 執行緒。 */
private suspend fun loadHistorySettings(context: Context): Result<HistorySettings> =
    suspendCancellableCoroutine { continuation ->
        HistorySettingsRepository.load(context) { continuation.resume(it) }
    }

private suspend fun setVoiceHistoryEnabled(
    context: Context,
    enabled: Boolean
): Result<HistorySettings> = suspendCancellableCoroutine { continuation ->
    HistorySettingsRepository.setVoiceHistoryEnabled(context, enabled) { continuation.resume(it) }
}

private suspend fun setClipboardHistoryEnabled(
    context: Context,
    enabled: Boolean
): Result<HistorySettings> = suspendCancellableCoroutine { continuation ->
    HistorySettingsRepository.setClipboardHistoryEnabled(context, enabled) { continuation.resume(it) }
}

@Composable
private fun HistoryToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
