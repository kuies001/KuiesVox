package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTransformationCoreTest {
    private val request = TextTransformationRequest(
        transformationType = TextTransformationType.EDIT_SELECTED_TEXT,
        sourceText = "你們到底什麼時候才要處理？已經拖很久了。",
        instruction = "幫我改得客氣一點，但保留催促的意思。",
        provider = TextFormattingProviderId.GROQ,
        model = "fake-model",
        operationId = 42L
    )

    @Test
    fun thePromptKeepsInstructionAndSourceApart() {
        val prompt = TextTransformationCore.promptFor(request)

        assertEquals(TextEditPrompt.SYSTEM_PROMPT, prompt.systemPrompt)
        assertTrue(prompt.userMessage.contains(request.instruction))
        assertTrue(prompt.userMessage.contains(request.sourceText))
        assertTrue(prompt.userMessage.contains("<<<SOURCE_CONTENT"))
    }

    @Test
    fun aControlledRewriteFlowsThroughWithTheSameOperationId() {
        val expected = "想請問目前的處理進度如何？由於已等待一段時間，希望能儘快協助處理，謝謝。"
        val provider = FakeFormattingProvider(expected)
        var result: TextFormattingResult? = null

        val prompt = TextTransformationCore.promptFor(request)
        provider.format("test-key", request.model, prompt.userMessage, prompt.systemPrompt) { result = it }

        val transformed = TextTransformationCore.interpretSuccess(
            request,
            (result as TextFormattingResult.Success).text
        )

        assertEquals(TextTransformationResult.Success(expected, 42L), transformed)
        assertEquals(1, provider.requestCount)
        assertEquals(TextEditPrompt.SYSTEM_PROMPT, provider.lastSystemPrompt)
    }

    @Test
    fun aBlankOrFailedResultNeverBecomesASuccess() {
        assertEquals(
            TextTransformationResult.Failure(TextTransformationFailure.EMPTY_RESULT, 42L),
            TextTransformationCore.interpretSuccess(request, "   ")
        )
        assertEquals(
            TextTransformationResult.Failure(TextTransformationFailure.EMPTY_RESULT, 42L),
            TextTransformationCore.interpretSuccess(request, null)
        )
        assertEquals(
            TextTransformationResult.Failure(TextTransformationFailure.REQUEST_FAILED, 42L),
            TextTransformationCore.interpretFailure(request, "network_error")
        )
    }

    private val translateRequest = TextTransformationRequest(
        transformationType = TextTransformationType.TRANSLATE_SPEECH,
        sourceText = "你明天有空嗎？",
        instruction = "",
        provider = TextFormattingProviderId.GROQ,
        model = "fake-model",
        operationId = 7L,
        targetLanguage = "English"
    )

    @Test
    fun translationUsesTheTranslatePromptNotTheEditPrompt() {
        val prompt = TextTransformationCore.promptFor(translateRequest)

        assertEquals(TextTranslatePrompt.SYSTEM_PROMPT, prompt.systemPrompt)
        assertTrue(prompt.userMessage.contains("TARGET LANGUAGE:"))
        assertTrue(prompt.userMessage.contains("English"))
        assertTrue(prompt.userMessage.contains("你明天有空嗎？"))
    }

    @Test
    fun aTranslationMakesExactlyOneProviderCallAndCarriesTheTranslationThrough() {
        val expected = "Are you free tomorrow?"
        val provider = FakeFormattingProvider(expected)
        var result: TextFormattingResult? = null

        val prompt = TextTransformationCore.promptFor(translateRequest)
        provider.format("test-key", translateRequest.model, prompt.userMessage, prompt.systemPrompt) { result = it }

        val transformed = TextTransformationCore.interpretSuccess(
            translateRequest,
            (result as TextFormattingResult.Success).text
        )

        assertEquals(TextTransformationResult.Success(expected, 7L), transformed)
        assertEquals(1, provider.requestCount)
        assertEquals(TextTranslatePrompt.SYSTEM_PROMPT, provider.lastSystemPrompt)
    }

    @Test
    fun aMixedCjkAndTechnologySentenceIsCarriedThroughUnchangedByThePipeline() {
        val expected = "I will update the GitHub README today, then test the API."
        val provider = FakeFormattingProvider(expected)
        val mixedRequest = translateRequest.copy(
            sourceText = "我今天要更新 GitHub README，然後測試 API。"
        )
        var result: TextFormattingResult? = null

        val prompt = TextTransformationCore.promptFor(mixedRequest)
        provider.format("test-key", mixedRequest.model, prompt.userMessage, prompt.systemPrompt) { result = it }
        val transformed = TextTransformationCore.interpretSuccess(
            mixedRequest,
            (result as TextFormattingResult.Success).text
        )

        assertEquals(TextTransformationResult.Success(expected, 7L), transformed)
        assertTrue(prompt.userMessage.contains("GitHub README"))
    }

    @Test
    fun aBlankTranslationIsAFailureNotTheSourceTranscript() {
        assertEquals(
            TextTransformationResult.Failure(TextTransformationFailure.EMPTY_RESULT, 7L),
            TextTransformationCore.interpretSuccess(translateRequest, "  ")
        )
    }
}
