package tw.kuies.voiceime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun SmartFormattingSettingsScreen(
    modifier: Modifier = Modifier,
    settings: SmartFormattingSettings,
    loaded: Boolean,
    status: String,
    groqApiKeySaved: Boolean,
    geminiApiKey: String,
    geminiApiKeySaved: Boolean,
    geminiApiKeyLoaded: Boolean,
    geminiApiKeyStatus: String,
    openAiApiKey: String,
    openAiApiKeySaved: Boolean,
    openAiApiKeyLoaded: Boolean,
    openAiApiKeyStatus: String,
    onSave: (SmartFormattingSettings) -> Unit,
    onGeminiApiKeyChange: (String) -> Unit,
    onSaveGeminiApiKey: () -> Unit,
    onClearGeminiApiKey: () -> Unit,
    onOpenAiApiKeyChange: (String) -> Unit,
    onSaveOpenAiApiKey: () -> Unit,
    onClearOpenAiApiKey: () -> Unit,
    onOpenGroqSettings: () -> Unit,
    onOpenApiKeyPage: (String) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var thresholdText by remember(settings.threshold) { mutableStateOf(settings.threshold.toString()) }
    var geminiKeyDraft by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var openAiKeyDraft by remember(openAiApiKey) { mutableStateOf(openAiApiKey) }
    val threshold = thresholdText.toIntOrNull()
    val thresholdIsValid = threshold != null && threshold in
        SmartFormattingSettings.MIN_THRESHOLD..SmartFormattingSettings.MAX_THRESHOLD
    val selectedModelId = selectedModelId(draft)
    val selectedOption = FormattingModels.options(draft.provider).firstOrNull { it.id == selectedModelId }
    val modelLabel = selectedOption?.displayName
        ?: if (selectedModelId == FormattingModels.CUSTOM_MODEL_ID) "自訂模型" else selectedModelId
    val modelIsValid = draft.modelFor(draft.provider).isNotBlank()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("智慧整理", style = MaterialTheme.typography.headlineSmall)
        Text(
            "語音辨識會參考個人詞庫；文字整理會套用本機修正规則，再依下方已串接的 Provider 設定整理。",
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
                Text(if (draft.enabled) "已啟用" else "已停用", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) }, enabled = loaded)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("上下文智慧糾錯", style = MaterialTheme.typography.titleMedium)
                Text(
                    "依照句子語意與個人詞庫，修正明顯的同音字、近音字及辨識錯誤。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!draft.enabled) {
                    Text(
                        "需要啟用智慧整理才能使用；目前不會執行上下文智慧糾錯。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Switch(
                checked = draft.contextualCorrectionEnabled,
                onCheckedChange = { draft = draft.copy(contextualCorrectionEnabled = it) },
                enabled = loaded
            )
        }
        OutlinedTextField(
            value = thresholdText,
            onValueChange = { value -> if (value.length <= 3 && value.all(Char::isDigit)) thresholdText = value },
            label = { Text("短文字門檻") },
            supportingText = { Text(if (thresholdIsValid) "0～500 字；不超過門檻時使用本機修正結果。" else "請輸入 0～500 的整數。") },
            isError = !thresholdIsValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = loaded,
            modifier = Modifier.fillMaxWidth()
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("\u53e5\u5c3e\u53e5\u865f", style = MaterialTheme.typography.titleMedium)
            TerminalPeriodMode.entries.forEach { mode ->
                TerminalPeriodModeOption(
                    mode = mode,
                    selected = draft.terminalPeriodMode,
                    enabled = loaded,
                    onSelect = { draft = draft.copy(terminalPeriodMode = it) }
                )
            }
        }
        SettingsDropdown(
            title = "文字整理風格",
            value = draft.formattingStyle.displayName,
            items = TextFormattingStyle.entries.map { it.displayName },
            enabled = loaded,
            onSelect = { name ->
                draft = draft.copy(formattingStyle = TextFormattingStyle.entries.first { it.displayName == name })
            }
        )
        SettingsDropdown(
            title = "Provider",
            value = draft.provider.displayName,
            items = TextFormattingProviderId.entries.map { it.displayName },
            enabled = loaded,
            onSelect = { name -> draft = draft.copy(provider = TextFormattingProviderId.entries.first { it.displayName == name }) }
        )
        ModelDropdown(
            value = modelLabel,
            options = FormattingModels.options(draft.provider),
            enabled = loaded,
            onSelect = { id -> draft = setSelectedModel(draft, id) }
        )
        selectedOption?.let { option ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(option.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                option.badge?.let { ModelBadge(it) }
            }
        }
        if (selectedModelId == FormattingModels.CUSTOM_MODEL_ID) {
            OutlinedTextField(
                value = customModelId(draft),
                onValueChange = { draft = setCustomModelId(draft, it) },
                label = { Text("Model ID") },
                supportingText = { Text("只影響目前 Provider；其他 Provider 的自訂 ID 會分開保存。") },
                singleLine = true,
                enabled = loaded,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ProviderKeyCard(
            provider = draft.provider,
            groqApiKeySaved = groqApiKeySaved,
            geminiApiKey = geminiKeyDraft,
            geminiApiKeySaved = geminiApiKeySaved,
            geminiApiKeyLoaded = geminiApiKeyLoaded,
            geminiApiKeyStatus = geminiApiKeyStatus,
            openAiApiKey = openAiKeyDraft,
            openAiApiKeySaved = openAiApiKeySaved,
            openAiApiKeyLoaded = openAiApiKeyLoaded,
            openAiApiKeyStatus = openAiApiKeyStatus,
            onGeminiApiKeyChange = { geminiKeyDraft = it; onGeminiApiKeyChange(it) },
            onSaveGeminiApiKey = onSaveGeminiApiKey,
            onClearGeminiApiKey = onClearGeminiApiKey,
            onOpenAiApiKeyChange = { openAiKeyDraft = it; onOpenAiApiKeyChange(it) },
            onSaveOpenAiApiKey = onSaveOpenAiApiKey,
            onClearOpenAiApiKey = onClearOpenAiApiKey,
            onOpenGroqSettings = onOpenGroqSettings,
            onOpenApiKeyPage = onOpenApiKeyPage
        )
        if (draft.provider != TextFormattingProviderId.GROQ) {
            Text(
                "目前僅提供 " + draft.provider.displayName + " 的 API Key 與模型設定欄位，尚未串接該 API；選用時會使用本機修正結果。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Button(
            onClick = { onSave(draft.copy(threshold = threshold ?: SmartFormattingSettings.DEFAULT_THRESHOLD)) },
            enabled = loaded && thresholdIsValid && modelIsValid,
            shape = RoundedCornerShape(50),
            modifier = Modifier.align(Alignment.End)
        ) { Text("儲存智慧整理設定") }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Gemini 與 OpenAI API Key 只儲存在 App 私有設定中；目前不會送至任何服務。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TerminalPeriodModeOption(
    mode: TerminalPeriodMode,
    selected: TerminalPeriodMode,
    enabled: Boolean,
    onSelect: (TerminalPeriodMode) -> Unit
) {
    val title = when (mode) {
        TerminalPeriodMode.AUTO -> "\u667a\u6167\uff08\u63a8\u85a6\uff09"
        TerminalPeriodMode.ALWAYS -> "\u52a0\u5165\u53e5\u865f"
        TerminalPeriodMode.NEVER -> "\u4e0d\u52a0\u5165\u53e5\u865f"
    }
    val description = when (mode) {
        TerminalPeriodMode.AUTO -> "\u4f9d\u8f38\u5165\u6b04\u4f4d\u81ea\u52d5\u5224\u65b7\u3002\u804a\u5929\u3001\u641c\u5c0b\u7b49\u60c5\u5883\u901a\u5e38\u4e0d\u88dc\u53e5\u865f\uff1b\u4e00\u822c\u6587\u5b57\u8207\u6587\u4ef6\u5247\u4fdd\u7559\u3002"
        TerminalPeriodMode.ALWAYS -> "\u4fdd\u7559\u8a9e\u97f3\u8fa8\u8b58\u6216\u6587\u5b57\u6574\u7406\u7522\u751f\u7684\u53e5\u5c3e\u53e5\u865f\u3002"
        TerminalPeriodMode.NEVER -> "\u9001\u51fa\u6587\u5b57\u524d\u79fb\u9664\u6700\u5f8c\u7684\u4e2d\u6587\u53e5\u865f\uff0c\u9069\u5408\u804a\u5929\u4f7f\u7528\u3002"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelect(mode) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = selected == mode,
            onClick = { onSelect(mode) },
            enabled = enabled
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsDropdown(title: String, value: String, items: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(value, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(item) }, onClick = { expanded = false; onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun ModelDropdown(value: String, options: List<FormattingModelOption>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("模型", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(value, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(option.displayName)
                                option.badge?.let { ModelBadge(it) }
                            }
                        },
                        onClick = { expanded = false; onSelect(option.id) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("自訂模型") },
                    onClick = { expanded = false; onSelect(FormattingModels.CUSTOM_MODEL_ID) }
                )
            }
        }
    }
}

@Composable
private fun ModelBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ProviderKeyCard(
    provider: TextFormattingProviderId,
    groqApiKeySaved: Boolean,
    geminiApiKey: String,
    geminiApiKeySaved: Boolean,
    geminiApiKeyLoaded: Boolean,
    geminiApiKeyStatus: String,
    openAiApiKey: String,
    openAiApiKeySaved: Boolean,
    openAiApiKeyLoaded: Boolean,
    openAiApiKeyStatus: String,
    onGeminiApiKeyChange: (String) -> Unit,
    onSaveGeminiApiKey: () -> Unit,
    onClearGeminiApiKey: () -> Unit,
    onOpenAiApiKeyChange: (String) -> Unit,
    onSaveOpenAiApiKey: () -> Unit,
    onClearOpenAiApiKey: () -> Unit,
    onOpenGroqSettings: () -> Unit,
    onOpenApiKeyPage: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(provider.displayName + " API 設定", style = MaterialTheme.typography.titleMedium)
        when (provider) {
            TextFormattingProviderId.GROQ -> {
                ApiKeyStatus(groqApiKeySaved)
                Text(
                    "登入 Groq Console 建立 API Key。Groq 語音辨識與 Groq 文字整理可共用同一組 Key。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenGroqSettings) { Text("設定 Groq API Key") }
                    TextButton(onClick = { onOpenApiKeyPage("https://console.groq.com/keys") }) { Text("取得 API Key") }
                }
            }
            TextFormattingProviderId.GEMINI -> {
                ApiKeyStatus(geminiApiKeySaved)
                OutlinedTextField(
                    value = geminiApiKey, onValueChange = onGeminiApiKeyChange, label = { Text("Gemini API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, enabled = geminiApiKeyLoaded, modifier = Modifier.fillMaxWidth()
                )
                Text("前往 Google AI Studio 建立 Gemini API Key。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveGeminiApiKey, enabled = geminiApiKeyLoaded && geminiApiKey.isNotBlank()) { Text("儲存") }
                    TextButton(onClick = onClearGeminiApiKey, enabled = geminiApiKeyLoaded) { Text("清除") }
                    TextButton(onClick = { onOpenApiKeyPage("https://aistudio.google.com/apikey") }) { Text("取得 API Key") }
                }
                if (geminiApiKeyStatus.isNotBlank()) Text(geminiApiKeyStatus, style = MaterialTheme.typography.bodySmall)
            }
            TextFormattingProviderId.OPENAI -> {
                ApiKeyStatus(openAiApiKeySaved)
                OutlinedTextField(
                    value = openAiApiKey, onValueChange = onOpenAiApiKeyChange, label = { Text("OpenAI API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, enabled = openAiApiKeyLoaded, modifier = Modifier.fillMaxWidth()
                )
                Text("登入 OpenAI Platform 建立 API Key。API 使用量依帳號與模型計費。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveOpenAiApiKey, enabled = openAiApiKeyLoaded && openAiApiKey.isNotBlank()) { Text("儲存") }
                    TextButton(onClick = onClearOpenAiApiKey, enabled = openAiApiKeyLoaded) { Text("清除") }
                    TextButton(onClick = { onOpenApiKeyPage("https://platform.openai.com/api-keys") }) { Text("取得 API Key") }
                }
                if (openAiApiKeyStatus.isNotBlank()) Text(openAiApiKeyStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ApiKeyStatus(saved: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("API Key：", style = MaterialTheme.typography.bodyMedium)
        Text(if (saved) "已設定" else "未設定",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun selectedModelId(settings: SmartFormattingSettings): String = when (settings.provider) {
    TextFormattingProviderId.GROQ -> settings.groqFormattingModel
    TextFormattingProviderId.GEMINI -> settings.geminiFormattingModel
    TextFormattingProviderId.OPENAI -> settings.openAiFormattingModel
}

private fun customModelId(settings: SmartFormattingSettings): String = when (settings.provider) {
    TextFormattingProviderId.GROQ -> settings.groqCustomModelId
    TextFormattingProviderId.GEMINI -> settings.geminiCustomModelId
    TextFormattingProviderId.OPENAI -> settings.openAiCustomModelId
}

private fun setSelectedModel(settings: SmartFormattingSettings, model: String): SmartFormattingSettings = when (settings.provider) {
    TextFormattingProviderId.GROQ -> settings.copy(groqFormattingModel = model)
    TextFormattingProviderId.GEMINI -> settings.copy(geminiFormattingModel = model)
    TextFormattingProviderId.OPENAI -> settings.copy(openAiFormattingModel = model)
}

private fun setCustomModelId(settings: SmartFormattingSettings, model: String): SmartFormattingSettings = when (settings.provider) {
    TextFormattingProviderId.GROQ -> settings.copy(groqCustomModelId = model)
    TextFormattingProviderId.GEMINI -> settings.copy(geminiCustomModelId = model)
    TextFormattingProviderId.OPENAI -> settings.copy(openAiCustomModelId = model)
}
