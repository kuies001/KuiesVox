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

    @Test
    fun automaticAndMixedModesOmitTheLanguageField() {
        val automatic = serializedRequestBody(null, language = SpeechLanguageMode.AUTO.groqLanguageCode)
        val mixed = serializedRequestBody(null, language = SpeechLanguageMode.MIXED.groqLanguageCode)

        assertFalse(automatic.contains("name=\"language\""))
        assertFalse(mixed.contains("name=\"language\""))
        assertFalse(mixed.contains("zh-en"))
    }

    @Test
    fun chineseAndEnglishModesSendSupportedLanguageCodes() {
        val chinese = serializedRequestBody(null, language = SpeechLanguageMode.CHINESE.groqLanguageCode)
        val english = serializedRequestBody(null, language = SpeechLanguageMode.ENGLISH.groqLanguageCode)

        assertTrue(chinese.contains("name=\"language\""))
        assertTrue(chinese.contains("\r\nzh\r\n"))
        assertTrue(english.contains("name=\"language\""))
        assertTrue(english.contains("\r\nen\r\n"))
    }

    private fun serializedRequestBody(
        prompt: String?,
        model: String = SmartFormattingSettings.DEFAULT_SPEECH_MODEL,
        language: String? = "zh"
    ): String {
        val audioFile = File.createTempFile("voice-ime-test-", ".wav")
        return try {
            audioFile.writeText("test wav data")
            val requestBody = GroqTranscriptionClient.createRequestBody(audioFile, prompt, model, language)
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } finally {
            audioFile.delete()
        }
    }
}
