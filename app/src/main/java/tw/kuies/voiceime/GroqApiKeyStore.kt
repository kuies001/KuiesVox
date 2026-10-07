package tw.kuies.voiceime

import android.content.Context

internal object GroqApiKeyStore {
    fun read(context: Context): String? {
        CredentialMigration.ensure(context)
        return SecureCredentialStore(context)
            .readSecret(SecureCredentialStore.GROQ_API_KEY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun readAsync(context: Context, callback: (Result<String?>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({ read(appContext) }, callback)
    }

    fun save(context: Context, apiKey: String) {
        CredentialMigration.ensure(context)
        val storage = SecureCredentialStore(context)
        if (apiKey.isBlank()) {
            storage.removeSecret(SecureCredentialStore.GROQ_API_KEY)
        } else {
            storage.saveSecret(SecureCredentialStore.GROQ_API_KEY, apiKey.trim())
        }
    }

    fun saveAsync(context: Context, apiKey: String, callback: (Result<Unit>) -> Unit) {
        val appContext = context.applicationContext
        AppStorageExecutor.submit({
            save(appContext, apiKey)
            Unit
        }, callback)
    }
}
