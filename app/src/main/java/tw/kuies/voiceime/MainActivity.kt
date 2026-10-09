package tw.kuies.voiceime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
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
    private var editorPackageForProfiles by mutableStateOf<String?>(null)
    private var savedSnippetLaunch by mutableStateOf<SavedSnippetManagerLaunch?>(null)
    private var savedSnippetLaunchId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorPackageForProfiles = intent.getStringExtra(EXTRA_EDITOR_PACKAGE)
        updateSavedSnippetLaunch(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            VoiceIMETheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceImeSettingsApp(
                        modifier = Modifier.padding(innerPadding),
                        currentEditorPackageName = editorPackageForProfiles,
                        savedSnippetLaunch = savedSnippetLaunch,
                        onClearSavedSnippetLaunch = { requestId ->
                            if (savedSnippetLaunch?.requestId == requestId) savedSnippetLaunch = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        editorPackageForProfiles = intent.getStringExtra(EXTRA_EDITOR_PACKAGE)
        updateSavedSnippetLaunch(intent)
    }

    private fun updateSavedSnippetLaunch(intent: Intent) {
        val action = intent.getStringExtra(EXTRA_SAVED_SNIPPET_ACTION)
            ?.let { runCatching { SavedSnippetManagerAction.valueOf(it) }.getOrNull() }
            ?: return
        savedSnippetLaunch = SavedSnippetManagerLaunch(
            requestId = ++savedSnippetLaunchId,
            action = action,
            snippetId = intent.getStringExtra(EXTRA_SAVED_SNIPPET_ID)
        )
    }

    companion object {
        const val EXTRA_EDITOR_PACKAGE = "tw.kuies.voiceime.extra.EDITOR_PACKAGE"
        const val EXTRA_SAVED_SNIPPET_ACTION = "tw.kuies.voiceime.extra.SAVED_SNIPPET_ACTION"
        const val EXTRA_SAVED_SNIPPET_ID = "tw.kuies.voiceime.extra.SAVED_SNIPPET_ID"
    }
}

private enum class SettingsDestination {
    HOME,
    GROQ,
    SMART_FORMATTING,
    APP_PROFILES,
    PERSONALIZATION,
    SAVED_SNIPPETS,
    MCP,
    HISTORY_PRIVACY
}

@Composable
private fun VoiceImeSettingsApp(
    modifier: Modifier = Modifier,
    currentEditorPackageName: String? = null,
    savedSnippetLaunch: SavedSnippetManagerLaunch? = null,
    onClearSavedSnippetLaunch: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val screenIsActive = remember { AtomicBoolean(true) }
    var installResultCode by remember { mutableStateOf<Int?>(null) }
    val installerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> installResultCode = result.resultCode }
    val currentVersionName = remember(applicationContext) {
        currentInstalledVersionName(applicationContext)
    }
    var appUpdateState by remember { mutableStateOf<AppUpdateState>(AppUpdateState.Idle) }
    val appUpdateController = remember(applicationContext, currentVersionName) {
        AppUpdateController(
            context = applicationContext,
            currentVersionName = currentVersionName,
            launchInstaller = installerLauncher::launch,
            onStateChanged = { state ->
                if (screenIsActive.get()) appUpdateState = state
            }
        )
    }
    LaunchedEffect(appUpdateController, installResultCode) {
        val resultCode = installResultCode ?: return@LaunchedEffect
        appUpdateController.onInstallResult(resultCode)
        installResultCode = null
    }

    var destination by remember { mutableStateOf(SettingsDestination.HOME) }
    var savedSnippetAction by remember { mutableStateOf<SavedSnippetManagerAction?>(null) }
    var savedSnippetId by remember { mutableStateOf<String?>(null) }
    var savedSnippetRequestId by remember { mutableStateOf<Long?>(null) }
    var savedSnippetLibrary by remember {
        mutableStateOf(SavedSnippetLibrary(emptyList(), emptyList()))
    }
    var savedSnippetStatus by remember { mutableStateOf("載入快捷短語中…") }

    LaunchedEffect(savedSnippetLaunch?.requestId) {
        savedSnippetLaunch?.let { launch ->
            savedSnippetAction = launch.action
            savedSnippetId = launch.snippetId
            savedSnippetRequestId = launch.requestId
            destination = SettingsDestination.SAVED_SNIPPETS
            onClearSavedSnippetLaunch(launch.requestId)
        }
    }
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
    var appVoiceProfiles by remember { mutableStateOf<List<AppVoiceProfile>>(emptyList()) }
    var appVoiceProfilesLoaded by remember { mutableStateOf(false) }
    var geminiApiKey by remember { mutableStateOf("") }
    var geminiApiKeySaved by remember { mutableStateOf(false) }
    var geminiApiKeyLoaded by remember { mutableStateOf(false) }
    var geminiApiKeyStatus by remember { mutableStateOf("") }
    var openAiApiKey by remember { mutableStateOf("") }
    var openAiApiKeySaved by remember { mutableStateOf(false) }
    var openAiApiKeyLoaded by remember { mutableStateOf(false) }
    var openAiApiKeyStatus by remember { mutableStateOf("") }
    var smartFormattingStatus by remember { mutableStateOf("載入智慧整理設定中…") }

    fun reloadSavedSnippets(statusMessage: String? = null) {
        SavedSnippetRepository.loadAsync(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { library ->
                    savedSnippetLibrary = library
                    savedSnippetStatus = statusMessage ?: if (library.snippets.isEmpty()) {
                        "尚未建立快捷短語。"
                    } else {
                        ""
                    }
                }.onFailure {
                    savedSnippetStatus = statusMessage ?: "快捷短語讀取失敗，請稍後再試。"
                }
            }
        }
    }

    BackHandler(enabled = destination != SettingsDestination.HOME) {
        destination = SettingsDestination.HOME
        savedSnippetAction = null
        savedSnippetId = null
        savedSnippetRequestId = null
        savedSnippetLaunch?.let { onClearSavedSnippetLaunch(it.requestId) }
    }

    DisposableEffect(applicationContext, appUpdateController) {
        screenIsActive.set(true)
        appUpdateController.start()
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
        TextFormattingApiKeyStore.readAsync(applicationContext, ExternalFormattingProvider.GEMINI) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { storedKey ->
                    geminiApiKey = storedKey.orEmpty()
                    geminiApiKeySaved = !storedKey.isNullOrBlank()
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Gemini API key read failed: ${exception.javaClass.simpleName}")
                    geminiApiKeyStatus = "API Key 讀取失敗。"
                }
                geminiApiKeyLoaded = true
            }
        }
        TextFormattingApiKeyStore.readAsync(applicationContext, ExternalFormattingProvider.OPENAI) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { storedKey ->
                    openAiApiKey = storedKey.orEmpty()
                    openAiApiKeySaved = !storedKey.isNullOrBlank()
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "OpenAI API key read failed: ${exception.javaClass.simpleName}")
                    openAiApiKeyStatus = "API Key 讀取失敗。"
                }
                openAiApiKeyLoaded = true
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
        AppVoiceProfileRepository.load(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { profiles -> appVoiceProfiles = profiles }
                    .onFailure { exception ->
                        Log.w(MAIN_ACTIVITY_TAG, "App voice profiles load failed: ${exception.javaClass.simpleName}")
                    }
                appVoiceProfilesLoaded = true
            }
        }
        reloadSavedSnippets()
        onDispose {
            screenIsActive.set(false)
            appUpdateController.close()
        }
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

    fun saveAppVoiceProfile(profile: AppVoiceProfile) {
        AppVoiceProfileRepository.upsert(applicationContext, profile) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { appVoiceProfiles = it }
                    .onFailure { exception ->
                        Log.w(MAIN_ACTIVITY_TAG, "App voice profile save failed: ${exception.javaClass.simpleName}")
                    }
            }
        }
    }

    fun setAppVoiceProfileEnabled(packageName: String, enabled: Boolean) {
        AppVoiceProfileRepository.setEnabled(applicationContext, packageName, enabled) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { appVoiceProfiles = it }
                    .onFailure { exception ->
                        Log.w(MAIN_ACTIVITY_TAG, "App voice profile update failed: ${exception.javaClass.simpleName}")
                    }
            }
        }
    }

    fun deleteAppVoiceProfile(packageName: String) {
        AppVoiceProfileRepository.delete(applicationContext, packageName) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess { appVoiceProfiles = it }
                    .onFailure { exception ->
                        Log.w(MAIN_ACTIVITY_TAG, "App voice profile delete failed: ${exception.javaClass.simpleName}")
                    }
            }
        }
    }

    fun saveSavedSnippet(id: String?, title: String, content: String, categoryId: String) {
        SavedSnippetRepository.saveAsync(
            applicationContext,
            id,
            title,
            content,
            categoryId
        ) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                reloadSavedSnippets(
                    if (result.getOrNull() == true) "快捷短語已儲存。" else "儲存失敗，請檢查標題與內容。"
                )
            }
        }
    }

    fun setSavedSnippetPinned(id: String, pinned: Boolean) {
        SavedSnippetRepository.setPinnedAsync(applicationContext, id, pinned) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                reloadSavedSnippets(if (result.isSuccess) "收藏狀態已更新。" else "收藏狀態更新失敗。")
            }
        }
    }

    fun deleteSavedSnippet(id: String) {
        SavedSnippetRepository.deleteAsync(applicationContext, id) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                reloadSavedSnippets(if (result.isSuccess) "短語已刪除。" else "短語刪除失敗。")
            }
        }
    }

    fun clearSavedSnippets() {
        SavedSnippetRepository.clearAllAsync(applicationContext) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                reloadSavedSnippets(if (result.isSuccess) "全部短語已清除。" else "清除失敗，請稍後再試。")
            }
        }
    }

    fun saveExternalApiKey(provider: ExternalFormattingProvider, value: String) {
        val isGemini = provider == ExternalFormattingProvider.GEMINI
        if (isGemini) geminiApiKeyStatus = "正在儲存 API Key…" else openAiApiKeyStatus = "正在儲存 API Key…"
        TextFormattingApiKeyStore.saveAsync(applicationContext, provider, value) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    if (isGemini) {
                        geminiApiKeySaved = value.isNotBlank()
                        geminiApiKeyStatus = if (value.isBlank()) "API Key 已清除。" else "API Key 已儲存在 App 私有設定。"
                    } else {
                        openAiApiKeySaved = value.isNotBlank()
                        openAiApiKeyStatus = if (value.isBlank()) "API Key 已清除。" else "API Key 已儲存在 App 私有設定。"
                    }
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Provider API key save failed: ${exception.javaClass.simpleName}")
                    if (isGemini) {
                        geminiApiKeySaved = false
                        geminiApiKeyStatus = "API Key 儲存失敗。"
                    } else {
                        openAiApiKeySaved = false
                        openAiApiKeyStatus = "API Key 儲存失敗。"
                    }
                }
            }
        }
    }

    fun openApiKeyPage(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (exception: Exception) {
            Log.w(MAIN_ACTIVITY_TAG, "Could not open API key page: ${exception.javaClass.simpleName}")
        }
    }

    fun openReleasePage(url: String?) {
        val officialUrl = GitHubReleaseConfig.releasePageUrlOrFallback(url)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(officialUrl)))
        } catch (exception: Exception) {
            Log.w(MAIN_ACTIVITY_TAG, "Could not open GitHub Releases: ${exception.javaClass.simpleName}")
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

    fun setGlossaryTermContextPhrases(entry: PersonalGlossaryTerm, phrases: List<String>) {
        PersonalGlossaryRepository.setCommonPhrases(applicationContext, entry.id, phrases) { result ->
            mainHandler.post {
                if (!screenIsActive.get()) return@post
                result.onSuccess {
                    glossaryTerms = it
                    glossaryStatus = "詞彙語境已更新。"
                }.onFailure { exception ->
                    Log.w(MAIN_ACTIVITY_TAG, "Personal glossary context save failed: ${exception.javaClass.simpleName}")
                    glossaryStatus = "詞彙語境儲存失敗，請稍後再試。"
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
            appVoiceProfileCount = appVoiceProfiles.size,
            glossaryTerms = glossaryTerms,
            correctionRules = correctionRules,
            mcpConfig = mcpConfig,
            mcpConnectionStatus = mcpConnectionStatus,
            currentVersionName = currentVersionName,
            updateState = appUpdateState,
            onOpenGroq = { destination = SettingsDestination.GROQ },
            onOpenSmartFormatting = { destination = SettingsDestination.SMART_FORMATTING },
            onOpenAppProfiles = { destination = SettingsDestination.APP_PROFILES },
            onOpenPersonalization = { destination = SettingsDestination.PERSONALIZATION },
            onOpenSavedSnippets = {
                savedSnippetAction = null
                savedSnippetId = null
                savedSnippetRequestId = null
                destination = SettingsDestination.SAVED_SNIPPETS
            },
            onOpenMcp = { destination = SettingsDestination.MCP },
            onOpenHistoryPrivacy = { destination = SettingsDestination.HISTORY_PRIVACY },
            onCheckForUpdates = appUpdateController::checkForUpdates,
            onDownloadUpdate = { release, version, apk ->
                appUpdateController.download(release, version, apk)
            },
            onInstallUpdate = { release, downloadId ->
                appUpdateController.install(release, downloadId)
            },
            onOpenUnknownSourcesSettings = {
                if (!appUpdateController.openUnknownSourcesSettings()) {
                    appUpdateState = AppUpdateState.Error(
                        "無法開啟授權頁，請到系統設定允許 KuiesVox 安裝未知來源應用程式。"
                    )
                }
            },
            onOpenReleases = { openReleasePage(null) },
            onOpenRelease = ::openReleasePage,
            onRequestMicrophonePermission = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        SettingsDestination.GROQ -> GroqSettingsScreen(
            modifier = modifier,
            speechModel = smartFormattingSettings.speechModel,
            onSpeechModelChange = { model ->
                saveSmartFormattingSettings(smartFormattingSettings.copy(speechModel = model))
            },
            speechLanguageMode = smartFormattingSettings.speechLanguageMode,
            onSpeechLanguageModeChange = { mode ->
                saveSmartFormattingSettings(smartFormattingSettings.copy(speechLanguageMode = mode))
            },
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
            groqApiKeySaved = apiKeySaved,
            geminiApiKey = geminiApiKey,
            geminiApiKeySaved = geminiApiKeySaved,
            geminiApiKeyLoaded = geminiApiKeyLoaded,
            geminiApiKeyStatus = geminiApiKeyStatus,
            openAiApiKey = openAiApiKey,
            openAiApiKeySaved = openAiApiKeySaved,
            openAiApiKeyLoaded = openAiApiKeyLoaded,
            openAiApiKeyStatus = openAiApiKeyStatus,
            onSave = ::saveSmartFormattingSettings,
            onGeminiApiKeyChange = {
                geminiApiKey = it
                geminiApiKeySaved = false
                geminiApiKeyStatus = "尚未儲存"
            },
            onSaveGeminiApiKey = { saveExternalApiKey(ExternalFormattingProvider.GEMINI, geminiApiKey) },
            onClearGeminiApiKey = {
                geminiApiKey = ""
                saveExternalApiKey(ExternalFormattingProvider.GEMINI, "")
            },
            onOpenAiApiKeyChange = {
                openAiApiKey = it
                openAiApiKeySaved = false
                openAiApiKeyStatus = "尚未儲存"
            },
            onSaveOpenAiApiKey = { saveExternalApiKey(ExternalFormattingProvider.OPENAI, openAiApiKey) },
            onClearOpenAiApiKey = {
                openAiApiKey = ""
                saveExternalApiKey(ExternalFormattingProvider.OPENAI, "")
            },
            onOpenGroqSettings = { destination = SettingsDestination.GROQ },
            onOpenApiKeyPage = ::openApiKeyPage,
            onBack = { destination = SettingsDestination.HOME }
        )

        SettingsDestination.APP_PROFILES -> AppVoiceProfilesScreen(
            modifier = modifier,
            profiles = appVoiceProfiles,
            loaded = appVoiceProfilesLoaded,
            globalSettings = smartFormattingSettings,
            currentEditorPackageName = currentEditorPackageName,
            onOpenGlobalSettings = { destination = SettingsDestination.SMART_FORMATTING },
            onOpenGlobalAsrSettings = { destination = SettingsDestination.GROQ },
            onSaveProfile = ::saveAppVoiceProfile,
            onSetEnabled = ::setAppVoiceProfileEnabled,
            onDeleteProfile = ::deleteAppVoiceProfile,
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
            onSetGlossaryTermContextPhrases = ::setGlossaryTermContextPhrases,
            onDeleteGlossaryTerm = ::deleteGlossaryTerm,
            onAddCorrectionRules = ::addCorrectionRules,
            onImportDefaultCorrectionRules = ::importDefaultCorrectionRules,
            onSetCorrectionRuleEnabled = ::setCorrectionRuleEnabled,
            onDeleteCorrectionRule = ::deleteCorrectionRule,
            onBack = { destination = SettingsDestination.HOME }
        )

        SettingsDestination.SAVED_SNIPPETS -> SavedSnippetsScreen(
            modifier = modifier,
            library = savedSnippetLibrary,
            status = savedSnippetStatus,
            launchRequestId = savedSnippetRequestId,
            launchAction = savedSnippetAction,
            launchSnippetId = savedSnippetId,
            onSaveSnippet = ::saveSavedSnippet,
            onSetPinned = ::setSavedSnippetPinned,
            onDeleteSnippet = ::deleteSavedSnippet,
            onClearSnippets = ::clearSavedSnippets,
            onBack = {
                destination = SettingsDestination.HOME
                savedSnippetAction = null
                savedSnippetId = null
                savedSnippetRequestId = null
                savedSnippetLaunch?.let { onClearSavedSnippetLaunch(it.requestId) }
            }
        )

        SettingsDestination.HISTORY_PRIVACY -> HistoryPrivacyScreen(
            onBack = { destination = SettingsDestination.HOME },
            modifier = modifier
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

@Suppress("DEPRECATION")
private fun currentInstalledVersionName(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "unknown" }

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
