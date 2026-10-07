package tw.kuies.voiceime

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object GitHubReleaseConfig {
    const val OWNER = "kuies001"
    const val REPOSITORY = "KuiesVox"
    const val RELEASES_API_URL =
        "https://api.github.com/repos/$OWNER/$REPOSITORY/releases?per_page=100"
    const val RELEASES_PAGE_URL = "https://github.com/$OWNER/$REPOSITORY/releases"

    fun releasePageUrlOrFallback(url: String?): String =
        officialReleasePageUrlOrNull(url) ?: RELEASES_PAGE_URL

    fun officialReleasePageUrlOrNull(url: String?): String? =
        url.toOfficialHttpUrlOrNull()
            ?.takeIf { it.encodedPath.startsWith("/$OWNER/$REPOSITORY/releases/tag/") }
            ?.toString()

    fun officialAssetUrlOrNull(url: String?): String? =
        url.toOfficialHttpUrlOrNull()
            ?.takeIf { it.encodedPath.startsWith("/$OWNER/$REPOSITORY/releases/download/") }
            ?.toString()

    private fun String?.toOfficialHttpUrlOrNull(): HttpUrl? {
        val parsed = this?.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https" || parsed.host != "github.com") return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        return parsed
    }
}
