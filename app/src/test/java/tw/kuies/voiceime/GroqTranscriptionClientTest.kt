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

    private fun serializedRequestBody(prompt: String?): String {
        val audioFile = File.createTempFile("voice-ime-test-", ".wav")
        return try {
            audioFile.writeText("test wav data")
            val requestBody = GroqTranscriptionClient.createRequestBody(audioFile, prompt)
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } finally {
            audioFile.delete()
        }
    }
}
