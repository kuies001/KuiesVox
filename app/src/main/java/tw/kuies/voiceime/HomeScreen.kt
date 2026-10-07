package tw.kuies.voiceime

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    apiKeySaved: Boolean,
    hasMicrophonePermission: Boolean,
    smartFormattingSettings: SmartFormattingSettings,
    glossaryTerms: List<PersonalGlossaryTerm>,
    correctionRules: List<TextCorrectionRule>,
    mcpConfig: McpConfig,
    mcpConnectionStatus: String,
    currentVersionName: String,
    updateState: AppUpdateState,
    onOpenGroq: () -> Unit,
    onOpenSmartFormatting: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenMcp: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (GitHubRelease, SemanticVersion, GitHubReleaseAsset) -> Unit,
    onInstallUpdate: (GitHubRelease, Long) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenRelease: (String?) -> Unit,
    onRequestMicrophonePermission: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "KuiesVox 設定",
                style = MaterialTheme.typography.displaySmall,
                color = colors.onBackground
            )
            Text(
                "你的語音輸入小助手",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))

        MicrophonePermissionReminderCard(
            hasPermission = hasMicrophonePermission,
            onRequestPermission = onRequestMicrophonePermission
        )

        HomeFeatureCard(
            icon = R.drawable.ic_home_groq,
            title = "Groq",
            subtitle = "語音辨識服務",
            accent = colors.primary,
            status = if (apiKeySaved) "已設定" else "未設定"
        ) {
            StatusLine(label = "API Key", value = if (apiKeySaved) "已設定" else "尚未設定")
            ActionButton(
                label = "設定 Groq",
                accent = colors.primary,
                contentColor = colors.onPrimary,
                onClick = onOpenGroq,
                modifier = Modifier.align(Alignment.End)
            )
        }

        HomeFeatureCard(
            icon = R.drawable.ic_home_personalization,
            title = "智慧整理",
            subtitle = "長逐字稿語意校對與排版",
            accent = colors.secondary,
            status = if (smartFormattingSettings.enabled) "已啟用" else "已停用"
        ) {
            StatusLine(
                label = "短文字門檻",
                value = "${smartFormattingSettings.threshold} 字"
            )
            ActionButton(
                label = "設定智慧整理",
                accent = colors.secondary,
                contentColor = colors.onSecondary,
                onClick = onOpenSmartFormatting,
                modifier = Modifier.align(Alignment.End)
            )
        }

        HomeFeatureCard(
            icon = R.drawable.ic_home_personalization,
            title = "個人化",
            subtitle = "讓辨識結果更貼近你的用語",
            accent = colors.tertiary,
            status = "${glossaryTerms.size + correctionRules.size} 項設定"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryMetric(
                    label = "常用詞",
                    total = glossaryTerms.size,
                    enabled = glossaryTerms.count { it.enabled },
                    unit = "個",
                    accent = colors.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "修正规則",
                    total = correctionRules.size,
                    enabled = correctionRules.count { it.enabled },
                    unit = "條",
                    accent = colors.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
            ActionButton(
                label = "管理個人化",
                accent = colors.tertiary,
                contentColor = colors.onTertiary,
                onClick = onOpenPersonalization,
                modifier = Modifier.align(Alignment.End)
            )
        }

        HomeFeatureCard(
            icon = R.drawable.ic_home_mcp,
            title = "MCP Context",
            subtitle = "外部詞彙與上下文",
            accent = colors.primary,
            status = if (mcpConfig.enabled) "已啟用" else "未啟用"
        ) {
            StatusLine(label = "MCP", value = if (mcpConfig.enabled) "已啟用" else "未啟用")
            StatusLine(
                label = "Server",
                value = if (mcpConfig.serverUrl.isBlank()) "未設定" else "已設定"
            )
            if (mcpConfig.serverName.isNotBlank()) {
                StatusLine(label = "伺服器", value = mcpConfig.serverName)
            }
            Text(
                "連線狀態：$mcpConnectionStatus",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            ActionButton(
                label = "設定 MCP",
                accent = colors.primary,
                contentColor = colors.onPrimary,
                onClick = onOpenMcp,
                modifier = Modifier.align(Alignment.End)
            )
        }

        AppUpdateFeatureCard(
            currentVersionName = currentVersionName,
            updateState = updateState,
            onCheckForUpdates = onCheckForUpdates,
            onDownloadUpdate = onDownloadUpdate,
            onInstallUpdate = onInstallUpdate,
            onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
            onOpenReleases = onOpenReleases,
            onOpenRelease = onOpenRelease
        )
    }
}

@Composable
private fun MicrophonePermissionReminderCard(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (hasPermission) colors.tertiary else colors.secondary
    val cardColor = if (hasPermission) {
        colors.surfaceVariant.copy(alpha = 0.38f)
    } else {
        colors.secondaryContainer.copy(alpha = 0.34f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, accent.copy(alpha = if (hasPermission) 0.22f else 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_home_voice),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "麥克風授權",
                    modifier = Modifier.padding(start = 9.dp).weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface
                )
                StatusPill(
                    text = if (hasPermission) "已授權" else "未授權",
                    accent = accent
                )
            }
            if (!hasPermission) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "使用語音輸入前，請先授權麥克風",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onRequestPermission,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("前往授權", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateFeatureCard(
    currentVersionName: String,
    updateState: AppUpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (GitHubRelease, SemanticVersion, GitHubReleaseAsset) -> Unit,
    onInstallUpdate: (GitHubRelease, Long) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenRelease: (String?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showReleaseNotes by remember { mutableStateOf(false) }
    val busy = updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading
    val releaseUrl = when (updateState) {
        is AppUpdateState.UpdateAvailable -> updateState.release.htmlUrl
        is AppUpdateState.Downloading -> updateState.release.htmlUrl
        is AppUpdateState.ReadyToInstall -> updateState.release.htmlUrl
        is AppUpdateState.AwaitingUnknownSourcesPermission -> updateState.release.htmlUrl
        is AppUpdateState.Installing -> updateState.release.htmlUrl
        is AppUpdateState.Installed -> updateState.release.htmlUrl
        is AppUpdateState.Error -> updateState.releasePageUrl
        else -> null
    }

    HomeFeatureCard(
        icon = R.drawable.ic_ime_settings,
        title = "版本更新",
        subtitle = "檢查 KuiesVox 新版本",
        accent = colors.tertiary,
        status = "v$currentVersionName"
    ) {
        StatusLine(label = "目前版本", value = "v$currentVersionName")

        when (updateState) {
            AppUpdateState.Idle -> Text(
                "檢查官方 GitHub Releases，或手動前往下載。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            AppUpdateState.Checking -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在檢查更新…", style = MaterialTheme.typography.bodyMedium)
            }
            AppUpdateState.UpToDate -> Text(
                "目前已是最新版",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary
            )
            is AppUpdateState.UpdateAvailable -> {
                val release = updateState.release
                Text(
                    "發現新版本 v${updateState.version}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (release.title != release.tagName) {
                    Text(release.title, style = MaterialTheme.typography.bodyMedium)
                }
                release.publishedAt?.take(10)?.let { date ->
                    Text("發布日期：$date", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                val summary = releaseNotesSummary(release.body)
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { showReleaseNotes = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("查看更新內容")
                }
                Button(
                    onClick = { onDownloadUpdate(release, updateState.version, updateState.apk) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("下載並安裝")
                }
            }
            is AppUpdateState.Downloading -> Text(
                "正在下載 APK，進度可在系統通知列查看。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            is AppUpdateState.ReadyToInstall -> {
                Text(
                    "APK 已下載完成，接下來由 Android 安裝器顯示確認畫面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Button(
                    onClick = { onInstallUpdate(updateState.release, updateState.downloadId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("開啟安裝器")
                }
                Text(
                    "更新需使用相同 applicationId、相同簽章及較高 versionCode。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            is AppUpdateState.AwaitingUnknownSourcesPermission -> {
                Text(
                    "若要由 KuiesVox 直接更新，需要允許此 App 安裝未知來源應用程式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Button(
                    onClick = onOpenUnknownSourcesSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("前往授權")
                }
                OutlinedButton(
                    onClick = { onInstallUpdate(updateState.release, updateState.downloadId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("重新開啟安裝器")
                }
            }
            is AppUpdateState.Installing -> {
                Text(
                    "已開啟 Android 系統安裝器，請確認是否安裝更新。若取消安裝，可再次開啟安裝器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Button(
                    onClick = { onInstallUpdate(updateState.release, updateState.downloadId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("重新開啟安裝器")
                }
            }
            is AppUpdateState.Installed -> Text(
                "KuiesVox 更新安裝完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary
            )
            is AppUpdateState.Error -> Text(
                updateState.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error
            )
        }

        Button(
            onClick = onCheckForUpdates,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text(if (updateState is AppUpdateState.Checking) "正在檢查更新…" else "檢查版本更新")
        }
        OutlinedButton(
            onClick = { onOpenRelease(releaseUrl) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text(if (releaseUrl == null) "前往 GitHub Releases" else "前往 GitHub 手動下載")
        }

        if (showReleaseNotes && updateState is AppUpdateState.UpdateAvailable) {
            AlertDialog(
                onDismissRequest = { showReleaseNotes = false },
                title = { Text("v${updateState.version} 更新內容") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(updateState.release.body.take(MAX_RELEASE_NOTES_LENGTH).ifBlank { "此版本沒有提供更新內容。" })
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showReleaseNotes = false }) { Text("關閉") }
                }
            )
        }
    }
}

private fun releaseNotesSummary(body: String): String = body
    .lineSequence()
    .map { it.trim().replace(Regex("^#{1,6}\\s*"), "").replace(Regex("[`*_]"), "") }
    .filter { it.isNotBlank() }
    .joinToString(" ")
    .take(220)
    .let { summary -> if (body.length > summary.length) "$summary…" else summary }

private const val MAX_RELEASE_NOTES_LENGTH = 6000

@Composable
private fun HomeFeatureCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    accent: Color,
    status: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                StatusPill(text = status, accent = accent)
            }
            content()
        }
    }
}

@Composable
private fun StatusPill(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.13f),
        contentColor = accent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    total: Int,
    enabled: Int,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                total.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("啟用 $enabled $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionButton(
    label: String,
    accent: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = contentColor
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun GroqSettingsScreen(
    modifier: Modifier = Modifier,
    speechModel: String,
    onSpeechModelChange: (String) -> Unit,
    apiKey: String,
    apiKeySaved: Boolean,
    apiKeyLoaded: Boolean,
    status: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var speechModelMenuOpen by remember { mutableStateOf(false) }
    val selectedSpeechModel = FormattingModels.speech.firstOrNull { it.id == speechModel }
        ?: FormattingModels.speech.last()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("Groq", style = MaterialTheme.typography.headlineSmall)
        Text("語音辨識模型", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { speechModelMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedSpeechModel.displayName, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = speechModelMenuOpen, onDismissRequest = { speechModelMenuOpen = false }) {
                FormattingModels.speech.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            speechModelMenuOpen = false
                            onSpeechModelChange(option.id)
                        }
                    )
                }
            }
        }
        Text(
            selectedSpeechModel.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("API Key：${if (apiKeySaved) "已設定" else "未設定"}")
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Groq API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = apiKeyLoaded,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSave,
            enabled = apiKeyLoaded && apiKey.isNotBlank(),
            shape = RoundedCornerShape(50)
        ) {
            Text("儲存 API Key")
        }
        TextButton(
            onClick = onClear,
            enabled = apiKeyLoaded && apiKey.isNotBlank()
        ) {
            Text("清除 API Key")
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "API Key 僅儲存在 App 私有空間，不會顯示於首頁或寫入 Logcat。",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
