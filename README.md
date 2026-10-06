# VoiceIME

VoiceIME 是 Android 語音輸入法。

**目前版本：v0.7.0 Beta（測試版）**

## 功能

- 透過 Groq Whisper 將語音轉成文字
- 錄音、停止與取消語音流程
- 以個人常用詞與文字修正规則改善辨識結果
- 可選 MCP Context 與智慧逐字稿整理
- 顯示輸入狀態與最近結果
- 精簡輸入法面板，包含鍵盤切換與退格；退格支援長按連續刪除

## 系統需求

- Android 8.0（API 26）或更新版本
- 網路連線與麥克風權限
- 使用語音辨識前需自行設定 Groq API Key

## 安裝與啟用

1. 從 [GitHub Releases](https://github.com/kuies001/VoiceIME/releases) 下載 `VoiceIME-v0.7.0-debug.apk`。
2. 在 Android 設定中允許目前使用的瀏覽器或檔案管理器安裝未知來源 App，然後開啟 APK 安裝。
3. 開啟 VoiceIME，在 Groq 設定頁輸入並儲存自己的 Groq API Key，並允許麥克風權限。
4. 到 Android 的「設定 → 系統 → 語言與輸入」啟用 VoiceIME，再將它選為目前輸入法。不同廠牌的設定名稱可能不同。
5. 在文字欄位切換到 VoiceIME，點「開始語音輸入」；完成後按停止。退格按鈕可單擊刪除一個字元，或長按連續刪除。

此版本仍是測試版。v0.7.0 的 GitHub Release 尚未建立；發布時請將上述 APK 作為 Release asset 上傳。

## Groq API Key 與資料處理

Groq API Key 需由使用者自行提供，並在 App 的 Groq 設定頁儲存；API Key 不包含在 APK 中。語音辨識會將錄音送往 Groq API，長文字整理也可能使用 Groq API。

## 建置

使用 Android Studio 開啟專案，或執行：

```shell
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK 預設輸出於 `app/build/outputs/apk/debug/app-debug.apk`。v0.7.0 發布檔名為 `VoiceIME-v0.7.0-debug.apk`。
