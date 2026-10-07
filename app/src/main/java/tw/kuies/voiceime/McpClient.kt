package tw.kuies.voiceime

import android.util.Base64
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object McpJsonRpcParser {
    fun parseResponse(body: String, expectedId: String, isEventStream: Boolean = false): JSONObject {
        if (!isEventStream) {
            val response = try {
                JSONObject(body)
            } catch (_: JSONException) {
                throw McpClientException(McpFailureKind.INVALID_JSON)
            }
            return parseResponseObject(response, expectedId)
        }

        var eventData = StringBuilder()
        body.lineSequence().forEach { line ->
            if (line.isEmpty()) {
                val result = parseEventIfMatching(eventData.toString(), expectedId)
                if (result != null) return result
                eventData = StringBuilder()
            } else if (line.startsWith("data:")) {
                if (eventData.isNotEmpty()) eventData.append('\n')
                eventData.append(line.substringAfter(':').trimStart())
            }
        }
        return parseEventIfMatching(eventData.toString(), expectedId)
            ?: throw McpClientException(McpFailureKind.INVALID_JSON)
    }

    fun parseResourceTextContents(result: JSONObject): List<String> {
        val contents = result.optJSONArray("contents")
            ?: throw McpClientException(McpFailureKind.INVALID_JSON)
        val textContents = mutableListOf<String>()
        var hasNonTextContent = false
        for (index in 0 until contents.length()) {
            val content = contents.optJSONObject(index) ?: continue
            val text = content.opt("text")
            if (text is String) {
                textContents += text
            } else if (content.has("blob")) {
                hasNonTextContent = true
            }
        }
        if (textContents.isEmpty() && hasNonTextContent) {
            throw McpClientException(McpFailureKind.UNSUPPORTED_CONTENT)
        }
        return textContents
    }

    fun parsePromptTextContents(result: JSONObject): List<String> {
        val messages = result.optJSONArray("messages")
            ?: throw McpClientException(McpFailureKind.INVALID_JSON)
        val textContents = mutableListOf<String>()
        for (index in 0 until messages.length()) {
            val content = messages.optJSONObject(index)?.optJSONObject("content") ?: continue
            when (content.optString("type")) {
                "text" -> content.opt("text")?.takeIf { it is String }?.let { textContents += it as String }
                "resource" -> {
                    val resource = content.optJSONObject("resource") ?: continue
                    resource.opt("text")?.takeIf { it is String }?.let { textContents += it as String }
                }
            }
        }
        return textContents
    }

    internal fun errorDetails(body: String): Pair<Int, List<String>>? {
        val error = try {
            JSONObject(body).optJSONObject("error")
        } catch (_: JSONException) {
            null
        } ?: return null
        val code = error.optInt("code", Int.MIN_VALUE)
        val supported = error.optJSONObject("data")?.optJSONArray("supported")?.toStringList().orEmpty()
        return code to supported
    }

    private fun parseEventIfMatching(data: String, expectedId: String): JSONObject? {
        if (data.isBlank()) return null
        val response = try {
            JSONObject(data)
        } catch (_: JSONException) {
            return null
        }
        if (response.opt("id")?.toString() != expectedId) return null
        return parseResponseObject(response, expectedId)
    }

    private fun parseResponseObject(response: JSONObject, expectedId: String): JSONObject {
        if (response.optString("jsonrpc") != "2.0" || response.opt("id")?.toString() != expectedId) {
            throw McpClientException(McpFailureKind.INVALID_JSON)
        }
        response.optJSONObject("error")?.let { error ->
            throw McpClientException(
                kind = McpFailureKind.PROTOCOL,
                rpcCode = error.optInt("code", Int.MIN_VALUE),
                supportedVersions = error.optJSONObject("data")?.optJSONArray("supported")?.toStringList().orEmpty()
            )
        }
        val result = response.optJSONObject("result")
            ?: throw McpClientException(McpFailureKind.INVALID_JSON)
        if (result.optString("resultType") == "input_required") {
            throw McpClientException(McpFailureKind.UNSUPPORTED)
        }
        return result
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

internal class McpClient(private val config: McpConfig) {
    private val endpoint: HttpUrl = McpUrlValidator.parse(config.serverUrl)
        ?.toString()
        ?.toHttpUrlOrNull()
        ?: throw McpClientException(McpFailureKind.INVALID_URL)

    init {
        if (endpoint.scheme == "http" && config.bearerToken.isNotBlank()) {
            throw McpClientException(McpFailureKind.INVALID_URL)
        }
    }

    private var requestSequence = 0L
    private var protocolVersion = ""
    private var modernProtocol = false
    private var sessionId: String? = null
    private var resourcesSupported = false
    private var promptsSupported = false
    private var serverName = ""
    private var serverVersion = ""

    fun connect(): McpServerSnapshot {
        val discovery = try {
            requestRpc(
                method = "server/discover",
                params = modernMetadataParameters(),
                version = MCP_MODERN_VERSION,
                modern = true
            )
        } catch (exception: McpClientException) {
            if (shouldFallBackToLegacy(exception)) return connectLegacy()
            if (exception.rpcCode == -32022 && exception.supportedVersions.any(::isLegacyVersion)) {
                return connectLegacy()
            }
            throw exception
        }

        val supportedVersions = discovery.optJSONArray("supportedVersions")?.toStringList().orEmpty()
        if (MCP_MODERN_VERSION !in supportedVersions) {
            if (supportedVersions.any(::isLegacyVersion)) return connectLegacy()
            throw McpClientException(McpFailureKind.UNSUPPORTED)
        }

        modernProtocol = true
        protocolVersion = MCP_MODERN_VERSION
        readServerMetadata(discovery)
        return snapshot(emptyList(), emptyList())
    }

    fun listResources(): List<McpResourceDescriptor> {
        requireConnected()
        if (!resourcesSupported) return emptyList()
        return paginatedList("resources/list", "resources") { item ->
            val uri = item.optString("uri", "").trim()
            if (uri.isEmpty()) null else McpResourceDescriptor(
                uri = uri,
                name = item.optString("name", uri),
                title = item.optString("title", ""),
                description = item.optString("description", ""),
                mimeType = item.optString("mimeType", "")
            )
        }.distinctBy { it.uri }
    }

    fun listPrompts(): List<McpPromptDescriptor> {
        requireConnected()
        if (!promptsSupported) return emptyList()
        return paginatedList("prompts/list", "prompts") { item ->
            val name = item.optString("name", "").trim()
            if (name.isEmpty()) null else {
                val requiredArguments = buildList {
                    val arguments = item.optJSONArray("arguments") ?: return@buildList
                    for (index in 0 until arguments.length()) {
                        val argument = arguments.optJSONObject(index) ?: continue
                        if (argument.optBoolean("required", false)) {
                            argument.optString("name", "").trim().takeIf(String::isNotEmpty)?.let(::add)
                        }
                    }
                }
                McpPromptDescriptor(
                    name = name,
                    title = item.optString("title", ""),
                    description = item.optString("description", ""),
                    requiredArguments = requiredArguments
                )
            }
        }.distinctBy { it.name }
    }

    fun readResource(uri: String): List<String> {
        requireConnected()
        if (!resourcesSupported) throw McpClientException(McpFailureKind.UNSUPPORTED)
        val params = JSONObject().put("uri", uri)
        val result = requestRpc("resources/read", params, routeName = uri)
        return McpJsonRpcParser.parseResourceTextContents(result)
    }

    fun getPrompt(name: String): List<String> {
        requireConnected()
        if (!promptsSupported) throw McpClientException(McpFailureKind.UNSUPPORTED)
        val params = JSONObject().put("name", name).put("arguments", JSONObject())
        val result = requestRpc("prompts/get", params, routeName = name)
        return McpJsonRpcParser.parsePromptTextContents(result)
    }

    fun close() {
        val activeSession = sessionId ?: return
        if (modernProtocol) return
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("Mcp-Session-Id", activeSession)
            .delete()
        if (protocolVersion != "2025-03-26") {
            requestBuilder.header("MCP-Protocol-Version", protocolVersion)
        }
        config.bearerToken.trim().takeIf(String::isNotEmpty)?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        try {
            HTTP_CLIENT.newCall(requestBuilder.build()).execute().use { }
        } catch (_: Exception) {
            Unit
        } finally {
            sessionId = null
        }
    }

    private fun connectLegacy(): McpServerSnapshot {
        modernProtocol = false
        protocolVersion = ""
        sessionId = null
        val params = JSONObject()
            .put("protocolVersion", MCP_LEGACY_VERSION)
            .put("capabilities", JSONObject())
            .put("clientInfo", clientInfo())
        val initialize = requestRpc("initialize", params, version = "", modern = false)
        val acceptedVersion = initialize.optString("protocolVersion", "")
        if (!isLegacyVersion(acceptedVersion)) throw McpClientException(McpFailureKind.UNSUPPORTED)
        protocolVersion = acceptedVersion
        readServerMetadata(initialize)
        postInitializedNotification()
        return snapshot(emptyList(), emptyList())
    }

    private fun readServerMetadata(result: JSONObject) {
        val capabilities = result.optJSONObject("capabilities") ?: JSONObject()
        resourcesSupported = capabilities.has("resources") && !capabilities.isNull("resources")
        promptsSupported = capabilities.has("prompts") && !capabilities.isNull("prompts")
        val metadata = result.optJSONObject("_meta")
        val serverInfo = result.optJSONObject("serverInfo")
            ?: metadata?.optJSONObject("io.modelcontextprotocol/serverInfo")
        serverName = serverInfo?.optString("name", "").orEmpty()
        serverVersion = serverInfo?.optString("version", "").orEmpty()
    }

    private fun snapshot(
        resources: List<McpResourceDescriptor>,
        prompts: List<McpPromptDescriptor>
    ) = McpServerSnapshot(
        serverName = serverName,
        serverVersion = serverVersion,
        protocolVersion = protocolVersion,
        resourcesSupported = resourcesSupported,
        promptsSupported = promptsSupported,
        resources = resources,
        prompts = prompts
    )

    private fun <T : Any> paginatedList(
        method: String,
        arrayName: String,
        convert: (JSONObject) -> T?
    ): List<T> {
        val values = mutableListOf<T>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val params = JSONObject()
            cursor?.let { params.put("cursor", it) }
            val result = requestRpc(method, params)
            val items = result.optJSONArray(arrayName)
                ?: throw McpClientException(McpFailureKind.INVALID_JSON)
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                convert(item)?.let(values::add)
                if (values.size >= MAX_CATALOG_ITEMS) return values
            }
            val nextCursor = result.optString("nextCursor", "").takeIf(String::isNotBlank) ?: return values
            if (!seenCursors.add(nextCursor)) throw McpClientException(McpFailureKind.PROTOCOL)
            cursor = nextCursor
        }
        return values
    }

    private fun requestRpc(
        method: String,
        params: JSONObject,
        routeName: String? = null,
        version: String = protocolVersion,
        modern: Boolean = modernProtocol,
        retryExpiredSession: Boolean = true
    ): JSONObject {
        val id = "voiceime-${++requestSequence}"
        val requestJson = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", if (modern) addModernMetadata(params, version) else params)
        val response = post(requestJson, method, routeName, version, modern)
        val body = response.body
        if (response.status == 404 && !modern && sessionId != null && retryExpiredSession) {
            sessionId = null
            protocolVersion = ""
            connectLegacy()
            return requestRpc(
                method = method,
                params = params,
                routeName = routeName,
                version = protocolVersion,
                modern = false,
                retryExpiredSession = false
            )
        }
        if (response.status !in 200..299) {
            val details = McpJsonRpcParser.errorDetails(body)
            throw McpClientException(
                kind = if (response.status == 401 || response.status == 403) {
                    McpFailureKind.AUTHENTICATION
                } else {
                    McpFailureKind.HTTP
                },
                httpStatus = response.status,
                rpcCode = details?.first,
                supportedVersions = details?.second.orEmpty()
            )
        }
        return try {
            McpJsonRpcParser.parseResponse(
                body = body,
                expectedId = id,
                isEventStream = response.contentType?.startsWith("text/event-stream", ignoreCase = true) == true
            )
        } catch (exception: McpClientException) {
            if (exception.httpStatus != null) throw exception
            throw McpClientException(
                kind = exception.kind,
                httpStatus = response.status,
                rpcCode = exception.rpcCode,
                supportedVersions = exception.supportedVersions
            )
        }
    }

    private fun postInitializedNotification() {
        val notification = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", "notifications/initialized")
        val response = post(notification, "notifications/initialized", null, protocolVersion, modern = false)
        if (response.status !in 200..299) {
            throw McpClientException(
                kind = if (response.status == 401 || response.status == 403) {
                    McpFailureKind.AUTHENTICATION
                } else {
                    McpFailureKind.HTTP
                },
                httpStatus = response.status
            )
        }
    }

    private fun post(
        payload: JSONObject,
        method: String,
        routeName: String?,
        version: String,
        modern: Boolean
    ): McpHttpResponse {
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        val token = config.bearerToken.trim()
        if (token.isNotEmpty()) requestBuilder.header("Authorization", "Bearer $token")
        if (version.isNotEmpty() && version != "2025-03-26") {
            requestBuilder.header("MCP-Protocol-Version", version)
        }
        if (modern) {
            requestBuilder.header("Mcp-Method", method)
            routeName?.let { requestBuilder.header("Mcp-Name", encodeHeaderValue(it)) }
        } else if (sessionId != null) {
            requestBuilder.header("Mcp-Session-Id", sessionId!!)
        }

        try {
            HTTP_CLIENT.newCall(requestBuilder.build()).execute().use { response ->
                response.header("Mcp-Session-Id")?.let { sessionId = it }
                val body = readResponseBody(response)
                return McpHttpResponse(
                    status = response.code,
                    contentType = response.header("Content-Type"),
                    body = body
                )
            }
        } catch (exception: McpClientException) {
            throw exception
        } catch (_: IOException) {
            throw McpClientException(McpFailureKind.NETWORK)
        }
    }

    private fun readResponseBody(response: Response): String {
        val responseBody = response.body
        val result = StringBuilder()
        try {
            responseBody.source().use { source ->
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (result.length + line.length + 1 > MAX_RESPONSE_CHARACTERS) {
                        throw McpClientException(McpFailureKind.UNSUPPORTED)
                    }
                    if (result.isNotEmpty()) result.append('\n')
                    result.append(line)
                }
            }
        } catch (exception: McpClientException) {
            throw exception
        } catch (_: IOException) {
            throw McpClientException(McpFailureKind.NETWORK)
        }
        return result.toString()
    }

    private fun modernMetadataParameters(): JSONObject = JSONObject()
        .put("_meta", metadataObject(MCP_MODERN_VERSION))

    private fun addModernMetadata(params: JSONObject, version: String): JSONObject =
        JSONObject(params.toString()).put("_meta", metadataObject(version))

    private fun metadataObject(version: String): JSONObject = JSONObject()
        .put("io.modelcontextprotocol/protocolVersion", version)
        .put("io.modelcontextprotocol/clientInfo", clientInfo())
        .put("io.modelcontextprotocol/clientCapabilities", JSONObject())

    private fun clientInfo() = JSONObject()
        .put("name", "KuiesVox")
        .put("version", "0.6.0")

    private fun requireConnected() {
        if (protocolVersion.isEmpty()) throw McpClientException(McpFailureKind.PROTOCOL)
    }

    private fun shouldFallBackToLegacy(exception: McpClientException): Boolean {
        if (exception.kind == McpFailureKind.AUTHENTICATION) return false
        if (exception.httpStatus == 404 || exception.httpStatus == 405 || exception.rpcCode == -32601) return true
        if (exception.httpStatus == 400) {
            if (exception.rpcCode == -32022) return exception.supportedVersions.any(::isLegacyVersion)
            if (exception.rpcCode in MODERN_ERROR_CODES) return false
            return true
        }
        return false
    }

    private fun isLegacyVersion(version: String): Boolean = version in LEGACY_VERSIONS

    private fun encodeHeaderValue(value: String): String {
        val visibleAscii = value.all { it.code in 0x20..0x7E }
        val hasOuterWhitespace = value != value.trim()
        val usesEncodingSentinel = value.startsWith("=?base64?") && value.endsWith("?=")
        if (visibleAscii && !hasOuterWhitespace && !usesEncodingSentinel) return value
        val encoded = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "=?base64?$encoded?="
    }

    private data class McpHttpResponse(
        val status: Int,
        val contentType: String?,
        val body: String
    )

    companion object {
        private const val MAX_PAGES = 20
        private const val MAX_CATALOG_ITEMS = 500
        private const val MAX_RESPONSE_CHARACTERS = 1_000_000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LEGACY_VERSIONS = setOf("2025-11-25", "2025-06-18", "2025-03-26")
        private val MODERN_ERROR_CODES = setOf(-32020, -32021, -32022)
        private val HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}
