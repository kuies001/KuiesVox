package tw.kuies.voiceime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.text.Html
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class VoiceImeService : InputMethodService() {

    companion object {
        private const val TAG = "VoiceImeService"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val AUDIO_LEVEL_UPDATE_INTERVAL_MS = 40L
        private const val AUDIO_LEVEL_PCM_CHUNK_BYTES =
            SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE / 25
        private const val TERMINAL_STATUS_DURATION_MS = 1_500L
    }

    private val stateMachine = VoiceImeStateMachine()
    private val requestGate = VoiceImeRequestGate()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var serviceDestroyed = false

    private val backspaceRepeater = BackspaceRepeater(
        postDelayed = { runnable, delay -> mainHandler.postDelayed(runnable, delay) },
        removeCallbacks = { runnable -> mainHandler.removeCallbacks(runnable) },
        onDelete = ::deleteOneBeforeCursor,
        canRepeat = { !serviceDestroyed }
    )

    @Volatile
    private var activeOperationId = 0L

    @Volatile
    private var recordingFailure: Exception? = null

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var activeAudioFile: File? = null
    private var holdToTalkRecording = false
    private val audioLevelMonitor = AudioLevelMonitor()
    private var audioLevelUpdateRunnable: Runnable? = null
    private var audioLevelUpdateOperationId = 0L
    private val activeRequestCall = ActiveRequestCall()
    private var voicePanel: VoiceImePanel? = null
    private var statusResetRunnable: Runnable? = null
    private var statusRevision = 0L
    private var statusLabelOverride: String? = null
    @Volatile
    private var currentEditorInfo: EditorInfo? = null
    private var currentEditorDetails: EditorFieldDetails? = null
    private var currentEditorConnectionIdentity: InputConnection? = null
    @Volatile
    private var editorSessionId = 0L
    @Volatile
    private var activeVoiceOperationSnapshot: VoiceOperationSnapshot? = null
    @Volatile
    private var currentResolvedAppSettings = ResolvedAppVoiceSettings(
        settings = SmartFormattingSettings(),
        formattingStyle = TextFormattingStyle.DAILY,
        appliedProfile = null
    )
    private var profileCacheInitialized = false
    private var profileCachePackageName: String? = null
    private var profileCacheGlobalSettings = SmartFormattingSettings()
    private var profileCacheProfiles: List<AppVoiceProfile> = emptyList()
    @Volatile
    private var activeRawTranscript: String? = null

    /** AI 編輯：進入模式時建立的選取文字快照，只在當次工作記憶體中存活。 */
    @Volatile
    private var activeSelectedTextSnapshot: SelectedTextSnapshot? = null

    /** AI 編輯：等待使用者確認的改寫結果。 */
    @Volatile
    private var activeAiEditResult: String? = null

    /** 使用者手動啟動的格式指令／AI 編輯模式；每次處理結束後自動回到 NORMAL。 */
    @Volatile
    private var inputMode = VoiceInputMode.NORMAL
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        mainHandler.post { captureCurrentClipboard() }
    }
    private val clipboardListenerLifecycle = ClipboardListenerLifecycle(
        registerListener = {
            clipboardManager()?.let { manager ->
                manager.addPrimaryClipChangedListener(clipboardListener)
                true
            } ?: false
        },
        unregisterListener = {
            clipboardManager()?.removePrimaryClipChangedListener(clipboardListener)
        }
    )

    override fun onCreateInputView(): View {
        sweepStaleAudioTempFiles()
        val panel = VoiceImePanel(
            context = this,
            onVoiceAction = {
                when (stateMachine.state) {
                    VoiceImeState.RECORDING -> stopAndTranscribe()
                    VoiceImeState.TRANSCRIBING, VoiceImeState.FORMATTING -> Unit
                    else -> {
                        resetTerminalStatus()
                        startRecording()
                    }
                }
            },
            onCancel = ::cancelCurrentOperation,
            onSwitchInputMethod = { switchToNextInputMethod(false) },
            onEnter = ::insertNewline,
            onDelete = ::deleteOneBeforeCursor,
            onBackspacePressed = backspaceRepeater::start,
            onBackspaceReleased = backspaceRepeater::stop,
            onOpenSettings = ::openSettings,
            onSelectAll = ::selectAllInputText,
            onClearAll = ::clearAllInputText,
            onOpenClipboardHistory = ::openClipboardHistory,
            onOpenVoiceHistory = ::openVoiceHistory,
            onHoldToTalkStart = ::startHoldToTalkRecording,
            onHoldToTalkRelease = ::releaseHoldToTalkRecording,
            onHoldToTalkCancel = ::cancelHoldToTalkRecording,
            onInsertHistoryText = ::insertHistoryText,
            onCopyHistoryText = ::copyHistoryText,
            onPinClipboardItem = ::setClipboardPinned,
            onDeleteClipboardItem = ::deleteClipboardHistoryItem,
            onClearUnpinnedClipboard = ::clearUnpinnedClipboardHistory,
            onDeleteVoiceHistoryItem = ::deleteVoiceHistoryItem,
            onClearVoiceHistory = ::clearVoiceHistory,
            onOpenSavedSnippets = ::openSavedSnippets,
            onInsertSavedSnippet = ::insertSavedSnippet,
            onSetSavedSnippetPinned = ::setSavedSnippetPinned,
            onDeleteSavedSnippets = ::deleteSavedSnippets,
            onClearSavedSnippets = ::clearSavedSnippets,
            onManageSavedSnippets = ::openSavedSnippetManager,
            onIsSensitiveEditor = ::isCurrentEditorSensitive,
            onEnterFormatCommandMode = ::enterFormatCommandMode,
            onExitMode = ::exitSpecialMode,
            onEnterAiEditMode = ::enterAiEditMode,
            onAiEditConfirmReplace = ::confirmAiEditReplacement
        )
        voicePanel = panel
        panel.setSwitchAvailable(shouldOfferSwitchingToNextInputMethod())
        renderStatus()
        return panel.view
    }

    /**
     * 啟動時清掉上次異常結束（程序被殺）留下的過期錄音暫存檔。
     * 只處理 app 私有 cacheDir 內、本 App 命名且已明顯過期的檔案，進行中的錄音不會被選中。
     */
    private fun sweepStaleAudioTempFiles() {
        runCatching {
            val files = cacheDir.listFiles()?.toList().orEmpty()
            // 正在錄音／辨識或等待重試的檔案一定不在清掃範圍內。
            val inUse = setOfNotNull(activeAudioFile?.name)
            AudioTempFiles.expiredFiles(files, System.currentTimeMillis(), inUseNames = inUse)
                .forEach { file ->
                if (!file.delete()) Log.w(TAG, "Stale WAV cleanup failed")
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        voicePanel?.resetSavedSnippetTransientState()
        updateEditorInfo(info, restarting = true, fromInputView = true)
        voicePanel?.setSwitchAvailable(shouldOfferSwitchingToNextInputMethod())
        if (isCurrentEditorSensitive()) {
            when {
                voicePanel?.isShowingClipboardHistory() == true ->
                    voicePanel?.showClipboardHistoryPanel(isSensitiveEditor = true)
                voicePanel?.isShowingVoiceHistory() == true ->
                    voicePanel?.showVoiceHistoryPanel(isSensitiveEditor = true)
                voicePanel?.isShowingSavedSnippets() == true ->
                    voicePanel?.hideSavedSnippetsForPrivacy()
            }
        }
        registerClipboardListenerForSafeEditor()
        captureCurrentClipboard(allowWithoutListener = true)
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        voicePanel?.resetSavedSnippetTransientState()
        updateEditorInfo(attribute, restarting, fromInputView = false)
    }

    override fun onFinishInput() {
        voicePanel?.resetSavedSnippetTransientState()
        if (activeOperationId != 0L) cancelCurrentOperation()
        inputMode = VoiceInputMode.NORMAL
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        voicePanel?.hideAiEditPreview()
        editorSessionId += 1
        unregisterClipboardListener()
        currentEditorInfo = null
        currentEditorDetails = null
        currentEditorConnectionIdentity = null
        super.onFinishInput()
    }

    private fun updateEditorInfo(info: EditorInfo, restarting: Boolean, fromInputView: Boolean) {
        val previous = currentEditorDetails
        val incoming = EditorFieldDetails.from(info)
        val incomingConnection = currentInputConnection
        val packageChanged = previous?.packageName != incoming.packageName
        val editorDetailsChanged = previous != null && previous != incoming
        val connectionChanged = previous != null && currentEditorConnectionIdentity !== incomingConnection
        val targetChanged = when {
            previous == null -> true
            packageChanged || editorDetailsChanged || connectionChanged -> true
            !fromInputView && !restarting -> true
            else -> false
        }
        if (targetChanged) {
            if (activeOperationId != 0L) cancelCurrentOperation()
            editorSessionId += 1
        }
        currentEditorInfo = info
        currentEditorDetails = incoming
        currentEditorConnectionIdentity = incomingConnection
        resolveCurrentEditorSettings(info)
    }

    private fun resolveCurrentEditorSettings(info: EditorInfo) {
        val packageName = AppPackageName.normalize(info.packageName)
        if (!profileCacheInitialized || profileCachePackageName != packageName) {
            profileCacheInitialized = true
            profileCachePackageName = packageName
            profileCacheGlobalSettings = runCatching {
                SmartFormattingSettingsRepository.loadSync(applicationContext)
            }.getOrElse { exception ->
                Log.w(TAG, "Global settings unavailable for editor: ${exception.javaClass.simpleName}")
                SmartFormattingSettings()
            }
            profileCacheProfiles = runCatching {
                AppVoiceProfileRepository.loadSync(applicationContext)
            }.getOrElse { exception ->
                Log.w(TAG, "App profiles unavailable for editor: ${exception.javaClass.simpleName}")
                emptyList()
            }
        }
        val sensitive = EditorPrivacyPolicy.isSensitive(info.inputType, info.imeOptions)
        currentResolvedAppSettings = AppVoiceProfilePolicy.resolve(
            packageName = packageName,
            profiles = profileCacheProfiles,
            globalSettings = profileCacheGlobalSettings,
            sensitiveEditor = sensitive
        )
    }

    private fun openSettings() {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE || activeOperationId != 0L) return
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_EDITOR_PACKAGE, AppPackageName.normalize(currentEditorInfo?.packageName))
                    .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Settings activity could not be opened: ${exception.javaClass.simpleName}")
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        voicePanel?.resetSavedSnippetTransientState()
        voicePanel?.cancelHoldToTalkGesture()
        inputMode = VoiceInputMode.NORMAL
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        voicePanel?.hideAiEditPreview()
        if (stateMachine.state == VoiceImeState.RECORDING ||
            stateMachine.state == VoiceImeState.AWAITING_CONFIRM
        ) {
            cancelCurrentOperation()
        } else {
            stopAudioLevelFeedback()
        }
        backspaceRepeater.stop()
        unregisterClipboardListener()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        voicePanel?.resetSavedSnippetTransientState()
        super.onWindowHidden()
    }

    private fun isCurrentEditorSensitive(): Boolean {
        val info = currentEditorInfo ?: return true
        return EditorPrivacyPolicy.isSensitive(info.inputType, info.imeOptions)
    }

    private fun clipboardManager(): ClipboardManager? =
        getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager

    private fun registerClipboardListenerForSafeEditor() {
        unregisterClipboardListener()
        val shouldRegister = SystemClipboardCapturePolicy.shouldRegisterListener(
            serviceDestroyed,
            isCurrentEditorSensitive()
        )
        try {
            clipboardListenerLifecycle.registerIfAllowed(shouldRegister)
        } catch (exception: Exception) {
            Log.w(TAG, "Clipboard listener unavailable: ${exception.javaClass.simpleName}")
        }
    }

    private fun unregisterClipboardListener() {
        try {
            clipboardListenerLifecycle.unregister()
        } catch (exception: Exception) {
            Log.w(TAG, "Clipboard listener release failed: ${exception.javaClass.simpleName}")
        }
    }

    private fun captureCurrentClipboard(allowWithoutListener: Boolean = false) {
        val sensitiveEditor = isCurrentEditorSensitive()
        if (!SystemClipboardCapturePolicy.shouldReadClipboard(
                serviceDestroyed,
                clipboardListenerLifecycle.isRegistered,
                sensitiveEditor,
                allowWithoutListener
            )
        ) return
        val manager = clipboardManager() ?: return
        val clip = try {
            manager.primaryClip
        } catch (exception: Exception) {
            Log.w(TAG, "Clipboard read unavailable: ${exception.javaClass.simpleName}")
            return
        } ?: return

        val description = clip.description
        val isSensitiveClip = Build.VERSION.SDK_INT >= 33 &&
            description.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        val hasPlainText = description.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
        val hasHtmlText = description.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML)
        if (!SystemClipboardCapturePolicy.canCaptureClip(
                clip.itemCount,
                hasPlainText,
                hasHtmlText,
                isSensitiveClip,
                sensitiveEditor
            )
        ) {
            return
        }
        val item = try {
            clip.getItemAt(0)
        } catch (_: Exception) {
            return
        }
        val text = try {
            item.text?.toString()?.takeIf { it.isNotBlank() } ?: item.htmlText?.let {
                Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Clipboard text conversion failed: ${exception.javaClass.simpleName}")
            null
        } ?: return
        if (isCurrentEditorSensitive()) return
        UserHistoryRepository.recordClipboardAsync(
            applicationContext,
            text,
            isSensitiveEditor = false
        ) { result ->
            if (result.isFailure) {
                logHistoryFailure("clipboard_record", result.exceptionOrNull())
            } else {
                mainHandler.post {
                    if (voicePanel?.isShowingClipboardHistory() == true) refreshClipboardHistory()
                }
            }
        }
    }

    private fun openClipboardHistory() {
        if (isCurrentEditorSensitive()) return
        captureCurrentClipboard(allowWithoutListener = true)
        refreshClipboardHistory()
    }

    private fun refreshClipboardHistory() {
        if (isCurrentEditorSensitive()) {
            mainHandler.post {
                if (voicePanel?.isShowingClipboardHistory() == true) {
                    voicePanel?.updateClipboardHistory(emptyList(), "此欄位不顯示剪貼簿內容")
                }
            }
            return
        }
        UserHistoryRepository.loadClipboardAsync(applicationContext) { result ->
            mainHandler.post {
                val panel = voicePanel ?: return@post
                if (!panel.isShowingClipboardHistory()) return@post
                if (isCurrentEditorSensitive()) {
                    panel.updateClipboardHistory(emptyList(), "此欄位不顯示剪貼簿內容")
                } else {
                    panel.updateClipboardHistory(
                        result.getOrElse { emptyList() },
                        if (result.isFailure) "無法載入剪貼簿歷史，請稍後再試" else null
                    )
                }
            }
        }
    }

    private fun openVoiceHistory() {
        if (isCurrentEditorSensitive()) return
        UserHistoryRepository.loadVoiceAsync(applicationContext) { result ->
            mainHandler.post {
                val panel = voicePanel ?: return@post
                if (!panel.isShowingVoiceHistory()) return@post
                if (isCurrentEditorSensitive()) {
                    panel.updateVoiceHistory(emptyList(), "此欄位不顯示語音歷史")
                } else {
                    panel.updateVoiceHistory(
                        result.getOrElse { emptyList() },
                        if (result.isFailure) "無法載入語音歷史，請稍後再試" else null
                    )
                }
            }
        }
    }

    private fun insertHistoryText(text: String) {
        if (serviceDestroyed || isCurrentEditorSensitive() || text.isEmpty()) return
        val connection = try {
            currentInputConnection
        } catch (exception: Exception) {
            Log.w(TAG, "History input connection unavailable: ${exception.javaClass.simpleName}")
            null
        } ?: return
        try {
            if (!connection.commitText(text, 1)) Log.w(TAG, "History insertion unavailable")
        } catch (exception: Exception) {
            Log.w(TAG, "History insertion failed: ${exception.javaClass.simpleName}")
        }
    }

    private fun copyHistoryText(text: String) {
        if (serviceDestroyed) return
        try {
            clipboardManager()?.setPrimaryClip(ClipData.newPlainText("KuiesVox history", text))
        } catch (exception: Exception) {
            Log.w(TAG, "History copy failed: ${exception.javaClass.simpleName}")
        }
    }

    private fun setClipboardPinned(id: String, pinned: Boolean) {
        UserHistoryRepository.setClipboardPinnedAsync(applicationContext, id, pinned) { result ->
            if (result.isFailure) logHistoryFailure("clipboard_pin", result.exceptionOrNull())
            refreshClipboardHistory()
        }
    }

    private fun deleteClipboardHistoryItem(id: String) {
        UserHistoryRepository.deleteClipboardAsync(applicationContext, id) { result ->
            if (result.isFailure) logHistoryFailure("clipboard_delete", result.exceptionOrNull())
            refreshClipboardHistory()
        }
    }

    private fun clearUnpinnedClipboardHistory() {
        UserHistoryRepository.clearUnpinnedClipboardAsync(applicationContext) { result ->
            if (result.isFailure) logHistoryFailure("clipboard_clear_unpinned", result.exceptionOrNull())
            refreshClipboardHistory()
        }
    }

    private fun deleteVoiceHistoryItem(id: Long) {
        UserHistoryRepository.deleteVoiceAsync(applicationContext, id) { result ->
            if (result.isFailure) logHistoryFailure("voice_history_delete", result.exceptionOrNull())
            refreshVoiceHistory()
        }
    }

    private fun clearVoiceHistory() {
        UserHistoryRepository.clearVoiceAsync(applicationContext) { result ->
            if (result.isFailure) logHistoryFailure("voice_history_clear", result.exceptionOrNull())
            refreshVoiceHistory()
        }
    }

    private fun refreshVoiceHistory() {
        if (isCurrentEditorSensitive()) return
        UserHistoryRepository.loadVoiceAsync(applicationContext) { result ->
            mainHandler.post {
                val panel = voicePanel ?: return@post
                if (!panel.isShowingVoiceHistory()) return@post
                if (isCurrentEditorSensitive()) {
                    panel.updateVoiceHistory(emptyList(), "此欄位不顯示語音歷史")
                } else {
                    panel.updateVoiceHistory(
                        result.getOrElse { emptyList() },
                        if (result.isFailure) "無法載入語音歷史，請稍後再試" else null
                    )
                }
            }
        }
    }

    private fun openSavedSnippets() {
        if (isCurrentEditorSensitive()) {
            voicePanel?.hideSavedSnippetsForPrivacy()
            return
        }
        refreshSavedSnippets()
    }

    private fun refreshSavedSnippets(statusMessage: String? = null) {
        if (isCurrentEditorSensitive()) {
            voicePanel?.hideSavedSnippetsForPrivacy()
            return
        }
        SavedSnippetRepository.loadAsync(applicationContext) { result ->
            mainHandler.post {
                val panel = voicePanel ?: return@post
                if (!panel.isShowingSavedSnippets()) return@post
                if (isCurrentEditorSensitive()) {
                    panel.hideSavedSnippetsForPrivacy()
                    return@post
                }
                val library = result.getOrElse { SavedSnippetLibrary(emptyList(), emptyList()) }
                val message = statusMessage ?: if (result.isFailure) {
                    "無法載入快捷短語，請稍後再試"
                } else {
                    null
                }
                panel.updateSavedSnippets(library, message)
            }
        }
    }

    private fun insertSavedSnippet(snippet: SavedSnippet): Boolean {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE) return false
        if (isCurrentEditorSensitive()) {
            voicePanel?.hideSavedSnippetsForPrivacy()
            return false
        }
        val connection = try {
            currentInputConnection
        } catch (_: Exception) {
            null
        } ?: return false
        val target = SnippetCommitTarget { text, newCursorPosition ->
            connection.commitText(text, newCursorPosition)
        }
        return SavedSnippetInsertion.insert(target, snippet.content) {
            SavedSnippetRepository.markUsedAsync(applicationContext, snippet.id)
        }
    }

    private fun openSavedSnippetManager(action: SavedSnippetManagerAction, snippetId: String?) {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE || isCurrentEditorSensitive()) {
            if (isCurrentEditorSensitive()) voicePanel?.hideSavedSnippetsForPrivacy()
            return
        }
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SAVED_SNIPPET_ACTION, action.name)
                    .putExtra(MainActivity.EXTRA_SAVED_SNIPPET_ID, snippetId)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Saved snippet manager could not be opened: ${exception.javaClass.simpleName}")
        }
    }

    private fun setSavedSnippetPinned(id: String, pinned: Boolean) {
        if (isCurrentEditorSensitive()) return
        SavedSnippetRepository.setPinnedAsync(applicationContext, id, pinned) { result ->
            mainHandler.post {
                if (isCurrentEditorSensitive()) voicePanel?.hideSavedSnippetsForPrivacy()
                else refreshSavedSnippets(if (result.isFailure) "收藏狀態更新失敗" else null)
            }
        }
    }

    private fun deleteSavedSnippets(ids: Set<String>) {
        if (ids.isEmpty()) return
        if (isCurrentEditorSensitive()) return
        SavedSnippetRepository.deleteManyAsync(applicationContext, ids) { result ->
            mainHandler.post {
                if (isCurrentEditorSensitive()) voicePanel?.hideSavedSnippetsForPrivacy()
                else refreshSavedSnippets(if (result.isFailure) "短語刪除失敗" else null)
            }
        }
    }

    private fun clearSavedSnippets() {
        if (isCurrentEditorSensitive()) return
        SavedSnippetRepository.clearAllAsync(applicationContext) { result ->
            mainHandler.post {
                if (isCurrentEditorSensitive()) voicePanel?.hideSavedSnippetsForPrivacy()
                else refreshSavedSnippets(if (result.isFailure) "清除短語失敗" else null)
            }
        }
    }

    private fun logHistoryFailure(operation: String, exception: Throwable?) {
        val type = exception?.javaClass?.simpleName ?: "unknown"
        Log.w(TAG, "History operation=$operation failure=$type")
    }

    private fun deleteOneBeforeCursor() {
        if (serviceDestroyed) return
        val inputConnection = try {
            currentInputConnection
        } catch (exception: Exception) {
            Log.w(TAG, "Backspace input connection unavailable: ${exception.javaClass.simpleName}")
            null
        } ?: return

        try {
            BackspaceDeletion.deleteOne(inputConnection.asBackspaceInputConnection())
        } catch (exception: Exception) {
            Log.w(TAG, "Backspace failed: ${exception.javaClass.simpleName}")
        }
    }

    private fun insertNewline() {
        if (serviceDestroyed) return
        val inputConnection = try {
            currentInputConnection
        } catch (exception: Exception) {
            Log.w(TAG, "Enter input connection unavailable: ${exception.javaClass.simpleName}")
            null
        } ?: return

        val inserted = EnterKeyInsertion.insertNewline(object : EnterInputConnection {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean =
                inputConnection.commitText(text, newCursorPosition)

            override fun sendEnterKey(): Boolean {
                val down = try {
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                } catch (_: Exception) {
                    false
                }
                val up = try {
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                } catch (_: Exception) {
                    false
                }
                return down || up
            }
        })
        if (!inserted) Log.w(TAG, "Enter insertion unavailable")
    }

    private fun selectAllInputText() = performBulkTextAction(clearAll = false)

    private fun clearAllInputText() = performBulkTextAction(clearAll = true)

    private fun performBulkTextAction(clearAll: Boolean) {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE) return
        val inputConnection = try {
            currentInputConnection
        } catch (exception: Exception) {
            Log.w(TAG, "Bulk text input connection unavailable: ${exception.javaClass.simpleName}")
            null
        } ?: return

        val canInspectText = canInspectEditorText(currentEditorInfo)
        val connection = inputConnection.asBulkEditInputConnection()
        val succeeded = if (clearAll) {
            BulkTextEditing.clearAll(connection, allowFullTextFallback = canInspectText)
        } else {
            BulkTextEditing.selectAll(connection, allowFullTextFallback = canInspectText)
        }
        if (!succeeded) {
            Log.w(TAG, if (clearAll) "Clear-all action unavailable" else "Select-all action unavailable")
        }
    }

    private fun canInspectEditorText(editorInfo: EditorInfo?): Boolean {
        editorInfo ?: return false
        if (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false

        val inputClass = editorInfo.inputType and InputType.TYPE_MASK_CLASS
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation !in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            )
            InputType.TYPE_CLASS_NUMBER -> variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun InputConnection.asBulkEditInputConnection(): BulkEditInputConnection =
        object : BulkEditInputConnection {
            override fun performSelectAll(): Boolean =
                this@asBulkEditInputConnection.performContextMenuAction(android.R.id.selectAll)

            override fun getFullTextLength(): Int? {
                val extracted = this@asBulkEditInputConnection.getExtractedText(
                    ExtractedTextRequest(),
                    0
                ) ?: return null
                val text = extracted.text ?: return null
                if (extracted.startOffset != 0 || extracted.partialStartOffset != -1 ||
                    extracted.partialEndOffset != -1
                ) {
                    return null
                }
                return text.length
            }

            override fun setSelection(start: Int, end: Int): Boolean =
                this@asBulkEditInputConnection.setSelection(start, end)

            override fun sendSelectAllShortcut(): Boolean =
                this@asBulkEditInputConnection.sendKeyPair(
                    keyCode = KeyEvent.KEYCODE_A,
                    metaState = KeyEvent.META_CTRL_ON
                )

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean =
                this@asBulkEditInputConnection.commitText(text, newCursorPosition)

            override fun sendDeleteKey(): Boolean =
                this@asBulkEditInputConnection.sendKeyPair(KeyEvent.KEYCODE_DEL)
        }

    private fun InputConnection.sendKeyPair(
        keyCode: Int,
        metaState: Int = 0
    ): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = try {
            sendKeyEvent(
                KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
            )
        } catch (_: Exception) {
            false
        }
        val upTime = SystemClock.uptimeMillis()
        val up = try {
            sendKeyEvent(
                KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, keyCode, 0, metaState)
            )
        } catch (_: Exception) {
            false
        }
        return down || up
    }

    private fun InputConnection.asBackspaceInputConnection(): BackspaceInputConnection =
        object : BackspaceInputConnection {
            override fun getSelectedText(): CharSequence? =
                this@asBackspaceInputConnection.getSelectedText(0)

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean =
                this@asBackspaceInputConnection.commitText(text, newCursorPosition)

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int
            ): Boolean = this@asBackspaceInputConnection.deleteSurroundingTextInCodePoints(
                beforeLength,
                afterLength
            )

            override fun getTextBeforeCursor(maxChars: Int): CharSequence? =
                this@asBackspaceInputConnection.getTextBeforeCursor(maxChars, 0)

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
                this@asBackspaceInputConnection.deleteSurroundingText(beforeLength, afterLength)

            override fun sendDeleteKey(): Boolean {
                val down = this@asBackspaceInputConnection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                )
                val up = this@asBackspaceInputConnection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
                )
                return down || up
            }
        }

    private fun startHoldToTalkRecording(): Boolean {
        if (serviceDestroyed || stateMachine.state == VoiceImeState.RECORDING ||
            stateMachine.state == VoiceImeState.TRANSCRIBING ||
            stateMachine.state == VoiceImeState.FORMATTING
        ) return false
        resetTerminalStatus()
        return startRecording(isHoldToTalk = true)
    }

    private fun releaseHoldToTalkRecording() {
        if (serviceDestroyed || !holdToTalkRecording) return
        holdToTalkRecording = false
        stopAndTranscribe()
    }

    private fun cancelHoldToTalkRecording() {
        if (holdToTalkRecording) cancelCurrentOperation()
    }

    private fun startRecording(isHoldToTalk: Boolean = false): Boolean {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE || audioRecord != null) {
            return false
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Recording unavailable: microphone_permission_missing")
            transitionStatus(VoiceImeState.ERROR)
            return false
        }

        // AI 編輯：收音前重新讀取選取文字，避免用進入模式時的舊快照。
        if (inputMode == VoiceInputMode.AI_EDIT) {
            val refreshed = readCurrentSelectionSnapshot()
            if (refreshed == null) {
                Log.w(TAG, "AI edit recording refused: selection unavailable")
                transitionStatus(VoiceImeState.ERROR, "請重新選取要修改的文字")
                return false
            }
            activeSelectedTextSnapshot = refreshed
        }

        val voiceSnapshot = createVoiceOperationSnapshot()

        var newRecorder: AudioRecord? = null
        var outputFile: File? = null
        try {
            activeRawTranscript = null
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize <= 0) throw IOException("unsupported_audio_format")

            val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE) * 2
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()
            newRecorder = recorder
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("audio_recorder_initialization_failed")
            }

            val file = File.createTempFile("voice-", ".wav", cacheDir)
            outputFile = file
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("audio_recorder_start_failed")
            }

            val operationId = requestGate.begin()
            activeOperationId = operationId
            activeVoiceOperationSnapshot = voiceSnapshot
            activeAudioFile = file
            recordingFailure = null
            audioRecord = recorder
            holdToTalkRecording = isHoldToTalk
            transitionStatus(
                VoiceImeState.RECORDING,
                if (isHoldToTalk) "放開即辨識" else null
            )
            startAudioLevelUpdates(operationId)
            recordingThread = Thread(
                { capturePcmWav(recorder, file, bufferSize, operationId) },
                "VoiceImeAudioRecorder"
            ).apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Recording started: ${file.absolutePath}")
            return true
        } catch (exception: Exception) {
            Log.e(TAG, "Recording start failed: ${exception.javaClass.simpleName}")
            holdToTalkRecording = false
            releaseRecorder(newRecorder)
            audioRecord = null
            recordingThread = null
            outputFile?.delete()
            if (activeOperationId != 0L) {
                finishWithError(activeOperationId, "recording_start_failed")
            } else {
                activeAudioFile = null
                transitionStatus(VoiceImeState.ERROR)
            }
            return false
        }
    }

    private fun capturePcmWav(recorder: AudioRecord, file: File, bufferSize: Int, operationId: Long) {
        var failure: Exception? = null
        try {
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(0)
                writeWavHeader(output, 0)
                val buffer = ByteArray(bufferSize)
                var dataSize = 0L
                while (isOperationCurrent(operationId) && stateMachine.state == VoiceImeState.RECORDING) {
                    // Read 40 ms frames for the meter; every returned byte is still written unchanged to the WAV.
                    val bytesRead = recorder.read(
                        buffer,
                        0,
                        minOf(buffer.size, AUDIO_LEVEL_PCM_CHUNK_BYTES)
                    )
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        dataSize += bytesRead
                        if (stateMachine.state == VoiceImeState.RECORDING &&
                            isOperationCurrent(operationId)
                        ) {
                            audioLevelMonitor.observe(operationId, buffer, bytesRead)
                        }
                    } else if (!isOperationCurrent(operationId) ||
                        stateMachine.state != VoiceImeState.RECORDING
                    ) {
                        break
                    } else if (bytesRead < 0) {
                        throw IOException("audio_record_read_failed")
                    }
                }
                output.seek(0)
                writeWavHeader(output, dataSize)
            }
        } catch (exception: Exception) {
            failure = exception
        }

        if (failure != null) {
            audioLevelMonitor.stop()
            recordingFailure = failure
            Log.e(TAG, "Recording failed: ${failure.javaClass.simpleName}")
            mainHandler.post {
                if (isOperationCurrent(operationId) && stateMachine.state == VoiceImeState.RECORDING) {
                    finishWithError(operationId, "recording_failed")
                }
            }
        } else if (isOperationCurrent(operationId)) {
            Log.i(TAG, "Recording completed: ${file.absolutePath}, ${file.length()} bytes")
        }
    }

    private fun stopAndTranscribe() {
        val operationId = activeOperationId
        val completedFile = activeAudioFile
        if (operationId == 0L || completedFile == null) return
        holdToTalkRecording = false
        if (!transitionStatus(VoiceImeState.TRANSCRIBING)) return

        stopRecorderAndJoin()
        if (!isVoiceOperationTargetCurrent(operationId)) return
        if (recordingFailure != null) {
            finishWithError(operationId, "recording_failed")
            return
        }
        if (!completedFile.isFile || completedFile.length() <= 44L) {
            finishWithError(operationId, "empty_recording")
            return
        }
        transcribeRecording(completedFile, operationId)
    }

    private fun cancelCurrentOperation() {
        val state = stateMachine.state
        if (state != VoiceImeState.RECORDING && state != VoiceImeState.TRANSCRIBING &&
            state != VoiceImeState.FORMATTING && state != VoiceImeState.AWAITING_CONFIRM
        ) return

        val file = activeAudioFile
        holdToTalkRecording = false
        requestGate.invalidate()
        activeOperationId = 0L
        activeVoiceOperationSnapshot = null
        activeRequestCall.cancel()
        activeRawTranscript = null
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        inputMode = VoiceInputMode.NORMAL
        voicePanel?.hideAiEditPreview()
        transitionStatus(VoiceImeState.CANCELLED)
        stopRecorderAndJoin()
        recordingFailure = null
        activeAudioFile = null
        deleteAudioFile(file)
    }

    private fun stopRecorderAndJoin() {
        val recorder = audioRecord
        val thread = recordingThread
        audioRecord = null
        recordingThread = null

        try {
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (exception: Exception) {
            Log.w(TAG, "AudioRecord stop failed: ${exception.javaClass.simpleName}")
        }
        try {
            thread?.join(2_000)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Audio capture join interrupted")
        }
        if (thread?.isAlive == true) {
            releaseRecorder(recorder)
            try {
                thread.join(1_000)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Audio capture release join interrupted")
            }
            if (thread.isAlive) recordingFailure = IOException("audio_capture_thread_stuck")
        } else {
            releaseRecorder(recorder)
        }
    }

    private fun releaseRecorder(recorder: AudioRecord?) {
        try {
            recorder?.release()
        } catch (exception: Exception) {
            Log.w(TAG, "AudioRecord release failed: ${exception.javaClass.simpleName}")
        }
    }

    private fun transcribeRecording(file: File, operationId: Long) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        try {
            GroqApiKeyStore.readAsync(applicationContext) { keyResult ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    if (keyResult.isFailure) {
                        val errorType = keyResult.exceptionOrNull()?.javaClass?.simpleName ?: "unknown"
                        Log.e(TAG, "Groq API key read failed: $errorType")
                        finishWithError(operationId, "api_key_read_failed")
                        return@post
                    }
                    val apiKey = keyResult.getOrNull()
                    if (apiKey.isNullOrBlank()) {
                        Log.w(TAG, "Groq transcription skipped: missing_api_key")
                        finishWithError(operationId, "missing_api_key")
                        return@post
                    }
                    loadGlossaryAndTranscribe(file, operationId, apiKey)
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "API key read request failed: ${exception.javaClass.simpleName}")
            finishWithError(operationId, "api_key_read_failed")
        }
    }

    private fun createVoiceOperationSnapshot(): VoiceOperationSnapshot {
        val editorInfo = currentEditorInfo
        val sensitive = editorInfo == null || EditorPrivacyPolicy.isSensitive(
            editorInfo.inputType,
            editorInfo.imeOptions
        )
        val resolved = runCatching {
            val globalSettings = SmartFormattingSettingsRepository.loadSync(applicationContext)
            val profiles = if (sensitive) emptyList() else AppVoiceProfileRepository.loadSync(applicationContext)
            AppVoiceProfilePolicy.resolve(
                packageName = editorInfo?.packageName,
                profiles = profiles,
                globalSettings = globalSettings,
                sensitiveEditor = sensitive
            )
        }.getOrElse { exception ->
            Log.w(TAG, "Voice settings snapshot failed: ${exception.javaClass.simpleName}")
            if (sensitive) {
                currentResolvedAppSettings.copy(
                    settings = currentResolvedAppSettings.settings.copy(contextualCorrectionEnabled = false),
                    appliedProfile = null
                )
            } else {
                currentResolvedAppSettings
            }
        }
        currentResolvedAppSettings = resolved
        return VoiceOperationSnapshot(
            editorTarget = currentEditorTargetKey(),
            inputConnection = currentInputConnection,
            inputType = editorInfo?.inputType ?: 0,
            imeOptions = editorInfo?.imeOptions ?: 0,
            sensitiveEditor = sensitive,
            settings = resolved.settings,
            formattingStyle = resolved.formattingStyle,
            inputMode = inputMode
        )
    }

    /** 目前編輯器欄位的識別，供快照比對與安全政策使用。 */
    private fun currentEditorTargetKey(): VoiceEditorTargetKey {
        val editorInfo = currentEditorInfo
        return VoiceEditorTargetKey(
            sessionId = editorSessionId,
            packageName = AppPackageName.normalize(editorInfo?.packageName),
            fieldId = editorInfo?.fieldId ?: 0,
            fieldName = editorInfo?.fieldName,
            inputType = editorInfo?.inputType ?: 0,
            imeOptions = editorInfo?.imeOptions ?: 0,
            connectionIdentity = currentInputConnection
        )
    }

    private fun isOperationTargetCurrent(snapshot: VoiceOperationSnapshot): Boolean {
        currentEditorInfo ?: return false
        return VoiceEditorTargetPolicy.stillTargetsSameEditor(snapshot.editorTarget, currentEditorTargetKey())
    }

    private fun isVoiceOperationTargetCurrent(operationId: Long): Boolean {
        if (!isOperationCurrent(operationId)) return false
        val snapshot = activeVoiceOperationSnapshot ?: return false
        if (isOperationTargetCurrent(snapshot)) return true
        cancelCurrentOperation()
        return false
    }

    private fun loadGlossaryAndTranscribe(file: File, operationId: Long, apiKey: String) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        if (activeVoiceOperationSnapshot?.sensitiveEditor != false) {
            enqueueTranscription(file, operationId, apiKey, emptyList(), emptyList())
            return
        }
        try {
            PersonalGlossaryRepository.load(applicationContext) { result ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    val localGlossary = result.getOrNull().orEmpty()
                    if (result.isFailure) {
                        val errorType = result.exceptionOrNull()?.javaClass?.simpleName ?: "unknown"
                        Log.w(TAG, "Personal glossary read failed; continuing without prompt: $errorType")
                    }
                    loadMcpContextAndTranscribe(file, operationId, apiKey, localGlossary)
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Personal glossary unavailable; continuing without prompt")
            loadMcpContextAndTranscribe(file, operationId, apiKey, emptyList())
        }
    }

    private fun loadMcpContextAndTranscribe(
        file: File,
        operationId: Long,
        apiKey: String,
        localGlossary: List<PersonalGlossaryTerm>
    ) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        try {
            McpContextProvider.getContext(applicationContext) { mcpTerms ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    enqueueTranscription(file, operationId, apiKey, localGlossary, mcpTerms)
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "MCP context unavailable; continuing with local glossary: ${exception.javaClass.simpleName}")
            enqueueTranscription(file, operationId, apiKey, localGlossary, emptyList())
        }
    }

    private fun enqueueTranscription(
        file: File,
        operationId: Long,
        apiKey: String,
        localGlossary: List<PersonalGlossaryTerm>,
        mcpTerms: List<String>
    ) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        try {
            SmartFormattingSettingsRepository.loadAsync(applicationContext) { settingsResult ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    val settings = activeVoiceOperationSnapshot?.settings ?: settingsResult.getOrElse { exception ->
                        Log.w(TAG, "Speech model settings read failed: ${exception.javaClass.simpleName}")
                        SmartFormattingSettings()
                    }
                    val prompt = GlossaryPromptBuilder.build(
                        localGlossary,
                        mcpTerms,
                        settings.speechLanguageMode
                    )
                    try {
                        val call = GroqTranscriptionClient.createCall(
                            apiKey,
                            file,
                            prompt,
                            settings.speechModel,
                            settings.speechLanguageMode.groqLanguageCode
                        )
                        activeRequestCall.attach(call)
                        GroqTranscriptionClient.enqueue(call) { result ->
                            mainHandler.post {
                                if (!isVoiceOperationTargetCurrent(operationId)) return@post
                                activeRequestCall.clear()
                                when (result) {
                                    is GroqTranscriptionResult.Success -> {
                                        loadCorrectionRulesAndCommit(
                                            file,
                                            operationId,
                                            result.text,
                                            apiKey,
                                            localGlossary
                                        )
                                    }
                                    is GroqTranscriptionResult.Failure -> {
                                        val status = result.httpStatus?.let { ", http_status=$it" }.orEmpty()
                                        Log.e(TAG, "Groq transcription failed: ${result.type}$status")
                                        finishWithError(
                                            operationId,
                                            "transcription_${result.type}",
                                            result.httpStatus
                                        )
                                    }
                                }
                            }
                        }
                    } catch (exception: Exception) {
                        Log.e(TAG, "Groq request setup failed: ${exception.javaClass.simpleName}")
                        finishWithError(operationId, "request_setup_failed")
                    }
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Speech settings read setup failed: ${exception.javaClass.simpleName}")
            val fallbackSettings = activeVoiceOperationSnapshot?.settings ?: SmartFormattingSettings()
            val fallbackPrompt = GlossaryPromptBuilder.build(
                localGlossary,
                mcpTerms,
                fallbackSettings.speechLanguageMode
            )
            val fallbackCall = GroqTranscriptionClient.createCall(
                apiKey,
                file,
                fallbackPrompt,
                fallbackSettings.speechModel,
                fallbackSettings.speechLanguageMode.groqLanguageCode
            )
            activeRequestCall.attach(fallbackCall)
            GroqTranscriptionClient.enqueue(fallbackCall) { result ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    activeRequestCall.clear()
                    when (result) {
                        is GroqTranscriptionResult.Success ->
                            loadCorrectionRulesAndCommit(
                                file,
                                operationId,
                                result.text,
                                apiKey,
                                localGlossary
                            )
                        is GroqTranscriptionResult.Failure ->
                            finishWithError(operationId, "transcription_${result.type}", result.httpStatus)
                    }
                }
            }
        }
    }

    private fun loadCorrectionRulesAndCommit(
        file: File,
        operationId: Long,
        originalText: String,
        apiKey: String,
        localGlossary: List<PersonalGlossaryTerm>
    ) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        activeRawTranscript = originalText
        if (activeVoiceOperationSnapshot?.inputMode == VoiceInputMode.FORMAT_COMMAND) {
            processFormatCommand(file, operationId, originalText, apiKey)
            return
        }
        // AI 編輯：辨識結果是修改要求，直接套用在選取文字上，不進行一般逐字稿修正與整理。
        if (activeVoiceOperationSnapshot?.inputMode == VoiceInputMode.AI_EDIT) {
            processAiEditInstruction(operationId, originalText, apiKey)
            return
        }
        try {
            TextCorrectionRuleRepository.load(applicationContext) { result ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    val rules = result.getOrElse { exception ->
                        Log.w(
                            TAG,
                            "Text correction rules read failed; using original text: ${exception.javaClass.simpleName}"
                        )
                        emptyList()
                    }
                    processCorrectedText(file, operationId, originalText, rules, apiKey, localGlossary)
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Text correction rules unavailable; using original text: ${exception.javaClass.simpleName}")
            processCorrectedText(file, operationId, originalText, emptyList(), apiKey, localGlossary)
        }
    }

    private fun processCorrectedText(
        file: File,
        operationId: Long,
        originalText: String,
        rules: List<TextCorrectionRule>,
        apiKey: String,
        localGlossary: List<PersonalGlossaryTerm>
    ) {
        var postProcessedText = originalText
        var correctionPlan = TextCorrectionPlan(emptyMap())
        try {
            val correctionApplied = requestGate.runIfCurrent(operationId) {
                if (!isVoiceOperationTargetCurrent(operationId)) return@runIfCurrent
                postProcessedText = try {
                    TextPostProcessor.processWithPlan(originalText.trim(), rules).also { result ->
                        correctionPlan = result.correctionPlan
                    }.text
                } catch (exception: Exception) {
                    Log.w(TAG, "Text post-processing failed; using original text: ${exception.javaClass.simpleName}")
                    originalText
                }
            }
            if (!correctionApplied || !isVoiceOperationTargetCurrent(operationId)) return

            SmartFormattingSettingsRepository.loadAsync(applicationContext) { settingsResult ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    val settings = activeVoiceOperationSnapshot?.settings ?: settingsResult.getOrElse { exception ->
                        Log.w(TAG, "Smart formatting settings read failed: ${exception.javaClass.simpleName}")
                        commitFormattingFallback(
                            file,
                            operationId,
                            postProcessedText,
                            "settings_read_failed"
                        )
                        return@post
                    }

                    when (val decision = SmartFormattingPolicy.decide(postProcessedText, settings)) {
                        is SmartFormattingDecision.CommitOriginal -> {
                            commitTranscriptText(
                                file,
                                operationId,
                                decision.text,
                                VoiceImeState.SUCCESS,
                                terminalPeriodMode = settings.terminalPeriodMode
                            )
                        }
                        is SmartFormattingDecision.Format -> {
                            if (!transitionStatus(VoiceImeState.FORMATTING)) return@post
                            val provider = TextFormattingProviderRegistry.forProvider(decision.provider)
                            if (provider == null) {
                                val providerName = decision.provider.displayName
                                commitFormattingFallback(
                                    file,
                                    operationId,
                                    decision.text,
                                    "provider_not_integrated",
                                    statusLabel = "$providerName API 尚未串接，已使用本地修正結果",
                                    terminalPeriodMode = settings.terminalPeriodMode
                                )
                                return@post
                            }
                            val sensitiveEditor = activeVoiceOperationSnapshot?.sensitiveEditor != false
                            val apiKeyAvailable = apiKey.isNotBlank()
                            val useContextualCorrection = ContextualCorrectionPolicy.shouldUse(
                                enabled = settings.contextualCorrectionEnabled,
                                smartFormattingEnabled = settings.enabled,
                                providerAvailable = true,
                                apiKeyAvailable = apiKeyAvailable,
                                sensitiveEditor = sensitiveEditor
                            )
                            val useTaiwanWording = TextEnhancementPolicy.shouldUse(
                                featureEnabled = settings.taiwanWordingEnabled,
                                smartFormattingEnabled = settings.enabled,
                                providerAvailable = true,
                                apiKeyAvailable = apiKeyAvailable,
                                sensitiveEditor = sensitiveEditor
                            )
                            val useSmartPunctuation = TextEnhancementPolicy.shouldUse(
                                featureEnabled = settings.smartPunctuationEnabled,
                                smartFormattingEnabled = settings.enabled,
                                providerAvailable = true,
                                apiKeyAvailable = apiKeyAvailable,
                                sensitiveEditor = sensitiveEditor
                            )
                            formatTranscript(
                                file,
                                operationId,
                                apiKey,
                                decision.text,
                                decision.model,
                                provider,
                                rules,
                                correctionPlan,
                                TranscriptFormattingPrompt.buildSystemPrompt(
                                    contextualCorrectionEnabled = useContextualCorrection,
                                    glossary = if (useContextualCorrection) localGlossary else emptyList(),
                                    correctionRules = if (sensitiveEditor) emptyList() else rules.filter {
                                        it.id in correctionPlan.matchedOccurrences
                                    },
                                    formattingStyle = activeVoiceOperationSnapshot?.formattingStyle
                                        ?: settings.formattingStyle,
                                    taiwanWordingEnabled = useTaiwanWording,
                                    smartPunctuationEnabled = useSmartPunctuation
                                ),
                                settings.terminalPeriodMode
                            )
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Smart formatting preparation failed: ${exception.javaClass.simpleName}")
            if (isVoiceOperationTargetCurrent(operationId)) {
                val settings = activeVoiceOperationSnapshot?.settings ?: SmartFormattingSettings()
                commitFormattingFallback(
                    file,
                    operationId,
                    postProcessedText,
                    "preparation_failed",
                    terminalPeriodMode = settings.terminalPeriodMode
                )
            }
        }
    }

    private fun formatTranscript(
        file: File,
        operationId: Long,
        apiKey: String,
        originalText: String,
        model: String,
        provider: TextFormattingProvider,
        correctionRules: List<TextCorrectionRule>,
        correctionPlan: TextCorrectionPlan,
        systemPrompt: String,
        terminalPeriodMode: TerminalPeriodMode
    ) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        try {
            val call = provider.format(apiKey, model, originalText, systemPrompt) { result ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    activeRequestCall.clear()
                    when (result) {
                        is TextFormattingResult.Success -> {
                            val resolution = SmartFormattingPolicy.resolve(originalText, result.text)
                            if (resolution.usedFallback) {
                                commitFormattingFallback(
                                    file, operationId, resolution.text, "empty_text",
                                    terminalPeriodMode = terminalPeriodMode
                                )
                            } else {
                                val protectedText = TextPostProcessor.enforceCorrectionPlan(
                                    resolution.text,
                                    correctionRules,
                                    correctionPlan
                                )
                                if (protectedText.isBlank()) {
                                    commitFormattingFallback(
                                        file,
                                        operationId,
                                        originalText,
                                        "empty_after_correction",
                                        terminalPeriodMode = terminalPeriodMode
                                    )
                                    return@post
                                }
                                commitTranscriptText(
                                    file,
                                    operationId,
                                    protectedText,
                                    VoiceImeState.SUCCESS,
                                    terminalPeriodMode = terminalPeriodMode
                                )
                            }
                        }
                        is TextFormattingResult.Failure -> {
                            val status = result.httpStatus?.let { ", http_status=$it" }.orEmpty()
                            Log.w(TAG, "Groq text formatting failed: ${result.type}$status")
                            val userMessage = if (result.type == "model_unavailable" && model == "qwen/qwen3.6-27b") {
                                "此模型目前無法使用，建議改用 Qwen 3.8 27B。"
                            } else {
                                null
                            }
                            commitFormattingFallback(
                                file,
                                operationId,
                                originalText,
                                result.type,
                                result.httpStatus,
                                userMessage,
                                terminalPeriodMode
                            )
                        }
                    }
                }
            }
            activeRequestCall.attach(call)
        } catch (exception: Exception) {
            Log.w(TAG, "Text formatting setup failed: ${exception.javaClass.simpleName}")
            commitFormattingFallback(
                file, operationId, originalText, "request_setup_failed",
                terminalPeriodMode = terminalPeriodMode
            )
        }
    }

    private fun commitFormattingFallback(
        file: File,
        operationId: Long,
        originalText: String,
        failureType: String,
        httpStatus: Int? = null,
        statusLabel: String? = null,
        terminalPeriodMode: TerminalPeriodMode = TerminalPeriodMode.AUTO
    ) {
        val status = httpStatus?.let { ", http_status=$it" }.orEmpty()
        Log.w(TAG, "Using post-processed transcript after formatting failure: $failureType$status")
        commitTranscriptText(
            file,
            operationId,
            originalText,
            VoiceImeState.FORMATTING_FALLBACK,
            statusLabel,
            terminalPeriodMode
        )
    }

    private fun commitTranscriptText(
        file: File,
        operationId: Long,
        text: String,
        terminalState: VoiceImeState,
        statusLabel: String? = null,
        terminalPeriodMode: TerminalPeriodMode = TerminalPeriodMode.AUTO
    ) {
        try {
            requestGate.runIfCurrent(operationId) {
                if (!isVoiceOperationTargetCurrent(operationId)) return@runIfCurrent
                val snapshot = activeVoiceOperationSnapshot
                if (snapshot == null || !isOperationTargetCurrent(snapshot)) {
                    finishWithError(operationId, "editor_target_changed")
                    return@runIfCurrent
                }
                val finalText = TerminalPunctuationProcessor.process(
                    text,
                    terminalPeriodMode,
                    imeOptions = snapshot.imeOptions,
                    inputType = snapshot.inputType
                )
                val committed = try {
                    snapshot.inputConnection?.commitText(finalText, 1) == true
                } catch (exception: Exception) {
                    Log.e(TAG, "Voice result delivery failed: ${exception.javaClass.simpleName}")
                    false
                }
                if (committed) {
                    UserHistoryRepository.recordVoiceAsync(
                        applicationContext,
                        rawText = activeRawTranscript.orEmpty(),
                        finalText = finalText,
                        successfulCommit = true,
                        cancelled = false,
                        isSensitiveEditor = snapshot.sensitiveEditor
                    ) { result ->
                        if (result.isFailure) {
                            logHistoryFailure("voice_history_record", result.exceptionOrNull())
                        }
                    }
                    finishSuccessfully(operationId, file, terminalState, statusLabel)
                } else {
                    finishWithError(operationId, "input_connection_unavailable")
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Voice result delivery failed: ${exception.javaClass.simpleName}")
            finishWithError(operationId, "result_delivery_failed")
        }
    }

    private fun finishSuccessfully(
        operationId: Long,
        file: File?,
        terminalState: VoiceImeState,
        statusLabel: String? = null
    ) {
        if (!isOperationCurrent(operationId)) return
        requestGate.invalidate()
        activeOperationId = 0L
        activeVoiceOperationSnapshot = null
        activeRequestCall.clear()
        activeAudioFile = null
        activeRawTranscript = null
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        recordingFailure = null
        deleteAudioFile(file)
        inputMode = VoiceInputMode.NORMAL
        transitionStatus(terminalState, statusLabel)
    }

    private fun finishWithError(
        operationId: Long,
        errorType: String,
        httpStatus: Int? = null
    ) {
        if (!isOperationCurrent(operationId)) return
        holdToTalkRecording = false
        val status = httpStatus?.let { ", http_status=$it" }.orEmpty()
        Log.e(TAG, "Voice operation failed: $errorType$status")
        requestGate.invalidate()
        activeOperationId = 0L
        activeVoiceOperationSnapshot = null
        activeRequestCall.cancel()
        activeRawTranscript = null
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        stopRecorderAndJoin()
        deleteAudioFile(activeAudioFile)
        activeAudioFile = null
        recordingFailure = null
        inputMode = VoiceInputMode.NORMAL
        voicePanel?.hideAiEditPreview()
        transitionStatus(VoiceImeState.ERROR)
    }

    private fun deleteAudioFile(file: File?) {
        if (file?.exists() == true && !file.delete()) {
            Log.w(TAG, "Temporary WAV cleanup failed")
        }
    }

    /**
     * 格式指令模式：第一層先處理本機確定性指令，其餘才交給 AI 格式整理。
     * 一律只插入純文字，不執行任何操作，也不會替換既有文字。
     */
    private fun processFormatCommand(
        file: File,
        operationId: Long,
        originalText: String,
        apiKey: String
    ) {
        val snapshot = activeVoiceOperationSnapshot ?: return
        when (val interpretation = FormatCommandInterpreter.interpret(originalText)) {
            is FormatCommandInterpretation.Newline -> commitFormatCommandText(
                file, operationId, "\n", "已插入換行", interpretation.recordsVoiceHistory()
            )

            is FormatCommandInterpretation.BlankLine -> commitFormatCommandText(
                file,
                operationId,
                blankLineInsertionText(snapshot),
                "已插入空行",
                interpretation.recordsVoiceHistory()
            )

            is FormatCommandInterpretation.Unsupported -> {
                Log.w(TAG, "Format command rejected as unsupported")
                failFormatCommand(operationId, "不支援的格式指令，未插入任何文字")
            }

            is FormatCommandInterpretation.UseAi -> {
                val provider = TextFormattingProviderRegistry.forProvider(snapshot.settings.provider)
                if (provider == null || apiKey.isBlank()) {
                    failFormatCommand(operationId, "格式指令需要可用的 Provider 與 API Key，未插入任何文字")
                    return
                }
                if (!transitionStatus(VoiceImeState.FORMATTING)) return
                // 格式指令模式是使用者手動啟動的，因此不受智慧整理總開關與短文字門檻限制，
                // 但仍遵守台灣用字／智慧標點設定與敏感欄位政策。
                val useTaiwanWording = TextEnhancementPolicy.shouldUse(
                    featureEnabled = snapshot.settings.taiwanWordingEnabled,
                    smartFormattingEnabled = true,
                    providerAvailable = true,
                    apiKeyAvailable = apiKey.isNotBlank(),
                    sensitiveEditor = snapshot.sensitiveEditor
                )
                val useSmartPunctuation = TextEnhancementPolicy.shouldUse(
                    featureEnabled = snapshot.settings.smartPunctuationEnabled,
                    smartFormattingEnabled = true,
                    providerAvailable = true,
                    apiKeyAvailable = apiKey.isNotBlank(),
                    sensitiveEditor = snapshot.sensitiveEditor
                )
                runFormatCommand(
                    file,
                    operationId,
                    apiKey,
                    snapshot.settings.model,
                    provider,
                    FormatCommandPrompt.buildSystemPrompt(useTaiwanWording, useSmartPunctuation),
                    FormatCommandPrompt.buildUserMessage(
                        FormatCommandPrompt.formatInstructionFor(originalText),
                        originalText
                    ),
                    interpretation.recordsVoiceHistory()
                )
            }
        }
    }

    private fun blankLineInsertionText(snapshot: VoiceOperationSnapshot): String {
        val canInspect = !snapshot.sensitiveEditor && canInspectEditorText(currentEditorInfo)
        if (!canInspect) return BlankLineInsertion.newlinesFor(null, null, canInspect = false)
        val connection = snapshot.inputConnection
        val before = runCatching { connection?.getTextBeforeCursor(4, 0)?.toString() }.getOrNull()
        val after = runCatching { connection?.getTextAfterCursor(4, 0)?.toString() }.getOrNull()
        return BlankLineInsertion.newlinesFor(before, after, canInspect = true)
    }

    private fun runFormatCommand(
        file: File,
        operationId: Long,
        apiKey: String,
        model: String,
        provider: TextFormattingProvider,
        systemPrompt: String,
        userMessage: String,
        recordVoiceHistory: Boolean
    ) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        try {
            val call = provider.format(
                apiKey,
                model,
                userMessage,
                systemPrompt
            ) { result ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    activeRequestCall.clear()
                    when (result) {
                        is TextFormattingResult.Success -> {
                            val formatted = result.text.trim()
                            if (formatted.isEmpty()) {
                                Log.w(TAG, "Format command produced no insertable text")
                                failFormatCommand(operationId, "無法解讀這個格式指令，未插入任何文字")
                            } else {
                                commitFormatCommandText(
                                    file,
                                    operationId,
                                    formatted,
                                    "已插入格式指令結果",
                                    recordVoiceHistory
                                )
                            }
                        }
                        is TextFormattingResult.Failure -> {
                            val status = result.httpStatus?.let { ", http_status=$it" }.orEmpty()
                            Log.w(TAG, "Format command request failed: ${result.type}$status")
                            failFormatCommand(operationId, "格式指令處理失敗，未插入任何文字")
                        }
                    }
                }
            }
            activeRequestCall.attach(call)
        } catch (exception: Exception) {
            Log.w(TAG, "Format command setup failed: ${exception.javaClass.simpleName}")
            failFormatCommand(operationId, "格式指令處理失敗，未插入任何文字")
        }
    }

    /**
     * 純格式指令（換行、空一行）不寫入語音歷史，避免出現只有換行的項目；
     * 會產生實質文字的指令才記錄。
     */
    private fun commitFormatCommandText(
        file: File,
        operationId: Long,
        text: String,
        statusLabel: String,
        recordVoiceHistory: Boolean
    ) {
        try {
            requestGate.runIfCurrent(operationId) {
                if (!isVoiceOperationTargetCurrent(operationId)) return@runIfCurrent
                val snapshot = activeVoiceOperationSnapshot
                if (snapshot == null || !isOperationTargetCurrent(snapshot)) {
                    finishWithError(operationId, "editor_target_changed")
                    return@runIfCurrent
                }
                if (text.isEmpty()) {
                    finishSuccessfully(operationId, file, VoiceImeState.SUCCESS, "已有空行")
                    return@runIfCurrent
                }
                val committed = try {
                    snapshot.inputConnection?.commitText(text, 1) == true
                } catch (exception: Exception) {
                    Log.e(TAG, "Format command delivery failed: ${exception.javaClass.simpleName}")
                    false
                }
                if (committed) {
                    if (recordVoiceHistory) {
                        UserHistoryRepository.recordVoiceAsync(
                            applicationContext,
                            rawText = activeRawTranscript.orEmpty(),
                            finalText = text,
                            successfulCommit = true,
                            cancelled = false,
                            isSensitiveEditor = snapshot.sensitiveEditor
                        ) { result ->
                            if (result.isFailure) {
                                logHistoryFailure("voice_history_record", result.exceptionOrNull())
                            }
                        }
                    }
                    finishSuccessfully(operationId, file, VoiceImeState.SUCCESS, statusLabel)
                } else {
                    finishWithError(operationId, "input_connection_unavailable")
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Format command delivery failed: ${exception.javaClass.simpleName}")
            finishWithError(operationId, "result_delivery_failed")
        }
    }

    private fun failFormatCommand(operationId: Long, statusLabel: String) {
        if (!isOperationCurrent(operationId)) return
        holdToTalkRecording = false
        Log.w(TAG, "Format command finished without inserting text")
        requestGate.invalidate()
        activeOperationId = 0L
        activeVoiceOperationSnapshot = null
        activeRequestCall.cancel()
        activeRawTranscript = null
        inputMode = VoiceInputMode.NORMAL
        stopRecorderAndJoin()
        deleteAudioFile(activeAudioFile)
        activeAudioFile = null
        recordingFailure = null
        transitionStatus(VoiceImeState.ERROR, statusLabel)
    }

    /**
     * AI 編輯：把 ASR 轉出的口述要求當成編輯指令，套用在進入模式時讀取的選取文字上。
     * 每次編輯最多一次 ASR 加一次 LLM 請求；結果一律先預覽，不直接覆蓋原文。
     */
    private fun processAiEditInstruction(operationId: Long, instruction: String, apiKey: String) {
        if (!isVoiceOperationTargetCurrent(operationId)) return
        // 選取文字已讀入快照，錄音檔不再需要，先行移除避免在預覽期間佔用空間。
        deleteAudioFile(activeAudioFile)
        activeAudioFile = null
        val selection = activeSelectedTextSnapshot
        val snapshot = activeVoiceOperationSnapshot
        if (selection == null || snapshot == null) {
            failAiEdit(operationId, "沒有可修改的選取文字，未修改任何文字")
            return
        }
        val provider = TextFormattingProviderRegistry.forProvider(snapshot.settings.provider)
        if (provider == null || apiKey.isBlank()) {
            failAiEdit(operationId, "AI 編輯需要可用的 Provider 與 API Key，未修改任何文字")
            return
        }
        val trimmedInstruction = instruction.trim()
        if (!TextEditPrompt.isUsableInstruction(trimmedInstruction)) {
            failAiEdit(operationId, "無法解讀這個修改要求，未修改任何文字")
            return
        }
        if (!transitionStatus(VoiceImeState.FORMATTING)) return
        val request = TextTransformationRequest(
            transformationType = TextTransformationType.EDIT_SELECTED_TEXT,
            sourceText = selection.text,
            instruction = trimmedInstruction,
            provider = snapshot.settings.provider,
            model = snapshot.settings.model,
            operationId = operationId
        )
        val prompt = TextTransformationCore.promptFor(request)
        try {
            val call = provider.format(apiKey, request.model, prompt.userMessage, prompt.systemPrompt) { result ->
                mainHandler.post {
                    if (!isVoiceOperationTargetCurrent(operationId)) return@post
                    activeRequestCall.clear()
                    when (result) {
                        is TextFormattingResult.Success -> when (
                            val transformed = TextTransformationCore.interpretSuccess(request, result.text)
                        ) {
                            is TextTransformationResult.Success ->
                                showAiEditPreview(operationId, transformed.transformedText)

                            is TextTransformationResult.Failure -> {
                                Log.w(TAG, "AI edit produced no usable text: ${transformed.kind}")
                                failAiEdit(operationId, "AI 沒有產生可用的修改結果，未修改任何文字")
                            }
                        }

                        is TextFormattingResult.Failure -> {
                            val status = result.httpStatus?.let { ", http_status=$it" }.orEmpty()
                            Log.w(TAG, "AI edit request failed: ${result.type}$status")
                            failAiEdit(operationId, "AI 編輯處理失敗，未修改任何文字")
                        }
                    }
                }
            }
            activeRequestCall.attach(call)
        } catch (exception: Exception) {
            Log.w(TAG, "AI edit setup failed: ${exception.javaClass.simpleName}")
            failAiEdit(operationId, "AI 編輯處理失敗，未修改任何文字")
        }
    }

    private fun showAiEditPreview(operationId: Long, resultText: String) {
        val selection = activeSelectedTextSnapshot ?: return
        if (!isVoiceOperationTargetCurrent(operationId)) return
        activeAiEditResult = resultText
        if (!transitionStatus(VoiceImeState.AWAITING_CONFIRM)) return
        voicePanel?.showAiEditPreview(selection.text, resultText)
    }

    /**
     * 使用者按下「確認取代」：重新讀取目前選取範圍並以 [SelectedTextPolicy.canReplace] 驗證。
     * 只有操作、欄位、範圍與選取文字都與快照一致時才取代；否則一律拒絕，
     * 不退回游標插入，也不搜尋文件中相同文字。
     */
    private fun confirmAiEditReplacement() {
        val operationId = activeOperationId
        val selection = activeSelectedTextSnapshot
        val snapshot = activeVoiceOperationSnapshot
        val result = activeAiEditResult
        if (operationId == 0L || selection == null || snapshot == null || result == null) {
            voicePanel?.showAiEditRefusal("原始選取範圍已變更，為避免覆蓋其他文字，請重新選取")
            return
        }
        if (stateMachine.state != VoiceImeState.AWAITING_CONFIRM) return
        if (!isOperationTargetCurrent(snapshot)) {
            Log.w(TAG, "AI edit replacement refused: editor target changed")
            voicePanel?.showAiEditRefusal("原始選取範圍已變更，為避免覆蓋其他文字，請重新選取")
            return
        }
        val connection = try {
            currentInputConnection
        } catch (exception: Exception) {
            Log.w(TAG, "AI edit input connection unavailable: ${exception.javaClass.simpleName}")
            null
        }
        val current = SelectedTextReader.readCurrent(
            connection = connection?.asSelectionReadConnection(),
            operationId = operationId,
            editorTarget = currentEditorTargetKey()
        )
        if (!SelectedTextPolicy.canReplace(selection, operationId, current)) {
            Log.w(TAG, "AI edit replacement refused: selection changed")
            voicePanel?.showAiEditRefusal("原始選取範圍已變更，為避免覆蓋其他文字，請重新選取")
            return
        }
        val committed = try {
            connection?.commitText(result, 1) == true
        } catch (exception: Exception) {
            Log.e(TAG, "AI edit replacement failed: ${exception.javaClass.simpleName}")
            false
        }
        if (!committed) {
            Log.w(TAG, "AI edit replacement unavailable")
            voicePanel?.showAiEditRefusal("無法取代選取文字，請重新選取後再試")
            return
        }
        voicePanel?.hideAiEditPreview()
        finishSuccessfully(operationId, activeAudioFile, VoiceImeState.SUCCESS, "已取代選取文字")
    }

    private fun failAiEdit(operationId: Long, statusLabel: String) {
        if (!isOperationCurrent(operationId)) return
        holdToTalkRecording = false
        Log.w(TAG, "AI edit finished without changing text")
        requestGate.invalidate()
        activeOperationId = 0L
        activeVoiceOperationSnapshot = null
        activeRequestCall.cancel()
        activeRawTranscript = null
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        inputMode = VoiceInputMode.NORMAL
        stopRecorderAndJoin()
        deleteAudioFile(activeAudioFile)
        activeAudioFile = null
        recordingFailure = null
        voicePanel?.hideAiEditPreview()
        transitionStatus(VoiceImeState.ERROR, statusLabel)
    }

    private fun isOperationCurrent(operationId: Long): Boolean =
        !serviceDestroyed && activeOperationId == operationId && requestGate.isCurrent(operationId) &&
            activeVoiceOperationSnapshot?.editorTarget?.sessionId == editorSessionId

    private fun transitionStatus(next: VoiceImeState, labelOverride: String? = null): Boolean {
        if (!stateMachine.transitionTo(next)) return false
        if (next != VoiceImeState.RECORDING) stopAudioLevelFeedback()
        statusLabelOverride = labelOverride
        statusRevision += 1
        statusResetRunnable?.let { mainHandler.removeCallbacks(it) }
        statusResetRunnable = null
        renderStatus()
        if (next == VoiceImeState.SUCCESS || next == VoiceImeState.FORMATTING_FALLBACK ||
            next == VoiceImeState.CANCELLED || next == VoiceImeState.ERROR
        ) {
            val revision = statusRevision
            val reset = Runnable {
                if (!serviceDestroyed && statusRevision == revision && stateMachine.state == next) {
                    stateMachine.transitionTo(VoiceImeState.IDLE)
                    statusLabelOverride = null
                    statusResetRunnable = null
                    statusRevision += 1
                    renderStatus()
                }
            }
            statusResetRunnable = reset
            mainHandler.postDelayed(reset, TERMINAL_STATUS_DURATION_MS)
        }
        return true
    }

    private fun resetTerminalStatus() {
        if (stateMachine.state != VoiceImeState.SUCCESS &&
            stateMachine.state != VoiceImeState.FORMATTING_FALLBACK &&
            stateMachine.state != VoiceImeState.CANCELLED &&
            stateMachine.state != VoiceImeState.ERROR
        ) return
        statusResetRunnable?.let { mainHandler.removeCallbacks(it) }
        statusResetRunnable = null
        statusLabelOverride = null
        statusRevision += 1
        if (stateMachine.transitionTo(VoiceImeState.IDLE)) renderStatus()
    }

    private fun renderStatus() {
        voicePanel?.render(
            stateMachine.state,
            statusLabelOverride,
            holdToTalkRecording,
            formatCommandMode = inputMode == VoiceInputMode.FORMAT_COMMAND,
            aiEditMode = inputMode == VoiceInputMode.AI_EDIT
        )
    }

    private fun enterFormatCommandMode() {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE) return
        inputMode = VoiceInputMode.FORMAT_COMMAND
        voicePanel?.showMainPanel()
        renderStatus()
    }

    /**
     * 進入 AI 編輯模式：先讀取目前真正選取的文字並建立安全快照。
     * 讀不到可確認的選取（沒有選取、敏感欄位、範圍塌成游標等）時拒絕進入，
     * 不會以游標位置或整份文字代替。
     */
    private fun enterAiEditMode() {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE || activeOperationId != 0L) return
        val snapshot = readCurrentSelectionSnapshot()
        if (snapshot == null) {
            Log.w(TAG, "AI edit refused: no readable selection")
            voicePanel?.showMainPanel()
            transitionStatus(VoiceImeState.ERROR, "請先在文字欄位選取要修改的文字")
            return
        }
        activeSelectedTextSnapshot = snapshot
        activeAiEditResult = null
        inputMode = VoiceInputMode.AI_EDIT
        voicePanel?.showMainPanel()
        renderStatus()
    }

    /** 離開格式指令／AI 編輯模式；未確認的 AI 編輯結果一律丟棄，原文不變。 */
    private fun exitSpecialMode() {
        if (serviceDestroyed) return
        val state = stateMachine.state
        val canExit = state == VoiceImeState.IDLE || state == VoiceImeState.SUCCESS ||
            state == VoiceImeState.FORMATTING_FALLBACK ||
            state == VoiceImeState.CANCELLED || state == VoiceImeState.ERROR
        if (!canExit) return
        inputMode = VoiceInputMode.NORMAL
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        renderStatus()
    }

    private fun readCurrentSelectionSnapshot(): SelectedTextSnapshot? =
        SelectedTextReader.read(
            connection = selectionReadConnection(),
            canInspectEditorText = canInspectEditorText(currentEditorInfo),
            isSensitiveEditor = isCurrentEditorSensitive(),
            editorTarget = currentEditorTargetKey()
        )

    private fun selectionReadConnection(): SelectionReadConnection? {
        val connection = try {
            currentInputConnection
        } catch (exception: Exception) {
            Log.w(TAG, "Selection input connection unavailable: ${exception.javaClass.simpleName}")
            null
        } ?: return null
        return connection.asSelectionReadConnection()
    }

    private fun InputConnection.asSelectionReadConnection(): SelectionReadConnection =
        object : SelectionReadConnection {
            override fun selectedText(): CharSequence? = try {
                this@asSelectionReadConnection.getSelectedText(0)
            } catch (exception: Exception) {
                Log.w(TAG, "Selected text read failed: ${exception.javaClass.simpleName}")
                null
            }

            override fun selectionRange(): SelectionRange? {
                val extracted = try {
                    this@asSelectionReadConnection.getExtractedText(ExtractedTextRequest(), 0)
                } catch (exception: Exception) {
                    Log.w(TAG, "Selection range read failed: ${exception.javaClass.simpleName}")
                    null
                } ?: return null
                val start = extracted.selectionStart
                val end = extracted.selectionEnd
                if (start < 0 || end < 0) return null
                return SelectionRange(extracted.startOffset + start, extracted.startOffset + end)
            }
        }

    private fun startAudioLevelUpdates(operationId: Long) {
        stopAudioLevelFeedback()
        audioLevelMonitor.start(operationId)
        audioLevelUpdateOperationId = operationId
        voicePanel?.updateAudioLevel(0f)

        val update = object : Runnable {
            override fun run() {
                if (audioLevelUpdateOperationId != operationId) return
                if (serviceDestroyed || activeOperationId != operationId ||
                    stateMachine.state != VoiceImeState.RECORDING ||
                    !audioLevelMonitor.isActive(operationId)
                ) {
                    stopAudioLevelFeedback()
                    return
                }
                voicePanel?.updateAudioLevel(audioLevelMonitor.levelFor(operationId))
                mainHandler.postDelayed(this, AUDIO_LEVEL_UPDATE_INTERVAL_MS)
            }
        }
        audioLevelUpdateRunnable = update
        mainHandler.post(update)
    }

    private fun stopAudioLevelFeedback() {
        audioLevelUpdateRunnable?.let(mainHandler::removeCallbacks)
        audioLevelUpdateRunnable = null
        audioLevelUpdateOperationId = 0L
        audioLevelMonitor.stop()
        voicePanel?.updateAudioLevel(0f)
    }

    private fun writeWavHeader(output: RandomAccessFile, pcmDataSize: Long) {
        output.writeBytes("RIFF")
        writeIntLittleEndian(output, 36L + pcmDataSize)
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        writeIntLittleEndian(output, 16)
        writeShortLittleEndian(output, 1)
        writeShortLittleEndian(output, CHANNEL_COUNT)
        writeIntLittleEndian(output, SAMPLE_RATE.toLong())
        writeIntLittleEndian(output, SAMPLE_RATE.toLong() * CHANNEL_COUNT * BYTES_PER_SAMPLE)
        writeShortLittleEndian(output, CHANNEL_COUNT * BYTES_PER_SAMPLE)
        writeShortLittleEndian(output, BYTES_PER_SAMPLE * 8)
        output.writeBytes("data")
        writeIntLittleEndian(output, pcmDataSize)
    }

    private fun writeIntLittleEndian(output: RandomAccessFile, value: Long) {
        for (shift in 0..24 step 8) output.write((value shr shift).toInt() and 0xFF)
    }

    private fun writeShortLittleEndian(output: RandomAccessFile, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    override fun onDestroy() {
        voicePanel?.resetSavedSnippetTransientState()
        voicePanel?.disposeHoldToTalkGesture()
        serviceDestroyed = true
        holdToTalkRecording = false
        stopAudioLevelFeedback()
        unregisterClipboardListener()
        backspaceRepeater.stop()
        requestGate.invalidate()
        activeOperationId = 0L
        activeVoiceOperationSnapshot = null
        activeSelectedTextSnapshot = null
        activeAiEditResult = null
        activeRequestCall.cancel()
        statusResetRunnable?.let { mainHandler.removeCallbacks(it) }
        statusResetRunnable = null
        stopRecorderAndJoin()
        deleteAudioFile(activeAudioFile)
        activeAudioFile = null
        voicePanel = null
        super.onDestroy()
    }
}

private data class VoiceOperationSnapshot(
    val editorTarget: VoiceEditorTargetKey,
    val inputConnection: InputConnection?,
    val inputType: Int,
    val imeOptions: Int,
    val sensitiveEditor: Boolean,
    val settings: SmartFormattingSettings,
    val formattingStyle: TextFormattingStyle,
    val inputMode: VoiceInputMode
)

private data class EditorFieldDetails(
    val packageName: String?,
    val fieldId: Int,
    val fieldName: String?,
    val inputType: Int,
    val imeOptions: Int
) {
    companion object {
        fun from(info: EditorInfo) = EditorFieldDetails(
            packageName = AppPackageName.normalize(info.packageName),
            fieldId = info.fieldId,
            fieldName = info.fieldName,
            inputType = info.inputType,
            imeOptions = info.imeOptions
        )
    }
}
