package tw.kuies.voiceime

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 以本機 MockWebServer 測試 MCP 的實際 HTTP 行為：不連線任何真實伺服器，
 * 每個測試結束都關閉伺服器。Redirect 測試一律使用 loopback HTTP 且不帶 token
 * （cleartext 帶 token 本來就會被政策拒絕，見 cleartextWithATokenIsStillRejectedBeforeAnyRequest）。
 */
class McpClientHttpTest {
    private fun jsonResponse(body: String, code: Int = 200): MockResponse =
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun clientFor(server: MockWebServer): McpClient =
        McpClient(McpConfig(serverUrl = server.url("/mcp").toString()))

    /** 任何異常都必須是受控的 McpClientException，不得外洩其他例外型別。 */
    private fun failureOf(block: () -> Unit): McpClientException {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("expected a controlled failure, got: $error", error is McpClientException)
        return error as McpClientException
    }

    @Test
    fun everyRedirectCodeFailsWithoutASecondRequestOrTokenForwarding() {
        listOf(301, 302, 307, 308).forEach { code ->
            val origin = MockWebServer()
            val target = MockWebServer()
            origin.start()
            target.start()
            try {
                origin.enqueue(
                    MockResponse.Builder()
                        .code(code)
                        .addHeader("Location", target.url("/mcp").toString())
                        .body("")
                        .build()
                )

                val error = failureOf { clientFor(origin).connect() }

                assertEquals("redirect $code must fail as an HTTP error", McpFailureKind.HTTP, error.kind)
                assertEquals("exactly one request", 1, origin.requestCount)
                assertEquals("redirect target must never be contacted", 0, target.requestCount)
            } finally {
                origin.close()
                target.close()
            }
        }
    }

    @Test
    fun anOversizedResponseIsStoppedAtTheLimit() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                jsonResponse(
                    """{"jsonrpc":"2.0","id":"voiceime-1","result":{"x":"${"y".repeat(1_100_000)}"}}"""
                )
            )

            val error = failureOf { clientFor(server).connect() }

            assertEquals(McpFailureKind.UNSUPPORTED, error.kind)
        } finally {
            server.close()
        }
    }

    @Test
    fun malformedJsonFailsSafely() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(jsonResponse("""{"jsonrpc":"2.0","id":"""))

            val error = failureOf { clientFor(server).connect() }

            assertTrue(
                "unexpected kind: ${error.kind}",
                error.kind in setOf(McpFailureKind.INVALID_JSON, McpFailureKind.PROTOCOL)
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun aMismatchedJsonRpcIdFailsSafely() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(jsonResponse("""{"jsonrpc":"2.0","id":"not-ours","result":{}}"""))

            val error = failureOf { clientFor(server).connect() }

            assertTrue(
                "unexpected kind: ${error.kind}",
                error.kind in setOf(McpFailureKind.INVALID_JSON, McpFailureKind.PROTOCOL)
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun httpErrorsFailSafelyAndDistinguishAuthentication() {
        listOf(
            500 to McpFailureKind.HTTP,
            401 to McpFailureKind.AUTHENTICATION,
            403 to McpFailureKind.AUTHENTICATION
        ).forEach { (code, expectedKind) ->
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(jsonResponse("""{"error":"nope"}""", code = code))

                val error = failureOf { clientFor(server).connect() }

                assertEquals(expectedKind, error.kind)
                assertEquals(code, error.httpStatus)
            } finally {
                server.close()
            }
        }
    }

    @Test
    fun aClosedPortFailsSafely() {
        val probe = MockWebServer()
        probe.start()
        val url = probe.url("/mcp").toString()
        probe.close()

        val error = failureOf { McpClient(McpConfig(serverUrl = url)).connect() }

        assertEquals(McpFailureKind.NETWORK, error.kind)
    }

    @Test
    fun cleartextWithATokenIsStillRejectedBeforeAnyRequest() {
        val server = MockWebServer()
        server.start()
        try {
            val error = failureOf {
                McpClient(
                    McpConfig(
                        serverUrl = server.url("/mcp").toString(),
                        bearerToken = "secret-token"
                    )
                ).connect()
            }

            assertEquals(McpFailureKind.INVALID_URL, error.kind)
            assertEquals("no request may leave the device", 0, server.requestCount)
        } finally {
            server.close()
        }
    }
}
