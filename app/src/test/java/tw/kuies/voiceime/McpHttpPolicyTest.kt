package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpHttpPolicyTest {
    @Test
    fun redirectsAreNeverFollowedSoNoRedirectCodeReplaysTheBearerToken() {
        val client = McpHttpPolicy.client()

        // OkHttp 用這兩個旗標統一處理 301／302／303／307／308；關閉後任何 3xx 都不會被跟隨，
        // 因此 Authorization 標頭不可能被轉送到另一個主機。
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertNull(client.proxy)
    }

    @Test
    fun timeoutsAreBounded() {
        val client = McpHttpPolicy.client()

        assertEquals(McpHttpPolicy.CONNECT_TIMEOUT_SECONDS.toInt() * 1_000, client.connectTimeoutMillis)
        assertEquals(McpHttpPolicy.READ_TIMEOUT_SECONDS.toInt() * 1_000, client.readTimeoutMillis)
        assertEquals(McpHttpPolicy.CALL_TIMEOUT_SECONDS.toInt() * 1_000, client.callTimeoutMillis)
    }

    @Test
    fun remoteServersMustUseHttps() {
        assertNull(McpUrlValidator.parse("http://example.com/mcp"))
        assertNull(McpUrlValidator.parse("http://192.168.1.10/mcp"))
        assertNull(McpUrlValidator.parse("http://[2001:db8::1]/mcp"))

        assertEquals("https", McpUrlValidator.parse("https://example.com/mcp")?.scheme)
        assertEquals("https", McpUrlValidator.parse("https://192.168.1.10/mcp")?.scheme)
    }

    @Test
    fun cleartextIsLimitedToLoopbackLiterals() {
        listOf("http://localhost/mcp", "http://127.0.0.1/mcp", "http://[::1]/mcp").forEach { url ->
            assertTrue("must accept loopback: $url", McpUrlValidator.parse(url) != null)
        }

        assertNull(McpUrlValidator.parse("http://localhost.example.com/mcp"))
        assertNull(McpUrlValidator.parse("http://0.0.0.0/mcp"))
        assertNull(McpUrlValidator.parse("http://2130706433/mcp"))
    }

    @Test
    fun cleartextNeverCarriesABearerToken() {
        assertTrue(McpUrlValidator.isValid("https://example.com/mcp", "token"))
        assertFalse(McpUrlValidator.isValid("http://localhost/mcp", "token"))
        assertFalse(McpUrlValidator.isValid("http://127.0.0.1/mcp", "token"))
        assertTrue(McpUrlValidator.isValid("http://localhost/mcp", ""))
    }

    @Test
    fun userInfoBadPortAndFragmentAreRejected() {
        assertNull(McpUrlValidator.parse("https://user:pass@example.com/mcp"))
        assertNull(McpUrlValidator.parse("https://user@example.com/mcp"))
        assertNull(McpUrlValidator.parse("https://example.com/mcp#fragment"))
        assertNull(McpUrlValidator.parse("https://example.com:99999/mcp"))
        assertNull(McpUrlValidator.parse("https://example.com:0/mcp"))
        assertNull(McpUrlValidator.parse("ftp://example.com/mcp"))
        assertNull(McpUrlValidator.parse("file:///etc/passwd"))
        assertNull(McpUrlValidator.parse(""))
        assertNull(McpUrlValidator.parse("not a url"))

        assertEquals(8443, McpUrlValidator.parse("https://example.com:8443/mcp")?.port)
    }

    @Test
    fun theClientOnlyKnowsReadOnlyRpcMethods() {
        assertEquals(
            setOf(
                "initialize",
                "server/discover",
                "resources/list",
                "resources/read",
                "prompts/list",
                "prompts/get"
            ),
            McpClient.READ_ONLY_METHODS
        )
        assertTrue(
            "MCP must stay read-only: no tool invocation may be added",
            McpClient.READ_ONLY_METHODS.none { it.startsWith("tools") }
        )
    }
}
