package tw.kuies.voiceime

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal sealed interface GroqTextFormattingResult {
    data class Success(val text: String) : GroqTextFormattingResult
    data class Failure(val type: String, val httpStatus: Int? = null) : GroqTextFormattingResult
}

internal object GroqTextFormattingClient {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val TEMPERATURE = 0.2
    private const val MAX_COMPLETION_TOKENS = 2048
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun createCall(apiKey: String, model: String, userText: String): Call {
        require(apiKey.isNotBlank())
        require(model.isNotBlank())
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(buildPayload(model, userText).toString().toRequestBody(jsonMediaType))
            .build()
        return GroqHttpClient.client.newCall(request)
    }

    internal fun buildPayload(model: String, userText: String): JSONObject {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", TranscriptFormattingPrompt.SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", userText))
        return JSONObject()
            .put("model", model.trim())
            .put("messages", messages)
            .put("temperature", TEMPERATURE)
            .put("reasoning_effort", "none")
            .put("max_completion_tokens", MAX_COMPLETION_TOKENS)
    }

    fun enqueue(call: Call, onResult: (GroqTextFormattingResult) -> Unit) {
        call.enqueue(object : Callback {
            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun onFailure(call: Call, exception: IOException) {
                onResult(
                    GroqTextFormattingResult.Failure(
                        if (call.isCanceled()) "cancelled" else "network_error"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { parseResponse(it) }
                } catch (exception: Exception) {
                    GroqTextFormattingResult.Failure("response_error")
                }
                onResult(result)
            }
        })
    }

    internal fun parseResponse(response: Response): GroqTextFormattingResult {
        if (!response.isSuccessful) {
            return GroqTextFormattingResult.Failure("http_error", response.code)
        }

        val responseBody = try {
            response.body?.string()
        } catch (exception: IOException) {
            return GroqTextFormattingResult.Failure("response_read_error")
        }
        if (responseBody.isNullOrBlank()) {
            return GroqTextFormattingResult.Failure("empty_response")
        }

        return try {
            val json = JSONObject(responseBody)
            if (json.has("error") && !json.isNull("error")) {
                return GroqTextFormattingResult.Failure("api_error", response.code)
            }
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: return GroqTextFormattingResult.Failure("invalid_response")
            if (choice.optString("finish_reason") == "length") {
                return GroqTextFormattingResult.Failure("incomplete_response")
            }
            val message = choice.optJSONObject("message")
                ?: return GroqTextFormattingResult.Failure("invalid_response")
            val text = message.opt("content") as? String
                ?: return GroqTextFormattingResult.Failure("invalid_content")
            if (text.isBlank()) {
                GroqTextFormattingResult.Failure("empty_text")
            } else {
                GroqTextFormattingResult.Success(text)
            }
        } catch (exception: JSONException) {
            GroqTextFormattingResult.Failure("invalid_json")
        }
    }
}
