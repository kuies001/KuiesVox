# KuiesVox

KuiesVox 是 Android 語音輸入法。

<!-- KUIESVOX_VERSION_START -->
目前版本：v0.12.0 Beta（測試版）
<!-- KUIESVOX_VERSION_END -->

## 版本與下載

查看[版本更新紀錄](CHANGELOG.md)，或前往 [GitHub Releases](https://github.com/kuies001/KuiesVox/releases) 下載 APK。

## 功能

- 使用 Groq Whisper 語音辨識，可選 Whisper Large V3 或 Whisper Large V3 Turbo。
- 錄音、停止與取消語音流程，並可在輸入法面板直接插入換行。
- 使用個人詞庫與修正规則改善辨識結果，並可選擇 MCP Context。
- Groq 智慧文字整理；Gemini 與 OpenAI 提供 Provider、模型與 API Key 設定欄位，目前尚未串接 API，使用時會採用本機修正結果。
- 可設定句尾句號為智慧判斷、加入或不加入。
- 精簡深色輸入面板提供設定、刪除、換行與切換鍵盤工具，退格支援長按連續刪除。
- 顯示輸入狀態與最近結果。

## 系統需求

- Android 8.0（API 26）或更新版本
- 網路連線與麥克風權限
- 使用 Groq 語音辨識前需自行設定 Groq API Key

## 安裝與啟用

1. 從 [GitHub Releases](https://github.com/kuies001/KuiesVox/releases) 下載最新的 `KuiesVox-vX.Y.Z.apk`。
2. 在 Android 設定中允許目前使用的瀏覽器或檔案管理器安裝未知來源 App，然後開啟 APK 安裝。
3. 開啟 KuiesVox，在 Groq 設定頁輸入並儲存自己的 Groq API Key，並允許麥克風權限。
4. 到 Android 的「設定 → 系統 → 語言與輸入」啟用 KuiesVox，再將它選為目前輸入法。不同廠牌的設定名稱可能不同。
5. 在文字欄位切換到 KuiesVox，點大圓形麥克風按鈕開始語音輸入；完成後按停止。也可使用工具列刪除、換行或切換鍵盤。

此版本仍是測試版。Debug APK 會作為 GitHub Release asset 提供。

## App 更新

KuiesVox 支援在 App 內檢查 GitHub Releases 新版本、下載新版 APK，並呼叫 Android 系統安裝器；設定首頁也提供前往 GitHub Releases 手動下載的入口。

Android 基於安全限制，下載完成後仍需由使用者確認安裝。App 內直接更新需要允許 KuiesVox 安裝未知來源應用程式。

更新需要相同的 `applicationId`、相同的 APK signing certificate，以及較高的 `versionCode`。若簽章不同，Android 會拒絕覆蓋安裝。

## API Key 與資料處理

API Key 由使用者自行申請並儲存在 App 私有設定中，不包含在 APK 內，也不會顯示完整金鑰。Groq 語音辨識與 Groq 文字整理可共用 Groq Key。Gemini 與 OpenAI 的 Key 欄位目前僅供設定保存，不會呼叫對應 API。

- Groq：[申請 API Key](https://console.groq.com/keys)
- Google Gemini：[申請 API Key](https://aistudio.google.com/apikey)
- OpenAI：[申請 API Key](https://platform.openai.com/api-keys)

## 建置

使用 Android Studio 開啟專案，或執行：

```shell
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK 預設輸出於 `app/build/outputs/apk/debug/app-debug.apk`。正式 Release APK 由 GitHub Actions 建置並以 `KuiesVox-vX.Y.Z.apk` 命名。

發版指令、DryRun 與簽署設定請參閱[發版流程說明](.github/RELEASING.md)。
