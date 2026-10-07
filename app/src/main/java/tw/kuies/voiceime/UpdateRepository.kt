package tw.kuies.voiceime

internal enum class UpdateChannel {
    STABLE,
    PREVIEW
}

internal sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(
        val release: GitHubRelease,
        val version: SemanticVersion,
        val apk: GitHubReleaseAsset
    ) : UpdateCheckResult

    data class Error(val message: String, val releasePageUrl: String? = null) : UpdateCheckResult
}

internal class UpdateRepository(
    private val releaseSource: GitHubReleaseSource = GitHubReleaseClient()
) {
    fun check(
        currentVersionName: String,
        channel: UpdateChannel = UpdateChannel.PREVIEW
    ): UpdateCheckResult {
        val currentVersion = SemanticVersion.parse(currentVersionName)
            ?: return UpdateCheckResult.Error(GENERIC_UPDATE_ERROR)

        return try {
            val latest = releaseSource.fetchReleases()
                .asSequence()
                .filterNot { it.draft }
                .filter { channel == UpdateChannel.PREVIEW || !it.prerelease }
                .mapNotNull { release ->
                    SemanticVersion.parse(release.tagName)?.let { release to it }
                }
                .sortedByDescending { it.second }
                .firstOrNull()
                ?: return UpdateCheckResult.Error(GENERIC_UPDATE_ERROR)

            val (release, latestVersion) = latest
            if (latestVersion <= currentVersion) return UpdateCheckResult.UpToDate

            val apk = selectKuiesVoxApk(release.assets)
                ?: return UpdateCheckResult.Error(
                    message = NO_APK_ERROR,
                    releasePageUrl = release.htmlUrl
                )
            UpdateCheckResult.Available(release, latestVersion, apk)
        } catch (_: Exception) {
            UpdateCheckResult.Error(GENERIC_UPDATE_ERROR)
        }
    }

    companion object {
        const val GENERIC_UPDATE_ERROR = "目前無法檢查更新，請稍後再試。"
        const val NO_APK_ERROR = "此版本沒有可直接安裝的 APK，請前往 GitHub Releases 查看。"

        internal fun selectKuiesVoxApk(assets: List<GitHubReleaseAsset>): GitHubReleaseAsset? {
            val apks = assets.filter { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    GitHubReleaseConfig.officialAssetUrlOrNull(asset.browserDownloadUrl) != null
            }
            if (apks.size == 1) return apks.single()
            if (apks.isEmpty()) return null

            val kuiesVoxApks = apks.filter { it.name.startsWith("KuiesVox-", ignoreCase = true) }
            val candidates = kuiesVoxApks.ifEmpty { apks }
            return candidates.sortedWith(
                compareBy<GitHubReleaseAsset> { asset ->
                    when {
                        asset.name.contains("release", ignoreCase = true) -> 0
                        asset.name.contains("debug", ignoreCase = true) -> 1
                        else -> 2
                    }
                }.thenBy { it.name.lowercase() }
            ).firstOrNull()
        }
    }
}
