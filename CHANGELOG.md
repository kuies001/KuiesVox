# Changelog

本檔依 Keep a Changelog 的基本格式整理 KuiesVox 各版本的重要變更。版本由新到舊排列；已正式發布版本的日期採 GitHub Release 發布日期。

## 版本索引

- [未發布](#unreleased)
- [0.13.0](#v0-13-0)
- [0.12.0](#v0-12-0)
- [0.11.0](#v0-11-0)
- [0.10.0](#v0-10-0)
- [0.9.0](#v0-9-0)
- [0.8.0](#v0-8-0)
- [0.7.0](#v0-7-0)

## [Unreleased]

此區留給下一版尚未發布的變更。

<a id="v0-13-0"></a>
## [0.13.0] - 2026-10-09

### Changed

- 移除快捷短語分類操作，改以「全部／最近使用」檢視清單；既有分類中的短語資料完整保留，搜尋可搭配排序使用。
- 最近使用排序依實際成功插入時間更新，瀏覽、搜尋、編輯或長按不會更新使用時間。

### Fixed

- 修復長按快捷短語無法顯示刪除確認的問題；長按、卡片單筆刪除與批次刪除共用 IME 內確認介面。

<a id="v0-12-0"></a>
## [0.12.0] - 2026-10-08

### Changed

- 將狀態燈與單排功能工具列分開，並調整 IME 內容寬度配置。
- 工具列順序整理為剪貼簿、全選、清除、鍵盤、設定、更多。

### Fixed

- 修正工具列「全選」與「清除」文字顯示擁擠的問題。
- 對齊清除確認按鈕，以及錄音中的停止／取消按鈕尺寸與內容。

### Known Limitations

- 本版沒有新增已知限制。

<a id="v0-11-0"></a>
## [0.11.0] - 2026-10-07

### Added

- 新增 IME 工具列剪貼簿入口，可直接開啟剪貼簿歷史。
- 將 Android 系統剪貼簿文字收錄到 KuiesVox 剪貼簿歷史；若 Phone Link 已將文字同步到 Android 系統剪貼簿，也可由 IME 收錄。

### Changed

- 將刪除鍵與 Enter 鍵改為右側上下排列，語音操作區略向左調整，以降低按鍵混淆。
- 主畫面、錄音狀態與歷史頁共用固定內容高度，歷史清單改在面板內捲動。
- Clipboard listener 僅在 IME 輸入視圖活躍時註冊，離開輸入視圖或結束 service 時移除。

### Security

- 排除 Android 標記為敏感的剪貼簿內容，以及密碼欄位中的剪貼簿內容。

<a id="v0-10-0"></a>
## [0.10.0] - 2026-10-07

### Added

- 新增 IME「全選」與「清除全部」操作；清除全部需要再次確認。
- 將全選與清除全部直接放在面板上，移除「更多」二級選單中的入口。
- 新增面板高度一致性測試。

### Changed

- 縮小待命主錄音按鈕，讓輸入法面板更精簡。
- 將主互動區固定為 130dp，讓待命與錄音中切換時維持相同面板高度。
- 設定首頁改以精簡麥克風授權提醒開場，並將版本更新移至最後。
- 保留底部系統安全區處理。

<a id="v0-9-0"></a>
## [0.9.0] - 2026-10-07

> 此日期為原始 Release commit 日期；目前 repository 沒有對應的 GitHub Release 或 Tag。

### Added

- 新增 App 內版本更新檢查，從 KuiesVox 官方 GitHub Releases 讀取 release 清單。
- 支援檢查 Preview prerelease 與 Stable release，並依 Semantic Version 選出最高版本。
- 新增 App 內官方 Release APK 下載；下載狀態顯示於設定頁與 Android 通知列。
- 下載完成後呼叫 Android 系統安裝器，交由使用者確認更新。
- 設定首頁新增「前往 GitHub Releases」手動下載入口；發現更新時也可直接開啟該版本頁面。
- 新增查看更新內容的視窗，避免整篇 release notes 佔滿設定首頁。

### Security

- 公開 GitHub Releases API 以匿名方式讀取，不使用 GitHub Token、OAuth token 或 GitHub secret。
- APK 僅從 KuiesVox 官方 GitHub Release assets 透過 HTTPS 下載。
- APK 存放於 App 可控的下載路徑，不要求 `MANAGE_EXTERNAL_STORAGE`。
- 下載完成後仍需由使用者在 Android 系統安裝器確認。
- 更新需使用相同 `applicationId`、相同 APK signing certificate，以及較高的 `versionCode`。

<a id="v0-8-0"></a>
## [0.8.0] - 2026-10-07

### Added

- 新增 Groq Whisper 語音辨識模型選擇：Whisper Large V3 與 Whisper Large V3 Turbo，預設使用 Turbo。
- 新增智慧文字整理 Provider 選擇：Groq、Google Gemini 與 OpenAI。
- 提供 Provider 推薦模型與自訂 Model ID 欄位。原版本清單為：Groq Qwen 3.8 27B、Qwen 3.6 27B、GPT-OSS 20B；Gemini 3.5 Flash-Lite、Gemini 3.6 Flash、Gemini 3.8 Flash；OpenAI GPT-5.6 Luna、GPT-5.6 Terra、GPT-5.4 Mini。各 Provider 均可設定自訂 Model ID。
- 新增 Groq、Gemini 與 OpenAI 官方 API Key 申請連結；API Key 儲存在 App 私有設定中。
- 新增句尾句號模式：智慧（依輸入欄位判斷）、加入句號、不加入句號。
- 新增 IME 設定按鈕，可從輸入法面板開啟 KuiesVox 設定。
- 新增 Enter／換行按鈕，可在目前文字欄位插入換行。

### Changed

- 主語音輸入按鈕改為大圓形錄音鍵，讓待命畫面的主要操作更清楚。
- 工具列依序排列設定、刪除、換行與切換鍵盤；刪除改用帶 X 的 backspace 鍵帽圖示，換行改用折返箭頭。
- 增加刪除、換行與切換鍵盤按鈕之間的間距；刪除使用粉灰色、換行使用淡紫色，設定與鍵盤切換維持中性色，以降低誤觸機率。
- 工具按鈕尺寸保持一致，並以省略狀態文字支援較窄的面板寬度。
- 保留 Android navigation bar 與 system gesture inset，改善小螢幕及系統鍵盤切換／收合區域的相容性。
- 最近輸入維持單行精簡預覽，不擴大成卡片。

### Known Limitations

- Gemini 與 OpenAI 當時僅提供 Provider、模型與 API Key 設定欄位，尚未串接對應 API；選用時會使用本機文字修正結果。Groq 智慧整理仍使用既有整理流程。

### Links

- Groq API Key：[console.groq.com/keys](https://console.groq.com/keys)
- Google Gemini API Key：[aistudio.google.com/apikey](https://aistudio.google.com/apikey)
- OpenAI API Key：[platform.openai.com/api-keys](https://platform.openai.com/api-keys)

<a id="v0-7-0"></a>
## [0.7.0] - 2026-10-06

### Added

- 新增 Groq Whisper 語音辨識，並可停止或取消目前流程。
- 新增個人常用詞、文字修正规則與可選 MCP Context。
- 新增可選的 Groq 智慧逐字稿整理。
- 顯示輸入狀態與最近結果。
- 精簡面板提供鍵盤切換與退格；退格支援單擊刪除及長按連續刪除。

### Security

- 使用者需自行準備 Groq API Key 並在 App 設定；APK 不內含 API Key，請勿將個人 API Key 貼到公開 issue、README 或 Git repository。
- 語音錄音會傳送至 Groq API 辨識；長文字整理也可能傳送文字至 Groq API。

### Known Limitations

- 此版本為 debug 測試版，可能仍有未發現的問題，不代表正式發布簽章或 Play 商店版本。
- 語音辨識與長文字整理需要網路及可用的 Groq API；此版本沒有離線語音辨識。
- Android 版本、廠牌及 App 的輸入欄位實作可能不同；密碼欄位或自訂輸入元件的行為仍取決於目標 App。
- MCP Context 須另外設定可用的 MCP 服務。

### Links

- Groq API Key：[console.groq.com/keys](https://console.groq.com/keys)

[Unreleased]: https://github.com/kuies001/KuiesVox/compare/v0.13.0...HEAD
[0.13.0]: https://github.com/kuies001/KuiesVox/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.12.0
[0.11.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.11.0
[0.10.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.10.0
[0.9.0]: https://github.com/kuies001/KuiesVox/blob/b8f1a2ca2055876095bdbfe842bd937baded1149/RELEASE_NOTES_v0.9.0.md
[0.8.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.8.0
[0.7.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.7.0
