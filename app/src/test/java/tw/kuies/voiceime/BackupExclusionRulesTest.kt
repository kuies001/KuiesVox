package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 備份與裝置轉移的排除政策：憑證、個人化資料與歷史紀錄預設都不離開裝置。
 *
 * Android 11（API 30）以下由 backup_rules.xml 決定，Android 12（API 31）以上由
 * data_extraction_rules.xml 的 cloud-backup 與 device-transfer 決定，三套規則必須一致。
 */
class BackupExclusionRulesTest {

    private val credentialsAndMcp = listOf(
        "secure_credentials.xml",
        "groq_settings.xml",
        "text_formatting_api_keys.xml",
        "mcp_settings.xml",
        "mcp_config.xml",
        "mcp_context_cache.xml"
    )
    private val personalization = listOf(
        "personal_glossary.xml",
        "text_correction_rules.xml",
        "app_voice_profiles.xml"
    )
    private val historyDatabase = listOf(
        "kuiesvox_history.db",
        "kuiesvox_history.db-wal",
        "kuiesvox_history.db-shm",
        "kuiesvox_history.db-journal"
    )
    private val expectedSharedPreferences = credentialsAndMcp + personalization

    private fun repositoryFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot find $relativePath under ${File(".").absolutePath}")
    }

    private fun excludedEntries(block: String): Set<String> =
        Regex("""<exclude\s+domain="([^"]+)"\s+path="([^"]+)"\s*/>""")
            .findAll(block)
            .map { "${it.groupValues[1]}|${it.groupValues[2]}" }
            .toSet()

    private fun section(xml: String, tag: String): String {
        val start = xml.indexOf("<$tag>")
        val end = xml.indexOf("</$tag>")
        assertTrue("rules must contain <$tag>", start >= 0 && end > start)
        return xml.substring(start, end)
    }

    private fun assertProtectsPersonalData(exclusionSet: Set<String>, ruleName: String) {
        expectedSharedPreferences.forEach { name ->
            assertTrue("$ruleName must exclude sharedpref/$name", exclusionSet.contains("sharedpref|$name"))
        }
        historyDatabase.forEach { name ->
            assertTrue("$ruleName must exclude database/$name", exclusionSet.contains("database|$name"))
        }
        assertTrue("$ruleName must exclude the downloaded update APK", exclusionSet.contains("external|Download"))
    }

    @Test
    fun theLegacyFullBackupRulesExcludePersonalizationAndHistory() {
        val rules = repositoryFile("src/main/res/xml/backup_rules.xml").readText(Charsets.UTF_8)

        assertProtectsPersonalData(excludedEntries(rules), "backup_rules.xml")
    }

    @Test
    fun cloudBackupExcludesPersonalizationAndHistory() {
        val rules = repositoryFile("src/main/res/xml/data_extraction_rules.xml").readText(Charsets.UTF_8)

        assertProtectsPersonalData(excludedEntries(section(rules, "cloud-backup")), "cloud-backup")
    }

    @Test
    fun deviceTransferUsesExactlyTheSameExclusionsAsCloudBackup() {
        val rules = repositoryFile("src/main/res/xml/data_extraction_rules.xml").readText(Charsets.UTF_8)
        val cloud = excludedEntries(section(rules, "cloud-backup"))
        val transfer = excludedEntries(section(rules, "device-transfer"))

        assertProtectsPersonalData(transfer, "device-transfer")
        assertEquals(cloud, transfer)
    }

    @Test
    fun everyExclusionFileMatchesARealStoredLocation() {
        // 規則裡的檔名必須是程式實際使用的儲存位置，避免排除清單寫錯名字而失效。
        val storedSharedPreferences = listOf(
            "secure_credentials.xml",
            "groq_settings.xml",
            "text_formatting_api_keys.xml",
            "mcp_settings.xml",
            "mcp_config.xml",
            "mcp_context_cache.xml",
            "personal_glossary.xml",
            "text_correction_rules.xml",
            "app_voice_profiles.xml"
        )
        val sourceFiles = listOf(
            "src/main/java/tw/kuies/voiceime/SecureCredentialStore.kt",
            "src/main/java/tw/kuies/voiceime/CredentialMigration.kt",
            "src/main/java/tw/kuies/voiceime/McpConfigRepository.kt",
            "src/main/java/tw/kuies/voiceime/McpContextProvider.kt",
            "src/main/java/tw/kuies/voiceime/PersonalGlossaryRepository.kt",
            "src/main/java/tw/kuies/voiceime/TextCorrectionRuleRepository.kt",
            "src/main/java/tw/kuies/voiceime/AppVoiceProfileRepository.kt"
        )
        val sources = sourceFiles.joinToString("\n") { repositoryFile(it).readText(Charsets.UTF_8) }

        storedSharedPreferences.forEach { name ->
            val key = name.removeSuffix(".xml")
            assertTrue("no source declares the prefs file $key", sources.contains("\"$key\""))
        }
    }
}
