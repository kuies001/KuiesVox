package tw.kuies.voiceime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LauncherAppCandidate(val packageName: String, val label: String)
private data class ProfileChoice<T>(val value: T?, val label: String)

@Composable
internal fun AppVoiceProfilesScreen(
    modifier: Modifier = Modifier,
    profiles: List<AppVoiceProfile>,
    loaded: Boolean,
    globalSettings: SmartFormattingSettings,
    currentEditorPackageName: String?,
    onOpenGlobalSettings: () -> Unit,
    onOpenGlobalAsrSettings: () -> Unit,
    onSaveProfile: (AppVoiceProfile) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AppVoiceProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<AppVoiceProfile?>(null) }
    val currentPackage = AppPackageName.normalize(currentEditorPackageName)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("App 專屬設定", style = MaterialTheme.typography.headlineSmall)
        Text(
            "全域預設直接使用 KuiesVox 目前已儲存的設定。App 未明確指定的項目會繼承全域值；停用或刪除後立即回到全域預設。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("全域預設", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("ASR：${globalSettings.displaySpeechModelName()} · 語言：${globalSettings.speechLanguageMode.displayName}")
                Text(
                    "智慧整理：${if (globalSettings.enabled) "開" else "關"} · Provider：${globalSettings.provider.displayName} · " +
                        "上下文糾錯：${if (globalSettings.contextualCorrectionEnabled) "開" else "關"}"
                )
                Text(
                    "句尾：${globalSettings.terminalPeriodMode.profileDisplayName()} · 風格：${globalSettings.formattingStyle.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onOpenGlobalSettings) {
                    Text("修改全域智慧整理設定")
                }
                OutlinedButton(onClick = onOpenGlobalAsrSettings) {
                    Text("修改全域 ASR 設定")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("App 設定", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { showPicker = true }, enabled = loaded) { Text("新增 App 設定") }
        }

        if (!loaded) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (profiles.isEmpty()) {
            Text(
                "尚未建立 App 專屬設定。所有 App 目前都使用全域預設。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            profiles.sortedBy { appLabel(context, it.packageName).lowercase() }.forEach { profile ->
                AppProfileCard(
                    context = context,
                    profile = profile,
                    onEnabledChange = { onSetEnabled(profile.packageName, it) },
                    onEdit = { editingProfile = profile },
                    onDelete = { profileToDelete = profile }
                )
            }
        }

        if (currentPackage != null) {
            OutlinedButton(
                onClick = { editingProfile = existingOrNewProfile(profiles, currentPackage) },
                enabled = loaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("為目前使用中的 App 建立／編輯設定：$currentPackage", maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (showPicker) {
        AppProfilePickerDialog(
            existingProfiles = profiles,
            currentEditorPackageName = currentPackage,
            onDismiss = { showPicker = false },
            onSelectPackage = { packageName ->
                editingProfile = existingOrNewProfile(profiles, packageName)
                showPicker = false
            }
        )
    }

    editingProfile?.let { profile ->
        AppProfileEditorDialog(
            profile = profile,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                onSaveProfile(updated)
                editingProfile = null
            }
        )
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("刪除 App 設定？") },
            text = { Text("刪除 ${profile.packageName} 的 Profile 後，該 App 會使用全域預設。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProfile(profile.packageName)
                    profileToDelete = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { profileToDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun AppProfileCard(
    context: Context,
    profile: AppVoiceProfile,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAppIcon(profile.packageName, Modifier.size(42.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        appLabel(context, profile.packageName),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        profile.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                if (profile.enabled) profile.summary() else "已停用，套用全域預設",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDelete) { Text("刪除") }
                OutlinedButton(onClick = onEdit) { Text("編輯") }
            }
        }
    }
}

@Composable
private fun AppProfilePickerDialog(
    existingProfiles: List<AppVoiceProfile>,
    currentEditorPackageName: String?,
    onDismiss: () -> Unit,
    onSelectPackage: (String) -> Unit
) {
    val context = LocalContext.current
    var candidates by remember { mutableStateOf<List<LauncherAppCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var packageInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        candidates = withContext(Dispatchers.IO) { queryLauncherApps(context) }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇 App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                currentEditorPackageName?.let { packageName ->
                    OutlinedButton(onClick = { onSelectPackage(packageName) }, modifier = Modifier.fillMaxWidth()) {
                        Text("目前使用中的 App：$packageName", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it; inputError = null },
                    label = { Text("手動輸入 package name") },
                    supportingText = { Text(inputError ?: "若清單找不到 App，可手動輸入 package name。") },
                    isError = inputError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("已安裝的啟動器 App", style = MaterialTheme.typography.titleSmall)
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (candidates.isEmpty()) {
                    Text("無法列出 App，請使用上方欄位手動輸入。", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 230.dp)) {
                        items(candidates, key = LauncherAppCandidate::packageName) { candidate ->
                            val existing = existingProfiles.any { it.packageName == candidate.packageName }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectPackage(candidate.packageName) }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                ProfileAppIcon(candidate.packageName, Modifier.size(34.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(candidate.packageName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (existing) Text("編輯", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val packageName = AppPackageName.normalize(packageInput)
                if (packageName == null) inputError = "請輸入有效的 Android package name。"
                else onSelectPackage(packageName)
            }) { Text("使用此 package") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AppProfileEditorDialog(
    profile: AppVoiceProfile,
    onDismiss: () -> Unit,
    onSave: (AppVoiceProfile) -> Unit
) {
    var draft by remember(profile) { mutableStateOf(profile) }
    val speechModels = FormattingModels.speech.map { option ->
        ProfileChoice(option.id, option.displayName)
    }
    val providerOptions = TextFormattingProviderRegistry.supportedProfileProviders()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(appLabel(LocalContext.current, profile.packageName), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(profile.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileDropdown(
                    title = "ASR 模型",
                    selected = draft.speechModelOverride,
                    choices = listOf(ProfileChoice<String>(null, "繼承全域")) + speechModels,
                    onSelect = { draft = draft.copy(speechModelOverride = it) }
                )
                ProfileDropdown(
                    title = "辨識語言",
                    selected = draft.speechLanguageModeOverride,
                    choices = listOf(ProfileChoice<SpeechLanguageMode>(null, "繼承全域")) + SpeechLanguageMode.entries.map {
                        ProfileChoice(it, it.displayName)
                    },
                    onSelect = { draft = draft.copy(speechLanguageModeOverride = it) }
                )
                ProfileDropdown(
                    title = "智慧文字整理",
                    selected = draft.smartFormattingEnabledOverride,
                    choices = listOf(ProfileChoice<Boolean>(null, "繼承全域"), ProfileChoice(true, "開"), ProfileChoice(false, "關")),
                    onSelect = { draft = draft.copy(smartFormattingEnabledOverride = it) }
                )
                ProfileDropdown(
                    title = "智慧整理 Provider",
                    selected = draft.providerOverride,
                    choices = listOf(ProfileChoice<TextFormattingProviderId>(null, "繼承全域")) + providerOptions.map {
                        ProfileChoice(it, it.displayName)
                    },
                    onSelect = { draft = draft.copy(providerOverride = it) }
                )
                ProfileDropdown(
                    title = "上下文智慧糾錯",
                    selected = draft.contextualCorrectionEnabledOverride,
                    choices = listOf(ProfileChoice<Boolean>(null, "繼承全域"), ProfileChoice(true, "開"), ProfileChoice(false, "關")),
                    onSelect = { draft = draft.copy(contextualCorrectionEnabledOverride = it) }
                )
                ProfileDropdown(
                    title = "句尾句號",
                    selected = draft.terminalPeriodModeOverride,
                    choices = listOf(ProfileChoice<TerminalPeriodMode>(null, "繼承全域")) + TerminalPeriodMode.entries.map {
                        ProfileChoice(it, it.profileDisplayName())
                    },
                    onSelect = { draft = draft.copy(terminalPeriodModeOverride = it) }
                )
                ProfileDropdown(
                    title = "文字整理風格",
                    selected = draft.formattingStyleOverride,
                    choices = listOf(ProfileChoice<TextFormattingStyle>(null, "繼承全域")) + TextFormattingStyle.entries.map {
                        ProfileChoice(it, it.displayName)
                    },
                    onSelect = { draft = draft.copy(formattingStyleOverride = it) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(updatedAt = System.currentTimeMillis())) }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun <T> ProfileDropdown(
    title: String,
    selected: T?,
    choices: List<ProfileChoice<T>>,
    onSelect: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.value == selected }?.label ?: "繼承全域"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("⌄")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        onClick = { expanded = false; onSelect(choice.value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(context, packageName) { loadAppIcon(context, packageName) }
    val bitmap = remember(icon) { runCatching { icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(appLabel(context, packageName).firstOrNull()?.toString() ?: "?", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun existingOrNewProfile(profiles: List<AppVoiceProfile>, packageName: String): AppVoiceProfile {
    val normalized = AppPackageName.normalize(packageName) ?: packageName.trim()
    val existing = profiles.firstOrNull { it.packageName == normalized }
    if (existing != null) return existing
    val now = System.currentTimeMillis()
    return AppVoiceProfile(packageName = normalized, createdAt = now, updatedAt = now)
}

private fun appLabel(context: Context, packageName: String): String = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString().takeIf(String::isNotBlank) ?: packageName
}.getOrDefault(packageName)

private fun loadAppIcon(context: Context, packageName: String): Drawable? = runCatching {
    context.packageManager.getApplicationIcon(packageName)
}.getOrNull()

@Suppress("DEPRECATION")
private fun queryLauncherApps(context: Context): List<LauncherAppCandidate> = runCatching {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { candidateFromResolveInfo(context.packageManager, it) }
        .filterNot { it.packageName == context.packageName }
        .distinctBy(LauncherAppCandidate::packageName)
        .sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())

@Suppress("DEPRECATION")
private fun candidateFromResolveInfo(packageManager: PackageManager, info: ResolveInfo): LauncherAppCandidate? {
    val applicationInfo = info.activityInfo?.applicationInfo ?: return null
    val packageName = AppPackageName.normalize(applicationInfo.packageName) ?: return null
    val label = runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
        .getOrDefault(packageName).ifBlank { packageName }
    return LauncherAppCandidate(packageName, label)
}

private fun SmartFormattingSettings.displaySpeechModelName(): String =
    FormattingModels.speech.firstOrNull { it.id == speechModel }?.displayName ?: speechModel

private fun TerminalPeriodMode.profileDisplayName(): String = when (this) {
    TerminalPeriodMode.AUTO -> "智慧"
    TerminalPeriodMode.ALWAYS -> "加入"
    TerminalPeriodMode.NEVER -> "不加入"
}
