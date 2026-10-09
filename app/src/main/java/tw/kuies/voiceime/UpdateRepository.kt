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

        /**
         * Release 發布的 checksum 資產命名是 `<APK 去掉 .apk>.sha256`：
         * `KuiesVox-v0.14.0.apk` → `KuiesVox-v0.14.0.sha256`（與 `release.yml` 的 `sha256sum` 輸出同名）。
         */
        internal fun checksumAssetNameFor(apkName: String): String? {
            if (!apkName.endsWith(APK_SUFFIX, ignoreCase = true)) return null
            return apkName.dropLast(APK_SUFFIX.length) + CHECKSUM_SUFFIX
        }

        /** 只接受與 APK 同版本、且位於官方 Release 下載路徑的 checksum 資產。 */
        internal fun selectChecksumAsset(
            assets: List<GitHubReleaseAsset>,
            apkName: String
        ): GitHubReleaseAsset? {
            val expectedName = checksumAssetNameFor(apkName) ?: return null
            return assets.firstOrNull { asset ->
                asset.name.equals(expectedName, ignoreCase = true) &&
                    GitHubReleaseConfig.officialAssetUrlOrNull(asset.browserDownloadUrl) != null
            }
        }

        private const val APK_SUFFIX = ".apk"
        private const val CHECKSUM_SUFFIX = ".sha256"

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
