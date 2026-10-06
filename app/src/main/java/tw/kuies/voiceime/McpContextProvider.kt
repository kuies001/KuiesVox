package tw.kuies.voiceime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors

internal object McpContextProvider {
    private const val CACHE_PREFERENCES = "mcp_context_cache"
    private const val CACHE_ENTRY_KEY = "last_success_v1"
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "VoiceImeMcpContext").apply { isDaemon = true }
    }

    fun getContext(context: Context, callback: (List<String>) -> Unit) {
        val appContext = context.applicationContext
        try {
            executor.execute {
                val result = try {
                    val config = McpConfigRepository.loadSync(appContext)
                    if (!config.enabled || config.serverUrl.isBlank() || !hasSelections(config)) {
                        emptyList()
                    } else {
                        val cacheKey = configurationKey(config)
                        val cache = readCache(appContext)
                        val now = System.currentTimeMillis()
                        if (McpContextCacheRules.isFresh(cache, cacheKey, now)) {
                            cache?.terms.orEmpty()
                        } else {
                            try {
                                val terms = fetchSelectedContext(config)
                                writeCache(appContext, McpContextCache(cacheKey, now, terms))
                                terms
                            } catch (_: Exception) {
                                McpContextCacheRules.fallback(cache, cacheKey)
                            }
                        }
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                try {
                    callback(result)
                } catch (_: Exception) {
                    Unit
                }
            }
        } catch (_: Exception) {
            callbackSafely(callback, emptyList())
        }
    }

    fun testConnection(
        context: Context,
        config: McpConfig,
        callback: (Result<McpServerSnapshot>) -> Unit
    ) {
        runOnMcpExecutor(callback) {
            val client = McpClient(config)
            try {
                val connected = client.connect()
                connected.copy(
                    resources = client.listResources(),
                    prompts = client.listPrompts()
                )
            } finally {
                client.close()
            }
        }
    }

    fun refresh(
        context: Context,
        config: McpConfig,
        callback: (Result<McpRefreshResult>) -> Unit
    ) {
        val appContext = context.applicationContext
        runOnMcpExecutor(callback) {
            val client = McpClient(config)
            try {
                val connected = client.connect()
                val resources = client.listResources()
                val prompts = client.listPrompts()
                val availableResourceUris = resources.mapTo(mutableSetOf()) { it.uri }
                val availablePrompts = prompts.filter { it.requiredArguments.isEmpty() }
                    .mapTo(mutableSetOf()) { it.name }
                val refreshedConfig = config.copy(
                    serverName = connected.serverName,
                    serverVersion = connected.serverVersion,
                    protocolVersion = connected.protocolVersion,
                    resourcesSupported = connected.resourcesSupported,
                    promptsSupported = connected.promptsSupported,
                    resources = resources,
                    prompts = prompts,
                    selectedResourceUris = config.selectedResourceUris.intersect(availableResourceUris),
                    selectedPromptNames = config.selectedPromptNames.intersect(availablePrompts)
                )
                val terms = McpContextTextParser.parse(readSelectedContents(client, refreshedConfig))
                val cache = McpContextCache(
                    configurationKey(refreshedConfig),
                    System.currentTimeMillis(),
                    terms
                )
                writeCache(appContext, cache)
                McpRefreshResult(refreshedConfig, terms.size)
            } finally {
                client.close()
            }
        }
    }

    private fun fetchSelectedContext(config: McpConfig): List<String> {
        if (!hasSelections(config)) return emptyList()
        val client = McpClient(config)
        return try {
            client.connect()
            McpContextTextParser.parse(readSelectedContents(client, config))
        } finally {
            client.close()
        }
    }

    private fun readSelectedContents(client: McpClient, config: McpConfig): List<String> {
        val content = mutableListOf<String>()
        var totalCharacters = 0
        var sourcesRead = 0
        for (uri in config.selectedResourceUris) {
            if (sourcesRead >= MAX_SELECTED_SOURCES || totalCharacters >= MAX_CONTEXT_CHARACTERS) break
            sourcesRead++
            client.readResource(uri).forEach { text ->
                val boundedText = text.take(MAX_CONTEXT_CHARACTERS - totalCharacters)
                if (boundedText.isNotEmpty()) content += boundedText
                totalCharacters += boundedText.length
                if (totalCharacters >= MAX_CONTEXT_CHARACTERS) return content
            }
        }
        val selectedPrompts = config.prompts
            .filter { it.name in config.selectedPromptNames && it.requiredArguments.isEmpty() }
        for (prompt in selectedPrompts) {
            if (sourcesRead >= MAX_SELECTED_SOURCES || totalCharacters >= MAX_CONTEXT_CHARACTERS) break
            sourcesRead++
            client.getPrompt(prompt.name).forEach { text ->
                val boundedText = text.take(MAX_CONTEXT_CHARACTERS - totalCharacters)
                if (boundedText.isNotEmpty()) content += boundedText
                totalCharacters += boundedText.length
                if (totalCharacters >= MAX_CONTEXT_CHARACTERS) return content
            }
        }
        return content
    }

    private fun hasSelections(config: McpConfig): Boolean =
        config.selectedResourceUris.isNotEmpty() || config.selectedPromptNames.any { name ->
            config.prompts.any { it.name == name && it.requiredArguments.isEmpty() }
        }

    private fun configurationKey(config: McpConfig): String {
        val identity = buildString {
            append(config.serverUrl.trim())
            append('\u0000')
            append(config.bearerToken.trim())
            append('\u0000')
            config.selectedResourceUris.sorted().forEach { append(it).append('\u0000') }
            config.selectedPromptNames.sorted().forEach { append(it).append('\u0000') }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun readCache(context: Context): McpContextCache? = try {
        val serialized = context.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
            .getString(CACHE_ENTRY_KEY, null)
            ?: return null
        val json = JSONObject(serialized)
        val termsJson = json.optJSONArray("terms") ?: JSONArray()
        val terms = buildList {
            for (index in 0 until termsJson.length()) {
                termsJson.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        McpContextCache(
            configurationKey = json.optString("configurationKey", ""),
            fetchedAtMillis = json.optLong("fetchedAtMillis", 0L),
            terms = terms
        )
    } catch (_: Exception) {
        null
    }

    private fun writeCache(context: Context, cache: McpContextCache) {
        try {
            val json = JSONObject()
                .put("configurationKey", cache.configurationKey)
                .put("fetchedAtMillis", cache.fetchedAtMillis)
                .put("terms", JSONArray(cache.terms))
            val saved = context.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(CACHE_ENTRY_KEY, json.toString())
                .commit()
            if (!saved) throw IOException("MCP context cache could not be persisted")
        } catch (_: Exception) {
            Unit
        }
    }

    private fun <T> runOnMcpExecutor(
        callback: (Result<T>) -> Unit,
        operation: () -> T
    ) {
        try {
            executor.execute {
                val result = try {
                    Result.success(operation())
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
                callbackSafely(callback, result)
            }
        } catch (exception: Exception) {
            callbackSafely(callback, Result.failure(exception))
        }
    }

    private fun <T> callbackSafely(callback: (T) -> Unit, value: T) {
        try {
            callback(value)
        } catch (_: Exception) {
            Unit
        }
    }

    private const val MAX_SELECTED_SOURCES = 50
    private const val MAX_CONTEXT_CHARACTERS = 100_000
}
