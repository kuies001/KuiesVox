# KuiesVox 自動化開發與實機驗收規範

本檔為 KuiesVox（`C:\AndroidProjects\VoiceIME`，`tw.kuies.voiceime`，GitHub `kuies001/KuiesVox`）的 Agent 工作規範。
適用於一般功能開發、Bug 修復、單元測試、Debug / Release 建置、實機驗收及回歸測試。

---

## 0. 專案與環境速查

| 項目 | 值 |
| --- | --- |
| 專案根目錄 | `C:\AndroidProjects\VoiceIME` |
| applicationId / namespace | `tw.kuies.voiceime` |
| IME 服務 | `tw.kuies.voiceime/.VoiceImeService` |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| 建置工具鏈 | Gradle 9.6.0、AGP 9.4.1、Kotlin 2.2.10、Compose BOM、Java 11 相容 |
| 原始碼 | `app/src/main/java/tw/kuies/voiceime/`（Compose + Kotlin） |
| JVM 單元測試 | `app/src/test/`（`testDebugUnitTest` / `testReleaseUnitTest`） |
| 儀器測試 | `app/src/androidTest/`（需裝置，見 §6） |
| adb | `C:\adb\platform-tools\adb.exe` |
| 測試機 | Samsung SM-F7660（Android 16 / SDK 36），目前為 USB 序號 `R5CY73X79SP` |

**Gradle 呼叫方式（本機必需）：** `java`／`gradle` 不在 PATH，`JAVA_HOME` 需指向 Android Studio JBR。

```cmd
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr" && gradlew.bat <tasks>
```

**標準驗證指令：**

```cmd
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr" && gradlew.bat testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease --console=plain
git diff --check
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`。

---

## 一、主要原則

開發者已透過 Android Studio 連接 Android 實體手機並開啟 USB 偵錯。

往後一般功能開發、Bug 修復、單元測試、Debug / Release 建置、實機驗收及回歸測試，原則上由 Coding Agent 自主完成。

除非涉及重大 UI 風格改版、安全與隱私重大變更、資料可能遺失、不可逆操作，否則不需逐項要求開發者手動確認。

**編譯成功不等於功能測試通過。** 一律以 §七 的報告格式回報實際驗證範圍。

---

## 二、實機測試流程

每次完成開發後依序執行：

1. **確認裝置**：`C:\adb\platform-tools\adb.exe devices -l`，確認實體裝置狀態為 `device`（已授權且在線）。注意連線方式（USB 序號 vs `192.168.x.x:port` 無線）：涉及網路／Wi-Fi 的測試會中斷無線 adb，必須先確認傳輸方式。
2. **建置與單元測試**：執行 §0 的標準驗證指令，記錄測試數量與失敗數。
3. **評估安裝的資料風險**（**關鍵步驟，見 §6**）：先判斷手機上 KuiesVox 的簽章軌道。若與本次建置簽章不相容，安裝／`-r` 更新會失敗，強制安裝則需先卸載並清空資料。此情況視為「無法在不影響資料的前提下安裝」，**停止並回報，取得開發者同意或改用隔離裝置**。
4. **安裝測試 APK**：僅在第 3 步確認安全後執行 `adb install -r`。禁止為測試而 `adb uninstall` 或 `pm clear`。
5. **啟動並操作**：`adb shell am start -n tw.kuies.voiceime/.MainActivity`，依功能進行操作。
6. **操作驗證**：以 `uiautomator dump`（配合 `adb pull` 解析 XML 的 `text` / `content-desc` / `bounds`）、`adb shell input`、必要時 `screencap`（寫在裝置端再 `adb pull`）進行。**注意：`uiautomator dump` 不含 IME 面板視窗，面板版面只能靠儀器測試或開發者目視。**
7. **監控 Logcat**：排查 Crash、ANR、`SecurityException`、`IllegalAccessError` 及其他異常。
8. **驗證結果**：確認功能輸出、畫面狀態與必要回歸項目。
9. **自主修復**：發現 Bug 時追查根因、最小範圍修復、重跑相關測試（權限見 §四）。
10. **輸出驗收報告**：格式見 §七。

> 若無法完成某項測試，明確標示 `NOT TESTED` 或 `INCONCLUSIVE`，**不得假裝 PASS**。

---

## 三、KuiesVox 專屬測試項目

優先覆蓋（依風險與既有功能）：

- **麥克風授權與拒絕**：首次授權、拒絕後的降級行為（拒絕後錄音會靜默中止，必須確認有明確提示）。
- **語音錄音、停止、取消**：一般模式與長按錄音手勢。
- **Groq Whisper 辨識**：ASR 模型選擇、中英混合、語言模式。
- **AI 文字整理、翻譯、編輯預覽**：智慧整理、智慧標點／段落、台灣用字偏好、句尾句號；語音翻譯模式；AI 編輯模式的預覽與「確認取代」。
- **IME 鍵盤切換**：工具列「切換鍵盤」、系統輸入法切換。
- **工具列功能**：剪貼簿、全選、清除（需二次確認）、AI 翻譯、切換鍵盤、設定、更多；工具列按鈕**不得因裝置探測而被隱藏**。
- **剪貼簿與語音歷史**：檢視、插入、複製、刪除、釘選、隱私開關（關閉後不再新增，既有紀錄不刪）。
- **個人化詞庫與修正规則**：新增／刪除常用詞與「原文字 → 修正文字」規則，批次刪除。
- **App 設定與新手導覽**：首次啟動 3 頁導覽、設定首頁、App 專屬 Profile 覆蓋／繼承、MCP 設定。
- **輸入框 Session 與非同步請求安全**：輸入框失效或切換時的結果丟棄、AI 編輯的四項取代前驗證（operationId／欄位／選取範圍／選取文字）。
- **Debug 與 Release 差異**：Release 為 R8 縮減＋混淆版本；確認沒有只在 Release 出現的啟動崩潰或行為差異。
- **輸入法高度、按鈕位置、深淺色外觀**：版面高度不得因預覽頁增加，外觀在深／淺色與各模式配色一致。

**真實 API 測試**僅在現有設定可安全使用時執行，不得在紀錄、logcat 或報告中洩漏 API Key。

---

## 四、自動修復權限

允許 Agent 自主修復並重新測試（無須每次暫停詢問）：

- 編譯錯誤。
- 單元測試失敗。
- 一般程式邏輯 Bug。
- Crash 與例外處理。
- 小幅 UI 錯位。
- 不影響資料的相容性問題。
- 既有功能的回歸問題。

**限制**：僅修復本次任務相關問題，不得順便大規模重構。

---

## 五、必須由開發者確認（不得自主執行）

- 大幅變更 UI 設計、風格、配色或操作流程。
- 清空、重設、遷移可能無法復原的使用者資料。
- 刪除 App、清除 App Data（`pm clear`）或恢復原廠設定。
- 修改 API Key 儲存、憑證及重大隱私權限策略。
- 對外發布 GitHub Release。
- 建立 Git Tag 或更改版本號（`versionCode` / `versionName`）。
- 涉及付費、外部帳戶設定或其他不可逆操作。

**禁止為了測試而直接執行** `adb uninstall`、`pm clear`，或使用任何可能造成資料遺失的方式覆蓋正式安裝版本。

Release APK 若與目前安裝版本簽章不相容，**不得自行移除 App 強制安裝**；應改用隔離的測試裝置、模擬器或其他安全驗收方式。

---

## 六、測試資料隔離與安裝安全

### 6.1 手機上的簽章軌道（安裝前必查）

手機目前安裝的 KuiesVox 為**正式 Release 簽章軌道**（`versionName 0.15.5`、`versionCode 15`，簽章憑證 SHA-256 `4f71649663a3195cdc7686a5d9e7a08cc6f3f0e65f4bebe95bb0417fdc22e35d`）。本機產生的 **Debug APK 無法覆蓋安裝**，會出現 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

Release keystore 位於 `C:\Users\張阿翔\AndroidKeys\kuiesvox-release.jks`，但 alias／密碼只存在 GitHub Actions secrets，本機無法簽出相符的 APK。

**因此：** 未經開發者同意，不得在手機上卸載後強制安裝本機建置版本（會永久遺失 Groq API Key、語音／剪貼簿歷史、快捷短語與 App Profile）。此類驗收請改用模擬器或隔離裝置，或等候正式 Release 更新。

安裝前先確認軌道：

```cmd
C:\adb\platform-tools\adb.exe shell dumpsys package tw.kuies.voiceime | findstr /C:versionName /C:versionCode
```

### 6.2 儀器測試（instrumented test）

`connectedDebugAndroidTest` 等 Gradle connected 測試結束時會**卸載 App 並清空其資料**（已於實機確認）。**嚴禁對開發者日常手機執行。**

需要裝置的儀器測試（如 `VoiceImePanelLayoutTest`、`SavedSnippetDatabaseMigrationTest`、`SecureCredentialStoreTest` 等）請在模擬器或專用測試機執行；執行前先 `adb disconnect` 手機，避免 AGP 誤選手機為目標。若無法執行，於報告中標示 `NOT TESTED` 並說明原因，不得以其他檢查替代。

### 6.3 測試資料

- 自動測試可使用專用測試資料，但**不得修改或刪除開發者的正式資料**。
- 測試應盡可能可重複執行，並在完成後清理自身產生的暫存檔（例如 `adb pull` 的暫存 APK、dump XML、截圖）。
- 若某項操作會導致既有 App 被卸載或資料被重設，必須先評估影響並取得同意。

---

## 七、完成標準與驗收報告格式

每次任務完成時，自動產出並回報：

1. **修改檔案清單**。
2. **Bug 根本原因與修復方式**（若為修復任務）。
3. **Gradle 測試結果**：具體測試數量與失敗數（`testDebugUnitTest` / `testReleaseUnitTest`），以及 `assembleDebug` / `assembleRelease` 是否成功與 APK 產物路徑。
4. **ADB 實機測試結果**：裝置序號、執行過的步驟與觀察到的結果。
5. **Logcat 異常檢查結果**：是否出現 Crash、ANR、`SecurityException`、`IllegalAccessError` 等。
6. **Debug / Release 驗證情況**：特別標示只在 Release 可重現的差異。
7. **尚未通過或無法驗證的項目**：明確列出，並說明阻礙與解鎖條件。

結果一律使用 `PASS` / `FAIL` / `NOT TESTED` / `INCONCLUSIVE` 清楚區分。

- **不得把編譯成功視為功能測試通過。**
- **不得以單元測試通過宣稱需要實機的跨 App 功能（如 AI 編輯選取文字、IME 面板版面）已完成驗證。**
- 若環境無法完成某項檢查（無法連線裝置、工具鏈缺失、建置被防毒阻擋），**報告確切阻礙，不得宣稱成功**；遇到防毒（Kaspersky）或 Gradle worker 問題時，分別回報編譯、測試來源編譯與測試執行，且**不得關閉防毒**來強行通過。

任務完成後直接回報，不需要求開發者再手動執行已經成功的測試。

---

## 八、版本、Tag 與 Release 界線

- **功能／實作 commit 不動版本**：`versionCode` / `versionName` 保持不變，不建立 Git tag、不發布 GitHub Release、不執行 `release.ps1`。版本化是開發者明確要求時的獨立步驟，回報時說明「版本、tag、Release 均未變動」。
- 發版流程見 `.github/RELEASING.md`，腳本為 `release.ps1`；發版屬 §五 須確認事項。
- 本機執行 `release.ps1` 的注意事項：**不要重導向輸出**（PowerShell 5.1 + `$ErrorActionPreference=Stop` 會把原生 stderr 變成終止錯誤），且 `app/build.gradle.kts` 必須維持 **LF** 換行（腳本版本 regex 只匹配 LF）。先跑 `-DryRun`。
- `gh` 未安裝；查證發布結果改用 GitHub REST API（`https://api.github.com/repos/kuies001/KuiesVox/releases/tags/vX.Y.Z`）。

---

## 九、已知裝置與工具限制

- **IME 面板不在 uiautomator dump 中**：`adb shell uiautomator dump` 的 XML 不含 IME 視窗，面板版面只能用儀器測試或開發者目視確認。
- **截圖**：`adb exec-out screencap -p > file` 在 Windows shell 會產生損壞 PNG；請 `adb shell screencap -p /sdcard/x.png` 後 `adb pull`。
- **無法由 adb 啟動非 exported 服務**：`am start-foreground-service` 對非 exported 元件會因權限失敗；改用 UI 自動化或 debug-only exported receiver。
- **無線 vs USB adb**：關閉 Wi-Fi 會斷開無線 adb；網路相關測試前先確認序號來源。手機有時作為 PC 的網路熱點，網路中斷測試可能同時切斷 Agent 自身連線。
- **螢幕鎖**：測試機已設定螢幕鎖，未解鎖時無法無人操作；不得為了測試清除螢幕鎖。
- **R8 設定**：release buildType 僅保留 `optimization { enable = true }`，**不得**加回 `packageScope`（partial R8 曾導致 Release APK 啟動即 `IllegalAccessError`）。
