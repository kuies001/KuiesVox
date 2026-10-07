package tw.kuies.voiceime

import android.content.Context

internal enum class ExternalFormattingProvider {
    GEMINI,
    OPENAI
}

internal object TextFormattingApiKeyStore {
    private const val PREFERENCES_NAME = "text_formatting_api_keys"
    private const val GEMINI_API_KEY = "gemini_api_key"
    private const val OPENAI_API_KEY = "openai_api_key"

    fun read(context: Context, provider: ExternalFormattingProvider): String? =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(keyFor(provider), null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    fun readAsync(
        context: Context,
        provider: ExternalFormattingProvider,
        callback: (Result<String?>) -> Unit
    ) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ read(appContext, provider) }, callback)
    }

    fun save(context: Context, provider: ExternalFormattingProvider, apiKey: String) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        if (apiKey.isBlank()) {
            editor.remove(keyFor(provider))
        } else {
            editor.putString(keyFor(provider), apiKey.trim())
        }
        editor.apply()
    }

    fun saveAsync(
        context: Context,
        provider: ExternalFormattingProvider,
        apiKey: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            save(appContext, provider, apiKey)
            Unit
        }, callback)
    }

    private fun keyFor(provider: ExternalFormattingProvider): String = when (provider) {
        ExternalFormattingProvider.GEMINI -> GEMINI_API_KEY
        ExternalFormattingProvider.OPENAI -> OPENAI_API_KEY
    }
}
