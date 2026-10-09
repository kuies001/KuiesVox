package tw.kuies.voiceime

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * MCP 連線的 OkHttp 安全設定。
 *
 * 一律不跟隨重新導向（含 SSL 重新導向），所以 301／302／307／308 都會以非 2xx 失敗收場，
 * `Authorization: Bearer` 標頭不可能被轉送到另一個主機。
 */
internal object McpHttpPolicy {
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 20L
    const val CALL_TIMEOUT_SECONDS = 30L

    fun builder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)

    fun client(): OkHttpClient = builder().build()
}
