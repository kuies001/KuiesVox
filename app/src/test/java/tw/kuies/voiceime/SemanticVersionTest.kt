package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun comparesPatchAndMajorVersionsNumerically() {
        assertTrue(version("0.9.0") > version("0.8.0"))
        assertTrue(version("0.10.0") > version("0.9.0"))
        assertTrue(version("1.0.0") > version("0.10.0"))
    }

    @Test
    fun acceptsReleaseTagPrefix() {
        assertEquals("0.9.0", SemanticVersion.parse("v0.9.0")?.toString())
    }

    @Test
    fun prereleaseHasLowerPrecedenceThanStableVersion() {
        assertTrue(version("0.9.0") > version("0.9.0-rc.1"))
        assertTrue(version("0.9.0-rc.2") > version("0.9.0-rc.1"))
    }

    @Test
    fun invalidOrOverflowingVersionIsIgnored() {
        assertNull(SemanticVersion.parse("release-next"))
        assertNull(SemanticVersion.parse("999999999999999999999.0.0"))
    }

    private fun version(value: String): SemanticVersion =
        requireNotNull(SemanticVersion.parse(value))
}
