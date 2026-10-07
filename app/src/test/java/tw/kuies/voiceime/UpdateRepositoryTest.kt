package tw.kuies.voiceime

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {
    @Test
    fun ignoresDraftAndAllowsPreviewReleaseByDefault() {
        val repository = repository(
            release("v8.0.0", draft = true),
            release("v0.9.0"),
            release("v0.10.0-rc.1", prerelease = true, assets = listOf(apk("KuiesVox-preview.apk")))
        )

        val result = repository.check("0.9.0")

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("0.10.0-rc.1", (result as UpdateCheckResult.Available).version.toString())
    }

    @Test
    fun stableChannelDoesNotSelectPrerelease() {
        val repository = repository(
            release("v0.9.0"),
            release("v1.0.0-rc.1", prerelease = true, assets = listOf(apk("KuiesVox-preview.apk")))
        )

        assertEquals(UpdateCheckResult.UpToDate, repository.check("0.9.0", UpdateChannel.STABLE))
    }

    @Test
    fun returnsUpToDateWhenInstalledVersionIsNewer() {
        val repository = repository(release("v0.9.0"))

        assertEquals(UpdateCheckResult.UpToDate, repository.check("0.10.0"))
    }

    @Test
    fun missingApkReturnsHelpfulErrorAndReleaseLink() {
        val repository = repository(release("v0.10.0", assets = emptyList()))

        val result = repository.check("0.9.0")

        assertTrue(result is UpdateCheckResult.Error)
        assertEquals(UpdateRepository.NO_APK_ERROR, (result as UpdateCheckResult.Error).message)
        assertEquals(RELEASE_PAGE_URL, result.releasePageUrl)
    }

    @Test
    fun selectsKuiesVoxReleaseApkBeforeDebugOrOtherAssets() {
        val assets = listOf(
            GitHubReleaseAsset("source.zip", "https://github.com/kuies001/KuiesVox/releases/download/v0.9.0/source.zip", 10),
            apk("KuiesVox-debug.apk"),
            apk("Other-release.apk"),
            apk("KuiesVox-release.apk")
        )

        assertEquals("KuiesVox-release.apk", UpdateRepository.selectKuiesVoxApk(assets)?.name)
    }

    @Test
    fun usesOnlyApkAndAllowsSingleApkFallback() {
        val assets = listOf(
            GitHubReleaseAsset("source.tar.gz", "https://github.com/kuies001/KuiesVox/releases/download/v0.9.0/source.tar.gz", 10),
            apk("custom-build.apk")
        )

        assertEquals("custom-build.apk", UpdateRepository.selectKuiesVoxApk(assets)?.name)
    }

    @Test
    fun rejectsThirdPartyApkUrl() {
        val result = repository(
            release(
                "v0.10.0",
                assets = listOf(
                    GitHubReleaseAsset("KuiesVox-release.apk", "https://example.com/KuiesVox.apk", 10)
                )
            )
        ).check("0.9.0")

        assertEquals(UpdateRepository.NO_APK_ERROR, (result as UpdateCheckResult.Error).message)
    }

    @Test
    fun networkAndJsonFailuresBecomeFriendlyErrorResults() {
        val networkError = UpdateRepository(GitHubReleaseSource { error("network unavailable") })
            .check("0.9.0")
        val jsonError = UpdateRepository(GitHubReleaseSource { throw JSONException("invalid response") })
            .check("0.9.0")

        assertEquals(UpdateRepository.GENERIC_UPDATE_ERROR, (networkError as UpdateCheckResult.Error).message)
        assertEquals(UpdateRepository.GENERIC_UPDATE_ERROR, (jsonError as UpdateCheckResult.Error).message)
    }

    private fun repository(vararg releases: GitHubRelease): UpdateRepository =
        UpdateRepository(GitHubReleaseSource { releases.toList() })

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<GitHubReleaseAsset> = listOf(apk("KuiesVox-$tag-debug.apk"))
    ) = GitHubRelease(
        tagName = tag,
        title = "KuiesVox $tag",
        body = "更新內容",
        publishedAt = "2026-10-07T00:00:00Z",
        htmlUrl = RELEASE_PAGE_URL,
        draft = draft,
        prerelease = prerelease,
        assets = assets
    )

    private fun apk(name: String) = GitHubReleaseAsset(
        name = name,
        browserDownloadUrl = "https://github.com/${GitHubReleaseConfig.OWNER}/${GitHubReleaseConfig.REPOSITORY}/releases/download/v0.9.0/$name",
        sizeBytes = 12L
    )

    companion object {
        private val RELEASE_PAGE_URL = GitHubReleaseConfig.RELEASES_PAGE_URL
    }
}
