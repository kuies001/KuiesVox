package tw.kuies.voiceime

import java.lang.reflect.Proxy
import okhttp3.Call
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualCorrectionTest {
    @Test
    fun controlledFormatterResponsesCoverContextAndPreserveTerms() {
        val cases = listOf(
            CorrectionCase(
                input = "你給我助手",
                glossary = listOf(term("住手", "你給我住手", "快住手")),
                response = "你給我住手",
                expected = "你給我住手"
            ),
            CorrectionCase(
                input = "我正在開發 AI 助手",
                glossary = listOf(term("住手", "你給我住手", "快住手")),
                response = "我正在開發 AI 助手",
                expected = "我正在開發 AI 助手"
            ),
            CorrectionCase(
                input = "快助手不要動",
                glossary = listOf(term("住手", "快住手", "住手不要動")),
                response = "快住手不要動",
                expected = "快住手不要動"
            ),
            CorrectionCase(
                input = "幫我 commit 到 GitHub",
                glossary = emptyList(),
                response = "幫我 commit 到 GitHub",
                expected = "幫我 commit 到 GitHub"
            ),
            CorrectionCase(
                input = "OpenCode Go",
                glossary = listOf(term("OpenCode Go")),
                response = "OpenCode Go",
                expected = "OpenCode Go"
            ),
            CorrectionCase("123456", emptyList(), "123456", "123456"),
            CorrectionCase(
                "https://example.com",
                emptyList(),
                "https://example.com",
                "https://example.com"
            )
        )

        cases.forEach { case ->
            val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
                contextualCorrectionEnabled = true,
                glossary = case.glossary
            )
            val provider = FakeFormattingProvider(case.response)
            var result: TextFormattingResult? = null
            provider.format("test-key", "fake-model", case.input, prompt) { result = it }

            assertEquals(case.expected, SmartFormattingPolicy.resolve(case.input, (result as TextFormattingResult.Success).text).text)
            assertEquals(1, provider.requestCount)
            assertTrue(prompt.contains("上下文智慧糾錯"))
            case.glossary.forEach { entry -> assertTrue(prompt.contains(entry.term)) }
        }
    }

    @Test
    fun disabledContextUsesLegacyPromptAndDoesNotIncludeGlossary() {
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = false,
            glossary = listOf(term("住手", "你給我住手"))
        )

        assertEquals(TranscriptFormattingPrompt.SYSTEM_PROMPT, prompt)
        assertFalse(prompt.contains("住手"))
    }

    @Test
    fun explicitRuleSurvivesAControlledFormatterReversionInOneRequest() {
        val rule = TextCorrectionRule("rule-1", "助手", "住手", true)
        val prepared = TextPostProcessor.processWithPlan("你給我助手", listOf(rule))
        val prompt = TranscriptFormattingPrompt.buildSystemPrompt(
            contextualCorrectionEnabled = true,
            glossary = listOf(term("住手", "你給我住手")),
            correctionRules = listOf(rule)
        )
        val provider = FakeFormattingProvider("你給我助手")
        var response: TextFormattingResult? = null
        provider.format("test-key", "fake-model", prepared.text, prompt) { response = it }
        val formatted = (response as TextFormattingResult.Success).text
        val protected = TextPostProcessor.enforceCorrectionPlan(formatted, listOf(rule), prepared.correctionPlan)

        assertTrue(prompt.contains("最高優先權"))
        assertEquals("你給我住手", protected)
        assertEquals(1, provider.requestCount)
    }

    @Test
    fun disabledSmartFormattingDoesNotProduceAFormattingDecision() {
        val decision = SmartFormattingPolicy.decide(
            "一段足夠長的逐字稿內容",
            SmartFormattingSettings(enabled = false, threshold = 0)
        )
        val provider = FakeFormattingProvider("整理後文字")

        if (decision is SmartFormattingDecision.Format) {
            provider.format("test-key", decision.model, decision.text, TranscriptFormattingPrompt.SYSTEM_PROMPT) {}
        }

        assertTrue(decision is SmartFormattingDecision.CommitOriginal)
        assertEquals(0, provider.requestCount)
    }

    private fun term(term: String, vararg phrases: String) = PersonalGlossaryTerm(
        id = "term-$term",
        term = term,
        enabled = true,
        commonPhrases = phrases.toList()
    )

    private data class CorrectionCase(
        val input: String,
        val glossary: List<PersonalGlossaryTerm>,
        val response: String,
        val expected: String
    )

    private inner class FakeFormattingProvider(private val response: String) : TextFormattingProvider {
        var requestCount = 0
            private set

        override fun format(
            apiKey: String,
            model: String,
            transcript: String,
            systemPrompt: String,
            onResult: (TextFormattingResult) -> Unit
        ): Call {
            requestCount++
            onResult(TextFormattingResult.Success(response))
            return fakeCall()
        }
    }

    private fun fakeCall(): Call {
        var cancelled = false
        return Proxy.newProxyInstance(
            Call::class.java.classLoader,
            arrayOf(Call::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "cancel" -> { cancelled = true; null }
                "isCanceled" -> cancelled
                "isExecuted" -> false
                "request" -> Request.Builder().url("https://example.invalid").build()
                "timeout" -> null
                "clone" -> proxy
                "toString" -> "FakeCall"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        } as Call
    }
}
