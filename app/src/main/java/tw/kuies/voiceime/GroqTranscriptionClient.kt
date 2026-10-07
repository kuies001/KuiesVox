package tw.kuies.voiceime

import java.io.File
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

internal sealed interface GroqTranscriptionResult {
    data class Success(val text: String) : GroqTranscriptionResult
    data class Failure(val type: String, val httpStatus: Int? = null) : GroqTranscriptionResult
}

internal object GroqTranscriptionClient {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"

    fun createCall(
        apiKey: String,
        audioFile: File,
        prompt: String? = null,
        model: String = SmartFormattingSettings.DEFAULT_SPEECH_MODEL
    ): Call {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(createRequestBody(audioFile, prompt, model))
            .build()

        return GroqHttpClient.client.newCall(request)
    }

    internal fun createRequestBody(
        audioFile: File,
        prompt: String?,
        model: String = SmartFormattingSettings.DEFAULT_SPEECH_MODEL
    ): MultipartBody {
        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType())
            )

        if (!prompt.isNullOrBlank()) {
            requestBodyBuilder.addFormDataPart("prompt", prompt)
        }

        return requestBodyBuilder
            .addFormDataPart(
                "model",
                model.takeIf { candidate -> FormattingModels.speech.any { it.id == candidate } }
                    ?: SmartFormattingSettings.DEFAULT_SPEECH_MODEL
            )
            .addFormDataPart("response_format", "json")
            .addFormDataPart("language", "zh")
            .addFormDataPart("temperature", "0")
            .build()
    }

    fun enqueue(call: Call, onResult: (GroqTranscriptionResult) -> Unit) {
        call.enqueue(object : Callback {
            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun onFailure(call: Call, exception: IOException) {
                onResult(
                    GroqTranscriptionResult.Failure(
                        if (call.isCanceled()) "cancelled" else "network_error"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { parseResponse(it) }
                } catch (exception: Exception) {
                    GroqTranscriptionResult.Failure("response_error")
                }
                onResult(result)
            }
        })
    }

    private fun parseResponse(response: Response): GroqTranscriptionResult {
        if (!response.isSuccessful) {
            return GroqTranscriptionResult.Failure("http_error", response.code)
        }

        val responseBody = try {
            response.body.string()
        } catch (exception: IOException) {
            return GroqTranscriptionResult.Failure("response_read_error")
        }
        if (responseBody.isNullOrBlank()) {
            return GroqTranscriptionResult.Failure("empty_response")
        }

        return try {
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                GroqTranscriptionResult.Failure("groq_error", response.code)
            } else {
                val text = json.optString("text").trim()
                if (text.isEmpty()) {
                    GroqTranscriptionResult.Failure("empty_text")
                } else {
                    GroqTranscriptionResult.Success(text)
                }
            }
        } catch (exception: JSONException) {
            GroqTranscriptionResult.Failure("invalid_json")
        }
    }
}
