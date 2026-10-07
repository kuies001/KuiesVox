package tw.kuies.voiceime

import java.io.File
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqTranscriptionClientTest {
    @Test
    fun promptIsIncludedInMultipartRequest() {
        val prompt = "專有名詞拼寫參考：DeepSeek, 南岡山"
        val requestBody = serializedRequestBody(prompt)

        assertTrue(requestBody.contains("name=\"prompt\""))
        assertTrue(requestBody.contains(prompt))
    }

    @Test
    fun emptyPromptIsOmittedFromMultipartRequest() {
        val requestBody = serializedRequestBody(null)

        assertFalse(requestBody.contains("name=\"prompt\""))
    }

    @Test
    fun speechModelCanBeSelectedInMultipartRequest() {
        val large = serializedRequestBody(null, "whisper-large-v3")
        val turbo = serializedRequestBody(null, "whisper-large-v3-turbo")

        assertTrue(large.contains("whisper-large-v3"))
        assertFalse(large.contains("whisper-large-v3-turbo"))
        assertTrue(turbo.contains("whisper-large-v3-turbo"))
    }

    private fun serializedRequestBody(
        prompt: String?,
        model: String = SmartFormattingSettings.DEFAULT_SPEECH_MODEL
    ): String {
        val audioFile = File.createTempFile("voice-ime-test-", ".wav")
        return try {
            audioFile.writeText("test wav data")
            val requestBody = GroqTranscriptionClient.createRequestBody(audioFile, prompt, model)
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } finally {
            audioFile.delete()
        }
    }
}
