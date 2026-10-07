package tw.kuies.voiceime

import android.content.Context
import java.io.IOException

internal object CredentialMigration {
    private const val MIGRATION_MARKER = "credential_migration_v1"
    private const val MIGRATION_COMPLETE = "complete"
    private val lock = Any()

    fun ensure(context: Context) = synchronized(lock) {
        val storage = SecureCredentialStore(context)
        CredentialMigrationLogic.migrateOnce(storage, MIGRATION_MARKER, MIGRATION_COMPLETE) {
            val groqPreferences = context.getSharedPreferences("groq_settings", Context.MODE_PRIVATE)
            CredentialMigrationLogic.migrateSecret(
                storage = storage,
                secretName = SecureCredentialStore.GROQ_API_KEY,
                readLegacy = { groqPreferences.getString("api_key", null) },
                removeLegacy = { groqPreferences.edit().remove("api_key").commit() }
            )

            val formattingPreferences =
                context.getSharedPreferences("text_formatting_api_keys", Context.MODE_PRIVATE)
            CredentialMigrationLogic.migrateSecret(
                storage = storage,
                secretName = SecureCredentialStore.GEMINI_API_KEY,
                readLegacy = { formattingPreferences.getString("gemini_api_key", null) },
                removeLegacy = { formattingPreferences.edit().remove("gemini_api_key").commit() }
            )
            CredentialMigrationLogic.migrateSecret(
                storage = storage,
                secretName = SecureCredentialStore.OPENAI_API_KEY,
                readLegacy = { formattingPreferences.getString("openai_api_key", null) },
                removeLegacy = { formattingPreferences.edit().remove("openai_api_key").commit() }
            )

            McpConfigRepository.migrateLegacy(context, storage)
        }
    }
}

internal object CredentialMigrationLogic {
    fun migrateOnce(
        storage: SecretStorage,
        markerName: String,
        completeValue: String,
        migrate: () -> Unit
    ) {
        if (storage.readSecret(markerName) == completeValue) return
        migrate()
        storage.saveSecret(markerName, completeValue)
        if (storage.readSecret(markerName) != completeValue) {
            throw IOException("Credential migration could not be confirmed")
        }
    }

    fun migrateSecret(
        storage: SecretStorage,
        secretName: String,
        readLegacy: () -> String?,
        removeLegacy: () -> Boolean
    ) {
        val legacyRaw = readLegacy() ?: return
        val legacyValue = legacyRaw.trim()
        if (legacyValue.isEmpty()) {
            if (!removeLegacy()) throw IOException("Legacy credential could not be removed")
            return
        }

        val existingValue = storage.readSecret(secretName)
        if (existingValue == null) storage.saveSecret(secretName, legacyValue)
        val expectedValue = existingValue ?: legacyValue
        if (storage.readSecret(secretName) != expectedValue) {
            throw IOException("Credential migration could not be confirmed")
        }
        if (!removeLegacy()) throw IOException("Legacy credential could not be removed")
    }
}
