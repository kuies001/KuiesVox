package tw.kuies.voiceime

import android.content.Context

internal enum class ExternalFormattingProvider {
    GEMINI,
    OPENAI
}

internal object TextFormattingApiKeyStore {
    fun read(context: Context, provider: ExternalFormattingProvider): String? {
        CredentialMigration.ensure(context)
        return SecureCredentialStore(context)
            .readSecret(keyFor(provider))
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun readAsync(
        context: Context,
        provider: ExternalFormattingProvider,
        callback: (Result<String?>) -> Unit
    ) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ read(appContext, provider) }, callback)
    }

    fun save(context: Context, provider: ExternalFormattingProvider, apiKey: String) {
        CredentialMigration.ensure(context)
        val storage = SecureCredentialStore(context)
        if (apiKey.isBlank()) {
            storage.removeSecret(keyFor(provider))
        } else {
            storage.saveSecret(keyFor(provider), apiKey.trim())
        }
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
        ExternalFormattingProvider.GEMINI -> SecureCredentialStore.GEMINI_API_KEY
        ExternalFormattingProvider.OPENAI -> SecureCredentialStore.OPENAI_API_KEY
    }
}
