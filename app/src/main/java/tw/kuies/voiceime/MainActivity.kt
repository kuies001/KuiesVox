package tw.kuies.voiceime

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import tw.kuies.voiceime.ui.theme.VoiceIMETheme

private const val MAIN_ACTIVITY_TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            VoiceIMETheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceImeSettingsApp(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private enum class SettingsDestination {
    HOME,
    GROQ,
    SMART_FORMATTING,
    PERSONALIZATION,
    MCP
}

@Composable
private fun VoiceImeSettingsApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val screenIsActive = remember { AtomicBoolean(true) }

    var destination by remember { mutableStateOf(SettingsDestination.HOME) }
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordAudioPermission = granted
    }

    var apiKey by remember { mutableStateOf("") }
    var apiKeySaved by remember { mutableStateOf(false) }
    var apiKeyLoaded by remember { mutableStateOf(false) }
    var apiKeyStatus by remember { mutableStateOf("載入 API Key 中…") }
    var glossaryTerms by remember { mutableStateOf<List<PersonalGlossaryTerm>>(emptyList()) }
    var glossaryStatus by remember { mutableStateOf("載入個人詞庫中…") }
    var glossaryLoaded by remember { mutableStateOf(false) }
    var correctionRules by remember { mutableStateOf<List<TextCorrectionRule>>(emptyList()) }
    var correctionStatus by remember { mutableStateOf("載入文字修正規則中…") }
    var correctionRulesLoaded by remember { mutableStateOf(false) }
    var mcpConfig by remember { mutableStateOf(McpConfig()) }
    var mcpConfigLoaded by remember { mutableStateOf(false) }
    var mcpConnectionStatus by remember { mutableStateOf("載入 MCP 設定中…") }
    var mcpStatusMessage by remember { mutableStateOf("") }
    var mcpBusy by remember { mutableStateOf(false) }
    var smartFormattingSettings by remember { mutableStateOf(SmartFormattingSettings()) }
    var smartFormattingLoaded by remember { mutableStateOf(false) }
    var smartFormattingStatus by remember { mutableStateOf("載入智慧整理設定中…") }

    BackHandler(enabled = destination != SettingsDestination.HOME) {
        destination = SettingsDestination.HOME
    }

    DisposableEffect(applicationContext) {
        screenIsActive.set(true)
        GroqApiKeyStore.readAsync(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { storedKey ->
                    apiKey = storedKey.orEmpty()
                    apiKeySaved = !storedKey.isNullOrBlank()
                    apiKeyStatus = if (apiKeySaved) {
                        "API Key 已儲存在此 App 的私有空間。"
                    } else {
                        "輸入並儲存 Groq API Key。"
                    }
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "API key read failed: ${exception.javaClass.simpleName}")
                    apiKeyStatus = "API Key 讀取失敗，請重新輸入並儲存。"
                }
                apiKeyLoaded = true
            }
        }
        PersonalGlossaryRepository.load(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    glossaryTerms = it
                    glossaryStatus = ""
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Personal glossary load failed: ${exception.javaClass.simpleName}")
                    glossaryStatus = "詞庫讀取失敗，語音辨識仍可使用。"
                }
                glossaryLoaded = true
            }
        }
        TextCorrectionRuleRepository.load(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    correctionRules = it
                    correctionStatus = ""
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Text correction rules load failed: ${exception.javaClass.simpleName}")
                    correctionStatus = "規則讀取失敗，仍可正常使用語音輸入。"
                }
                correctionRulesLoaded = true
            }
        }
        McpConfigRepository.load(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { config ->
                    mcpConfig = config
                    mcpConnectionStatus = if (config.serverUrl.isBlank()) "未設定" else "尚未測試連線"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "MCP settings load failed: ${exception.javaClass.simpleName}")
                    mcpConnectionStatus = "連線失敗：設定讀取失敗"
                }
                mcpConfigLoaded = true
            }
        }
        SmartFormattingSettingsRepository.loadAsync(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { settings ->
                    smartFormattingSettings = settings
                    smartFormattingStatus = "設定已載入。"
                }.onFailure { exception ->
                    Log.w(
                        MAIN_ACTIVITY_TAG,
                        "Smart formatting settings load failed: ${exception.javaClass.simpleName}"
                    )
                    smartFormattingStatus = "設定讀取失敗；輸入法會使用預設值。"
                }
                smartFormattingLoaded = true
            }
        }
        onDispose { screenIsActive.set(false) }
    }

    fun saveMcpConfig(config: McpConfig, successMessage: String = "") {
        mcpConfig = config
        try {
            McpConfigRepository.save(applicationContext, config) { result ->
                mainHandler.post {
                    if (!screenIsActive.get()) return@post
                    result.onSuccess {
                        if (successMessage.isNotBlank()) mcpStatusMessage = successMessage
                    }.onFailure { exception ->
                        Log.w(MAIN_ACTIVITY_TAG, "MCP settings save failed: ${exception.javaClass.simpleName}")
                        mcpStatusMessage = "MCP 設定儲存失敗，請稍後再試。"
                    }
                }
            }
        } catch (exception: Exception) {
            Log.w(MAIN_ACTIVITY_TAG, "MCP settings save failed: ${exception.javaClass.simpleName}")
            mcpStatusMessage = "MCP 設定儲存失敗，請稍後再試。"
        }
    }

    fun saveSmartFormattingSettings(settings: SmartFormattingSettings) {
        val normalized = SmartFormattingSettingsRepository.normalize(settings)
        smartFormattingSettings = normalized
        smartFormattingStatus = "正在儲存智慧整理設定…"
        SmartFormattingSettingsRepository.saveAsync(applicationContext, normalized) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    smartFormattingStatus = "設定已儲存。"
                }.onFailure { exception ->
                    Log.w(
                        MAIN_ACTIVITY_TAG,
                        "Smart formatting settings save failed: ${exception.javaClass.simpleName}"
                    )
                    smartFormattingStatus = "設定儲存失敗，請稍後再試。"
                }
            }
        }
    }

    fun addGlossaryTerms(terms: List<String>) {
        PersonalGlossaryRepository.add(applicationContext, terms) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { addResult ->
                    glossaryTerms = addResult.entries
                    glossaryStatus = if (addResult.addedCount > 0) {
                        "已新增 ${addResult.addedCount} 個詞彙。"
                    } else {
                        "沒有新增詞彙；內容可能空白或已存在。"
                    }
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Personal glossary save failed: ${exception.javaClass.simpleName}")
                    glossaryStatus = "詞庫儲存失敗，請稍後再試。"
                }
            }
        }
    }

    fun importDefaultGlossary() {
        PersonalGlossaryRepository.importDefaults(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { importResult ->
                    glossaryTerms = importResult.entries
                    glossaryStatus = "預設詞庫匯入完成，新增 ${importResult.addedCount} 筆。"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Default glossary import failed: ${exception.javaClass.simpleName}")
                    glossaryStatus = "預設詞庫匯入失敗，請稍後再試。"
                }
            }
        }
    }

    fun setGlossaryTermEnabled(entry: PersonalGlossaryTerm, enabled: Boolean) {
        PersonalGlossaryRepository.setEnabled(applicationContext, entry.id, enabled) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    glossaryTerms = it
                    glossaryStatus = "詞彙狀態已更新。"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Personal glossary update failed: ${exception.javaClass.simpleName}")
                    glossaryStatus = "詞庫更新失敗，請稍後再試。"
                }
            }
        }
    }

    fun deleteGlossaryTerm(entry: PersonalGlossaryTerm) {
        PersonalGlossaryRepository.delete(applicationContext, entry.id) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    glossaryTerms = it
                    glossaryStatus = "詞彙已刪除。"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Personal glossary delete failed: ${exception.javaClass.simpleName}")
                    glossaryStatus = "詞庫刪除失敗，請稍後再試。"
                }
            }
        }
    }

    fun addCorrectionRules(rules: List<Pair<String, String>>) {
        TextCorrectionRuleRepository.add(applicationContext, rules) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { addResult ->
                    correctionRules = addResult.rules
                    correctionStatus = if (addResult.addedCount > 0) {
                        "已新增 ${addResult.addedCount} 條修正規則。"
                    } else {
                        "沒有新增規則；原始文字可能空白或已存在。"
                    }
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Text correction rule save failed: ${exception.javaClass.simpleName}")
                    correctionStatus = "規則儲存失敗，請稍後再試。"
                }
            }
        }
    }

    fun importDefaultCorrectionRules() {
        TextCorrectionRuleRepository.importDefaults(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { importResult ->
                    correctionRules = importResult.rules
                    correctionStatus = "預設修正规則匯入完成，新增 ${importResult.addedCount} 筆。"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Default correction rules import failed: ${exception.javaClass.simpleName}")
                    correctionStatus = "預設修正规則匯入失敗，請稍後再試。"
                }
            }
        }
    }

    fun setCorrectionRuleEnabled(rule: TextCorrectionRule, enabled: Boolean) {
        TextCorrectionRuleRepository.setEnabled(applicationContext, rule.id, enabled) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    correctionRules = it
                    correctionStatus = "規則狀態已更新。"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Text correction rule update failed: ${exception.javaClass.simpleName}")
                    correctionStatus = "規則更新失敗，請稍後再試。"
                }
            }
        }
    }

    fun deleteCorrectionRule(rule: TextCorrectionRule) {
        TextCorrectionRuleRepository.delete(applicationContext, rule.id) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    correctionRules = it
                    correctionStatus = "規則已刪除。"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Text correction rule delete failed: ${exception.javaClass.simpleName}")
                    correctionStatus = "規則刪除失敗，請稍後再試。"
                }
            }
        }
    }

    fun testMcpConnection() {
        val testConfig = mcpConfig
        mcpBusy = true
        mcpStatusMessage = "正在測試連線…"
        McpContextProvider.testConnection(applicationContext, testConfig) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                mcpBusy = false
                result.onSuccess { snapshot ->
                    val updated = mcpConfigFromSnapshot(testConfig, snapshot)
                    mcpConnectionStatus = "連線成功"
                    mcpStatusMessage = "資源與提示清單已更新。"
                    saveMcpConfig(updated)
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "MCP connection test failed: ${exception.javaClass.simpleName}")
                    mcpConnectionStatus = "連線失敗"
                    mcpStatusMessage = McpFailureMessages.describe(exception)
                }
            }
        }
    }

    fun refreshMcpContext() {
        val refreshConfig = mcpConfig
        mcpBusy = true
        mcpStatusMessage = "正在重新整理 MCP Context…"
        McpContextProvider.refresh(applicationContext, refreshConfig) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                mcpBusy = false
                result.onSuccess { refreshed ->
                    mcpConnectionStatus = "連線成功"
                    mcpStatusMessage = "快取已更新，取得 ${refreshed.termCount} 個候選詞。"
                    saveMcpConfig(refreshed.config)
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "MCP context refresh failed: ${exception.javaClass.simpleName}")
                    mcpConnectionStatus = "連線失敗"
                    mcpStatusMessage = McpFailureMessages.describe(exception)
                }
            }
        }
    }

    when (destination) {
        SettingsDestination.HOME -> HomeScreen(
            modifier = modifier,
            apiKeySaved = apiKeySaved,
            hasMicrophonePermission = hasRecordAudioPermission,
            smartFormattingSettings = smartFormattingSettings,
            glossaryTerms = glossaryTerms,
            correctionRules = correctionRules,
            mcpConfig = mcpConfig,
            mcpConnectionStatus = mcpConnectionStatus,
            onOpenGroq = { destination = SettingsDestination.GROQ },
            onOpenSmartFormatting = { destination = SettingsDestination.SMART_FORMATTING },
            onOpenPersonalization = { destination = SettingsDestination.PERSONALIZATION },
            onOpenMcp = { destination = SettingsDestination.MCP },
            onRequestMicrophonePermission = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        SettingsDestination.GROQ -> GroqSettingsScreen(
            modifier = modifier,
            apiKey = apiKey,
            apiKeySaved = apiKeySaved,
            apiKeyLoaded = apiKeyLoaded,
            status = apiKeyStatus,
            onApiKeyChange = {
                apiKey = it
                apiKeySaved = false
                apiKeyStatus = "尚未儲存 API Key。"
            },
            onSave = {
                apiKeyStatus = "正在儲存 API Key…"
                GroqApiKeyStore.saveAsync(applicationContext, apiKey) { result ->
                    mainHandler.post {
                        if (!screenIsActive.get()) return@post
                        result.onSuccess {
                            apiKeySaved = true
                            apiKeyStatus = "API Key 已儲存在此 App 的私有空間。"
                        }.onFailure { exception ->
                            Log.w(MAIN_ACTIVITY_TAG, "API key save failed: ${exception.javaClass.simpleName}")
                            apiKeySaved = false
                            apiKeyStatus = "API Key 儲存失敗，請稍後再試。"
                        }
                    }
                }
            },
            onClear = {
                apiKeyStatus = "正在清除 API Key…"
                GroqApiKeyStore.saveAsync(applicationContext, "") { result ->
                    mainHandler.post {
                        if (!screenIsActive.get()) return@post
                        result.onSuccess {
                            apiKey = ""
                            apiKeySaved = false
                            apiKeyStatus = "API Key 已清除。"
                        }.onFailure { exception ->
                            Log.w(MAIN_ACTIVITY_TAG, "API key clear failed: ${exception.javaClass.simpleName}")
                            apiKeyStatus = "API Key 清除失敗，請稍後再試。"
                        }
                    }
                }
            },
            onBack = { destination = SettingsDestination.HOME }
        )

        SettingsDestination.SMART_FORMATTING -> SmartFormattingSettingsScreen(
            modifier = modifier,
            settings = smartFormattingSettings,
            loaded = smartFormattingLoaded,
            status = smartFormattingStatus,
            onSave = ::saveSmartFormattingSettings,
            onBack = { destination = SettingsDestination.HOME }
        )

        SettingsDestination.PERSONALIZATION -> PersonalizationScreen(
            modifier = modifier,
            glossaryTerms = glossaryTerms,
            glossaryLoaded = glossaryLoaded,
            glossaryStatus = glossaryStatus,
            correctionRules = correctionRules,
            correctionRulesLoaded = correctionRulesLoaded,
            correctionStatus = correctionStatus,
            onAddGlossaryTerms = ::addGlossaryTerms,
            onImportDefaultGlossary = ::importDefaultGlossary,
            onSetGlossaryTermEnabled = ::setGlossaryTermEnabled,
            onDeleteGlossaryTerm = ::deleteGlossaryTerm,
            onAddCorrectionRules = ::addCorrectionRules,
            onImportDefaultCorrectionRules = ::importDefaultCorrectionRules,
            onSetCorrectionRuleEnabled = ::setCorrectionRuleEnabled,
            onDeleteCorrectionRule = ::deleteCorrectionRule,
            onBack = { destination = SettingsDestination.HOME }
        )

        SettingsDestination.MCP -> McpSettingsScreen(
            modifier = modifier,
            config = mcpConfig,
            loaded = mcpConfigLoaded,
            connectionStatus = mcpConnectionStatus,
            statusMessage = mcpStatusMessage,
            busy = mcpBusy,
            onConfigChange = { config, status ->
                mcpConfig = config
                mcpConnectionStatus = status
                mcpStatusMessage = ""
            },
            onSave = { config, message -> saveMcpConfig(config, message) },
            onTestConnection = ::testMcpConnection,
            onRefresh = ::refreshMcpContext,
            onBack = { destination = SettingsDestination.HOME }
        )
    }
}

private fun mcpConfigFromSnapshot(config: McpConfig, snapshot: McpServerSnapshot): McpConfig {
    val availableResources = snapshot.resources.mapTo(mutableSetOf()) { it.uri }
    val supportedPrompts = snapshot.prompts.filter { it.requiredArguments.isEmpty() }
        .mapTo(mutableSetOf()) { it.name }
    return config.copy(
        serverName = snapshot.serverName,
        serverVersion = snapshot.serverVersion,
        protocolVersion = snapshot.protocolVersion,
        resourcesSupported = snapshot.resourcesSupported,
        promptsSupported = snapshot.promptsSupported,
        resources = snapshot.resources,
        prompts = snapshot.prompts,
        selectedResourceUris = config.selectedResourceUris.intersect(availableResources),
        selectedPromptNames = config.selectedPromptNames.intersect(supportedPrompts)
    )
}
