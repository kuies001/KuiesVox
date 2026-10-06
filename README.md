# VoiceIME

Android 語音輸入法專案。

目前功能：
- Android `InputMethodService`
- Groq Whisper 語音辨識
- 個人常用詞
- 文字修正规則
- MCP Context
- 智慧逐字稿整理
- 可取消錄音 / 辨識
- 自訂輸入法 UI

## 建置

使用 Android Studio 開啟專案，或執行：

```shell
./gradlew assembleDebug
```

Debug APK 預設輸出於 `app/build/outputs/apk/debug/app-debug.apk`。

Groq API Key 請在 App 設定頁輸入。請勿將 API Key、Bearer Token 或其他憑證放入原始碼、README 或 Git repository。
