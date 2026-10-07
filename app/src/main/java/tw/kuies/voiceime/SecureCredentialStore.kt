package tw.kuies.voiceime

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SecretStorage {
    fun saveSecret(name: String, value: String)
    fun readSecret(name: String): String?
    fun removeSecret(name: String)
}

/** Stores encrypted values in private preferences with the encryption key held by Android Keystore. */
internal class SecureCredentialStore(context: Context) : SecretStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun saveSecret(name: String, value: String) = synchronized(LOCK) {
        require(name.isNotBlank())
        require(value.isNotEmpty())

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv ?: throw IOException("Credential encryption did not produce an IV")
        cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
        val plaintext = value.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val encoded = VERSION_PREFIX + Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
        if (!preferences.edit().putString(name, encoded).commit()) {
            throw IOException("Credential could not be persisted")
        }
    }

    override fun readSecret(name: String): String? = synchronized(LOCK) {
        val encoded = preferences.getString(name, null) ?: return@synchronized null
        if (!encoded.startsWith(VERSION_PREFIX)) {
            throw IOException("Credential has an unsupported storage format")
        }
        val payload = try {
            Base64.decode(encoded.removePrefix(VERSION_PREFIX), Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            throw IOException("Credential data is invalid")
        }
        if (payload.size <= GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES) {
            throw IOException("Credential data is incomplete")
        }

        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(name.toByteArray(StandardCharsets.UTF_8))
        val plaintext = cipher.doFinal(ciphertext)
        try {
            String(plaintext, StandardCharsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun removeSecret(name: String) = synchronized(LOCK) {
        if (!preferences.edit().remove(name).commit()) {
            throw IOException("Credential could not be removed")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    internal companion object {
        const val PREFERENCES_NAME = "secure_credentials"
        const val GROQ_API_KEY = "groq_api_key"
        const val GEMINI_API_KEY = "gemini_api_key"
        const val OPENAI_API_KEY = "openai_api_key"
        const val MCP_BEARER_TOKEN = "mcp_bearer_token"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "tw.kuies.voiceime.credentials.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION_PREFIX = "v1:"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
        private const val KEY_SIZE_BITS = 256
        private val LOCK = Any()
    }
}
