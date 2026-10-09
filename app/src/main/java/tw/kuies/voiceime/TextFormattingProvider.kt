package tw.kuies.voiceime

import okhttp3.Call

internal interface TextFormattingProvider {
    fun format(
        apiKey: String,
        model: String,
        transcript: String,
        systemPrompt: String,
        onResult: (TextFormattingResult) -> Unit
    ): Call
}

internal class GroqTextFormattingProvider : TextFormattingProvider {
    override fun format(
        apiKey: String,
        model: String,
        transcript: String,
        systemPrompt: String,
        onResult: (TextFormattingResult) -> Unit
    ): Call {
        val call = GroqTextFormattingClient.createCall(apiKey, model, transcript, systemPrompt)
        GroqTextFormattingClient.enqueue(call, onResult)
        return call
    }
}

internal object TextFormattingProviderRegistry {
    private val groqProvider: TextFormattingProvider = GroqTextFormattingProvider()

    fun forProvider(provider: TextFormattingProviderId): TextFormattingProvider? = when (provider) {
        TextFormattingProviderId.GROQ -> groqProvider
        TextFormattingProviderId.GEMINI, TextFormattingProviderId.OPENAI -> null
    }
}
