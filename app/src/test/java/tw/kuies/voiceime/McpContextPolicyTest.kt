package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpContextPolicyTest {
    @Test
    fun urlValidationAllowsOnlyHttpAndHttpsUrlsWithoutEmbeddedCredentials() {
        assertTrue(McpUrlValidator.isValid("https://mcp.example.com/mcp"))
        assertTrue(McpUrlValidator.isValid("http://127.0.0.1:8080/mcp"))
        assertFalse(McpUrlValidator.isValid("file:///tmp/server"))
        assertFalse(McpUrlValidator.isValid("https://@mcp.example.com/mcp"))
        assertFalse(McpUrlValidator.isValid("https:///missing-host"))
    }

    @Test
    fun contextTermsAreTrimmedAndDeduplicatedIgnoringCase() {
        assertEquals(
            listOf("DeepSeek", "OpenCode", "南岡山"),
            McpContextTextParser.parse(listOf(" DeepSeek, OpenCode ", "deepseek\n南岡山"))
        )
    }

    @Test
    fun promptBuilderKeepsLocalTermsBeforeMcpTermsAndDeduplicates() {
        val local = listOf(PersonalGlossaryTerm("local-1", "DeepSeek", true))
        val prompt = requireNotNull(
            GlossaryPromptBuilder.build(local, listOf("deepseek", "OpenCode"))
        )

        assertEquals(1, prompt.split("DeepSeek").size - 1)
        assertTrue(prompt.indexOf("DeepSeek") < prompt.indexOf("OpenCode"))
    }

    @Test
    fun localGlossaryUsesThePromptBudgetBeforeMcpTerms() {
        val local = (0 until GlossaryPromptBuilder.MAX_TERMS).map { index ->
            PersonalGlossaryTerm("local-$index", "L$index", true)
        }

        val prompt = requireNotNull(GlossaryPromptBuilder.build(local, listOf("MCP-ONLY")))

        assertFalse(prompt.contains("MCP-ONLY"))
        assertTrue(prompt.contains("L0"))
    }

    @Test
    fun cacheExpiresAfterFifteenMinutesAndFailureCanUseMostRecentCache() {
        val cached = McpContextCache("server-key", 1_000L, listOf("CachedTerm"))

        assertTrue(McpContextCacheRules.isFresh(cached, "server-key", 1_000L + 899_999L))
        assertFalse(McpContextCacheRules.isFresh(cached, "server-key", 1_000L + 900_000L))
        assertEquals(listOf("CachedTerm"), McpContextCacheRules.fallback(cached, "server-key"))
        assertTrue(McpContextCacheRules.fallback(cached, "other-server").isEmpty())
    }

    @Test
    fun emptyMcpContentProducesNoContext() {
        assertTrue(McpContextTextParser.parse(listOf(" \n, ， ; ; ")).isEmpty())
    }
}
