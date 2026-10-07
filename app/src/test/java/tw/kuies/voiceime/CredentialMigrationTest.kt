package tw.kuies.voiceime

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class CredentialMigrationTest {
    @Test
    fun migratesLegacySecretAndRemovesItOnlyAfterReadback() {
        val storage = InMemorySecretStorage()
        var legacy: String? = "  legacy-secret  "

        CredentialMigrationLogic.migrateSecret(
            storage = storage,
            secretName = "api_key",
            readLegacy = { legacy },
            removeLegacy = { legacy = null; true }
        )

        assertEquals("legacy-secret", storage.readSecret("api_key"))
        assertNull(legacy)
    }

    @Test
    fun failedSecretWritePreservesLegacyValue() {
        val storage = InMemorySecretStorage().apply { failWrites = true }
        var legacy: String? = "legacy-secret"

        try {
            CredentialMigrationLogic.migrateSecret(
                storage = storage,
                secretName = "api_key",
                readLegacy = { legacy },
                removeLegacy = { legacy = null; true }
            )
            fail("Expected secret migration to fail")
        } catch (_: IOException) {
            // The old value must remain available when secure persistence fails.
        }

        assertEquals("legacy-secret", legacy)
        assertNull(storage.readSecret("api_key"))
    }

    @Test
    fun failedLegacyRemovalDoesNotMarkMigrationComplete() {
        val storage = InMemorySecretStorage()
        val legacy = "legacy-secret"

        try {
            CredentialMigrationLogic.migrateOnce(storage, "migration", "complete") {
                CredentialMigrationLogic.migrateSecret(
                    storage = storage,
                    secretName = "api_key",
                    readLegacy = { legacy },
                    removeLegacy = { false }
                )
            }
            fail("Expected legacy removal to fail")
        } catch (_: IOException) {
            // The migration marker is written only after all old values are removed.
        }

        assertEquals("legacy-secret", legacy)
        assertNull(storage.readSecret("migration"))
    }

    @Test
    fun successfulOneTimeMigrationIsNotRepeated() {
        val storage = InMemorySecretStorage()
        var runs = 0

        repeat(2) {
            CredentialMigrationLogic.migrateOnce(storage, "migration", "complete") {
                runs++
            }
        }

        assertEquals(1, runs)
        assertEquals("complete", storage.readSecret("migration"))
    }

    private class InMemorySecretStorage : SecretStorage {
        private val values = mutableMapOf<String, String>()
        var failWrites = false

        override fun saveSecret(name: String, value: String) {
            if (failWrites) throw IOException("test failure")
            values[name] = value
        }

        override fun readSecret(name: String): String? = values[name]

        override fun removeSecret(name: String) {
            values.remove(name)
        }
    }
}
