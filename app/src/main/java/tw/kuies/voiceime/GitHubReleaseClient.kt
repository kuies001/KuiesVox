package tw.kuies.voiceime

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

internal data class GitHubReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val sizeBytes: Long
)

internal data class GitHubRelease(
    val tagName: String,
    val title: String,
    val body: String,
    val publishedAt: String?,
    val htmlUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubReleaseAsset>
)

internal fun interface GitHubReleaseSource {
    @Throws(IOException::class)
    fun fetchReleases(): List<GitHubRelease>
}

internal class GitHubReleaseClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) : GitHubReleaseSource {
    override fun fetchReleases(): List<GitHubRelease> {
        val request = Request.Builder()
            .url(GitHubReleaseConfig.RELEASES_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "KuiesVox-Android")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub Releases API returned HTTP ${response.code}.")
            }
            val responseBody = response.body?.string()
                ?: throw IOException("GitHub Releases API returned an empty response.")
            return parseReleaseList(responseBody)
        }
    }

    internal fun parseReleaseList(json: String): List<GitHubRelease> {
        val releases = JSONArray(json)
        return buildList {
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                val tagName = release.optString("tag_name", "").trim()
                if (tagName.isEmpty()) continue

                val assetsJson = release.optJSONArray("assets") ?: JSONArray()
                val assets = buildList {
                    for (assetIndex in 0 until assetsJson.length()) {
                        val asset = assetsJson.optJSONObject(assetIndex) ?: continue
                        val name = asset.optString("name", "").trim()
                        val downloadUrl = asset.optString("browser_download_url", "").trim()
                        if (name.isNotEmpty() && downloadUrl.isNotEmpty()) {
                            add(
                                GitHubReleaseAsset(
                                    name = name,
                                    browserDownloadUrl = downloadUrl,
                                    sizeBytes = asset.optLong("size", 0L)
                                )
                            )
                        }
                    }
                }
                val publishedAt = release.optString("published_at", "")
                    .takeIf { it.isNotBlank() && it != "null" }
                add(
                    GitHubRelease(
                        tagName = tagName,
                        title = release.optString("name", "").ifBlank { tagName },
                        body = release.optString("body", ""),
                        publishedAt = publishedAt,
                        htmlUrl = GitHubReleaseConfig.releasePageUrlOrFallback(
                            release.optString("html_url", "")
                        ),
                        draft = release.optBoolean("draft", false),
                        prerelease = release.optBoolean("prerelease", false),
                        assets = assets
                    )
                )
            }
        }
    }
}
