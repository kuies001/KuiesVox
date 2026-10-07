# KuiesVox v0.9.0

KuiesVox v0.9.0 新增 App 內版本檢查與 APK 更新流程，並提供 GitHub Releases 手動下載入口。

## 新功能

- 新增 App 內版本更新檢查，從 KuiesVox 官方 GitHub Releases 讀取 release 清單。
- 支援檢查 Preview prerelease 與 Stable release，並依 Semantic Version 選出最高版本。
- 新增 App 內下載官方 Release APK 的功能，下載狀態會顯示在設定頁與 Android 通知列。
- 下載完成後呼叫 Android 系統安裝器，讓使用者確認是否更新。
- 設定首頁新增「前往 GitHub Releases」手動下載入口；發現更新時也可直接開啟該版本頁面。
- 提供查看更新內容的視窗，避免整篇 release notes 佔滿設定首頁。

## 安全

- 公開 GitHub Releases API 匿名讀取，不使用 GitHub Token、OAuth token 或 GitHub secret。
- APK 僅從 KuiesVox 官方 GitHub Release assets 下載，使用 HTTPS。
- APK 存放於 App 可控的下載路徑，不要求 `MANAGE_EXTERNAL_STORAGE`。
- Android 基於安全限制，下載完成後仍需由使用者在系統安裝器確認。
- 更新需使用相同 `applicationId`、相同 APK signing certificate，以及較高的 `versionCode`。
