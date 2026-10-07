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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun McpSettingsScreen(
    modifier: Modifier = Modifier,
    config: McpConfig,
    loaded: Boolean,
    connectionStatus: String,
    statusMessage: String,
    busy: Boolean,
    onConfigChange: (McpConfig, String) -> Unit,
    onSave: (McpConfig, String) -> Unit,
    onTestConnection: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("MCP Context", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("啟用 MCP", modifier = Modifier.weight(1f))
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    onSave(config.copy(enabled = enabled), "MCP 設定已儲存。")
                },
                enabled = loaded && !busy
            )
        }
        OutlinedTextField(
            value = config.serverUrl,
            onValueChange = { value ->
                val changedUrl = value != config.serverUrl
                val updated = if (changedUrl) {
                    config.copy(
                        serverUrl = value,
                        serverName = "",
                        serverVersion = "",
                        protocolVersion = "",
                        resourcesSupported = false,
                        promptsSupported = false,
                        resources = emptyList(),
                        prompts = emptyList(),
                        selectedResourceUris = emptySet(),
                        selectedPromptNames = emptySet()
                    )
                } else {
                    config.copy(serverUrl = value)
                }
                onConfigChange(updated, if (value.isBlank()) "未設定" else "尚未測試連線")
            },
            label = { Text("Server URL") },
            singleLine = true,
            enabled = loaded && !busy,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "建議使用 HTTPS。HTTP 僅允許 localhost、127.0.0.1 或 ::1 的本機開發連線。Bearer Token 不會透過 HTTP 傳送；如需驗證請改用 HTTPS。",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = config.bearerToken,
            onValueChange = { value ->
                onConfigChange(
                    config.copy(bearerToken = value),
                    if (config.serverUrl.isBlank()) "未設定" else "尚未測試連線"
                )
            },
            label = { Text("Bearer Token（選填）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = loaded && !busy,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Token 以 Android Keystore 加密儲存，不會備份或輸出至 Logcat。", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { onSave(config, "MCP 設定已儲存。") },
            enabled = loaded && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("儲存 MCP 設定")
        }
        Button(
            onClick = onTestConnection,
            enabled = loaded && !busy && McpUrlValidator.isValid(config.serverUrl, config.bearerToken),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "連線中…" else "測試連線")
        }
        Button(
            onClick = onRefresh,
            enabled = loaded && !busy && McpUrlValidator.isValid(config.serverUrl, config.bearerToken),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("立即重新整理 MCP")
        }

        Text("連線狀態：$connectionStatus")
        Text("伺服器名稱：${config.serverName.ifBlank { "未取得" }}")
        if (config.serverVersion.isNotBlank()) {
            Text("伺服器版本：${config.serverVersion}")
        }
        Text("Protocol version：${config.protocolVersion.ifBlank { "未取得" }}")
        if (statusMessage.isNotBlank()) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(4.dp))
        Text("可用 Resources", style = MaterialTheme.typography.titleMedium)
        when {
            !config.resourcesSupported -> Text("伺服器未宣告 Resources capability。")
            config.resources.isEmpty() -> Text("目前沒有可用 Resource。")
            else -> config.resources.forEach { resource ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            resource.title.ifBlank { resource.name },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            resource.uri,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = resource.uri in config.selectedResourceUris,
                        onCheckedChange = { selected ->
                            val selection = config.selectedResourceUris.toMutableSet().apply {
                                if (selected) add(resource.uri) else remove(resource.uri)
                            }
                            onSave(config.copy(selectedResourceUris = selection), "")
                        },
                        enabled = loaded && !busy
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("可用 Prompts", style = MaterialTheme.typography.titleMedium)
        when {
            !config.promptsSupported -> Text("伺服器未宣告 Prompts capability。")
            config.prompts.isEmpty() -> Text("目前沒有可用 Prompt。")
            else -> config.prompts.forEach { prompt ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            prompt.title.ifBlank { prompt.name },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (prompt.requiredArguments.isEmpty()) {
                                prompt.description.ifBlank { "不需要必要參數" }
                            } else {
                                "需要參數（暫不支援）：${prompt.requiredArguments.joinToString()}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = prompt.name in config.selectedPromptNames,
                        onCheckedChange = { selected ->
                            val selection = config.selectedPromptNames.toMutableSet().apply {
                                if (selected) add(prompt.name) else remove(prompt.name)
                            }
                            onSave(config.copy(selectedPromptNames = selection), "")
                        },
                        enabled = loaded && !busy && prompt.requiredArguments.isEmpty()
                    )
                }
            }
        }
    }
}
