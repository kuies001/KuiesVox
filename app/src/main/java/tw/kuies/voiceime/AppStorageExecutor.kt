package tw.kuies.voiceime

import java.util.concurrent.Executors

internal object AppStorageExecutor {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "VoiceImePrivateStorage").apply { isDaemon = true }
    }

    fun <T> submit(operation: () -> T, callback: (Result<T>) -> Unit) {
        executor.execute {
            val result = try {
                Result.success(operation())
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            callback(result)
        }
    }
}
