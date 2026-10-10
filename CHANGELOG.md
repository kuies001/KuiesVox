# Changelog

本檔依 Keep a Changelog 的基本格式整理 KuiesVox 各版本的重要變更。版本由新到舊排列；已正式發布版本的日期採 GitHub Release 發布日期。

## 版本索引

- [未發布](#unreleased)
- [0.15.2](#v0-15-2)
- [0.15.1](#v0-15-1)
- [0.15.0](#v0-15-0)
- [0.14.0](#v0-14-0)
- [0.13.0](#v0-13-0)
- [0.12.0](#v0-12-0)
- [0.11.0](#v0-11-0)
- [0.10.0](#v0-10-0)
- [0.9.0](#v0-9-0)
- [0.8.0](#v0-8-0)
- [0.7.0](#v0-7-0)

## [Unreleased]

此區留給下一版尚未發布的變更。

<a id="v0-15-2"></a>
## [0.15.2] - 2026-10-10

### Added

- 個人化的「常用詞」與「修正规則」新增批次刪除：按頁面右上角「刪除」進入選取模式，每列出現勾選框、可整列點選，並提供「全選」與「已選取 X 筆」；下方「刪除已選取（X）」在未選取時停用，按下後先顯示「確認刪除」對話框才刪除，取消則保留選取、不更動任何資料。
- 批次刪除只會移除你勾選的項目：未選取的詞彙與規則、其啟用狀態與語境句都會原樣保留。

### Changed

- 常用詞與修正规則不再在每一列顯示「刪除」按鈕；啟用開關、搜尋、新增、批次匯入與「語句」編輯維持不變。
- 選取模式的「全選」只作用於目前的搜尋結果，不會選到被搜尋條件濾掉的其他資料；切換分頁或離開頁面會清空選取。

<a id="v0-15-1"></a>
## [0.15.1] - 2026-10-10

### Fixed

- 修正新安裝會自動帶入內建預設詞庫與修正规則的問題；現在詞庫與修正规則預設都是空的，只有使用者自己新增的內容會存在，覆蓋安裝也不會更動既有資料。
- 修正 Android 系統設定中的 KuiesVox 項目點了沒有反應的問題；現在會直接開啟 KuiesVox 設定首頁。
- 修正部分手機看不到「切換鍵盤」圖示的問題；圖示改為固定顯示，Android 無法直接切換時改為開啟系統輸入法選擇器。

### Changed

- 移除個人化設定中的「匯入預設詞庫」與「匯入預設修正规則」入口，因為已不再提供內建預設資料。

### Security

- 個人詞庫、修正规則與 App 專屬 Profile 納入雲端備份與裝置轉移的排除清單，個人化資料預設不離開裝置；API Key、MCP 設定與歷史紀錄的既有保護不變。
- 自本版起不再內建任何個人化預設資料；含舊內建預設詞庫的 v0.15.0 APK 附件已自 GitHub Releases 移除，避免繼續散布。

<a id="v0-15-0"></a>
## [0.15.0] - 2026-10-10

### Added

- 新增「AI 編輯模式」：先在 LINE、Gmail 等 App 選取文字，再從「更多」頁進入 AI 編輯，用語音說明要怎麼修改；改寫結果先在面板內預覽，按「確認取代」才替換原文，取消則原文完全不變。
- AI 編輯在取代前會重新確認作業、輸入欄位、選取範圍與選取文字仍與收音前一致；只要無法確認（例如錄音期間選取被取消）就一律拒絕取代並提醒重新選取，不會改以游標插入或搜尋相同文字頂替。
- 新增「語音翻譯模式」：頂部工具列新增 AI 翻譯捷徑，說出的內容會先翻譯再插入，目標語言可選英文、日文、韓文與繁體中文（台灣），並記住上次選擇。
- 新增「隱私與歷史紀錄」設定頁：語音歷史與剪貼簿歷史可分別關閉，關閉後不再新增紀錄，既有紀錄不會被刪除。

### Changed

- 頂部工具列順序改由單一來源決定：剪貼簿、全選、清除、AI 翻譯、鍵盤、設定、更多；七個項目改為等寬，鍵盤、設定與更多固定為最後三個。
- 語音翻譯模式的主按鈕改用薄荷青綠底色與深色圖示，待命、錄音與翻譯處理中維持同一識別；一般語音與格式指令的配色不變。
- 語音翻譯的後製併入既有口語整理原則：移除沒有語意作用的贅詞與結巴、修正停頓造成的重複標點，同時保留具語氣或語意作用的詞，維持語音辨識一次、LLM 一次。
- 語音翻譯的「切換語言」按鈕縮短為「切換」，寬度縮小但圖示與功能不變。
- 主按鈕、標題與副標題改以面板中心線置中，錄音控制不再向左偏移，也不侵入右側刪除／Enter 區域。
- 工具列圖示改用共用的容器高度與置中方式，圖示尺寸一致，並對剪貼簿與翻譯圖示做小幅視覺補償。

### Fixed

- 修正 Release APK 安裝後一開啟就閃退的問題：Release 建置不再啟用 R8 部分（漸進式）縮減，該模式會產生其他套件無法存取的類別，載入時丟出 IllegalAccessError。
- 修正 App 內更新永遠無法完成驗證的問題：改以官方發布的 `KuiesVox-vX.Y.Z.sha256` 檔名比對，並確認下載檔名、檔案大小與 SHA-256 相符後才允許安裝。
- 修正程序被中斷後殘留錄音暫存檔的問題：IME 建立輸入視圖時會清除超過六小時、且由 KuiesVox 自行命名的 `voice-*.wav`。
- 修正關閉歷史紀錄後仍可能寫入的問題：隱私開關的檢查移到實際寫入的位置，已排入的寫入不會在關閉後落盤。
- 修正翻譯捷徑出現大型色塊、錄音控制偏移等 IME 版面問題，並補上多組版面回歸測試。

### Security

- App 內更新新增完整性驗證：檢查下載檔名、檔案大小與 SHA-256，並限定在官方 release 下載路徑；任何無法確認的情況一律拒絕安裝，驗證失敗會刪除已下載檔案，Android 簽章驗證仍是最終信任依據。
- 雲端備份與裝置轉移排除 MCP 設定、MCP 內容快取與下載的更新 APK。
- MCP 連線不再直接採用伺服器回應的 `Mcp-Session-Id`，改用前先驗證內容格式與長度。

### Known Limitations

- AI 編輯的選取讀取取決於目標 App；若在錄音或切換輸入法時選取消失，KuiesVox 會拒絕取代並請使用者重新選取。
- 語音翻譯與 AI 編輯各需要一次語音辨識與一次 LLM 請求，需具備可用的 Provider 與 API Key。
- Gemini 與 OpenAI 仍只提供 Provider、模型與 API Key 設定欄位，尚未完成對應 API 串接。

<a id="v0-14-0"></a>
## [0.14.0] - 2026-10-09

### Added

- 新增「格式指令模式」：可從「更多」頁手動進入，用語音控制換行、空一行、編號清單、項目符號與標題；完成一次處理後自動回到一般語音模式，也可隨時按「返回」離開。
- 新增「台灣繁體中文用字偏好」：依語境優先使用台灣慣用詞彙，保留專有名詞與原意；可與智慧整理總開關分開設定，並支援 App Profile 覆寫。
- 新增「智慧標點與段落整理」：依句型判斷問號、句號、逗號與頓號，並在主題、時間或語意轉換時分段；問句標點不再只依關鍵字判斷。
- 「更多」頁面四個項目改為一致的「左側圖示＋右側文字」列，並新增格式指令入口。

### Changed

- 格式指令模式的主按鈕改用排版圖示，並將「返回一般模式」縮短為「返回」，讓模式只靠主按鈕即可辨識，也不再擠壓主按鈕區域。
- 語音歷史只記錄有實質文字輸出的格式指令；單純的換行與空一行不會產生只有換行的紀錄。

### Fixed

- 修正格式指令模式提示文字在部分裝置寬度被裁切的問題；提示改為最多兩行、置中顯示，且不使用省略號。
- 修正格式指令模式會把使用者口述內容當成提問回答的問題；請求改以 FORMAT INSTRUCTION 與 SOURCE CONTENT 兩個欄位明確區隔，來源內容一律視為資料，不得回答、執行或補充。
- 修正「更多」頁面新增第四個項目時清單被裁切的問題，項目改為可捲動。

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

[Unreleased]: https://github.com/kuies001/KuiesVox/compare/v0.15.2...HEAD
[0.15.2]: https://github.com/kuies001/KuiesVox/compare/v0.15.1...v0.15.2
[0.15.1]: https://github.com/kuies001/KuiesVox/compare/v0.15.0...v0.15.1
[0.15.0]: https://github.com/kuies001/KuiesVox/compare/v0.14.0...v0.15.0
[0.14.0]: https://github.com/kuies001/KuiesVox/compare/v0.13.0...v0.14.0
[0.13.0]: https://github.com/kuies001/KuiesVox/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.12.0
[0.11.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.11.0
[0.10.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.10.0
[0.9.0]: https://github.com/kuies001/KuiesVox/blob/b8f1a2ca2055876095bdbfe842bd937baded1149/RELEASE_NOTES_v0.9.0.md
[0.8.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.8.0
[0.7.0]: https://github.com/kuies001/KuiesVox/releases/tag/v0.7.0
