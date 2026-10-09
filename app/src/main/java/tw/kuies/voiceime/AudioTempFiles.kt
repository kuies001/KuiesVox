package tw.kuies.voiceime

import java.io.File

/**
 * 錄音暫存檔（`voice-*.wav`，位於 app 私有 cacheDir）的清理規則。
 *
 * 只認得本 App 自己產生的命名，避免刪到其他功能留下的檔案；
 * 只有「已過期且不再使用中」的檔案會被選中，正常辨識流程仍在使用的檔案不受影響。
 */
internal object AudioTempFiles {
    const val PREFIX = "voice-"
    const val SUFFIX = ".wav"

    /** 啟動清掃只處理明顯過期的檔案（6 小時），避免與進行中的錄音競爭。 */
    const val MAX_AGE_MILLIS = 6 * 60 * 60 * 1000L

    fun isTempRecordingFile(name: String): Boolean =
        name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    fun expiredFiles(
        files: List<File>,
        nowMillis: Long,
        maxAgeMillis: Long = MAX_AGE_MILLIS
    ): List<File> = files.filter { file ->
        file.isFile &&
            isTempRecordingFile(file.name) &&
            (nowMillis - file.lastModified()) > maxAgeMillis
    }
}
