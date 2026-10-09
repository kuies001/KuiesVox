package tw.kuies.voiceime

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.IOException
import java.io.InputStream

internal sealed interface ApkIntegrityResult {
    /** 通過 checksum 與大小驗證，才允許交給系統安裝器；downloadId 綁定被驗證的那次下載。 */
    data class Verified(
        val downloadId: Long,
        val fileName: String,
        val sha256: String
    ) : ApkIntegrityResult

    data object ChecksumMissing : ApkIntegrityResult
    data object ChecksumInvalid : ApkIntegrityResult
    data object ChecksumUnavailable : ApkIntegrityResult
    data object SizeMismatch : ApkIntegrityResult
    data object DigestMismatch : ApkIntegrityResult
    data object ReadFailed : ApkIntegrityResult
}

internal sealed interface ApkDownloadStatus {
    data object InProgress : ApkDownloadStatus
    data object Complete : ApkDownloadStatus
    data object Failed : ApkDownloadStatus
}

internal sealed interface InstallRequestResult {
    data class ReadyToLaunch(val intent: Intent) : InstallRequestResult
    data object PermissionRequired : InstallRequestResult
    data object Failed : InstallRequestResult
}

internal class AppUpdateManager(
    context: Context,
    private val checksumSource: GitHubChecksumSource = GitHubReleaseClient()
) {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(asset: GitHubReleaseAsset, version: SemanticVersion): Long {
        val safeUrl = GitHubReleaseConfig.officialAssetUrlOrNull(asset.browserDownloadUrl)
            ?: throw IOException("APK URL is not a KuiesVox release asset.")
        if (!asset.name.endsWith(".apk", ignoreCase = true)) {
            throw IOException("Release asset is not an APK.")
        }

        val fileName = apkFileNameFor(version)
        val request = DownloadManager.Request(Uri.parse(safeUrl))
            .setTitle("下載 KuiesVox v$version")
            .setDescription("下載完成後會由 Android 安裝器確認更新。")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        return downloadManager.enqueue(request)
    }

    fun getDownloadStatus(downloadId: Long): ApkDownloadStatus {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return ApkDownloadStatus.Failed
        cursor.use {
            if (!it.moveToFirst()) return ApkDownloadStatus.Failed
            return when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> ApkDownloadStatus.Complete
                DownloadManager.STATUS_FAILED -> ApkDownloadStatus.Failed
                else -> ApkDownloadStatus.InProgress
            }
        }
    }

    @Suppress("DEPRECATION")
    fun requestInstall(downloadId: Long, integrity: ApkIntegrityResult): InstallRequestResult {
        // 未通過完整性驗證、或驗證結果屬於另一次下載的檔案，都不得交給系統安裝器。
        if (integrity !is ApkIntegrityResult.Verified || integrity.downloadId != downloadId) {
            return InstallRequestResult.Failed
        }
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            return InstallRequestResult.PermissionRequired
        }
        val contentUri = downloadManager.getUriForDownloadedFile(downloadId)
            ?: return InstallRequestResult.Failed
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        return InstallRequestResult.ReadyToLaunch(installIntent)
    }

    /**
     * 安裝前的完整性驗證：比對 Release 發布的 SHA-256、下載檔案大小與檔名。
     * 任一項無法確認（缺 checksum、格式錯誤、無法讀取、下載不完整）都必須拒絕安裝。
     * 簽章驗證仍由 Android 安裝器負責，這裡不是替代品。
     */
    fun verifyDownloadedApk(
        downloadId: Long,
        checksumAssetUrl: String?,
        apkFileName: String,
        expectedSizeBytes: Long
    ): ApkIntegrityResult {
        val checksumUrl = checksumAssetUrl ?: return ApkIntegrityResult.ChecksumMissing
        if (expectedSizeBytes > 0L && !matchesDownloadedSize(downloadId, expectedSizeBytes)) {
            return ApkIntegrityResult.SizeMismatch
        }
        val checksum = try {
            ApkChecksumPolicy.parse(checksumSource.fetchChecksumText(checksumUrl))
        } catch (_: Exception) {
            return ApkIntegrityResult.ChecksumUnavailable
        } ?: return ApkIntegrityResult.ChecksumInvalid
        if (!checksum.matchesFileName(apkFileName)) return ApkIntegrityResult.ChecksumInvalid

        val actualDigest = try {
            openDownloadedInputStream(downloadId)?.use { ApkChecksumPolicy.sha256Of(it) }
        } catch (_: Exception) {
            null
        } ?: return ApkIntegrityResult.ReadFailed
        if (!checksum.matchesDigest(actualDigest)) return ApkIntegrityResult.DigestMismatch
        return ApkIntegrityResult.Verified(
            downloadId = downloadId,
            fileName = apkFileName,
            sha256 = actualDigest
        )
    }

    /** 驗證失敗時移除已下載的檔案，避免留下來源不明的 APK。 */
    fun discardDownload(downloadId: Long) {
        runCatching { downloadManager.remove(downloadId) }
    }

    private fun matchesDownloadedSize(downloadId: Long, expectedSizeBytes: Long): Boolean {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return false
        cursor.use {
            if (!it.moveToFirst()) return false
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return false
            val totalSize =
                it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return totalSize == expectedSizeBytes
        }
    }

    private fun openDownloadedInputStream(downloadId: Long): InputStream? {
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return null
        return appContext.contentResolver.openInputStream(uri)
    }

    fun openUnknownSourcesSettings(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val INSTALL_ERROR_MESSAGE =
            "新版 APK 與目前安裝版本的簽章可能不同，請改用手動安裝或確認簽章設定。"
        const val CHECKSUM_ERROR_MESSAGE =
            "下載的 APK 未通過完整性驗證（SHA-256 或檔案大小不符），已取消安裝。請改用 GitHub Releases 手動下載。"

        internal fun apkFileNameFor(version: SemanticVersion): String {
            val safeVersion = version.toString().replace(Regex("[^A-Za-z0-9.-]"), "_")
            return "KuiesVox-v$safeVersion.apk"
        }
    }
}
