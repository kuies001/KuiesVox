package tw.kuies.voiceime

/**
 * MCP Session ID 來自伺服器回應，之後會被放進下一個請求的標頭。
 * 只接受可安全放入 HTTP 標頭的字元（可見 ASCII、不含空白），
 * 避免 CRLF 等字元造成標頭注入，也避免 OkHttp 設定標頭時丟出未捕捉的
 * IllegalArgumentException。無法接受時回傳 null，呼叫端維持原有 session。
 */
internal object McpSessionIdPolicy {
    private const val MAX_LENGTH = 256
    private const val MIN_PRINTABLE = 0x21
    private const val MAX_PRINTABLE = 0x7e

    fun sanitizeOrNull(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > MAX_LENGTH) return null
        if (value.any { it.code < MIN_PRINTABLE || it.code > MAX_PRINTABLE }) return null
        return value
    }
}
