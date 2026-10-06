package tw.kuies.voiceime

import android.content.Context

internal object GroqApiKeyStore {
    private const val PREFERENCES_NAME = "groq_settings"
    private const val API_KEY_NAME = "api_key"

    fun read(context: Context): String? {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(API_KEY_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun readAsync(context: Context, callback: (Result<String?>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ read(appContext) }, callback)
    }

    fun save(context: Context, apiKey: String) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        if (apiKey.isBlank()) {
            editor.remove(API_KEY_NAME)
        } else {
            editor.putString(API_KEY_NAME, apiKey.trim())
        }
        editor.apply()
    }

    fun saveAsync(context: Context, apiKey: String, callback: (Result<Unit>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            save(appContext, apiKey)
            Unit
        }, callback)
    }
}
