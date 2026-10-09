package tw.kuies.voiceime

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class UpdateAvailable(
        val release: GitHubRelease,
        val version: SemanticVersion,
        val apk: GitHubReleaseAsset
    ) : AppUpdateState

    data class Downloading(
        val release: GitHubRelease,
        val downloadId: Long? = null
    ) : AppUpdateState

    data class ReadyToInstall(val release: GitHubRelease, val downloadId: Long) : AppUpdateState
    data class AwaitingUnknownSourcesPermission(
        val release: GitHubRelease,
        val downloadId: Long
    ) : AppUpdateState

    data class Installing(val release: GitHubRelease, val downloadId: Long) : AppUpdateState
    data class Installed(val release: GitHubRelease) : AppUpdateState
    data class Error(val message: String, val releasePageUrl: String? = null) : AppUpdateState
}

internal class AppUpdateController(
    context: Context,
    private val currentVersionName: String,
    private val onStateChanged: (AppUpdateState) -> Unit,
    private val launchInstaller: (Intent) -> Unit,
    private val repository: UpdateRepository = UpdateRepository(),
    private val appUpdateManager: AppUpdateManager = AppUpdateManager(context)
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val checking = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)

    @Volatile
    private var activeDownloadId: Long? = null

    @Volatile
    private var activeRelease: GitHubRelease? = null

    @Volatile
    private var activeChecksumAssetUrl: String? = null

    @Volatile
    private var activeApkFileName: String? = null

    @Volatile
    private var activeExpectedSizeBytes = 0L

    @Volatile
    private var activeIntegrity: ApkIntegrityResult? = null

    private var receiverRegistered = false

    @Volatile
    private var pendingInstallRelease: GitHubRelease? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId >= 0L && completedId == activeDownloadId) {
                processDownloadCompletion(completedId)
            }
        }
    }

    fun start() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            // DownloadManager may send this system broadcast from a privileged app UID.
            // The callback still verifies our exact active download ID and queries its status.
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    fun checkForUpdates() {
        if (!checking.compareAndSet(false, true)) return
        onStateChanged(AppUpdateState.Checking)
        executor.execute {
            val result = repository.check(currentVersionName, UpdateChannel.PREVIEW)
            mainHandler.post {
                checking.set(false)
                onStateChanged(
                    when (result) {
                        UpdateCheckResult.UpToDate -> AppUpdateState.UpToDate
                        is UpdateCheckResult.Available -> AppUpdateState.UpdateAvailable(
                            result.release,
                            result.version,
                            result.apk
                        )
                        is UpdateCheckResult.Error -> AppUpdateState.Error(
                            result.message,
                            result.releasePageUrl
                        )
                    }
                )
            }
        }
    }

    fun download(release: GitHubRelease, version: SemanticVersion, asset: GitHubReleaseAsset) {
        if (!downloading.compareAndSet(false, true)) return
        activeRelease = release
        activeChecksumAssetUrl =
            UpdateRepository.selectChecksumAsset(release.assets, asset.name)?.browserDownloadUrl
        activeApkFileName = AppUpdateManager.apkFileNameFor(version)
        activeExpectedSizeBytes = asset.sizeBytes
        activeIntegrity = null
        onStateChanged(AppUpdateState.Downloading(release))
        executor.execute {
            runCatching { appUpdateManager.enqueueDownload(asset, version) }
                .onSuccess { downloadId ->
                    activeDownloadId = downloadId
                    mainHandler.post {
                        onStateChanged(AppUpdateState.Downloading(release, downloadId))
                    }
                }
                .onFailure {
                    downloading.set(false)
                    activeRelease = null
                    mainHandler.post {
                        onStateChanged(
                            AppUpdateState.Error(
                                APK_DOWNLOAD_ERROR,
                                release.htmlUrl
                            )
                        )
                    }
                }
        }
    }

    fun install(release: GitHubRelease, downloadId: Long) {
        val integrity = activeIntegrity
        // 驗證結果必須對應這次下載，且檔名要與該 Release 的版本一致，避免用舊結果安裝其他檔案。
        val expectedFileName = SemanticVersion.parse(release.tagName)
            ?.let { AppUpdateManager.apkFileNameFor(it) }
        if (integrity !is ApkIntegrityResult.Verified ||
            integrity.downloadId != downloadId ||
            expectedFileName == null ||
            integrity.fileName != expectedFileName
        ) {
            onStateChanged(
                AppUpdateState.Error(AppUpdateManager.CHECKSUM_ERROR_MESSAGE, release.htmlUrl)
            )
            return
        }
        when (val request = appUpdateManager.requestInstall(downloadId, integrity)) {
            is InstallRequestResult.ReadyToLaunch -> {
                pendingInstallRelease = release
                onStateChanged(AppUpdateState.Installing(release, downloadId))
                try {
                    launchInstaller(request.intent)
                } catch (_: Exception) {
                    pendingInstallRelease = null
                    onStateChanged(AppUpdateState.Error(AppUpdateManager.INSTALL_ERROR_MESSAGE, release.htmlUrl))
                }
            }
            InstallRequestResult.PermissionRequired -> onStateChanged(
                AppUpdateState.AwaitingUnknownSourcesPermission(release, downloadId)
            )
            InstallRequestResult.Failed -> onStateChanged(
                AppUpdateState.Error(AppUpdateManager.INSTALL_ERROR_MESSAGE, release.htmlUrl)
            )
        }
    }

    fun onInstallResult(resultCode: Int) {
        val release = pendingInstallRelease ?: return
        pendingInstallRelease = null
        onStateChanged(
            if (resultCode == Activity.RESULT_OK) {
                AppUpdateState.Installed(release)
            } else {
                AppUpdateState.Error(AppUpdateManager.INSTALL_ERROR_MESSAGE, release.htmlUrl)
            }
        )
    }

    fun openUnknownSourcesSettings(): Boolean = appUpdateManager.openUnknownSourcesSettings()

    private fun processDownloadCompletion(downloadId: Long) {
        val release = activeRelease ?: return
        executor.execute {
            val status = runCatching { appUpdateManager.getDownloadStatus(downloadId) }
                .getOrDefault(ApkDownloadStatus.Failed)
            // 驗證在主執行緒之外進行：需要計算下載檔案的雜湊並取得 checksum。
            val integrity = if (status == ApkDownloadStatus.Complete) {
                runCatching {
                    appUpdateManager.verifyDownloadedApk(
                        downloadId = downloadId,
                        checksumAssetUrl = activeChecksumAssetUrl,
                        apkFileName = activeApkFileName.orEmpty(),
                        expectedSizeBytes = activeExpectedSizeBytes
                    )
                }.getOrDefault(ApkIntegrityResult.ReadFailed)
            } else {
                null
            }
            mainHandler.post {
                if (downloadId != activeDownloadId) return@post
                when (status) {
                    ApkDownloadStatus.InProgress -> Unit
                    ApkDownloadStatus.Complete -> {
                        downloading.set(false)
                        activeDownloadId = null
                        activeIntegrity = integrity
                        if (integrity is ApkIntegrityResult.Verified) {
                            install(release, downloadId)
                        } else {
                            activeRelease = null
                            runCatching { appUpdateManager.discardDownload(downloadId) }
                            onStateChanged(
                                AppUpdateState.Error(
                                    AppUpdateManager.CHECKSUM_ERROR_MESSAGE,
                                    release.htmlUrl
                                )
                            )
                        }
                    }
                    ApkDownloadStatus.Failed -> {
                        downloading.set(false)
                        activeDownloadId = null
                        activeRelease = null
                        onStateChanged(
                            AppUpdateState.Error(APK_DOWNLOAD_ERROR, release.htmlUrl)
                        )
                    }
                }
            }
        }
    }

    override fun close() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(downloadReceiver) }
            receiverRegistered = false
        }
        executor.shutdownNow()
    }

    companion object {
        const val APK_DOWNLOAD_ERROR = "APK 下載失敗，請稍後再試。"
    }
}
