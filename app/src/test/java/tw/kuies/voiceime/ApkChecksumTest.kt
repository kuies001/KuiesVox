package tw.kuies.voiceime

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkChecksumTest {
    private val apkName = "KuiesVox-v0.14.0.apk"
    private val checksumName = "KuiesVox-v0.14.0.sha256"
    private val digest = "b029695120d9b0f42f8fdedb4c0850cb691d9516a0ec1c195bbee21020281545"

    /** 與 v0.14.0 Release 實際發布的檔案內容一致。 */
    private val publishedChecksumText = "$digest  $apkName\n"

    private fun officialUrl(name: String) =
        "https://github.com/kuies001/KuiesVox/releases/download/v0.14.0/$name"

    // --- 與實際 Release 資產的整合 ---

    @Test
    fun theRealReleaseAssetPairIsAcceptedEndToEnd() {
        val assets = listOf(
            GitHubReleaseAsset(apkName, officialUrl(apkName), 2_392_972L),
            GitHubReleaseAsset(checksumName, officialUrl(checksumName), 87L)
        )

        val selected = UpdateRepository.selectChecksumAsset(assets, apkName)

        assertEquals(checksumName, selected?.name)
        val checksum = ApkChecksumPolicy.parse(publishedChecksumText)!!
        assertEquals(apkName, checksum.fileName)
        assertTrue(checksum.matchesFileName(apkName))
        assertEquals(digest, checksum.sha256)
    }

    @Test
    fun theDownloadedFileNameMatchesTheVersionInTheChecksum() {
        val version = SemanticVersion.parse("v0.14.0")!!

        val downloadedName = AppUpdateManager.apkFileNameFor(version)

        assertEquals(apkName, downloadedName)
        assertTrue(ApkChecksumPolicy.parse(publishedChecksumText)!!.matchesFileName(downloadedName))
    }

    @Test
    fun theCheckedInBadCompanionNameIsNotAccepted() {
        // 回歸測試：<apk>.sha256 不是 release.yml 發布的命名，必須找不到。
        assertNull(
            UpdateRepository.selectChecksumAsset(
                listOf(GitHubReleaseAsset("$apkName.sha256", officialUrl("$apkName.sha256"), 87L)),
                apkName
            )
        )
        assertEquals(checksumName, UpdateRepository.checksumAssetNameFor(apkName))
        assertNull(UpdateRepository.checksumAssetNameFor("KuiesVox-v0.14.0.txt"))
        assertNull(UpdateRepository.checksumAssetNameFor(""))
    }

    @Test
    fun onlyTheChecksumAssetForTheSameVersionOnTheOfficialHostIsAccepted() {
        val wrongVersion = GitHubReleaseAsset(
            "KuiesVox-v0.13.0.sha256",
            "https://github.com/kuies001/KuiesVox/releases/download/v0.13.0/KuiesVox-v0.13.0.sha256",
            87L
        )
        val foreignHost = GitHubReleaseAsset(checksumName, "https://example.com/$checksumName", 87L)
        val matching = GitHubReleaseAsset(checksumName, officialUrl(checksumName), 87L)

        assertEquals(
            matching,
            UpdateRepository.selectChecksumAsset(listOf(wrongVersion, foreignHost, matching), apkName)
        )
        assertNull(UpdateRepository.selectChecksumAsset(listOf(wrongVersion, foreignHost), apkName))
        assertNull(UpdateRepository.selectChecksumAsset(emptyList(), apkName))
    }

    // --- checksum 格式 ---

    @Test
    fun parsesThePublishedSha256SumFormat() {
        val checksum = ApkChecksumPolicy.parse(publishedChecksumText)

        assertEquals(digest, checksum?.sha256)
        assertEquals(apkName, checksum?.fileName)
    }

    @Test
    fun acceptsBinaryMarkerAndUppercaseDigest() {
        val checksum = ApkChecksumPolicy.parse("${digest.uppercase()} *$apkName")

        assertEquals(digest, checksum?.sha256)
        assertTrue(checksum!!.matchesDigest(digest.uppercase()))
        assertTrue(checksum.matchesFileName(apkName))
        assertFalse(checksum.matchesFileName("KuiesVox-v0.13.0.apk"))
    }

    @Test
    fun rejectsAnythingThatIsNotAStrictSingleLineChecksum() {
        listOf(
            "",
            "   ",
            digest,
            "$digest $apkName\nextra",
            "${digest.dropLast(1)}  $apkName",
            "$digest  ",
            "zz$digest  $apkName",
            "$digest ../$apkName",
            "$digest sub/$apkName",
            "$digest " + "$apkName\n".repeat(4)
        ).forEach { text ->
            assertNull("must reject: $text", ApkChecksumPolicy.parse(text))
        }
    }

    @Test
    fun hashesStreamsWithSha256() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkChecksumPolicy.sha256Of(ByteArrayInputStream("abc".toByteArray()))
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ApkChecksumPolicy.sha256Of(ByteArrayInputStream(ByteArray(0)))
        )
    }

    @Test
    fun aTamperedDownloadNoLongerMatchesTheDigest() {
        val checksum = ApkChecksumPolicy.parse(publishedChecksumText)!!
        val actual = ApkChecksumPolicy.sha256Of(ByteArrayInputStream("tampered".toByteArray()))

        assertFalse(checksum.matchesDigest(actual))
    }
}
