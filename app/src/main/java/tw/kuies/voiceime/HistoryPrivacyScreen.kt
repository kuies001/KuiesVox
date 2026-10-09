package tw.kuies.voiceime

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryPrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(HistorySettings()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HistorySettingsRepository.load(context) { result ->
            result.onSuccess { settings = it }
            loaded = true
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
                val updated = settings.copy(voiceHistoryEnabled = enabled)
                settings = updated
                HistorySettingsRepository.saveAsync(context, updated)
            }
        )
        HistoryToggleRow(
            title = "儲存剪貼簿歷史",
            description = "複製文字時，將內容加入剪貼簿歷史。",
            checked = settings.clipboardHistoryEnabled,
            enabled = loaded,
            onCheckedChange = { enabled ->
                val updated = settings.copy(clipboardHistoryEnabled = enabled)
                settings = updated
                HistorySettingsRepository.saveAsync(context, updated)
            }
        )
        Text(
            "密碼、驗證碼等敏感欄位永遠不會被記錄。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
