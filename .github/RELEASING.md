# 發版流程

本文件說明如何準備 KuiesVox 版本，並透過 GitHub Actions 建置、簽署及發布 APK。App 的版本更新功能會讀取 GitHub Releases；此流程不修改該功能。

## 發版前準備

1. 將已確認的功能變更與修正整理到 `CHANGELOG.md` 最上方，放在 `Unreleased` 區塊之後、較舊版本之前。
2. 標題使用 `## [X.Y.Z] - YYYY-MM-DD`，並使用 Keep a Changelog 分類，例如 `Added`、`Changed`、`Fixed`、`Security` 或 `Known Limitations`。每個分類至少要有一條實際變更項目。
3. 只記錄本版實際內容。發版程式會拒絕缺少版本、日期格式錯誤、空白或格式不合法的區塊。
4. 先提交發版流程本身的變更（例如 `release.ps1`、`.github/` 下的腳本或 workflow）。發版腳本只會接受 App、Gradle、`README.md` 與 `CHANGELOG.md` 等版本變更路徑中的未提交變更。

## 執行發版

請在 Windows PowerShell 中，從 `main` 分支的專案根目錄執行。工作目錄不可落後於 `origin/main`，且不能含有不相關的未提交變更。

先執行 DryRun：

```powershell
.\release.ps1 0.13.0 -DryRun
```

確認輸出的版本更新說明、檢查、測試及 Release build 結果後，再執行正式發版：

```powershell
.\release.ps1 0.13.0
```

正式發版也可使用其他符合 `X.Y.Z` 格式且高於目前版本的版本號。

### DryRun 會做什麼

- 確認目前分支為 `main`、工作目錄符合允許的版本變更路徑，並檢查遠端分支與標籤。
- 暫時更新 `app/build.gradle.kts` 的 `versionName` 與 `versionCode`，並更新 README 版本標記。
- 從 `CHANGELOG.md` 擷取指定版本區塊到系統暫存目錄，顯示該段更新說明。
- 執行 release app 編譯、Debug 單元測試來源編譯、JVM 單元測試及 Release APK 組裝。
- 結束時還原版本檔與 README，並刪除暫存更新說明。
- 不建立 commit、不 push、不建立或推送 tag，也不建立 GitHub Release。

## 正式發版內容

成功執行 `release.ps1` 後，腳本會更新 `versionName`、遞增 `versionCode`、更新 README 版本標記，並將允許的未提交 App／Gradle／README／`CHANGELOG.md` 變更一併納入發版 commit。腳本會先完成安全檢查、編譯、測試與 Release APK 組裝，然後 commit 並 push `main`，建立及 push `vX.Y.Z` 標籤。

GitHub Actions 收到 tag 後會：

1. 確認 tag、`app/build.gradle.kts` 版本及 `CHANGELOG.md` 區塊相符。
2. 再次執行 app 與測試來源編譯、JVM 單元測試及 Release APK 組裝。
3. 使用 repository Actions secrets 簽署 APK，並驗證 APK 簽章。
4. 計算 SHA-256，將 `KuiesVox-vX.Y.Z.apk` 與 `KuiesVox-vX.Y.Z.sha256` 附加到 GitHub Release。
5. 只將該版本的 `CHANGELOG.md` 分類項目作為 Release 說明，不會包含其他版本。

使用 `workflow_dispatch` 重建既有 tag 時，APK 仍從所選 tag 的原始碼建置；更新紀錄與擷取腳本則從 `main` 讀取，以支援尚未包含集中式 `CHANGELOG.md` 的歷史 tag。

`release.ps1` 與 GitHub Actions 共用 `.github/scripts/Extract-ChangelogReleaseNotes.ps1` 擷取及驗證邏輯。產生的說明檔只放在系統暫存目錄，不會加入 repository。

## Release APK 簽署 Secrets

首次設定或更新正式簽署時，請在 GitHub repository 的 **Settings → Secrets and variables → Actions** 設定：

- `KUIESVOX_KEYSTORE_BASE64`
- `KUIESVOX_KEY_ALIAS`
- `KUIESVOX_KEY_PASSWORD`
- `KUIESVOX_STORE_PASSWORD`

Keystore 與密碼只存放在 GitHub Secrets；不要將簽署檔、密碼或 API Key 提交到 repository。GitHub Actions 會在 runner 暫存目錄還原 keystore，工作結束時移除。
