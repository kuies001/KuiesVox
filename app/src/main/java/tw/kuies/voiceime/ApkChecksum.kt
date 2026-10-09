package tw.kuies.voiceime

import java.io.InputStream
import java.security.MessageDigest

/**
 * GitHub Release 發布的 `sha256sum` 檢查碼。
 *
 * 用途僅限於確認「下載到的檔案」與「發布的檔案」一致（完整性與中斷偵測）。
 * 它不取代 Android 安裝器的 APK 簽章驗證，也不是另一套信任根：
 * 真正的信任來源仍是簽章，這裡只是多一道防線。
 */
internal data class ApkChecksum(
    val sha256: String,
    val fileName: String
) {
    fun matchesFileName(apkFileName: String): Boolean = fileName.equals(apkFileName, ignoreCase = true)

    fun matchesDigest(digest: String): Boolean = sha256.equals(digest, ignoreCase = true)
}

internal object ApkChecksumPolicy {
    private const val MAX_CHECKSUM_CHARACTERS = 512
    private val DIGEST_PATTERN = Regex("[0-9a-fA-F]{64}")
    private val SEPARATOR_PATTERN = Regex("\\s+")

    /**
     * 只接受 sha256sum 的單行格式：`<64 位十六進位> <檔名>`（可含二進位標記 `*`）。
     * 其他形狀（多行、缺檔名、非十六進位、長度不符、含路徑）一律視為無效，不得用於驗證。
     */
    fun parse(text: String): ApkChecksum? {
        if (text.isBlank() || text.length > MAX_CHECKSUM_CHARACTERS) return null
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size != 1) return null
        val parts = SEPARATOR_PATTERN.split(lines.single().trim(), limit = 2)
        if (parts.size != 2) return null
        val digest = parts[0]
        val fileName = parts[1].trim().removePrefix("*").trim()
        if (!DIGEST_PATTERN.matches(digest)) return null
        if (fileName.isEmpty() || fileName.contains('/') || fileName.contains('\\')) return null
        return ApkChecksum(sha256 = digest.lowercase(), fileName = fileName)
    }

    /** 以串流計算 SHA-256；呼叫端負責關閉 stream。 */
    fun sha256Of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
