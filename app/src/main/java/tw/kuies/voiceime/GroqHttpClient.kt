package tw.kuies.voiceime

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

internal object GroqHttpClient {
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 60L
    const val WRITE_TIMEOUT_SECONDS = 60L
    const val CALL_TIMEOUT_SECONDS = 90L

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
