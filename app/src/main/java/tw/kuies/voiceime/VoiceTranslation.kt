package tw.kuies.voiceime

import android.content.Context
import java.io.IOException

/** 語音翻譯可選的目標語言；第一版固定四種。 */
internal enum class TranslationTargetLanguage(
    val displayName: String,
    val promptName: String,
    val storageValue: String
) {
    ENGLISH("英文", "English", "en"),
    JAPANESE("日文", "Japanese", "ja"),
    KOREAN("韓文", "Korean", "ko"),
    TRADITIONAL_CHINESE_TAIWAN(
        "繁體中文（台灣）",
        "Traditional Chinese (Taiwan, zh-Hant-TW), using Taiwan conventions",
        "zh-Hant-TW"
    );

    fun next(): TranslationTargetLanguage = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT: TranslationTargetLanguage = ENGLISH

        fun fromStoredValue(value: String?): TranslationTargetLanguage {
            val normalized = value?.trim().orEmpty()
            if (normalized.isEmpty()) return DEFAULT
            return entries.firstOrNull {
                it.storageValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}

/** 記住上次選擇的目標語言，沿用 App 既有的 SharedPreferences 儲存方式。 */
internal object VoiceTranslationSettingsRepository {
    private const val PREFERENCES_NAME = "voice_translation_settings"
    private const val TARGET_LANGUAGE_KEY = "target_language"

    internal fun loadSync(context: Context): TranslationTargetLanguage {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return TranslationTargetLanguage.fromStoredValue(preferences.getString(TARGET_LANGUAGE_KEY, null))
    }

    fun loadAsync(context: Context, callback: (Result<TranslationTargetLanguage>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ loadSync(appContext) }, callback)
    }

    internal fun saveSync(context: Context, language: TranslationTargetLanguage) {
        val saved = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(TARGET_LANGUAGE_KEY, language.storageValue)
            .commit()
        if (!saved) throw IOException("Translation target language could not be persisted")
    }

    fun saveAsync(context: Context, language: TranslationTargetLanguage, callback: (Result<Unit>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            saveSync(appContext, language)
            Unit
        }, callback)
    }
}

/** 語音翻譯結果的處置：可以直接插入，或只能保留給使用者複製。 */
internal sealed interface SpeechTranslationOutcome {
    data class Insert(val text: String) : SpeechTranslationOutcome

    /** 不可插入；[copyableText] 有值時可提供「複製結果」。 */
    data class Refuse(val copyableText: String?) : SpeechTranslationOutcome
}

/**
 * 語音翻譯的插入決策（純邏輯，可用單元測試驗證）。
 *
 * 空白結果一律拒絕，且**絕不**用原始逐字稿頂替翻譯；目標欄位失效或同一操作已插入過也拒絕。
 */
internal object SpeechTranslationPolicy {
    fun resolve(
        sourceText: String,
        translatedText: String?,
        targetStillValid: Boolean,
        alreadyInserted: Boolean
    ): SpeechTranslationOutcome {
        val trimmed = translatedText?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            // AI 失敗或回傳空白：不得把原始逐字稿當成翻譯結果插入。
            return SpeechTranslationOutcome.Refuse(copyableText = null)
        }
        if (!targetStillValid) return SpeechTranslationOutcome.Refuse(copyableText = trimmed)
        if (alreadyInserted) return SpeechTranslationOutcome.Refuse(copyableText = trimmed)
        return SpeechTranslationOutcome.Insert(trimmed)
    }
}

/** 保證同一次操作最多只插入一次，避免重複按停止或延遲回呼造成重複輸入。 */
internal class SingleInsertionClaim {
    private var claimedOperationId = 0L

    fun isClaimed(operationId: Long): Boolean = operationId != 0L && claimedOperationId == operationId

    fun claim(operationId: Long): Boolean {
        if (operationId == 0L || claimedOperationId == operationId) return false
        claimedOperationId = operationId
        return true
    }
}
