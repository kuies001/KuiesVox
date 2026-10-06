package tw.kuies.voiceime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun SmartFormattingSettingsScreen(
    modifier: Modifier = Modifier,
    settings: SmartFormattingSettings,
    loaded: Boolean,
    status: String,
    onSave: (SmartFormattingSettings) -> Unit,
    onBack: () -> Unit
) {
    var enabled by remember(settings.enabled) { mutableStateOf(settings.enabled) }
    var thresholdText by remember(settings.threshold) { mutableStateOf(settings.threshold.toString()) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    val threshold = thresholdText.toIntOrNull()
    val thresholdIsValid = threshold != null && threshold in
        SmartFormattingSettings.MIN_THRESHOLD..SmartFormattingSettings.MAX_THRESHOLD
    val modelIsValid = model.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("智慧整理", style = MaterialTheme.typography.headlineSmall)
        Text(
            "長逐字稿會在 Whisper 與本地修正後，交由 Groq 進行語意校對與排版。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("智慧整理", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "已啟用" else "已停用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = loaded)
        }

        OutlinedTextField(
            value = thresholdText,
            onValueChange = { value ->
                if (value.length <= 3 && value.all { character -> character.isDigit() }) {
                    thresholdText = value
                }
            },
            label = { Text("短文字門檻") },
            supportingText = {
                Text(
                    if (thresholdIsValid) {
                        "0～500 個 Unicode 字元；0 代表所有非空文字都整理。"
                    } else {
                        "請輸入 0～500 的整數。"
                    }
                )
            },
            isError = !thresholdIsValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = loaded,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("文字整理模型") },
            supportingText = { Text("Groq model ID，可依可用模型手動調整。") },
            singleLine = true,
            enabled = loaded,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSave(
                    SmartFormattingSettings(
                        enabled = enabled,
                        threshold = threshold ?: SmartFormattingSettings.DEFAULT_THRESHOLD,
                        model = model
                    )
                )
            },
            enabled = loaded && thresholdIsValid && modelIsValid,
            shape = RoundedCornerShape(50),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("儲存設定")
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "門檻以 trim 前後空白後的 Unicode code points 計算。短文字直接使用本地修正結果；較長文字才呼叫 Groq。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
