package tw.kuies.voiceime

import android.content.Context
import java.io.IOException

/**
 * 文字修正规則的本機儲存。
 *
 * 與 [PersonalGlossaryRepository] 相同：讀取只還原使用者自己存放的內容
 * （[TextCorrectionRuleCodec]），沒有內建預設、沒有版本遷移植入，因此新安裝一定是
 * 空清單，覆蓋安裝也不會動到既有规則。
 */
internal object TextCorrectionRuleRepository {
    private const val PREFERENCES_NAME = "text_correction_rules"
    private const val RULES_KEY = "rules_v1"

    fun load(context: Context, callback: (Result<List<TextCorrectionRule>>) -> Unit) {
        submit(callback) { readRules(context.applicationContext) }
    }

    fun add(
        context: Context,
        candidates: List<Pair<String, String>>,
        callback: (Result<TextCorrectionRuleAddResult>) -> Unit
    ) {
        submit(callback) {
            val appContext = context.applicationContext
            val currentRules = readRules(appContext)
            val result = TextCorrectionRules.add(currentRules, candidates)
            if (result.addedCount > 0) persistRules(appContext, result.rules)
            result
        }
    }

    fun setEnabled(
        context: Context,
        id: String,
        enabled: Boolean,
        callback: (Result<List<TextCorrectionRule>>) -> Unit
    ) {
        update(context, callback) { rules -> TextCorrectionRules.setEnabled(rules, id, enabled) }
    }

    fun delete(
        context: Context,
        id: String,
        callback: (Result<List<TextCorrectionRule>>) -> Unit
    ) {
        update(context, callback) { rules -> TextCorrectionRules.delete(rules, id) }
    }

    private fun update(
        context: Context,
        callback: (Result<List<TextCorrectionRule>>) -> Unit,
        transform: (List<TextCorrectionRule>) -> List<TextCorrectionRule>
    ) {
        submit(callback) {
            val appContext = context.applicationContext
            val currentRules = readRules(appContext)
            val updatedRules = transform(currentRules)
            if (updatedRules != currentRules) persistRules(appContext, updatedRules)
            updatedRules
        }
    }

    private fun <T> submit(callback: (Result<T>) -> Unit, operation: () -> T) {
        AppStorageExecutor.submit(operation, callback)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun readRules(context: Context): List<TextCorrectionRule> =
        TextCorrectionRuleCodec.decode(preferences(context).getString(RULES_KEY, null))

    private fun persistRules(context: Context, rules: List<TextCorrectionRule>) {
        val saved = preferences(context)
            .edit()
            .putString(RULES_KEY, TextCorrectionRuleCodec.encode(rules))
            .commit()
        if (!saved) throw IOException("Text correction rules could not be persisted")
    }
}
