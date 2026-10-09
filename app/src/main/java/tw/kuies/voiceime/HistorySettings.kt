package tw.kuies.voiceime

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException

/** 語音與剪貼簿歷史的儲存開關；兩者預設開啟，升級後行為與原本一致。 */
internal data class HistorySettings(
    val voiceHistoryEnabled: Boolean = DEFAULT_VOICE_HISTORY_ENABLED,
    val clipboardHistoryEnabled: Boolean = DEFAULT_CLIPBOARD_HISTORY_ENABLED
) {
    /** 只切換單一開關的複製，供寫入層做 read-modify-write。 */
    fun withVoiceHistoryEnabled(enabled: Boolean): HistorySettings =
        copy(voiceHistoryEnabled = enabled)

    fun withClipboardHistoryEnabled(enabled: Boolean): HistorySettings =
        copy(clipboardHistoryEnabled = enabled)

    companion object {
        const val DEFAULT_VOICE_HISTORY_ENABLED = true
        const val DEFAULT_CLIPBOARD_HISTORY_ENABLED = true
    }
}

/**
 * 歷史紀錄寫入的最後一道檢查：關閉儲存時不得新增任何紀錄。
 * 敏感輸入欄位永遠不得記錄，與開關狀態無關。
 */
internal object HistoryCapturePolicy {
    fun shouldRecordVoice(settings: HistorySettings, isSensitiveEditor: Boolean): Boolean =
        settings.voiceHistoryEnabled && !isSensitiveEditor

    fun shouldRecordClipboard(settings: HistorySettings, isSensitiveEditor: Boolean): Boolean =
        settings.clipboardHistoryEnabled && !isSensitiveEditor
}

/**
 * 寫入當下才做決定：設定值必須在實際寫入的執行緒上、以最新的持久化內容取得，
 * 不能在排入佇列前就先決定（否則使用者關閉後，仍在佇列中的工作還是會寫入）。
 */
internal object HistoryWriteGate {
    fun shouldWriteVoice(settings: () -> HistorySettings, isSensitiveEditor: Boolean): Boolean =
        HistoryCapturePolicy.shouldRecordVoice(settings(), isSensitiveEditor)

    fun shouldWriteClipboard(settings: () -> HistorySettings, isSensitiveEditor: Boolean): Boolean =
        HistoryCapturePolicy.shouldRecordClipboard(settings(), isSensitiveEditor)
}

internal object HistorySettingsRepository {
    private const val PREFERENCES_NAME = "history_settings"
    private const val VOICE_HISTORY_ENABLED_KEY = "voice_history_enabled"
    private const val CLIPBOARD_HISTORY_ENABLED_KEY = "clipboard_history_enabled"

    fun loadSync(context: Context): HistorySettings {
        val preferences = preferences(context)
        return HistorySettings(
            voiceHistoryEnabled = preferences.getBoolean(
                VOICE_HISTORY_ENABLED_KEY,
                HistorySettings.DEFAULT_VOICE_HISTORY_ENABLED
            ),
            clipboardHistoryEnabled = preferences.getBoolean(
                CLIPBOARD_HISTORY_ENABLED_KEY,
                HistorySettings.DEFAULT_CLIPBOARD_HISTORY_ENABLED
            )
        )
    }

    fun saveSync(context: Context, settings: HistorySettings) {
        val committed = preferences(context).edit()
            .putBoolean(VOICE_HISTORY_ENABLED_KEY, settings.voiceHistoryEnabled)
            .putBoolean(CLIPBOARD_HISTORY_ENABLED_KEY, settings.clipboardHistoryEnabled)
            .commit()
        if (!committed) throw IOException("History settings could not be saved.")
    }

    fun load(context: Context, callback: (Result<HistorySettings>) -> Unit) =
        AppStorageExecutor.submit({ loadSync(context) }, callback)

    fun saveAsync(context: Context, settings: HistorySettings, callback: (Result<Unit>) -> Unit = {}) =
        AppStorageExecutor.submit({ saveSync(context, settings) }, callback)

    /**
     * 只切換一個開關：在單一執行緒上做 read-modify-write，
     * 因此連續快速切換不會用舊值蓋掉另一個開關，也不會在 UI 執行緒上碰檔案。
     */
    fun setVoiceHistoryEnabled(
        context: Context,
        enabled: Boolean,
        callback: (Result<HistorySettings>) -> Unit = {}
    ) = AppStorageExecutor.submit(
        { updateSync(context) { it.withVoiceHistoryEnabled(enabled) } },
        callback
    )

    fun setClipboardHistoryEnabled(
        context: Context,
        enabled: Boolean,
        callback: (Result<HistorySettings>) -> Unit = {}
    ) = AppStorageExecutor.submit(
        { updateSync(context) { it.withClipboardHistoryEnabled(enabled) } },
        callback
    )

    private fun updateSync(
        context: Context,
        transform: (HistorySettings) -> HistorySettings
    ): HistorySettings {
        val updated = transform(loadSync(context))
        saveSync(context, updated)
        return updated
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
