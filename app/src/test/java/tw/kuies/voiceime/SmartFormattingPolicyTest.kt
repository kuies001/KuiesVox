package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFormattingPolicyTest {
    @Test
    fun textBelowThresholdSkipsFormatter() {
        assertEquals(
            SmartFormattingDecision.CommitOriginal("短文字"),
            SmartFormattingPolicy.decide("短文字", settings(threshold = 4))
        )
    }

    @Test
    fun textEqualToThresholdSkipsFormatter() {
        assertEquals(
            SmartFormattingDecision.CommitOriginal("等等下班去健工練背"),
            SmartFormattingPolicy.decide("等等下班去健工練背", settings(threshold = 9))
        )
    }

    @Test
    fun shortVoiceimeExampleSkipsFormatterAtFortyCharacters() {
        val text = "等等下班去健工練背"

        assertEquals(
            SmartFormattingDecision.CommitOriginal(text),
            SmartFormattingPolicy.decide(text, settings(threshold = 40))
        )
    }

    @Test
    fun textAboveThresholdUsesFormatter() {
        assertEquals(
            SmartFormattingDecision.Format("長逐字稿", "qwen/qwen3.8-27b"),
            SmartFormattingPolicy.decide("長逐字稿", settings(threshold = 3))
        )
    }

    @Test
    fun longTranscriptExampleUsesFormatterAtFortyCharacters() {
        val text = "我今天下班之後想先去健身工廠練背然後可能再打一下拳擊然後如果時間還夠的話我想去家樂福買一些東西主要是蛋白質還有明天早餐需要的東西"

        assertTrue(
            SmartFormattingPolicy.decide(text, settings(threshold = 40)) is SmartFormattingDecision.Format
        )
    }

    @Test
    fun zeroThresholdFormatsEveryNonEmptyText() {
        assertTrue(
            SmartFormattingPolicy.decide("文字", settings(threshold = 0)) is SmartFormattingDecision.Format
        )
        assertTrue(
            SmartFormattingPolicy.decide("   ", settings(threshold = 0)) is
                SmartFormattingDecision.CommitOriginal
        )
    }

    @Test
    fun disabledFeatureSkipsFormatterRegardlessOfLength() {
        assertEquals(
            SmartFormattingDecision.CommitOriginal("很長的逐字稿"),
            SmartFormattingPolicy.decide("很長的逐字稿", settings(enabled = false, threshold = 0))
        )
    }

    @Test
    fun successfulFormattingUsesFormattedText() {
        val resolution = SmartFormattingPolicy.resolve("原始文字", "整理後文字")

        assertEquals("整理後文字", resolution.text)
        assertFalse(resolution.usedFallback)
    }

    @Test
    fun failedFormattingFallsBackToPostProcessedText() {
        val resolution = SmartFormattingPolicy.resolve("本地修正結果", null)

        assertEquals("本地修正結果", resolution.text)
        assertTrue(resolution.usedFallback)
    }

    @Test
    fun blankFormattingFallsBackToPostProcessedText() {
        val resolution = SmartFormattingPolicy.resolve("原始修正結果", " \n\t")

        assertEquals("原始修正結果", resolution.text)
        assertTrue(resolution.usedFallback)
    }

    @Test
    fun formattingCancellationPreventsCommitWhenCallbackArrivesLater() {
        val machine = VoiceImeStateMachine()
        val gate = VoiceImeRequestGate()
        machine.transitionTo(VoiceImeState.RECORDING)
        machine.transitionTo(VoiceImeState.TRANSCRIBING)
        machine.transitionTo(VoiceImeState.FORMATTING)
        val requestId = gate.begin()
        var commitCount = 0

        gate.invalidate()
        machine.transitionTo(VoiceImeState.CANCELLED)
        val committed = gate.runIfCurrent(requestId) { commitCount += 1 }

        assertFalse(committed)
        assertEquals(0, commitCount)
        assertEquals(VoiceImeState.CANCELLED, machine.state)
    }

    @Test
    fun formattedParagraphsAndNewlinesArePreserved() {
        val formatted = "第一段內容\n\n1. 第一點\n2. 第二點\n3. 第三點"

        assertEquals(formatted, SmartFormattingPolicy.resolve("原文", formatted).text)
    }

    @Test
    fun codePointLengthCountsSupplementaryCharactersAsOne() {
        val text = "  😀😀  "

        assertEquals(
            SmartFormattingDecision.CommitOriginal(text),
            SmartFormattingPolicy.decide(text, settings(threshold = 2))
        )
    }

    @Test
    fun providersHaveIndependentRequestedDefaults() {
        val settings = SmartFormattingSettings()

        assertEquals("whisper-large-v3-turbo", settings.speechModel)
        assertEquals("qwen/qwen3.8-27b", settings.modelFor(TextFormattingProviderId.GROQ))
        assertEquals("gemini-3.5-flash-lite", settings.modelFor(TextFormattingProviderId.GEMINI))
        assertEquals("gpt-5.6-luna", settings.modelFor(TextFormattingProviderId.OPENAI))
    }

    @Test
    fun changingProviderPreservesEveryProvidersModelAndCustomId() {
        val configured = SmartFormattingSettings(
            provider = TextFormattingProviderId.GROQ,
            groqFormattingModel = FormattingModels.CUSTOM_MODEL_ID,
            groqCustomModelId = "groq/new-model",
            geminiFormattingModel = FormattingModels.CUSTOM_MODEL_ID,
            geminiCustomModelId = "gemini/new-model",
            openAiFormattingModel = FormattingModels.CUSTOM_MODEL_ID,
            openAiCustomModelId = "openai/new-model"
        )

        val switched = configured.copy(provider = TextFormattingProviderId.GEMINI)

        assertEquals("groq/new-model", switched.modelFor(TextFormattingProviderId.GROQ))
        assertEquals("gemini/new-model", switched.modelFor(TextFormattingProviderId.GEMINI))
        assertEquals("openai/new-model", switched.modelFor(TextFormattingProviderId.OPENAI))
        assertEquals(TextFormattingProviderId.GEMINI, switched.provider)
    }

    @Test
    fun externalProvidersAreRepresentedInFormattingDecisionForLocalFallbackRouting() {
        val decision = SmartFormattingPolicy.decide(
            "一段超過門檻、等待處理的逐字稿內容",
            SmartFormattingSettings(
                threshold = 0,
                provider = TextFormattingProviderId.OPENAI
            )
        )

        assertEquals(
            SmartFormattingDecision.Format(
                "一段超過門檻、等待處理的逐字稿內容",
                "gpt-5.6-luna",
                TextFormattingProviderId.OPENAI
            ),
            decision
        )
    }

    @Test
    fun invalidSpeechModelNormalizesToDefault() {
        val normalized = SmartFormattingSettingsRepository.normalize(
            SmartFormattingSettings(speechModel = "invalid-model")
        )

        assertEquals(SmartFormattingSettings.DEFAULT_SPEECH_MODEL, normalized.speechModel)
    }

    @Test
    fun legacyFreeFormGroqModelIsMigratedToCustomModelId() {
        val normalized = SmartFormattingSettingsRepository.normalize(
            SmartFormattingSettings(groqFormattingModel = "groq/legacy-custom-model")
        )

        assertEquals(FormattingModels.CUSTOM_MODEL_ID, normalized.groqFormattingModel)
        assertEquals("groq/legacy-custom-model", normalized.groqCustomModelId)
    }

    @Test
    fun onlyGroqHasAnIntegratedFormattingProvider() {
        assertTrue(TextFormattingProviderRegistry.forProvider(TextFormattingProviderId.GROQ) is GroqTextFormattingProvider)
        assertNull(TextFormattingProviderRegistry.forProvider(TextFormattingProviderId.GEMINI))
        assertNull(TextFormattingProviderRegistry.forProvider(TextFormattingProviderId.OPENAI))
    }

    private fun settings(
        enabled: Boolean = true,
        threshold: Int,
        model: String = SmartFormattingSettings.DEFAULT_GROQ_MODEL
    ) = SmartFormattingSettings(enabled = enabled, threshold = threshold, groqFormattingModel = model)
}
