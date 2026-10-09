package tw.kuies.voiceime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** App profiles are a small additive SharedPreferences document; existing global settings stay authoritative. */
internal object AppVoiceProfileRepository {
    private const val PREFERENCES_NAME = "app_voice_profiles"
    private const val PROFILES_KEY = "profiles_v1"

    fun load(context: Context, callback: (Result<List<AppVoiceProfile>>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ loadSync(appContext) }, callback)
    }

    fun upsert(context: Context, profile: AppVoiceProfile, callback: (Result<List<AppVoiceProfile>>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            val profiles = AppVoiceProfileRules.upsert(loadSync(appContext), profile)
            saveSync(appContext, profiles)
            profiles
        }, callback)
    }

    fun setEnabled(
        context: Context,
        packageName: String,
        enabled: Boolean,
        callback: (Result<List<AppVoiceProfile>>) -> Unit
    ) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            val profiles = AppVoiceProfileRules.setEnabled(loadSync(appContext), packageName, enabled, System.currentTimeMillis())
            saveSync(appContext, profiles)
            profiles
        }, callback)
    }

    fun delete(context: Context, packageName: String, callback: (Result<List<AppVoiceProfile>>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            val profiles = AppVoiceProfileRules.delete(loadSync(appContext), packageName)
            saveSync(appContext, profiles)
            profiles
        }, callback)
    }

    internal fun loadSync(context: Context): List<AppVoiceProfile> {
        val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PROFILES_KEY, null)
            ?: return emptyList()
        return decode(stored)
    }

    internal fun saveSync(context: Context, profiles: List<AppVoiceProfile>) {
        val value = encode(profiles)
        val saved = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PROFILES_KEY, value)
            .commit()
        if (!saved) throw IOException("App voice profiles could not be persisted")
    }

    internal fun encode(profiles: List<AppVoiceProfile>): String {
        val array = JSONArray()
        AppVoiceProfileRules.normalize(profiles).forEach { profile ->
            val item = JSONObject()
                .put("packageName", profile.packageName)
                .put("enabled", profile.enabled)
                .put("createdAt", profile.createdAt)
                .put("updatedAt", profile.updatedAt)
            profile.speechModelOverride?.let { item.put("speechModel", it) }
            profile.speechLanguageModeOverride?.let { item.put("speechLanguageMode", it.storageValue) }
            profile.smartFormattingEnabledOverride?.let { item.put("smartFormattingEnabled", it) }
            profile.providerOverride?.let { item.put("provider", it.name.lowercase()) }
            profile.contextualCorrectionEnabledOverride?.let { item.put("contextualCorrectionEnabled", it) }
            profile.terminalPeriodModeOverride?.let { item.put("terminalPeriodMode", it.storageValue) }
            profile.formattingStyleOverride?.let { item.put("formattingStyle", it.storageValue) }
            array.put(item)
        }
        return array.toString()
    }

    internal fun decode(value: String): List<AppVoiceProfile> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val packageName = AppPackageName.normalize(item.optString("packageName")) ?: continue
                val providerValue = item.nullableString("provider")
                add(
                    AppVoiceProfile(
                        packageName = packageName,
                        enabled = item.optBoolean("enabled", true),
                        speechModelOverride = item.nullableString("speechModel")?.takeIf { model ->
                            FormattingModels.speech.any { it.id == model }
                        },
                        speechLanguageModeOverride = SpeechLanguageMode.fromStoredValue(
                            item.nullableString("speechLanguageMode")
                        ),
                        smartFormattingEnabledOverride = item.nullableBoolean("smartFormattingEnabled"),
                        providerOverride = TextFormattingProviderId.entries.firstOrNull {
                            it.name.equals(providerValue, ignoreCase = true)
                        }?.takeIf(TextFormattingProviderRegistry::isProfileSupported),
                        contextualCorrectionEnabledOverride = item.nullableBoolean("contextualCorrectionEnabled"),
                        terminalPeriodModeOverride = item.nullableString("terminalPeriodMode")?.let { value ->
                            TerminalPeriodMode.entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) }
                        },
                        formattingStyleOverride = TextFormattingStyle.fromStoredValue(
                            item.nullableString("formattingStyle")
                        ),
                        createdAt = item.optLong("createdAt", 0L).coerceAtLeast(0L),
                        updatedAt = item.optLong("updatedAt", item.optLong("createdAt", 0L)).coerceAtLeast(0L)
                    )
                )
            }
        }.let(AppVoiceProfileRules::normalize)
    }.getOrDefault(emptyList())

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else optBoolean(key)
}
