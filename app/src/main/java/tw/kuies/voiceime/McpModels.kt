package tw.kuies.voiceime

import java.net.URI

internal const val MCP_MODERN_VERSION = "2026-07-28"
internal const val MCP_LEGACY_VERSION = "2025-11-25"

internal enum class McpFailureKind {
    INVALID_URL,
    NETWORK,
    AUTHENTICATION,
    HTTP,
    PROTOCOL,
    INVALID_JSON,
    UNSUPPORTED,
    UNSUPPORTED_CONTENT,
    STORAGE
}

internal class McpClientException(
    val kind: McpFailureKind,
    val httpStatus: Int? = null,
    val rpcCode: Int? = null,
    val supportedVersions: List<String> = emptyList()
) : Exception(kind.name)

internal data class McpResourceDescriptor(
    val uri: String,
    val name: String,
    val title: String = "",
    val description: String = "",
    val mimeType: String = ""
)

internal data class McpPromptDescriptor(
    val name: String,
    val title: String = "",
    val description: String = "",
    val requiredArguments: List<String> = emptyList()
)

internal data class McpConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val bearerToken: String = "",
    val selectedResourceUris: Set<String> = emptySet(),
    val selectedPromptNames: Set<String> = emptySet(),
    val serverName: String = "",
    val serverVersion: String = "",
    val protocolVersion: String = "",
    val resourcesSupported: Boolean = false,
    val promptsSupported: Boolean = false,
    val resources: List<McpResourceDescriptor> = emptyList(),
    val prompts: List<McpPromptDescriptor> = emptyList()
)

internal data class McpServerSnapshot(
    val serverName: String,
    val serverVersion: String,
    val protocolVersion: String,
    val resourcesSupported: Boolean,
    val promptsSupported: Boolean,
    val resources: List<McpResourceDescriptor>,
    val prompts: List<McpPromptDescriptor>
)

internal data class McpContextCache(
    val configurationKey: String,
    val fetchedAtMillis: Long,
    val terms: List<String>
)

internal data class McpRefreshResult(
    val config: McpConfig,
    val termCount: Int
)

internal object McpUrlValidator {
    fun parse(rawUrl: String): URI? {
        val uri = try {
            URI(rawUrl.trim())
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        if (scheme == "http" && !isLoopbackHost(uri.host)) return null
        return uri
    }

    fun isValid(rawUrl: String, bearerToken: String = ""): Boolean {
        val uri = parse(rawUrl) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) || bearerToken.isBlank()
    }

    private fun isLoopbackHost(host: String): Boolean {
        val normalized = host.lowercase().removeSurrounding("[", "]")
        return normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1"
    }
}

internal object McpContextCacheRules {
    const val DEFAULT_TTL_MILLIS = 15 * 60 * 1000L

    fun isFresh(
        cache: McpContextCache?,
        configurationKey: String,
        nowMillis: Long,
        ttlMillis: Long = DEFAULT_TTL_MILLIS
    ): Boolean = cache != null &&
        cache.configurationKey == configurationKey &&
        nowMillis >= cache.fetchedAtMillis &&
        nowMillis - cache.fetchedAtMillis < ttlMillis

    fun fallback(cache: McpContextCache?, configurationKey: String): List<String> =
        cache?.takeIf { it.configurationKey == configurationKey }?.terms.orEmpty()
}

internal object McpContextTextParser {
    const val MAX_TERMS = 500
    private const val MAX_INPUT_CHARACTERS = 100_000
    private val separators = charArrayOf('\n', '\r', ',', '，', ';', '；', '、')

    fun parse(contents: List<String>): List<String> {
        val terms = mutableListOf<String>()
        val knownTerms = mutableSetOf<String>()
        var charactersRead = 0
        contents.forEach { content ->
            val boundedContent = content.take(MAX_INPUT_CHARACTERS - charactersRead)
            charactersRead += boundedContent.length
            boundedContent.split(*separators).forEach { candidate ->
                val term = candidate.trim()
                if (term.isNotEmpty() && knownTerms.add(PersonalGlossaryRules.keyFor(term))) {
                    terms += term
                    if (terms.size >= MAX_TERMS) return terms
                }
            }
            if (charactersRead >= MAX_INPUT_CHARACTERS) return terms
        }
        return terms
    }
}

internal object McpFailureMessages {
    fun describe(exception: Throwable): String = when ((exception as? McpClientException)?.kind) {
        McpFailureKind.INVALID_URL -> "URL 格式錯誤，僅支援 http 或 https。"
        McpFailureKind.NETWORK -> "連線失敗，請檢查網路、DNS 或伺服器狀態。"
        McpFailureKind.AUTHENTICATION -> "驗證失敗，請檢查 Bearer Token。"
        McpFailureKind.HTTP -> "伺服器回應 HTTP ${exception.httpStatus ?: "錯誤"}。"
        McpFailureKind.PROTOCOL -> "MCP protocol 回應錯誤。"
        McpFailureKind.INVALID_JSON -> "MCP 回應格式無法解析。"
        McpFailureKind.UNSUPPORTED -> "伺服器不支援所需的 MCP capability 或 protocol。"
        McpFailureKind.UNSUPPORTED_CONTENT -> "所選 Resource 沒有可用的文字內容。"
        McpFailureKind.STORAGE -> "MCP 設定或快取無法讀取。"
        null -> "連線失敗，請檢查伺服器設定。"
    }
}
