package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McpSessionIdPolicyTest {
    @Test
    fun acceptsRealisticSessionIds() {
        assertEquals("abc123", McpSessionIdPolicy.sanitizeOrNull("abc123"))
        assertEquals(
            "3f2b1c4d-9e8a-4b7c-8d1e-2a3b4c5d6e7f",
            McpSessionIdPolicy.sanitizeOrNull("3f2b1c4d-9e8a-4b7c-8d1e-2a3b4c5d6e7f")
        )
        assertEquals("a-b_c.d~e+/=", McpSessionIdPolicy.sanitizeOrNull("a-b_c.d~e+/="))
        assertEquals("abc123", McpSessionIdPolicy.sanitizeOrNull("  abc123  "))
    }

    @Test
    fun rejectsHeaderInjectionAndControlCharacters() {
        listOf(
            "good\r\nX-Injected: 1",
            "good\nX-Injected: 1",
            "good\rbad",
            "good bad",
            "good\tbad",
            "caf\u00e9",
            "id\u0000",
            ""
        ).forEach { value ->
            assertNull("must reject: $value", McpSessionIdPolicy.sanitizeOrNull(value))
        }
        assertNull(McpSessionIdPolicy.sanitizeOrNull(null))
    }

    @Test
    fun rejectsOverlongValuesButKeepsTheBoundary() {
        assertNull(McpSessionIdPolicy.sanitizeOrNull("a".repeat(257)))
        assertEquals("a".repeat(256), McpSessionIdPolicy.sanitizeOrNull("a".repeat(256)))
    }
}
