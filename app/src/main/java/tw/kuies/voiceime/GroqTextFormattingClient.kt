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

internal sealed interface TextFormattingResult {
    data class Success(val text: String) : TextFormattingResult
    data class Failure(val type: String, val httpStatus: Int? = null) : TextFormattingResult
}

internal object GroqTextFormattingClient {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val TEMPERATURE = 0.2
    private const val MAX_COMPLETION_TOKENS = 2048
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun createCall(
        apiKey: String,
        model: String,
        userText: String,
        systemPrompt: String = TranscriptFormattingPrompt.SYSTEM_PROMPT
    ): Call {
        require(apiKey.isNotBlank())
        require(model.isNotBlank())
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(buildPayload(model, userText, systemPrompt).toString().toRequestBody(jsonMediaType))
            .build()
        return GroqHttpClient.client.newCall(request)
    }

    internal fun buildPayload(
        model: String,
        userText: String,
        systemPrompt: String = TranscriptFormattingPrompt.SYSTEM_PROMPT
    ): JSONObject {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userText))
        return JSONObject()
            .put("model", model.trim())
            .put("messages", messages)
            .put("temperature", TEMPERATURE)
            .put("reasoning_effort", "none")
            .put("max_completion_tokens", MAX_COMPLETION_TOKENS)
    }

    fun enqueue(call: Call, onResult: (TextFormattingResult) -> Unit) {
        call.enqueue(object : Callback {
            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun onFailure(call: Call, exception: IOException) {
                onResult(
                    TextFormattingResult.Failure(
                        if (call.isCanceled()) "cancelled" else "network_error"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { parseResponse(it) }
                } catch (exception: Exception) {
                    TextFormattingResult.Failure("response_error")
                }
                onResult(result)
            }
        })
    }

    internal fun parseResponse(response: Response): TextFormattingResult {
        if (!response.isSuccessful) {
            return TextFormattingResult.Failure(httpFailureType(response), response.code)
        }

        val responseBody = try {
            response.body?.string()
        } catch (exception: IOException) {
            return TextFormattingResult.Failure("response_read_error")
        }
        if (responseBody.isNullOrBlank()) {
            return TextFormattingResult.Failure("empty_response")
        }

        return try {
            val json = JSONObject(responseBody)
            if (json.has("error") && !json.isNull("error")) {
                val errorType = modelUnavailableType(json.opt("error")) ?: "api_error"
                return TextFormattingResult.Failure(errorType, response.code)
            }
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: return TextFormattingResult.Failure("invalid_response")
            if (choice.optString("finish_reason") == "length") {
                return TextFormattingResult.Failure("incomplete_response")
            }
            val message = choice.optJSONObject("message")
                ?: return TextFormattingResult.Failure("invalid_response")
            val text = message.opt("content") as? String
                ?: return TextFormattingResult.Failure("invalid_content")
            if (text.isBlank()) {
                TextFormattingResult.Failure("empty_text")
            } else {
                TextFormattingResult.Success(text)
            }
        } catch (exception: JSONException) {
            TextFormattingResult.Failure("invalid_json")
        }
    }

    private fun httpFailureType(response: Response): String {
        val errorBody = try {
            response.body?.string().orEmpty()
        } catch (_: IOException) {
            return "http_error"
        }
        if (errorBody.isBlank()) return "http_error"
        val errorValue = try {
            val error = JSONObject(errorBody).opt("error")
            error ?: errorBody
        } catch (_: JSONException) {
            errorBody
        }
        return modelUnavailableType(errorValue) ?: "http_error"
    }

    private fun modelUnavailableType(errorValue: Any?): String? {
        val description = errorValue?.toString()?.lowercase().orEmpty()
        val markers = listOf(
            "model unavailable",
            "model not found",
            "deprecated model",
            "model_unavailable",
            "model_not_found",
            "model_deprecated"
        )
        return "model_unavailable".takeIf { markers.any(description::contains) }
    }
}
