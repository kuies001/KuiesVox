package tw.kuies.voiceime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

internal object TextCorrectionRuleRepository {
    private const val PREFERENCES_NAME = "text_correction_rules"
    private const val RULES_KEY = "rules_v1"
    private const val DEFAULT_DATA_VERSION_KEY = "default_data_version"

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

    fun importDefaults(
        context: Context,
        callback: (Result<TextCorrectionRuleAddResult>) -> Unit
    ) {
        submit(callback) {
            val appContext = context.applicationContext
            val currentRules = readStoredRules(appContext)
            val result = DefaultCorrectionRulesData.importMissing(currentRules)
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

    private fun readRules(context: Context): List<TextCorrectionRule> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val storedVersion = preferences.getInt(DEFAULT_DATA_VERSION_KEY, 0)
        val rules = readStoredRules(context)

        val migration = DefaultCorrectionRulesData.migrate(rules, storedVersion)
        if (migration.version != storedVersion) {
            persistRules(context, migration.values, migration.version)
        }
        return migration.values
    }

    private fun readStoredRules(context: Context): List<TextCorrectionRule> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val storedJson = preferences.getString(RULES_KEY, null)

        val jsonRules = storedJson?.takeIf(String::isNotBlank)?.let(::JSONArray) ?: JSONArray()
        val rules = mutableListOf<TextCorrectionRule>()
        val knownSources = mutableSetOf<String>()
        val knownIds = mutableSetOf<String>()

        for (index in 0 until jsonRules.length()) {
            val jsonRule = jsonRules.optJSONObject(index) ?: continue
            val source = jsonRule.optString("sourceText", "").trim()
            if (source.isEmpty() || !knownSources.add(TextCorrectionRules.keyFor(source))) continue

            val storedId = jsonRule.optString("id", "").trim()
            val id = storedId.takeIf { it.isNotEmpty() && knownIds.add(it) }
                ?: UUID.randomUUID().toString().also { knownIds.add(it) }
            rules += TextCorrectionRule(
                id = id,
                sourceText = source,
                replacementText = jsonRule.optString("replacementText", "").trim(),
                enabled = jsonRule.optBoolean("enabled", true)
            )
        }

        return rules
    }

    private fun persistRules(
        context: Context,
        rules: List<TextCorrectionRule>,
        defaultDataVersion: Int? = null
    ) {
        val jsonRules = JSONArray()
        rules.forEach { rule ->
            jsonRules.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("sourceText", rule.sourceText)
                    .put("replacementText", rule.replacementText)
                    .put("enabled", rule.enabled)
            )
        }

        val editor = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(RULES_KEY, jsonRules.toString())
        defaultDataVersion?.let { editor.putInt(DEFAULT_DATA_VERSION_KEY, it) }
        val saved = editor.commit()
        if (!saved) throw IOException("Text correction rules could not be persisted")
    }
}
