package tw.kuies.voiceime

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.IOException

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

internal class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueDownload(asset: GitHubReleaseAsset, version: SemanticVersion): Long {
        val safeUrl = GitHubReleaseConfig.officialAssetUrlOrNull(asset.browserDownloadUrl)
            ?: throw IOException("APK URL is not a KuiesVox release asset.")
        if (!asset.name.endsWith(".apk", ignoreCase = true)) {
            throw IOException("Release asset is not an APK.")
        }

        val safeVersion = version.toString().replace(Regex("[^A-Za-z0-9.-]"), "_")
        val fileName = "KuiesVox-v$safeVersion.apk"
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
    fun requestInstall(downloadId: Long): InstallRequestResult {
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
    }
}
