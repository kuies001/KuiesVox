package tw.kuies.voiceime

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureCredentialStoreTest {
    @Test
    fun secretsAreEncryptedInPreferencesAndCanBeReadAndRemoved() {
        val context = isolatedPreferencesContext()
        val storage = SecureCredentialStore(context)
        val storedPreferences = context.getSharedPreferences(
            SecureCredentialStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        storage.removeSecret("test_secret")

        try {
            storage.saveSecret("test_secret", "credential-test-value")

            val encryptedValue = storedPreferences.getString("test_secret", null)
            assertNotNull(encryptedValue)
            assertFalse(requireNotNull(encryptedValue).contains("credential-test-value"))
            assertEquals("credential-test-value", storage.readSecret("test_secret"))

            storage.removeSecret("test_secret")
            assertNull(storage.readSecret("test_secret"))
        } finally {
            storage.removeSecret("test_secret")
        }
    }

    @Test
    fun migratesLegacyKeysAndMcpTokenThenRemovesPlaintextValues() {
        val context = isolatedPreferencesContext()
        val storage = SecureCredentialStore(context)
        val groqPreferences = context.getSharedPreferences("groq_settings", Context.MODE_PRIVATE)
        val formattingPreferences =
            context.getSharedPreferences("text_formatting_api_keys", Context.MODE_PRIVATE)
        val legacyMcpPreferences = context.getSharedPreferences("mcp_settings", Context.MODE_PRIVATE)
        val currentMcpPreferences = context.getSharedPreferences("mcp_config", Context.MODE_PRIVATE)
        val migrationMarker = "credential_migration_v1"

        groqPreferences.edit().clear().putString("api_key", "legacy-groq-key").commit()
        formattingPreferences.edit().clear()
            .putString("gemini_api_key", "legacy-gemini-key")
            .putString("openai_api_key", "legacy-openai-key")
            .commit()
        legacyMcpPreferences.edit().clear()
            .putString("config_v1", legacyMcpConfigJson())
            .commit()
        currentMcpPreferences.edit().clear().commit()
        listOf(
            SecureCredentialStore.GROQ_API_KEY,
            SecureCredentialStore.GEMINI_API_KEY,
            SecureCredentialStore.OPENAI_API_KEY,
            SecureCredentialStore.MCP_BEARER_TOKEN,
            migrationMarker
        ).forEach(storage::removeSecret)

        try {
            CredentialMigration.ensure(context)

            assertEquals("legacy-groq-key", storage.readSecret(SecureCredentialStore.GROQ_API_KEY))
            assertEquals("legacy-gemini-key", storage.readSecret(SecureCredentialStore.GEMINI_API_KEY))
            assertEquals("legacy-openai-key", storage.readSecret(SecureCredentialStore.OPENAI_API_KEY))
            assertEquals("legacy-mcp-token", storage.readSecret(SecureCredentialStore.MCP_BEARER_TOKEN))
            assertFalse(groqPreferences.contains("api_key"))
            assertFalse(formattingPreferences.contains("gemini_api_key"))
            assertFalse(formattingPreferences.contains("openai_api_key"))
            assertFalse(legacyMcpPreferences.contains("config_v1"))

            val safeConfig = JSONObject(requireNotNull(currentMcpPreferences.getString("config_v1", null)))
            assertFalse(safeConfig.has("bearerToken"))
            assertEquals("https://mcp.example.com/mcp", safeConfig.optString("serverUrl"))
            assertEquals("complete", storage.readSecret(migrationMarker))
        } finally {
            groqPreferences.edit().clear().commit()
            formattingPreferences.edit().clear().commit()
            legacyMcpPreferences.edit().clear().commit()
            currentMcpPreferences.edit().clear().commit()
            listOf(
                SecureCredentialStore.GROQ_API_KEY,
                SecureCredentialStore.GEMINI_API_KEY,
                SecureCredentialStore.OPENAI_API_KEY,
                SecureCredentialStore.MCP_BEARER_TOKEN,
                migrationMarker
            ).forEach(storage::removeSecret)
        }
    }

    private fun legacyMcpConfigJson(): String = JSONObject()
        .put("enabled", true)
        .put("serverUrl", "https://mcp.example.com/mcp")
        .put("bearerToken", "legacy-mcp-token")
        .put("selectedResourceUris", JSONArray().put("file:///terms"))
        .put("selectedPromptNames", JSONArray())
        .put("resources", JSONArray())
        .put("prompts", JSONArray())
        .toString()

    private fun isolatedPreferencesContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
}
