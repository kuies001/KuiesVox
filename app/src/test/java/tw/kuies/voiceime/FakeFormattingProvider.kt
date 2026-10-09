package tw.kuies.voiceime

import java.lang.reflect.Proxy
import okhttp3.Call
import okhttp3.Request

/** 可控的格式化 Provider，用來驗證 Prompt 組合與請求次數，不需要真實 API。 */
internal class FakeFormattingProvider(private val response: String) : TextFormattingProvider {
    var requestCount = 0
        private set

    var lastSystemPrompt: String? = null
        private set

    override fun format(
        apiKey: String,
        model: String,
        transcript: String,
        systemPrompt: String,
        onResult: (TextFormattingResult) -> Unit
    ): Call {
        requestCount++
        lastSystemPrompt = systemPrompt
        onResult(TextFormattingResult.Success(response))
        return fakeCall()
    }
}

internal fun fakeCall(): Call {
    var cancelled = false
    return Proxy.newProxyInstance(
        Call::class.java.classLoader,
        arrayOf(Call::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "cancel" -> {
                cancelled = true
                null
            }
            "isCanceled" -> cancelled
            "isExecuted" -> false
            "request" -> Request.Builder().url("https://example.invalid").build()
            "timeout" -> null
            "clone" -> proxy
            "toString" -> "FakeCall"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> null
        }
    } as Call
}
