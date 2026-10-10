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
}
