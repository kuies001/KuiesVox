package tw.kuies.voiceime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import okhttp3.Call

class VoiceImeService : InputMethodService() {

    companion object {
        private const val TAG = "VoiceImeService"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
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
    private var activeRequestCall: Call? = null
    private var voicePanel: VoiceImePanel? = null
    private val previewState = VoiceImePreviewState()
    private var statusResetRunnable: Runnable? = null
    private var statusRevision = 0L
    private var statusLabelOverride: String? = null
    @Volatile
    private var currentEditorInfo: EditorInfo? = null

    override fun onCreateInputView(): View {
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
            onClearAll = ::clearAllInputText
        )
        voicePanel = panel
        panel.setSwitchAvailable(shouldOfferSwitchingToNextInputMethod())
        renderStatus()
        return panel.view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info
        voicePanel?.setSwitchAvailable(shouldOfferSwitchingToNextInputMethod())
    }

    private fun openSettings() {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE || activeOperationId != 0L) return
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
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
        backspaceRepeater.stop()
        super.onFinishInputView(finishingInput)
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

    private fun startRecording() {
        if (serviceDestroyed || stateMachine.state != VoiceImeState.IDLE || audioRecord != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Recording unavailable: microphone_permission_missing")
            transitionStatus(VoiceImeState.ERROR)
            return
        }

        var newRecorder: AudioRecord? = null
        var outputFile: File? = null
        try {
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
            activeAudioFile = file
            recordingFailure = null
            audioRecord = recorder
            transitionStatus(VoiceImeState.RECORDING)
            recordingThread = Thread(
                { capturePcmWav(recorder, file, bufferSize, operationId) },
                "VoiceImeAudioRecorder"
            ).apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Recording started: ${file.absolutePath}")
        } catch (exception: Exception) {
            Log.e(TAG, "Recording start failed: ${exception.javaClass.simpleName}")
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
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        dataSize += bytesRead
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
        if (operationId == 0L || completedFile == null ||
            !transitionStatus(VoiceImeState.TRANSCRIBING)
        ) return

        stopRecorderAndJoin()
        if (!isOperationCurrent(operationId)) return
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
            state != VoiceImeState.FORMATTING
        ) return

        val file = activeAudioFile
        requestGate.invalidate()
        activeOperationId = 0L
        activeRequestCall?.cancel()
        activeRequestCall = null
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
        if (!isOperationCurrent(operationId)) return
        try {
            GroqApiKeyStore.readAsync(applicationContext) { keyResult ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
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

    private fun loadGlossaryAndTranscribe(file: File, operationId: Long, apiKey: String) {
        if (!isOperationCurrent(operationId)) return
        try {
            PersonalGlossaryRepository.load(applicationContext) { result ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
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
        if (!isOperationCurrent(operationId)) return
        try {
            McpContextProvider.getContext(applicationContext) { mcpTerms ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
                    val prompt = GlossaryPromptBuilder.build(localGlossary, mcpTerms)
                    enqueueTranscription(file, operationId, apiKey, prompt)
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "MCP context unavailable; continuing with local glossary: ${exception.javaClass.simpleName}")
            enqueueTranscription(file, operationId, apiKey, GlossaryPromptBuilder.build(localGlossary))
        }
    }

    private fun enqueueTranscription(file: File, operationId: Long, apiKey: String, prompt: String?) {
        if (!isOperationCurrent(operationId)) return
        try {
            SmartFormattingSettingsRepository.loadAsync(applicationContext) { settingsResult ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
                    val model = settingsResult.getOrElse { exception ->
                        Log.w(TAG, "Speech model settings read failed: ${exception.javaClass.simpleName}")
                        SmartFormattingSettings()
                    }.speechModel
                    try {
                        val call = GroqTranscriptionClient.createCall(apiKey, file, prompt, model)
                        activeRequestCall = call
                        GroqTranscriptionClient.enqueue(call) { result ->
                            mainHandler.post {
                                if (!isOperationCurrent(operationId)) return@post
                                activeRequestCall = null
                                when (result) {
                                    is GroqTranscriptionResult.Success -> {
                                        loadCorrectionRulesAndCommit(file, operationId, result.text, apiKey)
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
            val fallbackCall = GroqTranscriptionClient.createCall(
                apiKey,
                file,
                prompt,
                SmartFormattingSettings.DEFAULT_SPEECH_MODEL
            )
            activeRequestCall = fallbackCall
            GroqTranscriptionClient.enqueue(fallbackCall) { result ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
                    activeRequestCall = null
                    when (result) {
                        is GroqTranscriptionResult.Success ->
                            loadCorrectionRulesAndCommit(file, operationId, result.text, apiKey)
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
        apiKey: String
    ) {
        if (!isOperationCurrent(operationId)) return
        try {
            TextCorrectionRuleRepository.load(applicationContext) { result ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
                    val rules = result.getOrElse { exception ->
                        Log.w(
                            TAG,
                            "Text correction rules read failed; using original text: ${exception.javaClass.simpleName}"
                        )
                        emptyList()
                    }
                    processCorrectedText(file, operationId, originalText, rules, apiKey)
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Text correction rules unavailable; using original text: ${exception.javaClass.simpleName}")
            processCorrectedText(file, operationId, originalText, emptyList(), apiKey)
        }
    }

    private fun processCorrectedText(
        file: File,
        operationId: Long,
        originalText: String,
        rules: List<TextCorrectionRule>,
        apiKey: String
    ) {
        var postProcessedText = originalText
        try {
            val correctionApplied = requestGate.runIfCurrent(operationId) {
                if (serviceDestroyed || activeOperationId != operationId) return@runIfCurrent
                postProcessedText = try {
                    TextPostProcessor.process(originalText, rules)
                } catch (exception: Exception) {
                    Log.w(TAG, "Text post-processing failed; using original text: ${exception.javaClass.simpleName}")
                    originalText
                }
            }
            if (!correctionApplied || !isOperationCurrent(operationId)) return

            SmartFormattingSettingsRepository.loadAsync(applicationContext) { settingsResult ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
                    val settings = settingsResult.getOrElse { exception ->
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
                            formatTranscript(
                                file,
                                operationId,
                                apiKey,
                                decision.text,
                                decision.model,
                                provider,
                                settings.terminalPeriodMode
                            )
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Smart formatting preparation failed: ${exception.javaClass.simpleName}")
            if (isOperationCurrent(operationId)) {
                commitFormattingFallback(file, operationId, postProcessedText, "preparation_failed")
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
        terminalPeriodMode: TerminalPeriodMode
    ) {
        if (!isOperationCurrent(operationId)) return
        try {
            val call = provider.format(apiKey, model, originalText) { result ->
                mainHandler.post {
                    if (!isOperationCurrent(operationId)) return@post
                    activeRequestCall = null
                    when (result) {
                        is TextFormattingResult.Success -> {
                            val resolution = SmartFormattingPolicy.resolve(originalText, result.text)
                            if (resolution.usedFallback) {
                                commitFormattingFallback(
                                    file, operationId, resolution.text, "empty_text",
                                    terminalPeriodMode = terminalPeriodMode
                                )
                            } else {
                                commitTranscriptText(
                                    file,
                                    operationId,
                                    resolution.text,
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
            activeRequestCall = call
        } catch (exception: Exception) {
            Log.w(TAG, "Groq text formatting setup failed: ${exception.javaClass.simpleName}")
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
                if (!isOperationCurrent(operationId)) return@runIfCurrent
                val editorInfo = currentEditorInfo
                val finalText = TerminalPunctuationProcessor.process(
                    text,
                    terminalPeriodMode,
                    imeOptions = editorInfo?.imeOptions ?: 0,
                    inputType = editorInfo?.inputType ?: 0
                )
                val committed = try {
                    currentInputConnection?.commitText(finalText, 1) == true
                } catch (exception: Exception) {
                    Log.e(TAG, "Voice result delivery failed: ${exception.javaClass.simpleName}")
                    false
                }
                previewState.recordCommitResult(finalText, committed, terminalState)
                if (committed) {
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
        file: File,
        terminalState: VoiceImeState,
        statusLabel: String? = null
    ) {
        if (!isOperationCurrent(operationId)) return
        requestGate.invalidate()
        activeOperationId = 0L
        activeRequestCall = null
        activeAudioFile = null
        recordingFailure = null
        deleteAudioFile(file)
        transitionStatus(terminalState, statusLabel)
    }

    private fun finishWithError(
        operationId: Long,
        errorType: String,
        httpStatus: Int? = null
    ) {
        if (!isOperationCurrent(operationId)) return
        val status = httpStatus?.let { ", http_status=$it" }.orEmpty()
        Log.e(TAG, "Voice operation failed: $errorType$status")
        requestGate.invalidate()
        activeOperationId = 0L
        activeRequestCall?.cancel()
        activeRequestCall = null
        stopRecorderAndJoin()
        deleteAudioFile(activeAudioFile)
        activeAudioFile = null
        recordingFailure = null
        transitionStatus(VoiceImeState.ERROR)
    }

    private fun deleteAudioFile(file: File?) {
        if (file?.exists() == true && !file.delete()) {
            Log.w(TAG, "Temporary WAV cleanup failed")
        }
    }

    private fun isOperationCurrent(operationId: Long): Boolean =
        !serviceDestroyed && activeOperationId == operationId && requestGate.isCurrent(operationId)

    private fun transitionStatus(next: VoiceImeState, labelOverride: String? = null): Boolean {
        if (!stateMachine.transitionTo(next)) return false
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
        voicePanel?.render(stateMachine.state, previewState.visibleText, statusLabelOverride)
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
        serviceDestroyed = true
        backspaceRepeater.stop()
        requestGate.invalidate()
        activeOperationId = 0L
        activeRequestCall?.cancel()
        activeRequestCall = null
        statusResetRunnable?.let { mainHandler.removeCallbacks(it) }
        statusResetRunnable = null
        stopRecorderAndJoin()
        deleteAudioFile(activeAudioFile)
        activeAudioFile = null
        voicePanel = null
        super.onDestroy()
    }
}
