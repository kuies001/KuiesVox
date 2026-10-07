package tw.kuies.voiceime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal object McpConfigRepository {
    private const val PREFERENCES_NAME = "mcp_config"
    private const val LEGACY_PREFERENCES_NAME = "mcp_settings"
    private const val CONFIG_KEY = "config_v1"

    fun load(context: Context, callback: (Result<McpConfig>) -> Unit) {
        submit(callback) { loadSync(context.applicationContext) }
    }

    fun save(context: Context, config: McpConfig, callback: (Result<Unit>) -> Unit) {
        submit(callback) { saveSync(context.applicationContext, config) }
    }

    internal fun loadSync(context: Context): McpConfig {
        CredentialMigration.ensure(context)
        val bearerToken = SecureCredentialStore(context)
            .readSecret(SecureCredentialStore.MCP_BEARER_TOKEN)
            .orEmpty()
        val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(CONFIG_KEY, null)
            ?: return McpConfig(bearerToken = bearerToken)
        if (stored.isBlank()) return McpConfig(bearerToken = bearerToken)

        return decodeConfig(stored).copy(bearerToken = bearerToken)
    }

    internal fun saveSync(context: Context, config: McpConfig) {
        CredentialMigration.ensure(context)
        val storage = SecureCredentialStore(context)
        if (config.bearerToken.isBlank()) {
            storage.removeSecret(SecureCredentialStore.MCP_BEARER_TOKEN)
        } else {
            storage.saveSecret(SecureCredentialStore.MCP_BEARER_TOKEN, config.bearerToken.trim())
        }
        writeConfig(context, config)
    }

    internal fun migrateLegacy(context: Context, storage: SecretStorage) {
        val legacyPreferences =
            context.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = legacyPreferences.getString(CONFIG_KEY, null) ?: return
        if (stored.isBlank()) {
            if (!legacyPreferences.edit().remove(CONFIG_KEY).commit()) {
                throw IOException("Legacy MCP configuration could not be removed")
            }
            return
        }

        val legacyConfig = decodeConfig(stored)
        val currentPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!currentPreferences.contains(CONFIG_KEY)) {
            writeConfig(context, legacyConfig)
        }

        CredentialMigrationLogic.migrateSecret(
            storage = storage,
            secretName = SecureCredentialStore.MCP_BEARER_TOKEN,
            readLegacy = { legacyConfig.bearerToken },
            removeLegacy = { legacyPreferences.edit().remove(CONFIG_KEY).commit() }
        )
    }

    internal fun encodeConfig(config: McpConfig): String {
        val resources = JSONArray()
        config.resources.forEach { resource ->
            resources.put(
                JSONObject()
                    .put("uri", resource.uri)
                    .put("name", resource.name)
                    .put("title", resource.title)
                    .put("description", resource.description)
                    .put("mimeType", resource.mimeType)
            )
        }
        val prompts = JSONArray()
        config.prompts.forEach { prompt ->
            prompts.put(
                JSONObject()
                    .put("name", prompt.name)
                    .put("title", prompt.title)
                    .put("description", prompt.description)
                    .put("requiredArguments", JSONArray(prompt.requiredArguments))
            )
        }
        return JSONObject()
            .put("enabled", config.enabled)
            .put("serverUrl", config.serverUrl)
            .put("selectedResourceUris", JSONArray(config.selectedResourceUris.toList()))
            .put("selectedPromptNames", JSONArray(config.selectedPromptNames.toList()))
            .put("serverName", config.serverName)
            .put("serverVersion", config.serverVersion)
            .put("protocolVersion", config.protocolVersion)
            .put("resourcesSupported", config.resourcesSupported)
            .put("promptsSupported", config.promptsSupported)
            .put("resources", resources)
            .put("prompts", prompts)
            .toString()
    }

    internal fun decodeConfig(stored: String): McpConfig {
        val json = JSONObject(stored)
        return McpConfig(
            enabled = json.optBoolean("enabled", false),
            serverUrl = json.optString("serverUrl", ""),
            bearerToken = json.optString("bearerToken", ""),
            selectedResourceUris = json.readStringSet("selectedResourceUris"),
            selectedPromptNames = json.readStringSet("selectedPromptNames"),
            serverName = json.optString("serverName", ""),
            serverVersion = json.optString("serverVersion", ""),
            protocolVersion = json.optString("protocolVersion", ""),
            resourcesSupported = json.optBoolean("resourcesSupported", false),
            promptsSupported = json.optBoolean("promptsSupported", false),
            resources = json.optJSONArray("resources")?.let(::parseResources).orEmpty(),
            prompts = json.optJSONArray("prompts")?.let(::parsePrompts).orEmpty()
        )
    }

    private fun writeConfig(context: Context, config: McpConfig) {
        val saved = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(CONFIG_KEY, encodeConfig(config))
            .commit()
        if (!saved) throw IOException("MCP configuration could not be persisted")
    }

    private fun parseResources(json: JSONArray): List<McpResourceDescriptor> = buildList {
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            val uri = item.optString("uri", "").trim()
            if (uri.isEmpty()) continue
            add(
                McpResourceDescriptor(
                    uri = uri,
                    name = item.optString("name", uri),
                    title = item.optString("title", ""),
                    description = item.optString("description", ""),
                    mimeType = item.optString("mimeType", "")
                )
            )
        }
    }

    private fun parsePrompts(json: JSONArray): List<McpPromptDescriptor> = buildList {
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) continue
            val requiredArguments = buildList {
                val arguments = item.optJSONArray("arguments") ?: return@buildList
                for (argumentIndex in 0 until arguments.length()) {
                    val argument = arguments.optJSONObject(argumentIndex) ?: continue
                    if (argument.optBoolean("required", false)) {
                        val argumentName = argument.optString("name", "").trim()
                        if (argumentName.isNotEmpty()) add(argumentName)
                    }
                }
            }
            add(
                McpPromptDescriptor(
                    name = name,
                    title = item.optString("title", ""),
                    description = item.optString("description", ""),
                    requiredArguments = requiredArguments
                )
            )
        }
    }

    private fun JSONObject.readStringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
    }

    private fun <T> submit(callback: (Result<T>) -> Unit, operation: () -> T) {
        AppStorageExecutor.submit(operation, callback)
    }
}
