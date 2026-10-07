# KuiesVox v0.7.0 Beta

此版本為測試版，提供 Android 語音輸入與精簡輸入法面板，尚未作為正式版發布。

## 本版功能

- Groq Whisper 語音辨識，並可停止或取消目前流程。
- 個人常用詞、文字修正规則與可選 MCP Context。
- 可選的 Groq 智慧逐字稿整理。
- 顯示輸入狀態與最近結果。
- 精簡面板含鍵盤切換與退格；退格支援單擊刪除及長按連續刪除。

## 安裝方式

1. 從 [KuiesVox GitHub Releases](https://github.com/kuies001/VoiceIME/releases) 下載 `KuiesVox-v0.7.0-debug.apk`。
2. 在 Android 設定中允許瀏覽器或檔案管理器安裝未知來源 App，開啟 APK 完成安裝。
3. 開啟 KuiesVox，設定自己的 Groq API Key，並授予麥克風權限。
4. 在 Android 的「設定 → 系統 → 語言與輸入」啟用 KuiesVox，並選為目前輸入法。廠牌與系統版本不同時，選單名稱可能不同。

## 已知限制

- 本版是 debug 測試版，可能有未發現的問題，不代表正式發布簽章或 Play 商店版本。
- 語音辨識與長文字整理需要網路及可用的 Groq API；本版沒有離線語音辨識。
- 語音錄音會傳送至 Groq API 辨識，長文字整理也可能傳送文字至 Groq API。請先確認自己的資料與服務使用需求。
- Android 不同版本、廠牌及 App 的輸入欄位實作可能不同；密碼欄位或自訂輸入元件的行為仍取決於目標 App。
- MCP Context 須另外設定可用的 MCP 服務。

## Groq API Key

使用者需自行準備並在 KuiesVox App 的 Groq 設定頁輸入 Groq API Key。此 APK 不內含 API Key；請勿將個人 API Key 貼到公開 issue、README 或 Git repository。
