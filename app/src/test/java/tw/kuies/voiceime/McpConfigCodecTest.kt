package tw.kuies.voiceime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigCodecTest {
    @Test
    fun persistentConfigJsonDoesNotContainBearerToken() {
        val config = McpConfig(
            enabled = true,
            serverUrl = "https://mcp.example.com/mcp",
            bearerToken = "test-bearer-token",
            selectedResourceUris = setOf("file:///terms"),
            serverName = "Test MCP"
        )

        val encoded = McpConfigRepository.encodeConfig(config)
        val json = JSONObject(encoded)
        val decoded = McpConfigRepository.decodeConfig(encoded)

        assertFalse(json.has("bearerToken"))
        assertTrue(json.optBoolean("enabled"))
        assertEquals("https://mcp.example.com/mcp", json.optString("serverUrl"))
        assertEquals(setOf("file:///terms"), decoded.selectedResourceUris)
        assertEquals("", decoded.bearerToken)
    }
}
