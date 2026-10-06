package tw.kuies.voiceime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpJsonRpcParserTest {
    @Test
    fun parsesMcpJsonRpcResponseAndServerCapabilities() {
        val result = McpJsonRpcParser.parseResponse(
            """{"jsonrpc":"2.0","id":"request-1","result":{"capabilities":{"resources":{}},"supportedVersions":["2026-07-28"]}}""",
            "request-1"
        )

        assertTrue(result.getJSONObject("capabilities").has("resources"))
        assertEquals("2026-07-28", result.getJSONArray("supportedVersions").getString(0))
    }

    @Test
    fun parsesOnlyTextFromResourceContents() {
        val result = JSONObject(
            """{"contents":[{"uri":"glossary://terms","text":"DeepSeek\nOpenCode"},{"uri":"image://logo","blob":"AA=="}]}"""
        )

        assertEquals(
            listOf("DeepSeek\nOpenCode"),
            McpJsonRpcParser.parseResourceTextContents(result)
        )
    }

    @Test
    fun parsesPromptTextAndEmbeddedTextResources() {
        val result = JSONObject(
            """{"messages":[{"role":"user","content":{"type":"text","text":"DeepSeek"}},{"role":"assistant","content":{"type":"resource","resource":{"uri":"glossary://x","text":"OpenCode"}}}]}"""
        )

        assertEquals(
            listOf("DeepSeek", "OpenCode"),
            McpJsonRpcParser.parsePromptTextContents(result)
        )
    }
}
