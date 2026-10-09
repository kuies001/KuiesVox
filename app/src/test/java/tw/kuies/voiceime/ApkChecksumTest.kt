package tw.kuies.voiceime

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkChecksumTest {
    private val apkName = "KuiesVox-v0.14.0.apk"
    private val digest = "b029695120d9b0f42f8fdedb4c0850cb691d9516a0ec1c195bbee21020281545"

    @Test
    fun parsesThePublishedSha256SumFormat() {
        val checksum = ApkChecksumPolicy.parse("$digest  $apkName\n")

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
        val checksum = ApkChecksumPolicy.parse("$digest  $apkName")!!
        val actual = ApkChecksumPolicy.sha256Of(ByteArrayInputStream("tampered".toByteArray()))

        assertFalse(checksum.matchesDigest(actual))
    }

    @Test
    fun onlyTheChecksumAssetForTheSameApkOnTheOfficialHostIsAccepted() {
        val apk = GitHubReleaseAsset(
            apkName,
            "https://github.com/kuies001/KuiesVox/releases/download/v0.14.0/$apkName",
            2_392_972L
        )
        val matching = GitHubReleaseAsset(
            "$apkName.sha256",
            "https://github.com/kuies001/KuiesVox/releases/download/v0.14.0/$apkName.sha256",
            87L
        )
        val wrongName = GitHubReleaseAsset(
            "KuiesVox-v0.13.0.apk.sha256",
            "https://github.com/kuies001/KuiesVox/releases/download/v0.13.0/KuiesVox-v0.13.0.apk.sha256",
            87L
        )
        val foreignHost = GitHubReleaseAsset(
            "$apkName.sha256",
            "https://example.com/$apkName.sha256",
            87L
        )

        assertEquals(
            matching,
            UpdateRepository.selectChecksumAsset(listOf(apk, matching, wrongName, foreignHost), apkName)
        )
        assertNull(UpdateRepository.selectChecksumAsset(listOf(wrongName, foreignHost), apkName))
        assertNull(UpdateRepository.selectChecksumAsset(listOf(apk), apkName))
    }
}
